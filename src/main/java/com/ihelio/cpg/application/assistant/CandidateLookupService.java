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

import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.repository.ProcessInstanceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for looking up process instances by candidate information.
 *
 * <p>Provides fuzzy matching and search capabilities to find onboarding
 * processes by candidate name, ID, or other attributes.
 */
@Service
public class CandidateLookupService {

    private static final Logger log = LoggerFactory.getLogger(CandidateLookupService.class);
    private static final ProcessGraph.ProcessGraphId ONBOARDING_GRAPH_ID =
        new ProcessGraph.ProcessGraphId("employee-onboarding");

    private final ProcessInstanceRepository instanceRepository;

    public CandidateLookupService(ProcessInstanceRepository instanceRepository) {
        this.instanceRepository = instanceRepository;
    }

    /**
     * Finds a process instance by candidate name using fuzzy matching.
     *
     * @param name the candidate name to search for
     * @return the matching process instance, or empty if not found
     */
    public Optional<ProcessInstance> findByCandidateName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        String searchName = name.toLowerCase(Locale.ROOT).trim();
        log.debug("Searching for candidate by name: {}", searchName);

        return instanceRepository.findByProcessGraphId(ONBOARDING_GRAPH_ID).stream()
            .filter(instance -> matchesCandidateName(instance, searchName))
            .findFirst();
    }

    /**
     * Finds a process instance by candidate ID.
     *
     * @param candidateId the candidate ID
     * @return the matching process instance, or empty if not found
     */
    public Optional<ProcessInstance> findByCandidateId(String candidateId) {
        if (candidateId == null || candidateId.isBlank()) {
            return Optional.empty();
        }

        log.debug("Searching for candidate by ID: {}", candidateId);

        return instanceRepository.findByProcessGraphId(ONBOARDING_GRAPH_ID).stream()
            .filter(instance -> matchesCandidateId(instance, candidateId))
            .findFirst();
    }

    /**
     * Finds all process instances for a hiring manager.
     *
     * @param managerId the hiring manager's identifier
     * @return list of matching process instances
     */
    public List<ProcessInstance> findByHiringManager(String managerId) {
        if (managerId == null || managerId.isBlank()) {
            return List.of();
        }

        log.debug("Searching for candidates by hiring manager: {}", managerId);

        return instanceRepository.findByProcessGraphId(ONBOARDING_GRAPH_ID).stream()
            .filter(instance -> matchesHiringManager(instance, managerId))
            .toList();
    }

    /**
     * Searches for process instances across multiple fields.
     *
     * @param query the search query
     * @return list of matching process instances
     */
    public List<ProcessInstance> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String searchQuery = query.toLowerCase(Locale.ROOT).trim();
        log.debug("Searching for candidates with query: {}", searchQuery);

        return instanceRepository.findByProcessGraphId(ONBOARDING_GRAPH_ID).stream()
            .filter(instance -> matchesQuery(instance, searchQuery))
            .toList();
    }

    /**
     * Extracts candidate information from a process instance.
     *
     * @param instance the process instance
     * @return the candidate information
     */
    @SuppressWarnings("unchecked")
    public CandidateInfo extractCandidateInfo(ProcessInstance instance) {
        ExecutionContext context = instance.context();
        if (context == null) {
            return CandidateInfo.of("unknown", "Unknown Candidate");
        }

        Map<String, Object> domainContext = context.domainContext();

        String candidateId = getStringValue(domainContext, "candidateId", "unknown");
        String candidateName = getStringValue(domainContext, "candidateName", "Unknown");

        String email = getStringValue(domainContext, "candidateEmail", null);
        if (email == null) {
            Object candidate = domainContext.get("candidate");
            if (candidate instanceof Map<?, ?> candidateMap) {
                email = (String) candidateMap.get("email");
            }
        }

        String position = null;
        String department = null;
        Object offer = domainContext.get("offer");
        if (offer instanceof Map<?, ?> offerMap) {
            position = (String) offerMap.get("position");
            department = (String) offerMap.get("department");
        }

        String hiringManager = getStringValue(domainContext, "hiringManager", null);
        if (hiringManager == null) {
            Object hm = domainContext.get("hiringManager");
            if (hm instanceof Map<?, ?> hmMap) {
                hiringManager = (String) hmMap.get("id");
                if (hiringManager == null) {
                    hiringManager = (String) hmMap.get("email");
                }
            }
        }

        Instant startDate = null;
        Object startDateObj = domainContext.get("startDate");
        if (startDateObj instanceof Instant instant) {
            startDate = instant;
        } else if (startDateObj instanceof String dateStr) {
            try {
                startDate = Instant.parse(dateStr);
            } catch (Exception e) {
                // Ignore parse errors
            }
        }

        return new CandidateInfo(
            candidateId,
            candidateName,
            email,
            position,
            department,
            hiringManager,
            startDate
        );
    }

    private boolean matchesCandidateName(ProcessInstance instance, String searchName) {
        CandidateInfo info = extractCandidateInfo(instance);
        if (info.candidateName() == null) {
            return false;
        }

        String candidateName = info.candidateName().toLowerCase(Locale.ROOT);

        // Exact match
        if (candidateName.equals(searchName)) {
            return true;
        }

        // Contains match
        if (candidateName.contains(searchName) || searchName.contains(candidateName)) {
            return true;
        }

        // Word-by-word match (e.g., "John" matches "John Smith")
        String[] searchWords = searchName.split("\\s+");
        for (String word : searchWords) {
            if (word.length() >= 2 && candidateName.contains(word)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesCandidateId(ProcessInstance instance, String candidateId) {
        CandidateInfo info = extractCandidateInfo(instance);
        return candidateId.equalsIgnoreCase(info.candidateId());
    }

    private boolean matchesHiringManager(ProcessInstance instance, String managerId) {
        CandidateInfo info = extractCandidateInfo(instance);
        if (info.hiringManager() == null) {
            return false;
        }
        return managerId.equalsIgnoreCase(info.hiringManager());
    }

    private boolean matchesQuery(ProcessInstance instance, String query) {
        CandidateInfo info = extractCandidateInfo(instance);

        // Check all searchable fields
        if (containsIgnoreCase(info.candidateName(), query)) return true;
        if (containsIgnoreCase(info.candidateId(), query)) return true;
        if (containsIgnoreCase(info.email(), query)) return true;
        if (containsIgnoreCase(info.position(), query)) return true;
        if (containsIgnoreCase(info.department(), query)) return true;

        // Check correlation ID
        if (instance.correlationId() != null &&
            containsIgnoreCase(instance.correlationId(), query)) {
            return true;
        }

        return false;
    }

    private boolean containsIgnoreCase(String value, String search) {
        if (value == null) return false;
        return value.toLowerCase(Locale.ROOT).contains(search);
    }

    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value instanceof String str) {
            return str;
        }
        return defaultValue;
    }
}
