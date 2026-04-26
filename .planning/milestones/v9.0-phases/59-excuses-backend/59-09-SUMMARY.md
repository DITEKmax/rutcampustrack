---
phase: 59-excuses-backend
plan: 09
subsystem: attendance-service + phase integration
tags: [integration, it-test, grpc-validation, phase-report, testcontainers]
status: completed
completed: 2026-04-14

dependency_graph:
  requires:
    - 59-01..59-08 (все предыдущие планы фазы)
    - services/attendance-service/attendance-app/.../excuse/ExcuseService (59-02)
    - services/attendance-service/attendance-app/.../grpc/ScheduleGrpcClient.getLessonsByIds (59-03)
    - services/attendance-service/attendance-app/.../integration/AbstractAttendanceIntegrationTest (существующий базовый класс IT)
  provides:
    - ExcuseService#validateLessonIds (D-25 live) — gRPC check на group ownership
    - ExcuseControllerIT (AC-1..AC-6 + D-18) — 7 IT тестов
    - docs/phase-59-report.md
    - .planning/phases/59-excuses-backend/59-VALIDATION.md финализирован (nyquist_compliant: true)
    - Починка pre-existing ITs (ExcuseEventContractIT autoDelete queue bug, ExcuseServiceApproveIT Mongo ms precision)
  affects:
    - Phase 59 закрыта (кроме manual UAT AC-9/AC-10)

tech-stack:
  added: []
  patterns:
    - "Testcontainers shared static containers via AbstractAttendanceIntegrationTest → minimal per-test overhead"
    - "X-User-* headers → RequestContext via production UserContextFilter (no MockitoBean)"
    - "@MockitoBean на ScheduleGrpcClient / AcademicGrpcClient в базовом классе — stub per test"
    - "purgeQueue вместо drain-loop для test queue cleanup (autoDelete+receive = race)"

key-files:
  created:
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseControllerIT.java
    - docs/phase-59-report.md
  modified:
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseService.java (inject ScheduleGrpcClient, validateLessonIds)
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseServiceTest.java (+@Mock ScheduleGrpcClient lenient stub)
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseEventContractIT.java (stub scheduleGrpcClient + drop autoDelete)
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseServiceApproveIT.java (ms precision для createdAt)
    - .planning/phases/59-excuses-backend/59-VALIDATION.md

decisions:
  - "Lesson-ownership validation гейтится в createExcuse ПОСЛЕ проверки дубликата и ДО снимка studentName — fail-fast дешёвое, не тратим gRPC round-trip на academic если lessonIds заведомо невалидны."
  - "ExcuseControllerIT использует MockMvc через @AutoConfigureMockMvc базового класса (не TestRestTemplate) — MockMvc быстрее, имеет удобные jsonPath ассерты и не требует @LocalServerPort."
  - "Validate через X-User-* headers (не @MockitoBean RequestContext) — тестируем production UserContextFilter + scope proxy behavior тоже попадает в coverage."
  - "IT для self-approve создаёт тикет под HEADMAN_ID (а не под STUDENT_ID) — D-12 запрещает headman create, но мы обходим проверку через прямой excuseRepository.save() чтобы смоделировать edge case 'пришедший из прошлого' ticket и проверить D-13 guard."
  - "Pre-existing EventConsumerIntegrationTest flakiness в полном test run документирована как deferred — не входит в scope 59-09, требует отдельной задачи test-isolation."

metrics:
  tasks: 3
  commits: 3
  files_created: 2
  files_modified: 5
  tests_added: 7 (ExcuseControllerIT)
  duration: ~35 min
---

# Phase 59 Plan 09: Final Integration + Phase Report Summary

One-liner: D-25 gRPC-валидация lessonIds подключена в `ExcuseService.createExcuse`, 7-тест `ExcuseControllerIT` (AC-1..AC-6 + D-18) запущен под реальным Docker Desktop; попутно стабилизированы два pre-existing IT (`ExcuseEventContractIT`, `ExcuseServiceApproveIT`); создан `docs/phase-59-report.md` и финализирован `59-VALIDATION.md`.

## What Was Built

### Task 1: gRPC lesson validation (D-25)

- `ExcuseService` ctor расширен 6-м параметром `ScheduleGrpcClient` (монотонное расширение — предыдущие 5 сохраняют позиции).
- Новый private метод `validateLessonIds(List<Long>)`:
  1. `scheduleGrpcClient.getLessonsByIds(ids)` — batch fetch.
  2. Для каждого запрошенного id, которого НЕТ в ответе → `BadRequestException("Урок с id=... не найден")`.
  3. Для каждого вернувшегося lesson с `groupId != requestContext.getGroupId()` → `BadRequestException("... не принадлежит вашей группе")`.
- Вызывается в `createExcuse` после D-11 duplicate-check и до D-26 snapshot (экономим gRPC round-trips на невалидных запросах).

### Task 2: ExcuseControllerIT (AC-1..AC-6 + D-18)

