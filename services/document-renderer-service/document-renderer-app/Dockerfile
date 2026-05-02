# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY proto proto
COPY services/shared services/shared

COPY services/document-renderer-service/document-renderer-app/build.gradle.kts \
     services/document-renderer-service/document-renderer-app/
COPY services/document-renderer-service/document-renderer-app/src \
     services/document-renderer-service/document-renderer-app/src

RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew :services:document-renderer-service:document-renderer-app:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-jammy AS extractor
WORKDIR /app
COPY --from=builder /workspace/services/document-renderer-service/document-renderer-app/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        libreoffice-writer \
        libreoffice-java-common \
        poppler-utils \
        fontconfig \
        fonts-dejavu \
        fonts-liberation \
        wget \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app app

WORKDIR /app

COPY --from=extractor /app/dependencies/ ./
COPY --from=extractor /app/spring-boot-loader/ ./
COPY --from=extractor /app/snapshot-dependencies/ ./
COPY --from=extractor /app/application/ ./

USER app
EXPOSE 9095 19095

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -qO- http://localhost:9095/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
