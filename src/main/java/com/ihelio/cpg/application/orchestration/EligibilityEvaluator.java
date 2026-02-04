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

package com.ihelio.cpg.application.orchestration;

import com.ihelio.cpg.domain.engine.EdgeEvaluation;
import com.ihelio.cpg.domain.engine.EdgeEvaluator;
import com.ihelio.cpg.domain.engine.NodeEvaluation;
import com.ihelio.cpg.domain.engine.NodeEvaluator;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.orchestration.EligibleSpace;
import com.ihelio.cpg.domain.orchestration.RuntimeContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * EligibilityEvaluator evaluates nodes and edges to build the EligibleSpace.
 *
 * <p>It determines:
 * <ul>
 *   <li>Which nodes are eligible (preconditions, policies, rules all pass)</li>
 *   <li>Which edges are traversable (guard conditions satisfied)</li>
 *   <li>The cross-product of eligible transitions with priorities</li>
 * </ul>
 *
 * <p>The evaluation uses the existing NodeEvaluator and EdgeEvaluator components,
 * wrapping them in the orchestration context.
 */
public class EligibilityEvaluator {

    private final NodeEvaluator nodeEvaluator;
    private final EdgeEvaluator edgeEvaluator;

    /**
     * Creates an EligibilityEvaluator with the required evaluators.
     *
     * @param nodeEvaluator the node evaluator
     * @param edgeEvaluator the edge evaluator
     */
    public EligibilityEvaluator(NodeEvaluator nodeEvaluator, EdgeEvaluator edgeEvaluator) {
        this.nodeEvaluator = Objects.requireNonNull(nodeEvaluator, "nodeEvaluator is required");
        this.edgeEvaluator = Objects.requireNonNull(edgeEvaluator, "edgeEvaluator is required");
    }

    /**
     * Evaluates the eligible space for a process instance.
     *
     * @param instance the process instance
     * @param graph the process graph
     * @param context the runtime context
     * @return the eligible space
     */
    public EligibleSpace evaluate(ProcessInstance instance, ProcessGraph graph,
            RuntimeContext context) {
        Objects.requireNonNull(instance, "instance is required");
        Objects.requireNonNull(graph, "graph is required");
        Objects.requireNonNull(context, "context is required");

        // Get candidate nodes (entry nodes or nodes reachable from active nodes)
        List<Node> candidateNodes = getCandidateNodes(instance, graph);

        // Evaluate all candidate nodes
        List<NodeEvaluation> allNodeEvaluations = evaluateAllNodes(candidateNodes, context);
        List<NodeEvaluation> eligibleNodes = allNodeEvaluations.stream()
            .filter(NodeEvaluation::available)
            .toList();

        // Get candidate edges (outbound from active nodes or entry edges)
        List<Edge> candidateEdges = getCandidateEdges(instance, graph);

        // Build rule outputs and policy outcomes from eligible nodes
        Map<Node.NodeId, Map<String, Object>> nodeRuleOutputs = collectRuleOutputs(eligibleNodes);
        Map<Node.NodeId, Map<String, Edge.PolicyOutcome>> nodePolicyOutcomes =
            collectPolicyOutcomes(eligibleNodes);

        // Evaluate all candidate edges
        List<EdgeEvaluation> allEdgeEvaluations = evaluateAllEdges(
            candidateEdges, context, nodeRuleOutputs, nodePolicyOutcomes, instance, graph);
        List<EdgeEvaluation> traversableEdges = allEdgeEvaluations.stream()
            .filter(EdgeEvaluation::traversable)
            .toList();

        // Build candidate actions from eligible space
        List<EligibleSpace.CandidateAction> candidateActions = buildCandidateActions(
            eligibleNodes, traversableEdges, instance, graph);

        return EligibleSpace.builder()
            .eligibleNodes(eligibleNodes)
            .traversableEdges(traversableEdges)
            .candidateActions(candidateActions)
            .build();
    }

    /**
     * Evaluates entry nodes for a new process instance.
     *
     * @param graph the process graph
     * @param context the runtime context
     * @return the eligible space with entry nodes
     */
    public EligibleSpace evaluateEntryNodes(ProcessGraph graph, RuntimeContext context) {
        Objects.requireNonNull(graph, "graph is required");
        Objects.requireNonNull(context, "context is required");

        // Get entry nodes
        List<Node> entryNodes = graph.entryNodeIds().stream()
            .map(graph::findNode)
            .flatMap(java.util.Optional::stream)
            .toList();

        // Evaluate entry nodes
        List<NodeEvaluation> nodeEvaluations = evaluateAllNodes(entryNodes, context);
        List<NodeEvaluation> eligibleNodes = nodeEvaluations.stream()
            .filter(NodeEvaluation::available)
            .toList();

        // Entry nodes have no inbound edges
        List<EligibleSpace.CandidateAction> candidateActions = eligibleNodes.stream()
            .map(ne -> EligibleSpace.CandidateAction.forEntryNode(ne, 100))
            .toList();

        return EligibleSpace.builder()
            .eligibleNodes(eligibleNodes)
            .traversableEdges(List.of())
            .candidateActions(candidateActions)
            .build();
    }

