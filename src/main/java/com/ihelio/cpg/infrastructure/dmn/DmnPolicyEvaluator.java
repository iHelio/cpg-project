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

package com.ihelio.cpg.infrastructure.dmn;

import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.policy.PolicyEvaluator;
import com.ihelio.cpg.domain.policy.PolicyResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DMN-based implementation of the PolicyEvaluator port.
 *
 * <p>Wraps the DmnDecisionService to evaluate policy gates using
 * DMN decision tables. Business users can maintain the decision
 * tables without code changes.
 */
@Component
public class DmnPolicyEvaluator implements PolicyEvaluator {

    private static final Logger log = LoggerFactory.getLogger(DmnPolicyEvaluator.class);
    private static final String DEFAULT_POLICY_MODEL = "PolicyRules";

    private final DmnDecisionService dmnDecisionService;

    public DmnPolicyEvaluator(DmnDecisionService dmnDecisionService) {
        this.dmnDecisionService = dmnDecisionService;
    }

    @Override
    public PolicyResult evaluate(Node.PolicyGate policyGate, Map<String, Object> context) {
        String decisionRef = policyGate.dmnDecisionRef();

        if (decisionRef == null || decisionRef.isBlank()) {
            // No DMN decision configured - pass by default
            log.debug("Policy gate {} has no DMN decision configured, passing by default",
                policyGate.id());
            return PolicyResult.passed(policyGate, Map.of("reason", "No DMN decision configured"));
        }

        try {
            // Parse decision reference (format: "modelName.decisionName" or just "decisionName")
            String modelName = DEFAULT_POLICY_MODEL;
            String decisionName = decisionRef;

            if (decisionRef.contains(".")) {
                String[] parts = decisionRef.split("\\.", 2);
                modelName = parts[0];
                decisionName = parts[1];
            }

            DmnDecisionService.DecisionResult result =
                dmnDecisionService.evaluate(modelName, decisionName, context);

            if (!result.success()) {
                log.warn("Policy DMN evaluation failed for {}: {}", policyGate.id(), result.error());
                return PolicyResult.error(policyGate, result.error());
            }

            // Interpret the DMN result
            return interpretPolicyResult(policyGate, result);

        } catch (IllegalArgumentException e) {
            // Model not found - treat as passed with warning
            log.warn("DMN model not found for policy {}: {}", policyGate.id(), e.getMessage());
            return PolicyResult.passed(policyGate, Map.of(
                "warning", "DMN model not found: " + e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Policy evaluation error for {}: {}", policyGate.id(), e.getMessage());
            return PolicyResult.error(policyGate, e.getMessage());
        }
    }

    @Override
    public List<PolicyResult> evaluateAll(
            List<Node.PolicyGate> policyGates,
            Map<String, Object> context) {

        List<PolicyResult> results = new ArrayList<>();
        for (Node.PolicyGate policyGate : policyGates) {
            results.add(evaluate(policyGate, context));
        }
        return results;
    }

    private PolicyResult interpretPolicyResult(
            Node.PolicyGate policyGate,
            DmnDecisionService.DecisionResult result) {

        Object value = result.value();

        // Handle string outcomes
        if (value instanceof String outcome) {
            return interpretStringOutcome(policyGate, outcome);
        }

        // Handle boolean outcomes
        if (value instanceof Boolean passed) {
            if (passed) {
                return PolicyResult.passed(policyGate, Map.of());
            } else {
                return PolicyResult.failed(policyGate, "Policy check returned false");
            }
        }

        // Handle map outcomes (structured response)
        if (value instanceof Map<?, ?> mapResult) {
            return interpretMapOutcome(policyGate, mapResult);
        }

        // Default: check if matches required outcome
        String requiredOutcome = policyGate.requiredOutcome();
        if (requiredOutcome != null && requiredOutcome.equals(String.valueOf(value))) {
            return PolicyResult.passed(policyGate, Map.of("value", value));
        }

        return PolicyResult.failed(policyGate,
            "Unexpected outcome: " + value + " (expected: " + requiredOutcome + ")");
    }

    private PolicyResult interpretStringOutcome(
            Node.PolicyGate policyGate,
            String outcome) {

        return switch (outcome.toUpperCase()) {
            case "PASSED", "APPROVED", "ALLOWED", "YES", "TRUE" ->
                PolicyResult.passed(policyGate, Map.of("outcome", outcome));
            case "FAILED", "REJECTED", "DENIED", "NO", "FALSE" ->
                PolicyResult.failed(policyGate, "Policy rejected: " + outcome);
            case "WAIVED", "EXEMPT", "SKIP" ->
                PolicyResult.waived(policyGate, outcome);
            case "PENDING", "REVIEW", "PENDING_REVIEW" ->
                PolicyResult.pendingReview(policyGate, "manual review");
            default -> {
                // Check if matches required outcome
                String required = policyGate.requiredOutcome();
                if (required != null && required.equalsIgnoreCase(outcome)) {
                    yield PolicyResult.passed(policyGate, Map.of("outcome", outcome));
                }
                yield PolicyResult.failed(policyGate, "Unknown outcome: " + outcome);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private PolicyResult interpretMapOutcome(
            Node.PolicyGate policyGate,
            Map<?, ?> mapResult) {

        Map<String, Object> details = (Map<String, Object>) mapResult;

        // Look for standard outcome fields
        Object outcome = details.get("outcome");
        if (outcome == null) {
            outcome = details.get("result");
        }
        if (outcome == null) {
            outcome = details.get("status");
        }

        if (outcome instanceof String outcomeStr) {
            PolicyResult baseResult = interpretStringOutcome(policyGate, outcomeStr);
            // Merge additional details
            return new PolicyResult(
                policyGate,
                baseResult.outcome(),
                details,
                baseResult.error()
            );
        }

        if (outcome instanceof Boolean passed) {
            Edge.PolicyOutcome policyOutcome = passed
                ? Edge.PolicyOutcome.PASSED
                : Edge.PolicyOutcome.FAILED;
            return new PolicyResult(policyGate, policyOutcome, details, null);
        }

        return PolicyResult.passed(policyGate, details);
    }
}
