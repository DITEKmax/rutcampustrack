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

# M13 G25.5 — shared modules (attendance-app транзитивно зависит от shared-*).
COPY services/shared services/shared

# API contract module
COPY services/attendance-service/attendance-api-contract/build.gradle.kts \
     services/attendance-service/attendance-api-contract/
COPY services/attendance-service/attendance-api-contract/src \
     services/attendance-service/attendance-api-contract/src

# App module
COPY services/attendance-service/attendance-app/build.gradle.kts \
     services/attendance-service/attendance-app/
COPY services/attendance-service/attendance-app/src \
     services/attendance-service/attendance-app/src

# Keep wrapper/dependencies warm per service without sharing daemon state.
# BuildKit serializes only writers using the same service cache.
RUN --mount=type=cache,id=rct-gradle-attendance,target=/root/.gradle,sharing=locked \
    chmod +x gradlew && ./gradlew :services:attendance-service:attendance-app:bootJar --no-daemon -x test

# Stage 2: Extract layers
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /app
COPY --from=builder /workspace/services/attendance-service/attendance-app/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10003 app && adduser -S -u 10003 app -G app
WORKDIR /app

COPY --from=extractor /app/dependencies/ ./
COPY --from=extractor /app/spring-boot-loader/ ./
COPY --from=extractor /app/snapshot-dependencies/ ./
COPY --from=extractor /app/application/ ./

USER app
EXPOSE 9093

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget -qO- http://localhost:9093/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
