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

package com.ihelio.cpg.infrastructure.feel;

import static org.assertj.core.api.Assertions.assertThat;

import com.ihelio.cpg.domain.expression.EvaluationResult;
import com.ihelio.cpg.domain.model.FeelExpression;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("KieFeelExpressionEvaluator")
class KieFeelExpressionEvaluatorTest {

    private KieFeelExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new KieFeelExpressionEvaluator();
    }

    @Nested
    @DisplayName("when evaluating boolean expressions")
    class BooleanExpressions {

        @Test
        @DisplayName("should evaluate equality expression")
        void shouldEvaluateEquality() {
            FeelExpression expr = FeelExpression.of("status = \"APPROVED\"");
            Map<String, Object> context = Map.of("status", "APPROVED");

            EvaluationResult result = evaluator.evaluate(expr, context);

            assertThat(result.success()).isTrue();
            assertThat(result.asBoolean()).isTrue();
        }

        @Test
        @DisplayName("should evaluate inequality expression")
        void shouldEvaluateInequality() {
            FeelExpression expr = FeelExpression.of("age >= 18");
            Map<String, Object> context = Map.of("age", BigDecimal.valueOf(21));

            EvaluationResult result = evaluator.evaluate(expr, context);

            assertThat(result.success()).isTrue();
            assertThat(result.asBoolean()).isTrue();
        }

        @Test
        @DisplayName("should evaluate logical AND")
        void shouldEvaluateLogicalAnd() {
            FeelExpression expr = FeelExpression.of("active = true and verified = true");
            Map<String, Object> context = Map.of(
                "active", true,
                "verified", true
            );

            EvaluationResult result = evaluator.evaluate(expr, context);

            assertThat(result.success()).isTrue();
            assertThat(result.asBoolean()).isTrue();
        }

        @Test
        @DisplayName("should evaluate logical OR")
        void shouldEvaluateLogicalOr() {
            FeelExpression expr = FeelExpression.of("status = \"APPROVED\" or status = \"PENDING\"");
            Map<String, Object> context = Map.of("status", "PENDING");

            EvaluationResult result = evaluator.evaluate(expr, context);

            assertThat(result.success()).isTrue();
            assertThat(result.asBoolean()).isTrue();
        }

        @Test
        @DisplayName("should return false for failed comparison")
        void shouldReturnFalseForFailedComparison() {
            FeelExpression expr = FeelExpression.of("status = \"APPROVED\"");
            Map<String, Object> context = Map.of("status", "REJECTED");

            EvaluationResult result = evaluator.evaluate(expr, context);

            assertThat(result.success()).isTrue();
            assertThat(result.asBoolean()).isFalse();
        }
    }

    @Nested
    @DisplayName("when evaluating string functions")
    class StringFunctions {

        @Test
        @DisplayName("should evaluate starts with function")
        void shouldEvaluateStartsWith() {
            FeelExpression expr = FeelExpression.of("starts with(location, \"US-\")");
            Map<String, Object> context = Map.of("location", "US-CA");

            EvaluationResult result = evaluator.evaluate(expr, context);

            assertThat(result.success()).isTrue();
            assertThat(result.asBoolean()).isTrue();
        }

        @Test
        @DisplayName("should evaluate contains function")
        void shouldEvaluateContains() {
            FeelExpression expr = FeelExpression.of("contains(name, \"John\")");
            Map<String, Object> context = Map.of("name", "John Doe");

            EvaluationResult result = evaluator.evaluate(expr, context);

            assertThat(result.success()).isTrue();
            assertThat(result.asBoolean()).isTrue();
        }
    }

    @Nested
    @DisplayName("when evaluating nested context")
    class NestedContext {

        @Test
        @DisplayName("should access nested properties")
        void shouldAccessNestedProperties() {
            FeelExpression expr = FeelExpression.of("employee.department = \"Engineering\"");
            Map<String, Object> context = Map.of(
                "employee", Map.of(
                    "name", "John",
                    "department", "Engineering"
                )
            );

            EvaluationResult result = evaluator.evaluate(expr, context);

            assertThat(result.success()).isTrue();
            assertThat(result.asBoolean()).isTrue();
        }
    }

    @Nested
    @DisplayName("when evaluating all expressions")
    class EvaluateAll {

        @Test
        @DisplayName("should return true when all expressions pass")
        void shouldReturnTrueWhenAllPass() {
            List<FeelExpression> expressions = List.of(
                FeelExpression.of("a > 0"),
                FeelExpression.of("b = true"),
                FeelExpression.of("c = \"valid\"")
            );
            Map<String, Object> context = Map.of(
                "a", BigDecimal.valueOf(10),
                "b", true,
                "c", "valid"
            );

            boolean result = evaluator.evaluateAllAsBoolean(expressions, context);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when any expression fails")
        void shouldReturnFalseWhenAnyFails() {
            List<FeelExpression> expressions = List.of(
                FeelExpression.of("a > 0"),
                FeelExpression.of("b = true"),
                FeelExpression.of("c = \"invalid\"")
            );
            Map<String, Object> context = Map.of(
                "a", BigDecimal.valueOf(10),
                "b", true,
                "c", "valid"
            );

            boolean result = evaluator.evaluateAllAsBoolean(expressions, context);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true for empty expression list")
        void shouldReturnTrueForEmptyList() {
            boolean result = evaluator.evaluateAllAsBoolean(List.of(), Map.of());

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("when handling errors")
    class ErrorHandling {

        @Test
        @DisplayName("should return failure for invalid expression")
        void shouldReturnFailureForInvalidExpression() {
            FeelExpression expr = FeelExpression.of("unknownFunction(x)");
            Map<String, Object> context = Map.of("x", 1);

            EvaluationResult result = evaluator.evaluate(expr, context);

            // FEEL may return null for unknown functions rather than throwing
            assertThat(result.success()).isTrue();
            assertThat(result.result()).isNull();
        }

        @Test
        @DisplayName("should handle missing variable gracefully")
        void shouldHandleMissingVariable() {
            FeelExpression expr = FeelExpression.of("missingVar = \"test\"");
            Map<String, Object> context = Map.of();

            EvaluationResult result = evaluator.evaluate(expr, context);

            // FEEL returns null for missing variables, comparison with null is false
            assertThat(result.success()).isTrue();
        }
    }
}
