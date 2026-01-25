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

package com.ihelio.cpg.infrastructure.orchestration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.ihelio.cpg.application.orchestration.ContextAssembler;
import com.ihelio.cpg.application.orchestration.InstanceOrchestrator;
import com.ihelio.cpg.domain.engine.NodeEvaluation;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.orchestration.DecisionTrace;
import com.ihelio.cpg.domain.orchestration.DecisionTracer;
import com.ihelio.cpg.domain.orchestration.EligibleSpace;
import com.ihelio.cpg.domain.orchestration.GovernanceResult;
import com.ihelio.cpg.domain.orchestration.NavigationDecision;
import com.ihelio.cpg.domain.orchestration.OrchestrationEvent;
import com.ihelio.cpg.domain.orchestration.ProcessOrchestrator;
import com.ihelio.cpg.domain.orchestration.RuntimeContext;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import com.ihelio.cpg.domain.repository.ProcessInstanceRepository;
import com.ihelio.cpg.infrastructure.persistence.InMemoryDecisionTraceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrchestratorIntegrationTest {

    @Mock
    private InstanceOrchestrator instanceOrchestrator;

    @Mock
    private ContextAssembler contextAssembler;

    @Mock
    private ProcessGraphRepository graphRepository;

    @Mock
    private ProcessInstanceRepository instanceRepository;

    private DefaultDecisionTracer decisionTracer;
    private InMemoryDecisionTraceRepository traceRepository;
    private DefaultProcessOrchestrator orchestrator;
    private ProcessGraph graph;

    @BeforeEach
    void setUp() {
        traceRepository = new InMemoryDecisionTraceRepository();
        OrchestratorConfigProperties config = OrchestratorConfigProperties.forTesting();
        decisionTracer = new DefaultDecisionTracer(traceRepository, config);

        orchestrator = new DefaultProcessOrchestrator(
            instanceOrchestrator,
            contextAssembler,
            graphRepository,
            instanceRepository,
            decisionTracer,
            config
        );

        graph = createGraph();
    }

    @AfterEach
    void tearDown() {
        orchestrator.stopEventLoop();
    }

    @Test
    @DisplayName("Should start a process instance and return it")
    void shouldStartProcessInstance() {
        // Given
        RuntimeContext initialContext = RuntimeContext.empty();

        Node entryNode = createNode("entry-1", "Entry Node");
        ProcessGraph graphWithEntry = createGraph(
            List.of(entryNode), List.of(), List.of(entryNode.id()));

        NodeEvaluation nodeEval = NodeEvaluation.available(entryNode, List.of(), List.of(), Map.of());
        EligibleSpace.CandidateAction action = EligibleSpace.CandidateAction.forEntryNode(nodeEval, 100);

        NavigationDecision decision = NavigationDecision.proceed(
            List.of(new NavigationDecision.NodeSelection(entryNode, action, "Entry")),
            List.of(NavigationDecision.AlternativeConsidered.selected(action, "Entry")),
            NavigationDecision.SelectionCriteria.SINGLE_OPTION,
            "Single entry node",
            EligibleSpace.empty()
        );

        DecisionTrace trace = DecisionTrace.builder()
            .instanceId(new ProcessInstance.ProcessInstanceId("temp"))
            .type(DecisionTrace.DecisionType.EXECUTION)
            .build();

        GovernanceResult governance = GovernanceResult.approved(
            GovernanceResult.IdempotencyResult.skipped(),
            GovernanceResult.AuthorizationResult.skipped(),
            GovernanceResult.PolicyGateResult.skipped()
        );

        InstanceOrchestrator.OrchestrationResult orchestrationResult =
            InstanceOrchestrator.OrchestrationResult.executed(
                createInstance(graphWithEntry), decision, trace, governance);

        when(instanceOrchestrator.orchestrateEntry(any(), eq(graphWithEntry), eq(initialContext)))
            .thenReturn(orchestrationResult);

        // When
        ProcessInstance result = orchestrator.start(graphWithEntry, initialContext);

        // Then
        assertNotNull(result);
        verify(instanceRepository, times(2)).save(any(ProcessInstance.class));
        verify(instanceOrchestrator).orchestrateEntry(any(), eq(graphWithEntry), eq(initialContext));
    }

    @Test
    @DisplayName("Should suspend a running process instance")
    void shouldSuspendRunningInstance() {
        // Given
        ProcessInstance instance = createInstance(graph);
        ProcessInstance.ProcessInstanceId instanceId = instance.id();
        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));

        // When
        orchestrator.suspend(instanceId);

        // Then
        verify(instanceRepository).save(any(ProcessInstance.class));
        assertEquals(ProcessInstance.ProcessInstanceStatus.SUSPENDED, instance.status());
    }

    @Test
    @DisplayName("Should resume a suspended process instance")
    void shouldResumeSuspendedInstance() {
        // Given
        ProcessInstance instance = createInstance(graph);
        instance.suspend();
        ProcessInstance.ProcessInstanceId instanceId = instance.id();

        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));
        when(graphRepository.findById(instance.processGraphId())).thenReturn(Optional.of(graph));

        RuntimeContext context = RuntimeContext.empty();
        when(contextAssembler.assemble(any(), any())).thenReturn(context);

        InstanceOrchestrator.OrchestrationResult orchestrationResult =
            InstanceOrchestrator.OrchestrationResult.waiting(
                instance,
                NavigationDecision.wait("Waiting", EligibleSpace.empty()),
                DecisionTrace.builder()
                    .instanceId(instanceId)
                    .type(DecisionTrace.DecisionType.WAIT)
                    .build()
            );
        when(instanceOrchestrator.orchestrate(any(), eq(graph), any()))
            .thenReturn(orchestrationResult);

        // When
        orchestrator.resume(instanceId);

        // Then
        verify(instanceOrchestrator).orchestrate(any(), eq(graph), any());
        verify(instanceRepository, atLeastOnce()).save(any(ProcessInstance.class));
    }

    @Test
    @DisplayName("Should cancel a running process instance")
    void shouldCancelRunningInstance() {
        // Given
        ProcessInstance instance = createInstance(graph);
        ProcessInstance.ProcessInstanceId instanceId = instance.id();
        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));

        // When
        orchestrator.cancel(instanceId);

        // Then
        verify(instanceRepository).save(any(ProcessInstance.class));
        assertEquals(ProcessInstance.ProcessInstanceStatus.FAILED, instance.status());
    }

    @Test
    @DisplayName("Should return null status for unknown instance")
    void shouldReturnNullStatusForUnknownInstance() {
        // Given
        ProcessInstance.ProcessInstanceId unknownId =
            new ProcessInstance.ProcessInstanceId("unknown-instance");
        when(instanceRepository.findById(unknownId)).thenReturn(Optional.empty());

        // When
        ProcessOrchestrator.OrchestrationStatus status = orchestrator.getStatus(unknownId);

        // Then
        assertNull(status);
    }

    @Test
    @DisplayName("Should get status from repository when not cached")
    void shouldGetStatusFromRepositoryWhenNotCached() {
        // Given
        ProcessInstance instance = createInstance(graph);
        ProcessInstance.ProcessInstanceId instanceId = instance.id();
        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));

        // When
        ProcessOrchestrator.OrchestrationStatus status = orchestrator.getStatus(instanceId);

        // Then
        assertNotNull(status);
        assertEquals(instance, status.instance());
        assertTrue(status.isActive());
    }

    @Test
    @DisplayName("Should signal event to orchestrator without error")
    void shouldSignalEventWithoutError() {
        // Given
        OrchestrationEvent event = OrchestrationEvent.NodeCompleted.of(
            new ProcessInstance.ProcessInstanceId("test-instance"),
            new Node.NodeId("node-1"),
            Map.of("result", "success"),
            100
        );

        // When / Then - should not throw
        assertDoesNotThrow(() -> orchestrator.signal(event));
    }

    @Test
    @DisplayName("Should handle event loop start and stop")
    void shouldHandleEventLoopStartAndStop() {
        // When / Then - should not throw
        assertDoesNotThrow(() -> {
            orchestrator.startEventLoop();
            orchestrator.stopEventLoop();
        });
    }

    @Test
    @DisplayName("Should accept events without error when event loop is running")
    void shouldAcceptEventsWhenEventLoopIsRunning() {
        // Given
        orchestrator.startEventLoop();

        OrchestrationEvent event = OrchestrationEvent.NodeCompleted.of(
            new ProcessInstance.ProcessInstanceId("test-instance-1"),
            new Node.NodeId("node-1"),
            Map.of("result", "success"),
            100
        );

        // When / Then - signaling events while loop is running should not throw
        assertDoesNotThrow(() -> orchestrator.signal(event));
    }

    @Test
    @DisplayName("Should return started instance from orchestration result")
    void shouldReturnStartedInstanceFromOrchestrationResult() {
        // Given
        RuntimeContext initialContext = RuntimeContext.empty();
        ProcessInstance instance = createInstance(graph);

        NavigationDecision decision = NavigationDecision.wait("Waiting", EligibleSpace.empty());
        DecisionTrace trace = DecisionTrace.builder()
            .instanceId(instance.id())
            .type(DecisionTrace.DecisionType.WAIT)
            .build();

        InstanceOrchestrator.OrchestrationResult orchestrationResult =
            InstanceOrchestrator.OrchestrationResult.waiting(instance, decision, trace);

        when(instanceOrchestrator.orchestrateEntry(any(), eq(graph), eq(initialContext)))
            .thenReturn(orchestrationResult);

        // When
        ProcessInstance started = orchestrator.start(graph, initialContext);

        // Then - the returned instance comes from the orchestration result
        assertNotNull(started);
        assertEquals(instance.id(), started.id());
        // Verify both the initial and updated instances were saved
        verify(instanceRepository, times(2)).save(any(ProcessInstance.class));
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

    private ProcessGraph createGraph() {
        return createGraph(List.of(), List.of(), List.of());
    }

    private ProcessGraph createGraph(List<Node> nodes,
            List<com.ihelio.cpg.domain.model.Edge> edges, List<Node.NodeId> entryNodeIds) {
        return new ProcessGraph(
            new ProcessGraph.ProcessGraphId("test-graph"),
            "Test Graph",
            "Test graph for integration testing",
            1,
            ProcessGraph.ProcessGraphStatus.PUBLISHED,
            nodes,
            edges,
            entryNodeIds,
            List.of(),
            new ProcessGraph.Metadata("test", Instant.now(), null, null, Map.of())
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
