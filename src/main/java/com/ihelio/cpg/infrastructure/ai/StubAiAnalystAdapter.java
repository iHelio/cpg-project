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

import com.ihelio.cpg.domain.ai.AiAnalystPort;
import com.ihelio.cpg.domain.ai.BackgroundAnalysisResult;
import com.ihelio.cpg.domain.ai.BackgroundCheckData;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stub implementation of the AI analyst port for testing and development.
 *
 * <p>This adapter provides deterministic responses based on the input data,
 * useful for testing without requiring an actual AI API key.
 */
public class StubAiAnalystAdapter implements AiAnalystPort {

    private static final Logger log = LoggerFactory.getLogger(StubAiAnalystAdapter.class);

    @Override
    public BackgroundAnalysisResult analyzeBackgroundCheck(
            BackgroundCheckData data,
            Map<String, Object> context) {

        log.info("Stub AI analysis for candidate: {}", data.candidateId());

        if (data.findings().isEmpty()) {
            return new BackgroundAnalysisResult(
                5,
                "No adverse findings in background check.",
                List.of("Clean record", "All verifications passed"),
                BackgroundAnalysisResult.Recommendation.APPROVE,
                "Background check returned no findings. Candidate is cleared for hire.",
                Map.of("stub", true)
            );
        }

        long highSeverityCount = data.findings().stream()
            .filter(f -> "HIGH".equalsIgnoreCase(f.severity()))
            .count();

        long mediumSeverityCount = data.findings().stream()
            .filter(f -> "MEDIUM".equalsIgnoreCase(f.severity()))
            .count();

        int riskScore = (int) Math.min(100, highSeverityCount * 30 + mediumSeverityCount * 15 + 10);

        List<String> keyFindings = data.findings().stream()
            .map(f -> f.category() + ": " + f.description())
            .toList();

        BackgroundAnalysisResult.Recommendation recommendation;
        String rationale;

        if (highSeverityCount > 0) {
            recommendation = BackgroundAnalysisResult.Recommendation.REJECT;
            rationale = "High severity findings require rejection per company policy.";
        } else if (mediumSeverityCount > 0 || riskScore > 30) {
            recommendation = BackgroundAnalysisResult.Recommendation.REVIEW;
            rationale = "Medium severity findings require HR review before proceeding.";
        } else {
            recommendation = BackgroundAnalysisResult.Recommendation.APPROVE;
            rationale = "Only minor findings that do not affect eligibility.";
        }

        return new BackgroundAnalysisResult(
            riskScore,
            "Background check analysis completed with " + data.findings().size() + " finding(s).",
            keyFindings,
            recommendation,
            rationale,
            Map.of("stub", true, "findingsCount", data.findings().size())
        );
    }
}
