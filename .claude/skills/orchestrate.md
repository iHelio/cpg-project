# /orchestrate - Interactive Workflow Runner

Run CPG workflow orchestration interactively through MCP tools with detailed insights into the process graph execution.

## Usage

```
/orchestrate [processGraphId] [--auto] [--verbose] [--context '{"key":"value"}']
```

## Arguments

- `processGraphId` (optional): The process graph to orchestrate. If omitted, lists available graphs.
- `--auto`: Auto-advance through the workflow without prompting for events.
- `--verbose`: Show extra details about graph structure and execution.
- `--context`: Initial domain context as a JSON object.

## Examples

```
/orchestrate
/orchestrate employee-onboarding
/orchestrate employee-onboarding --auto
/orchestrate employee-onboarding --verbose
/orchestrate employee-onboarding --context '{"candidateName": "John Smith", "offer": {"signed": true}}'
```

---

## Instructions

When the user invokes `/orchestrate`, follow this workflow:

### Step 1: Process Graph Selection

If no `processGraphId` is provided:

1. Call `list_process_graphs` MCP tool
2. Display the available process graphs in a formatted list:
   ```
   Available Process Graphs:
   ┌────┬─────────────────────┬────────────────────────────────┬───────┬───────┐
   │ #  │ ID                  │ Name                           │ Nodes │ Edges │
   ├────┼─────────────────────┼────────────────────────────────┼───────┼───────┤
   │ 1  │ employee-onboarding │ Employee Onboarding Process    │ 12    │ 18    │
   │ 2  │ purchase-approval   │ Purchase Approval Workflow     │ 8     │ 10    │
   └────┴─────────────────────┴────────────────────────────────┴───────┴───────┘
   ```
3. Ask the user which graph to orchestrate
4. Use their selection as the `processGraphId`

### Step 2: Load and Display Graph Structure

Before starting orchestration, help the user understand the process:

1. Call `get_process_graph` MCP tool with the `processGraphId`
2. Call `get_graph_edges` MCP tool with the `processGraphId`
3. Display the graph overview:

```
╭─ Process Graph: Employee Onboarding ───────────────────────────────────────╮
│                                                                             │
│ Description: Complete employee onboarding workflow from offer acceptance    │
│              through equipment provisioning and orientation scheduling.     │
│                                                                             │
│ Structure:                                                                  │
│   • 12 nodes (actions/decision points)                                      │
│   • 18 edges (transitions between nodes)                                    │
│   • Entry points: offer-accepted                                            │
│   • Terminal points: onboarding-complete, onboarding-cancelled              │
│                                                                             │
│ Key Events Required:                                                        │
│   • BackgroundCheckCompleted / BackgroundCheckFailed                        │
│   • EquipmentReady → EquipmentShipped                                       │
│   • DocumentsCollected → I9Verified                                         │
│   • OrientationScheduled                                                    │
│                                                                             │
╰─────────────────────────────────────────────────────────────────────────────╯
```

### Step 3: Start Orchestration

1. Parse the `--context` argument if provided, otherwise use empty context
2. Call `start_orchestration` MCP tool with:
   - `processGraphId`: The selected graph ID
   - `domainContext`: The parsed context JSON (or `{}`)
3. Store the returned `instanceId` for subsequent calls
4. Display the start confirmation with context details:

```
╭─ Orchestration Started ─────────────────────────────────────────────────────╮
│                                                                              │
│ Process:  Employee Onboarding Process                                        │
│ Instance: 87e5a6a7-ec80-418a-a16c-1f01b7203808                              │
│ Status:   RUNNING                                                            │
│                                                                              │
│ Initial Context:                                                             │
│   domainContext: {                                                           │
│     "candidateName": "John Smith",                                           │
│     "offer": {"signed": true}                                                │
│   }                                                                          │
│                                                                              │
╰──────────────────────────────────────────────────────────────────────────────╯
```

### Step 4: Orchestration Loop

Execute the following loop until the workflow completes or is cancelled:

#### 4a. Execute Step

1. Call `step_orchestration` MCP tool with the `instanceId`
2. Check the `orchestrationStatus` in the response:

**If EXECUTED:**

