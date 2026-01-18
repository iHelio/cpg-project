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

import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CompensationHandler")
class CompensationHandlerTest {

    private CompensationHandler compensationHandler;

    @BeforeEach
    void setUp() {
        compensationHandler = new CompensationHandler();
    }

    private ProcessInstance createInstance() {
        return ProcessInstance.builder()
            .id("instance-1")
            .processGraphId(new ProcessGraph.ProcessGraphId("graph-1"))
            .processGraphVersion(1)
            .context(ExecutionContext.builder().build())
            .build();
    }

    private ProcessGraph createGraph() {
        return new ProcessGraph(
            new ProcessGraph.ProcessGraphId("graph-1"),
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

    private Node createNode(String id, Node.ExceptionRoutes routes, int retryCount) {
        return new Node(
            new Node.NodeId(id),
            "Test Node",
            null,
            1,
            Node.Preconditions.none(),
            List.of(),
            List.of(),
            new Node.Action(
                Node.ActionType.SYSTEM_INVOCATION,
                "test-handler",
                null,
                new Node.ActionConfig(false, 300, retryCount, null, null)
            ),
            Node.EventConfig.none(),
            routes
        );
    }

    @Nested
    @DisplayName("when determining compensation for action retry")
    class ActionRetry {

        @Test
        @DisplayName("should return retry action when retries available")
        void shouldReturnRetryWhenRetriesAvailable() {
            ProcessInstance instance = createInstance();
            ProcessGraph graph = createGraph();
            Node node = createNode("node-1", Node.ExceptionRoutes.none(), 3);

            CompensationAction result = compensationHandler.determineCompensation(
                instance, graph, node, "ACTION_FAILED", "Connection timeout"
            );

            assertThat(result.action()).isEqualTo(CompensationAction.ActionType.RETRY);
            assertThat(result.currentRetryCount()).isEqualTo(0);
            assertThat(result.maxRetries()).isEqualTo(3);
        }

        @Test
        @DisplayName("should fail when max retries exceeded")
        void shouldFailWhenMaxRetriesExceeded() {
            ProcessInstance instance = createInstance();
            ProcessGraph graph = createGraph();
            Node node = createNode("node-1", Node.ExceptionRoutes.none(), 2);

            // Simulate exhausting retries
            compensationHandler.determineCompensation(
                instance, graph, node, "ACTION_FAILED", "Error 1");
            compensationHandler.determineCompensation(
                instance, graph, node, "ACTION_FAILED", "Error 2");

            CompensationAction result = compensationHandler.determineCompensation(
                instance, graph, node, "ACTION_FAILED", "Error 3"
            );

            assertThat(result.action()).isEqualTo(CompensationAction.ActionType.FAIL);
        }
    }

    @Nested
    @DisplayName("when determining compensation from exception routes")
    class ExceptionRoutes {

        @Test
        @DisplayName("should return alternate when remediation route specifies alternate")
        void shouldReturnAlternateForRemediationRoute() {
            Node.RemediationRoute route = new Node.RemediationRoute(
                "ValidationException",
                Node.RemediationStrategy.ALTERNATE,
                0,
                "manual-review-node"
            );
            Node.ExceptionRoutes routes = new Node.ExceptionRoutes(
                List.of(route),
                List.of()
            );

            ProcessInstance instance = createInstance();
            ProcessGraph graph = createGraph();
            Node node = createNode("node-1", routes, 0);

            CompensationAction result = compensationHandler.determineCompensation(
                instance, graph, node, "ValidationException", "Invalid data"
            );

            assertThat(result.action()).isEqualTo(CompensationAction.ActionType.ALTERNATE);
            assertThat(result.targetNodeId().value()).isEqualTo("manual-review-node");
        }

        @Test
        @DisplayName("should return skip when remediation route specifies skip")
        void shouldReturnSkipForRemediationRoute() {
            Node.RemediationRoute route = new Node.RemediationRoute(
                "OptionalStepException",
                Node.RemediationStrategy.SKIP,
                0,
                null
            );
            Node.ExceptionRoutes routes = new Node.ExceptionRoutes(
                List.of(route),
                List.of()
            );

            ProcessInstance instance = createInstance();
            ProcessGraph graph = createGraph();
            Node node = createNode("node-1", routes, 0);

            CompensationAction result = compensationHandler.determineCompensation(
                instance, graph, node, "OptionalStepException", "Optional step failed"
            );

            assertThat(result.action()).isEqualTo(CompensationAction.ActionType.SKIP);
        }

        @Test
        @DisplayName("should return escalate for escalation route")
        void shouldReturnEscalateForEscalationRoute() {
            Node.EscalationRoute route = new Node.EscalationRoute(
                "CriticalException",
                "escalation-node",
                null,
                60
            );
            Node.ExceptionRoutes routes = new Node.ExceptionRoutes(
                List.of(),
                List.of(route)
            );

            ProcessInstance instance = createInstance();
            ProcessGraph graph = createGraph();
            Node node = createNode("node-1", routes, 0);

            CompensationAction result = compensationHandler.determineCompensation(
                instance, graph, node, "CriticalException", "Critical error"
            );

            assertThat(result.action()).isEqualTo(CompensationAction.ActionType.ESCALATE);
            assertThat(result.escalationNodeId().value()).isEqualTo("escalation-node");
        }
    }

    @Nested
    @DisplayName("when resetting retry counts")
    class RetryReset {

        @Test
        @DisplayName("should reset retry count after success")
        void shouldResetRetryCountAfterSuccess() {
            ProcessInstance instance = createInstance();
            ProcessGraph graph = createGraph();
            Node node = createNode("node-1", Node.ExceptionRoutes.none(), 3);

            // Accumulate retries
            compensationHandler.determineCompensation(
                instance, graph, node, "ACTION_FAILED", "Error 1");
            compensationHandler.determineCompensation(
                instance, graph, node, "ACTION_FAILED", "Error 2");

            // Reset on success
            compensationHandler.resetRetryCount(instance, node);

            // Should start from 0 again
            CompensationAction result = compensationHandler.determineCompensation(
                instance, graph, node, "ACTION_FAILED", "New error"
            );

            assertThat(result.action()).isEqualTo(CompensationAction.ActionType.RETRY);
            assertThat(result.currentRetryCount()).isEqualTo(0);
        }
    }
}
