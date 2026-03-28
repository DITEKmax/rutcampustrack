# Testing Patterns

**Analysis Date:** 2026-03-28

## Test Framework

**Runner:**
- JUnit 5 (JUnit Platform) -- configured globally in root `build.gradle.kts`
- All subprojects: `tasks.withType<Test> { useJUnitPlatform() }`

**Assertion Library:**
- Spring Boot Test (includes JUnit 5, Mockito, AssertJ, Hamcrest)
- Dependency in all `*-app` modules: `testImplementation("org.springframework.boot:spring-boot-starter-test")`
- JUnit Platform Launcher: `testRuntimeOnly("org.junit.platform:junit-platform-launcher")`

**Run Commands:**
```bash
# Run all tests (Windows)
.\gradlew.bat test

# Run all tests (Unix)
./gradlew test

# Run tests for a specific module
./gradlew :services:academic-service:academic-app:test

# Build (includes tests)
./gradlew build
```

## Test File Organization

**Location:**
- No test files exist yet. The project is in Phase 0 (scaffold/contracts/infrastructure).
- Expected location: `src/test/java/` mirroring `src/main/java/` package structure
- No `src/test/` directories have been created in any module

**Naming (prescribed by convention):**
- Unit tests: `{ClassName}Test.java`
- Integration tests: `{ClassName}IT.java` or `{ClassName}IntegrationTest.java`
- Place in same package as the class under test

**Expected Structure:**
```
services/{service}/{module}/
  src/
    main/java/ru/rutcampustrack/{service}/...
    test/java/ru/rutcampustrack/{service}/...
```

## Test Infrastructure

**Available Dependencies (already declared):**
- `spring-boot-starter-test` in all 6 app modules:
  - `services/academic-service/academic-app/build.gradle.kts`
  - `services/attendance-service/attendance-app/build.gradle.kts`
  - `services/auth-service/build.gradle.kts`
  - `services/api-gateway/build.gradle.kts`
  - `services/schedule-service/schedule-app/build.gradle.kts`
  - `services/notification-web/build.gradle.kts`

**What `spring-boot-starter-test` provides:**
- JUnit 5 (`org.junit.jupiter`)
- Mockito (`org.mockito`)
- AssertJ (`org.assertj.core.api`)
- Hamcrest (`org.hamcrest`)
- Spring Test (`@SpringBootTest`, `MockMvc`, `TestRestTemplate`)
- JSONPath and JsonAssert

**NOT yet declared (will be needed):**
- Testcontainers (for PostgreSQL, MongoDB, Redis, RabbitMQ integration tests)
- WireMock or MockServer (for HTTP mocking of inter-service calls)
- gRPC test support (for gRPC server/client testing)
- Contract testing framework (e.g., Spring Cloud Contract or Pact)

**Contract modules (`*-api-contract`) have NO test dependencies.** They are pure `java-library` modules with no test infrastructure.

## Coverage

**Current State:**
- **Zero test files exist** across the entire codebase
- **Zero test coverage** -- this is expected given Phase 0 status (scaffold only)
- No coverage tools configured (no JaCoCo plugin in `build.gradle.kts`)

**Services with test dependency declared (ready for tests):**

| Module | Test Dep | Test Files |
|--------|----------|------------|
| `services/academic-service/academic-app` | Yes | None |
| `services/attendance-service/attendance-app` | Yes | None |
| `services/auth-service` | Yes | None |
| `services/api-gateway` | Yes | None |
| `services/schedule-service/schedule-app` | Yes | None |
| `services/notification-web` | Yes | None |
| `services/academic-service/academic-api-contract` | No | None |
| `services/schedule-service/schedule-api-contract` | No | None |
| `services/attendance-service/attendance-api-contract` | No | None |

## Test Types Present

**Unit Tests:** None exist yet
**Integration Tests:** None exist yet
**E2E Tests:** None exist yet
**Contract Tests:** None exist yet

## Recommended Test Strategy (Based on Architecture)

Given the contract-first, microservice architecture documented in `CLAUDE.md` and `docs/architecture.md`, the following testing approach should be adopted:

**Unit Tests (per service):**
- Service layer logic (business rules, validation)
- Enum converters (`LowercaseEnumConverter` subclasses)
- DTO serialization/deserialization (especially `ErrorResponse`)
- Exception message formatting

**Integration Tests (per service):**
- Repository tests with real database (Testcontainers)
  - PostgreSQL for academic-service and schedule-service
  - MongoDB for attendance-service
  - Redis for auth-service and academic-service caching
- Controller tests with `@WebMvcTest` + `MockMvc`
- Flyway migration validation (schema correctness)
- RabbitMQ event publishing/consuming

**Contract Tests:**
- gRPC contract verification (proto files in `proto/` directory)
- REST API contract tests (contract interfaces in `*-api-contract` modules)
- Event schema validation against `event-schemas/*.json`

**E2E Tests:**
- API Gateway routing (full request flow through gateway to services)
- Multi-service scenarios (e.g., lesson start -> attendance marking -> notification)

## Test Conventions (Prescribed)

**Assertions:**
- Use AssertJ (`assertThat(...)`) as primary assertion library (comes with spring-boot-starter-test)
- Avoid mixing assertion styles within the same test class

**Mocking:**
- Use Mockito (`@Mock`, `@InjectMocks`, `@MockBean`) for unit tests
- Use `@MockBean` for Spring integration tests to replace specific beans
- Mock inter-service gRPC calls and RabbitMQ in integration tests

**Database Testing:**
- Use Testcontainers for real database instances (add dependency when writing tests)
- Never use H2 as a substitute for PostgreSQL (custom ENUM types are PostgreSQL-specific)
- Test Flyway migrations run cleanly on fresh database

**Test Data:**
- No fixtures or factories exist yet
- Recommended: create test data builders or factory classes per service

**Async Testing:**
- RabbitMQ event tests need async assertion support (e.g., Awaitility)
- Add `org.awaitility:awaitility` dependency when writing async tests

## Missing Test Infrastructure (Action Items)

**Add to root `build.gradle.kts` or individual modules:**
```kotlin
// JaCoCo for coverage reporting
plugins {
    jacoco
}

// Testcontainers (per module that needs it)
testImplementation("org.testcontainers:testcontainers")
testImplementation("org.testcontainers:junit-jupiter")
testImplementation("org.testcontainers:postgresql")      // academic-app, schedule-app
testImplementation("org.testcontainers:mongodb")          // attendance-app
testImplementation("org.testcontainers:rabbitmq")         // all event-producing services

// Awaitility for async tests
testImplementation("org.awaitility:awaitility")
```

---

*Testing analysis: 2026-03-28*
