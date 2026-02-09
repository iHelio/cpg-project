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
import com.ihelio.cpg.application.orchestration.ContextAssembler;
import com.ihelio.cpg.application.orchestration.EligibilityEvaluator;
import com.ihelio.cpg.application.orchestration.InstanceOrchestrator;
import com.ihelio.cpg.application.service.ProcessExecutionService;
import com.ihelio.cpg.application.service.ProcessGraphService;
import com.ihelio.cpg.application.service.ProcessInstanceService;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.FeelExpression;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.orchestration.EligibleSpace;
import com.ihelio.cpg.domain.orchestration.OrchestrationEvent;
import com.ihelio.cpg.domain.orchestration.ProcessOrchestrator;
import com.ihelio.cpg.domain.orchestration.RuntimeContext;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import com.ihelio.cpg.domain.repository.ProcessInstanceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessInstanceService processInstanceService;
    private final ProcessExecutionService processExecutionService;
    private final InstanceOrchestrator instanceOrchestrator;
    private final ContextAssembler contextAssembler;
    private final EligibilityEvaluator eligibilityEvaluator;
    private final ObjectMapper objectMapper;

    public OrchestrationTools(
            ProcessGraphService processGraphService,
            ProcessOrchestrator processOrchestrator,
            ProcessGraphRepository processGraphRepository,
            ProcessInstanceRepository processInstanceRepository,
            ProcessInstanceService processInstanceService,
            ProcessExecutionService processExecutionService,
            InstanceOrchestrator instanceOrchestrator,
            ContextAssembler contextAssembler,
            EligibilityEvaluator eligibilityEvaluator,
            ObjectMapper objectMapper) {
        this.processGraphService = processGraphService;
        this.processOrchestrator = processOrchestrator;
        this.processGraphRepository = processGraphRepository;
        this.processInstanceRepository = processInstanceRepository;
        this.processInstanceService = processInstanceService;
        this.processExecutionService = processExecutionService;
        this.instanceOrchestrator = instanceOrchestrator;
        this.contextAssembler = contextAssembler;
        this.eligibilityEvaluator = eligibilityEvaluator;
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

    @McpTool(name = "get_active_nodes",
             description = "Get currently active (executing) nodes for a process instance")
    public String getActiveNodes(
            @McpToolParam(description = "Process instance ID", required = true) String instanceId) {
        log.info("MCP tool: get_active_nodes({})", instanceId);

        return processInstanceService.getInstance(instanceId)
            .map(instance -> {
                // Get the process graph to provide node details
                ProcessGraph graph = processGraphRepository.findByIdAndVersion(
                        instance.processGraphId(), instance.processGraphVersion())
                    .orElse(null);

                List<Map<String, Object>> activeNodes = instance.activeNodeIds().stream()
                    .map(nodeId -> {
                        // Try to get node details from graph
                        String nodeName = nodeId.value();
                        String nodeDescription = "";
                        if (graph != null) {
                            graph.nodes().stream()
                                .filter(n -> n.id().equals(nodeId))
                                .findFirst()
                                .ifPresent(n -> {});
                            var nodeOpt = graph.nodes().stream()
                                .filter(n -> n.id().equals(nodeId))
                                .findFirst();
                            if (nodeOpt.isPresent()) {
                                nodeName = nodeOpt.get().name();
                                nodeDescription = nodeOpt.get().description() != null
                                    ? nodeOpt.get().description() : "";
                            }
                        }
                        return Map.<String, Object>of(
                            "nodeId", nodeId.value(),
                            "name", nodeName,
                            "description", nodeDescription
                        );
                    })
                    .toList();

                return toJson(Map.of(
                    "instanceId", instanceId,
                    "processGraphId", instance.processGraphId().value(),
                    "status", instance.status().name(),
                    "activeNodeCount", activeNodes.size(),
                    "activeNodes", activeNodes
                ));
            })
            .orElse("{\"error\": \"Process instance not found: " + instanceId + "\"}");
    }

    @McpTool(name = "get_process_history",
             description = "Get execution history for a process instance showing all node executions")
    public String getProcessHistory(
            @McpToolParam(description = "Process instance ID", required = true) String instanceId) {
        log.info("MCP tool: get_process_history({})", instanceId);

        return processInstanceService.getInstance(instanceId)
            .map(instance -> {
                // Get the process graph to provide node names
                ProcessGraph graph = processGraphRepository.findByIdAndVersion(
                        instance.processGraphId(), instance.processGraphVersion())
                    .orElse(null);

                List<Map<String, Object>> history = instance.nodeExecutions().stream()
                    .map(execution -> {
                        String nodeName = execution.nodeId().value();
                        if (graph != null) {
                            var nodeOpt = graph.nodes().stream()
                                .filter(n -> n.id().equals(execution.nodeId()))
                                .findFirst();
                            if (nodeOpt.isPresent()) {
                                nodeName = nodeOpt.get().name();
                            }
                        }

                        var entry = new java.util.LinkedHashMap<String, Object>();
                        entry.put("nodeId", execution.nodeId().value());
                        entry.put("nodeName", nodeName);
                        entry.put("status", execution.status().name());
                        entry.put("startedAt", execution.startedAt().toString());
                        if (execution.completedAt() != null) {
                            entry.put("completedAt", execution.completedAt().toString());
                        }
                        if (execution.error() != null) {
                            entry.put("error", execution.error());
                        }
                        return (Map<String, Object>) entry;
                    })
                    .toList();

                var result = new java.util.LinkedHashMap<String, Object>();
                result.put("instanceId", instanceId);
                result.put("processGraphId", instance.processGraphId().value());
                result.put("status", instance.status().name());
                result.put("startedAt", instance.startedAt().toString());
                instance.completedAt().ifPresent(t -> result.put("completedAt", t.toString()));
                result.put("executionCount", history.size());
                result.put("history", history);

                return toJson(result);
            })
            .orElse("{\"error\": \"Process instance not found: " + instanceId + "\"}");
    }

    @McpTool(name = "get_graph_nodes",
             description = "Get all nodes in a process graph with full details including preconditions, policy gates, business rules, actions, and event config")
    public String getGraphNodes(
            @McpToolParam(description = "Process graph ID", required = true) String graphId) {
        log.info("MCP tool: get_graph_nodes({})", graphId);

        return processGraphService.getGraph(graphId)
            .map(graph -> {
                List<Map<String, Object>> nodes = graph.nodes().stream()
                    .map(this::nodeToDetailedMap)
                    .toList();
                return toJson(Map.of(
                    "graphId", graphId,
                    "nodeCount", nodes.size(),
                    "nodes", nodes
                ));
            })
            .orElse("{\"error\": \"Process graph not found: " + graphId + "\"}");
    }

    @McpTool(name = "get_graph_edges",
             description = "Get all edges in a process graph with full details including guard conditions, execution semantics, priority, and event triggers")
    public String getGraphEdges(
            @McpToolParam(description = "Process graph ID", required = true) String graphId) {
        log.info("MCP tool: get_graph_edges({})", graphId);

        return processGraphService.getGraph(graphId)
            .map(graph -> {
                List<Map<String, Object>> edges = graph.edges().stream()
                    .map(this::edgeToDetailedMap)
                    .toList();
                return toJson(Map.of(
                    "graphId", graphId,
                    "edgeCount", edges.size(),
                    "edges", edges
                ));
            })
            .orElse("{\"error\": \"Process graph not found: " + graphId + "\"}");
    }

    @McpTool(name = "get_node_details",
             description = "Get full details of a specific node including preconditions, policy gates, business rules, action config, event subscriptions, and exception routes")
    public String getNodeDetails(
            @McpToolParam(description = "Process graph ID", required = true) String graphId,
            @McpToolParam(description = "Node ID", required = true) String nodeId) {
        log.info("MCP tool: get_node_details({}, {})", graphId, nodeId);

        return processGraphService.getGraph(graphId)
            .flatMap(graph -> graph.nodes().stream()
                .filter(n -> n.id().value().equals(nodeId))
                .findFirst())
            .map(node -> toJson(nodeToDetailedMap(node)))
            .orElse("{\"error\": \"Node not found: " + nodeId + " in graph: " + graphId + "\"}");
    }

    @McpTool(name = "get_edge_details",
             description = "Get full details of a specific edge including guard conditions, execution semantics, priority, event triggers, and compensation semantics")
    public String getEdgeDetails(
            @McpToolParam(description = "Process graph ID", required = true) String graphId,
            @McpToolParam(description = "Edge ID", required = true) String edgeId) {
        log.info("MCP tool: get_edge_details({}, {})", graphId, edgeId);

        return processGraphService.getGraph(graphId)
            .flatMap(graph -> graph.edges().stream()
                .filter(e -> e.id().value().equals(edgeId))
                .findFirst())
            .map(edge -> toJson(edgeToDetailedMap(edge)))
            .orElse("{\"error\": \"Edge not found: " + edgeId + " in graph: " + graphId + "\"}");
    }

    @McpTool(name = "step_orchestration",
             description = "Execute a single orchestration step for a process instance. "
                 + "The orchestrator is completely event-driven - each call executes at most one node. "
                 + "Call this repeatedly after signaling events to progress through the workflow.")
    public String stepOrchestration(
            @McpToolParam(description = "Process instance ID", required = true) String instanceId) {
        log.info("MCP tool: step_orchestration({})", instanceId);

        ProcessInstance.ProcessInstanceId id = new ProcessInstance.ProcessInstanceId(instanceId);

        return processInstanceRepository.findById(id)
            .map(instance -> {
                if (!instance.isRunning()) {
                    return toJson(Map.of(
                        "instanceId", instanceId,
                        "error", "Instance is not running",
                        "status", instance.status().name()
                    ));
                }

                ProcessGraph graph = processGraphRepository.findByIdAndVersion(
                        instance.processGraphId(), instance.processGraphVersion())
                    .orElse(null);

                if (graph == null) {
                    return toJson(Map.of(
                        "instanceId", instanceId,
                        "error", "Process graph not found"
                    ));
                }

                // Execute a single orchestration step
                InstanceOrchestrator.OrchestrationResult result =
                    instanceOrchestrator.orchestrate(instance, graph, null);

                // Save updated instance
                processInstanceRepository.save(result.instance());

                // Build response
                var response = new java.util.LinkedHashMap<String, Object>();
                response.put("instanceId", instanceId);
                response.put("orchestrationStatus", result.status().name());
                response.put("message", result.message());
                response.put("instanceStatus", result.instance().status().name());

                if (result.decision() != null && result.decision().primarySelection() != null) {
                    response.put("executedNode", Map.of(
                        "nodeId", result.decision().primarySelection().node().id().value(),
                        "nodeName", result.decision().primarySelection().node().name()
                    ));
                }

                // Add information about what's needed next
                if (result.isWaiting()) {
                    response.put("hint", "Use get_required_events to see what events can progress the workflow");
                } else if (result.isExecuted()) {
                    response.put("hint", "Call step_orchestration again to continue execution");
                }

                return toJson(response);
            })
            .orElse("{\"error\": \"Process instance not found: " + instanceId + "\"}");
    }

    @McpTool(name = "get_required_events",
             description = "Analyze what events are needed to progress a process instance. "
                 + "Returns events that could enable new nodes based on edge triggers, "
                 + "node preconditions, and edge guard conditions.")
    public String getRequiredEvents(
            @McpToolParam(description = "Process instance ID", required = true) String instanceId) {
        log.info("MCP tool: get_required_events({})", instanceId);

        ProcessInstance.ProcessInstanceId id = new ProcessInstance.ProcessInstanceId(instanceId);

        return processInstanceRepository.findById(id)
            .map(instance -> {
                ProcessGraph graph = processGraphRepository.findByIdAndVersion(
                        instance.processGraphId(), instance.processGraphVersion())
                    .orElse(null);

                if (graph == null) {
                    return toJson(Map.of(
                        "instanceId", instanceId,
                        "error", "Process graph not found"
                    ));
                }

                RuntimeContext context = contextAssembler.assemble(instance, null);

                // Get current eligible space
                EligibleSpace eligibleSpace = eligibilityEvaluator.evaluate(instance, graph, context);

                // Collect required events from multiple sources
                Set<RequiredEvent> requiredEvents = new HashSet<>();

                // 1. Events from candidate nodes (reachable nodes we might want to execute)
                List<Node> candidateNodes = eligibilityEvaluator.getCandidateNodes(instance, graph);
                for (Node node : candidateNodes) {
                    if (instance.hasExecutedNode(node.id())) {
                        continue;
                    }

                    // Check node preconditions for event-related conditions
                    node.preconditions().domainContextConditions().forEach(condition -> {
                        if (containsEventReference(condition.expression())) {
                            requiredEvents.add(new RequiredEvent(
                                extractEventType(condition.expression()),
                                "NODE_PRECONDITION",
                                node.id().value(),
                                node.name(),
                                condition.description() != null ? condition.description() : condition.expression()
                            ));
                        }
                    });

                    // Check node event subscriptions
                    node.eventConfig().subscribes().forEach(sub -> {
                        requiredEvents.add(new RequiredEvent(
                            sub.eventType(),
                            "NODE_SUBSCRIPTION",
                            node.id().value(),
                            node.name(),
                            "Node subscribes to this event"
                        ));
                    });
                }

                // 2. Events from edges (outbound from completed nodes)
                for (ProcessInstance.NodeExecution execution : instance.nodeExecutions()) {
                    if (execution.status() != ProcessInstance.NodeExecutionStatus.COMPLETED) {
                        continue;
                    }

                    List<Edge> outboundEdges = graph.getOutboundEdges(execution.nodeId());
                    for (Edge edge : outboundEdges) {
                        Node targetNode = graph.findNode(edge.targetNodeId()).orElse(null);
                        if (targetNode == null || instance.hasExecutedNode(edge.targetNodeId())) {
                            continue;
                        }

                        // Check edge activating events
                        edge.eventTriggers().activatingEvents().forEach(eventType -> {
                            requiredEvents.add(new RequiredEvent(
                                eventType,
                                "EDGE_TRIGGER",
                                edge.id().value(),
                                edge.name() != null ? edge.name() : "Edge to " + targetNode.name(),
                                "Edge is activated by this event"
                            ));
                        });

                        // Check edge guard conditions for event conditions
                        edge.guardConditions().eventConditions().forEach(ec -> {
                            if (ec.mustHaveOccurred()) {
                                requiredEvents.add(new RequiredEvent(
                                    ec.eventType(),
                                    "EDGE_GUARD",
                                    edge.id().value(),
                                    edge.name() != null ? edge.name() : "Edge to " + targetNode.name(),
                                    "Edge guard requires this event to have occurred"
                                ));
                            }
                        });

                        // Check edge context conditions for event-related expressions
                        edge.guardConditions().contextConditions().forEach(condition -> {
                            if (containsEventReference(condition.expression())) {
                                requiredEvents.add(new RequiredEvent(
                                    extractEventType(condition.expression()),
                                    "EDGE_CONDITION",
                                    edge.id().value(),
                                    edge.name() != null ? edge.name() : "Edge to " + targetNode.name(),
                                    condition.description() != null ? condition.description() : condition.expression()
                                ));
                            }
                        });
                    }
                }

                // Build response
                var response = new java.util.LinkedHashMap<String, Object>();
                response.put("instanceId", instanceId);
                response.put("instanceStatus", instance.status().name());
                response.put("eligibleActionCount", eligibleSpace.candidateActions().size());

                List<Map<String, Object>> eventList = requiredEvents.stream()
                    .map(re -> {
                        var m = new java.util.LinkedHashMap<String, Object>();
                        m.put("eventType", re.eventType);
                        m.put("source", re.source);
                        m.put("sourceId", re.sourceId);
                        m.put("sourceName", re.sourceName);
                        m.put("description", re.description);
                        return (Map<String, Object>) m;
                    })
                    .toList();

                response.put("requiredEvents", eventList);

                // Add suggestions for common progression patterns
                if (eligibleSpace.candidateActions().isEmpty() && requiredEvents.isEmpty()) {
                    response.put("hint", "No candidate actions or required events. "
                        + "The process may be waiting for manual intervention or external system completion.");
                } else if (!requiredEvents.isEmpty()) {
                    response.put("hint", "Use signal_event tool to send one of the required events, "
                        + "then call step_orchestration to execute the enabled node.");
                }

                return toJson(response);
            })
            .orElse("{\"error\": \"Process instance not found: " + instanceId + "\"}");
    }

    private record RequiredEvent(
        String eventType,
        String source,
        String sourceId,
        String sourceName,
        String description
    ) {}

    @McpTool(name = "get_available_events",
             description = "Get events that can be sent to progress a process instance. "
                 + "Returns events with their required payloads based on the current state.")
    public String getAvailableEvents(
            @McpToolParam(description = "Process instance ID", required = true) String instanceId) {
        log.info("MCP tool: get_available_events({})", instanceId);

        ProcessInstance.ProcessInstanceId id = new ProcessInstance.ProcessInstanceId(instanceId);

        return processInstanceRepository.findById(id)
            .map(instance -> {
                ProcessGraph graph = processGraphRepository.findByIdAndVersion(
                        instance.processGraphId(), instance.processGraphVersion())
                    .orElse(null);

                if (graph == null) {
                    return toJson(Map.of(
                        "instanceId", instanceId,
                        "error", "Process graph not found"
                    ));
                }

                // Collect available events based on executed nodes and edges
                List<Map<String, Object>> availableEvents = new ArrayList<>();

                // Check each completed node for outbound edge event triggers
                for (ProcessInstance.NodeExecution execution : instance.nodeExecutions()) {
                    if (execution.status() != ProcessInstance.NodeExecutionStatus.COMPLETED) {
                        continue;
                    }

                    List<Edge> outboundEdges = graph.getOutboundEdges(execution.nodeId());
                    for (Edge edge : outboundEdges) {
                        Node targetNode = graph.findNode(edge.targetNodeId()).orElse(null);
                        if (targetNode == null || instance.hasExecutedNode(edge.targetNodeId())) {
                            continue;
                        }

                        // Add activating events for this edge
                        for (String eventType : edge.eventTriggers().activatingEvents()) {
                            Map<String, Object> eventInfo = buildEventInfo(
                                eventType, edge, targetNode, instance, graph);
                            availableEvents.add(eventInfo);
                        }
                    }
                }

                // Remove duplicates by event type
                Map<String, Map<String, Object>> uniqueEvents = new java.util.LinkedHashMap<>();
                for (Map<String, Object> event : availableEvents) {
                    String type = (String) event.get("eventType");
                    if (!uniqueEvents.containsKey(type)) {
                        uniqueEvents.put(type, event);
                    }
                }

                var response = new java.util.LinkedHashMap<String, Object>();
                response.put("instanceId", instanceId);
                response.put("instanceStatus", instance.status().name());
                response.put("availableEvents", new ArrayList<>(uniqueEvents.values()));

                if (uniqueEvents.isEmpty()) {
                    response.put("hint", "No event-triggered edges available. Try step_orchestration to continue.");
                } else {
                    response.put("hint", "Use send_event with one of these events to progress the workflow.");
                }

                return toJson(response);
            })
            .orElse("{\"error\": \"Process instance not found: " + instanceId + "\"}");
    }

    @McpTool(name = "send_event",
             description = "Send an event with auto-populated payload to progress a process instance. "
                 + "Use get_available_events first to see which events can be sent.")
    public String sendEvent(
            @McpToolParam(description = "Process instance ID", required = true) String instanceId,
            @McpToolParam(description = "Event type (e.g., EquipmentReady, DocumentsCollected)",
                          required = true) String eventType) {
        log.info("MCP tool: send_event({}, {})", instanceId, eventType);

        ProcessInstance.ProcessInstanceId id = new ProcessInstance.ProcessInstanceId(instanceId);

        return processInstanceRepository.findById(id)
            .map(instance -> {
                ProcessGraph graph = processGraphRepository.findByIdAndVersion(
                        instance.processGraphId(), instance.processGraphVersion())
                    .orElse(null);

                if (graph == null) {
                    return toJson(Map.of(
                        "instanceId", instanceId,
                        "error", "Process graph not found"
                    ));
                }

                // Generate appropriate payload based on event type
                Map<String, Object> payload = generateEventPayload(eventType, instance, graph);

                // Create and signal the event
                OrchestrationEvent event = OrchestrationEvent.DomainEvent.of(
                    eventType,
                    instanceId,
                    payload
                );

                processOrchestrator.signal(event);

                // Get updated status
                ProcessOrchestrator.OrchestrationStatus status = processOrchestrator.getStatus(id);

                var response = new java.util.LinkedHashMap<String, Object>();
                response.put("instanceId", instanceId);
                response.put("eventType", eventType);
                response.put("payload", payload);
                response.put("sent", true);

                if (status != null) {
                    response.put("instanceStatus", status.instance().status().name());
                    response.put("isActive", status.isActive());
                    if (status.lastDecision() != null) {
                        response.put("lastDecision", Map.of(
                            "type", status.lastDecision().type().name(),
                            "selectedNodes", status.lastDecision().selectedNodes().stream()
                                .map(ns -> ns.node().name())
                                .toList()
                        ));
                    }
                }

                response.put("hint", "Call step_orchestration to execute the next eligible node.");

                return toJson(response);
            })
            .orElse("{\"error\": \"Process instance not found: " + instanceId + "\"}");
    }

    private Map<String, Object> buildEventInfo(
            String eventType, Edge edge, Node targetNode,
            ProcessInstance instance, ProcessGraph graph) {

        Map<String, Object> eventInfo = new java.util.LinkedHashMap<>();
        eventInfo.put("eventType", eventType);
        eventInfo.put("targetNode", targetNode.name());
        eventInfo.put("edgeName", edge.name() != null ? edge.name() : "");
        eventInfo.put("description", getEventDescription(eventType));
        eventInfo.put("payload", generateEventPayload(eventType, instance, graph));

        return eventInfo;
    }

    private String getEventDescription(String eventType) {
        return switch (eventType) {
            case "OnboardingStarted" -> "Signals that onboarding process has started";
            case "BackgroundCheckCompleted" -> "Signals that background check has completed successfully";
            case "BackgroundCheckFailed" -> "Signals that background check has failed";
            case "BackgroundReviewCompleted" -> "Signals that manual review of background check is complete";
            case "EquipmentReady" -> "Signals that ordered equipment is ready for shipping";
            case "EquipmentShipped" -> "Signals that equipment has been shipped to employee";
            case "DocumentsCollected" -> "Signals that required documents have been collected";
            case "I9Verified" -> "Signals that I-9 verification is complete";
            case "OrientationScheduled" -> "Signals that orientation has been scheduled";
            default -> "Domain event: " + eventType;
        };
    }

    private Map<String, Object> generateEventPayload(
            String eventType, ProcessInstance instance, ProcessGraph graph) {

        // Generate payload based on event type with data from context
        return switch (eventType) {
            case "OnboardingStarted" -> Map.of(
                "timestamp", java.time.Instant.now().toString(),
                "source", "orchestrator"
            );
            case "BackgroundCheckCompleted" -> Map.of(
                "status", "COMPLETED",
                "passed", true,
                "requiresReview", false,
                "timestamp", java.time.Instant.now().toString()
            );
            case "BackgroundCheckFailed" -> Map.of(
                "status", "FAILED",
                "passed", false,
                "reason", "Background check did not pass",
                "timestamp", java.time.Instant.now().toString()
            );
            case "BackgroundReviewCompleted" -> Map.of(
                "decision", "APPROVED",
                "reviewer", "hr-manager",
                "comments", "Review completed successfully",
                "timestamp", java.time.Instant.now().toString()
            );
            case "EquipmentReady" -> Map.of(
                "orderId", "EQ-" + System.currentTimeMillis(),
                "status", "READY",
                "items", List.of("laptop", "monitor", "keyboard"),
                "timestamp", java.time.Instant.now().toString()
            );
            case "EquipmentShipped" -> Map.of(
                "trackingNumber", "TRK-" + System.currentTimeMillis(),
                "carrier", "FedEx",
                "estimatedDelivery", java.time.LocalDate.now().plusDays(3).toString(),
                "timestamp", java.time.Instant.now().toString()
            );
            case "DocumentsCollected" -> Map.of(
                "i9Part1Completed", true,
                "w4Completed", true,
                "directDepositCompleted", true,
                "timestamp", java.time.Instant.now().toString()
            );
            case "I9Verified" -> Map.of(
                "verified", true,
                "verificationDate", java.time.LocalDate.now().toString(),
                "documentType", "passport",
                "timestamp", java.time.Instant.now().toString()
            );
            case "OrientationScheduled" -> Map.of(
                "scheduled", true,
                "date", java.time.LocalDate.now().plusDays(14).toString(),
                "time", "09:00",
                "location", "Virtual",
                "timestamp", java.time.Instant.now().toString()
            );
            default -> Map.of(
                "eventType", eventType,
                "timestamp", java.time.Instant.now().toString()
            );
        };
    }

    private boolean containsEventReference(String expression) {
        if (expression == null) {
            return false;
        }
        String lower = expression.toLowerCase();
        return lower.contains("event.") || lower.contains("events.") ||
               lower.contains("event[") || lower.contains("received");
    }

    private String extractEventType(String expression) {
        // Simple extraction - look for patterns like "event.BackgroundCheckComplete" or "events.approval"
        if (expression == null) {
            return "UNKNOWN";
        }

        // Try to extract event type from common patterns
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(?:event\\.?|events\\[?['\"]?)([A-Za-z][A-Za-z0-9_]*)",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(expression);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return "DOMAIN_EVENT";
    }

    private Map<String, Object> nodeToDetailedMap(Node node) {
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("nodeId", node.id().value());
        result.put("name", node.name());
        result.put("description", node.description() != null ? node.description() : "");
        result.put("version", node.version());

        // Preconditions
        if (node.preconditions() != null) {
            var preconditions = new java.util.LinkedHashMap<String, Object>();
            preconditions.put("clientContextConditions",
                node.preconditions().clientContextConditions().stream()
                    .map(this::feelExpressionToMap)
                    .toList());
            preconditions.put("domainContextConditions",
                node.preconditions().domainContextConditions().stream()
                    .map(this::feelExpressionToMap)
                    .toList());
            result.put("preconditions", preconditions);
        }

        // Policy gates
        result.put("policyGates", node.policyGates().stream()
            .map(pg -> Map.of(
                "id", pg.id(),
                "name", pg.name() != null ? pg.name() : "",
                "type", pg.type().name(),
                "dmnDecisionRef", pg.dmnDecisionRef() != null ? pg.dmnDecisionRef() : "",
                "requiredOutcome", pg.requiredOutcome() != null ? pg.requiredOutcome() : ""
            ))
            .toList());

        // Business rules
        result.put("businessRules", node.businessRules().stream()
            .map(br -> Map.of(
                "id", br.id(),
                "name", br.name() != null ? br.name() : "",
                "dmnDecisionRef", br.dmnDecisionRef() != null ? br.dmnDecisionRef() : "",
                "category", br.category() != null ? br.category().name() : ""
            ))
            .toList());

        // Action
        var action = new java.util.LinkedHashMap<String, Object>();
        action.put("type", node.action().type().name());
        action.put("handlerRef", node.action().handlerRef());
        action.put("description", node.action().description() != null ? node.action().description() : "");
        if (node.action().config() != null) {
            action.put("config", Map.of(
                "asynchronous", node.action().config().asynchronous(),
                "timeoutSeconds", node.action().config().timeoutSeconds(),
                "retryCount", node.action().config().retryCount(),
                "assigneeExpression", node.action().config().assigneeExpression() != null
                    ? node.action().config().assigneeExpression() : "",
                "formRef", node.action().config().formRef() != null
                    ? node.action().config().formRef() : ""
            ));
        }
        result.put("action", action);

        // Event config
        if (node.eventConfig() != null) {
            var eventConfig = new java.util.LinkedHashMap<String, Object>();
            eventConfig.put("subscribes", node.eventConfig().subscribes().stream()
                .map(sub -> {
                    var subMap = new java.util.LinkedHashMap<String, Object>();
                    subMap.put("eventType", sub.eventType());
                    if (sub.correlationExpression() != null) {
                        subMap.put("correlationExpression", feelExpressionToMap(sub.correlationExpression()));
                    }
                    return subMap;
                })
                .toList());
            eventConfig.put("emits", node.eventConfig().emits().stream()
                .map(emit -> {
                    var emitMap = new java.util.LinkedHashMap<String, Object>();
                    emitMap.put("eventType", emit.eventType());
                    emitMap.put("timing", emit.timing().name());
                    if (emit.payloadExpression() != null) {
                        emitMap.put("payloadExpression", feelExpressionToMap(emit.payloadExpression()));
                    }
                    return emitMap;
                })
                .toList());
            result.put("eventConfig", eventConfig);
        }

        // Exception routes
        if (node.exceptionRoutes() != null) {
            var exceptionRoutes = new java.util.LinkedHashMap<String, Object>();
            exceptionRoutes.put("remediationRoutes", node.exceptionRoutes().remediationRoutes().stream()
                .map(rr -> Map.of(
                    "exceptionType", rr.exceptionType(),
                    "strategy", rr.strategy().name(),
                    "maxRetries", rr.maxRetries(),
                    "alternateNodeId", rr.alternateNodeId() != null ? rr.alternateNodeId() : ""
                ))
                .toList());
            exceptionRoutes.put("escalationRoutes", node.exceptionRoutes().escalationRoutes().stream()
                .map(er -> Map.of(
                    "exceptionType", er.exceptionType(),
                    "escalationNodeId", er.escalationNodeId(),
                    "assigneeExpression", er.assigneeExpression() != null ? er.assigneeExpression() : "",
                    "slaMinutes", er.slaMinutes()
                ))
                .toList());
            result.put("exceptionRoutes", exceptionRoutes);
        }

        return result;
    }

    private Map<String, Object> edgeToDetailedMap(Edge edge) {
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("edgeId", edge.id().value());
        result.put("name", edge.name() != null ? edge.name() : "");
        result.put("description", edge.description() != null ? edge.description() : "");
        result.put("sourceNodeId", edge.sourceNodeId().value());
        result.put("targetNodeId", edge.targetNodeId().value());

        // Guard conditions
        var guardConditions = new java.util.LinkedHashMap<String, Object>();
        guardConditions.put("contextConditions",
            edge.guardConditions().contextConditions().stream()
                .map(this::feelExpressionToMap)
                .toList());
        guardConditions.put("ruleOutcomeConditions",
            edge.guardConditions().ruleOutcomeConditions().stream()
                .map(roc -> Map.of(
                    "ruleId", roc.ruleId(),
                    "expectedOutcome", feelExpressionToMap(roc.expectedOutcome())
                ))
                .toList());
        guardConditions.put("policyOutcomeConditions",
            edge.guardConditions().policyOutcomeConditions().stream()
                .map(poc -> Map.of(
                    "policyGateId", poc.policyGateId(),
                    "requiredOutcome", poc.requiredOutcome().name()
                ))
                .toList());
        guardConditions.put("eventConditions",
            edge.guardConditions().eventConditions().stream()
                .map(ec -> {
                    var ecMap = new java.util.LinkedHashMap<String, Object>();
                    ecMap.put("eventType", ec.eventType());
                    ecMap.put("mustHaveOccurred", ec.mustHaveOccurred());
                    if (ec.correlationExpression() != null) {
                        ecMap.put("correlationExpression", feelExpressionToMap(ec.correlationExpression()));
                    }
                    return (Map<String, Object>) ecMap;
                })
                .toList());
        result.put("guardConditions", guardConditions);

        // Execution semantics
        result.put("executionSemantics", Map.of(
            "type", edge.executionSemantics().type().name(),
            "joinType", edge.executionSemantics().joinType() != null
                ? edge.executionSemantics().joinType().name() : "",
            "compensationRef", edge.executionSemantics().compensationRef() != null
                ? edge.executionSemantics().compensationRef() : ""
        ));

        // Priority
        result.put("priority", Map.of(
            "weight", edge.priority().weight(),
            "rank", edge.priority().rank(),
            "exclusive", edge.priority().exclusive()
        ));

        // Event triggers
        result.put("eventTriggers", Map.of(
            "activatingEvents", edge.eventTriggers().activatingEvents(),
            "reevaluationEvents", edge.eventTriggers().reevaluationEvents()
        ));

        // Compensation semantics
        var compensation = new java.util.LinkedHashMap<String, Object>();
        compensation.put("hasCompensation", edge.compensationSemantics().hasCompensation());
        if (edge.compensationSemantics().strategy() != null) {
            compensation.put("strategy", edge.compensationSemantics().strategy().name());
        }
        compensation.put("maxRetries", edge.compensationSemantics().maxRetries());
        if (edge.compensationSemantics().compensatingEdgeId() != null) {
            compensation.put("compensatingEdgeId", edge.compensationSemantics().compensatingEdgeId());
        }
        if (edge.compensationSemantics().compensationCondition() != null) {
            compensation.put("compensationCondition",
                feelExpressionToMap(edge.compensationSemantics().compensationCondition()));
        }
        result.put("compensationSemantics", compensation);

        return result;
    }

    private Map<String, Object> feelExpressionToMap(FeelExpression expr) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("expression", expr.expression());
        if (expr.description() != null) {
            map.put("description", expr.description());
        }
        return map;
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
            default -> OrchestrationEvent.DomainEvent.of(
                eventType,
                instanceId.value(),
                payload);
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
