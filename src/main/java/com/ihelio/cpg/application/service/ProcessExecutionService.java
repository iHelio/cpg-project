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

import com.ihelio.cpg.application.orchestration.EligibilityEvaluator;
import com.ihelio.cpg.domain.engine.EdgeTraversal;
import com.ihelio.cpg.domain.engine.NodeEvaluation;
import com.ihelio.cpg.domain.engine.NodeEvaluator;
import com.ihelio.cpg.domain.engine.NodeExecutionResult;
import com.ihelio.cpg.domain.engine.ProcessExecutionEngine;
import com.ihelio.cpg.domain.exception.ProcessExecutionException;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import com.ihelio.cpg.domain.repository.ProcessInstanceRepository;
import com.ihelio.cpg.interfaces.rest.dto.request.ExecuteNodeRequest;
import com.ihelio.cpg.interfaces.rest.dto.request.UpdateContextRequest;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Application service for process execution operations.
 *
 * <p>This service handles node execution, context management, and
 * determining available nodes and transitions.
 */
@Service
public class ProcessExecutionService {

    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessGraphRepository processGraphRepository;
    private final ProcessExecutionEngine processExecutionEngine;
    private final NodeEvaluator nodeEvaluator;
    private final EligibilityEvaluator eligibilityEvaluator;

    public ProcessExecutionService(
            ProcessInstanceRepository processInstanceRepository,
            ProcessGraphRepository processGraphRepository,
            ProcessExecutionEngine processExecutionEngine,
            NodeEvaluator nodeEvaluator,
            EligibilityEvaluator eligibilityEvaluator) {
        this.processInstanceRepository = processInstanceRepository;
        this.processGraphRepository = processGraphRepository;
        this.processExecutionEngine = processExecutionEngine;
        this.nodeEvaluator = nodeEvaluator;
        this.eligibilityEvaluator = eligibilityEvaluator;
    }

    /**
     * Executes a node within a process instance.
     *
     * @param instanceId the instance ID
     * @param request the execution request
     * @return the execution result
     * @throws ProcessExecutionException if instance or node not found
     */
    public NodeExecutionResult executeNode(String instanceId, ExecuteNodeRequest request) {
        ProcessInstance instance = getInstanceOrThrow(instanceId);
        ProcessGraph graph = getGraphForInstance(instance);

        Node node = graph.findNode(new Node.NodeId(request.nodeId()))
            .orElseThrow(() -> ProcessExecutionException.withContext(
                "Node not found: " + request.nodeId(),
                instanceId,
                request.nodeId(),
                ProcessExecutionException.ErrorType.NODE_NOT_FOUND,
                false
            ));

        // Merge additional context if provided
        if (!request.additionalContext().isEmpty()) {
            ExecutionContext updatedContext = instance.context();
            for (var entry : request.additionalContext().entrySet()) {
                updatedContext = updatedContext.withState(entry.getKey(), entry.getValue());
            }
            instance.updateContext(updatedContext);
        }

        NodeExecutionResult result = processExecutionEngine.executeNode(instance, graph, node);
        processInstanceRepository.save(instance);

        return result;
    }

    /**
     * Gets all available (executable) nodes for a process instance.
     *
     * @param instanceId the instance ID
     * @return list of available nodes
     * @throws ProcessExecutionException if instance not found
     */
    public List<Node> getAvailableNodes(String instanceId) {
        ProcessInstance instance = getInstanceOrThrow(instanceId);
        ProcessGraph graph = getGraphForInstance(instance);

        List<Node> candidates = eligibilityEvaluator.getCandidateNodes(instance, graph);

        return candidates.stream()
            .filter(node -> {
                NodeEvaluation evaluation = nodeEvaluator.evaluate(node, instance.context());
                return evaluation.available()
                    && !instance.hasExecutedNode(node.id())
                    && !instance.activeNodeIds().contains(node.id());
            })
            .toList();
    }

    /**
     * Gets available transitions from a specific node.
     *
     * @param instanceId the instance ID
     * @param nodeId the source node ID
     * @return list of available edge traversals
     * @throws ProcessExecutionException if instance or node not found
     */
    public List<EdgeTraversal> getAvailableTransitions(String instanceId, String nodeId) {
        ProcessInstance instance = getInstanceOrThrow(instanceId);
        ProcessGraph graph = getGraphForInstance(instance);

        Node sourceNode = graph.findNode(new Node.NodeId(nodeId))
            .orElseThrow(() -> ProcessExecutionException.withContext(
                "Node not found: " + nodeId,
                instanceId,
                nodeId,
                ProcessExecutionException.ErrorType.NODE_NOT_FOUND,
                false
            ));

        return processExecutionEngine.evaluateAndTraverseEdges(instance, graph, sourceNode);
    }

    /**
     * Gets the execution context of a process instance.
     *
     * @param instanceId the instance ID
     * @return the execution context
     * @throws ProcessExecutionException if instance not found
     */
    public ExecutionContext getContext(String instanceId) {
        ProcessInstance instance = getInstanceOrThrow(instanceId);
        return instance.context();
    }

    /**
     * Updates the execution context of a process instance.
     *
     * @param instanceId the instance ID
     * @param request the update request
     * @return the updated context
     * @throws ProcessExecutionException if instance not found
     */
    public ExecutionContext updateContext(String instanceId, UpdateContextRequest request) {
        ProcessInstance instance = getInstanceOrThrow(instanceId);

        ExecutionContext context = instance.context();

        // Merge client context
        for (var entry : request.clientContext().entrySet()) {
            context = context.withClientContext(entry.getKey(), entry.getValue());
        }

        // Merge domain context
        for (var entry : request.domainContext().entrySet()) {
            context = context.withDomainContext(entry.getKey(), entry.getValue());
        }

        // Merge accumulated state
        for (var entry : request.accumulatedState().entrySet()) {
            context = context.withState(entry.getKey(), entry.getValue());
        }

        instance.updateContext(context);
        processInstanceRepository.save(instance);

        return context;
    }

    private ProcessInstance getInstanceOrThrow(String id) {
        return processInstanceRepository.findById(
                new ProcessInstance.ProcessInstanceId(id))
            .orElseThrow(() -> ProcessExecutionException.withContext(
                "Process instance not found: " + id,
                id,
                null,
                ProcessExecutionException.ErrorType.INSTANCE_NOT_FOUND,
                false
            ));
    }

    private ProcessGraph getGraphForInstance(ProcessInstance instance) {
        return processGraphRepository.findByIdAndVersion(
                instance.processGraphId(),
                instance.processGraphVersion())
            .orElseThrow(() -> new ProcessExecutionException(
                "Process graph not found",
                ProcessExecutionException.ErrorType.GRAPH_NOT_FOUND
            ));
    }
}
