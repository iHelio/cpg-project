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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools for hiring manager interactions with the onboarding assistant.
 *
 * <p>Provides conversational tools for checking candidate status, progress,
 * issues, and receiving updates on onboarding processes.
 */
@Component
public class HiringManagerTools {

    private static final Logger log = LoggerFactory.getLogger(HiringManagerTools.class);
    private static final ProcessGraph.ProcessGraphId ONBOARDING_GRAPH_ID =
        new ProcessGraph.ProcessGraphId("employee-onboarding");

    private final CandidateLookupService candidateLookupService;
    private final OnboardingProgressService progressService;
    private final ProcessGraphRepository processGraphRepository;
    private final ObjectMapper objectMapper;

    public HiringManagerTools(
            CandidateLookupService candidateLookupService,
            OnboardingProgressService progressService,
            ProcessGraphRepository processGraphRepository,
            ObjectMapper objectMapper) {
        this.candidateLookupService = candidateLookupService;
        this.progressService = progressService;
        this.processGraphRepository = processGraphRepository;
        this.objectMapper = objectMapper;
    }

    @McpTool(name = "find_onboarding_status",
             description = "Find onboarding status by candidate name or ID. "
                 + "Use this to check the current status of a candidate's onboarding process.")
    public String findOnboardingStatus(
            @McpToolParam(description = "Candidate name (e.g., 'John Smith') or candidate ID (e.g., 'CAND-12345')",
                          required = true) String candidateQuery) {
        log.info("MCP tool: find_onboarding_status({})", candidateQuery);

        Optional<ProcessInstance> instanceOpt = findInstance(candidateQuery);
        if (instanceOpt.isEmpty()) {
            return toJson(Map.of(
                "found", false,
                "message", "No onboarding found for: " + candidateQuery
            ));
        }

        ProcessInstance instance = instanceOpt.get();
        return buildStatusResponse(instance);
    }

    @McpTool(name = "get_my_onboardings",
             description = "Get all active onboardings. "
                 + "Returns a summary of all candidates currently going through the onboarding process.")
    public String getMyOnboardings(
            @McpToolParam(description = "Hiring manager email or ID (optional, defaults to all)",
                          required = false) String managerId) {
        log.info("MCP tool: get_my_onboardings({})", managerId);

        List<ProcessInstance> instances;
        if (managerId != null && !managerId.isBlank()) {
            instances = candidateLookupService.findByHiringManager(managerId);
        } else {
            instances = candidateLookupService.search("");
        }

        ProcessGraph graph = getOnboardingGraph();

        List<Map<String, Object>> summaries = instances.stream()
            .filter(i -> i.status() == ProcessInstance.ProcessInstanceStatus.RUNNING)
            .map(instance -> {
                CandidateInfo info = candidateLookupService.extractCandidateInfo(instance);
                int progress = graph != null ? progressService.calculateProgress(instance, graph) : 0;
                String phase = progressService.getCurrentPhase(instance);
                List<OnboardingIssue> issues = progressService.detectIssues(instance);

                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("candidateId", info.candidateId());
                summary.put("candidateName", info.candidateName());
                summary.put("position", info.position());
                summary.put("department", info.department());
                summary.put("progress", progress);
                summary.put("currentPhase", phase);
                summary.put("status", instance.status().name());
                summary.put("hasIssues", !issues.isEmpty());
                summary.put("issueCount", issues.size());
                summary.put("startedAt", instance.startedAt().toString());

                return summary;
            })
            .toList();

        return toJson(Map.of(
            "count", summaries.size(),
            "onboardings", summaries
        ));
    }

