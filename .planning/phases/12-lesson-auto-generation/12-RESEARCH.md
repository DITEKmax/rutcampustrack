# Phase 12: Lesson Auto-Generation - Research

**Researched:** 2026-04-02
**Domain:** Schedule Service — lesson generation from recurring templates, week parity, Academic Service gRPC contract extension
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01 — Synchronous generation:** Lesson generation is synchronous — happens in the same transaction as template creation in `ScheduleItemService.createScheduleItem()`. Response returns only after all lessons are inserted. ON CONFLICT DO NOTHING handles retries (LSSN-03).

**D-02 — first_week_type column:** Add `first_week_type` column (week_type enum: odd/even) to `semesters` table in `academic_db`. This determines whether the first calendar week of the semester is odd or even. Requires Flyway migration V6 in academic-service.

**D-03 — Proto update:** Update `SemesterResponse` in `academic.proto` to include `first_week_type` field. Update Academic Service gRPC server to return this field.

**D-04 — Parity calculation:** Count weeks from `date_from` of the semester. Week 1's type = `first_week_type`. Week 2 = opposite. For each date in [date_from, date_to]: determine its week number → derive parity → match against template's `week_type`. If template is `ALL`, include all dates matching `day_of_week`.

**D-05 — Weekend handling:** Semester `date_from` may fall on a weekend. Generation iterates from `date_from` to `date_to`, but only dates matching the template's `day_of_week` get lessons. Weekends are naturally excluded if no template exists for Saturday/Sunday.

**D-06 — Re-generation on update:** When template's schedule-affecting fields change (`day_of_week`, `week_type`, `start_time`, `end_time`, `lesson_number`), re-generate future planned lessons: delete all lessons with `status = 'planned'` and `date >= today`, then re-generate for the remaining semester period. Active/closed/cancelled lessons are NEVER touched.

**D-07 — Non-schedule fields:** `teacher_id`, `subject_id`, `room` changes do NOT trigger re-generation.

**D-08 — No lesson deletion:** Deletion of individual lessons and complex schedule management deferred to post-MVP.

**D-09 — Migration:** Flyway V6 in academic-service: `ALTER TABLE semesters ADD COLUMN first_week_type week_type NOT NULL DEFAULT 'odd'`. Since academic_db does not have `week_type` enum, CREATE TYPE week_type is required first.

**D-10 — Seed data:** Update `first_week_type` for existing test semester(s) in V6 migration.

### Claude's Discretion

- `LessonGenerationService` vs inline in `ScheduleItemService` — extract to separate service class if complex
- Batch insert strategy (single INSERT with ON CONFLICT DO NOTHING vs individual saves)
- Whether to use native SQL for bulk insert or JPA `saveAll()`
- Test strategy: unit test for date generation logic, integration test for full flow

### Deferred Ideas (OUT OF SCOPE)

- Individual lesson deletion by headman — post-MVP
- More complex schedule management (swap lessons between dates, etc.) — post-MVP
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| LSSN-01 | System auto-generates lessons for all semester dates when template is created | D-01 synchronous trigger in `createScheduleItem()`; `SemesterResponse` already carries `date_from`/`date_to` from `AcademicGrpcClient.getActiveSemester()`; need to add `first_week_type` to proto (D-03) and migration (D-02) |
| LSSN-02 | Lesson generation respects week parity (odd/even/all) anchored to semester start | D-04 parity algorithm; `first_week_type` stored in academic_db and returned via gRPC; `ScheduleItem.weekType` and `dayOfWeek` already on entity |
</phase_requirements>

---

## Summary

Phase 12 adds automatic lesson generation to the Schedule Service. When a headman creates a schedule template (`ScheduleItem`), the service must immediately generate concrete `Lesson` rows for every matching date in the active semester — one row per matching (day-of-week, week-parity) date. The existing `UNIQUE(schedule_item_id, date)` constraint with `ON CONFLICT DO NOTHING` makes this generation idempotent (LSSN-03 already implemented in Phase 10).

Two services are modified. In Academic Service: a new `first_week_type` column is added to the `semesters` table (Flyway V6 migration) and the `SemesterResponse` proto message is extended to carry that field. In Schedule Service: `AcademicGrpcClient` is updated to parse the new field, a `LessonGenerationService` is added with pure-Java date iteration logic, and both `createScheduleItem()` and `updateScheduleItem()` are wired to trigger generation or re-generation.

