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

package com.ihelio.cpg.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * A Node represents a governed decision point in a process graph.
 *
 * <p>Nodes are intentionally action-oriented rather than state-oriented.
 * They describe what CAN BE DONE next, given the current context, not what
 * IS true about the system.
 *
 * <p>Each node combines multiple concerns into a single, governed decision point:
 * preconditions, policy gates, business rules, actions, events, and exception routes.
 */
public record Node(
    NodeId id,
    String name,
    String description,
    int version,
    Preconditions preconditions,
    List<PolicyGate> policyGates,
    List<BusinessRule> businessRules,
    Action action,
    EventConfig eventConfig,
    ExceptionRoutes exceptionRoutes
) {

    public Node {
        Objects.requireNonNull(id, "Node id is required");
        Objects.requireNonNull(name, "Node name is required");
        Objects.requireNonNull(action, "Node action is required");
        policyGates = policyGates != null ? List.copyOf(policyGates) : List.of();
        businessRules = businessRules != null ? List.copyOf(businessRules) : List.of();
    }

    /**
     * Unique identifier for a node.
     */
    public record NodeId(String value) {
        public NodeId {
            Objects.requireNonNull(value, "NodeId value is required");
            if (value.isBlank()) {
                throw new IllegalArgumentException("NodeId cannot be blank");
            }
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * Preconditions derived from client-specific and domain context.
     * Evaluated using FEEL expressions.
     */
    public record Preconditions(
        List<FeelExpression> clientContextConditions,
        List<FeelExpression> domainContextConditions
    ) {
        public Preconditions {
            clientContextConditions = clientContextConditions != null
                ? List.copyOf(clientContextConditions) : List.of();
            domainContextConditions = domainContextConditions != null
                ? List.copyOf(domainContextConditions) : List.of();
        }

        public static Preconditions none() {
            return new Preconditions(List.of(), List.of());
        }
    }

    /**
     * Policy gate enforcing compliance and statutory requirements.
     */
    public record PolicyGate(
        String id,
        String name,
        PolicyType type,
        String dmnDecisionRef,
        String requiredOutcome
    ) {
        public PolicyGate {
            Objects.requireNonNull(id, "PolicyGate id is required");
            Objects.requireNonNull(type, "PolicyGate type is required");
        }
    }

    /**
     * Type of policy gate.
     */
    public enum PolicyType {
        COMPLIANCE,
        STATUTORY,
        REGULATORY,
        ORGANIZATIONAL
    }

    /**
     * Business rule that derives execution parameters and obligations.
     */
    public record BusinessRule(
        String id,
        String name,
        String dmnDecisionRef,
        RuleCategory category
    ) {
        public BusinessRule {
            Objects.requireNonNull(id, "BusinessRule id is required");
        }
    }

    /**
     * Category of business rule.
     */
    public enum RuleCategory {
        EXECUTION_PARAMETER,
        OBLIGATION,
        SLA,
        DERIVATION
    }

    /**
     * Action to be executed when the node is activated.
     */
    public record Action(
        ActionType type,
        String handlerRef,
        String description,
        ActionConfig config
    ) {
        public Action {
            Objects.requireNonNull(type, "Action type is required");
            Objects.requireNonNull(handlerRef, "Action handlerRef is required");
        }
    }

    /**
     * Type of action.
     */
    public enum ActionType {
        SYSTEM_INVOCATION,
        HUMAN_TASK,
        AGENT_ASSISTED,
        DECISION,
        NOTIFICATION,
        WAIT
    }

    /**
     * Configuration for action execution.
     */
    public record ActionConfig(
        boolean asynchronous,
        int timeoutSeconds,
        int retryCount,
        String assigneeExpression,
        String formRef
    ) {
        public static ActionConfig defaults() {
            return new ActionConfig(false, 300, 0, null, null);
        }

        public static ActionConfig async() {
            return new ActionConfig(true, 300, 0, null, null);
        }
    }

    /**
     * Event subscription and emission configuration.
     */
    public record EventConfig(
        List<EventSubscription> subscribes,
        List<EventEmission> emits
    ) {
        public EventConfig {
            subscribes = subscribes != null ? List.copyOf(subscribes) : List.of();
            emits = emits != null ? List.copyOf(emits) : List.of();
        }

        public static EventConfig none() {
            return new EventConfig(List.of(), List.of());
        }
    }

    /**
     * Event subscription that can trigger node availability.
     */
    public record EventSubscription(
        String eventType,
        FeelExpression correlationExpression
    ) {
        public EventSubscription {
            Objects.requireNonNull(eventType, "EventSubscription eventType is required");
        }
    }

    /**
     * Event emitted on node execution.
     */
    public record EventEmission(
        String eventType,
        EmissionTiming timing,
        FeelExpression payloadExpression
    ) {
        public EventEmission {
            Objects.requireNonNull(eventType, "EventEmission eventType is required");
            timing = timing != null ? timing : EmissionTiming.ON_COMPLETE;
        }
    }

    /**
     * When an event is emitted.
     */
    public enum EmissionTiming {
        ON_START,
        ON_COMPLETE,
        ON_FAILURE
    }

    /**
     * Exception handling routes for remediation or escalation.
     */
    public record ExceptionRoutes(
        List<RemediationRoute> remediationRoutes,
        List<EscalationRoute> escalationRoutes
    ) {
        public ExceptionRoutes {
            remediationRoutes = remediationRoutes != null
                ? List.copyOf(remediationRoutes) : List.of();
            escalationRoutes = escalationRoutes != null
                ? List.copyOf(escalationRoutes) : List.of();
        }

        public static ExceptionRoutes none() {
            return new ExceptionRoutes(List.of(), List.of());
        }
    }

    /**
     * Remediation route for handling specific exception types.
     */
    public record RemediationRoute(
        String exceptionType,
        RemediationStrategy strategy,
        int maxRetries,
        String alternateNodeId
    ) {
        public RemediationRoute {
            Objects.requireNonNull(exceptionType, "RemediationRoute exceptionType is required");
            Objects.requireNonNull(strategy, "RemediationRoute strategy is required");
        }
    }

    /**
     * Strategy for remediation.
     */
    public enum RemediationStrategy {
        RETRY,
        COMPENSATE,
        ALTERNATE,
        SKIP,
        FAIL
    }

    /**
     * Escalation route for critical failures.
     */
    public record EscalationRoute(
        String exceptionType,
        String escalationNodeId,
        String assigneeExpression,
        int slaMinutes
    ) {
        public EscalationRoute {
            Objects.requireNonNull(exceptionType, "EscalationRoute exceptionType is required");
            Objects.requireNonNull(escalationNodeId, "EscalationRoute escalationNodeId is required");
        }
    }
}