    @McpTool(name = "get_onboarding_progress",
             description = "Get detailed progress breakdown for a candidate's onboarding. "
                 + "Shows each step and its completion status.")
    public String getOnboardingProgress(
            @McpToolParam(description = "Candidate name or ID", required = true) String candidateQuery) {
        log.info("MCP tool: get_onboarding_progress({})", candidateQuery);

        Optional<ProcessInstance> instanceOpt = findInstance(candidateQuery);
        if (instanceOpt.isEmpty()) {
            return toJson(Map.of(
                "found", false,
                "message", "No onboarding found for: " + candidateQuery
            ));
        }

        ProcessInstance instance = instanceOpt.get();
        ProcessGraph graph = getOnboardingGraph();
        if (graph == null) {
            return toJson(Map.of("error", "Process graph not found"));
        }

        CandidateInfo info = candidateLookupService.extractCandidateInfo(instance);
        List<StepProgress> steps = progressService.getStepProgress(instance, graph);
        Map<String, PhaseStatus> phases = progressService.getPhaseProgress(instance);
        int progress = progressService.calculateProgress(instance, graph);

        List<Map<String, Object>> stepMaps = steps.stream()
            .map(step -> {
                Map<String, Object> stepMap = new LinkedHashMap<>();
                stepMap.put("stepName", step.stepName());
                stepMap.put("nodeId", step.nodeId());
                stepMap.put("status", step.status().name());
                if (step.completedAt() != null) {
                    stepMap.put("completedAt", step.completedAt().toString());
                }
                if (step.assignee() != null) {
                    stepMap.put("assignee", step.assignee());
                }
                if (step.notes() != null) {
                    stepMap.put("notes", step.notes());
                }
                return stepMap;
            })
            .toList();

        Map<String, String> phaseMap = new LinkedHashMap<>();
        phases.forEach((name, status) -> phaseMap.put(name, status.name()));

        return toJson(Map.of(
            "candidateId", info.candidateId(),
            "candidateName", info.candidateName(),
            "progress", progress,
            "currentPhase", progressService.getCurrentPhase(instance),
            "phases", phaseMap,
            "steps", stepMaps
        ));
    }

    @McpTool(name = "get_onboarding_issues",
             description = "Check for any blockers, delays, or issues in a candidate's onboarding. "
                 + "Returns a list of problems that may need attention.")
    public String getOnboardingIssues(
            @McpToolParam(description = "Candidate name or ID", required = true) String candidateQuery) {
        log.info("MCP tool: get_onboarding_issues({})", candidateQuery);

        Optional<ProcessInstance> instanceOpt = findInstance(candidateQuery);
        if (instanceOpt.isEmpty()) {
            return toJson(Map.of(
                "found", false,
                "message", "No onboarding found for: " + candidateQuery
            ));
        }

        ProcessInstance instance = instanceOpt.get();
        CandidateInfo info = candidateLookupService.extractCandidateInfo(instance);
        List<OnboardingIssue> issues = progressService.detectIssues(instance);

        List<Map<String, Object>> issueMaps = issues.stream()
            .map(issue -> {
                Map<String, Object> issueMap = new LinkedHashMap<>();
                issueMap.put("type", issue.issueType().name());
                issueMap.put("severity", issue.severity().name());
                issueMap.put("description", issue.description());
                issueMap.put("affectedStep", issue.affectedStep());
                if (issue.suggestedAction() != null) {
                    issueMap.put("suggestedAction", issue.suggestedAction());
                }
                issueMap.put("detectedAt", issue.detectedAt().toString());
                return issueMap;
            })
            .toList();

        return toJson(Map.of(
            "candidateId", info.candidateId(),
            "candidateName", info.candidateName(),
            "hasIssues", !issues.isEmpty(),
            "issueCount", issues.size(),
            "issues", issueMaps
        ));
    }

