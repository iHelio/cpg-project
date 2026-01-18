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

package com.ihelio.cpg.domain.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A domain event within the process execution system.
 *
 * <p>Events can be:
 * <ul>
 *   <li>Emitted by nodes during execution</li>
 *   <li>Received from external systems</li>
 *   <li>Used to trigger or re-evaluate edges</li>
 *   <li>Recorded in the execution context history</li>
 * </ul>
 *
 * @param eventId unique identifier for this event instance
 * @param eventType the type/name of the event
 * @param source the source that generated the event
 * @param correlationId correlation ID for matching to process instances
 * @param timestamp when the event occurred
 * @param payload the event data
 */
public record ProcessEvent(
    String eventId,
    String eventType,
    EventSource source,
    String correlationId,
    Instant timestamp,
    Map<String, Object> payload
) {

    public ProcessEvent {
        Objects.requireNonNull(eventId, "ProcessEvent eventId is required");
        Objects.requireNonNull(eventType, "ProcessEvent eventType is required");
        Objects.requireNonNull(source, "ProcessEvent source is required");
        Objects.requireNonNull(timestamp, "ProcessEvent timestamp is required");
        payload = payload != null ? Map.copyOf(payload) : Map.of();
    }

    /**
     * Creates an event with auto-generated ID and current timestamp.
     */
    public static ProcessEvent of(String eventType, EventSource source, Map<String, Object> payload) {
        return new ProcessEvent(
            UUID.randomUUID().toString(),
            eventType,
            source,
            null,
            Instant.now(),
            payload
        );
    }

    /**
     * Creates an event with a correlation ID.
     */
    public static ProcessEvent of(
            String eventType,
            EventSource source,
            String correlationId,
            Map<String, Object> payload) {
        return new ProcessEvent(
            UUID.randomUUID().toString(),
            eventType,
            source,
            correlationId,
            Instant.now(),
            payload
        );
    }

    /**
     * Creates a node execution event.
     */
    public static ProcessEvent nodeExecuted(String nodeId, String processInstanceId, Map<String, Object> output) {
        return of(
            "node.executed",
            EventSource.node(nodeId),
            processInstanceId,
            Map.of("nodeId", nodeId, "output", output)
        );
    }

    /**
     * Creates a process started event.
     */
    public static ProcessEvent processStarted(String processInstanceId, String processGraphId) {
        return of(
            "process.started",
            EventSource.system(),
            processInstanceId,
            Map.of("processGraphId", processGraphId)
        );
    }

    /**
     * Creates a process completed event.
     */
    public static ProcessEvent processCompleted(String processInstanceId) {
        return of(
            "process.completed",
            EventSource.system(),
            processInstanceId,
            Map.of()
        );
    }

    /**
     * Returns a payload value by key.
     */
    @SuppressWarnings("unchecked")
    public <T> T getPayloadValue(String key) {
        return (T) payload.get(key);
    }

    /**
     * Source of a process event.
     */
    public record EventSource(
        SourceType type,
        String identifier
    ) {
        public EventSource {
            Objects.requireNonNull(type, "EventSource type is required");
        }

        public static EventSource node(String nodeId) {
            return new EventSource(SourceType.NODE, nodeId);
        }

        public static EventSource external(String systemName) {
            return new EventSource(SourceType.EXTERNAL, systemName);
        }

        public static EventSource system() {
            return new EventSource(SourceType.SYSTEM, "engine");
        }

        public static EventSource user(String userId) {
            return new EventSource(SourceType.USER, userId);
        }
    }

    /**
     * Type of event source.
     */
    public enum SourceType {
        NODE,
        EXTERNAL,
        SYSTEM,
        USER
    }
}
