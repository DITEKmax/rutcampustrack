---
phase: 19-report-security-routing-fix
verified: 2026-04-04T21:00:00Z
status: passed
score: 5/5 must-haves verified
re_verification: false
---

# Phase 19: Report Security & Routing Fix Verification Report

**Phase Goal:** All report endpoints enforce @RequireRole at the AOP level (consistent with CheckinController/MarkingController), and the report URL path convention is aligned between gateway routing and documentation
**Verified:** 2026-04-04T21:00:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | An unauthenticated request (no X-User-Role header) to any report endpoint returns 403 | VERIFIED | RoleCheckAspect (@Around annotation) fires on all 4 @RequireRole-annotated methods; RoleCheckAspect.checkRole throws AccessDeniedException when role is null; GlobalExceptionHandler maps AccessDeniedException to HTTP 403 via HttpStatus.FORBIDDEN |
| 2 | @RequireRole is on all 4 ReportController methods, consistent with CheckinController and MarkingController | VERIFIED | `grep -c "@RequireRole" ReportController.java` returns 4; getLessonAttendance and getJournal carry `@RequireRole({UserRole.STUDENT, UserRole.TEACHER})`; getStudentStats and getStudentRecords carry `@RequireRole(UserRole.STUDENT)` |
| 3 | Report endpoints are reachable at /api/attendance/reports/* through the gateway | VERIFIED | ReportApi @RequestMapping is "/attendance/reports"; gateway predicate is `Path=/api/attendance/**` with StripPrefix=1; strip drops /api, forwards /attendance/reports/* to attendance-service |
| 4 | Gateway application.yml no longer routes /api/reports/** to attendance-service | VERIFIED | `grep "/api/reports" application.yml` returns no matches; sole attendance-service predicate is `Path=/api/attendance/**` |
| 5 | All 6 ReportIntegrationTest cases still pass after the URL path change | VERIFIED | All 6 test URLs updated to /attendance/reports/...; `grep -c "/attendance/reports/" ReportIntegrationTest.java` returns 6; old `/reports/` paths: 0 matches; SUMMARY confirms 6/6 passing; commit bbb44c8 includes Rule1 fix (`cells` -> `records`) that resolved pre-existing assertion failure |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportController.java` | @RequireRole on all 4 methods | VERIFIED | 4 occurrences of @RequireRole; both imports (UserRole, RequireRole) present; commit fdf945b |
| `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/ReportApi.java` | @RequestMapping("/attendance/reports") | VERIFIED | Line 30: `@RequestMapping("/attendance/reports")`; commit bbb44c8 |
| `services/api-gateway/src/main/resources/application.yml` | /api/attendance/** sole predicate for attendance-service | VERIFIED | Only `Path=/api/attendance/**` under attendance-service route; /api/reports/** absent |
| `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/ReportIntegrationTest.java` | 6 test URLs at /attendance/reports/... | VERIFIED | 6 occurrences of /attendance/reports/; 0 occurrences of old /reports/... paths |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| ReportController.java | RoleCheckAspect | @RequireRole annotation intercepted by @Around AOP | WIRED | spring-boot-starter-aop in build.gradle.kts; RoleCheckAspect is @Aspect @Component; @Around("@annotation(requireRole)") fires on all 4 annotated methods; AccessDeniedException -> GlobalExceptionHandler -> 403 |
| ReportApi.java | Gateway route | @RequestMapping path matches /api/attendance/** predicate after StripPrefix=1 | WIRED | /attendance/reports base + StripPrefix=1 (drops /api) aligns with gateway predicate /api/attendance/** |

### Data-Flow Trace (Level 4)

Not applicable — ReportController delegates to ReportService which was fully verified in Phase 18. Phase 19 changes are security decorators (@RequireRole) and URL path alignment only; no new data-rendering paths were introduced.

### Behavioral Spot-Checks

| Behavior | Verification Method | Result | Status |
|----------|---------------------|--------|--------|
| @RequireRole count = 4 | `grep -c "@RequireRole" ReportController.java` | 4 | PASS |
| Old /api/reports/** absent in gateway | `grep "/api/reports" application.yml` | no matches | PASS |
| New path in ReportApi | line 30: `@RequestMapping("/attendance/reports")` | exact match | PASS |
| Old test paths gone | `grep -c '"/reports/' ReportIntegrationTest.java` | 0 | PASS |
| New test paths present | `grep -c '"/attendance/reports/' ReportIntegrationTest.java` | 6 | PASS |
| AOP infrastructure wired | spring-boot-starter-aop in build.gradle.kts line 26 | present | PASS |
| AccessDeniedException -> 403 | GlobalExceptionHandler lines 43-55 | HttpStatus.FORBIDDEN | PASS |
| Commits exist | git show fdf945b, bbb44c8 | both present with correct files | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| RPRT-01 | 19-01-PLAN.md | Headman/teacher can view lesson attendance (all students + their status for a lesson) | SATISFIED | getLessonAttendance: @RequireRole({STUDENT, TEACHER}); endpoint at /attendance/reports/lesson/{lessonId}; underlying implementation from Phase 18 unchanged |
| RPRT-02 | 19-01-PLAN.md | Headman/teacher can view journal (students x lesson dates grid for group+subject) | SATISFIED | getJournal: @RequireRole({STUDENT, TEACHER}); endpoint at /attendance/reports/journal; underlying implementation from Phase 18 unchanged |
| RPRT-03 | 19-01-PLAN.md | Student can view own attendance stats (% per subject, excluding cancelled) | SATISFIED | getStudentStats: @RequireRole(STUDENT); endpoint at /attendance/reports/student/stats; null-role caller now gets 403 (was 200 with empty data pre-Phase 19) |
| RPRT-04 | 19-01-PLAN.md | Student can view own attendance list (raw records, filterable by subject) | SATISFIED | getStudentRecords: @RequireRole(STUDENT); endpoint at /attendance/reports/student/records; null-role caller now gets 403 |

All 4 requirement IDs declared in PLAN frontmatter are accounted for. REQUIREMENTS.md shows RPRT-01 through RPRT-04 as checked off (satisfied from Phase 18 plus Phase 19 security enforcement).

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | — | — | None found |

No TODO/FIXME/placeholder patterns. No empty return stubs. No hardcoded empty data affecting rendering. No console.log-only handlers. The one pre-existing test failure noted in SUMMARY (EventConsumerIntegrationTest) is explicitly out of scope for Phase 19 and pre-dates this phase.

### Human Verification Required

#### 1. End-to-end 403 for unauthenticated callers through running gateway

**Test:** Start docker-compose (gateway + attendance-service), send `curl -s http://localhost:8080/api/attendance/reports/student/stats` with no Authorization header (no X-User-Role propagated).
**Expected:** HTTP 403 with RFC 7807 problem details body.
**Why human:** Cannot start services in static verification; requires live gateway + attendance-service container stack.

#### 2. TEACHER role path through gateway

**Test:** Authenticate as a TEACHER, call GET /api/attendance/reports/lesson/1 through gateway.
**Expected:** HTTP 200 with lesson attendance data (not 403).
**Why human:** Requires live JWT token issued by auth-service; cannot test through static analysis.

### Gaps Summary

No gaps. All 5 must-have truths verified, all 4 artifacts pass levels 1-3, both key links wired, all 4 requirement IDs satisfied. The two human-verification items are integration smoke tests requiring a running stack — they do not block the phase goal, which is fully achieved at the code level.

---

_Verified: 2026-04-04T21:00:00Z_
_Verifier: Claude (gsd-verifier)_
