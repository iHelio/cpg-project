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

import com.ihelio.cpg.domain.execution.ProcessInstance;
import java.time.Instant;
import java.util.List;

/**
 * Full response for a process instance including execution history.
 *
 * @param id instance identifier
 * @param processGraphId the process graph ID
 * @param processGraphVersion the graph version being executed
 * @param correlationId business correlation ID
 * @param status current status
 * @param startedAt when the instance started
 * @param completedAt when the instance completed (if applicable)
 * @param activeNodeIds IDs of currently active nodes
 * @param pendingEdgeIds IDs of edges awaiting activation
 * @param nodeExecutions history of node executions
 * @param context current execution context
 */
public record ProcessInstanceResponse(
    String id,
    String processGraphId,
    int processGraphVersion,
    String correlationId,
    String status,
    Instant startedAt,
    Instant completedAt,
    List<String> activeNodeIds,
    List<String> pendingEdgeIds,
    List<NodeExecutionResponse> nodeExecutions,
    ExecutionContextResponse context
) {
    /**
     * Record of a node execution.
     */
    public record NodeExecutionResponse(
        String nodeId,
        String status,
        Instant startedAt,
        Instant completedAt,
        Object result,
        String error
    ) {
        public static NodeExecutionResponse from(ProcessInstance.NodeExecution execution) {
            return new NodeExecutionResponse(
                execution.nodeId().value(),
                execution.status().name(),
                execution.startedAt(),
                execution.completedAt(),
                execution.result(),
                execution.error()
            );
        }
    }

    /**
     * Creates a full response from a ProcessInstance domain object.
     */
    public static ProcessInstanceResponse from(ProcessInstance instance) {
        return new ProcessInstanceResponse(
            instance.id().value(),
            instance.processGraphId().value(),
            instance.processGraphVersion(),
            instance.correlationId(),
            instance.status().name(),
            instance.startedAt(),
            instance.completedAt().orElse(null),
            instance.activeNodeIds().stream().map(id -> id.value()).toList(),
            instance.pendingEdgeIds().stream().map(id -> id.value()).toList(),
            instance.nodeExecutions().stream()
                .map(NodeExecutionResponse::from)
                .toList(),
            ExecutionContextResponse.from(instance.context())
        );
    }
}
