# Codebase Concerns

**Analysis Date:** 2026-03-28

## Implementation Status

**Phase 0 is complete — scaffolding only.** Every service contains only a `@SpringBootApplication` main class. There are zero controllers, services, repositories, entities, DTOs (beyond enums), or REST interfaces implemented across the entire codebase.

**What exists (Phase 0 deliverables):**
- 6 Spring Boot application shells (main class + `application.yml`)
- 3 API contract modules with enums only (no DTO records, no REST interface definitions)
- 2 Flyway baseline migrations (`academic_db`: 12 tables, `schedule_db`: 2 tables)
- 2 gRPC proto definitions (`proto/academic.proto`, `proto/schedule.proto`)
- 7 JSON Schema event definitions in `event-schemas/`
- `LowercaseEnumConverter` + `EnumConverters` for Academic and Schedule services
- `ErrorResponse` record + `ResourceNotFoundException` in `academic-api-contract`
- Docker Compose for infrastructure (PostgreSQL x2, MongoDB, Redis, RabbitMQ)
- Python `requirements.txt` for notification-bot

**What is NOT implemented (Phases 1-6):**
- Auth Service: no JWT, no OTP, no login/logout/refresh endpoints
- API Gateway: no JWT validation filter, no CORS config, no rate limiting
- Academic Service: no entities, no repositories, no controllers, no gRPC server
- Schedule Service: no entities, no repositories, no controllers, no gRPC server
- Attendance Service: no MongoDB documents, no controllers, no report domain
- Notification Web: no WebSocket config, no STOMP endpoints
- Notification Bot: no Python source files (only `requirements.txt`)
- All 3 frontends: directories do not exist (`frontends/` not created)
- No tests whatsoever across all modules

## Technical Debt

**Duplicated enum converter pattern:**
- Issue: Schedule service's `EnumConverters` at `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/config/EnumConverters.java` duplicates the converter logic inline instead of reusing the `LowercaseEnumConverter` base class from Academic service.
- Files: `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/config/EnumConverters.java` vs `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/LowercaseEnumConverter.java`
- Impact: When Attendance service needs similar converters, there will be a third copy. Maintenance burden grows.
- Fix approach: Extract `LowercaseEnumConverter` into a shared module (e.g., `common-jpa`) or copy the base class into each service and have converters extend it consistently.

**ErrorResponse and ResourceNotFoundException only in academic-api-contract:**
- Issue: `ErrorResponse` and `ResourceNotFoundException` live in `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/exception/`. Other services will need these exact types but have no access without depending on `academic-api-contract`.
- Files: `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/exception/ErrorResponse.java`, `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/exception/ResourceNotFoundException.java`
- Impact: Schedule and Attendance services will either duplicate these classes or take an awkward cross-service dependency.
- Fix approach: Create a shared `common-contracts` module containing `ErrorResponse`, `ResourceNotFoundException`, and other cross-cutting types.

**No contract REST interfaces or DTOs defined yet:**
- Issue: The contract-first approach requires `*-api-contract` modules to define REST interface definitions and DTOs before implementation. Currently, contracts contain only enums.
- Files: `services/academic-service/academic-api-contract/`, `services/schedule-service/schedule-api-contract/`, `services/attendance-service/attendance-api-contract/`
- Impact: Not actual debt yet since implementation has not started, but these must be populated before implementing controllers.
- Fix approach: Define interfaces and DTOs in the contract modules as part of each phase's implementation.

**No TODO/FIXME/HACK/XXX comments in code:**
- The codebase is clean of inline debt markers (confirmed via grep). This is expected given Phase 0 is pure scaffolding.

## Security Concerns

**No authentication or authorization implemented:**
- Risk: All services are currently unprotected. Any endpoint added will be publicly accessible until Auth Service (Phase 1) is complete.
- Files: `services/auth-service/src/main/java/ru/rutcampustrack/auth/AuthApplication.java` (empty shell), `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/GatewayApplication.java` (empty shell)
- Current mitigation: None. No Spring Security configuration exists in any service.
- Recommendations: Phase 1 must be completed before any other service exposes REST endpoints. Gateway JWT filter is critical path.

