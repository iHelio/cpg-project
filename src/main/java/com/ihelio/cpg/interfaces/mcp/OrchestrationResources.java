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

package com.ihelio.cpg.interfaces.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihelio.cpg.application.service.ProcessEventService;
import com.ihelio.cpg.application.service.ProcessExecutionService;
import com.ihelio.cpg.application.service.ProcessGraphService;
import com.ihelio.cpg.application.service.ProcessInstanceService;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.ProcessGraph;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

/**
 * MCP resources for process graph orchestration.
 *
 * <p>Exposes read-only orchestration data as MCP resources that AI clients
 * can access via URI templates.
 */
@Component
public class OrchestrationResources {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationResources.class);

    private final ProcessGraphService processGraphService;
    private final ProcessInstanceService processInstanceService;
    private final ProcessExecutionService processExecutionService;
    private final ProcessEventService processEventService;
    private final ObjectMapper objectMapper;

    public OrchestrationResources(
            ProcessGraphService processGraphService,
            ProcessInstanceService processInstanceService,
            ProcessExecutionService processExecutionService,
            ProcessEventService processEventService,
            ObjectMapper objectMapper) {
        this.processGraphService = processGraphService;
        this.processInstanceService = processInstanceService;
        this.processExecutionService = processExecutionService;
        this.processEventService = processEventService;
        this.objectMapper = objectMapper;
    }

    @McpResource(uri = "graph://published",
                 name = "Published Process Graphs",
                 description = "All published process graphs (summary list)",
                 mimeType = "application/json")
    public String listPublishedGraphs() {
        log.info("MCP resource: graph://published");
        List<ProcessGraph> graphs = processGraphService.listGraphs(null);

        List<Map<String, Object>> summaries = graphs.stream()
            .map(g -> Map.<String, Object>of(
                "id", g.id().value(),
                "name", g.name(),
                "description", g.description() != null ? g.description() : "",
                "version", g.version(),
                "status", g.status().name(),
                "nodeCount", g.nodes().size(),
                "edgeCount", g.edges().size()
            ))
            .toList();

        return toJson(summaries);
    }

    @McpResource(uri = "graph://{graphId}",
                 name = "Process Graph",
                 description = "Full process graph definition including nodes and edges",
                 mimeType = "application/json")
    public String getGraph(String graphId) {
        log.info("MCP resource: graph://{}", graphId);
        return processGraphService.getGraph(graphId)
            .map(g -> toJson(toGraphMap(g)))
            .orElse("{\"error\": \"Process graph not found: " + graphId + "\"}");
    }

    @McpResource(uri = "instance://{instanceId}",
                 name = "Process Instance",
                 description = "Process instance state and node executions",
                 mimeType = "application/json")
    public String getInstance(String instanceId) {
        log.info("MCP resource: instance://{}", instanceId);
        return processInstanceService.getInstance(instanceId)
            .map(i -> toJson(toInstanceMap(i)))
            .orElse("{\"error\": \"Process instance not found: " + instanceId + "\"}");
    }

    @McpResource(uri = "instance://{instanceId}/context",
                 name = "Execution Context",
                 description = "Execution context including client, domain, and accumulated state",
                 mimeType = "application/json")
    public String getInstanceContext(String instanceId) {
        log.info("MCP resource: instance://{}/context", instanceId);
        ExecutionContext context = processExecutionService.getContext(instanceId);
        return toJson(toContextMap(context));
    }

    @McpResource(uri = "instance://{instanceId}/events",
                 name = "Event History",
                 description = "Event history for a process instance",
                 mimeType = "application/json")
    public String getInstanceEvents(String instanceId) {
        log.info("MCP resource: instance://{}/events", instanceId);
        List<ExecutionContext.ReceivedEvent> events =
            processEventService.getEventHistory(instanceId);

        List<Map<String, Object>> eventMaps = events.stream()
            .map(e -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("eventType", e.eventType());
                map.put("eventId", e.eventId());
                map.put("receivedAt", e.receivedAt().toString());
                map.put("payload", e.payload());
                return map;
            })
            .toList();

        return toJson(eventMaps);
    }

    private Map<String, Object> toGraphMap(ProcessGraph graph) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", graph.id().value());
        map.put("name", graph.name());
        map.put("description", graph.description() != null ? graph.description() : "");
        map.put("version", graph.version());
        map.put("status", graph.status().name());
        map.put("nodeCount", graph.nodes().size());
        map.put("edgeCount", graph.edges().size());
        map.put("entryNodeIds", graph.entryNodeIds().stream()
            .map(n -> n.value()).toList());
        map.put("terminalNodeIds", graph.terminalNodeIds().stream()
            .map(n -> n.value()).toList());
        map.put("nodes", graph.nodes().stream()
            .map(n -> Map.of(
                "id", n.id().value(),
                "name", n.name(),
                "description", n.description() != null ? n.description() : ""
            )).toList());
        map.put("edges", graph.edges().stream()
            .map(e -> Map.of(
                "id", e.id().value(),
                "name", e.name() != null ? e.name() : "",
                "sourceNodeId", e.sourceNodeId().value(),
                "targetNodeId", e.targetNodeId().value()
            )).toList());
        return map;
    }

    private Map<String, Object> toInstanceMap(ProcessInstance instance) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", instance.id().value());
        map.put("processGraphId", instance.processGraphId().value());
        map.put("processGraphVersion", instance.processGraphVersion());
        map.put("correlationId", instance.correlationId());
        map.put("status", instance.status().name());
        map.put("startedAt", instance.startedAt().toString());
        map.put("completedAt",
            instance.completedAt() != null ? instance.completedAt().toString() : null);
        map.put("activeNodeIds", instance.activeNodeIds().stream()
            .map(n -> n.value()).toList());
        map.put("pendingEdgeIds", instance.pendingEdgeIds().stream()
            .map(e -> e.value()).toList());
        map.put("nodeExecutionCount", instance.nodeExecutions().size());
        return map;
    }

    private Map<String, Object> toContextMap(ExecutionContext context) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("clientContext", context.clientContext());
        map.put("domainContext", context.domainContext());
        map.put("accumulatedState", context.accumulatedState());
        map.put("eventCount", context.eventHistory().size());
        map.put("obligationCount", context.obligations().size());
        return map;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"Serialization failed: " + e.getMessage() + "\"}";
        }
    }
}
