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

package com.ihelio.cpg.domain.policy;

import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Node;
import java.util.Map;
import java.util.Objects;

/**
 * Result of evaluating a policy gate.
 *
 * @param policyGate the policy gate that was evaluated
 * @param outcome the policy evaluation outcome
 * @param details additional details from the policy evaluation
 * @param error error message if evaluation failed
 */
public record PolicyResult(
    Node.PolicyGate policyGate,
    Edge.PolicyOutcome outcome,
    Map<String, Object> details,
    String error
) {

    public PolicyResult {
        Objects.requireNonNull(policyGate, "PolicyResult policyGate is required");
        Objects.requireNonNull(outcome, "PolicyResult outcome is required");
        details = details != null ? Map.copyOf(details) : Map.of();
    }

    /**
     * Creates a passed policy result.
     */
    public static PolicyResult passed(Node.PolicyGate policyGate, Map<String, Object> details) {
        return new PolicyResult(policyGate, Edge.PolicyOutcome.PASSED, details, null);
    }

    /**
     * Creates a failed policy result.
     */
    public static PolicyResult failed(Node.PolicyGate policyGate, String reason) {
        return new PolicyResult(policyGate, Edge.PolicyOutcome.FAILED, Map.of("reason", reason), null);
    }

    /**
     * Creates a waived policy result.
     */
    public static PolicyResult waived(Node.PolicyGate policyGate, String reason) {
        return new PolicyResult(policyGate, Edge.PolicyOutcome.WAIVED, Map.of("reason", reason), null);
    }

    /**
     * Creates a pending review policy result.
     */
    public static PolicyResult pendingReview(Node.PolicyGate policyGate, String reviewer) {
        return new PolicyResult(policyGate, Edge.PolicyOutcome.PENDING_REVIEW,
            Map.of("reviewer", reviewer), null);
    }

    /**
     * Creates an error result when policy evaluation failed.
     */
    public static PolicyResult error(Node.PolicyGate policyGate, String error) {
        return new PolicyResult(policyGate, Edge.PolicyOutcome.FAILED, Map.of(), error);
    }

    /**
     * Checks if the policy passed.
     */
    public boolean passed() {
        return outcome == Edge.PolicyOutcome.PASSED || outcome == Edge.PolicyOutcome.WAIVED;
    }

    /**
     * Checks if the policy blocks progression.
     */
    public boolean blocks() {
        return outcome == Edge.PolicyOutcome.FAILED || outcome == Edge.PolicyOutcome.PENDING_REVIEW;
    }
}
