/*
 * Copyright 2026 ihelio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ihelio.cpg.application.orchestration;

import com.ihelio.cpg.domain.engine.ProcessExecutionEngine;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.orchestration.DecisionTrace;
import com.ihelio.cpg.domain.orchestration.DecisionTracer;
import com.ihelio.cpg.domain.orchestration.EligibleSpace;
import com.ihelio.cpg.domain.orchestration.ExecutionGovernor;
import com.ihelio.cpg.domain.orchestration.GovernanceResult;
import com.ihelio.cpg.domain.orchestration.NavigationDecision;
import com.ihelio.cpg.domain.orchestration.NodeSelector;
import com.ihelio.cpg.domain.orchestration.RuntimeContext;
import java.util.Objects;
import java.util.UUID;

/**
 * InstanceOrchestrator orchestrates a single process instance through event-driven execution.
 *
 * <p>The orchestrator is completely event-driven:
 * <ul>
 *   <li>Each event triggers evaluation and potentially one node execution</li>
 *   <li>No auto-advance - explicit events required to progress</li>
 *   <li>Provides visibility into required events for progression</li>
 * </ul>
 */
public class InstanceOrchestrator {

    private final ContextAssembler contextAssembler;
    private final EligibilityEvaluator eligibilityEvaluator;
    private final NodeSelector nodeSelector;
    private final ExecutionGovernor executionGovernor;
    private final ProcessExecutionEngine executionEngine;
    private final DecisionTracer decisionTracer;

    public InstanceOrchestrator(
            ContextAssembler contextAssembler,
            EligibilityEvaluator eligibilityEvaluator,
            NodeSelector nodeSelector,
            ExecutionGovernor executionGovernor,
            ProcessExecutionEngine executionEngine,
            DecisionTracer decisionTracer) {
        this.contextAssembler = Objects.requireNonNull(contextAssembler);
        this.eligibilityEvaluator = Objects.requireNonNull(eligibilityEvaluator);
        this.nodeSelector = Objects.requireNonNull(nodeSelector);
        this.executionGovernor = Objects.requireNonNull(executionGovernor);
        this.executionEngine = Objects.requireNonNull(executionEngine);
        this.decisionTracer = Objects.requireNonNull(decisionTracer);
    }

    /**
     * Performs a single orchestration step for a process instance.
     * Executes at most one node per call - event-driven progression.
     */
    public OrchestrationResult orchestrate(ProcessInstance instance, ProcessGraph graph,
            String tenantId) {
        Objects.requireNonNull(instance, "instance is required");
        Objects.requireNonNull(graph, "graph is required");

        RuntimeContext context = contextAssembler.assemble(instance, tenantId);
        EligibleSpace eligibleSpace = eligibilityEvaluator.evaluate(instance, graph, context);
        NavigationDecision decision = nodeSelector.select(eligibleSpace, instance, graph);

        return switch (decision.type()) {
            case PROCEED -> handleProceed(instance, graph, context, eligibleSpace, decision);
            case WAIT -> handleWait(instance, context, eligibleSpace, decision);
            case COMPLETE -> handleComplete(instance, context, eligibleSpace, decision);
            case BLOCKED -> handleBlocked(instance, context, eligibleSpace, decision);
        };
    }

    /**
     * Orchestrates entry into the process (first node only).
     */
    public OrchestrationResult orchestrateEntry(ProcessInstance instance, ProcessGraph graph,
            RuntimeContext initialContext) {
        Objects.requireNonNull(instance, "instance is required");
        Objects.requireNonNull(graph, "graph is required");
        Objects.requireNonNull(initialContext, "initialContext is required");

        EligibleSpace eligibleSpace = eligibilityEvaluator.evaluateEntryNodes(graph, initialContext);
        NavigationDecision decision = nodeSelector.select(eligibleSpace, instance, graph);

        if (!decision.shouldProceed()) {
            return handleWait(instance, initialContext, eligibleSpace, decision);
        }

        return handleProceed(instance, graph, initialContext, eligibleSpace, decision);
    }

    /**
     * Re-evaluates and executes after an event (single step).
     */
    public OrchestrationResult reevaluateAfterEvent(ProcessInstance instance, ProcessGraph graph,
            RuntimeContext context, String eventType) {
        Objects.requireNonNull(eventType, "eventType is required");

        EligibleSpace eligibleSpace = eligibilityEvaluator.reevaluateAfterEvent(
            instance, graph, context, eventType);
        NavigationDecision decision = nodeSelector.select(eligibleSpace, instance, graph);

        return switch (decision.type()) {
            case PROCEED -> handleProceed(instance, graph, context, eligibleSpace, decision);
            case WAIT -> handleWait(instance, context, eligibleSpace, decision);
            case COMPLETE -> handleComplete(instance, context, eligibleSpace, decision);
            case BLOCKED -> handleBlocked(instance, context, eligibleSpace, decision);
        };
    }

