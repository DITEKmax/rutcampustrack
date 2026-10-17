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

# Proto files for gRPC stub generation
COPY proto proto

# M13 G25.5 — shared modules (schedule-app транзитивно зависит от shared-*).
COPY services/shared services/shared

# API contract module
COPY services/schedule-service/schedule-api-contract/build.gradle.kts \
     services/schedule-service/schedule-api-contract/
COPY services/schedule-service/schedule-api-contract/src \
     services/schedule-service/schedule-api-contract/src

# App module
COPY services/schedule-service/schedule-app/build.gradle.kts \
     services/schedule-service/schedule-app/
COPY services/schedule-service/schedule-app/src \
     services/schedule-service/schedule-app/src

# Keep wrapper/dependencies warm per service without sharing daemon state.
# BuildKit serializes only writers using the same service cache.
RUN --mount=type=cache,id=rct-gradle-schedule,target=/root/.gradle,sharing=locked \
    chmod +x gradlew && ./gradlew :services:schedule-service:schedule-app:bootJar --no-daemon -x test

# Stage 2: Extract layers
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /app
COPY --from=builder /workspace/services/schedule-service/schedule-app/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10002 app && adduser -S -u 10002 app -G app
WORKDIR /app

COPY --from=extractor /app/dependencies/ ./
COPY --from=extractor /app/spring-boot-loader/ ./
COPY --from=extractor /app/snapshot-dependencies/ ./
COPY --from=extractor /app/application/ ./

USER app
EXPOSE 9092

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:9092/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
