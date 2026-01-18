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

package com.ihelio.cpg.domain.exception;

import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Node;

/**
 * Base exception for process execution errors.
 *
 * <p>This exception hierarchy allows for fine-grained exception handling
 * and compensation strategies based on the type of failure.
 */
public class ProcessExecutionException extends RuntimeException {

    private final String processInstanceId;
    private final String nodeId;
    private final ErrorType errorType;
    private final boolean retryable;

    public ProcessExecutionException(String message, ErrorType errorType) {
        super(message);
        this.processInstanceId = null;
        this.nodeId = null;
        this.errorType = errorType;
        this.retryable = false;
    }

    public ProcessExecutionException(String message, ErrorType errorType, boolean retryable) {
        super(message);
        this.processInstanceId = null;
        this.nodeId = null;
        this.errorType = errorType;
        this.retryable = retryable;
    }

    public ProcessExecutionException(
            String message,
            ProcessInstance.ProcessInstanceId processInstanceId,
            ErrorType errorType) {
        super(message);
        this.processInstanceId = processInstanceId != null ? processInstanceId.value() : null;
        this.nodeId = null;
        this.errorType = errorType;
        this.retryable = false;
    }

    public ProcessExecutionException(
            String message,
            ProcessInstance.ProcessInstanceId processInstanceId,
            Node.NodeId nodeId,
            ErrorType errorType,
            boolean retryable) {
        super(message);
        this.processInstanceId = processInstanceId != null ? processInstanceId.value() : null;
        this.nodeId = nodeId != null ? nodeId.value() : null;
        this.errorType = errorType;
        this.retryable = retryable;
    }

    public ProcessExecutionException(String message, Throwable cause, ErrorType errorType) {
        super(message, cause);
        this.processInstanceId = null;
        this.nodeId = null;
        this.errorType = errorType;
        this.retryable = false;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public boolean isRetryable() {
        return retryable;
    }

    /**
     * Type of execution error.
     */
    public enum ErrorType {
        /** Node preconditions not met. */
        PRECONDITION_FAILED,
        /** Policy gate blocked execution. */
        POLICY_BLOCKED,
        /** Business rule evaluation failed. */
        RULE_EVALUATION_FAILED,
        /** Action execution failed. */
        ACTION_FAILED,
        /** Edge guard condition not met. */
        GUARD_FAILED,
        /** Process instance not found. */
        INSTANCE_NOT_FOUND,
        /** Process graph not found. */
        GRAPH_NOT_FOUND,
        /** Node not found in graph. */
        NODE_NOT_FOUND,
        /** Invalid process state. */
        INVALID_STATE,
        /** Expression evaluation failed. */
        EXPRESSION_ERROR,
        /** Timeout occurred. */
        TIMEOUT,
        /** Compensation failed. */
        COMPENSATION_FAILED,
        /** Unknown error. */
        UNKNOWN
    }
}
