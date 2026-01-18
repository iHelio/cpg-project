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

package com.ihelio.cpg.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ihelio.cpg.application.handler.ActionHandlerRegistry;
import com.ihelio.cpg.application.onboarding.OnboardingProcessGraphBuilder;
import com.ihelio.cpg.domain.action.ActionContext;
import com.ihelio.cpg.domain.action.ActionHandler;
import com.ihelio.cpg.domain.action.ActionResult;
import com.ihelio.cpg.domain.engine.CompensationHandler;
import com.ihelio.cpg.domain.engine.EdgeEvaluator;
import com.ihelio.cpg.domain.engine.EdgeTraversal;
import com.ihelio.cpg.domain.engine.ExecutionCoordinator;
import com.ihelio.cpg.domain.engine.NodeEvaluator;
import com.ihelio.cpg.domain.engine.NodeExecutionResult;
import com.ihelio.cpg.domain.engine.ProcessExecutionEngine;
import com.ihelio.cpg.domain.event.ProcessEvent;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.policy.PolicyEvaluator;
import com.ihelio.cpg.domain.policy.PolicyResult;
import com.ihelio.cpg.domain.rule.RuleEvaluator;
import com.ihelio.cpg.domain.rule.RuleResult;
import com.ihelio.cpg.infrastructure.event.InMemoryEventPublisher;
import com.ihelio.cpg.infrastructure.feel.KieFeelExpressionEvaluator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test for the employee onboarding process execution.
 *
 * <p>Tests the full execution flow using the OnboardingProcessGraphBuilder
 * to create the process graph and the ProcessExecutionEngine to execute it.
 */
@DisplayName("Onboarding Process Execution Integration")
class OnboardingProcessExecutionTest {

    private ProcessExecutionEngine engine;
    private InMemoryEventPublisher eventPublisher;
    private ActionHandlerRegistry actionRegistry;
    private ProcessGraph onboardingGraph;

    @BeforeEach
    void setUp() {
        // Build the onboarding process graph
        onboardingGraph = OnboardingProcessGraphBuilder.build();

        // Create infrastructure components
        KieFeelExpressionEvaluator expressionEvaluator = new KieFeelExpressionEvaluator();
        eventPublisher = new InMemoryEventPublisher();

        // Create stub policy evaluator (passes all policies)
        PolicyEvaluator policyEvaluator = new PolicyEvaluator() {
            @Override
            public PolicyResult evaluate(Node.PolicyGate policyGate, Map<String, Object> context) {
                return PolicyResult.passed(policyGate, Map.of());
            }

            @Override
            public List<PolicyResult> evaluateAll(List<Node.PolicyGate> gates, Map<String, Object> ctx) {
                return gates.stream()
                    .map(g -> PolicyResult.passed(g, Map.of()))
                    .toList();
            }
        };

        // Create stub rule evaluator (returns empty outputs)
        RuleEvaluator ruleEvaluator = new RuleEvaluator() {
            @Override
            public RuleResult evaluate(Node.BusinessRule rule, Map<String, Object> context) {
                return RuleResult.success(rule, Map.of());
            }

            @Override
            public List<RuleResult> evaluateAll(List<Node.BusinessRule> rules, Map<String, Object> ctx) {
                return rules.stream()
                    .map(r -> RuleResult.success(r, Map.of()))
                    .toList();
            }
        };

        // Create engine components
        NodeEvaluator nodeEvaluator = new NodeEvaluator(
            expressionEvaluator, policyEvaluator, ruleEvaluator);
        EdgeEvaluator edgeEvaluator = new EdgeEvaluator(expressionEvaluator);
        ExecutionCoordinator coordinator = new ExecutionCoordinator();
        CompensationHandler compensationHandler = new CompensationHandler();

        // Create action handler registry with a default handler
        actionRegistry = new ActionHandlerRegistry();
        actionRegistry.register(new TestActionHandler(Node.ActionType.SYSTEM_INVOCATION));
        actionRegistry.register(new TestActionHandler(Node.ActionType.HUMAN_TASK));
        actionRegistry.register(new TestActionHandler(Node.ActionType.NOTIFICATION));
        actionRegistry.register(new TestActionHandler(Node.ActionType.WAIT));

        // Create the execution engine
        engine = new ProcessExecutionEngine(
            nodeEvaluator,
            edgeEvaluator,
            coordinator,
            compensationHandler,
            eventPublisher,
            actionRegistry.asResolver()
        );
    }

    @Test
    @DisplayName("should create valid onboarding process graph")
    void shouldCreateValidOnboardingGraph() {
        assertThat(onboardingGraph).isNotNull();
        assertThat(onboardingGraph.validate()).isEmpty();
        assertThat(onboardingGraph.entryNodeIds()).isNotEmpty();
        assertThat(onboardingGraph.terminalNodeIds()).isNotEmpty();
    }

