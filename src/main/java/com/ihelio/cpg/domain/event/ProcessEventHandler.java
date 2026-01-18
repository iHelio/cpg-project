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

import com.ihelio.cpg.domain.engine.EdgeEvaluation;
import com.ihelio.cpg.domain.engine.EdgeEvaluator;
import com.ihelio.cpg.domain.engine.NodeEvaluation;
import com.ihelio.cpg.domain.engine.NodeEvaluator;
import com.ihelio.cpg.domain.engine.NodeExecutionResult;
import com.ihelio.cpg.domain.engine.ProcessExecutionEngine;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import com.ihelio.cpg.domain.repository.ProcessInstanceRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles incoming process events and triggers appropriate actions.
 *
 * <p>Event handling includes:
 * <ul>
 *   <li>Correlating events to process instances</li>
 *   <li>Adding events to instance context</li>
 *   <li>Triggering subscribed nodes</li>
 *   <li>Re-evaluating pending edges</li>
 * </ul>
 */
public class ProcessEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ProcessEventHandler.class);

    private final EventCorrelator eventCorrelator;
    private final ProcessInstanceRepository instanceRepository;
    private final ProcessGraphRepository graphRepository;
    private final NodeEvaluator nodeEvaluator;
    private final EdgeEvaluator edgeEvaluator;

    public ProcessEventHandler(
            EventCorrelator eventCorrelator,
            ProcessInstanceRepository instanceRepository,
            ProcessGraphRepository graphRepository,
            NodeEvaluator nodeEvaluator,
            EdgeEvaluator edgeEvaluator) {
        this.eventCorrelator = Objects.requireNonNull(eventCorrelator);
        this.instanceRepository = Objects.requireNonNull(instanceRepository);
        this.graphRepository = Objects.requireNonNull(graphRepository);
        this.nodeEvaluator = Objects.requireNonNull(nodeEvaluator);
        this.edgeEvaluator = Objects.requireNonNull(edgeEvaluator);
    }

    /**
     * Handles an incoming process event.
     *
     * @param event the incoming event
     * @return list of handling results
     */
    public List<EventHandlingResult> handle(ProcessEvent event) {
        log.info("Handling event: {} ({})", event.eventType(), event.eventId());

        List<EventHandlingResult> results = new ArrayList<>();

        // Find running process instances
        List<ProcessInstance> runningInstances = instanceRepository.findRunning();

        if (runningInstances.isEmpty()) {
            log.debug("No running instances to handle event: {}", event.eventType());
            return results;
        }

        // Group instances by process graph for efficient processing
        Map<ProcessGraph.ProcessGraphId, List<ProcessInstance>> instancesByGraph =
            groupByGraph(runningInstances);

        for (var entry : instancesByGraph.entrySet()) {
            ProcessGraph.ProcessGraphId graphId = entry.getKey();
            List<ProcessInstance> instances = entry.getValue();

            graphRepository.findById(graphId).ifPresent(graph -> {
                List<EventHandlingResult> graphResults =
                    handleEventForGraph(event, instances, graph);
                results.addAll(graphResults);
            });
        }

        return results;
    }

    private List<EventHandlingResult> handleEventForGraph(
            ProcessEvent event,
            List<ProcessInstance> instances,
            ProcessGraph graph) {

        List<EventHandlingResult> results = new ArrayList<>();

        // Correlate event to instances
        List<EventCorrelator.CorrelationMatch> matches =
            eventCorrelator.correlate(event, instances, graph);

        for (EventCorrelator.CorrelationMatch match : matches) {
            EventHandlingResult result = processMatch(event, match, graph);
            results.add(result);

            // Persist updated instance
            instanceRepository.save(match.instance());
        }

        return results;
    }

    private EventHandlingResult processMatch(
            ProcessEvent event,
            EventCorrelator.CorrelationMatch match,
            ProcessGraph graph) {

        ProcessInstance instance = match.instance();
        List<Node.NodeId> activatedNodes = new ArrayList<>();
        List<Edge.EdgeId> activatedEdges = new ArrayList<>();

        // 1. Add event to context
        ExecutionContext.ReceivedEvent receivedEvent = new ExecutionContext.ReceivedEvent(
            event.eventType(),
            event.eventId(),
            event.timestamp(),
            event.payload()
        );
        instance.updateContext(instance.context().withEvent(receivedEvent));

        // 2. Check subscribed nodes
        for (Node node : match.matchingNodes()) {
            // Only activate if not already executed and preconditions met
            if (!instance.hasExecutedNode(node.id()) &&
                !instance.activeNodeIds().contains(node.id())) {

                NodeEvaluation eval = nodeEvaluator.evaluate(node, instance.context());
                if (eval.available()) {
                    instance.startNodeExecution(node.id());
                    activatedNodes.add(node.id());
                    log.debug("Activated node {} due to event {}", node.id(), event.eventType());
                }
            }
        }

        // 3. Re-evaluate pending edges
        List<Edge> edgesToReevaluate = graph.getEdgesReevaluatedByEvent(event.eventType());
        for (Edge edge : edgesToReevaluate) {
            if (instance.pendingEdgeIds().contains(edge.id())) {
                // Check if edge is now traversable
                EdgeEvaluation eval = edgeEvaluator.evaluate(
                    edge,
                    instance.context(),
                    Map.of(),
                    Map.of()
                );

                if (eval.traversable()) {
                    instance.activatePendingEdge(edge.id());
                    activatedEdges.add(edge.id());
                    log.debug("Activated edge {} due to event {}", edge.id(), event.eventType());
                }
            }
        }

        // 4. Check edges activated by this event type
        List<Edge> edgesActivatedByEvent = graph.getEdgesActivatedByEvent(event.eventType());
        for (Edge edge : edgesActivatedByEvent) {
            if (!activatedEdges.contains(edge.id())) {
                // Add to pending or activate based on current state
                if (instance.hasExecutedNode(edge.sourceNodeId())) {
                    EdgeEvaluation eval = edgeEvaluator.evaluate(
                        edge,
                        instance.context(),
                        Map.of(),
                        Map.of()
                    );

                    if (eval.traversable()) {
                        activatedEdges.add(edge.id());
                        log.debug("Edge {} activated by event {}", edge.id(), event.eventType());
                    }
                }
            }
        }

        return new EventHandlingResult(
            instance.id(),
            event,
            true,
            activatedNodes,
            activatedEdges,
            null
        );
    }

    private Map<ProcessGraph.ProcessGraphId, List<ProcessInstance>> groupByGraph(
            List<ProcessInstance> instances) {

        Map<ProcessGraph.ProcessGraphId, List<ProcessInstance>> grouped = new java.util.HashMap<>();

        for (ProcessInstance instance : instances) {
            grouped.computeIfAbsent(instance.processGraphId(), k -> new ArrayList<>())
                .add(instance);
        }

        return grouped;
    }

    /**
     * Result of handling an event for a process instance.
     *
     * @param instanceId the affected process instance ID
     * @param event the handled event
     * @param success whether handling succeeded
     * @param activatedNodes nodes activated by the event
     * @param activatedEdges edges activated by the event
     * @param error error message if handling failed
     */
    public record EventHandlingResult(
        ProcessInstance.ProcessInstanceId instanceId,
        ProcessEvent event,
        boolean success,
        List<Node.NodeId> activatedNodes,
        List<Edge.EdgeId> activatedEdges,
        String error
    ) {
        public EventHandlingResult {
            Objects.requireNonNull(instanceId, "EventHandlingResult instanceId is required");
            Objects.requireNonNull(event, "EventHandlingResult event is required");
            activatedNodes = activatedNodes != null ? List.copyOf(activatedNodes) : List.of();
            activatedEdges = activatedEdges != null ? List.copyOf(activatedEdges) : List.of();
        }

        public static EventHandlingResult failure(
                ProcessInstance.ProcessInstanceId instanceId,
                ProcessEvent event,
                String error) {
            return new EventHandlingResult(
                instanceId,
                event,
                false,
                List.of(),
                List.of(),
                error
            );
        }

        public boolean hasActivations() {
            return !activatedNodes.isEmpty() || !activatedEdges.isEmpty();
        }
    }
}
