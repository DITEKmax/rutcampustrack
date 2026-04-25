# syntax=docker/dockerfile:1.7

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# M13 G25.5 — shared modules (auth-app зависит от shared-observability,
# shared-events, shared-web, shared-logback). До G25 multi-stage build
# никогда не запускался в CI (auth-service в dev compose отсутствовал).
COPY services/shared services/shared

COPY services/auth-service/auth-api-contract/build.gradle.kts services/auth-service/auth-api-contract/
COPY services/auth-service/auth-api-contract/src services/auth-service/auth-api-contract/src
COPY services/auth-service/auth-app/build.gradle.kts services/auth-service/auth-app/
COPY services/auth-service/auth-app/src services/auth-service/auth-app/src

# BuildKit cache mount: persists Gradle wrapper + dependency cache across builds.
# Requires BuildKit (DOCKER_BUILDKIT=1 locally; enabled by default in docker/build-push-action).
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew :services:auth-service:auth-app:bootJar --no-daemon -x test

# Stage 2: Extract layers
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /app
COPY --from=builder /workspace/services/auth-service/auth-app/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=extractor /app/dependencies/ ./
COPY --from=extractor /app/spring-boot-loader/ ./
COPY --from=extractor /app/snapshot-dependencies/ ./
COPY --from=extractor /app/application/ ./

# M13 G25.9 — pre-create /keys с owner=app до volume mount.
# Docker при первом mount пустого named volume на существующий каталог
# в image копирует ownership/mode каталога в volume. Без этого
# JwtService.writeKeyToFile падает AccessDeniedException на чистом VPS
# (root:root mount, USER app не имеет write).
#
# M13 G25.15 — pre-generate RSA keys at build time через openssl.
# JDK KeyPairGenerator на alpine musl JDK 21 hang'ает на холодном GitHub
# Actions Azure runner: ВСЕ JDK PRNG provider'ы (NativePRNG/Blocking,
# DRBG, SHA1PRNG re-seed) под капотом упираются в /dev/random для
# entropy при RSA prime generation, игнорируя `-Djava.security.egd` flag
# и явный SecureRandom seed (G25.13/G25.14 не помогли). openssl genrsa
# использует /dev/urandom напрямую через libcrypto, что non-blocking.
#
# Build-time generation acceptable для CI/e2e (все контейнеры одного
# билда получают одинаковые ключи — это OK для e2e). Для prod на VPS
# named volume `jwt-keys` mount копирует image content при первом
# запуске, далее JwtService видит существующие keys и пропускает
# regeneration. Если VPS owner хочет уникальные prod keys — генерит
# вручную в volume перед первым `docker compose up`.
# JwtService.loadPrivateKey использует PKCS8EncodedKeySpec → ключ
# должен быть в PKCS#8 формате (BEGIN PRIVATE KEY), не PKCS#1
# (BEGIN RSA PRIVATE KEY). `openssl genrsa` выдаёт PKCS#1 → конвертируем
# через `openssl pkcs8 -topk8 -nocrypt`. Public key через `-pubout`
# уже выходит в SPKI/X.509 формате — совместим с X509EncodedKeySpec.
# kid.txt — JwtService.init читает именно `kid.txt` (см. JwtService:56).
RUN mkdir -p /keys && \
    apk add --no-cache openssl && \
    openssl genrsa -out /keys/private.key.pkcs1 3072 && \
    openssl pkcs8 -topk8 -nocrypt -in /keys/private.key.pkcs1 -out /keys/private.key && \
    openssl rsa -in /keys/private.key -pubout -out /keys/public.key && \
    rm /keys/private.key.pkcs1 && \
    head -c 4 /dev/urandom | od -A n -t x1 | tr -d ' \n' > /keys/kid.txt && \
    chown -R app:app /keys && \
    chmod 600 /keys/private.key /keys/kid.txt && \
    chmod 644 /keys/public.key && \
    apk del openssl

USER app
EXPOSE 9090

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget -qO- http://localhost:9090/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
