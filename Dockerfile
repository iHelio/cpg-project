# =============================================================================
# CPG MCP Server - Multi-stage Docker Build
# =============================================================================
# Build:   docker build -t cpg-mcp .
# Run:     docker run -p 8080:8080 cpg-mcp
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: Build
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Install Maven
RUN apk add --no-cache maven

# Copy Maven wrapper and pom.xml first for dependency caching
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src
COPY checkstyle.xml .

# Build the application (skip tests for faster builds)
RUN ./mvnw clean package -DskipTests -B

# -----------------------------------------------------------------------------
# Stage 2: Runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Add labels for container metadata
LABEL org.opencontainers.image.title="CPG MCP Server"
LABEL org.opencontainers.image.description="Contextualized Process Graph - MCP Server for AI Orchestration"
LABEL org.opencontainers.image.version="0.0.1-SNAPSHOT"
LABEL org.opencontainers.image.vendor="iHelio"
LABEL org.opencontainers.image.source="https://github.com/iHelio/cpg-project"

# Create non-root user for security
RUN addgroup -g 1000 cpg && \
    adduser -u 1000 -G cpg -s /bin/sh -D cpg

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R cpg:cpg /app

USER cpg

# Expose MCP server port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
