# MCP Tools Usage Guide

This guide explains how to use the Model Context Protocol (MCP) tools exposed by the CPG orchestration server for AI-assisted workflow automation.

## Table of Contents

1. [Overview](#overview)
2. [Connecting to the MCP Server](#connecting-to-the-mcp-server)
3. [Event-Driven Orchestration Model](#event-driven-orchestration-model)
4. [Tool Reference](#tool-reference)
5. [Common Workflows](#common-workflows)
6. [Event Types and Payloads](#event-types-and-payloads)

---

## Overview

The CPG MCP server exposes 21 tools that allow AI clients to:

- Discover and inspect process graphs
- Start and manage orchestrated workflows
- Progress workflows through event-driven stepping
- Inspect process state and execution history

The orchestrator is **completely event-driven** - each orchestration step executes at most one node. This gives AI clients fine-grained control over workflow progression.

### Key Concepts

| Concept | Description |
|---------|-------------|
| **Process Graph** | A template defining nodes (actions) and edges (transitions) |
| **Process Instance** | A running execution of a process graph |
| **Node** | A single action or decision point in the workflow |
| **Edge** | A transition between nodes with guard conditions and event triggers |
| **Event** | A signal that can enable edge transitions or trigger node execution |

---

## Connecting to the MCP Server

The MCP server uses Server-Sent Events (SSE) transport over HTTP.

**SSE Endpoint:** `http://localhost:8080/sse`

**Test Connection:**
```bash
curl -s -N http://localhost:8080/sse
```

---

## Event-Driven Orchestration Model

The orchestrator follows an event-driven pattern where:

1. Each `step_orchestration` call executes **at most one node**
2. Workflow progression requires sending events to enable edge transitions
3. AI clients have full control over when and how the workflow advances

### Workflow Progression Cycle

```
┌─────────────────────────────────────────────────────────────────┐
│                    EVENT-DRIVEN WORKFLOW                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ┌──────────────────┐                                          │
│   │ start_orchestration │                                        │
│   └─────────┬────────┘                                          │
│             │                                                    │
│             ▼                                                    │
│   ┌──────────────────┐                                          │
│   │ step_orchestration │◄────────────────────────────────┐      │
│   └─────────┬────────┘                                   │      │
│             │                                            │      │
│             ▼                                            │      │
│   ┌──────────────────┐     ┌──────────────────┐         │      │
│   │ Node Executed?   │─Yes─►│ More nodes?      │─Yes────►│      │
│   └─────────┬────────┘     └─────────┬────────┘         │      │
│             │ No                     │ No               │      │
│             ▼                        ▼                  │      │
│   ┌──────────────────┐     ┌──────────────────┐         │      │
│   │ WAITING for event │     │ COMPLETED        │         │      │
│   └─────────┬────────┘     └──────────────────┘         │      │
│             │                                            │      │
│             ▼                                            │      │
│   ┌──────────────────┐                                   │      │
│   │ get_available_   │                                   │      │
│   │ events           │                                   │      │
│   └─────────┬────────┘                                   │      │
│             │                                            │      │
│             ▼                                            │      │
│   ┌──────────────────┐                                   │      │
│   │ send_event       │───────────────────────────────────┘      │
│   └──────────────────┘                                          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Tool Reference

### Process Graph Tools

#### `list_process_graphs`

List all published process graphs.

**Parameters:** None

**Example Response:**
```json
[
  {
    "id": "employee-onboarding",
    "name": "Employee Onboarding Process",
    "description": "Complete onboarding workflow",
    "version": 1,
    "status": "PUBLISHED",
    "nodeCount": 12,
    "edgeCount": 18
  }
]
```

#### `get_process_graph`

Get a process graph by ID with structure summary.

**Parameters:**
- `graphId` (required): Process graph ID

#### `get_graph_nodes`

Get all nodes in a process graph with full details including preconditions, policy gates, business rules, actions, and event config.

**Parameters:**
- `graphId` (required): Process graph ID

#### `get_graph_edges`

Get all edges in a process graph with full details including guard conditions, execution semantics, priority, and event triggers.

**Parameters:**
- `graphId` (required): Process graph ID

#### `get_node_details`

Get full details of a specific node.

**Parameters:**
- `graphId` (required): Process graph ID
- `nodeId` (required): Node ID

#### `get_edge_details`

Get full details of a specific edge.

**Parameters:**
- `graphId` (required): Process graph ID
- `edgeId` (required): Edge ID

---

### Orchestration Control Tools

#### `start_orchestration`

Start autonomous orchestration of a process graph.

**Parameters:**
- `processGraphId` (required): Process graph ID
- `clientContext` (optional): Client context as JSON object
- `domainContext` (optional): Domain context as JSON object

**Example:**
```json
{
  "processGraphId": "employee-onboarding",
  "clientContext": "{\"tenantId\": \"acme-corp\"}",
  "domainContext": "{\"candidateName\": \"John Smith\", \"offer\": {\"signed\": true}}"
}
```

**Response:**
```json
{
  "instanceId": "87e5a6a7-ec80-418a-a16c-1f01b7203808",
  "processGraphId": "employee-onboarding",
  "status": "RUNNING",
  "isActive": true
}
```

#### `step_orchestration`

Execute a single orchestration step. The orchestrator evaluates eligible nodes and executes at most one.

**Parameters:**
- `instanceId` (required): Process instance ID

**Response:**
```json
{
  "instanceId": "87e5a6a7-ec80-418a-a16c-1f01b7203808",
  "orchestrationStatus": "EXECUTED",
  "message": "Executed node: background-check-init",
  "instanceStatus": "RUNNING",
  "executedNode": {
    "nodeId": "background-check-init",
    "nodeName": "Initiate Background Check"
  },
  "hint": "Call step_orchestration again to continue execution"
}
```

**Status Values:**
- `EXECUTED` - A node was executed
- `WAITING` - No eligible nodes, waiting for events
- `COMPLETED` - Process completed
- `BLOCKED` - Governance rejected execution
- `FAILED` - Execution error

#### `get_orchestration_status`

Get current orchestration status for a process instance.

**Parameters:**
- `instanceId` (required): Process instance ID

#### `suspend_orchestration`

Pause orchestration of a running process instance.

**Parameters:**
- `instanceId` (required): Process instance ID

#### `resume_orchestration`

Resume a suspended orchestration.

**Parameters:**
- `instanceId` (required): Process instance ID

#### `cancel_orchestration`

Cancel orchestration of a process instance.

**Parameters:**
- `instanceId` (required): Process instance ID

---

### Event-Driven Workflow Tools

#### `get_required_events`

Analyze what events are needed to progress a process instance. Returns events from edge triggers, node preconditions, and guard conditions.

**Parameters:**
- `instanceId` (required): Process instance ID

**Response:**
```json
{
  "instanceId": "87e5a6a7-ec80-418a-a16c-1f01b7203808",
  "instanceStatus": "RUNNING",
  "eligibleActionCount": 0,
  "requiredEvents": [
    {
      "eventType": "BackgroundCheckCompleted",
      "source": "EDGE_TRIGGER",
      "sourceId": "bg-check-passed",
      "sourceName": "Background Check Passed",
      "description": "Edge is activated by this event"
    }
  ],
  "hint": "Use signal_event or send_event to send one of the required events"
}
```

#### `get_available_events`

Get events that can be sent to progress the workflow, including auto-populated payloads based on event type.

**Parameters:**
- `instanceId` (required): Process instance ID

**Response:**
```json
{
  "instanceId": "87e5a6a7-ec80-418a-a16c-1f01b7203808",
  "instanceStatus": "RUNNING",
  "availableEvents": [
    {
      "eventType": "BackgroundCheckCompleted",
      "targetNode": "Equipment Procurement",
      "edgeName": "background-passed",
      "description": "Signals that background check has completed successfully",
      "payload": {
        "status": "COMPLETED",
        "passed": true,
        "requiresReview": false,
        "timestamp": "2026-02-08T12:00:00Z"
      }
    }
  ],
  "hint": "Use send_event with one of these events to progress the workflow."
}
```

#### `send_event`

Send an event with auto-populated payload to progress the workflow.

**Parameters:**
- `instanceId` (required): Process instance ID
- `eventType` (required): Event type (e.g., "BackgroundCheckCompleted")

**Response:**
```json
{
  "instanceId": "87e5a6a7-ec80-418a-a16c-1f01b7203808",
  "eventType": "BackgroundCheckCompleted",
  "payload": {
    "status": "COMPLETED",
    "passed": true,
    "timestamp": "2026-02-08T12:00:00Z"
  },
  "sent": true,
  "instanceStatus": "RUNNING",
  "isActive": true,
  "hint": "Call step_orchestration to execute the next eligible node."
}
```

#### `signal_event`

Signal an event with custom payload to trigger orchestrator reevaluation.

**Parameters:**
- `instanceId` (required): Process instance ID
- `eventType` (required): Event type (NODE_COMPLETED, DATA_CHANGE, or domain event)
- `nodeId` (optional): Node ID (required for node events)
- `payload` (optional): Event payload as JSON object

---

### Instance Inspection Tools

#### `list_process_instances`

List running process instances with optional filters.

**Parameters:**
- `processGraphId` (optional): Filter by process graph ID
- `status` (optional): Filter by status (RUNNING, SUSPENDED, COMPLETED, FAILED, CANCELLED)
- `correlationId` (optional): Filter by correlation ID

#### `get_available_nodes`

Get nodes eligible for execution in a process instance.

**Parameters:**
- `instanceId` (required): Process instance ID

#### `get_active_nodes`

Get currently active (executing) nodes for a process instance.

**Parameters:**
- `instanceId` (required): Process instance ID

#### `get_process_history`

Get execution history for a process instance showing all node executions.

**Parameters:**
- `instanceId` (required): Process instance ID

**Response:**
```json
{
  "instanceId": "87e5a6a7-ec80-418a-a16c-1f01b7203808",
  "processGraphId": "employee-onboarding",
  "status": "RUNNING",
  "startedAt": "2026-02-08T12:00:00Z",
  "executionCount": 3,
  "history": [
    {
      "nodeId": "offer-accepted",
      "nodeName": "Offer Accepted",
      "status": "COMPLETED",
      "startedAt": "2026-02-08T12:00:01Z",
      "completedAt": "2026-02-08T12:00:02Z"
    }
  ]
}
```

---

## Common Workflows

### Basic Workflow Progression

```
1. start_orchestration(processGraphId="employee-onboarding", domainContext=...)
   → Returns instanceId

2. step_orchestration(instanceId)
   → Executes "Offer Accepted" node
   → Returns EXECUTED status

3. step_orchestration(instanceId)
   → Executes "Validate Candidate Data" node
   → Returns EXECUTED status

4. step_orchestration(instanceId)
   → Executes "Initiate Background Check" node
   → Returns WAITING status (waiting for background check result)

5. get_available_events(instanceId)
   → Returns BackgroundCheckCompleted, BackgroundCheckFailed events

6. send_event(instanceId, eventType="BackgroundCheckCompleted")
   → Signals the event with auto-populated payload

7. step_orchestration(instanceId)
   → Executes "Equipment Procurement" node
   → Continue...
```

### Monitoring Workflow Progress

```
1. get_orchestration_status(instanceId)
   → Check overall status, active nodes, last decision

2. get_process_history(instanceId)
   → View all executed nodes and their results

3. get_active_nodes(instanceId)
   → See which nodes are currently executing
```

### Handling Workflow Blockages

```
1. step_orchestration(instanceId)
   → Returns WAITING status

2. get_required_events(instanceId)
   → Analyze what events could unblock the workflow

3. get_available_events(instanceId)
   → Get events with payloads ready to send

4. send_event(instanceId, eventType="...")
   → Send the appropriate event

5. step_orchestration(instanceId)
   → Continue execution
```

---

## Event Types and Payloads

The system automatically populates event payloads based on event type. Here are the supported event types for the employee onboarding process:

### OnboardingStarted
```json
{
  "timestamp": "2026-02-08T12:00:00Z",
  "source": "orchestrator"
}
```

### BackgroundCheckCompleted
```json
{
  "status": "COMPLETED",
  "passed": true,
  "requiresReview": false,
  "timestamp": "2026-02-08T12:00:00Z"
}
```

### BackgroundCheckFailed
```json
{
  "status": "FAILED",
  "passed": false,
  "reason": "Background check did not pass",
  "timestamp": "2026-02-08T12:00:00Z"
}
```

### BackgroundReviewCompleted
```json
{
  "decision": "APPROVED",
  "reviewer": "hr-manager",
  "comments": "Review completed successfully",
  "timestamp": "2026-02-08T12:00:00Z"
}
```

### EquipmentReady
```json
{
  "orderId": "EQ-1707393600000",
  "status": "READY",
  "items": ["laptop", "monitor", "keyboard"],
  "timestamp": "2026-02-08T12:00:00Z"
}
```

### EquipmentShipped
```json
{
  "trackingNumber": "TRK-1707393600000",
  "carrier": "FedEx",
  "estimatedDelivery": "2026-02-11",
  "timestamp": "2026-02-08T12:00:00Z"
}
```

### DocumentsCollected
```json
{
  "i9Part1Completed": true,
  "w4Completed": true,
  "directDepositCompleted": true,
  "timestamp": "2026-02-08T12:00:00Z"
}
```

### I9Verified
```json
{
  "verified": true,
  "verificationDate": "2026-02-08",
  "documentType": "passport",
  "timestamp": "2026-02-08T12:00:00Z"
}
```

### OrientationScheduled
```json
{
  "scheduled": true,
  "date": "2026-02-22",
  "time": "09:00",
  "location": "Virtual",
  "timestamp": "2026-02-08T12:00:00Z"
}
```

---

## License

Copyright 2026 ihelio. Licensed under the Apache License 2.0.
