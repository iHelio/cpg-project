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
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.CANCEL_ONBOARDING;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.FINALIZE_ONBOARDING;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.INITIALIZE_ONBOARDING;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.ORDER_EQUIPMENT;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.REVIEW_BACKGROUND_RESULTS;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.RUN_BACKGROUND_CHECK;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.SCHEDULE_ORIENTATION;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.SHIP_EQUIPMENT;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.VALIDATE_CANDIDATE;
import static com.ihelio.cpg.application.onboarding.OnboardingNodes.VERIFY_I9;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ihelio.cpg.domain.model.Edge;
import com.ihelio.cpg.domain.model.Node;
import com.ihelio.cpg.domain.model.Node.ActionType;
import com.ihelio.cpg.domain.model.Node.NodeId;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.model.ProcessGraph.ProcessGraphStatus;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the employee onboarding process graph.
 */
class OnboardingProcessGraphTest {

    private ProcessGraph graph;

    @BeforeEach
    void setUp() {
        graph = OnboardingProcessGraphBuilder.build();
    }

    @Nested
    @DisplayName("Graph Structure")
    class GraphStructureTests {

        @Test
        @DisplayName("Graph has correct identity")
        void graphHasCorrectIdentity() {
            assertEquals("employee-onboarding", graph.id().value());
            assertEquals("Employee Onboarding", graph.name());
            assertEquals(1, graph.version());
            assertEquals(ProcessGraphStatus.PUBLISHED, graph.status());
        }

        @Test
        @DisplayName("Graph has all expected nodes")
        void graphHasAllExpectedNodes() {
            assertEquals(13, graph.nodes().size());

            Set<NodeId> nodeIds = graph.nodes().stream()
                .map(Node::id)
                .collect(Collectors.toSet());

            assertTrue(nodeIds.contains(INITIALIZE_ONBOARDING));
            assertTrue(nodeIds.contains(VALIDATE_CANDIDATE));
            assertTrue(nodeIds.contains(RUN_BACKGROUND_CHECK));
            assertTrue(nodeIds.contains(AI_ANALYZE_BACKGROUND));
            assertTrue(nodeIds.contains(REVIEW_BACKGROUND_RESULTS));
            assertTrue(nodeIds.contains(ORDER_EQUIPMENT));
            assertTrue(nodeIds.contains(SHIP_EQUIPMENT));
            assertTrue(nodeIds.contains(CREATE_ACCOUNTS));
            assertTrue(nodeIds.contains(COLLECT_DOCUMENTS));
            assertTrue(nodeIds.contains(VERIFY_I9));
            assertTrue(nodeIds.contains(SCHEDULE_ORIENTATION));
            assertTrue(nodeIds.contains(FINALIZE_ONBOARDING));
            assertTrue(nodeIds.contains(CANCEL_ONBOARDING));
        }

        @Test
        @DisplayName("Graph has all expected edges")
        void graphHasAllExpectedEdges() {
            assertEquals(19, graph.edges().size());
        }

        @Test
        @DisplayName("Graph has correct entry and terminal nodes")
        void graphHasCorrectEntryAndTerminalNodes() {
            assertEquals(1, graph.entryNodeIds().size());
            assertEquals(INITIALIZE_ONBOARDING, graph.entryNodeIds().get(0));

            assertEquals(2, graph.terminalNodeIds().size());
            assertTrue(graph.terminalNodeIds().contains(FINALIZE_ONBOARDING));
            assertTrue(graph.terminalNodeIds().contains(CANCEL_ONBOARDING));
        }

        @Test
        @DisplayName("Graph validation passes")
        void graphValidationPasses() {
            List<String> errors = graph.validate();
            assertTrue(errors.isEmpty(), "Expected no validation errors but got: " + errors);
        }

        @Test
        @DisplayName("Graph has metadata")
        void graphHasMetadata() {
            assertNotNull(graph.metadata());
            assertEquals("hr", graph.metadata().tags().get("domain"));
            assertEquals("onboarding", graph.metadata().tags().get("process-type"));
            assertNotNull(graph.metadata().createdAt());
        }
    }

    @Nested
    @DisplayName("Node Properties")
    class NodePropertiesTests {

        @Test
        @DisplayName("Offer accepted node is system invocation")
        void offerAcceptedNodeIsSystemInvocation() {
            Node node = graph.findNode(INITIALIZE_ONBOARDING).orElseThrow();
            assertEquals(ActionType.SYSTEM_INVOCATION, node.action().type());
            assertEquals("initializeOnboarding", node.action().handlerRef());
        }

        @Test
        @DisplayName("Background check node has policy gates")
        void backgroundCheckNodeHasPolicyGates() {
            Node node = graph.findNode(RUN_BACKGROUND_CHECK).orElseThrow();
            assertEquals(2, node.policyGates().size());

            var policyIds = node.policyGates().stream()
                .map(Node.PolicyGate::id)
                .collect(Collectors.toSet());
            assertTrue(policyIds.contains("fcra-compliance"));
            assertTrue(policyIds.contains("ban-the-box"));
        }

