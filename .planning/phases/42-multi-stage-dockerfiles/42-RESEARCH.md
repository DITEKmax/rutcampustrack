# Phase 42: Multi-Stage Dockerfiles - Research

**Researched:** 2026-04-07
**Domain:** Docker multi-stage builds — Java (Spring Boot 3.4 / Gradle), Python (Aiogram 3), and Node.js (React/Angular/static HTML) frontends
**Confidence:** HIGH

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DOCK-01 | All 5 Java services have multi-stage Dockerfiles (build + runtime with layered JARs) | Spring Boot layered JAR is already active (layers.idx confirmed in auth-service JAR). Multi-stage pattern with eclipse-temurin:21-jdk-alpine builder + eclipse-temurin:21-jre-alpine runtime is standard. |
| DOCK-02 | notification-web Dockerfile upgraded to multi-stage build | notification-web = services/notification-service/notification-app. Has single-stage Dockerfile today (eclipse-temurin:21-jre-alpine, copies pre-built JAR). Uses LoaderImplementation.CLASSIC — layered extract still works with classic loader. |
| DOCK-03 | notification-bot Dockerfile uses python:3.12-slim (not Alpine) | Already uses python:3.12-slim. No change needed to base image. Requirement is essentially already satisfied; verification step confirms grpcio installs without error. |
| DOCK-04 | Frontend Dockerfiles produce nginx containers with optimized builds | 4 frontends: pwa (Vite/React), mini-app (Vite/React), web-panel (Angular 19), landing (static HTML only). Each needs a Dockerfile. nginx.conf files already exist in each frontend directory. |
</phase_requirements>

---

## Project Constraints (from CLAUDE.md)

- Java 21, Spring Boot 3.4, Gradle 8.12 monorepo
- 5 Java services: api-gateway, auth-service, academic-service/academic-app, schedule-service/schedule-app, attendance-service/attendance-app
- notification-web = services/notification-service/notification-app (Java, Spring Boot WebSocket STOMP)
- notification-bot = services/notification-bot (Python Aiogram 3, grpcio)
- 4 frontends: pwa (Vite/React), mini-app (Vite/React), web-panel (Angular), landing (static HTML)
- Packing structure: multi-module Gradle — api-contract modules are pure java-library, app modules are Spring Boot

---

## Summary

Phase 42 creates production-ready Dockerfiles for every service and frontend in the monorepo. The work splits cleanly into three patterns:

**Pattern A — Java multi-stage** (6 Dockerfiles: api-gateway, auth-service, academic-app, schedule-app, attendance-app, notification-app): build stage compiles the fat JAR with Gradle, then uses Spring Boot's built-in layered JAR extractor to separate dependencies, snapshot-dependencies, spring-boot-loader, and application classes into discrete Docker layers. The runtime stage copies only the extracted layers onto a minimal JRE image.

**Pattern B — Python** (1 Dockerfile: notification-bot): already uses python:3.12-slim. Only change is confirming grpcio installs without errors. Minor improvement: add non-root user.

**Pattern C — Frontend nginx** (4 Dockerfiles: pwa, mini-app, web-panel, landing): build stage runs `npm ci && npm run build`, runtime stage copies the `dist/` output plus the existing nginx.conf into nginx:1.27-alpine. Landing has no build step — static HTML only.

The most important planning subtlety is the Gradle monorepo build context: every Java service Dockerfile must set its Docker build context at the **repository root** so Gradle can resolve all subproject dependencies.

**Primary recommendation:** Use repository root as build context for all Java services. Use service/frontend directory as build context for Python and frontend Dockerfiles.

---

## Current State Audit

### Existing Dockerfiles (in main branch, not worktrees)

| Service | File Exists? | Current Pattern | Needs Change? |
|---------|-------------|-----------------|---------------|
| api-gateway | No | — | Create (DOCK-01) |
| auth-service | No | — | Create (DOCK-01) |
| academic-service/academic-app | No | — | Create (DOCK-01) |
| schedule-service/schedule-app | No | — | Create (DOCK-01) |
| attendance-service/attendance-app | No | — | Create (DOCK-01) |
| notification-service/notification-app | YES | Single-stage, copies pre-built JAR | Upgrade to multi-stage (DOCK-02) |
| notification-bot | YES | python:3.12-slim, correct | Verify only (DOCK-03) |
| frontends/pwa | No | — | Create (DOCK-04) |
| frontends/mini-app | No | — | Create (DOCK-04) |
| frontends/web-panel | No | — | Create (DOCK-04) |
| frontends/landing | No | — | Create (DOCK-04) |

