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
 * Value object containing background check data for AI analysis.
 *
 * <p>Encapsulates the findings from a background check provider that will be
 * analyzed by the AI analyst to produce a risk assessment and recommendation.
 *
 * @param candidateId unique identifier for the candidate
 * @param position the position the candidate is applying for
 * @param department the department for the position
 * @param findings list of findings from the background check
 * @param rawData raw data from the background check provider
 */
public record BackgroundCheckData(
    String candidateId,
    String position,
    String department,
    List<Finding> findings,
    Map<String, Object> rawData
) {

    public BackgroundCheckData {
        Objects.requireNonNull(candidateId, "Candidate ID is required");
        findings = findings != null ? List.copyOf(findings) : List.of();
        rawData = rawData != null ? Map.copyOf(rawData) : Map.of();
    }

    /**
     * A finding from the background check.
     *
     * @param category the category of the finding (e.g., "CRIMINAL", "EMPLOYMENT", "EDUCATION")
     * @param severity the severity level (e.g., "LOW", "MEDIUM", "HIGH")
     * @param description description of the finding
     * @param verified whether the finding has been verified
     * @param details additional details about the finding
     */
    public record Finding(
        String category,
        String severity,
        String description,
        boolean verified,
        Map<String, Object> details
    ) {
        public Finding {
            Objects.requireNonNull(category, "Finding category is required");
            Objects.requireNonNull(severity, "Finding severity is required");
            details = details != null ? Map.copyOf(details) : Map.of();
        }
    }

    /**
     * Creates a BackgroundCheckData instance from execution context data.
     *
     * @param contextData map containing background check data from context
     * @return BackgroundCheckData instance
     */
    @SuppressWarnings("unchecked")
    public static BackgroundCheckData fromContext(Map<String, Object> contextData) {
        String candidateId = (String) contextData.getOrDefault("candidateId", "unknown");
        String position = (String) contextData.get("position");
        String department = (String) contextData.get("department");

        List<Finding> findings = List.of();
        Object findingsObj = contextData.get("findings");
        if (findingsObj instanceof List<?> findingsList) {
            findings = findingsList.stream()
                .filter(f -> f instanceof Map)
                .map(f -> {
                    Map<String, Object> fm = (Map<String, Object>) f;
                    return new Finding(
                        (String) fm.getOrDefault("category", "UNKNOWN"),
                        (String) fm.getOrDefault("severity", "LOW"),
                        (String) fm.get("description"),
                        Boolean.TRUE.equals(fm.get("verified")),
                        (Map<String, Object>) fm.getOrDefault("details", Map.of())
                    );
                })
                .toList();
        }

        return new BackgroundCheckData(
            candidateId,
            position,
            department,
            findings,
            contextData
        );
    }
}
