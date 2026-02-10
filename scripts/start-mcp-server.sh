#!/bin/bash
# =============================================================================
# CPG MCP Server - Startup Script
# =============================================================================
# Usage: ./scripts/start-mcp-server.sh [--build] [--detach]
# =============================================================================

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
CONTAINER_NAME="cpg-mcp"
IMAGE_NAME="cpg-mcp:latest"

# Parse arguments
BUILD=false
DETACH=false

for arg in "$@"; do
    case $arg in
        --build|-b)
            BUILD=true
            shift
            ;;
        --detach|-d)
            DETACH=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --build, -b   Rebuild the Docker image before starting"
            echo "  --detach, -d  Run in detached mode (background)"
            echo "  --help, -h    Show this help message"
            exit 0
            ;;
    esac
done

cd "$PROJECT_DIR"

# Build if requested or image doesn't exist
if [ "$BUILD" = true ] || ! docker image inspect "$IMAGE_NAME" &> /dev/null; then
    echo -e "${YELLOW}Building Docker image...${NC}"
    docker build -t "$IMAGE_NAME" .
    echo -e "${GREEN}✓ Image built${NC}"
fi

# Stop existing container
if docker ps -q -f name="$CONTAINER_NAME" | grep -q .; then
    echo -e "${YELLOW}Stopping existing container...${NC}"
    docker stop "$CONTAINER_NAME"
fi

# Remove existing container
if docker ps -aq -f name="$CONTAINER_NAME" | grep -q .; then
    docker rm "$CONTAINER_NAME"
fi

# Start container
echo -e "${YELLOW}Starting CPG MCP Server...${NC}"

if [ "$DETACH" = true ]; then
    docker run -d \
        --name "$CONTAINER_NAME" \
        -p 8080:8080 \
        --restart unless-stopped \
        "$IMAGE_NAME"

    echo ""
    echo -e "${GREEN}╔═══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║              CPG MCP Server Started (Background)              ║${NC}"
    echo -e "${GREEN}╚═══════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${BLUE}Endpoints:${NC}"
    echo "  • SSE:    http://localhost:8080/sse"
    echo "  • Health: http://localhost:8080/actuator/health"
    echo "  • REST:   http://localhost:8080/api/v1/orchestration"
    echo ""
    echo -e "${BLUE}Commands:${NC}"
    echo "  • Logs:   docker logs -f $CONTAINER_NAME"
    echo "  • Stop:   docker stop $CONTAINER_NAME"
    echo "  • Status: docker ps -f name=$CONTAINER_NAME"
    echo ""
else
    echo ""
    echo -e "${GREEN}╔═══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║                   CPG MCP Server Starting                     ║${NC}"
    echo -e "${GREEN}╚═══════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${BLUE}Endpoints:${NC}"
    echo "  • SSE:    http://localhost:8080/sse"
    echo "  • Health: http://localhost:8080/actuator/health"
    echo ""
    echo "Press Ctrl+C to stop"
    echo ""

    docker run --rm \
        --name "$CONTAINER_NAME" \
        -p 8080:8080 \
        "$IMAGE_NAME"
fi
