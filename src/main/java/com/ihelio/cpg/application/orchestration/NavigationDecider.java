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

import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.orchestration.EligibleSpace;
import com.ihelio.cpg.domain.orchestration.NavigationDecision;
import com.ihelio.cpg.domain.orchestration.NodeSelector;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * NavigationDecider implements the NodeSelector port for deterministic action selection.
 *
 * <p>The selection algorithm:
 * <ol>
 *   <li>Check for exclusive actions - if found, select only that one</li>
 *   <li>Apply dependency constraints (A must execute before B)</li>
 *   <li>Sort by priority (weight descending, then rank ascending)</li>
 *   <li>Determine parallel vs sequential based on execution semantics</li>
 *   <li>Record all alternatives with selection reasoning</li>
 * </ol>
 *
 * <p>All alternatives are recorded in the resulting NavigationDecision for audit.
 */
public class NavigationDecider implements NodeSelector {

    /**
     * Creates a NavigationDecider.
     */
    public NavigationDecider() {
    }

    @Override
    public NavigationDecision select(
            EligibleSpace eligibleSpace,
            ProcessInstance instance,
            ProcessGraph graph) {
        return select(eligibleSpace, instance, graph, DependencyConstraints.none());
    }

    @Override
    public NavigationDecision select(
            EligibleSpace eligibleSpace,
            ProcessInstance instance,
            ProcessGraph graph,
            DependencyConstraints dependencies) {
        Objects.requireNonNull(eligibleSpace, "eligibleSpace is required");
        Objects.requireNonNull(instance, "instance is required");
        Objects.requireNonNull(graph, "graph is required");
        Objects.requireNonNull(dependencies, "dependencies is required");

        // No actions available - wait
        if (eligibleSpace.isEmpty()) {
            return handleNoActions(eligibleSpace, instance, graph);
        }

        List<EligibleSpace.CandidateAction> candidates = new ArrayList<>(
            eligibleSpace.candidateActions());

        // Sort by priority
        candidates.sort(Comparator
            .comparingInt(EligibleSpace.CandidateAction::effectivePriority)
            .reversed());

        // Check for exclusive actions
        List<EligibleSpace.CandidateAction> exclusiveActions = candidates.stream()
            .filter(EligibleSpace.CandidateAction::isExclusive)
            .toList();

        if (!exclusiveActions.isEmpty()) {
            return handleExclusiveAction(exclusiveActions.get(0), candidates, eligibleSpace);
        }

        // Apply dependency constraints
        candidates = applyDependencyConstraints(candidates, instance, graph, dependencies);

        // If single action, select it
        if (candidates.size() == 1) {
            return handleSingleAction(candidates.get(0), eligibleSpace.candidateActions(),
                eligibleSpace);
        }

        // Check for parallel execution
        List<EligibleSpace.CandidateAction> parallelCandidates = candidates.stream()
            .filter(EligibleSpace.CandidateAction::allowsParallel)
            .toList();

        if (parallelCandidates.size() > 1) {
            return handleParallelActions(parallelCandidates, candidates, eligibleSpace);
        }

        // Select highest priority
        return handleHighestPriority(candidates.get(0), candidates, eligibleSpace);
    }

    private NavigationDecision handleNoActions(EligibleSpace eligibleSpace,
            ProcessInstance instance, ProcessGraph graph) {
        // Check if process is complete
        Set<Node.NodeId> completedNodes = instance.nodeExecutions().stream()
            .filter(ne -> ne.status() == ProcessInstance.NodeExecutionStatus.COMPLETED)
            .map(ProcessInstance.NodeExecution::nodeId)
            .collect(java.util.stream.Collectors.toSet());

        boolean allTerminalNodesReached = !graph.terminalNodeIds().isEmpty() &&
            graph.terminalNodeIds().stream().anyMatch(completedNodes::contains);

        if (allTerminalNodesReached) {
            return NavigationDecision.complete("All terminal nodes reached");
        }

        return NavigationDecision.wait(
            "No eligible actions available - waiting for events",
            eligibleSpace
        );
    }

    private NavigationDecision handleExclusiveAction(
            EligibleSpace.CandidateAction exclusive,
            List<EligibleSpace.CandidateAction> allCandidates,
            EligibleSpace eligibleSpace) {

        List<NavigationDecision.AlternativeConsidered> alternatives = new ArrayList<>();

        // Record all alternatives
        for (EligibleSpace.CandidateAction candidate : allCandidates) {
            if (candidate == exclusive) {
                alternatives.add(NavigationDecision.AlternativeConsidered.selected(
                    candidate, "Selected: exclusive edge takes precedence"));
            } else {
                alternatives.add(NavigationDecision.AlternativeConsidered.rejected(
                    candidate, "Rejected: preempted by exclusive edge"));
            }
        }

        NavigationDecision.NodeSelection selection = new NavigationDecision.NodeSelection(
            exclusive.node(),
            exclusive,
            "Exclusive edge selected"
        );

        return NavigationDecision.proceed(
            List.of(selection),
            alternatives,
            NavigationDecision.SelectionCriteria.EXCLUSIVE,
            "Selected exclusive edge: " + (exclusive.edge() != null
                ? exclusive.edge().id().value() : "entry"),
            eligibleSpace
        );
    }

