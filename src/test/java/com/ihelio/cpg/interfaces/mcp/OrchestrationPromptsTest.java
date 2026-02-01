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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrchestrationPrompts")
class OrchestrationPromptsTest {

    @Mock
    private ProcessGraphService processGraphService;

    @Mock
    private ProcessInstanceService processInstanceService;

    @Mock
    private ProcessExecutionService processExecutionService;

    @Mock
    private ProcessEventService processEventService;

    @Mock
    private ProcessOrchestrator processOrchestrator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OrchestrationPrompts prompts;

    @BeforeEach
    void setUp() {
        objectMapper.findAndRegisterModules();
        prompts = new OrchestrationPrompts(
            processGraphService,
            processInstanceService,
            processExecutionService,
            processEventService,
            processOrchestrator,
            objectMapper
        );
    }

    // ── analyze_process_graph ───────────────────────────────────────────────

    @Test
    @DisplayName("analyzeProcessGraph should return prompt with graph structure")
    void analyzeProcessGraphShouldReturnPromptWithStructure() {
        ProcessGraph graph = createTestProcessGraph();
        when(processGraphService.getGraph("employee-onboarding"))
            .thenReturn(Optional.of(graph));
        when(processGraphService.validateGraph("employee-onboarding"))
            .thenReturn(List.of());

        GetPromptResult result = prompts.analyzeProcessGraph("employee-onboarding");

        assertThat(result.description()).contains("Employee Onboarding");
        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().get(0).role()).isEqualTo(Role.USER);

