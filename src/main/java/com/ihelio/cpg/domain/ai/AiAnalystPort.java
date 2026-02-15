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

package com.ihelio.cpg.domain.ai;

import java.util.Map;

/**
 * Port for AI-powered analysis operations.
 *
 * <p>This port defines the interface for AI analyst capabilities that can be
 * implemented by various AI providers (e.g., Claude, OpenAI, or stub implementations
 * for testing).
 */
public interface AiAnalystPort {

    /**
     * Analyzes background check data and produces a risk assessment.
     *
     * <p>The AI analyst evaluates the background check findings considering the
     * position requirements and organizational context to produce a recommendation.
     *
     * @param data the background check data to analyze
     * @param context additional context for the analysis (e.g., company policies)
     * @return the analysis result with risk score and recommendation
     * @throws AiAnalysisException if the analysis fails
     */
    BackgroundAnalysisResult analyzeBackgroundCheck(
        BackgroundCheckData data,
        Map<String, Object> context
    );

    /**
     * Exception thrown when AI analysis fails.
     */
    class AiAnalysisException extends RuntimeException {

        private final boolean retryable;

        public AiAnalysisException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        public AiAnalysisException(String message, Throwable cause, boolean retryable) {
            super(message, cause);
            this.retryable = retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }
}