Display detailed information about the executed node:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ ✓ NODE EXECUTED: Initiate Background Check                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│ Node ID:     background-check-init                                          │
│ Action Type: SYSTEM_INVOCATION                                              │
│ Handler:     backgroundCheckAdapter                                         │
│                                                                             │
│ What happened:                                                              │
│   The system initiated a background check request with an external          │
│   provider. This is an asynchronous operation - the workflow will           │
│   wait for a BackgroundCheckCompleted or BackgroundCheckFailed event.       │
│                                                                             │
│ Outbound Edges (possible next steps):                                       │
│   ┌─────────────────────────┬─────────────────────┬────────────────────┐   │
│   │ Edge                    │ Target Node         │ Trigger Event      │   │
│   ├─────────────────────────┼─────────────────────┼────────────────────┤   │
│   │ background-passed       │ Equipment Procure.  │ BackgroundCheck-   │   │
│   │                         │                     │ Completed          │   │
│   ├─────────────────────────┼─────────────────────┼────────────────────┤   │
│   │ background-failed       │ Onboarding Cancel.  │ BackgroundCheck-   │   │
│   │                         │                     │ Failed             │   │
│   └─────────────────────────┴─────────────────────┴────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

To get this information:
- Call `get_node_details` with `graphId` and the executed `nodeId`
- Call `get_graph_edges` and filter for edges where `sourceNodeId` matches the executed node

Continue to the next iteration of the loop.

**If WAITING:**

Display waiting status and go to Step 4b (Event Handler)

**If COMPLETED:**

Display completion summary:
```
╭─ Orchestration Complete ────────────────────────────────────────────────────╮
│                                                                              │
│ Process:  Employee Onboarding Process                                        │
│ Instance: 87e5a6a7-ec80-418a-a16c-1f01b7203808                              │
│ Status:   COMPLETED                                                          │
│ Duration: 45.2 seconds                                                       │
│                                                                              │
│ Execution Path:                                                              │
│   ✓ Offer Accepted                                                           │
│   ✓ Validate Candidate Data                                                  │
│   ✓ Initiate Background Check                                                │
│       ↓ [BackgroundCheckCompleted event received]                            │
│   ✓ Equipment Procurement                                                    │
│       ↓ [EquipmentReady event received]                                      │
│   ✓ Ship Equipment                                                           │
│       ↓ [EquipmentShipped event received]                                    │
│   ✓ Document Collection                                                      │
│       ↓ [DocumentsCollected event received]                                  │
│   ✓ I-9 Verification                                                         │
│       ↓ [I9Verified event received]                                          │
│   ✓ Schedule Orientation                                                     │
│       ↓ [OrientationScheduled event received]                                │
│   ✓ Onboarding Complete                                                      │
│                                                                              │
╰──────────────────────────────────────────────────────────────────────────────╯
```

**If FAILED or BLOCKED:**

Display error information:
```
╭─ Orchestration Failed ──────────────────────────────────────────────────────╮
│                                                                              │
│ Status: FAILED                                                               │
│ Reason: [error message from response]                                        │
│                                                                              │
│ Last Successful Node: Initiate Background Check                              │
│ Failed At: Equipment Procurement                                             │
│                                                                              │
│ Error Details:                                                               │
│   [Include any error details from the response]                              │
│                                                                              │
╰──────────────────────────────────────────────────────────────────────────────╯
```

#### 4b. Event Handler (when WAITING)

1. Call `get_available_events` MCP tool with the `instanceId`
2. Check if there are available events

**If events are available:**

Display detailed event information with payloads:

