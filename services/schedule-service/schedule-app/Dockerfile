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

RUN chmod +x gradlew && ./gradlew :services:schedule-service:schedule-app:bootJar --no-daemon -x test

# Stage 2: Extract layers
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /app
COPY --from=builder /workspace/services/schedule-service/schedule-app/build/libs/*.jar app.jar
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
EXPOSE 9092
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
