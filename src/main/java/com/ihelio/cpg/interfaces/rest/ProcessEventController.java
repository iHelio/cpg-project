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

package com.ihelio.cpg.interfaces.rest;

import com.ihelio.cpg.application.service.ProcessEventService;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.interfaces.rest.dto.request.PublishEventRequest;
import com.ihelio.cpg.interfaces.rest.dto.response.ExecutionContextResponse;
import com.ihelio.cpg.interfaces.rest.dto.response.ProcessInstanceSummaryResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for process event operations.
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class ProcessEventController {

    private final ProcessEventService processEventService;

    public ProcessEventController(ProcessEventService processEventService) {
        this.processEventService = processEventService;
    }

    /**
     * Publishes an event to matching process instances.
     *
     * @param request the publish event request
     * @return list of affected process instances
     */
    @PostMapping("/events")
    public ResponseEntity<List<ProcessInstanceSummaryResponse>> publishEvent(
            @Valid @RequestBody PublishEventRequest request) {

        List<ProcessInstance> affected = processEventService.publishEvent(request);

        List<ProcessInstanceSummaryResponse> response = affected.stream()
            .map(ProcessInstanceSummaryResponse::from)
            .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Gets the event history for a process instance.
     *
     * @param instanceId the instance ID
     * @return list of received events
     */
    @GetMapping("/process-instances/{instanceId}/events")
    public ResponseEntity<List<ExecutionContextResponse.ReceivedEventResponse>> getEventHistory(
            @PathVariable String instanceId) {

        List<ExecutionContext.ReceivedEvent> events =
            processEventService.getEventHistory(instanceId);

        List<ExecutionContextResponse.ReceivedEventResponse> response = events.stream()
            .map(ExecutionContextResponse.ReceivedEventResponse::from)
            .toList();

        return ResponseEntity.ok(response);
    }
}
