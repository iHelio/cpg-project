# /orchestrate - Interactive Workflow Runner

Run CPG workflow orchestration interactively through MCP tools.

## Usage

```
/orchestrate [processGraphId] [--auto] [--context '{"key":"value"}']
```

## Arguments

- `processGraphId` (optional): The process graph to orchestrate. If omitted, lists available graphs.
- `--auto`: Auto-advance through the workflow without prompting for events.
- `--context`: Initial domain context as a JSON object.

## Examples

```
/orchestrate
/orchestrate employee-onboarding
/orchestrate employee-onboarding --auto
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
   1. employee-onboarding - Employee Onboarding Process (12 nodes, 18 edges)
   2. purchase-approval - Purchase Approval Workflow (8 nodes, 10 edges)
   ```
3. Ask the user which graph to orchestrate
4. Use their selection as the `processGraphId`

### Step 2: Start Orchestration

1. Parse the `--context` argument if provided, otherwise use empty context
2. Call `start_orchestration` MCP tool with:
   - `processGraphId`: The selected graph ID
   - `domainContext`: The parsed context JSON (or `{}`)
3. Store the returned `instanceId` for subsequent calls
4. Display the start confirmation:
   ```
   ╭─ Orchestration Started ─────────────────────────────────╮
   │ Process: Employee Onboarding Process                    │
   │ Instance: 87e5a6a7-ec80-418a-a16c-1f01b7203808         │
   │ Status: RUNNING                                         │
   ╰─────────────────────────────────────────────────────────╯
   ```

### Step 3: Orchestration Loop

Execute the following loop until the workflow completes or is cancelled:

#### 3a. Execute Step

1. Call `step_orchestration` MCP tool with the `instanceId`
2. Check the `orchestrationStatus` in the response:

**If EXECUTED:**
- Display the executed node:
  ```
  ✓ Executed: Offer Accepted
  ```
- Continue to the next iteration of the loop

**If WAITING:**
- Display waiting status and go to Step 3b (Event Handler)

**If COMPLETED:**
- Display completion summary and exit:
  ```
  ╭─ Orchestration Complete ─────────────────────────────────╮
  │ Process: Employee Onboarding Process                     │
  │ Instance: 87e5a6a7-ec80-418a-a16c-1f01b7203808          │
  │ Status: COMPLETED                                        │
  │                                                          │
  │ Executed Nodes:                                          │
  │   ✓ Offer Accepted                                       │
  │   ✓ Validate Candidate Data                              │
  │   ✓ Initiate Background Check                            │
  │   ✓ Equipment Procurement                                │
  │   ✓ Onboarding Complete                                  │
  ╰──────────────────────────────────────────────────────────╯
  ```

**If FAILED or BLOCKED:**
- Display error information and exit:
  ```
  ╭─ Orchestration Failed ───────────────────────────────────╮
  │ Status: FAILED                                           │
  │ Reason: [error message from response]                    │
  ╰──────────────────────────────────────────────────────────╯
  ```

#### 3b. Event Handler (when WAITING)

1. Call `get_available_events` MCP tool with the `instanceId`
2. Check if there are available events

**If events are available:**

Display the waiting status with available events:
```
╭─ Waiting for Event ────────────────────────────────────────╮
│ Instance: 87e5a6a7-ec80-418a-a16c-1f01b7203808            │
│                                                            │
│ Progress:                                                  │
│   ✓ Offer Accepted                                         │
│   ✓ Validate Candidate Data                                │
│   ✓ Initiate Background Check                              │
│   ⏳ Waiting...                                            │
│                                                            │
│ Available Events:                                          │
│   1. BackgroundCheckCompleted - Background check passed    │
│   2. BackgroundCheckFailed - Background check failed       │
╰────────────────────────────────────────────────────────────╯
```

**If `--auto` flag is set:**
- Automatically select the first available event
- Display: `Auto-selecting event: BackgroundCheckCompleted`

**If `--auto` flag is NOT set:**
- Ask the user: "Which event would you like to send? (1-N, or 'cancel' to stop)"
- Wait for user selection

3. Call `send_event` MCP tool with:
   - `instanceId`: The current instance ID
   - `eventType`: The selected event type
4. Display confirmation:
   ```
   → Sent event: BackgroundCheckCompleted
   ```
5. Continue to the next iteration of the loop (Step 3a)

**If NO events are available:**

1. Call `get_required_events` MCP tool to analyze what's needed
2. Display diagnostic information:
   ```
   ╭─ Workflow Blocked ──────────────────────────────────────╮
   │ No events available to send automatically.              │
   │                                                         │
   │ Required Events (from analysis):                        │
   │   - ExternalApproval (NODE_PRECONDITION)               │
   │   - ManualReview (EDGE_GUARD)                          │
   │                                                         │
   │ These events may require external system integration    │
   │ or manual intervention.                                 │
   │                                                         │
   │ Options:                                                │
   │   1. Use signal_event tool manually with custom payload │
   │   2. Suspend orchestration with /workflow-status        │
   │   3. Cancel orchestration                               │
   ╰─────────────────────────────────────────────────────────╯
   ```
3. Ask the user what they want to do:
   - "signal" - Use signal_event with custom payload
   - "suspend" - Suspend the orchestration
   - "cancel" - Cancel the orchestration
   - "wait" - Exit skill but keep orchestration running

### Step 4: Final Summary

When the orchestration completes (either successfully or with failure), call `get_process_history` to show the full execution history:

```
╭─ Execution History ────────────────────────────────────────╮
│ Node                    │ Status    │ Duration            │
├─────────────────────────┼───────────┼─────────────────────┤
│ Offer Accepted          │ COMPLETED │ 0.12s               │
│ Validate Candidate Data │ COMPLETED │ 0.08s               │
│ Initiate Background... │ COMPLETED │ 0.15s               │
│ Equipment Procurement   │ COMPLETED │ 0.11s               │
│ Onboarding Complete     │ COMPLETED │ 0.09s               │
╰─────────────────────────────────────────────────────────────╯
```

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
