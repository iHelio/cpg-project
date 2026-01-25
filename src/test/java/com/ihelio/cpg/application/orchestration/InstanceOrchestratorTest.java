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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.ihelio.cpg.application.orchestration.InstanceOrchestrator.OrchestrationResult;
import com.ihelio.cpg.domain.action.ActionResult;
import com.ihelio.cpg.domain.engine.NodeEvaluation;
import com.ihelio.cpg.domain.engine.NodeExecutionResult;
import com.ihelio.cpg.domain.engine.ProcessExecutionEngine;
import com.ihelio.cpg.domain.execution.ExecutionContext;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstanceOrchestratorTest {

    @Mock
    private ContextAssembler contextAssembler;

    @Mock
    private EligibilityEvaluator eligibilityEvaluator;

    @Mock
    private NodeSelector nodeSelector;

    @Mock
    private ExecutionGovernor executionGovernor;

    @Mock
    private ProcessExecutionEngine executionEngine;

    @Mock
    private DecisionTracer decisionTracer;

    private InstanceOrchestrator orchestrator;
    private ProcessGraph graph;
    private ProcessInstance instance;
    private Node entryNode;

    @BeforeEach
    void setUp() {
        orchestrator = new InstanceOrchestrator(
            contextAssembler,
            eligibilityEvaluator,
            nodeSelector,
            executionGovernor,
            executionEngine,
            decisionTracer
        );

        entryNode = createNode("entry-1", "Entry Node");
        graph = createGraph(List.of(entryNode), List.of(), List.of(entryNode.id()));
        instance = createInstance(graph);
    }

    @Test
    @DisplayName("Should execute full orchestration cycle successfully")
    void shouldExecuteFullOrchestrationCycle() {
        // Given
        RuntimeContext context = RuntimeContext.empty();
        when(contextAssembler.assemble(eq(instance), any())).thenReturn(context);

        NodeEvaluation nodeEval = NodeEvaluation.available(entryNode, List.of(), List.of(), Map.of());
        EligibleSpace.CandidateAction action = EligibleSpace.CandidateAction.forEntryNode(nodeEval, 100);
        EligibleSpace eligibleSpace = EligibleSpace.builder()
            .eligibleNodes(List.of(nodeEval))
            .candidateActions(List.of(action))
            .build();
        when(eligibilityEvaluator.evaluate(eq(instance), eq(graph), eq(context)))
            .thenReturn(eligibleSpace);

        NavigationDecision decision = NavigationDecision.proceed(
            List.of(new NavigationDecision.NodeSelection(entryNode, action, "Selected")),
            List.of(NavigationDecision.AlternativeConsidered.selected(action, "Highest priority")),
            NavigationDecision.SelectionCriteria.SINGLE_OPTION,
            "Single option available",
            eligibleSpace
        );
        when(nodeSelector.select(eq(eligibleSpace), eq(instance), eq(graph)))
            .thenReturn(decision);

        GovernanceResult governance = GovernanceResult.approved(
            GovernanceResult.IdempotencyResult.passed("key"),
            GovernanceResult.AuthorizationResult.skipped(),
            GovernanceResult.PolicyGateResult.skipped()
        );
        when(executionGovernor.enforce(eq(instance), eq(entryNode), eq(context)))
            .thenReturn(governance);

        NodeExecutionResult executionResult = NodeExecutionResult.success(
            entryNode,
            ActionResult.success(Map.of("result", "done")),
            instance.context()
        );
        when(executionEngine.executeNode(eq(instance), eq(graph), eq(entryNode)))
            .thenReturn(executionResult);

        lenient().when(contextAssembler.updateEntityState(any(), any(), any()))
            .thenReturn(context);

        // When
        OrchestrationResult result = orchestrator.orchestrate(instance, graph, null);

        // Then
        assertTrue(result.isExecuted());
        assertEquals(OrchestrationResult.OrchestrationStatus.EXECUTED, result.status());
        assertNotNull(result.trace());
        assertNotNull(result.governance());
        verify(decisionTracer).record(any(DecisionTrace.class));
        verify(executionGovernor).recordExecution(eq(instance), eq(entryNode), eq(context), any());
    }

    @Test
    @DisplayName("Should return WAITING when no eligible actions")
    void shouldReturnWaitingWhenNoEligibleActions() {
        // Given
        RuntimeContext context = RuntimeContext.empty();
        when(contextAssembler.assemble(eq(instance), any())).thenReturn(context);

        EligibleSpace emptySpace = EligibleSpace.empty();
        when(eligibilityEvaluator.evaluate(eq(instance), eq(graph), eq(context)))
            .thenReturn(emptySpace);

        NavigationDecision waitDecision = NavigationDecision.wait(
            "No eligible actions", emptySpace);
        when(nodeSelector.select(eq(emptySpace), eq(instance), eq(graph)))
            .thenReturn(waitDecision);

        // When
        OrchestrationResult result = orchestrator.orchestrate(instance, graph, null);

        // Then
        assertTrue(result.isWaiting());
        assertEquals(OrchestrationResult.OrchestrationStatus.WAITING, result.status());
        assertNotNull(result.trace());
        verify(decisionTracer).record(any(DecisionTrace.class));
        verifyNoInteractions(executionGovernor, executionEngine);
    }

    @Test
    @DisplayName("Should return BLOCKED when governance rejects")
    void shouldReturnBlockedWhenGovernanceRejects() {
        // Given
        RuntimeContext context = RuntimeContext.empty();
        when(contextAssembler.assemble(eq(instance), any())).thenReturn(context);

        NodeEvaluation nodeEval = NodeEvaluation.available(entryNode, List.of(), List.of(), Map.of());
        EligibleSpace.CandidateAction action = EligibleSpace.CandidateAction.forEntryNode(nodeEval, 100);
        EligibleSpace eligibleSpace = EligibleSpace.builder()
            .eligibleNodes(List.of(nodeEval))
            .candidateActions(List.of(action))
            .build();
        when(eligibilityEvaluator.evaluate(eq(instance), eq(graph), eq(context)))
            .thenReturn(eligibleSpace);

        NavigationDecision decision = NavigationDecision.proceed(
            List.of(new NavigationDecision.NodeSelection(entryNode, action, "Selected")),
            List.of(NavigationDecision.AlternativeConsidered.selected(action, "Highest priority")),
            NavigationDecision.SelectionCriteria.SINGLE_OPTION,
            "Single option available",
            eligibleSpace
        );
        when(nodeSelector.select(eq(eligibleSpace), eq(instance), eq(graph)))
            .thenReturn(decision);

        GovernanceResult rejected = GovernanceResult.rejected(
            GovernanceResult.IdempotencyResult.alreadyExecuted("key", "prev-exec-1"),
            GovernanceResult.AuthorizationResult.skipped(),
            GovernanceResult.PolicyGateResult.skipped()
        );
        when(executionGovernor.enforce(eq(instance), eq(entryNode), eq(context)))
            .thenReturn(rejected);

        // When
        OrchestrationResult result = orchestrator.orchestrate(instance, graph, null);

        // Then
        assertTrue(result.isBlocked());
        assertEquals(OrchestrationResult.OrchestrationStatus.BLOCKED, result.status());
        assertNotNull(result.message());
        verify(decisionTracer).record(any(DecisionTrace.class));
        verifyNoInteractions(executionEngine);
    }

    @Test
    @DisplayName("Should return FAILED when execution throws exception")
    void shouldReturnFailedWhenExecutionThrows() {
        // Given
        RuntimeContext context = RuntimeContext.empty();
        when(contextAssembler.assemble(eq(instance), any())).thenReturn(context);

        NodeEvaluation nodeEval = NodeEvaluation.available(entryNode, List.of(), List.of(), Map.of());
        EligibleSpace.CandidateAction action = EligibleSpace.CandidateAction.forEntryNode(nodeEval, 100);
        EligibleSpace eligibleSpace = EligibleSpace.builder()
            .eligibleNodes(List.of(nodeEval))
            .candidateActions(List.of(action))
            .build();
        when(eligibilityEvaluator.evaluate(eq(instance), eq(graph), eq(context)))
            .thenReturn(eligibleSpace);

        NavigationDecision decision = NavigationDecision.proceed(
            List.of(new NavigationDecision.NodeSelection(entryNode, action, "Selected")),
            List.of(NavigationDecision.AlternativeConsidered.selected(action, "Highest priority")),
            NavigationDecision.SelectionCriteria.SINGLE_OPTION,
            "Single option available",
            eligibleSpace
        );
        when(nodeSelector.select(eq(eligibleSpace), eq(instance), eq(graph)))
            .thenReturn(decision);

        GovernanceResult governance = GovernanceResult.approved(
            GovernanceResult.IdempotencyResult.passed("key"),
            GovernanceResult.AuthorizationResult.skipped(),
            GovernanceResult.PolicyGateResult.skipped()
        );
        when(executionGovernor.enforce(eq(instance), eq(entryNode), eq(context)))
            .thenReturn(governance);

        when(executionEngine.executeNode(eq(instance), eq(graph), eq(entryNode)))
            .thenThrow(new RuntimeException("External system unavailable"));

        // When
        OrchestrationResult result = orchestrator.orchestrate(instance, graph, null);

        // Then
        assertTrue(result.isFailed());
        assertEquals(OrchestrationResult.OrchestrationStatus.FAILED, result.status());
        assertTrue(result.message().contains("External system unavailable"));
        verify(decisionTracer).record(any(DecisionTrace.class));
    }

    @Test
    @DisplayName("Should orchestrate entry nodes for new process")
    void shouldOrchestrateEntryNodesForNewProcess() {
        // Given
        RuntimeContext context = RuntimeContext.empty();

        NodeEvaluation nodeEval = NodeEvaluation.available(entryNode, List.of(), List.of(), Map.of());
        EligibleSpace.CandidateAction action = EligibleSpace.CandidateAction.forEntryNode(nodeEval, 100);
        EligibleSpace eligibleSpace = EligibleSpace.builder()
            .eligibleNodes(List.of(nodeEval))
            .candidateActions(List.of(action))
            .build();
        when(eligibilityEvaluator.evaluateEntryNodes(eq(graph), eq(context)))
            .thenReturn(eligibleSpace);

        NavigationDecision decision = NavigationDecision.proceed(
            List.of(new NavigationDecision.NodeSelection(entryNode, action, "Entry node")),
            List.of(NavigationDecision.AlternativeConsidered.selected(action, "Entry node")),
            NavigationDecision.SelectionCriteria.SINGLE_OPTION,
            "Single entry node",
            eligibleSpace
        );
        when(nodeSelector.select(eq(eligibleSpace), eq(instance), eq(graph)))
            .thenReturn(decision);

        GovernanceResult governance = GovernanceResult.approved(
            GovernanceResult.IdempotencyResult.passed("key"),
            GovernanceResult.AuthorizationResult.skipped(),
            GovernanceResult.PolicyGateResult.skipped()
        );
        when(executionGovernor.enforce(eq(instance), eq(entryNode), eq(context)))
            .thenReturn(governance);

        NodeExecutionResult executionResult = NodeExecutionResult.success(
            entryNode,
            ActionResult.success(Map.of("started", true)),
            instance.context()
        );
        when(executionEngine.executeNode(eq(instance), eq(graph), eq(entryNode)))
            .thenReturn(executionResult);

        lenient().when(contextAssembler.updateEntityState(any(), any(), any()))
            .thenReturn(context);

        // When
        OrchestrationResult result = orchestrator.orchestrateEntry(instance, graph, context);

        // Then
        assertTrue(result.isExecuted());
        verify(eligibilityEvaluator).evaluateEntryNodes(graph, context);
        verify(decisionTracer).record(any(DecisionTrace.class));
    }

    @Test
    @DisplayName("Should return WAITING for entry when no eligible entry nodes")
    void shouldReturnWaitingForEntryWhenNoEligibleEntryNodes() {
        // Given
        RuntimeContext context = RuntimeContext.empty();

        EligibleSpace emptySpace = EligibleSpace.empty();
        when(eligibilityEvaluator.evaluateEntryNodes(eq(graph), eq(context)))
            .thenReturn(emptySpace);

        NavigationDecision waitDecision = NavigationDecision.wait(
            "No eligible entry nodes", emptySpace);
        when(nodeSelector.select(eq(emptySpace), eq(instance), eq(graph)))
            .thenReturn(waitDecision);

        // When
        OrchestrationResult result = orchestrator.orchestrateEntry(instance, graph, context);

        // Then
        assertTrue(result.isWaiting());
        verifyNoInteractions(executionGovernor, executionEngine);
    }

    @Test
    @DisplayName("Should reevaluate after event")
    void shouldReevaluateAfterEvent() {
        // Given
        RuntimeContext context = RuntimeContext.empty();

        EligibleSpace emptySpace = EligibleSpace.empty();
        when(eligibilityEvaluator.reevaluateAfterEvent(
            eq(instance), eq(graph), eq(context), eq("approval.received")))
            .thenReturn(emptySpace);

        NavigationDecision waitDecision = NavigationDecision.wait(
            "Still waiting after event", emptySpace);
        when(nodeSelector.select(eq(emptySpace), eq(instance), eq(graph)))
            .thenReturn(waitDecision);

        // When
        OrchestrationResult result = orchestrator.reevaluateAfterEvent(
            instance, graph, context, "approval.received");

        // Then
        assertTrue(result.isWaiting());
        verify(eligibilityEvaluator).reevaluateAfterEvent(
            instance, graph, context, "approval.received");
        verify(decisionTracer).record(any(DecisionTrace.class));
    }

    @Test
    @DisplayName("Should record decision trace for every orchestration outcome")
    void shouldRecordDecisionTraceForEveryOutcome() {
        // Given
        RuntimeContext context = RuntimeContext.empty();
        when(contextAssembler.assemble(eq(instance), any())).thenReturn(context);

        EligibleSpace emptySpace = EligibleSpace.empty();
        when(eligibilityEvaluator.evaluate(eq(instance), eq(graph), eq(context)))
            .thenReturn(emptySpace);

        NavigationDecision waitDecision = NavigationDecision.wait(
            "No options", emptySpace);
        when(nodeSelector.select(eq(emptySpace), eq(instance), eq(graph)))
            .thenReturn(waitDecision);

        // When
        OrchestrationResult result = orchestrator.orchestrate(instance, graph, null);

        // Then
        assertNotNull(result.trace());
        verify(decisionTracer, times(1)).record(any(DecisionTrace.class));
    }

    private Node createNode(String id, String name) {
        return new Node(
            new Node.NodeId(id),
            name,
            "Description for " + name,
            1,
            Node.Preconditions.none(),
            List.of(),
            List.of(),
            new Node.Action(Node.ActionType.SYSTEM_INVOCATION, "testHandler", "Test action",
                Node.ActionConfig.defaults()),
            Node.EventConfig.none(),
            Node.ExceptionRoutes.none()
        );
    }

    private ProcessGraph createGraph(List<Node> nodes, List<com.ihelio.cpg.domain.model.Edge> edges,
            List<Node.NodeId> entryNodeIds) {
        return new ProcessGraph(
            new ProcessGraph.ProcessGraphId("test-graph"),
            "Test Graph",
            "Test graph for testing",
            1,
            ProcessGraph.ProcessGraphStatus.PUBLISHED,
            nodes,
            edges,
            entryNodeIds,
            List.of(),
            new ProcessGraph.Metadata("test", java.time.Instant.now(), null, null, Map.of())
        );
    }

    private ProcessInstance createInstance(ProcessGraph graph) {
        return ProcessInstance.builder()
            .id("test-instance-1")
            .processGraphId(graph.id())
            .processGraphVersion(graph.version())
            .context(ExecutionContext.builder().build())
            .build();
    }
}
