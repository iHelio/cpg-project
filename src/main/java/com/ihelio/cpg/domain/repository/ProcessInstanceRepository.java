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

import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.ProcessGraph;
import java.util.List;
import java.util.Optional;

/**
 * Port for process instance persistence.
 *
 * <p>Process instances represent running or completed executions of
 * process graphs. They maintain state across the process lifecycle.
 */
public interface ProcessInstanceRepository {

    /**
     * Saves a process instance.
     *
     * @param instance the process instance to save
     * @return the saved process instance
     */
    ProcessInstance save(ProcessInstance instance);

    /**
     * Finds a process instance by ID.
     *
     * @param id the process instance ID
     * @return the process instance, or empty if not found
     */
    Optional<ProcessInstance> findById(ProcessInstance.ProcessInstanceId id);

    /**
     * Finds all process instances for a process graph.
     *
     * @param processGraphId the process graph ID
     * @return list of process instances
     */
    List<ProcessInstance> findByProcessGraphId(ProcessGraph.ProcessGraphId processGraphId);

    /**
     * Finds process instances by correlation ID.
     *
     * @param correlationId the correlation ID
     * @return list of matching process instances
     */
    List<ProcessInstance> findByCorrelationId(String correlationId);

    /**
     * Finds all running process instances.
     *
     * @return list of running process instances
     */
    List<ProcessInstance> findRunning();

    /**
     * Finds all suspended process instances.
     *
     * @return list of suspended process instances
     */
    List<ProcessInstance> findSuspended();

    /**
     * Finds process instances by status.
     *
     * @param status the status to filter by
     * @return list of matching process instances
     */
    List<ProcessInstance> findByStatus(ProcessInstance.ProcessInstanceStatus status);

    /**
     * Deletes a process instance by ID.
     *
     * @param id the process instance ID
     */
    void deleteById(ProcessInstance.ProcessInstanceId id);

    /**
     * Checks if a process instance exists.
     *
     * @param id the process instance ID
     * @return true if the instance exists
     */
    boolean existsById(ProcessInstance.ProcessInstanceId id);
}
