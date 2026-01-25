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

import com.ihelio.cpg.domain.engine.EdgeEvaluation;
import com.ihelio.cpg.domain.engine.NodeEvaluation;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.orchestration.EligibleSpace;
import com.ihelio.cpg.domain.orchestration.NavigationDecision;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NavigationDeciderTest {

    private NavigationDecider decider;
    private ProcessGraph graph;
    private ProcessInstance instance;

    @BeforeEach
    void setUp() {
        decider = new NavigationDecider();
        graph = createGraph();
        instance = createInstance(graph);
    }

    @Test
    @DisplayName("Should return WAIT decision when eligible space is empty")
    void shouldReturnWaitWhenEligibleSpaceEmpty() {
        // Given
        EligibleSpace emptySpace = EligibleSpace.empty();

        // When
        NavigationDecision decision = decider.select(emptySpace, instance, graph);

        // Then
        assertTrue(decision.isWaiting());
        assertEquals(NavigationDecision.DecisionType.WAIT, decision.type());
        assertEquals(NavigationDecision.SelectionCriteria.NO_OPTIONS, decision.selectionCriteria());
    }

    @Test
    @DisplayName("Should select single available action")
    void shouldSelectSingleAvailableAction() {
        // Given
        Node node = createNode("node-1", "Node 1");
        NodeEvaluation nodeEval = NodeEvaluation.available(node, List.of(), List.of(), Map.of());
        EligibleSpace.CandidateAction action = EligibleSpace.CandidateAction.forEntryNode(nodeEval, 100);

        EligibleSpace space = EligibleSpace.builder()
            .eligibleNodes(List.of(nodeEval))
            .candidateActions(List.of(action))
            .build();

        // When
        NavigationDecision decision = decider.select(space, instance, graph);

        // Then
        assertTrue(decision.shouldProceed());
        assertEquals(NavigationDecision.DecisionType.PROCEED, decision.type());
        assertEquals(NavigationDecision.SelectionCriteria.SINGLE_OPTION, decision.selectionCriteria());
        assertEquals(1, decision.selectedNodes().size());
        assertEquals(node.id(), decision.primarySelection().nodeId());
    }

    @Test
    @DisplayName("Should select highest priority action when multiple available")
    void shouldSelectHighestPriorityAction() {
        // Given
        Node node1 = createNode("node-1", "Node 1");
        Node node2 = createNode("node-2", "Node 2");

        NodeEvaluation nodeEval1 = NodeEvaluation.available(node1, List.of(), List.of(), Map.of());
        NodeEvaluation nodeEval2 = NodeEvaluation.available(node2, List.of(), List.of(), Map.of());

        EligibleSpace.CandidateAction action1 = EligibleSpace.CandidateAction.forEntryNode(nodeEval1, 50);
        EligibleSpace.CandidateAction action2 = EligibleSpace.CandidateAction.forEntryNode(nodeEval2, 100);

        EligibleSpace space = EligibleSpace.builder()
            .eligibleNodes(List.of(nodeEval1, nodeEval2))
            .candidateActions(List.of(action1, action2))
            .build();

        // When
        NavigationDecision decision = decider.select(space, instance, graph);

        // Then
        assertTrue(decision.shouldProceed());
        assertEquals(NavigationDecision.SelectionCriteria.HIGHEST_PRIORITY, decision.selectionCriteria());
        assertEquals(node2.id(), decision.primarySelection().nodeId());
        assertEquals(2, decision.alternatives().size());
    }

    @Test
    @DisplayName("Should select exclusive action when available")
    void shouldSelectExclusiveAction() {
        // Given
        Node node1 = createNode("node-1", "Node 1");
        Node node2 = createNode("node-2", "Node 2");
        Edge exclusiveEdge = createEdge("edge-1", node1.id(), node2.id(), true);

        NodeEvaluation nodeEval1 = NodeEvaluation.available(node1, List.of(), List.of(), Map.of());
        NodeEvaluation nodeEval2 = NodeEvaluation.available(node2, List.of(), List.of(), Map.of());
        EdgeEvaluation edgeEval = EdgeEvaluation.traversable(exclusiveEdge);

        EligibleSpace.CandidateAction action1 = EligibleSpace.CandidateAction.forEntryNode(nodeEval1, 100);
        EligibleSpace.CandidateAction action2 = EligibleSpace.CandidateAction.forEdge(nodeEval2, edgeEval);

        EligibleSpace space = EligibleSpace.builder()
            .eligibleNodes(List.of(nodeEval1, nodeEval2))
            .traversableEdges(List.of(edgeEval))
            .candidateActions(List.of(action1, action2))
            .build();

        // When
        NavigationDecision decision = decider.select(space, instance, graph);

        // Then
        assertTrue(decision.shouldProceed());
        assertEquals(NavigationDecision.SelectionCriteria.EXCLUSIVE, decision.selectionCriteria());
        assertEquals(node2.id(), decision.primarySelection().nodeId());
    }

    @Test
    @DisplayName("Should record all alternatives considered")
    void shouldRecordAllAlternativesConsidered() {
        // Given
        Node node1 = createNode("node-1", "Node 1");
        Node node2 = createNode("node-2", "Node 2");
        Node node3 = createNode("node-3", "Node 3");

        NodeEvaluation nodeEval1 = NodeEvaluation.available(node1, List.of(), List.of(), Map.of());
        NodeEvaluation nodeEval2 = NodeEvaluation.available(node2, List.of(), List.of(), Map.of());
        NodeEvaluation nodeEval3 = NodeEvaluation.available(node3, List.of(), List.of(), Map.of());

        EligibleSpace.CandidateAction action1 = EligibleSpace.CandidateAction.forEntryNode(nodeEval1, 100);
        EligibleSpace.CandidateAction action2 = EligibleSpace.CandidateAction.forEntryNode(nodeEval2, 50);
        EligibleSpace.CandidateAction action3 = EligibleSpace.CandidateAction.forEntryNode(nodeEval3, 25);

        EligibleSpace space = EligibleSpace.builder()
            .eligibleNodes(List.of(nodeEval1, nodeEval2, nodeEval3))
            .candidateActions(List.of(action1, action2, action3))
            .build();

        // When
        NavigationDecision decision = decider.select(space, instance, graph);

        // Then
        assertEquals(3, decision.alternatives().size());

        // Verify selected alternative is marked
        long selectedCount = decision.alternatives().stream()
            .filter(NavigationDecision.AlternativeConsidered::selected)
            .count();
        assertEquals(1, selectedCount);
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

    private Edge createEdge(String id, Node.NodeId sourceId, Node.NodeId targetId, boolean exclusive) {
        return new Edge(
            new Edge.EdgeId(id),
            "Edge " + id,
            "Description",
            sourceId,
            targetId,
            Edge.GuardConditions.alwaysTrue(),
            Edge.ExecutionSemantics.sequential(),
            exclusive ? Edge.Priority.exclusive(1000) : Edge.Priority.defaults(),
            Edge.EventTriggers.none(),
            Edge.CompensationSemantics.none()
        );
    }

    private ProcessGraph createGraph() {
        return new ProcessGraph(
            new ProcessGraph.ProcessGraphId("test-graph"),
            "Test Graph",
            "Test graph for testing",
            1,
            ProcessGraph.ProcessGraphStatus.PUBLISHED,
            List.of(),
            List.of(),
            List.of(),
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
