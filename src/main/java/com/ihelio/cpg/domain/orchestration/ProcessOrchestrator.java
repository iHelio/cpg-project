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
import com.ihelio.cpg.domain.model.ProcessGraph;

/**
 * ProcessOrchestrator is the port interface for the policy-enforcing navigation engine.
 *
 * <p>The orchestrator is the authoritative runtime component that navigates process graphs
 * as a decision engine. It evaluates, decides, governs, and traces every meaningful decision.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Navigate the graph based on declared preconditions, business rules, and policy outcomes</li>
 *   <li>Deterministically select the next best action(s) using priority, dependency, and concurrency semantics</li>
 *   <li>Apply idempotency, authorization, and policy enforcement BEFORE producing side effects</li>
 *   <li>Produce immutable decision traces for every meaningful decision</li>
 *   <li>Adapt dynamically to events without modifying the graph definition</li>
 * </ul>
 */
public interface ProcessOrchestrator {

    /**
     * Starts orchestrating a new process instance.
     *
     * @param graph the process graph to execute
     * @param initialContext the initial runtime context
     * @return the started process instance
     */
    ProcessInstance start(ProcessGraph graph, RuntimeContext initialContext);

    /**
     * Signals an event to the orchestrator for processing.
     *
     * <p>Events trigger reevaluation of nodes and edges. Execution paths
     * adapt dynamically without modifying the graph definition.
     *
     * @param event the orchestration event
     */
    void signal(OrchestrationEvent event);

    /**
     * Suspends orchestration of a process instance.
     *
     * @param instanceId the process instance to suspend
     */
    void suspend(ProcessInstance.ProcessInstanceId instanceId);

    /**
     * Resumes orchestration of a suspended process instance.
     *
     * @param instanceId the process instance to resume
     */
    void resume(ProcessInstance.ProcessInstanceId instanceId);

    /**
     * Cancels orchestration of a process instance.
     *
     * @param instanceId the process instance to cancel
     */
    void cancel(ProcessInstance.ProcessInstanceId instanceId);

    /**
     * Gets the current status of a process instance.
     *
     * @param instanceId the process instance ID
     * @return the orchestration status
     */
    OrchestrationStatus getStatus(ProcessInstance.ProcessInstanceId instanceId);

    /**
     * Represents the current orchestration status of a process instance.
     *
     * @param instance the process instance
     * @param lastDecision the last navigation decision made
     * @param lastTrace the last decision trace
     * @param isActive whether orchestration is actively running
     */
    record OrchestrationStatus(
        ProcessInstance instance,
        NavigationDecision lastDecision,
        DecisionTrace lastTrace,
        boolean isActive
    ) {
        public boolean isComplete() {
            return instance.status() == ProcessInstance.ProcessInstanceStatus.COMPLETED;
        }

        public boolean isFailed() {
            return instance.status() == ProcessInstance.ProcessInstanceStatus.FAILED;
        }

        public boolean isSuspended() {
            return instance.status() == ProcessInstance.ProcessInstanceStatus.SUSPENDED;
        }
    }
}
