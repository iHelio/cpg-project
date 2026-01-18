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

import com.ihelio.cpg.domain.expression.EvaluationResult;
import com.ihelio.cpg.domain.expression.ExpressionEvaluator;
import com.ihelio.cpg.domain.model.FeelExpression;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.kie.dmn.feel.FEEL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * KIE FEEL implementation of the ExpressionEvaluator port.
 *
 * <p>Uses the Drools FEEL engine to evaluate FEEL expressions.
 * Thread-safe for concurrent evaluations.
 */
@Component
public class KieFeelExpressionEvaluator implements ExpressionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(KieFeelExpressionEvaluator.class);

    private final FEEL feel;

    public KieFeelExpressionEvaluator() {
        this.feel = FEEL.newInstance();
    }

    @Override
    public EvaluationResult evaluate(FeelExpression expression, Map<String, Object> context) {
        try {
            Object result = feel.evaluate(expression.expression(), context);

            if (result == null) {
                log.debug("FEEL expression '{}' evaluated to null", expression.expression());
                return EvaluationResult.success(expression, null);
            }

            log.debug("FEEL expression '{}' evaluated to: {}", expression.expression(), result);
            return EvaluationResult.success(expression, result);

        } catch (Exception e) {
            log.warn("FEEL expression evaluation failed: '{}' - {}",
                expression.expression(), e.getMessage());
            return EvaluationResult.failure(expression, e.getMessage());
        }
    }

    @Override
    public List<EvaluationResult> evaluateAll(
            List<FeelExpression> expressions,
            Map<String, Object> context) {

        List<EvaluationResult> results = new ArrayList<>();
        for (FeelExpression expression : expressions) {
            results.add(evaluate(expression, context));
        }
        return results;
    }

    @Override
    public boolean evaluateAllAsBoolean(
            List<FeelExpression> expressions,
            Map<String, Object> context) {

        if (expressions == null || expressions.isEmpty()) {
            return true;
        }

        for (FeelExpression expression : expressions) {
            EvaluationResult result = evaluate(expression, context);
            if (!result.success()) {
                log.debug("Expression failed: {}", expression.expression());
                return false;
            }
            if (!result.asBoolean()) {
                log.debug("Expression returned false: {}", expression.expression());
                return false;
            }
        }
        return true;
    }
}
