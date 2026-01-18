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
import com.ihelio.cpg.domain.expression.ExpressionEvaluator;
import com.ihelio.cpg.domain.model.FeelExpression;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.policy.PolicyEvaluator;
import com.ihelio.cpg.domain.policy.PolicyResult;
import com.ihelio.cpg.domain.rule.RuleEvaluator;
import com.ihelio.cpg.domain.rule.RuleResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates node availability by checking preconditions, policies, and rules.
 *
 * <p>The evaluation order is:
 * <ol>
 *   <li>Preconditions (FEEL expressions from client and domain context)</li>
 *   <li>Policy gates (DMN decisions for compliance/statutory requirements)</li>
 *   <li>Business rules (DMN decisions for execution parameters)</li>
 * </ol>
 *
 * <p>Evaluation short-circuits on first failure for preconditions and policies.
 */
public class NodeEvaluator {

    private final ExpressionEvaluator expressionEvaluator;
    private final PolicyEvaluator policyEvaluator;
    private final RuleEvaluator ruleEvaluator;

    public NodeEvaluator(
            ExpressionEvaluator expressionEvaluator,
            PolicyEvaluator policyEvaluator,
            RuleEvaluator ruleEvaluator) {
        this.expressionEvaluator = Objects.requireNonNull(expressionEvaluator);
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator);
        this.ruleEvaluator = Objects.requireNonNull(ruleEvaluator);
    }

    /**
     * Evaluates if a node is available for execution.
     *
     * @param node the node to evaluate
     * @param context the execution context
     * @return the evaluation result
     */
    public NodeEvaluation evaluate(Node node, ExecutionContext context) {
        Map<String, Object> feelContext = context.toFeelContext();

        // 1. Evaluate preconditions
        if (!evaluatePreconditions(node, feelContext)) {
            return NodeEvaluation.blockedByPreconditions(node,
                "Node preconditions not satisfied");
        }

        // 2. Evaluate policy gates
        List<PolicyResult> policyResults = evaluatePolicies(node, feelContext);
        if (hasBlockingPolicy(policyResults)) {
            return NodeEvaluation.blockedByPolicy(node, policyResults);
        }

        // 3. Evaluate business rules and collect outputs
        List<RuleResult> ruleResults = evaluateRules(node, feelContext);
        Map<String, Object> ruleOutputs = collectRuleOutputs(ruleResults);

        return NodeEvaluation.available(node, policyResults, ruleResults, ruleOutputs);
    }

    /**
     * Evaluates only preconditions for a node.
     *
     * @param node the node to evaluate
     * @param context the execution context
     * @return true if all preconditions pass
     */
    public boolean evaluatePreconditionsOnly(Node node, ExecutionContext context) {
        return evaluatePreconditions(node, context.toFeelContext());
    }

    private boolean evaluatePreconditions(Node node, Map<String, Object> feelContext) {
        Node.Preconditions preconditions = node.preconditions();
        if (preconditions == null) {
            return true;
        }

        // Evaluate client context conditions
        List<FeelExpression> clientConditions = preconditions.clientContextConditions();
        if (!expressionEvaluator.evaluateAllAsBoolean(clientConditions, feelContext)) {
            return false;
        }

        // Evaluate domain context conditions
        List<FeelExpression> domainConditions = preconditions.domainContextConditions();
        return expressionEvaluator.evaluateAllAsBoolean(domainConditions, feelContext);
    }

    private List<PolicyResult> evaluatePolicies(Node node, Map<String, Object> feelContext) {
        List<Node.PolicyGate> policyGates = node.policyGates();
        if (policyGates == null || policyGates.isEmpty()) {
            return List.of();
        }
        return policyEvaluator.evaluateAll(policyGates, feelContext);
    }

    private boolean hasBlockingPolicy(List<PolicyResult> results) {
        return results.stream().anyMatch(PolicyResult::blocks);
    }

    private List<RuleResult> evaluateRules(Node node, Map<String, Object> feelContext) {
        List<Node.BusinessRule> rules = node.businessRules();
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        return ruleEvaluator.evaluateAll(rules, feelContext);
    }

    private Map<String, Object> collectRuleOutputs(List<RuleResult> ruleResults) {
        Map<String, Object> outputs = new HashMap<>();
        for (RuleResult result : ruleResults) {
            if (result.success()) {
                outputs.putAll(result.output());
            }
        }
        return outputs;
    }

    /**
     * Evaluates multiple nodes and returns those that are available.
     *
     * @param nodes the nodes to evaluate
     * @param context the execution context
     * @return list of evaluations for available nodes
     */
    public List<NodeEvaluation> evaluateAvailable(List<Node> nodes, ExecutionContext context) {
        List<NodeEvaluation> available = new ArrayList<>();
        for (Node node : nodes) {
            NodeEvaluation evaluation = evaluate(node, context);
            if (evaluation.available()) {
                available.add(evaluation);
            }
        }
        return available;
    }
}
