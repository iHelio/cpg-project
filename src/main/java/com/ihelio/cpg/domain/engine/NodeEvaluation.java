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

import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.policy.PolicyResult;
import com.ihelio.cpg.domain.rule.RuleResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of evaluating a node's availability and readiness.
 *
 * <p>Contains the outcome of evaluating preconditions, policy gates,
 * and business rules for a node.
 *
 * @param node the node that was evaluated
 * @param available whether the node is available for execution
 * @param preconditionsPassed whether all preconditions passed
 * @param policiesPassed whether all policy gates passed
 * @param policyResults individual policy evaluation results
 * @param ruleResults individual rule evaluation results
 * @param ruleOutputs merged outputs from all rules
 * @param blockedReason reason if the node is blocked
 */
public record NodeEvaluation(
    Node node,
    boolean available,
    boolean preconditionsPassed,
    boolean policiesPassed,
    List<PolicyResult> policyResults,
    List<RuleResult> ruleResults,
    Map<String, Object> ruleOutputs,
    String blockedReason
) {

    public NodeEvaluation {
        Objects.requireNonNull(node, "NodeEvaluation node is required");
        policyResults = policyResults != null ? List.copyOf(policyResults) : List.of();
        ruleResults = ruleResults != null ? List.copyOf(ruleResults) : List.of();
        ruleOutputs = ruleOutputs != null ? Map.copyOf(ruleOutputs) : Map.of();
    }

    /**
     * Creates an available node evaluation.
     */
    public static NodeEvaluation available(
            Node node,
            List<PolicyResult> policyResults,
            List<RuleResult> ruleResults,
            Map<String, Object> ruleOutputs) {
        return new NodeEvaluation(
            node,
            true,
            true,
            true,
            policyResults,
            ruleResults,
            ruleOutputs,
            null
        );
    }

    /**
     * Creates a blocked node evaluation due to preconditions.
     */
    public static NodeEvaluation blockedByPreconditions(Node node, String reason) {
        return new NodeEvaluation(
            node,
            false,
            false,
            false,
            List.of(),
            List.of(),
            Map.of(),
            reason
        );
    }

    /**
     * Creates a blocked node evaluation due to policy.
     */
    public static NodeEvaluation blockedByPolicy(
            Node node,
            List<PolicyResult> policyResults) {
        String reason = policyResults.stream()
            .filter(PolicyResult::blocks)
            .findFirst()
            .map(r -> "Policy blocked: " + r.policyGate().name())
            .orElse("Policy blocked");
        return new NodeEvaluation(
            node,
            false,
            true,
            false,
            policyResults,
            List.of(),
            Map.of(),
            reason
        );
    }

    /**
     * Checks if the node is blocked.
     */
    public boolean isBlocked() {
        return !available;
    }

    /**
     * Returns a specific rule output value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getRuleOutput(String key) {
        return (T) ruleOutputs.get(key);
    }
}
