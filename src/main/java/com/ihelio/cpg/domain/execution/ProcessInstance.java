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

package com.ihelio.cpg.domain.execution;

import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A ProcessInstance represents a running execution of a ProcessGraph.
 *
 * <p>Each instance maintains:
 * <ul>
 *   <li>Reference to the ProcessGraph template</li>
 *   <li>Current execution context</li>
 *   <li>Active nodes (currently executing)</li>
 *   <li>Completed nodes history</li>
 *   <li>Pending edges awaiting activation</li>
 * </ul>
 *
 * <p>ProcessInstance is the aggregate root for process execution.
 */
public class ProcessInstance {

    private final ProcessInstanceId id;
    private final ProcessGraph.ProcessGraphId processGraphId;
    private final int processGraphVersion;
    private final String correlationId;
    private final Instant startedAt;
    private Instant completedAt;
    private ProcessInstanceStatus status;
    private ExecutionContext context;
    private final List<NodeExecution> nodeExecutions;
    private final Set<Node.NodeId> activeNodeIds;
    private final Set<Edge.EdgeId> pendingEdgeIds;

    private ProcessInstance(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "ProcessInstance id is required");
        this.processGraphId = Objects.requireNonNull(builder.processGraphId,
            "ProcessInstance processGraphId is required");
        this.processGraphVersion = builder.processGraphVersion;
        this.correlationId = builder.correlationId;
        this.startedAt = builder.startedAt != null ? builder.startedAt : Instant.now();
        this.completedAt = builder.completedAt;
        this.status = builder.status != null ? builder.status : ProcessInstanceStatus.RUNNING;
        this.context = Objects.requireNonNull(builder.context,
            "ProcessInstance context is required");
        this.nodeExecutions = new java.util.ArrayList<>(builder.nodeExecutions);
        this.activeNodeIds = new java.util.HashSet<>(builder.activeNodeIds);
        this.pendingEdgeIds = new java.util.HashSet<>(builder.pendingEdgeIds);
    }

    public ProcessInstanceId id() {
        return id;
    }

    public ProcessGraph.ProcessGraphId processGraphId() {
        return processGraphId;
    }

    public int processGraphVersion() {
        return processGraphVersion;
    }

    public String correlationId() {
        return correlationId;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Optional<Instant> completedAt() {
        return Optional.ofNullable(completedAt);
    }

    public ProcessInstanceStatus status() {
        return status;
    }

    public ExecutionContext context() {
        return context;
    }

    public List<NodeExecution> nodeExecutions() {
        return List.copyOf(nodeExecutions);
    }

    public Set<Node.NodeId> activeNodeIds() {
        return Set.copyOf(activeNodeIds);
    }

    public Set<Edge.EdgeId> pendingEdgeIds() {
        return Set.copyOf(pendingEdgeIds);
    }

    /**
     * Checks if the process is still running.
     */
    public boolean isRunning() {
        return status == ProcessInstanceStatus.RUNNING;
    }

    /**
     * Checks if a node has been executed.
     */
    public boolean hasExecutedNode(Node.NodeId nodeId) {
        return nodeExecutions.stream()
            .anyMatch(ne -> ne.nodeId().equals(nodeId)
                && ne.status() == NodeExecutionStatus.COMPLETED);
    }

    /**
     * Records the start of a node execution.
     */
    public void startNodeExecution(Node.NodeId nodeId) {
        activeNodeIds.add(nodeId);
        nodeExecutions.add(new NodeExecution(
            nodeId,
            Instant.now(),
            null,
            NodeExecutionStatus.RUNNING,
            null,
            null
        ));
    }

    /**
     * Records the completion of a node execution.
     */
    public void completeNodeExecution(Node.NodeId nodeId, Object result) {
        activeNodeIds.remove(nodeId);

        // Update the node execution record
        for (int i = nodeExecutions.size() - 1; i >= 0; i--) {
            NodeExecution ne = nodeExecutions.get(i);
            if (ne.nodeId().equals(nodeId) && ne.status() == NodeExecutionStatus.RUNNING) {
                nodeExecutions.set(i, new NodeExecution(
                    nodeId,
                    ne.startedAt(),
                    Instant.now(),
                    NodeExecutionStatus.COMPLETED,
                    result,
                    null
                ));
                break;
            }
        }
    }

    /**
     * Records a failed node execution.
     */
    public void failNodeExecution(Node.NodeId nodeId, String error) {
        activeNodeIds.remove(nodeId);

        // Update the node execution record
        for (int i = nodeExecutions.size() - 1; i >= 0; i--) {
            NodeExecution ne = nodeExecutions.get(i);
            if (ne.nodeId().equals(nodeId) && ne.status() == NodeExecutionStatus.RUNNING) {
                nodeExecutions.set(i, new NodeExecution(
                    nodeId,
                    ne.startedAt(),
                    Instant.now(),
                    NodeExecutionStatus.FAILED,
                    null,
                    error
                ));
                break;
            }
        }
    }

    /**
     * Adds a pending edge awaiting activation.
     */
    public void addPendingEdge(Edge.EdgeId edgeId) {
        pendingEdgeIds.add(edgeId);
    }

    /**
     * Removes a pending edge after activation.
     */
    public void activatePendingEdge(Edge.EdgeId edgeId) {
        pendingEdgeIds.remove(edgeId);
    }

    /**
     * Updates the execution context.
     */
    public void updateContext(ExecutionContext newContext) {
        this.context = newContext;
    }

    /**
     * Completes the process instance.
     */
    public void complete() {
        this.status = ProcessInstanceStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    /**
     * Fails the process instance.
     */
    public void fail() {
        this.status = ProcessInstanceStatus.FAILED;
        this.completedAt = Instant.now();
    }

    /**
     * Suspends the process instance.
     */
    public void suspend() {
        this.status = ProcessInstanceStatus.SUSPENDED;
    }

    /**
     * Resumes a suspended process instance.
     */
    public void resume() {
        if (this.status == ProcessInstanceStatus.SUSPENDED) {
            this.status = ProcessInstanceStatus.RUNNING;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Unique identifier for a process instance.
     */
    public record ProcessInstanceId(String value) {
        public ProcessInstanceId {
            Objects.requireNonNull(value, "ProcessInstanceId value is required");
            if (value.isBlank()) {
                throw new IllegalArgumentException("ProcessInstanceId cannot be blank");
            }
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * Status of a process instance.
     */
    public enum ProcessInstanceStatus {
        RUNNING,
        SUSPENDED,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    /**
     * Record of a node execution.
     */
    public record NodeExecution(
        Node.NodeId nodeId,
        Instant startedAt,
        Instant completedAt,
        NodeExecutionStatus status,
        Object result,
        String error
    ) {
        public NodeExecution {
            Objects.requireNonNull(nodeId, "NodeExecution nodeId is required");
            Objects.requireNonNull(startedAt, "NodeExecution startedAt is required");
            Objects.requireNonNull(status, "NodeExecution status is required");
        }
    }

    /**
     * Status of a node execution.
     */
    public enum NodeExecutionStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        SKIPPED
    }

    /**
     * Builder for ProcessInstance.
     */
    public static class Builder {
        private ProcessInstanceId id;
        private ProcessGraph.ProcessGraphId processGraphId;
        private int processGraphVersion;
        private String correlationId;
        private Instant startedAt;
        private Instant completedAt;
        private ProcessInstanceStatus status;
        private ExecutionContext context;
        private List<NodeExecution> nodeExecutions = new java.util.ArrayList<>();
        private Set<Node.NodeId> activeNodeIds = new java.util.HashSet<>();
        private Set<Edge.EdgeId> pendingEdgeIds = new java.util.HashSet<>();

        public Builder id(ProcessInstanceId id) {
            this.id = id;
            return this;
        }

        public Builder id(String id) {
            this.id = new ProcessInstanceId(id);
            return this;
        }

        public Builder processGraphId(ProcessGraph.ProcessGraphId processGraphId) {
            this.processGraphId = processGraphId;
            return this;
        }

        public Builder processGraphVersion(int version) {
            this.processGraphVersion = version;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder status(ProcessInstanceStatus status) {
            this.status = status;
            return this;
        }

        public Builder context(ExecutionContext context) {
            this.context = context;
            return this;
        }

        public Builder nodeExecutions(List<NodeExecution> nodeExecutions) {
            this.nodeExecutions = new java.util.ArrayList<>(nodeExecutions);
            return this;
        }

        public Builder activeNodeIds(Set<Node.NodeId> activeNodeIds) {
            this.activeNodeIds = new java.util.HashSet<>(activeNodeIds);
            return this;
        }

        public Builder pendingEdgeIds(Set<Edge.EdgeId> pendingEdgeIds) {
            this.pendingEdgeIds = new java.util.HashSet<>(pendingEdgeIds);
            return this;
        }

        public ProcessInstance build() {
            return new ProcessInstance(this);
        }
    }
}
