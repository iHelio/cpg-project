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
@DisplayName("EdgeEvaluator")
class EdgeEvaluatorTest {

    @Mock
    private ExpressionEvaluator expressionEvaluator;

    private EdgeEvaluator edgeEvaluator;

    @BeforeEach
    void setUp() {
        edgeEvaluator = new EdgeEvaluator(expressionEvaluator);
    }

    private Edge createTestEdge(String id, Edge.GuardConditions guards) {
        return new Edge(
            new Edge.EdgeId(id),
            "Test Edge",
            "Test description",
            new Node.NodeId("source-node"),
            new Node.NodeId("target-node"),
            guards,
            Edge.ExecutionSemantics.sequential(),
            Edge.Priority.defaults(),
            Edge.EventTriggers.none(),
            Edge.CompensationSemantics.none()
        );
    }

    private ExecutionContext createContext() {
        return ExecutionContext.builder()
            .addClientContext("tenantId", "test-tenant")
            .addDomainContext("status", "APPROVED")
            .build();
    }

    @Nested
    @DisplayName("when evaluating edge with no conditions")
    class NoConditions {

        @Test
        @DisplayName("should return traversable when no guards exist")
        void shouldReturnTraversableWhenNoGuards() {
            Edge edge = createTestEdge("edge-1", Edge.GuardConditions.alwaysTrue());
            ExecutionContext context = createContext();

            EdgeEvaluation result = edgeEvaluator.evaluate(edge, context, Map.of(), Map.of());

            assertThat(result.traversable()).isTrue();
            assertThat(result.isBlocked()).isFalse();
        }
    }

    @Nested
    @DisplayName("when evaluating context conditions")
    class ContextConditions {

        @Test
        @DisplayName("should return traversable when context conditions pass")
        void shouldReturnTraversableWhenContextConditionsPass() {
            FeelExpression condition = FeelExpression.of("status = \"APPROVED\"");
            Edge.GuardConditions guards = Edge.GuardConditions.ofContext(List.of(condition));
            Edge edge = createTestEdge("edge-1", guards);
            ExecutionContext context = createContext();

            when(expressionEvaluator.evaluateAllAsBoolean(anyList(), any())).thenReturn(true);

            EdgeEvaluation result = edgeEvaluator.evaluate(edge, context, Map.of(), Map.of());

            assertThat(result.traversable()).isTrue();
        }

        @Test
        @DisplayName("should return blocked when context conditions fail")
        void shouldReturnBlockedWhenContextConditionsFail() {
            FeelExpression condition = FeelExpression.of("status = \"REJECTED\"");
            Edge.GuardConditions guards = Edge.GuardConditions.ofContext(List.of(condition));
            Edge edge = createTestEdge("edge-1", guards);
            ExecutionContext context = createContext();

            when(expressionEvaluator.evaluateAllAsBoolean(anyList(), any())).thenReturn(false);

            EdgeEvaluation result = edgeEvaluator.evaluate(edge, context, Map.of(), Map.of());

            assertThat(result.traversable()).isFalse();
            assertThat(result.blockedReason()).contains("Context guard conditions");
        }
    }

    @Nested
    @DisplayName("when evaluating policy outcome conditions")
    class PolicyOutcomeConditions {

        @Test
        @DisplayName("should return traversable when policy outcome matches")
        void shouldReturnTraversableWhenPolicyOutcomeMatches() {
            Edge.PolicyOutcomeCondition policyCondition = new Edge.PolicyOutcomeCondition(
                "compliance-check",
                Edge.PolicyOutcome.PASSED
            );
            Edge.GuardConditions guards = new Edge.GuardConditions(
                List.of(),
                List.of(),
                List.of(policyCondition),
                List.of()
            );
            Edge edge = createTestEdge("edge-1", guards);
            ExecutionContext context = createContext();

            Map<String, Edge.PolicyOutcome> policyOutcomes = Map.of(
                "compliance-check", Edge.PolicyOutcome.PASSED
            );

            when(expressionEvaluator.evaluateAllAsBoolean(anyList(), any())).thenReturn(true);

            EdgeEvaluation result = edgeEvaluator.evaluate(edge, context, Map.of(), policyOutcomes);

            assertThat(result.traversable()).isTrue();
        }

