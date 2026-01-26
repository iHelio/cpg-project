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

package com.ihelio.cpg.interfaces.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Request to start a new process orchestration.
 *
 * <p>The orchestrator will take full control of the process execution,
 * evaluating preconditions, navigating the graph, and executing nodes
 * automatically based on the context.
 *
 * @param processGraphId the ID of the process graph to orchestrate
 * @param correlationId optional business correlation ID for tracking
 * @param clientContext client/tenant-specific context data
 * @param domainContext business domain data for the process
 */
public record StartOrchestrationRequest(
    @NotBlank(message = "Process graph ID is required")
    String processGraphId,

    String correlationId,

    @NotNull(message = "Client context is required")
    Map<String, Object> clientContext,

    @NotNull(message = "Domain context is required")
    Map<String, Object> domainContext
) {
    public StartOrchestrationRequest {
        clientContext = clientContext != null ? Map.copyOf(clientContext) : Map.of();
        domainContext = domainContext != null ? Map.copyOf(domainContext) : Map.of();
    }
}
