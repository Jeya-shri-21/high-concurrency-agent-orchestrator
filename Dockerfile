# syntax=docker/dockerfile:1

# ==============================================================================
# Stage 1: Build stage with OpenJDK 21 & Maven
# ==============================================================================
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy Maven POM and pre-fetch dependencies for layer caching
COPY pom.xml .
RUN mvn dependency:resolve-plugins dependency:resolve -B || true

# Copy application source and protobuf definitions
COPY src ./src

# Build production JAR (skipping unit tests during container image packaging)
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Stage 2: Minimal, secure runtime stage
# ==============================================================================
FROM eclipse-temurin:21-jre-jammy AS runner

# Install curl for container healthcheck and remove package manager caches
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Run as non-root user for container security
RUN groupadd -r orchestrator && useradd -r -g orchestrator -d /app -s /sbin/nologin orchestrator

WORKDIR /app

# Copy packaged Spring Boot executable JAR from builder stage
COPY --from=builder /build/target/high-concurrency-agent-orchestrator-*.jar /app/app.jar
RUN chown -R orchestrator:orchestrator /app

# Expose HTTP REST port (8080) and gRPC port (9090)
EXPOSE 8080 9090

# Default environment configuration
ENV JAVA_TOOL_OPTIONS="--enable-preview -XX:+UseG1GC -XX:MaxRAMPercentage=75.0" \
    SERVER_PORT=8080 \
    GRPC_SERVER_PORT=9090 \
    REDIS_HOST=redis \
    REDIS_PORT=6379 \
    NODE_ID=node-1

# Container healthcheck targeting Spring Boot root endpoint
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/ || exit 1

# Switch to unprivileged user
USER orchestrator

# Execute with exec form so PID 1 directly receives SIGTERM for graceful shutdown
ENTRYPOINT ["java", "--enable-preview", "-jar", "/app/app.jar"]
