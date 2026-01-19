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

import java.util.List;

/**
 * Response for process graph validation.
 *
 * @param valid whether the graph is valid
 * @param errors list of validation errors (empty if valid)
 */
public record ValidationResultResponse(
    boolean valid,
    List<String> errors
) {
    /**
     * Creates a validation result from a list of errors.
     */
    public static ValidationResultResponse from(List<String> errors) {
        return new ValidationResultResponse(
            errors.isEmpty(),
            errors
        );
    }

    /**
     * Creates a successful validation result.
     */
    public static ValidationResultResponse success() {
        return new ValidationResultResponse(true, List.of());
    }
}
