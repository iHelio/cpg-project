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

package com.ihelio.cpg.interfaces.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihelio.cpg.application.assistant.CandidateInfo;
import com.ihelio.cpg.application.assistant.CandidateLookupService;
import com.ihelio.cpg.application.assistant.OnboardingIssue;
import com.ihelio.cpg.application.assistant.OnboardingProgressService;
import com.ihelio.cpg.application.assistant.OnboardingProgressService.PhaseStatus;
import com.ihelio.cpg.application.assistant.StepProgress;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

/**
 * MCP prompts for hiring manager assistant interactions.
 *
 * <p>Provides reusable prompt templates for common hiring manager queries
 * like dashboard views, candidate deep dives, and troubleshooting.
 */
@Component
public class HiringManagerPrompts {

    private static final Logger log = LoggerFactory.getLogger(HiringManagerPrompts.class);
    private static final ProcessGraph.ProcessGraphId ONBOARDING_GRAPH_ID =
        new ProcessGraph.ProcessGraphId("employee-onboarding");

    private final CandidateLookupService candidateLookupService;
    private final OnboardingProgressService progressService;
    private final ProcessGraphRepository processGraphRepository;
    private final ObjectMapper objectMapper;

    public HiringManagerPrompts(
            CandidateLookupService candidateLookupService,
            OnboardingProgressService progressService,
            ProcessGraphRepository processGraphRepository,
            ObjectMapper objectMapper) {
        this.candidateLookupService = candidateLookupService;
        this.progressService = progressService;
        this.processGraphRepository = processGraphRepository;
        this.objectMapper = objectMapper;
    }

    @McpPrompt(name = "onboarding_dashboard",
               description = "Get a comprehensive dashboard view of all active onboardings. "
                   + "Shows progress, issues, and status for all candidates.")
    public GetPromptResult onboardingDashboard(
            @McpArg(name = "managerId",
                    description = "Hiring manager ID (optional, shows all if not provided)",
                    required = false) String managerId) {
        log.info("MCP prompt: onboarding_dashboard({})", managerId);

        List<ProcessInstance> instances;
        if (managerId != null && !managerId.isBlank()) {
            instances = candidateLookupService.findByHiringManager(managerId);
        } else {
            instances = candidateLookupService.search("");
        }

        // Filter to only running instances
        instances = instances.stream()
            .filter(i -> i.status() == ProcessInstance.ProcessInstanceStatus.RUNNING)
            .toList();

        ProcessGraph graph = getOnboardingGraph();

        StringBuilder dashboard = new StringBuilder();
        dashboard.append("# Onboarding Dashboard\n\n");
        dashboard.append("## Summary\n");
        dashboard.append(String.format("- **Total Active Onboardings**: %d\n", instances.size()));

        // Count by phase
        Map<String, Long> byPhase = instances.stream()
            .collect(Collectors.groupingBy(
                i -> progressService.getCurrentPhase(i),
                Collectors.counting()));
        dashboard.append("- **By Phase**: ").append(byPhase).append("\n\n");

        // Count issues
        long withIssues = instances.stream()
            .filter(i -> !progressService.detectIssues(i).isEmpty())
            .count();
        dashboard.append(String.format("- **With Issues**: %d\n\n", withIssues));

        dashboard.append("## Active Onboardings\n\n");
        dashboard.append("| Candidate | Position | Progress | Phase | Issues | Started |\n");
        dashboard.append("|-----------|----------|----------|-------|--------|--------|\n");

        for (ProcessInstance instance : instances) {
            CandidateInfo info = candidateLookupService.extractCandidateInfo(instance);
            int progress = graph != null ? progressService.calculateProgress(instance, graph) : 0;
            String phase = progressService.getCurrentPhase(instance);
            List<OnboardingIssue> issues = progressService.detectIssues(instance);

            dashboard.append(String.format("| %s | %s | %d%% | %s | %s | %s |\n",
                info.candidateName() != null ? info.candidateName() : "Unknown",
                info.position() != null ? info.position() : "N/A",
                progress,
                phase,
                issues.isEmpty() ? "None" : issues.size() + " issue(s)",
                instance.startedAt().toString().substring(0, 10)));
        }

        // Highlight issues
        List<ProcessInstance> instancesWithIssues = instances.stream()
            .filter(i -> !progressService.detectIssues(i).isEmpty())
            .toList();

        if (!instancesWithIssues.isEmpty()) {
            dashboard.append("\n## Attention Needed\n\n");
            for (ProcessInstance instance : instancesWithIssues) {
                CandidateInfo info = candidateLookupService.extractCandidateInfo(instance);
                List<OnboardingIssue> issues = progressService.detectIssues(instance);

                dashboard.append(String.format("### %s\n", info.candidateName()));
                for (OnboardingIssue issue : issues) {
                    dashboard.append(String.format("- **%s** (%s): %s\n",
                        issue.issueType().name(),
                        issue.severity().name(),
                        issue.description()));
                }
                dashboard.append("\n");
            }
        }

        String prompt = """
            Review the following onboarding dashboard and provide a summary with:
            1. Overall status of onboardings
            2. Any candidates that need immediate attention
            3. Recommendations for improving throughput
            4. Patterns or trends you notice

            %s""".formatted(dashboard.toString());

        return new GetPromptResult("Onboarding Dashboard",
            List.of(new PromptMessage(Role.USER, new TextContent(prompt))));
    }