```
╭─ Waiting for Event ─────────────────────────────────────────────────────────╮
│                                                                              │
│ Instance: 87e5a6a7-ec80-418a-a16c-1f01b7203808                              │
│                                                                              │
│ Current Progress:                                                            │
│   ✓ Offer Accepted                                                           │
│   ✓ Validate Candidate Data                                                  │
│   ✓ Initiate Background Check                                                │
│   ⏳ Waiting for external event...                                           │
│                                                                              │
│ The workflow is paused, waiting for one of these events:                     │
│                                                                              │
╰──────────────────────────────────────────────────────────────────────────────╯

┌─ Event Option 1 ────────────────────────────────────────────────────────────┐
│                                                                              │
│ Event Type: BackgroundCheckCompleted                                         │
│ Description: Signals that background check has completed successfully        │
│                                                                              │
│ This event will:                                                             │
│   • Enable edge: background-passed                                           │
│   • Allow execution of: Equipment Procurement                                │
│                                                                              │
│ Payload that will be sent:                                                   │
│   {                                                                          │
│     "status": "COMPLETED",                                                   │
│     "passed": true,                                                          │
│     "requiresReview": false,                                                 │
│     "timestamp": "2026-02-09T12:30:00Z"                                      │
│   }                                                                          │
│                                                                              │
│ What this means:                                                             │
│   The external background check provider has verified the candidate.         │
│   The check passed without issues requiring manual review.                   │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘

┌─ Event Option 2 ────────────────────────────────────────────────────────────┐
│                                                                              │
│ Event Type: BackgroundCheckFailed                                            │
│ Description: Signals that background check has failed                        │
│                                                                              │
│ This event will:                                                             │
│   • Enable edge: background-failed                                           │
│   • Allow execution of: Onboarding Cancelled                                 │
│                                                                              │
│ Payload that will be sent:                                                   │
│   {                                                                          │
│     "status": "FAILED",                                                      │
│     "passed": false,                                                         │
│     "reason": "Background check did not pass",                               │
│     "timestamp": "2026-02-09T12:30:00Z"                                      │
│   }                                                                          │
│                                                                              │
│ What this means:                                                             │
│   The background check found issues that prevent the candidate from          │
│   being hired. The onboarding process will be cancelled.                     │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘

Which event would you like to send? (1-2, or 'cancel' to stop):
```

**If `--auto` flag is set:**
- Automatically select the first available event (typically the "success" path)
- Display: `Auto-selecting event: BackgroundCheckCompleted`

**If `--auto` flag is NOT set:**
- Ask the user: "Which event would you like to send? (1-N, or 'cancel' to stop)"
- Wait for user selection

3. Call `send_event` MCP tool with:
   - `instanceId`: The current instance ID
   - `eventType`: The selected event type

4. Display detailed confirmation of the event sent:

