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

package com.ihelio.cpg.application.handler;

import com.ihelio.cpg.domain.action.ActionContext;
import com.ihelio.cpg.domain.action.ActionHandler;
import com.ihelio.cpg.domain.action.ActionResult;
import com.ihelio.cpg.domain.model.Node;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Action handler for onboarding process nodes.
 *
 * <p>Handles system invocation actions specific to the employee onboarding workflow.
 */
@Component
public class OnboardingActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(OnboardingActionHandler.class);

    private static final Set<String> SUPPORTED_HANDLERS = Set.of(
        "initializeOnboarding",
        "validateCandidateData",
        "backgroundCheckAdapter",
        "equipmentProcurement",
        "shippingService",
        "identityProvisioningService",
        "documentCollectionTask",
        "calendarService",
        "finalizeOnboarding",
        "cancelOnboarding"
    );

    @Override
    public Node.ActionType supportedType() {
        return Node.ActionType.SYSTEM_INVOCATION;
    }

    @Override
    public boolean canHandle(String handlerRef) {
        return SUPPORTED_HANDLERS.contains(handlerRef);
    }

    @Override
    public ActionResult execute(ActionContext context) {
        String handlerRef = context.handlerRef();
        log.info("Executing onboarding action: {}", handlerRef);

        return switch (handlerRef) {
            case "initializeOnboarding" -> initializeOnboarding(context);
            case "validateCandidateData" -> validateCandidateData(context);
            case "backgroundCheckAdapter" -> runBackgroundCheck(context);
            case "equipmentProcurement" -> orderEquipment(context);
            case "shippingService" -> shipEquipment(context);
            case "identityProvisioningService" -> createAccounts(context);
            case "documentCollectionTask" -> collectDocuments(context);
            case "calendarService" -> scheduleOrientation(context);
            case "finalizeOnboarding" -> finalizeOnboarding(context);
            case "cancelOnboarding" -> cancelOnboarding(context);
            default -> ActionResult.failure("Unknown handler: " + handlerRef, false);
        };
    }

    private ActionResult initializeOnboarding(ActionContext context) {
        log.info("Initializing onboarding for instance: {}",
            context.processInstance().id().value());

        // Set offer.status = "ACCEPTED" to enable downstream nodes
        return ActionResult.success(Map.of(
            "offer", Map.of("status", "ACCEPTED"),
            "onboarding", Map.of("initialized", true)
        ));
    }

    private ActionResult validateCandidateData(ActionContext context) {
        log.info("Validating candidate data for instance: {}",
            context.processInstance().id().value());

        // Mark validation as passed and set required data for next nodes
        return ActionResult.success(Map.of(
            "validation", Map.of("status", "PASSED"),
            "candidate", Map.of(
                "validated", true,
                "consentGiven", true,
                "disclosuresSigned", true
            ),
            "client", Map.of(
                "backgroundCheckProvider", "default-provider",
                "equipmentBudget", 2500
            )
        ));
    }

    private ActionResult runBackgroundCheck(ActionContext context) {
        log.info("Running background check for instance: {}",
            context.processInstance().id().value());

        // Simulate successful background check
        return ActionResult.success(Map.of(
            "backgroundCheck", Map.of(
                "status", "COMPLETED",
                "passed", true,
                "requiresReview", false
            )
        ));
    }

    private ActionResult orderEquipment(ActionContext context) {
        log.info("Ordering equipment for instance: {}",
            context.processInstance().id().value());

        return ActionResult.success(Map.of(
            "equipmentOrder", Map.of(
                "status", "READY",
                "orderId", "EQ-" + System.currentTimeMillis()
            ),
            "employee", Map.of("shippingAddress", "123 Main St")
        ));
    }

    private ActionResult shipEquipment(ActionContext context) {
        log.info("Shipping equipment for instance: {}",
            context.processInstance().id().value());

        return ActionResult.success(Map.of(
            "equipment", Map.of("shipped", true),
            "shipment", Map.of(
                "trackingNumber", "TRK-" + System.currentTimeMillis(),
                "carrier", "FedEx"
            )
        ));
    }

    private ActionResult createAccounts(ActionContext context) {
        log.info("Creating accounts for instance: {}",
            context.processInstance().id().value());

        return ActionResult.success(Map.of(
            "accounts", Map.of("created", true),
            "employee", Map.of(
                "email", "employee@company.com",
                "startDate", java.time.LocalDate.now().plusDays(14).toString()
            )
        ));
    }

    private ActionResult collectDocuments(ActionContext context) {
        log.info("Collecting documents for instance: {}",
            context.processInstance().id().value());

        return ActionResult.success(Map.of(
            "documents", Map.of(
                "collected", true,
                "i9Part1Completed", true
            )
        ));
    }

    private ActionResult scheduleOrientation(ActionContext context) {
        log.info("Scheduling orientation for instance: {}",
            context.processInstance().id().value());

        return ActionResult.success(Map.of(
            "orientation", Map.of(
                "scheduled", true,
                "date", java.time.LocalDate.now().plusDays(14).toString()
            )
        ));
    }

    private ActionResult finalizeOnboarding(ActionContext context) {
        log.info("Finalizing onboarding for instance: {}",
            context.processInstance().id().value());

        return ActionResult.success(Map.of(
            "onboarding", Map.of("completed", true)
        ));
    }

    private ActionResult cancelOnboarding(ActionContext context) {
        log.info("Cancelling onboarding for instance: {}",
            context.processInstance().id().value());

        return ActionResult.success(Map.of(
            "onboarding", Map.of("cancelled", true)
        ));
    }
}
