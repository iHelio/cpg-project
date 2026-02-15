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

package com.ihelio.cpg.application.onboarding;

import com.ihelio.cpg.domain.model.FeelExpression;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.Node.Action;
import com.ihelio.cpg.domain.model.Node.ActionConfig;
import com.ihelio.cpg.domain.model.Node.ActionType;
import com.ihelio.cpg.domain.model.Node.BusinessRule;
import com.ihelio.cpg.domain.model.Node.EmissionTiming;
import com.ihelio.cpg.domain.model.Node.EscalationRoute;
import com.ihelio.cpg.domain.model.Node.EventConfig;
import com.ihelio.cpg.domain.model.Node.EventEmission;
import com.ihelio.cpg.domain.model.Node.EventSubscription;
import com.ihelio.cpg.domain.model.Node.ExceptionRoutes;
import com.ihelio.cpg.domain.model.Node.NodeId;
import com.ihelio.cpg.domain.model.Node.PolicyGate;
import com.ihelio.cpg.domain.model.Node.PolicyType;
import com.ihelio.cpg.domain.model.Node.Preconditions;
import com.ihelio.cpg.domain.model.Node.RemediationRoute;
import com.ihelio.cpg.domain.model.Node.RemediationStrategy;
import com.ihelio.cpg.domain.model.Node.RuleCategory;
import java.util.List;

/**
 * Factory for creating nodes in the employee onboarding process.
 *
 * <p>This class defines all the decision points in the onboarding workflow:
 * <ul>
 *   <li>Offer acceptance and initial validation</li>
 *   <li>Background check processing</li>
 *   <li>IT provisioning (equipment, accounts)</li>
 *   <li>HR documentation collection</li>
 *   <li>Orientation scheduling</li>
 *   <li>Onboarding completion</li>
 * </ul>
 */
public final class OnboardingNodes {

    // Node IDs as constants for reference in edges
    public static final NodeId OFFER_ACCEPTED = new NodeId("offer-accepted");
    public static final NodeId VALIDATE_CANDIDATE = new NodeId("validate-candidate");
    public static final NodeId RUN_BACKGROUND_CHECK = new NodeId("run-background-check");
    public static final NodeId AI_ANALYZE_BACKGROUND = new NodeId("ai-analyze-background-check");
    public static final NodeId REVIEW_BACKGROUND_RESULTS = new NodeId("review-background-results");
    public static final NodeId ORDER_EQUIPMENT = new NodeId("order-equipment");
    public static final NodeId SHIP_EQUIPMENT = new NodeId("ship-equipment");
    public static final NodeId CREATE_ACCOUNTS = new NodeId("create-accounts");
    public static final NodeId COLLECT_DOCUMENTS = new NodeId("collect-documents");
    public static final NodeId VERIFY_I9 = new NodeId("verify-i9");
    public static final NodeId SCHEDULE_ORIENTATION = new NodeId("schedule-orientation");
    public static final NodeId ONBOARDING_COMPLETE = new NodeId("onboarding-complete");
    public static final NodeId ONBOARDING_CANCELLED = new NodeId("onboarding-cancelled");

    private OnboardingNodes() {
    }

    /**
     * Entry node: Offer has been accepted by the candidate.
     */
    public static Node offerAccepted() {
        return new Node(
            OFFER_ACCEPTED,
            "Offer Accepted",
            "Triggered when a candidate accepts an employment offer",
            1,
            Preconditions.none(),
            List.of(),
            List.of(),
            new Action(
                ActionType.SYSTEM_INVOCATION,
                "initializeOnboarding",
                "Initialize the onboarding process context",
                ActionConfig.defaults()
            ),
            new EventConfig(
                List.of(new EventSubscription("OfferAccepted", null)),
                List.of(new EventEmission("OnboardingStarted", EmissionTiming.ON_COMPLETE, null))
            ),
            ExceptionRoutes.none()
        );
    }

