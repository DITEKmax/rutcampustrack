# Phase 12: Lesson Auto-Generation - Context

**Gathered:** 2026-04-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Automatic lesson generation when a schedule template is created — generates all lesson instances for the semester date range, respecting week parity (odd/even/all). Includes adding `first_week_type` field to Academic Service's `semesters` table and gRPC contract, and re-generation of future planned lessons when template schedule parameters change.

No cron jobs (Phase 13), no RabbitMQ events (Phase 13), no gRPC server (Phase 14).

</domain>

<decisions>
## Implementation Decisions

### Trigger Timing
- **D-01:** Lesson generation is **synchronous** — happens in the same transaction as template creation in `ScheduleItemService.createScheduleItem()`. Response returns only after all lessons are inserted. ON CONFLICT DO NOTHING handles retries (LSSN-03).

### Week Parity
- **D-02:** Add `first_week_type` column (week_type enum: odd/even) to `semesters` table in `academic_db`. This determines whether the first calendar week of the semester is odd or even. Requires Flyway migration V6 in academic-service.
- **D-03:** Update `SemesterResponse` in `academic.proto` to include `first_week_type` field. Update Academic Service gRPC server to return this field.
- **D-04:** Parity calculation: count weeks from `date_from` of the semester. Week 1's type = `first_week_type`. Week 2 = opposite. For each date in [date_from, date_to]: determine its week number → derive parity → match against template's `week_type`. If template is `ALL`, include all dates matching `day_of_week`.
- **D-05:** Semester `date_from` may fall on a weekend (e.g., Sunday Sept 1). Generation still iterates from `date_from` to `date_to`, but only dates matching the template's `day_of_week` get lessons. Weekends are naturally excluded if no template exists for Saturday/Sunday.

### Re-generation on Template Update
- **D-06:** When template's schedule-affecting fields change (`day_of_week`, `week_type`, `start_time`, `end_time`, `lesson_number`), **re-generate future planned lessons**: delete all lessons with `status = 'planned'` and `date >= today`, then re-generate for the remaining semester period. Active/closed/cancelled lessons are NEVER touched — they may have attendance records.
- **D-07:** Non-schedule fields (teacher_id, subject_id, room) do NOT trigger re-generation. These changes are reflected in the schedule view via JOIN with schedule_items (LessonResponse already pulls from ScheduleItem).
- **D-08:** Deletion of individual lessons and more complex schedule management deferred to post-MVP.

### Academic Service Changes
- **D-09:** Flyway migration V6 in academic-service: `ALTER TABLE semesters ADD COLUMN first_week_type week_type NOT NULL DEFAULT 'odd'`. Reuses existing `week_type` enum type from schedule_db — but since it's in academic_db, need to CREATE TYPE if not exists.
- **D-10:** Seed data update: set `first_week_type` for existing test semester(s).

### Claude's Discretion
- `LessonGenerationService` vs inline in `ScheduleItemService` — extract to separate service class if complex
- Batch insert strategy (single INSERT with ON CONFLICT DO NOTHING vs individual saves)
- Whether to use native SQL for bulk insert or JPA `saveAll()`
- Test strategy: unit test for date generation logic, integration test for full flow

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Database Schema
- `docs/database-schema.md` — schedule_db: schedule_items and lessons tables, UNIQUE(schedule_item_id, date) constraint
- `docs/database-schema.md` §semesters — academic_db: semesters table (date_from, date_to fields)

### Proto Contracts
- `proto/academic.proto` — SemesterResponse message (needs `first_week_type` field added)

### Existing Code (Phase 11 artifacts)
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemService.java` — `createScheduleItem()` method where generation will be triggered
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/AcademicGrpcClient.java` — `getActiveSemester()` returns SemesterResponse with date_from/date_to
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/entity/ScheduleItem.java` — weekType, dayOfWeek fields
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/entity/Lesson.java` — entity with scheduleItemId, date, status
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java` — repository to extend

### Academic Service (for migration + gRPC update)
- `services/academic-service/academic-app/src/main/resources/db/migration/` — existing Flyway migrations
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java` — gRPC server to update

### Architecture & Conventions
- `CLAUDE.md` — Coding rules, enum handling (lowercase in PG), contract-first, no JPA associations
- `docs/architecture.md` — Service map, inter-service communication

### Phase Plan
- `docs/phases-plan.md` §Phase 3 — Original lesson auto-generation requirements

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ScheduleItemService.createScheduleItem()` — entry point for triggering generation
- `AcademicGrpcClient.getActiveSemester()` — returns SemesterResponse with date_from/date_to strings
- `LessonRepository` — JPA repository, needs custom query for deleting planned lessons by schedule_item_id and date >= today
- `AbstractScheduleIntegrationTest` — Testcontainers base for integration tests
- `EnumConverters.WeekTypeConverter` — autoApply converter for WeekType enum

### Established Patterns
- Eager generation with ON CONFLICT DO NOTHING (KEY DECISION from PROJECT.md)
- UNIQUE(schedule_item_id, date) idempotency anchor (LSSN-03, Phase 10)
- @TransactionalEventListener(AFTER_COMMIT) for domain events (academic-service pattern — NOT used here, generation is synchronous)
- Native @Query for PostgreSQL-specific operations (used in Phase 11 for enum casts)

### Integration Points
- `ScheduleItemService.createScheduleItem()` — call generation after save
- `ScheduleItemService.updateScheduleItem()` — detect schedule-affecting field changes, trigger re-generation
- `academic.proto` — add first_week_type to SemesterResponse
- Academic Service V6 migration — add column to semesters table

</code_context>

<specifics>
## Specific Ideas

- Semester `first_week_type` is a university-level setting — admins set it when creating the semester, all groups in the semester use the same parity anchor.
- Real-world example: if Sept 1 is Sunday and `first_week_type = odd`, then week containing Sept 1 is odd. Monday Sept 2 starts the first actual day of classes. Templates for Monday with `week_type = odd` will have their first lesson on Sept 2.
- Attendance records are tied to lesson.id — re-generation must NEVER delete non-planned lessons to preserve attendance history.

</specifics>

<deferred>
## Deferred Ideas

- Individual lesson deletion by headman — post-MVP
- More complex schedule management (swap lessons between dates, etc.) — post-MVP

</deferred>

---

*Phase: 12-lesson-auto-generation*
*Context gathered: 2026-04-01*
