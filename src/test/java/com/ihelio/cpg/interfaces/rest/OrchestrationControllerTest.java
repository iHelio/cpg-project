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

package com.ihelio.cpg.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.orchestration.ProcessOrchestrator;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrchestrationController.class)
@DisplayName("OrchestrationController")
class OrchestrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProcessOrchestrator processOrchestrator;

    @MockitoBean
    private ProcessGraphRepository processGraphRepository;

    // ── POST /api/v1/orchestration/start ──────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/orchestration/start should create new instance")
    void startOrchestrationShouldCreateNewInstance() throws Exception {
        ProcessGraph graph = createTestProcessGraph();
        ProcessInstance instance = createTestInstance();
        ProcessOrchestrator.OrchestrationStatus orchestrationStatus =
            createTestOrchestrationStatus(instance);

        when(processGraphRepository.findLatestVersion(any())).thenReturn(Optional.of(graph));
        when(processOrchestrator.start(any(), any())).thenReturn(instance);
        when(processOrchestrator.getStatus(any())).thenReturn(orchestrationStatus);

        String requestBody = """
            {
                "processGraphId": "employee-onboarding",
                "clientContext": {"tenantId": "acme"},
                "domainContext": {"employeeId": "emp-123"}
            }
            """;

        mockMvc.perform(post("/api/v1/orchestration/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/orchestration/inst-123"))
            .andExpect(jsonPath("$.instanceId").value("inst-123"))
            .andExpect(jsonPath("$.processGraphId").value("employee-onboarding"))
            .andExpect(jsonPath("$.status").value("RUNNING"))
            .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/orchestration/start should return 400 for missing graphId")
    void startOrchestrationShouldReturn400ForMissingGraphId() throws Exception {
        String requestBody = """
            {
                "clientContext": {"tenantId": "acme"},
                "domainContext": {"employeeId": "emp-123"}
            }
            """;

        mockMvc.perform(post("/api/v1/orchestration/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/orchestration/start should return 404 for unknown graph")
    void startOrchestrationShouldReturn404ForUnknownGraph() throws Exception {
        when(processGraphRepository.findLatestVersion(any())).thenReturn(Optional.empty());

        String requestBody = """
            {
                "processGraphId": "unknown-graph",
                "clientContext": {"tenantId": "acme"},
                "domainContext": {"employeeId": "emp-123"}
            }
            """;

        mockMvc.perform(post("/api/v1/orchestration/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorType").value("GRAPH_NOT_FOUND"));
    }

    // ── GET /api/v1/orchestration/{instanceId} ────────────────────────────

    @Test
    @DisplayName("GET /api/v1/orchestration/{instanceId} should return status when found")
    void getStatusShouldReturnStatusWhenFound() throws Exception {
        ProcessInstance instance = createTestInstance();
        ProcessOrchestrator.OrchestrationStatus orchestrationStatus =
            createTestOrchestrationStatus(instance);

        when(processOrchestrator.getStatus(any())).thenReturn(orchestrationStatus);

        mockMvc.perform(get("/api/v1/orchestration/inst-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.instanceId").value("inst-123"))
            .andExpect(jsonPath("$.processGraphId").value("employee-onboarding"))
            .andExpect(jsonPath("$.status").value("RUNNING"))
            .andExpect(jsonPath("$.isActive").value(true))
            .andExpect(jsonPath("$.isComplete").value(false))
            .andExpect(jsonPath("$.isSuspended").value(false))
            .andExpect(jsonPath("$.isFailed").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/orchestration/{instanceId} should return 404 when not found")
    void getStatusShouldReturn404WhenNotFound() throws Exception {
        when(processOrchestrator.getStatus(any())).thenReturn(null);

        mockMvc.perform(get("/api/v1/orchestration/unknown"))
            .andExpect(status().isNotFound());
    }

    // ── POST /api/v1/orchestration/{instanceId}/signal ────────────────────

    @Test
    @DisplayName("POST /api/v1/orchestration/{instanceId}/signal should return updated status")
    void signalEventShouldReturnUpdatedStatus() throws Exception {
        ProcessInstance instance = createTestInstance();
        ProcessOrchestrator.OrchestrationStatus orchestrationStatus =
            createTestOrchestrationStatus(instance);

        doNothing().when(processOrchestrator).signal(any());
        when(processOrchestrator.getStatus(any())).thenReturn(orchestrationStatus);

        String requestBody = """
            {
                "eventType": "NODE_COMPLETED",
                "nodeId": "node-1",
                "payload": {"result": "success"}
            }
            """;

        mockMvc.perform(post("/api/v1/orchestration/inst-123/signal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.instanceId").value("inst-123"))
            .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    @DisplayName("POST /api/v1/orchestration/{instanceId}/signal should return 400 for missing eventType")
    void signalEventShouldReturn400ForMissingEventType() throws Exception {
        String requestBody = """
            {
                "nodeId": "node-1",
                "payload": {}
            }
            """;

        mockMvc.perform(post("/api/v1/orchestration/inst-123/signal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/orchestration/{instanceId}/signal should return 400 for unsupported eventType")
    void signalEventShouldReturn400ForUnsupportedEventType() throws Exception {
        String requestBody = """
            {
                "eventType": "UNSUPPORTED_TYPE",
                "payload": {}
            }
            """;

        mockMvc.perform(post("/api/v1/orchestration/inst-123/signal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    // ── POST /api/v1/orchestration/{instanceId}/suspend ───────────────────

    @Test
    @DisplayName("POST /api/v1/orchestration/{instanceId}/suspend should return updated status")
    void suspendShouldReturnUpdatedStatus() throws Exception {
        ProcessInstance instance = createTestInstance();
        instance.suspend();
        ProcessOrchestrator.OrchestrationStatus orchestrationStatus =
            new ProcessOrchestrator.OrchestrationStatus(instance, null, null, false);

        doNothing().when(processOrchestrator).suspend(any());
        when(processOrchestrator.getStatus(any())).thenReturn(orchestrationStatus);

        mockMvc.perform(post("/api/v1/orchestration/inst-123/suspend"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.instanceId").value("inst-123"))
            .andExpect(jsonPath("$.status").value("SUSPENDED"))
            .andExpect(jsonPath("$.isSuspended").value(true));
    }

    // ── POST /api/v1/orchestration/{instanceId}/resume ────────────────────

    @Test
    @DisplayName("POST /api/v1/orchestration/{instanceId}/resume should return updated status")
    void resumeShouldReturnUpdatedStatus() throws Exception {
        ProcessInstance instance = createTestInstance();
        ProcessOrchestrator.OrchestrationStatus orchestrationStatus =
            createTestOrchestrationStatus(instance);

        doNothing().when(processOrchestrator).resume(any());
        when(processOrchestrator.getStatus(any())).thenReturn(orchestrationStatus);

        mockMvc.perform(post("/api/v1/orchestration/inst-123/resume"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.instanceId").value("inst-123"))
            .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    // ── POST /api/v1/orchestration/{instanceId}/cancel ────────────────────

    @Test
    @DisplayName("POST /api/v1/orchestration/{instanceId}/cancel should return updated status")
    void cancelShouldReturnUpdatedStatus() throws Exception {
        ProcessInstance instance = createTestInstance();
        instance.fail();
        ProcessOrchestrator.OrchestrationStatus orchestrationStatus =
            new ProcessOrchestrator.OrchestrationStatus(instance, null, null, false);

        doNothing().when(processOrchestrator).cancel(any());
        when(processOrchestrator.getStatus(any())).thenReturn(orchestrationStatus);

        mockMvc.perform(post("/api/v1/orchestration/inst-123/cancel"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.instanceId").value("inst-123"))
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.isFailed").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/orchestration/{instanceId}/cancel should return 404 when status null")
    void cancelShouldReturn404WhenStatusNull() throws Exception {
        doNothing().when(processOrchestrator).cancel(any());
        when(processOrchestrator.getStatus(any())).thenReturn(null);

        mockMvc.perform(post("/api/v1/orchestration/inst-123/cancel"))
            .andExpect(status().isNotFound());
    }

    // ── Test Data Helpers ─────────────────────────────────────────────────

    private ProcessInstance createTestInstance() {
        return ProcessInstance.builder()
            .id("inst-123")
            .processGraphId(new ProcessGraph.ProcessGraphId("employee-onboarding"))
            .processGraphVersion(1)
            .context(ExecutionContext.builder().build())
            .build();
    }

    private ProcessOrchestrator.OrchestrationStatus createTestOrchestrationStatus(
            ProcessInstance instance) {
        return new ProcessOrchestrator.OrchestrationStatus(
            instance, null, null, instance.isRunning());
    }

    private ProcessGraph createTestProcessGraph() {
        return new ProcessGraph(
            new ProcessGraph.ProcessGraphId("employee-onboarding"),
            "Employee Onboarding",
            "Employee onboarding process",
            1,
            ProcessGraph.ProcessGraphStatus.PUBLISHED,
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            null
        );
    }
}
