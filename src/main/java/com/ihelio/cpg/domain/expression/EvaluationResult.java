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
import java.util.Objects;

/**
 * Result of evaluating a FEEL expression.
 *
 * @param success whether the evaluation succeeded without errors
 * @param result the evaluation result (typically Boolean for conditions)
 * @param expression the expression that was evaluated
 * @param error error message if evaluation failed
 */
public record EvaluationResult(
    boolean success,
    Object result,
    FeelExpression expression,
    String error
) {

    public EvaluationResult {
        Objects.requireNonNull(expression, "EvaluationResult expression is required");
    }

    /**
     * Creates a successful evaluation result.
     */
    public static EvaluationResult success(FeelExpression expression, Object result) {
        return new EvaluationResult(true, result, expression, null);
    }

    /**
     * Creates a failed evaluation result.
     */
    public static EvaluationResult failure(FeelExpression expression, String error) {
        return new EvaluationResult(false, null, expression, error);
    }

    /**
     * Returns the result as a boolean, defaulting to false if not a boolean.
     */
    public boolean asBoolean() {
        if (result instanceof Boolean b) {
            return b;
        }
        return false;
    }

    /**
     * Returns the result cast to the specified type.
     */
    @SuppressWarnings("unchecked")
    public <T> T as(Class<T> type) {
        if (result == null) {
            return null;
        }
        return (T) result;
    }
}
