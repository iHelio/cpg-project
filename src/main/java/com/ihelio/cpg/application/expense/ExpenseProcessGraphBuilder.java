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

package com.ihelio.cpg.application.expense;

import static com.ihelio.cpg.application.expense.ExpenseNodes.EXPENSE_APPROVED;
import static com.ihelio.cpg.application.expense.ExpenseNodes.EXPENSE_REJECTED;
import static com.ihelio.cpg.application.expense.ExpenseNodes.SUBMIT_EXPENSE;

import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.model.ProcessGraph.Metadata;
import com.ihelio.cpg.domain.model.ProcessGraph.ProcessGraphId;
import com.ihelio.cpg.domain.model.ProcessGraph.ProcessGraphStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Builder for the expense approval process graph.
 *
 * <p>This builder assembles the expense workflow including:
 * <ul>
 *   <li>7 decision nodes covering submission, validation, approvals, and payment</li>
 *   <li>9 edges defining permissible transitions with FEEL guard conditions</li>
 *   <li>Multi-level approval based on expense amount</li>
 *   <li>Rejection paths at each approval stage</li>
 * </ul>
 *
 * <p>Flow diagram:
 * <pre>
 *   Submit → Validate → Manager Approval
 *                          ↓
 *            ┌─────────────┼─────────────┐
 *            ↓             ↓             ↓
 *         Rejected   Finance Review   Payment
 *                          ↓             ↓
 *                    ┌─────┴─────┐       ↓
 *                    ↓           ↓       ↓
 *                 Rejected    Payment → Approved
 * </pre>
 */
public final class ExpenseProcessGraphBuilder {

    private static final ProcessGraphId GRAPH_ID = new ProcessGraphId("expense-approval");
    private static final int CURRENT_VERSION = 1;

    private ExpenseProcessGraphBuilder() {
    }

    /**
     * Builds the complete expense approval process graph.
     *
     * @return the fully configured process graph
     */
    public static ProcessGraph build() {
        return new ProcessGraph(
            GRAPH_ID,
            "Expense Approval",
            "Multi-level expense approval workflow with finance review for high amounts",
            CURRENT_VERSION,
            ProcessGraphStatus.PUBLISHED,
            ExpenseNodes.all(),
            ExpenseEdges.all(),
            List.of(SUBMIT_EXPENSE),
            List.of(EXPENSE_APPROVED, EXPENSE_REJECTED),
            createMetadata()
        );
    }

    private static Metadata createMetadata() {
        return new Metadata(
            "system",
            Instant.now(),
            "system",
            Instant.now(),
            Map.of(
                "domain", "finance",
                "process-type", "approval",
                "threshold", "5000"
            )
        );
    }
}