    @McpTool(name = "get_estimated_completion",
             description = "Get the estimated completion date for a candidate's onboarding.")
    public String getEstimatedCompletion(
            @McpToolParam(description = "Candidate name or ID", required = true) String candidateQuery) {
        log.info("MCP tool: get_estimated_completion({})", candidateQuery);

        Optional<ProcessInstance> instanceOpt = findInstance(candidateQuery);
        if (instanceOpt.isEmpty()) {
            return toJson(Map.of(
                "found", false,
                "message", "No onboarding found for: " + candidateQuery
            ));
        }

        ProcessInstance instance = instanceOpt.get();
        CandidateInfo info = candidateLookupService.extractCandidateInfo(instance);
        ProcessGraph graph = getOnboardingGraph();

        Instant estimated = progressService.estimateCompletion(instance);
        int progress = graph != null ? progressService.calculateProgress(instance, graph) : 0;
        String phase = progressService.getCurrentPhase(instance);

        return toJson(Map.of(
            "candidateId", info.candidateId(),
            "candidateName", info.candidateName(),
            "progress", progress,
            "currentPhase", phase,
            "estimatedCompletion", estimated.toString(),
            "status", instance.status().name()
        ));
    }

    @McpTool(name = "get_recent_activity",
             description = "Get recent activity and updates for a candidate's onboarding. "
                 + "Shows what has happened recently in the process.")
    public String getRecentActivity(
            @McpToolParam(description = "Candidate name or ID", required = true) String candidateQuery,
            @McpToolParam(description = "Number of recent entries to return (default 10)",
                          required = false) String limitStr) {
        log.info("MCP tool: get_recent_activity({}, {})", candidateQuery, limitStr);

        Optional<ProcessInstance> instanceOpt = findInstance(candidateQuery);
        if (instanceOpt.isEmpty()) {
            return toJson(Map.of(
                "found", false,
                "message", "No onboarding found for: " + candidateQuery
            ));
        }

        ProcessInstance instance = instanceOpt.get();
        CandidateInfo info = candidateLookupService.extractCandidateInfo(instance);

        int limit = 10;
        if (limitStr != null && !limitStr.isBlank()) {
            try {
                limit = Integer.parseInt(limitStr);
            } catch (NumberFormatException e) {
                // Use default
            }
        }

        // Get recent node executions
        List<Map<String, Object>> activities = instance.nodeExecutions().stream()
            .sorted((a, b) -> {
                Instant timeA = a.completedAt() != null ? a.completedAt() : a.startedAt();
                Instant timeB = b.completedAt() != null ? b.completedAt() : b.startedAt();
                return timeB.compareTo(timeA); // Most recent first
            })
            .limit(limit)
            .map(ne -> {
                Map<String, Object> activity = new LinkedHashMap<>();
                activity.put("nodeId", ne.nodeId().value());
                activity.put("status", ne.status().name());
                activity.put("startedAt", ne.startedAt().toString());
                if (ne.completedAt() != null) {
                    activity.put("completedAt", ne.completedAt().toString());
                }
                if (ne.error() != null) {
                    activity.put("error", ne.error());
                }
                return activity;
            })
            .toList();

        return toJson(Map.of(
            "candidateId", info.candidateId(),
            "candidateName", info.candidateName(),
            "activityCount", activities.size(),
            "activities", activities
        ));
    }

    @McpTool(name = "get_ai_analysis_summary",
             description = "Get the AI background check analysis summary for a candidate. "
                 + "Shows risk score, recommendation, and key findings.")
    public String getAiAnalysisSummary(
            @McpToolParam(description = "Candidate name or ID", required = true) String candidateQuery) {
        log.info("MCP tool: get_ai_analysis_summary({})", candidateQuery);

        Optional<ProcessInstance> instanceOpt = findInstance(candidateQuery);
        if (instanceOpt.isEmpty()) {
            return toJson(Map.of(
                "found", false,
                "message", "No onboarding found for: " + candidateQuery
            ));
        }

        ProcessInstance instance = instanceOpt.get();
        CandidateInfo info = candidateLookupService.extractCandidateInfo(instance);

        Optional<Map<String, Object>> analysisOpt = progressService.getAiAnalysisSummary(instance);
        if (analysisOpt.isEmpty()) {
            return toJson(Map.of(
                "candidateId", info.candidateId(),
                "candidateName", info.candidateName(),
                "hasAiAnalysis", false,
                "message", "AI analysis not yet completed for this candidate"
            ));
        }

        Map<String, Object> analysis = analysisOpt.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidateId", info.candidateId());
        result.put("candidateName", info.candidateName());
        result.put("hasAiAnalysis", true);
        result.put("riskScore", analysis.get("riskScore"));
        result.put("recommendation", analysis.get("recommendation"));
        result.put("summary", analysis.get("summary"));
        result.put("keyFindings", analysis.get("keyFindings"));
        result.put("requiresReview", analysis.get("requiresReview"));
        result.put("passed", analysis.get("passed"));

        return toJson(result);
    }

