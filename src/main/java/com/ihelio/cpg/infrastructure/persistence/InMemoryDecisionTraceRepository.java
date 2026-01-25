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
import com.ihelio.cpg.domain.orchestration.DecisionTrace;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of DecisionTraceRepository.
 *
 * <p>This implementation is suitable for development and testing. For production,
 * use a persistent implementation backed by a database.
 */
public class InMemoryDecisionTraceRepository implements DecisionTraceRepository {

    private final Map<DecisionTrace.DecisionTraceId, DecisionTrace> traces =
        new ConcurrentHashMap<>();

    @Override
    public void save(DecisionTrace trace) {
        Objects.requireNonNull(trace, "trace is required");
        traces.put(trace.id(), trace);
    }

    @Override
    public Optional<DecisionTrace> findById(DecisionTrace.DecisionTraceId traceId) {
        Objects.requireNonNull(traceId, "traceId is required");
        return Optional.ofNullable(traces.get(traceId));
    }

    @Override
    public List<DecisionTrace> findByInstanceId(ProcessInstance.ProcessInstanceId instanceId) {
        Objects.requireNonNull(instanceId, "instanceId is required");
        return traces.values().stream()
            .filter(trace -> trace.instanceId().equals(instanceId))
            .sorted(Comparator.comparing(DecisionTrace::timestamp))
            .toList();
    }

    @Override
    public List<DecisionTrace> findByInstanceIdAndTimeRange(
            ProcessInstance.ProcessInstanceId instanceId,
            Instant from,
            Instant to) {
        Objects.requireNonNull(instanceId, "instanceId is required");
        Objects.requireNonNull(from, "from is required");
        Objects.requireNonNull(to, "to is required");

        return traces.values().stream()
            .filter(trace -> trace.instanceId().equals(instanceId))
            .filter(trace -> !trace.timestamp().isBefore(from))
            .filter(trace -> trace.timestamp().isBefore(to))
            .sorted(Comparator.comparing(DecisionTrace::timestamp))
            .toList();
    }

    @Override
    public Optional<DecisionTrace> findLatestByInstanceId(
            ProcessInstance.ProcessInstanceId instanceId) {
        Objects.requireNonNull(instanceId, "instanceId is required");
        return traces.values().stream()
            .filter(trace -> trace.instanceId().equals(instanceId))
            .max(Comparator.comparing(DecisionTrace::timestamp));
    }

    @Override
    public List<DecisionTrace> findByInstanceIdAndType(
            ProcessInstance.ProcessInstanceId instanceId,
            DecisionTrace.DecisionType type) {
        Objects.requireNonNull(instanceId, "instanceId is required");
        Objects.requireNonNull(type, "type is required");

        return traces.values().stream()
            .filter(trace -> trace.instanceId().equals(instanceId))
            .filter(trace -> trace.type().equals(type))
            .sorted(Comparator.comparing(DecisionTrace::timestamp))
            .toList();
    }

    @Override
    public long countByInstanceId(ProcessInstance.ProcessInstanceId instanceId) {
        Objects.requireNonNull(instanceId, "instanceId is required");
        return traces.values().stream()
            .filter(trace -> trace.instanceId().equals(instanceId))
            .count();
    }

    @Override
    public long deleteOlderThan(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff is required");

        List<DecisionTrace.DecisionTraceId> toDelete = traces.values().stream()
            .filter(trace -> trace.timestamp().isBefore(cutoff))
            .map(DecisionTrace::id)
            .toList();

        toDelete.forEach(traces::remove);
        return toDelete.size();
    }

    @Override
    public List<DecisionTrace> findAll() {
        return traces.values().stream()
            .sorted(Comparator.comparing(DecisionTrace::timestamp))
            .toList();
    }

    @Override
    public void clear() {
        traces.clear();
    }

    /**
     * Returns the total number of traces stored.
     *
     * @return the count
     */
    public int size() {
        return traces.size();
    }
}
