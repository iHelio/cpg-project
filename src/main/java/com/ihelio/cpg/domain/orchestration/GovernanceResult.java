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

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * GovernanceResult captures the outcome of pre-execution governance checks.
 *
 * <p>Before any action can produce side effects, it must pass all governance gates:
 * <ul>
 *   <li><b>Idempotency</b>: Has this exact action already been executed?</li>
 *   <li><b>Authorization</b>: Is the current context authorized to execute?</li>
 *   <li><b>Policy Gate</b>: Do all applicable policies allow execution?</li>
 * </ul>
 *
 * <p>All three checks must pass for execution to proceed.
 *
 * @param approved whether all governance checks passed
 * @param idempotencyResult result of idempotency check
 * @param authorizationResult result of authorization check
 * @param policyGateResult result of policy gate check
 * @param evaluatedAt timestamp when governance was evaluated
 */
public record GovernanceResult(
    boolean approved,
    IdempotencyResult idempotencyResult,
    AuthorizationResult authorizationResult,
    PolicyGateResult policyGateResult,
    Instant evaluatedAt
) {

    public GovernanceResult {
        Objects.requireNonNull(idempotencyResult, "GovernanceResult idempotencyResult is required");
        Objects.requireNonNull(authorizationResult, "GovernanceResult authorizationResult is required");
        Objects.requireNonNull(policyGateResult, "GovernanceResult policyGateResult is required");
        Objects.requireNonNull(evaluatedAt, "GovernanceResult evaluatedAt is required");
    }

    /**
     * Creates an approved governance result.
     *
     * @param idempotencyResult the idempotency check result
     * @param authorizationResult the authorization check result
     * @param policyGateResult the policy gate check result
     * @return an approved governance result
     */
    public static GovernanceResult approved(
            IdempotencyResult idempotencyResult,
            AuthorizationResult authorizationResult,
            PolicyGateResult policyGateResult) {
        return new GovernanceResult(
            true,
            idempotencyResult,
            authorizationResult,
            policyGateResult,
            Instant.now()
        );
    }

    /**
     * Creates a rejected governance result.
     *
     * @param idempotencyResult the idempotency check result
     * @param authorizationResult the authorization check result
     * @param policyGateResult the policy gate check result
     * @return a rejected governance result
     */
    public static GovernanceResult rejected(
            IdempotencyResult idempotencyResult,
            AuthorizationResult authorizationResult,
            PolicyGateResult policyGateResult) {
        return new GovernanceResult(
            false,
            idempotencyResult,
            authorizationResult,
            policyGateResult,
            Instant.now()
        );
    }

    /**
     * Combines the three individual results into a unified result.
     *
     * @param idempotencyResult the idempotency check result
     * @param authorizationResult the authorization check result
     * @param policyGateResult the policy gate check result
     * @return the combined governance result
     */
    public static GovernanceResult combine(
            IdempotencyResult idempotencyResult,
            AuthorizationResult authorizationResult,
            PolicyGateResult policyGateResult) {
        boolean allPassed = idempotencyResult.passed()
            && authorizationResult.passed()
            && policyGateResult.passed();
        return new GovernanceResult(
            allPassed,
            idempotencyResult,
            authorizationResult,
            policyGateResult,
            Instant.now()
        );
    }

    /**
     * Returns the primary reason for rejection, if any.
     *
     * @return the rejection reason, or null if approved
     */
    public String rejectionReason() {
        if (approved) {
            return null;
        }
        if (!idempotencyResult.passed()) {
            return "Idempotency check failed: " + idempotencyResult.reason();
        }
        if (!authorizationResult.passed()) {
            return "Authorization check failed: " + authorizationResult.reason();
        }
        if (!policyGateResult.passed()) {
            return "Policy gate check failed: " + policyGateResult.reason();
        }
        return "Unknown governance failure";
    }

    /**
     * Result of idempotency check.
     *
     * @param passed whether the check passed (action not yet executed)
     * @param idempotencyKey the key used for the check
     * @param reason explanation of the result
     * @param previousExecutionId if already executed, the previous execution ID
     */
    public record IdempotencyResult(
        boolean passed,
        String idempotencyKey,
        String reason,
        String previousExecutionId
    ) {
        public IdempotencyResult {
            Objects.requireNonNull(idempotencyKey, "IdempotencyResult idempotencyKey is required");
        }

        /**
         * Creates a passed idempotency result (action not yet executed).
         *
         * @param key the idempotency key
         * @return a passed result
         */
        public static IdempotencyResult passed(String key) {
            return new IdempotencyResult(true, key, "Action not previously executed", null);
        }

        /**
         * Creates a failed idempotency result (action already executed).
         *
         * @param key the idempotency key
         * @param previousExecutionId the ID of the previous execution
         * @return a failed result
         */
        public static IdempotencyResult alreadyExecuted(String key, String previousExecutionId) {
            return new IdempotencyResult(
                false,
                key,
                "Action already executed with ID: " + previousExecutionId,
                previousExecutionId
            );
        }

        /**
         * Creates a skipped idempotency result (check disabled).
         *
         * @return a skipped result
         */
        public static IdempotencyResult skipped() {
            return new IdempotencyResult(true, "SKIPPED", "Idempotency check disabled", null);
        }
    }

    /**
     * Result of authorization check.
     *
     * @param passed whether authorization was granted
     * @param principal the principal being authorized
     * @param permissions required permissions for the action
     * @param reason explanation of the result
     */
    public record AuthorizationResult(
        boolean passed,
        String principal,
        List<String> permissions,
        String reason
    ) {
        public AuthorizationResult {
            permissions = permissions != null ? List.copyOf(permissions) : List.of();
        }

        /**
         * Creates an authorized result.
         *
         * @param principal the authorized principal
         * @param permissions the permissions that were checked
         * @return an authorized result
         */
        public static AuthorizationResult authorized(String principal, List<String> permissions) {
            return new AuthorizationResult(
                true,
                principal,
                permissions,
                "Authorization granted"
            );
        }

        /**
         * Creates an unauthorized result.
         *
         * @param principal the principal that was denied
         * @param permissions the permissions that were missing
         * @param reason the denial reason
         * @return an unauthorized result
         */
        public static AuthorizationResult unauthorized(
                String principal, List<String> permissions, String reason) {
            return new AuthorizationResult(false, principal, permissions, reason);
        }

        /**
         * Creates a skipped authorization result (check disabled).
         *
         * @return a skipped result
         */
        public static AuthorizationResult skipped() {
            return new AuthorizationResult(
                true,
                "SYSTEM",
                List.of(),
                "Authorization check disabled"
            );
        }
    }

    /**
     * Result of policy gate check.
     *
     * @param passed whether all policies passed
     * @param policiesChecked list of policies that were evaluated
     * @param failedPolicies list of policies that failed
     * @param reason explanation of the result
     */
    public record PolicyGateResult(
        boolean passed,
        List<PolicyCheck> policiesChecked,
        List<PolicyCheck> failedPolicies,
        String reason
    ) {
        public PolicyGateResult {
            policiesChecked = policiesChecked != null ? List.copyOf(policiesChecked) : List.of();
            failedPolicies = failedPolicies != null ? List.copyOf(failedPolicies) : List.of();
        }

        /**
         * Creates a passed policy gate result.
         *
         * @param policiesChecked the policies that were checked
         * @return a passed result
         */
        public static PolicyGateResult passed(List<PolicyCheck> policiesChecked) {
            return new PolicyGateResult(
                true,
                policiesChecked,
                List.of(),
                "All policies passed"
            );
        }

        /**
         * Creates a failed policy gate result.
         *
         * @param policiesChecked all policies that were checked
         * @param failedPolicies the policies that failed
         * @return a failed result
         */
        public static PolicyGateResult failed(
                List<PolicyCheck> policiesChecked, List<PolicyCheck> failedPolicies) {
            String failedNames = failedPolicies.stream()
                .map(PolicyCheck::policyId)
                .reduce((a, b) -> a + ", " + b)
                .orElse("unknown");
            return new PolicyGateResult(
                false,
                policiesChecked,
                failedPolicies,
                "Policies failed: " + failedNames
            );
        }

        /**
         * Creates a skipped policy gate result (check disabled).
         *
         * @return a skipped result
         */
        public static PolicyGateResult skipped() {
            return new PolicyGateResult(
                true,
                List.of(),
                List.of(),
                "Policy gate check disabled"
            );
        }

        /**
         * Individual policy check result.
         *
         * @param policyId the policy identifier
         * @param policyName the policy name
         * @param passed whether the policy passed
         * @param outcome the policy outcome
         */
        public record PolicyCheck(
            String policyId,
            String policyName,
            boolean passed,
            String outcome
        ) {
            public PolicyCheck {
                Objects.requireNonNull(policyId, "PolicyCheck policyId is required");
            }

            public static PolicyCheck passed(String policyId, String policyName) {
                return new PolicyCheck(policyId, policyName, true, "PASSED");
            }

            public static PolicyCheck failed(String policyId, String policyName, String outcome) {
                return new PolicyCheck(policyId, policyName, false, outcome);
            }
        }
    }
}
