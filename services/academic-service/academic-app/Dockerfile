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

# M13 G25.5 — shared modules (academic-app транзитивно зависит от shared-*).
COPY services/shared services/shared

COPY services/auth-service/auth-api-contract/build.gradle.kts services/auth-service/auth-api-contract/
COPY services/auth-service/auth-api-contract/src services/auth-service/auth-api-contract/src

# API contract module
COPY services/academic-service/academic-api-contract/build.gradle.kts \
     services/academic-service/academic-api-contract/
COPY services/academic-service/academic-api-contract/src \
     services/academic-service/academic-api-contract/src

# App module
COPY services/academic-service/academic-app/build.gradle.kts \
     services/academic-service/academic-app/
COPY services/academic-service/academic-app/src \
     services/academic-service/academic-app/src

# Keep wrapper/dependencies warm per service without sharing daemon state.
# BuildKit serializes only writers using the same service cache.
RUN --mount=type=cache,id=rct-gradle-academic,target=/root/.gradle,sharing=locked \
    chmod +x gradlew && ./gradlew :services:academic-service:academic-app:bootJar --no-daemon -x test

# Stage 2: Extract layers
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /app
COPY --from=builder /workspace/services/academic-service/academic-app/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -u 10001 app -G app
WORKDIR /app

COPY --from=extractor /app/dependencies/ ./
COPY --from=extractor /app/spring-boot-loader/ ./
COPY --from=extractor /app/snapshot-dependencies/ ./
COPY --from=extractor /app/application/ ./

USER app
EXPOSE 9091

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:9091/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
