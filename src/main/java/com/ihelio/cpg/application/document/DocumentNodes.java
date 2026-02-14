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
 * Factory for creating nodes in the document review process.
 *
 * <p>This class defines all decision points in the document workflow:
 * <ul>
 *   <li>Document submission and upload</li>
 *   <li>Automated compliance scan</li>
 *   <li>Reviewer assignment and review</li>
 *   <li>Revision requests</li>
 *   <li>Final approval or rejection</li>
 *   <li>Document publishing</li>
 * </ul>
 */
public final class DocumentNodes {

    // Node IDs as constants for reference in edges
    public static final NodeId UPLOAD_DOCUMENT = new NodeId("upload-document");
    public static final NodeId SCAN_DOCUMENT = new NodeId("scan-document");
    public static final NodeId ASSIGN_REVIEWER = new NodeId("assign-reviewer");
    public static final NodeId REVIEW_DOCUMENT = new NodeId("review-document");
    public static final NodeId REQUEST_REVISION = new NodeId("request-revision");
    public static final NodeId PUBLISH_DOCUMENT = new NodeId("publish-document");
    public static final NodeId DOCUMENT_APPROVED = new NodeId("document-approved");
    public static final NodeId DOCUMENT_REJECTED = new NodeId("document-rejected");

    private DocumentNodes() {
    }

    /**
     * Entry node: Author uploads document for review.
     */
    public static Node uploadDocument() {
        return new Node(
            UPLOAD_DOCUMENT,
            "Upload Document",
            "Author uploads document for review",
            1,
            Preconditions.none(),
            List.of(),
            List.of(),
            new Action(
                ActionType.HUMAN_TASK,
                "documentUploadForm",
                "Upload document with metadata and category",
                new ActionConfig(false, 0, 0, "author", "document-upload-form")
            ),
            new EventConfig(
                List.of(),
                List.of(new EventEmission("DocumentUploaded", EmissionTiming.ON_COMPLETE, null))
            ),
            ExceptionRoutes.none()
        );
    }

    /**
     * Automated scan for compliance, malware, and formatting.
     */
    public static Node scanDocument() {
        return new Node(
            SCAN_DOCUMENT,
            "Scan Document",
            "Automated compliance and security scan",
            1,
            new Preconditions(
                List.of(),
                List.of(FeelExpression.of("document.uploaded = true"))
            ),
            List.of(
                new PolicyGate(
                    "security-scan",
                    "Security Scan",
                    PolicyType.COMPLIANCE,
                    "document-security-policy",
                    "SCAN_PASSED"
                )
            ),
            List.of(
                new BusinessRule("document-classification", "Document Classification",
                    "document-policies", RuleCategory.DERIVATION)
            ),
            new Action(
                ActionType.SYSTEM_INVOCATION,
                "documentScanner",
                "Scan document for compliance and security issues",
                ActionConfig.async()
            ),
            new EventConfig(
                List.of(),
                List.of(new EventEmission("ScanCompleted", EmissionTiming.ON_COMPLETE, null))
            ),
            new ExceptionRoutes(
                List.of(
                    new RemediationRoute("MALWARE_DETECTED", RemediationStrategy.FAIL, 0, null)
                ),
                List.of()
            )
        );
    }

    /**
     * Assign appropriate reviewer based on document type.
     */
    public static Node assignReviewer() {
        return new Node(
            ASSIGN_REVIEWER,
            "Assign Reviewer",
            "Assign reviewer based on document type and workload",
            1,
            new Preconditions(
                List.of(),
                List.of(
                    FeelExpression.of("scan.passed = true"),
                    FeelExpression.of("document.classification != null")
                )
            ),
            List.of(),
            List.of(
                new BusinessRule("reviewer-selection", "Reviewer Selection",
                    "document-policies", RuleCategory.EXECUTION_PARAMETER),
                new BusinessRule("review-sla", "Review SLA",
                    "document-policies", RuleCategory.SLA)
            ),
            new Action(
                ActionType.SYSTEM_INVOCATION,
                "reviewerAssignment",
                "Assign reviewer based on expertise and availability",
                ActionConfig.defaults()
            ),
            new EventConfig(
                List.of(),
                List.of(new EventEmission("ReviewerAssigned", EmissionTiming.ON_COMPLETE, null))
            ),
            ExceptionRoutes.none()
        );
    }

