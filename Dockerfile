# Build stage
FROM gradle:8.7-jdk17 AS build

WORKDIR /app

# Copy gradle files first for better caching
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY core/build.gradle.kts ./core/
COPY api-mvc/build.gradle.kts ./api-mvc/
COPY api-webflux/build.gradle.kts ./api-webflux/
COPY infra/build.gradle.kts ./infra/

# Download dependencies
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY core/src ./core/src
COPY api-mvc/src ./api-mvc/src
COPY api-webflux/src ./api-webflux/src
COPY infra/src ./infra/src

# Build arguments for selecting module
ARG MODULE=api-mvc

# Build the application
RUN gradle :${MODULE}:bootJar --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

ARG MODULE=api-mvc

# Copy the built jar
COPY --from=build /app/${MODULE}/build/libs/*.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# JVM options
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
