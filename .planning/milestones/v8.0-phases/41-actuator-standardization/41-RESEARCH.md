# Phase 41: Actuator Standardization - Research

**Researched:** 2026-04-07
**Domain:** Spring Boot Actuator configuration (Spring Boot 3.4, Java 21)
**Confidence:** HIGH

## Summary

This phase adds `spring-boot-starter-actuator` to the four core Java backend services that currently lack it (auth-service, academic-service, schedule-service, attendance-service) and restricts exposed endpoints to `health` and `info` in all profiles. The work is purely additive configuration — no code logic changes, no new classes, no schema migrations.

Two services already have actuator in the monorepo: `api-gateway` and `notification-web`. The notification-web config is a reference implementation showing the correct `management:` YAML block. None of the four target services have the dependency or `management:` YAML yet.

The auth-service has Spring Security configured with a `SecurityFilterChain`. Actuator endpoints must be explicitly permitted in the security filter chain (`.requestMatchers("/actuator/**").permitAll()`) or they will return 401 rather than 200, breaking healthchecks. The other three services (academic, schedule, attendance) have no Spring Security — no filter chain update needed there.

**Primary recommendation:** Add `spring-boot-starter-actuator` to each target service's `build.gradle.kts`, add a `management:` block to each service's `application.yml` (restrict to `health,info`), add a production-profile override to expose only `health,info` (same values — this doubles as the `prod` contract), and permit `/actuator/**` in auth-service's SecurityFilterChain. Four files per service pattern, one extra file for auth-service.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MON-01 | spring-boot-starter-actuator added to auth, academic, schedule, attendance services | Dependency addition in 4 build.gradle.kts files — no version pin needed, managed by Spring BOM |
| MON-02 | Actuator exposes only health and info endpoints in production profile | management.endpoints.web.exposure.include=health,info in application.yml + production profile override |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-boot-starter-actuator | managed by Spring Boot BOM (3.4.x) | Exposes /actuator/health, /actuator/info | Standard Spring Boot observability starter, no version pin needed [VERIFIED: codebase — api-gateway uses it without version] |

### No Supporting Libraries Needed
This phase is config-only. No additional libraries beyond the actuator starter are required.

