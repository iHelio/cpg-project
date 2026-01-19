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

import com.ihelio.cpg.application.service.ProcessGraphService;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.model.ProcessGraph.ProcessGraphStatus;
import com.ihelio.cpg.interfaces.rest.dto.response.ProcessGraphResponse;
import com.ihelio.cpg.interfaces.rest.dto.response.ProcessGraphSummaryResponse;
import com.ihelio.cpg.interfaces.rest.dto.response.ValidationResultResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for process graph management.
 */
@RestController
@RequestMapping("/api/v1/process-graphs")
@Validated
public class ProcessGraphController {

    private final ProcessGraphService processGraphService;

    public ProcessGraphController(ProcessGraphService processGraphService) {
        this.processGraphService = processGraphService;
    }

    /**
     * Lists all process graphs, optionally filtered by status.
     *
     * @param status optional status filter
     * @return list of process graph summaries
     */
    @GetMapping
    public ResponseEntity<List<ProcessGraphSummaryResponse>> listGraphs(
            @RequestParam(required = false) String status) {

        ProcessGraphStatus graphStatus = null;
        if (status != null) {
            graphStatus = ProcessGraphStatus.valueOf(status.toUpperCase());
        }

        List<ProcessGraph> graphs = processGraphService.listGraphs(graphStatus);

        List<ProcessGraphSummaryResponse> response = graphs.stream()
            .map(ProcessGraphSummaryResponse::from)
            .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a process graph by ID (latest version).
     *
     * @param id the graph ID
     * @return the process graph
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProcessGraphResponse> getGraph(@PathVariable String id) {
        return processGraphService.getGraph(id)
            .map(ProcessGraphResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retrieves a specific version of a process graph.
     *
     * @param id the graph ID
     * @param version the version number
     * @return the process graph version
     */
    @GetMapping("/{id}/versions/{version}")
    public ResponseEntity<ProcessGraphResponse> getGraphVersion(
            @PathVariable String id,
            @PathVariable int version) {

        return processGraphService.getGraphVersion(id, version)
            .map(ProcessGraphResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Validates a process graph structure.
     *
     * @param id the graph ID
     * @return validation result
     */
    @PostMapping("/{id}/validate")
    public ResponseEntity<ValidationResultResponse> validateGraph(@PathVariable String id) {
        List<String> errors = processGraphService.validateGraph(id);
        return ResponseEntity.ok(ValidationResultResponse.from(errors));
    }
}
