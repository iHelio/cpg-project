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

import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.repository.ProcessInstanceRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * In-memory implementation of ProcessInstanceRepository.
 *
 * <p>Uses a ConcurrentHashMap for thread-safe storage.
 * Suitable for development and testing.
 */
@Repository
public class InMemoryProcessInstanceRepository implements ProcessInstanceRepository {

    private final Map<String, ProcessInstance> instances = new ConcurrentHashMap<>();

    @Override
    public ProcessInstance save(ProcessInstance instance) {
        instances.put(instance.id().value(), instance);
        return instance;
    }

    @Override
    public Optional<ProcessInstance> findById(ProcessInstance.ProcessInstanceId id) {
        return Optional.ofNullable(instances.get(id.value()));
    }

    @Override
    public List<ProcessInstance> findByProcessGraphId(ProcessGraph.ProcessGraphId processGraphId) {
        return instances.values().stream()
            .filter(i -> i.processGraphId().equals(processGraphId))
            .toList();
    }

    @Override
    public List<ProcessInstance> findByCorrelationId(String correlationId) {
        return instances.values().stream()
            .filter(i -> correlationId.equals(i.correlationId()))
            .toList();
    }

    @Override
    public List<ProcessInstance> findRunning() {
        return findByStatus(ProcessInstance.ProcessInstanceStatus.RUNNING);
    }

    @Override
    public List<ProcessInstance> findSuspended() {
        return findByStatus(ProcessInstance.ProcessInstanceStatus.SUSPENDED);
    }

    @Override
    public List<ProcessInstance> findByStatus(ProcessInstance.ProcessInstanceStatus status) {
        return instances.values().stream()
            .filter(i -> i.status() == status)
            .toList();
    }

    @Override
    public void deleteById(ProcessInstance.ProcessInstanceId id) {
        instances.remove(id.value());
    }

    @Override
    public boolean existsById(ProcessInstance.ProcessInstanceId id) {
        return instances.containsKey(id.value());
    }

    /**
     * Returns all instances (for testing).
     */
    public List<ProcessInstance> findAll() {
        return List.copyOf(instances.values());
    }

    /**
     * Clears all stored instances (for testing).
     */
    public void clear() {
        instances.clear();
    }

    /**
     * Returns the count of stored instances.
     */
    public int count() {
        return instances.size();
    }
}
