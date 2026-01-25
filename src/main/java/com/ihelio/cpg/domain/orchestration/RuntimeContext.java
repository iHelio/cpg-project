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

package com.ihelio.cpg.domain.orchestration;

import com.ihelio.cpg.domain.execution.ExecutionContext;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RuntimeContext is the authoritative context assembled for orchestration decisions.
 *
 * <p>It combines multiple context sources into a single, immutable snapshot that forms
 * the basis for all node eligibility, edge traversability, and governance decisions.
 *
 * <p>Context compartments:
 * <ul>
 *   <li><b>client</b>: Tenant-specific configuration (client config repository)</li>
 *   <li><b>domain</b>: Domain knowledge from the process instance</li>
 *   <li><b>entity</b>: Entity state from accumulated execution state</li>
 *   <li><b>operational</b>: Current system state and pending obligations</li>
 *   <li><b>events</b>: Received event history for correlation</li>
 * </ul>
 *
 * @param clientContext tenant-specific configuration
 * @param domainContext domain knowledge from process instance
 * @param entityState entity state from accumulated state
 * @param operationalContext current system state and pending obligations
 * @param receivedEvents received event history
 * @param assembledAt timestamp when context was assembled
 */
public record RuntimeContext(
    Map<String, Object> clientContext,
    Map<String, Object> domainContext,
    Map<String, Object> entityState,
    OperationalContext operationalContext,
    List<ExecutionContext.ReceivedEvent> receivedEvents,
    Instant assembledAt
) {

    public RuntimeContext {
        Objects.requireNonNull(assembledAt, "RuntimeContext assembledAt is required");
        clientContext = clientContext != null ? Map.copyOf(clientContext) : Map.of();
        domainContext = domainContext != null ? Map.copyOf(domainContext) : Map.of();
        entityState = entityState != null ? Map.copyOf(entityState) : Map.of();
        operationalContext = operationalContext != null ? operationalContext : OperationalContext.empty();
        receivedEvents = receivedEvents != null ? List.copyOf(receivedEvents) : List.of();
    }

    /**
     * Creates RuntimeContext from an ExecutionContext.
     *
     * @param executionContext the execution context to convert
     * @return a new RuntimeContext
     */
    public static RuntimeContext fromExecutionContext(ExecutionContext executionContext) {
        return new RuntimeContext(
            executionContext.clientContext(),
            executionContext.domainContext(),
            executionContext.accumulatedState(),
            OperationalContext.fromObligations(executionContext.obligations()),
            executionContext.eventHistory(),
            Instant.now()
        );
    }

    /**
     * Creates an empty RuntimeContext for testing.
     */
    public static RuntimeContext empty() {
        return new RuntimeContext(
            Map.of(),
            Map.of(),
            Map.of(),
            OperationalContext.empty(),
            List.of(),
            Instant.now()
        );
    }

    /**
     * Converts this context to a flat map for FEEL expression evaluation.
     *
     * @return map containing all context data
     */
    public Map<String, Object> toFeelContext() {
        Map<String, Object> feelContext = new HashMap<>();
        feelContext.put("client", clientContext);
        feelContext.put("domain", domainContext);
        feelContext.put("entity", entityState);
        feelContext.put("state", entityState); // Alias for backward compatibility
        feelContext.put("operational", operationalContext.toMap());
        feelContext.put("events", receivedEvents);

        // Flattened access for convenience
        feelContext.putAll(clientContext);
        feelContext.putAll(domainContext);
        feelContext.putAll(entityState);

        return feelContext;
    }

    /**
     * Gets a value from the context by path (dot notation).
     *
     * @param path the path to the value (e.g., "domain.employee.location")
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
        return receivedEvents.stream()
            .anyMatch(e -> e.eventType().equals(eventType));
    }

    /**
     * Returns a new context with updated client configuration.
     *
     * @param key the client config key
     * @param value the client config value
     * @return new RuntimeContext with updated client context
     */
    public RuntimeContext withClientContext(String key, Object value) {
        Map<String, Object> newClientContext = new HashMap<>(clientContext);
        newClientContext.put(key, value);
        return new RuntimeContext(
            newClientContext,
            domainContext,
            entityState,
            operationalContext,
            receivedEvents,
            Instant.now()
        );
    }

    /**
     * Returns a new context with updated entity state.
     *
     * @param key the state key
     * @param value the state value
     * @return new RuntimeContext with updated entity state
     */
    public RuntimeContext withEntityState(String key, Object value) {
        Map<String, Object> newEntityState = new HashMap<>(entityState);
        newEntityState.put(key, value);
        return new RuntimeContext(
            clientContext,
            domainContext,
            newEntityState,
            operationalContext,
            receivedEvents,
            Instant.now()
        );
    }

    /**
     * Converts this context to an ExecutionContext for use with existing evaluators.
     *
     * @return an ExecutionContext representation
     */
    public ExecutionContext toExecutionContext() {
        return ExecutionContext.builder()
            .clientContext(new HashMap<>(clientContext))
            .domainContext(new HashMap<>(domainContext))
            .accumulatedState(new HashMap<>(entityState))
            .eventHistory(new java.util.ArrayList<>(receivedEvents))
            .obligations(operationalContext.obligations())
            .build();
    }

    /**
     * Operational context containing system state and pending obligations.
     */
    public record OperationalContext(
        SystemState systemState,
        List<ExecutionContext.Obligation> obligations
    ) {
        public OperationalContext {
            systemState = systemState != null ? systemState : SystemState.NORMAL;
            obligations = obligations != null ? List.copyOf(obligations) : List.of();
        }

        public static OperationalContext empty() {
            return new OperationalContext(SystemState.NORMAL, List.of());
        }

        public static OperationalContext fromObligations(List<ExecutionContext.Obligation> obligations) {
            return new OperationalContext(SystemState.NORMAL, obligations);
        }

        public Map<String, Object> toMap() {
            return Map.of(
                "systemState", systemState.name(),
                "obligations", obligations,
                "hasOverdueObligations", hasOverdueObligations()
            );
        }

        public boolean hasOverdueObligations() {
            return obligations.stream().anyMatch(ExecutionContext.Obligation::isOverdue);
        }
    }

    /**
     * System state affecting operational decisions.
     */
    public enum SystemState {
        NORMAL,
        DEGRADED,
        MAINTENANCE,
        EMERGENCY
    }

    /**
     * Builder for RuntimeContext.
     */
    public static class Builder {
        private Map<String, Object> clientContext = new HashMap<>();
        private Map<String, Object> domainContext = new HashMap<>();
        private Map<String, Object> entityState = new HashMap<>();
        private OperationalContext operationalContext = OperationalContext.empty();
        private List<ExecutionContext.ReceivedEvent> receivedEvents = new java.util.ArrayList<>();

        public Builder clientContext(Map<String, Object> clientContext) {
            this.clientContext = new HashMap<>(clientContext);
            return this;
        }

        public Builder domainContext(Map<String, Object> domainContext) {
            this.domainContext = new HashMap<>(domainContext);
            return this;
        }

        public Builder entityState(Map<String, Object> entityState) {
            this.entityState = new HashMap<>(entityState);
            return this;
        }

        public Builder operationalContext(OperationalContext operationalContext) {
            this.operationalContext = operationalContext;
            return this;
        }

        public Builder receivedEvents(List<ExecutionContext.ReceivedEvent> receivedEvents) {
            this.receivedEvents = new java.util.ArrayList<>(receivedEvents);
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

        public Builder addEntityState(String key, Object value) {
            this.entityState.put(key, value);
            return this;
        }

        public RuntimeContext build() {
            return new RuntimeContext(
                clientContext,
                domainContext,
                entityState,
                operationalContext,
                receivedEvents,
                Instant.now()
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
