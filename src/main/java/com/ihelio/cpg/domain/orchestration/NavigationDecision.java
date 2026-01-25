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

import com.ihelio.cpg.domain.model.Node;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * NavigationDecision represents the outcome of the decision engine's selection process.
 *
 * <p>It captures:
 * <ul>
 *   <li>The type of decision (proceed, wait, blocked)</li>
 *   <li>The selected node(s) for execution</li>
 *   <li>All alternatives that were considered</li>
 *   <li>The selection criteria and reasoning</li>
 *   <li>The eligible space from which the selection was made</li>
 * </ul>
 *
 * <p>This record forms part of the immutable decision trace for audit purposes.
 *
 * @param type the type of navigation decision
 * @param selectedNodes the nodes selected for execution
 * @param alternatives all alternatives that were considered
 * @param selectionCriteria the criteria used for selection
 * @param selectionReason human-readable explanation of the selection
 * @param eligibleSpace the eligible space from which selection was made
 * @param decidedAt timestamp when the decision was made
 */
public record NavigationDecision(
    DecisionType type,
    List<NodeSelection> selectedNodes,
    List<AlternativeConsidered> alternatives,
    SelectionCriteria selectionCriteria,
    String selectionReason,
    EligibleSpace eligibleSpace,
    Instant decidedAt
) {

    public NavigationDecision {
        Objects.requireNonNull(type, "NavigationDecision type is required");
        Objects.requireNonNull(selectionCriteria, "NavigationDecision selectionCriteria is required");
        Objects.requireNonNull(decidedAt, "NavigationDecision decidedAt is required");
        selectedNodes = selectedNodes != null ? List.copyOf(selectedNodes) : List.of();
        alternatives = alternatives != null ? List.copyOf(alternatives) : List.of();
    }

    /**
     * Type of navigation decision.
     */
    public enum DecisionType {
        /** Selected node(s) for execution */
        PROCEED,
        /** No actions available - waiting for events */
        WAIT,
        /** Actions available but blocked by governance */
        BLOCKED,
        /** Process has completed */
        COMPLETE
    }

    /**
     * Criteria used for selecting among alternatives.
     */
    public enum SelectionCriteria {
        /** Selected highest priority action */
        HIGHEST_PRIORITY,
        /** Selected exclusive action */
        EXCLUSIVE,
        /** Selected multiple actions for parallel execution */
        PARALLEL,
        /** Selected based on dependency ordering */
        DEPENDENCY_ORDER,
        /** Single action available - no selection needed */
        SINGLE_OPTION,
        /** No actions available */
        NO_OPTIONS
    }

    /**
     * Creates a PROCEED decision with selected nodes.
     *
     * @param selectedNodes the nodes to execute
     * @param alternatives all alternatives considered
     * @param criteria the selection criteria
     * @param reason the selection reason
     * @param eligibleSpace the eligible space
     * @return a new navigation decision
     */
    public static NavigationDecision proceed(
            List<NodeSelection> selectedNodes,
            List<AlternativeConsidered> alternatives,
            SelectionCriteria criteria,
            String reason,
            EligibleSpace eligibleSpace) {
        return new NavigationDecision(
            DecisionType.PROCEED,
            selectedNodes,
            alternatives,
            criteria,
            reason,
            eligibleSpace,
            Instant.now()
        );
    }

    /**
     * Creates a WAIT decision when no actions are available.
     *
     * @param reason the reason for waiting
     * @param eligibleSpace the empty eligible space
     * @return a new navigation decision
     */
    public static NavigationDecision wait(String reason, EligibleSpace eligibleSpace) {
        return new NavigationDecision(
            DecisionType.WAIT,
            List.of(),
            List.of(),
            SelectionCriteria.NO_OPTIONS,
            reason,
            eligibleSpace,
            Instant.now()
        );
    }

    /**
     * Creates a BLOCKED decision when governance prevents execution.
     *
     * @param alternatives the alternatives that were blocked
     * @param reason the reason for blocking
     * @param eligibleSpace the eligible space
     * @return a new navigation decision
     */
    public static NavigationDecision blocked(
            List<AlternativeConsidered> alternatives,
            String reason,
            EligibleSpace eligibleSpace) {
        return new NavigationDecision(
            DecisionType.BLOCKED,
            List.of(),
            alternatives,
            SelectionCriteria.NO_OPTIONS,
            reason,
            eligibleSpace,
            Instant.now()
        );
    }

    /**
     * Creates a COMPLETE decision when the process has finished.
     *
     * @param reason the completion reason
     * @return a new navigation decision
     */
    public static NavigationDecision complete(String reason) {
        return new NavigationDecision(
            DecisionType.COMPLETE,
            List.of(),
            List.of(),
            SelectionCriteria.NO_OPTIONS,
            reason,
            EligibleSpace.empty(),
            Instant.now()
        );
    }

    /**
     * Checks if this decision indicates execution should proceed.
     *
     * @return true if type is PROCEED
     */
    public boolean shouldProceed() {
        return type == DecisionType.PROCEED;
    }

    /**
     * Checks if this decision indicates waiting.
     *
     * @return true if type is WAIT
     */
    public boolean isWaiting() {
        return type == DecisionType.WAIT;
    }

    /**
     * Checks if this decision indicates blocking.
     *
     * @return true if type is BLOCKED
     */
    public boolean isBlocked() {
        return type == DecisionType.BLOCKED;
    }

    /**
     * Checks if this decision indicates completion.
     *
     * @return true if type is COMPLETE
     */
    public boolean isComplete() {
        return type == DecisionType.COMPLETE;
    }

    /**
     * Returns the first selected node, if any.
     *
     * @return the first selected node
     */
    public NodeSelection primarySelection() {
        return selectedNodes.isEmpty() ? null : selectedNodes.get(0);
    }

    /**
     * A selected node with its associated edge and selection reason.
     *
     * @param node the selected node
     * @param candidateAction the candidate action that was selected
     * @param reason why this node was selected
     */
    public record NodeSelection(
        Node node,
        EligibleSpace.CandidateAction candidateAction,
        String reason
    ) {
        public NodeSelection {
            Objects.requireNonNull(node, "NodeSelection node is required");
            Objects.requireNonNull(candidateAction, "NodeSelection candidateAction is required");
        }

        public Node.NodeId nodeId() {
            return node.id();
        }
    }

    /**
     * An alternative that was considered during selection.
     *
     * @param candidateAction the candidate that was considered
     * @param priority the priority of this alternative
     * @param selected whether this alternative was selected
     * @param reason why it was or was not selected
     */
    public record AlternativeConsidered(
        EligibleSpace.CandidateAction candidateAction,
        int priority,
        boolean selected,
        String reason
    ) {
        public AlternativeConsidered {
            Objects.requireNonNull(candidateAction, "AlternativeConsidered candidateAction is required");
        }

        /**
         * Creates an alternative that was selected.
         */
        public static AlternativeConsidered selected(
                EligibleSpace.CandidateAction action, String reason) {
            return new AlternativeConsidered(action, action.effectivePriority(), true, reason);
        }

        /**
         * Creates an alternative that was rejected.
         */
        public static AlternativeConsidered rejected(
                EligibleSpace.CandidateAction action, String reason) {
            return new AlternativeConsidered(action, action.effectivePriority(), false, reason);
        }
    }

    /**
     * Builder for NavigationDecision.
     */
    public static class Builder {
        private DecisionType type;
        private List<NodeSelection> selectedNodes = new java.util.ArrayList<>();
        private List<AlternativeConsidered> alternatives = new java.util.ArrayList<>();
        private SelectionCriteria selectionCriteria = SelectionCriteria.NO_OPTIONS;
        private String selectionReason;
        private EligibleSpace eligibleSpace;

        public Builder type(DecisionType type) {
            this.type = type;
            return this;
        }

        public Builder selectedNodes(List<NodeSelection> selectedNodes) {
            this.selectedNodes = new java.util.ArrayList<>(selectedNodes);
            return this;
        }

        public Builder addSelectedNode(NodeSelection selection) {
            this.selectedNodes.add(selection);
            return this;
        }

        public Builder alternatives(List<AlternativeConsidered> alternatives) {
            this.alternatives = new java.util.ArrayList<>(alternatives);
            return this;
        }

        public Builder addAlternative(AlternativeConsidered alternative) {
            this.alternatives.add(alternative);
            return this;
        }

        public Builder selectionCriteria(SelectionCriteria criteria) {
            this.selectionCriteria = criteria;
            return this;
        }

        public Builder selectionReason(String reason) {
            this.selectionReason = reason;
            return this;
        }

        public Builder eligibleSpace(EligibleSpace eligibleSpace) {
            this.eligibleSpace = eligibleSpace;
            return this;
        }

        public NavigationDecision build() {
            return new NavigationDecision(
                type,
                selectedNodes,
                alternatives,
                selectionCriteria,
                selectionReason,
                eligibleSpace,
                Instant.now()
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
