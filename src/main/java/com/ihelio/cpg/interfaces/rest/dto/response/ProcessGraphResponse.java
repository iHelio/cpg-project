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
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Full response for a process graph including all nodes and edges.
 *
 * @param id graph identifier
 * @param name human-readable name
 * @param description graph description
 * @param version current version number
 * @param status graph status
 * @param nodes list of nodes in the graph
 * @param edges list of edges in the graph
 * @param entryNodeIds IDs of entry point nodes
 * @param terminalNodeIds IDs of terminal nodes
 * @param metadata graph metadata
 */
public record ProcessGraphResponse(
    String id,
    String name,
    String description,
    int version,
    String status,
    List<NodeResponse> nodes,
    List<EdgeResponse> edges,
    List<String> entryNodeIds,
    List<String> terminalNodeIds,
    MetadataResponse metadata
) {
    /**
     * Metadata about the process graph.
     */
    public record MetadataResponse(
        String createdBy,
        Instant createdAt,
        String lastModifiedBy,
        Instant lastModifiedAt,
        Map<String, String> tags
    ) {
        public static MetadataResponse from(ProcessGraph.Metadata metadata) {
            return new MetadataResponse(
                metadata.createdBy(),
                metadata.createdAt(),
                metadata.lastModifiedBy(),
                metadata.lastModifiedAt(),
                metadata.tags()
            );
        }
    }

    /**
     * Creates a full response from a ProcessGraph domain object.
     */
    public static ProcessGraphResponse from(ProcessGraph graph) {
        return new ProcessGraphResponse(
            graph.id().value(),
            graph.name(),
            graph.description(),
            graph.version(),
            graph.status().name(),
            graph.nodes().stream().map(NodeResponse::from).toList(),
            graph.edges().stream().map(EdgeResponse::from).toList(),
            graph.entryNodeIds().stream().map(id -> id.value()).toList(),
            graph.terminalNodeIds().stream().map(id -> id.value()).toList(),
            MetadataResponse.from(graph.metadata())
        );
    }
}