```
┌─ Event Sent ────────────────────────────────────────────────────────────────┐
│                                                                              │
│ → Event Type: BackgroundCheckCompleted                                       │
│                                                                              │
│ Payload Delivered:                                                           │
│   {                                                                          │
│     "status": "COMPLETED",                                                   │
│     "passed": true,                                                          │
│     "requiresReview": false,                                                 │
│     "timestamp": "2026-02-09T12:30:45Z"                                      │
│   }                                                                          │
│                                                                              │
│ Effect:                                                                      │
│   • Edge 'background-passed' is now traversable                              │
│   • Node 'Equipment Procurement' is now eligible for execution               │
│                                                                              │
│ Context Updated:                                                             │
│   The event payload has been added to the instance's event history.          │
│   Guard conditions on edges can now reference this event data.               │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

5. Continue to the next iteration of the loop (Step 4a)

**If NO events are available:**

1. Call `get_required_events` MCP tool to analyze what's needed
2. Display diagnostic information:

```
╭─ Workflow Blocked ──────────────────────────────────────────────────────────╮
│                                                                              │
│ No events available to send automatically.                                   │
│                                                                              │
│ Analysis of Required Events:                                                 │
│                                                                              │
│ ┌─────────────────────┬────────────────────┬─────────────────────────────┐  │
│ │ Event Type          │ Source             │ Description                 │  │
│ ├─────────────────────┼────────────────────┼─────────────────────────────┤  │
│ │ ExternalApproval    │ NODE_PRECONDITION  │ Requires manager approval   │  │
│ │ ManualReview        │ EDGE_GUARD         │ HR must review documents    │  │
│ └─────────────────────┴────────────────────┴─────────────────────────────┘  │
│                                                                              │
│ These events typically come from:                                            │
│   • External systems (HR, IT, background check providers)                    │
│   • Human actions (approvals, reviews, manual tasks)                         │
│   • Scheduled jobs (timeouts, reminders)                                     │
│                                                                              │
│ Options:                                                                     │
│   1. signal - Use signal_event tool manually with custom payload             │
│   2. suspend - Suspend the orchestration                                     │
│   3. cancel - Cancel the orchestration                                       │
│   4. wait - Exit skill but keep orchestration running                        │
│                                                                              │
╰──────────────────────────────────────────────────────────────────────────────╯
```

3. Ask the user what they want to do and handle their selection

### Step 5: Final Summary

When the orchestration completes (either successfully or with failure), call `get_process_history` to show the full execution history with event details:

```
╭─ Complete Execution History ────────────────────────────────────────────────╮
│                                                                              │
│ Instance: 87e5a6a7-ec80-418a-a16c-1f01b7203808                              │
│ Process:  Employee Onboarding Process                                        │
│ Final Status: COMPLETED                                                      │
│                                                                              │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│ Step 1: Offer Accepted                                                       │
│   Time: 12:30:00 - 12:30:01 (1.0s)                                          │
│   Action: SYSTEM_INVOCATION → initializeOnboarding                          │
│   Result: Initialized onboarding record                                      │
│                                                                              │
│ Step 2: Validate Candidate Data                                              │
│   Time: 12:30:01 - 12:30:02 (0.8s)                                          │
│   Action: SYSTEM_INVOCATION → validateCandidateData                         │
│   Result: Validation passed                                                  │
│                                                                              │
│ Step 3: Initiate Background Check                                            │
│   Time: 12:30:02 - 12:30:03 (0.5s)                                          │
│   Action: SYSTEM_INVOCATION → backgroundCheckAdapter                        │
│   Result: Background check initiated with provider                           │
│                                                                              │
│   ⚡ EVENT: BackgroundCheckCompleted                                         │
│      Payload: {"status": "COMPLETED", "passed": true, ...}                   │
│      Edge Enabled: background-passed → Equipment Procurement                 │
│                                                                              │
│ Step 4: Equipment Procurement                                                │
│   Time: 12:30:15 - 12:30:16 (0.7s)                                          │
│   Action: SYSTEM_INVOCATION → equipmentProcurement                          │
│   Result: Equipment order placed                                             │
│                                                                              │
│   ⚡ EVENT: EquipmentReady                                                   │
│      Payload: {"orderId": "EQ-123", "items": ["laptop", ...]}               │
│      Edge Enabled: equipment-ready → Ship Equipment                          │
│                                                                              │
│ ... (additional steps) ...                                                   │
│                                                                              │
│ Step 10: Onboarding Complete                                                 │
│   Time: 12:31:45 - 12:31:46 (0.3s)                                          │
│   Action: SYSTEM_INVOCATION → finalizeOnboarding                            │
│   Result: Onboarding finalized, employee record created                      │
│                                                                              │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│ Summary:                                                                     │
│   Total Nodes Executed: 10                                                   │
│   Total Events Received: 6                                                   │
│   Total Duration: 1m 46s                                                     │
│   Path Taken: Happy path (all checks passed)                                 │
│                                                                              │
╰──────────────────────────────────────────────────────────────────────────────╯
```

---

## Understanding the Process Graph

### How Events Drive the Workflow

The orchestrator is **event-driven**. Here's how it works:

1. **Nodes** are actions or decision points (e.g., "Initiate Background Check")
2. **Edges** connect nodes and have **event triggers** (e.g., "BackgroundCheckCompleted")
3. When a node completes, the workflow **waits** for an event to enable the next edge
4. Once an event is received, the **guard conditions** on edges are evaluated
5. If conditions pass, the edge is **traversed** and the target node becomes eligible

### Event Payload Significance

Each event carries a **payload** that becomes part of the execution context:

```
Event: BackgroundCheckCompleted
Payload: {
  "status": "COMPLETED",    ← Can be used in guard conditions
  "passed": true,           ← Determines which edge to take
  "requiresReview": false,  ← May trigger additional workflow branches
  "timestamp": "..."        ← Audit trail
}
```

Guard conditions on edges can reference this data:
- `backgroundCheck.passed = true` → Take the success path
- `backgroundCheck.requiresReview = true` → Route to manual review

---

## Error Handling

- **Network errors:** Retry the failed tool call up to 3 times
- **Invalid graph ID:** Show error and list available graphs
- **Invalid event selection:** Re-prompt the user
- **Unexpected status:** Display raw response and ask user how to proceed

## Cancellation

If the user types "cancel" or "quit" at any prompt:
1. Ask for confirmation: "Cancel the orchestration? (y/n)"
2. If confirmed, call `cancel_orchestration` MCP tool
3. Display cancellation confirmation and exit
