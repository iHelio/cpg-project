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
import com.ihelio.cpg.domain.orchestration.ProcessOrchestrator;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

/**
 * MCP prompts for process graph orchestration.
 *
 * <p>Exposes reusable prompt templates that AI clients can use to
 * analyze, troubleshoot, and summarize orchestration state.
 */
@Component
public class OrchestrationPrompts {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationPrompts.class);

    private final ProcessGraphService processGraphService;
    private final ProcessInstanceService processInstanceService;
    private final ProcessExecutionService processExecutionService;
    private final ProcessEventService processEventService;
    private final ProcessOrchestrator processOrchestrator;
    private final ObjectMapper objectMapper;

    public OrchestrationPrompts(
            ProcessGraphService processGraphService,
            ProcessInstanceService processInstanceService,
            ProcessExecutionService processExecutionService,
            ProcessEventService processEventService,
            ProcessOrchestrator processOrchestrator,
            ObjectMapper objectMapper) {
        this.processGraphService = processGraphService;
        this.processInstanceService = processInstanceService;
        this.processExecutionService = processExecutionService;
        this.processEventService = processEventService;
        this.processOrchestrator = processOrchestrator;
        this.objectMapper = objectMapper;
    }

    @McpPrompt(name = "analyze_process_graph",
               description = "Analyze a process graph's structure, nodes, edges, "
                   + "entry/terminal points, and potential issues")
    public GetPromptResult analyzeProcessGraph(
            @McpArg(name = "graphId", description = "The process graph ID to analyze",
                    required = true) String graphId) {
        log.info("MCP prompt: analyze_process_graph({})", graphId);

        ProcessGraph graph = processGraphService.getGraph(graphId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Process graph not found: " + graphId));

        List<String> validationErrors = processGraphService.validateGraph(graphId);

        String prompt = """
            Analyze the following process graph and provide insights on its structure, \
            potential issues, and optimization opportunities.

            ## Process Graph
            - **ID**: %s
            - **Name**: %s
            - **Description**: %s
            - **Version**: %d
            - **Status**: %s

            ## Structure
            - **Nodes** (%d total): %s
            - **Edges** (%d total): %s
            - **Entry Nodes**: %s
            - **Terminal Nodes**: %s

            ## Full Graph Definition
            ```json
            %s
            ```

            ## Validation Results
            %s

            Please analyze:
            1. Graph structure and flow patterns
            2. Entry and terminal node configuration
            3. Edge conditions and branching logic
            4. Potential deadlocks or unreachable nodes
            5. Optimization suggestions""".formatted(
                graph.id().value(),
                graph.name(),
                graph.description() != null ? graph.description() : "N/A",
                graph.version(),
                graph.status().name(),
                graph.nodes().size(),
                graph.nodes().stream().map(n -> n.name()).toList(),
                graph.edges().size(),
                graph.edges().stream().map(e -> e.id().value()).toList(),
                graph.entryNodeIds().stream().map(n -> n.value()).toList(),
                graph.terminalNodeIds().stream().map(n -> n.value()).toList(),
                toJson(graph),
                validationErrors.isEmpty()
                    ? "No validation errors found."
                    : "Errors: " + String.join(", ", validationErrors));

        return new GetPromptResult("Analyze Process Graph: " + graph.name(),
            List.of(new PromptMessage(Role.USER, new TextContent(prompt))));
    }

    @McpPrompt(name = "troubleshoot_instance",
               description = "Diagnose a stuck or failed process instance "
                   + "with context, status, and event history")
    public GetPromptResult troubleshootInstance(
            @McpArg(name = "instanceId",
                    description = "The process instance ID to troubleshoot",
                    required = true) String instanceId) {
        log.info("MCP prompt: troubleshoot_instance({})", instanceId);

        ProcessInstance instance = processInstanceService.getInstance(instanceId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Process instance not found: " + instanceId));

        ExecutionContext context = processExecutionService.getContext(instanceId);
        List<ExecutionContext.ReceivedEvent> events =
            processEventService.getEventHistory(instanceId);

        ProcessOrchestrator.OrchestrationStatus orchStatus =
            processOrchestrator.getStatus(instance.id());

        String prompt = """
            Troubleshoot the following process instance and diagnose why it may be \
            stuck, failed, or not progressing as expected.

            ## Process Instance
            - **ID**: %s
            - **Process Graph**: %s (v%d)
            - **Status**: %s
            - **Active Nodes**: %s
            - **Pending Edges**: %s

            ## Orchestration Status
            %s

            ## Node Executions
            ```json
            %s
            ```

            ## Execution Context
            ```json
            %s
            ```

            ## Event History (%d events)
            ```json
            %s
            ```

            Please diagnose:
            1. Current state and what the instance is waiting for
            2. Any failed node executions and their error details
            3. Whether preconditions for next nodes are met
            4. Event history anomalies or missing events
            5. Recommended actions to resolve the issue""".formatted(
                instance.id().value(),
                instance.processGraphId().value(),
                instance.processGraphVersion(),
                instance.status().name(),
                instance.activeNodeIds().stream().map(n -> n.value()).toList(),
                instance.pendingEdgeIds().stream().map(e -> e.value()).toList(),
                orchStatus != null
                    ? "Active: " + orchStatus.isActive()
                        + ", Complete: " + orchStatus.isComplete()
                        + ", Failed: " + orchStatus.isFailed()
                    : "Not available",
                toJson(instance.nodeExecutions()),
                toJson(context),
                events.size(),
                toJson(events));

        return new GetPromptResult("Troubleshoot Instance: " + instanceId,
            List.of(new PromptMessage(Role.USER, new TextContent(prompt))));
    }

    @McpPrompt(name = "orchestration_summary",
               description = "Summarize the current orchestration state, "
                   + "decisions made, and next steps")
    public GetPromptResult orchestrationSummary(
            @McpArg(name = "instanceId",
                    description = "The process instance ID to summarize",
                    required = true) String instanceId) {
        log.info("MCP prompt: orchestration_summary({})", instanceId);

        ProcessInstance instance = processInstanceService.getInstance(instanceId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Process instance not found: " + instanceId));

        ExecutionContext context = processExecutionService.getContext(instanceId);
        List<ExecutionContext.ReceivedEvent> events =
            processEventService.getEventHistory(instanceId);

        ProcessOrchestrator.OrchestrationStatus orchStatus =
            processOrchestrator.getStatus(instance.id());

        String prompt = """
            Provide a concise summary of the current orchestration state for this \
            process instance.

            ## Process Instance
            - **ID**: %s
            - **Process Graph**: %s (v%d)
            - **Status**: %s
            - **Started At**: %s

            ## Progress
            - **Completed Nodes**: %d
            - **Active Nodes**: %s
            - **Pending Edges**: %d
            - **Events Received**: %d

            ## Orchestration Status
            %s

            ## Accumulated State
            ```json
            %s
            ```

            Please provide:
            1. A brief status overview (1-2 sentences)
            2. What has been accomplished so far
            3. What is currently in progress
            4. What the expected next steps are
            5. Any blockers or items requiring attention""".formatted(
                instance.id().value(),
                instance.processGraphId().value(),
                instance.processGraphVersion(),
                instance.status().name(),
                instance.startedAt(),
                instance.nodeExecutions().size(),
                instance.activeNodeIds().stream().map(n -> n.value()).toList(),
                instance.pendingEdgeIds().size(),
                events.size(),
                orchStatus != null
                    ? "Active: " + orchStatus.isActive()
                        + ", Complete: " + orchStatus.isComplete()
                        + ", Failed: " + orchStatus.isFailed()
                    : "Not available",
                toJson(context.accumulatedState()));

        return new GetPromptResult("Orchestration Summary: " + instanceId,
            List.of(new PromptMessage(Role.USER, new TextContent(prompt))));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"Serialization failed\"}";
        }
    }
}
