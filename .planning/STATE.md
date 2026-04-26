---
gsd_state_version: 1.0
milestone: v9.0
milestone_name: Frontend Unification — Single Login & Role-Based Web Clients
status: shipped
shipped_at: 2026-04-27
last_updated: "2026-04-27"
last_activity: 2026-04-27
progress:
  total_phases: 13
  completed_phases: 13
  total_plans: 41
  completed_plans: 41
  percent: 100
notes:
  - "v0.0.0 pre-release hardening (M01-M16) проходит параллельно вне GSD-workflow — см. CLAUDE.md и docs/milestones/"
  - ".planning/phases/ пуст — все v9.0 phases (49-61) архивированы в milestones/v9.0-phases/"
  - "Phase 61 закрыта 2026-04-27; plan-7 SUMMARY/VERIFICATION не создавались (фаза признана завершённой по факту)"
---

# Project State

## Current Milestone

v9.0 Frontend Unification — SHIPPED 2026-04-27 (все фазы 49-61 закрыты)

Следующий milestone: TBD. Запустить через `/gsd-new-milestone v10.0`.

## Current Position

Активной фазы нет. v9.0 закрыта. Текущий рабочий контекст — v0.0.0 pre-release hardening (M01-M16) в `docs/milestones/`, не в GSD.

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-04-08)

**Core value:** Full-stack attendance tracking: 5 backend microservices + React PWA + Telegram Mini App + Angular Web Panel + Landing page
**Current focus:** v0.0.0 pre-release hardening (см. `CLAUDE.md` + `docs/milestones/`).

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
| 56 | PWA Headman Mode | PWA-HEAD-01..04 | Complete |
| 57 | Landing Presentation Mode + Documentation | LAND-v9-02, 04, 05; DOCS-v9-01..04 | Complete |
| 58 | Admin BUG-006 fixes (search/groups/semester/telegram/conflict-handler) | BUG-006 | Complete |
| 59 | Excuses Backend (excuse.requested publisher + headman approval) | NOTIF deferred | Complete |
| 60 | Headman Schedule Management (templates + one-off lessons) | HEAD-SCH-01..09 | Complete |
| 61 | Headman Homework Management UI + HomeworkApi | HEAD-HW-01..07 | Complete |

## Completed Milestones

| Version | Name | Phases | Plans | Shipped |
|---------|------|--------|-------|---------|
| v1.0 | Auth Service + API Gateway | 1-4 | 4 | 2026-03-30 |
| v2.0 | Academic Service | 5-9 | 12 | 2026-03-31 |
| v3.0 | Schedule Service | 10-14 | 10 | 2026-04-04 |
| v4.0 | Attendance Service MVP | 15-19 | 12 | 2026-04-04 |
| v5.0 | Notification Service (Web + Bot) | 20-26 | 16 | 2026-04-05 |
| v6.0 | PWA + Web Push | 27-32 | 14 | 2026-04-06 |
| v7.0 | Frontends — Mini App, Web Panel, Landing | 33-40 | 16 | 2026-04-07 |
| v8.0 | CI/CD, Deployment & Documentation | 41-48 | 11 | 2026-04-08 |

## Parallel Track: v0.0.0 Pre-Release Hardening

После закрытия v9.0 запущен отдельный pre-release hardening цикл вне GSD-workflow. Артефакты в `docs/milestones/M01-M16/` (PLAN/CHECKLIST/NOTES/DECISIONS per milestone).

| # | Milestone | Status |
|---|-----------|--------|
| M01 | Shared Foundations | ✅ 2026-04-19 |
| M02 | Reliable Eventing | ✅ 2026-04-19 |
| M03a | Internal JWT + Rate-limit | ✅ 2026-04-20 |
| M03b | Secure Boundaries Part B | ✅ 2026-04-20 |
| M04 | Observability | ✅ 2026-04-20 |
| M05 | Performance | ✅ 2026-04-21 |
| M06 | Ops & Supply Chain | ✅ 2026-04-21 |
| M07 | Frontend Hardening | ✅ 2026-04-22 |
| M08 | Test Infrastructure | ✅ 2026-04-23 |
| M09 | Prod Release Blockers | ✅ 2026-04-24 |
| M10 | Notification History | ✅ 2026-04-24 |
| M11 | OpenAPI Polish | ✅ 2026-04-24 |
| M12 | Auth Contract-first Refactor | ✅ 2026-04-24 |
| M13 | Pre-Deploy Hardening | ✅ 2026-04-25 |
| M14 | Post-Audit Fixes | ✅ 2026-04-26 (`v0.0.0-alpha.16`) |
| M15 | First VPS Deploy | ✅ shipped (см. `docs/M16-cleanup-backlog.md`) |
| M16 | Cleanup backlog | 📋 planned |

См. `CLAUDE.md` § «Текущий статус» для актуальной таблицы.

## Accumulated Context

### Roadmap Evolution

