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

package com.ihelio.cpg.domain.orchestration;

import com.ihelio.cpg.domain.engine.EdgeEvaluation;
import com.ihelio.cpg.domain.engine.NodeEvaluation;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Node;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * DecisionTrace is an immutable record capturing every meaningful orchestration decision.
 *
 * <p>It forms the system of record for execution reasoning, capturing:
 * <ul>
 *   <li>The context at decision time</li>
 *   <li>All rules and policies evaluated</li>
 *   <li>All alternatives considered</li>
 *   <li>The action taken and why</li>
 *   <li>The governance checks performed</li>
 *   <li>The outcome of execution</li>
 * </ul>
 *
 * <p>Decision traces are immutable and persisted for audit and compliance purposes.
 *
 * @param id unique identifier for this trace
 * @param timestamp when the decision was made
 * @param instanceId the process instance this decision belongs to
 * @param type the type of decision
 * @param context snapshot of the context at decision time
 * @param evaluation snapshot of what was evaluated
 * @param decision snapshot of the decision made
 * @param governance snapshot of governance checks
 * @param outcome snapshot of the execution outcome
 */
public record DecisionTrace(
    DecisionTraceId id,
    Instant timestamp,
    ProcessInstance.ProcessInstanceId instanceId,
    DecisionType type,
    ContextSnapshot context,
    EvaluationSnapshot evaluation,
    DecisionSnapshot decision,
    GovernanceSnapshot governance,
    OutcomeSnapshot outcome
) {

    public DecisionTrace {
        Objects.requireNonNull(id, "DecisionTrace id is required");
        Objects.requireNonNull(timestamp, "DecisionTrace timestamp is required");
        Objects.requireNonNull(instanceId, "DecisionTrace instanceId is required");
        Objects.requireNonNull(type, "DecisionTrace type is required");
        Objects.requireNonNull(context, "DecisionTrace context is required");
        Objects.requireNonNull(evaluation, "DecisionTrace evaluation is required");
        Objects.requireNonNull(decision, "DecisionTrace decision is required");
        Objects.requireNonNull(governance, "DecisionTrace governance is required");
        Objects.requireNonNull(outcome, "DecisionTrace outcome is required");
    }

    /**
     * Type of orchestration decision.
     */
    public enum DecisionType {
        /** Selected next action for navigation */
        NAVIGATION,
        /** Executed an action */
        EXECUTION,
        /** No action available - waiting */
        WAIT,
        /** Action blocked by governance */
        BLOCKED
    }

    /**
     * Unique identifier for a decision trace.
     */
    public record DecisionTraceId(String value) {
        public DecisionTraceId {
            Objects.requireNonNull(value, "DecisionTraceId value is required");
            if (value.isBlank()) {
                throw new IllegalArgumentException("DecisionTraceId cannot be blank");
            }
        }

        public static DecisionTraceId generate() {
            return new DecisionTraceId(UUID.randomUUID().toString());
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * Snapshot of the runtime context at decision time.
     *
     * @param clientContext tenant configuration
     * @param domainContext domain knowledge
     * @param entityState entity state
     * @param operationalState operational context
     * @param eventsConsidered events that were considered
     */
    public record ContextSnapshot(
        Map<String, Object> clientContext,
        Map<String, Object> domainContext,
        Map<String, Object> entityState,
        Map<String, Object> operationalState,
        List<String> eventsConsidered
    ) {
        public ContextSnapshot {
            clientContext = clientContext != null ? Map.copyOf(clientContext) : Map.of();
            domainContext = domainContext != null ? Map.copyOf(domainContext) : Map.of();
            entityState = entityState != null ? Map.copyOf(entityState) : Map.of();
            operationalState = operationalState != null ? Map.copyOf(operationalState) : Map.of();
            eventsConsidered = eventsConsidered != null ? List.copyOf(eventsConsidered) : List.of();
        }

        public static ContextSnapshot from(RuntimeContext context) {
            return new ContextSnapshot(
                context.clientContext(),
                context.domainContext(),
                context.entityState(),
                context.operationalContext().toMap(),
                context.receivedEvents().stream()
                    .map(e -> e.eventType())
                    .toList()
            );
        }

        public static ContextSnapshot empty() {
            return new ContextSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), List.of());
        }
    }

    /**
     * Snapshot of evaluation results.
     *
     * @param nodesEvaluated summary of node evaluations
     * @param edgesEvaluated summary of edge evaluations
     * @param eligibleSpace the resulting eligible space
     */
    public record EvaluationSnapshot(
        List<NodeEvaluationSummary> nodesEvaluated,
        List<EdgeEvaluationSummary> edgesEvaluated,
        EligibleSpaceSummary eligibleSpace
    ) {
        public EvaluationSnapshot {
            nodesEvaluated = nodesEvaluated != null ? List.copyOf(nodesEvaluated) : List.of();
            edgesEvaluated = edgesEvaluated != null ? List.copyOf(edgesEvaluated) : List.of();
        }

        public static EvaluationSnapshot empty() {
            return new EvaluationSnapshot(
                List.of(),
                List.of(),
                EligibleSpaceSummary.empty()
            );
        }

        public record NodeEvaluationSummary(
            String nodeId,
            String nodeName,
            boolean eligible,
            List<String> reasons,
            Map<String, Object> ruleOutputs,
            List<String> policyResults
        ) {
            public NodeEvaluationSummary {
                Objects.requireNonNull(nodeId, "nodeId is required");
                reasons = reasons != null ? List.copyOf(reasons) : List.of();
                ruleOutputs = ruleOutputs != null ? Map.copyOf(ruleOutputs) : Map.of();
                policyResults = policyResults != null ? List.copyOf(policyResults) : List.of();
            }

            public static NodeEvaluationSummary from(NodeEvaluation eval) {
                List<String> reasons = new java.util.ArrayList<>();
                if (!eval.available()) {
                    reasons.add(eval.blockedReason());
                }
                List<String> policyResults = eval.policyResults().stream()
                    .map(pr -> pr.policyGate().id() + ":" + (pr.passed() ? "PASSED" : "FAILED"))
                    .toList();
                return new NodeEvaluationSummary(
                    eval.node().id().value(),
                    eval.node().name(),
                    eval.available(),
                    reasons,
                    eval.ruleOutputs(),
                    policyResults
                );
            }
        }

        public record EdgeEvaluationSummary(
            String edgeId,
            String edgeName,
            boolean traversable,
            List<String> reasons
        ) {
            public EdgeEvaluationSummary {
                Objects.requireNonNull(edgeId, "edgeId is required");
                reasons = reasons != null ? List.copyOf(reasons) : List.of();
            }

            public static EdgeEvaluationSummary from(EdgeEvaluation eval) {
                List<String> reasons = new java.util.ArrayList<>();
                if (!eval.traversable()) {
                    reasons.add(eval.blockedReason());
                }
                return new EdgeEvaluationSummary(
                    eval.edge().id().value(),
                    eval.edge().name(),
                    eval.traversable(),
                    reasons
                );
            }
        }

        public record EligibleSpaceSummary(
            int eligibleNodeCount,
            int traversableEdgeCount,
            int candidateActionCount,
            List<String> eligibleNodeIds,
            List<String> traversableEdgeIds
        ) {
            public EligibleSpaceSummary {
                eligibleNodeIds = eligibleNodeIds != null ? List.copyOf(eligibleNodeIds) : List.of();
                traversableEdgeIds = traversableEdgeIds != null ? List.copyOf(traversableEdgeIds) : List.of();
            }

            public static EligibleSpaceSummary from(EligibleSpace space) {
                return new EligibleSpaceSummary(
                    space.eligibleNodes().size(),
                    space.traversableEdges().size(),
                    space.candidateActions().size(),
                    space.eligibleNodes().stream()
                        .map(ne -> ne.node().id().value())
                        .toList(),
                    space.traversableEdges().stream()
                        .map(ee -> ee.edge().id().value())
                        .toList()
                );
            }

            public static EligibleSpaceSummary empty() {
                return new EligibleSpaceSummary(0, 0, 0, List.of(), List.of());
            }
        }
    }

    /**
     * Snapshot of the decision made.
     *
     * @param alternativesConsidered all alternatives that were evaluated
     * @param selectedAction the action that was selected
     * @param selectionCriteria the criteria used for selection
     * @param selectionReason why this selection was made
     */
    public record DecisionSnapshot(
        List<AlternativeSummary> alternativesConsidered,
        SelectedActionSummary selectedAction,
        String selectionCriteria,
        String selectionReason
    ) {
        public DecisionSnapshot {
            alternativesConsidered = alternativesConsidered != null
                ? List.copyOf(alternativesConsidered) : List.of();
        }

        public static DecisionSnapshot from(NavigationDecision decision) {
            List<AlternativeSummary> alternatives = decision.alternatives().stream()
                .map(alt -> new AlternativeSummary(
                    alt.candidateAction().nodeId().value(),
                    alt.priority(),
                    alt.selected(),
                    alt.reason()
                ))
                .toList();

            SelectedActionSummary selected = null;
            if (!decision.selectedNodes().isEmpty()) {
                NavigationDecision.NodeSelection primary = decision.primarySelection();
                selected = new SelectedActionSummary(
                    primary.nodeId().value(),
                    primary.candidateAction().edgeId() != null
                        ? primary.candidateAction().edgeId().value()
                        : null,
                    primary.reason()
                );
            }

            return new DecisionSnapshot(
                alternatives,
                selected,
                decision.selectionCriteria().name(),
                decision.selectionReason()
            );
        }

        public static DecisionSnapshot empty() {
            return new DecisionSnapshot(List.of(), null, "NONE", "No decision made");
        }

        public record AlternativeSummary(
            String nodeId,
            int priority,
            boolean selected,
            String reason
        ) {
            public AlternativeSummary {
                Objects.requireNonNull(nodeId, "nodeId is required");
            }
        }

        public record SelectedActionSummary(
            String nodeId,
            String edgeId,
            String reason
        ) {
            public SelectedActionSummary {
                Objects.requireNonNull(nodeId, "nodeId is required");
            }
        }
    }

    /**
     * Snapshot of governance checks.
     *
     * @param idempotencyCheck result of idempotency check
     * @param authorizationCheck result of authorization check
     * @param policyGateCheck result of policy gate check
     * @param overallApproved whether governance approved the action
     */
    public record GovernanceSnapshot(
        CheckSummary idempotencyCheck,
        CheckSummary authorizationCheck,
        CheckSummary policyGateCheck,
        boolean overallApproved
    ) {
        public GovernanceSnapshot {
            Objects.requireNonNull(idempotencyCheck, "idempotencyCheck is required");
            Objects.requireNonNull(authorizationCheck, "authorizationCheck is required");
            Objects.requireNonNull(policyGateCheck, "policyGateCheck is required");
        }

        public static GovernanceSnapshot from(GovernanceResult result) {
            return new GovernanceSnapshot(
                new CheckSummary(
                    result.idempotencyResult().passed(),
                    result.idempotencyResult().idempotencyKey(),
                    result.idempotencyResult().reason()
                ),
                new CheckSummary(
                    result.authorizationResult().passed(),
                    result.authorizationResult().principal(),
                    result.authorizationResult().reason()
                ),
                new CheckSummary(
                    result.policyGateResult().passed(),
                    String.valueOf(result.policyGateResult().policiesChecked().size()) + " policies",
                    result.policyGateResult().reason()
                ),
                result.approved()
            );
        }

        public static GovernanceSnapshot skipped() {
            return new GovernanceSnapshot(
                new CheckSummary(true, "SKIPPED", "Check skipped"),
                new CheckSummary(true, "SKIPPED", "Check skipped"),
                new CheckSummary(true, "SKIPPED", "Check skipped"),
                true
            );
        }

        public record CheckSummary(
            boolean passed,
            String key,
            String reason
        ) {}
    }

    /**
     * Snapshot of the execution outcome.
     *
     * @param status the outcome status
     * @param result the execution result (if any)
     * @param error the error message (if failed)
     * @param nextState the next state after execution
     */
    public record OutcomeSnapshot(
        OutcomeStatus status,
        Map<String, Object> result,
        String error,
        NextState nextState
    ) {
        public OutcomeSnapshot {
            result = result != null ? Map.copyOf(result) : Map.of();
        }

        public static OutcomeSnapshot executed(Map<String, Object> result, NextState nextState) {
            return new OutcomeSnapshot(OutcomeStatus.EXECUTED, result, null, nextState);
        }

        public static OutcomeSnapshot blocked(String reason) {
            return new OutcomeSnapshot(OutcomeStatus.BLOCKED, Map.of(), reason, null);
        }

        public static OutcomeSnapshot waiting() {
            return new OutcomeSnapshot(OutcomeStatus.WAITING, Map.of(), null, null);
        }

        public static OutcomeSnapshot failed(String error) {
            return new OutcomeSnapshot(OutcomeStatus.FAILED, Map.of(), error, null);
        }

        public record NextState(
            List<String> activeNodeIds,
            List<String> pendingEdgeIds,
            String instanceStatus
        ) {
            public NextState {
                activeNodeIds = activeNodeIds != null ? List.copyOf(activeNodeIds) : List.of();
                pendingEdgeIds = pendingEdgeIds != null ? List.copyOf(pendingEdgeIds) : List.of();
            }

            public static NextState from(ProcessInstance instance) {
                return new NextState(
                    instance.activeNodeIds().stream().map(Node.NodeId::value).toList(),
                    instance.pendingEdgeIds().stream()
                        .map(id -> id.value())
                        .toList(),
                    instance.status().name()
                );
            }
        }
    }

    /**
     * Outcome status of the decision.
     */
    public enum OutcomeStatus {
        EXECUTED,
        BLOCKED,
        WAITING,
        FAILED
    }

    /**
     * Builder for DecisionTrace.
     */
    public static class Builder {
        private DecisionTraceId id = DecisionTraceId.generate();
        private Instant timestamp = Instant.now();
        private ProcessInstance.ProcessInstanceId instanceId;
        private DecisionType type;
        private ContextSnapshot context = ContextSnapshot.empty();
        private EvaluationSnapshot evaluation = EvaluationSnapshot.empty();
        private DecisionSnapshot decision = DecisionSnapshot.empty();
        private GovernanceSnapshot governance = GovernanceSnapshot.skipped();
        private OutcomeSnapshot outcome = OutcomeSnapshot.waiting();

        public Builder id(DecisionTraceId id) {
            this.id = id;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder instanceId(ProcessInstance.ProcessInstanceId instanceId) {
            this.instanceId = instanceId;
            return this;
        }

        public Builder type(DecisionType type) {
            this.type = type;
            return this;
        }

        public Builder context(ContextSnapshot context) {
            this.context = context;
            return this;
        }

        public Builder context(RuntimeContext runtimeContext) {
            this.context = ContextSnapshot.from(runtimeContext);
            return this;
        }

        public Builder evaluation(EvaluationSnapshot evaluation) {
            this.evaluation = evaluation;
            return this;
        }

        public Builder decision(DecisionSnapshot decision) {
            this.decision = decision;
            return this;
        }

        public Builder decision(NavigationDecision navigationDecision) {
            this.decision = DecisionSnapshot.from(navigationDecision);
            return this;
        }

        public Builder governance(GovernanceSnapshot governance) {
            this.governance = governance;
            return this;
        }

        public Builder governance(GovernanceResult governanceResult) {
            this.governance = GovernanceSnapshot.from(governanceResult);
            return this;
        }

        public Builder outcome(OutcomeSnapshot outcome) {
            this.outcome = outcome;
            return this;
        }

        public DecisionTrace build() {
            return new DecisionTrace(
                id,
                timestamp,
                instanceId,
                type,
                context,
                evaluation,
                decision,
                governance,
                outcome
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
