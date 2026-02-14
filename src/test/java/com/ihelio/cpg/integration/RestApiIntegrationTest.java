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

package com.ihelio.cpg.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihelio.cpg.application.onboarding.OnboardingProcessGraphBuilder;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration test for the full REST API lifecycle.
 *
 * <p>Tests the complete flow from starting a process to executing nodes
 * using the actual Spring context and in-memory repositories.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("REST API Integration Test")
class RestApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProcessGraphRepository processGraphRepository;

    @BeforeEach
    void setUp() {
        // Save the onboarding process graph to the repository
        ProcessGraph graph = OnboardingProcessGraphBuilder.build();
        processGraphRepository.save(graph);
    }

    @Test
    @DisplayName("should list available process graphs")
    void shouldListAvailableProcessGraphs() throws Exception {
        mockMvc.perform(get("/api/v1/process-graphs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$[?(@.id == 'employee-onboarding')]").exists())
            .andExpect(jsonPath("$[?(@.name == 'Employee Onboarding')]").exists());
    }

    @Test
    @DisplayName("should get process graph by id")
    void shouldGetProcessGraphById() throws Exception {
        mockMvc.perform(get("/api/v1/process-graphs/employee-onboarding"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("employee-onboarding"))
            .andExpect(jsonPath("$.name").value("Employee Onboarding"))
            .andExpect(jsonPath("$.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.nodes").isArray())
            .andExpect(jsonPath("$.edges").isArray());
    }

    @Test
    @DisplayName("should validate process graph")
    void shouldValidateProcessGraph() throws Exception {
        mockMvc.perform(post("/api/v1/process-graphs/employee-onboarding/validate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    @DisplayName("should start new process instance")
    void shouldStartNewProcessInstance() throws Exception {
        String requestBody = """
            {
                "processGraphId": "employee-onboarding",
                "correlationId": "new-hire-123",
                "clientContext": {
                    "tenantId": "acme-corp",
                    "region": "US-WEST"
                },
                "domainContext": {
                    "employeeId": "emp-456",
                    "department": "Engineering"
                }
            }
            """;

        mockMvc.perform(post("/api/v1/process-instances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.processGraphId").value("employee-onboarding"))
            .andExpect(jsonPath("$.status").value("RUNNING"))
            .andExpect(jsonPath("$.correlationId").value("new-hire-123"));
    }

    @Test
    @DisplayName("should complete full process lifecycle")
    void shouldCompleteFullProcessLifecycle() throws Exception {
        // Step 1: Start a new process instance
        String startRequest = """
            {
                "processGraphId": "employee-onboarding",
                "correlationId": "lifecycle-test",
                "clientContext": {"tenantId": "test"},
                "domainContext": {"employeeId": "emp-test"}
            }
            """;

        MvcResult startResult = mockMvc.perform(post("/api/v1/process-instances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(startRequest))
            .andExpect(status().isCreated())
            .andReturn();

        JsonNode responseJson = objectMapper.readTree(
            startResult.getResponse().getContentAsString());
        String instanceId = responseJson.get("id").asText();
        assertThat(instanceId).isNotBlank();

        // Step 2: Get the process instance
        mockMvc.perform(get("/api/v1/process-instances/" + instanceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(instanceId))
            .andExpect(jsonPath("$.status").value("RUNNING"));

        // Step 3: Get available nodes
        mockMvc.perform(get("/api/v1/process-instances/" + instanceId + "/available-nodes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        // Step 4: Get execution context
        mockMvc.perform(get("/api/v1/process-instances/" + instanceId + "/context"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clientContext.tenantId").value("test"))
            .andExpect(jsonPath("$.domainContext.employeeId").value("emp-test"));

        // Step 5: Suspend the process
        mockMvc.perform(post("/api/v1/process-instances/" + instanceId + "/suspend"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUSPENDED"));

        // Step 6: Verify suspended status
        mockMvc.perform(get("/api/v1/process-instances/" + instanceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUSPENDED"));

        // Step 7: Resume the process
        mockMvc.perform(post("/api/v1/process-instances/" + instanceId + "/resume"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RUNNING"));

        // Step 8: Cancel the process
        mockMvc.perform(post("/api/v1/process-instances/" + instanceId + "/cancel"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    @DisplayName("should handle events")
    void shouldHandleEvents() throws Exception {
        // Start a process first
        String startRequest = """
            {
                "processGraphId": "employee-onboarding",
                "correlationId": "event-test",
                "clientContext": {},
                "domainContext": {}
            }
            """;

        mockMvc.perform(post("/api/v1/process-instances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(startRequest))
            .andExpect(status().isCreated());

        // Publish an event
        String eventRequest = """
            {
                "eventType": "background.check.completed",
                "correlationId": "event-test",
                "payload": {
                    "status": "PASSED",
                    "checkId": "check-123"
                },
                "sourceType": "EXTERNAL",
                "sourceIdentifier": "background-check-service"
            }
            """;

        mockMvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("should return 404 for unknown process graph")
    void shouldReturn404ForUnknownProcessGraph() throws Exception {
        mockMvc.perform(get("/api/v1/process-graphs/unknown-graph"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should return 404 for unknown process instance")
    void shouldReturn404ForUnknownProcessInstance() throws Exception {
        mockMvc.perform(get("/api/v1/process-instances/unknown-instance"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should return 400 for invalid start process request")
    void shouldReturn400ForInvalidStartProcessRequest() throws Exception {
        String invalidRequest = """
            {
                "clientContext": {},
                "domainContext": {}
            }
            """;

        mockMvc.perform(post("/api/v1/process-instances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
            .andExpect(status().isBadRequest());
    }
}
