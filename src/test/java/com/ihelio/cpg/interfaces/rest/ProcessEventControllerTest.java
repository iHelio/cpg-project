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

import com.ihelio.cpg.application.service.ProcessEventService;
import com.ihelio.cpg.domain.exception.ProcessExecutionException;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.ProcessGraph;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProcessEventController.class)
@DisplayName("ProcessEventController")
class ProcessEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProcessEventService processEventService;

    @Test
    @DisplayName("POST /api/v1/events should publish event")
    void publishEventShouldPublishEvent() throws Exception {
        ProcessInstance instance = createTestInstance();
        when(processEventService.publishEvent(any())).thenReturn(List.of(instance));

        String requestBody = """
            {
                "eventType": "background.check.completed",
                "correlationId": "corr-123",
                "payload": {"status": "PASSED"},
                "sourceType": "EXTERNAL",
                "sourceIdentifier": "background-check-system"
            }
            """;

        mockMvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].id").value("inst-123"));
    }

    @Test
    @DisplayName("POST /api/v1/events should return 400 for invalid request")
    void publishEventShouldReturn400ForInvalidRequest() throws Exception {
        String requestBody = """
            {
                "payload": {},
                "sourceType": "EXTERNAL"
            }
            """;

        mockMvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/process-instances/{id}/events should return event history")
    void getEventHistoryShouldReturnEvents() throws Exception {
        ExecutionContext.ReceivedEvent event = new ExecutionContext.ReceivedEvent(
            "background.check.completed",
            "event-123",
            Instant.parse("2026-01-17T10:00:00Z"),
            Map.of("status", "PASSED")
        );
        when(processEventService.getEventHistory("inst-123")).thenReturn(List.of(event));

        mockMvc.perform(get("/api/v1/process-instances/inst-123/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].eventType").value("background.check.completed"))
            .andExpect(jsonPath("$[0].eventId").value("event-123"));
    }

    @Test
    @DisplayName("GET /api/v1/process-instances/{id}/events should return 404 when instance not found")
    void getEventHistoryShouldReturn404WhenNotFound() throws Exception {
        when(processEventService.getEventHistory("unknown"))
            .thenThrow(ProcessExecutionException.withContext(
                "Instance not found",
                "unknown",
                null,
                ProcessExecutionException.ErrorType.INSTANCE_NOT_FOUND,
                false
            ));

        mockMvc.perform(get("/api/v1/process-instances/unknown/events"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorType").value("INSTANCE_NOT_FOUND"));
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
