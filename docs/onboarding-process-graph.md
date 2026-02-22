# Employee Onboarding Process Graph

This document describes the complete employee onboarding workflow as defined in the CPG process graph. It covers all 13 nodes, their actions, entry/exit events, and transition paths.

## Process Overview

- **Graph ID:** `employee-onboarding`
- **Entry Node:** Offer Accepted
- **Terminal Nodes:** Onboarding Complete, Onboarding Cancelled
- **Execution Model:** Event-driven, single-step orchestration (no auto-advance)

---

## Nodes

### 1. Offer Accepted (`offer-accepted`)

| | |
|---|---|
| **Action** | SYSTEM_INVOCATION &mdash; `initializeOnboarding` |
| **Task** | Initialize the onboarding process context |
| **Entry Trigger** | Subscribes to `OfferAccepted` event |
| **Exit Event** | `OnboardingStarted` (on complete) |
| **Transitions Out** | &rarr; Validate Candidate |

### 2. Validate Candidate (`validate-candidate`)

| | |
|---|---|
| **Action** | SYSTEM_INVOCATION &mdash; `validateCandidateData` |
| **Task** | Verify candidate information is complete and valid. Derives start date via `onboarding-policies` DMN. |
| **Entry Trigger** | Edge from Offer Accepted, activated by `OnboardingStarted` |
| **Exit Event** | _(none)_ |
| **Transitions Out** | &rarr; Run Background Check (guard: `validation.status = "PASSED"`) |
| | &rarr; Onboarding Cancelled (guard: `validation.status = "FAILED"`) |

### 3. Run Background Check (`run-background-check`)

| | |
|---|---|
| **Action** | SYSTEM_INVOCATION (async) &mdash; `backgroundCheckAdapter` |
| **Task** | Submit background check to external provider. DMN determines check package. Policy gates enforce FCRA and Ban-the-Box compliance. |
| **Entry Trigger** | Edge from Validate Candidate |
| **Exit Events** | `BackgroundCheckInitiated` (on start), `BackgroundCheckCompleted` (on complete) |
| **Transitions Out** | &rarr; AI Analyze Background Check (activated by `BackgroundCheckCompleted`) |
| | &rarr; Onboarding Cancelled (activated by `BackgroundCheckFailed`) |

### 4. AI Analyze Background Check (`ai-analyze-background-check`)

| | |
|---|---|
| **Action** | AGENT_ASSISTED &mdash; `aiBackgroundAnalyst` (timeout: 120s, max 2 retries) |
| **Task** | AI evaluates background check findings and produces a risk assessment with recommendation |
| **Entry Trigger** | Subscribes to `BackgroundCheckCompleted` |
| **Exit Events** | `AiAnalysisStarted` (on start), `AiAnalysisCompleted` (on complete) |
| **Transitions Out** | &rarr; Review Background Results (guard: `aiAnalysis.requiresReview = true`, high priority) |
| | &rarr; Order Equipment, Create Accounts, Collect Documents **in parallel** (guard: `aiAnalysis.passed = true`) |

### 5. Review Background Results (`review-background-results`)

| | |
|---|---|
| **Action** | HUMAN_TASK &mdash; `hrReviewTask` (form: `background-review-form`, SLA: 24h) |
| **Task** | HR manager reviews adverse background check findings. DMN determines reviewer and review SLA. |
| **Entry Trigger** | Subscribes to `BackgroundCheckCompleted` (filtered: `event.requiresReview = true`) |
| **Exit Event** | `BackgroundReviewCompleted` (on complete) |
| **Transitions Out** | If **approved**: &rarr; Order Equipment, Create Accounts, Collect Documents **in parallel** |
| | If **rejected**: &rarr; Onboarding Cancelled |

### 6. Order Equipment (`order-equipment`)

