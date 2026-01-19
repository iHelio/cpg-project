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

import com.ihelio.cpg.domain.execution.ExecutionContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response representing the execution context of a process instance.
 *
 * @param clientContext client/tenant-specific context data
 * @param domainContext business domain data
 * @param accumulatedState state accumulated from node executions
 * @param eventHistory history of events received
 * @param obligations pending obligations and SLAs
 */
public record ExecutionContextResponse(
    Map<String, Object> clientContext,
    Map<String, Object> domainContext,
    Map<String, Object> accumulatedState,
    List<ReceivedEventResponse> eventHistory,
    List<ObligationResponse> obligations
) {
    /**
     * Received event in event history.
     */
    public record ReceivedEventResponse(
        String eventType,
        String eventId,
        Instant receivedAt,
        Map<String, Object> payload
    ) {
        public static ReceivedEventResponse from(ExecutionContext.ReceivedEvent event) {
            return new ReceivedEventResponse(
                event.eventType(),
                event.eventId(),
                event.receivedAt(),
                event.payload()
            );
        }
    }

    /**
     * Obligation derived from business rules.
     */
    public record ObligationResponse(
        String id,
        String description,
        String sourceNodeId,
        Instant deadline,
        String status,
        boolean overdue
    ) {
        public static ObligationResponse from(ExecutionContext.Obligation obligation) {
            return new ObligationResponse(
                obligation.id(),
                obligation.description(),
                obligation.sourceNodeId(),
                obligation.deadline(),
                obligation.status().name(),
                obligation.isOverdue()
            );
        }
    }

    /**
     * Creates a context response from an ExecutionContext domain object.
     */
    public static ExecutionContextResponse from(ExecutionContext context) {
        return new ExecutionContextResponse(
            context.clientContext(),
            context.domainContext(),
            context.accumulatedState(),
            context.eventHistory().stream()
                .map(ReceivedEventResponse::from)
                .toList(),
            context.obligations().stream()
                .map(ObligationResponse::from)
                .toList()
        );
    }
}
