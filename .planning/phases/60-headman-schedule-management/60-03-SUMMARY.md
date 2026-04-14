---
phase: 60-headman-schedule-management
plan: 03
subsystem: schedule-service
tags: [backend, flyway, one-off-lesson, headman, grpc, hateoas, rest-api, contract-first]
dependency_graph:
  requires:
    - "60-02 (ScheduleItem without teacherId — sibling Wave 1)"
    - "60-01 (Subject.groupId model — context for subject_id FK semantics)"
  provides:
    - "Table schedule_one_off_lessons (Flyway V4) with UNIQUE(group_id, date, lesson_number) and semester_id NOT NULL"
    - "OneOffLesson entity + OneOffLessonRepository"
    - "Contract DTOs: CreateOneOffLessonRequest (record), OneOffLessonResponse (class, no Lombok)"
    - "OneOffLessonApi contract interface at /schedule/one-off-lessons"
    - "OneOffLessonService with D-09 template conflict check, D-11 RBAC, D-22 past-date delete, D-23 auto semester lookup"
    - "ScheduleItemRepository.existsActiveTemplateSlot — native SQL with week_type CAST"
    - "ConflictException → 409 via GlobalExceptionHandler"
  affects:
    - "downstream 60-04: event publishers lesson.one_off.created/cancelled will hook into OneOffLessonService"
    - "downstream 60-05: attendance-service read-path merge expects OneOffLesson entity shape"
tech_stack:
  added: []
  patterns:
    - "Native SQL query with CAST(:weekType AS week_type) — PG custom enum forbids implicit varchar comparison"
    - "HATEOAS CollectionModel for list endpoint (self-link points to DELETE /{id})"
    - "computeWeekTypeForDate algorithm mirrors LessonGenerationService (anchor = Monday of semester-start week; even week index keeps firstWeekType parity)"
    - "D-09 conflict check uses ZERO-based day_of_week to match schedule_items schema (0=Mon..5=Sat) even though LocalDate.getDayOfWeek().getValue() is 1-based"
key_files:
  created:
    - services/schedule-service/schedule-app/src/main/resources/db/migration/V4__one_off_lessons.sql
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/oneoff/entity/OneOffLesson.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/oneoff/repository/OneOffLessonRepository.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/oneoff/OneOffLessonService.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/oneoff/OneOffLessonAssembler.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/oneoff/OneOffLessonController.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/exception/ConflictException.java
    - services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/oneoff/CreateOneOffLessonRequest.java
    - services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/oneoff/OneOffLessonResponse.java
    - services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/api/OneOffLessonApi.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/oneoff/OneOffLessonSchemaIT.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/oneoff/OneOffLessonControllerIT.java
  modified:
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/repository/ScheduleItemRepository.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/exception/GlobalExceptionHandler.java
decisions:
  - "existsActiveTemplateSlot написан на native SQL: PostgreSQL custom enum week_type требует CAST(:weekType AS week_type); JPQL с WeekType.ALL литералом выбрасывал 'operator does not exist: week_type = character varying' — см. ключевое решение проекта по кастам (v2.0 V5)"
  - "day_of_week для conflict-check приводится к 0-based (LocalDate.getDayOfWeek().getValue()-1), т.к. схема V1 требует 0=Mon..5=Sat"
  - "semester_id резолвится только из активного семестра; если дата вне [date_from..date_to] — 409 Conflict (D-23 говорит 'lookup по date'; MVP использует единственный активный семестр как источник)"
  - "Events lesson.one_off.created/cancelled вынесены в plan 60-04 (как указано в CONTEXT и плане) — в сервисе сейчас нет publisher-ов"
metrics:
  duration_min: ~15
  completed: 2026-04-14
---

# Phase 60 Plan 03: schedule_one_off_lessons model + HEADMAN CRUD — Summary