    /**
     * Re-evaluates the eligible space after an event.
     *
     * @param instance the process instance
     * @param graph the process graph
     * @param context the updated runtime context
     * @param eventType the event type that triggered reevaluation
     * @return the updated eligible space
     */
    public EligibleSpace reevaluateAfterEvent(ProcessInstance instance, ProcessGraph graph,
            RuntimeContext context, String eventType) {
        Objects.requireNonNull(eventType, "eventType is required");

        // Get nodes subscribed to this event
        List<Node> eventSubscribers = graph.getNodesSubscribedToEvent(eventType);

        // Get edges activated by this event
        List<Edge> activatedEdges = graph.getEdgesActivatedByEvent(eventType);

        // Combine with regular candidate nodes
        Set<Node> candidateNodeSet = new java.util.HashSet<>(getCandidateNodes(instance, graph));
        candidateNodeSet.addAll(eventSubscribers);
        List<Node> candidateNodes = new ArrayList<>(candidateNodeSet);

        // Combine with regular candidate edges
        Set<Edge> candidateEdgeSet = new java.util.HashSet<>(getCandidateEdges(instance, graph));
        candidateEdgeSet.addAll(activatedEdges);
        List<Edge> candidateEdges = new ArrayList<>(candidateEdgeSet);

        // Evaluate all candidates
        List<NodeEvaluation> allNodeEvaluations = evaluateAllNodes(candidateNodes, context);
        List<NodeEvaluation> eligibleNodes = allNodeEvaluations.stream()
            .filter(NodeEvaluation::available)
            .toList();

        Map<Node.NodeId, Map<String, Object>> nodeRuleOutputs = collectRuleOutputs(eligibleNodes);
        Map<Node.NodeId, Map<String, Edge.PolicyOutcome>> nodePolicyOutcomes =
            collectPolicyOutcomes(eligibleNodes);

        List<EdgeEvaluation> allEdgeEvaluations = evaluateAllEdges(
            candidateEdges, context, nodeRuleOutputs, nodePolicyOutcomes, instance, graph);
        List<EdgeEvaluation> traversableEdges = allEdgeEvaluations.stream()
            .filter(EdgeEvaluation::traversable)
            .toList();

        List<EligibleSpace.CandidateAction> candidateActions = buildCandidateActions(
            eligibleNodes, traversableEdges, instance, graph);

        return EligibleSpace.builder()
            .eligibleNodes(eligibleNodes)
            .traversableEdges(traversableEdges)
            .candidateActions(candidateActions)
            .build();
    }

    public List<Node> getCandidateNodes(ProcessInstance instance, ProcessGraph graph) {
        List<Node> candidates = new ArrayList<>();

        // If no active nodes, consider entry nodes
        if (instance.activeNodeIds().isEmpty()) {
            graph.entryNodeIds().stream()
                .map(graph::findNode)
                .flatMap(java.util.Optional::stream)
                .forEach(candidates::add);
        }

        // Add nodes reachable from active nodes via outbound edges
        for (Node.NodeId activeNodeId : instance.activeNodeIds()) {
            List<Edge> outboundEdges = graph.getOutboundEdges(activeNodeId);
            for (Edge edge : outboundEdges) {
                graph.findNode(edge.targetNodeId()).ifPresent(candidates::add);
            }
        }

        // Add nodes that have been completed and have outbound edges
        for (ProcessInstance.NodeExecution execution : instance.nodeExecutions()) {
            if (execution.status() == ProcessInstance.NodeExecutionStatus.COMPLETED) {
                List<Edge> outboundEdges = graph.getOutboundEdges(execution.nodeId());
                for (Edge edge : outboundEdges) {
                    // Only if target not already executed
                    if (!instance.hasExecutedNode(edge.targetNodeId())) {
                        graph.findNode(edge.targetNodeId()).ifPresent(candidates::add);
                    }
                }
            }
        }

        return candidates.stream().distinct().toList();
    }

