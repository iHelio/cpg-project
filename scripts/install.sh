#!/bin/bash
# =============================================================================
# CPG MCP Server - Installation Script
# =============================================================================
# Usage: curl -fsSL https://raw.githubusercontent.com/iHelio/cpg/main/scripts/install.sh | bash
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
REPO_URL="https://github.com/iHelio/cpg-project.git"
INSTALL_DIR="${CPG_INSTALL_DIR:-$HOME/.cpg-mcp}"
IMAGE_NAME="cpg-mcp"
IMAGE_TAG="latest"

echo -e "${BLUE}"
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║             CPG MCP Server - Installation Script              ║"
echo "║       Contextualized Process Graph for AI Orchestration       ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# Check prerequisites
check_prerequisites() {
    echo -e "${YELLOW}Checking prerequisites...${NC}"

    # Check Docker
    if ! command -v docker &> /dev/null; then
        echo -e "${RED}Error: Docker is not installed.${NC}"
        echo "Please install Docker: https://docs.docker.com/get-docker/"
        exit 1
    fi

    # Check if Docker daemon is running
    if ! docker info &> /dev/null; then
        echo -e "${RED}Error: Docker daemon is not running.${NC}"
        echo "Please start Docker and try again."
        exit 1
    fi

    echo -e "${GREEN}✓ Docker is installed and running${NC}"
}

# Clone or update repository
setup_repository() {
    echo -e "${YELLOW}Setting up repository...${NC}"

    if [ -d "$INSTALL_DIR" ]; then
        echo "Updating existing installation..."
        cd "$INSTALL_DIR"
        git pull origin main
    else
        echo "Cloning repository..."
        git clone "$REPO_URL" "$INSTALL_DIR"
        cd "$INSTALL_DIR"
    fi

    echo -e "${GREEN}✓ Repository ready at $INSTALL_DIR${NC}"
}

# Build Docker image
build_image() {
    echo -e "${YELLOW}Building Docker image...${NC}"

    cd "$INSTALL_DIR"
    docker build -t "${IMAGE_NAME}:${IMAGE_TAG}" .

    echo -e "${GREEN}✓ Docker image built: ${IMAGE_NAME}:${IMAGE_TAG}${NC}"
}

# Configure Claude Desktop
configure_claude_desktop() {
    echo -e "${YELLOW}Configuring Claude Desktop...${NC}"

    # Detect OS and set config path
    case "$(uname -s)" in
        Darwin)
            CONFIG_DIR="$HOME/Library/Application Support/Claude"
            ;;
        Linux)
            CONFIG_DIR="$HOME/.config/claude"
            ;;
        MINGW*|MSYS*|CYGWIN*)
            CONFIG_DIR="$APPDATA/Claude"
            ;;
        *)
            echo -e "${YELLOW}Unknown OS. Please manually configure Claude Desktop.${NC}"
            return
            ;;
    esac

    # Create config directory
    mkdir -p "$CONFIG_DIR"

    # Check for existing config
    CONFIG_FILE="$CONFIG_DIR/claude_desktop_config.json"

    if [ -f "$CONFIG_FILE" ]; then
        echo -e "${YELLOW}Existing Claude Desktop config found.${NC}"
        echo "Please manually add the CPG server to your config:"
        echo ""
        echo -e "${BLUE}Add this to your mcpServers:${NC}"
        echo '  "cpg-orchestration": {'
        echo '    "url": "http://localhost:8080/sse",'
        echo '    "transport": "sse"'
        echo '  }'
    else
        # Create new config
        cat > "$CONFIG_FILE" << 'EOF'
{
  "mcpServers": {
    "cpg-orchestration": {
      "url": "http://localhost:8080/sse",
      "transport": "sse"
    }
  }
}
EOF
        echo -e "${GREEN}✓ Claude Desktop configured at $CONFIG_FILE${NC}"
    fi
}

# Create start script
create_start_script() {
    echo -e "${YELLOW}Creating start script...${NC}"

    START_SCRIPT="$INSTALL_DIR/start-mcp.sh"

    cat > "$START_SCRIPT" << 'EOF'
#!/bin/bash
# Start CPG MCP Server

CONTAINER_NAME="cpg-mcp"
IMAGE_NAME="cpg-mcp:latest"

# Stop existing container if running
docker stop "$CONTAINER_NAME" 2>/dev/null || true
docker rm "$CONTAINER_NAME" 2>/dev/null || true

# Start new container
echo "Starting CPG MCP Server..."
docker run -d \
    --name "$CONTAINER_NAME" \
    -p 8080:8080 \
    --restart unless-stopped \
    "$IMAGE_NAME"

echo "CPG MCP Server started at http://localhost:8080"
echo "SSE endpoint: http://localhost:8080/sse"
echo ""
echo "View logs: docker logs -f $CONTAINER_NAME"
echo "Stop:      docker stop $CONTAINER_NAME"
EOF

    chmod +x "$START_SCRIPT"

    echo -e "${GREEN}✓ Start script created: $START_SCRIPT${NC}"
}

# Print summary
print_summary() {
    echo ""
    echo -e "${GREEN}"
    echo "╔═══════════════════════════════════════════════════════════════╗"
    echo "║                   Installation Complete!                       ║"
    echo "╚═══════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
    echo ""
    echo -e "${BLUE}Quick Start:${NC}"
    echo ""
    echo "  1. Start the MCP server:"
    echo -e "     ${YELLOW}$INSTALL_DIR/start-mcp.sh${NC}"
    echo ""
    echo "  2. Or use Docker Compose:"
    echo -e "     ${YELLOW}cd $INSTALL_DIR && docker compose up -d${NC}"
    echo ""
    echo "  3. Restart Claude Desktop"
    echo ""
    echo -e "${BLUE}MCP Endpoints:${NC}"
    echo "  • SSE:    http://localhost:8080/sse"
    echo "  • Health: http://localhost:8080/actuator/health"
    echo ""
    echo -e "${BLUE}Available Tools:${NC}"
    echo "  • 21 MCP Tools (orchestration, events, inspection)"
    echo "  • 5 MCP Resources (graphs, instances, context)"
    echo "  • 3 MCP Prompts (analyze, troubleshoot, summarize)"
    echo ""
    echo -e "${BLUE}Documentation:${NC}"
    echo "  • $INSTALL_DIR/docs/MCP_TOOLS_GUIDE.md"
    echo "  • $INSTALL_DIR/docs/EVENTS_REFERENCE.md"
    echo ""
}

# Main installation flow
main() {
    check_prerequisites
    setup_repository
    build_image
    configure_claude_desktop
    create_start_script
    print_summary
}

main "$@"
