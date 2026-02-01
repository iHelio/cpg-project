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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the MCP (Model Context Protocol) server.
 *
 * <p>The MCP server exposes the CPG orchestration engine to AI clients
 * via SSE/HTTP transport. It co-hosts with the existing REST API on
 * the same Spring WebMVC server.
 *
 * <p>MCP capabilities (tools, resources, prompts) are auto-discovered
 * via annotation scanning of {@code @McpTool}, {@code @McpResource},
 * and {@code @McpPrompt} annotated beans.
 *
 * <p>Disable the MCP server by setting {@code cpg.mcp.enabled=false}.
 */
@Configuration
@ConditionalOnProperty(name = "cpg.mcp.enabled", havingValue = "true", matchIfMissing = true)
public class McpServerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpServerConfiguration.class);

    public McpServerConfiguration() {
        log.info("MCP server configuration loaded - orchestration tools, resources, "
            + "and prompts will be exposed via SSE/HTTP");
    }
}
