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

package com.ihelio.cpg.interfaces.rest.dto.request;

import java.util.Map;

/**
 * Request to update execution context of a process instance.
 *
 * <p>All fields are optional. Only provided fields will be merged into the context.
 *
 * @param clientContext client/tenant-specific context data to merge
 * @param domainContext business domain data to merge
 * @param accumulatedState accumulated state data to merge
 */
public record UpdateContextRequest(
    Map<String, Object> clientContext,
    Map<String, Object> domainContext,
    Map<String, Object> accumulatedState
) {
    public UpdateContextRequest {
        clientContext = clientContext != null ? Map.copyOf(clientContext) : Map.of();
        domainContext = domainContext != null ? Map.copyOf(domainContext) : Map.of();
        accumulatedState = accumulatedState != null ? Map.copyOf(accumulatedState) : Map.of();
    }
}