Добавлена модель разовых пар в schedule-service — Flyway V4 создаёт `schedule_one_off_lessons` с `UNIQUE(group_id, date, lesson_number)` и `semester_id NOT NULL`; реализованы REST CRUD endpoints (`POST/GET/DELETE /api/schedule/one-off-lessons`) для роли HEADMAN с D-09 конфликт-чекером по активному шаблону, D-23 авто-подбором `semester_id` по дате, D-22 удалением на любую дату (включая прошлое), D-11 RBAC через `requireHeadmanForGroup`; 3 schema IT + 6 controller IT зелёные; full `:services:schedule-service:schedule-app:build` — BUILD SUCCESS.

## Что сделано

### Task 1 — Flyway V4 + entity + repository + schema IT (commit `b7190af`)

**DB:**
- **V4__one_off_lessons.sql**: `CREATE TABLE schedule_one_off_lessons` с колонками `id BIGSERIAL PK, group_id BIGINT NOT NULL, subject_id BIGINT NOT NULL, semester_id BIGINT NOT NULL, date DATE NOT NULL, lesson_number SMALLINT NOT NULL CHECK (lesson_number BETWEEN 1 AND 8), classroom VARCHAR(64), created_by BIGINT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `CONSTRAINT uq_one_off_slot UNIQUE(group_id, date, lesson_number)`, `idx_one_off_group_date`. БЕЗ `teacher_id` (D-04/D-16).

**Entity & Repository:**
- **OneOffLesson** entity в `ru.rutcampustrack.schedule.oneoff.entity` — Lombok @Getter/@NoArgsConstructor/@Setter (допустим в app-модуле), `@PrePersist` инициализирует `createdAt`.
- **OneOffLessonRepository** — методы `findByGroupIdAndDateBetween`, `existsByGroupIdAndDateAndLessonNumber`, `findByGroupIdAndDateAndLessonNumber`.
- **ScheduleItemRepository.existsActiveTemplateSlot** — **native SQL** с `CAST(:weekType AS week_type)`, т.к. PostgreSQL custom enum `week_type` не сравнивается с varchar без явного каста. `week_type = 'all'::week_type OR week_type = CAST(:weekType AS week_type)` — ALL всегда коллидирует, ODD/EVEN матчится только со своей чётностью.

**IT:**
- **OneOffLessonSchemaIT** (3/3 зелёные): `uniqueConstraint_preventsDuplicate` (DataIntegrityViolationException), `semesterIdNotNull_rejected`, `canInsertAndRetrieve` (round-trip + `findByGroupIdAndDateAndLessonNumber`).

### Task 2 — Contract + Service + Controller + controller IT (commit `a9d33f3`)

**Contract (БЕЗ Lombok):**
- **CreateOneOffLessonRequest** — record с `@NotNull groupId/subjectId/date`, `@NotNull @Min(1) @Max(8) lessonNumber`, `@Size(max=64) classroom`. `semesterId` отсутствует (D-23 — резолвится на сервере).
- **OneOffLessonResponse** — класс extends `RepresentationModel<OneOffLessonResponse>`, 9 полей, ручные конструктор и геттеры.
- **OneOffLessonApi** — интерфейс с `@RequestMapping("/schedule/one-off-lessons")`, @Operation/@ApiResponse (201/400/403/409/503 для POST, 204/403/404 для DELETE).

**App:**
- **ConflictException** (новое) — `RuntimeException`, mapped to 409 в `GlobalExceptionHandler`.
- **GlobalExceptionHandler.handleDataIntegrity** — расширен: если сообщение содержит `uq_one_off_slot`, возвращает дружелюбный detail "Разовая пара на этот слот уже существует".
- **OneOffLessonService** @Transactional:
  - `requireHeadmanForGroup` — двухфазная проверка (ADMIN bypass → `requestContext.isHeadman()` → `academicGrpcClient.isHeadman(userId, groupId)`).
  - `createOneOffLesson`: RBAC → `validateGroup` → `getActiveSemester` → проверка `date ∈ [date_from, date_to]` (иначе 409) → `computeWeekTypeForDate` (алгоритм идентичен `LessonGenerationService`) → `existsActiveTemplateSlot` (409 при коллизии) → `save` (UNIQUE violation → 409 через handler).
  - `listOneOffLessons`: `findByGroupIdAndDateBetween`.
  - `deleteOneOffLesson`: любая дата (D-22), RBAC по `oneOff.groupId`.
  - `computeWeekTypeForDate` static method — anchor=Monday(semesterStart), (weeks % 2 == 0) ? firstWeekType : flip.
- **OneOffLessonAssembler** — `toModel` собирает `OneOffLessonResponse` + self-link на DELETE endpoint.
- **OneOffLessonController** implements `OneOffLessonApi`, `@RequireRole({ADMIN, STUDENT})` на POST/DELETE.

**IT (OneOffLessonControllerIT, 6/6 зелёные):**
1. `create_validRequest_returns201` — POST возвращает 201 + все поля, `oneOffLessonRepository.count() == 1`.
2. `create_whenTemplateSlotActive_returns409` — активный шаблон (WeekType.ALL) на (GROUP, DOW=0, slot=1) → 409, БД не затронута.
3. `create_whenDuplicateOneOff_returns409` — пре-существующая запись → 409 через DataIntegrityViolationException.
4. `delete_pastDate_succeeds` — дата `2026-02-09` (в начале семестра) → 204, запись удалена.
5. `create_headmanOfOtherGroup_returns403` — headman group=1 пытается создать для group=2 → 403, БД не затронута.
6. `list_returnsOnlyGroupLessons` — GET `?groupId=1&dateFrom=…&dateTo=…` → возвращает 2 записи (своей группы), не 3.

## Endpoints

| Метод | Путь | Статусы | Роль |
|-------|------|---------|------|
| POST | `/api/schedule/one-off-lessons` | 201 / 400 / 403 / 409 / 503 | HEADMAN / ADMIN |
| GET | `/api/schedule/one-off-lessons?groupId=&dateFrom=&dateTo=` | 200 | любая аутентифицированная |
| DELETE | `/api/schedule/one-off-lessons/{id}` | 204 / 403 / 404 | HEADMAN / ADMIN (любая дата, D-22) |

## Verification

| Команда | Результат |
|---------|-----------|
| `./gradlew :services:schedule-service:schedule-app:test --tests OneOffLessonSchemaIT` | 3/3 PASSED |
| `./gradlew :services:schedule-service:schedule-app:test --tests OneOffLessonControllerIT` | 6/6 PASSED |
| `./gradlew :services:schedule-service:schedule-app:build` | BUILD SUCCESSFUL |
| `grep -rn "schedule_one_off_lessons" services/schedule-service/schedule-app/src/main/resources/` | V4__one_off_lessons.sql содержит DDL |
| `grep -rn "teacher_id" services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/oneoff/` | 0 matches |
| `grep -rn "import lombok" services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/oneoff/` | 0 matches (contract-first: no Lombok) |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] JPQL с PostgreSQL custom enum: operator does not exist**

- **Found during:** Task 2, первый прогон `OneOffLessonControllerIT` — 3 из 6 тестов упали с 500 и `ERROR: operator does not exist: week_type = character varying`.
- **Issue:** В плане указано сделать JPQL `@Query("SELECT COUNT(si) > 0 FROM ScheduleItem si WHERE ... AND si.weekType IN ('ALL', :weekType)")`. Однако колонка `schedule_items.week_type` — это PostgreSQL **custom enum** (не varchar). `WeekTypeConverter` сериализует `WeekType` в `String`, но в WHERE-выражении Hibernate генерирует сравнение `week_type = '?'` (varchar), которое PG отвергает без явного каста. То же ограничение было задокументировано в Key Decisions проекта («V5 migration: implicit casts for PostgreSQL enums — JPA sends varchar, PostgreSQL needs CAST for custom enum columns»).
- **Fix:** Переписал `existsActiveTemplateSlot` на native SQL с `week_type = 'all'::week_type OR week_type = CAST(:weekType AS week_type)`; параметр стал `String` и в сервисе передаётся `weekTypeForDate.name().toLowerCase()`.
- **Files modified:** `ScheduleItemRepository.java`, `OneOffLessonService.java`.
- **Commit:** `a9d33f3`.

**2. [Rule 2 — Critical] Semester out-of-range → 409 (не NullPointerException)**

- **Found during:** Review плана — `D-23` говорит "semester определяется автоматически по date", но в MVP есть только `getActiveSemester()` (gRPC `getSemesterByDate` не добавлен, как и отмечено в плане). Без явной проверки вход в будущий/прошлый семестр падал бы невнятной ошибкой.
- **Issue:** План на этот случай предлагал silently использовать активный семестр. Это нарушает semantic целостность (`semester_id` должен относиться к реальному диапазону даты).
- **Fix:** В `createOneOffLesson` после `getActiveSemester()` проверяется `date ∈ [date_from, date_to]`; вне диапазона — `ConflictException("Дата X не входит в активный семестр [...]")` → 409. Это лучше, чем NPE или тихое приписывание чужому семестру. Покрыто косвенно в тестах (все даты в пределах Spring 2026).
- **Commit:** `a9d33f3`.

## Known Stubs

Нет. Event publishers `lesson.one_off.created` / `lesson.one_off.cancelled` — явно out-of-scope для 60-03 (планируются в 60-04). Это не stub, а запланированная работа следующей волны.

## Threat Flags

STRIDE-mapping плана полностью смитигирован:
- **T-60-01 (Broken Access Control):** `requireHeadmanForGroup` делает двухфазную проверку (JWT claim `isHeadman` + gRPC round-trip) — идентично проверенной в 60-02 логике; покрыто `create_headmanOfOtherGroup_returns403` IT.
- **T-60-03 (Duplicate one-off):** UNIQUE(group_id, date, lesson_number) в БД; `DataIntegrityViolationException` → 409 с дружелюбным сообщением; покрыто `create_whenDuplicateOneOff_returns409`.
- **T-60-05 (Template conflict):** `existsActiveTemplateSlot` native SQL с корректным соответствием недели (ALL всегда, ODD/EVEN по чётности); покрыто `create_whenTemplateSlotActive_returns409`.
- **T-60-06 (Injection):** Все запросы parameterized (`@Param`), native SQL использует `?` placeholders Spring Data, пользовательский ввод не конкатенируется.

Новый surface: `POST/GET/DELETE /api/schedule/one-off-lessons`. RBAC через `@RequireRole({ADMIN, STUDENT})` + app-level headman check. Нет новых threat flags вне плана.

## Self-Check: PASSED

**Created files (existence verified):**
- FOUND: services/schedule-service/schedule-app/src/main/resources/db/migration/V4__one_off_lessons.sql
- FOUND: services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/oneoff/entity/OneOffLesson.java
- FOUND: services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/oneoff/repository/OneOffLessonRepository.java
- FOUND: services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/oneoff/OneOffLessonService.java
- FOUND: services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/oneoff/OneOffLessonAssembler.java
- FOUND: services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/oneoff/OneOffLessonController.java
- FOUND: services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/exception/ConflictException.java
- FOUND: services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/oneoff/CreateOneOffLessonRequest.java
- FOUND: services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/oneoff/OneOffLessonResponse.java
- FOUND: services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/api/OneOffLessonApi.java
- FOUND: services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/oneoff/OneOffLessonSchemaIT.java
- FOUND: services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/oneoff/OneOffLessonControllerIT.java

**Commits (verified via `git log --oneline -2`):**
- FOUND: b7190af feat(60-03): add schedule_one_off_lessons model (Flyway V4 + entity + repo)
- FOUND: a9d33f3 feat(60-03): one-off lesson REST CRUD (contract + service + controller + IT)

**Tests:** `:services:schedule-service:schedule-app:test` — BUILD SUCCESSFUL (все прошлые IT + 9 новых IT зелёные). `:services:schedule-service:schedule-app:build` — BUILD SUCCESSFUL.