**Installation (per target service build.gradle.kts):**
```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

No version pin — version resolved by `io.spring.dependency-management` BOM already in place for all four services. [VERIFIED: codebase grep — all four services import Spring BOM via `id("io.spring.dependency-management")`]

## Architecture Patterns

### Recommended configuration block (application.yml)
Mirrors the pattern already used by `notification-web` (verified in codebase):

```yaml
# application.yml — default profile (dev/local): expose health and info
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: never
```

```yaml
# application-prod.yml — production profile override
# Redundant but explicit: locks down to health,info even if base config changes
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: never
```

Spring Boot 3.x default: only `health` is exposed. Adding `info` requires explicit inclusion. The `show-details: never` prevents leaking DB/Redis connection details in health responses. [ASSUMED — based on Spring Boot 3.x docs knowledge; the notification-web config in codebase uses the same pattern]

### auth-service: SecurityFilterChain permit

The auth-service SecurityFilterChain currently permits only specific paths. `/actuator/**` is not listed, so actuator requests would be intercepted by `JwtAuthenticationFilter` and return 401. [VERIFIED: codebase — SecurityConfig.java lines 29-39]

Required addition to `SecurityConfig.java`:
```java
.requestMatchers("/actuator/**").permitAll()
```

The other three services (academic-service, schedule-service, attendance-service) have no Spring Security dependency or `SecurityFilterChain` — actuator will be publicly accessible on their ports by default. [VERIFIED: codebase — no SecurityConfig.java found in those three services]

### Application-level profile structure

All four target services currently have a single `application.yml` with a `local` profile override section using Spring's multi-document YAML (`---`). The prod profile should either:
- Option A: add a `---` section activated by `spring.config.activate.on-profile: prod` inside the same `application.yml`
- Option B: create a separate `application-prod.yml`

Option B (separate file) is cleaner for CI/CD since the production config file can be clearly identified and audited. Phase 43 (docker-compose.prod.yml) will set `SPRING_PROFILES_ACTIVE=prod` — the actuator prod file needs to exist before that phase.

### Anti-Patterns to Avoid
- **Exposing `env`, `beans`, `heapdump` in any profile:** These endpoints expose secrets (env vars, passwords), class graph, and heap dumps. The success criteria explicitly forbid them. Spring Boot 3.x default exposes nothing except `health` over HTTP, so the only risk is over-permissive `include: *` configuration.
- **Setting `show-details: always`:** This causes the health endpoint to return DB connection strings, Redis host/port, and MongoDB URI — leaked via healthcheck responses.
- **Omitting the prod profile override:** Without an explicit prod override, a developer adding `include: "*"` to the base config would be silently included in production.
- **Forgetting the SecurityFilterChain permit in auth-service:** Results in 401 on `/actuator/health` — Docker healthchecks will fail at phase 43.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Health endpoint | Custom `/health` controller | spring-boot-starter-actuator | Actuator checks DB, Redis, MongoDB, RabbitMQ connections automatically |
| Info endpoint | Custom `/info` controller | spring-boot-starter-actuator with `info.*` properties | Actuator reads `info.*` from application.yml or build info |

## Common Pitfalls

### Pitfall 1: auth-service returns 401 on /actuator/health
**What goes wrong:** Docker healthcheck `curl http://localhost:9090/actuator/health` returns 401.
**Why it happens:** Spring Security's `JwtAuthenticationFilter` intercepts `/actuator/**` before actuator can respond. Only `/auth/login`, `/auth/refresh`, etc. are permitted without a JWT.
**How to avoid:** Add `.requestMatchers("/actuator/**").permitAll()` to the SecurityFilterChain in `SecurityConfig.java` before `.anyRequest().authenticated()`.
**Warning signs:** 401 response code from the health endpoint when tested manually.

### Pitfall 2: Sensitive endpoints accessible in default config
**What goes wrong:** Adding `include: "*"` or `include: health,info,env,beans` accidentally exposes secrets.
**Why it happens:** Developer copies config from a tutorial or adds endpoints incrementally.
**How to avoid:** Only ever set `include: health,info`. The prod override file acts as a final fence.

### Pitfall 3: Management port conflicts with gRPC or application port
**What goes wrong:** Setting `management.server.port` to a separate value causes the healthcheck URL to differ from the application URL.
**Why it happens:** Some configurations use a dedicated management port for isolation.
**How to avoid:** For this project, do NOT set `management.server.port`. Actuator should share the service's main port (9090–9093). The healthcheck in docker-compose can then use the same port as the application.

### Pitfall 4: Missing `application-prod.yml` before Phase 43
**What goes wrong:** Phase 43 sets `SPRING_PROFILES_ACTIVE=prod` but there is no prod actuator config — MON-02 is technically satisfied only in the running container if the default config is already correct, but the explicit prod file is the required artifact.
**Why it happens:** Phase 41 only creates default config and skips the prod override file.
**How to avoid:** Create `application-prod.yml` in each service with the actuator restriction, even though it duplicates the default.

## Code Examples

### Minimal management block (verified against notification-web pattern)
```yaml
# Source: services/notification-service/notification-app/src/main/resources/application.yml (lines 27-33)
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: never
```

### SecurityFilterChain permit pattern (auth-service)
```java
// Source: services/auth-service/src/main/java/ru/rutcampustrack/auth/config/SecurityConfig.java (adapted)
.authorizeHttpRequests(auth -> auth
    .requestMatchers(
        "/auth/login",
        "/auth/refresh",
        // ... existing permits ...
        "/actuator/**"   // ADD THIS LINE
    ).permitAll()
    .anyRequest().authenticated()
)
```

### Verifying endpoint exposure
After startup, these should return 200:
```
GET http://localhost:9090/actuator/health  → {"status":"UP"}
GET http://localhost:9090/actuator/info    → {}
GET http://localhost:9090/actuator         → {"_links":{"health":...,"info":...}}
GET http://localhost:9090/actuator/env     → 404 Not Found
GET http://localhost:9090/actuator/beans   → 404 Not Found
```

## Environment Availability

Step 2.6: SKIPPED — this phase adds no external dependencies. All four target services already run with their databases and messaging. The actuator dependency is resolved from Maven Central via Gradle, already cached in the monorepo's Gradle cache.

## Validation Architecture

nyquist_validation not set to false — section included.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers |
| Config file | none — each service uses `@SpringBootTest` with `@ActiveProfiles("test")` |
| Quick run command | `./gradlew.bat :services:auth-service:test --tests "*ActuatorIT"` |
| Full suite command | `./gradlew.bat :services:auth-service:test :services:academic-service:academic-app:test :services:schedule-service:schedule-app:test :services:attendance-service:attendance-app:test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| MON-01 | /actuator/health returns 200 UP on all 4 services | integration | `./gradlew.bat :services:auth-service:test --tests "*ActuatorIT"` (×4 services) | No — Wave 0 gap |
| MON-02 | /actuator/env returns 404 in prod profile | integration | Include in ActuatorIT with `@ActiveProfiles("prod")` | No — Wave 0 gap |

### Sampling Rate
- **Per task commit:** Run the specific service test: e.g., `./gradlew.bat :services:auth-service:test`
- **Per wave merge:** Full suite on all 4 services
- **Phase gate:** All 4 services actuator tests green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `services/auth-service/src/test/java/ru/rutcampustrack/auth/integration/ActuatorIT.java` — covers MON-01, MON-02 for auth-service
- [ ] `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/ActuatorIT.java` — covers MON-01, MON-02 for academic-service
- [ ] `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ActuatorIT.java` — covers MON-01, MON-02 for schedule-service
- [ ] `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/ActuatorIT.java` — covers MON-01, MON-02 for attendance-service

Note: Each ActuatorIT test needs `@SpringBootTest(webEnvironment = RANDOM_PORT)` with Testcontainers (same pattern as AbstractIntegrationTest in auth-service). Test profile must start with default config (not prod) to verify default restriction. A separate `@ActiveProfiles("prod")` test verifies MON-02.

## Security Domain

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | N/A — actuator health/info are intentionally public |
| V3 Session Management | no | Stateless services |
| V4 Access Control | yes | Permit only health,info; block env/heapdump/beans via exclusion |
| V5 Input Validation | no | Read-only GET endpoints, no input accepted |
| V6 Cryptography | no | No crypto changes |

### Known Threat Patterns
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Sensitive env var exposure via /actuator/env | Information Disclosure | `include: health,info` — env endpoint never enabled |
| Heap dump download via /actuator/heapdump | Information Disclosure | Same — heapdump never enabled |
| Internal service state via /actuator/beans | Information Disclosure | Same — beans never enabled |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Spring Boot 3.x default HTTP actuator exposure is `health` only (not all endpoints) | Architecture Patterns | Low — if default were `*`, prod restriction would be even more critical; behavior stays the same |
| A2 | `show-details: never` prevents DB connection info from appearing in health response body | Architecture Patterns | Low — if wrong, health body may leak internal hostnames in dev environment |
| A3 | academic-service, schedule-service, attendance-service have no Spring Security filter chain | Architecture Patterns, Pitfalls | Medium — if any service added security after last review, actuator would return 401 without the SecurityFilterChain fix |

## Sources

### Primary (HIGH confidence)
- Codebase grep: `management:` in application.yml files — `notification-web` reference implementation verified
- Codebase inspection: `build.gradle.kts` for all 4 target services — confirmed no actuator dependency present
- Codebase inspection: `SecurityConfig.java` in auth-service — confirmed `/actuator/**` is not in permitted matchers
- Codebase inspection: No SecurityConfig in academic/schedule/attendance services

### Secondary (MEDIUM confidence)
- Spring Boot 3.4 standard behavior for actuator endpoint exposure defaults

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — dependency is version-managed by BOM, verified in existing services
- Architecture: HIGH — reference implementation exists in notification-web; SecurityFilterChain gap verified by reading SecurityConfig.java
- Pitfalls: HIGH — auth-service security pitfall is a concrete gap found in the codebase, not speculative

**Research date:** 2026-04-07
**Valid until:** 2026-05-07 (stable Spring Boot API — low churn expected)
