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

import com.ihelio.cpg.domain.model.FeelExpression;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.Node.Action;
import com.ihelio.cpg.domain.model.Node.ActionConfig;
import com.ihelio.cpg.domain.model.Node.ActionType;
import com.ihelio.cpg.domain.model.Node.BusinessRule;
import com.ihelio.cpg.domain.model.Node.EmissionTiming;
import com.ihelio.cpg.domain.model.Node.EventConfig;
import com.ihelio.cpg.domain.model.Node.EventEmission;
import com.ihelio.cpg.domain.model.Node.ExceptionRoutes;
import com.ihelio.cpg.domain.model.Node.NodeId;
import com.ihelio.cpg.domain.model.Node.PolicyGate;
import com.ihelio.cpg.domain.model.Node.PolicyType;
import com.ihelio.cpg.domain.model.Node.Preconditions;
import com.ihelio.cpg.domain.model.Node.RuleCategory;
import java.util.List;

/**
 * Factory for creating nodes in the expense approval process.
 *
 * <p>This class defines all decision points in the expense workflow:
 * <ul>
 *   <li>Expense submission</li>
 *   <li>Manager approval</li>
 *   <li>Finance review (for high amounts)</li>
 *   <li>Payment processing</li>
 *   <li>Rejection handling</li>
 * </ul>
 */
public final class ExpenseNodes {

    // Node IDs as constants for reference in edges
    public static final NodeId SUBMIT_EXPENSE = new NodeId("submit-expense");
    public static final NodeId VALIDATE_EXPENSE = new NodeId("validate-expense");
    public static final NodeId MANAGER_APPROVAL = new NodeId("manager-approval");
    public static final NodeId FINANCE_REVIEW = new NodeId("finance-review");
    public static final NodeId PROCESS_PAYMENT = new NodeId("process-payment");
    public static final NodeId EXPENSE_APPROVED = new NodeId("expense-approved");
    public static final NodeId EXPENSE_REJECTED = new NodeId("expense-rejected");

    private ExpenseNodes() {
    }

    /**
     * Entry node: Employee submits expense report.
     */
    public static Node submitExpense() {
        return new Node(
            SUBMIT_EXPENSE,
            "Submit Expense",
            "Employee submits expense report with receipts",
            1,
            Preconditions.none(),
            List.of(),
            List.of(),
            new Action(
                ActionType.HUMAN_TASK,
                "expenseSubmissionForm",
                "Submit expense report with receipts and details",
                new ActionConfig(false, 0, 0, "employee", "expense-form")
            ),
            new EventConfig(
                List.of(),
                List.of(new EventEmission("ExpenseSubmitted", EmissionTiming.ON_COMPLETE, null))
            ),
            ExceptionRoutes.none()
        );
    }

    /**
     * Validate expense report for completeness and policy compliance.
     */
    public static Node validateExpense() {
        return new Node(
            VALIDATE_EXPENSE,
            "Validate Expense",
            "Validate expense report completeness and policy compliance",
            1,
            new Preconditions(
                List.of(),
                List.of(FeelExpression.of("expense.submitted = true"))
            ),
            List.of(
                new PolicyGate(
                    "expense-policy",
                    "Expense Policy",
                    PolicyType.ORGANIZATIONAL,
                    "expense-policy",
                    "VALID"
                )
            ),
            List.of(
                new BusinessRule("expense-category", "Expense Category",
                    "expense-policies", RuleCategory.DERIVATION),
                new BusinessRule("approval-threshold", "Approval Threshold",
                    "expense-policies", RuleCategory.DERIVATION)
            ),
            new Action(
                ActionType.SYSTEM_INVOCATION,
                "expenseValidator",
                "Validate expense against policy rules",
                ActionConfig.defaults()
            ),
            new EventConfig(
                List.of(),
                List.of(new EventEmission("ExpenseValidated", EmissionTiming.ON_COMPLETE, null))
            ),
            ExceptionRoutes.none()
        );
    }

