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

package com.ihelio.cpg.application.document;

import static com.ihelio.cpg.application.document.DocumentNodes.ASSIGN_REVIEWER;
import static com.ihelio.cpg.application.document.DocumentNodes.DOCUMENT_APPROVED;
import static com.ihelio.cpg.application.document.DocumentNodes.DOCUMENT_REJECTED;
import static com.ihelio.cpg.application.document.DocumentNodes.PUBLISH_DOCUMENT;
import static com.ihelio.cpg.application.document.DocumentNodes.REQUEST_REVISION;
import static com.ihelio.cpg.application.document.DocumentNodes.REVIEW_DOCUMENT;
import static com.ihelio.cpg.application.document.DocumentNodes.SCAN_DOCUMENT;
import static com.ihelio.cpg.application.document.DocumentNodes.UPLOAD_DOCUMENT;

import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Edge.CompensationSemantics;
import com.ihelio.cpg.domain.model.Edge.EventTriggers;
import com.ihelio.cpg.domain.model.Edge.ExecutionSemantics;
import com.ihelio.cpg.domain.model.Edge.GuardConditions;
import com.ihelio.cpg.domain.model.Edge.Priority;
import com.ihelio.cpg.domain.model.FeelExpression;
import java.util.List;

/**
 * Factory for creating edges in the document review process.
 *
 * <p>This class defines all permissible transitions between decision points
 * in the document workflow, including:
 * <ul>
 *   <li>Upload to scan flow</li>
 *   <li>Reviewer assignment</li>
 *   <li>Review decision paths</li>
 *   <li>Revision loop</li>
 *   <li>Publication path</li>
 * </ul>
 */
public final class DocumentEdges {

    private DocumentEdges() {
    }

    // =========================================================================
    // Upload and Scan Flow
    // =========================================================================

    /**
     * Upload document triggers scan.
     */
    public static Edge uploadToScan() {
        return new Edge(
            new Edge.EdgeId("upload-to-scan"),
            "Scan Document",
            "Scan uploaded document for compliance",
            UPLOAD_DOCUMENT,
            SCAN_DOCUMENT,
            GuardConditions.alwaysTrue(),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.activatedBy("DocumentUploaded"),
            CompensationSemantics.none()
        );
    }

    /**
     * Scan passed - assign reviewer.
     */
    public static Edge scanToAssign() {
        return new Edge(
            new Edge.EdgeId("scan-to-assign"),
            "Assign Reviewer",
            "Assign reviewer after scan passes",
            SCAN_DOCUMENT,
            ASSIGN_REVIEWER,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("scan.passed = true")
            )),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.activatedBy("ScanCompleted"),
            CompensationSemantics.none()
        );
    }

    /**
     * Scan failed - reject document.
     */
    public static Edge scanToRejected() {
        return new Edge(
            new Edge.EdgeId("scan-to-rejected"),
            "Scan Failed",
            "Reject document due to scan failure",
            SCAN_DOCUMENT,
            DOCUMENT_REJECTED,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("scan.passed = false")
            )),
            ExecutionSemantics.sequential(),
            Priority.exclusive(1000),
            EventTriggers.activatedBy("ScanCompleted"),
            CompensationSemantics.none()
        );
    }

    // =========================================================================
    // Review Flow
    // =========================================================================

    /**
     * Reviewer assigned - start review.
     */
    public static Edge assignToReview() {
        return new Edge(
            new Edge.EdgeId("assign-to-review"),
            "Start Review",
            "Reviewer begins document review",
            ASSIGN_REVIEWER,
            REVIEW_DOCUMENT,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("reviewer.assigned = true")
            )),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.activatedBy("ReviewerAssigned"),
            CompensationSemantics.none()
        );
    }

    /**
     * Review approved - publish document.
     */
    public static Edge reviewToPublish() {
        return new Edge(
            new Edge.EdgeId("review-to-publish"),
            "Approved - Publish",
            "Publish document after approval",
            REVIEW_DOCUMENT,
            PUBLISH_DOCUMENT,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("review.decision = \"APPROVED\"")
            )),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.activatedBy("ReviewDecision"),
            CompensationSemantics.none()
        );
    }

    /**
     * Review requires revision - request changes.
     */
    public static Edge reviewToRevision() {
        return new Edge(
            new Edge.EdgeId("review-to-revision"),
            "Revision Required",
            "Request revision from author",
            REVIEW_DOCUMENT,
            REQUEST_REVISION,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("review.decision = \"REVISION_REQUIRED\""),
                FeelExpression.of("document.revisionCount < 3")
            )),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.activatedBy("ReviewDecision"),
            CompensationSemantics.none()
        );
    }

    /**
     * Review rejected - reject document.
     */
    public static Edge reviewToRejected() {
        return new Edge(
            new Edge.EdgeId("review-to-rejected"),
            "Review Rejected",
            "Reject document after review",
            REVIEW_DOCUMENT,
            DOCUMENT_REJECTED,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("review.decision = \"REJECTED\"")
            )),
            ExecutionSemantics.sequential(),
            Priority.exclusive(1000),
            EventTriggers.activatedBy("ReviewDecision"),
            CompensationSemantics.none()
        );
    }

    /**
     * Too many revisions - reject document.
     */
    public static Edge revisionLimitToRejected() {
        return new Edge(
            new Edge.EdgeId("revision-limit-rejected"),
            "Revision Limit Exceeded",
            "Reject document after too many revisions",
            REVIEW_DOCUMENT,
            DOCUMENT_REJECTED,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("review.decision = \"REVISION_REQUIRED\""),
                FeelExpression.of("document.revisionCount >= 3")
            )),
            ExecutionSemantics.sequential(),
            Priority.exclusive(900),
            EventTriggers.activatedBy("ReviewDecision"),
            CompensationSemantics.none()
        );
    }

    // =========================================================================
    // Revision Loop
    // =========================================================================

    /**
     * Revision submitted - return to review.
     */
    public static Edge revisionToReview() {
        return new Edge(
            new Edge.EdgeId("revision-to-review"),
            "Revision Submitted",
            "Return to review after revision",
            REQUEST_REVISION,
            REVIEW_DOCUMENT,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("revision.submitted = true")
            )),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.activatedBy("RevisionSubmitted"),
            CompensationSemantics.none()
        );
    }

    // =========================================================================
    // Publication Flow
    // =========================================================================

    /**
     * Document published - complete.
     */
    public static Edge publishToApproved() {
        return new Edge(
            new Edge.EdgeId("publish-to-approved"),
            "Publication Complete",
            "Mark document as approved after publication",
            PUBLISH_DOCUMENT,
            DOCUMENT_APPROVED,
            GuardConditions.ofContext(List.of(
                FeelExpression.of("document.published = true")
            )),
            ExecutionSemantics.sequential(),
            Priority.defaults(),
            EventTriggers.activatedBy("DocumentPublished"),
            CompensationSemantics.none()
        );
    }

    /**
     * Returns all edges in the document review process.
     */
    public static List<Edge> all() {
        return List.of(
            // Upload and scan flow
            uploadToScan(),
            scanToAssign(),
            scanToRejected(),

            // Review flow
            assignToReview(),
            reviewToPublish(),
            reviewToRevision(),
            reviewToRejected(),
            revisionLimitToRejected(),

            // Revision loop
            revisionToReview(),

            // Publication flow
            publishToApproved()
        );
    }
}
