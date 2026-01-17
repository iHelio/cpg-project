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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A ProcessGraph is a template that defines all possible nodes and edges
 * for a business process.
 *
 * <p>The graph is reusable across all process instances. It defines:
 * <ul>
 *   <li>All decision points (nodes) in the process</li>
 *   <li>All permissible transitions (edges) between nodes</li>
 *   <li>Entry points (trigger nodes) that start the process</li>
 *   <li>Terminal nodes that complete the process</li>
 * </ul>
 *
 * <p>ProcessGraph is immutable and versioned for governance.
 */
public record ProcessGraph(
    ProcessGraphId id,
    String name,
    String description,
    int version,
    ProcessGraphStatus status,
    List<Node> nodes,
    List<Edge> edges,
    List<Node.NodeId> entryNodeIds,
    List<Node.NodeId> terminalNodeIds,
    Metadata metadata
) {

    private static final Map<Node.NodeId, Node> EMPTY_NODE_MAP = Map.of();
    private static final Map<Edge.EdgeId, Edge> EMPTY_EDGE_MAP = Map.of();

    public ProcessGraph {
        Objects.requireNonNull(id, "ProcessGraph id is required");
        Objects.requireNonNull(name, "ProcessGraph name is required");
        Objects.requireNonNull(status, "ProcessGraph status is required");
        nodes = nodes != null ? List.copyOf(nodes) : List.of();
        edges = edges != null ? List.copyOf(edges) : List.of();
        entryNodeIds = entryNodeIds != null ? List.copyOf(entryNodeIds) : List.of();
        terminalNodeIds = terminalNodeIds != null ? List.copyOf(terminalNodeIds) : List.of();
        metadata = metadata != null ? metadata : Metadata.empty();
    }

    /**
     * Unique identifier for a process graph.
     */
    public record ProcessGraphId(String value) {
        public ProcessGraphId {
            Objects.requireNonNull(value, "ProcessGraphId value is required");
            if (value.isBlank()) {
                throw new IllegalArgumentException("ProcessGraphId cannot be blank");
            }
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * Status of the process graph.
     */
    public enum ProcessGraphStatus {
        DRAFT,
        PUBLISHED,
        DEPRECATED,
        ARCHIVED
    }

    /**
     * Metadata about the process graph.
     */
    public record Metadata(
        String createdBy,
        Instant createdAt,
        String lastModifiedBy,
        Instant lastModifiedAt,
        Map<String, String> tags
    ) {
        public Metadata {
            tags = tags != null ? Map.copyOf(tags) : Map.of();
        }

        public static Metadata empty() {
            return new Metadata(null, null, null, null, Map.of());
        }
    }

    /**
     * Returns a node by its ID.
     *
     * @param nodeId the node ID
     * @return the node, or empty if not found
     */
    public Optional<Node> findNode(Node.NodeId nodeId) {
        return nodes.stream()
            .filter(n -> n.id().equals(nodeId))
            .findFirst();
    }

    /**
     * Returns an edge by its ID.
     *
     * @param edgeId the edge ID
     * @return the edge, or empty if not found
     */
    public Optional<Edge> findEdge(Edge.EdgeId edgeId) {
        return edges.stream()
            .filter(e -> e.id().equals(edgeId))
            .findFirst();
    }

    /**
     * Returns all outbound edges from a given node.
     *
     * @param nodeId the source node ID
     * @return list of edges leaving this node
     */
    public List<Edge> getOutboundEdges(Node.NodeId nodeId) {
        return edges.stream()
            .filter(e -> e.sourceNodeId().equals(nodeId))
            .toList();
    }

    /**
     * Returns all inbound edges to a given node.
     *
     * @param nodeId the target node ID
     * @return list of edges entering this node
     */
    public List<Edge> getInboundEdges(Node.NodeId nodeId) {
        return edges.stream()
            .filter(e -> e.targetNodeId().equals(nodeId))
            .toList();
    }

    /**
     * Returns all edges that subscribe to a given event type.
     *
     * @param eventType the event type
     * @return list of edges with this event as an activating trigger
     */
    public List<Edge> getEdgesActivatedByEvent(String eventType) {
        return edges.stream()
            .filter(e -> e.eventTriggers().activatingEvents().contains(eventType))
            .toList();
    }

    /**
     * Returns all edges that should be re-evaluated when a given event occurs.
     *
     * @param eventType the event type
     * @return list of edges with this event as a re-evaluation trigger
     */
    public List<Edge> getEdgesReevaluatedByEvent(String eventType) {
        return edges.stream()
            .filter(e -> e.eventTriggers().reevaluationEvents().contains(eventType))
            .toList();
    }

    /**
     * Returns all nodes that subscribe to a given event type.
     *
     * @param eventType the event type
     * @return list of nodes that subscribe to this event
     */
    public List<Node> getNodesSubscribedToEvent(String eventType) {
        return nodes.stream()
            .filter(n -> n.eventConfig() != null)
            .filter(n -> n.eventConfig().subscribes().stream()
                .anyMatch(s -> s.eventType().equals(eventType)))
            .toList();
    }

    /**
     * Returns a map of node IDs to nodes for efficient lookup.
     *
     * @return map of node ID to node
     */
    public Map<Node.NodeId, Node> nodeMap() {
        return nodes.stream()
            .collect(Collectors.toMap(Node::id, Function.identity()));
    }

    /**
     * Returns a map of edge IDs to edges for efficient lookup.
     *
     * @return map of edge ID to edge
     */
    public Map<Edge.EdgeId, Edge> edgeMap() {
        return edges.stream()
            .collect(Collectors.toMap(Edge::id, Function.identity()));
    }

    /**
     * Validates the graph structure.
     *
     * @return list of validation errors, empty if valid
     */
    public List<String> validate() {
        var errors = new java.util.ArrayList<String>();
        var nodeIds = nodes.stream().map(Node::id).collect(Collectors.toSet());

        // Check entry nodes exist
        for (var entryId : entryNodeIds) {
            if (!nodeIds.contains(entryId)) {
                errors.add("Entry node not found: " + entryId);
            }
        }

        // Check terminal nodes exist
        for (var terminalId : terminalNodeIds) {
            if (!nodeIds.contains(terminalId)) {
                errors.add("Terminal node not found: " + terminalId);
            }
        }

        // Check edge references
        for (var edge : edges) {
            if (!nodeIds.contains(edge.sourceNodeId())) {
                errors.add("Edge " + edge.id() + " references unknown source node: "
                    + edge.sourceNodeId());
            }
            if (!nodeIds.contains(edge.targetNodeId())) {
                errors.add("Edge " + edge.id() + " references unknown target node: "
                    + edge.targetNodeId());
            }
        }

        return errors;
    }
}