### Current notification-bot Dockerfile [VERIFIED: codebase read]
```dockerfile
FROM python:3.12-slim
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
CMD ["python", "-m", "bot"]
```
Already uses python:3.12-slim. DOCK-03 is essentially already satisfied.

### Current notification-web Dockerfile [VERIFIED: codebase read]
```dockerfile
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 9094
ENTRYPOINT ["java", "-jar", "app.jar"]
```
Single-stage, copies pre-built JAR from host. Must be replaced with multi-stage.

### Spring Boot Layered JAR Status [VERIFIED: jar inspection]
`BOOT-INF/layers.idx` confirmed present in auth-service-0.1.0.jar. Four layers:
- `dependencies` → BOOT-INF/lib/
- `spring-boot-loader` → org/
- `snapshot-dependencies` → (empty in release builds)
- `application` → BOOT-INF/classes/, META-INF/

The `java -Djarmode=layertools -jar app.jar extract` command will work on all Spring Boot JARs in this project.

### notification-web CLASSIC Loader [VERIFIED: build.gradle.kts read]
`notification-app` sets `loaderImplementation = LoaderImplementation.CLASSIC` due to a BouncyCastle signed-JAR incompatibility with Spring Boot 3.2+ new loader. The CLASSIC loader still embeds `layers.idx` and supports `jarmode=layertools`. The multi-stage Dockerfile pattern is identical to other Java services — no special handling needed. [ASSUMED: CLASSIC loader still supports layertools extraction in Spring Boot 3.4]

### Services Using proto/ [VERIFIED: build.gradle.kts inspection]
All four multi-module app services use the protobuf Gradle plugin and reference `rootProject.file("proto")`:
- academic-service/academic-app — YES, has protobuf plugin
- schedule-service/schedule-app — YES, has protobuf plugin
- attendance-service/attendance-app — YES, has protobuf plugin
- notification-service/notification-app — YES (gRPC client in notification-api-contract)

api-gateway and auth-service do NOT use proto.

### Angular Output Path [VERIFIED: angular.json + dist inspection]
`frontends/web-panel/angular.json` has `"outputPath": "dist"` with the new `@angular/build:application` builder. The actual built output confirmed: `frontends/web-panel/dist/browser/` exists (not `dist/` directly). The correct Dockerfile COPY is `COPY --from=builder /app/dist/browser /usr/share/nginx/html`.

---

## Standard Stack

### Core Images

| Image | Version | Purpose | Why Standard |
|-------|---------|---------|--------------|
| eclipse-temurin:21-jdk-alpine | 21-jdk-alpine | Java build stage | Official Eclipse Temurin OpenJDK, Alpine keeps image small, JDK needed for Gradle compilation |
| eclipse-temurin:21-jre-alpine | 21-jre-alpine | Java runtime stage | JRE only (~100MB smaller than JDK), Alpine-based |
| python:3.12-slim | 3.12-slim | Python runtime | Project decision — grpcio has no musl wheels, slim (Debian) works |
| node:22-alpine | 22-alpine | Frontend build stage | LTS; Vite 7 and Angular 19 require Node >= 18; Node 22 is current LTS |
| nginx:1.27-alpine | 1.27-alpine | Frontend runtime | Already used in docker-compose.yml for dev volume mounts; consistent |

### Supporting Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Gradle Wrapper (`./gradlew`) | Java compilation inside build stage | Use wrapper, not system Gradle; wrapper is Gradle 8.12 |
| `java -Djarmode=layertools` | Extracts layers from Spring Boot fat JAR | Built into Spring Boot loader, no extra dependency |
| `npm ci` | Install Node deps reproducibly | Faster than `npm install`, uses package-lock.json exactly |
| `ng build` | Angular production build | Outputs to `dist/browser/` (with angular.json outputPath: dist) |
| `npm run build` (Vite) | Vite production build | Outputs to `dist/` |

---

## Architecture Patterns

### Pattern A: Java Multi-Stage with Layered JARs

