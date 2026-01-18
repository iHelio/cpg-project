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

import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.expression.EvaluationResult;
import com.ihelio.cpg.domain.expression.ExpressionEvaluator;
import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.FeelExpression;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates edge guard conditions to determine if edges can be traversed.
 *
 * <p>Guard conditions include:
 * <ul>
 *   <li>Context conditions (FEEL expressions against execution context)</li>
 *   <li>Rule outcome conditions (expected outputs from business rules)</li>
 *   <li>Policy outcome conditions (expected policy gate results)</li>
 *   <li>Event conditions (required events to have occurred)</li>
 * </ul>
 */
public class EdgeEvaluator {

    private final ExpressionEvaluator expressionEvaluator;

    public EdgeEvaluator(ExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = Objects.requireNonNull(expressionEvaluator);
    }

    /**
     * Evaluates if an edge can be traversed.
     *
     * @param edge the edge to evaluate
     * @param context the execution context
     * @param nodeRuleOutputs outputs from the source node's rule evaluation
     * @param nodePolicyOutcomes outcomes from the source node's policy evaluation
     * @return the evaluation result
     */
    public EdgeEvaluation evaluate(
            Edge edge,
            ExecutionContext context,
            Map<String, Object> nodeRuleOutputs,
            Map<String, Edge.PolicyOutcome> nodePolicyOutcomes) {

        Edge.GuardConditions guards = edge.guardConditions();

        // If no conditions, edge is always traversable
        if (!guards.hasConditions()) {
            return EdgeEvaluation.traversable(edge);
        }

        Map<String, Object> feelContext = context.toFeelContext();

        // 1. Evaluate context conditions
        if (!evaluateContextConditions(guards.contextConditions(), feelContext)) {
            return EdgeEvaluation.blockedByContext(edge,
                "Context guard conditions not satisfied");
        }

        // 2. Evaluate rule outcome conditions
        if (!evaluateRuleOutcomeConditions(guards.ruleOutcomeConditions(),
                nodeRuleOutputs, feelContext)) {
            return EdgeEvaluation.blockedByRules(edge,
                "Rule outcome conditions not satisfied");
        }

        // 3. Evaluate policy outcome conditions
        if (!evaluatePolicyOutcomeConditions(guards.policyOutcomeConditions(),
                nodePolicyOutcomes)) {
            return EdgeEvaluation.blockedByPolicies(edge,
                "Policy outcome conditions not satisfied");
        }

        // 4. Evaluate event conditions
        if (!evaluateEventConditions(guards.eventConditions(), context)) {
            return EdgeEvaluation.blockedByEvents(edge,
                "Event conditions not satisfied");
        }

        return EdgeEvaluation.traversable(edge);
    }

    /**
     * Evaluates multiple edges and returns those that are traversable,
     * sorted by priority.
     *
     * @param edges the edges to evaluate
     * @param context the execution context
     * @param nodeRuleOutputs outputs from the source node's rule evaluation
     * @param nodePolicyOutcomes outcomes from the source node's policy evaluation
     * @return list of traversable edges, sorted by priority (highest first)
     */
    public List<EdgeEvaluation> evaluateTraversable(
            List<Edge> edges,
            ExecutionContext context,
            Map<String, Object> nodeRuleOutputs,
            Map<String, Edge.PolicyOutcome> nodePolicyOutcomes) {

        List<EdgeEvaluation> traversable = new ArrayList<>();
        for (Edge edge : edges) {
            EdgeEvaluation evaluation = evaluate(edge, context,
                nodeRuleOutputs, nodePolicyOutcomes);
            if (evaluation.traversable()) {
                traversable.add(evaluation);
            }
        }

        // Sort by priority weight (higher weight = higher priority)
        traversable.sort(Comparator
            .comparingInt((EdgeEvaluation e) -> e.edge().priority().weight())
            .reversed()
            .thenComparingInt(e -> e.edge().priority().rank()));

        return traversable;
    }

    /**
     * Selects edges to traverse based on evaluation and exclusivity rules.
     *
     * @param evaluations the evaluated edges (must be traversable)
     * @return list of edges to traverse
     */
    public List<Edge> selectEdgesToTraverse(List<EdgeEvaluation> evaluations) {
        if (evaluations.isEmpty()) {
            return List.of();
        }

        List<Edge> selected = new ArrayList<>();

        for (EdgeEvaluation eval : evaluations) {
            Edge edge = eval.edge();

            if (edge.priority().exclusive()) {
                // Exclusive edge - only select this one
                return List.of(edge);
            }

            selected.add(edge);
        }

        return selected;
    }

    private boolean evaluateContextConditions(
            List<FeelExpression> conditions,
            Map<String, Object> feelContext) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        return expressionEvaluator.evaluateAllAsBoolean(conditions, feelContext);
    }

    private boolean evaluateRuleOutcomeConditions(
            List<Edge.RuleOutcomeCondition> conditions,
            Map<String, Object> ruleOutputs,
            Map<String, Object> feelContext) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        // Merge rule outputs into the context for expression evaluation
        Map<String, Object> enrichedContext = new java.util.HashMap<>(feelContext);
        enrichedContext.put("ruleOutputs", ruleOutputs);

        for (Edge.RuleOutcomeCondition condition : conditions) {
            FeelExpression expectedOutcome = condition.expectedOutcome();
            EvaluationResult result = expressionEvaluator.evaluate(
                expectedOutcome, enrichedContext);
            if (!result.success() || !result.asBoolean()) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluatePolicyOutcomeConditions(
            List<Edge.PolicyOutcomeCondition> conditions,
            Map<String, Edge.PolicyOutcome> policyOutcomes) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        for (Edge.PolicyOutcomeCondition condition : conditions) {
            Edge.PolicyOutcome actual = policyOutcomes.get(condition.policyGateId());
            if (actual != condition.requiredOutcome()) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateEventConditions(
            List<Edge.EventCondition> conditions,
            ExecutionContext context) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        for (Edge.EventCondition condition : conditions) {
            boolean eventOccurred = context.hasReceivedEvent(condition.eventType());
            if (condition.mustHaveOccurred() != eventOccurred) {
                return false;
            }
        }
        return true;
    }
}
