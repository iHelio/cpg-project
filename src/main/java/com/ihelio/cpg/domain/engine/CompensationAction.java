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

import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Node;
import java.util.Objects;

/**
 * Result record describing the compensation action to take.
 *
 * @param action the type of compensation action
 * @param targetNodeId for ALTERNATE, the node to redirect to
 * @param compensatingEdgeId for ROLLBACK, the edge to traverse for rollback
 * @param escalationNodeId for ESCALATE, the escalation node
 * @param currentRetryCount current retry attempt number
 * @param maxRetries maximum retries allowed
 * @param reason reason for the compensation
 */
public record CompensationAction(
    ActionType action,
    Node.NodeId targetNodeId,
    Edge.EdgeId compensatingEdgeId,
    Node.NodeId escalationNodeId,
    int currentRetryCount,
    int maxRetries,
    String reason
) {

    public CompensationAction {
        Objects.requireNonNull(action, "CompensationAction action is required");
    }

    /**
     * Creates a retry compensation action.
     */
    public static CompensationAction retry(int currentRetry, int maxRetries) {
        return new CompensationAction(
            ActionType.RETRY,
            null,
            null,
            null,
            currentRetry,
            maxRetries,
            "Retrying action attempt " + (currentRetry + 1) + " of " + maxRetries
        );
    }

    /**
     * Creates a rollback compensation action.
     */
    public static CompensationAction rollback(Edge.EdgeId compensatingEdgeId, String reason) {
        return new CompensationAction(
            ActionType.ROLLBACK,
            null,
            compensatingEdgeId,
            null,
            0,
            0,
            reason
        );
    }

    /**
     * Creates an alternate path compensation action.
     */
    public static CompensationAction alternate(Node.NodeId alternateNodeId, String reason) {
        return new CompensationAction(
            ActionType.ALTERNATE,
            alternateNodeId,
            null,
            null,
            0,
            0,
            reason
        );
    }

    /**
     * Creates an escalation compensation action.
     */
    public static CompensationAction escalate(Node.NodeId escalationNodeId, String reason) {
        return new CompensationAction(
            ActionType.ESCALATE,
            null,
            null,
            escalationNodeId,
            0,
            0,
            reason
        );
    }

    /**
     * Creates a skip compensation action.
     */
    public static CompensationAction skip(String reason) {
        return new CompensationAction(
            ActionType.SKIP,
            null,
            null,
            null,
            0,
            0,
            reason
        );
    }

    /**
     * Creates a fail compensation action (no recovery possible).
     */
    public static CompensationAction fail(String reason) {
        return new CompensationAction(
            ActionType.FAIL,
            null,
            null,
            null,
            0,
            0,
            reason
        );
    }

    /**
     * Checks if this action allows continued execution.
     */
    public boolean allowsContinuation() {
        return action != ActionType.FAIL;
    }

    /**
     * Checks if this is a retry action.
     */
    public boolean isRetry() {
        return action == ActionType.RETRY;
    }

    /**
     * Type of compensation action.
     */
    public enum ActionType {
        /** Retry the failed action. */
        RETRY,
        /** Execute rollback via compensating edge. */
        ROLLBACK,
        /** Take an alternate path to a different node. */
        ALTERNATE,
        /** Escalate to human review or special handling. */
        ESCALATE,
        /** Skip the failed action and continue. */
        SKIP,
        /** Fail the process - no recovery possible. */
        FAIL
    }
}
