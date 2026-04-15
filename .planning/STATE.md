---
gsd_state_version: 1.0
milestone: v9.0
milestone_name: Frontend Unification — Single Login & Role-Based Web Clients
status: executing
stopped_at: Completed 61-04-PLAN.md
last_updated: "2026-04-15T13:27:39.950Z"
last_activity: 2026-04-15
progress:
  total_phases: 13
  completed_phases: 3
  total_plans: 34
  completed_plans: 31
  percent: 91
---

# Project State

## Current Milestone

v9.0 Frontend Unification — Single Login & Role-Based Web Clients

## Current Position

Phase: 61 (headman-homework-management-ui-homeworkapi-controller-homewo) — EXECUTING
Plan: 5 of 7
Plans: 0/TBD
Status: Ready to execute
Last activity: 2026-04-15

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-04-08)

**Core value:** Full-stack attendance tracking: 5 backend microservices + React PWA + Telegram Mini App + Angular Web Panel + Landing page
**Current focus:** Phase 61 — headman-homework-management-ui-homeworkapi-controller-homewo

## Roadmap Summary

| Phase | Name | Requirements | Status |
|-------|------|--------------|--------|
| 49 | Nginx Routing + Landing Dead Link Fix | INFRA-v9-01..03, 05..07; LAND-v9-01, 03 | Complete |
| 50 | baseHref Migration + Unified /login | INFRA-v9-04; AUTH-v9-01..07 | Complete |
| 51 | Student Web Cabinet — Shell + Schedule + Check-in | STU-WEB-01..03 | Complete |
| 52 | Student Web Cabinet — Homework + Stats + Notifications + Profile | STU-WEB-04..06, 09 | Complete |
| 53 | Student Web Cabinet — Excuses + Late Check-in + PWA Install Banner | STU-WEB-07, 08, 10 | Complete |
| 54 | Headman Web Cabinet — Group Management + Subjects | HEAD-WEB-01..04 | Complete |
| 55 | Headman Web Cabinet — Attendance Management + Stats | HEAD-WEB-05..08 | Complete |
| 56 | PWA Headman Mode | PWA-HEAD-01..04 | Not started |
| 57 | Landing Presentation Mode + Documentation | LAND-v9-02, 04, 05; DOCS-v9-01..04 | Not started |

## Completed Milestones

| Version | Name | Phases | Plans | Shipped |
|---------|------|--------|-------|---------|
| v1.0 | Auth Service + API Gateway | 1.1-1.4 | 4 | 2026-03-30 |
| v2.0 | Academic Service | 5-9 | 12 | 2026-03-31 |
| v3.0 | Schedule Service | 10-14 | 10 | 2026-04-04 |
| v4.0 | Attendance Service MVP | 15-19 | 12 | 2026-04-04 |
| v5.0 | Notification Service (Web + Bot) | 20-26 | 16 | 2026-04-05 |
| v6.0 | PWA + Web Push | 27-32 | 14 | 2026-04-06 |
| v7.0 | Frontends — Mini App, Web Panel, Landing | 33-40 | 16 | 2026-04-07 |
| v8.0 | CI/CD, Deployment & Documentation | 41-48 | 11 | 2026-04-08 |

## Accumulated Context

### Roadmap Evolution

- Phase 61 added: Headman Homework Management — UI старосты для создания/редактирования ДЗ группы (бэкенд готов)

### Decisions

See PROJECT.md Key Decisions table for full history.

