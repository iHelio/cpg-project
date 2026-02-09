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

import com.ihelio.cpg.domain.action.ActionContext;
import com.ihelio.cpg.domain.action.ActionHandler;
import com.ihelio.cpg.domain.action.ActionResult;
import com.ihelio.cpg.domain.event.ProcessEvent;
import com.ihelio.cpg.domain.event.ProcessEventPublisher;
import com.ihelio.cpg.domain.exception.ProcessExecutionException;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.policy.PolicyResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Main process execution engine that orchestrates all components.
 *
 * <p>The engine coordinates:
 * <ul>
 *   <li>Process lifecycle (start, suspend, resume, complete)</li>
 *   <li>Node evaluation and execution</li>
 *   <li>Edge evaluation and traversal</li>
 *   <li>Parallel execution coordination</li>
 *   <li>Compensation and error handling</li>
 *   <li>Event publishing</li>
 * </ul>
 */
public class ProcessExecutionEngine {

    private final NodeEvaluator nodeEvaluator;
    private final EdgeEvaluator edgeEvaluator;
    private final ExecutionCoordinator executionCoordinator;
    private final CompensationHandler compensationHandler;
    private final ProcessEventPublisher eventPublisher;
    private final BiFunction<Node.ActionType, String, ActionHandler> actionHandlerResolver;

    public ProcessExecutionEngine(
            NodeEvaluator nodeEvaluator,
            EdgeEvaluator edgeEvaluator,
            ExecutionCoordinator executionCoordinator,
            CompensationHandler compensationHandler,
            ProcessEventPublisher eventPublisher,
            BiFunction<Node.ActionType, String, ActionHandler> actionHandlerResolver) {
        this.nodeEvaluator = Objects.requireNonNull(nodeEvaluator);
        this.edgeEvaluator = Objects.requireNonNull(edgeEvaluator);
        this.executionCoordinator = Objects.requireNonNull(executionCoordinator);
        this.compensationHandler = Objects.requireNonNull(compensationHandler);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.actionHandlerResolver = Objects.requireNonNull(actionHandlerResolver);
    }

    /**
     * Starts a new process instance.
     *
     * @param graph the process graph template
     * @param initialContext the initial execution context
     * @return the new process instance
     */
    public ProcessInstance startProcess(ProcessGraph graph, ExecutionContext initialContext) {
        Objects.requireNonNull(graph, "ProcessGraph is required");
        Objects.requireNonNull(initialContext, "Initial context is required");

        // Create new process instance
        ProcessInstance instance = ProcessInstance.builder()
            .id(UUID.randomUUID().toString())
            .processGraphId(graph.id())
            .processGraphVersion(graph.version())
            .context(initialContext)
            .build();

        // Publish process started event
        eventPublisher.publish(ProcessEvent.processStarted(
            instance.id().value(),
            graph.id().value()
        ));

        // Execute entry nodes
        for (Node.NodeId entryNodeId : graph.entryNodeIds()) {
            graph.findNode(entryNodeId).ifPresent(entryNode -> {
                instance.startNodeExecution(entryNodeId);
            });
        }

        return instance;
    }

    /**
     * Suspends a running process instance.
     *
     * @param instance the process instance to suspend
     */
    public void suspendProcess(ProcessInstance instance) {
        Objects.requireNonNull(instance, "ProcessInstance is required");

        if (instance.isRunning()) {
            instance.suspend();
            eventPublisher.publish(ProcessEvent.of(
                "process.suspended",
                ProcessEvent.EventSource.system(),
                instance.id().value(),
                Map.of()
            ));
        }
    }

    /**
     * Resumes a suspended process instance.
     *
     * @param instance the process instance to resume
     * @param graph the process graph
     */
    public void resumeProcess(ProcessInstance instance, ProcessGraph graph) {
        Objects.requireNonNull(instance, "ProcessInstance is required");
        Objects.requireNonNull(graph, "ProcessGraph is required");

        if (instance.status() == ProcessInstance.ProcessInstanceStatus.SUSPENDED) {
            instance.resume();
            eventPublisher.publish(ProcessEvent.of(
                "process.resumed",
                ProcessEvent.EventSource.system(),
                instance.id().value(),
                Map.of()
            ));
        }
    }

