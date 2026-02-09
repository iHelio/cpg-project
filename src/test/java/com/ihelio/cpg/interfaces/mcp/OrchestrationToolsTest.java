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

package com.ihelio.cpg.interfaces.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihelio.cpg.application.orchestration.ContextAssembler;
import com.ihelio.cpg.application.orchestration.EligibilityEvaluator;
import com.ihelio.cpg.application.orchestration.InstanceOrchestrator;
import com.ihelio.cpg.application.service.ProcessExecutionService;
import com.ihelio.cpg.application.service.ProcessGraphService;
import com.ihelio.cpg.application.service.ProcessInstanceService;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.FeelExpression;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.Node.Action;
import com.ihelio.cpg.domain.model.Node.ActionType;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.orchestration.ProcessOrchestrator;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import com.ihelio.cpg.domain.repository.ProcessInstanceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrchestrationTools")
class OrchestrationToolsTest {

    @Mock
    private ProcessGraphService processGraphService;

    @Mock
    private ProcessOrchestrator processOrchestrator;

    @Mock
    private ProcessGraphRepository processGraphRepository;

    @Mock
    private ProcessInstanceRepository processInstanceRepository;

    @Mock
    private ProcessInstanceService processInstanceService;

    @Mock
    private ProcessExecutionService processExecutionService;

    @Mock
    private InstanceOrchestrator instanceOrchestrator;

    @Mock
    private ContextAssembler contextAssembler;

    @Mock
    private EligibilityEvaluator eligibilityEvaluator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OrchestrationTools tools;

    @BeforeEach
    void setUp() {
        objectMapper.findAndRegisterModules();
        tools = new OrchestrationTools(
            processGraphService,
            processOrchestrator,
            processGraphRepository,
            processInstanceRepository,
            processInstanceService,
            processExecutionService,
            instanceOrchestrator,
            contextAssembler,
            eligibilityEvaluator,
            objectMapper
        );
    }

    // ── list_process_graphs ─────────────────────────────────────────────────

    @Test
    @DisplayName("list_process_graphs should return JSON list of graphs")
    void listProcessGraphsShouldReturnJsonList() {
        ProcessGraph graph = createTestProcessGraph();
        when(processGraphService.listGraphs(null)).thenReturn(List.of(graph));

        String result = tools.listProcessGraphs();

        assertThat(result).contains("employee-onboarding");
        assertThat(result).contains("Employee Onboarding");
    }

    @Test
    @DisplayName("list_process_graphs should return empty array when no graphs")
    void listProcessGraphsShouldReturnEmptyArray() {
        when(processGraphService.listGraphs(null)).thenReturn(List.of());

        String result = tools.listProcessGraphs();

        assertThat(result).isEqualTo("[]");
    }

    // ── get_process_graph ───────────────────────────────────────────────────

    @Test
    @DisplayName("get_process_graph should return graph JSON when found")
    void getProcessGraphShouldReturnGraphWhenFound() {
        ProcessGraph graph = createTestProcessGraph();
        when(processGraphService.getGraph("employee-onboarding"))
            .thenReturn(Optional.of(graph));

        String result = tools.getProcessGraph("employee-onboarding");

        assertThat(result).contains("employee-onboarding");
        assertThat(result).contains("PUBLISHED");
    }

    @Test
    @DisplayName("get_process_graph should return error when not found")
    void getProcessGraphShouldReturnErrorWhenNotFound() {
        when(processGraphService.getGraph("unknown")).thenReturn(Optional.empty());

        String result = tools.getProcessGraph("unknown");

        assertThat(result).contains("error");
        assertThat(result).contains("not found");
    }

    // ── validate_process_graph ──────────────────────────────────────────────

    @Test
    @DisplayName("validate_process_graph should return valid result when no errors")
    void validateProcessGraphShouldReturnValidResult() {
        when(processGraphService.validateGraph("graph-1")).thenReturn(List.of());

        String result = tools.validateProcessGraph("graph-1");

        assertThat(result).contains("\"valid\":true");
        assertThat(result).contains("\"errors\":[]");
    }

    @Test
    @DisplayName("validate_process_graph should return errors when invalid")
    void validateProcessGraphShouldReturnErrors() {
        when(processGraphService.validateGraph("graph-1"))
            .thenReturn(List.of("No entry nodes", "Cycle detected"));

        String result = tools.validateProcessGraph("graph-1");

        assertThat(result).contains("\"valid\":false");
        assertThat(result).contains("No entry nodes");
        assertThat(result).contains("Cycle detected");
    }

