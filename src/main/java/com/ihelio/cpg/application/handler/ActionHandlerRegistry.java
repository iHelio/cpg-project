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
import com.ihelio.cpg.domain.model.Node;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registry for action handlers.
 *
 * <p>Manages registration and lookup of action handlers by type and reference.
 * Provides a default no-op handler for unhandled action types.
 */
@Component
public class ActionHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(ActionHandlerRegistry.class);

    private final Map<Node.ActionType, List<ActionHandler>> handlersByType =
        new EnumMap<>(Node.ActionType.class);

    private final ActionHandler defaultHandler = new DefaultActionHandler();

    public ActionHandlerRegistry() {
        // Initialize handler lists for each type
        for (Node.ActionType type : Node.ActionType.values()) {
            handlersByType.put(type, new CopyOnWriteArrayList<>());
        }
    }

    /**
     * Auto-wires handlers from Spring context.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public ActionHandlerRegistry(List<ActionHandler> handlers) {
        this();
        if (handlers != null) {
            for (ActionHandler handler : handlers) {
                register(handler);
            }
        }
    }

    /**
     * Registers an action handler.
     *
     * @param handler the handler to register
     */
    public void register(ActionHandler handler) {
        Node.ActionType type = handler.supportedType();
        handlersByType.get(type).add(handler);
        log.info("Registered action handler: {} for type {}",
            handler.getClass().getSimpleName(), type);
    }

    /**
     * Unregisters an action handler.
     *
     * @param handler the handler to unregister
     */
    public void unregister(ActionHandler handler) {
        Node.ActionType type = handler.supportedType();
        handlersByType.get(type).remove(handler);
    }

    /**
     * Finds a handler for the given action type.
     *
     * @param type the action type
     * @return the handler, or the default handler if none found
     */
    public ActionHandler getHandler(Node.ActionType type) {
        List<ActionHandler> handlers = handlersByType.get(type);
        if (handlers != null && !handlers.isEmpty()) {
            return handlers.get(0);
        }
        return defaultHandler;
    }

    /**
     * Finds a handler that can handle the specific handler reference.
     *
     * @param type the action type
     * @param handlerRef the handler reference
     * @return the matching handler, or the default handler
     */
    public ActionHandler getHandler(Node.ActionType type, String handlerRef) {
        List<ActionHandler> handlers = handlersByType.get(type);
        if (handlers != null) {
            for (ActionHandler handler : handlers) {
                if (handler.canHandle(handlerRef)) {
                    return handler;
                }
            }
        }
        return defaultHandler;
    }

    /**
     * Finds all handlers for a given action type.
     *
     * @param type the action type
     * @return list of handlers
     */
    public List<ActionHandler> getHandlers(Node.ActionType type) {
        return List.copyOf(handlersByType.get(type));
    }

    /**
     * Returns a function for resolving handlers by type and handler reference.
     * Used for ProcessExecutionEngine construction.
     */
    public java.util.function.BiFunction<Node.ActionType, String, ActionHandler> asResolver() {
        return this::getHandler;
    }

    /**
     * Default action handler that logs and returns success.
     */
    private static class DefaultActionHandler implements ActionHandler {

        private static final Logger log = LoggerFactory.getLogger(DefaultActionHandler.class);

        @Override
        public Node.ActionType supportedType() {
            return null; // Supports all as fallback
        }

        @Override
        public boolean canHandle(String handlerRef) {
            return true; // Can handle anything as fallback
        }

        @Override
        public ActionResult execute(ActionContext context) {
            log.info("Executing default handler for action: {} ({})",
                context.action().type(),
                context.handlerRef());

            // Return success with the node ID in output
            return ActionResult.success(Map.of(
                "executedBy", "default-handler",
                "nodeId", context.node().id().value(),
                "actionType", context.action().type().name()
            ));
        }
    }
}