    @Test
    @DisplayName("should start onboarding process with initial context")
    void shouldStartOnboardingProcess() {
        ExecutionContext context = createOnboardingContext();

        ProcessInstance instance = engine.startProcess(onboardingGraph, context);

        assertThat(instance).isNotNull();
        assertThat(instance.isRunning()).isTrue();
        assertThat(instance.processGraphId()).isEqualTo(onboardingGraph.id());

        // Verify process started event was published
        List<ProcessEvent> events = eventPublisher.getEventsOfType("process.started");
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("should execute entry node successfully")
    void shouldExecuteEntryNode() {
        ExecutionContext context = createOnboardingContext();
        ProcessInstance instance = engine.startProcess(onboardingGraph, context);

        // Get the first entry node
        Node.NodeId entryNodeId = onboardingGraph.entryNodeIds().get(0);
        Node entryNode = onboardingGraph.findNode(entryNodeId).orElseThrow();

        // Execute the entry node
        NodeExecutionResult result = engine.executeNode(instance, onboardingGraph, entryNode);

        assertThat(result.isSuccess()).isTrue();

        // Verify node executed event was published
        List<ProcessEvent> events = eventPublisher.getEventsOfType("node.executed");
        assertThat(events).isNotEmpty();
    }

    @Test
    @DisplayName("should evaluate outbound edges after node execution")
    void shouldEvaluateOutboundEdges() {
        ExecutionContext context = createOnboardingContext();
        ProcessInstance instance = engine.startProcess(onboardingGraph, context);

        // Get and execute the entry node
        Node.NodeId entryNodeId = onboardingGraph.entryNodeIds().get(0);
        Node entryNode = onboardingGraph.findNode(entryNodeId).orElseThrow();
        engine.executeNode(instance, onboardingGraph, entryNode);

        // Evaluate outbound edges
        List<EdgeTraversal> traversals = engine.evaluateAndTraverseEdges(
            instance, onboardingGraph, entryNode);

        // Entry node should have outbound edges
        assertThat(onboardingGraph.getOutboundEdges(entryNodeId)).isNotEmpty();
    }

    @Test
    @DisplayName("should suspend and resume process")
    void shouldSuspendAndResumeProcess() {
        ExecutionContext context = createOnboardingContext();
        ProcessInstance instance = engine.startProcess(onboardingGraph, context);

        // Suspend
        engine.suspendProcess(instance);
        assertThat(instance.status())
            .isEqualTo(ProcessInstance.ProcessInstanceStatus.SUSPENDED);

        // Resume
        engine.resumeProcess(instance, onboardingGraph);
        assertThat(instance.status())
            .isEqualTo(ProcessInstance.ProcessInstanceStatus.RUNNING);
    }

    @Test
    @DisplayName("should handle external events")
    void shouldHandleExternalEvents() {
        ExecutionContext context = createOnboardingContext();
        ProcessInstance instance = engine.startProcess(onboardingGraph, context);

        // Create an external event
        ProcessEvent externalEvent = ProcessEvent.of(
            "background.check.completed",
            ProcessEvent.EventSource.external("background-check-system"),
            instance.id().value(),
            Map.of(
                "employeeId", "emp-123",
                "status", "PASSED",
                "completedAt", "2026-01-17T10:00:00Z"
            )
        );

        // Handle the event
        List<ProcessInstance> affected = engine.handleEvent(
            externalEvent,
            List.of(instance),
            onboardingGraph
        );

        // Instance should have the event in its context
        assertThat(instance.context().hasReceivedEvent("background.check.completed")).isTrue();
    }

    private ExecutionContext createOnboardingContext() {
        return ExecutionContext.builder()
            .clientContext(Map.of(
                "tenantId", "acme-corp",
                "region", "US-WEST"
            ))
            .domainContext(Map.of(
                "employee", Map.of(
                    "id", "emp-123",
                    "name", "Jane Doe",
                    "email", "jane.doe@example.com",
                    "department", "Engineering",
                    "startDate", "2026-02-01",
                    "location", "US-CA"
                ),
                "offer", Map.of(
                    "id", "offer-456",
                    "position", "Software Engineer",
                    "salary", 150000,
                    "signedAt", "2026-01-10"
                )
            ))
            .build();
    }

    /**
     * Test action handler that returns success for all actions.
     */
    private static class TestActionHandler implements ActionHandler {

        private final Node.ActionType supportedType;

        TestActionHandler(Node.ActionType type) {
            this.supportedType = type;
        }

        @Override
        public Node.ActionType supportedType() {
            return supportedType;
        }

        @Override
        public boolean canHandle(String handlerRef) {
            return true;
        }

        @Override
        public ActionResult execute(ActionContext context) {
            return ActionResult.success(Map.of(
                "handledBy", "test-handler",
                "nodeId", context.node().id().value(),
                "actionType", context.action().type().name()
            ));
        }
    }
}