Standard pattern for Spring Boot + Gradle in Docker. [CITED: https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#container-images.dockerfiles]

```dockerfile
# Build context: repository root (CRITICAL for Gradle multi-module projects)

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy Gradle wrapper and root config first (layer cache optimization)
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Copy this service's build file
COPY services/auth-service/build.gradle.kts services/auth-service/

# Copy source code
COPY services/auth-service/src services/auth-service/src

# Build the fat JAR (skip tests — tests run separately in CI)
RUN ./gradlew :services:auth-service:bootJar --no-daemon -x test

# Stage 2: Extract layers
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /app
COPY --from=builder /workspace/services/auth-service/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=extractor /app/dependencies/ ./
COPY --from=extractor /app/spring-boot-loader/ ./
COPY --from=extractor /app/snapshot-dependencies/ ./
COPY --from=extractor /app/application/ ./

EXPOSE 9090
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

**Notes on the pattern:**
- Three stages: builder → extractor → runtime
- `ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]` is the correct main class for Spring Boot 3.x default loader extracted layered JARs [ASSUMED: verify exact class name; check MANIFEST.MF Main-Class when JAR is available]
- `--no-daemon` is required inside Docker (no persistent daemon)
- The extractor can be merged into the builder stage but keeping it separate is cleaner

### Pattern A Variant: Services with API Contract Submodules + proto/

For academic-app, schedule-app, attendance-app, and notification-app:

```dockerfile
# Stage 1: Build (academic-service example)
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Copy all build files for this service's modules
COPY services/academic-service/academic-api-contract/build.gradle.kts \
     services/academic-service/academic-api-contract/
COPY services/academic-service/academic-app/build.gradle.kts \
     services/academic-service/academic-app/

# Copy proto files (required for gRPC stub generation)
COPY proto proto

# Copy source for both api-contract and app modules
COPY services/academic-service/academic-api-contract/src \
     services/academic-service/academic-api-contract/src
COPY services/academic-service/academic-app/src \
     services/academic-service/academic-app/src

RUN ./gradlew :services:academic-service:academic-app:bootJar --no-daemon -x test

# Stages 2 & 3 same as Pattern A above
```

**notification-app** additionally needs:
```dockerfile
COPY services/notification-service/notification-api-contract/build.gradle.kts \
     services/notification-service/notification-api-contract/
COPY services/notification-service/notification-api-contract/src \
     services/notification-service/notification-api-contract/src
```

### Pattern B: Python (notification-bot)

Existing Dockerfile is correct. Minor improvement — add non-root user for security:

```dockerfile
FROM python:3.12-slim

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Run as non-root
RUN useradd -r -s /bin/false botuser
USER botuser

COPY . .

CMD ["python", "-m", "bot"]
```

Note: `COPY . .` will include tests/ directory. A `.dockerignore` in the service directory should exclude `tests/`, `requirements-test.txt`, `pytest.ini`.

### Pattern C: Frontend nginx (React/Vite — pwa, mini-app)

```dockerfile
# Stage 1: Build
FROM node:22-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# Stage 2: Runtime
FROM nginx:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
```

### Pattern C Variant: Angular web-panel

Angular 19 with `@angular/build:application` and `outputPath: "dist"` outputs to `dist/browser/` (confirmed from existing build output):

```dockerfile
# Stage 1: Build
FROM node:22-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# Stage 2: Runtime
FROM nginx:1.27-alpine
# CRITICAL: Angular outputs to dist/browser/ not dist/
COPY --from=builder /app/dist/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
```

### Pattern C Variant: Static HTML landing (no build step)

Landing is static HTML — `dist/` already contains the final `index.html`. No build stage needed:

```dockerfile
FROM nginx:1.27-alpine
COPY dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

---

## Critical: Build Context for Java Services

**The monorepo Gradle build REQUIRES the repository root as Docker build context.**

Gradle resolves subproject dependencies from `settings.gradle.kts` at the root. If the build context is set to `services/auth-service/`, the gradlew wrapper, root `build.gradle.kts`, and `settings.gradle.kts` won't be accessible and the build will fail with "settings file not found."

**Correct docker-compose.yml build stanzas for Java services:**
```yaml
build:
  context: .        # repo root
  dockerfile: services/auth-service/Dockerfile
```

**Frontend Dockerfiles** can use the frontend directory as context — they have no cross-directory dependencies:
```yaml
build:
  context: ./frontends/pwa
  dockerfile: Dockerfile
```

**notification-bot** can also use its own directory as context:
```yaml
build:
  context: ./services/notification-bot
  dockerfile: Dockerfile
```

---

## Dockerfile Locations Summary