The week-parity algorithm is purely in-memory Java: iterate from `semester.dateFrom` to `semester.dateTo`, for each date compute `weeksSinceStart = ChronoUnit.WEEKS.between(semesterStart.with(DayOfWeek.MONDAY), lessonDate.with(DayOfWeek.MONDAY))`, derive parity as `(weeksSinceStart % 2 == 0) ? firstWeekType : opposite`, then match against the template's `WeekType`. If `WeekType.ALL`, all dates matching `dayOfWeek` pass.

**Primary recommendation:** Extract generation to a dedicated `LessonGenerationService`; use a native bulk INSERT with `ON CONFLICT DO NOTHING` for performance; test the parity algorithm with a pure unit test against a known semester.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Data JPA | 3.4.x (via Spring Boot BOM) | Lesson batch persistence | Already used in project |
| Flyway | 10.x (via Spring Boot BOM) | Academic DB schema migration | Project-wide convention |
| gRPC / protobuf | 3.x (already in project) | Extend SemesterResponse message | Existing inter-service contract |
| JUnit 5 + Testcontainers | Already in project | Integration tests | Established test base |

### No New Libraries Required

All libraries needed for this phase are already on the classpath. No new `build.gradle.kts` changes are expected.

---

## Architecture Patterns

### Where Generation Lives

Extract a dedicated `LessonGenerationService` in the schedule-service's `lesson/` package. It takes `ScheduleItem` + `SemesterResponse` as inputs, returns `List<LocalDate>` of matching dates (or directly performs the insert). Keeping it separate from `ScheduleItemService` isolates the date-iteration logic for unit testing and keeps the service classes focused.

```
schedule-app/
├── lesson/
│   ├── LessonGenerationService.java   ← NEW: generates lesson dates + inserts
│   ├── LessonService.java             ← existing, unchanged
│   ├── entity/Lesson.java             ← unchanged
│   └── repository/LessonRepository.java ← add bulk-insert + delete-planned queries
├── item/
│   └── ScheduleItemService.java       ← wire generation after save; detect field changes on update
└── grpc/
    └── AcademicGrpcClient.java        ← add firstWeekType parsing from SemesterResponse
```

### Pattern 1: Synchronous Generation in Transaction (D-01)

`createScheduleItem()` already calls `academicGrpcClient.getActiveSemester()` and returns `SemesterResponse` with `dateFrom`/`dateTo`. After saving the `ScheduleItem`, call `lessonGenerationService.generateLessons(savedItem, activeSemester)` in the same `@Transactional` method. If generation fails, the whole transaction rolls back — no orphaned schedule item without lessons.

```java
// ScheduleItemService.createScheduleItem() — after save
ScheduleItem saved = scheduleItemRepository.save(item);
lessonGenerationService.generateLessons(saved, activeSemester);   // NEW
return saved;
```

### Pattern 2: Week-Parity Date Iteration (D-04)

The canonical algorithm from STATE.md and PROJECT.md:

```java
// Source: PROJECT.md Key Decisions + CONTEXT.md D-04
public List<LocalDate> computeLessonDates(
        LocalDate semesterStart,
        LocalDate semesterEnd,
        WeekType firstWeekType,      // ODD or EVEN (never ALL — ALL is a template value)
        short targetDayOfWeek,       // 0=Mon, 5=Sat
        WeekType templateWeekType) { // ODD, EVEN, or ALL

    DayOfWeek targetJavaDow = DayOfWeek.of(targetDayOfWeek + 1); // 0->1(Mon)..5->6(Sat)
    LocalDate anchor = semesterStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    List<LocalDate> dates = new ArrayList<>();

    LocalDate current = semesterStart;
    while (!current.isAfter(semesterEnd)) {
        if (current.getDayOfWeek() == targetJavaDow) {
            if (templateWeekType == WeekType.ALL) {
                dates.add(current);
            } else {
                long weeksSinceStart = ChronoUnit.WEEKS.between(
                    anchor,
                    current.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
                WeekType currentParity = (weeksSinceStart % 2 == 0)
                    ? firstWeekType
                    : (firstWeekType == WeekType.ODD ? WeekType.EVEN : WeekType.ODD);
                if (currentParity == templateWeekType) {
                    dates.add(current);
                }
            }
        }
        current = current.plusDays(1);
    }
    return dates;
}
```

**Key detail:** `anchor` is the Monday of (or before) `semesterStart`, not `semesterStart` itself. This handles the case where `semesterStart` is a weekend (e.g. Sunday Sept 1) — the anchor is still the preceding Monday (Aug 26), and week counting is consistent.