    /**
     * Validate candidate information before proceeding.
     */
    public static Node validateCandidate() {
        return new Node(
            VALIDATE_CANDIDATE,
            "Validate Candidate",
            "Verify candidate information is complete and valid",
            1,
            new Preconditions(
                List.of(),
                List.of(FeelExpression.of("offer.status = \"ACCEPTED\""))
            ),
            List.of(),
            List.of(
                new BusinessRule("derive-start-date", "Derive Start Date",
                    "onboarding-policies", RuleCategory.DERIVATION)
            ),
            new Action(
                ActionType.SYSTEM_INVOCATION,
                "validateCandidateData",
                "Validate candidate data completeness",
                ActionConfig.defaults()
            ),
            EventConfig.none(),
            new ExceptionRoutes(
                List.of(new RemediationRoute(
                    "INCOMPLETE_DATA",
                    RemediationStrategy.ALTERNATE,
                    0,
                    "request-missing-info"
                )),
                List.of()
            )
        );
    }

    /**
     * Run background check with external provider.
     */
    public static Node runBackgroundCheck() {
        return new Node(
            RUN_BACKGROUND_CHECK,
            "Run Background Check",
            "Initiate background check with configured provider",
            1,
            new Preconditions(
                List.of(FeelExpression.of("client.backgroundCheckProvider != null")),
                List.of(
                    FeelExpression.of("candidate.consentGiven = true"),
                    FeelExpression.of("candidate.disclosuresSigned = true")
                )
            ),
            List.of(
                new PolicyGate(
                    "fcra-compliance",
                    "FCRA Compliance",
                    PolicyType.STATUTORY,
                    "background-check-policy",
                    "ALLOWED"
                ),
                new PolicyGate(
                    "ban-the-box",
                    "Ban-the-Box Compliance",
                    PolicyType.STATUTORY,
                    "background-check-policy",
                    "ALLOWED"
                )
            ),
            List.of(
                new BusinessRule("determine-check-package", "Determine Check Package",
                    "onboarding-policies", RuleCategory.EXECUTION_PARAMETER)
            ),
            new Action(
                ActionType.SYSTEM_INVOCATION,
                "backgroundCheckAdapter",
                "Submit background check request to provider",
                ActionConfig.async()
            ),
            new EventConfig(
                List.of(),
                List.of(
                    new EventEmission("BackgroundCheckInitiated", EmissionTiming.ON_START, null),
                    new EventEmission("BackgroundCheckCompleted", EmissionTiming.ON_COMPLETE, null)
                )
            ),
            new ExceptionRoutes(
                List.of(
                    new RemediationRoute("PROVIDER_UNAVAILABLE", RemediationStrategy.RETRY, 3, null),
                    new RemediationRoute("INVALID_SSN", RemediationStrategy.ALTERNATE, 0,
                        "request-ssn-correction")
                ),
                List.of(
                    new EscalationRoute("PROVIDER_TIMEOUT", "escalate-to-hr", "hr.manager", 60)
                )
            )
        );
    }

    /**
     * AI-powered analysis of background check results.
     *
     * <p>Uses AI to evaluate background check findings and produce a risk
     * assessment with recommendation. Low-risk approvals skip HR review.
     */
    public static Node aiAnalyzeBackgroundCheck() {
        return new Node(
            AI_ANALYZE_BACKGROUND,
            "AI Analyze Background Check",
            "AI analyzes background check results and provides risk assessment",
            1,
            new Preconditions(
                List.of(),
                List.of(FeelExpression.of("backgroundCheck.status = \"COMPLETED\""))
            ),
            List.of(),
            List.of(),
            new Action(
                ActionType.AGENT_ASSISTED,
                "aiBackgroundAnalyst",
                "AI analyzes background check findings",
                new ActionConfig(true, 120, 2, null, null)
            ),
            new EventConfig(
                List.of(new EventSubscription("BackgroundCheckCompleted", null)),
                List.of(
                    new EventEmission("AiAnalysisStarted", EmissionTiming.ON_START, null),
                    new EventEmission("AiAnalysisCompleted", EmissionTiming.ON_COMPLETE, null)
                )
            ),
            new ExceptionRoutes(
                List.of(
                    new RemediationRoute("AI_TIMEOUT", RemediationStrategy.RETRY, 2, null),
                    new RemediationRoute("AI_ERROR", RemediationStrategy.SKIP, 0, null)
                ),
                List.of()
            )
        );
    }