    /**
     * Executes a single node - no auto-advance.
     */
    private OrchestrationResult handleProceed(
            ProcessInstance instance,
            ProcessGraph graph,
            RuntimeContext context,
            EligibleSpace eligibleSpace,
            NavigationDecision decision) {

        NavigationDecision.NodeSelection selection = decision.primarySelection();
        if (selection == null) {
            return handleWait(instance, context, eligibleSpace, decision);
        }

        Node node = selection.node();

        if (instance.hasExecutedNode(node.id())) {
            return handleWait(instance, context, eligibleSpace, decision);
        }

        GovernanceResult governance = executionGovernor.enforce(instance, node, context);
        if (!governance.approved()) {
            return handleGovernanceBlocked(instance, context, eligibleSpace, decision, governance);
        }

        String executionId = UUID.randomUUID().toString();
        try {
            instance.startNodeExecution(node.id());
            var executionResult = executionEngine.executeNode(instance, graph, node);
            instance.completeNodeExecution(node.id(), executionResult.getOutput());
            executionGovernor.recordExecution(instance, node, context, executionId);

            RuntimeContext updatedContext = RuntimeContext.fromExecutionContext(instance.context())
                .withEntityState(node.id().value(), executionResult.getOutput());

            DecisionTrace trace = buildExecutionTrace(
                instance, updatedContext, eligibleSpace, decision, governance, executionResult.getOutput());
            decisionTracer.record(trace);

            return OrchestrationResult.executed(instance, decision, trace, governance);

        } catch (Exception e) {
            instance.failNodeExecution(node.id(), e.getMessage());
            DecisionTrace trace = buildFailedTrace(
                instance, context, eligibleSpace, decision, governance, e.getMessage());
            decisionTracer.record(trace);
            return OrchestrationResult.failed(instance, decision, trace, e.getMessage());
        }
    }

    private OrchestrationResult handleWait(
            ProcessInstance instance,
            RuntimeContext context,
            EligibleSpace eligibleSpace,
            NavigationDecision decision) {
        DecisionTrace trace = buildWaitTrace(instance, context, eligibleSpace, decision);
        decisionTracer.record(trace);
        return OrchestrationResult.waiting(instance, decision, trace);
    }

    private OrchestrationResult handleComplete(
            ProcessInstance instance,
            RuntimeContext context,
            EligibleSpace eligibleSpace,
            NavigationDecision decision) {
        instance.complete();
        DecisionTrace trace = buildCompleteTrace(instance, context, eligibleSpace, decision);
        decisionTracer.record(trace);
        return OrchestrationResult.completed(instance, decision, trace);
    }

    private OrchestrationResult handleBlocked(
            ProcessInstance instance,
            RuntimeContext context,
            EligibleSpace eligibleSpace,
            NavigationDecision decision) {
        DecisionTrace trace = buildBlockedTrace(instance, context, eligibleSpace, decision, null);
        decisionTracer.record(trace);
        return OrchestrationResult.blocked(instance, decision, trace, decision.selectionReason());
    }

    private OrchestrationResult handleGovernanceBlocked(
            ProcessInstance instance,
            RuntimeContext context,
            EligibleSpace eligibleSpace,
            NavigationDecision decision,
            GovernanceResult governance) {
        DecisionTrace trace = buildBlockedTrace(instance, context, eligibleSpace, decision, governance);
        decisionTracer.record(trace);
        return OrchestrationResult.blocked(instance, decision, trace, governance.rejectionReason());
    }

    private DecisionTrace buildExecutionTrace(
            ProcessInstance instance, RuntimeContext context, EligibleSpace eligibleSpace,
            NavigationDecision decision, GovernanceResult governance, Object result) {
        return DecisionTrace.builder()
            .instanceId(instance.id())
            .type(DecisionTrace.DecisionType.EXECUTION)
            .context(context)
            .evaluation(buildEvaluationSnapshot(eligibleSpace))
            .decision(decision)
            .governance(governance)
            .outcome(DecisionTrace.OutcomeSnapshot.executed(
                result instanceof java.util.Map ? (java.util.Map) result : java.util.Map.of(),
                DecisionTrace.OutcomeSnapshot.NextState.from(instance)))
            .build();
    }

    private DecisionTrace buildWaitTrace(
            ProcessInstance instance, RuntimeContext context,
            EligibleSpace eligibleSpace, NavigationDecision decision) {
        return DecisionTrace.builder()
            .instanceId(instance.id())
            .type(DecisionTrace.DecisionType.WAIT)
            .context(context)
            .evaluation(buildEvaluationSnapshot(eligibleSpace))
            .decision(decision)
            .governance(DecisionTrace.GovernanceSnapshot.skipped())
            .outcome(DecisionTrace.OutcomeSnapshot.waiting())
            .build();
    }

