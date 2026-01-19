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

import com.ihelio.cpg.application.service.ProcessInstanceService;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.execution.ProcessInstance.ProcessInstanceStatus;
import com.ihelio.cpg.interfaces.rest.dto.request.StartProcessRequest;
import com.ihelio.cpg.interfaces.rest.dto.response.ProcessInstanceResponse;
import com.ihelio.cpg.interfaces.rest.dto.response.ProcessInstanceSummaryResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for process instance lifecycle management.
 */
@RestController
@RequestMapping("/api/v1/process-instances")
@Validated
public class ProcessInstanceController {

    private final ProcessInstanceService processInstanceService;

    public ProcessInstanceController(ProcessInstanceService processInstanceService) {
        this.processInstanceService = processInstanceService;
    }

    /**
     * Starts a new process instance.
     *
     * @param request the start process request
     * @return the created process instance
     */
    @PostMapping
    public ResponseEntity<ProcessInstanceResponse> startProcess(
            @Valid @RequestBody StartProcessRequest request) {

        ProcessInstance instance = processInstanceService.startProcess(request);
        ProcessInstanceResponse response = ProcessInstanceResponse.from(instance);

        return ResponseEntity
            .created(URI.create("/api/v1/process-instances/" + instance.id().value()))
            .body(response);
    }

    /**
     * Retrieves a process instance by ID.
     *
     * @param id the instance ID
     * @return the process instance
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProcessInstanceResponse> getInstance(@PathVariable String id) {
        return processInstanceService.getInstance(id)
            .map(ProcessInstanceResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lists process instances with optional filters.
     *
     * @param processGraphId optional graph ID filter
     * @param status optional status filter
     * @param correlationId optional correlation ID filter
     * @return list of process instance summaries
     */
    @GetMapping
    public ResponseEntity<List<ProcessInstanceSummaryResponse>> listInstances(
            @RequestParam(required = false) String processGraphId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String correlationId) {

        ProcessInstanceStatus instanceStatus = null;
        if (status != null) {
            instanceStatus = ProcessInstanceStatus.valueOf(status.toUpperCase());
        }

        List<ProcessInstance> instances = processInstanceService.listInstances(
            processGraphId,
            instanceStatus,
            correlationId
        );

        List<ProcessInstanceSummaryResponse> response = instances.stream()
            .map(ProcessInstanceSummaryResponse::from)
            .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Suspends a running process instance.
     *
     * @param id the instance ID
     * @return the updated process instance
     */
    @PostMapping("/{id}/suspend")
    public ResponseEntity<ProcessInstanceResponse> suspendInstance(@PathVariable String id) {
        ProcessInstance instance = processInstanceService.suspendInstance(id);
        return ResponseEntity.ok(ProcessInstanceResponse.from(instance));
    }

    /**
     * Resumes a suspended process instance.
     *
     * @param id the instance ID
     * @return the updated process instance
     */
    @PostMapping("/{id}/resume")
    public ResponseEntity<ProcessInstanceResponse> resumeInstance(@PathVariable String id) {
        ProcessInstance instance = processInstanceService.resumeInstance(id);
        return ResponseEntity.ok(ProcessInstanceResponse.from(instance));
    }

    /**
     * Cancels a process instance.
     *
     * @param id the instance ID
     * @return the updated process instance
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ProcessInstanceResponse> cancelInstance(@PathVariable String id) {
        ProcessInstance instance = processInstanceService.cancelInstance(id);
        return ResponseEntity.ok(ProcessInstanceResponse.from(instance));
    }
}
