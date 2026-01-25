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
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for DecisionTrace persistence.
 *
 * <p>Decision traces are immutable records that form the system of record for
 * execution reasoning. They should be persisted for audit and compliance purposes.
 */
public interface DecisionTraceRepository {

    /**
     * Saves a decision trace.
     *
     * @param trace the trace to save
     */
    void save(DecisionTrace trace);

    /**
     * Finds a decision trace by its ID.
     *
     * @param traceId the trace ID
     * @return the trace, or empty if not found
     */
    Optional<DecisionTrace> findById(DecisionTrace.DecisionTraceId traceId);

    /**
     * Finds all traces for a process instance.
     *
     * @param instanceId the process instance ID
     * @return list of traces, ordered by timestamp
     */
    List<DecisionTrace> findByInstanceId(ProcessInstance.ProcessInstanceId instanceId);

    /**
     * Finds traces within a time range.
     *
     * @param instanceId the process instance ID
     * @param from start of range (inclusive)
     * @param to end of range (exclusive)
     * @return list of traces within the range
     */
    List<DecisionTrace> findByInstanceIdAndTimeRange(
        ProcessInstance.ProcessInstanceId instanceId,
        Instant from,
        Instant to
    );

    /**
     * Finds the most recent trace for an instance.
     *
     * @param instanceId the process instance ID
     * @return the latest trace, or empty if none
     */
    Optional<DecisionTrace> findLatestByInstanceId(ProcessInstance.ProcessInstanceId instanceId);

    /**
     * Finds traces by type for an instance.
     *
     * @param instanceId the process instance ID
     * @param type the decision type
     * @return list of matching traces
     */
    List<DecisionTrace> findByInstanceIdAndType(
        ProcessInstance.ProcessInstanceId instanceId,
        DecisionTrace.DecisionType type
    );

    /**
     * Counts traces for an instance.
     *
     * @param instanceId the process instance ID
     * @return the count
     */
    long countByInstanceId(ProcessInstance.ProcessInstanceId instanceId);

    /**
     * Deletes traces older than the cutoff.
     *
     * @param cutoff traces older than this will be deleted
     * @return number deleted
     */
    long deleteOlderThan(Instant cutoff);

    /**
     * Finds all traces (for testing/admin).
     *
     * @return all traces
     */
    List<DecisionTrace> findAll();

    /**
     * Clears all traces (for testing).
     */
    void clear();
}