    @McpPrompt(name = "candidate_deep_dive",
               description = "Get detailed analysis of a specific candidate's onboarding "
                   + "including progress, AI analysis, issues, and recommendations.")
    public GetPromptResult candidateDeepDive(
            @McpArg(name = "candidateQuery",
                    description = "Candidate name or ID to analyze",
                    required = true) String candidateQuery) {
        log.info("MCP prompt: candidate_deep_dive({})", candidateQuery);

        Optional<ProcessInstance> instanceOpt = findInstance(candidateQuery);
        if (instanceOpt.isEmpty()) {
            return new GetPromptResult("Candidate Not Found",
                List.of(new PromptMessage(Role.USER, new TextContent(
                    "No onboarding found for candidate: " + candidateQuery))));
        }

        ProcessInstance instance = instanceOpt.get();
        CandidateInfo info = candidateLookupService.extractCandidateInfo(instance);
        ProcessGraph graph = getOnboardingGraph();

        int progress = graph != null ? progressService.calculateProgress(instance, graph) : 0;
        List<StepProgress> steps = graph != null ? progressService.getStepProgress(instance, graph) : List.of();
        Map<String, PhaseStatus> phases = progressService.getPhaseProgress(instance);
        List<OnboardingIssue> issues = progressService.detectIssues(instance);
        Instant estimated = progressService.estimateCompletion(instance);

        StringBuilder details = new StringBuilder();
        details.append(String.format("""
            # Candidate Deep Dive: %s

            ## Candidate Information
            - **Candidate ID**: %s
            - **Name**: %s
            - **Position**: %s
            - **Department**: %s
            - **Hiring Manager**: %s

            ## Onboarding Status
            - **Status**: %s
            - **Progress**: %d%%
            - **Current Phase**: %s
            - **Started**: %s
            - **Estimated Completion**: %s

            """,
            info.candidateName(),
            info.candidateId(),
            info.candidateName(),
            info.position() != null ? info.position() : "N/A",
            info.department() != null ? info.department() : "N/A",
            info.hiringManager() != null ? info.hiringManager() : "N/A",
            instance.status().name(),
            progress,
            progressService.getCurrentPhase(instance),
            instance.startedAt().toString(),
            estimated.toString()));

        // Phase progress
        details.append("## Phase Progress\n");
        for (Map.Entry<String, PhaseStatus> entry : phases.entrySet()) {
            String icon = switch (entry.getValue()) {
                case COMPLETE -> "✅";
                case IN_PROGRESS -> "🔄";
                case PENDING -> "⏳";
            };
            details.append(String.format("- %s %s: %s\n", icon, entry.getKey(), entry.getValue().name()));
        }
        details.append("\n");

        // Step details
        details.append("## Step Details\n");
        for (StepProgress step : steps) {
            String icon = switch (step.status()) {
                case COMPLETED -> "✅";
                case IN_PROGRESS -> "🔄";
                case PENDING -> "⏳";
                case BLOCKED -> "🚫";
                case SKIPPED -> "⏭️";
            };
            details.append(String.format("- %s **%s** (%s)", icon, step.stepName(), step.status().name()));
            if (step.completedAt() != null) {
                details.append(String.format(" - Completed %s", step.completedAt().toString().substring(0, 10)));
            }
            if (step.notes() != null) {
                details.append(String.format(" - *%s*", step.notes()));
            }
            details.append("\n");
        }
        details.append("\n");

        // AI Analysis
        Optional<Map<String, Object>> aiAnalysis = progressService.getAiAnalysisSummary(instance);
        if (aiAnalysis.isPresent()) {
            Map<String, Object> analysis = aiAnalysis.get();
            details.append("## AI Background Check Analysis\n");
            details.append(String.format("- **Risk Score**: %s/100\n", analysis.get("riskScore")));
            details.append(String.format("- **Recommendation**: %s\n", analysis.get("recommendation")));
            details.append(String.format("- **Requires Review**: %s\n", analysis.get("requiresReview")));
            details.append(String.format("- **Summary**: %s\n", analysis.get("summary")));

            Object findings = analysis.get("keyFindings");
            if (findings instanceof List<?> findingsList && !findingsList.isEmpty()) {
                details.append("- **Key Findings**:\n");
                for (Object finding : findingsList) {
                    details.append(String.format("  - %s\n", finding));
                }
            }
            details.append("\n");
        }

        // Issues
        if (!issues.isEmpty()) {
            details.append("## Issues & Blockers\n");
            for (OnboardingIssue issue : issues) {
                details.append(String.format("### %s (%s)\n", issue.issueType().name(), issue.severity().name()));
                details.append(String.format("- **Description**: %s\n", issue.description()));
                details.append(String.format("- **Affected Step**: %s\n", issue.affectedStep()));
                if (issue.suggestedAction() != null) {
                    details.append(String.format("- **Suggested Action**: %s\n", issue.suggestedAction()));
                }
                details.append("\n");
            }
        }

        String prompt = """
            Analyze the following candidate onboarding details and provide:
            1. A clear summary of where this candidate is in the onboarding process
            2. Any concerns or risks based on the AI analysis
            3. Recommended actions if there are issues
            4. Expected timeline to completion
            5. Any patterns that might indicate problems

            %s""".formatted(details.toString());

        return new GetPromptResult("Candidate Deep Dive: " + info.candidateName(),
            List.of(new PromptMessage(Role.USER, new TextContent(prompt))));
    }

