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

import static com.ihelio.cpg.application.document.DocumentNodes.DOCUMENT_APPROVED;
import static com.ihelio.cpg.application.document.DocumentNodes.DOCUMENT_REJECTED;
import static com.ihelio.cpg.application.document.DocumentNodes.UPLOAD_DOCUMENT;

import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.model.ProcessGraph.Metadata;
import com.ihelio.cpg.domain.model.ProcessGraph.ProcessGraphId;
import com.ihelio.cpg.domain.model.ProcessGraph.ProcessGraphStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Builder for the document review process graph.
 *
 * <p>This builder assembles the document review workflow including:
 * <ul>
 *   <li>8 decision nodes covering upload, scan, review, revision, and publication</li>
 *   <li>10 edges defining permissible transitions with FEEL guard conditions</li>
 *   <li>Revision loop with maximum retry limit</li>
 *   <li>Automated compliance scanning</li>
 * </ul>
 *
 * <p>Flow diagram:
 * <pre>
 *   Upload → Scan → Assign Reviewer → Review
 *                        ↑              ↓
 *                        │    ┌─────────┼─────────┐
 *                        │    ↓         ↓         ↓
 *                     Revision     Publish    Rejected
 *                                    ↓
 *                                 Approved
 * </pre>
 */
public final class DocumentProcessGraphBuilder {

    private static final ProcessGraphId GRAPH_ID = new ProcessGraphId("document-review");
    private static final int CURRENT_VERSION = 1;

    private DocumentProcessGraphBuilder() {
    }

    /**
     * Builds the complete document review process graph.
     *
     * @return the fully configured process graph
     */
    public static ProcessGraph build() {
        return new ProcessGraph(
            GRAPH_ID,
            "Document Review",
            "Document review workflow with automated scanning, review, and revision loop",
            CURRENT_VERSION,
            ProcessGraphStatus.PUBLISHED,
            DocumentNodes.all(),
            DocumentEdges.all(),
            List.of(UPLOAD_DOCUMENT),
            List.of(DOCUMENT_APPROVED, DOCUMENT_REJECTED),
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
                "domain", "content",
                "process-type", "review",
                "max-revisions", "3"
            )
        );
    }
}
