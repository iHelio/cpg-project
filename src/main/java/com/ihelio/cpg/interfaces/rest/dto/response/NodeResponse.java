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

package com.ihelio.cpg.interfaces.rest.dto.response;

import com.ihelio.cpg.domain.model.Node;
import java.util.List;

/**
 * Response representing a node in a process graph.
 *
 * @param id node identifier
 * @param name human-readable name
 * @param description node description
 * @param version node version
 * @param actionType type of action (SYSTEM_INVOCATION, HUMAN_TASK, etc.)
 * @param actionHandler handler reference for the action
 * @param policyGateIds IDs of policy gates on this node
 * @param businessRuleIds IDs of business rules on this node
 */
public record NodeResponse(
    String id,
    String name,
    String description,
    int version,
    String actionType,
    String actionHandler,
    List<String> policyGateIds,
    List<String> businessRuleIds
) {
    /**
     * Creates a node response from a Node domain object.
     */
    public static NodeResponse from(Node node) {
        return new NodeResponse(
            node.id().value(),
            node.name(),
            node.description(),
            node.version(),
            node.action().type().name(),
            node.action().handlerRef(),
            node.policyGates().stream().map(Node.PolicyGate::id).toList(),
            node.businessRules().stream().map(Node.BusinessRule::id).toList()
        );
    }
}
