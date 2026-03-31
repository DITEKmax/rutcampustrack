# Feature Research

**Domain:** University scheduling service — lesson lifecycle management for a microservice attendance tracking system
**Researched:** 2026-03-31
**Project:** RutCampusTrack v3.0 Schedule Service
**Confidence:** HIGH (sourced from project contracts, DB schema, proto files, phase plan)

---

## Context: What Already Exists (Must Not Re-Implement)

These capabilities are fully operational in the existing system:

| Feature | Where | Relevant to Schedule Service |
|---------|-------|------------------------------|
| JWT auth, role injection | API Gateway | X-User-Role, X-Is-Headman headers arrive on every request |
| Groups (CRUD, headman assignment) | Academic Service | group_id is a logical FK in schedule_items |
| Semesters (CRUD, activate) | Academic Service | semester date range drives lesson auto-generation |
| Subjects + teacher assignments | Academic Service | subject_id, teacher_id are logical FKs in schedule_items |
| gRPC server: GetGroup, GetActiveSemester, GetTeacherSubjects | Academic Service | Schedule Service calls these as gRPC client |
| LowercaseEnumConverter, @RequireRole AOP | Platform conventions | Must follow same conventions for WeekType, LessonStatus |
| RabbitMQ fanout exchange `rut-uit.events` | Infrastructure | Publish lesson.* events to same exchange |
| schedule_db schema (2 tables) | Flyway V1 baseline | schedule_items and lessons tables already created |
| schedule.proto (3 RPCs) | proto/schedule.proto | gRPC server contract already defined |
| event-schemas: lesson.started, lesson.closed, lesson.cancelled | event-schemas/ | Event payload shapes already specified |

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features the system cannot ship v3.0 without. Missing = Attendance Service is blocked and the system is non-functional.

| Feature | Why Expected | Complexity | Dependencies |
|---------|--------------|------------|--------------|
| **CRUD schedule_items (schedule template)** | A headman needs to define recurring weekly slots (Mon 9:00, lecture, room 301) for a group+subject+teacher+semester. This is the foundational input. | Medium | gRPC client to Academic for GetGroup, GetTeacherSubjects, GetActiveSemester validation; UNIQUE constraint (group_id, day_of_week, lesson_number, week_type, semester_id) prevents double-booking |
| **Auto-generate lessons for all semester dates** | On schedule_item creation, the system must generate all lesson rows for the semester respecting week_type (all/odd/even). Without this, no lessons exist to trigger attendance. | High | Needs semester date_from/date_to via gRPC GetActiveSemester; week parity calculation from semester start; UNIQUE(schedule_item_id, date) prevents duplicates on retry |
| **GET group schedule for a date range** | All roles need to view upcoming/past lessons. Students see their week; headmen manage schedule; teachers see their classes. | Low | Joins schedule_items to lessons by date range; accessible by ALL roles |
| **Cancel a specific lesson** | Headman cancels a class for a holiday or professor absence. Sets lesson.status = CANCELLED, records cancel_reason. Triggers lesson.cancelled RabbitMQ event. | Low | lesson must be in PLANNED or ACTIVE status; cannot cancel CLOSED lessons |
| **Restore a cancelled lesson** | Headman made a mistake or the situation changed. Restores CANCELLED → PLANNED. No event published (lesson was never "started"). | Low | Only if lesson.date is in the future; cannot restore past-date cancelled lessons |
| **Block geo-checkin on a specific lesson** | Headman flips is_geo_blocked=true for a specific lesson (e.g., lab where students must show physical work). Attendance Service checks this flag. | Low | Toggle endpoint; no status change; lesson stays PLANNED/ACTIVE |
| **Automatic PLANNED → ACTIVE status transition** | Cron runs every minute. Any lesson with status=PLANNED and start_time <= now() becomes ACTIVE. Publishes lesson.started to RabbitMQ. | Medium | Spring @Scheduled; must be idempotent (batch update by date+time range); lesson.started event schema already defined |
| **Automatic ACTIVE → CLOSED status transition** | Cron runs every minute. Any lesson with status=ACTIVE and end_time + 5min <= now() becomes CLOSED. Publishes lesson.closed to RabbitMQ (triggers auto-absent in Attendance Service). | Medium | Same cron job or second @Scheduled; lesson.closed triggers critical downstream action (absent auto-insert); closed_at timestamp recorded |
| **RabbitMQ event: lesson.started** | Consumed by Notification Service (push "class started" to students) and Attendance Service. Without this event the notification flow is broken. | Low | Published from PLANNED→ACTIVE transition; payload: lesson_id, group_id, subject_id, teacher_id, lesson_number, start_time, end_time, room |
| **RabbitMQ event: lesson.closed** | Consumed by Attendance Service to auto-insert absent records. Critical for attendance completeness. | Low | Published from ACTIVE→CLOSED transition; payload: lesson_id, group_id, subject_id |
| **RabbitMQ event: lesson.cancelled** | Consumed by Notification Service (push "class cancelled" to students). Without this, students find out through word of mouth. | Low | Published on cancel; payload: lesson_id, group_id, subject_id, date, cancel_reason |
| **gRPC server: GetActiveLesson** | Called by Attendance Service on every geo-checkin to verify a lesson is currently active for the student's group. This is the hottest read path. | Low | Returns LessonResponse from lessons where status=active and group_id matches; proto already defined |
| **gRPC server: GetLessonById** | Called by Attendance Service to retrieve lesson details (subject_id, teacher_id, is_geo_blocked, date) when processing check-in or generating reports. | Low | Simple PK lookup; returns LessonResponse; proto already defined |
| **gRPC server: GetLessonsByGroup** | Called by Attendance Service report module to get all lessons for a group in a semester for report generation. | Low | Returns repeated LessonResponse filtered by group_id + semester_id + date range; proto already defined |
| **gRPC client: Academic Service validation** | On schedule_item creation, validate group_id (GetGroup), teacher+subject assignment (GetTeacherSubjects for the semester). Prevents phantom schedule entries. | Medium | Calls academic-service:9090 via gRPC; must handle NOT_FOUND with meaningful REST error response |

