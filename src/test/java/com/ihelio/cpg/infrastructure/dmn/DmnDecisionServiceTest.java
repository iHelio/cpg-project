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

package com.ihelio.cpg.infrastructure.dmn;

import static org.assertj.core.api.Assertions.assertThat;

import com.ihelio.cpg.infrastructure.dmn.DmnDecisionService.DecisionResult;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DmnDecisionServiceTest {

    private DmnDecisionService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new DmnDecisionService();
        service.init();
    }

    @Nested
    @DisplayName("Background Check Allowed Decision")
    class BackgroundCheckAllowedTests {

        @Test
        @DisplayName("should block when consent not given")
        void shouldBlockWithoutConsent() {
            Map<String, Object> context = Map.of(
                "location", "US-NY",
                "consentGiven", false,
                "disclosuresSigned", true,
                "daysSinceOffer", 10
            );

            DecisionResult result = service.evaluate(
                "Onboarding Policies",
                "Background Check Allowed",
                context
            );

            assertThat(result.success()).isTrue();
            assertThat(result.getValueAs(Boolean.class)).isFalse();
        }

        @Test
        @DisplayName("should block California before 5 days")
        void shouldBlockCaliforniaBeforeFiveDays() {
            Map<String, Object> context = Map.of(
                "location", "US-CA",
                "consentGiven", true,
                "disclosuresSigned", true,
                "daysSinceOffer", 3
            );

            DecisionResult result = service.evaluate(
                "Onboarding Policies",
                "Background Check Allowed",
                context
            );

            assertThat(result.success()).isTrue();
            assertThat(result.getValueAs(Boolean.class)).isFalse();
        }

        @Test
        @DisplayName("should allow California after 5 days")
        void shouldAllowCaliforniaAfterFiveDays() {
            Map<String, Object> context = Map.of(
                "location", "US-CA",
                "consentGiven", true,
                "disclosuresSigned", true,
                "daysSinceOffer", 5
            );

            DecisionResult result = service.evaluate(
                "Onboarding Policies",
                "Background Check Allowed",
                context
            );

            assertThat(result.success()).isTrue();
            assertThat(result.getValueAs(Boolean.class)).isTrue();
        }

        @Test
        @DisplayName("should allow other US states immediately")
        void shouldAllowOtherUsStates() {
            Map<String, Object> context = Map.of(
                "location", "US-NY",
                "consentGiven", true,
                "disclosuresSigned", true,
                "daysSinceOffer", 1
            );

            DecisionResult result = service.evaluate(
                "Onboarding Policies",
                "Background Check Allowed",
                context
            );

            assertThat(result.success()).isTrue();
            assertThat(result.getValueAs(Boolean.class)).isTrue();
        }

        @Test
        @DisplayName("should allow EU locations")
        void shouldAllowEuLocations() {
            Map<String, Object> context = Map.of(
                "location", "EU-DE",
                "consentGiven", true,
                "disclosuresSigned", true,
                "daysSinceOffer", 1
            );

            DecisionResult result = service.evaluate(
                "Onboarding Policies",
                "Background Check Allowed",
                context
            );

            assertThat(result.success()).isTrue();
            assertThat(result.getValueAs(Boolean.class)).isTrue();
        }
    }

    @Nested
    @DisplayName("Equipment Package Decision")
    class EquipmentPackageTests {

        @Test
        @DisplayName("should assign premium remote package for remote engineers")
        void shouldAssignPremiumRemoteForEngineers() {
            Map<String, Object> context = Map.of(
                "roleType", "ENGINEERING",
                "isRemote", true,
                "location", "US-NY"
            );

            DecisionResult result = service.evaluate(
                "Onboarding Policies",
                "Equipment Package",
                context
            );

            assertThat(result.success()).isTrue();
            assertThat(result.getValueAs(String.class)).isEqualTo("LAPTOP_PREMIUM_REMOTE");
        }

        @Test
        @DisplayName("should assign docking package for onsite engineers")
        void shouldAssignDockingForOnsiteEngineers() {
            Map<String, Object> context = Map.of(
                "roleType", "ENGINEERING",
                "isRemote", false,
                "location", "US-NY"
            );

            DecisionResult result = service.evaluate(
                "Onboarding Policies",
                "Equipment Package",
                context
            );

            assertThat(result.success()).isTrue();
            assertThat(result.getValueAs(String.class)).isEqualTo("LAPTOP_PREMIUM_DOCKING");
        }

        @Test
        @DisplayName("should assign mobile package for remote sales")
        void shouldAssignMobileForRemoteSales() {
            Map<String, Object> context = Map.of(
                "roleType", "SALES",
                "isRemote", true,
                "location", "US-TX"
            );

            DecisionResult result = service.evaluate(
                "Onboarding Policies",
                "Equipment Package",
                context
            );

            assertThat(result.success()).isTrue();
            assertThat(result.getValueAs(String.class)).isEqualTo("LAPTOP_STANDARD_MOBILE");
        }

        @Test
        @DisplayName("should assign GDPR package for EU locations")
        void shouldAssignGdprForEu() {
            Map<String, Object> context = Map.of(
                "roleType", "OPERATIONS",
                "isRemote", false,
                "location", "EU-FR"
            );

            DecisionResult result = service.evaluate(
                "Onboarding Policies",
                "Equipment Package",
                context
            );

            assertThat(result.success()).isTrue();
            assertThat(result.getValueAs(String.class)).isEqualTo("LAPTOP_STANDARD_GDPR");
        }
    }
}
