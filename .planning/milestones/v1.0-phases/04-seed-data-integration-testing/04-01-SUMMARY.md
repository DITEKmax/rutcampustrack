---
phase: 04-seed-data-integration-testing
phase_legacy_id: 01.4-seed-data-integration-testing
plan: 01
status: complete
started: 2026-03-30
completed: 2026-03-30
tasks_completed: 4
tasks_total: 4
---

# Plan 04-01 (legacy 01.4-01) Summary

## Objective
Set up Testcontainers integration test infrastructure for auth-service and write comprehensive integration tests verifying all auth endpoints work end-to-end against real PostgreSQL and Redis, using seed data from Flyway V2 migration. Additionally, create a Gateway E2E verification script.

## What Was Built

### Task 1: Testcontainers Infrastructure
- Added Testcontainers + JUnit Jupiter dependencies to `auth-service/build.gradle.kts`
- Created `application-test.yml` with Testcontainers PostgreSQL + Redis, Flyway enabled
- Copied V1 + V2 Flyway migrations to auth-service test resources
- Created `AbstractIntegrationTest` base class with `@SpringBootTest`, `@Testcontainers`, `@DynamicPropertySource`

### Task 2: Auth Endpoint Integration Tests
- `AuthIntegrationTest` with 9 tests: login (admin, student, teacher), invalid credentials 401, nonexistent user 401, refresh token, refresh token rotation, logout 204, public-key PEM

### Task 3: OTP + Change Password Integration Tests
- `OtpIntegrationTest` with 6 tests: OTP request, verify, wrong code, nonexistent user, change password, wrong old password
- SQL setup scripts for telegram_id and password reset
- Redis key cleanup between tests to avoid 60s cooldown collision

### Task 4: Gateway E2E Verification Script
- `scripts/verify-gateway-e2e.sh` — curl-based script testing login, public-key, protected route with/without JWT through Gateway

## Key Files

### Created
- `services/auth-service/src/test/resources/application-test.yml`
- `services/auth-service/src/test/resources/db/migration/V1__baseline.sql`
- `services/auth-service/src/test/resources/db/migration/V2__seed_test_data.sql`
- `services/auth-service/src/test/java/ru/rutcampustrack/auth/integration/AbstractIntegrationTest.java`
- `services/auth-service/src/test/java/ru/rutcampustrack/auth/integration/AuthIntegrationTest.java`
- `services/auth-service/src/test/java/ru/rutcampustrack/auth/integration/OtpIntegrationTest.java`
- `services/auth-service/src/test/resources/sql/set-telegram-id.sql`
- `services/auth-service/src/test/resources/sql/clear-telegram-id.sql`
- `services/auth-service/src/test/resources/sql/reset-student-password.sql`
- `scripts/verify-gateway-e2e.sh`

### Modified
- `services/auth-service/build.gradle.kts` — Testcontainers dependencies
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/repository/UserRepository.java`
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/AuthService.java`

## Commits
- `91c373f` feat(01.4-01): Testcontainers infrastructure and Flyway test setup
- `85523c1` feat(01.4-01): Auth endpoint integration tests
- `f7a8973` test(01.4-01): OTP + change-password integration tests with Redis cleanup
- `f7470cd` feat(01.4-01): add Gateway E2E verification script

## Decisions
- `@DynamicPropertySource` used for Redis (GenericContainer not auto-detected by @ServiceConnection)
- Flyway migrations copied to auth-service test resources (not cross-module classpath)
- Redis OTP keys cleaned between tests via `@BeforeEach` to avoid 60s resend cooldown
- Gateway E2E is a manual verification script (services must be running)

## Self-Check: PASSED
- All 15 integration tests pass (`./gradlew.bat :services:auth-service:test`)
- All 11 gateway unit tests pass (`./gradlew.bat :services:api-gateway:test`)
- Gateway E2E script passes syntax check (`bash -n scripts/verify-gateway-e2e.sh`)