        String content = extractTextContent(result);
        assertThat(content).contains("employee-onboarding");
        assertThat(content).contains("Employee Onboarding");
        assertThat(content).contains("PUBLISHED");
        assertThat(content).contains("No validation errors");
        assertThat(content).contains("Graph structure and flow patterns");
    }

    @Test
    @DisplayName("analyzeProcessGraph should include validation errors")
    void analyzeProcessGraphShouldIncludeValidationErrors() {
        ProcessGraph graph = createTestProcessGraph();
        when(processGraphService.getGraph("employee-onboarding"))
            .thenReturn(Optional.of(graph));
        when(processGraphService.validateGraph("employee-onboarding"))
            .thenReturn(List.of("No entry nodes defined", "Unreachable node: node-5"));

        GetPromptResult result = prompts.analyzeProcessGraph("employee-onboarding");

        String content = extractTextContent(result);
        assertThat(content).contains("No entry nodes defined");
        assertThat(content).contains("Unreachable node: node-5");
    }

    @Test
    @DisplayName("analyzeProcessGraph should throw when graph not found")
    void analyzeProcessGraphShouldThrowWhenNotFound() {
        when(processGraphService.getGraph("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> prompts.analyzeProcessGraph("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }

    // ── troubleshoot_instance ───────────────────────────────────────────────

    @Test
    @DisplayName("troubleshootInstance should return prompt with instance diagnostics")
    void troubleshootInstanceShouldReturnDiagnostics() {
        ProcessInstance instance = createTestInstance();
        ExecutionContext context = ExecutionContext.builder()
            .clientContext(Map.of("tenantId", "acme"))
            .build();
        ProcessOrchestrator.OrchestrationStatus orchStatus =
            new ProcessOrchestrator.OrchestrationStatus(instance, null, null, true);

        when(processInstanceService.getInstance("inst-123"))
            .thenReturn(Optional.of(instance));
        when(processExecutionService.getContext("inst-123")).thenReturn(context);
        when(processEventService.getEventHistory("inst-123")).thenReturn(List.of());
        when(processOrchestrator.getStatus(any())).thenReturn(orchStatus);

        GetPromptResult result = prompts.troubleshootInstance("inst-123");

        assertThat(result.description()).contains("inst-123");
        assertThat(result.messages()).hasSize(1);

        String content = extractTextContent(result);
        assertThat(content).contains("inst-123");
        assertThat(content).contains("employee-onboarding");
        assertThat(content).contains("RUNNING");
        assertThat(content).contains("Active: true");
        assertThat(content).contains("Current state");
        assertThat(content).contains("Recommended actions");
    }

    @Test
    @DisplayName("troubleshootInstance should include event history")
    void troubleshootInstanceShouldIncludeEventHistory() {
        ProcessInstance instance = createTestInstance();
        ExecutionContext context = ExecutionContext.builder().build();
        ExecutionContext.ReceivedEvent event = new ExecutionContext.ReceivedEvent(
            "node.completed", "evt-1", Instant.now(), Map.of("result", "ok"));

        when(processInstanceService.getInstance("inst-123"))
            .thenReturn(Optional.of(instance));
        when(processExecutionService.getContext("inst-123")).thenReturn(context);
        when(processEventService.getEventHistory("inst-123"))
            .thenReturn(List.of(event));
        when(processOrchestrator.getStatus(any())).thenReturn(null);

        GetPromptResult result = prompts.troubleshootInstance("inst-123");

        String content = extractTextContent(result);
        assertThat(content).contains("1 events");
        assertThat(content).contains("Not available");
    }

    @Test
    @DisplayName("troubleshootInstance should throw when instance not found")
    void troubleshootInstanceShouldThrowWhenNotFound() {
        when(processInstanceService.getInstance("unknown"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> prompts.troubleshootInstance("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }

    // ── orchestration_summary ───────────────────────────────────────────────

    @Test
    @DisplayName("orchestrationSummary should return prompt with progress overview")
    void orchestrationSummaryShouldReturnProgressOverview() {
        ProcessInstance instance = createTestInstance();
        ExecutionContext context = ExecutionContext.builder()
            .accumulatedState(Map.of("step1Result", "done"))
            .build();
        ProcessOrchestrator.OrchestrationStatus orchStatus =
            new ProcessOrchestrator.OrchestrationStatus(instance, null, null, true);

        when(processInstanceService.getInstance("inst-123"))
            .thenReturn(Optional.of(instance));
        when(processExecutionService.getContext("inst-123")).thenReturn(context);
        when(processEventService.getEventHistory("inst-123")).thenReturn(List.of());
        when(processOrchestrator.getStatus(any())).thenReturn(orchStatus);

        GetPromptResult result = prompts.orchestrationSummary("inst-123");

        assertThat(result.description()).contains("inst-123");
        assertThat(result.messages()).hasSize(1);

        String content = extractTextContent(result);
        assertThat(content).contains("inst-123");
        assertThat(content).contains("employee-onboarding");
        assertThat(content).contains("RUNNING");
        assertThat(content).contains("Active: true");
        assertThat(content).contains("step1Result");
        assertThat(content).contains("status overview");
        assertThat(content).contains("next steps");
    }

    @Test
    @DisplayName("orchestrationSummary should handle null orchestration status")
    void orchestrationSummaryShouldHandleNullStatus() {
        ProcessInstance instance = createTestInstance();
        ExecutionContext context = ExecutionContext.builder().build();

        when(processInstanceService.getInstance("inst-123"))
            .thenReturn(Optional.of(instance));
        when(processExecutionService.getContext("inst-123")).thenReturn(context);
        when(processEventService.getEventHistory("inst-123")).thenReturn(List.of());
        when(processOrchestrator.getStatus(any())).thenReturn(null);

        GetPromptResult result = prompts.orchestrationSummary("inst-123");

        String content = extractTextContent(result);
        assertThat(content).contains("Not available");
    }

    @Test
    @DisplayName("orchestrationSummary should throw when instance not found")
    void orchestrationSummaryShouldThrowWhenNotFound() {
        when(processInstanceService.getInstance("unknown"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> prompts.orchestrationSummary("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }

    // ── Test Data Helpers ───────────────────────────────────────────────────

    private String extractTextContent(GetPromptResult result) {
        return ((TextContent) result.messages().get(0).content()).text();
    }

    private ProcessInstance createTestInstance() {
        return ProcessInstance.builder()
            .id("inst-123")
            .processGraphId(new ProcessGraph.ProcessGraphId("employee-onboarding"))
            .processGraphVersion(1)
            .context(ExecutionContext.builder().build())
            .build();
    }

    private ProcessGraph createTestProcessGraph() {
        return new ProcessGraph(
            new ProcessGraph.ProcessGraphId("employee-onboarding"),
            "Employee Onboarding",
            "Employee onboarding process",
            1,
            ProcessGraph.ProcessGraphStatus.PUBLISHED,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null
        );
    }
}
