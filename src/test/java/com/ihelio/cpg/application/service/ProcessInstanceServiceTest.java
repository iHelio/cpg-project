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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ihelio.cpg.domain.engine.ProcessExecutionEngine;
import com.ihelio.cpg.domain.exception.ProcessExecutionException;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import com.ihelio.cpg.domain.repository.ProcessInstanceRepository;
import com.ihelio.cpg.interfaces.rest.dto.request.StartProcessRequest;
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
@DisplayName("ProcessInstanceService")
class ProcessInstanceServiceTest {

    @Mock
    private ProcessInstanceRepository processInstanceRepository;

    @Mock
    private ProcessGraphRepository processGraphRepository;

    @Mock
    private ProcessExecutionEngine processExecutionEngine;

    private ProcessInstanceService service;

    @BeforeEach
    void setUp() {
        service = new ProcessInstanceService(
            processInstanceRepository,
            processGraphRepository,
            processExecutionEngine
        );
    }

    @Nested
    @DisplayName("startProcess")
    class StartProcess {

        @Test
        @DisplayName("should start process with latest graph version")
        void shouldStartProcessWithLatestVersion() {
            ProcessGraph graph = createTestGraph();
            ProcessInstance instance = createTestInstance(graph);

            when(processGraphRepository.findLatestVersion(any())).thenReturn(Optional.of(graph));
            when(processExecutionEngine.startProcess(any(), any())).thenReturn(instance);

            StartProcessRequest request = new StartProcessRequest(
                "employee-onboarding",
                null,
                null,
                Map.of("tenantId", "acme"),
                Map.of("employeeId", "emp-123")
            );

            ProcessInstance result = service.startProcess(request);

            assertThat(result).isNotNull();
            assertThat(result.isRunning()).isTrue();
            verify(processInstanceRepository).save(any());
        }

        @Test
        @DisplayName("should start process with specific version")
        void shouldStartProcessWithSpecificVersion() {
            ProcessGraph graph = createTestGraph();
            ProcessInstance instance = createTestInstance(graph);

            when(processGraphRepository.findByIdAndVersion(any(), anyInt()))
                .thenReturn(Optional.of(graph));
            when(processExecutionEngine.startProcess(any(), any())).thenReturn(instance);

            StartProcessRequest request = new StartProcessRequest(
                "employee-onboarding",
                2,
                null,
                Map.of(),
                Map.of()
            );

            ProcessInstance result = service.startProcess(request);

            assertThat(result).isNotNull();
            verify(processGraphRepository).findByIdAndVersion(any(), anyInt());
        }

        @Test
        @DisplayName("should throw exception when graph not found")
        void shouldThrowExceptionWhenGraphNotFound() {
            when(processGraphRepository.findLatestVersion(any())).thenReturn(Optional.empty());

            StartProcessRequest request = new StartProcessRequest(
                "unknown",
                null,
                null,
                Map.of(),
                Map.of()
            );

            assertThatThrownBy(() -> service.startProcess(request))
                .isInstanceOf(ProcessExecutionException.class)
                .extracting("errorType")
                .isEqualTo(ProcessExecutionException.ErrorType.GRAPH_NOT_FOUND);
        }

        @Test
        @DisplayName("should set correlation id when provided")
        void shouldSetCorrelationIdWhenProvided() {
            ProcessGraph graph = createTestGraph();
            ProcessInstance instance = createTestInstance(graph);

            when(processGraphRepository.findLatestVersion(any())).thenReturn(Optional.of(graph));
            when(processExecutionEngine.startProcess(any(), any())).thenReturn(instance);

            StartProcessRequest request = new StartProcessRequest(
                "employee-onboarding",
                null,
                "corr-123",
                Map.of(),
                Map.of()
            );

            ProcessInstance result = service.startProcess(request);

            assertThat(result.correlationId()).isEqualTo("corr-123");
        }
    }

    @Nested
    @DisplayName("getInstance")
    class GetInstance {

