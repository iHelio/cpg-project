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

package com.ihelio.cpg.interfaces.rest.dto.response;

import com.ihelio.cpg.domain.engine.EdgeTraversal;

/**
 * Response for an available edge traversal.
 *
 * @param edgeId the edge identifier
 * @param edgeName the edge name
 * @param sourceNodeId source node ID
 * @param sourceNodeName source node name
 * @param targetNodeId target node ID
 * @param targetNodeName target node name
 * @param executionType execution type (SEQUENTIAL, PARALLEL)
 * @param priority edge priority
 */
public record EdgeTraversalResponse(
    String edgeId,
    String edgeName,
    String sourceNodeId,
    String sourceNodeName,
    String targetNodeId,
    String targetNodeName,
    String executionType,
    int priority
) {
    /**
     * Creates a response from an EdgeTraversal domain object.
     */
    public static EdgeTraversalResponse from(EdgeTraversal traversal) {
        return new EdgeTraversalResponse(
            traversal.edge().id().value(),
            traversal.edge().name(),
            traversal.sourceNode().id().value(),
            traversal.sourceNode().name(),
            traversal.targetNode().id().value(),
            traversal.targetNode().name(),
            traversal.edge().executionSemantics().type().name(),
            traversal.edge().priority().weight()
        );
    }
}