---

### Differentiators (Competitive Advantage)

Features that go beyond basic schedule CRUD and make the system robust for real university operation.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Week parity (odd/even/all) in lesson generation** | Russian university timetables commonly use alternating-week schedules (e.g., a lab runs only on odd weeks). Without this, many Russian universities cannot use the system at all. | Medium | week_type enum: ALL generates every matching weekday; ODD generates only weeks where week_number_from_semester_start is odd; EVEN generates even weeks. Parity anchored to semester start date, not calendar year. |
| **Bulk lesson cancellation (cancel all future for a schedule_item)** | Headman cancels an entire subject for the rest of the semester (teacher gone, subject restructured). More efficient than cancelling each lesson individually. | Medium | Cancel all lessons WHERE schedule_item_id=? AND date >= today AND status IN (planned, active); one event per cancelled lesson or one bulk event |
| **is_active flag on schedule_item** | Soft-disable a template without deleting it or cancelling all its lessons. Useful when a schedule_item is being restructured mid-semester. | Low | Setting is_active=false does NOT retroactively affect already-generated lessons; only prevents future regeneration |
| **Room information on lesson** | Room is stored on schedule_item (weekly template) and copied into LessonResponse. Students and teachers know where to go without checking a separate system. | Low | room VARCHAR(64) already in schema; passed through in gRPC LessonResponse; part of lesson.started event payload |
| **Redis caching for gRPC hot paths** | GetActiveLesson is called on every student geo-checkin; without caching this is a DB hit per student per minute. Cache lessons by group_id with short TTL matching lesson transition windows. | Medium | Cache key: `lesson:active:{group_id}` with TTL ~60s; invalidate on status transition; risk: stale cache during transition — mitigate with short TTL |
| **Cron idempotency guard** | If two schedule service instances run (future scale), the same lesson must not be transitioned twice. DB UPDATE with WHERE status=planned condition is naturally idempotent when using batch update. | Low | Use `UPDATE lessons SET status='active' WHERE status='planned' AND ...` — only one UPDATE per row ever succeeds |

