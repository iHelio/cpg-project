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

package com.ihelio.cpg.domain.rule;

import com.ihelio.cpg.domain.model.Node;
import java.util.List;
import java.util.Map;

/**
 * Port for evaluating business rules.
 *
 * <p>Business rules derive execution parameters, obligations, SLAs, and
 * other computed values. They are evaluated using DMN decision tables.
 */
public interface RuleEvaluator {

    /**
     * Evaluates a single business rule.
     *
     * @param rule the business rule to evaluate
     * @param context the variable context for evaluation
     * @return the rule evaluation result
     */
    RuleResult evaluate(Node.BusinessRule rule, Map<String, Object> context);

    /**
     * Evaluates multiple business rules.
     *
     * @param rules the business rules to evaluate
     * @param context the variable context for evaluation
     * @return list of rule results in the same order as input
     */
    List<RuleResult> evaluateAll(List<Node.BusinessRule> rules, Map<String, Object> context);

    /**
     * Evaluates all rules and merges their outputs into a single map.
     *
     * @param rules the business rules to evaluate
     * @param context the variable context for evaluation
     * @return merged output from all rules
     */
    default Map<String, Object> evaluateAndMergeOutputs(
            List<Node.BusinessRule> rules, Map<String, Object> context) {
        if (rules == null || rules.isEmpty()) {
            return Map.of();
        }
        var merged = new java.util.HashMap<String, Object>();
        for (Node.BusinessRule rule : rules) {
            RuleResult result = evaluate(rule, context);
            if (result.success()) {
                merged.putAll(result.output());
            }
        }
        return merged;
    }
}
