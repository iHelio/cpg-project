# Plugin, Connector & Cowork Design for CPG
## Based on Anthropic's Agent Design Recommendations

---

## Table of Contents

1. [Mental Model: Three Layers of Extensibility](#mental-model-three-layers-of-extensibility)
2. [How I Think About This System](#how-i-think-about-this-system)
3. [Plugin Design](#plugin-design)
4. [Connector Design](#connector-design)
5. [Cowork Design](#cowork-design)
6. [Design Decision Matrix](#design-decision-matrix)
7. [What Anthropic Recommends — And How I Apply It](#what-anthropic-recommends--and-how-i-apply-it)
8. [Putting It All Together: The Onboarding Example](#putting-it-all-together-the-onboarding-example)

---

## Mental Model: Three Layers of Extensibility

When I reason about extending CPG's capabilities I use three distinct mental frames that map to a clear separation of concerns:

```
┌─────────────────────────────────────────────────────────────────────┐
│                         COWORK LAYER                                │
│         How AI agents coordinate and reason together                │
│   (Claude + MCP server + orchestration signals + agent autonomy)    │
├─────────────────────────────────────────────────────────────────────┤
│                        CONNECTOR LAYER                              │
│        How the system talks to the outside world                    │
│  (REST, Kafka, PostgreSQL, Anthropic API, HR systems, external APIs)│
├─────────────────────────────────────────────────────────────────────┤
│                         PLUGIN LAYER                                │
│       How new business behaviors enter the workflow engine          │
│       (ActionHandlers, custom node types, PolicyEvaluators)         │
└─────────────────────────────────────────────────────────────────────┘
```

These layers are **not** independent — they compose. A plugin can call a connector. A connector can publish events that a coworking agent reacts to. The key insight is that the hexagonal architecture of CPG is already designed for this: each layer interacts through **domain ports** (interfaces), never through direct infrastructure coupling.

---

## How I Think About This System

### The Core Question for Each Capability

Before adding anything, I ask three questions:

1. **Plugin or Connector?** — Is this new *behavior inside a workflow node* (plugin) or a new *integration boundary with an external system* (connector)?
2. **Sync or Async?** — Does it produce an immediate result the orchestrator can evaluate, or does it emit an event that the workflow waits for?
3. **Human in the loop or automated?** — Does this decision warrant governance (idempotency check, authorization, policy gate) before executing?

### The Anthropic Principle I Keep Central

Anthropic's guidance for building agents centers on one idea: **simplest solution that works**. Add autonomy only when the complexity of the task genuinely justifies it. For CPG this translates to:

- If a business rule can be expressed as a FEEL expression or DMN table → don't write a plugin, just configure.
- If integration with an external system can be a synchronous REST call that returns in the same request → don't use Kafka, use an adapter.
- If a workflow can run end-to-end without an AI agent → don't add Claude, let the process graph handle it.
- Only when tasks are genuinely ambiguous, require real judgment, or benefit from language understanding do I route to an `AGENT_ASSISTED` node.

---

## Plugin Design

### What a Plugin Is in CPG

A **plugin** is an implementation of the `ActionHandler` domain port. It represents the *executable behavior* of a workflow node. The process graph declares *what* to do via a node's `Action`; a plugin is *how* it gets done.

```
Domain Port (interface — never changes):
  ActionHandler.execute(ActionContext) → ActionResult

Plugin Implementation (varies per use case):
  BackgroundCheckActionHandler
  EquipmentProvisioningActionHandler
  AiBackgroundAnalystHandler       ← AI-assisted plugin
  NotificationActionHandler
  LegacyHrSystemActionHandler
```

### Plugin Registration Pattern

All plugins register through the `ActionHandlerRegistry` in the application layer. This is a Spring-managed registry that maps a `handlerRef` string (declared on a node's Action) to an `ActionHandler` bean.

```
Node Declaration (in ProcessGraph builder):
  action = Action.of(ActionType.SYSTEM_INVOCATION, "background-check-adapter")
                                                    ↑
                               This string is the plugin's registration key

Registry (application layer):
  @Component("background-check-adapter")
  public class BackgroundCheckActionHandler implements ActionHandler { ... }
```

**Why this works for extensibility:** Adding a new plugin is purely additive — declare a new Spring component, annotate it with the handler reference name, implement the interface. No changes to the engine, orchestrator, or existing nodes.

### Plugin Design Rules I Follow

#### 1. Plugins are stateless and idempotent
The `ExecutionGovernor` checks idempotency *before* calling the plugin, but the plugin itself must also be safe to call twice if governance is disabled in a test environment. This means plugins never hold mutable state — they take `ActionContext` in and produce `ActionResult` out.

#### 2. Plugins do not orchestrate
A plugin executes exactly one action and returns. It does not decide what comes next. Navigation is the orchestrator's job. If a plugin needs to branch (e.g., "if high risk, route to HR"), it returns a structured result in `ActionResult.output()` that the EdgeEvaluator's FEEL guard conditions will read.

```
Plugin returns:         { "riskScore": 72, "recommendation": "REVIEW" }
Edge guard evaluates:   aiAnalysis.requiresReview = true
Orchestrator routes:    → review-background-results node
```

This separation is critical: plugins produce *data*, the graph produces *decisions*.

#### 3. AGENT_ASSISTED nodes are plugins with language model calls
The `AiBackgroundAnalystHandler` is a plugin that delegates to `AiAnalystPort` (a domain port). The adapter behind that port calls Claude. From the orchestrator's perspective, this is identical to any other plugin — it receives an `ActionContext` and returns an `ActionResult`. The fact that language reasoning happens inside is an infrastructure detail.

```
ActionHandler (plugin interface)
  └── AiBackgroundAnalystHandler
        └── AiAnalystPort (domain port)
              └── ClaudeAiAnalystAdapter (infrastructure)
                    └── Spring AI → Anthropic API → Claude
```

#### 4. Human tasks are plugins that pause, not complete
A `HUMAN_TASK` node type maps to a plugin that records the task assignment and returns `ActionResult.pending()`. The workflow waits. When the human submits their decision (via REST or an external signal), an event is published. The orchestrator reevaluates. This pattern — **emit an event, let the graph decide what to do with it** — is fundamental to how I keep plugins decoupled from routing logic.

### Designing a New Plugin: Checklist

When I need to add a new node behavior:

- [ ] Define the `ActionType` (SYSTEM_INVOCATION, HUMAN_TASK, AGENT_ASSISTED, DECISION, NOTIFICATION, WAIT)
- [ ] Implement `ActionHandler` in `application/handler/`
- [ ] Register with a unique `handlerRef` string
- [ ] Return structured output that FEEL guards can evaluate
- [ ] Write the node definition in the relevant `ProcessGraphBuilder`
- [ ] Add unit test in `AiBackgroundAnalystHandlerTest` style
- [ ] Decide: does this plugin need a domain port (to swap implementations)?

---

## Connector Design

### What a Connector Is in CPG

A **connector** is an infrastructure adapter that implements a domain port and integrates with an external system. It lives in `infrastructure/` and is never referenced by domain or application code directly — only the port interface is visible upward.

```
Domain Ports (in domain/):              Infrastructure Adapters (in infrastructure/):
  ExpressionEvaluator        ←──────    KieFeelExpressionEvaluator
  PolicyEvaluator            ←──────    DmnPolicyEvaluator
  RuleEvaluator              ←──────    DmnRuleEvaluator
  AiAnalystPort              ←──────    ClaudeAiAnalystAdapter
                             ←──────    StubAiAnalystAdapter (test)
  ProcessGraphRepository     ←──────    InMemoryProcessGraphRepository
                             ←──────    JpaProcessGraphRepository (planned)
  ProcessEventPublisher      ←──────    InMemoryEventPublisher
                             ←──────    KafkaProcessEventPublisher (planned)
```

### Connector Categories

#### Category 1: Computation Connectors
These replace an algorithmic capability. FEEL and DMN are computation connectors — they evaluate expressions and decision tables. The domain does not care whether the FEEL engine is KIE Drools, OpenRules, or a custom parser.

**Design rule:** Computation connectors must be synchronous, deterministic (same input → same output), and fast enough for inline evaluation during orchestration.

#### Category 2: Persistence Connectors
These implement `ProcessGraphRepository`, `ProcessInstanceRepository`, and `DecisionTraceRepository`. Today they are in-memory. The planned JPA/PostgreSQL adapters are drop-in replacements.

**Design rule:** Persistence connectors must handle concurrent writes (optimistic locking on `ProcessInstance` aggregate). The port interface must not leak ORM-specific exceptions — wrap them in domain exceptions.

#### Category 3: Messaging Connectors
These implement `ProcessEventPublisher`. The in-memory publisher is for development; Kafka is the production target. Orchestration events received from external systems also flow through here.

**Design rule:** Messaging connectors are asynchronous. The orchestrator does not block waiting for downstream consumers. The `OrchestratorEventSubscriber` bridges the event publisher to the orchestrator's blocking event queue — this decoupling is deliberate.

#### Category 4: AI/ML Connectors
These implement `AiAnalystPort` or future ports like `ClassificationPort`, `SummarizationPort`. The `ClaudeAiAnalystAdapter` calls the Anthropic API via Spring AI. The `StubAiAnalystAdapter` returns deterministic results for testing.

**Design rule:** AI connectors must have a stub counterpart. Production AI calls are conditional (`@ConditionalOnProperty`). Tests never call real APIs. This is Anthropic's own recommendation: build observable, testable AI components where the AI boundary is an explicit seam.

#### Category 5: External System Connectors
These are planned for HR systems, background check providers, payroll, equipment vendors. Each will implement an application-layer port (not domain — they are use-case specific) and live in `infrastructure/`.

**Design rule:** External system connectors own retry logic, circuit breaking, and timeout configuration. The plugin that calls them (via application layer) sees only a clean interface. Failures surface as `ActionResult.failed()`, not raw HTTP exceptions.

### Connector Design Rules I Follow

#### 1. One port, multiple adapters — always
Every connector has a domain or application-layer port. I never reference a concrete adapter from application or domain code. This is what allows `StubAiAnalystAdapter` to exist alongside `ClaudeAiAnalystAdapter` and be selected via Spring's `@ConditionalOnProperty`.

#### 2. Connectors are configuration, not code
Switching from in-memory to PostgreSQL, or from KIE FEEL to another expression engine, should require adding a new implementation and changing `application.yml` — not touching domain or application logic.

#### 3. Connectors hold their own error contracts
A `KieFeelExpressionEvaluator` that fails to parse an expression wraps the KIE exception in `EvaluationResult.error(reason)`. The orchestrator sees a typed result, not a raw library exception. This prevents infrastructure failures from leaking into domain decision traces.

#### 4. Test connectors match production contracts exactly
The `StubAiAnalystAdapter` implements the same port as `ClaudeAiAnalystAdapter` with deterministic behavior. The stub is not a mock — it is a real implementation with fixed rules. This makes tests reliable without requiring environment variables or network calls.

### Designing a New Connector: Checklist

- [ ] Define or identify the domain port in `domain/` (or application port in `application/`)
- [ ] Create implementation in `infrastructure/[category]/`
- [ ] Add `@ConditionalOnProperty` or `@Profile` to allow swap-out
- [ ] Create stub implementation for tests
- [ ] Wrap all external exceptions in domain-safe types
- [ ] Configure timeouts, retries in `application.yml` — not in the adapter itself
- [ ] Add integration test using `Testcontainers` for database/Kafka connectors

---

## Cowork Design

### What Cowork Means in CPG

**Cowork** is how AI agents — specifically Claude and future specialized agents — participate in CPG's workflow execution. CPG exposes a Model Context Protocol (MCP) server that gives AI clients a structured, discoverable interface to the orchestration engine. Cowork is the discipline of deciding *when* to involve AI, *how much autonomy* to grant it, and *how* to keep human oversight meaningful.

### Three Cowork Modes

I think about AI participation in three modes, each with different trust levels and autonomy:

#### Mode 1: Tool-Assisted Human Operator (Current)
Claude Desktop connects to CPG's MCP server. A hiring manager asks "What's the status of Sarah Chen's onboarding?" Claude calls `find_onboarding_status`, reads the response, and answers in natural language. Claude is a *presentation layer* here — it adds language understanding and conversational UX on top of structured data.

```
Human → Claude Desktop → MCP Server → CPG API
                         (29 tools)
```

**Trust level:** Claude sees read-only data and can call action tools, but every action it takes goes through CPG's `ExecutionGovernor` (idempotency + authorization + policy gate). Claude cannot bypass governance.

**When I use this mode:** Always available. This is the baseline cowork capability — it costs nothing extra in the process graph and any Claude client can participate.

#### Mode 2: AI-Assisted Node Execution (Current)
A node in the process graph declares `ActionType.AGENT_ASSISTED`. When the orchestrator executes this node, it calls `AiBackgroundAnalystHandler`, which invokes Claude to analyze structured data and produce a typed recommendation. Claude is *inside* the workflow as a decision-making participant, not an external observer.

```
ProcessExecutionEngine
  └── executeNode(ai-analyze-background-check)
        └── AiBackgroundAnalystHandler.execute(ActionContext)
              └── AiAnalystPort.analyzeBackgroundCheck(data, context)
                    └── ClaudeAiAnalystAdapter → Anthropic API → Claude
                          └── BackgroundAnalysisResult { riskScore, recommendation }
```

**Trust level:** Claude's output becomes data in the `ExecutionContext`. It does *not* control routing — FEEL expressions and the orchestrator do. Claude is a *contributor*, not a *controller*.

**When I use this mode:** When a task genuinely requires language understanding or judgment over unstructured data (e.g., interpreting background check findings, summarizing documents). I do not use it for tasks that can be expressed as deterministic rules.

#### Mode 3: Autonomous Agent Orchestration (Planned)
A Claude agent connects to CPG via MCP, starts a process, steps through nodes, sends events, monitors status, and makes decisions about when to escalate — all autonomously. This is multi-step agentic behavior where Claude is the "pilot" and CPG is the "autopilot system."

```
Claude Agent (autonomous)
  ├── start_orchestration(...)
  ├── step_orchestration(instanceId) ─── loop ───┐
  ├── get_available_events(instanceId)            │
  ├── send_event(instanceId, eventType)           │
  └── get_orchestration_status(instanceId) ───────┘
```

**Trust level:** This mode requires the most careful design. The agent has significant capability but CPG's governance layer is the hard backstop. Every node execution passes through `ExecutionGovernor`. Irreversible actions (payment processing, account creation) must require human approval before the agent can send the enabling event.

**When I use this mode:** When the orchestration task is well-defined, reversibility is high, and human review would add more latency than value. Never for high-stakes or hard-to-reverse decisions without a human-task gate.

### MCP as the Cowork Interface Contract

The MCP server is not just a technical integration — it is a **capability contract** between CPG and any AI agent. I design MCP tools with these principles:

#### Tool naming communicates intent and scope
Tools use verb-noun naming that is unambiguous to a language model:
- `start_orchestration` — clearly creates a new process (consequential)
- `step_orchestration` — clearly executes one step (bounded)
- `get_orchestration_status` — clearly read-only (safe to call anytime)

An AI agent reading the tool list must be able to infer from the name alone whether a tool is read-only, single-step, or potentially consequential.

#### Resources provide context without side effects
The 5 MCP resources (`graph://published`, `graph://{graphId}`, `instance://{instanceId}`, etc.) are read-only views. An agent can read them freely to build situational awareness before taking action. This mirrors Anthropic's recommendation to give agents enough context to reason correctly before committing to an action.

#### Prompts encode expert workflow knowledge
The 3 MCP prompts (`analyze_process_graph`, `troubleshoot_instance`, `orchestration_summary`) are pre-built reasoning templates. They encode domain expert knowledge about *what to look for* when inspecting a workflow. An agent using these prompts benefits from the same diagnostic heuristics a human expert would apply.

### Cowork Design Rules I Follow (From Anthropic's Guidance)

#### 1. Minimal footprint — prefer read before write
Anthropic's principle: agents should request only necessary permissions and avoid acquiring resources beyond what the current task needs.

In CPG, I design agent workflows so Claude reads status (`get_orchestration_status`, `get_available_events`) before taking action (`step_orchestration`, `send_event`). The read-heavy tool set is intentional.

#### 2. Explicit checkpoints for irreversible actions
Any node tagged with a consequential action type (payment, account creation, equipment order) has a corresponding `HUMAN_TASK` gate or policy guard that an agent cannot bypass. The agent can signal a `NodeCompleted` event, but the `ExecutionGovernor` will reject execution if authorization is missing.

This maps to Anthropic's recommendation: *"If in doubt, don't. Pause and clarify rather than proceed with an ambiguous or risky action."*

#### 3. Prefer event-driven stepping over autonomous looping
The CPG MCP API is deliberately step-by-step — each `step_orchestration` call executes at most one node. This is not a limitation; it is a feature. It forces an agent to re-evaluate state after every action before deciding what to do next. An agent that calls `step_orchestration` in a tight loop without inspecting `get_orchestration_status` between calls is making a design error.

The correct agentic pattern:
```
loop:
  result = step_orchestration(instanceId)
  if result.status == WAITING:
    events = get_available_events(instanceId)
    chosen_event = agent_reason_about(events, context)
    send_event(instanceId, chosen_event)
  elif result.status == COMPLETED:
    break
  elif result.status == BLOCKED or FAILED:
    escalate_to_human()
    break
```

#### 4. Tracing is not optional — it is the cowork audit trail
Every orchestration decision produces an immutable `DecisionTrace`. For cowork scenarios where Claude autonomously steps through a process, the decision trace is how a human can reconstruct *why* the agent made each choice. This satisfies Anthropic's transparency requirement: AI systems must produce outputs that humans can understand and verify.

The trace captures context, eligible space, alternatives considered, governance results, and outcomes — exactly what you need to audit an autonomous agent's behavior.

#### 5. Multi-agent trust hierarchy
When CPG's orchestrator receives events, it must treat the event source with appropriate trust. Events from human operators (via REST), from the local event publisher, and from an external Claude agent via MCP are structurally similar but carry different implicit authority levels.

Current state: all events are implicitly trusted once they reach the `OrchestrationEvent` queue. Future cowork design should tag events with their origin (human, agent, system) and let the `AuthorizationResult` in `ExecutionGovernor` differentiate. An agent-originated event should not be able to approve a human-task gate.

### Cowork Anti-Patterns I Avoid

| Anti-Pattern | Why I Avoid It |
|---|---|
| Giving Claude access to modify the ProcessGraph | Agents should operate *within* defined processes, not redefine them. Graph authoring is a human design-time activity. |
| Having Claude directly call infrastructure adapters | Claude operates through MCP tools (the interfaces layer). It never bypasses the domain, application, or governance layers. |
| Using AGENT_ASSISTED for deterministic decisions | If a decision can be expressed as FEEL or DMN, use those. Agent invocations add latency, cost, and non-determinism. Reserve them for genuine ambiguity. |
| Fully autonomous multi-step execution without checkpoints | For any workflow with irreversible side effects, insert HUMAN_TASK gates. The agent orchestrates up to the gate, then pauses for human confirmation. |
| Ignoring orchestration status between steps | Always check status after each step. An agent that assumes `EXECUTED` and loops without checking will miss `WAITING`, `BLOCKED`, or `FAILED` states. |

---

## Design Decision Matrix

When I receive a new extensibility requirement, I use this matrix:

| Requirement | Category | Where It Lives | Key Interface |
|---|---|---|---|
| New workflow step type (e.g., "Send Slack notification") | Plugin | `application/handler/` | `ActionHandler` |
| New expression language (e.g., SpEL) | Connector (computation) | `infrastructure/expression/` | `ExpressionEvaluator` |
| PostgreSQL persistence | Connector (persistence) | `infrastructure/persistence/` | `ProcessGraphRepository`, `ProcessInstanceRepository` |
| Kafka event streaming | Connector (messaging) | `infrastructure/event/` | `ProcessEventPublisher` |
| HR system integration | Connector (external) | `infrastructure/hr/` | Application-layer port |
| AI document summarization | Plugin + Connector | Handler in `application/handler/`, adapter in `infrastructure/ai/` | `ActionHandler` → new AI port |
| Claude Desktop workflow assistant | Cowork (Mode 1) | MCP server already exists | MCP tools/resources/prompts |
| Autonomous agent stepping through approvals | Cowork (Mode 3) | MCP + governance config | `ExecutionGovernor` authorization |
| New business rule source (database-driven) | Connector (computation) | `infrastructure/dmn/` or new | `RuleEvaluator` |
| Scheduled timer events | Connector (messaging) | `infrastructure/event/` scheduler | `ProcessEventPublisher` + timer |

---

## What Anthropic Recommends — And How I Apply It

### Recommendation 1: Start Simple, Add Complexity Only When Needed

**What Anthropic says:** Use the simplest solution. Before building an agent, ask whether a deterministic script would work. Before adding multi-agent coordination, ask whether a single agent suffices.

**How CPG applies it:**
- The default `ActionType` for new nodes is `SYSTEM_INVOCATION` — a deterministic plugin. `AGENT_ASSISTED` is opt-in only.
- FEEL expressions and DMN tables handle routing. Claude handles only what FEEL cannot.
- The `StubAiAnalystAdapter` defaults to `true` (`matchIfMissing = true`) — the system runs without any AI by default.

### Recommendation 2: Well-Defined Tool Interfaces with Good Error Handling

**What Anthropic says:** Define clear input/output contracts for tools. Errors should be informative, not raw stack traces. Tools should indicate what went wrong in a way the agent can act on.

**How CPG applies it:**
- `ActionResult` is a typed value object: `success(output)`, `failed(reason)`, `pending()`. No raw exceptions cross the plugin boundary.
- MCP tool responses include `hint` fields (e.g., `"Call step_orchestration again to continue execution"`). These are guidance for the AI client.
- `get_required_events` tells an agent exactly what is needed, not just that something is blocked.
- `EvaluationResult`, `PolicyResult`, `RuleResult` all carry structured error information for tracing and agent consumption.

### Recommendation 3: Human in the Loop for High-Stakes Decisions

**What Anthropic says:** For irreversible actions, have the agent pause and confirm with a human. Build workflows with explicit human checkpoints.

**How CPG applies it:**
- `HUMAN_TASK` is a first-class `ActionType`. Background check review, I-9 verification, manager expense approval — all modeled as human tasks.
- The orchestrator halts at human tasks and waits for an event. No agent can skip a human task gate without the appropriate authorization token.
- `ExecutionGovernor.checkAuthorization()` can enforce that certain node types can only be approved by human-originated signals.

### Recommendation 4: Build for Observability

**What Anthropic says:** AI-driven systems must produce auditable outputs. You must be able to understand what an agent did and why.

**How CPG applies it:**
- `DecisionTrace` is immutable and captures the *full context* at decision time: all nodes evaluated, all alternatives considered, governance check results, and outcome.
- Every AGENT_ASSISTED node execution produces a trace entry that includes Claude's recommendation alongside the orchestrator's routing decision.
- `get_process_history` MCP tool lets any agent (or human) reconstruct the complete execution path.

### Recommendation 5: Trust Boundaries in Multi-Agent Systems

**What Anthropic says:** When building multi-agent systems, clearly distinguish orchestrators from subagents. Orchestrators direct. Subagents execute. Don't allow subagents to override orchestrator policy.

**How CPG applies it:**
- CPG's `InstanceOrchestrator` is the sole authority on navigation decisions. No plugin, connector, or external agent can override the `NavigationDecider`.
- External agents (Claude Desktop, autonomous agents) interact through the `interfaces/mcp/` layer only. They cannot directly call `application/orchestration/` classes.
- `ExecutionGovernor` is the final hard gate — it is not bypassable through any external interface. Governance runs in-process, not as an external service an agent could call separately.

### Recommendation 6: Design for Composition

**What Anthropic says:** Build components that can be combined flexibly. An orchestrator should be able to spawn subagents for specialized work and collect their results.

**How CPG applies it:**
- The `AiAnalystPort` is designed for composition: any `ActionHandler` can call it. Tomorrow, a document review handler or contract analysis handler can use the same port.
- The MCP prompt `orchestration_summary` is composable: a higher-level orchestrating agent can call it to get a structured summary, then use that summary to decide whether to continue, escalate, or cancel — without needing to understand CPG internals.
- Future planned design: each workflow domain (onboarding, expense, document) could register its own specialized MCP tools, allowing a Claude agent to discover and compose domain-specific capabilities automatically.

---

## Putting It All Together: The Onboarding Example

The employee onboarding workflow is the best demonstration of how all three layers compose:

```
COWORK: Claude agent starts orchestration via MCP tool
   ↓
PLUGIN: initialize-onboarding → default ActionHandler (SYSTEM_INVOCATION)
   ↓
PLUGIN: validate-candidate → validation ActionHandler
   ↓
PLUGIN: run-background-check → CONNECTOR: BackgroundCheckProviderAdapter (planned)
   ↓ (workflow waits for external event)
COWORK: Claude agent calls send_event("BackgroundCheckCompleted")
   ↓
PLUGIN: ai-analyze-background-check → AiBackgroundAnalystHandler
   └── CONNECTOR: ClaudeAiAnalystAdapter → Anthropic API
         └── Claude analyzes findings → { riskScore: 15, recommendation: APPROVE }
   ↓
Graph routes based on result:
  → if APPROVE: parallel provisioning (no human needed)
  → if REVIEW:  review-background-results (HUMAN_TASK gate)
         └── COWORK: agent pauses, signals human, waits for Approval event
   ↓
PLUGIN: order-equipment + create-accounts (PARALLEL)
   └── CONNECTOR: EquipmentVendorAdapter + ActiveDirectoryAdapter (planned)
   ↓
PLUGIN: finalize-onboarding → terminal node
   ↓
COWORK: Claude agent reads get_orchestration_status → COMPLETED
         └── generates natural language summary for hiring manager
```

Each capability is cleanly separated:
- The **graph** (ProcessGraph) declares *what* the workflow does
- The **plugins** (ActionHandlers) declare *how* each step executes
- The **connectors** (infrastructure adapters) declare *where* data and integration come from
- The **cowork** layer (MCP + Claude) declares *who* drives and observes the workflow

Adding a new HR system integration (connector) does not touch the graph. Adding a new AI analysis step (plugin + connector) does not change how events work. Adding a new Claude-powered assistant feature (cowork) does not change how nodes execute. The layers compose without coupling.

---

## License

Copyright 2026 ihelio. Licensed under the Apache License 2.0.
