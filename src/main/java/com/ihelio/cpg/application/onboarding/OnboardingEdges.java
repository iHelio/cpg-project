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

import static com.ihelio.cpg.application.onboarding.OnboardingNodes.AI_ANALYZE_BACKGROUND;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.COLLECT_DOCUMENTS;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.CREATE_ACCOUNTS;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.OFFER_ACCEPTED;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.ONBOARDING_CANCELLED;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.ONBOARDING_COMPLETE;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.ORDER_EQUIPMENT;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.REVIEW_BACKGROUND_RESULTS;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.RUN_BACKGROUND_CHECK;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.SCHEDULE_ORIENTATION;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.SHIP_EQUIPMENT;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.VALIDATE_CANDIDATE;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.VERIFY_I9;

import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Edge.CompensationSemantics;
import com.ihelio.cpg.domain.model.Edge.EventCondition;
import com.ihelio.cpg.domain.model.Edge.EventTriggers;
import com.ihelio.cpg.domain.model.Edge.ExecutionSemantics;
import com.ihelio.cpg.domain.model.Edge.GuardConditions;
import com.ihelio.cpg.domain.model.Edge.JoinType;
import com.ihelio.cpg.domain.model.Edge.Priority;
import com.ihelio.cpg.domain.model.FeelExpression;
import java.util.List;

/**
 * Factory for creating edges in the employee onboarding process.
 *
 * <p>This class defines all permissible transitions between decision points
 * in the onboarding workflow, including:
 * <ul>
 *   <li>Sequential progressions with guard conditions</li>
 *   <li>Parallel branches for concurrent activities</li>
 *   <li>Conditional paths based on background check results</li>
 *   <li>Compensation paths for cancellation scenarios</li>
 * </ul>
 */
public final class OnboardingEdges {

    private OnboardingEdges() {
    }

    // =========================================================================
    // Initial Flow: Offer Accepted → Validation → Background Check
    // =========================================================================

    /**
     * Offer accepted triggers candidate validation.
     */
    public static Edge offerAcceptedToValidate() {
        return new Edge(
            new Edge.EdgeId("offer-to-validate"),
            "Start Validation",
            "Begin candidate validation after offer acceptance",
            OFFER_ACCEPTED,
            VALIDATE_CANDIDATE,
            GuardConditions.alwaysTrue(),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.activatedBy("OnboardingStarted"),
            CompensationSemantics.none()
        );
    }

