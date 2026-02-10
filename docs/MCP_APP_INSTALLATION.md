# CPG MCP App Installation Guide

This guide covers how to install and configure the CPG MCP Server for use with AI clients like Claude Desktop.

## Overview

The CPG MCP App exposes the Contextualized Process Graph engine via the Model Context Protocol (MCP), enabling AI assistants to:

- **21 Tools** — Process graph discovery, orchestration control, event-driven workflows, instance inspection
- **5 Resources** — Read-only access to graphs, instances, context, and events
- **3 Prompts** — Pre-built prompts for analysis, troubleshooting, and summaries

## Quick Start

### One-Line Installation

```bash
curl -fsSL https://raw.githubusercontent.com/iHelio/cpg/main/scripts/install.sh | bash
```

This script will:
1. Clone the repository
2. Build the Docker image
3. Configure Claude Desktop
4. Create startup scripts

### Manual Installation

#### Prerequisites

- Docker 20.10+
- Git (for cloning)

#### Step 1: Clone and Build

```bash
# Clone repository
git clone https://github.com/iHelio/cpg-project.git
cd cpg-project

# Build Docker image
docker build -t cpg-mcp .
```

#### Step 2: Start the Server

```bash
# Option A: Using Docker Compose (recommended)
docker compose up -d

# Option B: Direct Docker run
docker run -d -p 8080:8080 --name cpg-mcp cpg-mcp:latest

# Option C: Using the start script
./scripts/start-mcp-server.sh --detach
```

#### Step 3: Verify Installation

```bash
# Check health
curl http://localhost:8080/actuator/health

# Test SSE endpoint
curl -N http://localhost:8080/sse
```

## Claude Desktop Configuration

### macOS

1. Open Claude Desktop settings directory:
   ```bash
   open ~/Library/Application\ Support/Claude/
   ```

2. Create or edit `claude_desktop_config.json`:
   ```json
   {
     "mcpServers": {
       "cpg-orchestration": {
         "url": "http://localhost:8080/sse",
         "transport": "sse"
       }
     }
   }
   ```

3. Restart Claude Desktop

### Linux

```bash
mkdir -p ~/.config/claude
cat > ~/.config/claude/claude_desktop_config.json << 'EOF'
{
  "mcpServers": {
    "cpg-orchestration": {
      "url": "http://localhost:8080/sse",
      "transport": "sse"
    }
  }
}
EOF
```

### Windows

```powershell
mkdir -Force "$env:APPDATA\Claude"
@"
{
  "mcpServers": {
    "cpg-orchestration": {
      "url": "http://localhost:8080/sse",
      "transport": "sse"
    }
  }
}
"@ | Out-File -Encoding UTF8 "$env:APPDATA\Claude\claude_desktop_config.json"
```

## Configuration Options

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `CPG_MCP_ENABLED` | `true` | Enable/disable MCP server |
| `SERVER_PORT` | `8080` | HTTP server port |
| `SPRING_PROFILES_ACTIVE` | `default` | Spring profile |
| `JAVA_OPTS` | (tuned) | JVM options |

### Custom Port

To run on a different port:

```bash
# Docker
docker run -d -p 9090:8080 --name cpg-mcp cpg-mcp:latest

# Update Claude Desktop config
{
  "mcpServers": {
    "cpg-orchestration": {
      "url": "http://localhost:9090/sse",
      "transport": "sse"
    }
  }
}
```

### Docker Compose Profiles

```bash
# Production mode (default)
docker compose up -d

# Development mode with hot reload
docker compose --profile dev up cpg-mcp-dev
```

## Available MCP Capabilities

### Tools (21)

