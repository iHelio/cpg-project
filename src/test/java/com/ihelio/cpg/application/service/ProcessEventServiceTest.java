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

package com.ihelio.cpg.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ihelio.cpg.domain.engine.ProcessExecutionEngine;
import com.ihelio.cpg.domain.exception.ProcessExecutionException;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import com.ihelio.cpg.domain.repository.ProcessInstanceRepository;
import com.ihelio.cpg.interfaces.rest.dto.request.PublishEventRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessEventService")
class ProcessEventServiceTest {

    @Mock
    private ProcessInstanceRepository processInstanceRepository;

    @Mock
    private ProcessGraphRepository processGraphRepository;

    @Mock
    private ProcessExecutionEngine processExecutionEngine;

    private ProcessEventService service;

    @BeforeEach
    void setUp() {
        service = new ProcessEventService(
            processInstanceRepository,
            processGraphRepository,
            processExecutionEngine
        );
    }

    @Nested
    @DisplayName("publishEvent")
    class PublishEvent {

        @Test
        @DisplayName("should publish event to matching instances by correlation id")
        void shouldPublishEventToMatchingInstancesByCorrelationId() {
            ProcessGraph graph = createTestGraph();
            ProcessInstance instance = createTestInstance(graph);

            when(processInstanceRepository.findByCorrelationId("corr-123"))
                .thenReturn(List.of(instance));
            when(processGraphRepository.findLatestVersion(any()))
                .thenReturn(Optional.of(graph));
            when(processExecutionEngine.handleEvent(any(), any(), any()))
                .thenReturn(List.of(instance));

            PublishEventRequest request = new PublishEventRequest(
                "task.completed",
                "corr-123",
                Map.of("result", "success"),
                "EXTERNAL",
                "external-system"
            );

            List<ProcessInstance> result = service.publishEvent(request);

            assertThat(result).hasSize(1);
            verify(processInstanceRepository).save(instance);
        }

        @Test
        @DisplayName("should publish event to running instances when no correlation id")
        void shouldPublishEventToRunningInstancesWhenNoCorrelationId() {
            ProcessGraph graph = createTestGraph();
            ProcessInstance instance = createTestInstance(graph);

            when(processInstanceRepository.findByStatus(
                ProcessInstance.ProcessInstanceStatus.RUNNING))
                .thenReturn(List.of(instance));
            when(processGraphRepository.findLatestVersion(any()))
                .thenReturn(Optional.of(graph));
            when(processExecutionEngine.handleEvent(any(), any(), any()))
                .thenReturn(List.of(instance));

            PublishEventRequest request = new PublishEventRequest(
                "task.completed",
                null,
                Map.of("result", "success"),
                "SYSTEM",
                null
            );

            List<ProcessInstance> result = service.publishEvent(request);

            assertThat(result).hasSize(1);
            verify(processInstanceRepository).findByStatus(
                ProcessInstance.ProcessInstanceStatus.RUNNING);
        }

        @Test
        @DisplayName("should handle no matching instances")
        void shouldHandleNoMatchingInstances() {
            when(processInstanceRepository.findByCorrelationId("unknown"))
                .thenReturn(List.of());

            PublishEventRequest request = new PublishEventRequest(
                "task.completed",
                "unknown",
                Map.of(),
                "USER",
                "user-123"
            );

            List<ProcessInstance> result = service.publishEvent(request);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getEventHistory")
    class GetEventHistory {

        @Test
        @DisplayName("should return event history for instance")
        void shouldReturnEventHistoryForInstance() {
            ExecutionContext.ReceivedEvent event = new ExecutionContext.ReceivedEvent(
                "test.event",
                "event-123",
                Instant.now(),
                Map.of()
            );
            ExecutionContext context = ExecutionContext.builder().build()
                .withEvent(event);

            ProcessInstance instance = ProcessInstance.builder()
                .id("inst-123")
                .processGraphId(new ProcessGraph.ProcessGraphId("test-graph"))
                .processGraphVersion(1)
                .context(context)
                .build();

            when(processInstanceRepository.findById(any())).thenReturn(Optional.of(instance));

            List<ExecutionContext.ReceivedEvent> result = service.getEventHistory("inst-123");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).eventType()).isEqualTo("test.event");
        }

        @Test
        @DisplayName("should throw exception when instance not found")
        void shouldThrowExceptionWhenInstanceNotFound() {
            when(processInstanceRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getEventHistory("unknown"))
                .isInstanceOf(ProcessExecutionException.class)
                .extracting("errorType")
                .isEqualTo(ProcessExecutionException.ErrorType.INSTANCE_NOT_FOUND);
        }
    }

    private ProcessGraph createTestGraph() {
        return new ProcessGraph(
            new ProcessGraph.ProcessGraphId("test-graph"),
            "Test Graph",
            null,
            1,
            ProcessGraph.ProcessGraphStatus.PUBLISHED,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null
        );
    }

    private ProcessInstance createTestInstance(ProcessGraph graph) {
        return ProcessInstance.builder()
            .id("inst-123")
            .processGraphId(graph.id())
            .processGraphVersion(graph.version())
            .context(ExecutionContext.builder().build())
            .build();
    }
}
