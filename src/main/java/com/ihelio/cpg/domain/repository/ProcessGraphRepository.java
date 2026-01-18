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

package com.ihelio.cpg.domain.repository;

import com.ihelio.cpg.domain.model.ProcessGraph;
import java.util.List;
import java.util.Optional;

/**
 * Port for process graph persistence.
 *
 * <p>Process graphs are templates that define all possible nodes and edges
 * for a business process. They are versioned and reusable across instances.
 */
public interface ProcessGraphRepository {

    /**
     * Saves a process graph.
     *
     * @param graph the process graph to save
     * @return the saved process graph
     */
    ProcessGraph save(ProcessGraph graph);

    /**
     * Finds a process graph by ID.
     *
     * @param id the process graph ID
     * @return the process graph, or empty if not found
     */
    Optional<ProcessGraph> findById(ProcessGraph.ProcessGraphId id);

    /**
     * Finds a process graph by ID and version.
     *
     * @param id the process graph ID
     * @param version the version number
     * @return the process graph, or empty if not found
     */
    Optional<ProcessGraph> findByIdAndVersion(ProcessGraph.ProcessGraphId id, int version);

    /**
     * Finds the latest version of a process graph.
     *
     * @param id the process graph ID
     * @return the latest version, or empty if not found
     */
    Optional<ProcessGraph> findLatestVersion(ProcessGraph.ProcessGraphId id);

    /**
     * Finds all process graphs by status.
     *
     * @param status the status to filter by
     * @return list of matching process graphs
     */
    List<ProcessGraph> findByStatus(ProcessGraph.ProcessGraphStatus status);

    /**
     * Finds all published process graphs.
     *
     * @return list of published process graphs
     */
    default List<ProcessGraph> findPublished() {
        return findByStatus(ProcessGraph.ProcessGraphStatus.PUBLISHED);
    }

    /**
     * Deletes a process graph by ID.
     *
     * @param id the process graph ID
     */
    void deleteById(ProcessGraph.ProcessGraphId id);

    /**
     * Checks if a process graph exists.
     *
     * @param id the process graph ID
     * @return true if the graph exists
     */
    boolean existsById(ProcessGraph.ProcessGraphId id);
}
