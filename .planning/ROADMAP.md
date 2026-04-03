# Roadmap — RutCampusTrack

## Milestones

- ✅ **v1.0 Auth Service + API Gateway** — Phases 1.1-1.4 (shipped 2026-03-30)
- ✅ **v2.0 Academic Service** — Phases 5-9 (shipped 2026-03-31)
- **v3.0 Schedule Service** — Phases 10-14 (in progress)

## Phases

<details>
<summary>✅ v1.0 Auth Service + API Gateway (Phases 1.1-1.4) — SHIPPED 2026-03-30</summary>

- [x] Phase 1.1: Auth Service Core — JWT + Login (1/1 plan) — completed 2026-03-28
- [x] Phase 1.2: OTP Flow + Change Password (1/1 plan) — completed 2026-03-29
- [x] Phase 1.3: API Gateway JWT Filter + Routing (1/1 plan) — completed 2026-03-30
- [x] Phase 1.4: Seed Data + Integration Testing (1/1 plan) — completed 2026-03-30

Full details: `.planning/milestones/v1.0-ROADMAP.md`

</details>

<details>
<summary>✅ v2.0 Academic Service (Phases 5-9) — SHIPPED 2026-03-31</summary>

- [x] Phase 5: Entity and Repository Foundation (2/2 plans) — completed 2026-03-30
- [x] Phase 6: REST API + HATEOAS (4/4 plans) — completed 2026-03-30
- [x] Phase 7: gRPC Server (2/2 plans) — completed 2026-03-30
- [x] Phase 8: Redis Caching (2/2 plans) — completed 2026-03-31
- [x] Phase 9: RabbitMQ Events (2/2 plans) — completed 2026-03-31

Full details: `.planning/milestones/v2.0-ROADMAP.md`

</details>

### Phase 11: REST API + gRPC Client

**Goal:** Full REST API for schedule templates (CRUD), lesson operations (cancel/restore/mass-cancel/geo-block), and schedule viewing — with gRPC client to Academic Service for validation.

**Requirements:** [TMPL-01, TMPL-02, TMPL-03, TMPL-04, TMPL-05, LSSN-04, LSSN-05, LSSN-06, LSSN-07, VIEW-01, VIEW-02]

**Plans:** 3/3 plans complete

Plans:
- [x] 11-01-PLAN.md — Infrastructure: gRPC client setup, contract DTOs/APIs, exceptions, repository extensions
- [x] 11-02-PLAN.md — ScheduleItem CRUD: service, controller, assembler, integration tests (TMPL-01..05)
- [x] 11-03-PLAN.md — Lesson operations + schedule view: service, controller, assembler, integration tests (LSSN-04..07, VIEW-01..02)

### Phase 12: Lesson Auto-Generation

**Goal:** Automatic lesson generation when schedule template is created — generates all lessons for semester dates respecting week parity (odd/even/all), idempotent via UNIQUE constraint.

**Requirements:** [LSSN-01, LSSN-02]

**Plans:** 2/2 plans complete

Plans:
- [x] 12-01-PLAN.md — Academic Service first_week_type (migration + proto + gRPC) + LessonGenerationService with parity algorithm + unit tests
- [x] 12-02-PLAN.md — Wire generation into ScheduleItemService (create + update) + integration tests

### Phase 13: Status Transitions + RabbitMQ Events

**Goal:** Cron-based lesson status transitions (planned->active->closed) with RabbitMQ event publishing (lesson.started, lesson.closed, lesson.cancelled).

**Requirements:** [CRON-01, CRON-02, CRON-03, EVNT-01, EVNT-02, EVNT-03, EVNT-04]

**Plans:** 1/2 plans executed

Plans:
- [x] 13-01-PLAN.md — Event infrastructure (port from academic-service) + event subclasses + cancel event wiring (EVNT-03, EVNT-04)
- [ ] 13-02-PLAN.md — LessonStatusTransitionJob cron + integration tests (CRON-01..03, EVNT-01, EVNT-02)

### Phase 14: gRPC Server

**Goal:** Implement schedule.proto gRPC server — GetActiveLesson, GetLessonById, GetLessonsByGroup for Attendance Service consumption.

**Requirements:** [GRPC-01, GRPC-02, GRPC-03]

**Plans:** 0 plans

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1.1 Auth Service Core | v1.0 | 1/1 | Complete | 2026-03-28 |
| 1.2 OTP + Change Password | v1.0 | 1/1 | Complete | 2026-03-29 |
| 1.3 Gateway JWT Filter | v1.0 | 1/1 | Complete | 2026-03-30 |
| 1.4 Seed Data + Integration Tests | v1.0 | 1/1 | Complete | 2026-03-30 |
| 5. Entity and Repository Foundation | v2.0 | 2/2 | Complete | 2026-03-30 |
| 6. REST API + HATEOAS | v2.0 | 4/4 | Complete | 2026-03-30 |
| 7. gRPC Server | v2.0 | 2/2 | Complete | 2026-03-30 |
| 8. Redis Caching | v2.0 | 2/2 | Complete | 2026-03-31 |
| 9. RabbitMQ Events | v2.0 | 2/2 | Complete | 2026-03-31 |
| 10. Foundation | v3.0 | 2/2 | Complete    | 2026-04-01 |
| 11. REST API + gRPC Client | v3.0 | 3/3 | Complete    | 2026-04-01 |
| 12. Lesson Auto-Generation | v3.0 | 2/2 | Complete    | 2026-04-01 |
| 13. Status Transitions + RabbitMQ Events | v3.0 | 1/2 | In Progress|  |