**No JWT secret/key configuration:**
- Risk: `services/auth-service/src/main/resources/application.yml` defines `jwt.access-token-expiration` and `jwt.refresh-token-expiration` but has no JWT signing key/secret property.
- Files: `services/auth-service/src/main/resources/application.yml`
- Current mitigation: Auth service is not implemented yet.
- Recommendations: JWT signing key must be injected via environment variable, never committed. Add `jwt.secret` or asymmetric key path to `application.yml` with `${JWT_SECRET}` placeholder.

**No CORS configuration anywhere:**
- Risk: When frontends (React Mini App, Angular Web Panel) start calling the API, CORS will block all requests.
- Files: `services/api-gateway/src/main/resources/application.yml` (no CORS section)
- Current mitigation: None needed yet (no frontends exist).
- Recommendations: Add CORS configuration to API Gateway. Must restrict origins to known frontend domains.

**MongoDB has no authentication:**
- Risk: `docker-compose.yml` line 49-50 configures MongoDB without `MONGO_INITDB_ROOT_USERNAME`/`MONGO_INITDB_ROOT_PASSWORD`. Attendance service connects without credentials at `services/attendance-service/attendance-app/src/main/resources/application.yml` line 10.
- Files: `docker-compose.yml`, `services/attendance-service/attendance-app/src/main/resources/application.yml`
- Current mitigation: MongoDB is on private Docker network (`private_net`), not exposed to host.
- Recommendations: Add MongoDB authentication before production. Even for development, authenticated connections are better practice.

**Hardcoded default passwords in application.yml:**
- Risk: Default password `rct_dev_pass` is used as fallback across all services when environment variables are not set.
- Files: `services/academic-service/academic-app/src/main/resources/application.yml` (lines 11, 36), `services/schedule-service/schedule-app/src/main/resources/application.yml` (lines 11, 27), `services/attendance-service/attendance-app/src/main/resources/application.yml` (line 16), `services/notification-web/src/main/resources/application.yml` (line 12)
- Current mitigation: `.env` file is in `.gitignore`. Default values are for development only.
- Recommendations: Use Spring profiles (`application-prod.yml`) with no default values for production. Fail fast if secrets are not provided in production profile.

**RabbitMQ Management UI exposed on port 15672:**
- Risk: `docker-compose.yml` line 87 exposes RabbitMQ Management UI to the host with default credentials.
- Files: `docker-compose.yml`
- Current mitigation: Comment in docker-compose says "только для dev, убрать в production".
- Recommendations: Use a separate `docker-compose.prod.yml` or override that removes port mapping.

## Scalability Concerns

**No Redis caching implemented:**
- Problem: Academic service includes `spring-boot-starter-data-redis` dependency but has no `@Cacheable` annotations, cache config, or any Redis usage.
- Files: `services/academic-service/academic-app/build.gradle.kts` (line 17)
- Cause: Phase 0 only — implementation planned for Phase 2.
- Improvement path: Implement cache for frequently-read reference data (groups, subjects, semesters) in Phase 2.

**Schedule service missing Redis dependency:**
- Problem: Schedule service has no Redis dependency in `build.gradle.kts` despite likely needing caching for schedule lookups (hot path for attendance check-in).
- Files: `services/schedule-service/schedule-app/build.gradle.kts`
- Cause: Oversight or intentional deferral.
- Improvement path: Add `spring-boot-starter-data-redis` when implementing schedule queries.

**No database connection pooling configuration:**
- Problem: PostgreSQL services use default HikariCP settings. For a system serving 500-5000 students with concurrent attendance check-ins, default pool size (10) may be insufficient.
- Files: `services/academic-service/academic-app/src/main/resources/application.yml`, `services/schedule-service/schedule-app/src/main/resources/application.yml`
- Cause: Not configured yet.
- Improvement path: Add `spring.datasource.hikari.maximum-pool-size` tuning in later phases.

**Single MongoDB instance with no replica set:**
- Problem: `docker-compose.yml` runs a single MongoDB instance. MongoDB transactions require a replica set, and the attendance service will likely need transactions for atomic mark + status updates.
- Files: `docker-compose.yml` (lines 46-61)
- Cause: Development simplicity.
- Improvement path: Configure MongoDB replica set for production. Consider if transactions are needed for attendance writes.

## Dependency Risks

