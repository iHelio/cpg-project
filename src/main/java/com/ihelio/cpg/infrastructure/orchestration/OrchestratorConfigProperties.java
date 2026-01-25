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

package com.ihelio.cpg.infrastructure.orchestration;

/**
 * Configuration properties for the Process Orchestrator.
 *
 * <p>Configuration can be provided via Spring Boot application.yml:
 * <pre>
 * cpg:
 *   orchestrator:
 *     enabled: true
 *     event-queue-capacity: 10000
 *     evaluation-interval-ms: 5000
 *     governance:
 *       idempotency-enabled: true
 *       authorization-enabled: true
 *       policy-gate-enabled: true
 *     tracing:
 *       enabled: true
 *       persist-traces: true
 *       trace-retention-days: 90
 * </pre>
 *
 * @param enabled whether the orchestrator is enabled
 * @param eventQueueCapacity capacity of the event queue
 * @param evaluationIntervalMs interval for periodic evaluations
 * @param governance governance configuration
 * @param tracing tracing configuration
 */
public record OrchestratorConfigProperties(
    boolean enabled,
    int eventQueueCapacity,
    long evaluationIntervalMs,
    GovernanceConfig governance,
    TracingConfig tracing
) {

    public OrchestratorConfigProperties {
        if (eventQueueCapacity <= 0) {
            eventQueueCapacity = 10000;
        }
        if (evaluationIntervalMs <= 0) {
            evaluationIntervalMs = 5000;
        }
        if (governance == null) {
            governance = GovernanceConfig.defaults();
        }
        if (tracing == null) {
            tracing = TracingConfig.defaults();
        }
    }

    /**
     * Creates default configuration.
     */
    public static OrchestratorConfigProperties defaults() {
        return new OrchestratorConfigProperties(
            true,
            10000,
            5000,
            GovernanceConfig.defaults(),
            TracingConfig.defaults()
        );
    }

    /**
     * Creates configuration with governance disabled (for testing).
     */
    public static OrchestratorConfigProperties forTesting() {
        return new OrchestratorConfigProperties(
            true,
            1000,
            1000,
            GovernanceConfig.disabled(),
            TracingConfig.forTesting()
        );
    }

    /**
     * Governance configuration.
     *
     * @param idempotencyEnabled whether idempotency checks are enabled
     * @param authorizationEnabled whether authorization checks are enabled
     * @param policyGateEnabled whether policy gate checks are enabled
     */
    public record GovernanceConfig(
        boolean idempotencyEnabled,
        boolean authorizationEnabled,
        boolean policyGateEnabled
    ) {
        public static GovernanceConfig defaults() {
            return new GovernanceConfig(true, true, true);
        }

        public static GovernanceConfig disabled() {
            return new GovernanceConfig(false, false, false);
        }
    }

    /**
     * Tracing configuration.
     *
     * @param enabled whether tracing is enabled
     * @param persistTraces whether traces should be persisted
     * @param traceRetentionDays how long to retain traces
     */
    public record TracingConfig(
        boolean enabled,
        boolean persistTraces,
        int traceRetentionDays
    ) {
        public TracingConfig {
            if (traceRetentionDays <= 0) {
                traceRetentionDays = 90;
            }
        }

        public static TracingConfig defaults() {
            return new TracingConfig(true, true, 90);
        }

        public static TracingConfig forTesting() {
            return new TracingConfig(true, false, 1);
        }
    }

    /**
     * Builder for OrchestratorConfigProperties.
     */
    public static class Builder {
        private boolean enabled = true;
        private int eventQueueCapacity = 10000;
        private long evaluationIntervalMs = 5000;
        private GovernanceConfig governance = GovernanceConfig.defaults();
        private TracingConfig tracing = TracingConfig.defaults();

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder eventQueueCapacity(int capacity) {
            this.eventQueueCapacity = capacity;
            return this;
        }

        public Builder evaluationIntervalMs(long intervalMs) {
            this.evaluationIntervalMs = intervalMs;
            return this;
        }

        public Builder governance(GovernanceConfig governance) {
            this.governance = governance;
            return this;
        }

        public Builder tracing(TracingConfig tracing) {
            this.tracing = tracing;
            return this;
        }

        public Builder idempotencyEnabled(boolean enabled) {
            this.governance = new GovernanceConfig(
                enabled,
                governance.authorizationEnabled(),
                governance.policyGateEnabled()
            );
            return this;
        }

        public Builder authorizationEnabled(boolean enabled) {
            this.governance = new GovernanceConfig(
                governance.idempotencyEnabled(),
                enabled,
                governance.policyGateEnabled()
            );
            return this;
        }

        public Builder policyGateEnabled(boolean enabled) {
            this.governance = new GovernanceConfig(
                governance.idempotencyEnabled(),
                governance.authorizationEnabled(),
                enabled
            );
            return this;
        }

        public OrchestratorConfigProperties build() {
            return new OrchestratorConfigProperties(
                enabled,
                eventQueueCapacity,
                evaluationIntervalMs,
                governance,
                tracing
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