    @McpTool(name = "search_candidates",
             description = "Search for candidates by name, ID, position, or department. "
                 + "Use this to find candidates when you don't have the exact name or ID.")
    public String searchCandidates(
            @McpToolParam(description = "Search query (name, ID, position, department, etc.)",
                          required = true) String query) {
        log.info("MCP tool: search_candidates({})", query);

        List<ProcessInstance> instances = candidateLookupService.search(query);

        List<Map<String, Object>> results = instances.stream()
            .map(instance -> {
                CandidateInfo info = candidateLookupService.extractCandidateInfo(instance);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("candidateId", info.candidateId());
                result.put("candidateName", info.candidateName());
                result.put("position", info.position());
                result.put("department", info.department());
                result.put("status", instance.status().name());
                return result;
            })
            .toList();

        return toJson(Map.of(
            "query", query,
            "count", results.size(),
            "candidates", results
        ));
    }

    private Optional<ProcessInstance> findInstance(String candidateQuery) {
        // Try by name first
        Optional<ProcessInstance> byName = candidateLookupService.findByCandidateName(candidateQuery);
        if (byName.isPresent()) {
            return byName;
        }

        // Try by ID
        return candidateLookupService.findByCandidateId(candidateQuery);
    }

    private String buildStatusResponse(ProcessInstance instance) {
        CandidateInfo info = candidateLookupService.extractCandidateInfo(instance);
        ProcessGraph graph = getOnboardingGraph();

        int progress = graph != null ? progressService.calculateProgress(instance, graph) : 0;
        String phase = progressService.getCurrentPhase(instance);
        Instant estimated = progressService.estimateCompletion(instance);
        List<OnboardingIssue> issues = progressService.detectIssues(instance);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("found", true);
        response.put("candidateId", info.candidateId());
        response.put("candidateName", info.candidateName());
        response.put("position", info.position());
        response.put("department", info.department());
        response.put("status", instance.status().name());
        response.put("progress", progress);
        response.put("currentPhase", phase);
        response.put("estimatedCompletion", estimated.toString());
        response.put("startedAt", instance.startedAt().toString());
        response.put("hasIssues", !issues.isEmpty());
        response.put("issueCount", issues.size());

        // Add phase summary
        if (graph != null) {
            Map<String, String> phases = new LinkedHashMap<>();
            progressService.getPhaseProgress(instance).forEach(
                (name, status) -> phases.put(name, status.name()));
            response.put("phases", phases);
        }

        // Add AI analysis summary if available
        progressService.getAiAnalysisSummary(instance).ifPresent(analysis -> {
            Map<String, Object> aiSummary = new LinkedHashMap<>();
            aiSummary.put("riskScore", analysis.get("riskScore"));
            aiSummary.put("recommendation", analysis.get("recommendation"));
            aiSummary.put("requiresReview", analysis.get("requiresReview"));
            response.put("aiAnalysis", aiSummary);
        });

        return toJson(response);
    }

    private ProcessGraph getOnboardingGraph() {
        return processGraphRepository.findById(ONBOARDING_GRAPH_ID).orElse(null);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to JSON", e);
            return "{\"error\": \"Failed to serialize response\"}";
        }
    }
}
