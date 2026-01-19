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
import java.util.Map;

/**
 * Request to execute a node within a process instance.
 *
 * @param nodeId the ID of the node to execute
 * @param additionalContext optional additional context data for execution
 */
public record ExecuteNodeRequest(
    @NotBlank(message = "Node ID is required")
    String nodeId,

    Map<String, Object> additionalContext
) {
    public ExecuteNodeRequest {
        additionalContext = additionalContext != null ? Map.copyOf(additionalContext) : Map.of();
    }
}
