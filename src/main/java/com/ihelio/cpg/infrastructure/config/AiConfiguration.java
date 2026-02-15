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

package com.ihelio.cpg.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihelio.cpg.domain.ai.AiAnalystPort;
import com.ihelio.cpg.infrastructure.ai.ClaudeAiAnalystAdapter;
import com.ihelio.cpg.infrastructure.ai.StubAiAnalystAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/**
 * Configuration for AI-powered analysis features.
 *
 * <p>Provides conditional bean configuration for either the real Claude AI adapter
 * or a stub adapter for testing and development without an API key.
 */
@Configuration
public class AiConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiConfiguration.class);

    @Bean
    @ConfigurationProperties(prefix = "cpg.ai")
    public AiConfigProperties aiConfigProperties() {
        return new AiConfigProperties();
    }

    @Bean
    @ConditionalOnProperty(name = "cpg.ai.enabled", havingValue = "true")
    public AiAnalystPort claudeAiAnalyst(
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader) {
        log.info("Configuring Claude AI analyst adapter");
        return new ClaudeAiAnalystAdapter(chatClientBuilder, objectMapper, resourceLoader);
    }

    @Bean
    @ConditionalOnProperty(name = "cpg.ai.enabled", havingValue = "false", matchIfMissing = true)
    public AiAnalystPort stubAiAnalyst() {
        log.info("Configuring stub AI analyst adapter (AI disabled)");
        return new StubAiAnalystAdapter();
    }

    /**
     * Configuration properties for AI features.
     */
    public static class AiConfigProperties {

        private boolean enabled = false;
        private int timeoutSeconds = 120;
        private int maxRetries = 2;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }
    }
}
