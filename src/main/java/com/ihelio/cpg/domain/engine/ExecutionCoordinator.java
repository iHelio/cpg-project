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

import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Coordinates execution flow including parallel branches and joins.
 *
 * <p>Handles:
 * <ul>
 *   <li>Parallel branch creation and tracking</li>
 *   <li>Join synchronization (ALL, ANY, N_OF_M)</li>
 *   <li>Branch completion detection</li>
 * </ul>
 */
public class ExecutionCoordinator {

    /**
     * Record tracking parallel branch state.
     */
    public record ParallelBranch(
        String branchId,
        Edge.EdgeId originEdgeId,
        Node.NodeId currentNodeId,
        BranchStatus status
    ) {
        public ParallelBranch {
            Objects.requireNonNull(branchId, "ParallelBranch branchId is required");
            Objects.requireNonNull(originEdgeId, "ParallelBranch originEdgeId is required");
            Objects.requireNonNull(status, "ParallelBranch status is required");
        }
    }

    /**
     * Status of a parallel branch.
     */
    public enum BranchStatus {
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    /**
     * Result of checking join conditions.
     */
    public record JoinResult(
        boolean canProceed,
        List<ParallelBranch> completedBranches,
        List<ParallelBranch> pendingBranches
    ) {
        public JoinResult {
            completedBranches = completedBranches != null
                ? List.copyOf(completedBranches) : List.of();
            pendingBranches = pendingBranches != null
                ? List.copyOf(pendingBranches) : List.of();
        }
    }

    // Track parallel branches per process instance
    private final Map<String, List<ParallelBranch>> instanceBranches = new HashMap<>();

    /**
     * Initiates parallel execution for multiple edges.
     *
     * @param instance the process instance
     * @param edges the edges to execute in parallel
     * @return list of created parallel branches
     */
    public List<ParallelBranch> initiateParallelExecution(
            ProcessInstance instance,
            List<Edge> edges) {

        String instanceId = instance.id().value();
        List<ParallelBranch> branches = instanceBranches.computeIfAbsent(
            instanceId, k -> new ArrayList<>());

        List<ParallelBranch> newBranches = new ArrayList<>();
        int branchCounter = branches.size();

        for (Edge edge : edges) {
            ParallelBranch branch = new ParallelBranch(
                instanceId + "-branch-" + (++branchCounter),
                edge.id(),
                edge.targetNodeId(),
                BranchStatus.RUNNING
            );
            branches.add(branch);
            newBranches.add(branch);
        }

        return newBranches;
    }

    /**
     * Updates the status of a parallel branch.
     *
     * @param instance the process instance
     * @param branchId the branch ID
     * @param newNodeId the new current node (null if completed)
     * @param status the new branch status
     */
    public void updateBranch(
            ProcessInstance instance,
            String branchId,
            Node.NodeId newNodeId,
            BranchStatus status) {

        String instanceId = instance.id().value();
        List<ParallelBranch> branches = instanceBranches.get(instanceId);

        if (branches != null) {
            for (int i = 0; i < branches.size(); i++) {
                ParallelBranch branch = branches.get(i);
                if (branch.branchId().equals(branchId)) {
                    branches.set(i, new ParallelBranch(
                        branchId,
                        branch.originEdgeId(),
                        newNodeId,
                        status
                    ));
                    break;
                }
            }
        }
    }

    /**
     * Checks if join conditions are satisfied for a target node.
     *
     * @param instance the process instance
     * @param graph the process graph
     * @param targetNodeId the node to check join conditions for
     * @return the join result
     */
    public JoinResult checkJoinConditions(
            ProcessInstance instance,
            ProcessGraph graph,
            Node.NodeId targetNodeId) {

        // Find all inbound edges to the target node
        List<Edge> inboundEdges = graph.getInboundEdges(targetNodeId);

        if (inboundEdges.isEmpty()) {
            return new JoinResult(true, List.of(), List.of());
        }

        // Check if any inbound edge has parallel semantics with join
        Edge.JoinType joinType = null;
        for (Edge edge : inboundEdges) {
            if (edge.executionSemantics().type() == Edge.ExecutionType.PARALLEL) {
                joinType = edge.executionSemantics().joinType();
                break;
            }
        }

        if (joinType == null) {
            // No join required - sequential execution
            return new JoinResult(true, List.of(), List.of());
        }

        // Get branches for this instance
        String instanceId = instance.id().value();
        List<ParallelBranch> allBranches = instanceBranches.getOrDefault(
            instanceId, List.of());

        // Find branches relevant to this join point
        Set<Edge.EdgeId> inboundEdgeIds = new HashSet<>();
        for (Edge edge : inboundEdges) {
            inboundEdgeIds.add(edge.id());
        }

        List<ParallelBranch> relevantBranches = new ArrayList<>();
        for (ParallelBranch branch : allBranches) {
            if (inboundEdgeIds.contains(branch.originEdgeId())) {
                relevantBranches.add(branch);
            }
        }

        List<ParallelBranch> completed = new ArrayList<>();
        List<ParallelBranch> pending = new ArrayList<>();

        for (ParallelBranch branch : relevantBranches) {
            if (branch.status() == BranchStatus.COMPLETED) {
                completed.add(branch);
            } else if (branch.status() == BranchStatus.RUNNING) {
                pending.add(branch);
            }
        }

        boolean canProceed = evaluateJoinCondition(joinType, completed, relevantBranches);

        return new JoinResult(canProceed, completed, pending);
    }

    private boolean evaluateJoinCondition(
            Edge.JoinType joinType,
            List<ParallelBranch> completed,
            List<ParallelBranch> total) {

        return switch (joinType) {
            case ALL -> completed.size() == total.size();
            case ANY -> !completed.isEmpty();
            case N_OF_M -> completed.size() >= (total.size() / 2) + 1; // Majority
        };
    }

    /**
     * Determines which edges should be executed in parallel vs sequentially.
     *
     * @param edges the edges to categorize
     * @return categorization result
     */
    public ExecutionPlan categorizeEdges(List<Edge> edges) {
        List<Edge> sequential = new ArrayList<>();
        List<Edge> parallel = new ArrayList<>();

        for (Edge edge : edges) {
            if (edge.executionSemantics().type() == Edge.ExecutionType.PARALLEL) {
                parallel.add(edge);
            } else {
                sequential.add(edge);
            }
        }

        return new ExecutionPlan(sequential, parallel);
    }

    /**
     * Execution plan categorizing edges by execution type.
     */
    public record ExecutionPlan(
        List<Edge> sequential,
        List<Edge> parallel
    ) {
        public ExecutionPlan {
            sequential = sequential != null ? List.copyOf(sequential) : List.of();
            parallel = parallel != null ? List.copyOf(parallel) : List.of();
        }

        public boolean hasParallel() {
            return !parallel.isEmpty();
        }

        public boolean hasSequential() {
            return !sequential.isEmpty();
        }
    }

    /**
     * Cleans up branch tracking for a completed process instance.
     *
     * @param instance the completed process instance
     */
    public void cleanupInstance(ProcessInstance instance) {
        instanceBranches.remove(instance.id().value());
    }
}
