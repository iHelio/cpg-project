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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ihelio.cpg.domain.ai.BackgroundAnalysisResult;
import com.ihelio.cpg.domain.ai.BackgroundCheckData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for StubAiAnalystAdapter.
 */
class StubAiAnalystAdapterTest {

    private StubAiAnalystAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new StubAiAnalystAdapter();
    }

    @Test
    @DisplayName("Returns APPROVE with low risk for no findings")
    void returnsApproveForNoFindings() {
        BackgroundCheckData data = new BackgroundCheckData(
            "C123",
            "Software Engineer",
            "Engineering",
            List.of(),
            Map.of()
        );

        BackgroundAnalysisResult result = adapter.analyzeBackgroundCheck(data, Map.of());

        assertNotNull(result);
        assertEquals(5, result.riskScore());
        assertEquals(BackgroundAnalysisResult.Recommendation.APPROVE, result.recommendation());
        assertFalse(result.requiresReview());
        assertTrue(result.passed());
        assertEquals(true, result.metadata().get("stub"));
    }

    @Test
    @DisplayName("Returns REJECT with high risk for high severity findings")
    void returnsRejectForHighSeverityFindings() {
        BackgroundCheckData.Finding finding = new BackgroundCheckData.Finding(
            "CRIMINAL",
            "HIGH",
            "Felony conviction",
            true,
            Map.of()
        );

        BackgroundCheckData data = new BackgroundCheckData(
            "C456",
            "Manager",
            "Finance",
            List.of(finding),
            Map.of()
        );

        BackgroundAnalysisResult result = adapter.analyzeBackgroundCheck(data, Map.of());

        assertNotNull(result);
        assertTrue(result.riskScore() >= 30);
        assertEquals(BackgroundAnalysisResult.Recommendation.REJECT, result.recommendation());
        assertTrue(result.requiresReview());
        assertFalse(result.passed());
    }

    @Test
    @DisplayName("Returns REVIEW with medium risk for medium severity findings")
    void returnsReviewForMediumSeverityFindings() {
        BackgroundCheckData.Finding finding = new BackgroundCheckData.Finding(
            "EMPLOYMENT",
            "MEDIUM",
            "Employment gap of 6 months",
            true,
            Map.of()
        );

        BackgroundCheckData data = new BackgroundCheckData(
            "C789",
            "Analyst",
            "Operations",
            List.of(finding),
            Map.of()
        );

        BackgroundAnalysisResult result = adapter.analyzeBackgroundCheck(data, Map.of());

        assertNotNull(result);
        assertEquals(BackgroundAnalysisResult.Recommendation.REVIEW, result.recommendation());
        assertTrue(result.requiresReview());
    }

    @Test
    @DisplayName("Calculates risk score based on finding severities")
    void calculatesRiskScoreBasedOnSeverities() {
        BackgroundCheckData.Finding highFinding = new BackgroundCheckData.Finding(
            "CRIMINAL", "HIGH", "Felony", true, Map.of()
        );
        BackgroundCheckData.Finding mediumFinding = new BackgroundCheckData.Finding(
            "EMPLOYMENT", "MEDIUM", "Gap", true, Map.of()
        );

        BackgroundCheckData dataWithHigh = new BackgroundCheckData(
            "C1", null, null, List.of(highFinding), Map.of()
        );

        BackgroundCheckData dataWithMedium = new BackgroundCheckData(
            "C2", null, null, List.of(mediumFinding), Map.of()
        );

        BackgroundCheckData dataWithBoth = new BackgroundCheckData(
            "C3", null, null, List.of(highFinding, mediumFinding), Map.of()
        );

        int highScore = adapter.analyzeBackgroundCheck(dataWithHigh, Map.of()).riskScore();
        int mediumScore = adapter.analyzeBackgroundCheck(dataWithMedium, Map.of()).riskScore();
        int bothScore = adapter.analyzeBackgroundCheck(dataWithBoth, Map.of()).riskScore();

        assertTrue(highScore > mediumScore, "High severity should have higher score");
        assertTrue(bothScore > highScore, "Multiple findings should have higher score");
        assertTrue(bothScore <= 100, "Score should not exceed 100");
    }

    @Test
    @DisplayName("Includes key findings in result")
    void includesKeyFindingsInResult() {
        BackgroundCheckData.Finding finding1 = new BackgroundCheckData.Finding(
            "CRIMINAL", "LOW", "Minor offense", true, Map.of()
        );
        BackgroundCheckData.Finding finding2 = new BackgroundCheckData.Finding(
            "EDUCATION", "LOW", "Degree verified", true, Map.of()
        );

        BackgroundCheckData data = new BackgroundCheckData(
            "C999",
            "Developer",
            "IT",
            List.of(finding1, finding2),
            Map.of()
        );

        BackgroundAnalysisResult result = adapter.analyzeBackgroundCheck(data, Map.of());

        assertEquals(2, result.keyFindings().size());
        assertTrue(result.keyFindings().stream().anyMatch(f -> f.contains("CRIMINAL")));
        assertTrue(result.keyFindings().stream().anyMatch(f -> f.contains("EDUCATION")));
    }

    @Test
    @DisplayName("Includes stub metadata in result")
    void includesStubMetadataInResult() {
        BackgroundCheckData data = new BackgroundCheckData(
            "C123", null, null, List.of(), Map.of()
        );

        BackgroundAnalysisResult result = adapter.analyzeBackgroundCheck(data, Map.of());

        assertEquals(true, result.metadata().get("stub"));
    }
}
