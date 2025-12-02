# =============================================================================
# 10x-Apiary Dockerfile - Optimized Multi-Stage Build
# =============================================================================
# Build: docker build -t 10x-apiary:$(git rev-parse --short HEAD) .
# Run:   docker run --rm --env-file config.env \
#          -v /opt/apriary/storage:/app/storage \
#          -p 8080:8080 10x-apiary:$(git rev-parse --short HEAD)
# =============================================================================

# =============================================================================
# Stage 1: Builder
# Build uberjar and generate static assets (CSS, etc.)
# =============================================================================
FROM clojure:temurin-21-tools-deps-alpine AS builder

# Build-time arguments for versioning and metadata
ARG TAILWIND_VERSION=v3.4.17
ARG GIT_COMMIT_SHA=unknown
ARG BUILD_DATE=unknown

# Install build dependencies and clean up in single layer for optimal caching
# curl: Download Tailwind CSS binary
# rlwrap: REPL wrapper (Clojure tools dependency)
RUN apk add --no-cache curl rlwrap && \
    curl -L -o /usr/local/bin/tailwindcss \
      https://github.com/tailwindlabs/tailwindcss/releases/download/${TAILWIND_VERSION}/tailwindcss-linux-x64 && \
    chmod +x /usr/local/bin/tailwindcss

WORKDIR /app

# Copy dependency definitions first for better layer caching
# Changes to source code won't invalidate dependency resolution
COPY deps.edn .

# Copy source files required for uberjar build
COPY src ./src
COPY dev ./dev
COPY resources ./resources

# Build uberjar and preserve generated static assets
# The uberjar task:
# 1. Resolves dependencies (including Git-based Biff framework)
# 2. Compiles Clojure code
# 3. Generates CSS with Tailwind
# 4. Creates app.jar in target/jar/
# 5. Generates static assets in target/resources/public
#
# We preserve static assets because they're needed at runtime:
RUN clj -M:dev uberjar && \
    cp target/jar/app.jar . && \
    mkdir -p /tmp/public && \
    cp -r target/resources/public/* /tmp/public/ 2>/dev/null || true && \
    rm -rf target && \
    mkdir -p target/resources/public && \
    cp -r /tmp/public/* target/resources/public/ 2>/dev/null || true

# =============================================================================
# Stage 2: Runtime
# Minimal production image with JRE only
# =============================================================================
FROM eclipse-temurin:21-alpine

# Metadata labels following OCI image spec
LABEL org.opencontainers.image.title="10x-Apriary" \
      org.opencontainers.image.description="Apiary work summary automation application built with Biff/Clojure" \
      org.opencontainers.image.version="${GIT_COMMIT_SHA}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${GIT_COMMIT_SHA}" \
      org.opencontainers.image.authors="10x Development Team" \
      org.opencontainers.image.vendor="10x-Apriary" \
      org.opencontainers.image.url="https://github.com/yourusername/10x-apriary-cljs" \
      org.opencontainers.image.source="https://github.com/yourusername/10x-apriary-cljs"

# Install runtime dependencies and clean cache in same layer
# wget: Used for HEALTHCHECK
RUN apk add --no-cache wget && \
    rm -rf /var/cache/apk/*

WORKDIR /app

# Create non-root user for security
# UID/GID 1000 matches most Linux user accounts for easier volume permissions
RUN addgroup -g 1000 apriary && \
    adduser -D -u 1000 -G apriary apriary

# Copy application artifacts from builder stage
COPY --from=builder --chown=apriary:apriary /app/app.jar /app/app.jar
COPY --from=builder --chown=apriary:apriary /app/target/resources/public /app/target/resources/public
COPY --from=builder --chown=apriary:apriary /app/resources/public /app/resources/public

# Create storage directory for XTDB with proper permissions
# This will be mounted to host volume at runtime
RUN mkdir -p /app/storage && \
    chown -R apriary:apriary /app

# Switch to non-root user for security
# All subsequent commands and container runtime will use this user
USER apriary:apriary

# Declare volume for XTDB persistent storage
# Mount to host path: /opt/apriary/storage (recommended)
# Ensure host directory has ownership: chown -R 1000:1000 /opt/apriary/storage
VOLUME ["/app/storage"]

# Document exposed port (reverse proxy should map this)
EXPOSE 8080

# Environment variables for runtime configuration
# These can be overridden at runtime with docker run -e VAR=value
ENV BIFF_PROFILE=prod \
    HOST=0.0.0.0 \
    PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -XX:+UseContainerSupport"

# Health check configuration
# Checks if application responds on port 8080 every 30 seconds
# Allows 30 seconds for startup before first check
# Considers unhealthy after 3 consecutive failures
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ || exit 1

# Start application with optimized JVM flags
# -XX:-OmitStackTraceInFastThrow: Full stack traces for debugging
# -XX:+CrashOnOutOfMemoryError: Crash on OOM (allows container orchestrator to restart)
# -XX:MaxRAMPercentage=50.0: Use max 50% of container memory (2GB on Mikrus 3.5)
# -XX:+UseContainerSupport: Respect container resource limits
CMD ["/opt/java/openjdk/bin/java", \
     "-XX:-OmitStackTraceInFastThrow", \
     "-XX:+CrashOnOutOfMemoryError", \
     "-jar", \
     "app.jar"]