### Pattern 3: Bulk INSERT with ON CONFLICT DO NOTHING

Use a native SQL `@Query` for bulk insert to avoid N round-trips. JPA `saveAll()` with per-row inserts is correct but slow for a full semester (up to ~100 rows per template). The UNIQUE constraint on `(schedule_item_id, date)` silently ignores duplicates.

```java
// LessonRepository — new method
@Modifying
@Query(value = """
    INSERT INTO lessons (schedule_item_id, date, status, is_geo_blocked, created_at)
    SELECT :scheduleItemId, d, 'planned', false, NOW()
    FROM unnest(CAST(:dates AS date[])) AS d
    ON CONFLICT (schedule_item_id, date) DO NOTHING
    """, nativeQuery = true)
void bulkInsertPlanned(@Param("scheduleItemId") Long scheduleItemId,
                       @Param("dates") String[] dates);
```

**Note:** JPA does not natively support `date[]` parameter. The recommended approach is to accept `String[]` of ISO dates and cast via `CAST(:dates AS date[])`. Alternatively, use `JdbcTemplate` for the unnest approach. A simpler fallback is `saveAll()` — with ~100 rows per semester it is acceptable and avoids native SQL complexity.

**Recommendation:** For the first implementation use `saveAll(List<Lesson>)` (clear, safe, tested pattern from existing `massCancelLessons`). Performance is acceptable at MVP scale (100–200 lessons per semester per template). Switch to native bulk insert only if profiling shows a problem.

### Pattern 4: Re-generation on Update (D-06)

In `updateScheduleItem()`, compare the incoming request against the current entity fields to detect schedule-affecting changes:

```java
boolean scheduleAffected = !Objects.equals(existing.getDayOfWeek(), request.dayOfWeek())
    || !Objects.equals(existing.getWeekType(), request.weekType())
    || !Objects.equals(existing.getStartTime(), request.startTime())
    || !Objects.equals(existing.getEndTime(), request.endTime())
    || !Objects.equals(existing.getLessonNumber(), request.lessonNumber());

// Apply all field updates first
// ...

if (scheduleAffected) {
    LocalDate today = LocalDate.now();
    lessonRepository.deletePlannedFromDate(saved.getId(), today);
    SemesterResponse semester = academicGrpcClient.getActiveSemester();
    lessonGenerationService.generateFrom(saved, semester, today);
}
```

Add two methods to `LessonRepository`:
1. `deletePlannedFromDate(Long scheduleItemId, LocalDate fromDate)` — deletes lessons with `status = 'planned'` and `date >= fromDate` for a given schedule item.
2. `generateFrom()` only generates dates from `max(today, semesterStart)` to `semesterEnd`.

### Anti-Patterns to Avoid

- **Deleting non-planned lessons on re-generation:** Active, closed, and cancelled lessons have or had attendance records. Only `status = 'planned'` with `date >= today` is safe to delete. Never touch active/closed/cancelled rows.
- **Using ISO week number modulo for parity:** ISO week 1 depends on Jan 1 and can be even or odd in unpredictable ways across years. The correct approach counts weeks from the semester start's Monday anchor, not from the ISO calendar.
- **Calling `getActiveSemester()` inside the generation algorithm:** The semester is already fetched by `createScheduleItem()` before calling generation. Pass it as a parameter to avoid a second gRPC call.
- **Not handling `first_week_type = null` in proto:** Proto3 does not have required fields. The new `first_week_type` field in `SemesterResponse` must be defaulted in the gRPC server implementation. If it is absent (zero value), the client should throw a clear exception or default to ODD.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Idempotent lesson insert | Custom dedup logic | `ON CONFLICT (schedule_item_id, date) DO NOTHING` already in schema | The UNIQUE constraint was placed in Phase 10 specifically for this |
| Bulk insert | For-loop with individual `save()` calls | `saveAll()` or native `INSERT ... unnest` | Hibernate batches `saveAll()` with `spring.jpa.properties.hibernate.jdbc.batch_size` |
| Enum storage | `@Enumerated(EnumType.STRING)` | Existing `EnumConverters.WeekTypeConverter` with `autoApply = true` | Project-wide convention; already handles `week_type` in schedule_db |

---

## Runtime State Inventory

> Not applicable — this is a greenfield feature addition, not a rename/refactor/migration of existing data. The `first_week_type` migration defaults to 'odd' for all existing rows (D-09/D-10) so no custom data migration script is required beyond the Flyway migration itself.

