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

import java.time.Instant;

/**
 * Standardized error response for REST API errors.
 *
 * @param timestamp when the error occurred
 * @param status HTTP status code
 * @param error HTTP status reason phrase
 * @param message detailed error message
 * @param path request path that caused the error
 * @param errorType domain error type (from ProcessExecutionException.ErrorType)
 * @param processInstanceId related process instance ID if applicable
 * @param nodeId related node ID if applicable
 * @param retryable whether the operation can be retried
 */
public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    String errorType,
    String processInstanceId,
    String nodeId,
    boolean retryable
) {
    /**
     * Creates an error response with minimal fields.
     */
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(
            Instant.now(),
            status,
            error,
            message,
            path,
            null,
            null,
            null,
            false
        );
    }

    /**
     * Creates an error response with domain context.
     */
    public static ErrorResponse of(
            int status,
            String error,
            String message,
            String path,
            String errorType,
            String processInstanceId,
            String nodeId,
            boolean retryable) {
        return new ErrorResponse(
            Instant.now(),
            status,
            error,
            message,
            path,
            errorType,
            processInstanceId,
            nodeId,
            retryable
        );
    }
}