    private NavigationDecision handleSingleAction(
            EligibleSpace.CandidateAction action,
            List<EligibleSpace.CandidateAction> allCandidates,
            EligibleSpace eligibleSpace) {

        List<NavigationDecision.AlternativeConsidered> alternatives = allCandidates.stream()
            .map(c -> c == action
                ? NavigationDecision.AlternativeConsidered.selected(c, "Only eligible action")
                : NavigationDecision.AlternativeConsidered.rejected(c, "Filtered by constraints"))
            .toList();

        NavigationDecision.NodeSelection selection = new NavigationDecision.NodeSelection(
            action.node(),
            action,
            "Single eligible action"
        );

        return NavigationDecision.proceed(
            List.of(selection),
            alternatives,
            NavigationDecision.SelectionCriteria.SINGLE_OPTION,
            "Selected single eligible action: " + action.nodeId().value(),
            eligibleSpace
        );
    }

    private NavigationDecision handleParallelActions(
            List<EligibleSpace.CandidateAction> parallelActions,
            List<EligibleSpace.CandidateAction> allCandidates,
            EligibleSpace eligibleSpace) {

        List<NavigationDecision.AlternativeConsidered> alternatives = new ArrayList<>();
        List<NavigationDecision.NodeSelection> selections = new ArrayList<>();

        Set<EligibleSpace.CandidateAction> parallelSet = new HashSet<>(parallelActions);

        for (EligibleSpace.CandidateAction candidate : allCandidates) {
            if (parallelSet.contains(candidate)) {
                alternatives.add(NavigationDecision.AlternativeConsidered.selected(
                    candidate, "Selected for parallel execution"));
                selections.add(new NavigationDecision.NodeSelection(
                    candidate.node(),
                    candidate,
                    "Parallel execution"
                ));
            } else {
                alternatives.add(NavigationDecision.AlternativeConsidered.rejected(
                    candidate, "Not eligible for parallel execution"));
            }
        }

        return NavigationDecision.proceed(
            selections,
            alternatives,
            NavigationDecision.SelectionCriteria.PARALLEL,
            "Selected " + selections.size() + " actions for parallel execution",
            eligibleSpace
        );
    }

    private NavigationDecision handleHighestPriority(
            EligibleSpace.CandidateAction highest,
            List<EligibleSpace.CandidateAction> allCandidates,
            EligibleSpace eligibleSpace) {

        List<NavigationDecision.AlternativeConsidered> alternatives = new ArrayList<>();

        for (EligibleSpace.CandidateAction candidate : allCandidates) {
            if (candidate == highest) {
                alternatives.add(NavigationDecision.AlternativeConsidered.selected(
                    candidate,
                    "Selected: highest priority (weight=" + candidate.effectivePriority() + ")"));
            } else {
                alternatives.add(NavigationDecision.AlternativeConsidered.rejected(
                    candidate,
                    "Rejected: lower priority (weight=" + candidate.effectivePriority() + ")"));
            }
        }

        NavigationDecision.NodeSelection selection = new NavigationDecision.NodeSelection(
            highest.node(),
            highest,
            "Highest priority action"
        );

        return NavigationDecision.proceed(
            List.of(selection),
            alternatives,
            NavigationDecision.SelectionCriteria.HIGHEST_PRIORITY,
            "Selected highest priority: " + highest.nodeId().value() +
                " (weight=" + highest.effectivePriority() + ")",
            eligibleSpace
        );
    }

    private List<EligibleSpace.CandidateAction> applyDependencyConstraints(
            List<EligibleSpace.CandidateAction> candidates,
            ProcessInstance instance,
            ProcessGraph graph,
            DependencyConstraints dependencies) {

        // Get completed node IDs
        Set<String> completedNodeIds = instance.nodeExecutions().stream()
            .filter(ne -> ne.status() == ProcessInstance.NodeExecutionStatus.COMPLETED)
            .map(ne -> ne.nodeId().value())
            .collect(java.util.stream.Collectors.toSet());

        // Filter candidates based on dependencies
        List<EligibleSpace.CandidateAction> filtered = new ArrayList<>();

        for (EligibleSpace.CandidateAction candidate : candidates) {
            String nodeId = candidate.nodeId().value();

            // Check explicit dependencies
            List<String> mustExecuteBefore = dependencies.mustExecuteBefore()
                .getOrDefault(nodeId, List.of());

            boolean dependenciesMet = mustExecuteBefore.stream()
                .allMatch(completedNodeIds::contains);

            // Check implicit dependencies from graph structure (edges)
            if (candidate.edge() != null) {
                Node.NodeId sourceNodeId = candidate.edge().sourceNodeId();
                dependenciesMet = dependenciesMet && completedNodeIds.contains(sourceNodeId.value());
            }

            if (dependenciesMet) {
                filtered.add(candidate);
            }
        }

        return filtered.isEmpty() ? candidates : filtered;
    }
}