    /**
     * After validation, run background check.
     */
    public static Edge validateToBackgroundCheck() {
        return new Edge(
            new Edge.EdgeId("validate-to-bgcheck"),
            "Initiate Background Check",
            "Start background check after candidate validation",
            VALIDATE_CANDIDATE,
            RUN_BACKGROUND_CHECK,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("validation.status = \"PASSED\"")
            )),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.none(),
            CompensationSemantics.none()
        );
    }

    // =========================================================================
    // Background Check to AI Analysis
    // =========================================================================

    /**
     * Background check completed - route to AI analysis.
     */
    public static Edge backgroundCheckToAiAnalysis() {
        return new Edge(
            new Edge.EdgeId("bgcheck-to-ai-analysis"),
            "AI Analysis",
            "Route completed background check to AI analysis",
            RUN_BACKGROUND_CHECK,
            AI_ANALYZE_BACKGROUND,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("backgroundCheck.status = \"COMPLETED\"")
            )),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.activatedBy("BackgroundCheckCompleted"),
            CompensationSemantics.none()
        );
    }

    // =========================================================================
    // AI Analysis Paths
    // =========================================================================

    /**
     * AI analysis recommends review or risk score is high - route to HR review.
     */
    public static Edge aiAnalysisToReview() {
        return new Edge(
            new Edge.EdgeId("ai-analysis-to-review"),
            "AI Review Required",
            "Route to HR review when AI recommends review or risk is high",
            AI_ANALYZE_BACKGROUND,
            REVIEW_BACKGROUND_RESULTS,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("aiAnalysis.requiresReview = true")
            )),
            ExecutionSemantics.sequential(),
            Priority.high(),
            EventTriggers.activatedBy("AiAnalysisCompleted"),
            CompensationSemantics.none()
        );
    }

    /**
     * AI analysis approved with low risk - proceed to equipment ordering.
     */
    public static Edge aiAnalysisToOrderEquipment() {
        return new Edge(
            new Edge.EdgeId("ai-analysis-to-equipment"),
            "AI Approved - Equipment",
            "Start equipment ordering after AI approval",
            AI_ANALYZE_BACKGROUND,
            ORDER_EQUIPMENT,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("aiAnalysis.passed = true")
            )),
            ExecutionSemantics.parallel(JoinType.ALL),
            Priority.defaults(),
            EventTriggers.activatedBy("AiAnalysisCompleted"),
            CompensationSemantics.none()
        );
    }

    /**
     * AI analysis approved with low risk - proceed to account creation.
     */
    public static Edge aiAnalysisToCreateAccounts() {
        return new Edge(
            new Edge.EdgeId("ai-analysis-to-accounts"),
            "AI Approved - Accounts",
            "Start account provisioning after AI approval",
            AI_ANALYZE_BACKGROUND,
            CREATE_ACCOUNTS,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("aiAnalysis.passed = true")
            )),
            ExecutionSemantics.parallel(JoinType.ALL),
            Priority.defaults(),
            EventTriggers.activatedBy("AiAnalysisCompleted"),
            CompensationSemantics.none()
        );
    }

    /**
     * AI analysis approved with low risk - proceed to document collection.
     */
    public static Edge aiAnalysisToCollectDocuments() {
        return new Edge(
            new Edge.EdgeId("ai-analysis-to-documents"),
            "AI Approved - Documents",
            "Start document collection after AI approval",
            AI_ANALYZE_BACKGROUND,
            COLLECT_DOCUMENTS,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("aiAnalysis.passed = true")
            )),
            ExecutionSemantics.parallel(JoinType.ALL),
            Priority.defaults(),
            EventTriggers.activatedBy("AiAnalysisCompleted"),
            CompensationSemantics.none()
        );
    }

    // =========================================================================
    // Background Review Paths (unchanged, triggered from AI review route)
    // =========================================================================

    // =========================================================================
    // Background Review Paths
    // =========================================================================

    /**
     * Background review approved - proceed with equipment.
     */
    public static Edge reviewToOrderEquipment() {
        return new Edge(
            new Edge.EdgeId("review-to-equipment"),
            "Review Approved - Equipment",
            "Proceed with equipment ordering after favorable review",
            REVIEW_BACKGROUND_RESULTS,
            ORDER_EQUIPMENT,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("backgroundReview.decision = \"APPROVED\"")
            )),
            ExecutionSemantics.parallel(JoinType.ALL),
            Priority.defaults(),
            EventTriggers.activatedBy("BackgroundReviewCompleted"),
            CompensationSemantics.none()
        );
    }

    /**
     * Background review approved - proceed with accounts.
     */
    public static Edge reviewToCreateAccounts() {
        return new Edge(
            new Edge.EdgeId("review-to-accounts"),
            "Review Approved - Accounts",
            "Proceed with account creation after favorable review",
            REVIEW_BACKGROUND_RESULTS,
            CREATE_ACCOUNTS,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("backgroundReview.decision = \"APPROVED\"")
            )),
            ExecutionSemantics.parallel(JoinType.ALL),
            Priority.defaults(),
            EventTriggers.activatedBy("BackgroundReviewCompleted"),
            CompensationSemantics.none()
        );
    }

    /**
     * Background review approved - proceed with documents.
     */
    public static Edge reviewToCollectDocuments() {
        return new Edge(
            new Edge.EdgeId("review-to-documents"),
            "Review Approved - Documents",
            "Proceed with document collection after favorable review",
            REVIEW_BACKGROUND_RESULTS,
            COLLECT_DOCUMENTS,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("backgroundReview.decision = \"APPROVED\"")
            )),
            ExecutionSemantics.parallel(JoinType.ALL),
            Priority.defaults(),
            EventTriggers.activatedBy("BackgroundReviewCompleted"),
            CompensationSemantics.none()
        );
    }

    /**
     * Background review rejected - cancel onboarding.
     */
    public static Edge reviewToCancelled() {
        return new Edge(
            new Edge.EdgeId("review-to-cancelled"),
            "Review Rejected",
            "Cancel onboarding when background review is unfavorable",
            REVIEW_BACKGROUND_RESULTS,
            ONBOARDING_CANCELLED,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("backgroundReview.decision = \"REJECTED\"")
            )),
            ExecutionSemantics.sequential(),
            Priority.exclusive(1000),
            EventTriggers.activatedBy("BackgroundReviewCompleted"),
            CompensationSemantics.escalate()
        );
    }

    // =========================================================================
    // IT Provisioning Flow
    // =========================================================================

    /**
     * Equipment ordered - ship to employee.
     */
    public static Edge orderEquipmentToShip() {
        return new Edge(
            new Edge.EdgeId("equipment-to-ship"),
            "Ship Equipment",
            "Ship equipment after order is ready",
            ORDER_EQUIPMENT,
            SHIP_EQUIPMENT,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("equipmentOrder.status = \"READY\"")
            )),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.activatedBy("EquipmentReady"),
            CompensationSemantics.retry(2)
        );
    }

    // =========================================================================
    // HR Documentation Flow
    // =========================================================================

    /**
     * Documents collected - verify I-9 for US employees.
     */
    public static Edge documentsToVerifyI9() {
        return new Edge(
            new Edge.EdgeId("documents-to-i9"),
            "Verify I-9",
            "Initiate I-9 verification for US employees",
            COLLECT_DOCUMENTS,
            VERIFY_I9,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("documents.i9Part1Completed = true"),
                FeelExpression.of("starts with(employee.location, \"US-\")")
            )),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.activatedBy("DocumentsCollected"),
            CompensationSemantics.none()
        );
    }

    // =========================================================================
    // Convergence: Schedule Orientation
    // =========================================================================

    /**
     * Accounts created - ready for orientation.
     */
    public static Edge accountsToOrientation() {
        return new Edge(
            new Edge.EdgeId("accounts-to-orientation"),
            "Accounts Ready",
            "Accounts created, ready for orientation scheduling",
            CREATE_ACCOUNTS,
            SCHEDULE_ORIENTATION,
            new GuardConditions(
                List.of(
                    FeelExpression.of("accounts.created = true"),
                    FeelExpression.of("backgroundCheck.passed = true or backgroundCheck.waived = true")
                ),
                List.of(),
                List.of(),
                List.of(EventCondition.occurred("DocumentsCollected"))
            ),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.none(),
            CompensationSemantics.none()
        );
    }

    /**
     * Equipment shipped - ready for orientation.
     */
    public static Edge shipToOrientation() {
        return new Edge(
            new Edge.EdgeId("ship-to-orientation"),
            "Equipment Shipped",
            "Equipment shipped, ready for orientation scheduling",
            SHIP_EQUIPMENT,
            SCHEDULE_ORIENTATION,
            new GuardConditions(
                List.of(
                    FeelExpression.of("equipment.shipped = true"),
                    FeelExpression.of("accounts.created = true"),
                    FeelExpression.of("documents.collected = true")
                ),
                List.of(),
                List.of(),
                List.of()
            ),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.activatedBy("EquipmentShipped"),
            CompensationSemantics.none()
        );
    }

    /**
     * I-9 verified - ready for orientation.
     */
    public static Edge i9ToOrientation() {
        return new Edge(
            new Edge.EdgeId("i9-to-orientation"),
            "I-9 Verified",
            "I-9 verification complete, ready for orientation",
            VERIFY_I9,
            SCHEDULE_ORIENTATION,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("i9.verified = true"),
                FeelExpression.of("accounts.created = true")
            )),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.activatedBy("I9Verified"),
            CompensationSemantics.none()
        );
    }

    // =========================================================================
    // Completion
    // =========================================================================

    /**
     * Orientation scheduled - complete onboarding.
     */
    public static Edge orientationToComplete() {
        return new Edge(
            new Edge.EdgeId("orientation-to-complete"),
            "Complete Onboarding",
            "All prerequisites met, finalize onboarding",
            SCHEDULE_ORIENTATION,
            ONBOARDING_COMPLETE,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("orientation.scheduled = true"),
                FeelExpression.of("accounts.created = true"),
                FeelExpression.of("documents.collected = true"),
                FeelExpression.of("backgroundCheck.passed = true or backgroundCheck.waived = true")
            )),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.activatedBy("OrientationScheduled"),
            CompensationSemantics.none()
        );
    }

    // =========================================================================
    // Cancellation Paths
    // =========================================================================

    /**
     * Validation failed - cancel onboarding.
     */
    public static Edge validateToCancelled() {
        return new Edge(
            new Edge.EdgeId("validate-to-cancelled"),
            "Validation Failed",
            "Cancel onboarding when candidate validation fails",
            VALIDATE_CANDIDATE,
            ONBOARDING_CANCELLED,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("validation.status = \"FAILED\"")
            )),
            ExecutionSemantics.sequential(),
            Priority.exclusive(1000),
            EventTriggers.none(),
            CompensationSemantics.none()
        );
    }

    /**
     * Background check failed - cancel onboarding.
     */
    public static Edge backgroundCheckToCancelled() {
        return new Edge(
            new Edge.EdgeId("bgcheck-to-cancelled"),
            "Background Check Failed",
            "Cancel onboarding when background check fails",
            RUN_BACKGROUND_CHECK,
            ONBOARDING_CANCELLED,
            new GuardConditions(
                List.of(
                    FeelExpression.of("backgroundCheck.status = \"FAILED\"")
                ),
                List.of(),
                List.of(),
                List.of(EventCondition.occurred("BackgroundCheckFailed"))
            ),
            ExecutionSemantics.sequential(),
            Priority.exclusive(1000),
            EventTriggers.activatedBy("BackgroundCheckFailed"),
            CompensationSemantics.escalate()
        );
    }

    /**
     * Returns all edges in the onboarding process.
     */
    public static List<Edge> all() {
        return List.of(
            // Initial flow
            offerAcceptedToValidate(),
            validateToBackgroundCheck(),

            // Background check to AI analysis
            backgroundCheckToAiAnalysis(),

            // AI analysis paths
            aiAnalysisToReview(),
            aiAnalysisToOrderEquipment(),
            aiAnalysisToCreateAccounts(),
            aiAnalysisToCollectDocuments(),

            // Review paths (after AI recommends review)
            reviewToOrderEquipment(),
            reviewToCreateAccounts(),
            reviewToCollectDocuments(),
            reviewToCancelled(),

            // IT provisioning
            orderEquipmentToShip(),

            // HR documentation
            documentsToVerifyI9(),

            // Convergence
            accountsToOrientation(),
            shipToOrientation(),
            i9ToOrientation(),

            // Completion
            orientationToComplete(),

            // Cancellation
            validateToCancelled(),
            backgroundCheckToCancelled()
        );
    }
}
