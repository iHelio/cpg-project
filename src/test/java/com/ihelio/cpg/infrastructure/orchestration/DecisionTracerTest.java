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

package com.ihelio.cpg.infrastructure.orchestration;

import static org.junit.jupiter.api.Assertions.*;

import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.orchestration.DecisionTrace;
import com.ihelio.cpg.infrastructure.persistence.InMemoryDecisionTraceRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DecisionTracerTest {

    private DefaultDecisionTracer tracer;
    private InMemoryDecisionTraceRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryDecisionTraceRepository();
        OrchestratorConfigProperties config = OrchestratorConfigProperties.defaults();
        tracer = new DefaultDecisionTracer(repository, config);
    }

    @Test
    @DisplayName("Should record decision trace")
    void shouldRecordDecisionTrace() {
        // Given
        ProcessInstance.ProcessInstanceId instanceId = new ProcessInstance.ProcessInstanceId("instance-1");
        DecisionTrace trace = createTrace(instanceId, DecisionTrace.DecisionType.NAVIGATION);

        // When
        tracer.record(trace);

        // Then
        Optional<DecisionTrace> found = tracer.findById(trace.id());
        assertTrue(found.isPresent());
        assertEquals(trace.id(), found.get().id());
        assertEquals(instanceId, found.get().instanceId());
    }

    @Test
    @DisplayName("Should find traces by instance ID")
    void shouldFindTracesByInstanceId() {
        // Given
        ProcessInstance.ProcessInstanceId instanceId = new ProcessInstance.ProcessInstanceId("instance-1");
        DecisionTrace trace1 = createTrace(instanceId, DecisionTrace.DecisionType.NAVIGATION);
        DecisionTrace trace2 = createTrace(instanceId, DecisionTrace.DecisionType.EXECUTION);

        tracer.record(trace1);
        tracer.record(trace2);

        // When
        List<DecisionTrace> traces = tracer.findByInstanceId(instanceId);

        // Then
        assertEquals(2, traces.size());
    }

    @Test
    @DisplayName("Should find latest trace by instance ID")
    void shouldFindLatestTraceByInstanceId() {
        // Given
        ProcessInstance.ProcessInstanceId instanceId = new ProcessInstance.ProcessInstanceId("instance-1");
        DecisionTrace trace1 = createTraceWithTimestamp(instanceId,
            Instant.now().minus(1, ChronoUnit.HOURS));
        DecisionTrace trace2 = createTraceWithTimestamp(instanceId, Instant.now());

        tracer.record(trace1);
        tracer.record(trace2);

        // When
        Optional<DecisionTrace> latest = tracer.findLatestByInstanceId(instanceId);

        // Then
        assertTrue(latest.isPresent());
        assertEquals(trace2.id(), latest.get().id());
    }

    @Test
    @DisplayName("Should find traces by type")
    void shouldFindTracesByType() {
        // Given
        ProcessInstance.ProcessInstanceId instanceId = new ProcessInstance.ProcessInstanceId("instance-1");
        DecisionTrace trace1 = createTrace(instanceId, DecisionTrace.DecisionType.NAVIGATION);
        DecisionTrace trace2 = createTrace(instanceId, DecisionTrace.DecisionType.EXECUTION);
        DecisionTrace trace3 = createTrace(instanceId, DecisionTrace.DecisionType.NAVIGATION);

        tracer.record(trace1);
        tracer.record(trace2);
        tracer.record(trace3);

        // When
        List<DecisionTrace> navigationTraces = tracer.findByInstanceIdAndType(
            instanceId, DecisionTrace.DecisionType.NAVIGATION);

        // Then
        assertEquals(2, navigationTraces.size());
    }

    @Test
    @DisplayName("Should count traces by instance ID")
    void shouldCountTracesByInstanceId() {
        // Given
        ProcessInstance.ProcessInstanceId instanceId = new ProcessInstance.ProcessInstanceId("instance-1");
        tracer.record(createTrace(instanceId, DecisionTrace.DecisionType.NAVIGATION));
        tracer.record(createTrace(instanceId, DecisionTrace.DecisionType.EXECUTION));
        tracer.record(createTrace(instanceId, DecisionTrace.DecisionType.WAIT));

        // When
        long count = tracer.countByInstanceId(instanceId);

        // Then
        assertEquals(3, count);
    }

    @Test
    @DisplayName("Should delete old traces")
    void shouldDeleteOldTraces() {
        // Given
        ProcessInstance.ProcessInstanceId instanceId = new ProcessInstance.ProcessInstanceId("instance-1");
        DecisionTrace oldTrace = createTraceWithTimestamp(instanceId,
            Instant.now().minus(100, ChronoUnit.DAYS));
        DecisionTrace newTrace = createTraceWithTimestamp(instanceId, Instant.now());

        tracer.record(oldTrace);
        tracer.record(newTrace);

        // When
        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        long deleted = tracer.deleteOlderThan(cutoff);

        // Then
        assertEquals(1, deleted);
        assertEquals(1, repository.size());
        assertFalse(tracer.findById(oldTrace.id()).isPresent());
        assertTrue(tracer.findById(newTrace.id()).isPresent());
    }

    private DecisionTrace createTrace(ProcessInstance.ProcessInstanceId instanceId,
            DecisionTrace.DecisionType type) {
        return createTraceWithTimestampAndType(instanceId, Instant.now(), type);
    }

    private DecisionTrace createTraceWithTimestamp(ProcessInstance.ProcessInstanceId instanceId,
            Instant timestamp) {
        return createTraceWithTimestampAndType(instanceId, timestamp, DecisionTrace.DecisionType.NAVIGATION);
    }

    private DecisionTrace createTraceWithTimestampAndType(ProcessInstance.ProcessInstanceId instanceId,
            Instant timestamp, DecisionTrace.DecisionType type) {
        return new DecisionTrace(
            DecisionTrace.DecisionTraceId.generate(),
            timestamp,
            instanceId,
            type,
            DecisionTrace.ContextSnapshot.empty(),
            DecisionTrace.EvaluationSnapshot.empty(),
            DecisionTrace.DecisionSnapshot.empty(),
            DecisionTrace.GovernanceSnapshot.skipped(),
            DecisionTrace.OutcomeSnapshot.waiting()
        );
    }
}