    private DecisionTrace buildCompleteTrace(
            ProcessInstance instance, RuntimeContext context,
            EligibleSpace eligibleSpace, NavigationDecision decision) {
        return DecisionTrace.builder()
            .instanceId(instance.id())
            .type(DecisionTrace.DecisionType.NAVIGATION)
            .context(context)
            .evaluation(buildEvaluationSnapshot(eligibleSpace))
            .decision(decision)
            .governance(DecisionTrace.GovernanceSnapshot.skipped())
            .outcome(DecisionTrace.OutcomeSnapshot.executed(
                java.util.Map.of("completed", true),
                DecisionTrace.OutcomeSnapshot.NextState.from(instance)))
            .build();
    }

    private DecisionTrace buildBlockedTrace(
            ProcessInstance instance, RuntimeContext context, EligibleSpace eligibleSpace,
            NavigationDecision decision, GovernanceResult governance) {
        String reason = governance != null ? governance.rejectionReason() : decision.selectionReason();
        DecisionTrace.GovernanceSnapshot governanceSnapshot = governance != null
            ? DecisionTrace.GovernanceSnapshot.from(governance)
            : DecisionTrace.GovernanceSnapshot.skipped();
        return DecisionTrace.builder()
            .instanceId(instance.id())
            .type(DecisionTrace.DecisionType.BLOCKED)
            .context(context)
            .evaluation(buildEvaluationSnapshot(eligibleSpace))
            .decision(decision)
            .governance(governanceSnapshot)
            .outcome(DecisionTrace.OutcomeSnapshot.blocked(reason))
            .build();
    }

    private DecisionTrace buildFailedTrace(
            ProcessInstance instance, RuntimeContext context, EligibleSpace eligibleSpace,
            NavigationDecision decision, GovernanceResult governance, String error) {
        return DecisionTrace.builder()
            .instanceId(instance.id())
            .type(DecisionTrace.DecisionType.EXECUTION)
            .context(context)
            .evaluation(buildEvaluationSnapshot(eligibleSpace))
            .decision(decision)
            .governance(governance)
            .outcome(DecisionTrace.OutcomeSnapshot.failed(error))
            .build();
    }

    private DecisionTrace.EvaluationSnapshot buildEvaluationSnapshot(EligibleSpace eligibleSpace) {
        return new DecisionTrace.EvaluationSnapshot(
            eligibleSpace.eligibleNodes().stream()
                .map(DecisionTrace.EvaluationSnapshot.NodeEvaluationSummary::from)
                .toList(),
            eligibleSpace.traversableEdges().stream()
                .map(DecisionTrace.EvaluationSnapshot.EdgeEvaluationSummary::from)
                .toList(),
            DecisionTrace.EvaluationSnapshot.EligibleSpaceSummary.from(eligibleSpace)
        );
    }

    /**
     * Result of an orchestration cycle.
     */
    public record OrchestrationResult(
        ProcessInstance instance,
        NavigationDecision decision,
        DecisionTrace trace,
        GovernanceResult governance,
        OrchestrationStatus status,
        String message
    ) {
        public enum OrchestrationStatus {
            EXECUTED,
            WAITING,
            BLOCKED,
            COMPLETED,
            FAILED
        }

        public static OrchestrationResult executed(
                ProcessInstance instance, NavigationDecision decision,
                DecisionTrace trace, GovernanceResult governance) {
            return new OrchestrationResult(instance, decision, trace, governance,
                OrchestrationStatus.EXECUTED, "Node executed successfully");
        }

        public static OrchestrationResult waiting(
                ProcessInstance instance, NavigationDecision decision, DecisionTrace trace) {
            return new OrchestrationResult(instance, decision, trace, null,
                OrchestrationStatus.WAITING, "Waiting for events");
        }

        public static OrchestrationResult blocked(
                ProcessInstance instance, NavigationDecision decision,
                DecisionTrace trace, String reason) {
            return new OrchestrationResult(instance, decision, trace, null,
                OrchestrationStatus.BLOCKED, reason);
        }

        public static OrchestrationResult completed(
                ProcessInstance instance, NavigationDecision decision, DecisionTrace trace) {
            return new OrchestrationResult(instance, decision, trace, null,
                OrchestrationStatus.COMPLETED, "Process completed");
        }

        public static OrchestrationResult failed(
                ProcessInstance instance, NavigationDecision decision,
                DecisionTrace trace, String error) {
            return new OrchestrationResult(instance, decision, trace, null,
                OrchestrationStatus.FAILED, error);
        }

        public boolean isExecuted() { return status == OrchestrationStatus.EXECUTED; }
        public boolean isWaiting() { return status == OrchestrationStatus.WAITING; }
        public boolean isBlocked() { return status == OrchestrationStatus.BLOCKED; }
        public boolean isCompleted() { return status == OrchestrationStatus.COMPLETED; }
        public boolean isFailed() { return status == OrchestrationStatus.FAILED; }
    }
}