    private List<Edge> getCandidateEdges(ProcessInstance instance, ProcessGraph graph) {
        List<Edge> candidates = new ArrayList<>();

        // Add outbound edges from completed nodes
        for (ProcessInstance.NodeExecution execution : instance.nodeExecutions()) {
            if (execution.status() == ProcessInstance.NodeExecutionStatus.COMPLETED) {
                candidates.addAll(graph.getOutboundEdges(execution.nodeId()));
            }
        }

        // Add pending edges
        for (Edge.EdgeId pendingEdgeId : instance.pendingEdgeIds()) {
            graph.findEdge(pendingEdgeId).ifPresent(candidates::add);
        }

        return candidates.stream().distinct().toList();
    }

    private List<NodeEvaluation> evaluateAllNodes(List<Node> nodes, RuntimeContext context) {
        var executionContext = context.toExecutionContext();
        List<NodeEvaluation> evaluations = new ArrayList<>();

        for (Node node : nodes) {
            NodeEvaluation evaluation = nodeEvaluator.evaluate(node, executionContext);
            evaluations.add(evaluation);
        }

        return evaluations;
    }

    private List<EdgeEvaluation> evaluateAllEdges(
            List<Edge> edges,
            RuntimeContext context,
            Map<Node.NodeId, Map<String, Object>> nodeRuleOutputs,
            Map<Node.NodeId, Map<String, Edge.PolicyOutcome>> nodePolicyOutcomes,
            ProcessInstance instance,
            ProcessGraph graph) {

        var executionContext = context.toExecutionContext();
        List<EdgeEvaluation> evaluations = new ArrayList<>();

        for (Edge edge : edges) {
            // Get rule outputs and policy outcomes for the source node
            Map<String, Object> ruleOutputs = nodeRuleOutputs.getOrDefault(
                edge.sourceNodeId(), Map.of());
            Map<String, Edge.PolicyOutcome> policyOutcomes = nodePolicyOutcomes.getOrDefault(
                edge.sourceNodeId(), Map.of());

            EdgeEvaluation evaluation = edgeEvaluator.evaluate(
                edge, executionContext, ruleOutputs, policyOutcomes);
            evaluations.add(evaluation);
        }

        return evaluations;
    }

    private Map<Node.NodeId, Map<String, Object>> collectRuleOutputs(
            List<NodeEvaluation> eligibleNodes) {
        Map<Node.NodeId, Map<String, Object>> result = new HashMap<>();
        for (NodeEvaluation eval : eligibleNodes) {
            result.put(eval.node().id(), eval.ruleOutputs());
        }
        return result;
    }

    private Map<Node.NodeId, Map<String, Edge.PolicyOutcome>> collectPolicyOutcomes(
            List<NodeEvaluation> eligibleNodes) {
        Map<Node.NodeId, Map<String, Edge.PolicyOutcome>> result = new HashMap<>();

        for (NodeEvaluation eval : eligibleNodes) {
            Map<String, Edge.PolicyOutcome> outcomes = new HashMap<>();
            for (var policyResult : eval.policyResults()) {
                Edge.PolicyOutcome outcome = policyResult.passed()
                    ? Edge.PolicyOutcome.PASSED
                    : Edge.PolicyOutcome.FAILED;
                outcomes.put(policyResult.policyGate().id(), outcome);
            }
            result.put(eval.node().id(), outcomes);
        }

        return result;
    }

    private List<EligibleSpace.CandidateAction> buildCandidateActions(
            List<NodeEvaluation> eligibleNodes,
            List<EdgeEvaluation> traversableEdges,
            ProcessInstance instance,
            ProcessGraph graph) {

        List<EligibleSpace.CandidateAction> actions = new ArrayList<>();

        // Map eligible nodes by ID for lookup (use first evaluation if duplicates)
        Map<Node.NodeId, NodeEvaluation> nodeEvalMap = eligibleNodes.stream()
            .collect(Collectors.toMap(ne -> ne.node().id(), ne -> ne, (first, second) -> first));

        // Create actions for traversable edges
        for (EdgeEvaluation edgeEval : traversableEdges) {
            Node.NodeId targetNodeId = edgeEval.edge().targetNodeId();
            NodeEvaluation targetNodeEval = nodeEvalMap.get(targetNodeId);

            if (targetNodeEval != null) {
                // Target node is also eligible - create action
                actions.add(EligibleSpace.CandidateAction.forEdge(targetNodeEval, edgeEval));
            }
        }

        // If instance has no active nodes and no completed nodes, add entry nodes
        if (instance.activeNodeIds().isEmpty() && instance.nodeExecutions().isEmpty()) {
            for (NodeEvaluation nodeEval : eligibleNodes) {
                if (graph.entryNodeIds().contains(nodeEval.node().id())) {
                    actions.add(EligibleSpace.CandidateAction.forEntryNode(nodeEval, 100));
                }
            }
        }

        return actions;
    }
}