- 7 @SpringBootTest + Testcontainers тестов, наследует `AbstractAttendanceIntegrationTest` (MongoDB 7 + RabbitMQ 3.13 + Redis в shared static container).
- Имитация JWT через X-User-* headers (через production `UserContextFilter` → `RequestContext` — не mockito-bean).
- `@MockitoBean ScheduleGrpcClient` (из базы) стабится в `@BeforeEach` на возврат matching LessonInfo → D-25 всегда проходит.
- Тесты:
  1. `createExcuse_asPlainStudent_returns201AndPersistsTicket` — POST /attendance/excuses → 201, body `{id, status:"submitted", lessonIds:[11,12]}`, Mongo содержит 1 тикет.
  2. `createExcuse_duplicateLessonId_returns409` (D-11) — pre-seed ticket с lessonId=22, POST с `[22,23]` → 409, Mongo остаётся с 1 тикетом.
  3. `createExcuse_asHeadman_returns409` (D-12) — X-Is-Headman:true → 409, Mongo пустой.
  4. `getTicketById_foreignStudent_returns403` (D-14) — другой user читает чужой тикет → 403.
  5. `updateStatus_approvedByHeadman_returns200AndCascadesAttendance` (AC-5/D-16) — approve 3-lesson ticket от headman → 200, 3 `AttendanceDocument` со status=EXCUSED.
  6. `updateStatus_headmanSelfApprove_returns409` (D-13) — headman пытается одобрить свой тикет → 409, статус остаётся SUBMITTED, attendance пустой.
  7. `updateStatus_alreadyDecided_returns409` (D-18) — повторное решение по уже APPROVED тикету → 409.

### Task 3: Phase report + final regressions

- `docs/phase-59-report.md` — полный отчёт: цели, результаты по всем 9 планам, AC coverage table, test counts, architecture decisions, known limitations (UAT + pre-existing flaky test), новые эндпоинты/gRPC/события, next steps.
- `59-VALIDATION.md` — per-task verification map заполнена для всех 9 планов (✅ green для каждого), sign-off checkboxes checked, `nyquist_compliant: true`, `status: complete`.
- Полная регрессия запущена — см. Verification ниже.

## Verification

| Check | Result |
|---|---|
| `./gradlew :services:attendance-service:attendance-app:compileJava` | BUILD SUCCESSFUL |
| `./gradlew :services:attendance-service:attendance-app:compileTestJava` | BUILD SUCCESSFUL |
| `./gradlew ... test --tests "*ExcuseControllerIT"` | **7/7 green** (0.95s) |
| `./gradlew ... test --tests "*ExcuseEventContractIT"` | **2/2 green** (fixed) |
| `./gradlew ... test --tests "*ExcuseServiceApproveIT"` | **4/4 green** (fixed) |
| `./gradlew ... test --tests "*Excuse*"` | **all Excuse-scoped green** |
| `./gradlew :services:schedule-service:schedule-app:test` | **BUILD SUCCESSFUL** |
| `cd services/notification-bot && pytest tests/ -q` | **128 passed** |
| `cd frontends/web-panel && npm test -- --run` | **358 passed** |
| `./gradlew :services:attendance-service:attendance-app:test` (full) | ⚠️ pre-existing flaky `EventConsumerIntegrationTest#semesterArchived_refreshesSemesterCache` (race между `Mockito.reset()` и async consumer — подробнее в report «Known limitations»). Test зелёный при focused-запуске. |

## Commits

| Task | Commit | Message |
|---|---|---|
| 1 | `dab56c9` | feat(59-09): wire ScheduleGrpcClient lesson validation into ExcuseService (D-25) |
| 2 | `99af935` | test(59-09): add ExcuseControllerIT (AC-1..AC-6, D-18) + finalize VALIDATION |
| 2b | `5c9788d` | fix(59-09): stabilize ExcuseEventContractIT/ApproveIT under real Docker |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] `ExcuseEventContractIT` test queue auto-deletes mid-test**

- **Found during:** Task 3 full-suite regression run under Docker Desktop.
- **Issue:** Queue был объявлен как `QueueBuilder.nonDurable().autoDelete()`. `RabbitTemplate.receive(queueName, timeout)` открывает consumer и закрывает его — после первого вызова autoDelete queue удаляется брокером, следующий `receive()` падает с `AmqpIOException`.
- **Fix:** Убрали `.autoDelete()`; заменили drain-loop через `rabbitTemplate.receive()` на более быстрый `amqpAdmin.purgeQueue(name, false)`.
- **Files modified:** `ExcuseEventContractIT.java`
- **Commit:** `5c9788d`

**2. [Rule 1 — Bug] `ExcuseServiceApproveIT` createdAt nanosecond comparison**

- **Found during:** Task 3 full-suite regression run.
- **Issue:** Тест сравнивал `Instant.now()` (nanosecond-precision в Java) с `document.getCreatedAt()`, который Mongo хранит с millisecond-precision → `expected: 2026-...828400Z, but was: 2026-...828Z`.
- **Fix:** Замена `isEqualTo(past)` на `isCloseTo(past, within(1, ChronoUnit.MILLIS))`.
- **Files modified:** `ExcuseServiceApproveIT.java`
- **Commit:** `5c9788d`

