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

package com.ihelio.cpg.application.assistant;

import java.time.Instant;

/**
 * Progress information for a single step in the onboarding process.
 *
 * @param stepName human-readable name of the step
 * @param nodeId the node ID in the process graph
 * @param status current status of the step
 * @param completedAt when the step was completed (null if not completed)
 * @param assignee who is responsible for the step (for human tasks)
 * @param notes additional notes or context
 */
public record StepProgress(
    String stepName,
    String nodeId,
    StepStatus status,
    Instant completedAt,
    String assignee,
    String notes
) {
    /**
     * Status of a step in the onboarding process.
     */
    public enum StepStatus {
        /** Step has been completed */
        COMPLETED,
        /** Step is currently in progress */
        IN_PROGRESS,
        /** Step is waiting to start */
        PENDING,
        /** Step is blocked by a dependency or issue */
        BLOCKED,
        /** Step was skipped */
        SKIPPED
    }

    /**
     * Creates a completed step.
     */
    public static StepProgress completed(String stepName, String nodeId, Instant completedAt) {
        return new StepProgress(stepName, nodeId, StepStatus.COMPLETED, completedAt, null, null);
    }

    /**
     * Creates an in-progress step.
     */
    public static StepProgress inProgress(String stepName, String nodeId) {
        return new StepProgress(stepName, nodeId, StepStatus.IN_PROGRESS, null, null, null);
    }

    /**
     * Creates a pending step.
     */
    public static StepProgress pending(String stepName, String nodeId) {
        return new StepProgress(stepName, nodeId, StepStatus.PENDING, null, null, null);
    }

    /**
     * Creates a blocked step.
     */
    public static StepProgress blocked(String stepName, String nodeId, String reason) {
        return new StepProgress(stepName, nodeId, StepStatus.BLOCKED, null, null, reason);
    }
}
