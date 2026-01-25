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

package com.ihelio.cpg.domain.orchestration;

import com.ihelio.cpg.domain.execution.ProcessInstance;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * DecisionTracer is the port for recording and querying decision traces.
 *
 * <p>Decision traces form the system of record for execution reasoning. They capture:
 * <ul>
 *   <li>Context at decision time</li>
 *   <li>All rules and policies evaluated</li>
 *   <li>All alternatives considered</li>
 *   <li>The action taken and why</li>
 *   <li>The governance checks performed</li>
 *   <li>The outcome of execution</li>
 * </ul>
 *
 * <p>Traces are immutable once recorded and should be persisted for audit and compliance.
 */
public interface DecisionTracer {

    /**
     * Records a decision trace.
     *
     * @param trace the decision trace to record
     */
    void record(DecisionTrace trace);

    /**
     * Finds a decision trace by its ID.
     *
     * @param traceId the trace ID
     * @return the decision trace, or empty if not found
     */
    Optional<DecisionTrace> findById(DecisionTrace.DecisionTraceId traceId);

    /**
     * Finds all decision traces for a process instance.
     *
     * @param instanceId the process instance ID
     * @return list of decision traces, ordered by timestamp
     */
    List<DecisionTrace> findByInstanceId(ProcessInstance.ProcessInstanceId instanceId);

    /**
     * Finds decision traces for a process instance within a time range.
     *
     * @param instanceId the process instance ID
     * @param from start of time range (inclusive)
     * @param to end of time range (exclusive)
     * @return list of decision traces within the range
     */
    List<DecisionTrace> findByInstanceIdAndTimeRange(
        ProcessInstance.ProcessInstanceId instanceId,
        Instant from,
        Instant to
    );

    /**
     * Finds the most recent decision trace for a process instance.
     *
     * @param instanceId the process instance ID
     * @return the most recent trace, or empty if none exists
     */
    Optional<DecisionTrace> findLatestByInstanceId(ProcessInstance.ProcessInstanceId instanceId);

    /**
     * Finds decision traces by type for a process instance.
     *
     * @param instanceId the process instance ID
     * @param type the decision type
     * @return list of matching decision traces
     */
    List<DecisionTrace> findByInstanceIdAndType(
        ProcessInstance.ProcessInstanceId instanceId,
        DecisionTrace.DecisionType type
    );

    /**
     * Counts the total number of decisions for a process instance.
     *
     * @param instanceId the process instance ID
     * @return the count of decision traces
     */
    long countByInstanceId(ProcessInstance.ProcessInstanceId instanceId);

    /**
     * Deletes old traces beyond the retention period.
     *
     * @param retentionCutoff traces older than this will be deleted
     * @return number of traces deleted
     */
    long deleteOlderThan(Instant retentionCutoff);
}