        @Test
        @DisplayName("Review node is human task")
        void reviewNodeIsHumanTask() {
            Node node = graph.findNode(REVIEW_BACKGROUND_RESULTS).orElseThrow();
            assertEquals(ActionType.HUMAN_TASK, node.action().type());
            assertEquals("hr.manager", node.action().config().assigneeExpression());
            assertEquals("background-review-form", node.action().config().formRef());
        }

        @Test
        @DisplayName("I-9 verification has statutory policy gate")
        void i9VerificationHasStatutoryPolicyGate() {
            Node node = graph.findNode(VERIFY_I9).orElseThrow();
            assertEquals(1, node.policyGates().size());
            assertEquals(Node.PolicyType.STATUTORY, node.policyGates().get(0).type());
        }

        @Test
        @DisplayName("Nodes have business rules")
        void nodesHaveBusinessRules() {
            Node node = graph.findNode(RUN_BACKGROUND_CHECK).orElseThrow();
            assertEquals(1, node.businessRules().size());
            assertEquals("determine-check-package", node.businessRules().get(0).id());
        }

        @Test
        @DisplayName("Cancelled node subscribes to failure events")
        void cancelledNodeSubscribesToFailureEvents() {
            Node node = graph.findNode(CANCEL_ONBOARDING).orElseThrow();
            var subscriptions = node.eventConfig().subscribes();
            assertEquals(3, subscriptions.size());

            var eventTypes = subscriptions.stream()
                .map(Node.EventSubscription::eventType)
                .collect(Collectors.toSet());
            assertTrue(eventTypes.contains("CandidateWithdrew"));
            assertTrue(eventTypes.contains("OfferRescinded"));
            assertTrue(eventTypes.contains("BackgroundCheckFailed"));
        }
    }

    @Nested
    @DisplayName("Edge Properties")
    class EdgePropertiesTests {

        @Test
        @DisplayName("Edge from offer to validate has no guards")
        void offerToValidateHasNoGuards() {
            List<Edge> edges = graph.getOutboundEdges(INITIALIZE_ONBOARDING);
            assertEquals(1, edges.size());

            Edge edge = edges.get(0);
            assertEquals(VALIDATE_CANDIDATE, edge.targetNodeId());
            assertFalse(edge.guardConditions().hasConditions());
        }

        @Test
        @DisplayName("Background check routes to AI analysis")
        void backgroundCheckRoutesToAiAnalysis() {
            List<Edge> edges = graph.getOutboundEdges(RUN_BACKGROUND_CHECK);
            assertEquals(2, edges.size()); // to AI analysis, cancelled

            assertTrue(edges.stream()
                .anyMatch(e -> e.targetNodeId().equals(AI_ANALYZE_BACKGROUND)));
        }

        @Test
        @DisplayName("AI analysis has multiple outbound edges")
        void aiAnalysisHasMultipleOutboundEdges() {
            List<Edge> edges = graph.getOutboundEdges(AI_ANALYZE_BACKGROUND);
            assertEquals(4, edges.size()); // to review, equipment, accounts, documents

            long parallelEdges = edges.stream()
                .filter(e -> e.executionSemantics().type() == Edge.ExecutionType.PARALLEL)
                .count();
            assertEquals(3, parallelEdges);
        }

        @Test
        @DisplayName("Review rejection is exclusive")
        void reviewRejectionIsExclusive() {
            List<Edge> edges = graph.getOutboundEdges(REVIEW_BACKGROUND_RESULTS);
            Edge rejectionEdge = edges.stream()
                .filter(e -> e.targetNodeId().equals(CANCEL_ONBOARDING))
                .findFirst()
                .orElseThrow();

            assertTrue(rejectionEdge.priority().exclusive());
            assertEquals(1000, rejectionEdge.priority().weight());
        }

        @Test
        @DisplayName("Equipment shipping has retry compensation")
        void equipmentShippingHasRetryCompensation() {
            List<Edge> edges = graph.getOutboundEdges(ORDER_EQUIPMENT);
            Edge shipEdge = edges.get(0);

            assertEquals(SHIP_EQUIPMENT, shipEdge.targetNodeId());
            assertTrue(shipEdge.compensationSemantics().hasCompensation());
            assertEquals(Edge.CompensationStrategy.RETRY, shipEdge.compensationSemantics().strategy());
            assertEquals(2, shipEdge.compensationSemantics().maxRetries());
        }

        @Test
        @DisplayName("Edges have guard conditions with FEEL expressions")
        void edgesHaveGuardConditions() {
            Edge validateToBackground = graph.edges().stream()
                .filter(e -> e.sourceNodeId().equals(VALIDATE_CANDIDATE)
                    && e.targetNodeId().equals(RUN_BACKGROUND_CHECK))
                .findFirst()
                .orElseThrow();

            assertTrue(validateToBackground.guardConditions().hasConditions());
            assertEquals(1, validateToBackground.guardConditions().contextConditions().size());
            assertEquals("validation.status = \"PASSED\"",
                validateToBackground.guardConditions().contextConditions().get(0).expression());
        }
    }

    @Nested
    @DisplayName("Graph Queries")
    class GraphQueryTests {

