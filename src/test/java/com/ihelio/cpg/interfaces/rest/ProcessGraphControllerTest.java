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

import com.ihelio.cpg.application.service.ProcessGraphService;
import com.ihelio.cpg.domain.exception.ProcessExecutionException;
import com.ihelio.cpg.domain.model.ProcessGraph;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProcessGraphController.class)
@DisplayName("ProcessGraphController")
class ProcessGraphControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProcessGraphService processGraphService;

    @Test
    @DisplayName("GET /api/v1/process-graphs should return list of graphs")
    void listGraphsShouldReturnListOfGraphs() throws Exception {
        ProcessGraph graph = createTestGraph("employee-onboarding");
        when(processGraphService.listGraphs(any())).thenReturn(List.of(graph));

        mockMvc.perform(get("/api/v1/process-graphs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].id").value("employee-onboarding"))
            .andExpect(jsonPath("$[0].name").value("Test Graph"));
    }

    @Test
    @DisplayName("GET /api/v1/process-graphs/{id} should return graph when found")
    void getGraphShouldReturnGraphWhenFound() throws Exception {
        ProcessGraph graph = createTestGraph("employee-onboarding");
        when(processGraphService.getGraph("employee-onboarding"))
            .thenReturn(Optional.of(graph));

        mockMvc.perform(get("/api/v1/process-graphs/employee-onboarding"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("employee-onboarding"))
            .andExpect(jsonPath("$.name").value("Test Graph"))
            .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("GET /api/v1/process-graphs/{id} should return 404 when not found")
    void getGraphShouldReturn404WhenNotFound() throws Exception {
        when(processGraphService.getGraph("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/process-graphs/unknown"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/process-graphs/{id}/validate should return validation result")
    void validateGraphShouldReturnValidationResult() throws Exception {
        when(processGraphService.validateGraph("employee-onboarding"))
            .thenReturn(List.of());

        mockMvc.perform(post("/api/v1/process-graphs/employee-onboarding/validate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/process-graphs/{id}/validate should return errors for invalid graph")
    void validateGraphShouldReturnErrorsForInvalidGraph() throws Exception {
        when(processGraphService.validateGraph("invalid-graph"))
            .thenReturn(List.of("Graph has no entry nodes", "Graph has cycles"));

        mockMvc.perform(post("/api/v1/process-graphs/invalid-graph/validate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    @DisplayName("POST /api/v1/process-graphs/{id}/validate should return 404 when graph not found")
    void validateGraphShouldReturn404WhenNotFound() throws Exception {
        when(processGraphService.validateGraph("unknown"))
            .thenThrow(new ProcessExecutionException(
                "Graph not found",
                ProcessExecutionException.ErrorType.GRAPH_NOT_FOUND
            ));

        mockMvc.perform(post("/api/v1/process-graphs/unknown/validate"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorType").value("GRAPH_NOT_FOUND"));
    }

    private ProcessGraph createTestGraph(String id) {
        return new ProcessGraph(
            new ProcessGraph.ProcessGraphId(id),
            "Test Graph",
            "Test description",
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
