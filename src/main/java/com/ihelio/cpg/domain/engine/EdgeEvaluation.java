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

package com.ihelio.cpg.domain.engine;

import com.ihelio.cpg.domain.model.Edge;
import java.util.Objects;

/**
 * Result of evaluating an edge's guard conditions.
 *
 * @param edge the edge that was evaluated
 * @param traversable whether the edge can be traversed
 * @param contextConditionsPassed whether context FEEL expressions passed
 * @param ruleConditionsPassed whether rule outcome conditions passed
 * @param policyConditionsPassed whether policy outcome conditions passed
 * @param eventConditionsPassed whether event conditions passed
 * @param blockedReason reason if the edge is blocked
 */
public record EdgeEvaluation(
    Edge edge,
    boolean traversable,
    boolean contextConditionsPassed,
    boolean ruleConditionsPassed,
    boolean policyConditionsPassed,
    boolean eventConditionsPassed,
    String blockedReason
) {

    public EdgeEvaluation {
        Objects.requireNonNull(edge, "EdgeEvaluation edge is required");
    }

    /**
     * Creates a traversable edge evaluation.
     */
    public static EdgeEvaluation traversable(Edge edge) {
        return new EdgeEvaluation(edge, true, true, true, true, true, null);
    }

    /**
     * Creates a blocked edge evaluation.
     */
    public static EdgeEvaluation blocked(Edge edge, String reason) {
        return new EdgeEvaluation(edge, false, false, false, false, false, reason);
    }

    /**
     * Creates a blocked evaluation due to context conditions.
     */
    public static EdgeEvaluation blockedByContext(Edge edge, String reason) {
        return new EdgeEvaluation(edge, false, false, true, true, true, reason);
    }

    /**
     * Creates a blocked evaluation due to rule conditions.
     */
    public static EdgeEvaluation blockedByRules(Edge edge, String reason) {
        return new EdgeEvaluation(edge, false, true, false, true, true, reason);
    }

    /**
     * Creates a blocked evaluation due to policy conditions.
     */
    public static EdgeEvaluation blockedByPolicies(Edge edge, String reason) {
        return new EdgeEvaluation(edge, false, true, true, false, true, reason);
    }

    /**
     * Creates a blocked evaluation due to event conditions.
     */
    public static EdgeEvaluation blockedByEvents(Edge edge, String reason) {
        return new EdgeEvaluation(edge, false, true, true, true, false, reason);
    }

    /**
     * Checks if the edge is blocked.
     */
    public boolean isBlocked() {
        return !traversable;
    }
}
