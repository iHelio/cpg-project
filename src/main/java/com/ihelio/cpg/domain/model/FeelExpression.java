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

package com.ihelio.cpg.domain.model;

import java.util.Objects;

/**
 * A FEEL (Friendly Enough Expression Language) expression.
 *
 * <p>FEEL is the expression language defined by the DMN specification.
 * It is used throughout the process graph for:
 * <ul>
 *   <li>Guard conditions on edges</li>
 *   <li>Preconditions on nodes</li>
 *   <li>Event correlation expressions</li>
 *   <li>Dynamic value derivation</li>
 * </ul>
 *
 * <p>Examples:
 * <pre>
 *   context.employee.location = "US-CA"
 *   context.daysSinceOffer >= 5
 *   starts with(context.location, "EU-")
 *   context.backgroundCheck.status = "COMPLETED" and context.backgroundCheck.passed
 * </pre>
 */
public record FeelExpression(
    String expression,
    String description
) {

    public FeelExpression {
        Objects.requireNonNull(expression, "FEEL expression is required");
        if (expression.isBlank()) {
            throw new IllegalArgumentException("FEEL expression cannot be blank");
        }
    }

    /**
     * Creates a FEEL expression without a description.
     *
     * @param expression the FEEL expression
     * @return the FeelExpression instance
     */
    public static FeelExpression of(String expression) {
        return new FeelExpression(expression, null);
    }

    /**
     * Creates a FEEL expression with a description.
     *
     * @param expression the FEEL expression
     * @param description human-readable description of what the expression checks
     * @return the FeelExpression instance
     */
    public static FeelExpression of(String expression, String description) {
        return new FeelExpression(expression, description);
    }

    @Override
    public String toString() {
        return expression;
    }
}
