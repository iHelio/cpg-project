# Claude Desktop MCP Configuration

This directory contains configuration files for connecting Claude Desktop to the CPG MCP Server.

## Configuration Options

### Option 1: SSE Connection (Recommended)

Use this when the CPG MCP server is already running (via Docker or directly).

**File:** `claude_desktop_config_sse.json`

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

### Option 2: Docker Auto-Start

Use this to have Claude Desktop automatically start the container.

**File:** `claude_desktop_config.json`

```json
{
  "mcpServers": {
    "cpg-orchestration": {
      "command": "docker",
      "args": ["run", "--rm", "-p", "8080:8080", "--name", "cpg-mcp", "cpg-mcp:latest"]
    }
  }
}
```

## Installation

### macOS

1. Build the Docker image:
   ```bash
   cd /path/to/cpg
   docker build -t cpg-mcp .
   ```

2. Copy configuration to Claude Desktop:
   ```bash
   # Create config directory if it doesn't exist
   mkdir -p ~/Library/Application\ Support/Claude/

   # Copy the SSE config (recommended)
   cp mcp-config/claude_desktop_config_sse.json \
      ~/Library/Application\ Support/Claude/claude_desktop_config.json
   ```

3. Restart Claude Desktop

### Linux

1. Build the Docker image:
   ```bash
   docker build -t cpg-mcp .
   ```

2. Copy configuration:
   ```bash
   mkdir -p ~/.config/claude/
   cp mcp-config/claude_desktop_config_sse.json ~/.config/claude/claude_desktop_config.json
   ```

3. Restart Claude Desktop

### Windows

1. Build the Docker image:
   ```powershell
   docker build -t cpg-mcp .
   ```

2. Copy configuration:
   ```powershell
   mkdir -Force "$env:APPDATA\Claude"
   Copy-Item mcp-config\claude_desktop_config_sse.json "$env:APPDATA\Claude\claude_desktop_config.json"
   ```

3. Restart Claude Desktop

## Verification

After configuring Claude Desktop:

1. Start the MCP server:
   ```bash
   docker compose up -d
   ```

2. Open Claude Desktop and look for "cpg-orchestration" in the MCP servers list

3. Test by asking Claude:
   ```
   List all available process graphs using MCP
   ```

## Troubleshooting

### Server not connecting

1. Check if container is running:
   ```bash
   docker ps | grep cpg-mcp
   ```

2. Check container health:
   ```bash
   curl http://localhost:8080/actuator/health
   ```

3. View container logs:
   ```bash
   docker logs cpg-mcp
   ```

### Port already in use

Stop any existing container:
```bash
docker stop cpg-mcp
```

Or use a different port:
```json
{
  "mcpServers": {
    "cpg-orchestration": {
      "url": "http://localhost:9090/sse",
      "transport": "sse"
    }
  }
}
```

And run with: `docker run -p 9090:8080 cpg-mcp`
