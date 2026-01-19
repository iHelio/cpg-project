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
import com.ihelio.cpg.domain.event.ProcessEvent;
import com.ihelio.cpg.domain.exception.ProcessExecutionException;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import com.ihelio.cpg.domain.repository.ProcessInstanceRepository;
import com.ihelio.cpg.interfaces.rest.dto.request.PublishEventRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Application service for process event operations.
 *
 * <p>This service handles publishing events to process instances and
 * retrieving event history.
 */
@Service
public class ProcessEventService {

    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessGraphRepository processGraphRepository;
    private final ProcessExecutionEngine processExecutionEngine;

    public ProcessEventService(
            ProcessInstanceRepository processInstanceRepository,
            ProcessGraphRepository processGraphRepository,
            ProcessExecutionEngine processExecutionEngine) {
        this.processInstanceRepository = processInstanceRepository;
        this.processGraphRepository = processGraphRepository;
        this.processExecutionEngine = processExecutionEngine;
    }

    /**
     * Publishes an event to matching process instances.
     *
     * @param request the publish event request
     * @return list of affected process instances
     */
    public List<ProcessInstance> publishEvent(PublishEventRequest request) {
        ProcessEvent.SourceType sourceType = ProcessEvent.SourceType.valueOf(
            request.sourceType().toUpperCase()
        );
        ProcessEvent event = ProcessEvent.of(
            request.eventType(),
            new ProcessEvent.EventSource(sourceType, request.sourceIdentifier()),
            request.correlationId(),
            request.payload()
        );

        // Find running instances that might be affected
        List<ProcessInstance> candidates;
        if (request.correlationId() != null) {
            candidates = processInstanceRepository.findByCorrelationId(request.correlationId());
        } else {
            candidates = processInstanceRepository.findByStatus(
                ProcessInstance.ProcessInstanceStatus.RUNNING
            );
        }

        // Group instances by their process graph
        Map<ProcessGraph.ProcessGraphId, List<ProcessInstance>> instancesByGraph =
            candidates.stream()
                .collect(Collectors.groupingBy(ProcessInstance::processGraphId));

        List<ProcessInstance> allAffected = new ArrayList<>();

        // Process each group
        for (var entry : instancesByGraph.entrySet()) {
            ProcessGraph graph = processGraphRepository.findLatestVersion(entry.getKey())
                .orElse(null);

            if (graph != null) {
                List<ProcessInstance> affected = processExecutionEngine.handleEvent(
                    event,
                    entry.getValue(),
                    graph
                );

                // Save affected instances
                for (ProcessInstance instance : affected) {
                    processInstanceRepository.save(instance);
                }

                allAffected.addAll(affected);
            }
        }

        return allAffected;
    }

    /**
     * Gets the event history for a process instance.
     *
     * @param instanceId the instance ID
     * @return list of received events
     * @throws ProcessExecutionException if instance not found
     */
    public List<ExecutionContext.ReceivedEvent> getEventHistory(String instanceId) {
        ProcessInstance instance = processInstanceRepository.findById(
                new ProcessInstance.ProcessInstanceId(instanceId))
            .orElseThrow(() -> ProcessExecutionException.withContext(
                "Process instance not found: " + instanceId,
                instanceId,
                null,
                ProcessExecutionException.ErrorType.INSTANCE_NOT_FOUND,
                false
            ));

        return instance.context().eventHistory();
    }
}
