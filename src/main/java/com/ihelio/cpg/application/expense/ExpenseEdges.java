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
import static com.ihelio.cpg.application.expense.ExpenseNodes.FINANCE_REVIEW;
import static com.ihelio.cpg.application.expense.ExpenseNodes.MANAGER_APPROVAL;
import static com.ihelio.cpg.application.expense.ExpenseNodes.PROCESS_PAYMENT;
import static com.ihelio.cpg.application.expense.ExpenseNodes.SUBMIT_EXPENSE;
import static com.ihelio.cpg.application.expense.ExpenseNodes.VALIDATE_EXPENSE;

import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Edge.CompensationSemantics;
import com.ihelio.cpg.domain.model.Edge.EventTriggers;
import com.ihelio.cpg.domain.model.Edge.ExecutionSemantics;
import com.ihelio.cpg.domain.model.Edge.GuardConditions;
import com.ihelio.cpg.domain.model.Edge.Priority;
import com.ihelio.cpg.domain.model.FeelExpression;
import java.util.List;

/**
 * Factory for creating edges in the expense approval process.
 *
 * <p>This class defines all permissible transitions between decision points
 * in the expense workflow, including:
 * <ul>
 *   <li>Submission to validation flow</li>
 *   <li>Manager approval path</li>
 *   <li>Finance review for high amounts</li>
 *   <li>Payment processing</li>
 *   <li>Rejection paths</li>
 * </ul>
 */
public final class ExpenseEdges {

    private ExpenseEdges() {
    }

    // =========================================================================
    // Submission Flow
    // =========================================================================

    /**
     * Submit expense triggers validation.
     */
    public static Edge submitToValidate() {
        return new Edge(
            new Edge.EdgeId("submit-to-validate"),
            "Validate Submission",
            "Validate expense report after submission",
            SUBMIT_EXPENSE,
            VALIDATE_EXPENSE,
            GuardConditions.alwaysTrue(),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.activatedBy("ExpenseSubmitted"),
            CompensationSemantics.none()
        );
    }

    /**
     * Validation passed - route to manager approval.
     */
    public static Edge validateToManager() {
        return new Edge(
            new Edge.EdgeId("validate-to-manager"),
            "Request Manager Approval",
            "Route to manager for approval after validation",
            VALIDATE_EXPENSE,
            MANAGER_APPROVAL,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("expense.validated = true")
            )),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.activatedBy("ExpenseValidated"),
            CompensationSemantics.none()
        );
    }

    /**
     * Validation failed - reject expense.
     */
    public static Edge validateToRejected() {
        return new Edge(
            new Edge.EdgeId("validate-to-rejected"),
            "Validation Failed",
            "Reject expense due to validation failure",
            VALIDATE_EXPENSE,
            EXPENSE_REJECTED,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("expense.validated = false")
            )),
            ExecutionSemantics.sequential(),
            Priority.exclusive(1000),
            EventTriggers.none(),
            CompensationSemantics.none()
        );
    }

    // =========================================================================
    // Manager Approval Flow
    // =========================================================================

    /**
     * Manager approved - route to finance for high amounts.
     */
    public static Edge managerToFinance() {
        return new Edge(
            new Edge.EdgeId("manager-to-finance"),
            "Finance Review Required",
            "Route to finance for high-value expense review",
            MANAGER_APPROVAL,
            FINANCE_REVIEW,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("expense.managerApproved = true"),
                FeelExpression.of("expense.amount >= 5000")
            )),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.activatedBy("ManagerDecision"),
            CompensationSemantics.none()
        );
    }

    /**
     * Manager approved low amount - direct to payment.
     */
    public static Edge managerToPayment() {
        return new Edge(
            new Edge.EdgeId("manager-to-payment"),
            "Process Payment",
            "Process payment for manager-approved low-value expense",
            MANAGER_APPROVAL,
            PROCESS_PAYMENT,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("expense.managerApproved = true"),
                FeelExpression.of("expense.amount < 5000")
            )),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.activatedBy("ManagerDecision"),
            CompensationSemantics.none()
        );
    }

    /**
     * Manager rejected - reject expense.
     */
    public static Edge managerToRejected() {
        return new Edge(
            new Edge.EdgeId("manager-to-rejected"),
            "Manager Rejected",
            "Reject expense due to manager rejection",
            MANAGER_APPROVAL,
            EXPENSE_REJECTED,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("expense.managerApproved = false")
            )),
            ExecutionSemantics.sequential(),
            Priority.exclusive(1000),
            EventTriggers.activatedBy("ManagerDecision"),
            CompensationSemantics.none()
        );
    }

    // =========================================================================
    // Finance Review Flow
    // =========================================================================

    /**
     * Finance approved - process payment.
     */
    public static Edge financeToPayment() {
        return new Edge(
            new Edge.EdgeId("finance-to-payment"),
            "Finance Approved",
            "Process payment after finance approval",
            FINANCE_REVIEW,
            PROCESS_PAYMENT,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("expense.financeApproved = true")
            )),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.activatedBy("FinanceDecision"),
            CompensationSemantics.none()
        );
    }

    /**
     * Finance rejected - reject expense.
     */
    public static Edge financeToRejected() {
        return new Edge(
            new Edge.EdgeId("finance-to-rejected"),
            "Finance Rejected",
            "Reject expense due to finance rejection",
            FINANCE_REVIEW,
            EXPENSE_REJECTED,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("expense.financeApproved = false")
            )),
            ExecutionSemantics.sequential(),
            Priority.exclusive(1000),
            EventTriggers.activatedBy("FinanceDecision"),
            CompensationSemantics.none()
        );
    }

    // =========================================================================
    // Payment Flow
    // =========================================================================

    /**
     * Payment processed - complete expense.
     */
    public static Edge paymentToApproved() {
        return new Edge(
            new Edge.EdgeId("payment-to-approved"),
            "Payment Complete",
            "Mark expense as approved after payment",
            PROCESS_PAYMENT,
            EXPENSE_APPROVED,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("payment.processed = true")
            )),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.activatedBy("PaymentProcessed"),
            CompensationSemantics.none()
        );
    }

    /**
     * Returns all edges in the expense approval process.
     */
    public static List<Edge> all() {
        return List.of(
            // Submission flow
            submitToValidate(),
            validateToManager(),
            validateToRejected(),

            // Manager approval flow
            managerToFinance(),
            managerToPayment(),
            managerToRejected(),

            // Finance review flow
            financeToPayment(),
            financeToRejected(),

            // Payment flow
            paymentToApproved()
        );
    }
}
