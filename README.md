# Contextualized Process Graph (CPG)

An enterprise-grade, action-oriented process execution engine built with Java 21, Spring Boot 3.4, and Domain-Driven Design. CPG orchestrates complex business workflows by describing what CAN be done next given the current context, with policy-enforcing navigation, governance enforcement, and immutable decision traces.

## Key Capabilities

- **Process Execution Engine** — Node/edge evaluation with FEEL expressions and DMN decision tables
- **Policy-Enforcing Orchestrator** — Autonomous navigation with idempotency, authorization, and policy gate enforcement
- **Immutable Decision Traces** — Complete audit trail of every orchestration decision
- **MCP Server** — Model Context Protocol server exposing 11 tools, 5 resources, and 3 prompts for AI client orchestration access
- **REST API** — Full REST API for process graph management, instance execution, and orchestration control
- **Event-Driven** — Event correlation and reactive edge traversal with reevaluation

## Quick Start

```bash
# Compile
./mvnw clean compile

# Run all tests (250 tests)
./mvnw test

# Start the application
./mvnw spring-boot:run
```

## Documentation

See [docs/SYSTEM_DOCUMENTATION.md](docs/SYSTEM_DOCUMENTATION.md) for full system documentation including architecture, domain model, API reference, and examples.
