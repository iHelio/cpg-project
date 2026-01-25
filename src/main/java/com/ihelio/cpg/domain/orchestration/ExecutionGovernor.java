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
import com.ihelio.cpg.domain.model.Node;

/**
 * ExecutionGovernor is the port for pre-execution governance checks.
 *
 * <p>Before any action can produce side effects, it must pass all governance gates:
 * <ul>
 *   <li><b>Idempotency</b>: Has this exact action already been executed?</li>
 *   <li><b>Authorization</b>: Is the current context authorized to execute?</li>
 *   <li><b>Policy Gate</b>: Do all applicable policies allow execution?</li>
 * </ul>
 *
 * <p>The governance layer is the final checkpoint before execution. If any check fails,
 * the action is blocked and a decision trace is recorded with the rejection reason.
 */
public interface ExecutionGovernor {

    /**
     * Checks if this exact action has already been executed (idempotency check).
     *
     * <p>Prevents duplicate execution of the same action in the same context.
     * Uses a combination of instance ID, node ID, and context hash to generate
     * a unique idempotency key.
     *
     * @param instance the process instance
     * @param node the node to execute
     * @param context the runtime context
     * @return the idempotency check result
     */
    GovernanceResult.IdempotencyResult checkIdempotency(
        ProcessInstance instance,
        Node node,
        RuntimeContext context
    );

    /**
     * Checks if execution is authorized in the current context.
     *
     * <p>Verifies that the current principal has the required permissions
     * to execute the action defined by the node.
     *
     * @param instance the process instance
     * @param node the node to execute
     * @param context the runtime context
     * @return the authorization check result
     */
    GovernanceResult.AuthorizationResult checkAuthorization(
        ProcessInstance instance,
        Node node,
        RuntimeContext context
    );

    /**
     * Performs the final policy gate check before producing side effects.
     *
     * <p>This is a last-line-of-defense policy evaluation that may include
     * runtime policy checks not covered by node policy gates.
     *
     * @param instance the process instance
     * @param node the node to execute
     * @param context the runtime context
     * @return the policy gate check result
     */
    GovernanceResult.PolicyGateResult checkPolicyGate(
        ProcessInstance instance,
        Node node,
        RuntimeContext context
    );

    /**
     * Performs all governance checks and returns a combined result.
     *
     * <p>This is the primary method that should be called before execution.
     * It combines idempotency, authorization, and policy gate checks into
     * a single result.
     *
     * <p>All three checks must pass for the result to be approved.
     *
     * @param instance the process instance
     * @param node the node to execute
     * @param context the runtime context
     * @return the combined governance result
     */
    GovernanceResult enforce(
        ProcessInstance instance,
        Node node,
        RuntimeContext context
    );

    /**
     * Records that an action has been executed (for idempotency tracking).
     *
     * <p>This should be called after successful execution to mark the action
     * as completed and prevent duplicate execution.
     *
     * @param instance the process instance
     * @param node the executed node
     * @param context the runtime context
     * @param executionId the unique execution ID
     */
    void recordExecution(
        ProcessInstance instance,
        Node node,
        RuntimeContext context,
        String executionId
    );
}