| Service | Dockerfile Path | Build Context | Proto Needed? | API Contract? |
|---------|----------------|---------------|---------------|---------------|
| api-gateway | services/api-gateway/Dockerfile | repo root | No | No |
| auth-service | services/auth-service/Dockerfile | repo root | No | No |
| academic-service/academic-app | services/academic-service/academic-app/Dockerfile | repo root | YES | YES |
| schedule-service/schedule-app | services/schedule-service/schedule-app/Dockerfile | repo root | YES | YES |
| attendance-service/attendance-app | services/attendance-service/attendance-app/Dockerfile | repo root | YES | YES |
| notification-service/notification-app | services/notification-service/notification-app/Dockerfile | repo root | YES | YES |
| notification-bot | services/notification-bot/Dockerfile | service dir | No | No |
| frontends/pwa | frontends/pwa/Dockerfile | frontend dir | No | No |
| frontends/mini-app | frontends/mini-app/Dockerfile | frontend dir | No | No |
| frontends/web-panel | frontends/web-panel/Dockerfile | frontend dir | No | No |
| frontends/landing | frontends/landing/Dockerfile | frontend dir | No | No |

---

## Service Port Map

| Service | EXPOSE |
|---------|--------|
| api-gateway | 8080 |
| auth-service | 9090 |
| academic-service/academic-app | 9091 |
| schedule-service/schedule-app | 9092 |
| attendance-service/attendance-app | 9093 |
| notification-service/notification-app | 9094 |
| frontends (all) | 80 |

---

## Gradle Task Names per Service

| Service | Gradle Task | JAR Output |
|---------|------------|------------|
| api-gateway | `:services:api-gateway:bootJar` | `services/api-gateway/build/libs/api-gateway-0.1.0.jar` |
| auth-service | `:services:auth-service:bootJar` | `services/auth-service/build/libs/auth-service-0.1.0.jar` |
| academic-app | `:services:academic-service:academic-app:bootJar` | `services/academic-service/academic-app/build/libs/academic-app-0.1.0.jar` |
| schedule-app | `:services:schedule-service:schedule-app:bootJar` | `services/schedule-service/schedule-app/build/libs/schedule-app-0.1.0.jar` |
| attendance-app | `:services:attendance-service:attendance-app:bootJar` | `services/attendance-service/attendance-app/build/libs/attendance-app-0.1.0.jar` |
| notification-app | `:services:notification-service:notification-app:bootJar` | `services/notification-service/notification-app/build/libs/notification-app-0.1.0.jar` |

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JAR layer extraction | Manual `unzip` or custom scripts | `java -Djarmode=layertools -jar app.jar extract` | Built into Spring Boot loader, handles BOOT-INF/layers.idx correctly |
| Dependency caching in Docker | Complex volume mounts or Gradle home caching | Copy build files first, then source (layer cache trick) | Standard pattern — Docker rebuilds only changed layers |
| Frontend build | Custom shell scripts | `npm ci && npm run build` | package.json defines the correct build command |
| Nginx config | Custom nginx.conf from scratch | Reuse existing `nginx.conf` files in each frontend dir | Already battle-tested, handles SPA fallback and cache headers |

**Key insight:** Spring Boot's layertools extractor handles all the complexity of splitting a fat JAR into cacheable Docker layers. Never extract manually.

---

## Common Pitfalls

### Pitfall 1: Wrong Build Context for Java Services
**What goes wrong:** `docker build services/auth-service/` — Gradle can't find settings.gradle.kts or other subprojects.
**Why it happens:** Gradle needs the root settings.gradle.kts to know about subproject structure.
**How to avoid:** Always use repo root as context. Place Dockerfile in service directory but reference it with `-f`.
**Warning signs:** `Could not find settings file` or `Project ':services:...' not found` in build output.

### Pitfall 2: Forgetting API Contract Source for Multi-Module Services
**What goes wrong:** academic-app, schedule-app, attendance-app reference their api-contract subprojects. If only the app source is copied, Gradle can't compile the contract dependency.
**Why it happens:** `implementation(project(":services:academic-service:academic-api-contract"))` requires the contract source.
**How to avoid:** Copy both api-contract and app sources in the Dockerfile.
**Warning signs:** `Could not resolve project :services:academic-service:academic-api-contract`.