    /**
     * Review background check results (for adverse findings).
     */
    public static Node reviewBackgroundResults() {
        return new Node(
            REVIEW_BACKGROUND_RESULTS,
            "Review Background Results",
            "HR reviews adverse background check findings",
            1,
            new Preconditions(
                List.of(),
                List.of(
                    FeelExpression.of("backgroundCheck.status = \"COMPLETED\""),
                    FeelExpression.of("backgroundCheck.requiresReview = true")
                )
            ),
            List.of(
                new PolicyGate(
                    "adverse-action-policy",
                    "Adverse Action Policy",
                    PolicyType.COMPLIANCE,
                    "adverse-action-policy",
                    "REVIEW_REQUIRED"
                )
            ),
            List.of(
                new BusinessRule("determine-reviewer", "Determine Reviewer",
                    "onboarding-policies", RuleCategory.EXECUTION_PARAMETER),
                new BusinessRule("review-sla", "Review SLA",
                    "onboarding-policies", RuleCategory.SLA)
            ),
            new Action(
                ActionType.HUMAN_TASK,
                "hrReviewTask",
                "HR manager reviews background check findings",
                new ActionConfig(false, 86400, 0, "hr.manager", "background-review-form")
            ),
            new EventConfig(
                List.of(new EventSubscription("BackgroundCheckCompleted",
                    FeelExpression.of("event.requiresReview = true"))),
                List.of(new EventEmission("BackgroundReviewCompleted", EmissionTiming.ON_COMPLETE, null))
            ),
            new ExceptionRoutes(
                List.of(),
                List.of(
                    new EscalationRoute("SLA_BREACH", "escalate-to-legal", "legal.counsel", 120)
                )
            )
        );
    }

    /**
     * Order equipment based on role and location.
     */
    public static Node orderEquipment() {
        return new Node(
            ORDER_EQUIPMENT,
            "Order Equipment",
            "Order equipment based on role, location, and remote status",
            1,
            new Preconditions(
                List.of(FeelExpression.of("client.equipmentBudget != null")),
                List.of(
                    FeelExpression.of("offer.status = \"ACCEPTED\""),
                    FeelExpression.of("equipmentOrder.status = null or equipmentOrder.status = \"NOT_STARTED\"")
                )
            ),
            List.of(),
            List.of(
                new BusinessRule("equipment-package", "Equipment Package",
                    "onboarding-policies", RuleCategory.EXECUTION_PARAMETER)
            ),
            new Action(
                ActionType.SYSTEM_INVOCATION,
                "equipmentProcurement",
                "Submit equipment order to procurement system",
                ActionConfig.async()
            ),
            new EventConfig(
                List.of(),
                List.of(
                    new EventEmission("EquipmentOrdered", EmissionTiming.ON_COMPLETE, null)
                )
            ),
            new ExceptionRoutes(
                List.of(
                    new RemediationRoute("OUT_OF_STOCK", RemediationStrategy.ALTERNATE, 0,
                        "order-alternate-equipment"),
                    new RemediationRoute("BUDGET_EXCEEDED", RemediationStrategy.ALTERNATE, 0,
                        "request-budget-approval")
                ),
                List.of()
            )
        );
    }

    /**
     * Ship equipment to employee location.
     */
    public static Node shipEquipment() {
        return new Node(
            SHIP_EQUIPMENT,
            "Ship Equipment",
            "Ship ordered equipment to employee address",
            1,
            new Preconditions(
                List.of(),
                List.of(
                    FeelExpression.of("equipmentOrder.status = \"READY\""),
                    FeelExpression.of("employee.shippingAddress != null")
                )
            ),
            List.of(),
            List.of(
                new BusinessRule("shipping-priority", "Shipping Priority",
                    "onboarding-policies", RuleCategory.EXECUTION_PARAMETER)
            ),
            new Action(
                ActionType.SYSTEM_INVOCATION,
                "shippingService",
                "Create shipping label and schedule pickup",
                ActionConfig.async()
            ),
            new EventConfig(
                List.of(new EventSubscription("EquipmentReady", null)),
                List.of(
                    new EventEmission("EquipmentShipped", EmissionTiming.ON_COMPLETE, null)
                )
            ),
            ExceptionRoutes.none()
        );
    }

