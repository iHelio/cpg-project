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

package com.ihelio.cpg.interfaces.rest.dto.response;

import com.ihelio.cpg.domain.engine.CompensationAction;
import com.ihelio.cpg.domain.engine.NodeExecutionResult;
import java.util.Map;

/**
 * Response for a node execution result.
 *
 * @param nodeId the executed node ID
 * @param nodeName the executed node name
 * @param status execution status (SUCCESS, FAILED, SKIPPED, PENDING)
 * @param output output data from the action
 * @param error error message if failed
 * @param compensation compensation action if applicable
 */
public record NodeExecutionResultResponse(
    String nodeId,
    String nodeName,
    String status,
    Map<String, Object> output,
    String error,
    CompensationResponse compensation
) {
    /**
     * Compensation action details.
     */
    public record CompensationResponse(
        String action,
        String reason,
        int currentRetry,
        int maxRetries,
        String targetNodeId,
        String escalationNodeId
    ) {
        public static CompensationResponse from(CompensationAction compensation) {
            if (compensation == null) {
                return null;
            }
            return new CompensationResponse(
                compensation.action().name(),
                compensation.reason(),
                compensation.currentRetryCount(),
                compensation.maxRetries(),
                compensation.targetNodeId() != null
                    ? compensation.targetNodeId().value() : null,
                compensation.escalationNodeId() != null
                    ? compensation.escalationNodeId().value() : null
            );
        }
    }

    /**
     * Creates a response from a NodeExecutionResult domain object.
     */
    public static NodeExecutionResultResponse from(NodeExecutionResult result) {
        return new NodeExecutionResultResponse(
            result.node().id().value(),
            result.node().name(),
            result.status().name(),
            result.actionResult() != null ? result.actionResult().output() : Map.of(),
            result.error(),
            CompensationResponse.from(result.compensationAction())
        );
    }
}
