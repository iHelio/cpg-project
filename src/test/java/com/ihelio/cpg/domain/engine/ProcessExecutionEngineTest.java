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

package com.ihelio.cpg.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ihelio.cpg.domain.action.ActionContext;
import com.ihelio.cpg.domain.action.ActionHandler;
import com.ihelio.cpg.domain.action.ActionResult;
import com.ihelio.cpg.domain.event.ProcessEvent;
import com.ihelio.cpg.domain.event.ProcessEventPublisher;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessExecutionEngine")
class ProcessExecutionEngineTest {

    @Mock
    private NodeEvaluator nodeEvaluator;

    @Mock
    private EdgeEvaluator edgeEvaluator;

    @Mock
    private ExecutionCoordinator executionCoordinator;

    @Mock
    private CompensationHandler compensationHandler;

    @Mock
    private ProcessEventPublisher eventPublisher;

    @Mock
    private ActionHandler actionHandler;

    private ProcessExecutionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ProcessExecutionEngine(
            nodeEvaluator,
            edgeEvaluator,
            executionCoordinator,
            compensationHandler,
            eventPublisher,
            (type, handlerRef) -> actionHandler
        );
    }

    private Node createTestNode(String id) {
        return new Node(
            new Node.NodeId(id),
            "Test Node " + id,
            null,
            1,
            Node.Preconditions.none(),
            List.of(),
            List.of(),
            new Node.Action(
                Node.ActionType.SYSTEM_INVOCATION,
                "test-handler",
                null,
                Node.ActionConfig.defaults()
            ),
            Node.EventConfig.none(),
            Node.ExceptionRoutes.none()
        );
    }

    private ProcessGraph createTestGraph() {
        Node entryNode = createTestNode("entry-node");
        Node terminalNode = createTestNode("terminal-node");

        return new ProcessGraph(
            new ProcessGraph.ProcessGraphId("test-graph"),
            "Test Process",
            null,
            1,
            ProcessGraph.ProcessGraphStatus.PUBLISHED,
            List.of(entryNode, terminalNode),
            List.of(),
            List.of(new Node.NodeId("entry-node")),
            List.of(new Node.NodeId("terminal-node")),
            null
        );
    }

    @Nested
    @DisplayName("when starting a process")
    class StartProcess {

        @Test
        @DisplayName("should create process instance with initial context")
        void shouldCreateInstanceWithContext() {
            ProcessGraph graph = createTestGraph();
            ExecutionContext context = ExecutionContext.builder()
                .addDomainContext("employeeId", "emp-123")
                .build();

            ProcessInstance instance = engine.startProcess(graph, context);

            assertThat(instance).isNotNull();
            assertThat(instance.processGraphId()).isEqualTo(graph.id());
            assertThat(instance.processGraphVersion()).isEqualTo(graph.version());
            assertThat(instance.isRunning()).isTrue();
            assertThat(instance.context().domainContext()).containsEntry("employeeId", "emp-123");
        }

        @Test
        @DisplayName("should activate entry nodes")
        void shouldActivateEntryNodes() {
            ProcessGraph graph = createTestGraph();
            ExecutionContext context = ExecutionContext.builder().build();

            ProcessInstance instance = engine.startProcess(graph, context);

            assertThat(instance.activeNodeIds())
                .contains(new Node.NodeId("entry-node"));
        }
    }

    @Nested
    @DisplayName("when executing a node")
    class ExecuteNode {

        @Test
        @DisplayName("should return success when action completes")
        void shouldReturnSuccessWhenActionCompletes() {
            ProcessGraph graph = createTestGraph();
            ExecutionContext context = ExecutionContext.builder().build();
            ProcessInstance instance = engine.startProcess(graph, context);
            Node node = graph.findNode(new Node.NodeId("entry-node")).orElseThrow();

            NodeEvaluation available = NodeEvaluation.available(node, List.of(), List.of(), Map.of());
            when(nodeEvaluator.evaluate(any(), any())).thenReturn(available);
            when(actionHandler.execute(any())).thenReturn(ActionResult.success(Map.of("result", "done")));

            NodeExecutionResult result = engine.executeNode(instance, graph, node);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("result", "done");
        }

        @Test
        @DisplayName("should return skipped when node is blocked")
        void shouldReturnSkippedWhenNodeBlocked() {
            ProcessGraph graph = createTestGraph();
            ExecutionContext context = ExecutionContext.builder().build();
            ProcessInstance instance = engine.startProcess(graph, context);
            Node node = graph.findNode(new Node.NodeId("entry-node")).orElseThrow();

            NodeEvaluation blocked = NodeEvaluation.blockedByPreconditions(node, "Precondition not met");
            when(nodeEvaluator.evaluate(any(), any())).thenReturn(blocked);

            NodeExecutionResult result = engine.executeNode(instance, graph, node);

            assertThat(result.status()).isEqualTo(NodeExecutionResult.Status.SKIPPED);
            assertThat(result.error()).contains("Precondition");
        }

        @Test
        @DisplayName("should handle action failure with compensation")
        void shouldHandleActionFailureWithCompensation() {
            ProcessGraph graph = createTestGraph();
            ExecutionContext context = ExecutionContext.builder().build();
            ProcessInstance instance = engine.startProcess(graph, context);
            Node node = graph.findNode(new Node.NodeId("entry-node")).orElseThrow();

            NodeEvaluation available = NodeEvaluation.available(node, List.of(), List.of(), Map.of());
            when(nodeEvaluator.evaluate(any(), any())).thenReturn(available);
            when(actionHandler.execute(any())).thenReturn(ActionResult.failure("Connection failed", true));
            when(compensationHandler.determineCompensation(any(), any(), any(), any(), any()))
                .thenReturn(CompensationAction.retry(0, 3));

            NodeExecutionResult result = engine.executeNode(instance, graph, node);

            assertThat(result.isFailed()).isTrue();
            assertThat(result.compensationAction()).isNotNull();
            assertThat(result.compensationAction().action())
                .isEqualTo(CompensationAction.ActionType.RETRY);
        }
    }

    @Nested
    @DisplayName("when suspending and resuming")
    class SuspendResume {

        @Test
        @DisplayName("should suspend running process")
        void shouldSuspendRunningProcess() {
            ProcessGraph graph = createTestGraph();
            ProcessInstance instance = engine.startProcess(graph, ExecutionContext.builder().build());

            engine.suspendProcess(instance);

            assertThat(instance.status()).isEqualTo(ProcessInstance.ProcessInstanceStatus.SUSPENDED);
        }

        @Test
        @DisplayName("should resume suspended process")
        void shouldResumeSuspendedProcess() {
            ProcessGraph graph = createTestGraph();
            ProcessInstance instance = engine.startProcess(graph, ExecutionContext.builder().build());
            engine.suspendProcess(instance);

            engine.resumeProcess(instance, graph);

            assertThat(instance.status()).isEqualTo(ProcessInstance.ProcessInstanceStatus.RUNNING);
        }
    }
}
