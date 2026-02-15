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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihelio.cpg.domain.ai.AiAnalystPort;
import com.ihelio.cpg.domain.ai.BackgroundAnalysisResult;
import com.ihelio.cpg.domain.ai.BackgroundCheckData;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Claude AI implementation of the AI analyst port.
 *
 * <p>Uses Spring AI ChatClient with Anthropic Claude to analyze background check
 * findings and produce risk assessments with recommendations.
 */
public class ClaudeAiAnalystAdapter implements AiAnalystPort {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAiAnalystAdapter.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    public ClaudeAiAnalystAdapter(
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public BackgroundAnalysisResult analyzeBackgroundCheck(
            BackgroundCheckData data,
            Map<String, Object> context) {

        log.info("Starting AI analysis for candidate: {}", data.candidateId());

        try {
            String prompt = buildPrompt(data, context);

            ChatResponse response = chatClient.prompt()
                .user(prompt)
                .call()
                .chatResponse();

            String content = response.getResult().getOutput().getText();
            log.debug("AI response received: {}", content);

            return parseResponse(content);

        } catch (Exception e) {
            log.error("AI analysis failed for candidate: {}", data.candidateId(), e);
            throw new AiAnalysisException(
                "Failed to analyze background check: " + e.getMessage(),
                e,
                isRetryableError(e)
            );
        }
    }

    private String buildPrompt(BackgroundCheckData data, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an HR compliance analyst reviewing background check results.\n\n");

        prompt.append("## Candidate Information\n");
        prompt.append("- Candidate ID: ").append(data.candidateId()).append("\n");
        if (data.position() != null) {
            prompt.append("- Position: ").append(data.position()).append("\n");
        }
        if (data.department() != null) {
            prompt.append("- Department: ").append(data.department()).append("\n");
        }
        prompt.append("\n");

        prompt.append("## Background Check Findings\n");
        if (data.findings().isEmpty()) {
            prompt.append("No findings reported.\n");
        } else {
            for (BackgroundCheckData.Finding finding : data.findings()) {
                prompt.append("- **").append(finding.category()).append("** (")
                    .append(finding.severity()).append("): ")
                    .append(finding.description());
                if (finding.verified()) {
                    prompt.append(" [VERIFIED]");
                }
                prompt.append("\n");
            }
        }
        prompt.append("\n");

        if (!context.isEmpty()) {
            prompt.append("## Additional Context\n");
            try {
                prompt.append(objectMapper.writeValueAsString(context));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize context", e);
            }
            prompt.append("\n\n");
        }

        prompt.append("## Instructions\n");
        prompt.append("Analyze the background check findings and provide:\n");
        prompt.append("1. A risk score from 0-100 (0 = lowest risk, 100 = highest risk)\n");
        prompt.append("2. A brief summary of your analysis\n");
        prompt.append("3. Key findings that influenced your decision\n");
        prompt.append("4. A recommendation: APPROVE, REVIEW, or REJECT\n");
        prompt.append("5. Rationale for your recommendation\n\n");

        prompt.append("## Guidelines\n");
        prompt.append("- APPROVE: No significant findings, low risk (score 0-30)\n");
        prompt.append("- REVIEW: Findings that require human judgment (score 31-70)\n");
        prompt.append("- REJECT: Disqualifying findings for the position (score 71-100)\n\n");

        prompt.append("Respond ONLY with valid JSON in this exact format:\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"riskScore\": <number 0-100>,\n");
        prompt.append("  \"summary\": \"<brief summary>\",\n");
        prompt.append("  \"keyFindings\": [\"<finding1>\", \"<finding2>\"],\n");
        prompt.append("  \"recommendation\": \"<APPROVE|REVIEW|REJECT>\",\n");
        prompt.append("  \"rationale\": \"<explanation>\"\n");
        prompt.append("}\n");
        prompt.append("```\n");

        return prompt.toString();
    }

    private BackgroundAnalysisResult parseResponse(String content) {
        try {
            String jsonContent = extractJson(content);

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(jsonContent, Map.class);

            int riskScore = ((Number) parsed.get("riskScore")).intValue();
            String summary = (String) parsed.get("summary");

            @SuppressWarnings("unchecked")
            List<String> keyFindings = (List<String>) parsed.getOrDefault("keyFindings", List.of());

            String recommendationStr = (String) parsed.get("recommendation");
            BackgroundAnalysisResult.Recommendation recommendation =
                BackgroundAnalysisResult.Recommendation.valueOf(recommendationStr.toUpperCase());

            String rationale = (String) parsed.get("rationale");

            return new BackgroundAnalysisResult(
                riskScore,
                summary,
                keyFindings,
                recommendation,
                rationale,
                Map.of()
            );

        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", content, e);
            throw new AiAnalysisException(
                "Failed to parse AI response: " + e.getMessage(),
                e,
                false
            );
        }
    }

    private String extractJson(String content) {
        int startIdx = content.indexOf('{');
        int endIdx = content.lastIndexOf('}');

        if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) {
            throw new IllegalArgumentException("No valid JSON object found in response");
        }

        return content.substring(startIdx, endIdx + 1);
    }

    private boolean isRetryableError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return true;
        }
        return message.contains("timeout")
            || message.contains("rate limit")
            || message.contains("overloaded")
            || message.contains("503")
            || message.contains("529");
    }
}
