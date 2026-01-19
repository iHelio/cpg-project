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

import com.ihelio.cpg.application.onboarding.OnboardingProcessGraphBuilder;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Initializes sample process graphs on application startup.
 */
@Component
public class ProcessGraphInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessGraphInitializer.class);

    private final ProcessGraphRepository processGraphRepository;

    public ProcessGraphInitializer(ProcessGraphRepository processGraphRepository) {
        this.processGraphRepository = processGraphRepository;
    }

    @Override
    public void run(String... args) {
        log.info("Initializing sample process graphs...");

        ProcessGraph onboardingGraph = OnboardingProcessGraphBuilder.build();
        processGraphRepository.save(onboardingGraph);

        log.info("Loaded process graph: {} ({})",
            onboardingGraph.name(),
            onboardingGraph.id().value());
    }
}