    /**
     * Create IT accounts (email, SSO, etc.).
     */
    public static Node createAccounts() {
        return new Node(
            CREATE_ACCOUNTS,
            "Create Accounts",
            "Provision IT accounts based on role",
            1,
            new Preconditions(
                List.of(),
                List.of(
                    FeelExpression.of("offer.status = \"ACCEPTED\""),
                    FeelExpression.of("employee.email = null")
                )
            ),
            List.of(
                new PolicyGate(
                    "access-policy",
                    "Access Policy",
                    PolicyType.ORGANIZATIONAL,
                    "access-policy",
                    "ALLOWED"
                )
            ),
            List.of(
                new BusinessRule("account-entitlements", "Account Entitlements",
                    "onboarding-policies", RuleCategory.EXECUTION_PARAMETER)
            ),
            new Action(
                ActionType.SYSTEM_INVOCATION,
                "identityProvisioningService",
                "Create email, SSO, and role-based access",
                ActionConfig.defaults()
            ),
            new EventConfig(
                List.of(),
                List.of(
                    new EventEmission("AccountsCreated", EmissionTiming.ON_COMPLETE, null)
                )
            ),
            new ExceptionRoutes(
                List.of(
                    new RemediationRoute("DUPLICATE_EMAIL", RemediationStrategy.ALTERNATE, 0,
                        "generate-alternate-email")
                ),
                List.of()
            )
        );
    }

    /**
     * Collect required HR documents.
     */
    public static Node collectDocuments() {
        return new Node(
            COLLECT_DOCUMENTS,
            "Collect Documents",
            "Collect required HR and tax documents from employee",
            1,
            new Preconditions(
                List.of(),
                List.of(FeelExpression.of("offer.status = \"ACCEPTED\""))
            ),
            List.of(
                new PolicyGate(
                    "document-compliance",
                    "Document Compliance",
                    PolicyType.STATUTORY,
                    "document-requirements-policy",
                    "DOCUMENTS_REQUIRED"
                )
            ),
            List.of(
                new BusinessRule("required-documents", "Required Documents",
                    "onboarding-policies", RuleCategory.OBLIGATION)
            ),
            new Action(
                ActionType.HUMAN_TASK,
                "documentCollectionTask",
                "Employee submits required documents",
                new ActionConfig(false, 604800, 0, "employee", "document-upload-form")
            ),
            new EventConfig(
                List.of(),
                List.of(
                    new EventEmission("DocumentsCollected", EmissionTiming.ON_COMPLETE, null)
                )
            ),
            new ExceptionRoutes(
                List.of(),
                List.of(
                    new EscalationRoute("DOCUMENTS_OVERDUE", "escalate-to-hr", "hr.generalist", 72)
                )
            )
        );
    }

    /**
     * Verify I-9 employment eligibility.
     */
    public static Node verifyI9() {
        return new Node(
            VERIFY_I9,
            "Verify I-9",
            "Complete I-9 employment eligibility verification",
            1,
            new Preconditions(
                List.of(),
                List.of(
                    FeelExpression.of("documents.i9Part1Completed = true"),
                    FeelExpression.of("starts with(employee.location, \"US-\")")
                )
            ),
            List.of(
                new PolicyGate(
                    "i9-compliance",
                    "I-9 Compliance",
                    PolicyType.STATUTORY,
                    "i9-policy",
                    "VERIFICATION_REQUIRED"
                )
            ),
            List.of(
                new BusinessRule("i9-deadline", "I-9 Deadline",
                    "onboarding-policies", RuleCategory.SLA)
            ),
            new Action(
                ActionType.HUMAN_TASK,
                "i9VerificationTask",
                "HR verifies I-9 documents in person or via authorized representative",
                new ActionConfig(false, 259200, 0, "hr.verifier", "i9-verification-form")
            ),
            new EventConfig(
                List.of(new EventSubscription("EmployeeStarted", null)),
                List.of(
                    new EventEmission("I9Verified", EmissionTiming.ON_COMPLETE, null)
                )
            ),
            new ExceptionRoutes(
                List.of(),
                List.of(
                    new EscalationRoute("I9_DEADLINE_BREACH", "escalate-to-legal", "legal.counsel", 24)
                )
            )
        );
    }

