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

package com.ihelio.cpg.domain.policy;

import com.ihelio.cpg.domain.model.Node;
import java.util.List;
import java.util.Map;

/**
 * Port for evaluating policy gates.
 *
 * <p>Policy gates enforce compliance, statutory, regulatory, and organizational
 * requirements. They are evaluated using DMN decision tables maintained by
 * business users.
 */
public interface PolicyEvaluator {

    /**
     * Evaluates a single policy gate.
     *
     * @param policyGate the policy gate to evaluate
     * @param context the variable context for evaluation
     * @return the policy evaluation result
     */
    PolicyResult evaluate(Node.PolicyGate policyGate, Map<String, Object> context);

    /**
     * Evaluates multiple policy gates.
     *
     * @param policyGates the policy gates to evaluate
     * @param context the variable context for evaluation
     * @return list of policy results in the same order as input
     */
    List<PolicyResult> evaluateAll(List<Node.PolicyGate> policyGates, Map<String, Object> context);

    /**
     * Checks if all policy gates pass.
     * Short-circuits on first blocking policy.
     *
     * @param policyGates the policy gates to evaluate
     * @param context the variable context for evaluation
     * @return true if all policies pass or are waived
     */
    default boolean allPoliciesPass(List<Node.PolicyGate> policyGates, Map<String, Object> context) {
        if (policyGates == null || policyGates.isEmpty()) {
            return true;
        }
        for (Node.PolicyGate gate : policyGates) {
            PolicyResult result = evaluate(gate, context);
            if (result.blocks()) {
                return false;
            }
        }
        return true;
    }
}
