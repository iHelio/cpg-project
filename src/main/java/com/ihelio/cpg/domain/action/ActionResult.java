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

package com.ihelio.cpg.domain.action;

import java.util.Map;
import java.util.Objects;

/**
 * Result of executing an action.
 *
 * @param status the execution status
 * @param output data produced by the action, to be merged into context
 * @param error error message if the action failed
 * @param retryable whether the action can be retried on failure
 */
public record ActionResult(
    Status status,
    Map<String, Object> output,
    String error,
    boolean retryable
) {

    public ActionResult {
        Objects.requireNonNull(status, "ActionResult status is required");
        output = output != null ? Map.copyOf(output) : Map.of();
    }

    /**
     * Creates a successful action result with output data.
     */
    public static ActionResult success(Map<String, Object> output) {
        return new ActionResult(Status.COMPLETED, output, null, false);
    }

    /**
     * Creates a successful action result with no output.
     */
    public static ActionResult success() {
        return new ActionResult(Status.COMPLETED, Map.of(), null, false);
    }

    /**
     * Creates an action result indicating the action is pending (async).
     */
    public static ActionResult pending() {
        return new ActionResult(Status.PENDING, Map.of(), null, false);
    }

    /**
     * Creates an action result indicating waiting for external input.
     */
    public static ActionResult waiting() {
        return new ActionResult(Status.WAITING, Map.of(), null, false);
    }

    /**
     * Creates a failed action result.
     */
    public static ActionResult failure(String error, boolean retryable) {
        return new ActionResult(Status.FAILED, Map.of(), error, retryable);
    }

    /**
     * Creates a skipped action result.
     */
    public static ActionResult skipped(String reason) {
        return new ActionResult(Status.SKIPPED, Map.of(), reason, false);
    }

    /**
     * Checks if the action completed successfully.
     */
    public boolean isSuccess() {
        return status == Status.COMPLETED;
    }

    /**
     * Checks if the action failed.
     */
    public boolean isFailed() {
        return status == Status.FAILED;
    }

    /**
     * Checks if the action is still in progress.
     */
    public boolean isPending() {
        return status == Status.PENDING || status == Status.WAITING;
    }

    /**
     * Status of an action execution.
     */
    public enum Status {
        /** Action completed successfully. */
        COMPLETED,
        /** Action is pending (async execution). */
        PENDING,
        /** Action is waiting for external input (human task). */
        WAITING,
        /** Action failed. */
        FAILED,
        /** Action was skipped. */
        SKIPPED
    }
}
