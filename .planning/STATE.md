---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: Executing phase 1.3
stopped_at: Completed 01.3-01-PLAN.md (API Gateway JWT Filter)
last_updated: "2026-03-30T06:47:46.279Z"
progress:
  total_phases: 4
  completed_phases: 2
  total_plans: 2
  completed_plans: 2
---

# Project State

## Current Milestone

Milestone 1: Auth Service + API Gateway

## Current Phase

Phase 1.1: Auth Service Core (JWT + Login) — COMPLETED
Phase 1.2: OTP Flow + Password Change — COMPLETED
Phase 1.3: API Gateway JWT Filter + Routing — EXECUTING

## Phase Status

| Phase | Status |
|-------|--------|
| 1.1 | completed |
| 1.2 | completed |
| 1.3 | executing |
| 1.4 | pending |

## Completed Plans

| Phase | Plan | Name | Commit Range |
|-------|------|------|--------------|
| 1.1 | 01 | Auth Service Core (JWT + Login) | b408abe..89b781c |

## Decisions

1. Auth-service reads academic_db via JPA but does not own schema (Flyway disabled, ddl-auto=validate)
2. Local enum copies in auth-service — no dependency on academic-api-contract
3. RSA 2048-bit keys persisted to filesystem (jwt.key-dir), generated on first startup
4. Refresh token rotation: old token deleted from Redis on each use, new token issued
5. Logout is idempotent: unparseable tokens silently ignored
- [Phase 01.2]: OTP Telegram delivery stubbed — code stored in Redis only, actual delivery deferred to notification-bot phase
- [Phase 01.2]: OtpExpiredException used for both expired and wrong code to avoid revealing OTP state (security)
- [Phase 01.2]: Field-level @Setter (not class-level) on User entity to minimize mutation surface
- [Phase 01.3]: parsePemPublicKey made package-private for testability without reflection
- [Phase 01.3]: X-Group-Id and X-Is-Headman headers omitted when JWT claims are null (TEACHER/ADMIN)
- [Phase 01.3]: RFC 7807 Problem Details body in all 401 responses per CLAUDE.md mandate

## Blockers

None

## Performance Metrics

| Phase | Plan | Duration | Tasks | Files |
|-------|------|----------|-------|-------|
| 1.1 | 01 | 7m 23s | 7 | 23 |
| Phase 01.2 P01 | 4min | 3 tasks | 12 files |
| Phase 01.3 P01 | 15m | 3 tasks | 6 files |

## Last Updated

2026-03-30

## Last Session

Stopped at: Completed 01.3-01-PLAN.md (API Gateway JWT Filter)
