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

package com.ihelio.cpg.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ihelio.cpg.domain.action.ActionResult;
import com.ihelio.cpg.domain.engine.EdgeTraversal;
import com.ihelio.cpg.domain.engine.NodeEvaluation;
import com.ihelio.cpg.domain.engine.NodeEvaluator;
import com.ihelio.cpg.domain.engine.NodeExecutionResult;
import com.ihelio.cpg.domain.engine.ProcessExecutionEngine;
import com.ihelio.cpg.domain.exception.ProcessExecutionException;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import com.ihelio.cpg.domain.repository.ProcessInstanceRepository;
import com.ihelio.cpg.interfaces.rest.dto.request.ExecuteNodeRequest;
import com.ihelio.cpg.interfaces.rest.dto.request.UpdateContextRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessExecutionService")
class ProcessExecutionServiceTest {

    @Mock
    private ProcessInstanceRepository processInstanceRepository;

    @Mock
    private ProcessGraphRepository processGraphRepository;

    @Mock
    private ProcessExecutionEngine processExecutionEngine;

    @Mock
    private NodeEvaluator nodeEvaluator;

    private ProcessExecutionService service;

    @BeforeEach
    void setUp() {
        service = new ProcessExecutionService(
            processInstanceRepository,
            processGraphRepository,
            processExecutionEngine,
            nodeEvaluator
        );
    }

    @Nested
    @DisplayName("executeNode")
    class ExecuteNode {

        @Test
        @DisplayName("should execute node successfully")
        void shouldExecuteNodeSuccessfully() {
            Node node = createTestNode();
            ProcessGraph graph = createTestGraphWithNode(node);
            ProcessInstance instance = createTestInstance(graph);
            NodeExecutionResult result = NodeExecutionResult.success(
                node, ActionResult.success(Map.of()), instance.context());

            when(processInstanceRepository.findById(any())).thenReturn(Optional.of(instance));
            when(processGraphRepository.findByIdAndVersion(any(), anyInt()))
                .thenReturn(Optional.of(graph));
            when(processExecutionEngine.executeNode(any(), any(), any())).thenReturn(result);

            ExecuteNodeRequest request = new ExecuteNodeRequest("node-1", Map.of());

            NodeExecutionResult execResult = service.executeNode("inst-123", request);

            assertThat(execResult.isSuccess()).isTrue();
            verify(processInstanceRepository).save(instance);
        }

