# Contextualized Process Graph (CPG)

An enterprise-grade, action-oriented process execution engine built with Java 21, Spring Boot 3.4, and Domain-Driven Design. CPG orchestrates complex business workflows by describing what CAN be done next given the current context, with policy-enforcing navigation, governance enforcement, and immutable decision traces.

## Key Capabilities

- **Process Execution Engine** — Node/edge evaluation with FEEL expressions and DMN decision tables
- **Policy-Enforcing Orchestrator** — Autonomous navigation with idempotency, authorization, and policy gate enforcement
- **Immutable Decision Traces** — Complete audit trail of every orchestration decision
- **MCP Server** — Model Context Protocol server exposing 21 tools, 5 resources, and 3 prompts for AI client orchestration access
- **REST API** — Full REST API for process graph management, instance execution, and orchestration control
- **Event-Driven** — Event correlation and reactive edge traversal with reevaluation

## Quick Start

```bash
# Compile
./mvnw clean compile

# Run all tests (250 tests)
./mvnw test

# Start the application (port 8080)
./mvnw spring-boot:run

# Stop the application
# Press Ctrl+C in the terminal, or:
kill $(lsof -ti:8080)

# Check if the application is running
curl -s http://localhost:8080/actuator/health | python3 -m json.tool
```

## Common Commands

```bash
# Security scan (OWASP dependency check)
./mvnw dependency-check:check

# License audit
./mvnw license:check

# Linting (Google Java Style)
./mvnw checkstyle:check

# Generate SBOM
./mvnw cyclonedx:makeAggregateBom

# Test MCP server SSE endpoint
curl -s -N http://localhost:8080/sse
```

## Documentation

- [System Documentation](docs/SYSTEM_DOCUMENTATION.md) — Architecture, domain model, API reference, and examples
- [MCP Tools Guide](docs/MCP_TOOLS_GUIDE.md) — Guide for AI clients using MCP tools for workflow automation
- [Skills Reference](docs/SKILLS_REFERENCE.md) — Claude Code skills for interactive workflow automation
- [Events Reference](docs/EVENTS_REFERENCE.md) — Complete event catalog with payloads and flow diagrams

## Skills (Slash Commands)

CPG includes Claude Code skills for simplified workflow automation:

| Skill | Description |
|-------|-------------|
| `/orchestrate` | Interactive workflow runner - start and step through workflows |
| `/workflow-status` | Check workflow status with actionable suggestions |
| `/send-events` | Send one or more events to progress a workflow |

Example:
```bash
/orchestrate employee-onboarding
/workflow-status 87e5a6a7-ec80-418a-a16c-1f01b7203808
/send-events 87e5a6a7 BackgroundCheckCompleted EquipmentReady
```