| | |
|---|---|
| **Action** | SYSTEM_INVOCATION (async) &mdash; `equipmentProcurement` |
| **Task** | Submit equipment order to procurement. DMN determines equipment package based on role, location, and remote status. |
| **Entry Trigger** | Edge from AI Analysis or Review (after approval) |
| **Exit Event** | `EquipmentOrdered` (on complete) |
| **Transitions Out** | &rarr; Ship Equipment (activated by `EquipmentReady`) |

### 7. Ship Equipment (`ship-equipment`)

| | |
|---|---|
| **Action** | SYSTEM_INVOCATION (async) &mdash; `shippingService` |
| **Task** | Create shipping label and schedule pickup. DMN determines shipping priority. |
| **Entry Trigger** | Subscribes to `EquipmentReady` |
| **Exit Event** | `EquipmentShipped` (on complete) |
| **Transitions Out** | &rarr; Schedule Orientation (guards: `equipment.shipped`, `accounts.created`, `documents.collected`) |

### 8. Create Accounts (`create-accounts`)

| | |
|---|---|
| **Action** | SYSTEM_INVOCATION &mdash; `identityProvisioningService` |
| **Task** | Provision email, SSO, and role-based access. DMN determines account entitlements. Policy gate enforces access policy. |
| **Entry Trigger** | Edge from AI Analysis or Review (after approval) |
| **Exit Event** | `AccountsCreated` (on complete) |
| **Transitions Out** | &rarr; Schedule Orientation (guards: `accounts.created`, `backgroundCheck.passed`, event: `DocumentsCollected` occurred) |

### 9. Collect Documents (`collect-documents`)

| | |
|---|---|
| **Action** | HUMAN_TASK &mdash; `documentCollectionTask` (form: `document-upload-form`, SLA: 7 days) |
| **Task** | Employee submits required HR and tax documents. DMN determines required documents. Policy gate enforces document compliance. |
| **Entry Trigger** | Edge from AI Analysis or Review (after approval) |
| **Exit Event** | `DocumentsCollected` (on complete) |
| **Transitions Out** | &rarr; Verify I-9 (guards: `documents.i9Part1Completed = true`, US location only) |

### 10. Verify I-9 (`verify-i9`)

| | |
|---|---|
| **Action** | HUMAN_TASK &mdash; `i9VerificationTask` (form: `i9-verification-form`, SLA: 3 days) |
| **Task** | HR verifies I-9 employment eligibility documents in person or via authorized representative. US employees only. DMN determines I-9 deadline. Policy gate enforces I-9 statutory compliance. |
| **Entry Trigger** | Subscribes to `EmployeeStarted`; reached via edge activated by `DocumentsCollected` |
| **Exit Event** | `I9Verified` (on complete) |
| **Transitions Out** | &rarr; Schedule Orientation (guards: `i9.verified = true`, `accounts.created = true`) |

### 11. Schedule Orientation (`schedule-orientation`)

| | |
|---|---|
| **Action** | SYSTEM_INVOCATION &mdash; `calendarService` |
| **Task** | Schedule new employee orientation based on start date and location. DMN determines orientation type. |
| **Entry Trigger** | Converges from Ship Equipment, Create Accounts, and Verify I-9 (first edge whose guards are satisfied) |
| **Exit Event** | `OrientationScheduled` (on complete) |
| **Transitions Out** | &rarr; Onboarding Complete (guards: `orientation.scheduled`, `accounts.created`, `documents.collected`, `backgroundCheck.passed`) |

### 12. Onboarding Complete (`onboarding-complete`)

| | |
|---|---|
| **Action** | SYSTEM_INVOCATION &mdash; `finalizeOnboarding` |
| **Task** | Mark onboarding as complete and notify all stakeholders |
| **Entry Trigger** | Edge from Schedule Orientation, activated by `OrientationScheduled` |
| **Exit Event** | `OnboardingCompleted` (on complete) |
| **Transitions Out** | _(terminal node)_ |

### 13. Onboarding Cancelled (`onboarding-cancelled`)

