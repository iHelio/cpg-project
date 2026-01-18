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

package com.ihelio.cpg.domain.engine;

import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Handles compensation logic for failed actions.
 *
 * <p>Determines the appropriate compensation strategy based on:
 * <ul>
 *   <li>Node exception routes (remediation and escalation)</li>
 *   <li>Edge compensation semantics</li>
 *   <li>Retry counts and limits</li>
 * </ul>
 */
public class CompensationHandler {

    // Track retry counts per (processInstance, node) pair
    private final Map<String, Integer> retryCounts = new HashMap<>();

    /**
     * Determines the compensation action for a failed node execution.
     *
     * @param instance the process instance
     * @param graph the process graph
     * @param node the node that failed
     * @param exceptionType the type of exception
     * @param error the error message
     * @return the compensation action to take
     */
    public CompensationAction determineCompensation(
            ProcessInstance instance,
            ProcessGraph graph,
            Node node,
            String exceptionType,
            String error) {

        Objects.requireNonNull(node, "Node is required");

        // 1. Check for matching remediation route
        Optional<CompensationAction> remediationAction =
            findRemediationAction(instance, node, exceptionType);
        if (remediationAction.isPresent()) {
            return remediationAction.get();
        }

        // 2. Check for matching escalation route
        Optional<CompensationAction> escalationAction =
            findEscalationAction(node, exceptionType);
        if (escalationAction.isPresent()) {
            return escalationAction.get();
        }

        // 3. Check edge compensation semantics
        Optional<CompensationAction> edgeCompensation =
            findEdgeCompensation(instance, graph, node);
        if (edgeCompensation.isPresent()) {
            return edgeCompensation.get();
        }

        // 4. Default: check action retry config
        if (node.action().config().retryCount() > 0) {
            int currentRetries = getRetryCount(instance, node);
            if (currentRetries < node.action().config().retryCount()) {
                incrementRetryCount(instance, node);
                return CompensationAction.retry(
                    currentRetries,
                    node.action().config().retryCount()
                );
            }
        }

        // 5. No compensation available - fail
        return CompensationAction.fail(
            "No compensation strategy available for: " + error);
    }

    private Optional<CompensationAction> findRemediationAction(
            ProcessInstance instance,
            Node node,
            String exceptionType) {

        Node.ExceptionRoutes routes = node.exceptionRoutes();
        if (routes == null) {
            return Optional.empty();
        }

        for (Node.RemediationRoute route : routes.remediationRoutes()) {
            if (matchesExceptionType(route.exceptionType(), exceptionType)) {
                return Optional.of(createRemediationAction(instance, node, route));
            }
        }

        return Optional.empty();
    }

    private CompensationAction createRemediationAction(
            ProcessInstance instance,
            Node node,
            Node.RemediationRoute route) {

        return switch (route.strategy()) {
            case RETRY -> {
                int currentRetries = getRetryCount(instance, node);
                if (currentRetries < route.maxRetries()) {
                    incrementRetryCount(instance, node);
                    yield CompensationAction.retry(currentRetries, route.maxRetries());
                }
                yield CompensationAction.fail(
                    "Max retries exceeded for: " + route.exceptionType());
            }
            case COMPENSATE -> CompensationAction.rollback(
                null,
                "Compensating for: " + route.exceptionType());
            case ALTERNATE -> CompensationAction.alternate(
                new Node.NodeId(route.alternateNodeId()),
                "Taking alternate path for: " + route.exceptionType());
            case SKIP -> CompensationAction.skip(
                "Skipping due to: " + route.exceptionType());
            case FAIL -> CompensationAction.fail(
                "Failing due to: " + route.exceptionType());
        };
    }

    private Optional<CompensationAction> findEscalationAction(
            Node node,
            String exceptionType) {

        Node.ExceptionRoutes routes = node.exceptionRoutes();
        if (routes == null) {
            return Optional.empty();
        }

        for (Node.EscalationRoute route : routes.escalationRoutes()) {
            if (matchesExceptionType(route.exceptionType(), exceptionType)) {
                return Optional.of(CompensationAction.escalate(
                    new Node.NodeId(route.escalationNodeId()),
                    "Escalating: " + exceptionType
                ));
            }
        }

        return Optional.empty();
    }

    private Optional<CompensationAction> findEdgeCompensation(
            ProcessInstance instance,
            ProcessGraph graph,
            Node node) {

        // Find edges that led to this node
        for (Edge edge : graph.getInboundEdges(node.id())) {
            Edge.CompensationSemantics compensation = edge.compensationSemantics();
            if (compensation.hasCompensation()) {
                return Optional.of(createEdgeCompensationAction(
                    instance, node, edge, compensation));
            }
        }

        return Optional.empty();
    }

    private CompensationAction createEdgeCompensationAction(
            ProcessInstance instance,
            Node node,
            Edge edge,
            Edge.CompensationSemantics compensation) {

        return switch (compensation.strategy()) {
            case RETRY -> {
                int currentRetries = getRetryCount(instance, node);
                if (currentRetries < compensation.maxRetries()) {
                    incrementRetryCount(instance, node);
                    yield CompensationAction.retry(currentRetries, compensation.maxRetries());
                }
                yield CompensationAction.fail("Max edge retries exceeded");
            }
            case ROLLBACK -> CompensationAction.rollback(
                new Edge.EdgeId(compensation.compensatingEdgeId()),
                "Rolling back via edge compensation"
            );
            case ALTERNATE -> CompensationAction.skip(
                "Taking alternate path via edge compensation"
            );
            case ESCALATE -> CompensationAction.escalate(
                null,
                "Escalating via edge compensation"
            );
        };
    }

    private boolean matchesExceptionType(String pattern, String actual) {
        if (pattern == null || actual == null) {
            return false;
        }
        // Support wildcard matching
        if (pattern.equals("*") || pattern.equals("ANY")) {
            return true;
        }
        return pattern.equals(actual) || actual.contains(pattern);
    }

    private String retryKey(ProcessInstance instance, Node node) {
        return instance.id().value() + ":" + node.id().value();
    }

    private int getRetryCount(ProcessInstance instance, Node node) {
        return retryCounts.getOrDefault(retryKey(instance, node), 0);
    }

    private void incrementRetryCount(ProcessInstance instance, Node node) {
        String key = retryKey(instance, node);
        retryCounts.put(key, retryCounts.getOrDefault(key, 0) + 1);
    }

    /**
     * Resets retry count for a node (e.g., after successful execution).
     */
    public void resetRetryCount(ProcessInstance instance, Node node) {
        retryCounts.remove(retryKey(instance, node));
    }

    /**
     * Cleans up tracking for a completed process instance.
     */
    public void cleanupInstance(ProcessInstance instance) {
        String prefix = instance.id().value() + ":";
        retryCounts.keySet().removeIf(key -> key.startsWith(prefix));
    }
}