- [Phase 56]: Extracted isHeadmanApiRequest into sw-runtime-cache.ts (pure module) for testability without SW context
- [Phase 56]: Placeholder detail pages (Overview/Students/Subjects/Journal/Excuses/LateCheckin/Stats) are complete replacements for Wave 3, not extension targets
- [Phase 56]: usePendingExcuses + usePendingLateCheckins use retry:false + catch-404 pattern per D-10 graceful degradation
- [Phase 56]: Overview/StudentsList/SubjectsList placeholder pages replaced with full implementations; modal overlay pattern established for AddAssistantModal/SubjectFormModal
- [Phase 56]: SegmentedControl placed in shared/components per D-15 — reusable 5-segment attendance picker with WCAG 40×40 tap target
- [Phase 56]: JournalPage 2-step flow preserves subject/date in component state (no URL params) — simpler state model for Wave 3 scope
- [Phase 56]: Plan 56-05: Excuses/LateCheckin pages ship as pure D-10 shells (no API calls) — empty state IS the feature; StatsPage uses SubjectStatsCollector pattern to call per-subject hooks in stable subjects-array order
- [Phase 60]: Subject.groupId immutable after creation (D-02) — updateSubject rejects cross-group tampering
- [Phase 60]: ScheduleItem.teacherId removed end-to-end (D-16); proto field marked reserved 5 for wire-compat
- [Phase 60]: existsActiveTemplateSlot: native SQL с CAST(:weekType AS week_type) — JPA varchar несовместим с PG custom enum (v2.0 V5 convention)
- [Phase 60]: One-off lesson semester_id резолвится по активному семестру; дата вне [date_from..date_to] → 409 ConflictException (MVP D-23, gRPC getSemesterByDate отложен)
- [Phase 60]: One-off lesson events published via DomainEvent + AFTER_COMMIT; notification-bot pushes entire group (D-18, headman included) with gRPC subject-name fallback
- [Phase 60]: attendance-service cascade delete (lesson.one_off.cancelled) использует natural key (group_id, lesson_date, lesson_number); idempotency через MongoDB remove → deletedCount=0
- [Phase 60]: Read-path merge one-off + template (AC-09) отложен на 60-09 — требует нового proto-метода в schedule-service
- [Phase 60]: Phase 60-06: Angular subject dialog — multi-select teacherIds[] + required type (LECTURE/PRACTICE/LAB) per D-02+D-19
- [Phase 60]: Phase 60-07: Angular /headman/schedule — матрица 5×8, клик→диалог (D-13/D-14); ScheduleSlot и OneOff диалоги; /api/schedule/items (реальный URL, deviation от плана); MatDialogModule убран из imports компонента для тестируемости
- [Phase 61]: Phase 61-01: IT-тесты для gRPC resolveLesson ассертят ResourceNotFoundException (pattern ScheduleGrpcServiceImplTest, direct invocation без in-process канала)
- [Phase 61]: Phase 61-02: ClockConfig переключён с systemUTC на Europe/Moscow (D-03 требует сравнения lesson_date с Moscow-днём)
- [Phase 61]: Phase 61-03: ADMIN explicitly blocked from homework write (D-06); assistant manage_homework kept for Phase 52 back-compat; author-only guard for update/delete (D-05)
- [Phase 61]: Phase 61-04: homework events payload extended with lesson_date+lesson_number (D-07); notification-bot handle_homework.updated resolves subject via academic_client с graceful fallback

### Key v9.0 Architecture Decisions

- **HEADMAN model:** `is_headman` boolean JWT claim (JwtService.java:96). UserRole enum stays `{ADMIN, TEACHER, STUDENT}` across all 5 services — no enum extension.
- **Unified /login:** Single Angular web-panel app with `baseHref: /` (was `/admin/`). All roles served from one SPA; lazy-loaded feature routes per role.
- **headmanGuard:** `role === 'STUDENT' && isHeadman === true`. Headman also passes `studentGuard`.
- **WPAN-13 fix approach:** Extend `@RequireRole` AOP aspect in academic-service to allow headman-scoped assistant operations when `X-Is-Headman: true` AND target group matches `X-Group-Id`. No new UserRole enum value.
- **PWA stays separate:** React PWA in `frontends/pwa/` is NOT merged into Angular. HEADMAN features added as new `features/headman/` directory within existing React project.
- **Phase 55 decision:** Excuse/late-checkin approval UI deferred to future phase (backend endpoint not yet implemented). Graceful degradation shells shipped per CONTEXT.md + ROADMAP Notes.

### Blockers/Concerns

- **baseHref migration risk:** `/admin/` → `/` may break external links. Phase 50 must grep for `ruttrack.site/admin` references before editing.
- **297 web-panel vitest tests:** All passing after Phase 55 (verified 2026-04-10). Must continue passing through Phase 56+ (was 129, grew to 297).
- **63 PWA vitest tests:** Must continue passing through Phase 56 HEADMAN feature addition (PWA-HEAD-03).
- **Excuse / late-checkin backend:** Publishers for `excuse.requested`, `late_checkin.requested` still deferred from v5.0. HEAD-WEB-06, 07 implement UI with graceful degradation.
- **CI/CD pipeline:** No modifications to `.github/workflows/*.yml` (INFRA-v9-06).

### Critical Path

Phases 49 and 50 are CRITICAL-PATH and completed. Phases 51-55 (Blocks B and C) are complete. Phase 56 (Block D) is next.

## Session Continuity

Last session: 2026-04-15T13:27:39.943Z
Stopped at: Completed 61-04-PLAN.md
Next action: /gsd-verifier 58 (после UAT) или /gsd-next для следующей фазы.
Deferred: UAT checkpoint plan 58-09 Task 2 — 13 ручных шагов через браузер (docs/phase-58-report.md Known limitations).