**3. [Rule 2 — Missing critical functionality] `ExcuseServiceTest` + `ExcuseEventContractIT` не стубили новый ScheduleGrpcClient**

- **Found during:** Task 1 compile/test cycle после добавления validateLessonIds.
- **Issue:** Существующие тесты падали с NPE / validation failure на первом же `createExcuse` — Mockito default возвращал пустой список, D-25 валидация ругалась «Урок не найден».
- **Fix:** Добавлен `@Mock ScheduleGrpcClient` в `ExcuseServiceTest` с lenient `getLessonsByIds(anyList())` возвращающим LessonInfo для всех requested ids с GROUP_ID. Тот же стаб добавлен в `ExcuseEventContractIT.setUp()`.
- **Files modified:** `ExcuseServiceTest.java`, `ExcuseEventContractIT.java`
- **Commit:** `dab56c9`

### Clarifications (not deviations)

- **Plan указывал `ScheduleGrpcClient` как 3-й параметр конструктора**, я поставил 6-м (append). Причина: предыдущие 5 параметров (`ExcuseRepository, RequestContext, AcademicGrpcClient, AttendanceWritePort, ExcuseEventPublisher`) уже устоялись во всех 59-02/04/05 тестах, менять позиции — значит лишний merge conflict risk и fake deltas в blame.
- **IT-тест D-18 добавлен сверх AC-6.** Плановые 6 тестов закрывают AC-1..AC-6; я добавил 7-й `updateStatus_alreadyDecided_returns409` т.к. D-18 декомпозиция уже есть в `ExcuseServiceTest` на unit-уровне, но REST-путь тоже стоило подтвердить — копипаста 10 строк, zero cost.

## Known Stubs

None. Wave 5 — закрытие фазы, никаких новых stubs.

Unresolved housekeeping items (из 59-08, not in scope):
- `HeadmanApiService.getPendingExcuses()` — dead code (Phase 55 shell), safe to delete.
- `features/headman/excuses/excuse.types.ts` дублирует student-side типы — candidate for consolidation в `shared/excuses/`.

## Known Deferred Issues (документировано в phase-59-report.md)

**`EventConsumerIntegrationTest#semesterArchived_refreshesSemesterCache` flaky под полной нагрузкой.**

- Симптом: `Wanted but not invoked: semesterCacheService.refresh()` — Awaitility 5s истекает.
- Root cause: `Mockito.reset(semesterCacheService)` в `@BeforeEach` конкурирует с асинхронным RabbitMQ consumer, дочитывающим сообщения из предыдущего теста. Когда consumer вызывает `refresh()` ДО `reset()`, invocation wiped.
- **Not in scope 59-09**: существует с Phase 15/16 (Attendance Service v4.0), просто никогда локально не прогонялся полный test run под Docker.
- Fix path (для будущей housekeeping-задачи): либо `@DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD)`, либо `Awaitility.await().ignoreExceptions()` + выбрать другой invariant (например, публиковать и ждать поле `semesterCacheService.getActiveSemesterId()`).
- Workaround: focused-прогон `./gradlew test --tests "*EventConsumerIntegrationTest"` стабилен.

## Threat Flags

None. Plan's `<threat_model>` covered:
- **T-59-09-01 (Repudiation: нет аудит-лога)** — accepted. `ExcuseTicket.decisionBy + decisionAt + updatedAt + AttendanceDocument.source=HEADMAN_EXCUSE` формируют полную цепочку аудита.
- **T-59-09-02 (Info disclosure: phase-59-report.md в репо)** — accepted. Отчёт без секретов, стандартная практика проекта.

## Self-Check: PASSED

- FOUND: services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseControllerIT.java
- FOUND: docs/phase-59-report.md
- FOUND: `validateLessonIds` в services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseService.java
- FOUND: `nyquist_compliant: true` в .planning/phases/59-excuses-backend/59-VALIDATION.md
- FOUND commit `dab56c9` (Task 1)
- FOUND commit `99af935` (Task 2)
- FOUND commit `5c9788d` (Task 2b: IT stabilization)
- VERIFIED: `./gradlew ... --tests "*ExcuseControllerIT"` → 7/7 green
- VERIFIED: `./gradlew :services:schedule-service:schedule-app:test` → BUILD SUCCESSFUL
- VERIFIED: `pytest services/notification-bot/tests/` → 128 passed
- VERIFIED: `npm test --run` (web-panel) → 358 passed

## Notes

- **AC-9 / AC-10 UAT** — осталось ручное подтверждение сквозного flow (студент submit → Telegram alert старосте → headman approve → Telegram DM студенту). Testcontainers не моделируют реальный Telegram bot API.
- Phase 59 technically complete как backend + test coverage; production deploy и UAT — следующие шаги в next_steps раздела phase-59-report.md.
