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

package com.ihelio.cpg.application.onboarding;

import static com.ihelio.cpg.application.onboarding.OnboardingNodes.OFFER_ACCEPTED;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.ONBOARDING_CANCELLED;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.ONBOARDING_COMPLETE;

import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.model.ProcessGraph.Metadata;
import com.ihelio.cpg.domain.model.ProcessGraph.ProcessGraphId;
import com.ihelio.cpg.domain.model.ProcessGraph.ProcessGraphStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Builder for the employee onboarding process graph.
 *
 * <p>This builder assembles the complete onboarding workflow including:
 * <ul>
 *   <li>12 decision nodes covering validation, background check, IT setup, HR, and completion</li>
 *   <li>18 edges defining permissible transitions with FEEL guard conditions</li>
 *   <li>Parallel execution paths for IT provisioning and HR documentation</li>
 *   <li>Compensation paths for cancellation scenarios</li>
 * </ul>
 *
 * <p>The graph follows DDD principles where:
 * <ul>
 *   <li>Nodes are action-oriented (what CAN be done), not state-oriented</li>
 *   <li>Edges express permissible transitions, not fixed control flow</li>
 *   <li>Guards are FEEL expressions evaluated against runtime context</li>
 *   <li>Events can trigger or re-evaluate edge availability</li>
 * </ul>
 */
public final class OnboardingProcessGraphBuilder {

    private static final ProcessGraphId GRAPH_ID = new ProcessGraphId("employee-onboarding");
    private static final int CURRENT_VERSION = 1;

    private OnboardingProcessGraphBuilder() {
    }

    /**
     * Builds the complete employee onboarding process graph.
     *
     * @return the fully configured process graph
     */
    public static ProcessGraph build() {
        return new ProcessGraph(
            GRAPH_ID,
            "Employee Onboarding",
            "Comprehensive employee onboarding workflow from offer acceptance to day-one readiness",
            CURRENT_VERSION,
            ProcessGraphStatus.PUBLISHED,
            OnboardingNodes.all(),
            OnboardingEdges.all(),
            List.of(OFFER_ACCEPTED),
            List.of(ONBOARDING_COMPLETE, ONBOARDING_CANCELLED),
            createMetadata()
        );
    }

    /**
     * Builds the process graph in draft status for testing.
     *
     * @return the process graph in draft status
     */
    public static ProcessGraph buildDraft() {
        return new ProcessGraph(
            GRAPH_ID,
            "Employee Onboarding",
            "Comprehensive employee onboarding workflow from offer acceptance to day-one readiness",
            CURRENT_VERSION,
            ProcessGraphStatus.DRAFT,
            OnboardingNodes.all(),
            OnboardingEdges.all(),
            List.of(OFFER_ACCEPTED),
            List.of(ONBOARDING_COMPLETE, ONBOARDING_CANCELLED),
            createMetadata()
        );
    }

    private static Metadata createMetadata() {
        return new Metadata(
            "system",
            Instant.now(),
            "system",
            Instant.now(),
            Map.of(
                "domain", "hr",
                "process-type", "onboarding",
                "compliance", "fcra,i9,ban-the-box"
            )
        );
    }
}
