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

import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Node;
import java.util.Objects;

/**
 * Represents an edge traversal from source to target node.
 *
 * @param edge the edge being traversed
 * @param sourceNode the source node
 * @param targetNode the target node
 * @param executionType how this edge is being executed
 */
public record EdgeTraversal(
    Edge edge,
    Node sourceNode,
    Node targetNode,
    Edge.ExecutionType executionType
) {

    public EdgeTraversal {
        Objects.requireNonNull(edge, "EdgeTraversal edge is required");
        Objects.requireNonNull(sourceNode, "EdgeTraversal sourceNode is required");
        Objects.requireNonNull(targetNode, "EdgeTraversal targetNode is required");
        executionType = executionType != null
            ? executionType : edge.executionSemantics().type();
    }

    /**
     * Creates a traversal using the edge's default execution type.
     */
    public static EdgeTraversal of(Edge edge, Node sourceNode, Node targetNode) {
        return new EdgeTraversal(
            edge,
            sourceNode,
            targetNode,
            edge.executionSemantics().type()
        );
    }

    /**
     * Checks if this is a parallel execution.
     */
    public boolean isParallel() {
        return executionType == Edge.ExecutionType.PARALLEL;
    }

    /**
     * Checks if this is a compensating execution.
     */
    public boolean isCompensating() {
        return executionType == Edge.ExecutionType.COMPENSATING;
    }

    /**
     * Checks if this is a sequential execution.
     */
    public boolean isSequential() {
        return executionType == Edge.ExecutionType.SEQUENTIAL;
    }
}
