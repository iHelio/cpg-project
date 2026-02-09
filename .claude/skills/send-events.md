# /send-events - Batch Event Sender

Send one or more events to progress a CPG workflow.

## Usage

```
/send-events <instanceId> <eventType1> [eventType2] [eventType3] ...
```

## Arguments

- `instanceId` (required): The process instance ID
- `eventType1, eventType2, ...` (required): One or more event types to send

## Examples

```
/send-events 87e5a6a7-ec80-418a-a16c-1f01b7203808 BackgroundCheckCompleted
/send-events 87e5a6a7-ec80-418a-a16c-1f01b7203808 BackgroundCheckCompleted EquipmentReady
/send-events 87e5a6a7 DocumentsCollected I9Verified OrientationScheduled
```

---

## Instructions

When the user invokes `/send-events`, follow this workflow:

### Step 1: Parse Arguments

1. Extract the `instanceId` from the first argument
   - Accept both full UUIDs and partial IDs (first 8 characters)
2. Extract all event types from remaining arguments
3. Validate that at least one event type is provided

**If arguments are missing:**
```
Usage: /send-events <instanceId> <eventType1> [eventType2] ...

Example:
  /send-events 87e5a6a7-ec80-418a-a16c-1f01b7203808 BackgroundCheckCompleted

To see available events for an instance, use:
  /workflow-status <instanceId>
```

### Step 2: Validate Instance

1. If a partial instance ID was provided, call `list_process_instances` to find the full ID
2. Call `get_orchestration_status` to verify the instance exists and is running

**If instance not found:**
```
❌ Instance not found: {instanceId}

Use /workflow-status to see active instances.
```

**If instance is not running:**
```
⚠️  Instance {instanceId} is {status}, not RUNNING.

Only running instances can receive events.
```

### Step 3: Validate Events

1. Call `get_available_events` to get the list of valid events for this instance
2. Check each provided event type against the available events
3. Categorize events as:
   - **Valid:** Event is in the available events list
   - **Unknown:** Event is not in the list but might be valid (domain event)

Display validation results:
```
╭─ Event Validation ─────────────────────────────────────────╮
│ Instance: 87e5a6a7-ec80-418a-a16c-1f01b7203808            │
├────────────────────────────────────────────────────────────┤
│ Events to send:                                            │
│   ✓ BackgroundCheckCompleted (available)                   │
│   ✓ EquipmentReady (available)                             │
│   ? CustomEvent (not in available list - will send anyway) │
╰────────────────────────────────────────────────────────────╯
```

Ask for confirmation if there are unknown events:
```
Some events are not in the available list. Send anyway? (y/n)
```

### Step 4: Send Events

For each event type, in order:

1. Call `send_event` MCP tool with:
   - `instanceId`: The validated instance ID
   - `eventType`: The current event type
2. Display the result:
   ```
   → Sent: BackgroundCheckCompleted ✓
     Payload: {"status": "COMPLETED", "passed": true, ...}
   ```

If sending multiple events, show progress:
```
Sending events...
  [1/3] BackgroundCheckCompleted ✓
  [2/3] EquipmentReady ✓
  [3/3] DocumentsCollected ✓
```

### Step 5: Post-Send Status

After all events are sent:

1. Call `get_orchestration_status` to get updated status
2. Call `get_available_events` to show what's next
3. Display summary:

```
╭─ Events Sent Successfully ─────────────────────────────────╮
│ Instance: 87e5a6a7-ec80-418a-a16c-1f01b7203808            │
│ Status: RUNNING                                            │
├────────────────────────────────────────────────────────────┤
│ Sent Events:                                               │
│   ✓ BackgroundCheckCompleted                               │
│   ✓ EquipmentReady                                         │
│   ✓ DocumentsCollected                                     │
├────────────────────────────────────────────────────────────┤
│ Next Steps:                                                │
│   • Call step_orchestration to execute newly enabled nodes │
│   • Or continue with /orchestrate to run interactively     │
│                                                            │
│ Remaining Available Events:                                │
│   • I9Verified                                             │
│   • OrientationScheduled                                   │
╰────────────────────────────────────────────────────────────╯
```

### Step 6: Offer Follow-up

Ask if the user wants to continue:
```
Would you like to:
  1. Step the orchestration forward
  2. Send more events
  3. Check full status
  4. Exit

Enter choice (1-4):
```

---

## Batch Mode Behavior

When multiple events are provided:

1. Events are sent **sequentially** in the order provided
2. Each event is sent immediately after the previous one completes
3. If one event fails, subsequent events are still attempted
4. A summary shows which succeeded and which failed

```
╭─ Batch Results ────────────────────────────────────────────╮
│ Sent: 2 of 3                                               │
│   ✓ BackgroundCheckCompleted                               │
│   ✓ EquipmentReady                                         │
│   ✗ InvalidEvent - Not recognized                          │
╰────────────────────────────────────────────────────────────╯
```

---

## Event Type Reference

Common event types for the Employee Onboarding process:

| Event Type | Description |
|------------|-------------|
| `OnboardingStarted` | Onboarding process has started |
| `BackgroundCheckCompleted` | Background check passed |
| `BackgroundCheckFailed` | Background check failed |
| `BackgroundReviewCompleted` | Manual review completed |
| `EquipmentReady` | Equipment is ready for shipping |
| `EquipmentShipped` | Equipment has been shipped |
| `DocumentsCollected` | Required documents collected |
| `I9Verified` | I-9 verification complete |
| `OrientationScheduled` | Orientation scheduled |

Display this reference when user asks for help:
```
/send-events --help
```

---

## Error Handling

- **Invalid instance ID format:** Show usage and examples
- **Instance not found:** List available instances
- **Event send failure:** Show error, continue with remaining events
- **All events failed:** Suggest using `get_required_events` for diagnosis
