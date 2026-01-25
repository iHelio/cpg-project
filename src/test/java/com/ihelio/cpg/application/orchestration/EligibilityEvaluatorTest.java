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
import static org.mockito.Mockito.*;

import com.ihelio.cpg.domain.engine.EdgeEvaluation;
import com.ihelio.cpg.domain.engine.EdgeEvaluator;
import com.ihelio.cpg.domain.engine.NodeEvaluation;
import com.ihelio.cpg.domain.engine.NodeEvaluator;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.orchestration.EligibleSpace;
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
class EligibilityEvaluatorTest {

    @Mock
    private NodeEvaluator nodeEvaluator;

    @Mock
    private EdgeEvaluator edgeEvaluator;

    private EligibilityEvaluator eligibilityEvaluator;

    @BeforeEach
    void setUp() {
        eligibilityEvaluator = new EligibilityEvaluator(nodeEvaluator, edgeEvaluator);
    }

    @Test
    @DisplayName("Should evaluate entry nodes for new process")
    void shouldEvaluateEntryNodesForNewProcess() {
        // Given
        Node entryNode = createNode("entry-1", "Entry Node");
        ProcessGraph graph = createGraph(List.of(entryNode), List.of(), List.of(entryNode.id()));
        RuntimeContext context = RuntimeContext.empty();

        NodeEvaluation nodeEval = NodeEvaluation.available(
            entryNode, List.of(), List.of(), Map.of());
        when(nodeEvaluator.evaluate(eq(entryNode), any(ExecutionContext.class)))
            .thenReturn(nodeEval);

        // When
        EligibleSpace space = eligibilityEvaluator.evaluateEntryNodes(graph, context);

        // Then
        assertTrue(space.hasActions());
        assertEquals(1, space.eligibleNodes().size());
        assertEquals(1, space.candidateActions().size());
        assertTrue(space.candidateActions().get(0).isEntryNode());
    }

    @Test
    @DisplayName("Should return empty space when no eligible nodes")
    void shouldReturnEmptySpaceWhenNoEligibleNodes() {
        // Given
        Node entryNode = createNode("entry-1", "Entry Node");
        ProcessGraph graph = createGraph(List.of(entryNode), List.of(), List.of(entryNode.id()));
        RuntimeContext context = RuntimeContext.empty();

        NodeEvaluation blockedEval = NodeEvaluation.blockedByPreconditions(
            entryNode, "Preconditions not met");
        when(nodeEvaluator.evaluate(eq(entryNode), any(ExecutionContext.class)))
            .thenReturn(blockedEval);

        // When
        EligibleSpace space = eligibilityEvaluator.evaluateEntryNodes(graph, context);

        // Then
        assertFalse(space.hasActions());
        assertTrue(space.isEmpty());
        assertEquals(0, space.eligibleNodes().size());
    }

    @Test
    @DisplayName("Should evaluate edges for traversal")
    void shouldEvaluateEdgesForTraversal() {
        // Given
        Node node1 = createNode("node-1", "Node 1");
        Node node2 = createNode("node-2", "Node 2");
        Edge edge = createEdge("edge-1", node1.id(), node2.id());
        ProcessGraph graph = createGraph(
            List.of(node1, node2),
            List.of(edge),
            List.of(node1.id())
        );
        RuntimeContext context = RuntimeContext.empty();

        ProcessInstance instance = createInstance(graph);
        instance.startNodeExecution(node1.id());
        instance.completeNodeExecution(node1.id(), Map.of("result", "success"));

        NodeEvaluation node2Eval = NodeEvaluation.available(
            node2, List.of(), List.of(), Map.of());
        // The evaluator may evaluate multiple nodes; only node2 is a candidate here
        lenient().when(nodeEvaluator.evaluate(any(Node.class), any(ExecutionContext.class)))
            .thenReturn(node2Eval);

        EdgeEvaluation edgeEval = EdgeEvaluation.traversable(edge);
        lenient().when(edgeEvaluator.evaluate(any(Edge.class), any(ExecutionContext.class), any(), any()))
            .thenReturn(edgeEval);

        // When
        EligibleSpace space = eligibilityEvaluator.evaluate(instance, graph, context);

        // Then
        assertTrue(space.hasActions());
        assertEquals(1, space.traversableEdges().size());
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

    private Edge createEdge(String id, Node.NodeId sourceId, Node.NodeId targetId) {
        return new Edge(
            new Edge.EdgeId(id),
            "Edge " + id,
            "Description",
            sourceId,
            targetId,
            Edge.GuardConditions.alwaysTrue(),
            Edge.ExecutionSemantics.sequential(),
            Edge.Priority.defaults(),
            Edge.EventTriggers.none(),
            Edge.CompensationSemantics.none()
        );
    }

    private ProcessGraph createGraph(List<Node> nodes, List<Edge> edges,
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
