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

import com.ihelio.cpg.domain.action.ActionResult;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.model.Node;
import java.util.Map;
import java.util.Objects;

/**
 * Result of executing a node.
 *
 * @param node the node that was executed
 * @param status the execution status
 * @param actionResult the result from the action handler
 * @param updatedContext the execution context after action execution
 * @param compensationAction compensation action if execution failed
 * @param error error message if execution failed
 */
public record NodeExecutionResult(
    Node node,
    Status status,
    ActionResult actionResult,
    ExecutionContext updatedContext,
    CompensationAction compensationAction,
    String error
) {

    public NodeExecutionResult {
        Objects.requireNonNull(node, "NodeExecutionResult node is required");
        Objects.requireNonNull(status, "NodeExecutionResult status is required");
    }

    /**
     * Creates a successful execution result.
     */
    public static NodeExecutionResult success(
            Node node,
            ActionResult actionResult,
            ExecutionContext updatedContext) {
        return new NodeExecutionResult(
            node,
            Status.COMPLETED,
            actionResult,
            updatedContext,
            null,
            null
        );
    }

    /**
     * Creates a pending execution result (async action).
     */
    public static NodeExecutionResult pending(Node node, ActionResult actionResult) {
        return new NodeExecutionResult(
            node,
            Status.PENDING,
            actionResult,
            null,
            null,
            null
        );
    }

    /**
     * Creates a waiting execution result (human task).
     */
    public static NodeExecutionResult waiting(Node node, ActionResult actionResult) {
        return new NodeExecutionResult(
            node,
            Status.WAITING,
            actionResult,
            null,
            null,
            null
        );
    }

    /**
     * Creates a failed execution result with compensation.
     */
    public static NodeExecutionResult failed(
            Node node,
            String error,
            CompensationAction compensation) {
        return new NodeExecutionResult(
            node,
            Status.FAILED,
            null,
            null,
            compensation,
            error
        );
    }

    /**
     * Creates a skipped execution result.
     */
    public static NodeExecutionResult skipped(Node node, String reason) {
        return new NodeExecutionResult(
            node,
            Status.SKIPPED,
            ActionResult.skipped(reason),
            null,
            null,
            reason
        );
    }

    /**
     * Checks if execution completed successfully.
     */
    public boolean isSuccess() {
        return status == Status.COMPLETED;
    }

    /**
     * Checks if execution failed.
     */
    public boolean isFailed() {
        return status == Status.FAILED;
    }

    /**
     * Checks if execution is still in progress.
     */
    public boolean isPending() {
        return status == Status.PENDING || status == Status.WAITING;
    }

    /**
     * Returns the action output if available.
     */
    public Map<String, Object> getOutput() {
        return actionResult != null ? actionResult.output() : Map.of();
    }

    /**
     * Status of node execution.
     */
    public enum Status {
        COMPLETED,
        PENDING,
        WAITING,
        FAILED,
        SKIPPED
    }
}
