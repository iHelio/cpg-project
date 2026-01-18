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

package com.ihelio.cpg.domain.action;

import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Node;
import java.util.Map;
import java.util.Objects;

/**
 * Context passed to action handlers during execution.
 *
 * <p>Provides all necessary information for an action to execute,
 * including the node definition, process instance, and execution context.
 *
 * @param node the node whose action is being executed
 * @param processInstance the process instance
 * @param executionContext the current execution context
 * @param ruleOutputs outputs from evaluated business rules
 * @param policyOutputs outputs from evaluated policy gates
 */
public record ActionContext(
    Node node,
    ProcessInstance processInstance,
    ExecutionContext executionContext,
    Map<String, Object> ruleOutputs,
    Map<String, Object> policyOutputs
) {

    public ActionContext {
        Objects.requireNonNull(node, "ActionContext node is required");
        Objects.requireNonNull(processInstance, "ActionContext processInstance is required");
        Objects.requireNonNull(executionContext, "ActionContext executionContext is required");
        ruleOutputs = ruleOutputs != null ? Map.copyOf(ruleOutputs) : Map.of();
        policyOutputs = policyOutputs != null ? Map.copyOf(policyOutputs) : Map.of();
    }

    /**
     * Creates an action context with minimal information.
     */
    public static ActionContext of(Node node, ProcessInstance instance, ExecutionContext context) {
        return new ActionContext(node, instance, context, Map.of(), Map.of());
    }

    /**
     * Returns the action configuration from the node.
     */
    public Node.Action action() {
        return node.action();
    }

    /**
     * Returns the action handler reference.
     */
    public String handlerRef() {
        return node.action().handlerRef();
    }

    /**
     * Returns a value from the execution context by path.
     */
    public Object getValue(String path) {
        return executionContext.getValue(path);
    }

    /**
     * Returns the flat context map for FEEL evaluation.
     */
    public Map<String, Object> toFeelContext() {
        return executionContext.toFeelContext();
    }
}