        @Test
        @DisplayName("should throw exception when node not found")
        void shouldThrowExceptionWhenNodeNotFound() {
            ProcessGraph graph = new ProcessGraph(
                new ProcessGraph.ProcessGraphId("test-graph"),
                "Test",
                null,
                1,
                ProcessGraph.ProcessGraphStatus.PUBLISHED,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
            );
            ProcessInstance instance = createTestInstance(graph);

            when(processInstanceRepository.findById(any())).thenReturn(Optional.of(instance));
            when(processGraphRepository.findByIdAndVersion(any(), anyInt()))
                .thenReturn(Optional.of(graph));

            ExecuteNodeRequest request = new ExecuteNodeRequest("unknown-node", Map.of());

            assertThatThrownBy(() -> service.executeNode("inst-123", request))
                .isInstanceOf(ProcessExecutionException.class)
                .extracting("errorType")
                .isEqualTo(ProcessExecutionException.ErrorType.NODE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getAvailableNodes")
    class GetAvailableNodes {

        @Test
        @DisplayName("should return available nodes")
        void shouldReturnAvailableNodes() {
            Node node = createTestNode();
            ProcessGraph graph = createTestGraphWithNode(node);
            ProcessInstance instance = createTestInstance(graph);
            NodeEvaluation evaluation = NodeEvaluation.available(
                node, List.of(), List.of(), Map.of());

            when(processInstanceRepository.findById(any())).thenReturn(Optional.of(instance));
            when(processGraphRepository.findByIdAndVersion(any(), anyInt()))
                .thenReturn(Optional.of(graph));
            when(nodeEvaluator.evaluate(any(), any())).thenReturn(evaluation);

            List<Node> result = service.getAvailableNodes("inst-123");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should throw exception when instance not found")
        void shouldThrowExceptionWhenInstanceNotFound() {
            when(processInstanceRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getAvailableNodes("unknown"))
                .isInstanceOf(ProcessExecutionException.class)
                .extracting("errorType")
                .isEqualTo(ProcessExecutionException.ErrorType.INSTANCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getAvailableTransitions")
    class GetAvailableTransitions {

        @Test
        @DisplayName("should return available transitions")
        void shouldReturnAvailableTransitions() {
            Node node = createTestNode();
            ProcessGraph graph = createTestGraphWithNode(node);
            ProcessInstance instance = createTestInstance(graph);
            EdgeTraversal traversal = EdgeTraversal.of(
                createTestEdge(), node, node);

            when(processInstanceRepository.findById(any())).thenReturn(Optional.of(instance));
            when(processGraphRepository.findByIdAndVersion(any(), anyInt()))
                .thenReturn(Optional.of(graph));
            when(processExecutionEngine.evaluateAndTraverseEdges(any(), any(), any()))
                .thenReturn(List.of(traversal));

            List<EdgeTraversal> result = service.getAvailableTransitions("inst-123", "node-1");

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should throw exception when node not found")
        void shouldThrowExceptionWhenNodeNotFound() {
            ProcessGraph graph = new ProcessGraph(
                new ProcessGraph.ProcessGraphId("test-graph"),
                "Test",
                null,
                1,
                ProcessGraph.ProcessGraphStatus.PUBLISHED,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
            );
            ProcessInstance instance = createTestInstance(graph);

            when(processInstanceRepository.findById(any())).thenReturn(Optional.of(instance));
            when(processGraphRepository.findByIdAndVersion(any(), anyInt()))
                .thenReturn(Optional.of(graph));

            assertThatThrownBy(() -> service.getAvailableTransitions("inst-123", "unknown"))
                .isInstanceOf(ProcessExecutionException.class)
                .extracting("errorType")
                .isEqualTo(ProcessExecutionException.ErrorType.NODE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getContext")
    class GetContext {

        @Test
        @DisplayName("should return execution context")
        void shouldReturnExecutionContext() {
            ProcessInstance instance = createTestInstance(createTestGraph());
            when(processInstanceRepository.findById(any())).thenReturn(Optional.of(instance));

            ExecutionContext result = service.getContext("inst-123");

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("updateContext")
    class UpdateContext {

        @Test
        @DisplayName("should update context")
        void shouldUpdateContext() {
            ProcessInstance instance = createTestInstance(createTestGraph());
            when(processInstanceRepository.findById(any())).thenReturn(Optional.of(instance));

            UpdateContextRequest request = new UpdateContextRequest(
                Map.of("newClient", "value"),
                Map.of("newDomain", "value"),
                Map.of("newState", "value")
            );

            ExecutionContext result = service.updateContext("inst-123", request);

            assertThat(result).isNotNull();
            verify(processInstanceRepository).save(instance);
        }
    }

    private ProcessGraph createTestGraph() {
        return new ProcessGraph(
            new ProcessGraph.ProcessGraphId("test-graph"),
            "Test Graph",
            null,
            1,
            ProcessGraph.ProcessGraphStatus.PUBLISHED,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null
        );
    }

    private ProcessGraph createTestGraphWithNode(Node node) {
        return new ProcessGraph(
            new ProcessGraph.ProcessGraphId("test-graph"),
            "Test Graph",
            null,
            1,
            ProcessGraph.ProcessGraphStatus.PUBLISHED,
            List.of(node),
            List.of(),
            List.of(node.id()),
            List.of(),
            null
        );
    }

    private ProcessInstance createTestInstance(ProcessGraph graph) {
        return ProcessInstance.builder()
            .id("inst-123")
            .processGraphId(graph.id())
            .processGraphVersion(graph.version())
            .context(ExecutionContext.builder().build())
            .build();
    }

    private Node createTestNode() {
        return new Node(
            new Node.NodeId("node-1"),
            "Test Node",
            null,
            1,
            Node.Preconditions.none(),
            List.of(),
            List.of(),
            new Node.Action(Node.ActionType.SYSTEM_INVOCATION, "test-handler", "Test action", null),
            Node.EventConfig.none(),
            Node.ExceptionRoutes.none()
        );
    }

    private Edge createTestEdge() {
        return new Edge(
            new Edge.EdgeId("edge-1"),
            "Test Edge",
            null,
            new Node.NodeId("node-1"),
            new Node.NodeId("node-2"),
            Edge.GuardConditions.alwaysTrue(),
            Edge.ExecutionSemantics.sequential(),
            Edge.Priority.defaults(),
            Edge.EventTriggers.none(),
            Edge.CompensationSemantics.none()
        );
    }
}
