# CPG Orchestration Skills Reference

This document describes the Claude Code skills available for automating CPG workflow orchestration.

## Overview

CPG provides three skills (slash commands) that simplify interaction with the orchestration engine:

| Skill | Description |
|-------|-------------|
| `/orchestrate` | Interactive workflow runner - start and step through workflows |
| `/workflow-status` | Check workflow status with actionable suggestions |
| `/send-events` | Send one or more events to progress a workflow |

These skills wrap the MCP tools to provide a higher-level, conversational interface for workflow automation.

---

## Quick Start

### Run a Complete Workflow

```
/orchestrate employee-onboarding
```

This starts the onboarding process and guides you through each step, prompting for events when needed.

### Check Workflow Status

```
/workflow-status 87e5a6a7-ec80-418a-a16c-1f01b7203808
```

Shows current progress, available events, and suggested next actions.

### Send Events to Progress

```
/send-events 87e5a6a7-ec80-418a-a16c-1f01b7203808 BackgroundCheckCompleted
```

Sends an event with auto-populated payload to enable the next workflow step.

---

## Skill Details

### /orchestrate

**Purpose:** Interactive workflow runner that starts and steps through a process graph with detailed insights into the orchestration process.

**Usage:**
```
/orchestrate [processGraphId] [--auto] [--verbose] [--context '{"key":"value"}']
```

**Arguments:**
- `processGraphId` (optional): Process graph to run. Lists available graphs if omitted.
- `--auto`: Automatically advance without prompting for event selection.
- `--verbose`: Show extra details about graph structure and execution.
- `--context`: Initial domain context as JSON.

**Workflow:**
1. Displays process graph overview (nodes, edges, key events)
2. Starts orchestration with context details
3. Loops through step → detailed node info → event handling with payloads
4. Shows comprehensive execution history on completion

**Key Features:**
- Shows graph structure before starting (entry points, terminals, required events)
- Displays detailed node execution info (action type, handler, outbound edges)
- Shows full event payloads and their effects on the workflow
- Explains what each event means in business terms
- Provides complete execution history with event timeline

**Example Session:**
```
> /orchestrate employee-onboarding

╭─ Process Graph: Employee Onboarding ────────────────────╮
│ Structure: 12 nodes, 18 edges                           │
│ Entry: offer-accepted                                   │
│ Key Events: BackgroundCheckCompleted, EquipmentReady... │
╰─────────────────────────────────────────────────────────╯

╭─ Orchestration Started ─────────────────────────────────╮
│ Instance: 87e5a6a7-ec80-418a-a16c-1f01b7203808         │
│ Status: RUNNING                                         │
╰─────────────────────────────────────────────────────────╯

┌─ NODE EXECUTED: Initiate Background Check ──────────────┐
│ Action Type: SYSTEM_INVOCATION                          │
│ Handler: backgroundCheckAdapter                         │
│                                                         │
│ Outbound Edges:                                         │
│   • background-passed → Equipment Procurement           │
│     Trigger: BackgroundCheckCompleted                   │
│   • background-failed → Onboarding Cancelled            │
│     Trigger: BackgroundCheckFailed                      │
└─────────────────────────────────────────────────────────┘

┌─ Event Option 1: BackgroundCheckCompleted ──────────────┐
│ Description: Background check has completed successfully│
│                                                         │
│ Payload:                                                │
│   {                                                     │
│     "status": "COMPLETED",                              │
│     "passed": true,                                     │
│     "requiresReview": false,                            │
│     "timestamp": "2026-02-09T12:30:00Z"                 │
│   }                                                     │
│                                                         │
│ Effect: Enables edge to Equipment Procurement           │
└─────────────────────────────────────────────────────────┘

Which event would you like to send? (1-2): 1

┌─ Event Sent ────────────────────────────────────────────┐
│ → BackgroundCheckCompleted                              │
│ Payload: {"status": "COMPLETED", "passed": true, ...}   │
│ Edge 'background-passed' now traversable                │
└─────────────────────────────────────────────────────────┘
```

---

### /workflow-status

**Purpose:** Comprehensive status check with actionable suggestions.

**Usage:**
```
/workflow-status [instanceId]
```

**Arguments:**
- `instanceId` (optional): Instance to check. Lists active instances if omitted.

**Information Displayed:**
- Instance ID, process graph, and status
- Execution progress with completed nodes
- Current state (active nodes, waiting status)
- Available events that can be sent
- Suggested next actions

