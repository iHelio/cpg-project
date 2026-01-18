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

import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.rule.RuleEvaluator;
import com.ihelio.cpg.domain.rule.RuleResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DMN-based implementation of the RuleEvaluator port.
 *
 * <p>Wraps the DmnDecisionService to evaluate business rules using
 * DMN decision tables. Business users can maintain the decision
 * tables without code changes.
 */
@Component
public class DmnRuleEvaluator implements RuleEvaluator {

    private static final Logger log = LoggerFactory.getLogger(DmnRuleEvaluator.class);
    private static final String DEFAULT_RULE_MODEL = "BusinessRules";

    private final DmnDecisionService dmnDecisionService;

    public DmnRuleEvaluator(DmnDecisionService dmnDecisionService) {
        this.dmnDecisionService = dmnDecisionService;
    }

    @Override
    public RuleResult evaluate(Node.BusinessRule rule, Map<String, Object> context) {
        String decisionRef = rule.dmnDecisionRef();

        if (decisionRef == null || decisionRef.isBlank()) {
            // No DMN decision configured - return empty output
            log.debug("Business rule {} has no DMN decision configured", rule.id());
            return RuleResult.success(rule, Map.of());
        }

        try {
            // Parse decision reference (format: "modelName.decisionName" or just "decisionName")
            String modelName = DEFAULT_RULE_MODEL;
            String decisionName = decisionRef;

            if (decisionRef.contains(".")) {
                String[] parts = decisionRef.split("\\.", 2);
                modelName = parts[0];
                decisionName = parts[1];
            }

            DmnDecisionService.DecisionResult result =
                dmnDecisionService.evaluate(modelName, decisionName, context);

            if (!result.success()) {
                log.warn("Rule DMN evaluation failed for {}: {}", rule.id(), result.error());
                return RuleResult.failure(rule, result.error());
            }

            // Convert result to output map
            Map<String, Object> output = convertToOutputMap(rule, result.value());

            log.debug("Business rule {} evaluated with output: {}", rule.id(), output);
            return RuleResult.success(rule, output);

        } catch (IllegalArgumentException e) {
            // Model not found - return empty output with warning
            log.warn("DMN model not found for rule {}: {}", rule.id(), e.getMessage());
            return RuleResult.success(rule, Map.of(
                "_warning", "DMN model not found: " + e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Rule evaluation error for {}: {}", rule.id(), e.getMessage());
            return RuleResult.failure(rule, e.getMessage());
        }
    }

    @Override
    public List<RuleResult> evaluateAll(
            List<Node.BusinessRule> rules,
            Map<String, Object> context) {

        List<RuleResult> results = new ArrayList<>();
        for (Node.BusinessRule rule : rules) {
            results.add(evaluate(rule, context));
        }
        return results;
    }

    @Override
    public Map<String, Object> evaluateAndMergeOutputs(
            List<Node.BusinessRule> rules,
            Map<String, Object> context) {

        Map<String, Object> merged = new HashMap<>();

        for (Node.BusinessRule rule : rules) {
            RuleResult result = evaluate(rule, context);
            if (result.success()) {
                merged.putAll(result.output());
            }
        }

        return merged;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToOutputMap(Node.BusinessRule rule, Object value) {
        if (value == null) {
            return Map.of();
        }

        // If already a map, use it directly
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }

        // If a list of maps (multiple rows), merge them
        if (value instanceof List<?> listValue && !listValue.isEmpty()) {
            if (listValue.get(0) instanceof Map) {
                Map<String, Object> result = new HashMap<>();
                for (Object item : listValue) {
                    Map<?, ?> itemMap = (Map<?, ?>) item;
                    for (Map.Entry<?, ?> entry : itemMap.entrySet()) {
                        result.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                return result;
            }
            // List of simple values - use rule ID as key
            return Map.of(rule.id(), listValue);
        }

        // Single value - use rule category to determine key
        String key = determineOutputKey(rule);
        return Map.of(key, value);
    }

    private String determineOutputKey(Node.BusinessRule rule) {
        // Use category-based key naming
        return switch (rule.category()) {
            case EXECUTION_PARAMETER -> rule.name() != null
                ? camelCase(rule.name())
                : rule.id();
            case OBLIGATION -> "obligation_" + rule.id();
            case SLA -> "sla_" + rule.id();
            case DERIVATION -> rule.name() != null
                ? camelCase(rule.name())
                : "derived_" + rule.id();
        };
    }

    private String camelCase(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        // Simple camelCase conversion
        String[] words = input.toLowerCase().split("[\\s_-]+");
        StringBuilder result = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            if (!words[i].isEmpty()) {
                result.append(Character.toUpperCase(words[i].charAt(0)));
                result.append(words[i].substring(1));
            }
        }
        return result.toString();
    }
}
