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

package com.ihelio.cpg.infrastructure.persistence;

import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * In-memory implementation of ProcessGraphRepository.
 *
 * <p>Uses a ConcurrentHashMap for thread-safe storage.
 * Suitable for development and testing.
 */
@Repository
public class InMemoryProcessGraphRepository implements ProcessGraphRepository {

    // Key format: "id:version"
    private final Map<String, ProcessGraph> graphs = new ConcurrentHashMap<>();

    @Override
    public ProcessGraph save(ProcessGraph graph) {
        String key = makeKey(graph.id(), graph.version());
        graphs.put(key, graph);
        return graph;
    }

    @Override
    public Optional<ProcessGraph> findById(ProcessGraph.ProcessGraphId id) {
        // Find the latest version
        return findLatestVersion(id);
    }

    @Override
    public Optional<ProcessGraph> findByIdAndVersion(
            ProcessGraph.ProcessGraphId id,
            int version) {
        return Optional.ofNullable(graphs.get(makeKey(id, version)));
    }

    @Override
    public Optional<ProcessGraph> findLatestVersion(ProcessGraph.ProcessGraphId id) {
        return graphs.values().stream()
            .filter(g -> g.id().equals(id))
            .max(Comparator.comparingInt(ProcessGraph::version));
    }

    @Override
    public List<ProcessGraph> findByStatus(ProcessGraph.ProcessGraphStatus status) {
        return graphs.values().stream()
            .filter(g -> g.status() == status)
            .toList();
    }

    @Override
    public void deleteById(ProcessGraph.ProcessGraphId id) {
        graphs.entrySet().removeIf(e -> e.getValue().id().equals(id));
    }

    @Override
    public boolean existsById(ProcessGraph.ProcessGraphId id) {
        return graphs.values().stream()
            .anyMatch(g -> g.id().equals(id));
    }

    /**
     * Clears all stored graphs (for testing).
     */
    public void clear() {
        graphs.clear();
    }

    /**
     * Returns the count of stored graphs.
     */
    public int count() {
        return graphs.size();
    }

    private String makeKey(ProcessGraph.ProcessGraphId id, int version) {
        return id.value() + ":" + version;
    }
}
