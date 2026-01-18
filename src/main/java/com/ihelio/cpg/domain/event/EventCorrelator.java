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

package com.ihelio.cpg.domain.event;

import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.expression.ExpressionEvaluator;
import com.ihelio.cpg.domain.model.FeelExpression;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Correlates incoming events to process instances.
 *
 * <p>Events are matched to process instances using:
 * <ul>
 *   <li>Correlation ID matching</li>
 *   <li>FEEL correlation expressions from node subscriptions</li>
 *   <li>Event type matching against subscribed nodes</li>
 * </ul>
 */
public class EventCorrelator {

    private final ExpressionEvaluator expressionEvaluator;

    public EventCorrelator(ExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = Objects.requireNonNull(expressionEvaluator);
    }

    /**
     * Finds all process instances that correlate with the given event.
     *
     * @param event the incoming event
     * @param instances the candidate process instances
     * @param graph the process graph
     * @return list of matching process instances with correlation details
     */
    public List<CorrelationMatch> correlate(
            ProcessEvent event,
            List<ProcessInstance> instances,
            ProcessGraph graph) {

        List<CorrelationMatch> matches = new ArrayList<>();

        for (ProcessInstance instance : instances) {
            if (!instance.isRunning()) {
                continue;
            }

            CorrelationMatch match = matchInstance(event, instance, graph);
            if (match != null) {
                matches.add(match);
            }
        }

        return matches;
    }

    /**
     * Checks if an event correlates with a specific process instance.
     *
     * @param event the incoming event
     * @param instance the process instance to check
     * @param graph the process graph
     * @return correlation match if correlated, null otherwise
     */
    public CorrelationMatch matchInstance(
            ProcessEvent event,
            ProcessInstance instance,
            ProcessGraph graph) {

        // 1. Check direct correlation ID match
        if (event.correlationId() != null) {
            if (event.correlationId().equals(instance.id().value()) ||
                event.correlationId().equals(instance.correlationId())) {
                return new CorrelationMatch(
                    instance,
                    CorrelationMethod.CORRELATION_ID,
                    List.of()
                );
            }
        }

        // 2. Find nodes subscribed to this event type
        List<Node> subscribedNodes = graph.getNodesSubscribedToEvent(event.eventType());
        if (subscribedNodes.isEmpty()) {
            return null;
        }

        // 3. Evaluate correlation expressions
        List<Node> matchingNodes = new ArrayList<>();

        for (Node node : subscribedNodes) {
            if (matchesNodeSubscription(event, instance, node)) {
                matchingNodes.add(node);
            }
        }

        if (!matchingNodes.isEmpty()) {
            return new CorrelationMatch(
                instance,
                CorrelationMethod.EXPRESSION,
                matchingNodes
            );
        }

        return null;
    }

    private boolean matchesNodeSubscription(
            ProcessEvent event,
            ProcessInstance instance,
            Node node) {

        if (node.eventConfig() == null) {
            return false;
        }

        for (Node.EventSubscription subscription : node.eventConfig().subscribes()) {
            if (!subscription.eventType().equals(event.eventType())) {
                continue;
            }

            // Check correlation expression if present
            FeelExpression correlationExpr = subscription.correlationExpression();
            if (correlationExpr == null) {
                // No correlation expression - match by event type only
                return true;
            }

            // Build context for correlation expression evaluation
            Map<String, Object> context = buildCorrelationContext(event, instance);

            // Evaluate correlation expression
            var result = expressionEvaluator.evaluate(correlationExpr, context);
            if (result.success() && result.asBoolean()) {
                return true;
            }
        }

        return false;
    }

    private Map<String, Object> buildCorrelationContext(
            ProcessEvent event,
            ProcessInstance instance) {

        Map<String, Object> context = new HashMap<>();

        // Add event data
        context.put("event", Map.of(
            "type", event.eventType(),
            "id", event.eventId(),
            "correlationId", event.correlationId() != null ? event.correlationId() : "",
            "payload", event.payload()
        ));

        // Add event payload at top level for convenience
        context.putAll(event.payload());

        // Add instance data
        context.put("instance", Map.of(
            "id", instance.id().value(),
            "correlationId", instance.correlationId() != null ? instance.correlationId() : "",
            "processGraphId", instance.processGraphId().value()
        ));

        // Add execution context
        context.putAll(instance.context().toFeelContext());

        return context;
    }

    /**
     * Result of correlating an event to a process instance.
     *
     * @param instance the matched process instance
     * @param method how the correlation was established
     * @param matchingNodes nodes whose subscriptions matched
     */
    public record CorrelationMatch(
        ProcessInstance instance,
        CorrelationMethod method,
        List<Node> matchingNodes
    ) {
        public CorrelationMatch {
            Objects.requireNonNull(instance, "CorrelationMatch instance is required");
            Objects.requireNonNull(method, "CorrelationMatch method is required");
            matchingNodes = matchingNodes != null ? List.copyOf(matchingNodes) : List.of();
        }
    }

    /**
     * Method used to correlate event to instance.
     */
    public enum CorrelationMethod {
        /** Matched by explicit correlation ID. */
        CORRELATION_ID,
        /** Matched by evaluating correlation expression. */
        EXPRESSION,
        /** Matched by event type only (no correlation required). */
        EVENT_TYPE
    }
}
