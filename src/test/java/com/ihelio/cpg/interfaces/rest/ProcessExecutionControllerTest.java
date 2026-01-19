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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ihelio.cpg.application.service.ProcessExecutionService;
import com.ihelio.cpg.domain.action.ActionResult;
import com.ihelio.cpg.domain.engine.EdgeTraversal;
import com.ihelio.cpg.domain.engine.NodeExecutionResult;
import com.ihelio.cpg.domain.exception.ProcessExecutionException;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Node;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProcessExecutionController.class)
@DisplayName("ProcessExecutionController")
class ProcessExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProcessExecutionService processExecutionService;

    @Test
    @DisplayName("POST /api/v1/process-instances/{id}/execute should execute node")
    void executeNodeShouldExecuteNode() throws Exception {
        Node node = createTestNode();
        NodeExecutionResult result = NodeExecutionResult.success(
            node,
            ActionResult.success(Map.of("output", "value")),
            ExecutionContext.builder().build()
        );
        when(processExecutionService.executeNode(any(), any())).thenReturn(result);

        String requestBody = """
            {
                "nodeId": "collect-documents",
                "additionalContext": {}
            }
            """;

        mockMvc.perform(post("/api/v1/process-instances/inst-123/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodeId").value("node-1"))
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("POST /api/v1/process-instances/{id}/execute should return 404 for unknown node")
    void executeNodeShouldReturn404ForUnknownNode() throws Exception {
        when(processExecutionService.executeNode(any(), any()))
            .thenThrow(ProcessExecutionException.withContext(
                "Node not found",
                "inst-123",
                "unknown-node",
                ProcessExecutionException.ErrorType.NODE_NOT_FOUND,
                false
            ));

        String requestBody = """
            {
                "nodeId": "unknown-node",
                "additionalContext": {}
            }
            """;

        mockMvc.perform(post("/api/v1/process-instances/inst-123/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorType").value("NODE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/process-instances/{id}/available-nodes should return nodes")
    void getAvailableNodesShouldReturnNodes() throws Exception {
        Node node = createTestNode();
        when(processExecutionService.getAvailableNodes("inst-123"))
            .thenReturn(List.of(node));

        mockMvc.perform(get("/api/v1/process-instances/inst-123/available-nodes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].id").value("node-1"))
            .andExpect(jsonPath("$[0].name").value("Test Node"));
    }

    @Test
    @DisplayName("GET /api/v1/process-instances/{id}/nodes/{nodeId}/transitions should return transitions")
    void getAvailableTransitionsShouldReturnTransitions() throws Exception {
        Node sourceNode = createTestNode();
        Node targetNode = new Node(
            new Node.NodeId("node-2"),
            "Target Node",
            null,
            1,
            Node.Preconditions.none(),
            List.of(),
            List.of(),
            new Node.Action(Node.ActionType.SYSTEM_INVOCATION, "handler", "Test", null),
            Node.EventConfig.none(),
            Node.ExceptionRoutes.none()
        );
        Edge edge = new Edge(
            new Edge.EdgeId("edge-1"),
            "Test Edge",
            null,
            sourceNode.id(),
            targetNode.id(),
            Edge.GuardConditions.alwaysTrue(),
            Edge.ExecutionSemantics.sequential(),
            Edge.Priority.defaults(),
            Edge.EventTriggers.none(),
            Edge.CompensationSemantics.none()
        );
        EdgeTraversal traversal = EdgeTraversal.of(edge, sourceNode, targetNode);

        when(processExecutionService.getAvailableTransitions("inst-123", "node-1"))
            .thenReturn(List.of(traversal));

        mockMvc.perform(get("/api/v1/process-instances/inst-123/nodes/node-1/transitions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].edgeId").value("edge-1"))
            .andExpect(jsonPath("$[0].targetNodeId").value("node-2"));
    }

    @Test
    @DisplayName("GET /api/v1/process-instances/{id}/context should return context")
    void getContextShouldReturnContext() throws Exception {
        ExecutionContext context = ExecutionContext.builder()
            .addClientContext("tenantId", "acme")
            .addDomainContext("employeeId", "emp-123")
            .build();
        when(processExecutionService.getContext("inst-123")).thenReturn(context);

        mockMvc.perform(get("/api/v1/process-instances/inst-123/context"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clientContext.tenantId").value("acme"))
            .andExpect(jsonPath("$.domainContext.employeeId").value("emp-123"));
    }

    @Test
    @DisplayName("PUT /api/v1/process-instances/{id}/context should update context")
    void updateContextShouldUpdateContext() throws Exception {
        ExecutionContext context = ExecutionContext.builder()
            .addClientContext("newKey", "newValue")
            .build();
        when(processExecutionService.updateContext(any(), any())).thenReturn(context);

        String requestBody = """
            {
                "clientContext": {"newKey": "newValue"},
                "domainContext": {},
                "accumulatedState": {}
            }
            """;

        mockMvc.perform(put("/api/v1/process-instances/inst-123/context")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clientContext.newKey").value("newValue"));
    }

    private Node createTestNode() {
        return new Node(
            new Node.NodeId("node-1"),
            "Test Node",
            null,
            1,
            Node.Preconditions.none(),
            List.of(),
            List.of(),
            new Node.Action(Node.ActionType.SYSTEM_INVOCATION, "test-handler", "Test action", null),
            Node.EventConfig.none(),
            Node.ExceptionRoutes.none()
        );
    }
}