---

## Common Pitfalls

### Pitfall 1: `week_type` Enum Missing in academic_db

**What goes wrong:** The Flyway V6 migration for `academic_db` adds `first_week_type week_type NOT NULL DEFAULT 'odd'` but `week_type` is not defined in `academic_db` — it only exists in `schedule_db`.

**Why it happens:** The database-schema.md shows `week_type` defined in schedule-service's `V1__baseline.sql`. The academic_db baseline (`V1__baseline.sql`) has no `week_type` type.

**How to avoid:** The V6 migration must `CREATE TYPE week_type AS ENUM ('all', 'odd', 'even')` (or `('odd', 'even')` — the `'all'` value is logically meaningless for semester `first_week_type`, but matches the existing type in schedule_db) before the `ALTER TABLE`. Use `CREATE TYPE IF NOT EXISTS` or check that the migration is idempotent.

**Warning signs:** `org.postgresql.util.PSQLException: ERROR: type "week_type" does not exist` during Flyway migration.

### Pitfall 2: `SemesterResponse.firstWeekType` Proto3 Default

**What goes wrong:** Proto3 strings default to empty string `""`. If academic-service's gRPC server does not explicitly set `first_week_type`, the Schedule Service receives `""` and parsing fails or silently defaults.

**How to avoid:** In `AcademicGrpcServiceImpl.getActiveSemester()`, explicitly call `.setFirstWeekType(semester.getFirstWeekType().name().toLowerCase())` after the Semester entity has the field. Add a null-guard in `AcademicGrpcClient` to throw a clear exception if the returned value is blank.

### Pitfall 3: Semester Cache Returns Stale Data Without `first_week_type`

**What goes wrong:** `AcademicReadService.fetchActiveSemester()` is annotated with `@Cacheable(value = "active_semester", key = "'current'")`. After the V6 migration adds `first_week_type`, the cached `Semester` entity from before the migration would be missing the field — but since the column has a NOT NULL DEFAULT, Hibernate will read it correctly after the cache expires (30 min TTL per architecture.md Redis key `semester:active`).

**How to avoid:** Not a practical issue in a dev/test environment (cache is restarted with the service). In production, a rolling deploy resets the JVM cache. Document that the Redis cache for `semester:active` should be flushed (or service restarted) after the V6 migration is applied.

### Pitfall 4: Re-generation Touches Today's Lessons

**What goes wrong:** `deletePlannedFromDate(itemId, LocalDate.now())` deletes lessons for today. If a lesson is planned for today and the headman updates the schedule at 8:00 AM before the lesson starts (CRON-01 transitions it to active at lesson start time), that lesson would be deleted and not re-generated (since we only generate from `today` forward for remaining semester dates).

**How to avoid:** This is a documented edge case accepted in the design. The re-generation contract (D-06) says `date >= today`. In practice, if the lesson for today is still PLANNED at update time, it gets deleted and re-generated, which is correct behaviour. If it's already ACTIVE, it is preserved (only PLANNED rows are deleted). This is acceptable.

### Pitfall 5: `dayOfWeek` Mapping (0-indexed vs `java.time.DayOfWeek`)

**What goes wrong:** `ScheduleItem.dayOfWeek` is stored as `SMALLINT` with `0=Mon, 5=Sat` (per schema). `java.time.DayOfWeek.MONDAY` has value `1`. Direct comparison fails.

**How to avoid:** Convert: `DayOfWeek targetJavaDow = DayOfWeek.of(scheduleItem.getDayOfWeek() + 1)`. Always verified by the unit test for the generation algorithm.

---

## Code Examples

### Semester Entity Update (Academic Service)

```java
// Semester.java — add field after V6 migration
// Requires new WeekType enum in academic-api-contract OR a local enum
// The simplest approach: store as String, or add WeekType to academic-contract

@Setter
@Column(name = "first_week_type", nullable = false, length = 10)
private String firstWeekType;  // store as plain string, convert at gRPC layer
```

**Alternative (preferred if academic-api-contract has no enum dependency):** Store as `String` directly, no enum mapping needed on the academic side. The Schedule Service parses the string from `SemesterResponse.firstWeekType` back to its own `WeekType` enum. This avoids adding a `WeekType` enum to `academic-api-contract` (which has no dependency on schedule types) and avoids cross-service enum coupling.

### V6 Migration (academic_db)

