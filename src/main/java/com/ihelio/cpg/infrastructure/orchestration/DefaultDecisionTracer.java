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

import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.orchestration.DecisionTrace;
import com.ihelio.cpg.domain.orchestration.DecisionTracer;
import com.ihelio.cpg.infrastructure.persistence.DecisionTraceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DefaultDecisionTracer implements the DecisionTracer port with persistence and logging.
 *
 * <p>Decision traces are:
 * <ul>
 *   <li>Logged for observability</li>
 *   <li>Persisted for audit trail</li>
 *   <li>Queryable by instance, time range, and type</li>
 * </ul>
 */
public class DefaultDecisionTracer implements DecisionTracer {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDecisionTracer.class);

    private final DecisionTraceRepository repository;
    private final OrchestratorConfigProperties config;

    /**
     * Creates a DefaultDecisionTracer with repository and configuration.
     *
     * @param repository the decision trace repository
     * @param config the orchestrator configuration
     */
    public DefaultDecisionTracer(DecisionTraceRepository repository,
            OrchestratorConfigProperties config) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.config = Objects.requireNonNull(config, "config is required");
    }

    @Override
    public void record(DecisionTrace trace) {
        Objects.requireNonNull(trace, "trace is required");

        // Log the decision
        logTrace(trace);

        // Persist if enabled
        if (config.tracing().persistTraces()) {
            repository.save(trace);
        }
    }

    @Override
    public Optional<DecisionTrace> findById(DecisionTrace.DecisionTraceId traceId) {
        Objects.requireNonNull(traceId, "traceId is required");
        return repository.findById(traceId);
    }

    @Override
    public List<DecisionTrace> findByInstanceId(ProcessInstance.ProcessInstanceId instanceId) {
        Objects.requireNonNull(instanceId, "instanceId is required");
        return repository.findByInstanceId(instanceId);
    }

    @Override
    public List<DecisionTrace> findByInstanceIdAndTimeRange(
            ProcessInstance.ProcessInstanceId instanceId,
            Instant from,
            Instant to) {
        Objects.requireNonNull(instanceId, "instanceId is required");
        Objects.requireNonNull(from, "from is required");
        Objects.requireNonNull(to, "to is required");
        return repository.findByInstanceIdAndTimeRange(instanceId, from, to);
    }

    @Override
    public Optional<DecisionTrace> findLatestByInstanceId(
            ProcessInstance.ProcessInstanceId instanceId) {
        Objects.requireNonNull(instanceId, "instanceId is required");
        return repository.findLatestByInstanceId(instanceId);
    }

    @Override
    public List<DecisionTrace> findByInstanceIdAndType(
            ProcessInstance.ProcessInstanceId instanceId,
            DecisionTrace.DecisionType type) {
        Objects.requireNonNull(instanceId, "instanceId is required");
        Objects.requireNonNull(type, "type is required");
        return repository.findByInstanceIdAndType(instanceId, type);
    }

    @Override
    public long countByInstanceId(ProcessInstance.ProcessInstanceId instanceId) {
        Objects.requireNonNull(instanceId, "instanceId is required");
        return repository.countByInstanceId(instanceId);
    }

    @Override
    public long deleteOlderThan(Instant retentionCutoff) {
        Objects.requireNonNull(retentionCutoff, "retentionCutoff is required");
        long deleted = repository.deleteOlderThan(retentionCutoff);
        LOG.info("Deleted {} decision traces older than {}", deleted, retentionCutoff);
        return deleted;
    }

    private void logTrace(DecisionTrace trace) {
        if (!config.tracing().enabled()) {
            return;
        }

        switch (trace.type()) {
            case EXECUTION -> LOG.info(
                "DECISION [{}] Instance={} Type={} Node={} Governance={} Outcome={}",
                trace.id(),
                trace.instanceId(),
                trace.type(),
                trace.decision().selectedAction() != null
                    ? trace.decision().selectedAction().nodeId()
                    : "none",
                trace.governance().overallApproved() ? "APPROVED" : "REJECTED",
                trace.outcome().status()
            );

            case NAVIGATION -> LOG.info(
                "DECISION [{}] Instance={} Type={} Selected={} Criteria={} Alternatives={}",
                trace.id(),
                trace.instanceId(),
                trace.type(),
                trace.decision().selectedAction() != null
                    ? trace.decision().selectedAction().nodeId()
                    : "none",
                trace.decision().selectionCriteria(),
                trace.decision().alternativesConsidered().size()
            );

            case WAIT -> LOG.debug(
                "DECISION [{}] Instance={} Type={} Reason={} EligibleNodes={}",
                trace.id(),
                trace.instanceId(),
                trace.type(),
                trace.decision().selectionReason(),
                trace.evaluation().eligibleSpace().eligibleNodeCount()
            );

            case BLOCKED -> LOG.warn(
                "DECISION [{}] Instance={} Type={} Reason={} Governance={}",
                trace.id(),
                trace.instanceId(),
                trace.type(),
                trace.outcome().error() != null ? trace.outcome().error() : "unknown",
                trace.governance().overallApproved() ? "APPROVED" : "REJECTED"
            );
        }

        // Debug-level detailed trace
        if (LOG.isDebugEnabled()) {
            LOG.debug("Full decision trace: {}", trace);
        }
    }
}
