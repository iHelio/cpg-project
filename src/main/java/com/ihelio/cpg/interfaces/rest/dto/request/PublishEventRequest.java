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
 * Request to publish an event to process instances.
 *
 * @param eventType the type of event (e.g., "background.check.completed")
 * @param correlationId optional correlation ID to target specific instances
 * @param payload event payload data
 * @param sourceType the source type (EXTERNAL, USER, SYSTEM)
 * @param sourceIdentifier optional identifier for the source
 */
public record PublishEventRequest(
    @NotBlank(message = "Event type is required")
    String eventType,

    String correlationId,

    @NotNull(message = "Payload is required")
    Map<String, Object> payload,

    @NotBlank(message = "Source type is required")
    String sourceType,

    String sourceIdentifier
) {
    public PublishEventRequest {
        payload = payload != null ? Map.copyOf(payload) : Map.of();
    }

    /**
     * Valid source types for events.
     */
    public enum SourceType {
        EXTERNAL,
        USER,
        SYSTEM,
        NODE
    }
}
