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

import com.ihelio.cpg.domain.model.ProcessGraph;

/**
 * Summary response for process graph list operations.
 *
 * @param id graph identifier
 * @param name human-readable name
 * @param description graph description
 * @param version current version number
 * @param status graph status (DRAFT, PUBLISHED, DEPRECATED, ARCHIVED)
 * @param nodeCount number of nodes in the graph
 * @param edgeCount number of edges in the graph
 */
public record ProcessGraphSummaryResponse(
    String id,
    String name,
    String description,
    int version,
    String status,
    int nodeCount,
    int edgeCount
) {
    /**
     * Creates a summary response from a ProcessGraph domain object.
     */
    public static ProcessGraphSummaryResponse from(ProcessGraph graph) {
        return new ProcessGraphSummaryResponse(
            graph.id().value(),
            graph.name(),
            graph.description(),
            graph.version(),
            graph.status().name(),
            graph.nodes().size(),
            graph.edges().size()
        );
    }
}
