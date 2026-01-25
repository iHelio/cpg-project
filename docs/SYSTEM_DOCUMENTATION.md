# Contextualized Process Graph (CPG) System Documentation

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Project Structure](#project-structure)
4. [Domain Model](#domain-model)
5. [Process Execution Engine](#process-execution-engine)
6. [Process Orchestrator](#process-orchestrator)
7. [Infrastructure Adapters](#infrastructure-adapters)
8. [Example: Employee Onboarding](#example-employee-onboarding)
9. [Build and Test](#build-and-test)
10. [Next Steps](#next-steps)

---

## Overview

### Purpose

The Contextualized Process Graph (CPG) is an enterprise-grade process execution engine designed to orchestrate complex business workflows. Unlike traditional workflow engines that are state-centric, CPG is **action-oriented** - it describes what CAN be done next given the current context, rather than what IS true about the system.

### Key Features

- **Domain-Driven Design (DDD)**: Clean separation between domain logic and infrastructure
- **Hexagonal Architecture**: Ports and adapters pattern for maximum testability
- **Policy-Enforcing Orchestrator**: Authoritative navigation engine with governance enforcement
- **Immutable Decision Traces**: Complete audit trail of every orchestration decision
- **FEEL Expressions**: Industry-standard Friendly Enough Expression Language for conditions
- **DMN Integration**: Decision Model and Notation for business rules and policies
- **Event-Driven**: Event correlation and reactive edge traversal with reevaluation
- **Governance Layer**: Idempotency, authorization, and policy gate enforcement
- **Parallel Execution**: Support for concurrent workflow branches with join semantics
- **Compensation Handling**: Built-in retry, rollback, and escalation mechanisms

### Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 (LTS) with Virtual Threads |
| Framework | Spring Boot 3.4 |
| Build | Maven 3.9+ |
| Expression Engine | KIE FEEL (Drools) 9.44 |
| Decision Engine | KIE DMN (Drools) 9.44 |
| Testing | JUnit 5, Mockito, Testcontainers |

---

## Architecture

### Hexagonal Architecture

The system follows the Hexagonal (Ports & Adapters) architecture pattern:

```
┌─────────────────────────────────────────────────────────────────┐
│                      External Systems                            │
│   ┌──────────┐    ┌──────────────┐    ┌─────────────────────┐   │
│   │  Web UI  │    │External Events│    │   HR/IT Systems     │   │
│   └────┬─────┘    └──────┬───────┘    └──────────┬──────────┘   │
└────────┼─────────────────┼───────────────────────┼──────────────┘
         │                 │                       │
┌────────┼─────────────────┼───────────────────────┼──────────────┐
│        ▼                 ▼                       │  interfaces  │
│   ┌─────────────────────────────────────────┐   │              │
│   │    REST Controllers │ Event Listeners   │   │              │
│   └────────────────────┬────────────────────┘   │              │
└────────────────────────┼────────────────────────┼──────────────┘
                         │                        │
┌────────────────────────┼────────────────────────┼──────────────┐
│                        ▼                        │  application │
│   ┌──────────────────────────────────────────────────────────┐ │
│   │  PROCESS ORCHESTRATOR (Policy-Enforcing Navigation)      │ │
│   │  ┌────────────────────────────────────────────────────┐  │ │
│   │  │  InstanceOrchestrator                              │  │ │
│   │  │    ├── ContextAssembler (builds RuntimeContext)    │  │ │
│   │  │    ├── EligibilityEvaluator (builds EligibleSpace) │  │ │
│   │  │    ├── NavigationDecider (selects next action)     │  │ │
│   │  │    └── DecisionTracer (records decisions)          │  │ │
│   │  └────────────────────────────────────────────────────┘  │ │
│   │  Use Cases │ Graph Builders │ Action Handlers            │ │
│   └──────────────────────┬───────────────────────────────────┘ │
└────────────────────────┼────────────────────────┼──────────────┘
                         │                        │
┌────────────────────────┼────────────────────────┼──────────────┐
│                        ▼                        │    domain    │
│   ┌──────────────────────────────────────────────────────────┐ │
│   │                      CORE ENGINE                          │ │
│   │  ┌────────────────────────────────────────────────────┐  │ │
│   │  │  ProcessExecutionEngine                            │  │ │
│   │  │    ├── NodeEvaluator                               │  │ │
│   │  │    ├── EdgeEvaluator                               │  │ │
│   │  │    ├── ExecutionCoordinator                        │  │ │
│   │  │    └── CompensationHandler                         │  │ │
│   │  └────────────────────────────────────────────────────┘  │ │
│   │                                                          │ │
│   │  ┌─────────────┐  ┌────────────┐  ┌─────────────────┐   │ │
│   │  │ ProcessGraph│  │   Node     │  │ ProcessInstance │   │ │
│   │  │    Edge     │  │ FeelExpr   │  │ ExecutionContext│   │ │
│   │  └─────────────┘  └────────────┘  └─────────────────┘   │ │
│   │                                                          │ │
│   │  ┌─────────── ORCHESTRATION DOMAIN ────────────────────┐ │ │
│   │  │ RuntimeContext │ EligibleSpace │ NavigationDecision │ │ │
│   │  │ DecisionTrace │ GovernanceResult │ OrchestrationEvent│ │ │
│   │  └──────────────────────────────────────────────────────┘ │ │
│   │                                                          │ │
│   │  ┌──────────────────── PORTS ──────────────────────────┐ │ │
│   │  │ ExpressionEvaluator │ PolicyEvaluator │ RuleEvaluator│ │ │
│   │  │ ActionHandler │ EventPublisher │ Repositories        │ │ │
│   │  │ ProcessOrchestrator │ ExecutionGovernor │ NodeSelector│ │ │
│   │  │ DecisionTracer │ DecisionTraceRepository             │ │ │
│   │  └──────────────────────────────────────────────────────┘ │ │
│   └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
                         │
┌────────────────────────┼───────────────────────────────────────┐
│                        ▼                       infrastructure  │
│   ┌─────────────────────────────────────────────────────────┐  │
│   │  KieFeelEvaluator │ DmnPolicyEvaluator │ DmnRuleEvaluator│  │
│   │  InMemoryRepositories │ InMemoryEventPublisher          │  │
│   │  DefaultProcessOrchestrator │ DefaultExecutionGovernor  │  │
│   │  DefaultDecisionTracer │ InMemoryDecisionTraceRepository│  │
│   └─────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

### Layer Responsibilities

| Layer | Package | Responsibility |
|-------|---------|----------------|
| **Domain** | `com.ihelio.cpg.domain` | Core business logic, entities, ports |
| **Domain/Orchestration** | `com.ihelio.cpg.domain.orchestration` | Orchestration domain types and ports |
| **Application** | `com.ihelio.cpg.application` | Use cases, graph builders, action handlers |
| **Application/Orchestration** | `com.ihelio.cpg.application.orchestration` | Policy-enforcing navigation engine |
| **Infrastructure** | `com.ihelio.cpg.infrastructure` | External adapters, persistence, messaging |
| **Infrastructure/Orchestration** | `com.ihelio.cpg.infrastructure.orchestration` | Orchestrator implementations |
| **Interfaces** | `com.ihelio.cpg.interfaces` | REST controllers, CLI, event listeners |

---

## Project Structure

```
cpg/
├── pom.xml                           # Maven build configuration
├── CLAUDE.md                         # AI collaboration guidelines
├── checkstyle.xml                    # Google Java Style rules
├── docs/
│   ├── architecture.mmd              # Architecture diagram (Mermaid)
│   ├── domain-model.mmd              # Domain model class diagram
│   ├── execution-flow.mmd            # Execution sequence diagram
│   ├── onboarding-process.mmd        # Onboarding flowchart
│   └── SYSTEM_DOCUMENTATION.md       # This file
├── src/
│   ├── main/
│   │   ├── java/com/ihelio/cpg/
│   │   │   ├── CpgApplication.java           # Spring Boot entry point
│   │   │   ├── domain/
│   │   │   │   ├── model/                    # Domain entities
│   │   │   │   │   ├── ProcessGraph.java     # Graph template aggregate
│   │   │   │   │   ├── Node.java             # Decision point entity
│   │   │   │   │   ├── Edge.java             # Transition entity
│   │   │   │   │   └── FeelExpression.java   # Expression value object
│   │   │   │   ├── execution/                # Runtime state
│   │   │   │   │   ├── ProcessInstance.java  # Running process aggregate
│   │   │   │   │   └── ExecutionContext.java # Runtime context
│   │   │   │   ├── engine/                   # Core engine services
│   │   │   │   │   ├── ProcessExecutionEngine.java
│   │   │   │   │   ├── NodeEvaluator.java
│   │   │   │   │   ├── EdgeEvaluator.java
│   │   │   │   │   ├── ExecutionCoordinator.java
│   │   │   │   │   └── CompensationHandler.java
│   │   │   │   ├── orchestration/            # Orchestration domain
│   │   │   │   │   ├── RuntimeContext.java   # Assembled context
│   │   │   │   │   ├── EligibleSpace.java    # Eligible nodes/edges
│   │   │   │   │   ├── NavigationDecision.java # Decision with alternatives
│   │   │   │   │   ├── DecisionTrace.java    # Immutable audit record
│   │   │   │   │   ├── GovernanceResult.java # Governance check results
│   │   │   │   │   ├── OrchestrationEvent.java # Event sealed interface
│   │   │   │   │   ├── ProcessOrchestrator.java # Orchestrator port
│   │   │   │   │   ├── ExecutionGovernor.java # Governance port
│   │   │   │   │   ├── DecisionTracer.java   # Tracing port
│   │   │   │   │   └── NodeSelector.java     # Selection port
│   │   │   │   ├── expression/               # Expression evaluation port
│   │   │   │   ├── action/                   # Action handler port
│   │   │   │   ├── policy/                   # Policy evaluation port
│   │   │   │   ├── rule/                     # Rule evaluation port
│   │   │   │   ├── event/                    # Event handling
│   │   │   │   ├── repository/               # Persistence ports
│   │   │   │   └── exception/                # Domain exceptions
│   │   │   ├── application/
│   │   │   │   ├── handler/                  # Action handler registry
│   │   │   │   ├── onboarding/               # Onboarding process builder
│   │   │   │   └── orchestration/            # Process orchestrator
│   │   │   │       ├── ContextAssembler.java # Builds RuntimeContext
│   │   │   │       ├── EligibilityEvaluator.java # Builds EligibleSpace
│   │   │   │       ├── NavigationDecider.java # Selects next action
│   │   │   │       └── InstanceOrchestrator.java # Full cycle
│   │   │   ├── infrastructure/
│   │   │   │   ├── feel/                     # KIE FEEL adapter
│   │   │   │   ├── dmn/                      # DMN decision service
│   │   │   │   ├── persistence/              # In-memory repositories
│   │   │   │   ├── event/                    # Event publisher adapter
│   │   │   │   ├── config/                   # Spring configuration
│   │   │   │   └── orchestration/            # Orchestrator implementations
│   │   │   │       ├── DefaultProcessOrchestrator.java
│   │   │   │       ├── DefaultExecutionGovernor.java
│   │   │   │       ├── DefaultDecisionTracer.java
│   │   │   │       ├── OrchestratorConfigProperties.java
│   │   │   │       └── OrchestratorEventSubscriber.java
│   │   │   └── interfaces/
│   │   │       └── rest/                     # REST controllers
│   │   └── resources/
│   │       └── dmn/                          # DMN decision tables
│   └── test/
│       └── java/com/ihelio/cpg/
│           ├── domain/engine/                # Engine unit tests
│           ├── application/orchestration/    # Orchestration unit tests
│           ├── infrastructure/orchestration/ # Orchestrator tests
│           └── integration/                  # Integration tests
```

---

## Domain Model

### Core Entities

#### ProcessGraph (Aggregate Root)

A **ProcessGraph** is an immutable, versioned template that defines all possible nodes and edges for a business process.

```java
public record ProcessGraph(
    ProcessGraphId id,           // Unique identifier
    String name,                 // Human-readable name
    String description,          // Documentation
    int version,                 // Version for governance
    ProcessGraphStatus status,   // DRAFT, PUBLISHED, DEPRECATED, ARCHIVED
    List<Node> nodes,            // All decision points
    List<Edge> edges,            // All permissible transitions
    List<NodeId> entryNodeIds,   // Process start points
    List<NodeId> terminalNodeIds,// Process end points
    Metadata metadata            // Audit information
)
```

**Key Methods:**
- `findNode(NodeId)` - Retrieve a node by ID
- `getOutboundEdges(NodeId)` - Get transitions leaving a node
- `getInboundEdges(NodeId)` - Get transitions entering a node
- `getNodesSubscribedToEvent(String)` - Find event-triggered nodes
- `validate()` - Check graph structural integrity

#### Node (Entity)

A **Node** represents a governed decision point - what CAN be done next, not what IS true.

```java
public record Node(
    NodeId id,                   // Unique identifier
    String name,                 // Human-readable name
    String description,          // Documentation
    int version,                 // Version tracking
    Preconditions preconditions, // FEEL conditions for availability
    List<PolicyGate> policyGates,// Compliance/regulatory gates
    List<BusinessRule> businessRules, // DMN-evaluated rules
    Action action,               // What happens when executed
    EventConfig eventConfig,     // Event subscriptions/emissions
    ExceptionRoutes exceptionRoutes // Error handling routes
)
```

**Node Components:**

| Component | Purpose |
|-----------|---------|
| **Preconditions** | FEEL expressions evaluated against context to determine node availability |
| **PolicyGate** | Compliance, statutory, regulatory, or organizational gates via DMN |
| **BusinessRule** | DMN decisions for execution parameters, SLAs, obligations |
| **Action** | The work performed (SYSTEM_INVOCATION, HUMAN_TASK, AGENT_ASSISTED, etc.) |
| **EventConfig** | Events that trigger or are emitted by this node |
| **ExceptionRoutes** | Remediation and escalation paths for failures |

#### Edge (Entity)

An **Edge** represents a permissible transition between nodes with guard conditions.

```java
public record Edge(
    EdgeId id,                   // Unique identifier
    String name,                 // Human-readable name
    String description,          // Documentation
    NodeId sourceNodeId,         // Origin node
    NodeId targetNodeId,         // Destination node
    GuardConditions guardConditions, // Conditions for traversal
    ExecutionSemantics executionSemantics, // SEQUENTIAL, PARALLEL
    Priority priority,           // Edge selection priority
    EventTriggers eventTriggers, // Events that activate this edge
    CompensationSemantics compensationSemantics // Rollback behavior
)
```

**Guard Conditions Types:**

| Type | Description |
|------|-------------|
| **contextConditions** | FEEL expressions evaluated against runtime context |
| **ruleOutcomeConditions** | Conditions on business rule results |
| **policyOutcomeConditions** | Conditions on policy gate outcomes |
| **eventConditions** | Events that must have occurred |

#### ProcessInstance (Aggregate Root)

A **ProcessInstance** is a running execution of a ProcessGraph.

```java
public class ProcessInstance {
    ProcessInstanceId id;              // Unique execution ID
    ProcessGraphId processGraphId;     // Template reference
    int processGraphVersion;           // Template version
    String correlationId;              // Business correlation
    Instant startedAt;                 // Start timestamp
    Instant completedAt;               // Completion timestamp
    ProcessInstanceStatus status;      // RUNNING, SUSPENDED, COMPLETED, FAILED
    ExecutionContext context;          // Runtime context
    List<NodeExecution> nodeExecutions;// Execution history
    Set<NodeId> activeNodeIds;         // Currently executing
    Set<EdgeId> pendingEdgeIds;        // Awaiting activation
}
```

#### ExecutionContext (Value Object)

The runtime context containing all data needed for expression evaluation.

```java
public record ExecutionContext(
    Map<String, Object> clientContext,     // Tenant-specific data
    Map<String, Object> domainContext,     // Business domain data
    Map<String, Object> accumulatedState,  // Node execution outputs
    List<ReceivedEvent> eventHistory,      // Events received
    List<Obligation> obligations           // Derived obligations
)
```

**Context Methods:**
- `toFeelContext()` - Flatten context for FEEL evaluation
- `withState(key, value)` - Add accumulated state
- `withEvent(event)` - Add received event

### Enumerations

| Enum | Values |
|------|--------|
| **ProcessGraphStatus** | DRAFT, PUBLISHED, DEPRECATED, ARCHIVED |
| **ProcessInstanceStatus** | RUNNING, SUSPENDED, COMPLETED, FAILED, CANCELLED |
| **ActionType** | SYSTEM_INVOCATION, HUMAN_TASK, AGENT_ASSISTED, DECISION, NOTIFICATION, WAIT |
| **PolicyType** | COMPLIANCE, STATUTORY, REGULATORY, ORGANIZATIONAL |
| **ExecutionType** | SEQUENTIAL, PARALLEL, COMPENSATING |
| **JoinType** | ALL, ANY, N_OF_M |

### Orchestration Domain Types

The orchestration layer introduces additional domain types for policy-enforcing navigation.

#### RuntimeContext (Value Object)

The assembled authoritative context for orchestration decisions.

```java
public record RuntimeContext(
    Map<String, Object> clientContext,    // Tenant-specific configuration
    Map<String, Object> domainContext,    // Business domain data
    Map<String, Object> entityState,      // Accumulated execution state
    OperationalContext operationalContext,// System state and obligations
    List<ReceivedEvent> receivedEvents,   // Event history
    Instant assembledAt                   // Timestamp
)
```

#### EligibleSpace (Value Object)

The cross-product of eligible nodes and traversable edges.

```java
public record EligibleSpace(
    List<NodeEvaluation> eligibleNodes,   // Nodes that passed all checks
    List<EdgeEvaluation> traversableEdges,// Edges that can be traversed
    List<CandidateAction> candidateActions,// Node+edge combinations
    Instant evaluatedAt                   // Timestamp
)
```

#### NavigationDecision (Value Object)

The deterministic selection result with alternatives considered.

```java
public record NavigationDecision(
    DecisionType type,                    // PROCEED, WAIT, BLOCKED, COMPLETE
    List<NodeSelection> selectedNodes,    // Actions to execute
    List<AlternativeConsidered> alternatives, // All options evaluated
    SelectionCriteria selectionCriteria,  // How selection was made
    String selectionReason,               // Human-readable explanation
    EligibleSpace eligibleSpace,          // Source eligible space
    Instant decidedAt                     // Timestamp
)
```

#### DecisionTrace (Immutable Record)

Complete audit trail for every orchestration decision.

```java
public record DecisionTrace(
    DecisionTraceId id,                   // Unique trace ID
    Instant timestamp,                    // When decision was made
    ProcessInstanceId instanceId,         // Process instance
    DecisionType type,                    // NAVIGATION, EXECUTION, WAIT, BLOCKED
    ContextSnapshot context,              // Full context snapshot
    EvaluationSnapshot evaluation,        // Nodes/edges evaluated
    DecisionSnapshot decision,            // Selection made
    GovernanceSnapshot governance,        // Governance checks
    OutcomeSnapshot outcome               // Result of decision
)
```

#### GovernanceResult (Value Object)

Combined result of pre-execution governance checks.

```java
public record GovernanceResult(
    boolean approved,                     // All checks passed
    IdempotencyResult idempotency,        // Duplicate check
    AuthorizationResult authorization,    // Permission check
    PolicyGateResult policyGate,          // Final policy check
    String rejectionReason                // Why rejected (if any)
)
```

#### OrchestrationEvent (Sealed Interface)

Events that trigger orchestration reevaluation.

| Event Type | Description |
|------------|-------------|
| `DataChange` | External domain entity updated |
| `Approval` | Human approval/rejection received |
| `Failure` | External system failure |
| `TimerExpired` | SLA/deadline timer fired |
| `PolicyUpdate` | Policy definition changed |
| `NodeCompleted` | Node execution finished |
| `NodeFailed` | Node execution failed |

---

## Process Execution Engine

### Overview

The `ProcessExecutionEngine` is the main orchestrator that coordinates all execution components.

```
┌─────────────────────────────────────────────────────────────────┐
│                    ProcessExecutionEngine                        │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                   Core Operations                            ││
│  │  • startProcess()      - Begin new instance                  ││
│  │  • suspendProcess()    - Pause execution                     ││
│  │  • resumeProcess()     - Continue execution                  ││
│  │  • executeNode()       - Run a specific node                 ││
│  │  • evaluateAndTraverseEdges() - Find next nodes              ││
│  │  • handleEvent()       - Process external events             ││
│  └─────────────────────────────────────────────────────────────┘│
│                              │                                   │
│  ┌───────────────────────────┼───────────────────────────────┐  │
│  │                    Collaborators                           │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌────────────────────┐ │  │
│  │  │NodeEvaluator │ │EdgeEvaluator │ │ExecutionCoordinator│ │  │
│  │  └──────────────┘ └──────────────┘ └────────────────────┘ │  │
│  │  ┌──────────────────────┐ ┌───────────────────────────┐   │  │
│  │  │CompensationHandler   │ │ProcessEventPublisher      │   │  │
│  │  └──────────────────────┘ └───────────────────────────┘   │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Engine Components

#### NodeEvaluator

Evaluates whether a node is available for execution.

**Evaluation Flow:**
1. Evaluate preconditions (FEEL expressions)
2. Evaluate policy gates (DMN decisions)
3. Evaluate business rules (DMN decisions)
4. Return `NodeEvaluation` with availability status

```java
public record NodeEvaluation(
    Node node,
    boolean available,           // Can this node execute?
    String blockedReason,        // Why is it blocked?
    List<PolicyResult> policyResults,  // Policy outcomes
    Map<String, Object> ruleOutputs    // Rule-derived values
)
```

#### EdgeEvaluator

Evaluates which edges are traversable from a completed node.

**Evaluation Flow:**
1. Evaluate context conditions (FEEL)
2. Check rule outcome conditions
3. Check policy outcome conditions
4. Check event conditions
5. Select edges respecting priority (exclusive edges)

```java
public record EdgeEvaluation(
    Edge edge,
    boolean traversable,     // Can this edge be traversed?
    String blockedReason     // Why is it blocked?
)
```

#### ExecutionCoordinator

Manages parallel execution branches and join synchronization.

**Capabilities:**
- Categorize edges into sequential and parallel groups
- Initiate parallel branches
- Synchronize at join points (ALL, ANY, N_OF_M)
- Track parallel branch status

```java
public record ParallelBranch(
    String branchId,
    Node.NodeId startingNodeId,
    ParallelBranchStatus status,  // PENDING, ACTIVE, COMPLETED, FAILED
    Instant startedAt,
    Instant completedAt
)
```

#### CompensationHandler

Determines compensation actions when execution fails.

**Compensation Strategies:**

| Strategy | Description |
|----------|-------------|
| **RETRY** | Retry the failed node (with backoff) |
| **ROLLBACK** | Execute compensation actions |
| **ESCALATE** | Route to escalation node |
| **FAIL** | Fail the entire process |
| **SKIP** | Skip and continue |

```java
public record CompensationAction(
    CompensationActionType type,  // RETRY, ROLLBACK, ESCALATE, FAIL, SKIP
    String reason,
    int retryAttempt,
    int maxRetries,
    Node.NodeId escalationNodeId,
    Node.NodeId compensationNodeId
)
```

### Execution Flow Sequence

```
Client                  Engine               NodeEvaluator        ActionHandler
   │                      │                       │                    │
   │ startProcess()       │                       │                    │
   │─────────────────────>│                       │                    │
   │                      │ create instance       │                    │
   │                      │ publish start event   │                    │
   │                      │ activate entry nodes  │                    │
   │<─────────────────────│                       │                    │
   │                      │                       │                    │
   │ executeNode()        │                       │                    │
   │─────────────────────>│                       │                    │
   │                      │ evaluate()            │                    │
   │                      │──────────────────────>│                    │
   │                      │<──────────────────────│                    │
   │                      │                       │                    │
   │                      │ (if available)        │                    │
   │                      │ execute()             │                    │
   │                      │───────────────────────────────────────────>│
   │                      │<───────────────────────────────────────────│
   │                      │                       │                    │
   │                      │ update context        │                    │
   │                      │ complete node         │                    │
   │                      │ publish events        │                    │
   │<─────────────────────│                       │                    │
```

---

## Process Orchestrator

### Overview

The **Process Orchestrator** is the authoritative runtime component that navigates process graphs as a **policy-enforcing decision engine**. It doesn't merely execute workflows - it evaluates, decides, governs, and traces every meaningful decision.

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                    PROCESS ORCHESTRATOR (Policy-Enforcing Navigation)         │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  CONTEXT LAYER                                                                │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │ RuntimeContext: client config + domain knowledge + entity state +       │ │
│  │                 operational signals + received events                    │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                              │                                                │
│                              ▼                                                │
│  EVALUATION LAYER (Decision Engine)                                          │
│  ┌───────────────────┐  ┌───────────────────┐  ┌───────────────────────────┐│
│  │ NodeEligibility   │  │ EdgeEligibility   │  │ AlternativeEvaluator     ││
│  │ Evaluator         │  │ Evaluator         │  │ (consider all options)   ││
│  │ (preconditions,   │  │ (guard conditions,│  │                          ││
│  │  rules, policies) │  │  event conditions)│  │                          ││
│  └───────────────────┘  └───────────────────┘  └───────────────────────────┘│
│                              │                                                │
│                              ▼                                                │
│  DECISION LAYER (Deterministic Selection)                                     │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │ NavigationDecider: priority + dependency + concurrency semantics        │ │
│  │                    → selects next best action(s)                        │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                              │                                                │
│                              ▼                                                │
│  GOVERNANCE LAYER (Pre-Execution Enforcement)                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │ ExecutionGovernor: idempotency check → authorization → policy gate      │ │
│  │                    → ONLY THEN execute                                   │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                              │                                                │
│                              ▼                                                │
│  TRACE LAYER (System of Record)                                               │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │ DecisionTracer: immutable record of context, rules, policies,           │ │
│  │                 alternatives considered, action taken, outcome          │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                               │
│  EVENT LAYER (Real-Time Adaptation)                                           │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │ EventSubscriber: data changes │ approvals │ failures │ timers │ policy  │ │
│  │                  updates → trigger reevaluation                          │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Core Principles

| Principle | Description |
|-----------|-------------|
| **Policy-Enforcing Navigation** | Navigates based on declared preconditions, business rules, and policy outcomes |
| **Decision Engine** | Deterministically selects the next best action(s) using explicit priority and semantics |
| **Governed Execution** | Applies idempotency, authorization, and policy enforcement BEFORE side effects |
| **Event-Driven Adaptation** | Events trigger reevaluation; execution paths adapt without modifying the graph |
| **Immutable Decision Traces** | Produces structured immutable traces for every meaningful decision |

### Core Algorithm

```
ORCHESTRATION LOOP:
  1. Await event (external, timer, completion, policy change)
  2. Correlate event to affected process instances
  3. For each instance → FULL REEVALUATION:
     a. Rebuild authoritative context (client config + domain + entity state + events)
     b. Evaluate ALL candidate nodes for eligibility
     c. Evaluate ALL candidate edges for eligibility
     d. Determine eligible navigation space
     e. Select next best action(s) deterministically
     f. Enforce governance before execution
     g. Execute and trace decision

DETERMINISTIC SELECTION:
  1. Build eligible space: {eligible nodes × traversable edges}
  2. Check for exclusive edges (exclusive wins)
  3. Apply dependency constraints (must execute A before B)
  4. Sort by priority (weight descending, then rank ascending)
  5. Apply concurrency semantics (parallel vs sequential)
  6. RECORD all alternatives considered
  7. SELECT best action(s) with explicit reasoning

GOVERNANCE ENFORCEMENT (Before Execution):
  1. IDEMPOTENCY CHECK: Has this exact action been executed?
  2. AUTHORIZATION CHECK: Is caller/context authorized?
  3. POLICY GATE: Final policy check before side effects
  4. IF any fails → ABORT and trace reason
  5. ONLY on full approval → Execute action
```

### Orchestrator Components

#### ContextAssembler

Builds the authoritative RuntimeContext from all available sources.

```java
public class ContextAssembler {
    // Load client configuration
    // Extract domain context from instance
    // Build entity state from accumulated execution
    // Assemble operational context (system state, obligations)
    // Include received event history

    RuntimeContext assemble(ProcessInstance instance, String tenantId);
    RuntimeContext addEvent(RuntimeContext context, ReceivedEvent event);
    RuntimeContext updateEntityState(RuntimeContext context, String nodeId, Object result);
}
```

#### EligibilityEvaluator

Evaluates all candidate nodes and edges to build the EligibleSpace.

```java
public class EligibilityEvaluator {
    // Uses NodeEvaluator and EdgeEvaluator from domain.engine
    // Builds cross-product of eligible nodes × traversable edges
    // Records evaluation details for tracing

    EligibleSpace evaluate(ProcessInstance instance, ProcessGraph graph, RuntimeContext context);
    EligibleSpace evaluateEntryNodes(ProcessGraph graph, RuntimeContext context);
    EligibleSpace reevaluateAfterEvent(ProcessInstance instance, ProcessGraph graph,
                                        RuntimeContext context, String eventType);
}
```

#### NavigationDecider

Implements NodeSelector port for deterministic selection.

```java
public class NavigationDecider implements NodeSelector {
    // Check for exclusive edges
    // Apply dependency constraints
    // Sort by priority
    // Check concurrency semantics
    // Record all alternatives

    NavigationDecision select(EligibleSpace eligibleSpace, ProcessInstance instance,
                              ProcessGraph graph);
}
```

**Selection Criteria:**

| Criteria | Description |
|----------|-------------|
| `SINGLE_OPTION` | Only one action available |
| `EXCLUSIVE` | Exclusive edge preempts all others |
| `HIGHEST_PRIORITY` | Selected based on priority weight |
| `PARALLEL` | Multiple actions can execute concurrently |
| `NO_OPTIONS` | No eligible actions (WAIT) |

#### InstanceOrchestrator

Orchestrates a single instance through the full evaluation-decision-governance-execution cycle.

```java
public class InstanceOrchestrator {
    OrchestrationResult orchestrate(ProcessInstance instance, ProcessGraph graph, String tenantId);
    OrchestrationResult orchestrateEntry(ProcessInstance instance, ProcessGraph graph,
                                          RuntimeContext context);
    OrchestrationResult reevaluateAfterEvent(ProcessInstance instance, ProcessGraph graph,
                                              RuntimeContext context, String eventType);
}
```

**OrchestrationResult Status:**

| Status | Description |
|--------|-------------|
| `EXECUTED` | Action was executed successfully |
| `WAITING` | No eligible actions, waiting for event |
| `BLOCKED` | Governance rejected the action |
| `FAILED` | Execution failed with error |
| `COMPLETED` | Process instance completed |

### Governance Layer

The ExecutionGovernor performs three checks before any action can produce side effects:

#### 1. Idempotency Check

Prevents duplicate execution of the same action.

```java
IdempotencyResult checkIdempotency(ProcessInstance instance, Node node, RuntimeContext context);
// Generates key: instanceId + nodeId + executionCount + contextHash
// Returns: PASSED, ALREADY_EXECUTED, or SKIPPED (if disabled)
```

#### 2. Authorization Check

Verifies the current principal has required permissions.

```java
AuthorizationResult checkAuthorization(ProcessInstance instance, Node node, RuntimeContext context);
// Checks: execute:{actionType}, action:{handlerRef}
// Returns: AUTHORIZED, UNAUTHORIZED, or SKIPPED (if disabled)
```

#### 3. Policy Gate Check

Final policy evaluation before producing side effects.

```java
PolicyGateResult checkPolicyGate(ProcessInstance instance, Node node, RuntimeContext context);
// Evaluates: node policy gates + runtime policies (operational state)
// Returns: PASSED, FAILED (with reasons), or SKIPPED (if disabled)
```

### Decision Traces

Every orchestration decision produces an immutable DecisionTrace:

```java
public record DecisionTrace(
    DecisionTraceId id,           // Unique identifier
    Instant timestamp,            // When decision was made
    ProcessInstanceId instanceId, // Which instance
    DecisionType type,            // NAVIGATION, EXECUTION, WAIT, BLOCKED

    ContextSnapshot context,      // Full context at decision time
    EvaluationSnapshot evaluation,// Nodes and edges evaluated
    DecisionSnapshot decision,    // Selection made with alternatives
    GovernanceSnapshot governance,// All governance check results
    OutcomeSnapshot outcome       // What happened next
)
```

**Trace Types:**

| Type | When Created |
|------|--------------|
| `NAVIGATION` | When selecting next action from eligible space |
| `EXECUTION` | When an action is executed |
| `WAIT` | When no actions are available |
| `BLOCKED` | When governance rejects execution |

### Configuration

```yaml
cpg:
  orchestrator:
    enabled: true
    event-queue-capacity: 10000
    evaluation-interval-ms: 5000
    governance:
      idempotency-enabled: true
      authorization-enabled: true
      policy-gate-enabled: true
    tracing:
      enabled: true
      persist-traces: true
      trace-retention-days: 90
```

---

## Infrastructure Adapters

### FEEL Expression Evaluator

**Class:** `KieFeelExpressionEvaluator`

Implements the `ExpressionEvaluator` port using the KIE (Drools) FEEL engine.

```java
public class KieFeelExpressionEvaluator implements ExpressionEvaluator {
    private final FEEL feel = FEEL.newInstance();

    public EvaluationResult evaluate(FeelExpression expression, Map<String, Object> context) {
        Object result = feel.evaluate(expression.expression(), context);
        return EvaluationResult.of(result);
    }
}
```

**FEEL Expression Examples:**
- `status = "APPROVED"` - Simple equality
- `amount > 1000 and region = "US"` - Compound conditions
- `date("2026-01-01") < today()` - Date comparisons
- `some x in items satisfies x.price > 100` - Quantified expressions

### DMN Policy Evaluator

**Class:** `DmnPolicyEvaluator`

Evaluates policy gates using DMN decision tables.

```java
public class DmnPolicyEvaluator implements PolicyEvaluator {
    private final DmnDecisionService dmnService;

    public PolicyResult evaluate(PolicyGate policyGate, Map<String, Object> context) {
        Map<String, Object> decision = dmnService.evaluate(
            policyGate.dmnDecisionRef(),
            context
        );
        // Map DMN output to PolicyResult
    }
}
```

### DMN Rule Evaluator

**Class:** `DmnRuleEvaluator`

Evaluates business rules using DMN decision tables.

```java
public class DmnRuleEvaluator implements RuleEvaluator {
    private final DmnDecisionService dmnService;

    public RuleResult evaluate(BusinessRule rule, Map<String, Object> context) {
        Map<String, Object> outputs = dmnService.evaluate(
            rule.dmnDecisionRef(),
            context
        );
        return RuleResult.success(rule, outputs);
    }
}
```

### In-Memory Repositories

For development and testing, in-memory implementations are provided:

| Repository | Implementation |
|------------|----------------|
| `ProcessGraphRepository` | `InMemoryProcessGraphRepository` |
| `ProcessInstanceRepository` | `InMemoryProcessInstanceRepository` |

### Event Publisher

**Class:** `InMemoryEventPublisher`

Simple in-memory event bus for development:

```java
public class InMemoryEventPublisher implements ProcessEventPublisher {
    private final List<ProcessEvent> events = new CopyOnWriteArrayList<>();
    private final List<Consumer<ProcessEvent>> listeners = new CopyOnWriteArrayList<>();

    public void publish(ProcessEvent event) {
        events.add(event);
        listeners.forEach(l -> l.accept(event));
    }
}
```

### Orchestrator Infrastructure

#### DefaultProcessOrchestrator

Event-driven implementation with background event loop using Java 21 virtual threads.

```java
public class DefaultProcessOrchestrator implements ProcessOrchestrator {
    private final BlockingQueue<OrchestrationEvent> eventQueue;
    private final ExecutorService executor; // Virtual threads

    public ProcessInstance start(ProcessGraph graph, RuntimeContext context);
    public void signal(OrchestrationEvent event);  // Queue event
    public void suspend(ProcessInstanceId instanceId);
    public void resume(ProcessInstanceId instanceId);
    public void cancel(ProcessInstanceId instanceId);
    public OrchestrationStatus getStatus(ProcessInstanceId instanceId);

    // Background event loop
    private void eventLoop() {
        while (running) {
            OrchestrationEvent event = eventQueue.poll(timeout);
            if (event != null) {
                processEvent(event);
            } else {
                performPeriodicEvaluation(); // Check timers, SLAs
            }
        }
    }
}
```

#### DefaultExecutionGovernor

Configurable governance with idempotency tracking.

```java
public class DefaultExecutionGovernor implements ExecutionGovernor {
    private final Map<String, String> executedActions; // Idempotency tracking

    public GovernanceResult enforce(ProcessInstance instance, Node node, RuntimeContext context) {
        var idempotency = checkIdempotency(instance, node, context);
        var authorization = checkAuthorization(instance, node, context);
        var policyGate = checkPolicyGate(instance, node, context);
        return GovernanceResult.combine(idempotency, authorization, policyGate);
    }

    public void recordExecution(ProcessInstance instance, Node node,
                                 RuntimeContext context, String executionId);
}
```

#### DefaultDecisionTracer

Persists decision traces with structured logging.

```java
public class DefaultDecisionTracer implements DecisionTracer {
    private final DecisionTraceRepository repository;

    public void record(DecisionTrace trace);
    public Optional<DecisionTrace> findById(DecisionTraceId id);
    public List<DecisionTrace> findByInstanceId(ProcessInstanceId instanceId);
    public Optional<DecisionTrace> findLatestByInstanceId(ProcessInstanceId instanceId);
    public long deleteOlderThan(Instant cutoff);
}
```

#### OrchestratorEventSubscriber

Bridges process events to orchestration events.

```java
public class OrchestratorEventSubscriber {
    // Subscribes to InMemoryEventPublisher
    // Converts ProcessEvent to OrchestrationEvent
    // Signals to ProcessOrchestrator
}
```

### Decision Trace Repository

**Interface:** `DecisionTraceRepository`

```java
public interface DecisionTraceRepository {
    void save(DecisionTrace trace);
    Optional<DecisionTrace> findById(DecisionTraceId id);
    List<DecisionTrace> findByInstanceId(ProcessInstanceId instanceId);
    List<DecisionTrace> findByType(DecisionType type);
    long deleteOlderThan(Instant cutoff);
}
```

**Implementation:** `InMemoryDecisionTraceRepository` - ConcurrentHashMap-based for development.

---

## Example: Employee Onboarding

### Process Overview

The system includes a complete employee onboarding process as a reference implementation.

```
    ┌─────────────────┐
    │ Offer Accepted  │ (Entry Point)
    └────────┬────────┘
             │ offer.signed = true
             ▼
    ┌─────────────────┐
    │Validate Documents│
    └────────┬────────┘
             │ documents.valid = true
             ▼
    ┌─────────────────┐
    │Background Check │
    │   Initiation    │
    └────────┬────────┘
             │ (parallel split)
    ┌────────┼────────────────────────┐
    │        │                        │
    ▼        ▼                        ▼
┌───────┐ ┌──────────┐ ┌─────────────────────┐
│Create │ │Generate  │ │Await Background     │
│User   │ │Employment│ │Check Result         │
│Accounts││Docs      │ │(event: bg.completed)│
└───┬───┘ └────┬─────┘ └──────────┬──────────┘
    │          │                  │
    ▼          ▼                  │
┌───────┐ ┌──────────┐           │
│Provision││Benefits │            │
│Equipment││Enrollment│           │
└───┬───┘ └────┬─────┘           │
    │          │                  │
    └──────────┼──────────────────┘
               │ (join: ALL)
               ▼
       ┌──────────────┐
       │ Final Review │
       └──────┬───────┘
              │ review.approved = true
              ▼
       ┌──────────────┐
       │  Onboarding  │ (Terminal)
       │   Complete   │
       └──────────────┘
```

### Process Components

**Nodes (12 total):**
- `offer-accepted` - Entry point when offer is signed
- `validate-documents` - Verify offer documents
- `background-check-init` - Start background investigation
- `create-user-accounts` - Provision AD/email accounts
- `provision-equipment` - Order laptop, badges, etc.
- `generate-employment-docs` - Create contracts, NDAs
- `benefits-enrollment` - Open benefits enrollment
- `await-background-check` - Wait for external result
- `final-review` - Management sign-off
- `onboarding-complete` - Success terminal
- `onboarding-cancelled` - Failure terminal

**Edges (18 total):**
- Sequential transitions with FEEL guards
- Parallel splits from background-check-init
- Join at final-review (JoinType.ALL)
- Cancellation paths for failures

### Building the Graph

```java
// Create the onboarding process graph
ProcessGraph graph = OnboardingProcessGraphBuilder.build();

// Start a new instance
ExecutionContext context = ExecutionContext.builder()
    .addClientContext("tenantId", "acme-corp")
    .addDomainContext("candidateId", "EMP-12345")
    .addDomainContext("offer", Map.of("signed", true))
    .build();

ProcessInstance instance = engine.startProcess(graph, context);

// Execute nodes as they become available
for (Node.NodeId activeId : instance.activeNodeIds()) {
    graph.findNode(activeId).ifPresent(node -> {
        NodeExecutionResult result = engine.executeNode(instance, graph, node);
        // Handle result...
    });
}
```

---

## Build and Test

### Maven Commands

| Command | Description |
|---------|-------------|
| `./mvnw clean compile` | Compile the project |
| `./mvnw test` | Run all tests |
| `./mvnw spring-boot:run` | Start the application |
| `./mvnw checkstyle:check` | Verify code style |
| `./mvnw dependency-check:check` | OWASP security scan |
| `./mvnw license:check` | Verify license compliance |

### Running Tests

```bash
# All tests
./mvnw test

# Specific test classes
./mvnw test -Dtest=NodeEvaluatorTest
./mvnw test -Dtest=EdgeEvaluatorTest
./mvnw test -Dtest=ProcessExecutionEngineTest
./mvnw test -Dtest=CompensationHandlerTest

# FEEL expression tests
./mvnw test -Dtest=KieFeelExpressionEvaluatorTest

# Integration tests
./mvnw test -Dtest=OnboardingProcessExecutionTest
```

### Test Coverage

The project requires 80% line coverage (enforced by JaCoCo):

| Package | Tests |
|---------|-------|
| `domain.engine` | 46 tests |
| `application.orchestration` | 11 tests (EligibilityEvaluator, NavigationDecider, InstanceOrchestrator) |
| `infrastructure.orchestration` | 23 tests (ExecutionGovernor, DecisionTracer, Integration) |
| `infrastructure.feel` | Expression evaluation tests |
| `interfaces.rest` | REST controller tests |
| `integration` | End-to-end onboarding test |

**Total: 200+ tests**

---

## Next Steps

### Completed Features

| Feature | Status |
|---------|--------|
| Process Execution Engine | Complete |
| Node/Edge Evaluation | Complete |
| FEEL Expression Support | Complete |
| DMN Policy/Rule Evaluation | Complete |
| **Process Orchestrator** | **Complete** |
| **Policy-Enforcing Navigation** | **Complete** |
| **Governance Layer** | **Complete** |
| **Decision Tracing** | **Complete** |
| REST API Layer | Complete |

### Phase 1: Production-Ready Persistence (High Priority)

| Task | Description |
|------|-------------|
| JPA Entities | Add JPA annotations to ProcessGraph, ProcessInstance, DecisionTrace |
| PostgreSQL Adapter | Implement `JpaProcessGraphRepository`, `JpaDecisionTraceRepository` |
| Flyway Migrations | Database schema versioning |
| Optimistic Locking | Handle concurrent instance updates |

### Phase 2: Event Infrastructure

| Task | Description |
|------|-------------|
| Kafka Publisher | `KafkaProcessEventPublisher` adapter |
| Kafka Orchestration Events | `KafkaOrchestrationEventSubscriber` |
| Event Consumers | Kafka listener for external events |
| Dead Letter Queue | Handle failed event processing |
| Event Sourcing | Consider event-sourced process instances |

### Phase 3: Human Tasks

| Task | Description |
|------|-------------|
| Task Inbox | User task assignment and inbox |
| Form Engine | Dynamic form rendering |
| Task Delegation | Delegation and escalation |
| SLA Monitoring | Task deadline tracking |

### Phase 4: Observability

| Task | Description |
|------|-------------|
| Metrics | Micrometer metrics for orchestration decisions |
| Tracing | Distributed tracing with OpenTelemetry |
| Decision Trace Analytics | Query and analyze decision traces |
| Dashboards | Grafana dashboards for monitoring |

### Phase 5: Advanced Features

| Task | Description |
|------|-------------|
| Process Versioning | Handle version upgrades mid-execution |
| Subprocess Support | Nested process execution |
| Timer Events | Scheduled and delay events |
| External Tasks | Long-running async task pattern |
| Multi-Tenant Governance | Per-tenant governance policies |

---

## License

Copyright 2026 ihelio. Licensed under the Apache License 2.0.