```sql
-- V6__add_semester_first_week_type.sql
-- Adds first_week_type to semesters for week parity anchoring (LSSN-02)

CREATE TYPE week_type AS ENUM ('all', 'odd', 'even');

ALTER TABLE semesters
    ADD COLUMN first_week_type week_type NOT NULL DEFAULT 'odd';

-- Update seed test semester: Spring 2026 starts Feb 1 (Sunday) — first real week is odd
UPDATE semesters SET first_week_type = 'odd' WHERE name = 'Spring 2026';
```

### Proto Extension (academic.proto)

```protobuf
message SemesterResponse {
  int64 id = 1;
  string name = 2;
  string date_from = 3;
  string date_to = 4;
  string first_week_type = 5;   // NEW: "odd" or "even"
}
```

### LessonRepository — New Queries

```java
// Delete planned lessons for a schedule item from a given date forward
@Modifying
@Query(value = "DELETE FROM lessons WHERE schedule_item_id = :itemId AND status = 'planned' AND date >= :fromDate",
       nativeQuery = true)
void deletePlannedFromDate(@Param("itemId") Long scheduleItemId,
                           @Param("fromDate") LocalDate fromDate);
```

### AcademicGrpcClient — firstWeekType Parsing

```java
// In getActiveSemester() result handling — wrap the existing call
public SemesterResponse getActiveSemester() {
    // existing implementation unchanged
}

// New helper used by LessonGenerationService
public WeekType getSemesterFirstWeekType(SemesterResponse response) {
    String raw = response.getFirstWeekType();
    if (raw == null || raw.isBlank()) {
        throw new IllegalStateException("SemesterResponse missing first_week_type — Academic Service may need restart after V6 migration");
    }
    return WeekType.valueOf(raw.toUpperCase());
}
```

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| `@TransactionalEventListener(AFTER_COMMIT)` for generation | Synchronous in-transaction generation (D-01) | Simpler; rollback on failure is free; no event infrastructure needed |
| ISO week number modulo for parity | Weeks-since-semester-start from Monday anchor | Correct across year boundaries; anchored to university calendar |

---

## Open Questions

1. **Should `week_type` in academic_db include `'all'`?**
   - What we know: `first_week_type` on a semester is always `odd` or `even` — `all` is a template concept. Including `'all'` in the type definition makes it consistent with schedule_db but adds a value that should never be used.
   - What's unclear: Whether to use `CREATE TYPE week_type AS ENUM ('odd', 'even')` (minimal, correct) or `('all', 'odd', 'even')` (consistent with schedule_db).
   - Recommendation: Use `('odd', 'even')` only — simpler, prevents invalid data. The type has the same name as schedule_db's type but lives in a different database so there is no conflict.

2. **`saveAll()` vs native bulk INSERT for lesson generation**
   - What we know: A full semester (~20 weeks × ~3 lessons/week = ~60 rows per template) is small. `saveAll()` with batch_size=50 sends 1–2 INSERT statements.
   - Recommendation: Use `saveAll()` for simplicity. Add native bulk INSERT only if performance tests warrant it.

3. **Academic Service entity for `first_week_type` — String or enum?**
   - What we know: Academic Service does not import any schedule-service types. Using a plain `String` field on `Semester` entity avoids cross-module coupling. The gRPC layer sends the string; the Schedule Service parses it to its own `WeekType` enum.
   - Recommendation: Store as `String` in the Semester entity (academic-app). No enum definition in academic-api-contract. Schedule Service parses at client boundary.

---

## Environment Availability