**Pinned dependency versions in api-contract modules:**
- Risk: API contract `build.gradle.kts` files pin specific versions of Spring Web (`6.2.1`), Spring HATEOAS (`2.4.1`), Jackson (`2.18.2`), Swagger (`2.2.22`), and Jakarta Validation (`3.1.0`) independently from the Spring Boot BOM.
- Files: `services/academic-service/academic-api-contract/build.gradle.kts`, `services/schedule-service/schedule-api-contract/build.gradle.kts`, `services/attendance-service/attendance-api-contract/build.gradle.kts`
- Impact: Version mismatches between api-contract modules and Spring Boot-managed app modules. If Spring Boot upgrades from 3.4.1, these pinned versions will lag behind and may cause classpath conflicts.
- Migration plan: Use a version catalog (`gradle/libs.versions.toml`) or apply `io.spring.dependency-management` to contract modules (even without Spring Boot plugin) to align versions.

**gRPC not yet integrated into Gradle build:**
- Risk: `proto/academic.proto` and `proto/schedule.proto` exist but no Gradle protobuf plugin is configured. The comment in `services/academic-service/academic-app/build.gradle.kts` line 33 says "gRPC будет добавлено в Фазе 2".
- Files: `build.gradle.kts` (root), `services/academic-service/academic-app/build.gradle.kts` (line 33)
- Impact: Proto files are documentation-only until the protobuf Gradle plugin is added. Generated Java stubs do not exist.
- Migration plan: Add `com.google.protobuf` Gradle plugin and `net.devh:grpc-spring-boot-starter` in Phase 2.

**Python notification-bot dependencies are version-pinned but no lockfile:**
- Risk: `services/notification-bot/requirements.txt` pins exact versions but there is no `requirements.lock` or `pip-compile` workflow.
- Files: `services/notification-bot/requirements.txt`
- Impact: Low risk currently since exact versions are pinned. Transitive dependencies are not locked.
- Migration plan: Consider using `pip-tools` or `poetry` for proper dependency locking.

## Missing Infrastructure

**No CI/CD pipeline:**
- There is no `.github/workflows/`, `Jenkinsfile`, or any CI configuration.
- Impact: No automated builds, no test execution, no lint checks on push/PR.
- Priority: Planned for Phase 6. Should be introduced earlier (at least `./gradlew build` check on PRs).

**No tests at all:**
- Zero test files exist across the entire codebase. Not a single unit test, integration test, or test configuration.
- Files: `services/**/src/test/` directories do not contain any `*.java` files.
- Impact: No safety net for refactoring or new feature development.
- Priority: High. Each phase should include tests for the code it produces.

**No centralized logging / monitoring:**
- No ELK, Loki, Prometheus, Grafana, or Micrometer configuration.
- Services use basic `logging.level` config with console output only.
- Files: All `application.yml` files (logging section)
- Impact: No observability in development or production.
- Priority: Planned for Phase 6.

**No Spring Boot Actuator on most services:**
- Only API Gateway includes `spring-boot-starter-actuator` (`services/api-gateway/build.gradle.kts` line 12). All other services lack health/metrics endpoints.
- Files: `services/auth-service/build.gradle.kts`, `services/academic-service/academic-app/build.gradle.kts`, `services/schedule-service/schedule-app/build.gradle.kts`, `services/attendance-service/attendance-app/build.gradle.kts`, `services/notification-web/build.gradle.kts`
- Impact: No `/actuator/health` for container health checks, no metrics export.
- Priority: Medium. Add Actuator to all services.

**No Dockerfiles for application services:**
- Docker Compose only defines infrastructure containers (PostgreSQL, MongoDB, Redis, RabbitMQ). No Dockerfiles exist for the Java services or Python bot.
- Impact: Services must be run locally via Gradle. No containerized deployment possible.
- Priority: Needed before Phase 6 (CI/CD), but useful earlier for integration testing.

**No Spring profiles for environment separation:**
- All services have a single `application.yml` with no `application-dev.yml`, `application-prod.yml`, or `application-test.yml`.
- Files: All `services/*/src/main/resources/application.yml`
- Impact: Cannot differentiate dev/test/prod configurations. Particularly risky for secrets management.
- Priority: Medium. Add at minimum a `prod` profile with strict secret requirements.

## Test Coverage Gaps

**Entire codebase is untested:**
- What's not tested: Everything. Zero test classes exist.
- Files: All `services/**/src/test/` directories are empty.
- Risk: Any code added in future phases has no regression safety net.
- Priority: High. Must be addressed starting from Phase 1.

---

*Concerns audit: 2026-03-28*
