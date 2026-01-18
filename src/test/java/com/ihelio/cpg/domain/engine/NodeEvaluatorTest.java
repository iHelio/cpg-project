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

package com.ihelio.cpg.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.expression.EvaluationResult;
import com.ihelio.cpg.domain.expression.ExpressionEvaluator;
import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.FeelExpression;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.policy.PolicyEvaluator;
import com.ihelio.cpg.domain.policy.PolicyResult;
import com.ihelio.cpg.domain.rule.RuleEvaluator;
import com.ihelio.cpg.domain.rule.RuleResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NodeEvaluator")
class NodeEvaluatorTest {

    @Mock
    private ExpressionEvaluator expressionEvaluator;

    @Mock
    private PolicyEvaluator policyEvaluator;

    @Mock
    private RuleEvaluator ruleEvaluator;

    private NodeEvaluator nodeEvaluator;

    @BeforeEach
    void setUp() {
        nodeEvaluator = new NodeEvaluator(expressionEvaluator, policyEvaluator, ruleEvaluator);
    }

    private Node createTestNode(String id, Node.Preconditions preconditions,
                                 List<Node.PolicyGate> policyGates,
                                 List<Node.BusinessRule> businessRules) {
        return new Node(
            new Node.NodeId(id),
            "Test Node",
            "Test description",
            1,
            preconditions,
            policyGates,
            businessRules,
            new Node.Action(Node.ActionType.SYSTEM_INVOCATION, "test-handler", "Test action", null),
            Node.EventConfig.none(),
            Node.ExceptionRoutes.none()
        );
    }

    private ExecutionContext createContext() {
        return ExecutionContext.builder()
            .addClientContext("tenantId", "test-tenant")
            .addDomainContext("employeeId", "emp-123")
            .build();
    }

    @Nested
    @DisplayName("when evaluating node with no preconditions")
    class NoPreconditions {

        @Test
        @DisplayName("should return available when no preconditions exist")
        void shouldReturnAvailableWhenNoPreconditions() {
            Node node = createTestNode("node-1", Node.Preconditions.none(), List.of(), List.of());
            ExecutionContext context = createContext();

            when(expressionEvaluator.evaluateAllAsBoolean(anyList(), any())).thenReturn(true);
            when(policyEvaluator.evaluateAll(anyList(), any())).thenReturn(List.of());
            when(ruleEvaluator.evaluateAll(anyList(), any())).thenReturn(List.of());

            NodeEvaluation result = nodeEvaluator.evaluate(node, context);

            assertThat(result.available()).isTrue();
            assertThat(result.preconditionsPassed()).isTrue();
            assertThat(result.policiesPassed()).isTrue();
        }
    }

    @Nested
    @DisplayName("when evaluating preconditions")
    class PreconditionEvaluation {

        @Test
        @DisplayName("should return available when all preconditions pass")
        void shouldReturnAvailableWhenPreconditionsPass() {
            FeelExpression condition = FeelExpression.of("tenantId = \"test-tenant\"");
            Node.Preconditions preconditions = new Node.Preconditions(
                List.of(condition),
                List.of()
            );
            Node node = createTestNode("node-1", preconditions, List.of(), List.of());
            ExecutionContext context = createContext();

            when(expressionEvaluator.evaluateAllAsBoolean(anyList(), any())).thenReturn(true);
            when(policyEvaluator.evaluateAll(anyList(), any())).thenReturn(List.of());
            when(ruleEvaluator.evaluateAll(anyList(), any())).thenReturn(List.of());

            NodeEvaluation result = nodeEvaluator.evaluate(node, context);

            assertThat(result.available()).isTrue();
            assertThat(result.preconditionsPassed()).isTrue();
        }

