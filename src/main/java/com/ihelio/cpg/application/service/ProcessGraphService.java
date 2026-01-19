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

import com.ihelio.cpg.domain.exception.ProcessExecutionException;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.model.ProcessGraph.ProcessGraphStatus;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Application service for process graph management.
 *
 * <p>This service provides CRUD operations for process graphs and handles
 * graph lifecycle management (publishing, deprecation).
 */
@Service
public class ProcessGraphService {

    private final ProcessGraphRepository processGraphRepository;

    public ProcessGraphService(ProcessGraphRepository processGraphRepository) {
        this.processGraphRepository = processGraphRepository;
    }

    /**
     * Lists all process graphs, optionally filtered by status.
     *
     * @param status optional status filter
     * @return list of matching process graphs
     */
    public List<ProcessGraph> listGraphs(ProcessGraphStatus status) {
        if (status != null) {
            return processGraphRepository.findByStatus(status);
        }
        // Return all published graphs if no status filter
        return processGraphRepository.findPublished();
    }

    /**
     * Retrieves a process graph by ID.
     *
     * @param id the graph ID
     * @return the graph, or empty if not found
     */
    public Optional<ProcessGraph> getGraph(String id) {
        return processGraphRepository.findLatestVersion(
            new ProcessGraph.ProcessGraphId(id)
        );
    }

    /**
     * Retrieves a specific version of a process graph.
     *
     * @param id the graph ID
     * @param version the version number
     * @return the graph version, or empty if not found
     */
    public Optional<ProcessGraph> getGraphVersion(String id, int version) {
        return processGraphRepository.findByIdAndVersion(
            new ProcessGraph.ProcessGraphId(id),
            version
        );
    }

    /**
     * Saves a process graph.
     *
     * @param graph the graph to save
     * @return the saved graph
     */
    public ProcessGraph saveGraph(ProcessGraph graph) {
        return processGraphRepository.save(graph);
    }

    /**
     * Validates a process graph structure.
     *
     * @param id the graph ID
     * @return list of validation errors (empty if valid)
     * @throws ProcessExecutionException if graph not found
     */
    public List<String> validateGraph(String id) {
        ProcessGraph graph = processGraphRepository.findLatestVersion(
                new ProcessGraph.ProcessGraphId(id))
            .orElseThrow(() -> new ProcessExecutionException(
                "Process graph not found: " + id,
                ProcessExecutionException.ErrorType.GRAPH_NOT_FOUND
            ));

        return graph.validate();
    }

    /**
     * Deletes a process graph.
     *
     * @param id the graph ID
     */
    public void deleteGraph(String id) {
        processGraphRepository.deleteById(new ProcessGraph.ProcessGraphId(id));
    }
}