    /**
     * Executes a node within a process instance.
     *
     * @param instance the process instance
     * @param graph the process graph
     * @param node the node to execute
     * @return the execution result
     */
    public NodeExecutionResult executeNode(
            ProcessInstance instance,
            ProcessGraph graph,
            Node node) {

        Objects.requireNonNull(instance, "ProcessInstance is required");
        Objects.requireNonNull(graph, "ProcessGraph is required");
        Objects.requireNonNull(node, "Node is required");

        if (!instance.isRunning()) {
            throw new ProcessExecutionException(
                "Cannot execute node on non-running process",
                ProcessExecutionException.ErrorType.INVALID_STATE
            );
        }

        // 1. Evaluate node availability
        NodeEvaluation evaluation = nodeEvaluator.evaluate(node, instance.context());

        if (!evaluation.available()) {
            return NodeExecutionResult.skipped(node, evaluation.blockedReason());
        }

        // 2. Mark node as executing
        instance.startNodeExecution(node.id());

        try {
            // 3. Execute the action
            ActionHandler handler = actionHandlerResolver.apply(
                node.action().type(), node.action().handlerRef());
            if (handler == null) {
                throw new ProcessExecutionException(
                    "No handler for action type: " + node.action().type(),
                    ProcessExecutionException.ErrorType.ACTION_FAILED
                );
            }

            ActionContext actionContext = new ActionContext(
                node,
                instance,
                instance.context(),
                evaluation.ruleOutputs(),
                extractPolicyOutputs(evaluation.policyResults())
            );

            ActionResult actionResult;
            if (node.action().config().asynchronous() && handler.supportsAsync()) {
                // Async execution - return pending
                handler.executeAsync(actionContext);
                return NodeExecutionResult.pending(node, ActionResult.pending());
            } else {
                actionResult = handler.execute(actionContext);
            }

            // 4. Handle action result
            if (actionResult.isFailed()) {
                return handleActionFailure(instance, graph, node, actionResult);
            }

            if (actionResult.isPending()) {
                return NodeExecutionResult.pending(node, actionResult);
            }

            // 5. Update context with action output
            ExecutionContext updatedContext = instance.context();
            if (!actionResult.output().isEmpty()) {
                for (Map.Entry<String, Object> entry : actionResult.output().entrySet()) {
                    updatedContext = updatedContext.withState(entry.getKey(), entry.getValue());
                }
                instance.updateContext(updatedContext);
            }

            // 6. Complete node execution
            instance.completeNodeExecution(node.id(), actionResult.output());
            compensationHandler.resetRetryCount(instance, node);

            // 7. Publish node executed event
            publishNodeEvents(node, instance, actionResult);

            // 8. Check if process is complete
            if (graph.terminalNodeIds().contains(node.id()) &&
                instance.activeNodeIds().isEmpty()) {
                completeProcess(instance);
            }

            return NodeExecutionResult.success(node, actionResult, updatedContext);

        } catch (Exception e) {
            return handleExecutionException(instance, graph, node, e);
        }
    }

    /**
     * Evaluates outbound edges from a node and returns traversable edges.
     *
     * @param instance the process instance
     * @param graph the process graph
     * @param sourceNode the source node
     * @return list of edge traversals
     */
    public List<EdgeTraversal> evaluateAndTraverseEdges(
            ProcessInstance instance,
            ProcessGraph graph,
            Node sourceNode) {

        Objects.requireNonNull(instance, "ProcessInstance is required");
        Objects.requireNonNull(graph, "ProcessGraph is required");
        Objects.requireNonNull(sourceNode, "Source node is required");

        // Get outbound edges
        List<Edge> outboundEdges = graph.getOutboundEdges(sourceNode.id());
        if (outboundEdges.isEmpty()) {
            return List.of();
        }

        // Get node evaluation for rule and policy outputs
        NodeEvaluation nodeEval = nodeEvaluator.evaluate(sourceNode, instance.context());
        Map<String, Edge.PolicyOutcome> policyOutcomes =
            extractPolicyOutcomes(nodeEval.policyResults());

        // Evaluate edges
        List<EdgeEvaluation> traversableEvals = edgeEvaluator.evaluateTraversable(
            outboundEdges,
            instance.context(),
            nodeEval.ruleOutputs(),
            policyOutcomes
        );

        // Select edges to traverse
        List<Edge> edgesToTraverse = edgeEvaluator.selectEdgesToTraverse(traversableEvals);

        // Build traversal results
        List<EdgeTraversal> traversals = new ArrayList<>();
        for (Edge edge : edgesToTraverse) {
            graph.findNode(edge.targetNodeId()).ifPresent(targetNode -> {
                traversals.add(EdgeTraversal.of(edge, sourceNode, targetNode));
            });
        }

        return traversals;
    }

