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
import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Node;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * EligibleSpace represents the set of eligible nodes and traversable edges at a point in time.
 *
 * <p>This is the cross-product of:
 * <ul>
 *   <li>Nodes whose preconditions, policies, and rules all pass</li>
 *   <li>Edges whose guard conditions are satisfied</li>
 * </ul>
 *
 * <p>The eligible space is used by the NavigationDecider to select the next best action(s).
 * It captures not just what is eligible, but the full evaluation details for each node and edge.
 *
 * @param eligibleNodes list of node evaluations that passed all checks
 * @param traversableEdges list of edge evaluations that can be traversed
 * @param candidateActions cross-product of eligible transitions with priorities
 * @param evaluatedAt timestamp when the space was evaluated
 */
public record EligibleSpace(
    List<NodeEvaluation> eligibleNodes,
    List<EdgeEvaluation> traversableEdges,
    List<CandidateAction> candidateActions,
    Instant evaluatedAt
) {

    public EligibleSpace {
        Objects.requireNonNull(evaluatedAt, "EligibleSpace evaluatedAt is required");
        eligibleNodes = eligibleNodes != null ? List.copyOf(eligibleNodes) : List.of();
        traversableEdges = traversableEdges != null ? List.copyOf(traversableEdges) : List.of();
        candidateActions = candidateActions != null ? List.copyOf(candidateActions) : List.of();
    }

    /**
     * Creates an empty eligible space indicating no actions are available.
     */
    public static EligibleSpace empty() {
        return new EligibleSpace(List.of(), List.of(), List.of(), Instant.now());
    }

    /**
     * Checks if there are any actions available.
     *
     * @return true if at least one action is eligible
     */
    public boolean hasActions() {
        return !candidateActions.isEmpty();
    }

    /**
     * Checks if the space is empty (no eligible actions).
     *
     * @return true if no actions are available
     */
    public boolean isEmpty() {
        return candidateActions.isEmpty();
    }

    /**
     * Returns the highest priority action if available.
     *
     * @return the highest priority action, or empty if no actions
     */
    public Optional<CandidateAction> highestPriorityAction() {
        return candidateActions.stream()
            .max(Comparator.comparingInt(CandidateAction::effectivePriority));
    }

    /**
     * Returns all actions with exclusive flag set.
     *
     * @return list of exclusive actions
     */
    public List<CandidateAction> exclusiveActions() {
        return candidateActions.stream()
            .filter(CandidateAction::isExclusive)
            .toList();
    }

    /**
     * Returns actions that can be executed in parallel.
     *
     * @return list of parallel-eligible actions
     */
    public List<CandidateAction> parallelActions() {
        return candidateActions.stream()
            .filter(a -> !a.isExclusive() && a.allowsParallel())
            .toList();
    }

    /**
     * Finds a node evaluation by node ID.
     *
     * @param nodeId the node ID to find
     * @return the node evaluation, or empty if not found
     */
    public Optional<NodeEvaluation> findNodeEvaluation(Node.NodeId nodeId) {
        return eligibleNodes.stream()
            .filter(ne -> ne.node().id().equals(nodeId))
            .findFirst();
    }

    /**
     * Finds an edge evaluation by edge ID.
     *
     * @param edgeId the edge ID to find
     * @return the edge evaluation, or empty if not found
     */
    public Optional<EdgeEvaluation> findEdgeEvaluation(Edge.EdgeId edgeId) {
        return traversableEdges.stream()
            .filter(ee -> ee.edge().id().equals(edgeId))
            .findFirst();
    }

    /**
     * A candidate action representing an eligible node+edge combination.
     *
     * @param node the target node to execute
     * @param edge the edge leading to this node (null for entry nodes)
     * @param nodeEvaluation the full node evaluation result
     * @param edgeEvaluation the full edge evaluation result (null for entry nodes)
     * @param effectivePriority computed priority for selection
     */
    public record CandidateAction(
        Node node,
        Edge edge,
        NodeEvaluation nodeEvaluation,
        EdgeEvaluation edgeEvaluation,
        int effectivePriority
    ) {
        public CandidateAction {
            Objects.requireNonNull(node, "CandidateAction node is required");
            Objects.requireNonNull(nodeEvaluation, "CandidateAction nodeEvaluation is required");
        }

        /**
         * Creates a candidate action for an entry node (no inbound edge).
         *
         * @param nodeEvaluation the node evaluation
         * @param priority the priority
         * @return a new candidate action
         */
        public static CandidateAction forEntryNode(NodeEvaluation nodeEvaluation, int priority) {
            return new CandidateAction(
                nodeEvaluation.node(),
                null,
                nodeEvaluation,
                null,
                priority
            );
        }

        /**
         * Creates a candidate action for an edge traversal.
         *
         * @param nodeEvaluation the target node evaluation
         * @param edgeEvaluation the edge evaluation
         * @return a new candidate action
         */
        public static CandidateAction forEdge(NodeEvaluation nodeEvaluation, EdgeEvaluation edgeEvaluation) {
            int priority = edgeEvaluation.edge().priority().weight();
            return new CandidateAction(
                nodeEvaluation.node(),
                edgeEvaluation.edge(),
                nodeEvaluation,
                edgeEvaluation,
                priority
            );
        }

        /**
         * Checks if this is an entry node action (no inbound edge).
         *
         * @return true if this is an entry node action
         */
        public boolean isEntryNode() {
            return edge == null;
        }

        /**
         * Checks if this action is exclusive (should be taken alone).
         *
         * @return true if the edge is exclusive
         */
        public boolean isExclusive() {
            return edge != null && edge.priority().exclusive();
        }

        /**
         * Checks if this action allows parallel execution.
         *
         * @return true if parallel execution is allowed
         */
        public boolean allowsParallel() {
            return edge != null &&
                edge.executionSemantics().type() == Edge.ExecutionType.PARALLEL;
        }

        /**
         * Returns the node ID for this action.
         *
         * @return the node ID
         */
        public Node.NodeId nodeId() {
            return node.id();
        }

        /**
         * Returns the edge ID for this action, or null if entry node.
         *
         * @return the edge ID or null
         */
        public Edge.EdgeId edgeId() {
            return edge != null ? edge.id() : null;
        }
    }

    /**
     * Builder for EligibleSpace.
     */
    public static class Builder {
        private List<NodeEvaluation> eligibleNodes = new java.util.ArrayList<>();
        private List<EdgeEvaluation> traversableEdges = new java.util.ArrayList<>();
        private List<CandidateAction> candidateActions = new java.util.ArrayList<>();

        public Builder eligibleNodes(List<NodeEvaluation> eligibleNodes) {
            this.eligibleNodes = new java.util.ArrayList<>(eligibleNodes);
            return this;
        }

        public Builder traversableEdges(List<EdgeEvaluation> traversableEdges) {
            this.traversableEdges = new java.util.ArrayList<>(traversableEdges);
            return this;
        }

        public Builder candidateActions(List<CandidateAction> candidateActions) {
            this.candidateActions = new java.util.ArrayList<>(candidateActions);
            return this;
        }

        public Builder addEligibleNode(NodeEvaluation nodeEvaluation) {
            this.eligibleNodes.add(nodeEvaluation);
            return this;
        }

        public Builder addTraversableEdge(EdgeEvaluation edgeEvaluation) {
            this.traversableEdges.add(edgeEvaluation);
            return this;
        }

        public Builder addCandidateAction(CandidateAction candidateAction) {
            this.candidateActions.add(candidateAction);
            return this;
        }

        public EligibleSpace build() {
            return new EligibleSpace(
                eligibleNodes,
                traversableEdges,
                candidateActions,
                Instant.now()
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
