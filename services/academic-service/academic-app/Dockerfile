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
COPY services/academic-service/academic-api-contract/build.gradle.kts \
     services/academic-service/academic-api-contract/
COPY services/academic-service/academic-api-contract/src \
     services/academic-service/academic-api-contract/src

# App module
COPY services/academic-service/academic-app/build.gradle.kts \
     services/academic-service/academic-app/
COPY services/academic-service/academic-app/src \
     services/academic-service/academic-app/src

RUN chmod +x gradlew && ./gradlew :services:academic-service:academic-app:bootJar --no-daemon -x test

# Stage 2: Extract layers
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /app
COPY --from=builder /workspace/services/academic-service/academic-app/build/libs/*.jar app.jar
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
EXPOSE 9091
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
