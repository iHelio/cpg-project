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

package com.ihelio.cpg.application.orchestration;

import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.orchestration.RuntimeContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * ContextAssembler builds the authoritative RuntimeContext from all available sources.
 *
 * <p>The context assembly process:
 * <ol>
 *   <li>Load client configuration from the client config repository</li>
 *   <li>Extract domain context from the process instance</li>
 *   <li>Build entity state from accumulated execution state</li>
 *   <li>Assemble operational context (system state, obligations)</li>
 *   <li>Include received event history</li>
 * </ol>
 *
 * <p>The assembled context is immutable and represents the authoritative state
 * at the moment of assembly. All orchestration decisions are based on this snapshot.
 */
public class ContextAssembler {

    private final Function<String, Optional<Map<String, Object>>> clientConfigLoader;

    /**
     * Creates a ContextAssembler with a client config loader.
     *
     * @param clientConfigLoader function to load client configuration by tenant ID
     */
    public ContextAssembler(Function<String, Optional<Map<String, Object>>> clientConfigLoader) {
        this.clientConfigLoader = Objects.requireNonNull(clientConfigLoader,
            "clientConfigLoader is required");
    }

    /**
     * Creates a ContextAssembler with a default (empty) client config loader.
     */
    public ContextAssembler() {
        this(tenantId -> Optional.of(Map.of()));
    }

    /**
     * Assembles the runtime context for a process instance.
     *
     * @param instance the process instance
     * @param tenantId optional tenant ID for loading client config
     * @return the assembled runtime context
     */
    public RuntimeContext assemble(ProcessInstance instance, String tenantId) {
        Objects.requireNonNull(instance, "instance is required");

        // Load client configuration
        Map<String, Object> clientContext = loadClientConfig(tenantId, instance);

        // Extract domain context from instance
        Map<String, Object> domainContext = extractDomainContext(instance);

        // Build entity state from accumulated state
        Map<String, Object> entityState = extractEntityState(instance);

        // Assemble operational context
        RuntimeContext.OperationalContext operationalContext = assembleOperationalContext(instance);

        return new RuntimeContext(
            clientContext,
            domainContext,
            entityState,
            operationalContext,
            instance.context().eventHistory(),
            java.time.Instant.now()
        );
    }

    /**
     * Assembles the runtime context from an existing execution context.
     *
     * <p>This is useful when you already have an ExecutionContext and want to
     * convert it to a RuntimeContext for orchestration.
     *
     * @param executionContext the execution context
     * @return the assembled runtime context
     */
    public RuntimeContext assembleFromExecutionContext(ExecutionContext executionContext) {
        Objects.requireNonNull(executionContext, "executionContext is required");
        return RuntimeContext.fromExecutionContext(executionContext);
    }

    /**
     * Refreshes the context with latest client configuration.
     *
     * @param context the current runtime context
     * @param tenantId the tenant ID
     * @return a new context with refreshed client configuration
     */
    public RuntimeContext refreshClientConfig(RuntimeContext context, String tenantId) {
        Map<String, Object> freshClientConfig = clientConfigLoader.apply(tenantId)
            .orElse(context.clientContext());

        return new RuntimeContext(
            freshClientConfig,
            context.domainContext(),
            context.entityState(),
            context.operationalContext(),
            context.receivedEvents(),
            java.time.Instant.now()
        );
    }

    /**
     * Enriches the context with additional domain data.
     *
     * @param context the current runtime context
     * @param additionalData additional domain data to include
     * @return a new context with enriched domain data
     */
    public RuntimeContext enrichDomainContext(RuntimeContext context,
            Map<String, Object> additionalData) {
        Map<String, Object> enrichedDomain = new HashMap<>(context.domainContext());
        enrichedDomain.putAll(additionalData);

        return new RuntimeContext(
            context.clientContext(),
            enrichedDomain,
            context.entityState(),
            context.operationalContext(),
            context.receivedEvents(),
            java.time.Instant.now()
        );
    }

    /**
     * Updates the context with new entity state from node execution.
     *
     * @param context the current runtime context
     * @param nodeId the node that produced the result
     * @param result the execution result
     * @return a new context with updated entity state
     */
    @SuppressWarnings("unchecked")
    public RuntimeContext updateEntityState(RuntimeContext context, String nodeId, Object result) {
        Map<String, Object> updatedState = new HashMap<>(context.entityState());

        // Store result under the node ID
        updatedState.put(nodeId, result);

        // If result is a map, also merge its contents at the top level for convenience
        if (result instanceof Map) {
            updatedState.putAll((Map<String, Object>) result);
        }

        return new RuntimeContext(
            context.clientContext(),
            context.domainContext(),
            updatedState,
            context.operationalContext(),
            context.receivedEvents(),
            java.time.Instant.now()
        );
    }

    /**
     * Adds a received event to the context.
     *
     * @param context the current runtime context
     * @param event the received event
     * @return a new context with the event added
     */
    public RuntimeContext addEvent(RuntimeContext context, ExecutionContext.ReceivedEvent event) {
        var updatedEvents = new java.util.ArrayList<>(context.receivedEvents());
        updatedEvents.add(event);

        return new RuntimeContext(
            context.clientContext(),
            context.domainContext(),
            context.entityState(),
            context.operationalContext(),
            updatedEvents,
            java.time.Instant.now()
        );
    }

    private Map<String, Object> loadClientConfig(String tenantId, ProcessInstance instance) {
        if (tenantId != null && !tenantId.isBlank()) {
            return clientConfigLoader.apply(tenantId).orElse(Map.of());
        }

        // Fall back to client context from instance if available
        return new HashMap<>(instance.context().clientContext());
    }

    private Map<String, Object> extractDomainContext(ProcessInstance instance) {
        // Domain context comes from the execution context
        return new HashMap<>(instance.context().domainContext());
    }

    private Map<String, Object> extractEntityState(ProcessInstance instance) {
        // Entity state is the accumulated state from executed nodes
        return new HashMap<>(instance.context().accumulatedState());
    }

    private RuntimeContext.OperationalContext assembleOperationalContext(ProcessInstance instance) {
        // Check system state and obligations
        RuntimeContext.SystemState systemState = determineSystemState();

        return new RuntimeContext.OperationalContext(
            systemState,
            instance.context().obligations()
        );
    }

    private RuntimeContext.SystemState determineSystemState() {
        // In a real implementation, this would check system health indicators
        // For now, we return NORMAL
        return RuntimeContext.SystemState.NORMAL;
    }

    /**
     * Builder for configuring the ContextAssembler.
     */
    public static class Builder {
        private Function<String, Optional<Map<String, Object>>> clientConfigLoader =
            tenantId -> Optional.of(Map.of());

        public Builder clientConfigLoader(
                Function<String, Optional<Map<String, Object>>> loader) {
            this.clientConfigLoader = loader;
            return this;
        }

        public ContextAssembler build() {
            return new ContextAssembler(clientConfigLoader);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
