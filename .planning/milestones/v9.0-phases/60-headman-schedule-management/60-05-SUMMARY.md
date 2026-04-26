---
phase: 60-headman-schedule-management
plan: 05
subsystem: attendance-service
tags: [attendance, rabbitmq, event-consumer, cascade-delete, mongodb, one-off-lesson, idempotency]
dependency_graph:
  requires:
    - "60-04 (lesson.one_off.cancelled JSON Schema + publisher in schedule-service)"
  provides:
    - "EventConsumer route: lesson.one_off.cancelled → handleOneOffLessonCancelled"
    - "LessonEventService.processOneOffLessonCancelled(groupId, date, lessonNumber) — idempotent MongoDB cascade delete"
    - "OneOffLessonCancelledConsumerIT: 3 integration tests (happy / idempotent / isolation)"
    - "LessonGenerationMergeTest: placeholder anchoring ScheduleGrpcClient contract (AC-09 Known Limitation)"
  affects:
    - "downstream: attendance read-path merge (AC-09) — deferred to 60-09 (documented below)"
tech_stack:
  added: []
  patterns:
    - "Natural-key cascade delete via MongoTemplate.remove(Query, AttendanceDocument.class) — idempotent by construction"
    - "Event envelope field extraction: ((Number) raw).intValue() / LocalDate.parse — aligned with existing EventConsumer handlers"
    - "TDD: RED (3 failing IT) → GREEN (EventConsumer + LessonEventService wiring) — no refactor needed"
key_files:
  created:
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/OneOffLessonCancelledConsumerIT.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/LessonGenerationMergeTest.java
  modified:
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java
decisions:
  - "Natural key (group_id, lesson_date, lesson_number) выбран как match-критерий cascade delete — attendance-service не хранит отдельный one_off_lesson_id, и этот ключ уникально идентифицирует все attendance docs слота (D-22)"
  - "Idempotency гарантируется MongoDB: remove на non-matching filter → deletedCount=0 без исключения. Отдельной логики defensive-check не требуется"
  - "lesson_number в AttendanceDocument — Integer (не Short). В сигнатуре processOneOffLessonCancelled используется Integer для консистентности"
  - "ScheduleGrpcClient НЕ расширен getOneOffLessonsOnDate: это требует нового proto-метода в schedule-service, что выходит за границы attendance-service scope. Полный read-path merge (AC-09) документирован как Known Limitation и отложен на 60-09"
metrics:
  duration_min: ~8
  completed: 2026-04-14
---

# Phase 60 Plan 05: Attendance Service — One-off Lesson Cascade Delete Summary

В attendance-service добавлен RabbitMQ consumer для `lesson.one_off.cancelled`: `EventConsumer` маршрутизирует событие в `LessonEventService.processOneOffLessonCancelled(groupId, date, lessonNumber)`, который каскадно удаляет attendance docs по натуральному ключу `(group_id, lesson_date, lesson_number)` через `MongoTemplate.remove(...)`. Idempotency подтверждена IT: повторная доставка даёт `deletedCount=0` без исключения. Три интеграционных теста (happy-path / idempotent / isolation) зелёные; `:services:attendance-service:attendance-app:test` — BUILD SUCCESSFUL без регрессий. Полный read-path merge (AC-09) задокументирован как Known Limitation и отложен на 60-09 — требует нового proto-метода `getOneOffLessonsOnDate` в schedule-service.

## Что сделано

### Task 1 — EventConsumer + LessonEventService cascade delete (commits `99a943f` RED → `d3b1afc` GREEN)

**RED (`99a943f`):** создан `OneOffLessonCancelledConsumerIT` (3 теста), упал 3/3:
- `cancellation_deletesAttendanceDocs` — 2 attendance docs `(group=1, date=2026-05-01, slot=2)` вставлены, публикуется envelope `lesson.one_off.cancelled` → ожидается count=0 после обработки.
- `cancellation_idempotent` — повторная публикация → не throw, collection пустая.
- `cancellation_doesNotAffectOtherDocs` — 4 docs: target + другая группа + другая дата + другой slot → удалён только target, остальные 3 сохранены.

IT наследует `AbstractAttendanceIntegrationTest` (MongoDB 7.0 + RabbitMQ 3.13 Testcontainers, `@MockitoBean` на gRPC-клиенты), публикует на fanout `rut-uit.events` через `RabbitTemplate`, ждёт результат через `Awaitility.await().atMost(5s)`.

**GREEN (`d3b1afc`):**
- `EventConsumer.java`:
  - Новый `case "lesson.one_off.cancelled" -> handleOneOffLessonCancelled(envelope)` в switch.
  - Метод `handleOneOffLessonCancelled` — `extractPayload`, `extractLong("group_id")`, `(String) payload.get("date")`, `((Number) raw).intValue()` для `lesson_number`, `LocalDate.parse(dateStr)`, делегирует `lessonEventService.processOneOffLessonCancelled(...)`. Null-guard на обязательные поля → warn+return без exception.
- `LessonEventService.java`:
  - Импорт `com.mongodb.client.result.DeleteResult`.
  - Метод `processOneOffLessonCancelled(Long groupId, LocalDate date, Integer lessonNumber)`:
    ```java
    Query filter = Query.query(
        Criteria.where("group_id").is(groupId)
                .and("lesson_date").is(date)
                .and("lesson_number").is(lessonNumber));
    DeleteResult result = mongoTemplate.remove(filter, AttendanceDocument.class);
    log.info("lesson.one_off.cancelled: groupId={}, date={}, lessonNumber={}, deletedCount={}", ...);
    ```
  - Паттерн согласован с `processLessonCancelled` (тоже использует `Criteria.where(...)` + `mongoTemplate.updateMulti`).