        @Test
        @DisplayName("Find node by ID")
        void findNodeById() {
            assertTrue(graph.findNode(INITIALIZE_ONBOARDING).isPresent());
            assertTrue(graph.findNode(new NodeId("nonexistent")).isEmpty());
        }

        @Test
        @DisplayName("Get outbound edges")
        void getOutboundEdges() {
            List<Edge> outbound = graph.getOutboundEdges(VALIDATE_CANDIDATE);
            assertEquals(2, outbound.size()); // to background check or cancelled
        }

        @Test
        @DisplayName("Get inbound edges")
        void getInboundEdges() {
            List<Edge> inbound = graph.getInboundEdges(SCHEDULE_ORIENTATION);
            assertEquals(3, inbound.size()); // from accounts, ship equipment, i9
        }

        @Test
        @DisplayName("Get edges activated by event")
        void getEdgesActivatedByEvent() {
            List<Edge> edges = graph.getEdgesActivatedByEvent("BackgroundCheckCompleted");
            assertEquals(1, edges.size()); // to AI analysis

            List<Edge> aiEdges = graph.getEdgesActivatedByEvent("AiAnalysisCompleted");
            assertEquals(4, aiEdges.size()); // to review, equipment, accounts, documents
        }

        @Test
        @DisplayName("Node map lookup")
        void nodeMapLookup() {
            var nodeMap = graph.nodeMap();
            assertEquals(13, nodeMap.size());
            assertNotNull(nodeMap.get(INITIALIZE_ONBOARDING));
            assertNotNull(nodeMap.get(AI_ANALYZE_BACKGROUND));
        }
    }

    @Nested
    @DisplayName("Nodes Factory")
    class NodesFactoryTests {

        @Test
        @DisplayName("All nodes returns complete list")
        void allNodesReturnsCompleteList() {
            List<Node> nodes = OnboardingNodes.all();
            assertEquals(13, nodes.size());
        }

        @Test
        @DisplayName("AI analyze node has correct properties")
        void aiAnalyzeNodeHasCorrectProperties() {
            Node node = graph.findNode(AI_ANALYZE_BACKGROUND).orElseThrow();
            assertEquals(ActionType.AGENT_ASSISTED, node.action().type());
            assertEquals("aiBackgroundAnalyst", node.action().handlerRef());
            assertTrue(node.action().config().asynchronous());
            assertEquals(120, node.action().config().timeoutSeconds());
            assertEquals(2, node.action().config().retryCount());
        }

        @Test
        @DisplayName("Each node has unique ID")
        void eachNodeHasUniqueId() {
            List<Node> nodes = OnboardingNodes.all();
            Set<String> ids = nodes.stream()
                .map(n -> n.id().value())
                .collect(Collectors.toSet());
            assertEquals(nodes.size(), ids.size());
        }

        @Test
        @DisplayName("All nodes have required fields")
        void allNodesHaveRequiredFields() {
            for (Node node : OnboardingNodes.all()) {
                assertNotNull(node.id());
                assertNotNull(node.name());
                assertNotNull(node.action());
                assertNotNull(node.action().type());
                assertNotNull(node.action().handlerRef());
            }
        }
    }

    @Nested
    @DisplayName("Edges Factory")
    class EdgesFactoryTests {

        @Test
        @DisplayName("All edges returns complete list")
        void allEdgesReturnsCompleteList() {
            List<Edge> edges = OnboardingEdges.all();
            assertEquals(19, edges.size());
        }

        @Test
        @DisplayName("Each edge has unique ID")
        void eachEdgeHasUniqueId() {
            List<Edge> edges = OnboardingEdges.all();
            Set<String> ids = edges.stream()
                .map(e -> e.id().value())
                .collect(Collectors.toSet());
            assertEquals(edges.size(), ids.size());
        }

        @Test
        @DisplayName("All edges reference valid nodes")
        void allEdgesReferenceValidNodes() {
            Set<NodeId> validNodes = OnboardingNodes.all().stream()
                .map(Node::id)
                .collect(Collectors.toSet());

            for (Edge edge : OnboardingEdges.all()) {
                assertTrue(validNodes.contains(edge.sourceNodeId()),
                    "Edge " + edge.id() + " has invalid source: " + edge.sourceNodeId());
                assertTrue(validNodes.contains(edge.targetNodeId()),
                    "Edge " + edge.id() + " has invalid target: " + edge.targetNodeId());
            }
        }
    }

    @Nested
    @DisplayName("Draft Graph")
    class DraftGraphTests {

        @Test
        @DisplayName("Draft graph has draft status")
        void draftGraphHasDraftStatus() {
            ProcessGraph draft = OnboardingProcessGraphBuilder.buildDraft();
            assertEquals(ProcessGraphStatus.DRAFT, draft.status());
        }

        @Test
        @DisplayName("Draft graph has same structure as published")
        void draftGraphHasSameStructure() {
            ProcessGraph draft = OnboardingProcessGraphBuilder.buildDraft();
            ProcessGraph published = OnboardingProcessGraphBuilder.build();

            assertEquals(published.nodes().size(), draft.nodes().size());
            assertEquals(published.edges().size(), draft.edges().size());
        }
    }
}
