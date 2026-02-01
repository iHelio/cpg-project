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
import com.ihelio.cpg.application.service.ProcessExecutionService;
import com.ihelio.cpg.application.service.ProcessGraphService;
import com.ihelio.cpg.application.service.ProcessInstanceService;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.orchestration.OrchestrationEvent;
import com.ihelio.cpg.domain.orchestration.ProcessOrchestrator;
import com.ihelio.cpg.domain.orchestration.RuntimeContext;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools for process graph orchestration.
 *
 * <p>Exposes orchestration operations as MCP tools that AI clients
 * can discover and invoke over the SSE/HTTP transport.
 */
@Component
public class OrchestrationTools {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationTools.class);

    private final ProcessGraphService processGraphService;
    private final ProcessOrchestrator processOrchestrator;
    private final ProcessGraphRepository processGraphRepository;
    private final ProcessInstanceService processInstanceService;
    private final ProcessExecutionService processExecutionService;
    private final ObjectMapper objectMapper;

    public OrchestrationTools(
            ProcessGraphService processGraphService,
            ProcessOrchestrator processOrchestrator,
            ProcessGraphRepository processGraphRepository,
            ProcessInstanceService processInstanceService,
            ProcessExecutionService processExecutionService,
            ObjectMapper objectMapper) {
        this.processGraphService = processGraphService;
        this.processOrchestrator = processOrchestrator;
        this.processGraphRepository = processGraphRepository;
        this.processInstanceService = processInstanceService;
        this.processExecutionService = processExecutionService;
        this.objectMapper = objectMapper;
    }

    @McpTool(name = "list_process_graphs",
             description = "List all published process graphs")
    public String listProcessGraphs() {
        log.info("MCP tool: list_process_graphs");
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

    @McpTool(name = "get_process_graph",
             description = "Get a process graph by ID with full structure including nodes and edges")
    public String getProcessGraph(
            @McpToolParam(description = "Process graph ID", required = true) String graphId) {
        log.info("MCP tool: get_process_graph({})", graphId);
        return processGraphService.getGraph(graphId)
            .map(g -> toJson(Map.of(
                "id", g.id().value(),
                "name", g.name(),
                "description", g.description() != null ? g.description() : "",
                "version", g.version(),
                "status", g.status().name(),
                "nodeCount", g.nodes().size(),
                "edgeCount", g.edges().size(),
                "entryNodeIds", g.entryNodeIds().stream().map(n -> n.value()).toList(),
                "terminalNodeIds", g.terminalNodeIds().stream().map(n -> n.value()).toList()
            )))
            .orElse("{\"error\": \"Process graph not found: " + graphId + "\"}");
    }

    @McpTool(name = "validate_process_graph",
             description = "Validate a process graph's structural integrity and return any errors")
    public String validateProcessGraph(
            @McpToolParam(description = "Process graph ID", required = true) String graphId) {
        log.info("MCP tool: validate_process_graph({})", graphId);
        List<String> errors = processGraphService.validateGraph(graphId);
        return toJson(Map.of("graphId", graphId, "valid", errors.isEmpty(), "errors", errors));
    }

    @McpTool(name = "start_orchestration",
             description = "Start autonomous orchestration of a process graph")
    public String startOrchestration(
            @McpToolParam(description = "Process graph ID", required = true) String processGraphId,
            @McpToolParam(description = "Client context as JSON object") String clientContext,
            @McpToolParam(description = "Domain context as JSON object") String domainContext) {
        log.info("MCP tool: start_orchestration({})", processGraphId);

        ProcessGraph graph = processGraphRepository.findLatestVersion(
                new ProcessGraph.ProcessGraphId(processGraphId))
            .orElseThrow(() -> new IllegalArgumentException(
                "Process graph not found: " + processGraphId));

        RuntimeContext.Builder builder = RuntimeContext.builder();
        if (clientContext != null) {
            builder.clientContext(parseJsonMap(clientContext));
        }
        if (domainContext != null) {
            builder.domainContext(parseJsonMap(domainContext));
        }

        ProcessInstance instance = processOrchestrator.start(graph, builder.build());
        ProcessOrchestrator.OrchestrationStatus status = processOrchestrator.getStatus(instance.id());

        if (status == null) {
            status = new ProcessOrchestrator.OrchestrationStatus(
                instance, null, null, instance.isRunning());
        }
        return toJson(Map.of(
            "instanceId", instance.id().value(),
            "processGraphId", processGraphId,
            "status", instance.status().name(),
            "isActive", status.isActive()
        ));
    }

    @McpTool(name = "get_orchestration_status",
             description = "Get current orchestration status for a process instance")
    public String getOrchestrationStatus(
            @McpToolParam(description = "Process instance ID", required = true) String instanceId) {
        log.info("MCP tool: get_orchestration_status({})", instanceId);

        ProcessOrchestrator.OrchestrationStatus status = processOrchestrator.getStatus(
            new ProcessInstance.ProcessInstanceId(instanceId));

        if (status == null) {
            return "{\"error\": \"Orchestration status not found: " + instanceId + "\"}";
        }
        return toJson(Map.of(
            "instanceId", instanceId,
            "status", status.instance().status().name(),
            "isActive", status.isActive(),
            "isComplete", status.isComplete(),
            "isFailed", status.isFailed(),
            "isSuspended", status.isSuspended()
        ));
    }

    @McpTool(name = "signal_event",
             description = "Signal an event to trigger orchestrator reevaluation of a process instance")
    public String signalEvent(
            @McpToolParam(description = "Process instance ID", required = true) String instanceId,
            @McpToolParam(description = "Event type (e.g. NODE_COMPLETED, DATA_CHANGE)",
                          required = true) String eventType,
            @McpToolParam(description = "Node ID (required for node events)") String nodeId,
            @McpToolParam(description = "Event payload as JSON object") String payload) {
        log.info("MCP tool: signal_event({}, {})", instanceId, eventType);

        ProcessInstance.ProcessInstanceId id = new ProcessInstance.ProcessInstanceId(instanceId);
        Map<String, Object> payloadMap = payload != null ? parseJsonMap(payload) : Map.of();

        OrchestrationEvent event = createEvent(id, eventType, nodeId, payloadMap);
        processOrchestrator.signal(event);

        ProcessOrchestrator.OrchestrationStatus status = processOrchestrator.getStatus(id);
        if (status == null) {
            return toJson(Map.of("instanceId", instanceId, "signaled", true));
        }
        return toJson(Map.of(
            "instanceId", instanceId,
            "signaled", true,
            "status", status.instance().status().name(),
            "isActive", status.isActive()
        ));
    }

    @McpTool(name = "suspend_orchestration",
             description = "Pause orchestration of a running process instance")
    public String suspendOrchestration(
            @McpToolParam(description = "Process instance ID", required = true) String instanceId) {
        log.info("MCP tool: suspend_orchestration({})", instanceId);

        ProcessInstance.ProcessInstanceId id = new ProcessInstance.ProcessInstanceId(instanceId);
        processOrchestrator.suspend(id);

        ProcessOrchestrator.OrchestrationStatus status = processOrchestrator.getStatus(id);
        return toJson(Map.of(
            "instanceId", instanceId,
            "status", status != null ? status.instance().status().name() : "UNKNOWN"
        ));
    }

    @McpTool(name = "resume_orchestration",
             description = "Resume a suspended orchestration")
    public String resumeOrchestration(
            @McpToolParam(description = "Process instance ID", required = true) String instanceId) {
        log.info("MCP tool: resume_orchestration({})", instanceId);

        ProcessInstance.ProcessInstanceId id = new ProcessInstance.ProcessInstanceId(instanceId);
        processOrchestrator.resume(id);

        ProcessOrchestrator.OrchestrationStatus status = processOrchestrator.getStatus(id);
        return toJson(Map.of(
            "instanceId", instanceId,
            "status", status != null ? status.instance().status().name() : "UNKNOWN"
        ));
    }

    @McpTool(name = "cancel_orchestration",
             description = "Cancel orchestration of a process instance")
    public String cancelOrchestration(
            @McpToolParam(description = "Process instance ID", required = true) String instanceId) {
        log.info("MCP tool: cancel_orchestration({})", instanceId);

        ProcessInstance.ProcessInstanceId id = new ProcessInstance.ProcessInstanceId(instanceId);
        processOrchestrator.cancel(id);

        ProcessOrchestrator.OrchestrationStatus status = processOrchestrator.getStatus(id);
        return toJson(Map.of(
            "instanceId", instanceId,
            "status", status != null ? status.instance().status().name() : "UNKNOWN"
        ));
    }

    @McpTool(name = "list_process_instances",
             description = "List running process instances with optional filters")
    public String listProcessInstances(
            @McpToolParam(description = "Filter by process graph ID") String processGraphId,
            @McpToolParam(description = "Filter by status (RUNNING, SUSPENDED, COMPLETED, FAILED, CANCELLED)")
                String status,
            @McpToolParam(description = "Filter by correlation ID") String correlationId) {
        log.info("MCP tool: list_process_instances");

        ProcessInstance.ProcessInstanceStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            statusEnum = ProcessInstance.ProcessInstanceStatus.valueOf(status.toUpperCase());
        }

        List<ProcessInstance> instances = processInstanceService.listInstances(
            processGraphId, statusEnum, correlationId);

        List<Map<String, Object>> summaries = instances.stream()
            .map(inst -> Map.<String, Object>of(
                "instanceId", inst.id().value(),
                "processGraphId", inst.processGraphId().value(),
                "status", inst.status().name(),
                "activeNodeCount", inst.activeNodeIds().size()
            ))
            .toList();

        return toJson(summaries);
    }

    @McpTool(name = "get_available_nodes",
             description = "Get nodes eligible for execution in a process instance")
    public String getAvailableNodes(
            @McpToolParam(description = "Process instance ID", required = true) String instanceId) {
        log.info("MCP tool: get_available_nodes({})", instanceId);

        List<Node> nodes = processExecutionService.getAvailableNodes(instanceId);

        List<Map<String, Object>> nodeSummaries = nodes.stream()
            .map(node -> Map.<String, Object>of(
                "nodeId", node.id().value(),
                "name", node.name(),
                "description", node.description() != null ? node.description() : ""
            ))
            .toList();

        return toJson(nodeSummaries);
    }

    private OrchestrationEvent createEvent(
            ProcessInstance.ProcessInstanceId instanceId,
            String eventType,
            String nodeId,
            Map<String, Object> payload) {
        return switch (eventType.toUpperCase()) {
            case "NODE_COMPLETED" -> OrchestrationEvent.NodeCompleted.of(
                instanceId, new Node.NodeId(nodeId), payload, 0);
            case "NODE_FAILED" -> OrchestrationEvent.NodeFailed.of(
                instanceId, new Node.NodeId(nodeId),
                (String) payload.getOrDefault("errorType", "UNKNOWN"),
                (String) payload.getOrDefault("errorMessage", "Unknown error"),
                Boolean.TRUE.equals(payload.get("retryable")));
            case "DATA_CHANGE" -> OrchestrationEvent.DataChange.updated(
                (String) payload.getOrDefault("entityType", "unknown"),
                (String) payload.getOrDefault("entityId", "unknown"),
                List.of(),
                payload);
            default -> throw new IllegalArgumentException("Unsupported event type: " + eventType);
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"Serialization failed: " + e.getMessage() + "\"}";
        }
    }
}
