# CPG Events Reference

This document describes all events configured in the CPG orchestration system, their payloads, and how they drive workflow progression.

## Table of Contents

1. [Overview](#overview)
2. [Event-Driven Architecture](#event-driven-architecture)
3. [Domain Events](#domain-events)
4. [System Events](#system-events)
5. [Event Flow Diagrams](#event-flow-diagrams)
6. [Payload Reference](#payload-reference)
7. [Guard Conditions](#guard-conditions)
8. [Sending Events](#sending-events)

---

## Overview

The CPG orchestrator is **completely event-driven**. Events are the primary mechanism for:

- **Triggering edge traversal** - Events activate edges between nodes
- **Enabling workflow progression** - Without events, workflows wait indefinitely
- **Carrying data** - Event payloads become part of the execution context
- **Routing decisions** - Guard conditions use payload data to choose paths

### Event Categories

| Category | Purpose | Examples |
|----------|---------|----------|
| **Domain Events** | Business process signals from external systems | `BackgroundCheckCompleted`, `EquipmentReady` |
| **System Events** | Orchestration control signals | `NODE_COMPLETED`, `APPROVAL` |

---

## Event-Driven Architecture

### How Events Work

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           EVENT PROCESSING FLOW                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────┐                                                           │
│  │ External     │                                                           │
│  │ System       │──┐                                                        │
│  │ (HR, IT...)  │  │                                                        │
│  └──────────────┘  │                                                        │
│                    │  Event                                                  │
│  ┌──────────────┐  │  {type, payload}                                       │
│  │ MCP Tool     │──┼─────────────────┐                                      │
│  │ send_event   │  │                 │                                      │
│  └──────────────┘  │                 ▼                                      │
│                    │    ┌─────────────────────────┐                         │
│  ┌──────────────┐  │    │     ORCHESTRATOR        │                         │
│  │ REST API     │──┘    │                         │                         │
│  │ /send-event  │       │  1. Receive event       │                         │
│  └──────────────┘       │  2. Add to context      │                         │
│                         │  3. Evaluate edges      │                         │
│                         │  4. Check guards        │                         │
│                         │  5. Enable traversal    │                         │
│                         └───────────┬─────────────┘                         │
│                                     │                                        │
│                                     ▼                                        │
│                         ┌─────────────────────────┐                         │
│                         │    EDGE EVALUATION      │                         │
│                         │                         │                         │
│                         │  For each outbound edge:│                         │
│                         │  • Check activatingEvent│                         │
│                         │  • Evaluate guardConds  │                         │
│                         │  • If all pass → enable │                         │
│                         └───────────┬─────────────┘                         │
│                                     │                                        │
│                                     ▼                                        │
│                         ┌─────────────────────────┐                         │
│                         │   NODE NOW ELIGIBLE     │                         │
│                         │                         │                         │
│                         │  Call step_orchestration│                         │
│                         │  to execute the node    │                         │
│                         └─────────────────────────┘                         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Event Lifecycle

1. **Event Created** - External system or user sends event
2. **Event Received** - Orchestrator receives via REST API or MCP tool
3. **Context Updated** - Payload added to instance's event history
4. **Edges Evaluated** - All outbound edges from completed nodes checked
5. **Guards Checked** - FEEL expressions evaluated against new context
6. **Edge Enabled** - If event type matches and guards pass
7. **Node Eligible** - Target node becomes available for execution

---

## Domain Events

Domain events represent business process signals, typically from external systems.

### Employee Onboarding Events

#### OnboardingStarted

**Description:** Signals that the onboarding process has been initiated.

**Triggers Edge:** `offer-to-validate` (Offer Accepted → Validate Candidate)

**Payload:**
```json
{
  "timestamp": "2026-02-09T12:00:00Z",
  "source": "orchestrator"
}
```

**When Sent:** Automatically when orchestration starts, or manually to begin validation.

---

#### BackgroundCheckCompleted

**Description:** Signals that the background check has completed successfully.

**Triggers Edges:**
- `bgcheck-to-equipment` → Order Equipment (parallel)
- `bgcheck-to-accounts` → Create Accounts (parallel)
- `bgcheck-to-documents` → Collect Documents (parallel)
- `bgcheck-to-review` → Review Background Results (if `requiresReview=true`)

**Payload:**
```json
{
  "status": "COMPLETED",
  "passed": true,
  "requiresReview": false,
  "timestamp": "2026-02-09T12:15:00Z"
}
```

**Guard Conditions Using This Payload:**
- `backgroundCheck.status = "COMPLETED"` - Check completed
- `backgroundCheck.passed = true` - Check passed
- `backgroundCheck.requiresReview = true` - Needs HR review

**Routing Logic:**
- If `requiresReview = true` → Routes to HR Review node
- If `requiresReview = false` and `passed = true` → Routes to 3 parallel branches

---

#### BackgroundCheckFailed

**Description:** Signals that the background check has failed.

**Triggers Edge:** `bgcheck-to-cancelled` → Onboarding Cancelled

**Payload:**
```json
{
  "status": "FAILED",
  "passed": false,
  "reason": "Background check did not pass",
  "timestamp": "2026-02-09T12:15:00Z"
}
```

**Guard Conditions:**
- `backgroundCheck.status = "FAILED"` - Check failed
- Event condition: `BackgroundCheckFailed` must have occurred

**Effect:** Immediately cancels the onboarding process.

---

#### BackgroundReviewCompleted

**Description:** Signals that manual HR review of background check is complete.

**Triggers Edges:**
- If `decision = "APPROVED"`:
  - `review-to-equipment` → Order Equipment
  - `review-to-accounts` → Create Accounts
  - `review-to-documents` → Collect Documents
- If `decision = "REJECTED"`:
  - `review-to-cancelled` → Onboarding Cancelled

**Payload:**
```json
{
  "decision": "APPROVED",
  "reviewer": "hr-manager",
  "comments": "Review completed successfully",
  "timestamp": "2026-02-09T12:30:00Z"
}
```

**Guard Conditions:**
- `backgroundReview.decision = "APPROVED"` - Review approved
- `backgroundReview.decision = "REJECTED"` - Review rejected

---

#### EquipmentReady

**Description:** Signals that ordered equipment is ready for shipping.

**Triggers Edge:** `equipment-to-ship` → Ship Equipment

**Payload:**
```json
{
  "orderId": "EQ-1707486900000",
  "status": "READY",
  "items": ["laptop", "monitor", "keyboard"],
  "timestamp": "2026-02-09T13:00:00Z"
}
```

**Guard Conditions:**
- `equipmentOrder.status = "READY"` - Equipment ready to ship

---

#### EquipmentShipped

**Description:** Signals that equipment has been shipped to the employee.

**Triggers Edge:** `ship-to-orientation` → Schedule Orientation

**Payload:**
```json
{
  "trackingNumber": "TRK-1707486900000",
  "carrier": "FedEx",
  "estimatedDelivery": "2026-02-12",
  "timestamp": "2026-02-09T14:00:00Z"
}
```

**Guard Conditions:**
- `equipment.shipped = true` - Equipment shipped
- Also requires: `accounts.created = true`, `documents.collected = true`

---

#### DocumentsCollected

**Description:** Signals that required employment documents have been collected.

**Triggers Edge:** `documents-to-i9` → Verify I-9

**Payload:**
```json
{
  "i9Part1Completed": true,
  "w4Completed": true,
  "directDepositCompleted": true,
  "timestamp": "2026-02-09T13:30:00Z"
}
```

**Guard Conditions:**
- `documents.i9Part1Completed = true` - I-9 Section 1 done
- `starts with(employee.location, "US-")` - US employee (for I-9)

---

#### I9Verified

**Description:** Signals that I-9 employment verification is complete.

**Triggers Edge:** `i9-to-orientation` → Schedule Orientation

**Payload:**
```json
{
  "verified": true,
  "verificationDate": "2026-02-09",
  "documentType": "passport",
  "timestamp": "2026-02-09T15:00:00Z"
}
```

**Guard Conditions:**
- `i9.verified = true` - I-9 verified
- Also requires: `accounts.created = true`

---

#### OrientationScheduled

**Description:** Signals that new hire orientation has been scheduled.

**Triggers Edge:** `orientation-to-complete` → Onboarding Complete

**Payload:**
```json
{
  "scheduled": true,
  "date": "2026-02-22",
  "time": "09:00",
  "location": "Virtual",
  "timestamp": "2026-02-09T16:00:00Z"
}
```

**Guard Conditions:**
- `orientation.scheduled = true` - Orientation scheduled
- Also requires: `accounts.created`, `documents.collected`, `backgroundCheck.passed`

---

## System Events

System events are used for orchestration control and node lifecycle management.

### NODE_COMPLETED

**Description:** Signals that a node has finished executing.

**Usage:** Typically sent automatically by the orchestrator, but can be sent manually.

**Payload:**
```json
{
  "nodeId": "background-check-init",
  "result": {
    "status": "SUCCESS",
    "output": { ... }
  }
}
```

**Required Parameters:**
- `nodeId` - The ID of the completed node
- `payload` - Result data from the node

---

### NODE_FAILED

**Description:** Signals that a node execution has failed.

**Payload:**
```json
{
  "nodeId": "background-check-init",
  "errorType": "EXTERNAL_SERVICE_ERROR",
  "errorMessage": "Background check provider unavailable",
  "retryable": true
}
```

**Required Parameters:**
- `nodeId` - The ID of the failed node
- `errorType` - Category of error
- `errorMessage` - Human-readable error description
- `retryable` - Whether the operation can be retried

---

### APPROVAL

**Description:** Signals that a human approval has been received.

**Payload:**
```json
{
  "nodeId": "review-background-results",
  "approver": "hr-manager@company.com",
  "comments": "Approved after reviewing documentation"
}
```

**Required Parameters:**
- `nodeId` - The node awaiting approval
- `approver` - Who approved

---

### REJECTION

**Description:** Signals that a human rejection has been received.

**Payload:**
```json
{
  "nodeId": "review-background-results",
  "approver": "hr-manager@company.com",
  "reason": "Discrepancies found in employment history"
}
```

**Required Parameters:**
- `nodeId` - The node being rejected
- `approver` - Who rejected
- `reason` - Why rejected

---

### DATA_CHANGE

**Description:** Signals that external data has changed, triggering reevaluation.

**Payload:**
```json
{
  "entityType": "candidate",
  "entityId": "CAND-12345",
  "changedFields": ["address", "phone"],
  "data": {
    "address": "123 New Street",
    "phone": "555-1234"
  }
}
```

**Required Parameters:**
- `entityType` - Type of entity that changed
- `entityId` - ID of the changed entity
- `data` - New data values

---

## Event Flow Diagrams

### Complete Employee Onboarding Flow

```
                                    ┌─────────────────┐
                                    │   START         │
                                    └────────┬────────┘
                                             │
                                    OnboardingStarted
                                             │
                                             ▼
                                    ┌─────────────────┐
                                    │ Offer Accepted  │
                                    └────────┬────────┘
                                             │
                                             │ (auto - no event needed)
                                             ▼
                                    ┌─────────────────┐
                                    │ Validate        │
                                    │ Candidate       │
                                    └────────┬────────┘
                                             │
                          ┌──────────────────┼──────────────────┐
                          │                  │                  │
                   validation.status    validation.status       │
                      ="FAILED"           ="PASSED"             │
                          │                  │                  │
                          ▼                  ▼                  │
                   ┌───────────┐    ┌─────────────────┐        │
                   │ CANCELLED │    │ Run Background  │        │
                   └───────────┘    │ Check           │        │
                                    └────────┬────────┘        │
                                             │                  │
                 ┌───────────────────────────┼───────────────────────────┐
                 │                           │                           │
        BackgroundCheck              BackgroundCheck             BackgroundCheck
           Failed                    Completed                   Completed
             │                    (requiresReview=true)      (requiresReview=false)
             │                           │                           │
             ▼                           ▼                           │
      ┌───────────┐             ┌─────────────────┐                 │
      │ CANCELLED │             │ Review          │                 │
      └───────────┘             │ Background      │                 │
                                └────────┬────────┘                 │
                                         │                           │
                    ┌────────────────────┼────────────────────┐     │
                    │                    │                    │     │
           BackgroundReview      BackgroundReview             │     │
           Completed             Completed                    │     │
           (decision=REJECTED)   (decision=APPROVED)          │     │
                    │                    │                    │     │
                    ▼                    └────────────────────┼─────┘
             ┌───────────┐                                    │
             │ CANCELLED │                                    │
             └───────────┘                                    │
                                                              │
                         ┌────────────────────────────────────┼────────────────────────────────────┐
                         │                                    │                                    │
                         ▼                                    ▼                                    ▼
                ┌─────────────────┐                  ┌─────────────────┐                  ┌─────────────────┐
                │ Order           │                  │ Create          │                  │ Collect         │
                │ Equipment       │                  │ Accounts        │                  │ Documents       │
                └────────┬────────┘                  └────────┬────────┘                  └────────┬────────┘
                         │                                    │                                    │
                   EquipmentReady                             │                           DocumentsCollected
                         │                                    │                                    │
                         ▼                                    │                                    ▼
                ┌─────────────────┐                           │                           ┌─────────────────┐
                │ Ship            │                           │                           │ Verify I-9      │
                │ Equipment       │                           │                           │                 │
                └────────┬────────┘                           │                           └────────┬────────┘
                         │                                    │                                    │
                  EquipmentShipped                            │                              I9Verified
                         │                                    │                                    │
                         └────────────────────────────────────┼────────────────────────────────────┘
                                                              │
                                                              ▼
                                                     ┌─────────────────┐
                                                     │ Schedule        │
                                                     │ Orientation     │
                                                     └────────┬────────┘
                                                              │
                                                    OrientationScheduled
                                                              │
                                                              ▼
                                                     ┌─────────────────┐
                                                     │ Onboarding      │
                                                     │ Complete        │
                                                     └─────────────────┘
```

### Parallel Branch Synchronization

```
                    BackgroundCheckCompleted
                    (requiresReview=false, passed=true)
                              │
            ┌─────────────────┼─────────────────┐
            │                 │                 │
            ▼                 ▼                 ▼
    ┌───────────────┐ ┌───────────────┐ ┌───────────────┐
    │ Order         │ │ Create        │ │ Collect       │
    │ Equipment     │ │ Accounts      │ │ Documents     │
    └───────┬───────┘ └───────┬───────┘ └───────┬───────┘
            │                 │                 │
      EquipmentReady          │          DocumentsCollected
            │                 │                 │
            ▼                 │                 ▼
    ┌───────────────┐         │         ┌───────────────┐
    │ Ship          │         │         │ Verify I-9    │
    │ Equipment     │         │         │               │
    └───────┬───────┘         │         └───────┬───────┘
            │                 │                 │
     EquipmentShipped         │            I9Verified
            │                 │                 │
            └─────────────────┴─────────────────┘
                              │
                    ┌─────────▼─────────┐
                    │ All 3 branches    │
                    │ must complete     │
                    │ (JoinType.ALL)    │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │ Schedule        │
                    │ Orientation     │
                    └─────────────────┘
```

### Happy Path (Minimum Events)

For a successful onboarding with no review needed:

```
1. OnboardingStarted          → Starts validation
2. (auto)                     → Runs background check
3. BackgroundCheckCompleted   → Starts 3 parallel branches
4. EquipmentReady             → Enables shipping
5. EquipmentShipped           → Equipment branch complete
6. DocumentsCollected         → Enables I-9 verification
7. I9Verified                 → Documents branch complete
8. (accounts auto-complete)   → Accounts branch complete
9. OrientationScheduled       → Finalizes onboarding
```

**Total: 7 events for complete workflow**

---

## Payload Reference

### Quick Reference Table

| Event | Key Payload Fields | Used In Guard Conditions |
|-------|-------------------|-------------------------|
| `OnboardingStarted` | `timestamp`, `source` | — |
| `BackgroundCheckCompleted` | `status`, `passed`, `requiresReview` | `backgroundCheck.status`, `backgroundCheck.passed`, `backgroundCheck.requiresReview` |
| `BackgroundCheckFailed` | `status`, `passed`, `reason` | `backgroundCheck.status` |
| `BackgroundReviewCompleted` | `decision`, `reviewer`, `comments` | `backgroundReview.decision` |
| `EquipmentReady` | `orderId`, `status`, `items[]` | `equipmentOrder.status` |
| `EquipmentShipped` | `trackingNumber`, `carrier`, `estimatedDelivery` | `equipment.shipped` |
| `DocumentsCollected` | `i9Part1Completed`, `w4Completed`, `directDepositCompleted` | `documents.i9Part1Completed` |
| `I9Verified` | `verified`, `verificationDate`, `documentType` | `i9.verified` |
| `OrientationScheduled` | `scheduled`, `date`, `time`, `location` | `orientation.scheduled` |

### Payload to Context Mapping

When an event is received, its payload is added to the execution context. The mapping follows this pattern:

```
Event Type               →  Context Path
─────────────────────────────────────────
BackgroundCheckCompleted →  backgroundCheck.*
BackgroundCheckFailed    →  backgroundCheck.*
BackgroundReviewCompleted→  backgroundReview.*
EquipmentReady           →  equipmentOrder.*
EquipmentShipped         →  equipment.*
DocumentsCollected       →  documents.*
I9Verified               →  i9.*
OrientationScheduled     →  orientation.*
```

**Example:**

Event:
```json
{"type": "BackgroundCheckCompleted", "payload": {"passed": true}}
```

Context after event:
```json
{
  "backgroundCheck": {
    "passed": true,
    "status": "COMPLETED",
    ...
  }
}
```

Guard condition can now check:
```feel
backgroundCheck.passed = true
```

---

## Guard Conditions

### How Guard Conditions Use Events

Guard conditions are FEEL expressions that evaluate against the execution context. Events contribute to this context through their payloads.

#### Example: Background Check Routing

```java
// Edge: bgcheck-to-equipment (background check passed, no review needed)
GuardConditions.ofContext(List.of(
    FeelExpression.of("backgroundCheck.status = \"COMPLETED\""),
    FeelExpression.of("backgroundCheck.requiresReview = false or backgroundCheck.passed = true")
))

// Edge: bgcheck-to-review (requires HR review)
GuardConditions.ofContext(List.of(
    FeelExpression.of("backgroundCheck.status = \"COMPLETED\""),
    FeelExpression.of("backgroundCheck.requiresReview = true")
))

// Edge: bgcheck-to-cancelled (failed)
GuardConditions.ofContext(List.of(
    FeelExpression.of("backgroundCheck.status = \"FAILED\"")
))
```

#### Event Conditions

Some edges require specific events to have occurred:

```java
// Edge requires BackgroundCheckFailed event
new GuardConditions(
    List.of(FeelExpression.of("backgroundCheck.status = \"FAILED\"")),
    List.of(),  // ruleOutcomeConditions
    List.of(),  // policyOutcomeConditions
    List.of(EventCondition.occurred("BackgroundCheckFailed"))  // eventConditions
)
```

---

## Sending Events

### Via MCP Tools

#### send_event (Recommended)

Auto-populates payload based on event type:

```
send_event instanceId="abc-123" eventType="BackgroundCheckCompleted"
```

#### signal_event (Custom Payload)

For custom payloads:

```
signal_event instanceId="abc-123" eventType="BackgroundCheckCompleted" payload='{"passed": true, "requiresReview": true}'
```

### Via REST API

#### Send with Auto-Payload

```bash
curl -X POST http://localhost:8080/api/v1/orchestration/{instanceId}/send-event/BackgroundCheckCompleted
```

#### Signal with Custom Payload

```bash
curl -X POST http://localhost:8080/api/v1/orchestration/{instanceId}/signal \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "BackgroundCheckCompleted",
    "payload": {
      "status": "COMPLETED",
      "passed": true,
      "requiresReview": true
    }
  }'
```

### Via Skills

```
/send-events abc-123 BackgroundCheckCompleted EquipmentReady
```

---

## Related Documentation

- [Skills Reference](SKILLS_REFERENCE.md) - Using skills to send events
- [MCP Tools Guide](MCP_TOOLS_GUIDE.md) - Direct MCP tool usage
- [System Documentation](SYSTEM_DOCUMENTATION.md) - Full architecture reference

---

## License

Copyright 2026 ihelio. Licensed under the Apache License 2.0.
