# Project State

## Current Milestone
Milestone 1: Auth Service + API Gateway

## Current Phase
Phase 1.1: Auth Service Core (JWT + Login) — COMPLETED
Phase 1.2: OTP Flow + Password Change — PLANNED

## Phase Status
| Phase | Status |
|-------|--------|
| 1.1 | completed |
| 1.2 | planned |
| 1.3 | pending |
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

## Blockers
None

## Performance Metrics
| Phase | Plan | Duration | Tasks | Files |
|-------|------|----------|-------|-------|
| 1.1 | 01 | 7m 23s | 7 | 23 |

## Last Updated
2026-03-28

## Last Session
Stopped at: Completed 1.1-01-PLAN.md (Auth Service Core)
