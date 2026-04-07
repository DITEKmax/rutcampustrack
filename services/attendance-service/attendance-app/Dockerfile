# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Proto files for gRPC stub generation
COPY proto proto

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

RUN chmod +x gradlew && ./gradlew :services:attendance-service:attendance-app:bootJar --no-daemon -x test

# Stage 2: Extract layers
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /app
COPY --from=builder /workspace/services/attendance-service/attendance-app/build/libs/*.jar app.jar
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
EXPOSE 9093
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
