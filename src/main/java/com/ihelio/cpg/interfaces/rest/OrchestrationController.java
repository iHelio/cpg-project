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

import com.ihelio.cpg.domain.exception.ProcessExecutionException;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.orchestration.OrchestrationEvent;
import com.ihelio.cpg.domain.orchestration.ProcessOrchestrator;
import com.ihelio.cpg.domain.orchestration.RuntimeContext;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import com.ihelio.cpg.interfaces.rest.dto.request.SignalEventRequest;
import com.ihelio.cpg.interfaces.rest.dto.request.StartOrchestrationRequest;
import com.ihelio.cpg.interfaces.rest.dto.response.OrchestrationStatusResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the Process Orchestrator.
 *
 * <p>The orchestrator is the policy-enforcing navigation engine that
 * autonomously executes process graphs based on declared preconditions,
 * business rules, and policy outcomes.
 *
 * <p>Unlike the ProcessInstanceController which requires explicit node
 * execution requests, the orchestrator takes full control of the process
 * and automatically navigates to eligible nodes.
 */
@RestController
@RequestMapping("/api/v1/orchestration")
@Validated
public class OrchestrationController {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationController.class);

    private final ProcessOrchestrator processOrchestrator;
    private final ProcessGraphRepository processGraphRepository;

    public OrchestrationController(
            ProcessOrchestrator processOrchestrator,
            ProcessGraphRepository processGraphRepository) {
        this.processOrchestrator = processOrchestrator;
        this.processGraphRepository = processGraphRepository;
    }

    /**
     * Starts a new orchestrated process.
     *
     * <p>The orchestrator will automatically evaluate preconditions,
     * navigate to eligible entry nodes, and execute them. The process
     * continues autonomously until it completes, fails, or waits for
     * external events.
     *
     * @param request the start orchestration request
     * @return the orchestration status
     */
    @PostMapping("/start")
    public ResponseEntity<OrchestrationStatusResponse> startOrchestration(
            @Valid @RequestBody StartOrchestrationRequest request) {

        log.info("Starting orchestration for process graph: {}", request.processGraphId());

        ProcessGraph.ProcessGraphId graphId =
            new ProcessGraph.ProcessGraphId(request.processGraphId());

        ProcessGraph graph = processGraphRepository.findLatestVersion(graphId)
            .orElseThrow(() -> new ProcessExecutionException(
                "Process graph not found: " + request.processGraphId(),
                ProcessExecutionException.ErrorType.GRAPH_NOT_FOUND
            ));

        RuntimeContext initialContext = RuntimeContext.builder()
            .clientContext(request.clientContext())
            .domainContext(request.domainContext())
            .build();

        ProcessInstance instance = processOrchestrator.start(graph, initialContext);

        ProcessOrchestrator.OrchestrationStatus status =
            processOrchestrator.getStatus(instance.id());

        if (status == null) {
            status = new ProcessOrchestrator.OrchestrationStatus(
                instance, null, null, instance.isRunning());
        }

        OrchestrationStatusResponse response = OrchestrationStatusResponse.from(status);

        log.info("Orchestration started for instance: {}", instance.id().value());

        return ResponseEntity
            .created(URI.create("/api/v1/orchestration/" + instance.id().value()))
            .body(response);
    }

    /**
     * Gets the current orchestration status.
     *
     * @param instanceId the process instance ID
     * @return the orchestration status
     */
    @GetMapping("/{instanceId}")
    public ResponseEntity<OrchestrationStatusResponse> getStatus(
            @PathVariable String instanceId) {

        ProcessInstance.ProcessInstanceId id =
            new ProcessInstance.ProcessInstanceId(instanceId);

        ProcessOrchestrator.OrchestrationStatus status = processOrchestrator.getStatus(id);

        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(OrchestrationStatusResponse.from(status));
    }

    /**
     * Signals an event to the orchestrator.
     *
     * <p>Events trigger reevaluation of the process instance. The orchestrator
     * will rebuild the runtime context and recompute the eligible space,
     * potentially executing new nodes that become available.
     *
     * @param instanceId the process instance ID
     * @param request the event signal request
     * @return the updated orchestration status
     */
    @PostMapping("/{instanceId}/signal")
    public ResponseEntity<OrchestrationStatusResponse> signalEvent(
            @PathVariable String instanceId,
            @Valid @RequestBody SignalEventRequest request) {

        log.info("Signaling event {} to instance {}", request.eventType(), instanceId);

        ProcessInstance.ProcessInstanceId id =
            new ProcessInstance.ProcessInstanceId(instanceId);

        OrchestrationEvent event = createEvent(id, request);
        processOrchestrator.signal(event);

        ProcessOrchestrator.OrchestrationStatus status = processOrchestrator.getStatus(id);

        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(OrchestrationStatusResponse.from(status));
    }

    /**
     * Suspends orchestration of a process instance.
     *
     * @param instanceId the process instance ID
     * @return the updated orchestration status
     */
    @PostMapping("/{instanceId}/suspend")
    public ResponseEntity<OrchestrationStatusResponse> suspend(
            @PathVariable String instanceId) {

        log.info("Suspending orchestration for instance: {}", instanceId);

        ProcessInstance.ProcessInstanceId id =
            new ProcessInstance.ProcessInstanceId(instanceId);

        processOrchestrator.suspend(id);

        ProcessOrchestrator.OrchestrationStatus status = processOrchestrator.getStatus(id);

        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(OrchestrationStatusResponse.from(status));
    }

    /**
     * Resumes orchestration of a suspended process instance.
     *
     * @param instanceId the process instance ID
     * @return the updated orchestration status
     */
    @PostMapping("/{instanceId}/resume")
    public ResponseEntity<OrchestrationStatusResponse> resume(
            @PathVariable String instanceId) {

        log.info("Resuming orchestration for instance: {}", instanceId);

        ProcessInstance.ProcessInstanceId id =
            new ProcessInstance.ProcessInstanceId(instanceId);

        processOrchestrator.resume(id);

        ProcessOrchestrator.OrchestrationStatus status = processOrchestrator.getStatus(id);

        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(OrchestrationStatusResponse.from(status));
    }

    /**
     * Cancels orchestration of a process instance.
     *
     * @param instanceId the process instance ID
     * @return the updated orchestration status
     */
    @PostMapping("/{instanceId}/cancel")
    public ResponseEntity<OrchestrationStatusResponse> cancel(
            @PathVariable String instanceId) {

        log.info("Cancelling orchestration for instance: {}", instanceId);

        ProcessInstance.ProcessInstanceId id =
            new ProcessInstance.ProcessInstanceId(instanceId);

        processOrchestrator.cancel(id);

        ProcessOrchestrator.OrchestrationStatus status = processOrchestrator.getStatus(id);

        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(OrchestrationStatusResponse.from(status));
    }

    private OrchestrationEvent createEvent(
            ProcessInstance.ProcessInstanceId instanceId,
            SignalEventRequest request) {

        return switch (request.eventType().toUpperCase()) {
            case "NODE_COMPLETED" -> {
                String nodeId = request.nodeId();
                if (nodeId == null) {
                    throw new IllegalArgumentException("nodeId is required for NODE_COMPLETED events");
                }
                yield OrchestrationEvent.NodeCompleted.of(
                    instanceId,
                    new Node.NodeId(nodeId),
                    request.payload(),
                    0
                );
            }
            case "NODE_FAILED" -> {
                String nodeId = request.nodeId();
                if (nodeId == null) {
                    throw new IllegalArgumentException("nodeId is required for NODE_FAILED events");
                }
                String errorType = (String) request.payload().getOrDefault("errorType", "UNKNOWN");
                String errorMessage = (String) request.payload().getOrDefault("errorMessage", "Unknown error");
                boolean retryable = (Boolean) request.payload().getOrDefault("retryable", false);
                yield OrchestrationEvent.NodeFailed.of(
                    instanceId,
                    new Node.NodeId(nodeId),
                    errorType,
                    errorMessage,
                    retryable
                );
            }
            case "APPROVAL" -> {
                String nodeId = request.nodeId();
                if (nodeId == null) {
                    throw new IllegalArgumentException("nodeId is required for APPROVAL events");
                }
                String approver = (String) request.payload().getOrDefault("approver", "system");
                String comments = (String) request.payload().get("comments");
                yield OrchestrationEvent.Approval.approved(
                    instanceId,
                    new Node.NodeId(nodeId),
                    approver,
                    comments
                );
            }
            case "REJECTION" -> {
                String nodeId = request.nodeId();
                if (nodeId == null) {
                    throw new IllegalArgumentException("nodeId is required for REJECTION events");
                }
                String approver = (String) request.payload().getOrDefault("approver", "system");
                String reason = (String) request.payload().getOrDefault("reason", "Rejected");
                yield OrchestrationEvent.Approval.rejected(
                    instanceId,
                    new Node.NodeId(nodeId),
                    approver,
                    reason
                );
            }
            case "DATA_CHANGE" -> {
                String entityType = (String) request.payload().getOrDefault("entityType", "unknown");
                String entityId = (String) request.payload().getOrDefault("entityId", "unknown");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) request.payload().getOrDefault("data", Map.of());
                yield OrchestrationEvent.DataChange.updated(
                    entityType,
                    entityId,
                    java.util.List.of(),
                    data
                );
            }
            default -> throw new IllegalArgumentException(
                "Unsupported event type: " + request.eventType()
            );
        };
    }
}
