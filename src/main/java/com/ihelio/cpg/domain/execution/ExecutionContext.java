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

package com.ihelio.cpg.domain.execution;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ExecutionContext maintains all state needed for process execution and guard evaluation.
 *
 * <p>The context is the source of truth for all node availability and edge guard
 * evaluation. It accumulates state from executed actions and received events.
 *
 * <p>Context is structured into compartments:
 * <ul>
 *   <li><b>clientContext</b>: Tenant-specific configuration, loaded at process start</li>
 *   <li><b>domainContext</b>: Business entity data (employee, offer, job details)</li>
 *   <li><b>accumulatedState</b>: Results from executed nodes</li>
 *   <li><b>eventHistory</b>: Events received during execution</li>
 *   <li><b>obligations</b>: Pending SLAs and deadlines</li>
 * </ul>
 */
public class ExecutionContext {

    private final Map<String, Object> clientContext;
    private final Map<String, Object> domainContext;
    private final Map<String, Object> accumulatedState;
    private final List<ReceivedEvent> eventHistory;
    private final List<Obligation> obligations;

    private ExecutionContext(Builder builder) {
        this.clientContext = Map.copyOf(builder.clientContext);
        this.domainContext = Map.copyOf(builder.domainContext);
        this.accumulatedState = Map.copyOf(builder.accumulatedState);
        this.eventHistory = List.copyOf(builder.eventHistory);
        this.obligations = List.copyOf(builder.obligations);
    }

    public Map<String, Object> clientContext() {
        return clientContext;
    }

    public Map<String, Object> domainContext() {
        return domainContext;
    }

    public Map<String, Object> accumulatedState() {
        return accumulatedState;
    }

    public List<ReceivedEvent> eventHistory() {
        return eventHistory;
    }

    public List<Obligation> obligations() {
        return obligations;
    }

    /**
     * Converts the entire context to a flat map for FEEL expression evaluation.
     *
     * @return map containing all context data
     */
    public Map<String, Object> toFeelContext() {
        Map<String, Object> feelContext = new HashMap<>();
        feelContext.put("client", clientContext);
        feelContext.put("domain", domainContext);
        feelContext.put("state", accumulatedState);
        feelContext.put("events", eventHistory);
        feelContext.put("obligations", obligations);

        // Also add flattened access for convenience
        feelContext.putAll(clientContext);
        feelContext.putAll(domainContext);
        feelContext.putAll(accumulatedState);

        return feelContext;
    }

    /**
     * Gets a value from the context by path (dot notation).
     *
     * @param path the path to the value (e.g., "employee.location")
     * @return the value, or null if not found
     */
    @SuppressWarnings("unchecked")
    public Object getValue(String path) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = toFeelContext();

        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (next instanceof Map) {
                current = (Map<String, Object>) next;
            } else {
                return null;
            }
        }

        return current.get(parts[parts.length - 1]);
    }

    /**
     * Checks if an event of the given type has been received.
     *
     * @param eventType the event type
     * @return true if the event has been received
     */
    public boolean hasReceivedEvent(String eventType) {
        return eventHistory.stream()
            .anyMatch(e -> e.eventType().equals(eventType));
    }

    /**
     * Returns a new context with updated accumulated state.
     *
     * @param key the state key
     * @param value the state value
     * @return new ExecutionContext with updated state
     */
    public ExecutionContext withState(String key, Object value) {
        Map<String, Object> newState = new HashMap<>(accumulatedState);
        newState.put(key, value);
        return toBuilder().accumulatedState(newState).build();
    }

    /**
     * Returns a new context with an additional event.
     *
     * @param event the received event
     * @return new ExecutionContext with the event added
     */
    public ExecutionContext withEvent(ReceivedEvent event) {
        var newEvents = new java.util.ArrayList<>(eventHistory);
        newEvents.add(event);
        return toBuilder().eventHistory(newEvents).build();
    }

    /**
     * Returns a new context with an additional obligation.
     *
     * @param obligation the obligation
     * @return new ExecutionContext with the obligation added
     */
    public ExecutionContext withObligation(Obligation obligation) {
        var newObligations = new java.util.ArrayList<>(obligations);
        newObligations.add(obligation);
        return toBuilder().obligations(newObligations).build();
    }

    public Builder toBuilder() {
        return new Builder()
            .clientContext(new HashMap<>(clientContext))
            .domainContext(new HashMap<>(domainContext))
            .accumulatedState(new HashMap<>(accumulatedState))
            .eventHistory(new java.util.ArrayList<>(eventHistory))
            .obligations(new java.util.ArrayList<>(obligations));
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * An event received during process execution.
     */
    public record ReceivedEvent(
        String eventType,
        String eventId,
        Instant receivedAt,
        Map<String, Object> payload
    ) {
        public ReceivedEvent {
            Objects.requireNonNull(eventType, "ReceivedEvent eventType is required");
            Objects.requireNonNull(receivedAt, "ReceivedEvent receivedAt is required");
            payload = payload != null ? Map.copyOf(payload) : Map.of();
        }
    }

    /**
     * An obligation or SLA that must be fulfilled.
     */
    public record Obligation(
        String id,
        String description,
        String sourceNodeId,
        Instant deadline,
        ObligationStatus status
    ) {
        public Obligation {
            Objects.requireNonNull(id, "Obligation id is required");
            Objects.requireNonNull(status, "Obligation status is required");
        }

        public boolean isOverdue() {
            return deadline != null && Instant.now().isAfter(deadline);
        }
    }

    /**
     * Status of an obligation.
     */
    public enum ObligationStatus {
        PENDING,
        FULFILLED,
        BREACHED,
        WAIVED
    }

    /**
     * Builder for ExecutionContext.
     */
    public static class Builder {
        private Map<String, Object> clientContext = new HashMap<>();
        private Map<String, Object> domainContext = new HashMap<>();
        private Map<String, Object> accumulatedState = new HashMap<>();
        private List<ReceivedEvent> eventHistory = new java.util.ArrayList<>();
        private List<Obligation> obligations = new java.util.ArrayList<>();

        public Builder clientContext(Map<String, Object> clientContext) {
            this.clientContext = clientContext;
            return this;
        }

        public Builder domainContext(Map<String, Object> domainContext) {
            this.domainContext = domainContext;
            return this;
        }

        public Builder accumulatedState(Map<String, Object> accumulatedState) {
            this.accumulatedState = accumulatedState;
            return this;
        }

        public Builder eventHistory(List<ReceivedEvent> eventHistory) {
            this.eventHistory = eventHistory;
            return this;
        }

        public Builder obligations(List<Obligation> obligations) {
            this.obligations = obligations;
            return this;
        }

        public Builder addClientContext(String key, Object value) {
            this.clientContext.put(key, value);
            return this;
        }

        public Builder addDomainContext(String key, Object value) {
            this.domainContext.put(key, value);
            return this;
        }

        public ExecutionContext build() {
            return new ExecutionContext(this);
        }
    }
}
