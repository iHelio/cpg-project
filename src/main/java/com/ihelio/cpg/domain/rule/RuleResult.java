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
import java.util.Map;
import java.util.Objects;

/**
 * Result of evaluating a business rule.
 *
 * @param businessRule the business rule that was evaluated
 * @param success whether the evaluation succeeded
 * @param output the output values from the rule
 * @param error error message if evaluation failed
 */
public record RuleResult(
    Node.BusinessRule businessRule,
    boolean success,
    Map<String, Object> output,
    String error
) {

    public RuleResult {
        Objects.requireNonNull(businessRule, "RuleResult businessRule is required");
        output = output != null ? Map.copyOf(output) : Map.of();
    }

    /**
     * Creates a successful rule result with output.
     */
    public static RuleResult success(Node.BusinessRule rule, Map<String, Object> output) {
        return new RuleResult(rule, true, output, null);
    }

    /**
     * Creates a successful rule result with a single output value.
     */
    public static RuleResult success(Node.BusinessRule rule, String key, Object value) {
        return new RuleResult(rule, true, Map.of(key, value), null);
    }

    /**
     * Creates a failed rule result.
     */
    public static RuleResult failure(Node.BusinessRule rule, String error) {
        return new RuleResult(rule, false, Map.of(), error);
    }

    /**
     * Returns a specific output value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getOutput(String key) {
        return (T) output.get(key);
    }

    /**
     * Checks if the rule produced any output.
     */
    public boolean hasOutput() {
        return !output.isEmpty();
    }
}
