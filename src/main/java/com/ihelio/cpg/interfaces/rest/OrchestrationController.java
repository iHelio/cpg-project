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

import com.ihelio.cpg.application.orchestration.InstanceOrchestrator;
import com.ihelio.cpg.domain.exception.ProcessExecutionException;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.orchestration.OrchestrationEvent;
import com.ihelio.cpg.domain.orchestration.ProcessOrchestrator;
import com.ihelio.cpg.domain.orchestration.RuntimeContext;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import com.ihelio.cpg.domain.repository.ProcessInstanceRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final ProcessInstanceRepository processInstanceRepository;
    private final InstanceOrchestrator instanceOrchestrator;

    public OrchestrationController(
            ProcessOrchestrator processOrchestrator,
            ProcessGraphRepository processGraphRepository,
            ProcessInstanceRepository processInstanceRepository,
            InstanceOrchestrator instanceOrchestrator) {
        this.processOrchestrator = processOrchestrator;
        this.processGraphRepository = processGraphRepository;
        this.processInstanceRepository = processInstanceRepository;
        this.instanceOrchestrator = instanceOrchestrator;
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

    /**
     * Executes a single orchestration step for a process instance.
     *
     * <p>The orchestrator is completely event-driven - each call executes
     * at most one node. Call this repeatedly after signaling events to
     * progress through the workflow.
     *
     * @param instanceId the process instance ID
     * @return the updated orchestration status
     */
    @PostMapping("/{instanceId}/step")
    public ResponseEntity<OrchestrationStatusResponse> step(
            @PathVariable String instanceId) {

        log.info("Stepping orchestration for instance: {}", instanceId);

        ProcessInstance.ProcessInstanceId id =
            new ProcessInstance.ProcessInstanceId(instanceId);

        ProcessInstance instance = processInstanceRepository.findById(id)
            .orElse(null);

        if (instance == null) {
            return ResponseEntity.notFound().build();
        }

        if (!instance.isRunning()) {
            ProcessOrchestrator.OrchestrationStatus status = processOrchestrator.getStatus(id);
            return ResponseEntity.ok(OrchestrationStatusResponse.from(status));
        }

        ProcessGraph graph = processGraphRepository.findByIdAndVersion(
                instance.processGraphId(), instance.processGraphVersion())
            .orElseThrow(() -> new ProcessExecutionException(
                "Process graph not found: " + instance.processGraphId().value(),
                ProcessExecutionException.ErrorType.GRAPH_NOT_FOUND
            ));

        // Execute a single orchestration step
        InstanceOrchestrator.OrchestrationResult result =
            instanceOrchestrator.orchestrate(instance, graph, null);

        // Save updated instance
        processInstanceRepository.save(result.instance());

        // Build and return status
        ProcessOrchestrator.OrchestrationStatus status = new ProcessOrchestrator.OrchestrationStatus(
            result.instance(),
            result.decision(),
            result.trace(),
            result.isExecuted() || result.isWaiting()
        );

        return ResponseEntity.ok(OrchestrationStatusResponse.from(status));
    }

    /**
     * Gets available events that can be sent to progress the instance.
     *
     * @param instanceId the process instance ID
     * @return list of available events with payloads
     */
    @GetMapping("/{instanceId}/available-events")
    public ResponseEntity<Map<String, Object>> getAvailableEvents(
            @PathVariable String instanceId) {

        log.info("Getting available events for instance: {}", instanceId);

        ProcessInstance.ProcessInstanceId id =
            new ProcessInstance.ProcessInstanceId(instanceId);

        ProcessInstance instance = processInstanceRepository.findById(id)
            .orElse(null);

        if (instance == null) {
            return ResponseEntity.notFound().build();
        }

        ProcessGraph graph = processGraphRepository.findByIdAndVersion(
                instance.processGraphId(), instance.processGraphVersion())
            .orElse(null);

        if (graph == null) {
            return ResponseEntity.notFound().build();
        }

        List<Map<String, Object>> availableEvents = new ArrayList<>();

        // Check each completed node for outbound edge event triggers
        for (ProcessInstance.NodeExecution execution : instance.nodeExecutions()) {
            if (execution.status() != ProcessInstance.NodeExecutionStatus.COMPLETED) {
                continue;
            }

            List<Edge> outboundEdges = graph.getOutboundEdges(execution.nodeId());
            for (Edge edge : outboundEdges) {
                Node targetNode = graph.findNode(edge.targetNodeId()).orElse(null);
                if (targetNode == null || instance.hasExecutedNode(edge.targetNodeId())) {
                    continue;
                }

                for (String eventType : edge.eventTriggers().activatingEvents()) {
                    Map<String, Object> eventInfo = new LinkedHashMap<>();
                    eventInfo.put("eventType", eventType);
                    eventInfo.put("targetNode", targetNode.name());
                    eventInfo.put("edgeName", edge.name() != null ? edge.name() : "");
                    eventInfo.put("description", getEventDescription(eventType));
                    eventInfo.put("payload", generateEventPayload(eventType));
                    availableEvents.add(eventInfo);
                }
            }
        }

        // Remove duplicates
        Map<String, Map<String, Object>> uniqueEvents = new LinkedHashMap<>();
        for (Map<String, Object> event : availableEvents) {
            String type = (String) event.get("eventType");
            if (!uniqueEvents.containsKey(type)) {
                uniqueEvents.put(type, event);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("instanceId", instanceId);
        response.put("instanceStatus", instance.status().name());
        response.put("availableEvents", new ArrayList<>(uniqueEvents.values()));

        return ResponseEntity.ok(response);
    }

    /**
     * Sends an event with auto-populated payload to progress the instance.
     *
     * @param instanceId the process instance ID
     * @param eventType the event type to send
     * @return the updated orchestration status
     */
    @PostMapping("/{instanceId}/send-event/{eventType}")
    public ResponseEntity<Map<String, Object>> sendEvent(
            @PathVariable String instanceId,
            @PathVariable String eventType) {

        log.info("Sending event {} to instance {}", eventType, instanceId);

        ProcessInstance.ProcessInstanceId id =
            new ProcessInstance.ProcessInstanceId(instanceId);

        ProcessInstance instance = processInstanceRepository.findById(id)
            .orElse(null);

        if (instance == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> payload = generateEventPayload(eventType);

        OrchestrationEvent event = OrchestrationEvent.DomainEvent.of(
            eventType,
            instanceId,
            payload
        );

        processOrchestrator.signal(event);

        ProcessOrchestrator.OrchestrationStatus status = processOrchestrator.getStatus(id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("instanceId", instanceId);
        response.put("eventType", eventType);
        response.put("payload", payload);
        response.put("sent", true);

        if (status != null) {
            response.put("instanceStatus", status.instance().status().name());
            response.put("isActive", status.isActive());
        }

        return ResponseEntity.ok(response);
    }

    private String getEventDescription(String eventType) {
        return switch (eventType) {
            case "OnboardingStarted" -> "Signals that onboarding process has started";
            case "BackgroundCheckCompleted" -> "Signals that background check has completed";
            case "BackgroundCheckFailed" -> "Signals that background check has failed";
            case "BackgroundReviewCompleted" -> "Signals that manual review is complete";
            case "EquipmentReady" -> "Signals that ordered equipment is ready for shipping";
            case "EquipmentShipped" -> "Signals that equipment has been shipped";
            case "DocumentsCollected" -> "Signals that required documents have been collected";
            case "I9Verified" -> "Signals that I-9 verification is complete";
            case "OrientationScheduled" -> "Signals that orientation has been scheduled";
            default -> "Domain event: " + eventType;
        };
    }

    private Map<String, Object> generateEventPayload(String eventType) {
        return switch (eventType) {
            case "OnboardingStarted" -> Map.of(
                "timestamp", Instant.now().toString(),
                "source", "orchestrator"
            );
            case "BackgroundCheckCompleted" -> Map.of(
                "status", "COMPLETED",
                "passed", true,
                "requiresReview", false,
                "timestamp", Instant.now().toString()
            );
            case "BackgroundCheckFailed" -> Map.of(
                "status", "FAILED",
                "passed", false,
                "reason", "Background check did not pass",
                "timestamp", Instant.now().toString()
            );
            case "BackgroundReviewCompleted" -> Map.of(
                "decision", "APPROVED",
                "reviewer", "hr-manager",
                "comments", "Review completed successfully",
                "timestamp", Instant.now().toString()
            );
            case "EquipmentReady" -> Map.of(
                "orderId", "EQ-" + System.currentTimeMillis(),
                "status", "READY",
                "items", List.of("laptop", "monitor", "keyboard"),
                "timestamp", Instant.now().toString()
            );
            case "EquipmentShipped" -> Map.of(
                "trackingNumber", "TRK-" + System.currentTimeMillis(),
                "carrier", "FedEx",
                "estimatedDelivery", LocalDate.now().plusDays(3).toString(),
                "timestamp", Instant.now().toString()
            );
            case "DocumentsCollected" -> Map.of(
                "i9Part1Completed", true,
                "w4Completed", true,
                "directDepositCompleted", true,
                "timestamp", Instant.now().toString()
            );
            case "I9Verified" -> Map.of(
                "verified", true,
                "verificationDate", LocalDate.now().toString(),
                "documentType", "passport",
                "timestamp", Instant.now().toString()
            );
            case "OrientationScheduled" -> Map.of(
                "scheduled", true,
                "date", LocalDate.now().plusDays(14).toString(),
                "time", "09:00",
                "location", "Virtual",
                "timestamp", Instant.now().toString()
            );
            default -> Map.of(
                "eventType", eventType,
                "timestamp", Instant.now().toString()
            );
        };
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
            default -> {
                // Treat unknown event types as domain events
                yield OrchestrationEvent.DomainEvent.of(
                    request.eventType(),
                    instanceId.value(),
                    request.payload() != null ? request.payload() : Map.of()
                );
            }
        };
    }
}