        @Test
        @DisplayName("should return instance when found")
        void shouldReturnInstanceWhenFound() {
            ProcessInstance instance = createTestInstance(createTestGraph());
            when(processInstanceRepository.findById(any())).thenReturn(Optional.of(instance));

            Optional<ProcessInstance> result = service.getInstance("inst-123");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            when(processInstanceRepository.findById(any())).thenReturn(Optional.empty());

            Optional<ProcessInstance> result = service.getInstance("unknown");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("listInstances")
    class ListInstances {

        @Test
        @DisplayName("should filter by correlation id when provided")
        void shouldFilterByCorrelationId() {
            ProcessInstance instance = createTestInstance(createTestGraph());
            when(processInstanceRepository.findByCorrelationId("corr-123"))
                .thenReturn(List.of(instance));

            List<ProcessInstance> result = service.listInstances(null, null, "corr-123");

            assertThat(result).hasSize(1);
            verify(processInstanceRepository).findByCorrelationId("corr-123");
        }

        @Test
        @DisplayName("should filter by process graph id when provided")
        void shouldFilterByProcessGraphId() {
            ProcessInstance instance = createTestInstance(createTestGraph());
            when(processInstanceRepository.findByProcessGraphId(any()))
                .thenReturn(List.of(instance));

            List<ProcessInstance> result = service.listInstances("graph-123", null, null);

            assertThat(result).hasSize(1);
            verify(processInstanceRepository).findByProcessGraphId(any());
        }

        @Test
        @DisplayName("should filter by status when provided")
        void shouldFilterByStatus() {
            ProcessInstance instance = createTestInstance(createTestGraph());
            when(processInstanceRepository.findByStatus(ProcessInstance.ProcessInstanceStatus.RUNNING))
                .thenReturn(List.of(instance));

            List<ProcessInstance> result = service.listInstances(
                null, ProcessInstance.ProcessInstanceStatus.RUNNING, null);

            assertThat(result).hasSize(1);
            verify(processInstanceRepository).findByStatus(
                ProcessInstance.ProcessInstanceStatus.RUNNING);
        }

        @Test
        @DisplayName("should return running instances by default")
        void shouldReturnRunningInstancesByDefault() {
            ProcessInstance instance = createTestInstance(createTestGraph());
            when(processInstanceRepository.findRunning()).thenReturn(List.of(instance));

            List<ProcessInstance> result = service.listInstances(null, null, null);

            assertThat(result).hasSize(1);
            verify(processInstanceRepository).findRunning();
        }
    }

    @Nested
    @DisplayName("suspendInstance")
    class SuspendInstance {

        @Test
        @DisplayName("should suspend running instance")
        void shouldSuspendRunningInstance() {
            ProcessInstance instance = createTestInstance(createTestGraph());
            when(processInstanceRepository.findById(any())).thenReturn(Optional.of(instance));

            ProcessInstance result = service.suspendInstance("inst-123");

            verify(processExecutionEngine).suspendProcess(instance);
            verify(processInstanceRepository).save(instance);
        }

        @Test
        @DisplayName("should throw exception when instance not found")
        void shouldThrowExceptionWhenNotFound() {
            when(processInstanceRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.suspendInstance("unknown"))
                .isInstanceOf(ProcessExecutionException.class)
                .extracting("errorType")
                .isEqualTo(ProcessExecutionException.ErrorType.INSTANCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("resumeInstance")
    class ResumeInstance {

        @Test
        @DisplayName("should resume suspended instance")
        void shouldResumeSuspendedInstance() {
            ProcessGraph graph = createTestGraph();
            ProcessInstance instance = createSuspendedInstance(graph);

            when(processInstanceRepository.findById(any())).thenReturn(Optional.of(instance));
            when(processGraphRepository.findByIdAndVersion(any(), anyInt()))
                .thenReturn(Optional.of(graph));

            ProcessInstance result = service.resumeInstance("inst-123");

            verify(processExecutionEngine).resumeProcess(instance, graph);
            verify(processInstanceRepository).save(instance);
        }

        @Test
        @DisplayName("should throw exception when not suspended")
        void shouldThrowExceptionWhenNotSuspended() {
            ProcessInstance instance = createTestInstance(createTestGraph());
            when(processInstanceRepository.findById(any())).thenReturn(Optional.of(instance));

            assertThatThrownBy(() -> service.resumeInstance("inst-123"))
                .isInstanceOf(ProcessExecutionException.class)
                .extracting("errorType")
                .isEqualTo(ProcessExecutionException.ErrorType.INVALID_STATE);
        }
    }

    @Nested
    @DisplayName("cancelInstance")
    class CancelInstance {

        @Test
        @DisplayName("should cancel running instance")
        void shouldCancelRunningInstance() {
            ProcessInstance instance = createTestInstance(createTestGraph());
            when(processInstanceRepository.findById(any())).thenReturn(Optional.of(instance));

            ProcessInstance result = service.cancelInstance("inst-123");

            verify(processInstanceRepository).save(instance);
        }

        @Test
        @DisplayName("should throw exception when already completed")
        void shouldThrowExceptionWhenCompleted() {
            ProcessInstance instance = createCompletedInstance(createTestGraph());
            when(processInstanceRepository.findById(any())).thenReturn(Optional.of(instance));

            assertThatThrownBy(() -> service.cancelInstance("inst-123"))
                .isInstanceOf(ProcessExecutionException.class)
                .extracting("errorType")
                .isEqualTo(ProcessExecutionException.ErrorType.INVALID_STATE);
        }
    }

    private ProcessGraph createTestGraph() {
        return new ProcessGraph(
            new ProcessGraph.ProcessGraphId("employee-onboarding"),
            "Employee Onboarding",
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

    private ProcessInstance createSuspendedInstance(ProcessGraph graph) {
        ProcessInstance instance = createTestInstance(graph);
        instance.suspend();
        return instance;
    }

    private ProcessInstance createCompletedInstance(ProcessGraph graph) {
        ProcessInstance instance = createTestInstance(graph);
        instance.complete();
        return instance;
    }
}