---

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem like natural extensions but should explicitly NOT be built in v3.0.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| **Holiday calendar / blackout dates** | Universities have public holidays where classes don't meet. Seems like Schedule Service should handle this. | Adds a new `holidays` table, admin CRUD UI, and lesson-generation must query holidays. Doubles the complexity of auto-generation for a case that headmen can handle manually (cancel individual lessons). | Headman cancels lessons on holidays manually. If needed, add as v4.0 enhancement after core works. |
| **Substitute teacher assignment** | A teacher is absent; another takes the class. Common edge case. | Requires lesson-level teacher override field, separate event type (lesson.teacher_changed), and Academic Service must validate the substitute is a valid teacher. Significant complexity for an infrequent event. | Headman cancels the lesson. Or adds substitute teacher as a future PATCH endpoint on lessons. |
| **Room change on specific lesson** | A room becomes unavailable; the lesson must move to a different room. | Same problem as substitute teacher: adds lesson-level override for a field that's on schedule_item. Needs a separate event type. Not needed for MVP. | Headman cancels and tells students the new room out-of-band. Add as PATCH /lessons/{id}/room when Notification Service exists to push the change. |
| **Exam period scheduling** | Exams follow different rules: no attendance by geo-checkin, different room setup, individual schedules. | Exam scheduling is a fundamentally different domain from recurring weekly lessons. Adding it here would bloat the lesson model with exam-specific fields (exam_type, duration, proctor, etc.). | Out of scope for this system entirely. Attendance tracking does not apply to exams. |
| **Teacher conflict detection across groups** | Prevent a teacher from being assigned to two groups at the same time slot. | Academic Service already has the teacher_subject_groups data. Cross-service conflict detection requires a gRPC call to Schedule from Academic or vice versa, creating a circular dependency risk. The UNIQUE constraint already prevents same-group double-booking. | Accept that Academic Service is the source of truth for assignments; cross-group conflict is a headman-coordination problem. Add a validation call only if explicitly required. |
| **Retroactive lesson regeneration** | Admin changes semester dates; lessons must be regenerated. | Existing lessons may have attendance records attached (in MongoDB, attendance_db). Deleting or regenerating lessons would orphan attendance records by lesson_id. This cascades into data corruption. | Semester dates are immutable once lessons are generated. Create a new semester if dates change significantly. |
| **Lesson swap (move to different day/time)** | A lesson needs to be rescheduled from Tuesday to Thursday this week. | Requires creating a one-off lesson (not from a template), with a different date and time. This breaks the invariant that lessons always come from schedule_items. Adds ad-hoc lesson concept. | Cancel the Tuesday lesson (lesson.cancelled event), then separately cancel+restore or create a manual entry. Scope this as v4.0 if demand is high. |
| **Streaming gRPC for GetLessonsByGroup** | Instead of returning all lessons as a batch, stream them. | Premature optimization. Group semester lesson count is at most ~200-400 lessons. A repeated response fits easily in memory. Streaming adds complexity for no measurable benefit at this scale. | Use the batch repeated response as already defined in schedule.proto. |

---

## Feature Dependencies

```
schedule_item (template)
    └──requires──> Academic gRPC: GetGroup (group_id must exist)
    └──requires──> Academic gRPC: GetTeacherSubjects (teacher_id must be assigned to subject+group+semester)
    └──requires──> Academic gRPC: GetActiveSemester (get date_from, date_to for generation)
    └──generates──> lessons[] (one per matching weekday in semester date range, respecting week_type)

lesson (instance)
    └──requires──> schedule_item (parent template)
    └──status: planned ──cron──> active ──cron──> closed
    └──status: planned|active ──headman──> cancelled
    └──status: cancelled ──headman──> planned (restore, only future dates)
    └──is_geo_blocked ──toggle──> blocks Attendance geo-checkin

lesson status: planned → active
    └──publishes──> RabbitMQ lesson.started
    └──consumed by──> Notification Service (push to students)
    └──consumed by──> Attendance Service (starts checkin window)

lesson status: active → closed
    └──publishes──> RabbitMQ lesson.closed
    └──consumed by──> Attendance Service (triggers auto-absent insert)
    └──closed_at recorded

lesson status: cancelled
    └──publishes──> RabbitMQ lesson.cancelled
    └──consumed by──> Notification Service (push "cancelled" to students)

gRPC GetActiveLesson
    └──called by──> Attendance Service (on every geo-checkin POST)
    └──depends on──> lessons.status = active for group_id at current time

gRPC GetLessonById
    └──called by──> Attendance Service (on manual mark, excuse processing, reports)
    └──depends on──> lesson PK

gRPC GetLessonsByGroup
    └──called by──> Attendance Service report module (journal view, statistics)
    └──depends on──> date range + group_id + semester_id
```

### Dependency Notes