| | |
|---|---|
| **Action** | SYSTEM_INVOCATION &mdash; `cancelOnboarding` |
| **Task** | Clean up resources and notify stakeholders of cancellation |
| **Entry Trigger** | Subscribes to `CandidateWithdrew`, `OfferRescinded`, `BackgroundCheckFailed`; also reached via edges from Validate Candidate, Run Background Check, or Review Background Results |
| **Exit Event** | `OnboardingCancelled` (on complete) |
| **Transitions Out** | _(terminal node)_ |

---

## Transition Summary

### Happy Path

```
Offer Accepted
  │  OnboardingStarted
  ▼
Validate Candidate
  │  validation.status = "PASSED"
  ▼
Run Background Check
  │  BackgroundCheckCompleted
  ▼
AI Analyze Background Check
  │  AiAnalysisCompleted, aiAnalysis.passed = true
  ├──────────────────────┬──────────────────────┐
  ▼                      ▼                      ▼
Order Equipment    Create Accounts    Collect Documents
  │                      │                      │
  ▼                      │                      ▼
Ship Equipment           │              Verify I-9 (US only)
  │                      │                      │
  └──────────────────────┴──────────────────────┘
                         │
                         ▼
              Schedule Orientation
                         │  OrientationScheduled
                         ▼
              Onboarding Complete
```

### AI Review Path (Adverse Findings)

```
AI Analyze Background Check
  │  aiAnalysis.requiresReview = true (high priority)
  ▼
Review Background Results (HR Manager)
  ├─ APPROVED → parallel fork: Order Equipment, Create Accounts, Collect Documents
  └─ REJECTED → Onboarding Cancelled
```

### Cancellation Paths

| Source Node | Trigger | Guard Condition |
|---|---|---|
| Validate Candidate | _(edge guard)_ | `validation.status = "FAILED"` |
| Run Background Check | `BackgroundCheckFailed` | `backgroundCheck.status = "FAILED"` |
| Review Background Results | `BackgroundReviewCompleted` | `backgroundReview.decision = "REJECTED"` |
| _(any point)_ | `CandidateWithdrew` / `OfferRescinded` | _(event subscription on node)_ |

---

## Events Reference

| Event | Emitted By | Consumed By |
|---|---|---|
| `OfferAccepted` | _(external)_ | Offer Accepted (subscription) |
| `OnboardingStarted` | Offer Accepted | Edge: offer-to-validate |
| `BackgroundCheckInitiated` | Run Background Check | _(informational)_ |
| `BackgroundCheckCompleted` | Run Background Check | AI Analyze Background Check; Edge: bgcheck-to-ai-analysis |
| `BackgroundCheckFailed` | _(external)_ | Onboarding Cancelled; Edge: bgcheck-to-cancelled |
| `AiAnalysisStarted` | AI Analyze Background Check | _(informational)_ |
| `AiAnalysisCompleted` | AI Analyze Background Check | Edges to Review, Equipment, Accounts, Documents |
| `BackgroundReviewCompleted` | Review Background Results | Edges to Equipment, Accounts, Documents, Cancelled |
| `EquipmentOrdered` | Order Equipment | _(informational)_ |
| `EquipmentReady` | _(external)_ | Ship Equipment; Edge: equipment-to-ship |
| `EquipmentShipped` | Ship Equipment | Edge: ship-to-orientation |
| `AccountsCreated` | Create Accounts | _(informational)_ |
| `DocumentsCollected` | Collect Documents | Edge: documents-to-i9; Event condition: accounts-to-orientation |
| `EmployeeStarted` | _(external)_ | Verify I-9 (subscription) |
| `I9Verified` | Verify I-9 | Edge: i9-to-orientation |
| `OrientationScheduled` | Schedule Orientation | Edge: orientation-to-complete |
| `OnboardingCompleted` | Onboarding Complete | _(informational)_ |
| `OnboardingCancelled` | Onboarding Cancelled | _(informational)_ |
| `CandidateWithdrew` | _(external)_ | Onboarding Cancelled (subscription) |
| `OfferRescinded` | _(external)_ | Onboarding Cancelled (subscription) |