    // ── start_orchestration ─────────────────────────────────────────────────

    @Test
    @DisplayName("start_orchestration should return instance details")
    void startOrchestrationShouldReturnInstanceDetails() {
        ProcessGraph graph = createTestProcessGraph();
        ProcessInstance instance = createTestInstance();
        ProcessOrchestrator.OrchestrationStatus status =
            new ProcessOrchestrator.OrchestrationStatus(instance, null, null, true);

        when(processGraphRepository.findLatestVersion(any())).thenReturn(Optional.of(graph));
        when(processOrchestrator.start(any(), any())).thenReturn(instance);
        when(processOrchestrator.getStatus(any())).thenReturn(status);

        String result = tools.startOrchestration("employee-onboarding", null, null);

        assertThat(result).contains("inst-123");
        assertThat(result).contains("RUNNING");
        assertThat(result).contains("\"isActive\":true");
    }

    @Test
    @DisplayName("start_orchestration should throw when graph not found")
    void startOrchestrationShouldThrowWhenGraphNotFound() {
        when(processGraphRepository.findLatestVersion(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            tools.startOrchestration("unknown", null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("start_orchestration should parse client and domain context JSON")
    void startOrchestrationShouldParseContextJson() {
        ProcessGraph graph = createTestProcessGraph();
        ProcessInstance instance = createTestInstance();
        ProcessOrchestrator.OrchestrationStatus status =
            new ProcessOrchestrator.OrchestrationStatus(instance, null, null, true);

        when(processGraphRepository.findLatestVersion(any())).thenReturn(Optional.of(graph));
        when(processOrchestrator.start(any(), any())).thenReturn(instance);
        when(processOrchestrator.getStatus(any())).thenReturn(status);

        String result = tools.startOrchestration(
            "employee-onboarding",
            "{\"tenantId\": \"acme\"}",
            "{\"employeeId\": \"emp-123\"}"
        );

        assertThat(result).contains("inst-123");
        verify(processOrchestrator).start(any(), any());
    }

    // ── get_orchestration_status ────────────────────────────────────────────

    @Test
    @DisplayName("get_orchestration_status should return status when found")
    void getOrchestrationStatusShouldReturnStatusWhenFound() {
        ProcessInstance instance = createTestInstance();
        ProcessOrchestrator.OrchestrationStatus status =
            new ProcessOrchestrator.OrchestrationStatus(instance, null, null, true);

        when(processOrchestrator.getStatus(any())).thenReturn(status);

        String result = tools.getOrchestrationStatus("inst-123");

        assertThat(result).contains("RUNNING");
        assertThat(result).contains("\"isActive\":true");
        assertThat(result).contains("\"isComplete\":false");
    }

    @Test
    @DisplayName("get_orchestration_status should return error when not found")
    void getOrchestrationStatusShouldReturnErrorWhenNotFound() {
        when(processOrchestrator.getStatus(any())).thenReturn(null);

        String result = tools.getOrchestrationStatus("unknown");

        assertThat(result).contains("error");
        assertThat(result).contains("not found");
    }

    // ── signal_event ────────────────────────────────────────────────────────

    @Test
    @DisplayName("signal_event should signal and return status")
    void signalEventShouldSignalAndReturnStatus() {
        ProcessInstance instance = createTestInstance();
        ProcessOrchestrator.OrchestrationStatus status =
            new ProcessOrchestrator.OrchestrationStatus(instance, null, null, true);

        doNothing().when(processOrchestrator).signal(any());
        when(processOrchestrator.getStatus(any())).thenReturn(status);

        String result = tools.signalEvent(
            "inst-123", "NODE_COMPLETED", "node-1", "{\"result\": \"ok\"}");

        assertThat(result).contains("\"signaled\":true");
        assertThat(result).contains("RUNNING");
        verify(processOrchestrator).signal(any());
    }

    @Test
    @DisplayName("signal_event should handle custom domain events")
    void signalEventShouldHandleCustomDomainEvents() {
        ProcessInstance instance = createTestInstance();
        ProcessOrchestrator.OrchestrationStatus status =
            new ProcessOrchestrator.OrchestrationStatus(instance, null, null, true);

        doNothing().when(processOrchestrator).signal(any());
        when(processOrchestrator.getStatus(any())).thenReturn(status);

        String result = tools.signalEvent(
            "inst-123", "OnboardingStarted", null, "{\"employeeId\": \"emp-123\"}");

        assertThat(result).contains("\"signaled\":true");
        assertThat(result).contains("RUNNING");
        verify(processOrchestrator).signal(any());
    }

    // ── suspend_orchestration ───────────────────────────────────────────────

    @Test
    @DisplayName("suspend_orchestration should suspend and return status")
    void suspendOrchestrationShouldSuspendAndReturnStatus() {
        ProcessInstance instance = createTestInstance();
        instance.suspend();
        ProcessOrchestrator.OrchestrationStatus status =
            new ProcessOrchestrator.OrchestrationStatus(instance, null, null, false);

        doNothing().when(processOrchestrator).suspend(any());
        when(processOrchestrator.getStatus(any())).thenReturn(status);

        String result = tools.suspendOrchestration("inst-123");

        assertThat(result).contains("SUSPENDED");
        verify(processOrchestrator).suspend(any());
    }

    // ── resume_orchestration ────────────────────────────────────────────────

    @Test
    @DisplayName("resume_orchestration should resume and return status")
    void resumeOrchestrationShouldResumeAndReturnStatus() {
        ProcessInstance instance = createTestInstance();
        ProcessOrchestrator.OrchestrationStatus status =
            new ProcessOrchestrator.OrchestrationStatus(instance, null, null, true);

        doNothing().when(processOrchestrator).resume(any());
        when(processOrchestrator.getStatus(any())).thenReturn(status);

        String result = tools.resumeOrchestration("inst-123");

        assertThat(result).contains("RUNNING");
        verify(processOrchestrator).resume(any());
    }

    // ── cancel_orchestration ────────────────────────────────────────────────

    @Test
    @DisplayName("cancel_orchestration should cancel and return status")
    void cancelOrchestrationShouldCancelAndReturnStatus() {
        ProcessInstance instance = createTestInstance();
        instance.fail();
        ProcessOrchestrator.OrchestrationStatus status =
            new ProcessOrchestrator.OrchestrationStatus(instance, null, null, false);

        doNothing().when(processOrchestrator).cancel(any());
        when(processOrchestrator.getStatus(any())).thenReturn(status);

        String result = tools.cancelOrchestration("inst-123");

        assertThat(result).contains("FAILED");
        verify(processOrchestrator).cancel(any());
    }

    // ── list_process_instances ──────────────────────────────────────────────

    @Test
    @DisplayName("list_process_instances should return instance summaries")
    void listProcessInstancesShouldReturnSummaries() {
        ProcessInstance instance = createTestInstance();
        when(processInstanceService.listInstances(null, null, null))
            .thenReturn(List.of(instance));

        String result = tools.listProcessInstances(null, null, null);

        assertThat(result).contains("inst-123");
        assertThat(result).contains("employee-onboarding");
        assertThat(result).contains("RUNNING");
    }

    @Test
    @DisplayName("list_process_instances should filter by status")
    void listProcessInstancesShouldFilterByStatus() {
        when(processInstanceService.listInstances(
            null, ProcessInstance.ProcessInstanceStatus.SUSPENDED, null))
            .thenReturn(List.of());

        String result = tools.listProcessInstances(null, "SUSPENDED", null);

        assertThat(result).isEqualTo("[]");
    }

    // ── get_available_nodes ─────────────────────────────────────────────────

    @Test
    @DisplayName("get_available_nodes should return available node summaries")
    void getAvailableNodesShouldReturnNodeSummaries() {
        Node node = new Node(
            new Node.NodeId("node-1"),
            "Verify Identity",
            "Verify employee identity",
            1,
            null, List.of(), List.of(),
            new Action(ActionType.SYSTEM_INVOCATION, "verify-handler", "Verify", null),
            null, null
        );
        when(processExecutionService.getAvailableNodes("inst-123"))
            .thenReturn(List.of(node));

        String result = tools.getAvailableNodes("inst-123");

        assertThat(result).contains("node-1");
        assertThat(result).contains("Verify Identity");
    }

    @Test
    @DisplayName("get_available_nodes should return empty array when none available")
    void getAvailableNodesShouldReturnEmptyArray() {
        when(processExecutionService.getAvailableNodes("inst-123"))
            .thenReturn(List.of());

        String result = tools.getAvailableNodes("inst-123");

        assertThat(result).isEqualTo("[]");
    }

    // ── get_active_nodes ───────────────────────────────────────────────────

    @Test
    @DisplayName("get_active_nodes should return active node details")
    void getActiveNodesShouldReturnActiveNodeDetails() {
        ProcessInstance instance = createTestInstanceWithActiveNodes();
        ProcessGraph graph = createTestProcessGraphWithNodes();

        when(processInstanceService.getInstance("inst-123"))
            .thenReturn(Optional.of(instance));
        when(processGraphRepository.findByIdAndVersion(any(), anyInt()))
            .thenReturn(Optional.of(graph));

        String result = tools.getActiveNodes("inst-123");

        assertThat(result).contains("inst-123");
        assertThat(result).contains("node-1");
        assertThat(result).contains("Verify Identity");
        assertThat(result).contains("\"activeNodeCount\":1");
    }

    @Test
    @DisplayName("get_active_nodes should return error when instance not found")
    void getActiveNodesShouldReturnErrorWhenNotFound() {
        when(processInstanceService.getInstance("unknown"))
            .thenReturn(Optional.empty());

        String result = tools.getActiveNodes("unknown");

        assertThat(result).contains("error");
        assertThat(result).contains("not found");
    }

    @Test
    @DisplayName("get_active_nodes should handle missing graph gracefully")
    void getActiveNodesShouldHandleMissingGraph() {
        ProcessInstance instance = createTestInstanceWithActiveNodes();

        when(processInstanceService.getInstance("inst-123"))
            .thenReturn(Optional.of(instance));
        when(processGraphRepository.findByIdAndVersion(any(), anyInt()))
            .thenReturn(Optional.empty());

        String result = tools.getActiveNodes("inst-123");

        assertThat(result).contains("inst-123");
        assertThat(result).contains("node-1");
        assertThat(result).contains("\"activeNodeCount\":1");
    }

    // ── get_process_history ────────────────────────────────────────────────

    @Test
    @DisplayName("get_process_history should return execution history")
    void getProcessHistoryShouldReturnExecutionHistory() {
        ProcessInstance instance = createTestInstanceWithHistory();
        ProcessGraph graph = createTestProcessGraphWithNodes();

        when(processInstanceService.getInstance("inst-123"))
            .thenReturn(Optional.of(instance));
        when(processGraphRepository.findByIdAndVersion(any(), anyInt()))
            .thenReturn(Optional.of(graph));

        String result = tools.getProcessHistory("inst-123");

        assertThat(result).contains("inst-123");
        assertThat(result).contains("history");
        assertThat(result).contains("node-1");
        assertThat(result).contains("COMPLETED");
        assertThat(result).contains("\"executionCount\":1");
    }

    @Test
    @DisplayName("get_process_history should return error when instance not found")
    void getProcessHistoryShouldReturnErrorWhenNotFound() {
        when(processInstanceService.getInstance("unknown"))
            .thenReturn(Optional.empty());

        String result = tools.getProcessHistory("unknown");

        assertThat(result).contains("error");
        assertThat(result).contains("not found");
    }

    @Test
    @DisplayName("get_process_history should show failed executions with error")
    void getProcessHistoryShouldShowFailedExecutions() {
        ProcessInstance instance = createTestInstanceWithFailedExecution();

        when(processInstanceService.getInstance("inst-123"))
            .thenReturn(Optional.of(instance));
        when(processGraphRepository.findByIdAndVersion(any(), anyInt()))
            .thenReturn(Optional.empty());

        String result = tools.getProcessHistory("inst-123");

        assertThat(result).contains("FAILED");
        assertThat(result).contains("Connection timeout");
    }

    // ── get_graph_nodes ────────────────────────────────────────────────────

    @Test
    @DisplayName("get_graph_nodes should return all nodes with full details")
    void getGraphNodesShouldReturnAllNodesWithDetails() {
        ProcessGraph graph = createTestProcessGraphWithNodes();

        when(processGraphService.getGraph("employee-onboarding"))
            .thenReturn(Optional.of(graph));

        String result = tools.getGraphNodes("employee-onboarding");

        assertThat(result).contains("employee-onboarding");
        assertThat(result).contains("node-1");
        assertThat(result).contains("Verify Identity");
        assertThat(result).contains("SYSTEM_INVOCATION");
        assertThat(result).contains("\"nodeCount\":1");
    }

    @Test
    @DisplayName("get_graph_nodes should return error when graph not found")
    void getGraphNodesShouldReturnErrorWhenNotFound() {
        when(processGraphService.getGraph("unknown"))
            .thenReturn(Optional.empty());

        String result = tools.getGraphNodes("unknown");

        assertThat(result).contains("error");
        assertThat(result).contains("not found");
    }

    // ── get_graph_edges ────────────────────────────────────────────────────

    @Test
    @DisplayName("get_graph_edges should return all edges with full details")
    void getGraphEdgesShouldReturnAllEdgesWithDetails() {
        ProcessGraph graph = createTestProcessGraphWithEdges();

        when(processGraphService.getGraph("employee-onboarding"))
            .thenReturn(Optional.of(graph));

        String result = tools.getGraphEdges("employee-onboarding");

        assertThat(result).contains("employee-onboarding");
        assertThat(result).contains("edge-1");
        assertThat(result).contains("node-1");
        assertThat(result).contains("node-2");
        assertThat(result).contains("SEQUENTIAL");
        assertThat(result).contains("\"edgeCount\":1");
    }

    @Test
    @DisplayName("get_graph_edges should return error when graph not found")
    void getGraphEdgesShouldReturnErrorWhenNotFound() {
        when(processGraphService.getGraph("unknown"))
            .thenReturn(Optional.empty());

        String result = tools.getGraphEdges("unknown");

        assertThat(result).contains("error");
        assertThat(result).contains("not found");
    }

    // ── get_node_details ───────────────────────────────────────────────────

    @Test
    @DisplayName("get_node_details should return full node details")
    void getNodeDetailsShouldReturnFullDetails() {
        ProcessGraph graph = createTestProcessGraphWithNodes();

        when(processGraphService.getGraph("employee-onboarding"))
            .thenReturn(Optional.of(graph));

        String result = tools.getNodeDetails("employee-onboarding", "node-1");

        assertThat(result).contains("node-1");
        assertThat(result).contains("Verify Identity");
        assertThat(result).contains("SYSTEM_INVOCATION");
        assertThat(result).contains("verify-handler");
    }

    @Test
    @DisplayName("get_node_details should return error when node not found")
    void getNodeDetailsShouldReturnErrorWhenNotFound() {
        ProcessGraph graph = createTestProcessGraphWithNodes();

        when(processGraphService.getGraph("employee-onboarding"))
            .thenReturn(Optional.of(graph));

        String result = tools.getNodeDetails("employee-onboarding", "unknown-node");

        assertThat(result).contains("error");
        assertThat(result).contains("not found");
    }

    // ── get_edge_details ───────────────────────────────────────────────────

    @Test
    @DisplayName("get_edge_details should return full edge details")
    void getEdgeDetailsShouldReturnFullDetails() {
        ProcessGraph graph = createTestProcessGraphWithEdges();

        when(processGraphService.getGraph("employee-onboarding"))
            .thenReturn(Optional.of(graph));

        String result = tools.getEdgeDetails("employee-onboarding", "edge-1");

        assertThat(result).contains("edge-1");
        assertThat(result).contains("node-1");
        assertThat(result).contains("node-2");
        assertThat(result).contains("SEQUENTIAL");
    }

    @Test
    @DisplayName("get_edge_details should return error when edge not found")
    void getEdgeDetailsShouldReturnErrorWhenNotFound() {
        ProcessGraph graph = createTestProcessGraphWithEdges();

        when(processGraphService.getGraph("employee-onboarding"))
            .thenReturn(Optional.of(graph));

        String result = tools.getEdgeDetails("employee-onboarding", "unknown-edge");

        assertThat(result).contains("error");
        assertThat(result).contains("not found");
    }

    // ── Test Data Helpers ───────────────────────────────────────────────────

    private ProcessInstance createTestInstance() {
        return ProcessInstance.builder()
            .id("inst-123")
            .processGraphId(new ProcessGraph.ProcessGraphId("employee-onboarding"))
            .processGraphVersion(1)
            .context(ExecutionContext.builder().build())
            .build();
    }

    private ProcessGraph createTestProcessGraph() {
        return new ProcessGraph(
            new ProcessGraph.ProcessGraphId("employee-onboarding"),
            "Employee Onboarding",
            "Employee onboarding process",
            1,
            ProcessGraph.ProcessGraphStatus.PUBLISHED,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null
        );
    }

    private ProcessInstance createTestInstanceWithActiveNodes() {
        return ProcessInstance.builder()
            .id("inst-123")
            .processGraphId(new ProcessGraph.ProcessGraphId("employee-onboarding"))
            .processGraphVersion(1)
            .context(ExecutionContext.builder().build())
            .activeNodeIds(java.util.Set.of(new Node.NodeId("node-1")))
            .build();
    }

    private ProcessInstance createTestInstanceWithHistory() {
        ProcessInstance instance = ProcessInstance.builder()
            .id("inst-123")
            .processGraphId(new ProcessGraph.ProcessGraphId("employee-onboarding"))
            .processGraphVersion(1)
            .context(ExecutionContext.builder().build())
            .nodeExecutions(List.of(
                new ProcessInstance.NodeExecution(
                    new Node.NodeId("node-1"),
                    java.time.Instant.now().minusSeconds(60),
                    java.time.Instant.now(),
                    ProcessInstance.NodeExecutionStatus.COMPLETED,
                    null,
                    null
                )
            ))
            .build();
        return instance;
    }

    private ProcessInstance createTestInstanceWithFailedExecution() {
        return ProcessInstance.builder()
            .id("inst-123")
            .processGraphId(new ProcessGraph.ProcessGraphId("employee-onboarding"))
            .processGraphVersion(1)
            .context(ExecutionContext.builder().build())
            .nodeExecutions(List.of(
                new ProcessInstance.NodeExecution(
                    new Node.NodeId("node-1"),
                    java.time.Instant.now().minusSeconds(60),
                    java.time.Instant.now(),
                    ProcessInstance.NodeExecutionStatus.FAILED,
                    null,
                    "Connection timeout"
                )
            ))
            .build();
    }

    private ProcessGraph createTestProcessGraphWithNodes() {
        Node node = new Node(
            new Node.NodeId("node-1"),
            "Verify Identity",
            "Verify employee identity",
            1,
            null, List.of(), List.of(),
            new Action(ActionType.SYSTEM_INVOCATION, "verify-handler", "Verify", null),
            null, null
        );
        return new ProcessGraph(
            new ProcessGraph.ProcessGraphId("employee-onboarding"),
            "Employee Onboarding",
            "Employee onboarding process",
            1,
            ProcessGraph.ProcessGraphStatus.PUBLISHED,
            List.of(node),
            List.of(),
            List.of(),
            List.of(),
            null
        );
    }

    private ProcessGraph createTestProcessGraphWithEdges() {
        Node node1 = new Node(
            new Node.NodeId("node-1"),
            "Verify Identity",
            "Verify employee identity",
            1,
            null, List.of(), List.of(),
            new Action(ActionType.SYSTEM_INVOCATION, "verify-handler", "Verify", null),
            null, null
        );
        Node node2 = new Node(
            new Node.NodeId("node-2"),
            "Setup Account",
            "Setup employee account",
            1,
            null, List.of(), List.of(),
            new Action(ActionType.SYSTEM_INVOCATION, "setup-handler", "Setup", null),
            null, null
        );
        Edge edge = new Edge(
            new Edge.EdgeId("edge-1"),
            "Verify to Setup",
            "Transition from verify to setup",
            new Node.NodeId("node-1"),
            new Node.NodeId("node-2"),
            Edge.GuardConditions.alwaysTrue(),
            Edge.ExecutionSemantics.sequential(),
            Edge.Priority.defaults(),
            Edge.EventTriggers.none(),
            Edge.CompensationSemantics.none()
        );
        return new ProcessGraph(
            new ProcessGraph.ProcessGraphId("employee-onboarding"),
            "Employee Onboarding",
            "Employee onboarding process",
            1,
            ProcessGraph.ProcessGraphStatus.PUBLISHED,
            List.of(node1, node2),
            List.of(edge),
            List.of(),
            List.of(),
            null
        );
    }
}
