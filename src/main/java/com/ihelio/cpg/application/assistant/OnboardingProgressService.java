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

import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.execution.ProcessInstance.NodeExecution;
import com.ihelio.cpg.domain.execution.ProcessInstance.NodeExecutionStatus;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.ProcessGraph;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for tracking and calculating onboarding progress.
 *
 * <p>Provides progress calculations, phase identification, issue detection,
 * and completion estimates for onboarding processes.
 */
@Service
public class OnboardingProgressService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingProgressService.class);

    // Define phases and their associated nodes
    private static final Map<String, List<String>> PHASES = new LinkedHashMap<>();
    static {
        PHASES.put("Offer & Validation", List.of("initialize-onboarding", "validate-candidate"));
        PHASES.put("Background Check", List.of("run-background-check", "ai-analyze-background-check", "review-background-results"));
        PHASES.put("IT Provisioning", List.of("order-equipment", "ship-equipment", "create-accounts"));
        PHASES.put("HR Documentation", List.of("collect-documents", "verify-i9"));
        PHASES.put("Completion", List.of("schedule-orientation", "finalize-onboarding"));
    }

    // Terminal nodes that don't count toward progress
    private static final Set<String> TERMINAL_NODES = Set.of("finalize-onboarding", "cancel-onboarding");

    // Average duration per phase (for estimation)
    private static final Map<String, Duration> PHASE_DURATIONS = Map.of(
        "Offer & Validation", Duration.ofHours(2),
        "Background Check", Duration.ofDays(3),
        "IT Provisioning", Duration.ofDays(2),
        "HR Documentation", Duration.ofDays(3),
        "Completion", Duration.ofHours(4)
    );

    /**
     * Calculates the overall progress percentage.
     *
     * @param instance the process instance
     * @param graph the process graph
     * @return progress percentage (0-100)
     */
    public int calculateProgress(ProcessInstance instance, ProcessGraph graph) {
        List<String> completedNodeIds = instance.nodeExecutions().stream()
            .filter(ne -> ne.status() == NodeExecutionStatus.COMPLETED)
            .map(ne -> ne.nodeId().value())
            .distinct()
            .toList();

        // Count total actionable nodes (excluding terminal nodes)
        int totalNodes = (int) graph.nodes().stream()
            .filter(n -> !TERMINAL_NODES.contains(n.id().value()))
            .count();

        // Count completed actionable nodes
        int completedCount = (int) completedNodeIds.stream()
            .filter(id -> !TERMINAL_NODES.contains(id))
            .count();

        if (totalNodes == 0) {
            return 0;
        }

        return Math.min(100, (completedCount * 100) / totalNodes);
    }

    /**
     * Gets detailed step-by-step progress.
     *
     * @param instance the process instance
     * @param graph the process graph
     * @return list of step progress entries
     */
    public List<StepProgress> getStepProgress(ProcessInstance instance, ProcessGraph graph) {
        List<StepProgress> steps = new ArrayList<>();

        Map<String, NodeExecution> nodeExecutionMap = new LinkedHashMap<>();
        for (NodeExecution ne : instance.nodeExecutions()) {
            // Keep the latest execution for each node
            nodeExecutionMap.put(ne.nodeId().value(), ne);
        }

        Set<String> activeNodeIds = instance.activeNodeIds().stream()
            .map(Node.NodeId::value)
            .collect(java.util.stream.Collectors.toSet());

        // Process nodes in phase order
        for (Map.Entry<String, List<String>> phase : PHASES.entrySet()) {
            for (String nodeId : phase.getValue()) {
                Optional<Node> nodeOpt = graph.findNode(new Node.NodeId(nodeId));
                if (nodeOpt.isEmpty()) {
                    continue;
                }

                Node node = nodeOpt.get();
                NodeExecution execution = nodeExecutionMap.get(nodeId);

                StepProgress.StepStatus status;
                Instant completedAt = null;
                String notes = null;

                if (execution != null) {
                    switch (execution.status()) {
                        case COMPLETED -> {
                            status = StepProgress.StepStatus.COMPLETED;
                            completedAt = execution.completedAt();
                        }
                        case RUNNING -> status = StepProgress.StepStatus.IN_PROGRESS;
                        case FAILED -> {
                            status = StepProgress.StepStatus.BLOCKED;
                            notes = execution.error();
                        }
                        case SKIPPED -> status = StepProgress.StepStatus.SKIPPED;
                        default -> status = StepProgress.StepStatus.PENDING;
                    }
                } else if (activeNodeIds.contains(nodeId)) {
                    status = StepProgress.StepStatus.IN_PROGRESS;
                } else {
                    status = StepProgress.StepStatus.PENDING;
                }

                String assignee = null;
                if (node.action() != null && node.action().type() == Node.ActionType.HUMAN_TASK) {
                    assignee = "HR Team"; // Default assignee for human tasks
                }

                steps.add(new StepProgress(
                    node.name(),
                    nodeId,
                    status,
                    completedAt,
                    assignee,
                    notes
                ));
            }
        }

        return steps;
    }

    /**
     * Identifies the current phase of the onboarding process.
     *
     * @param instance the process instance
     * @return the current phase name
     */
    public String getCurrentPhase(ProcessInstance instance) {
        Set<String> activeNodeIds = instance.activeNodeIds().stream()
            .map(Node.NodeId::value)
            .collect(java.util.stream.Collectors.toSet());

        Set<String> completedNodeIds = instance.nodeExecutions().stream()
            .filter(ne -> ne.status() == NodeExecutionStatus.COMPLETED)
            .map(ne -> ne.nodeId().value())
            .collect(java.util.stream.Collectors.toSet());

        // Check if process is complete
        if (completedNodeIds.contains("finalize-onboarding")) {
            return "Complete";
        }
        if (completedNodeIds.contains("cancel-onboarding")) {
            return "Cancelled";
        }

        // Find the phase with active or incomplete nodes
        for (Map.Entry<String, List<String>> phase : PHASES.entrySet()) {
            for (String nodeId : phase.getValue()) {
                if (activeNodeIds.contains(nodeId)) {
                    return phase.getKey();
                }
            }
        }

        // Find the first phase with incomplete required nodes
        for (Map.Entry<String, List<String>> phase : PHASES.entrySet()) {
            boolean phaseComplete = phase.getValue().stream()
                .allMatch(nodeId -> completedNodeIds.contains(nodeId) ||
                    nodeId.equals("review-background-results")); // Optional node

            if (!phaseComplete) {
                return phase.getKey();
            }
        }

        return "In Progress";
    }

    /**
     * Estimates the completion date based on historical data and remaining work.
     *
     * @param instance the process instance
     * @return estimated completion timestamp
     */
    public Instant estimateCompletion(ProcessInstance instance) {
        String currentPhase = getCurrentPhase(instance);

        if ("Complete".equals(currentPhase) || "Cancelled".equals(currentPhase)) {
            return instance.completedAt().orElse(Instant.now());
        }

        // Calculate remaining duration
        Duration remaining = Duration.ZERO;
        boolean foundCurrent = false;

        for (Map.Entry<String, List<String>> phase : PHASES.entrySet()) {
            if (phase.getKey().equals(currentPhase)) {
                foundCurrent = true;
                // Add half the current phase (assume we're halfway through)
                remaining = remaining.plus(PHASE_DURATIONS.getOrDefault(phase.getKey(), Duration.ofDays(1)).dividedBy(2));
            } else if (foundCurrent) {
                remaining = remaining.plus(PHASE_DURATIONS.getOrDefault(phase.getKey(), Duration.ofDays(1)));
            }
        }

        return Instant.now().plus(remaining);
    }

    /**
     * Detects issues and blockers in the onboarding process.
     *
     * @param instance the process instance
     * @return list of detected issues
     */
    @SuppressWarnings("unchecked")
    public List<OnboardingIssue> detectIssues(ProcessInstance instance) {
        List<OnboardingIssue> issues = new ArrayList<>();

        // Check for failed node executions
        for (NodeExecution ne : instance.nodeExecutions()) {
            if (ne.status() == NodeExecutionStatus.FAILED) {
                issues.add(OnboardingIssue.blocked(
                    "Step failed: " + ne.error(),
                    ne.nodeId().value(),
                    "Review the error and retry or escalate"
                ));
            }
        }

        // Check AI analysis for flagged findings
        if (instance.context() != null) {
            Map<String, Object> state = instance.context().accumulatedState();
            Object aiAnalysis = state.get("aiAnalysis");
            if (aiAnalysis instanceof Map<?, ?> analysisMap) {
                Boolean requiresReview = (Boolean) analysisMap.get("requiresReview");
                if (Boolean.TRUE.equals(requiresReview)) {
                    String recommendation = (String) analysisMap.get("recommendation");
                    Integer riskScore = analysisMap.get("riskScore") instanceof Number n ? n.intValue() : 0;

                    OnboardingIssue.Severity severity = riskScore > 50 ?
                        OnboardingIssue.Severity.HIGH : OnboardingIssue.Severity.MEDIUM;

                    String summary = (String) analysisMap.get("summary");
                    issues.add(OnboardingIssue.flagged(
                        severity,
                        "AI Analysis: " + (summary != null ? summary : "Review required"),
                        "HR must review background check findings (recommendation: " + recommendation + ")"
                    ));

                    // Add individual findings as issues
                    Object findings = analysisMap.get("keyFindings");
                    if (findings instanceof List<?> findingsList) {
                        for (Object finding : findingsList) {
                            if (finding instanceof String findingStr) {
                                issues.add(new OnboardingIssue(
                                    OnboardingIssue.IssueType.FLAGGED,
                                    OnboardingIssue.Severity.LOW,
                                    findingStr,
                                    "Background Check",
                                    null,
                                    Instant.now()
                                ));
                            }
                        }
                    }
                }
            }
        }

        // Check for overdue tasks (nodes in progress for too long)
        Instant now = Instant.now();
        for (NodeExecution ne : instance.nodeExecutions()) {
            if (ne.status() == NodeExecutionStatus.RUNNING) {
                Duration elapsed = Duration.between(ne.startedAt(), now);
                if (elapsed.toHours() > 72) { // More than 3 days
                    issues.add(OnboardingIssue.overdue(
                        "Task has been in progress for " + elapsed.toDays() + " days",
                        ne.nodeId().value()
                    ));
                }
            }
        }

        // Check for stalled process (no activity for a while)
        if (instance.status() == ProcessInstance.ProcessInstanceStatus.RUNNING) {
            Instant lastActivity = instance.nodeExecutions().stream()
                .map(ne -> ne.completedAt() != null ? ne.completedAt() : ne.startedAt())
                .max(Instant::compareTo)
                .orElse(instance.startedAt());

            Duration sinceLastActivity = Duration.between(lastActivity, now);
            if (sinceLastActivity.toHours() > 48) {
                issues.add(new OnboardingIssue(
                    OnboardingIssue.IssueType.DELAYED,
                    OnboardingIssue.Severity.MEDIUM,
                    "No progress for " + sinceLastActivity.toDays() + " days",
                    getCurrentPhase(instance),
                    "Check on pending tasks and follow up",
                    Instant.now()
                ));
            }
        }

        return issues;
    }

    /**
     * Gets a summary of the AI analysis if available.
     *
     * @param instance the process instance
     * @return AI analysis summary or empty
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> getAiAnalysisSummary(ProcessInstance instance) {
        if (instance.context() == null) {
            return Optional.empty();
        }

        Map<String, Object> state = instance.context().accumulatedState();
        Object aiAnalysis = state.get("aiAnalysis");
        if (aiAnalysis instanceof Map<?, ?> analysisMap) {
            return Optional.of((Map<String, Object>) analysisMap);
        }

        return Optional.empty();
    }

    /**
     * Gets phase progress summary.
     *
     * @param instance the process instance
     * @return map of phase names to completion status
     */
    public Map<String, PhaseStatus> getPhaseProgress(ProcessInstance instance) {
        Set<String> completedNodeIds = instance.nodeExecutions().stream()
            .filter(ne -> ne.status() == NodeExecutionStatus.COMPLETED)
            .map(ne -> ne.nodeId().value())
            .collect(java.util.stream.Collectors.toSet());

        Set<String> activeNodeIds = instance.activeNodeIds().stream()
            .map(Node.NodeId::value)
            .collect(java.util.stream.Collectors.toSet());

        Map<String, PhaseStatus> result = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> phase : PHASES.entrySet()) {
            List<String> phaseNodes = phase.getValue();

            boolean hasActive = phaseNodes.stream().anyMatch(activeNodeIds::contains);
            boolean allComplete = phaseNodes.stream()
                .filter(nodeId -> !nodeId.equals("review-background-results")) // Optional
                .allMatch(completedNodeIds::contains);

            PhaseStatus status;
            if (allComplete) {
                status = PhaseStatus.COMPLETE;
            } else if (hasActive) {
                status = PhaseStatus.IN_PROGRESS;
            } else if (phaseNodes.stream().anyMatch(completedNodeIds::contains)) {
                status = PhaseStatus.IN_PROGRESS;
            } else {
                status = PhaseStatus.PENDING;
            }

            result.put(phase.getKey(), status);
        }

        return result;
    }

    /**
     * Status of a phase in the onboarding process.
     */
    public enum PhaseStatus {
        COMPLETE,
        IN_PROGRESS,
        PENDING
    }
}
