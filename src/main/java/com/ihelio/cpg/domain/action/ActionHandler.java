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

import com.ihelio.cpg.domain.model.Node;
import java.util.concurrent.CompletableFuture;

/**
 * Port for action execution handlers.
 *
 * <p>Action handlers implement the actual business logic for node actions.
 * Each handler type supports specific action types and handler references.
 */
public interface ActionHandler {

    /**
     * Returns the action type this handler supports.
     *
     * @return the supported action type
     */
    Node.ActionType supportedType();

    /**
     * Checks if this handler can process the given handler reference.
     *
     * @param handlerRef the handler reference from the action
     * @return true if this handler can process the reference
     */
    boolean canHandle(String handlerRef);

    /**
     * Executes the action synchronously.
     *
     * @param context the action context
     * @return the action result
     */
    ActionResult execute(ActionContext context);

    /**
     * Executes the action asynchronously.
     *
     * <p>Default implementation wraps the synchronous execute method.
     *
     * @param context the action context
     * @return a future containing the action result
     */
    default CompletableFuture<ActionResult> executeAsync(ActionContext context) {
        return CompletableFuture.supplyAsync(() -> execute(context));
    }

    /**
     * Checks if the handler supports asynchronous execution natively.
     *
     * @return true if async execution is supported
     */
    default boolean supportsAsync() {
        return false;
    }
}
