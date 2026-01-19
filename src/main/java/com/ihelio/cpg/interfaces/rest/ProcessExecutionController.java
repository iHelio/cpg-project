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

import com.ihelio.cpg.application.service.ProcessExecutionService;
import com.ihelio.cpg.domain.engine.EdgeTraversal;
import com.ihelio.cpg.domain.engine.NodeExecutionResult;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.interfaces.rest.dto.request.ExecuteNodeRequest;
import com.ihelio.cpg.interfaces.rest.dto.request.UpdateContextRequest;
import com.ihelio.cpg.interfaces.rest.dto.response.EdgeTraversalResponse;
import com.ihelio.cpg.interfaces.rest.dto.response.ExecutionContextResponse;
import com.ihelio.cpg.interfaces.rest.dto.response.NodeExecutionResultResponse;
import com.ihelio.cpg.interfaces.rest.dto.response.NodeResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for process execution operations.
 */
@RestController
@RequestMapping("/api/v1/process-instances/{instanceId}")
@Validated
public class ProcessExecutionController {

    private final ProcessExecutionService processExecutionService;

    public ProcessExecutionController(ProcessExecutionService processExecutionService) {
        this.processExecutionService = processExecutionService;
    }

    /**
     * Executes a node within a process instance.
     *
     * @param instanceId the instance ID
     * @param request the execution request
     * @return the execution result
     */
    @PostMapping("/execute")
    public ResponseEntity<NodeExecutionResultResponse> executeNode(
            @PathVariable String instanceId,
            @Valid @RequestBody ExecuteNodeRequest request) {

        NodeExecutionResult result = processExecutionService.executeNode(instanceId, request);
        return ResponseEntity.ok(NodeExecutionResultResponse.from(result));
    }

    /**
     * Gets all available (executable) nodes for a process instance.
     *
     * @param instanceId the instance ID
     * @return list of available nodes
     */
    @GetMapping("/available-nodes")
    public ResponseEntity<List<NodeResponse>> getAvailableNodes(
            @PathVariable String instanceId) {

        List<Node> availableNodes = processExecutionService.getAvailableNodes(instanceId);

        List<NodeResponse> response = availableNodes.stream()
            .map(NodeResponse::from)
            .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Gets available transitions from a specific node.
     *
     * @param instanceId the instance ID
     * @param nodeId the source node ID
     * @return list of available edge traversals
     */
    @GetMapping("/nodes/{nodeId}/transitions")
    public ResponseEntity<List<EdgeTraversalResponse>> getAvailableTransitions(
            @PathVariable String instanceId,
            @PathVariable String nodeId) {

        List<EdgeTraversal> transitions =
            processExecutionService.getAvailableTransitions(instanceId, nodeId);

        List<EdgeTraversalResponse> response = transitions.stream()
            .map(EdgeTraversalResponse::from)
            .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Gets the execution context of a process instance.
     *
     * @param instanceId the instance ID
     * @return the execution context
     */
    @GetMapping("/context")
    public ResponseEntity<ExecutionContextResponse> getContext(
            @PathVariable String instanceId) {

        ExecutionContext context = processExecutionService.getContext(instanceId);
        return ResponseEntity.ok(ExecutionContextResponse.from(context));
    }

    /**
     * Updates the execution context of a process instance.
     *
     * @param instanceId the instance ID
     * @param request the update request
     * @return the updated context
     */
    @PutMapping("/context")
    public ResponseEntity<ExecutionContextResponse> updateContext(
            @PathVariable String instanceId,
            @Valid @RequestBody UpdateContextRequest request) {

        ExecutionContext context = processExecutionService.updateContext(instanceId, request);
        return ResponseEntity.ok(ExecutionContextResponse.from(context));
    }
}
