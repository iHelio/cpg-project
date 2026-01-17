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

package com.ihelio.cpg.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * An Edge defines when and how execution may move from one decision point to another.
 *
 * <p>Edges do not impose fixed control flow. Instead, they express permissible
 * transitions that are evaluated dynamically at runtime. Key characteristics:
 *
 * <ul>
 *   <li><b>Guard Conditions</b>: FEEL expressions evaluated against updated context,
 *       rule output, policy outcome, and events</li>
 *   <li><b>Execution Semantics</b>: Sequential progression, parallel branching,
 *       or compensating transactions</li>
 *   <li><b>Priority/Ranking</b>: Metadata for when multiple transitions are eligible</li>
 *   <li><b>Event Triggers</b>: Transitions activated or re-evaluated in response
 *       to real-time signals</li>
 *   <li><b>Compensation Semantics</b>: Governed paths for recovery when downstream
 *       actions fail or become invalid</li>
 * </ul>
 */
public record Edge(
    EdgeId id,
    String name,
    String description,
    Node.NodeId sourceNodeId,
    Node.NodeId targetNodeId,
    GuardConditions guardConditions,
    ExecutionSemantics executionSemantics,
    Priority priority,
    EventTriggers eventTriggers,
    CompensationSemantics compensationSemantics
) {

    public Edge {
        Objects.requireNonNull(id, "Edge id is required");
        Objects.requireNonNull(sourceNodeId, "Edge sourceNodeId is required");
        Objects.requireNonNull(targetNodeId, "Edge targetNodeId is required");
        Objects.requireNonNull(guardConditions, "Edge guardConditions is required");
        executionSemantics = executionSemantics != null
            ? executionSemantics : ExecutionSemantics.sequential();
        priority = priority != null ? priority : Priority.defaults();
        eventTriggers = eventTriggers != null ? eventTriggers : EventTriggers.none();
        compensationSemantics = compensationSemantics != null
            ? compensationSemantics : CompensationSemantics.none();
    }

    /**
     * Unique identifier for an edge.
     */
    public record EdgeId(String value) {
        public EdgeId {
            Objects.requireNonNull(value, "EdgeId value is required");
            if (value.isBlank()) {
                throw new IllegalArgumentException("EdgeId cannot be blank");
            }
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * Guard conditions that must ALL be satisfied for the edge to be traversable.
     *
     * <p>All expressions use FEEL (Friendly Enough Expression Language) and are
     * evaluated against the current execution context.
     */
    public record GuardConditions(
        List<FeelExpression> contextConditions,
        List<RuleOutcomeCondition> ruleOutcomeConditions,
        List<PolicyOutcomeCondition> policyOutcomeConditions,
        List<EventCondition> eventConditions
    ) {
        public GuardConditions {
            contextConditions = contextConditions != null
                ? List.copyOf(contextConditions) : List.of();
            ruleOutcomeConditions = ruleOutcomeConditions != null
                ? List.copyOf(ruleOutcomeConditions) : List.of();
            policyOutcomeConditions = policyOutcomeConditions != null
                ? List.copyOf(policyOutcomeConditions) : List.of();
            eventConditions = eventConditions != null
                ? List.copyOf(eventConditions) : List.of();
        }

        /**
         * Creates guard conditions with only context conditions.
         */
        public static GuardConditions ofContext(List<FeelExpression> contextConditions) {
            return new GuardConditions(contextConditions, List.of(), List.of(), List.of());
        }

        /**
         * Creates guard conditions that always pass.
         */
        public static GuardConditions alwaysTrue() {
            return new GuardConditions(List.of(), List.of(), List.of(), List.of());
        }

        public boolean hasConditions() {
            return !contextConditions.isEmpty()
                || !ruleOutcomeConditions.isEmpty()
                || !policyOutcomeConditions.isEmpty()
                || !eventConditions.isEmpty();
        }
    }

    /**
     * Condition based on expected output from a business rule.
     */
    public record RuleOutcomeCondition(
        String ruleId,
        FeelExpression expectedOutcome
    ) {
        public RuleOutcomeCondition {
            Objects.requireNonNull(ruleId, "RuleOutcomeCondition ruleId is required");
            Objects.requireNonNull(expectedOutcome, "RuleOutcomeCondition expectedOutcome is required");
        }
    }

    /**
     * Condition based on required policy gate result.
     */
    public record PolicyOutcomeCondition(
        String policyGateId,
        PolicyOutcome requiredOutcome
    ) {
        public PolicyOutcomeCondition {
            Objects.requireNonNull(policyGateId, "PolicyOutcomeCondition policyGateId is required");
            Objects.requireNonNull(requiredOutcome, "PolicyOutcomeCondition requiredOutcome is required");
        }
    }

    /**
     * Possible outcomes of a policy gate evaluation.
     */
    public enum PolicyOutcome {
        PASSED,
        FAILED,
        WAIVED,
        PENDING_REVIEW
    }

    /**
     * Condition based on event occurrence.
     */
    public record EventCondition(
        String eventType,
        FeelExpression correlationExpression,
        boolean mustHaveOccurred
    ) {
        public EventCondition {
            Objects.requireNonNull(eventType, "EventCondition eventType is required");
        }

        public static EventCondition occurred(String eventType) {
            return new EventCondition(eventType, null, true);
        }

        public static EventCondition notOccurred(String eventType) {
            return new EventCondition(eventType, null, false);
        }
    }

    /**
     * Execution semantics defining how the transition is executed.
     */
    public record ExecutionSemantics(
        ExecutionType type,
        JoinType joinType,
        String compensationRef
    ) {
        public ExecutionSemantics {
            Objects.requireNonNull(type, "ExecutionSemantics type is required");
        }

        public static ExecutionSemantics sequential() {
            return new ExecutionSemantics(ExecutionType.SEQUENTIAL, null, null);
        }

        public static ExecutionSemantics parallel(JoinType joinType) {
            return new ExecutionSemantics(ExecutionType.PARALLEL, joinType, null);
        }

        public static ExecutionSemantics compensating(String compensationRef) {
            return new ExecutionSemantics(ExecutionType.COMPENSATING, null, compensationRef);
        }
    }

    /**
     * Type of execution for the edge.
     */
    public enum ExecutionType {
        /**
         * Standard sequential progression to next node.
         */
        SEQUENTIAL,

        /**
         * Parallel branching - multiple edges can execute simultaneously.
         */
        PARALLEL,

        /**
         * Compensating transaction - executed for rollback/recovery.
         */
        COMPENSATING
    }

    /**
     * Join type for parallel execution branches.
     */
    public enum JoinType {
        /**
         * Wait for ALL parallel branches to complete.
         */
        ALL,

        /**
         * Continue when ANY branch completes.
         */
        ANY,

        /**
         * Continue when N of M branches complete.
         */
        N_OF_M
    }

    /**
     * Priority and ranking for edge selection when multiple edges are eligible.
     */
    public record Priority(
        int weight,
        int rank,
        boolean exclusive
    ) {
        public Priority {
            if (weight < 0) {
                throw new IllegalArgumentException("Priority weight must be non-negative");
            }
            if (rank < 0) {
                throw new IllegalArgumentException("Priority rank must be non-negative");
            }
        }

        public static Priority defaults() {
            return new Priority(100, 0, false);
        }

        public static Priority high() {
            return new Priority(1000, 0, false);
        }

        public static Priority exclusive(int weight) {
            return new Priority(weight, 0, true);
        }
    }

    /**
     * Event triggers that activate or re-evaluate the edge.
     */
    public record EventTriggers(
        List<String> activatingEvents,
        List<String> reevaluationEvents
    ) {
        public EventTriggers {
            activatingEvents = activatingEvents != null
                ? List.copyOf(activatingEvents) : List.of();
            reevaluationEvents = reevaluationEvents != null
                ? List.copyOf(reevaluationEvents) : List.of();
        }

        public static EventTriggers none() {
            return new EventTriggers(List.of(), List.of());
        }

        public static EventTriggers activatedBy(String... events) {
            return new EventTriggers(List.of(events), List.of());
        }
    }

    /**
     * Compensation semantics for handling failures or invalidation.
     */
    public record CompensationSemantics(
        CompensationStrategy strategy,
        int maxRetries,
        String compensatingEdgeId,
        FeelExpression compensationCondition
    ) {
        public CompensationSemantics {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be non-negative");
            }
        }

        public static CompensationSemantics none() {
            return new CompensationSemantics(null, 0, null, null);
        }

        public static CompensationSemantics retry(int maxRetries) {
            return new CompensationSemantics(CompensationStrategy.RETRY, maxRetries, null, null);
        }

        public static CompensationSemantics rollback(String compensatingEdgeId) {
            return new CompensationSemantics(
                CompensationStrategy.ROLLBACK, 0, compensatingEdgeId, null);
        }

        public static CompensationSemantics escalate() {
            return new CompensationSemantics(CompensationStrategy.ESCALATE, 0, null, null);
        }

        public boolean hasCompensation() {
            return strategy != null;
        }
    }

    /**
     * Strategy for compensation when downstream actions fail.
     */
    public enum CompensationStrategy {
        /**
         * Retry the failed action.
         */
        RETRY,

        /**
         * Execute a rollback/undo action.
         */
        ROLLBACK,

        /**
         * Take an alternate path.
         */
        ALTERNATE,

        /**
         * Escalate to human review.
         */
        ESCALATE
    }
}
