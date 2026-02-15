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

package com.ihelio.cpg.application.handler;

import com.ihelio.cpg.domain.action.ActionContext;
import com.ihelio.cpg.domain.action.ActionHandler;
import com.ihelio.cpg.domain.action.ActionResult;
import com.ihelio.cpg.domain.ai.AiAnalystPort;
import com.ihelio.cpg.domain.ai.AiAnalystPort.AiAnalysisException;
import com.ihelio.cpg.domain.ai.BackgroundAnalysisResult;
import com.ihelio.cpg.domain.ai.BackgroundCheckData;
import com.ihelio.cpg.domain.model.Node;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Action handler for AI-powered background check analysis.
 *
 * <p>This handler invokes the AI analyst to evaluate background check findings
 * and produce a risk assessment with recommendation. The result is stored in
 * the execution context for use in downstream edge guards.
 */
@Component
public class AiBackgroundAnalystHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(AiBackgroundAnalystHandler.class);
    private static final String HANDLER_REF = "aiBackgroundAnalyst";

    private final AiAnalystPort aiAnalyst;

    public AiBackgroundAnalystHandler(AiAnalystPort aiAnalyst) {
        this.aiAnalyst = aiAnalyst;
    }

    @Override
    public Node.ActionType supportedType() {
        return Node.ActionType.AGENT_ASSISTED;
    }

    @Override
    public boolean canHandle(String handlerRef) {
        return HANDLER_REF.equals(handlerRef);
    }

    @Override
    public ActionResult execute(ActionContext context) {
        String instanceId = context.processInstance().id().value();
        log.info("Starting AI background analysis for instance: {}", instanceId);

        try {
            BackgroundCheckData checkData = extractBackgroundCheckData(context);

            Map<String, Object> analysisContext = buildAnalysisContext(context);

            BackgroundAnalysisResult result = aiAnalyst.analyzeBackgroundCheck(
                checkData,
                analysisContext
            );

            log.info("AI analysis completed for instance: {}, recommendation: {}, riskScore: {}",
                instanceId, result.recommendation(), result.riskScore());

            Map<String, Object> output = new HashMap<>();
            output.put("aiAnalysis", result.toContextMap());
            output.put("backgroundCheck", Map.of(
                "passed", result.passed(),
                "requiresReview", result.requiresReview(),
                "aiReviewed", true
            ));

            return ActionResult.success(output);

        } catch (AiAnalysisException e) {
            log.error("AI analysis failed for instance: {}", instanceId, e);
            return ActionResult.failure(
                "AI analysis failed: " + e.getMessage(),
                e.isRetryable()
            );
        } catch (Exception e) {
            log.error("Unexpected error during AI analysis for instance: {}", instanceId, e);
            return ActionResult.failure(
                "Unexpected error during AI analysis: " + e.getMessage(),
                true
            );
        }
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @SuppressWarnings("unchecked")
    private BackgroundCheckData extractBackgroundCheckData(ActionContext context) {
        Map<String, Object> feelContext = context.toFeelContext();

        Map<String, Object> backgroundCheckMap = new HashMap<>();

        Object backgroundCheck = feelContext.get("backgroundCheck");
        if (backgroundCheck instanceof Map) {
            backgroundCheckMap.putAll((Map<String, Object>) backgroundCheck);
        }

        Object candidate = feelContext.get("candidate");
        if (candidate instanceof Map) {
            Map<String, Object> candidateMap = (Map<String, Object>) candidate;
            backgroundCheckMap.put("candidateId", candidateMap.getOrDefault("id", "unknown"));
        }

        Object offer = feelContext.get("offer");
        if (offer instanceof Map) {
            Map<String, Object> offerMap = (Map<String, Object>) offer;
            backgroundCheckMap.put("position", offerMap.get("position"));
            backgroundCheckMap.put("department", offerMap.get("department"));
        }

        return BackgroundCheckData.fromContext(backgroundCheckMap);
    }

    private Map<String, Object> buildAnalysisContext(ActionContext context) {
        Map<String, Object> analysisContext = new HashMap<>();

        Map<String, Object> clientContext = context.executionContext().clientContext();
        if (clientContext.containsKey("backgroundCheckPolicy")) {
            analysisContext.put("policy", clientContext.get("backgroundCheckPolicy"));
        }

        analysisContext.putAll(context.ruleOutputs());
        analysisContext.putAll(context.policyOutputs());

        return analysisContext;
    }
}
