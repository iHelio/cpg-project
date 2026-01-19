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

import com.ihelio.cpg.domain.model.Edge;

/**
 * Response representing an edge in a process graph.
 *
 * @param id edge identifier
 * @param name human-readable name
 * @param description edge description
 * @param sourceNodeId source node identifier
 * @param targetNodeId target node identifier
 * @param executionType execution type (SEQUENTIAL, PARALLEL)
 * @param priority edge priority for selection
 * @param hasGuardConditions whether the edge has guard conditions
 */
public record EdgeResponse(
    String id,
    String name,
    String description,
    String sourceNodeId,
    String targetNodeId,
    String executionType,
    int priority,
    boolean hasGuardConditions
) {
    /**
     * Creates an edge response from an Edge domain object.
     */
    public static EdgeResponse from(Edge edge) {
        return new EdgeResponse(
            edge.id().value(),
            edge.name(),
            edge.description(),
            edge.sourceNodeId().value(),
            edge.targetNodeId().value(),
            edge.executionSemantics().type().name(),
            edge.priority().weight(),
            edge.guardConditions().hasConditions()
        );
    }
}
