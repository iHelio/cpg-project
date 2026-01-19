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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ihelio.cpg.application.service.ProcessInstanceService;
import com.ihelio.cpg.domain.exception.ProcessExecutionException;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.ProcessGraph;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProcessInstanceController.class)
@DisplayName("ProcessInstanceController")
class ProcessInstanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProcessInstanceService processInstanceService;

    @Test
    @DisplayName("POST /api/v1/process-instances should start new process")
    void startProcessShouldCreateNewInstance() throws Exception {
        ProcessInstance instance = createTestInstance();
        when(processInstanceService.startProcess(any())).thenReturn(instance);

        String requestBody = """
            {
                "processGraphId": "employee-onboarding",
                "clientContext": {"tenantId": "acme"},
                "domainContext": {"employeeId": "emp-123"}
            }
            """;

        mockMvc.perform(post("/api/v1/process-instances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("inst-123"))
            .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    @DisplayName("POST /api/v1/process-instances should return 400 for invalid request")
    void startProcessShouldReturn400ForInvalidRequest() throws Exception {
        String requestBody = """
            {
                "clientContext": {},
                "domainContext": {}
            }
            """;

        mockMvc.perform(post("/api/v1/process-instances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/process-instances should return list of instances")
    void listInstancesShouldReturnListOfInstances() throws Exception {
        ProcessInstance instance = createTestInstance();
        when(processInstanceService.listInstances(any(), any(), any()))
            .thenReturn(List.of(instance));

        mockMvc.perform(get("/api/v1/process-instances"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].id").value("inst-123"));
    }

    @Test
    @DisplayName("GET /api/v1/process-instances/{id} should return instance when found")
    void getInstanceShouldReturnInstanceWhenFound() throws Exception {
        ProcessInstance instance = createTestInstance();
        when(processInstanceService.getInstance("inst-123"))
            .thenReturn(Optional.of(instance));

        mockMvc.perform(get("/api/v1/process-instances/inst-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("inst-123"))
            .andExpect(jsonPath("$.processGraphId").value("employee-onboarding"))
            .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    @DisplayName("GET /api/v1/process-instances/{id} should return 404 when not found")
    void getInstanceShouldReturn404WhenNotFound() throws Exception {
        when(processInstanceService.getInstance("unknown"))
            .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/process-instances/unknown"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/process-instances/{id}/suspend should suspend instance")
    void suspendInstanceShouldSuspendInstance() throws Exception {
        ProcessInstance instance = createTestInstance();
        instance.suspend();
        when(processInstanceService.suspendInstance("inst-123")).thenReturn(instance);

        mockMvc.perform(post("/api/v1/process-instances/inst-123/suspend"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    @DisplayName("POST /api/v1/process-instances/{id}/suspend should return 409 for invalid state")
    void suspendInstanceShouldReturn409ForInvalidState() throws Exception {
        when(processInstanceService.suspendInstance("inst-123"))
            .thenThrow(ProcessExecutionException.withContext(
                "Cannot suspend",
                "inst-123",
                null,
                ProcessExecutionException.ErrorType.INVALID_STATE,
                false
            ));

        mockMvc.perform(post("/api/v1/process-instances/inst-123/suspend"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorType").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("POST /api/v1/process-instances/{id}/resume should resume instance")
    void resumeInstanceShouldResumeInstance() throws Exception {
        ProcessInstance instance = createTestInstance();
        when(processInstanceService.resumeInstance("inst-123")).thenReturn(instance);

        mockMvc.perform(post("/api/v1/process-instances/inst-123/resume"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("inst-123"));
    }

    @Test
    @DisplayName("POST /api/v1/process-instances/{id}/cancel should cancel instance")
    void cancelInstanceShouldCancelInstance() throws Exception {
        ProcessInstance instance = createTestInstance();
        instance.fail();
        when(processInstanceService.cancelInstance("inst-123")).thenReturn(instance);

        mockMvc.perform(post("/api/v1/process-instances/inst-123/cancel"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"));
    }

    private ProcessInstance createTestInstance() {
        return ProcessInstance.builder()
            .id("inst-123")
            .processGraphId(new ProcessGraph.ProcessGraphId("employee-onboarding"))
            .processGraphVersion(1)
            .context(ExecutionContext.builder().build())
            .build();
    }
}
