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

package com.ihelio.cpg.interfaces.rest;

import com.ihelio.cpg.domain.exception.ProcessExecutionException;
import com.ihelio.cpg.interfaces.rest.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for REST API endpoints.
 *
 * <p>Maps domain exceptions to appropriate HTTP responses with standardized error format.
 */
@RestControllerAdvice(basePackages = "com.ihelio.cpg.interfaces.rest")
public class GlobalExceptionHandler {

    /**
     * Handles ProcessExecutionException from the domain layer.
     */
    @ExceptionHandler(ProcessExecutionException.class)
    public ResponseEntity<ErrorResponse> handleProcessExecutionException(
            ProcessExecutionException ex,
            HttpServletRequest request) {

        HttpStatus status = mapErrorTypeToStatus(ex.getErrorType());

        ErrorResponse response = ErrorResponse.of(
            status.value(),
            status.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI(),
            ex.getErrorType().name(),
            ex.getProcessInstanceId(),
            ex.getNodeId(),
            ex.isRetryable()
        );

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Handles validation errors from @Valid annotations.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");

        ErrorResponse response = ErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            message,
            request.getRequestURI()
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles not found exceptions.
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(
            NoSuchElementException ex,
            HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.of(
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handles illegal argument exceptions.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles illegal state exceptions.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex,
            HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.of(
            HttpStatus.CONFLICT.value(),
            HttpStatus.CONFLICT.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handles all other exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.of(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            "An unexpected error occurred: " + ex.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Maps ProcessExecutionException.ErrorType to HTTP status code.
     */
    private HttpStatus mapErrorTypeToStatus(ProcessExecutionException.ErrorType errorType) {
        return switch (errorType) {
            case INSTANCE_NOT_FOUND, GRAPH_NOT_FOUND, NODE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PRECONDITION_FAILED, GUARD_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case POLICY_BLOCKED -> HttpStatus.FORBIDDEN;
            case INVALID_STATE -> HttpStatus.CONFLICT;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case ACTION_FAILED, RULE_EVALUATION_FAILED, EXPRESSION_ERROR,
                 COMPENSATION_FAILED, UNKNOWN -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