        @Test
        @DisplayName("should return blocked when preconditions fail")
        void shouldReturnBlockedWhenPreconditionsFail() {
            FeelExpression condition = FeelExpression.of("tenantId = \"other-tenant\"");
            Node.Preconditions preconditions = new Node.Preconditions(
                List.of(condition),
                List.of()
            );
            Node node = createTestNode("node-1", preconditions, List.of(), List.of());
            ExecutionContext context = createContext();

            when(expressionEvaluator.evaluateAllAsBoolean(anyList(), any())).thenReturn(false);

            NodeEvaluation result = nodeEvaluator.evaluate(node, context);

            assertThat(result.available()).isFalse();
            assertThat(result.preconditionsPassed()).isFalse();
            assertThat(result.blockedReason()).contains("preconditions");
        }
    }

    @Nested
    @DisplayName("when evaluating policy gates")
    class PolicyEvaluation {

        @Test
        @DisplayName("should return blocked when policy fails")
        void shouldReturnBlockedWhenPolicyFails() {
            Node.PolicyGate policyGate = new Node.PolicyGate(
                "policy-1",
                "Compliance Check",
                Node.PolicyType.COMPLIANCE,
                "ComplianceRules.eligibilityCheck",
                "PASSED"
            );
            Node node = createTestNode("node-1", Node.Preconditions.none(), List.of(policyGate), List.of());
            ExecutionContext context = createContext();

            PolicyResult failedPolicy = PolicyResult.failed(policyGate, "Not compliant");
            when(expressionEvaluator.evaluateAllAsBoolean(anyList(), any())).thenReturn(true);
            when(policyEvaluator.evaluateAll(anyList(), any())).thenReturn(List.of(failedPolicy));

            NodeEvaluation result = nodeEvaluator.evaluate(node, context);

            assertThat(result.available()).isFalse();
            assertThat(result.preconditionsPassed()).isTrue();
            assertThat(result.policiesPassed()).isFalse();
            assertThat(result.blockedReason()).contains("Policy blocked");
        }

        @Test
        @DisplayName("should return available when all policies pass")
        void shouldReturnAvailableWhenPoliciesPass() {
            Node.PolicyGate policyGate = new Node.PolicyGate(
                "policy-1",
                "Compliance Check",
                Node.PolicyType.COMPLIANCE,
                "ComplianceRules.eligibilityCheck",
                "PASSED"
            );
            Node node = createTestNode("node-1", Node.Preconditions.none(), List.of(policyGate), List.of());
            ExecutionContext context = createContext();

            PolicyResult passedPolicy = PolicyResult.passed(policyGate, Map.of());
            when(expressionEvaluator.evaluateAllAsBoolean(anyList(), any())).thenReturn(true);
            when(policyEvaluator.evaluateAll(anyList(), any())).thenReturn(List.of(passedPolicy));
            when(ruleEvaluator.evaluateAll(anyList(), any())).thenReturn(List.of());

            NodeEvaluation result = nodeEvaluator.evaluate(node, context);

            assertThat(result.available()).isTrue();
            assertThat(result.policiesPassed()).isTrue();
        }
    }

    @Nested
    @DisplayName("when evaluating business rules")
    class RuleEvaluation {

        @Test
        @DisplayName("should collect rule outputs")
        void shouldCollectRuleOutputs() {
            Node.BusinessRule rule = new Node.BusinessRule(
                "rule-1",
                "Calculate Deadline",
                "BusinessRules.deadlineCalculation",
                Node.RuleCategory.EXECUTION_PARAMETER
            );
            Node node = createTestNode("node-1", Node.Preconditions.none(), List.of(), List.of(rule));
            ExecutionContext context = createContext();

            RuleResult ruleResult = RuleResult.success(rule, Map.of("deadline", "2026-02-01"));
            when(expressionEvaluator.evaluateAllAsBoolean(anyList(), any())).thenReturn(true);
            when(policyEvaluator.evaluateAll(anyList(), any())).thenReturn(List.of());
            when(ruleEvaluator.evaluateAll(anyList(), any())).thenReturn(List.of(ruleResult));

            NodeEvaluation result = nodeEvaluator.evaluate(node, context);

            assertThat(result.available()).isTrue();
            assertThat(result.ruleOutputs()).containsEntry("deadline", "2026-02-01");
        }
    }
}
