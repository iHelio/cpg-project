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

package com.ihelio.cpg.infrastructure.orchestration;

import static org.junit.jupiter.api.Assertions.*;

import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.orchestration.GovernanceResult;
import com.ihelio.cpg.domain.orchestration.RuntimeContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExecutionGovernorTest {

    private DefaultExecutionGovernor governor;
    private OrchestratorConfigProperties config;

    @BeforeEach
    void setUp() {
        config = OrchestratorConfigProperties.defaults();
        governor = new DefaultExecutionGovernor(config);
        governor.clearRecordedExecutions();
    }

    @Test
    @DisplayName("Should pass idempotency check for new execution")
    void shouldPassIdempotencyCheckForNewExecution() {
        // Given
        Node node = createNode("node-1");
        ProcessInstance instance = createInstance();
        RuntimeContext context = RuntimeContext.empty();

        // When
        GovernanceResult.IdempotencyResult result = governor.checkIdempotency(instance, node, context);

        // Then
        assertTrue(result.passed());
        assertNotNull(result.idempotencyKey());
    }

    @Test
    @DisplayName("Should fail idempotency check for duplicate execution")
    void shouldFailIdempotencyCheckForDuplicateExecution() {
        // Given
        Node node = createNode("node-1");
        ProcessInstance instance = createInstance();
        RuntimeContext context = RuntimeContext.empty();

        // First execution
        governor.recordExecution(instance, node, context, "exec-1");

        // When - try to execute again
        GovernanceResult.IdempotencyResult result = governor.checkIdempotency(instance, node, context);

        // Then
        assertFalse(result.passed());
        assertEquals("exec-1", result.previousExecutionId());
    }

    @Test
    @DisplayName("Should skip idempotency check when disabled")
    void shouldSkipIdempotencyCheckWhenDisabled() {
        // Given
        OrchestratorConfigProperties disabledConfig = OrchestratorConfigProperties.builder()
            .idempotencyEnabled(false)
            .build();
        DefaultExecutionGovernor disabledGovernor = new DefaultExecutionGovernor(disabledConfig);

        Node node = createNode("node-1");
        ProcessInstance instance = createInstance();
        RuntimeContext context = RuntimeContext.empty();

        // When
        GovernanceResult.IdempotencyResult result = disabledGovernor.checkIdempotency(instance, node, context);

        // Then
        assertTrue(result.passed());
        assertEquals("SKIPPED", result.idempotencyKey());
    }

    @Test
    @DisplayName("Should pass authorization for SYSTEM principal")
    void shouldPassAuthorizationForSystemPrincipal() {
        // Given
        Node node = createNode("node-1");
        ProcessInstance instance = createInstance();
        RuntimeContext context = RuntimeContext.empty(); // No principal = SYSTEM

        // When
        GovernanceResult.AuthorizationResult result = governor.checkAuthorization(instance, node, context);

        // Then
        assertTrue(result.passed());
        assertEquals("SYSTEM", result.principal());
    }

    @Test
    @DisplayName("Should skip authorization check when disabled")
    void shouldSkipAuthorizationCheckWhenDisabled() {
        // Given
        OrchestratorConfigProperties disabledConfig = OrchestratorConfigProperties.builder()
            .authorizationEnabled(false)
            .build();
        DefaultExecutionGovernor disabledGovernor = new DefaultExecutionGovernor(disabledConfig);

        Node node = createNode("node-1");
        ProcessInstance instance = createInstance();
        RuntimeContext context = RuntimeContext.empty();

        // When
        GovernanceResult.AuthorizationResult result = disabledGovernor.checkAuthorization(instance, node, context);

        // Then
        assertTrue(result.passed());
        assertEquals("SYSTEM", result.principal());
        assertEquals("Authorization check disabled", result.reason());
    }

    @Test
    @DisplayName("Should pass policy gate for normal system state")
    void shouldPassPolicyGateForNormalSystemState() {
        // Given
        Node node = createNode("node-1");
        ProcessInstance instance = createInstance();
        RuntimeContext context = RuntimeContext.empty();

        // When
        GovernanceResult.PolicyGateResult result = governor.checkPolicyGate(instance, node, context);

        // Then
        assertTrue(result.passed());
    }

    @Test
    @DisplayName("Should fail policy gate for emergency system state")
    void shouldFailPolicyGateForEmergencySystemState() {
        // Given
        Node node = createNode("node-1");
        ProcessInstance instance = createInstance();
        RuntimeContext context = RuntimeContext.builder()
            .operationalContext(new RuntimeContext.OperationalContext(
                RuntimeContext.SystemState.EMERGENCY,
                List.of()
            ))
            .build();

        // When
        GovernanceResult.PolicyGateResult result = governor.checkPolicyGate(instance, node, context);

        // Then
        assertFalse(result.passed());
        assertTrue(result.reason().contains("emergency"));
    }

    @Test
    @DisplayName("Should combine all governance checks")
    void shouldCombineAllGovernanceChecks() {
        // Given
        Node node = createNode("node-1");
        ProcessInstance instance = createInstance();
        RuntimeContext context = RuntimeContext.empty();

        // When
        GovernanceResult result = governor.enforce(instance, node, context);

        // Then
        assertTrue(result.approved());
        assertTrue(result.idempotencyResult().passed());
        assertTrue(result.authorizationResult().passed());
        assertTrue(result.policyGateResult().passed());
    }

    @Test
    @DisplayName("Should reject when any governance check fails")
    void shouldRejectWhenAnyGovernanceCheckFails() {
        // Given
        Node node = createNode("node-1");
        ProcessInstance instance = createInstance();
        RuntimeContext context = RuntimeContext.builder()
            .operationalContext(new RuntimeContext.OperationalContext(
                RuntimeContext.SystemState.EMERGENCY,
                List.of()
            ))
            .build();

        // When
        GovernanceResult result = governor.enforce(instance, node, context);

        // Then
        assertFalse(result.approved());
        assertNotNull(result.rejectionReason());
        assertTrue(result.rejectionReason().contains("Policy gate check failed"));
    }

    private Node createNode(String id) {
        return new Node(
            new Node.NodeId(id),
            "Test Node",
            "Description",
            1,
            Node.Preconditions.none(),
            List.of(),
            List.of(),
            new Node.Action(Node.ActionType.SYSTEM_INVOCATION, "testHandler", "Test action",
                Node.ActionConfig.defaults()),
            Node.EventConfig.none(),
            Node.ExceptionRoutes.none()
        );
    }

    private ProcessInstance createInstance() {
        return ProcessInstance.builder()
            .id(UUID.randomUUID().toString())
            .processGraphId(new ProcessGraph.ProcessGraphId("test-graph"))
            .processGraphVersion(1)
            .context(ExecutionContext.builder().build())
            .build();
    }
}
