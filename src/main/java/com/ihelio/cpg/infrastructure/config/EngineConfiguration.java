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

import com.ihelio.cpg.application.handler.ActionHandlerRegistry;
import com.ihelio.cpg.domain.engine.CompensationHandler;
import com.ihelio.cpg.domain.engine.EdgeEvaluator;
import com.ihelio.cpg.domain.engine.ExecutionCoordinator;
import com.ihelio.cpg.domain.engine.NodeEvaluator;
import com.ihelio.cpg.domain.engine.ProcessExecutionEngine;
import com.ihelio.cpg.domain.event.ProcessEventPublisher;
import com.ihelio.cpg.domain.expression.ExpressionEvaluator;
import com.ihelio.cpg.domain.policy.PolicyEvaluator;
import com.ihelio.cpg.domain.rule.RuleEvaluator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the process execution engine components.
 *
 * <p>Wires up the domain engine classes with their infrastructure dependencies,
 * following the hexagonal architecture pattern where domain classes don't
 * have Spring annotations.
 */
@Configuration
public class EngineConfiguration {

    @Bean
    public NodeEvaluator nodeEvaluator(
            ExpressionEvaluator expressionEvaluator,
            PolicyEvaluator policyEvaluator,
            RuleEvaluator ruleEvaluator) {
        return new NodeEvaluator(expressionEvaluator, policyEvaluator, ruleEvaluator);
    }

    @Bean
    public EdgeEvaluator edgeEvaluator(ExpressionEvaluator expressionEvaluator) {
        return new EdgeEvaluator(expressionEvaluator);
    }

    @Bean
    public ExecutionCoordinator executionCoordinator() {
        return new ExecutionCoordinator();
    }

    @Bean
    public CompensationHandler compensationHandler() {
        return new CompensationHandler();
    }

    @Bean
    public ProcessExecutionEngine processExecutionEngine(
            NodeEvaluator nodeEvaluator,
            EdgeEvaluator edgeEvaluator,
            ExecutionCoordinator executionCoordinator,
            CompensationHandler compensationHandler,
            ProcessEventPublisher eventPublisher,
            ActionHandlerRegistry actionHandlerRegistry) {
        return new ProcessExecutionEngine(
            nodeEvaluator,
            edgeEvaluator,
            executionCoordinator,
            compensationHandler,
            eventPublisher,
            actionHandlerRegistry.asResolver()
        );
    }
}