    /**
     * Handles an incoming event and processes affected instances.
     *
     * @param event the process event
     * @param instances list of potentially affected process instances
     * @param graph the process graph
     * @return list of affected process instances
     */
    public List<ProcessInstance> handleEvent(
            ProcessEvent event,
            List<ProcessInstance> instances,
            ProcessGraph graph) {

        Objects.requireNonNull(event, "Event is required");
        Objects.requireNonNull(graph, "ProcessGraph is required");

        List<ProcessInstance> affected = new ArrayList<>();

        // Find instances that correlate with this event
        for (ProcessInstance instance : instances) {
            if (!instance.isRunning()) {
                continue;
            }

            // Check correlation
            if (event.correlationId() != null &&
                !event.correlationId().equals(instance.correlationId()) &&
                !event.correlationId().equals(instance.id().value())) {
                continue;
            }

            // Add event to context
            ExecutionContext.ReceivedEvent receivedEvent = new ExecutionContext.ReceivedEvent(
                event.eventType(),
                event.eventId(),
                event.timestamp(),
                event.payload()
            );
            instance.updateContext(instance.context().withEvent(receivedEvent));

            // Find nodes subscribed to this event
            List<Node> subscribedNodes = graph.getNodesSubscribedToEvent(event.eventType());
            for (Node node : subscribedNodes) {
                // Check if node preconditions are now met
                NodeEvaluation eval = nodeEvaluator.evaluate(node, instance.context());
                if (eval.available() && !instance.hasExecutedNode(node.id())) {
                    instance.startNodeExecution(node.id());
                }
            }

            // Re-evaluate pending edges
            List<Edge> edgesToReevaluate = graph.getEdgesReevaluatedByEvent(event.eventType());
            for (Edge edge : edgesToReevaluate) {
                if (instance.pendingEdgeIds().contains(edge.id())) {
                    // Edge might now be traversable
                    instance.activatePendingEdge(edge.id());
                }
            }

            affected.add(instance);
        }

        return affected;
    }

    private NodeExecutionResult handleActionFailure(
            ProcessInstance instance,
            ProcessGraph graph,
            Node node,
            ActionResult actionResult) {

        instance.failNodeExecution(node.id(), actionResult.error());

        CompensationAction compensation = compensationHandler.determineCompensation(
            instance,
            graph,
            node,
            "ACTION_FAILED",
            actionResult.error()
        );

        if (compensation.isRetry()) {
            // Will be retried - keep in running state
            instance.startNodeExecution(node.id());
        }

        return NodeExecutionResult.failed(node, actionResult.error(), compensation);
    }

    private NodeExecutionResult handleExecutionException(
            ProcessInstance instance,
            ProcessGraph graph,
            Node node,
            Exception e) {

        String errorType = e.getClass().getSimpleName();
        String errorMessage = e.getMessage();

        instance.failNodeExecution(node.id(), errorMessage);

        CompensationAction compensation = compensationHandler.determineCompensation(
            instance,
            graph,
            node,
            errorType,
            errorMessage
        );

        if (!compensation.allowsContinuation()) {
            instance.fail();
        }

        return NodeExecutionResult.failed(node, errorMessage, compensation);
    }

    private void completeProcess(ProcessInstance instance) {
        instance.complete();

        eventPublisher.publish(ProcessEvent.processCompleted(instance.id().value()));

        // Cleanup
        executionCoordinator.cleanupInstance(instance);
        compensationHandler.cleanupInstance(instance);
    }

    private void publishNodeEvents(
            Node node,
            ProcessInstance instance,
            ActionResult result) {

        // Publish standard node executed event
        eventPublisher.publish(ProcessEvent.nodeExecuted(
            node.id().value(),
            instance.id().value(),
            result.output()
        ));

        // Publish configured emissions
        if (node.eventConfig() != null) {
            for (Node.EventEmission emission : node.eventConfig().emits()) {
                if (emission.timing() == Node.EmissionTiming.ON_COMPLETE) {
                    eventPublisher.publish(ProcessEvent.of(
                        emission.eventType(),
                        ProcessEvent.EventSource.node(node.id().value()),
                        instance.id().value(),
                        result.output()
                    ));
                }
            }
        }
    }

    private Map<String, Object> extractPolicyOutputs(List<PolicyResult> policyResults) {
        Map<String, Object> outputs = new HashMap<>();
        for (PolicyResult result : policyResults) {
            outputs.put(result.policyGate().id(), result.details());
        }
        return outputs;
    }

    private Map<String, Edge.PolicyOutcome> extractPolicyOutcomes(List<PolicyResult> policyResults) {
        Map<String, Edge.PolicyOutcome> outcomes = new HashMap<>();
        for (PolicyResult result : policyResults) {
            outcomes.put(result.policyGate().id(), result.outcome());
        }
        return outcomes;
    }
}