| Category | Tools |
|----------|-------|
| **Process Graph Discovery** | `list_process_graphs`, `get_process_graph`, `get_graph_nodes`, `get_graph_edges`, `get_node_details`, `get_edge_details`, `validate_process_graph` |
| **Orchestration Control** | `start_orchestration`, `step_orchestration`, `get_orchestration_status`, `suspend_orchestration`, `resume_orchestration`, `cancel_orchestration` |
| **Event-Driven Workflow** | `signal_event`, `get_required_events`, `get_available_events`, `send_event` |
| **Instance Inspection** | `list_process_instances`, `get_available_nodes`, `get_active_nodes`, `get_process_history` |

### Resources (5)

| URI Pattern | Description |
|-------------|-------------|
| `graph://published` | List all published process graphs |
| `graph://{graphId}` | Full process graph definition |
| `instance://{instanceId}` | Process instance state |
| `instance://{instanceId}/context` | Execution context |
| `instance://{instanceId}/events` | Event history |

### Prompts (3)

| Prompt | Description |
|--------|-------------|
| `analyze_process_graph` | Analyze graph structure and issues |
| `troubleshoot_instance` | Diagnose stuck/failed instances |
| `orchestration_summary` | Summarize orchestration progress |

## Testing the Connection

Once configured, test in Claude Desktop:

```
List all available process graphs using MCP tools
```

Expected response: Claude will use the `list_process_graphs` tool and return available workflows.

### Sample Workflow

```
1. Start the employee onboarding workflow for John Smith
2. Check what events are needed to progress
3. Send the BackgroundCheckCompleted event
4. Show the current orchestration status
```

## Troubleshooting

### Server Not Connecting

1. **Check if container is running:**
   ```bash
   docker ps | grep cpg-mcp
   ```

2. **Check container logs:**
   ```bash
   docker logs cpg-mcp
   ```

3. **Test health endpoint:**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

### Port Conflicts

```bash
# Find what's using port 8080
lsof -i :8080

# Stop conflicting process or use different port
docker run -d -p 9090:8080 cpg-mcp:latest
```

### Container Keeps Restarting

```bash
# Check logs for errors
docker logs --tail 100 cpg-mcp

# Check resource limits
docker stats cpg-mcp
```

### Claude Desktop Not Detecting MCP Server

1. Ensure the server is running BEFORE starting Claude Desktop
2. Check config file syntax (valid JSON)
3. Restart Claude Desktop completely (quit and reopen)
4. Check Claude Desktop logs for MCP connection errors

## Uninstallation

```bash
# Stop and remove container
docker stop cpg-mcp
docker rm cpg-mcp

# Remove image
docker rmi cpg-mcp:latest

# Remove Claude Desktop config (optional)
# macOS
rm ~/Library/Application\ Support/Claude/claude_desktop_config.json

# Linux
rm ~/.config/claude/claude_desktop_config.json
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Claude Desktop                          │
│                    (or other MCP client)                     │
└─────────────────────────┬───────────────────────────────────┘
                          │ SSE (Server-Sent Events)
                          │ http://localhost:8080/sse
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                   Docker Container                           │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              CPG MCP Server (Spring Boot)             │  │
│  │                                                       │  │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐     │  │
│  │  │  21 Tools   │ │ 5 Resources │ │  3 Prompts  │     │  │
│  │  └─────────────┘ └─────────────┘ └─────────────┘     │  │
│  │                                                       │  │
│  │  ┌─────────────────────────────────────────────────┐ │  │
│  │  │         CPG Orchestration Engine                │ │  │
│  │  │  • Process Graphs    • Event Correlation        │ │  │
│  │  │  • FEEL Expressions  • DMN Decisions            │ │  │
│  │  │  • Policy Gates      • Audit Traces             │ │  │
│  │  └─────────────────────────────────────────────────┘ │  │
│  └───────────────────────────────────────────────────────┘  │
│                          Port 8080                           │
└─────────────────────────────────────────────────────────────┘
```

## Next Steps

- [MCP Tools Guide](MCP_TOOLS_GUIDE.md) — Detailed tool documentation
- [Events Reference](EVENTS_REFERENCE.md) — Event catalog and flow diagrams
- [System Documentation](SYSTEM_DOCUMENTATION.md) — Full architecture reference