### Pitfall 3: Forgetting proto/ for gRPC Services
**What goes wrong:** academic-app, schedule-app, attendance-app, notification-app generate gRPC stubs from `proto/` at the root. Missing proto/ breaks the `generateProto` Gradle task.
**Why it happens:** `sourceSets.main.proto.srcDir(rootProject.file("proto"))` requires the proto directory present at build time.
**How to avoid:** `COPY proto proto` in the Dockerfile for all 4 affected services. [VERIFIED: all 4 have protobuf plugin in build.gradle.kts]
**Warning signs:** `proto files not found` or `generateProto` task fails.

### Pitfall 4: Angular web-panel Output Path
**What goes wrong:** `COPY --from=builder /app/dist /usr/share/nginx/html` copies the parent `dist/` directory which only contains a `browser/` subdirectory, not HTML files directly.
**Why it happens:** Angular `@angular/build:application` builder with `outputPath: "dist"` creates `dist/browser/` for browser artifacts.
**How to avoid:** Use `COPY --from=builder /app/dist/browser /usr/share/nginx/html` [VERIFIED: dist/browser/ exists in the project].
**Warning signs:** Nginx serves directory listing or empty page.

### Pitfall 5: notification-app CLASSIC Loader — JarLauncher Class Name
**What goes wrong:** Using wrong ENTRYPOINT class name when running extracted layers from CLASSIC loader JAR.
**Why it happens:** Spring Boot 3.2+ moved the default loader to `org.springframework.boot.loader.launch.JarLauncher`. CLASSIC loader uses the older `org.springframework.boot.loader.JarLauncher`.
**How to avoid:** [ASSUMED: CLASSIC → old class `org.springframework.boot.loader.JarLauncher`]. The safest approach: inspect MANIFEST.MF `Main-Class` from the built notification-app JAR and use that class.
**Warning signs:** `ClassNotFoundException` at container startup.

### Pitfall 6: No .dockerignore — Huge Build Context for Java Services
**What goes wrong:** Large `node_modules`, `build/`, `.gradle/` directories get sent as build context, making `docker build` very slow.
**Why it happens:** No `.dockerignore` exists in the project yet.
**How to avoid:** Create a root `.dockerignore` excluding `build/`, `.gradle/`, `**/node_modules/`, `frontends/*/dist/`, `.planning/`.

### Pitfall 7: Landing — Don't Add a Build Stage
**What goes wrong:** Creating a Node.js build stage for a directory with no package.json.
**Why it happens:** Assuming all frontends have a Node.js build step.
**How to avoid:** Landing is static HTML. Its Dockerfile is a single-stage nginx image that copies the pre-built `dist/`.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker | Building all images | ✓ | 28.5.2 | — |
| Java 21 (JAVA_HOME) | Running `./gradlew` locally for verification | ✓ | ms-21.0.9 | — |
| Node.js | Frontend builds (inside Docker build stage; host is for reference) | ✓ | v24.14.0 host, node:22-alpine in Docker | — |
| npm | Frontend builds | ✓ | 11.9.0 | — |

No missing dependencies that block execution. Java services build entirely inside Docker using the Gradle wrapper.

---

## Validation Architecture

> `workflow.nyquist_validation` is absent from config.json — treat as enabled.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | docker build smoke tests (manual commands, no test framework files) |
| Config file | none |
| Quick run command | `docker build -f services/auth-service/Dockerfile -t rct-auth-test .` |
| Full suite command | Run all 11 `docker build` commands |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DOCK-01 | All 5 Java services build via multi-stage Dockerfile | smoke | `docker build -f services/{service}/Dockerfile -t rct-{service}-test .` x5 | ❌ no files needed |
| DOCK-02 | notification-web builds via multi-stage | smoke | `docker build -f services/notification-service/notification-app/Dockerfile -t rct-notification-web-test .` | ❌ no files needed |
| DOCK-03 | notification-bot uses python:3.12-slim, grpcio installs without error | smoke | `docker build -t rct-bot-test services/notification-bot/ && docker run --rm rct-bot-test python -c "import grpc; print('ok')"` | ❌ no files needed |
| DOCK-04 | All 4 frontend Dockerfiles produce nginx containers | smoke | `docker build -t rct-{frontend}-test frontends/{frontend}/` x4 | ❌ no files needed |

Verification for this phase is entirely `docker build` commands. No test framework files to create.

### Sampling Rate
- **Per task commit:** `docker build` the affected service, confirm exit code 0
- **Per wave merge:** Build all 11 images in sequence
- **Phase gate:** All 11 `docker build` commands succeed with no manual intervention

