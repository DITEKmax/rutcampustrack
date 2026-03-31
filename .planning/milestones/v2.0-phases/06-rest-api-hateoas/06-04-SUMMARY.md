---
phase: 06-rest-api-hateoas
plan: "04"
subsystem: academic-service
tags: [dashboard, hateoas, integration-tests, testcontainers, postgresql]
dependency_graph:
  requires: ["06-01", "06-02", "06-03"]
  provides: [dashboard-endpoint, integration-test-suite]
  affects: [academic-service]
tech_stack:
  added: [Testcontainers PostgreSQL, Spring Boot MockMvc, PostgreSQL implicit enum casts]
  patterns: [integration-test-with-testcontainers, native-sql-enum-cast-workaround]
key_files:
  created:
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/dashboard/DashboardController.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/dashboard/DashboardService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/UserContextFilter.java
    - services/academic-service/academic-app/src/main/resources/db/migration/V5__add_enum_casts.sql
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/RestApiIntegrationTest.java
  modified:
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/UserRepository.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/dashboard/DashboardService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java
decisions:
  - "Renamed RequestContextFilter to UserContextFilter to avoid bean name collision with Spring Boot WebMvcAutoConfiguration"
  - "Added V5 Flyway migration with PostgreSQL implicit casts (varchar->user_role etc.) instead of @JdbcTypeCode annotations"
  - "Used native SQL queries with CAST for UserRepository role-based queries due to PostgreSQL custom enum type restrictions"
  - "Removed @Transactional from integration test methods that check post-commit state to avoid Hibernate cache stale reads"
metrics:
  duration_minutes: 90
  completed_date: "2026-03-30"
  tasks_completed: 2
  tasks_total: 2
  files_created: 5
  files_modified: 3
---

# Phase 06 Plan 04: Dashboard Endpoint + Integration Tests Summary

Dashboard ADMIN-only stats endpoint (DASH-01) with 12-test integration suite covering all critical Academic Service REST API paths.

## Tasks Completed

| Task | Description | Commit |
|------|-------------|--------|
| 1 | DashboardController + DashboardService + prerequisite controllers from plans 01-03 | d3dd430 |
| 2 | 12-test RestApiIntegrationTest suite + enum cast migration + filter rename | e4e1f11 |

## What Was Built

**DashboardController + DashboardService (DASH-01):**
- `GET /academic/dashboard/stats` — admin-only aggregated statistics
- Returns `totalStudents`, `totalTeachers`, `totalGroups`, `activeGroups`, `activeSemesterName`
- HATEOAS self-link via `EntityModel<DashboardStatsResponse>`
- Protected by `@RequireRole({ADMIN})`

**Integration Test Suite (12 tests):**
- TEST-1: USER-01 admin creates user, receives login + initial password
- TEST-2: Validation error returns RFC 7807 with `fieldErrors`
- TEST-3: Student cannot create user, gets 403
- TEST-4: GSEM-02/03 semester creation + atomic activation (deactivates previous)
- TEST-5: GSEM-04 delete with wrong confirmation returns 400
- TEST-6: USER-06 student gets own profile via GET /me
- TEST-7: THRSH-04 threshold resolution most-specific-wins (group > global)
- TEST-8: HATEOAS pagination returns PagedModel with `_links`
- TEST-9: USER-04 headman revoke cascades to deactivate all assistants
- TEST-10: HW-03 homework completion toggle (mark + unmark)
- TEST-11: DASH-01 dashboard stats returns all 5 required fields
- TEST-12: Dashboard stats returns 403 for non-admin

All 12 tests pass against real PostgreSQL 16 via Testcontainers.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Spring BeanDefinitionOverrideException for requestContextFilter**
- **Found during:** Task 2 (test execution)
- **Issue:** Our `RequestContextFilter` class name produced a Spring bean named `requestContextFilter`, which collides with Spring Boot's auto-configured WebMvcAutoConfiguration bean of the same name
- **Fix:** Renamed class to `UserContextFilter` and its file accordingly
- **Files modified:** `security/UserContextFilter.java` (new), `security/RequestContextFilter.java` (deleted)
- **Commit:** e4e1f11

**2. [Rule 1 - Bug] PostgreSQL custom enum type reject JPA AttributeConverter string binding**
- **Found during:** Task 2 (test execution)
- **Issue:** PostgreSQL custom ENUM types (`user_role`, `account_status`, `subject_type`) reject implicit cast from `character varying`, causing `operator does not exist: user_role = character varying` and `column "role" is of type user_role but expression is of type character varying` errors
- **Fix:** Added V5 Flyway migration with `CREATE CAST (varchar AS user_role) WITH INOUT AS IMPLICIT` for all three custom enum types; changed role-based JPA queries in UserRepository to native SQL with explicit CAST
- **Files modified:** `V5__add_enum_casts.sql` (new), `UserRepository.java`, `UserService.java`, `DashboardService.java`
- **Commit:** e4e1f11

**3. [Rule 1 - Bug] Test assertions stale due to Hibernate first-level cache in @Transactional tests**
- **Found during:** Task 2 (test execution)
- **Issue:** Tests using `@Transactional` + `jdbcTemplate` assertions after MockMvc calls saw stale data because Hibernate's first-level cache wasn't flushed after bulk JPQL updates in services. Affected testAdminCreateAndActivateSemester and testHomeworkCompletionToggle
- **Fix:** Removed `@Transactional` from the affected test methods so each service call runs in its own committed transaction, making assertions against actual DB state accurate
- **Files modified:** `RestApiIntegrationTest.java`
- **Commit:** e4e1f11

**4. [Rule 1 - Bug] Wrong column name in homeworks INSERT in test**
- **Found during:** Task 2 (test execution)
- **Issue:** Test used `created_by` column but the actual schema column is `published_by`; also missing `updated_at` which has NOT NULL without default
- **Fix:** Corrected column name and added `updated_at` to the INSERT
- **Files modified:** `RestApiIntegrationTest.java`
- **Commit:** e4e1f11

## Known Stubs

None — all data is wired from real PostgreSQL via Testcontainers.

## API Gateway Verification

The API Gateway already had the academic-service route (`Path=/api/academic/**`, `StripPrefix=1`, `uri: http://academic-service:9091`) from prior setup — no changes needed.

## Self-Check

PASSED
- Task 1 commit d3dd430: confirmed via `git log --oneline`
- Task 2 commit e4e1f11: confirmed via `git log --oneline`
- DashboardController.java: created
- DashboardService.java: created
- RestApiIntegrationTest.java: created
- V5__add_enum_casts.sql: created
- UserContextFilter.java: created (replaces RequestContextFilter)
- All 12 tests: BUILD SUCCESSFUL confirmed
