# /workflow-status - Workflow Status Check

Check the status of a running CPG workflow with actionable suggestions.

## Usage

```
/workflow-status [instanceId]
```

## Arguments

- `instanceId` (optional): The process instance ID to check. If omitted, lists recent instances.

## Examples

```
/workflow-status
/workflow-status 87e5a6a7-ec80-418a-a16c-1f01b7203808
```

---

## Instructions

When the user invokes `/workflow-status`, follow this workflow:

### Step 1: Instance Selection

**If `instanceId` is provided:**
- Use the provided instance ID directly
- Proceed to Step 2

**If `instanceId` is NOT provided:**
1. Call `list_process_instances` MCP tool (no filters)
2. Display available instances:
   ```
   ╭─ Active Process Instances ──────────────────────────────╮
   │ #  │ Instance ID                          │ Graph      │ Status    │
   ├────┼──────────────────────────────────────┼────────────┼───────────┤
   │ 1  │ 87e5a6a7-ec80-418a-a16c-1f01b7203808 │ employee-  │ RUNNING   │
   │    │                                      │ onboarding │           │
   │ 2  │ f4c8d2b1-3e9a-4f5c-8b7d-2a1e3c4f5d6e │ purchase-  │ SUSPENDED │
   │    │                                      │ approval   │           │
   │ 3  │ a1b2c3d4-5e6f-7a8b-9c0d-1e2f3a4b5c6d │ employee-  │ COMPLETED │
   │    │                                      │ onboarding │           │
   ╰─────────────────────────────────────────────────────────────────────╯
   ```
3. Ask the user: "Which instance would you like to check? (1-N)"
4. Use their selection

### Step 2: Gather Status Information

Call the following MCP tools in parallel to gather comprehensive status:

1. `get_orchestration_status` with the `instanceId`
2. `get_process_history` with the `instanceId`
3. `get_active_nodes` with the `instanceId`
4. `get_available_events` with the `instanceId`

### Step 3: Display Comprehensive Status

Combine the gathered information into a comprehensive status display:

```
╭─ Workflow Status ──────────────────────────────────────────╮
│ Instance: 87e5a6a7-ec80-418a-a16c-1f01b7203808            │
│ Process:  Employee Onboarding Process                      │
│ Status:   RUNNING                                          │
│ Started:  2026-02-08 12:00:00                              │
├────────────────────────────────────────────────────────────┤
│ EXECUTION PROGRESS                                         │
│ ════════════════════════════════════════════════════════   │
│                                                            │
│ ✓ Initialize Onboarding              [12:00:01 - 12:00:02] │
│ ✓ Validate Candidate Data            [12:00:02 - 12:00:03] │
│ ✓ Initiate Background Check          [12:00:03 - 12:00:04] │
│ ⏳ (waiting for event)                                     │
│                                                            │
├────────────────────────────────────────────────────────────┤
│ CURRENT STATE                                              │
│ ════════════════════════════════════════════════════════   │
│                                                            │
│ Active Nodes: (none - waiting for event)                   │
│ Completed: 3 of ~12 nodes                                  │
│                                                            │
├────────────────────────────────────────────────────────────┤
│ AVAILABLE ACTIONS                                          │
│ ════════════════════════════════════════════════════════   │
│                                                            │
│ Events that can be sent:                                   │
│   • BackgroundCheckCompleted - Background check passed     │
│   • BackgroundCheckFailed - Background check failed        │
│                                                            │
│ Commands:                                                  │
│   • /orchestrate --resume 87e5a6a7...  Resume stepping     │
│   • /send-events 87e5a6a7... BackgroundCheckCompleted      │
│                                                            │
╰────────────────────────────────────────────────────────────╯
```

### Step 4: Status-Specific Suggestions

Based on the workflow status, provide actionable suggestions:

**If RUNNING and waiting for events:**
```
💡 Suggestions:
   1. Send an event: /send-events {instanceId} BackgroundCheckCompleted
   2. Continue orchestration: /orchestrate employee-onboarding --resume {instanceId}
   3. View required events: Use get_required_events tool for detailed analysis
```

**If RUNNING with active nodes:**
```
💡 Suggestions:
   1. Wait for active nodes to complete
   2. Step forward: Use step_orchestration tool
```

**If SUSPENDED:**
```
💡 Suggestions:
   1. Resume orchestration: Use resume_orchestration tool
   2. Cancel if no longer needed: Use cancel_orchestration tool
```

**If COMPLETED:**
```
✅ This workflow has completed successfully.
   View full history with get_process_history tool.
```

**If FAILED:**
```
❌ This workflow has failed.

   Error Details:
   [Include error information from the status]

💡 Suggestions:
   1. Review the execution history for failure point
   2. Start a new orchestration with corrected context
```

### Step 5: Offer Follow-up Actions

Ask the user if they want to take any action:

```
What would you like to do?
  1. Send an event
  2. Step the orchestration forward
  3. Suspend/Resume/Cancel
  4. View detailed history
  5. Exit

Enter choice (1-5):
```

Handle the user's selection:

1. **Send an event:** Call `send_event` with user-selected event type
2. **Step forward:** Call `step_orchestration` and display result
3. **Suspend/Resume/Cancel:** Call appropriate tool based on current status
4. **View detailed history:** Display full `get_process_history` output
5. **Exit:** End the skill

---

## Quick Status Format

For users who just want a quick check, also support a compact output when the instance is found:

```
[RUNNING] employee-onboarding | 3/12 nodes | Waiting: BackgroundCheckCompleted
```

This one-liner gives instant status at a glance.

---

## Error Handling

- **Instance not found:** Display error and list available instances
- **Tool call failures:** Retry up to 3 times, then show partial information
- **No instances:** Display "No active process instances found"