- **schedule_item creation requires Academic gRPC:** Group and teacher-subject-group must exist in Academic Service before a schedule template can be created. Schedule Service is a consumer of Academic's data, never the owner.
- **Lesson generation depends on semester dates:** If GetActiveSemester returns an error (no active semester), schedule_item creation must fail with a meaningful 422. Do not silently create items with no lessons.
- **lesson.closed is a critical event:** The entire auto-absent pipeline in Attendance Service depends on receiving this event. If Schedule Service fails to publish it (e.g., RabbitMQ down), absent records are never created. Use @TransactionalEventListener(AFTER_COMMIT) pattern (same as Academic Service) to ensure event is not published on rollback.
- **gRPC caching depends on lesson transitions:** If gRPC GetActiveLesson is cached and a lesson transitions PLANNED→ACTIVE, the cache must be invalidated so Attendance Service immediately sees the new active lesson. Short TTL (60s) is safer than explicit eviction.
- **Restore conflicts with bulk cancel:** If a headman bulk-cancels all future lessons for a schedule_item, individual restore of one lesson must still work. Bulk cancel writes individual lesson records; restore reads the same records. No special handling needed.

---

## MVP Definition

### Launch With (v3.0 Schedule Service)

All table stakes features are required for Attendance Service to function. There is no partial Schedule Service that enables even one attendance check-in without the full set.

- [ ] CRUD schedule_items (POST, GET list, GET by id, PUT, DELETE/deactivate) — **HEADMAN**
- [ ] Auto-generate lessons on schedule_item create (respecting week parity) — **system**
- [ ] GET group schedule for date range — **ALL roles**
- [ ] Cancel single lesson with reason — **HEADMAN**
- [ ] Restore cancelled lesson — **HEADMAN**
- [ ] Toggle is_geo_blocked on lesson — **HEADMAN**
- [ ] Cron: PLANNED → ACTIVE transition + lesson.started event — **system**
- [ ] Cron: ACTIVE → CLOSED transition + lesson.closed event — **system**
- [ ] lesson.cancelled event on cancel — **system**
- [ ] gRPC server: GetActiveLesson — **Attendance Service consumer**
- [ ] gRPC server: GetLessonById — **Attendance Service consumer**
- [ ] gRPC server: GetLessonsByGroup — **Attendance Service consumer**
- [ ] gRPC client: Academic Service (GetGroup, GetTeacherSubjects, GetActiveSemester) — **validation**

### Add After Validation (v3.x)

- [ ] Bulk cancel all future lessons for a schedule_item — trigger: headmen request it during pilot use
- [ ] Redis cache for gRPC GetActiveLesson — trigger: checkin rate exceeds 10 concurrent per minute
- [ ] Room change on specific lesson (PATCH /lessons/{id}/room) — trigger: Notification Service is live so room change can be pushed
- [ ] Substitute teacher on specific lesson (PATCH /lessons/{id}/teacher) — trigger: explicit request from Academic coordinator

### Future Consideration (v4+)

- [ ] Holiday calendar with blackout dates — defer: manual cancellation covers the MVP need
- [ ] Lesson swap / ad-hoc one-off lessons — defer: breaks schedule_item invariant, needs careful design
- [ ] Exam period scheduling — defer: out of scope, different domain

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| CRUD schedule_items | HIGH | MEDIUM | P1 |
| Auto-generate lessons | HIGH | HIGH | P1 |
| GET group schedule | HIGH | LOW | P1 |
| Cancel / restore lesson | HIGH | LOW | P1 |
| Block geo-checkin | MEDIUM | LOW | P1 |
| Cron PLANNED → ACTIVE + event | HIGH | MEDIUM | P1 |
| Cron ACTIVE → CLOSED + event | HIGH | MEDIUM | P1 |
| lesson.cancelled event | HIGH | LOW | P1 |
| gRPC GetActiveLesson | HIGH | LOW | P1 |
| gRPC GetLessonById | HIGH | LOW | P1 |
| gRPC GetLessonsByGroup | HIGH | LOW | P1 |
| gRPC client (Academic validation) | MEDIUM | MEDIUM | P1 |
| Bulk lesson cancellation | MEDIUM | MEDIUM | P2 |
| Redis cache for gRPC hot paths | MEDIUM | MEDIUM | P2 |
| Holiday calendar | LOW | HIGH | P3 |
| Substitute teacher assignment | LOW | HIGH | P3 |
| Ad-hoc lesson swap | LOW | HIGH | P3 |