    /**
     * Schedule new employee orientation.
     */
    public static Node scheduleOrientation() {
        return new Node(
            SCHEDULE_ORIENTATION,
            "Schedule Orientation",
            "Schedule new employee orientation session",
            1,
            new Preconditions(
                List.of(),
                List.of(
                    FeelExpression.of("backgroundCheck.passed = true or backgroundCheck.waived = true"),
                    FeelExpression.of("employee.startDate != null")
                )
            ),
            List.of(),
            List.of(
                new BusinessRule("orientation-type", "Orientation Type",
                    "onboarding-policies", RuleCategory.EXECUTION_PARAMETER)
            ),
            new Action(
                ActionType.SYSTEM_INVOCATION,
                "calendarService",
                "Schedule orientation based on start date and location",
                ActionConfig.defaults()
            ),
            new EventConfig(
                List.of(),
                List.of(
                    new EventEmission("OrientationScheduled", EmissionTiming.ON_COMPLETE, null)
                )
            ),
            ExceptionRoutes.none()
        );
    }

    /**
     * Terminal node: Onboarding completed successfully.
     */
    public static Node onboardingComplete() {
        return new Node(
            ONBOARDING_COMPLETE,
            "Onboarding Complete",
            "All onboarding steps completed successfully",
            1,
            new Preconditions(
                List.of(),
                List.of(
                    FeelExpression.of("backgroundCheck.passed = true or backgroundCheck.waived = true"),
                    FeelExpression.of("accounts.created = true"),
                    FeelExpression.of("documents.collected = true"),
                    FeelExpression.of("orientation.scheduled = true")
                )
            ),
            List.of(),
            List.of(),
            new Action(
                ActionType.SYSTEM_INVOCATION,
                "finalizeOnboarding",
                "Mark onboarding as complete and notify stakeholders",
                ActionConfig.defaults()
            ),
            new EventConfig(
                List.of(),
                List.of(
                    new EventEmission("OnboardingCompleted", EmissionTiming.ON_COMPLETE, null)
                )
            ),
            ExceptionRoutes.none()
        );
    }

    /**
     * Terminal node: Onboarding cancelled.
     */
    public static Node onboardingCancelled() {
        return new Node(
            ONBOARDING_CANCELLED,
            "Onboarding Cancelled",
            "Onboarding was cancelled due to withdrawal or failure",
            1,
            Preconditions.none(),
            List.of(),
            List.of(),
            new Action(
                ActionType.SYSTEM_INVOCATION,
                "cancelOnboarding",
                "Clean up and notify stakeholders of cancellation",
                ActionConfig.defaults()
            ),
            new EventConfig(
                List.of(
                    new EventSubscription("CandidateWithdrew", null),
                    new EventSubscription("OfferRescinded", null),
                    new EventSubscription("BackgroundCheckFailed", null)
                ),
                List.of(
                    new EventEmission("OnboardingCancelled", EmissionTiming.ON_COMPLETE, null)
                )
            ),
            ExceptionRoutes.none()
        );
    }

    /**
     * Returns all nodes in the onboarding process.
     */
    public static List<Node> all() {
        return List.of(
            offerAccepted(),
            validateCandidate(),
            runBackgroundCheck(),
            aiAnalyzeBackgroundCheck(),
            reviewBackgroundResults(),
            orderEquipment(),
            shipEquipment(),
            createAccounts(),
            collectDocuments(),
            verifyI9(),
            scheduleOrientation(),
            onboardingComplete(),
            onboardingCancelled()
        );
    }
}
