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

import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.ProcessGraph;

/**
 * NodeSelector is the port for deterministic selection from the eligible space.
 *
 * <p>Given an eligible space (the set of eligible nodes and traversable edges),
 * the node selector determines which action(s) to execute next based on:
 * <ul>
 *   <li><b>Priority</b>: Explicit priority weights and ranks</li>
 *   <li><b>Dependencies</b>: Required ordering between nodes</li>
 *   <li><b>Concurrency</b>: Parallel vs sequential execution semantics</li>
 *   <li><b>Exclusivity</b>: Exclusive edges that preempt all others</li>
 * </ul>
 *
 * <p>The selection is deterministic - given the same inputs, it will always
 * produce the same output. All alternatives considered are recorded in the
 * resulting NavigationDecision for audit purposes.
 */
public interface NodeSelector {

    /**
     * Selects the next action(s) from the eligible space.
     *
     * <p>The selection algorithm:
     * <ol>
     *   <li>Check for exclusive edges - if found, select only that one</li>
     *   <li>Apply dependency constraints (A must execute before B)</li>
     *   <li>Sort by priority (weight descending, then rank ascending)</li>
     *   <li>Determine parallel vs sequential based on execution semantics</li>
     *   <li>Record all alternatives with selection reasoning</li>
     * </ol>
     *
     * @param eligibleSpace the eligible space to select from
     * @param instance the current process instance state
     * @param graph the process graph (for dependency analysis)
     * @return the navigation decision with selected actions and alternatives
     */
    NavigationDecision select(
        EligibleSpace eligibleSpace,
        ProcessInstance instance,
        ProcessGraph graph
    );

    /**
     * Selects the next action(s) with dependency constraints.
     *
     * <p>Allows specifying explicit dependencies that must be respected
     * in addition to implicit dependencies from the graph structure.
     *
     * @param eligibleSpace the eligible space to select from
     * @param instance the current process instance state
     * @param graph the process graph
     * @param dependencies explicit dependency constraints
     * @return the navigation decision
     */
    NavigationDecision select(
        EligibleSpace eligibleSpace,
        ProcessInstance instance,
        ProcessGraph graph,
        DependencyConstraints dependencies
    );

    /**
     * Represents explicit dependency constraints for selection.
     *
     * @param mustExecuteBefore map of nodeId to list of nodes that must execute before it
     * @param mustNotParallel pairs of nodes that cannot execute in parallel
     */
    record DependencyConstraints(
        java.util.Map<String, java.util.List<String>> mustExecuteBefore,
        java.util.List<NodePair> mustNotParallel
    ) {
        public DependencyConstraints {
            mustExecuteBefore = mustExecuteBefore != null
                ? java.util.Map.copyOf(mustExecuteBefore) : java.util.Map.of();
            mustNotParallel = mustNotParallel != null
                ? java.util.List.copyOf(mustNotParallel) : java.util.List.of();
        }

        public static DependencyConstraints none() {
            return new DependencyConstraints(java.util.Map.of(), java.util.List.of());
        }

        public record NodePair(String nodeId1, String nodeId2) {}
    }
}
