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

package com.ihelio.cpg.domain.orchestration;

import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Node;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * OrchestrationEvent is a sealed interface representing events that trigger
 * reevaluation of the orchestration state.
 *
 * <p>Events can come from various sources:
 * <ul>
 *   <li>External data changes</li>
 *   <li>Human approvals/rejections</li>
 *   <li>System failures</li>
 *   <li>Timer expirations</li>
 *   <li>Policy updates</li>
 *   <li>Node execution completions/failures</li>
 * </ul>
 *
 * <p>Each event triggers a full reevaluation of the affected process instance(s),
 * rebuilding the runtime context and recomputing the eligible space.
 */
public sealed interface OrchestrationEvent
    permits OrchestrationEvent.DataChange,
            OrchestrationEvent.Approval,
            OrchestrationEvent.Failure,
            OrchestrationEvent.TimerExpired,
            OrchestrationEvent.PolicyUpdate,
            OrchestrationEvent.NodeCompleted,
            OrchestrationEvent.NodeFailed,
            OrchestrationEvent.DomainEvent {

    /**
     * Returns the unique event ID.
     */
    String eventId();

    /**
     * Returns the event timestamp.
     */
    Instant timestamp();

    /**
     * Returns the correlation ID for matching to process instances.
     */
    String correlationId();

    /**
     * Returns the event type name.
     */
    String eventType();

    /**
     * Event triggered when external domain data changes.
     *
     * @param eventId unique event identifier
     * @param timestamp when the event occurred
     * @param correlationId correlation to process instances
     * @param entityType the type of entity that changed
     * @param entityId the ID of the entity that changed
     * @param changeType the type of change (CREATED, UPDATED, DELETED)
     * @param changedFields the fields that changed
     * @param payload the new data values
     */
    record DataChange(
        String eventId,
        Instant timestamp,
        String correlationId,
        String entityType,
        String entityId,
        ChangeType changeType,
        java.util.List<String> changedFields,
        Map<String, Object> payload
    ) implements OrchestrationEvent {

        public DataChange {
            Objects.requireNonNull(eventId, "eventId is required");
            Objects.requireNonNull(timestamp, "timestamp is required");
            Objects.requireNonNull(entityType, "entityType is required");
            Objects.requireNonNull(entityId, "entityId is required");
            Objects.requireNonNull(changeType, "changeType is required");
            changedFields = changedFields != null ? java.util.List.copyOf(changedFields) : java.util.List.of();
            payload = payload != null ? Map.copyOf(payload) : Map.of();
        }

        @Override
        public String eventType() {
            return "data.change." + entityType.toLowerCase();
        }

        public static DataChange created(String entityType, String entityId, Map<String, Object> payload) {
            return new DataChange(
                UUID.randomUUID().toString(),
                Instant.now(),
                null,
                entityType,
                entityId,
                ChangeType.CREATED,
                java.util.List.of(),
                payload
            );
        }

        public static DataChange updated(String entityType, String entityId,
                java.util.List<String> changedFields, Map<String, Object> payload) {
            return new DataChange(
                UUID.randomUUID().toString(),
                Instant.now(),
                null,
                entityType,
                entityId,
                ChangeType.UPDATED,
                changedFields,
                payload
            );
        }

        public enum ChangeType {
            CREATED,
            UPDATED,
            DELETED
        }
    }

    /**
     * Event triggered when a human approval or rejection is received.
     *
     * @param eventId unique event identifier
     * @param timestamp when the event occurred
     * @param correlationId correlation to process instances
     * @param instanceId the process instance this approval is for
     * @param nodeId the node awaiting approval
     * @param approver the person/role who approved
     * @param decision the approval decision
     * @param comments optional comments
     * @param metadata additional approval metadata
     */
    record Approval(
        String eventId,
        Instant timestamp,
        String correlationId,
        ProcessInstance.ProcessInstanceId instanceId,
        Node.NodeId nodeId,
        String approver,
        ApprovalDecision decision,
        String comments,
        Map<String, Object> metadata
    ) implements OrchestrationEvent {

        public Approval {
            Objects.requireNonNull(eventId, "eventId is required");
            Objects.requireNonNull(timestamp, "timestamp is required");
            Objects.requireNonNull(instanceId, "instanceId is required");
            Objects.requireNonNull(nodeId, "nodeId is required");
            Objects.requireNonNull(approver, "approver is required");
            Objects.requireNonNull(decision, "decision is required");
            metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        }

        @Override
        public String eventType() {
            return "approval." + decision.name().toLowerCase();
        }

        public static Approval approved(ProcessInstance.ProcessInstanceId instanceId,
                Node.NodeId nodeId, String approver, String comments) {
            return new Approval(
                UUID.randomUUID().toString(),
                Instant.now(),
                instanceId.value(),
                instanceId,
                nodeId,
                approver,
                ApprovalDecision.APPROVED,
                comments,
                Map.of()
            );
        }

        public static Approval rejected(ProcessInstance.ProcessInstanceId instanceId,
                Node.NodeId nodeId, String approver, String reason) {
            return new Approval(
                UUID.randomUUID().toString(),
                Instant.now(),
                instanceId.value(),
                instanceId,
                nodeId,
                approver,
                ApprovalDecision.REJECTED,
                reason,
                Map.of()
            );
        }

        public enum ApprovalDecision {
            APPROVED,
            REJECTED,
            ESCALATED,
            DEFERRED
        }
    }

    /**
     * Event triggered when an external system failure occurs.
     *
     * @param eventId unique event identifier
     * @param timestamp when the event occurred
     * @param correlationId correlation to process instances
     * @param systemName the name of the failed system
     * @param failureType the type of failure
     * @param errorCode the error code
     * @param errorMessage the error message
     * @param retryable whether the failure is retryable
     * @param affectedInstances list of affected instance IDs
     */
    record Failure(
        String eventId,
        Instant timestamp,
        String correlationId,
        String systemName,
        FailureType failureType,
        String errorCode,
        String errorMessage,
        boolean retryable,
        java.util.List<ProcessInstance.ProcessInstanceId> affectedInstances
    ) implements OrchestrationEvent {

        public Failure {
            Objects.requireNonNull(eventId, "eventId is required");
            Objects.requireNonNull(timestamp, "timestamp is required");
            Objects.requireNonNull(systemName, "systemName is required");
            Objects.requireNonNull(failureType, "failureType is required");
            affectedInstances = affectedInstances != null
                ? java.util.List.copyOf(affectedInstances) : java.util.List.of();
        }

        @Override
        public String eventType() {
            return "failure." + failureType.name().toLowerCase();
        }

        public static Failure systemError(String systemName, String errorCode,
                String errorMessage, boolean retryable) {
            return new Failure(
                UUID.randomUUID().toString(),
                Instant.now(),
                null,
                systemName,
                FailureType.SYSTEM_ERROR,
                errorCode,
                errorMessage,
                retryable,
                java.util.List.of()
            );
        }

        public enum FailureType {
            SYSTEM_ERROR,
            NETWORK_ERROR,
            TIMEOUT,
            VALIDATION_ERROR,
            AUTHORIZATION_ERROR
        }
    }

    /**
     * Event triggered when a timer (SLA/deadline) expires.
     *
     * @param eventId unique event identifier
     * @param timestamp when the event occurred
     * @param correlationId correlation to process instances
     * @param instanceId the process instance with the expired timer
     * @param timerId the timer identifier
     * @param timerType the type of timer
     * @param originalDeadline the original deadline that was set
     * @param obligationId the obligation this timer was tracking (if any)
     */
    record TimerExpired(
        String eventId,
        Instant timestamp,
        String correlationId,
        ProcessInstance.ProcessInstanceId instanceId,
        String timerId,
        TimerType timerType,
        Instant originalDeadline,
        String obligationId
    ) implements OrchestrationEvent {

        public TimerExpired {
            Objects.requireNonNull(eventId, "eventId is required");
            Objects.requireNonNull(timestamp, "timestamp is required");
            Objects.requireNonNull(instanceId, "instanceId is required");
            Objects.requireNonNull(timerId, "timerId is required");
            Objects.requireNonNull(timerType, "timerType is required");
            Objects.requireNonNull(originalDeadline, "originalDeadline is required");
        }

        @Override
        public String eventType() {
            return "timer." + timerType.name().toLowerCase() + ".expired";
        }

        public static TimerExpired slaExpired(ProcessInstance.ProcessInstanceId instanceId,
                String timerId, Instant deadline, String obligationId) {
            return new TimerExpired(
                UUID.randomUUID().toString(),
                Instant.now(),
                instanceId.value(),
                instanceId,
                timerId,
                TimerType.SLA,
                deadline,
                obligationId
            );
        }

        public enum TimerType {
            SLA,
            DEADLINE,
            REMINDER,
            ESCALATION
        }
    }

    /**
     * Event triggered when a policy definition is updated.
     *
     * @param eventId unique event identifier
     * @param timestamp when the event occurred
     * @param correlationId correlation to process instances
     * @param policyId the updated policy ID
     * @param policyName the policy name
     * @param changeType the type of change
     * @param effectiveFrom when the policy change takes effect
     * @param affectedNodeIds nodes that use this policy
     */
    record PolicyUpdate(
        String eventId,
        Instant timestamp,
        String correlationId,
        String policyId,
        String policyName,
        PolicyChangeType changeType,
        Instant effectiveFrom,
        java.util.List<Node.NodeId> affectedNodeIds
    ) implements OrchestrationEvent {

        public PolicyUpdate {
            Objects.requireNonNull(eventId, "eventId is required");
            Objects.requireNonNull(timestamp, "timestamp is required");
            Objects.requireNonNull(policyId, "policyId is required");
            Objects.requireNonNull(policyName, "policyName is required");
            Objects.requireNonNull(changeType, "changeType is required");
            affectedNodeIds = affectedNodeIds != null
                ? java.util.List.copyOf(affectedNodeIds) : java.util.List.of();
        }

        @Override
        public String eventType() {
            return "policy." + changeType.name().toLowerCase();
        }

        public enum PolicyChangeType {
            CREATED,
            UPDATED,
            ACTIVATED,
            DEACTIVATED,
            DELETED
        }
    }

    /**
     * Event triggered when a node execution completes successfully.
     *
     * @param eventId unique event identifier
     * @param timestamp when the event occurred
     * @param correlationId correlation to process instances
     * @param instanceId the process instance
     * @param nodeId the completed node
     * @param result the execution result
     * @param duration execution duration in milliseconds
     */
    record NodeCompleted(
        String eventId,
        Instant timestamp,
        String correlationId,
        ProcessInstance.ProcessInstanceId instanceId,
        Node.NodeId nodeId,
        Object result,
        long durationMs
    ) implements OrchestrationEvent {

        public NodeCompleted {
            Objects.requireNonNull(eventId, "eventId is required");
            Objects.requireNonNull(timestamp, "timestamp is required");
            Objects.requireNonNull(instanceId, "instanceId is required");
            Objects.requireNonNull(nodeId, "nodeId is required");
        }

        @Override
        public String eventType() {
            return "node.completed";
        }

        public static NodeCompleted of(ProcessInstance.ProcessInstanceId instanceId,
                Node.NodeId nodeId, Object result, long durationMs) {
            return new NodeCompleted(
                UUID.randomUUID().toString(),
                Instant.now(),
                instanceId.value(),
                instanceId,
                nodeId,
                result,
                durationMs
            );
        }
    }

    /**
     * Event triggered when a node execution fails.
     *
     * @param eventId unique event identifier
     * @param timestamp when the event occurred
     * @param correlationId correlation to process instances
     * @param instanceId the process instance
     * @param nodeId the failed node
     * @param errorType the type of error
     * @param errorMessage the error message
     * @param retryCount the number of retries attempted
     * @param retryable whether the failure is retryable
     */
    record NodeFailed(
        String eventId,
        Instant timestamp,
        String correlationId,
        ProcessInstance.ProcessInstanceId instanceId,
        Node.NodeId nodeId,
        String errorType,
        String errorMessage,
        int retryCount,
        boolean retryable
    ) implements OrchestrationEvent {

        public NodeFailed {
            Objects.requireNonNull(eventId, "eventId is required");
            Objects.requireNonNull(timestamp, "timestamp is required");
            Objects.requireNonNull(instanceId, "instanceId is required");
            Objects.requireNonNull(nodeId, "nodeId is required");
            Objects.requireNonNull(errorType, "errorType is required");
        }

        @Override
        public String eventType() {
            return "node.failed";
        }

        public static NodeFailed of(ProcessInstance.ProcessInstanceId instanceId,
                Node.NodeId nodeId, String errorType, String errorMessage, boolean retryable) {
            return new NodeFailed(
                UUID.randomUUID().toString(),
                Instant.now(),
                instanceId.value(),
                instanceId,
                nodeId,
                errorType,
                errorMessage,
                0,
                retryable
            );
        }
    }

    /**
     * Event triggered by domain-specific business events.
     *
     * <p>DomainEvent is a catch-all for custom domain events that are emitted
     * by nodes (e.g., "OnboardingStarted", "OfferAccepted", "BackgroundCheckCompleted")
     * and can trigger other nodes that subscribe to these events.
     *
     * @param eventId unique event identifier
     * @param timestamp when the event occurred
     * @param correlationId correlation to process instances
     * @param domainEventType the domain-specific event type name
     * @param sourceNodeId the node that emitted this event (if any)
     * @param payload the event payload data
     */
    record DomainEvent(
        String eventId,
        Instant timestamp,
        String correlationId,
        String domainEventType,
        Node.NodeId sourceNodeId,
        Map<String, Object> payload
    ) implements OrchestrationEvent {

        public DomainEvent {
            Objects.requireNonNull(eventId, "eventId is required");
            Objects.requireNonNull(timestamp, "timestamp is required");
            Objects.requireNonNull(domainEventType, "domainEventType is required");
            payload = payload != null ? Map.copyOf(payload) : Map.of();
        }

        @Override
        public String eventType() {
            return domainEventType;
        }

        /**
         * Creates a domain event with correlation ID.
         */
        public static DomainEvent of(String domainEventType, String correlationId,
                Map<String, Object> payload) {
            return new DomainEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                correlationId,
                domainEventType,
                null,
                payload
            );
        }

        /**
         * Creates a domain event from a node emission.
         */
        public static DomainEvent fromNode(String domainEventType,
                ProcessInstance.ProcessInstanceId instanceId,
                Node.NodeId sourceNodeId,
                Map<String, Object> payload) {
            return new DomainEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                instanceId.value(),
                domainEventType,
                sourceNodeId,
                payload
            );
        }

        /**
         * Creates a domain event without correlation (broadcasts to all running instances).
         */
        public static DomainEvent broadcast(String domainEventType, Map<String, Object> payload) {
            return new DomainEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                null,
                domainEventType,
                null,
                payload
            );
        }
    }
}
