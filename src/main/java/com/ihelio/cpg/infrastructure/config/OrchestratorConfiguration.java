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

package com.ihelio.cpg.infrastructure.config;

import com.ihelio.cpg.application.orchestration.ContextAssembler;
import com.ihelio.cpg.application.orchestration.EligibilityEvaluator;
import com.ihelio.cpg.application.orchestration.InstanceOrchestrator;
import com.ihelio.cpg.application.orchestration.NavigationDecider;
import com.ihelio.cpg.domain.engine.EdgeEvaluator;
import com.ihelio.cpg.domain.engine.NodeEvaluator;
import com.ihelio.cpg.domain.engine.ProcessExecutionEngine;
import com.ihelio.cpg.domain.orchestration.DecisionTracer;
import com.ihelio.cpg.domain.orchestration.ExecutionGovernor;
import com.ihelio.cpg.domain.orchestration.NodeSelector;
import com.ihelio.cpg.domain.orchestration.ProcessOrchestrator;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import com.ihelio.cpg.domain.repository.ProcessInstanceRepository;
import com.ihelio.cpg.infrastructure.orchestration.DefaultDecisionTracer;
import com.ihelio.cpg.infrastructure.orchestration.DefaultExecutionGovernor;
import com.ihelio.cpg.infrastructure.orchestration.DefaultProcessOrchestrator;
import com.ihelio.cpg.infrastructure.orchestration.OrchestratorConfigProperties;
import com.ihelio.cpg.infrastructure.orchestration.OrchestratorEventSubscriber;
import com.ihelio.cpg.infrastructure.persistence.DecisionTraceRepository;
import com.ihelio.cpg.infrastructure.persistence.InMemoryDecisionTraceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the Process Orchestrator.
 *
 * <p>Configures all orchestration components as Spring beans with proper wiring.
 * The orchestrator can be enabled/disabled via configuration:
 *
 * <pre>
 * cpg:
 *   orchestrator:
 *     enabled: true
 * </pre>
 */
@Configuration
@ConditionalOnProperty(name = "cpg.orchestrator.enabled", havingValue = "true", matchIfMissing = true)
public class OrchestratorConfiguration {

    /**
     * Configuration properties for the orchestrator.
     */
    @Bean
    @ConfigurationProperties(prefix = "cpg.orchestrator")
    public OrchestratorConfigProperties orchestratorConfigProperties() {
        return OrchestratorConfigProperties.defaults();
    }

    /**
     * In-memory decision trace repository.
     * Override this bean for production to use a persistent implementation.
     */
    @Bean
    @ConditionalOnMissingBean(DecisionTraceRepository.class)
    public DecisionTraceRepository decisionTraceRepository() {
        return new InMemoryDecisionTraceRepository();
    }

    /**
     * Context assembler for building runtime context.
     */
    @Bean
    @ConditionalOnMissingBean(ContextAssembler.class)
    public ContextAssembler contextAssembler() {
        return new ContextAssembler();
    }

    /**
     * Eligibility evaluator for building eligible space.
     */
    @Bean
    @ConditionalOnMissingBean(EligibilityEvaluator.class)
    public EligibilityEvaluator eligibilityEvaluator(
            NodeEvaluator nodeEvaluator,
            EdgeEvaluator edgeEvaluator) {
        return new EligibilityEvaluator(nodeEvaluator, edgeEvaluator);
    }

    /**
     * Node selector for deterministic action selection.
     */
    @Bean
    @ConditionalOnMissingBean(NodeSelector.class)
    public NodeSelector nodeSelector() {
        return new NavigationDecider();
    }

    /**
     * Execution governor for pre-execution governance checks.
     */
    @Bean
    @ConditionalOnMissingBean(ExecutionGovernor.class)
    public ExecutionGovernor executionGovernor(OrchestratorConfigProperties config) {
        return new DefaultExecutionGovernor(config);
    }

    /**
     * Decision tracer for recording decision traces.
     */
    @Bean
    @ConditionalOnMissingBean(DecisionTracer.class)
    public DecisionTracer decisionTracer(
            DecisionTraceRepository repository,
            OrchestratorConfigProperties config) {
        return new DefaultDecisionTracer(repository, config);
    }

    /**
     * Instance orchestrator for orchestrating single process instances.
     */
    @Bean
    @ConditionalOnMissingBean(InstanceOrchestrator.class)
    public InstanceOrchestrator instanceOrchestrator(
            ContextAssembler contextAssembler,
            EligibilityEvaluator eligibilityEvaluator,
            NodeSelector nodeSelector,
            ExecutionGovernor executionGovernor,
            ProcessExecutionEngine executionEngine,
            DecisionTracer decisionTracer) {
        return new InstanceOrchestrator(
            contextAssembler,
            eligibilityEvaluator,
            nodeSelector,
            executionGovernor,
            executionEngine,
            decisionTracer
        );
    }

    /**
     * Main process orchestrator.
     */
    @Bean
    @ConditionalOnMissingBean(ProcessOrchestrator.class)
    public ProcessOrchestrator processOrchestrator(
            InstanceOrchestrator instanceOrchestrator,
            ContextAssembler contextAssembler,
            ProcessGraphRepository graphRepository,
            ProcessInstanceRepository instanceRepository,
            DecisionTracer decisionTracer,
            OrchestratorConfigProperties config) {
        return new DefaultProcessOrchestrator(
            instanceOrchestrator,
            contextAssembler,
            graphRepository,
            instanceRepository,
            decisionTracer,
            config
        );
    }

    /**
     * Event subscriber that bridges ProcessEvent to OrchestrationEvent.
     */
    @Bean
    @ConditionalOnMissingBean(OrchestratorEventSubscriber.class)
    public OrchestratorEventSubscriber orchestratorEventSubscriber(
            ProcessOrchestrator orchestrator) {
        return new OrchestratorEventSubscriber(orchestrator);
    }
}
