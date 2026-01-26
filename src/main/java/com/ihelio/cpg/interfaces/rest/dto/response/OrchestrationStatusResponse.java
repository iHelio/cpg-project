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

import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.orchestration.DecisionTrace;
import com.ihelio.cpg.domain.orchestration.NavigationDecision;
import com.ihelio.cpg.domain.orchestration.ProcessOrchestrator;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response containing the orchestration status of a process instance.
 *
 * @param instanceId the process instance ID
 * @param processGraphId the process graph ID
 * @param correlationId the business correlation ID
 * @param status the current instance status
 * @param isActive whether orchestration is actively running
 * @param isComplete whether the process is complete
 * @param lastDecision summary of the last navigation decision
 * @param lastTrace summary of the last decision trace
 * @param nodeExecutions list of node executions
 * @param startedAt when the process started
 * @param completedAt when the process completed (if applicable)
 */
public record OrchestrationStatusResponse(
    String instanceId,
    String processGraphId,
    String correlationId,
    String status,
    boolean isActive,
    boolean isComplete,
    boolean isSuspended,
    boolean isFailed,
    DecisionSummary lastDecision,
    TraceSummary lastTrace,
    List<NodeExecutionSummary> nodeExecutions,
    Instant startedAt,
    Instant completedAt
) {

    public static OrchestrationStatusResponse from(ProcessOrchestrator.OrchestrationStatus status) {
        ProcessInstance instance = status.instance();

        return new OrchestrationStatusResponse(
            instance.id().value(),
            instance.processGraphId().value(),
            instance.correlationId(),
            instance.status().name(),
            status.isActive(),
            status.isComplete(),
            status.isSuspended(),
            status.isFailed(),
            status.lastDecision() != null ? DecisionSummary.from(status.lastDecision()) : null,
            status.lastTrace() != null ? TraceSummary.from(status.lastTrace()) : null,
            instance.nodeExecutions().stream()
                .map(NodeExecutionSummary::from)
                .toList(),
            instance.startedAt(),
            instance.completedAt().orElse(null)
        );
    }

    public record DecisionSummary(
        String type,
        List<String> selectedNodes,
        String selectionReason,
        int alternativesConsidered,
        Instant decidedAt
    ) {
        public static DecisionSummary from(NavigationDecision decision) {
            return new DecisionSummary(
                decision.type().name(),
                decision.selectedNodes().stream()
                    .map(s -> s.node().id().value())
                    .toList(),
                decision.selectionReason(),
                decision.alternatives().size(),
                decision.decidedAt()
            );
        }
    }

    public record TraceSummary(
        String traceId,
        String type,
        int nodesEvaluated,
        int edgesEvaluated,
        boolean governanceApproved,
        String outcomeStatus,
        Instant timestamp
    ) {
        public static TraceSummary from(DecisionTrace trace) {
            return new TraceSummary(
                trace.id().value(),
                trace.type().name(),
                trace.evaluation() != null ? trace.evaluation().nodesEvaluated().size() : 0,
                trace.evaluation() != null ? trace.evaluation().edgesEvaluated().size() : 0,
                trace.governance() != null && trace.governance().overallApproved(),
                trace.outcome() != null ? trace.outcome().status().name() : "UNKNOWN",
                trace.timestamp()
            );
        }
    }

    public record NodeExecutionSummary(
        String nodeId,
        String status,
        Instant startedAt,
        Instant completedAt,
        Object result,
        String error
    ) {
        public static NodeExecutionSummary from(ProcessInstance.NodeExecution execution) {
            return new NodeExecutionSummary(
                execution.nodeId().value(),
                execution.status().name(),
                execution.startedAt(),
                execution.completedAt(),
                execution.result(),
                execution.error()
            );
        }
    }
}
