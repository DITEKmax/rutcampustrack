# Deferred Items — Phase 12

## Pre-existing Test Failures (Out of Scope)

### CacheIntegrationTest — 2 failing tests

**Tests:**
- `getActiveSemester_secondCall_servedFromCache()`
- `activateSemester_invalidatesActiveSemesterCache()`

**Error:** `io.grpc.StatusRuntimeException: NOT_FOUND: Semester с isActive=true не найден`

**Status:** Pre-existing failures confirmed by running tests on original branch before 12-01 changes. These failures existed before V6 migration was introduced.

**Root cause (suspected):** The CacheIntegrationTest's `activateSemester_invalidatesActiveSemesterCache` test deactivates the active semester in step 1 of `activateSemester()` (via `deactivateAllActive()`), then tries to prime the cache with `getActiveSemester()`. There may be a test isolation issue with the `@DirtiesContext(AFTER_CLASS)` and Spring's ApplicationContext sharing between test classes, causing the semester to be deactivated when this test runs.

**Action required:** Investigate in a future phase or dedicated bug-fix. This is unrelated to LSSN-01/LSSN-02 requirements.
