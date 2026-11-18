# syntax=docker/dockerfile:1

# ---------- 1. Build stage: compile the Spring Boot jar ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Download dependencies first so they're cached between builds (only re-runs when pom.xml changes).
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests run separately (mvnw test); skip them here to keep image builds fast.
RUN mvn -B -q -DskipTests package && cp target/wanderly-api-*.jar /workspace/app.jar

# ---------- 2. Runtime stage: small JRE image ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as an unprivileged user, never as root.
RUN addgroup -S wanderly && adduser -S wanderly -G wanderly \
    && mkdir -p /app/uploads && chown -R wanderly:wanderly /app
USER wanderly

COPY --from=build --chown=wanderly:wanderly /workspace/app.jar /app/app.jar

# Defaults for a container. Secrets (DB password, JWT_SECRET) are NOT baked in:
# pass them at runtime (docker run --env-file .env, or Render's Environment tab).
ENV SPRING_PROFILES_ACTIVE=supabase \
    PORT=8080 \
    UPLOAD_DIR=/app/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- "http://localhost:${PORT}/actuator/health/liveness" > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