### Wave 0 Gaps
None — no test framework infrastructure needed.

---

## Security Domain

> `security_enforcement` is absent from config — treat as enabled.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | — |
| V3 Session Management | no | — |
| V4 Access Control | yes (minimal) | Run containers as non-root |
| V5 Input Validation | no | — |
| V6 Cryptography | no | — |

### Known Threat Patterns for Docker Images

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Running as root in container | Elevation of privilege | Alpine: `addgroup -S app && adduser -S app -G app`; Debian slim: `useradd -r` |
| Sensitive files in build context | Information disclosure | Root `.dockerignore` excludes .env, *.key files |
| Dev tools in runtime image | Increased attack surface | Multi-stage: JDK never in runtime stage |

**For this phase:** notification-bot currently runs as root. Adding a non-root USER is recommended but not a blocker for DOCK-03.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | CLASSIC loader (LoaderImplementation.CLASSIC) supports `jarmode=layertools` extraction in Spring Boot 3.4 | Pattern A / notification-web | If wrong, use `java -jar app.jar` directly (no layer optimization) |
| A2 | JarLauncher for CLASSIC extracted layers is `org.springframework.boot.loader.JarLauncher` (old class) | Pitfall 5 / notification-web ENTRYPOINT | If wrong, container fails at startup with ClassNotFoundException. Mitigate: check MANIFEST.MF Main-Class |
| A3 | Node 22-alpine supports Vite 7 and Angular 19 build toolchains | Standard Stack / frontends | If wrong, use node:20-alpine (prior LTS). Vite 7 requires Node >= 18. |

Angular output path (A3 previously) is now VERIFIED — `dist/browser/` confirmed.
Services using proto/ are now VERIFIED — all 4 multi-module app services confirmed.

---

## Open Questions

1. **notification-web CLASSIC loader ENTRYPOINT class name**
   - What we know: `LoaderImplementation.CLASSIC` is set due to BouncyCastle JAR signing incompatibility with Spring Boot 3.2+ new loader.
   - What's unclear: Whether extracted layers from CLASSIC loader should use `org.springframework.boot.loader.JarLauncher` (old) or `org.springframework.boot.loader.launch.JarLauncher` (new).
   - Recommendation: When writing the notification-app Dockerfile, check `MANIFEST.MF` Main-Class from a freshly built notification-app JAR. Use that class name exactly. If notification-app can't be built at research time, use `java -jar app.jar` as a safe fallback (works for both loaders, sacrifices layered caching benefit).

---

## Sources

### Primary (HIGH confidence)
- [VERIFIED: codebase read] — All existing Dockerfiles inspected, confirmed current state
- [VERIFIED: jar inspection] — `auth-service-0.1.0.jar` contains `BOOT-INF/layers.idx` with 4-layer structure
- [VERIFIED: codebase read] — `settings.gradle.kts` subproject structure confirmed
- [VERIFIED: codebase read] — `notification-app/build.gradle.kts` CLASSIC loader confirmed
- [VERIFIED: codebase read] — `docker-compose.yml` build contexts and nginx.conf files confirmed
- [VERIFIED: shell] — Docker 28.5.2 available, Node 24.14.0 available, JAVA_HOME set
- [VERIFIED: angular.json + dist inspection] — Angular web-panel outputs to `dist/browser/` confirmed
- [VERIFIED: build.gradle.kts inspection x4] — academic-app, schedule-app, attendance-app, notification-app all use protobuf plugin with proto/ srcDir

### Secondary (MEDIUM confidence)
- [CITED: https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#container-images.dockerfiles] — Spring Boot layered JAR multi-stage Dockerfile pattern

### Tertiary (LOW confidence)
- [ASSUMED] — JarLauncher class name for CLASSIC loader extracted layers

---

## Metadata

**Confidence breakdown:**
- Java multi-stage pattern: HIGH — Spring Boot layered JAR confirmed, Gradle structure fully mapped
- Python Dockerfile: HIGH — already correct, minimal change
- Frontend Dockerfiles: HIGH — nginx.conf files confirmed, Angular output path verified
- notification-web CLASSIC loader ENTRYPOINT: MEDIUM — one unresolved assumption on exact class name

**Research date:** 2026-04-07
**Valid until:** 2026-05-07 (stable domain — Docker, Spring Boot, nginx)
