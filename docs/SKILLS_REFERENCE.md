# CPG Orchestration Skills Reference

This document describes the Claude Code skills available for automating CPG workflow orchestration.

## Table of Contents

1. [Overview](#overview)
2. [Event-Driven Orchestration Model](#event-driven-orchestration-model)
3. [Quick Start](#quick-start)
4. [Skill Details](#skill-details)
5. [Common Workflows](#common-workflows)
6. [Event Reference](#event-reference)
7. [Understanding Payloads](#understanding-payloads)
8. [Troubleshooting](#troubleshooting)

---

## Overview

CPG provides three skills (slash commands) that simplify interaction with the orchestration engine:

| Skill | Description |
|-------|-------------|
| `/orchestrate` | Interactive workflow runner - start and step through workflows with detailed insights |
| `/workflow-status` | Check workflow status with actionable suggestions |
| `/send-events` | Send one or more events to progress a workflow |

These skills wrap the 21 MCP tools to provide a higher-level, conversational interface for workflow automation.

---

## Event-Driven Orchestration Model

The CPG orchestrator is **completely event-driven**. Understanding this model is key to using the skills effectively.

### How It Works

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         EVENT-DRIVEN WORKFLOW                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   PROCESS GRAPH (Template)           PROCESS INSTANCE (Running)              │
│   ┌─────────────────────┐            ┌─────────────────────┐                │
│   │ Nodes = Actions     │            │ Execution State     │                │
│   │ Edges = Transitions │───────────▶│ Context Data        │                │
│   │ Events = Triggers   │            │ Event History       │                │
│   └─────────────────────┘            └──────────┬──────────┘                │
│                                                  │                           │
│                                                  ▼                           │
│   ┌──────────────────────────────────────────────────────────────────────┐  │
│   │                      ORCHESTRATION LOOP                               │  │
│   │                                                                       │  │
│   │   ┌─────────┐     ┌─────────┐     ┌─────────┐     ┌─────────┐       │  │
│   │   │  STEP   │────▶│ EXECUTE │────▶│  WAIT   │────▶│  EVENT  │───┐   │  │
│   │   │         │     │  NODE   │     │   FOR   │     │RECEIVED │   │   │  │
│   │   └─────────┘     └─────────┘     │  EVENT  │     └─────────┘   │   │  │
│   │        ▲                          └─────────┘                    │   │  │
│   │        │                                                         │   │  │
│   │        └─────────────────────────────────────────────────────────┘   │  │
│   │                                                                       │  │
│   └──────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Key Concepts

| Concept | Description |
|---------|-------------|
| **Node** | An action or decision point (e.g., "Initiate Background Check") |
| **Edge** | A transition between nodes with guard conditions and event triggers |
| **Event** | A signal that enables edge traversal (e.g., "BackgroundCheckCompleted") |
| **Payload** | Data carried by an event that becomes part of the execution context |
| **Guard Condition** | FEEL expression that must evaluate to true for an edge to be traversable |

### The Step-Event Cycle

1. **Step**: Execute one eligible node
2. **Wait**: Workflow pauses, waiting for an event
3. **Event**: External signal arrives (or you send one)
4. **Evaluate**: Guard conditions checked against new context
5. **Repeat**: Next node becomes eligible, loop continues

---

## Quick Start

### Run a Complete Workflow

```
/orchestrate employee-onboarding
```

This starts the onboarding process and guides you through each step, showing:
- What node executed and what it did
- What events are available to send
- Full payload details for each event
- Which edge will be enabled by each event

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
| Argument | Description |
|----------|-------------|
| `processGraphId` | Process graph to run. Lists available graphs if omitted. |
| `--auto` | Automatically advance without prompting for event selection. |
| `--verbose` | Show extra details about graph structure and execution. |
| `--context` | Initial domain context as JSON. |

**What You'll See:**

1. **Graph Overview** - Before starting, shows structure and key events:
```
╭─ Process Graph: Employee Onboarding ────────────────────╮
│ Structure: 12 nodes, 18 edges                           │
│ Entry: offer-accepted                                   │
│ Terminals: onboarding-complete, onboarding-cancelled    │
│                                                         │
│ Key Events Required:                                    │
│   • BackgroundCheckCompleted / BackgroundCheckFailed    │
│   • EquipmentReady → EquipmentShipped                   │
│   • DocumentsCollected → I9Verified                     │
│   • OrientationScheduled                                │
╰─────────────────────────────────────────────────────────╯
```

2. **Node Execution Details** - What happened and what's next:
```
┌─ NODE EXECUTED: Initiate Background Check ──────────────┐
│                                                         │
│ Node ID:     background-check-init                      │
│ Action Type: SYSTEM_INVOCATION                          │
│ Handler:     backgroundCheckAdapter                     │
│                                                         │
│ What happened:                                          │
│   The system initiated a background check request with  │
│   an external provider. This is asynchronous - the      │
│   workflow will wait for a completion event.            │
│                                                         │
│ Outbound Edges (possible next steps):                   │
│   ┌───────────────────┬─────────────────┬─────────────┐│
│   │ Edge              │ Target Node     │ Trigger     ││
│   ├───────────────────┼─────────────────┼─────────────┤│
│   │ background-passed │ Equipment       │ Background- ││
│   │                   │ Procurement     │ CheckCompl. ││
│   ├───────────────────┼─────────────────┼─────────────┤│
│   │ background-failed │ Onboarding      │ Background- ││
│   │                   │ Cancelled       │ CheckFailed ││
│   └───────────────────┴─────────────────┴─────────────┘│
└─────────────────────────────────────────────────────────┘
```

3. **Event Options with Full Payloads**:
```
┌─ Event Option 1: BackgroundCheckCompleted ──────────────┐
│                                                         │
│ Description: Signals background check passed            │
│                                                         │
│ This event will:                                        │
│   • Enable edge: background-passed                      │
│   • Allow execution of: Equipment Procurement           │
│                                                         │
│ Payload that will be sent:                              │
│   {                                                     │
│     "status": "COMPLETED",                              │
│     "passed": true,                                     │
│     "requiresReview": false,                            │
│     "timestamp": "2026-02-09T12:30:00Z"                 │
│   }                                                     │
│                                                         │
│ What this means:                                        │
│   The external background check provider has verified   │
│   the candidate. The check passed without issues.       │
└─────────────────────────────────────────────────────────┘
```

4. **Event Confirmation**:
```
┌─ Event Sent ────────────────────────────────────────────┐
│                                                         │
│ → Event Type: BackgroundCheckCompleted                  │
│                                                         │
│ Payload Delivered:                                      │
│   {"status": "COMPLETED", "passed": true, ...}          │
│                                                         │
│ Effect:                                                 │
│   • Edge 'background-passed' is now traversable         │
│   • Node 'Equipment Procurement' is now eligible        │
│                                                         │
│ Context Updated:                                        │
│   Event payload added to instance's event history.      │
│   Guard conditions can now reference this data.         │
└─────────────────────────────────────────────────────────┘
```

5. **Completion Summary**:
```
╭─ Orchestration Complete ────────────────────────────────╮
│                                                         │
│ Execution Path:                                         │
│   ✓ Offer Accepted                                      │
│   ✓ Validate Candidate Data                             │
│   ✓ Initiate Background Check                           │
│       ↓ [BackgroundCheckCompleted]                      │
│   ✓ Equipment Procurement                               │
│       ↓ [EquipmentReady]                                │
│   ✓ Ship Equipment                                      │
│       ↓ [EquipmentShipped]                              │
│   ...                                                   │
│   ✓ Onboarding Complete                                 │
│                                                         │
│ Summary:                                                │
│   Total Nodes: 10 | Events: 6 | Duration: 1m 46s        │
│   Path: Happy path (all checks passed)                  │
╰─────────────────────────────────────────────────────────╯
```

---

### /workflow-status

**Purpose:** Comprehensive status check with actionable suggestions.

**Usage:**
```
/workflow-status [instanceId]
```

**Arguments:**
| Argument | Description |
|----------|-------------|
| `instanceId` | Instance to check. Lists active instances if omitted. |

**Information Displayed:**
- Instance ID, process graph, and current status
- Execution progress with all completed nodes
- Current state (active nodes, waiting status)
- Available events that can be sent with descriptions
- Suggested next actions based on current state

**Example Output:**
```
╭─ Workflow Status ──────────────────────────────────────────╮
│                                                            │
│ Instance: 87e5a6a7-ec80-418a-a16c-1f01b7203808            │
│ Process:  Employee Onboarding Process                      │
│ Status:   RUNNING                                          │
│ Started:  2026-02-09 12:30:00                              │
│                                                            │
├────────────────────────────────────────────────────────────┤
│ EXECUTION PROGRESS                                         │
│                                                            │
│ ✓ Offer Accepted                     [12:30:01]           │
│ ✓ Validate Candidate Data            [12:30:02]           │
│ ✓ Initiate Background Check          [12:30:03]           │
│ ⏳ Waiting for event...                                    │
│                                                            │
├────────────────────────────────────────────────────────────┤
│ AVAILABLE EVENTS                                           │
│                                                            │
│ • BackgroundCheckCompleted                                 │
│   Enables: Equipment Procurement                           │
│                                                            │
│ • BackgroundCheckFailed                                    │
│   Enables: Onboarding Cancelled                            │
│                                                            │
╰────────────────────────────────────────────────────────────╯

💡 Suggestions:
   1. Send event: /send-events 87e5a6a7... BackgroundCheckCompleted
   2. Continue interactively: /orchestrate employee-onboarding
   3. View details: Use get_available_events MCP tool
```

---

### /send-events

**Purpose:** Send one or more events to progress a workflow with auto-populated payloads.

**Usage:**
```
/send-events <instanceId> <eventType1> [eventType2] ...
```

**Arguments:**
| Argument | Description |
|----------|-------------|
| `instanceId` | Process instance ID (full UUID or first 8 characters) |
| `eventType1...` | One or more event types to send |

**Features:**
- Accepts partial instance IDs (first 8 characters)
- Validates events against available events before sending
- Auto-populates payloads with realistic data
- Sends events sequentially with progress display
- Shows full payload details and effects

**Example with Payload Details:**
```
> /send-events 87e5a6a7 BackgroundCheckCompleted EquipmentReady

╭─ Event Validation ─────────────────────────────────────────╮
│ Events to send:                                            │
│   ✓ BackgroundCheckCompleted (available)                   │
│   ✓ EquipmentReady (available)                             │
╰────────────────────────────────────────────────────────────╯

Sending events...

┌─ [1/2] BackgroundCheckCompleted ────────────────────────────┐
│ Payload:                                                    │
│   {                                                         │
│     "status": "COMPLETED",                                  │
│     "passed": true,                                         │
│     "requiresReview": false,                                │
│     "timestamp": "2026-02-09T12:35:00Z"                     │
│   }                                                         │
│ Result: ✓ Sent successfully                                 │
└─────────────────────────────────────────────────────────────┘

┌─ [2/2] EquipmentReady ──────────────────────────────────────┐
│ Payload:                                                    │
│   {                                                         │
│     "orderId": "EQ-1707486900000",                          │
│     "status": "READY",                                      │
│     "items": ["laptop", "monitor", "keyboard"],             │
│     "timestamp": "2026-02-09T12:35:01Z"                     │
│   }                                                         │
│ Result: ✓ Sent successfully                                 │
└─────────────────────────────────────────────────────────────┘

╭─ Summary ──────────────────────────────────────────────────╮
│ Sent: 2 events successfully                                │
│ Instance Status: RUNNING                                   │
│                                                            │
│ Next Steps:                                                │
│   • Run /orchestrate to step through newly enabled nodes   │
│   • Or use step_orchestration MCP tool directly            │
╰────────────────────────────────────────────────────────────╯
```

---

## Common Workflows

### 1. First-Time Orchestration (Interactive)

```bash
# List available process graphs
/orchestrate

# Start with default context
/orchestrate employee-onboarding

# Start with custom context
/orchestrate employee-onboarding --context '{"candidateName": "John Smith", "department": "Engineering"}'
```

### 2. Automated Run (No Prompts)

```bash
# Run through entire workflow automatically
# Selects first (success) event at each decision point
/orchestrate employee-onboarding --auto
```

### 3. Resume a Paused Workflow

```bash
# Check what workflows are running
/workflow-status

# Get details on specific instance
/workflow-status 87e5a6a7-ec80-418a-a16c-1f01b7203808

# Send the needed event
/send-events 87e5a6a7 BackgroundCheckCompleted

# Continue stepping through
/orchestrate employee-onboarding
```

### 4. Batch Event Sending

```bash
# Send multiple events at once to catch up
/send-events 87e5a6a7 DocumentsCollected I9Verified OrientationScheduled
```

### 5. Explore a Process Graph (Without Running)

```bash
# Use MCP tools directly to inspect
get_process_graph graphId="employee-onboarding"
get_graph_nodes graphId="employee-onboarding"
get_graph_edges graphId="employee-onboarding"
```

---

## Event Reference

### Employee Onboarding Events

| Event | Description | Enables |
|-------|-------------|---------|
| `BackgroundCheckCompleted` | Background check passed | Equipment Procurement |
| `BackgroundCheckFailed` | Background check failed | Onboarding Cancelled |
| `BackgroundReviewCompleted` | Manual review approved | Equipment Procurement |
| `EquipmentReady` | Equipment ready to ship | Ship Equipment |
| `EquipmentShipped` | Equipment shipped | (parallel branch complete) |
| `DocumentsCollected` | Documents received | I-9 Verification |
| `I9Verified` | I-9 verification passed | (parallel branch complete) |
| `OrientationScheduled` | Orientation scheduled | Finalize Onboarding |

---

## Understanding Payloads

Each event type has an auto-generated payload. Here's what each contains and why:

### BackgroundCheckCompleted
```json
{
  "status": "COMPLETED",        // Overall check status
  "passed": true,               // Used by guard conditions to route flow
  "requiresReview": false,      // If true, might trigger review branch
  "timestamp": "2026-02-09..."  // Audit trail
}
```
**Guard conditions can check:** `backgroundCheck.passed = true`

### EquipmentReady
```json
{
  "orderId": "EQ-1707486900000",           // Unique order reference
  "status": "READY",                        // Equipment status
  "items": ["laptop", "monitor", "keyboard"], // What was ordered
  "timestamp": "2026-02-09..."              // When it became ready
}
```
**Used for:** Tracking equipment fulfillment, audit

### DocumentsCollected
```json
{
  "i9Part1Completed": true,       // I-9 Section 1 done
  "w4Completed": true,            // W-4 tax form done
  "directDepositCompleted": true, // Banking info collected
  "timestamp": "2026-02-09..."
}
```
**Guard conditions can check:** `documents.i9Part1Completed = true`

### OrientationScheduled
```json
{
  "scheduled": true,
  "date": "2026-02-22",           // Scheduled date
  "time": "09:00",                // Scheduled time
  "location": "Virtual",          // Location/format
  "timestamp": "2026-02-09..."
}
```
**Used for:** Calendar integration, employee notification

---

## Troubleshooting

### Workflow is Stuck (No Events Available)

**Symptoms:** `step_orchestration` returns WAITING but `get_available_events` returns empty

**Solutions:**
1. Check `get_required_events` for what the workflow needs
2. The required event may need to come from an external system
3. Use `signal_event` MCP tool with custom payload if needed

### Event Not Having Expected Effect

**Symptoms:** Event sent but workflow doesn't progress

**Solutions:**
1. Check that the event type matches exactly (case-sensitive)
2. Verify the event payload satisfies guard conditions
3. Call `step_orchestration` after sending the event

### Cannot Find Instance

**Solutions:**
1. Use `/workflow-status` without arguments to list all instances
2. Instance IDs can be partial (first 8 characters work)
3. Check if instance was completed or cancelled

### Wrong Path Taken

**Symptoms:** Workflow went down unexpected branch

**Solutions:**
1. Check the event payload - guard conditions use payload values
2. For example, `BackgroundCheckFailed` vs `BackgroundCheckCompleted`
3. Review `get_process_history` to see what events were received

---

## Related Documentation

- [MCP Tools Guide](MCP_TOOLS_GUIDE.md) - Direct MCP tool usage for advanced scenarios
- [System Documentation](SYSTEM_DOCUMENTATION.md) - Full architecture and API reference
- [README](../README.md) - Project overview and quick start

---

## License

Copyright 2026 ihelio. Licensed under the Apache License 2.0.
