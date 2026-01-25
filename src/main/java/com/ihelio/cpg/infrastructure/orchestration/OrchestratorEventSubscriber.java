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

package com.ihelio.cpg.infrastructure.orchestration;

import com.ihelio.cpg.domain.event.ProcessEvent;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.orchestration.OrchestrationEvent;
import com.ihelio.cpg.domain.orchestration.ProcessOrchestrator;
import com.ihelio.cpg.infrastructure.event.InMemoryEventPublisher;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OrchestratorEventSubscriber bridges the existing ProcessEvent system with the
 * orchestrator's OrchestrationEvent system.
 *
 * <p>It subscribes to ProcessEvents and converts them to OrchestrationEvents
 * for the orchestrator to process.
 */
public class OrchestratorEventSubscriber {

    private static final Logger LOG = LoggerFactory.getLogger(OrchestratorEventSubscriber.class);

    private final ProcessOrchestrator orchestrator;

    /**
     * Creates an OrchestratorEventSubscriber.
     *
     * @param orchestrator the process orchestrator
     */
    public OrchestratorEventSubscriber(ProcessOrchestrator orchestrator) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator is required");
    }

    /**
     * Subscribes to an InMemoryEventPublisher to receive events.
     *
     * @param publisher the event publisher to subscribe to
     */
    public void subscribeTo(InMemoryEventPublisher publisher) {
        Objects.requireNonNull(publisher, "publisher is required");
        publisher.subscribe(this::handleProcessEvent);
        LOG.info("Subscribed to process event publisher");
    }

    /**
     * Handles a ProcessEvent by converting it to an OrchestrationEvent.
     *
     * @param event the process event
     */
    public void handleProcessEvent(ProcessEvent event) {
        Objects.requireNonNull(event, "event is required");

        LOG.debug("Received process event: {} ({})", event.eventType(), event.eventId());

        OrchestrationEvent orchestrationEvent = convertToOrchestrationEvent(event);
        if (orchestrationEvent != null) {
            orchestrator.signal(orchestrationEvent);
        }
    }

    @SuppressWarnings("unchecked")
    private OrchestrationEvent convertToOrchestrationEvent(ProcessEvent event) {
        String eventType = event.eventType();

        // Node completion events
        if (eventType.startsWith("node.")) {
            return convertNodeEvent(event);
        }

        // Process events
        if (eventType.startsWith("process.")) {
            // Process events don't need orchestration signals
            return null;
        }

        // External data change events
        if (eventType.startsWith("data.")) {
            return convertDataChangeEvent(event);
        }

        // Approval events
        if (eventType.startsWith("approval.")) {
            return convertApprovalEvent(event);
        }

        // Timer events
        if (eventType.startsWith("timer.")) {
            return convertTimerEvent(event);
        }

        // Unknown event type - create generic data change
        LOG.debug("Unknown event type: {}, treating as data change", eventType);
        return createGenericDataChange(event);
    }

    @SuppressWarnings("unchecked")
    private OrchestrationEvent convertNodeEvent(ProcessEvent event) {
        String eventType = event.eventType();
        Map<String, Object> payload = event.payload();

        String nodeId = (String) payload.get("nodeId");
        String instanceId = (String) payload.get("processInstanceId");

        if (nodeId == null || instanceId == null) {
            LOG.warn("Node event missing required fields: nodeId={}, instanceId={}", nodeId, instanceId);
            return null;
        }

        ProcessInstance.ProcessInstanceId processInstanceId =
            new ProcessInstance.ProcessInstanceId(instanceId);
        Node.NodeId nodeIdValue = new Node.NodeId(nodeId);

        if ("node.executed".equals(eventType) || "node.completed".equals(eventType)) {
            Object result = payload.get("result");
            long durationMs = payload.containsKey("durationMs")
                ? ((Number) payload.get("durationMs")).longValue()
                : 0;

            return OrchestrationEvent.NodeCompleted.of(
                processInstanceId,
                nodeIdValue,
                result,
                durationMs
            );
        }

        if ("node.failed".equals(eventType)) {
            String errorType = (String) payload.getOrDefault("errorType", "UNKNOWN");
            String errorMessage = (String) payload.getOrDefault("error", "Unknown error");
            boolean retryable = (Boolean) payload.getOrDefault("retryable", false);

            return OrchestrationEvent.NodeFailed.of(
                processInstanceId,
                nodeIdValue,
                errorType,
                errorMessage,
                retryable
            );
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private OrchestrationEvent convertDataChangeEvent(ProcessEvent event) {
        Map<String, Object> payload = event.payload();

        String entityType = (String) payload.getOrDefault("entityType", "UNKNOWN");
        String entityId = (String) payload.getOrDefault("entityId", UUID.randomUUID().toString());
        String changeTypeStr = (String) payload.getOrDefault("changeType", "UPDATED");

        OrchestrationEvent.DataChange.ChangeType changeType;
        try {
            changeType = OrchestrationEvent.DataChange.ChangeType.valueOf(changeTypeStr);
        } catch (IllegalArgumentException e) {
            changeType = OrchestrationEvent.DataChange.ChangeType.UPDATED;
        }

        java.util.List<String> changedFields = payload.containsKey("changedFields")
            ? (java.util.List<String>) payload.get("changedFields")
            : java.util.List.of();

        return new OrchestrationEvent.DataChange(
            event.eventId(),
            event.timestamp(),
            event.correlationId(),
            entityType,
            entityId,
            changeType,
            changedFields,
            payload
        );
    }

    @SuppressWarnings("unchecked")
    private OrchestrationEvent convertApprovalEvent(ProcessEvent event) {
        Map<String, Object> payload = event.payload();

        String instanceId = (String) payload.get("processInstanceId");
        String nodeId = (String) payload.get("nodeId");
        String approver = (String) payload.getOrDefault("approver", "SYSTEM");
        String decisionStr = (String) payload.getOrDefault("decision", "APPROVED");
        String comments = (String) payload.get("comments");

        if (instanceId == null || nodeId == null) {
            LOG.warn("Approval event missing required fields: instanceId={}, nodeId={}",
                instanceId, nodeId);
            return null;
        }

        OrchestrationEvent.Approval.ApprovalDecision decision;
        try {
            decision = OrchestrationEvent.Approval.ApprovalDecision.valueOf(decisionStr);
        } catch (IllegalArgumentException e) {
            decision = OrchestrationEvent.Approval.ApprovalDecision.APPROVED;
        }

        return new OrchestrationEvent.Approval(
            event.eventId(),
            event.timestamp(),
            event.correlationId(),
            new ProcessInstance.ProcessInstanceId(instanceId),
            new Node.NodeId(nodeId),
            approver,
            decision,
            comments,
            payload
        );
    }

    @SuppressWarnings("unchecked")
    private OrchestrationEvent convertTimerEvent(ProcessEvent event) {
        Map<String, Object> payload = event.payload();

        String instanceId = (String) payload.get("processInstanceId");
        String timerId = (String) payload.getOrDefault("timerId", UUID.randomUUID().toString());
        String timerTypeStr = (String) payload.getOrDefault("timerType", "SLA");
        String obligationId = (String) payload.get("obligationId");

        if (instanceId == null) {
            LOG.warn("Timer event missing processInstanceId");
            return null;
        }

        OrchestrationEvent.TimerExpired.TimerType timerType;
        try {
            timerType = OrchestrationEvent.TimerExpired.TimerType.valueOf(timerTypeStr);
        } catch (IllegalArgumentException e) {
            timerType = OrchestrationEvent.TimerExpired.TimerType.SLA;
        }

        Instant deadline = payload.containsKey("deadline")
            ? Instant.parse((String) payload.get("deadline"))
            : event.timestamp();

        return new OrchestrationEvent.TimerExpired(
            event.eventId(),
            event.timestamp(),
            event.correlationId(),
            new ProcessInstance.ProcessInstanceId(instanceId),
            timerId,
            timerType,
            deadline,
            obligationId
        );
    }

    private OrchestrationEvent createGenericDataChange(ProcessEvent event) {
        return new OrchestrationEvent.DataChange(
            event.eventId(),
            event.timestamp(),
            event.correlationId(),
            "GENERIC",
            event.eventId(),
            OrchestrationEvent.DataChange.ChangeType.UPDATED,
            java.util.List.of(),
            event.payload()
        );
    }
}
