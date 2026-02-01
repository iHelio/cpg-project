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
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihelio.cpg.application.service.ProcessEventService;
import com.ihelio.cpg.application.service.ProcessExecutionService;
import com.ihelio.cpg.application.service.ProcessGraphService;
import com.ihelio.cpg.application.service.ProcessInstanceService;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.ProcessGraph;
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
@DisplayName("OrchestrationResources")
class OrchestrationResourcesTest {

    @Mock
    private ProcessGraphService processGraphService;

    @Mock
    private ProcessInstanceService processInstanceService;

    @Mock
    private ProcessExecutionService processExecutionService;

    @Mock
    private ProcessEventService processEventService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OrchestrationResources resources;

    @BeforeEach
    void setUp() {
        objectMapper.findAndRegisterModules();
        resources = new OrchestrationResources(
            processGraphService,
            processInstanceService,
            processExecutionService,
            processEventService,
            objectMapper
        );
    }

    // ── graph://published ───────────────────────────────────────────────────

    @Test
    @DisplayName("listPublishedGraphs should return graph summaries as JSON")
    void listPublishedGraphsShouldReturnSummaries() {
        ProcessGraph graph = createTestProcessGraph();
        when(processGraphService.listGraphs(null)).thenReturn(List.of(graph));

        String result = resources.listPublishedGraphs();

        assertThat(result).contains("employee-onboarding");
        assertThat(result).contains("Employee Onboarding");
        assertThat(result).contains("PUBLISHED");
        assertThat(result).contains("nodeCount");
    }

    @Test
    @DisplayName("listPublishedGraphs should return empty array when no graphs")
    void listPublishedGraphsShouldReturnEmptyArray() {
        when(processGraphService.listGraphs(null)).thenReturn(List.of());

        String result = resources.listPublishedGraphs();

        assertThat(result).isEqualTo("[]");
    }

    // ── graph://{graphId} ───────────────────────────────────────────────────

    @Test
    @DisplayName("getGraph should return full graph when found")
    void getGraphShouldReturnFullGraphWhenFound() {
        ProcessGraph graph = createTestProcessGraph();
        when(processGraphService.getGraph("employee-onboarding"))
            .thenReturn(Optional.of(graph));

        String result = resources.getGraph("employee-onboarding");

        assertThat(result).contains("employee-onboarding");
        assertThat(result).contains("PUBLISHED");
    }

    @Test
    @DisplayName("getGraph should return error when not found")
    void getGraphShouldReturnErrorWhenNotFound() {
        when(processGraphService.getGraph("unknown")).thenReturn(Optional.empty());

        String result = resources.getGraph("unknown");

        assertThat(result).contains("error");
        assertThat(result).contains("not found");
    }

    // ── instance://{instanceId} ─────────────────────────────────────────────

    @Test
    @DisplayName("getInstance should return instance map when found")
    void getInstanceShouldReturnInstanceWhenFound() {
        ProcessInstance instance = createTestInstance();
        when(processInstanceService.getInstance("inst-123"))
            .thenReturn(Optional.of(instance));

        String result = resources.getInstance("inst-123");

        assertThat(result).contains("inst-123");
        assertThat(result).contains("employee-onboarding");
        assertThat(result).contains("RUNNING");
        assertThat(result).contains("processGraphVersion");
    }

    @Test
    @DisplayName("getInstance should return error when not found")
    void getInstanceShouldReturnErrorWhenNotFound() {
        when(processInstanceService.getInstance("unknown"))
            .thenReturn(Optional.empty());

        String result = resources.getInstance("unknown");

        assertThat(result).contains("error");
        assertThat(result).contains("not found");
    }

    // ── instance://{instanceId}/context ─────────────────────────────────────

    @Test
    @DisplayName("getInstanceContext should return execution context as map")
    void getInstanceContextShouldReturnContext() {
        ExecutionContext context = ExecutionContext.builder()
            .clientContext(Map.of("tenantId", "acme"))
            .domainContext(Map.of("employeeId", "emp-123"))
            .build();
        when(processExecutionService.getContext("inst-123")).thenReturn(context);

        String result = resources.getInstanceContext("inst-123");

        assertThat(result).contains("clientContext");
        assertThat(result).contains("tenantId");
        assertThat(result).contains("acme");
        assertThat(result).contains("domainContext");
        assertThat(result).contains("employeeId");
    }

    // ── instance://{instanceId}/events ──────────────────────────────────────

    @Test
    @DisplayName("getInstanceEvents should return event history as maps")
    void getInstanceEventsShouldReturnEventHistory() {
        ExecutionContext.ReceivedEvent event = new ExecutionContext.ReceivedEvent(
            "node.completed", "evt-1", Instant.now(), Map.of("result", "ok"));
        when(processEventService.getEventHistory("inst-123"))
            .thenReturn(List.of(event));

        String result = resources.getInstanceEvents("inst-123");

        assertThat(result).contains("node.completed");
        assertThat(result).contains("evt-1");
        assertThat(result).contains("eventType");
    }

    @Test
    @DisplayName("getInstanceEvents should return empty array when no events")
    void getInstanceEventsShouldReturnEmptyArray() {
        when(processEventService.getEventHistory("inst-123"))
            .thenReturn(List.of());

        String result = resources.getInstanceEvents("inst-123");

        assertThat(result).isEqualTo("[]");
    }

    // ── Test Data Helpers ───────────────────────────────────────────────────

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
