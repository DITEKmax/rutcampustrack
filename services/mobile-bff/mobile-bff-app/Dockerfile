# syntax=docker/dockerfile:1.7

# Stage 1: Build
# The Gradle protobuf plugin uses glibc-linked protoc/grpc-java generators.
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY proto proto
COPY services/shared services/shared

COPY services/mobile-bff/mobile-bff-api-contract/build.gradle.kts \
     services/mobile-bff/mobile-bff-api-contract/
COPY services/mobile-bff/mobile-bff-api-contract/src \
     services/mobile-bff/mobile-bff-api-contract/src
COPY services/mobile-bff/mobile-bff-app/build.gradle.kts \
     services/mobile-bff/mobile-bff-app/
COPY services/mobile-bff/mobile-bff-app/src \
     services/mobile-bff/mobile-bff-app/src

RUN --mount=type=cache,target=/root/.gradle \
    sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew :services:mobile-bff:mobile-bff-app:bootJar --no-daemon -x test

# Stage 2: Extract Spring Boot layers
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /app
COPY --from=builder /workspace/services/mobile-bff/mobile-bff-app/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 3: Non-root runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=extractor /app/dependencies/ ./
COPY --from=extractor /app/spring-boot-loader/ ./
COPY --from=extractor /app/snapshot-dependencies/ ./
COPY --from=extractor /app/application/ ./

USER app
EXPOSE 9080

HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD wget -qO- http://localhost:9080/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
