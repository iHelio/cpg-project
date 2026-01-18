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

package com.ihelio.cpg.domain.expression;

import com.ihelio.cpg.domain.model.FeelExpression;
import java.util.List;
import java.util.Map;

/**
 * Port for evaluating FEEL (Friendly Enough Expression Language) expressions.
 *
 * <p>This is a domain port that defines the contract for expression evaluation.
 * The actual implementation is provided by infrastructure adapters.
 */
public interface ExpressionEvaluator {

    /**
     * Evaluates a single FEEL expression against the provided context.
     *
     * @param expression the FEEL expression to evaluate
     * @param context the variable context for evaluation
     * @return the evaluation result
     */
    EvaluationResult evaluate(FeelExpression expression, Map<String, Object> context);

    /**
     * Evaluates multiple FEEL expressions against the provided context.
     * Returns results for all expressions.
     *
     * @param expressions the FEEL expressions to evaluate
     * @param context the variable context for evaluation
     * @return list of evaluation results in the same order as input
     */
    List<EvaluationResult> evaluateAll(List<FeelExpression> expressions, Map<String, Object> context);

    /**
     * Evaluates multiple FEEL expressions and returns true only if all pass.
     * Short-circuits on first failure.
     *
     * @param expressions the FEEL expressions to evaluate
     * @param context the variable context for evaluation
     * @return true if all expressions evaluate to true
     */
    default boolean evaluateAllAsBoolean(List<FeelExpression> expressions, Map<String, Object> context) {
        if (expressions == null || expressions.isEmpty()) {
            return true;
        }
        for (FeelExpression expr : expressions) {
            EvaluationResult result = evaluate(expr, context);
            if (!result.success() || !result.asBoolean()) {
                return false;
            }
        }
        return true;
    }
}
