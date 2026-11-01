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

# M13 G25.5 — shared modules (auth-app зависит от shared-observability,
# shared-events, shared-web, shared-logback). До G25 multi-stage build
# никогда не запускался в CI (auth-service в dev compose отсутствовал).
COPY services/shared services/shared

COPY services/auth-service/auth-api-contract/build.gradle.kts services/auth-service/auth-api-contract/
COPY services/auth-service/auth-api-contract/src services/auth-service/auth-api-contract/src
COPY services/auth-service/auth-app/build.gradle.kts services/auth-service/auth-app/
COPY services/auth-service/auth-app/src services/auth-service/auth-app/src

# Keep wrapper/dependencies warm per service without sharing daemon state.
# BuildKit serializes only writers using the same service cache.
RUN --mount=type=cache,id=rct-gradle-auth,target=/root/.gradle,sharing=locked \
    chmod +x gradlew && ./gradlew :services:auth-service:auth-app:bootJar --no-daemon -x test

# Stage 2: Extract layers
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /app
COPY --from=builder /workspace/services/auth-service/auth-app/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app \
    && addgroup -S -g 10004 auth-tls && addgroup app auth-tls
WORKDIR /app

COPY --from=extractor /app/dependencies/ ./
COPY --from=extractor /app/spring-boot-loader/ ./
COPY --from=extractor /app/snapshot-dependencies/ ./
COPY --from=extractor /app/application/ ./

# Keep volume copy-up ownership, but never distribute signing material in image layers.
# Runtime OpenSSL generation avoids the cold musl/JDK RSA entropy path.
RUN apk add --no-cache openssl && mkdir -p /keys && chown app:app /keys && chmod 755 /keys
COPY --chmod=755 services/auth-service/docker-entrypoint.sh /usr/local/bin/auth-entrypoint
ENV JWT_KEY_DIR=/keys

USER app
EXPOSE 9090

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget -qO- http://localhost:9090/actuator/health || exit 1

ENTRYPOINT ["/usr/local/bin/auth-entrypoint", "java", "org.springframework.boot.loader.launch.JarLauncher"]
