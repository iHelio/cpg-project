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

/**
 * Summary response for process instance list operations.
 *
 * @param id instance identifier
 * @param processGraphId the process graph ID
 * @param processGraphVersion the graph version being executed
 * @param correlationId business correlation ID
 * @param status current status
 * @param startedAt when the instance started
 * @param completedAt when the instance completed (if applicable)
 * @param activeNodeCount number of currently active nodes
 */
public record ProcessInstanceSummaryResponse(
    String id,
    String processGraphId,
    int processGraphVersion,
    String correlationId,
    String status,
    Instant startedAt,
    Instant completedAt,
    int activeNodeCount
) {
    /**
     * Creates a summary response from a ProcessInstance domain object.
     */
    public static ProcessInstanceSummaryResponse from(ProcessInstance instance) {
        return new ProcessInstanceSummaryResponse(
            instance.id().value(),
            instance.processGraphId().value(),
            instance.processGraphVersion(),
            instance.correlationId(),
            instance.status().name(),
            instance.startedAt(),
            instance.completedAt().orElse(null),
            instance.activeNodeIds().size()
        );
    }
}
