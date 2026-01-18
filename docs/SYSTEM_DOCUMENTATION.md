# Contextualized Process Graph (CPG) System Documentation

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Project Structure](#project-structure)
4. [Domain Model](#domain-model)
5. [Process Execution Engine](#process-execution-engine)
6. [Infrastructure Adapters](#infrastructure-adapters)
7. [Example: Employee Onboarding](#example-employee-onboarding)
8. [Build and Test](#build-and-test)
9. [Next Steps](#next-steps)

---

## Overview

### Purpose

The Contextualized Process Graph (CPG) is an enterprise-grade process execution engine designed to orchestrate complex business workflows. Unlike traditional workflow engines that are state-centric, CPG is **action-oriented** - it describes what CAN be done next given the current context, rather than what IS true about the system.

### Key Features

- **Domain-Driven Design (DDD)**: Clean separation between domain logic and infrastructure
- **Hexagonal Architecture**: Ports and adapters pattern for maximum testability
- **FEEL Expressions**: Industry-standard Friendly Enough Expression Language for conditions
- **DMN Integration**: Decision Model and Notation for business rules and policies
- **Event-Driven**: Event correlation and reactive edge traversal
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
│   ┌──────────────────────────────────────────┐  │              │
│   │  Use Cases │ Graph Builders │ Handlers   │  │              │
│   └────────────────────┬─────────────────────┘  │              │
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
│   │  ┌──────────────────── PORTS ──────────────────────────┐ │ │
│   │  │ ExpressionEvaluator │ PolicyEvaluator │ RuleEvaluator│ │ │
│   │  │ ActionHandler │ EventPublisher │ Repositories        │ │ │
│   │  └──────────────────────────────────────────────────────┘ │ │
│   └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
                         │
┌────────────────────────┼───────────────────────────────────────┐
│                        ▼                       infrastructure  │
│   ┌─────────────────────────────────────────────────────────┐  │
│   │  KieFeelEvaluator │ DmnPolicyEvaluator │ DmnRuleEvaluator│  │
│   │  InMemoryRepositories │ InMemoryEventPublisher          │  │
│   └─────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

### Layer Responsibilities

| Layer | Package | Responsibility |
|-------|---------|----------------|
| **Domain** | `com.ihelio.cpg.domain` | Core business logic, entities, ports |
| **Application** | `com.ihelio.cpg.application` | Use cases, orchestration, graph builders |
| **Infrastructure** | `com.ihelio.cpg.infrastructure` | External adapters, persistence, messaging |
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
│   │   │   │   ├── expression/               # Expression evaluation port
│   │   │   │   ├── action/                   # Action handler port
│   │   │   │   ├── policy/                   # Policy evaluation port
│   │   │   │   ├── rule/                     # Rule evaluation port
│   │   │   │   ├── event/                    # Event handling
│   │   │   │   ├── repository/               # Persistence ports
│   │   │   │   └── exception/                # Domain exceptions
│   │   │   ├── application/
│   │   │   │   ├── handler/                  # Action handler registry
│   │   │   │   └── onboarding/               # Onboarding process builder
│   │   │   ├── infrastructure/
│   │   │   │   ├── feel/                     # KIE FEEL adapter
│   │   │   │   ├── dmn/                      # DMN decision service
│   │   │   │   ├── persistence/              # In-memory repositories
│   │   │   │   └── event/                    # Event publisher adapter
│   │   │   └── interfaces/
│   │   │       └── rest/                     # REST controllers
│   │   └── resources/
│   │       └── dmn/                          # DMN decision tables
│   └── test/
│       └── java/com/ihelio/cpg/
│           ├── domain/engine/                # Engine unit tests
│           ├── infrastructure/               # Adapter tests
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
| `infrastructure.feel` | Expression evaluation tests |
| `integration` | End-to-end onboarding test |

---

## Next Steps

### Phase 1: Production-Ready Persistence (High Priority)

| Task | Description |
|------|-------------|
| JPA Entities | Add JPA annotations to ProcessGraph, ProcessInstance |
| PostgreSQL Adapter | Implement `JpaProcessGraphRepository` |
| Flyway Migrations | Database schema versioning |
| Optimistic Locking | Handle concurrent instance updates |

### Phase 2: Event Infrastructure

| Task | Description |
|------|-------------|
| Kafka Publisher | `KafkaProcessEventPublisher` adapter |
| Event Consumers | Kafka listener for external events |
| Dead Letter Queue | Handle failed event processing |
| Event Sourcing | Consider event-sourced process instances |

### Phase 3: REST API

| Task | Description |
|------|-------------|
| Process Definition API | CRUD for ProcessGraph |
| Process Instance API | Start, query, suspend, resume |
| Task API | Human task management |
| Admin API | Process monitoring and management |

### Phase 4: Human Tasks

| Task | Description |
|------|-------------|
| Task Inbox | User task assignment and inbox |
| Form Engine | Dynamic form rendering |
| Task Delegation | Delegation and escalation |
| SLA Monitoring | Task deadline tracking |

### Phase 5: Observability

| Task | Description |
|------|-------------|
| Metrics | Micrometer metrics for execution |
| Tracing | Distributed tracing with OpenTelemetry |
| Logging | Structured logging with correlation IDs |
| Dashboards | Grafana dashboards for monitoring |

### Phase 6: Advanced Features

| Task | Description |
|------|-------------|
| Process Versioning | Handle version upgrades mid-execution |
| Subprocess Support | Nested process execution |
| Timer Events | Scheduled and delay events |
| External Tasks | Long-running async task pattern |

---

## License

Copyright 2026 ihelio. Licensed under the Apache License 2.0.
