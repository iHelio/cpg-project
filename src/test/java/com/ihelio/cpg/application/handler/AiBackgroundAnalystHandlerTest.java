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

package com.ihelio.cpg.application.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ihelio.cpg.domain.action.ActionContext;
import com.ihelio.cpg.domain.action.ActionResult;
import com.ihelio.cpg.domain.ai.AiAnalystPort;
import com.ihelio.cpg.domain.ai.AiAnalystPort.AiAnalysisException;
import com.ihelio.cpg.domain.ai.BackgroundAnalysisResult;
import com.ihelio.cpg.domain.ai.BackgroundCheckData;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.execution.ProcessInstance.ProcessInstanceId;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.Node.Action;
import com.ihelio.cpg.domain.model.Node.ActionConfig;
import com.ihelio.cpg.domain.model.Node.ActionType;
import com.ihelio.cpg.domain.model.Node.EventConfig;
import com.ihelio.cpg.domain.model.Node.ExceptionRoutes;
import com.ihelio.cpg.domain.model.Node.NodeId;
import com.ihelio.cpg.domain.model.Node.Preconditions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for AiBackgroundAnalystHandler.
 */
@ExtendWith(MockitoExtension.class)
class AiBackgroundAnalystHandlerTest {

    @Mock
    private AiAnalystPort aiAnalyst;

    private AiBackgroundAnalystHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AiBackgroundAnalystHandler(aiAnalyst);
    }

    @Test
    @DisplayName("Handler supports AGENT_ASSISTED action type")
    void supportsAgentAssistedActionType() {
        assertEquals(ActionType.AGENT_ASSISTED, handler.supportedType());
    }

    @Test
    @DisplayName("Handler can handle aiBackgroundAnalyst reference")
    void canHandleAiBackgroundAnalystRef() {
        assertTrue(handler.canHandle("aiBackgroundAnalyst"));
        assertFalse(handler.canHandle("otherHandler"));
        assertFalse(handler.canHandle(null));
    }

    @Test
    @DisplayName("Handler supports async execution")
    void supportsAsyncExecution() {
        assertTrue(handler.supportsAsync());
    }

    @Nested
    @DisplayName("Execute Tests")
    class ExecuteTests {

        @Test
        @DisplayName("Returns success with AI analysis when approved")
        void returnsSuccessWhenApproved() {
            BackgroundAnalysisResult analysisResult = new BackgroundAnalysisResult(
                15,
                "Clean background check",
                List.of("No findings"),
                BackgroundAnalysisResult.Recommendation.APPROVE,
                "Low risk candidate",
                Map.of()
            );

            when(aiAnalyst.analyzeBackgroundCheck(any(), any())).thenReturn(analysisResult);

            ActionContext context = createActionContext(Map.of(
                "backgroundCheck", Map.of("status", "COMPLETED"),
                "candidate", Map.of("id", "C123")
            ));

            ActionResult result = handler.execute(context);

            assertTrue(result.isSuccess());
            assertNotNull(result.output().get("aiAnalysis"));

            @SuppressWarnings("unchecked")
            Map<String, Object> aiAnalysis = (Map<String, Object>) result.output().get("aiAnalysis");
            assertEquals(15, aiAnalysis.get("riskScore"));
            assertEquals("APPROVE", aiAnalysis.get("recommendation"));
            assertEquals(false, aiAnalysis.get("requiresReview"));
            assertEquals(true, aiAnalysis.get("passed"));
        }

        @Test
        @DisplayName("Returns success with review required when risk is high")
        void returnsSuccessWithReviewRequiredWhenRiskHigh() {
            BackgroundAnalysisResult analysisResult = new BackgroundAnalysisResult(
                55,
                "Medium risk findings",
                List.of("Employment gap", "Address discrepancy"),
                BackgroundAnalysisResult.Recommendation.REVIEW,
                "Findings require human judgment",
                Map.of()
            );

            when(aiAnalyst.analyzeBackgroundCheck(any(), any())).thenReturn(analysisResult);

            ActionContext context = createActionContext(Map.of(
                "backgroundCheck", Map.of("status", "COMPLETED"),
                "candidate", Map.of("id", "C456")
            ));

            ActionResult result = handler.execute(context);

            assertTrue(result.isSuccess());

            @SuppressWarnings("unchecked")
            Map<String, Object> aiAnalysis = (Map<String, Object>) result.output().get("aiAnalysis");
            assertEquals(55, aiAnalysis.get("riskScore"));
            assertEquals("REVIEW", aiAnalysis.get("recommendation"));
            assertEquals(true, aiAnalysis.get("requiresReview"));
            assertEquals(false, aiAnalysis.get("passed"));
        }

        @Test
        @DisplayName("Returns failure when AI analysis throws retryable exception")
        void returnsFailureOnRetryableException() {
            when(aiAnalyst.analyzeBackgroundCheck(any(), any()))
                .thenThrow(new AiAnalysisException("Timeout", true));

            ActionContext context = createActionContext(Map.of(
                "backgroundCheck", Map.of("status", "COMPLETED"),
                "candidate", Map.of("id", "C789")
            ));

            ActionResult result = handler.execute(context);

            assertTrue(result.isFailed());
            assertTrue(result.retryable());
            assertTrue(result.error().contains("AI analysis failed"));
        }

        @Test
        @DisplayName("Returns failure when AI analysis throws non-retryable exception")
        void returnsFailureOnNonRetryableException() {
            when(aiAnalyst.analyzeBackgroundCheck(any(), any()))
                .thenThrow(new AiAnalysisException("Parse error", false));

            ActionContext context = createActionContext(Map.of(
                "backgroundCheck", Map.of("status", "COMPLETED"),
                "candidate", Map.of("id", "C999")
            ));

            ActionResult result = handler.execute(context);

            assertTrue(result.isFailed());
            assertFalse(result.retryable());
        }

        @Test
        @DisplayName("Extracts position and department from offer context")
        void extractsPositionAndDepartmentFromOffer() {
            BackgroundAnalysisResult analysisResult = new BackgroundAnalysisResult(
                10,
                "Clean",
                List.of(),
                BackgroundAnalysisResult.Recommendation.APPROVE,
                "Clear",
                Map.of()
            );

            when(aiAnalyst.analyzeBackgroundCheck(any(), any())).thenReturn(analysisResult);

            ActionContext context = createActionContext(Map.of(
                "backgroundCheck", Map.of("status", "COMPLETED"),
                "candidate", Map.of("id", "C123"),
                "offer", Map.of("position", "Engineer", "department", "Engineering")
            ));

            handler.execute(context);

            verify(aiAnalyst).analyzeBackgroundCheck(any(BackgroundCheckData.class), any());
        }
    }

    private ActionContext createActionContext(Map<String, Object> domainContext) {
        Node node = new Node(
            new NodeId("ai-analyze-background-check"),
            "AI Analyze Background Check",
            "Test node",
            1,
            Preconditions.none(),
            List.of(),
            List.of(),
            new Action(
                ActionType.AGENT_ASSISTED,
                "aiBackgroundAnalyst",
                "Test action",
                ActionConfig.defaults()
            ),
            EventConfig.none(),
            ExceptionRoutes.none()
        );

        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.id()).thenReturn(new ProcessInstanceId("test-instance-1"));

        ExecutionContext executionContext = ExecutionContext.builder()
            .domainContext(domainContext)
            .clientContext(Map.of())
            .build();

        return ActionContext.of(node, instance, executionContext);
    }
}