    /**
     * Reviewer reviews document and makes decision.
     */
    public static Node reviewDocument() {
        return new Node(
            REVIEW_DOCUMENT,
            "Review Document",
            "Reviewer evaluates document and provides decision",
            1,
            new Preconditions(
                List.of(),
                List.of(
                    FeelExpression.of("reviewer.assigned = true"),
                    FeelExpression.of("document.status = \"PENDING_REVIEW\"")
                )
            ),
            List.of(
                new PolicyGate(
                    "review-guidelines",
                    "Review Guidelines",
                    PolicyType.ORGANIZATIONAL,
                    "review-guidelines-policy",
                    "GUIDELINES_MET"
                )
            ),
            List.of(
                new BusinessRule("review-checklist", "Review Checklist",
                    "document-policies", RuleCategory.OBLIGATION)
            ),
            new Action(
                ActionType.HUMAN_TASK,
                "documentReviewTask",
                "Review document against guidelines and provide decision",
                new ActionConfig(false, 259200, 0, "reviewer", "document-review-form")
            ),
            new EventConfig(
                List.of(new EventSubscription("RevisionSubmitted", null)),
                List.of(new EventEmission("ReviewDecision", EmissionTiming.ON_COMPLETE, null))
            ),
            new ExceptionRoutes(
                List.of(),
                List.of(
                    new EscalationRoute("REVIEW_SLA_BREACH", "escalate-review", "review.manager", 48)
                )
            )
        );
    }

    /**
     * Request revision from author.
     */
    public static Node requestRevision() {
        return new Node(
            REQUEST_REVISION,
            "Request Revision",
            "Request changes from document author",
            1,
            new Preconditions(
                List.of(),
                List.of(
                    FeelExpression.of("review.decision = \"REVISION_REQUIRED\""),
                    FeelExpression.of("document.revisionCount < 3")
                )
            ),
            List.of(),
            List.of(),
            new Action(
                ActionType.HUMAN_TASK,
                "revisionRequestTask",
                "Author addresses reviewer feedback",
                new ActionConfig(false, 604800, 0, "author", "revision-form")
            ),
            new EventConfig(
                List.of(),
                List.of(new EventEmission("RevisionSubmitted", EmissionTiming.ON_COMPLETE, null))
            ),
            new ExceptionRoutes(
                List.of(),
                List.of(
                    new EscalationRoute("REVISION_OVERDUE", "escalate-revision", "review.manager", 168)
                )
            )
        );
    }

    /**
     * Publish approved document.
     */
    public static Node publishDocument() {
        return new Node(
            PUBLISH_DOCUMENT,
            "Publish Document",
            "Publish approved document to repository",
            1,
            new Preconditions(
                List.of(),
                List.of(
                    FeelExpression.of("review.decision = \"APPROVED\""),
                    FeelExpression.of("document.publishTarget != null")
                )
            ),
            List.of(),
            List.of(
                new BusinessRule("publish-location", "Publish Location",
                    "document-policies", RuleCategory.EXECUTION_PARAMETER)
            ),
            new Action(
                ActionType.SYSTEM_INVOCATION,
                "documentPublisher",
                "Publish document to target repository",
                ActionConfig.defaults()
            ),
            new EventConfig(
                List.of(),
                List.of(new EventEmission("DocumentPublished", EmissionTiming.ON_COMPLETE, null))
            ),
            ExceptionRoutes.none()
        );
    }

    /**
     * Terminal node: Document approved and published.
     */
    public static Node documentApproved() {
        return new Node(
            DOCUMENT_APPROVED,
            "Document Approved",
            "Document has been approved and published",
            1,
            new Preconditions(
                List.of(),
                List.of(FeelExpression.of("document.published = true"))
            ),
            List.of(),
            List.of(),
            new Action(
                ActionType.SYSTEM_INVOCATION,
                "notifyStakeholders",
                "Notify stakeholders of document publication",
                ActionConfig.defaults()
            ),
            new EventConfig(
                List.of(),
                List.of(new EventEmission("ReviewCompleted", EmissionTiming.ON_COMPLETE, null))
            ),
            ExceptionRoutes.none()
        );
    }

    /**
     * Terminal node: Document rejected.
     */
    public static Node documentRejected() {
        return new Node(
            DOCUMENT_REJECTED,
            "Document Rejected",
            "Document has been rejected",
            1,
            Preconditions.none(),
            List.of(),
            List.of(),
            new Action(
                ActionType.SYSTEM_INVOCATION,
                "notifyRejection",
                "Notify author of rejection with feedback",
                ActionConfig.defaults()
            ),
            new EventConfig(
                List.of(),
                List.of(new EventEmission("ReviewCompleted", EmissionTiming.ON_COMPLETE, null))
            ),
            ExceptionRoutes.none()
        );
    }

    /**
     * Returns all nodes in the document review process.
     */
    public static List<Node> all() {
        return List.of(
            uploadDocument(),
            scanDocument(),
            assignReviewer(),
            reviewDocument(),
            requestRevision(),
            publishDocument(),
            documentApproved(),
            documentRejected()
        );
    }
}
