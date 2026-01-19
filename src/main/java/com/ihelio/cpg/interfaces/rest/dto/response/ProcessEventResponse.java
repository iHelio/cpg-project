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

import com.ihelio.cpg.domain.event.ProcessEvent;
import java.time.Instant;
import java.util.Map;

/**
 * Response for a process event.
 *
 * @param eventId unique event identifier
 * @param eventType type of event
 * @param correlationId business correlation ID
 * @param timestamp when the event occurred
 * @param sourceType source type (EXTERNAL, USER, SYSTEM, NODE)
 * @param sourceIdentifier source identifier
 * @param payload event payload data
 */
public record ProcessEventResponse(
    String eventId,
    String eventType,
    String correlationId,
    Instant timestamp,
    String sourceType,
    String sourceIdentifier,
    Map<String, Object> payload
) {
    /**
     * Creates a response from a ProcessEvent domain object.
     */
    public static ProcessEventResponse from(ProcessEvent event) {
        return new ProcessEventResponse(
            event.eventId(),
            event.eventType(),
            event.correlationId(),
            event.timestamp(),
            event.source().type().name(),
            event.source().identifier(),
            event.payload()
        );
    }
}
