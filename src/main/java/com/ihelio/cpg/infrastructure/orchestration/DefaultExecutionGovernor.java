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

package com.ihelio.cpg.infrastructure.orchestration;

import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.orchestration.ExecutionGovernor;
import com.ihelio.cpg.domain.orchestration.GovernanceResult;
import com.ihelio.cpg.domain.orchestration.RuntimeContext;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DefaultExecutionGovernor implements the ExecutionGovernor port with configurable
 * idempotency, authorization, and policy gate checks.
 *
 * <p>Each check can be enabled/disabled via configuration. When enabled:
 * <ul>
 *   <li><b>Idempotency</b>: Tracks executed actions to prevent duplicates</li>
 *   <li><b>Authorization</b>: Checks if context is authorized to execute</li>
 *   <li><b>Policy Gate</b>: Performs final policy evaluation before execution</li>
 * </ul>
 */
public class DefaultExecutionGovernor implements ExecutionGovernor {

    private final OrchestratorConfigProperties config;
    private final Map<String, String> executedActions;  // idempotency key -> execution ID

    /**
     * Creates a DefaultExecutionGovernor with configuration.
     *
     * @param config the orchestrator configuration
     */
    public DefaultExecutionGovernor(OrchestratorConfigProperties config) {
        this.config = Objects.requireNonNull(config, "config is required");
        this.executedActions = new ConcurrentHashMap<>();
    }

    @Override
    public GovernanceResult.IdempotencyResult checkIdempotency(
            ProcessInstance instance,
            Node node,
            RuntimeContext context) {

        if (!config.governance().idempotencyEnabled()) {
            return GovernanceResult.IdempotencyResult.skipped();
        }

        String idempotencyKey = generateIdempotencyKey(instance, node, context);
        String previousExecutionId = executedActions.get(idempotencyKey);

        if (previousExecutionId != null) {
            return GovernanceResult.IdempotencyResult.alreadyExecuted(idempotencyKey, previousExecutionId);
        }

        return GovernanceResult.IdempotencyResult.passed(idempotencyKey);
    }

    @Override
    public GovernanceResult.AuthorizationResult checkAuthorization(
            ProcessInstance instance,
            Node node,
            RuntimeContext context) {

        if (!config.governance().authorizationEnabled()) {
            return GovernanceResult.AuthorizationResult.skipped();
        }

        // Extract principal from context
        String principal = extractPrincipal(context);

        // Get required permissions for the action
        List<String> requiredPermissions = getRequiredPermissions(node);

        // Check authorization (simplified - in production would use a real auth system)
        boolean authorized = checkPermissions(principal, requiredPermissions, context);

        if (authorized) {
            return GovernanceResult.AuthorizationResult.authorized(principal, requiredPermissions);
        }

        return GovernanceResult.AuthorizationResult.unauthorized(
            principal,
            requiredPermissions,
            "Principal lacks required permissions: " + requiredPermissions
        );
    }

    @Override
    public GovernanceResult.PolicyGateResult checkPolicyGate(
            ProcessInstance instance,
            Node node,
            RuntimeContext context) {

        if (!config.governance().policyGateEnabled()) {
            return GovernanceResult.PolicyGateResult.skipped();
        }

        List<GovernanceResult.PolicyGateResult.PolicyCheck> policiesChecked = new ArrayList<>();
        List<GovernanceResult.PolicyGateResult.PolicyCheck> failedPolicies = new ArrayList<>();

        // Check node policy gates
        for (Node.PolicyGate policyGate : node.policyGates()) {
            boolean passed = evaluatePolicyGate(policyGate, context);
            GovernanceResult.PolicyGateResult.PolicyCheck check;

            if (passed) {
                check = GovernanceResult.PolicyGateResult.PolicyCheck.passed(
                    policyGate.id(), policyGate.name());
            } else {
                check = GovernanceResult.PolicyGateResult.PolicyCheck.failed(
                    policyGate.id(), policyGate.name(), "Policy evaluation failed");
                failedPolicies.add(check);
            }
            policiesChecked.add(check);
        }

        // Check runtime policies (operational state, etc.)
        var runtimePolicyCheck = checkRuntimePolicies(context);
        policiesChecked.add(runtimePolicyCheck);
        if (!runtimePolicyCheck.passed()) {
            failedPolicies.add(runtimePolicyCheck);
        }

        if (failedPolicies.isEmpty()) {
            return GovernanceResult.PolicyGateResult.passed(policiesChecked);
        }

        return GovernanceResult.PolicyGateResult.failed(policiesChecked, failedPolicies);
    }