**Priority key:**
- P1: Must have for v3.0 launch — Attendance Service is blocked without these
- P2: Add when Schedule Service is operational and first usage patterns emerge
- P3: Future milestone; do not pre-build

---

## Edge Case Inventory

These are the known tricky cases that each phase implementation plan must explicitly address.

| Edge Case | Description | Handling Strategy |
|-----------|-------------|-------------------|
| **Odd/even week parity anchoring** | Week 1 of the semester might be odd or even depending on calendar. The EVEN/ODD designation must be anchored to the semester's start date, not the ISO calendar week number. | Week number = floor((date - semester.date_from).days / 7) + 1. If week_number is odd → ODD lesson; if even → EVEN lesson. ALL lessons generate for every matching weekday. |
| **Semester start on non-Monday** | If a semester starts on Wednesday, week 1 has only Wed-Sat. Day-of-week 0 (Monday) lessons in week 1 do not occur. | Generation loop: for each date in [date_from, date_to], if date.dayOfWeek matches schedule_item.day_of_week AND week parity matches → generate lesson. No special-casing needed; date iteration naturally handles partial first week. |
| **Duplicate generation on retry** | If lesson generation crashes mid-way and is retried, some lessons may already exist. | UNIQUE(schedule_item_id, date) constraint makes INSERT idempotent. Use INSERT ... ON CONFLICT DO NOTHING or catch ConstraintViolationException and skip. |
| **Cancel a lesson that is ACTIVE** | A lesson started (status=active) and then the headman cancels it (teacher left mid-class). | Allow cancel of ACTIVE lessons. Set status=CANCELLED, publish lesson.cancelled. Attendance Service may have partial checkin data — that data is preserved (students who checked in stay PRESENT; auto-absent will NOT run because lesson.closed is never published). |
| **Restore past-date cancelled lesson** | Headman tries to restore a lesson for a date that already passed. | Reject with 422. A past cancelled lesson cannot be restored because the checkin window has passed and restoring it would trigger no useful action. |
| **Cron race with multiple instances** | If two Schedule Service instances run simultaneously (future), both crons could fire at the same tick. | Batch UPDATE with WHERE status='planned' AND start_datetime <= now() is naturally atomic at the DB level. Second UPDATE finds 0 rows. No lock needed. |
| **Checkin window: 5 min before start** | Attendance Service checks the window, not Schedule Service. But GetActiveLesson may be called before the lesson is ACTIVE (still PLANNED). | Attendance Service handles this: it calls GetActiveLesson, gets null (no ACTIVE lesson yet), but then checks if any PLANNED lesson for the group starts within 5 minutes. This logic lives in Attendance, not Schedule. Schedule Service only returns ACTIVE status lessons. |
| **lesson.closed not published** | RabbitMQ is down when a lesson closes. Attendance Service never receives the event; absent records are never auto-inserted. | @TransactionalEventListener(AFTER_COMMIT) + RabbitMQ confirms ensure publish happens after DB commit. If RabbitMQ is down, the lesson is still CLOSED in DB; event is lost. For MVP this is acceptable — monitor with Spring Actuator. Retry/dead-letter queue is a v4 concern. |
| **schedule_item deleted with lessons** | Lessons have ON DELETE CASCADE from schedule_item_id. Deleting a schedule_item deletes all its lessons. | Never DELETE schedule_items; use is_active=false (soft disable). Expose no DELETE endpoint — only PATCH /deactivate. |
| **Lesson count per semester** | A group with 5 days of classes, 14-week semester = ~70 lessons. With odd/even: ~35 per alternate pattern. With 8 subjects: ~560 lessons per group. | Batch insert lessons in a single transaction. At 560 rows per call, this is cheap. No pagination needed for generation. |
| **Teacher not assigned to subject for this semester** | Headman creates a schedule_item referencing a teacher who was assigned in a previous semester but not the current one. | gRPC GetTeacherSubjects filters by semester_id. If teacher+subject+group is not in current semester, return 422 with message "Teacher not assigned to this subject in the active semester." |
| **No active semester** | Headman tries to create a schedule_item when no semester is marked active. | gRPC GetActiveSemester returns NOT_FOUND. Schedule Service catches this and returns 422 "No active semester found. An admin must activate a semester before creating a schedule." |
| **Timezone handling** | start_time and end_time in schedule_items are stored as TIME (no timezone). The cron transition must use the correct local time. | All times in the DB are treated as Moscow time (UTC+3) because the university is in Moscow. JVM timezone should be set to Europe/Moscow in production. For tests, use fixed clock with explicit timezone. |
| **Lessons already exist when schedule_item updated** | Headman changes the room or time of a recurring schedule_item. Existing generated lessons still have the old values. | Lessons are generated snapshots. Updating schedule_item does NOT retroactively change lessons. Only future lesson generation (if schedule_item is recreated) would use new values. Room/time changes are reflected in schedule_item for display, but individual lesson rows are immutable after creation (except status and is_geo_blocked). |