**Example Output:**
```
╭─ Workflow Status ──────────────────────────────────────────╮
│ Instance: 87e5a6a7-ec80-418a-a16c-1f01b7203808            │
│ Process:  Employee Onboarding Process                      │
│ Status:   RUNNING                                          │
├────────────────────────────────────────────────────────────┤
│ EXECUTION PROGRESS                                         │
│ ✓ Offer Accepted                                           │
│ ✓ Validate Candidate Data                                  │
│ ✓ Initiate Background Check                                │
│ ⏳ (waiting for event)                                     │
├────────────────────────────────────────────────────────────┤
│ AVAILABLE ACTIONS                                          │
│ Events: BackgroundCheckCompleted, BackgroundCheckFailed    │
╰────────────────────────────────────────────────────────────╯

💡 Suggestions:
   1. Send an event: /send-events 87e5a6a7... BackgroundCheckCompleted
   2. Continue orchestration: /orchestrate employee-onboarding
```

---

### /send-events

**Purpose:** Send one or more events to progress a workflow.

**Usage:**
```
/send-events <instanceId> <eventType1> [eventType2] ...
```

**Arguments:**
- `instanceId` (required): The process instance ID (full or partial)
- `eventType1, eventType2, ...` (required): Events to send

**Features:**
- Accepts partial instance IDs (first 8 characters)
- Validates events against available events
- Sends events sequentially with progress display
- Shows post-send status and next steps

**Example:**
```
> /send-events 87e5a6a7 BackgroundCheckCompleted EquipmentReady

╭─ Event Validation ─────────────────────────────────────────╮
│ Events to send:                                            │
│   ✓ BackgroundCheckCompleted (available)                   │
│   ✓ EquipmentReady (available)                             │
╰────────────────────────────────────────────────────────────╯

Sending events...
  [1/2] BackgroundCheckCompleted ✓
  [2/2] EquipmentReady ✓

╭─ Events Sent Successfully ─────────────────────────────────╮
│ Sent: 2 events                                             │
│ Status: RUNNING                                            │
│ Next: Call step_orchestration or /orchestrate to continue  │
╰────────────────────────────────────────────────────────────╯
```

---

## Common Workflows

### 1. First-Time Orchestration

```bash
# List available process graphs
/orchestrate

# Start a specific process
/orchestrate employee-onboarding --context '{"candidateName": "John Smith"}'
```

### 2. Resuming a Workflow

```bash
# Check status of running workflows
/workflow-status

# Select instance from list and view status
/workflow-status 87e5a6a7-ec80-418a-a16c-1f01b7203808

# Send required events
/send-events 87e5a6a7 BackgroundCheckCompleted

# Continue interactive orchestration
/orchestrate employee-onboarding
```

### 3. Automated Progression

```bash
# Run through workflow automatically (no prompts)
/orchestrate employee-onboarding --auto
```

### 4. Batch Event Sending

```bash
# Send multiple events at once
/send-events 87e5a6a7 DocumentsCollected I9Verified OrientationScheduled
```

---

## Event Reference

### Employee Onboarding Events

| Event | Description | Typical Payload |
|-------|-------------|-----------------|
| `OnboardingStarted` | Process initiated | timestamp, source |
| `BackgroundCheckCompleted` | Check passed | status, passed, requiresReview |
| `BackgroundCheckFailed` | Check failed | status, passed, reason |
| `BackgroundReviewCompleted` | Manual review done | decision, reviewer, comments |
| `EquipmentReady` | Equipment ready | orderId, status, items |
| `EquipmentShipped` | Equipment shipped | trackingNumber, carrier |
| `DocumentsCollected` | Documents received | i9Part1Completed, w4Completed |
| `I9Verified` | I-9 verified | verified, verificationDate |
| `OrientationScheduled` | Orientation set | scheduled, date, time, location |

All events include auto-populated payloads with realistic data when sent via `/send-events` or the `send_event` MCP tool.

---

## Troubleshooting

### Workflow is Stuck

1. Check status: `/workflow-status <instanceId>`
2. Look at "Available Events" section
3. If no events available, the workflow may need external input
4. Use `get_required_events` MCP tool for detailed analysis

### Event Not Recognized

1. Check spelling of event type (case-sensitive)
2. Use `/workflow-status` to see available events
3. Custom events can still be sent - they'll be processed as domain events

### Cannot Find Instance

1. Use `/workflow-status` without arguments to list all instances
2. Instance IDs can be partial (first 8 characters)
3. Check if instance completed or was cancelled

---

## Related Documentation

- [MCP Tools Guide](MCP_TOOLS_GUIDE.md) - Direct MCP tool usage
- [System Documentation](SYSTEM_DOCUMENTATION.md) - Full system reference
- [README](../README.md) - Project overview

---

## License

Copyright 2026 ihelio. Licensed under the Apache License 2.0.
