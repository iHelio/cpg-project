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

package com.ihelio.cpg.application.service;

import com.ihelio.cpg.domain.engine.ProcessExecutionEngine;
import com.ihelio.cpg.domain.exception.ProcessExecutionException;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.execution.ProcessInstance.ProcessInstanceStatus;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import com.ihelio.cpg.domain.repository.ProcessInstanceRepository;
import com.ihelio.cpg.interfaces.rest.dto.request.StartProcessRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Application service for process instance lifecycle management.
 *
 * <p>This service handles starting, suspending, resuming, and cancelling
 * process instances.
 */
@Service
public class ProcessInstanceService {

    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessGraphRepository processGraphRepository;
    private final ProcessExecutionEngine processExecutionEngine;

    public ProcessInstanceService(
            ProcessInstanceRepository processInstanceRepository,
            ProcessGraphRepository processGraphRepository,
            ProcessExecutionEngine processExecutionEngine) {
        this.processInstanceRepository = processInstanceRepository;
        this.processGraphRepository = processGraphRepository;
        this.processExecutionEngine = processExecutionEngine;
    }

    /**
     * Starts a new process instance.
     *
     * @param request the start process request
     * @return the created process instance
     * @throws ProcessExecutionException if graph not found
     */
    public ProcessInstance startProcess(StartProcessRequest request) {
        ProcessGraph.ProcessGraphId graphId =
            new ProcessGraph.ProcessGraphId(request.processGraphId());

        ProcessGraph graph;
        if (request.version() != null) {
            graph = processGraphRepository.findByIdAndVersion(graphId, request.version())
                .orElseThrow(() -> new ProcessExecutionException(
                    "Process graph not found: " + request.processGraphId()
                        + " version " + request.version(),
                    ProcessExecutionException.ErrorType.GRAPH_NOT_FOUND
                ));
        } else {
            graph = processGraphRepository.findLatestVersion(graphId)
                .orElseThrow(() -> new ProcessExecutionException(
                    "Process graph not found: " + request.processGraphId(),
                    ProcessExecutionException.ErrorType.GRAPH_NOT_FOUND
                ));
        }

        ExecutionContext context = ExecutionContext.builder()
            .clientContext(request.clientContext())
            .domainContext(request.domainContext())
            .build();

        ProcessInstance instance = processExecutionEngine.startProcess(graph, context);

        // Set correlation ID if provided
        if (request.correlationId() != null) {
            instance = ProcessInstance.builder()
                .id(instance.id())
                .processGraphId(instance.processGraphId())
                .processGraphVersion(instance.processGraphVersion())
                .correlationId(request.correlationId())
                .startedAt(instance.startedAt())
                .status(instance.status())
                .context(instance.context())
                .nodeExecutions(instance.nodeExecutions())
                .activeNodeIds(instance.activeNodeIds())
                .pendingEdgeIds(instance.pendingEdgeIds())
                .build();
        }

        processInstanceRepository.save(instance);
        return instance;
    }

    /**
     * Retrieves a process instance by ID.
     *
     * @param id the instance ID
     * @return the instance, or empty if not found
     */
    public Optional<ProcessInstance> getInstance(String id) {
        return processInstanceRepository.findById(
            new ProcessInstance.ProcessInstanceId(id)
        );
    }

    /**
     * Lists process instances with optional filters.
     *
     * @param processGraphId optional graph ID filter
     * @param status optional status filter
     * @param correlationId optional correlation ID filter
     * @return list of matching instances
     */
    public List<ProcessInstance> listInstances(
            String processGraphId,
            ProcessInstanceStatus status,
            String correlationId) {

        if (correlationId != null) {
            return processInstanceRepository.findByCorrelationId(correlationId);
        }

        if (processGraphId != null) {
            return processInstanceRepository.findByProcessGraphId(
                new ProcessGraph.ProcessGraphId(processGraphId)
            );
        }

        if (status != null) {
            return processInstanceRepository.findByStatus(status);
        }

        // Return running instances by default
        return processInstanceRepository.findRunning();
    }

    /**
     * Suspends a running process instance.
     *
     * @param id the instance ID
     * @return the updated instance
     * @throws ProcessExecutionException if instance not found or invalid state
     */
    public ProcessInstance suspendInstance(String id) {
        ProcessInstance instance = getInstanceOrThrow(id);

        if (!instance.isRunning()) {
            throw ProcessExecutionException.withContext(
                "Cannot suspend instance in state: " + instance.status(),
                instance.id().value(),
                null,
                ProcessExecutionException.ErrorType.INVALID_STATE,
                false
            );
        }

        processExecutionEngine.suspendProcess(instance);
        processInstanceRepository.save(instance);
        return instance;
    }

    /**
     * Resumes a suspended process instance.
     *
     * @param id the instance ID
     * @return the updated instance
     * @throws ProcessExecutionException if instance not found or invalid state
     */
    public ProcessInstance resumeInstance(String id) {
        ProcessInstance instance = getInstanceOrThrow(id);

        if (instance.status() != ProcessInstanceStatus.SUSPENDED) {
            throw ProcessExecutionException.withContext(
                "Cannot resume instance in state: " + instance.status(),
                instance.id().value(),
                null,
                ProcessExecutionException.ErrorType.INVALID_STATE,
                false
            );
        }

        ProcessGraph graph = processGraphRepository.findByIdAndVersion(
                instance.processGraphId(),
                instance.processGraphVersion())
            .orElseThrow(() -> new ProcessExecutionException(
                "Process graph not found",
                ProcessExecutionException.ErrorType.GRAPH_NOT_FOUND
            ));

        processExecutionEngine.resumeProcess(instance, graph);
        processInstanceRepository.save(instance);
        return instance;
    }

    /**
     * Cancels a process instance.
     *
     * @param id the instance ID
     * @return the updated instance
     * @throws ProcessExecutionException if instance not found or already completed
     */
    public ProcessInstance cancelInstance(String id) {
        ProcessInstance instance = getInstanceOrThrow(id);

        if (instance.status() == ProcessInstanceStatus.COMPLETED
            || instance.status() == ProcessInstanceStatus.CANCELLED) {
            throw ProcessExecutionException.withContext(
                "Cannot cancel instance in state: " + instance.status(),
                instance.id().value(),
                null,
                ProcessExecutionException.ErrorType.INVALID_STATE,
                false
            );
        }

        instance.fail(); // Using fail() to mark as terminated
        processInstanceRepository.save(instance);
        return instance;
    }

    private ProcessInstance getInstanceOrThrow(String id) {
        return processInstanceRepository.findById(
                new ProcessInstance.ProcessInstanceId(id))
            .orElseThrow(() -> ProcessExecutionException.withContext(
                "Process instance not found: " + id,
                id,
                null,
                ProcessExecutionException.ErrorType.INSTANCE_NOT_FOUND,
                false
            ));
    }
}
