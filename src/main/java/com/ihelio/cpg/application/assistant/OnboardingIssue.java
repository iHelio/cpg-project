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

package com.ihelio.cpg.application.assistant;

import java.time.Instant;

/**
 * An issue or blocker in the onboarding process.
 *
 * @param issueType the type of issue
 * @param severity the severity level
 * @param description human-readable description of the issue
 * @param affectedStep the step affected by this issue
 * @param suggestedAction suggested action to resolve the issue
 * @param detectedAt when the issue was detected
 */
public record OnboardingIssue(
    IssueType issueType,
    Severity severity,
    String description,
    String affectedStep,
    String suggestedAction,
    Instant detectedAt
) {
    /**
     * Types of issues that can occur during onboarding.
     */
    public enum IssueType {
        /** Process is blocked and cannot proceed */
        BLOCKED,
        /** Process is delayed beyond expected timeline */
        DELAYED,
        /** AI or review flagged a concern */
        FLAGGED,
        /** Issue has been escalated */
        ESCALATED,
        /** A task is overdue */
        OVERDUE,
        /** Missing required information or documents */
        MISSING_INFO
    }

    /**
     * Severity levels for issues.
     */
    public enum Severity {
        /** Low severity, informational */
        LOW,
        /** Medium severity, attention needed */
        MEDIUM,
        /** High severity, immediate action required */
        HIGH,
        /** Critical severity, process cannot continue */
        CRITICAL
    }

    /**
     * Creates a blocked issue.
     */
    public static OnboardingIssue blocked(String description, String affectedStep, String suggestedAction) {
        return new OnboardingIssue(
            IssueType.BLOCKED,
            Severity.HIGH,
            description,
            affectedStep,
            suggestedAction,
            Instant.now()
        );
    }

    /**
     * Creates a flagged issue from AI analysis.
     */
    public static OnboardingIssue flagged(Severity severity, String description, String suggestedAction) {
        return new OnboardingIssue(
            IssueType.FLAGGED,
            severity,
            description,
            "AI Analysis",
            suggestedAction,
            Instant.now()
        );
    }

    /**
     * Creates an overdue issue.
     */
    public static OnboardingIssue overdue(String description, String affectedStep) {
        return new OnboardingIssue(
            IssueType.OVERDUE,
            Severity.MEDIUM,
            description,
            affectedStep,
            "Follow up on pending task",
            Instant.now()
        );
    }
}
