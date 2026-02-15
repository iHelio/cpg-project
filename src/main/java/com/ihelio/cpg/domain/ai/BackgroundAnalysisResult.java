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

package com.ihelio.cpg.domain.ai;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of AI analysis of background check findings.
 *
 * <p>Contains the risk assessment, summary, and recommendation produced by the
 * AI analyst when evaluating background check results.
 *
 * @param riskScore risk score from 0-100, where 0 is lowest risk
 * @param summary brief summary of the analysis findings
 * @param keyFindings list of key findings from the background check
 * @param recommendation AI recommendation for how to proceed
 * @param rationale explanation for the recommendation
 * @param metadata additional metadata from the analysis
 */
public record BackgroundAnalysisResult(
    int riskScore,
    String summary,
    List<String> keyFindings,
    Recommendation recommendation,
    String rationale,
    Map<String, Object> metadata
) {

    public BackgroundAnalysisResult {
        if (riskScore < 0 || riskScore > 100) {
            throw new IllegalArgumentException("Risk score must be between 0 and 100");
        }
        Objects.requireNonNull(summary, "Summary is required");
        Objects.requireNonNull(recommendation, "Recommendation is required");
        Objects.requireNonNull(rationale, "Rationale is required");
        keyFindings = keyFindings != null ? List.copyOf(keyFindings) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Recommendation from the AI analysis.
     */
    public enum Recommendation {
        /**
         * AI recommends approval - low risk, no adverse findings.
         */
        APPROVE,

        /**
         * AI recommends human review - moderate risk or findings requiring judgment.
         */
        REVIEW,

        /**
         * AI recommends rejection - high risk or disqualifying findings.
         */
        REJECT
    }

    /**
     * Checks if the analysis result indicates HR review is required.
     *
     * <p>Review is required if the recommendation is not APPROVE or if the
     * risk score exceeds the auto-approval threshold of 30.
     *
     * @return true if HR review is required
     */
    public boolean requiresReview() {
        return recommendation != Recommendation.APPROVE || riskScore > 30;
    }

    /**
     * Checks if the analysis result indicates the candidate passed.
     *
     * @return true if the recommendation is APPROVE and risk score is acceptable
     */
    public boolean passed() {
        return recommendation == Recommendation.APPROVE && riskScore <= 30;
    }

    /**
     * Converts this result to a map suitable for inclusion in execution context.
     *
     * @return map representation of the analysis result
     */
    public Map<String, Object> toContextMap() {
        return Map.of(
            "riskScore", riskScore,
            "summary", summary,
            "keyFindings", keyFindings,
            "recommendation", recommendation.name(),
            "rationale", rationale,
            "requiresReview", requiresReview(),
            "passed", passed()
        );
    }
}