    @McpPrompt(name = "troubleshoot_onboarding",
               description = "Diagnose and suggest solutions for a stuck or problematic onboarding.")
    public GetPromptResult troubleshootOnboarding(
            @McpArg(name = "candidateQuery",
                    description = "Candidate name or ID to troubleshoot",
                    required = true) String candidateQuery) {
        log.info("MCP prompt: troubleshoot_onboarding({})", candidateQuery);

        Optional<ProcessInstance> instanceOpt = findInstance(candidateQuery);
        if (instanceOpt.isEmpty()) {
            return new GetPromptResult("Candidate Not Found",
                List.of(new PromptMessage(Role.USER, new TextContent(
                    "No onboarding found for candidate: " + candidateQuery))));
        }

        ProcessInstance instance = instanceOpt.get();
        CandidateInfo info = candidateLookupService.extractCandidateInfo(instance);
        ProcessGraph graph = getOnboardingGraph();

        List<StepProgress> steps = graph != null ? progressService.getStepProgress(instance, graph) : List.of();
        List<OnboardingIssue> issues = progressService.detectIssues(instance);

        StringBuilder troubleshoot = new StringBuilder();
        troubleshoot.append(String.format("""
            # Troubleshooting: %s

            ## Current State
            - **Status**: %s
            - **Current Phase**: %s
            - **Active Nodes**: %s
            - **Last Activity**: %s

            """,
            info.candidateName(),
            instance.status().name(),
            progressService.getCurrentPhase(instance),
            instance.activeNodeIds().stream().map(n -> n.value()).toList(),
            instance.nodeExecutions().isEmpty() ? "None" :
                instance.nodeExecutions().get(instance.nodeExecutions().size() - 1).startedAt().toString()));

        // Failed or blocked steps
        List<StepProgress> problemSteps = steps.stream()
            .filter(s -> s.status() == StepProgress.StepStatus.BLOCKED)
            .toList();

        if (!problemSteps.isEmpty()) {
            troubleshoot.append("## Blocked Steps\n");
            for (StepProgress step : problemSteps) {
                troubleshoot.append(String.format("- **%s**: %s\n", step.stepName(),
                    step.notes() != null ? step.notes() : "No details available"));
            }
            troubleshoot.append("\n");
        }

        // All detected issues
        troubleshoot.append("## Detected Issues\n");
        if (issues.isEmpty()) {
            troubleshoot.append("No issues detected.\n\n");
        } else {
            for (OnboardingIssue issue : issues) {
                troubleshoot.append(String.format("""
                    ### %s - %s
                    - **Description**: %s
                    - **Affected Step**: %s
                    - **Suggested Action**: %s

                    """,
                    issue.severity().name(),
                    issue.issueType().name(),
                    issue.description(),
                    issue.affectedStep(),
                    issue.suggestedAction() != null ? issue.suggestedAction() : "Review and resolve"));
            }
        }

        // Node execution history
        troubleshoot.append("## Recent Activity\n");
        List<ProcessInstance.NodeExecution> recentExecutions = instance.nodeExecutions().stream()
            .sorted((a, b) -> {
                Instant timeA = a.completedAt() != null ? a.completedAt() : a.startedAt();
                Instant timeB = b.completedAt() != null ? b.completedAt() : b.startedAt();
                return timeB.compareTo(timeA);
            })
            .limit(5)
            .toList();

        for (ProcessInstance.NodeExecution ne : recentExecutions) {
            troubleshoot.append(String.format("- **%s**: %s",
                ne.nodeId().value(), ne.status().name()));
            if (ne.error() != null) {
                troubleshoot.append(String.format(" - Error: %s", ne.error()));
            }
            troubleshoot.append("\n");
        }

        String prompt = """
            Diagnose the following onboarding issues and provide:
            1. Root cause analysis for each issue
            2. Step-by-step resolution instructions
            3. Who needs to take action (HR, IT, hiring manager, etc.)
            4. Priority order for addressing issues
            5. How to prevent similar issues in the future

            %s""".formatted(troubleshoot.toString());

        return new GetPromptResult("Troubleshoot Onboarding: " + info.candidateName(),
            List.of(new PromptMessage(Role.USER, new TextContent(prompt))));
    }

    private Optional<ProcessInstance> findInstance(String candidateQuery) {
        Optional<ProcessInstance> byName = candidateLookupService.findByCandidateName(candidateQuery);
        if (byName.isPresent()) {
            return byName;
        }
        return candidateLookupService.findByCandidateId(candidateQuery);
    }

    private ProcessGraph getOnboardingGraph() {
        return processGraphRepository.findById(ONBOARDING_GRAPH_ID).orElse(null);
    }

    @SuppressWarnings("unused")
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"Serialization failed\"}";
        }
    }
}