Step 2.6: SKIPPED — this phase makes code and SQL migration changes only. No new external tools or services are introduced. All infrastructure (PostgreSQL, gRPC) is already verified running from prior phases.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL 16) |
| Config file | `AbstractScheduleIntegrationTest.java` — shared Testcontainer base |
| Quick run command | `./gradlew.bat :services:schedule-service:schedule-app:test --tests "*LessonGenerationTest*" --info` |
| Full suite command | `./gradlew.bat :services:schedule-service:schedule-app:test :services:academic-service:academic-app:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| LSSN-01 | POST /schedule/items creates template AND generates lessons in DB | integration | `./gradlew.bat :services:schedule-service:schedule-app:test --tests "*LessonGenerationIntegrationTest*"` | Wave 0 |
| LSSN-02 | Parity algorithm produces correct dates for odd/even/all + weekend semesterStart | unit | `./gradlew.bat :services:schedule-service:schedule-app:test --tests "*LessonGenerationServiceTest*"` | Wave 0 |
| LSSN-02 | Update of schedule-affecting fields deletes planned future lessons and re-generates | integration | `./gradlew.bat :services:schedule-service:schedule-app:test --tests "*LessonGenerationIntegrationTest*"` | Wave 0 |
| LSSN-02 | Update of non-schedule fields does NOT trigger re-generation | integration | same test class | Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew.bat :services:schedule-service:schedule-app:test --tests "*LessonGenerationServiceTest*"`
- **Per wave merge:** `./gradlew.bat :services:schedule-service:schedule-app:test`
- **Phase gate:** Full suite (schedule-app + academic-app) green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `services/schedule-service/schedule-app/src/test/.../LessonGenerationServiceTest.java` — unit test for parity algorithm (pure Java, no Spring context needed)
- [ ] `services/schedule-service/schedule-app/src/test/.../LessonGenerationIntegrationTest.java` — integration test: POST template → assert lessons in DB, PUT template → assert re-generation
- [ ] Academic-service integration test for V6 migration (Flyway applies cleanly) — optional if existing `EntityMappingIntegrationTest` pattern covers schema validation

---

## Project Constraints (from CLAUDE.md)

All of the following directives apply to this phase:

| Directive | Impact on Phase 12 |
|-----------|-------------------|
| **Contract-first** | Any new DTO for lesson generation response is a `record` in `schedule-api-contract`; no Lombok in contract modules |
| **No `@Enumerated(EnumType.ORDINAL)`** | `first_week_type` stored as string in academic_db; `WeekType` in schedule-app via `EnumConverters.WeekTypeConverter` (autoApply) |
| **Flyway migrations** | V6 in academic-service for `first_week_type` column; V2 in schedule-service is already at V2; no new migration needed in schedule_db |
| **`ddl-auto: validate`** | After adding `first_week_type` to `Semester` entity, the column must exist in academic_db before the service starts |
| **No JPA associations** | `Lesson.scheduleItemId` stays as `Long`; no `@ManyToOne ScheduleItem` |
| **Soft delete** | Re-generation deletes only `PLANNED` lessons; never hard-deletes any other row |
| **Enum lowercase in PG** | `week_type` values in academic_db must be `'odd'`, `'even'` (lowercase) |
| **No Lombok in \*-api-contract** | `first_week_type` field addition to `SemesterResponse` proto is not a Java contract concern; if a new DTO is added to contract, use `record` not Lombok |
| **gRPC deadline** | Any new `AcademicGrpcClient` methods must use `.withDeadlineAfter(3, TimeUnit.SECONDS)` |
| **Package naming** | New service lives in `ru.rutcampustrack.schedule.lesson` |

---

## Sources

### Primary (HIGH confidence)

- Read directly: `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemService.java` — current entry points
- Read directly: `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/AcademicGrpcClient.java` — gRPC wrapper, deadline pattern
- Read directly: `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/entity/Lesson.java` — entity fields
- Read directly: `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java` — existing query patterns (native @Query with ::text cast)
- Read directly: `proto/academic.proto` — current SemesterResponse (fields 1–4)
- Read directly: `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java` — gRPC server, semester mapping
- Read directly: `services/academic-service/academic-app/src/main/resources/db/migration/V1__baseline.sql` — academic_db baseline (no week_type enum)
- Read directly: `docs/database-schema.md` — lessons UNIQUE(schedule_item_id, date), schedule_items schema
- Read directly: `.planning/STATE.md` — key pre-decisions including week-parity formula
- Read directly: `CLAUDE.md` — all coding rules
- Read directly: `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/config/EnumConverters.java` — WeekTypeConverter autoApply pattern

### Secondary (MEDIUM confidence)

- `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleItemApiTest.java` — mock setup pattern for AcademicGrpcClient in tests; SemesterResponse mock currently missing `first_week_type` (will need updating)
- `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonApiTest.java` — confirms test pattern: `@MockitoBean AcademicGrpcClient`, manual entity creation via repository

---

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH — no new libraries; all existing patterns verified in source
- Architecture patterns: HIGH — generation algorithm formula taken from STATE.md Key Decisions; cross-checked against entity fields and proto
- Migration design: HIGH — V1 baseline confirms `week_type` absent in academic_db; V6 content is straightforward
- Pitfalls: HIGH — Pitfall 1 (missing enum type) and Pitfall 5 (dayOfWeek offset) verified against actual source files

**Research date:** 2026-04-02
**Valid until:** 2026-05-02 (stable domain; proto and DB schema change only when this phase is implemented)