    @Override
    public GovernanceResult enforce(
            ProcessInstance instance,
            Node node,
            RuntimeContext context) {

        var idempotencyResult = checkIdempotency(instance, node, context);
        var authorizationResult = checkAuthorization(instance, node, context);
        var policyGateResult = checkPolicyGate(instance, node, context);

        return GovernanceResult.combine(idempotencyResult, authorizationResult, policyGateResult);
    }

    @Override
    public void recordExecution(
            ProcessInstance instance,
            Node node,
            RuntimeContext context,
            String executionId) {

        if (config.governance().idempotencyEnabled()) {
            String idempotencyKey = generateIdempotencyKey(instance, node, context);
            executedActions.put(idempotencyKey, executionId);
        }
    }

    /**
     * Clears recorded executions (for testing).
     */
    public void clearRecordedExecutions() {
        executedActions.clear();
    }

    private String generateIdempotencyKey(ProcessInstance instance, Node node, RuntimeContext context) {
        // Create a unique key based on instance, node, and relevant context
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(instance.id().value());
        keyBuilder.append(":");
        keyBuilder.append(node.id().value());
        keyBuilder.append(":");
        keyBuilder.append(instance.nodeExecutions().size());

        // Add hash of relevant context state
        String contextHash = hashContext(context);
        keyBuilder.append(":");
        keyBuilder.append(contextHash);

        return keyBuilder.toString();
    }

    private String hashContext(RuntimeContext context) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String contextString = context.entityState().toString();
            byte[] hash = digest.digest(contextString.getBytes());
            return Base64.getEncoder().encodeToString(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(context.entityState().hashCode());
        }
    }

    private String extractPrincipal(RuntimeContext context) {
        // Try to get principal from client context
        Object principal = context.clientContext().get("principal");
        if (principal != null) {
            return principal.toString();
        }

        // Try to get from domain context
        principal = context.domainContext().get("userId");
        if (principal != null) {
            return principal.toString();
        }

        return "SYSTEM";
    }

    private List<String> getRequiredPermissions(Node node) {
        List<String> permissions = new ArrayList<>();

        // Base permission for executing the node type
        permissions.add("execute:" + node.action().type().name().toLowerCase());

        // Permission based on action handler
        permissions.add("action:" + node.action().handlerRef());

        return permissions;
    }

    @SuppressWarnings("unchecked")
    private boolean checkPermissions(String principal, List<String> requiredPermissions,
            RuntimeContext context) {
        // In a real implementation, this would check against an authorization service
        // For now, we check if the principal has the permissions in their context

        // System principal always has access
        if ("SYSTEM".equals(principal)) {
            return true;
        }

        // Check for permissions in context
        Object permissionsObj = context.clientContext().get("permissions");
        if (permissionsObj instanceof Set) {
            Set<String> grantedPermissions = (Set<String>) permissionsObj;
            return grantedPermissions.containsAll(requiredPermissions);
        }

        if (permissionsObj instanceof List) {
            List<String> grantedPermissions = (List<String>) permissionsObj;
            return grantedPermissions.containsAll(requiredPermissions);
        }

        // Default: allow if no permission system configured
        return true;
    }

    private boolean evaluatePolicyGate(Node.PolicyGate policyGate, RuntimeContext context) {
        // In a real implementation, this would evaluate the DMN decision
        // For now, we return true (passed) unless there's explicit configuration to fail

        // Check for policy overrides in context
        Object policyOverride = context.clientContext().get("policy:" + policyGate.id());
        if (policyOverride instanceof Boolean) {
            return (Boolean) policyOverride;
        }

        // Default: policy passes
        return true;
    }

    private GovernanceResult.PolicyGateResult.PolicyCheck checkRuntimePolicies(RuntimeContext context) {
        // Check operational state
        if (context.operationalContext().systemState() == RuntimeContext.SystemState.EMERGENCY) {
            return GovernanceResult.PolicyGateResult.PolicyCheck.failed(
                "runtime.emergency",
                "Emergency Mode Check",
                "System is in emergency mode - execution blocked"
            );
        }

        if (context.operationalContext().systemState() == RuntimeContext.SystemState.MAINTENANCE) {
            return GovernanceResult.PolicyGateResult.PolicyCheck.failed(
                "runtime.maintenance",
                "Maintenance Mode Check",
                "System is in maintenance mode - execution blocked"
            );
        }

        return GovernanceResult.PolicyGateResult.PolicyCheck.passed(
            "runtime.operational",
            "Operational State Check"
        );
    }
}
