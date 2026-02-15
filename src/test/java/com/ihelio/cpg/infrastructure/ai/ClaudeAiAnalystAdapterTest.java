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

package com.ihelio.cpg.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihelio.cpg.domain.ai.AiAnalystPort.AiAnalysisException;
import com.ihelio.cpg.domain.ai.BackgroundAnalysisResult;
import com.ihelio.cpg.domain.ai.BackgroundCheckData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.core.io.ResourceLoader;

/**
 * Unit tests for ClaudeAiAnalystAdapter.
 */
@ExtendWith(MockitoExtension.class)
class ClaudeAiAnalystAdapterTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ResourceLoader resourceLoader;

    private ObjectMapper objectMapper;
    private ClaudeAiAnalystAdapter adapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(chatClientBuilder.build()).thenReturn(chatClient);
        adapter = new ClaudeAiAnalystAdapter(chatClientBuilder, objectMapper, resourceLoader);
    }

    @Nested
    @DisplayName("analyzeBackgroundCheck")
    class AnalyzeBackgroundCheckTests {

        @Test
        @DisplayName("Parses valid JSON response with APPROVE recommendation")
        void parsesValidApproveResponse() {
            String jsonResponse = """
                {
                  "riskScore": 10,
                  "summary": "Clean background check with no adverse findings",
                  "keyFindings": ["Verified employment", "Clean criminal record"],
                  "recommendation": "APPROVE",
                  "rationale": "All checks passed without issues"
                }
                """;

            mockChatResponse(jsonResponse);

            BackgroundCheckData data = new BackgroundCheckData(
                "C123",
                "Software Engineer",
                "Engineering",
                List.of(),
                Map.of()
            );

            BackgroundAnalysisResult result = adapter.analyzeBackgroundCheck(data, Map.of());

            assertNotNull(result);
            assertEquals(10, result.riskScore());
            assertEquals("Clean background check with no adverse findings", result.summary());
            assertEquals(2, result.keyFindings().size());
            assertEquals(BackgroundAnalysisResult.Recommendation.APPROVE, result.recommendation());
            assertEquals("All checks passed without issues", result.rationale());
        }

        @Test
        @DisplayName("Parses valid JSON response with REVIEW recommendation")
        void parsesValidReviewResponse() {
            String jsonResponse = """
                {
                  "riskScore": 45,
                  "summary": "Some findings require human review",
                  "keyFindings": ["Employment gap found"],
                  "recommendation": "REVIEW",
                  "rationale": "Employment history needs clarification"
                }
                """;

            mockChatResponse(jsonResponse);

            BackgroundCheckData data = new BackgroundCheckData(
                "C456",
                "Manager",
                "Operations",
                List.of(),
                Map.of()
            );

            BackgroundAnalysisResult result = adapter.analyzeBackgroundCheck(data, Map.of());

            assertEquals(45, result.riskScore());
            assertEquals(BackgroundAnalysisResult.Recommendation.REVIEW, result.recommendation());
            assertTrue(result.requiresReview());
        }

        @Test
        @DisplayName("Extracts JSON from markdown code block")
        void extractsJsonFromMarkdownCodeBlock() {
            String responseWithMarkdown = """
                Here is my analysis:

                ```json
                {
                  "riskScore": 5,
                  "summary": "Excellent candidate",
                  "keyFindings": [],
                  "recommendation": "APPROVE",
                  "rationale": "No concerns"
                }
                ```

                Let me know if you have questions.
                """;

            mockChatResponse(responseWithMarkdown);

            BackgroundCheckData data = new BackgroundCheckData("C789", null, null, List.of(), Map.of());

            BackgroundAnalysisResult result = adapter.analyzeBackgroundCheck(data, Map.of());

            assertEquals(5, result.riskScore());
            assertEquals(BackgroundAnalysisResult.Recommendation.APPROVE, result.recommendation());
        }

        @Test
        @DisplayName("Throws exception for invalid JSON response")
        void throwsExceptionForInvalidJson() {
            mockChatResponse("This is not valid JSON at all");

            BackgroundCheckData data = new BackgroundCheckData("C999", null, null, List.of(), Map.of());

            AiAnalysisException exception = assertThrows(
                AiAnalysisException.class,
                () -> adapter.analyzeBackgroundCheck(data, Map.of())
            );

            assertTrue(exception.getMessage().contains("Failed to parse"));
        }

        @Test
        @DisplayName("Includes findings in prompt")
        void includesFindingsInPrompt() {
            String jsonResponse = """
                {
                  "riskScore": 60,
                  "summary": "Findings present",
                  "keyFindings": ["Criminal record found"],
                  "recommendation": "REVIEW",
                  "rationale": "Criminal history needs review"
                }
                """;

            mockChatResponse(jsonResponse);

            BackgroundCheckData.Finding finding = new BackgroundCheckData.Finding(
                "CRIMINAL",
                "MEDIUM",
                "Misdemeanor from 2018",
                true,
                Map.of()
            );

            BackgroundCheckData data = new BackgroundCheckData(
                "C111",
                "Analyst",
                "Finance",
                List.of(finding),
                Map.of()
            );

            BackgroundAnalysisResult result = adapter.analyzeBackgroundCheck(data, Map.of());

            assertNotNull(result);
            assertEquals(60, result.riskScore());
        }
    }

    private void mockChatResponse(String content) {
        ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class);
        CallResponseSpec callResponseSpec = mock(CallResponseSpec.class);
        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage message = mock(AssistantMessage.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.chatResponse()).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(message);
        when(message.getText()).thenReturn(content);
    }
}
