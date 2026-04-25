# syntax=docker/dockerfile:1.7

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# M13 G25.5 — shared modules (auth-app зависит от shared-observability,
# shared-events, shared-web, shared-logback). До G25 multi-stage build
# никогда не запускался в CI (auth-service в dev compose отсутствовал).
COPY services/shared services/shared

COPY services/auth-service/auth-api-contract/build.gradle.kts services/auth-service/auth-api-contract/
COPY services/auth-service/auth-api-contract/src services/auth-service/auth-api-contract/src
COPY services/auth-service/auth-app/build.gradle.kts services/auth-service/auth-app/
COPY services/auth-service/auth-app/src services/auth-service/auth-app/src

# BuildKit cache mount: persists Gradle wrapper + dependency cache across builds.
# Requires BuildKit (DOCKER_BUILDKIT=1 locally; enabled by default in docker/build-push-action).
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew :services:auth-service:auth-app:bootJar --no-daemon -x test

# Stage 2: Extract layers
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /app
COPY --from=builder /workspace/services/auth-service/auth-app/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=extractor /app/dependencies/ ./
COPY --from=extractor /app/spring-boot-loader/ ./
COPY --from=extractor /app/snapshot-dependencies/ ./
COPY --from=extractor /app/application/ ./

USER app
EXPOSE 9090

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget -qO- http://localhost:9090/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