- Phase 61 added: Headman Homework Management — UI старосты для создания/редактирования ДЗ группы (бэкенд готов)
- v9.0 фактически расширилась за пределы изначальных 9 фаз (49-57): после shipping добавлены 58-61 как extensions

### Phase 61 Closure Note

Phase 61 закрыта 2026-04-27. Plan-7 выполнен по факту, но `61-07-SUMMARY.md` и `61-VERIFICATION.md` не создавались (фаза признана завершённой пользователем без формального verification — см. также v9.0 shipped status).
Архив: `.planning/milestones/v9.0-phases/61-headman-homework-management/`.

### Decisions

See PROJECT.md Key Decisions table for full history.

- [Phase 56]: Extracted isHeadmanApiRequest into sw-runtime-cache.ts (pure module) for testability without SW context
- [Phase 56]: Placeholder detail pages (Overview/Students/Subjects/Journal/Excuses/LateCheckin/Stats) are complete replacements for Wave 3, not extension targets
- [Phase 56]: usePendingExcuses + usePendingLateCheckins use retry:false + catch-404 pattern per D-10 graceful degradation
- [Phase 56]: SegmentedControl placed in shared/components per D-15 — reusable 5-segment attendance picker with WCAG 40×40 tap target
- [Phase 60]: Subject.groupId immutable after creation (D-02) — updateSubject rejects cross-group tampering
- [Phase 60]: ScheduleItem.teacherId removed end-to-end (D-16); proto field marked reserved 5 for wire-compat
- [Phase 60]: existsActiveTemplateSlot: native SQL с CAST(:weekType AS week_type) — JPA varchar несовместим с PG custom enum (v2.0 V5 convention)
- [Phase 60]: One-off lesson semester_id резолвится по активному семестру; дата вне [date_from..date_to] → 409 ConflictException (MVP D-23, gRPC getSemesterByDate отложен)
- [Phase 60]: One-off lesson events published via DomainEvent + AFTER_COMMIT; notification-bot pushes entire group (D-18, headman included) with gRPC subject-name fallback
- [Phase 61]: ClockConfig переключён с systemUTC на Europe/Moscow (D-03 требует сравнения lesson_date с Moscow-днём)
- [Phase 61]: ADMIN explicitly blocked from homework write (D-06); assistant manage_homework kept for Phase 52 back-compat; author-only guard for update/delete (D-05)
- [Phase 61]: shared/week-navigator создан с нуля (headman-schedule не имел inline week-nav); Vitest вместо ChromeHeadless
- [Phase 61]: SegmentedControlComponent generic <T> — reusable pill switcher; markComplete does full reload (not optimistic) for simplicity at ≤50 items

### Key v9.0 Architecture Decisions

- **HEADMAN model:** `is_headman` boolean JWT claim (JwtService.java:96). UserRole enum stays `{ADMIN, TEACHER, STUDENT}` across all 5 services — no enum extension.
- **Unified /login:** Single Angular web-panel app with `baseHref: /` (was `/admin/`). All roles served from one SPA; lazy-loaded feature routes per role.
- **headmanGuard:** `role === 'STUDENT' && isHeadman === true`. Headman also passes `studentGuard`.
- **WPAN-13 fix approach:** Extend `@RequireRole` AOP aspect in academic-service to allow headman-scoped assistant operations when `X-Is-Headman: true` AND target group matches `X-Group-Id`. No new UserRole enum value.
- **PWA stays separate:** React PWA in `frontends/pwa/` is NOT merged into Angular. HEADMAN features added as new `features/headman/` directory within existing React project.

### Blockers / Open Items

- **v0.0.0 release**: alpha.16 deployed to VPS (M15), M16 cleanup backlog planned.
- **Phase 61 closure**: 61-07 SUMMARY + VERIFICATION не создавались — известный gap в archive, признано acceptable пользователем.

## Session Continuity

Last GSD session: 2026-04-15T14:26:56.913Z (Phase 61 plan 6 completed)
v9.0 shipped: 2026-04-27 (Phase 61 archived)
Last commit (any): see `git log --oneline -5`
Next action when starting new milestone: `/gsd-new-milestone v10.0`

## Directory Layout (после normalization 2026-04-27)

```
.planning/
├── STATE.md                    ← этот файл
├── ROADMAP.md                  ← active roadmap (v1-v9 + open phases)
├── PROJECT.md                  ← detailed project spec
├── REQUIREMENTS.md             ← active requirements
├── MILESTONES.md               ← shipped milestones registry
├── RETROSPECTIVE.md
├── config.json
├── phases/                     ← empty (no active phase; populated by /gsd-plan-phase)
├── milestones/
│   ├── v{1-9}.0-ROADMAP.md     ← snapshots
│   ├── v{1-9}.0-REQUIREMENTS.md
│   ├── v{1-9}.0-phases/        ← archived phases per milestone
│   └── v9.0-BRIEF.md           ← (relocated 2026-04-27 from .planning/)
├── bug-reports/                ← manual QA bugs (BUG-001..009)
├── codebase/                   ← codebase analysis (M01-step)
└── research/                   ← project research artefacts
```