    /**
     * Manager reviews and approves/rejects expense.
     */
    public static Node managerApproval() {
        return new Node(
            MANAGER_APPROVAL,
            "Manager Approval",
            "Direct manager reviews and approves expense",
            1,
            new Preconditions(
                List.of(),
                List.of(
                    FeelExpression.of("expense.validated = true"),
                    FeelExpression.of("expense.amount > 0")
                )
            ),
            List.of(),
            List.of(
                new BusinessRule("manager-lookup", "Manager Lookup",
                    "expense-policies", RuleCategory.EXECUTION_PARAMETER)
            ),
            new Action(
                ActionType.HUMAN_TASK,
                "managerApprovalTask",
                "Manager reviews expense and approves or rejects",
                new ActionConfig(false, 172800, 0, "manager", "approval-form")
            ),
            new EventConfig(
                List.of(),
                List.of(new EventEmission("ManagerDecision", EmissionTiming.ON_COMPLETE, null))
            ),
            ExceptionRoutes.none()
        );
    }

    /**
     * Finance team reviews high-value expenses.
     */
    public static Node financeReview() {
        return new Node(
            FINANCE_REVIEW,
            "Finance Review",
            "Finance team reviews high-value expenses",
            1,
            new Preconditions(
                List.of(),
                List.of(
                    FeelExpression.of("expense.managerApproved = true"),
                    FeelExpression.of("expense.amount >= 5000")
                )
            ),
            List.of(
                new PolicyGate(
                    "budget-check",
                    "Budget Availability",
                    PolicyType.ORGANIZATIONAL,
                    "budget-policy",
                    "BUDGET_AVAILABLE"
                )
            ),
            List.of(
                new BusinessRule("budget-allocation", "Budget Allocation",
                    "expense-policies", RuleCategory.OBLIGATION)
            ),
            new Action(
                ActionType.HUMAN_TASK,
                "financeReviewTask",
                "Finance reviews budget impact and approves",
                new ActionConfig(false, 259200, 0, "finance.approver", "finance-review-form")
            ),
            new EventConfig(
                List.of(),
                List.of(new EventEmission("FinanceDecision", EmissionTiming.ON_COMPLETE, null))
            ),
            ExceptionRoutes.none()
        );
    }

    /**
     * Process payment for approved expense.
     */
    public static Node processPayment() {
        return new Node(
            PROCESS_PAYMENT,
            "Process Payment",
            "Process reimbursement payment to employee",
            1,
            new Preconditions(
                List.of(),
                List.of(
                    FeelExpression.of("expense.fullyApproved = true"),
                    FeelExpression.of("employee.paymentMethod != null")
                )
            ),
            List.of(),
            List.of(
                new BusinessRule("payment-method", "Payment Method",
                    "expense-policies", RuleCategory.EXECUTION_PARAMETER)
            ),
            new Action(
                ActionType.SYSTEM_INVOCATION,
                "paymentProcessor",
                "Submit reimbursement to payroll system",
                ActionConfig.async()
            ),
            new EventConfig(
                List.of(),
                List.of(new EventEmission("PaymentProcessed", EmissionTiming.ON_COMPLETE, null))
            ),
            ExceptionRoutes.none()
        );
    }

    /**
     * Terminal node: Expense approved and paid.
     */
    public static Node expenseApproved() {
        return new Node(
            EXPENSE_APPROVED,
            "Expense Approved",
            "Expense has been approved and payment processed",
            1,
            new Preconditions(
                List.of(),
                List.of(FeelExpression.of("payment.processed = true"))
            ),
            List.of(),
            List.of(),
            new Action(
                ActionType.SYSTEM_INVOCATION,
                "notifyEmployee",
                "Notify employee of approval and payment",
                ActionConfig.defaults()
            ),
            new EventConfig(
                List.of(),
                List.of(new EventEmission("ExpenseCompleted", EmissionTiming.ON_COMPLETE, null))
            ),
            ExceptionRoutes.none()
        );
    }

    /**
     * Terminal node: Expense rejected.
     */
    public static Node expenseRejected() {
        return new Node(
            EXPENSE_REJECTED,
            "Expense Rejected",
            "Expense has been rejected",
            1,
            Preconditions.none(),
            List.of(),
            List.of(),
            new Action(
                ActionType.SYSTEM_INVOCATION,
                "notifyRejection",
                "Notify employee of rejection with reason",
                ActionConfig.defaults()
            ),
            new EventConfig(
                List.of(),
                List.of(new EventEmission("ExpenseRejected", EmissionTiming.ON_COMPLETE, null))
            ),
            ExceptionRoutes.none()
        );
    }

    /**
     * Returns all nodes in the expense approval process.
     */
    public static List<Node> all() {
        return List.of(
            submitExpense(),
            validateExpense(),
            managerApproval(),
            financeReview(),
            processPayment(),
            expenseApproved(),
            expenseRejected()
        );
    }
}
