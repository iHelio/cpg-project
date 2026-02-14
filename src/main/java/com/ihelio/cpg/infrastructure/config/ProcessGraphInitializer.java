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

import com.ihelio.cpg.application.document.DocumentProcessGraphBuilder;
import com.ihelio.cpg.application.expense.ExpenseProcessGraphBuilder;
import com.ihelio.cpg.application.onboarding.OnboardingProcessGraphBuilder;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Initializes sample process graphs on application startup.
 *
 * <p>Pre-loads the following example workflows:
 * <ul>
 *   <li>Employee Onboarding (12 nodes, 18 edges)</li>
 *   <li>Expense Approval (7 nodes, 9 edges)</li>
 *   <li>Document Review (8 nodes, 10 edges)</li>
 * </ul>
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

        List<ProcessGraph> graphs = List.of(
            OnboardingProcessGraphBuilder.build(),
            ExpenseProcessGraphBuilder.build(),
            DocumentProcessGraphBuilder.build()
        );

        for (ProcessGraph graph : graphs) {
            processGraphRepository.save(graph);
            log.info("Loaded process graph: {} ({}) - {} nodes, {} edges",
                graph.name(),
                graph.id().value(),
                graph.nodes().size(),
                graph.edges().size());
        }

        log.info("Initialized {} sample process graphs", graphs.size());
    }
}
