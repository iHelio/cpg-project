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

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.kie.api.io.ResourceType;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNDecisionResult;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.core.internal.utils.DMNRuntimeBuilder;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

/**
 * Service for evaluating DMN (Decision Model and Notation) decisions.
 *
 * <p>This service loads DMN files from the classpath and provides methods
 * to evaluate decisions based on input context. Business users can maintain
 * the DMN decision tables without code changes.
 */
@Service
public class DmnDecisionService {

    private static final Logger log = LoggerFactory.getLogger(DmnDecisionService.class);
    private static final String DMN_RESOURCE_PATTERN = "classpath:dmn/*.dmn";

    private final Map<String, DMNModel> modelCache = new ConcurrentHashMap<>();
    private DMNRuntime dmnRuntime;

    @PostConstruct
    public void init() throws IOException {
        loadDmnModels();
    }

    private void loadDmnModels() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(DMN_RESOURCE_PATTERN);

        List<org.kie.api.io.Resource> kieResources = new ArrayList<>();
        for (Resource resource : resources) {
            org.kie.api.io.Resource kieResource = ResourceFactory.newInputStreamResource(
                resource.getInputStream()
            ).setResourceType(ResourceType.DMN);
            kieResources.add(kieResource);
            log.info("Loading DMN resource: {}", resource.getFilename());
        }

        this.dmnRuntime = DMNRuntimeBuilder.fromDefaults()
            .buildConfiguration()
            .fromResources(kieResources)
            .getOrElseThrow(e -> new RuntimeException("Failed to build DMN runtime: " + e));

        for (DMNModel model : dmnRuntime.getModels()) {
            modelCache.put(model.getName(), model);
            log.info("Registered DMN model: {} with decisions: {}",
                model.getName(),
                model.getDecisions().stream()
                    .map(d -> d.getName())
                    .toList());
        }

        log.info("DMN runtime initialized with {} models", modelCache.size());
    }

    /**
     * Evaluates a specific decision within a DMN model.
     *
     * @param modelName the name of the DMN model
     * @param decisionName the name of the decision to evaluate
     * @param context the input variables for the decision
     * @return the decision result
     */
    public DecisionResult evaluate(String modelName, String decisionName, Map<String, Object> context) {
        DMNModel model = modelCache.get(modelName);
        if (model == null) {
            throw new IllegalArgumentException("DMN model not found: " + modelName);
        }

        DMNContext dmnContext = dmnRuntime.newContext();
        context.forEach(dmnContext::set);

        DMNResult result = dmnRuntime.evaluateByName(model, dmnContext, decisionName);

        if (result.hasErrors()) {
            log.warn("DMN evaluation errors for {}.{}: {}",
                modelName, decisionName, result.getMessages());
            return DecisionResult.error(result.getMessages().toString());
        }

        Optional<DMNDecisionResult> decisionResult = result.getDecisionResults().stream()
            .filter(dr -> dr.getDecisionName().equals(decisionName))
            .findFirst();

        return decisionResult
            .map(dr -> DecisionResult.success(dr.getResult()))
            .orElse(DecisionResult.error("Decision not found: " + decisionName));
    }

    /**
     * Evaluates all decisions in a DMN model.
     *
     * @param modelName the name of the DMN model
     * @param context the input variables for the decisions
     * @return map of decision names to their results
     */
    public Map<String, DecisionResult> evaluateAll(String modelName, Map<String, Object> context) {
        DMNModel model = modelCache.get(modelName);
        if (model == null) {
            throw new IllegalArgumentException("DMN model not found: " + modelName);
        }

        DMNContext dmnContext = dmnRuntime.newContext();
        context.forEach(dmnContext::set);

        DMNResult result = dmnRuntime.evaluateAll(model, dmnContext);

        Map<String, DecisionResult> results = new ConcurrentHashMap<>();
        for (DMNDecisionResult dr : result.getDecisionResults()) {
            if (dr.hasErrors()) {
                results.put(dr.getDecisionName(), DecisionResult.error(dr.getMessages().toString()));
            } else {
                results.put(dr.getDecisionName(), DecisionResult.success(dr.getResult()));
            }
        }

        return results;
    }

    /**
     * Result of a DMN decision evaluation.
     *
     * @param success whether the evaluation succeeded
     * @param value the result value if successful
     * @param error the error message if failed
     */
    public record DecisionResult(boolean success, Object value, String error) {

        public static DecisionResult success(Object value) {
            return new DecisionResult(true, value, null);
        }

        public static DecisionResult error(String error) {
            return new DecisionResult(false, null, error);
        }

        @SuppressWarnings("unchecked")
        public <T> T getValueAs(Class<T> type) {
            if (!success || value == null) {
                return null;
            }
            return (T) value;
        }
    }
}
