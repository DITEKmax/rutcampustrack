# syntax=docker/dockerfile:1.7

# Stage 1: Build
# Note: must be a glibc-based image (not alpine/musl).
# protobuf-gradle-plugin downloads `protoc-gen-grpc-java-*-linux-x86_64.exe`,
# which is a glibc-linked ELF binary and fails to execute on musl (Alpine).
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY proto proto

# M13 G25.5 — shared modules (notification-app транзитивно зависит от shared-*).
COPY services/shared services/shared

COPY services/auth-service/auth-api-contract/build.gradle.kts services/auth-service/auth-api-contract/
COPY services/auth-service/auth-api-contract/src services/auth-service/auth-api-contract/src

# API contract module
COPY services/notification-service/notification-api-contract/build.gradle.kts \
     services/notification-service/notification-api-contract/
COPY services/notification-service/notification-api-contract/src \
     services/notification-service/notification-api-contract/src

# App module
COPY services/notification-service/notification-app/build.gradle.kts \
     services/notification-service/notification-app/
COPY services/notification-service/notification-app/src \
     services/notification-service/notification-app/src

# Keep wrapper/dependencies warm per service without sharing daemon state.
# BuildKit serializes only writers using the same service cache.
RUN --mount=type=cache,id=rct-gradle-notification,target=/root/.gradle,sharing=locked \
    chmod +x gradlew && ./gradlew :services:notification-service:notification-app:bootJar --no-daemon -x test

# Stage 2: Extract layers
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /app
COPY --from=builder /workspace/services/notification-service/notification-app/build/libs/*.jar app.jar
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
EXPOSE 9094

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:9094/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