        @Test
        @DisplayName("should return blocked when policy outcome does not match")
        void shouldReturnBlockedWhenPolicyOutcomeDoesNotMatch() {
            Edge.PolicyOutcomeCondition policyCondition = new Edge.PolicyOutcomeCondition(
                "compliance-check",
                Edge.PolicyOutcome.PASSED
            );
            Edge.GuardConditions guards = new Edge.GuardConditions(
                List.of(),
                List.of(),
                List.of(policyCondition),
                List.of()
            );
            Edge edge = createTestEdge("edge-1", guards);
            ExecutionContext context = createContext();

            Map<String, Edge.PolicyOutcome> policyOutcomes = Map.of(
                "compliance-check", Edge.PolicyOutcome.FAILED
            );

            when(expressionEvaluator.evaluateAllAsBoolean(anyList(), any())).thenReturn(true);

            EdgeEvaluation result = edgeEvaluator.evaluate(edge, context, Map.of(), policyOutcomes);

            assertThat(result.traversable()).isFalse();
            assertThat(result.blockedReason()).contains("Policy outcome");
        }
    }

    @Nested
    @DisplayName("when evaluating event conditions")
    class EventConditions {

        @Test
        @DisplayName("should return traversable when required event has occurred")
        void shouldReturnTraversableWhenEventOccurred() {
            Edge.EventCondition eventCondition = Edge.EventCondition.occurred("document.uploaded");
            Edge.GuardConditions guards = new Edge.GuardConditions(
                List.of(),
                List.of(),
                List.of(),
                List.of(eventCondition)
            );
            Edge edge = createTestEdge("edge-1", guards);

            ExecutionContext context = ExecutionContext.builder()
                .eventHistory(List.of(
                    new ExecutionContext.ReceivedEvent(
                        "document.uploaded",
                        "evt-1",
                        java.time.Instant.now(),
                        Map.of()
                    )
                ))
                .build();

            when(expressionEvaluator.evaluateAllAsBoolean(anyList(), any())).thenReturn(true);

            EdgeEvaluation result = edgeEvaluator.evaluate(edge, context, Map.of(), Map.of());

            assertThat(result.traversable()).isTrue();
        }

        @Test
        @DisplayName("should return blocked when required event has not occurred")
        void shouldReturnBlockedWhenEventNotOccurred() {
            Edge.EventCondition eventCondition = Edge.EventCondition.occurred("document.uploaded");
            Edge.GuardConditions guards = new Edge.GuardConditions(
                List.of(),
                List.of(),
                List.of(),
                List.of(eventCondition)
            );
            Edge edge = createTestEdge("edge-1", guards);
            ExecutionContext context = createContext();

            when(expressionEvaluator.evaluateAllAsBoolean(anyList(), any())).thenReturn(true);

            EdgeEvaluation result = edgeEvaluator.evaluate(edge, context, Map.of(), Map.of());

            assertThat(result.traversable()).isFalse();
            assertThat(result.blockedReason()).contains("Event conditions");
        }
    }

    @Nested
    @DisplayName("when selecting edges to traverse")
    class EdgeSelection {

        @Test
        @DisplayName("should select exclusive edge only when it matches")
        void shouldSelectExclusiveEdgeOnly() {
            Edge regularEdge = new Edge(
                new Edge.EdgeId("edge-1"),
                "Regular Edge",
                null,
                new Node.NodeId("source"),
                new Node.NodeId("target-1"),
                Edge.GuardConditions.alwaysTrue(),
                Edge.ExecutionSemantics.sequential(),
                Edge.Priority.defaults(),
                Edge.EventTriggers.none(),
                Edge.CompensationSemantics.none()
            );

            Edge exclusiveEdge = new Edge(
                new Edge.EdgeId("edge-2"),
                "Exclusive Edge",
                null,
                new Node.NodeId("source"),
                new Node.NodeId("target-2"),
                Edge.GuardConditions.alwaysTrue(),
                Edge.ExecutionSemantics.sequential(),
                Edge.Priority.exclusive(1000),
                Edge.EventTriggers.none(),
                Edge.CompensationSemantics.none()
            );

            List<EdgeEvaluation> evaluations = List.of(
                EdgeEvaluation.traversable(regularEdge),
                EdgeEvaluation.traversable(exclusiveEdge)
            );

            List<Edge> selected = edgeEvaluator.selectEdgesToTraverse(evaluations);

            assertThat(selected).hasSize(1);
            assertThat(selected.get(0).id().value()).isEqualTo("edge-2");
        }
    }
}