---

## Endpoint Inventory by Role

### HEADMAN endpoints (Schedule)

| Method | Path | Description | Complexity |
|--------|------|-------------|------------|
| POST | /api/schedule/schedule-items | Create template + auto-generate lessons | High |
| GET | /api/schedule/schedule-items | List templates for group+semester | Low |
| GET | /api/schedule/schedule-items/{id} | Get template by ID | Low |
| PUT | /api/schedule/schedule-items/{id} | Update room/metadata (not time slot — needs recreate) | Low |
| PATCH | /api/schedule/schedule-items/{id}/deactivate | Soft-disable template | Low |
| PATCH | /api/schedule/lessons/{id}/cancel | Cancel single lesson with reason | Low |
| PATCH | /api/schedule/lessons/{id}/restore | Restore cancelled lesson (future dates only) | Low |
| PATCH | /api/schedule/lessons/{id}/geo-block | Toggle is_geo_blocked | Low |
| PATCH | /api/schedule/schedule-items/{id}/cancel-future | Bulk cancel all future lessons | Medium |

### ALL ROLES endpoints (Schedule)

| Method | Path | Description | Complexity |
|--------|------|-------------|------------|
| GET | /api/schedule/lessons | Get group lessons for date range (?groupId=&dateFrom=&dateTo=) | Low |
| GET | /api/schedule/lessons/{id} | Get lesson by ID | Low |

---

## Complexity Assessment

| Area | Complexity | Reason |
|------|------------|--------|
| Lesson auto-generation | HIGH | Week parity calculation, batch insert, idempotency on retry, semester date boundary handling — requires careful implementation |
| Cron transitions + events | MEDIUM | @Scheduled every minute, batch update, event publishing with @TransactionalEventListener, idempotency under multi-instance |
| gRPC client to Academic | MEDIUM | Channel setup, error mapping (NOT_FOUND → 422), retry config — same as what Academic Service already does inbound |
| gRPC server (3 RPCs) | LOW | Proto already defined, simpler than Academic's 7 RPCs; two are PK lookups, one is a range query |
| CRUD schedule_items | MEDIUM | Validation via gRPC client makes creation more complex than standard CRUD |
| Cancel / restore / geo-block | LOW | Status checks + simple update; event publishing on cancel |
| Redis cache for gRPC | MEDIUM | Cache key design for group-scoped active lesson; invalidation on cron transition |

---

## Sources

- `.planning/PROJECT.md` — v3.0 milestone target features (HIGH confidence — primary source)
- `docs/phases-plan.md` — Phase 3 Schedule Service specification (HIGH confidence — authoritative spec)
- `docs/database-schema.md` — schedule_items and lessons table definitions, constraints, indexes (HIGH confidence — DB contract)
- `proto/schedule.proto` — 3 gRPC RPC signatures and message shapes (HIGH confidence — contract file)
- `proto/academic.proto` — 7 Academic gRPC RPCs that Schedule Service calls as client (HIGH confidence — contract file)
- `event-schemas/lesson.started.json`, `lesson.closed.json`, `lesson.cancelled.json` — event payload shapes (HIGH confidence — contract files)
- `CLAUDE.md` — project coding rules: LowercaseEnumConverter, @RequireRole AOP, @TransactionalEventListener(AFTER_COMMIT), contract-first conventions (HIGH confidence — project standards)
- WebSearch: university scheduling edge cases, holiday/room change patterns (LOW confidence — supplementary context only, all significant findings verified against project documents)

---
*Feature research for: Schedule Service — lesson lifecycle management*
*Researched: 2026-03-31*
