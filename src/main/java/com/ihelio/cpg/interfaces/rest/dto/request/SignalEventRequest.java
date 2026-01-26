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
import java.util.List;
import java.util.Map;

/**
 * Request to signal an event to the orchestrator.
 *
 * <p>Events trigger reevaluation of nodes and edges. The orchestrator
 * will adapt execution paths dynamically based on the event.
 *
 * @param eventType the type of event (e.g., "DataChange", "Approval", "NodeCompleted")
 * @param correlationId correlation ID to match to process instances
 * @param instanceId optional specific instance ID
 * @param nodeId optional node ID (for node-specific events)
 * @param payload event-specific data
 */
public record SignalEventRequest(
    @NotBlank(message = "Event type is required")
    String eventType,

    String correlationId,

    String instanceId,

    String nodeId,

    Map<String, Object> payload
) {
    public SignalEventRequest {
        payload = payload != null ? Map.copyOf(payload) : Map.of();
    }

    /**
     * Event types supported by the orchestrator.
     */
    public enum EventType {
        DATA_CHANGE,
        APPROVAL,
        REJECTION,
        NODE_COMPLETED,
        NODE_FAILED,
        TIMER_EXPIRED,
        POLICY_UPDATE
    }
}