### Task 2 — Read-path merge placeholder (commit `a4c98b0`)

Плановый scope Task 2 — расширение `ScheduleGrpcClient.getOneOffLessonsOnDate(...)` — требует нового proto-метода на стороне schedule-service (AC-09), что выходит за границы attendance-service и текущего плана (см. Known Limitation ниже).

Создан минимальный `LessonGenerationMergeTest.java` (Mockito unit, 1 тест `scheduleGrpcClient_getLessonById_returnsLessonResponseShape`):
- Пинит текущий shape `LessonResponse` от `getLessonById` (id, groupId, subjectId, lessonNumber, date, status).
- Javadoc явно фиксирует Known Limitation и ссылается на 60-09 как follow-up для полного merge.
- Даёт future-reviewer якорь: если в `LessonResponse` появится `is_one_off` — тест ломается, merge-логика ожидается рядом.

## Verification

| Команда | Результат |
|---------|-----------|
| `./gradlew :services:attendance-service:attendance-app:test --tests OneOffLessonCancelledConsumerIT` | 3/3 PASSED (BUILD SUCCESSFUL in 32s) |
| `./gradlew :services:attendance-service:attendance-app:test --tests LessonGenerationMergeTest` | 1/1 PASSED (BUILD SUCCESSFUL in 4s) |
| `./gradlew :services:attendance-service:attendance-app:test` (full suite) | BUILD SUCCESSFUL in 39s — регрессий нет |
| `grep -n "lesson.one_off.cancelled" services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java` | case в switch присутствует |
| `grep -n "processOneOffLessonCancelled" services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java` | метод существует |

## Deviations from Plan

### Auto-fixed Issues

Нет. План выполнен без автофиксов.

### Scope boundary notes

- План (Task 1, action block) предложил `Short lessonNumber` в сигнатуре. Реальный `AttendanceDocument.lessonNumber` — `Integer`; я использовал `Integer` чтобы не вводить конверсию и держать тип согласованным с остальным кодом attendance-service.
- Task 2 полностью соответствует ветви "NOTE: Полный read-path merge может потребовать Flyway V5 и изменения Lesson entity. Если это невозможно в рамках этого плана — создать минимальный тест и задокументировать для 60-09" из плана. ScheduleGrpcClient.java НЕ модифицирован (файл указан в frontmatter `files_modified`, но plan тело явно разрешает fallback — не создавать заглушку если proto не поддерживает).

## Known Stubs

Нет. `LessonGenerationMergeTest` — это не stub, а anchor-тест для будущего контракта; оба продукционных файла реализуют полный happy-path cascade delete.

## Known Limitations

### AC-09 — Read-path merge one-off + template lessons (deferred to 60-09)

**Что не сделано:** Attendance-service `ScheduleGrpcClient.getLessonsByGroup` / `getLessonById` / `getActiveLesson` читают только `ScheduleItem` + `Lesson` на стороне schedule-service. Они НЕ включают `schedule_one_off_lessons`. Как следствие, после создания one-off lesson до момента вызова attendance-side read-path `lesson.closed` event от schedule-service (который уже умеет создавать attendance docs для обычных lesson), разовая пара не отражается в gRPC-ответах attendance-service-у.

**Почему отложено:** Требует нового proto-метода в `schedule.proto` (например `GetOneOffLessonsOnDate(groupId, date)` или расширение `GetLessonsByGroup` с merge на серверной стороне). Это изменение контракта между сервисами, выходящее за scope плана 60-05 (attendance-service only). Плановый planner (60-03/60-04 авторы) ожидаемо добавит его в 60-09 integration plan или отдельный подплан.

**Митигирующий write-path (уже работает end-to-end):**
- Создание one-off lesson → schedule-service публикует `lesson.one_off.created`; notification-bot шлёт push (60-04).
- Закрытие one-off lesson (когда будет реализовано в schedule-service LessonStatusScheduler) → `lesson.closed` → `processLessonClosed` генерит auto-absent docs (v4.0 MVP работает для обычных lessons; для one-off потребуется либо материализация в `Lesson` таблицу, либо дополнение обработчика — см. 60-09).
- Удаление one-off → `lesson.one_off.cancelled` → `processOneOffLessonCancelled` каскадно удаляет docs **(реализовано в этом плане, AC-08 ✅).**

## Threat Flags

Нет новых threat flags. `T-60-04 (Tampering/Idempotency)` из `<threat_model>` плана полностью смитигирован:
- Idempotency — `mongoTemplate.remove` на non-matching filter → `deletedCount=0`; IT `cancellation_idempotent` подтверждает.
- `T-60-06 (Injection)` accept — Spring Data MongoDB `Criteria` API использует parameterized queries; прямых строк не применяется.

Новый surface: consumer side только (внутренняя шина RabbitMQ в той же docker network); publisher (schedule-service) уже в 60-04. Границ доверия не пересёк.

## Self-Check: PASSED

**Created files (existence verified):**
- FOUND: services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/OneOffLessonCancelledConsumerIT.java
- FOUND: services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/LessonGenerationMergeTest.java

**Modified files (existence verified):**
- FOUND: services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java (case добавлен)
- FOUND: services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java (processOneOffLessonCancelled добавлен)

**Commits (verified via `git log --oneline`):**
- FOUND: 99a943f test(60-05): add failing IT for lesson.one_off.cancelled cascade delete
- FOUND: d3b1afc feat(60-05): attendance EventConsumer handles lesson.one_off.cancelled
- FOUND: a4c98b0 test(60-05): anchor ScheduleGrpcClient contract for one-off merge

**Tests:**
- `:services:attendance-service:attendance-app:test` — BUILD SUCCESSFUL (полный suite, без регрессий).
