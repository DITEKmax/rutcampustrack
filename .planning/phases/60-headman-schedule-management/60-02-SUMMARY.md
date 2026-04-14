---
phase: 60-headman-schedule-management
plan: 02
subsystem: schedule-service
tags: [backend, flyway, schedule-item, teacher-id-removal, headman, grpc, rabbitmq-events]
dependency_graph:
  requires: []
  provides:
    - "schedule_items.teacher_id DROPPED (Flyway V3)"
    - "ScheduleItem entity без teacherId (D-16)"
    - "CreateScheduleItemRequest / UpdateScheduleItemRequest / ScheduleItemResponse без teacherId"
    - "LessonResponse (REST contract) без teacherId"
    - "schedule.proto LessonResponse: reserved 5 (teacher_id)"
    - "event-schemas/lesson.started.json без teacher_id"
    - "LessonStartedEvent.Payload без teacherId"
  affects:
    - "downstream attendance-service: build SUCCESS, teacher-journal JOIN path не затронут (он не читал teacher_id из lessons)"
    - "downstream notification-bot/notification-service: не читают teacher_id из событий"
tech_stack:
  added: []
  patterns:
    - "proto `reserved N` для безопасного удаления поля (сохраняет wire/field-id compatibility)"
    - "Reflection-based unit test (no Spring context) для контрактной проверки отсутствия поля"
    - "Mockito unit test для двухфазного guard (RequestContext.isHeadman → gRPC isHeadman)"
key_files:
  created:
    - services/schedule-service/schedule-app/src/main/resources/db/migration/V3__drop_teacher_id.sql
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/item/ScheduleItemEntityTest.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/item/ScheduleItemSecurityTest.java
  modified:
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/entity/ScheduleItem.java
    - services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/item/CreateScheduleItemRequest.java
    - services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/item/UpdateScheduleItemRequest.java
    - services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/item/ScheduleItemResponse.java
    - services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/lesson/LessonResponse.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemService.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemAssembler.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonAssembler.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonStatusTransitionJob.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/LessonStartedEvent.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java
    - proto/schedule.proto
    - event-schemas/lesson.started.json
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleItemApiTest.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonApiTest.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonCancelEventTest.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonGenerationIntegrationTest.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleViewTest.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/lesson/LessonStatusTransitionJobTest.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImplTest.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/grpc/LessonsByIdsGrpcIT.java
decisions:
  - "proto field `teacher_id = 5` помечен как `reserved 5; reserved \"teacher_id\";` — защищает от случайного переиспользования номера тэга и имени в будущем, сохраняя wire-forward-compat для любого оставшегося клиента"
  - "Rule 3 (каскадная чистка): удаление поля задело LessonResponse/LessonAssembler/LessonStartedEvent/LessonStatusTransitionJob/ScheduleGrpcServiceImpl + JSON Schema + 8 тестовых файлов — всё это blocking-зависимости от ScheduleItem.teacherId, без чистки build не собирается"
  - "event-schemas/lesson.started.json: teacher_id удалён из required и properties — ни один downstream-потребитель (notification-bot handle_lesson_started.py, notification-service EventConsumer, attendance EventConsumer) это поле не читает, grep подтвердил"
metrics:
  duration_min: ~35
  completed: 2026-04-14
---

# Phase 60 Plan 02: Remove ScheduleItem.teacherId (D-16) — Summary

Single sentence: удалено поле `teacher_id` из `schedule_items` (Flyway V3 DROP COLUMN IF EXISTS), из `ScheduleItem` entity, из всех DTO (`CreateScheduleItemRequest`, `UpdateScheduleItemRequest`, `ScheduleItemResponse`, `LessonResponse`), из event `LessonStartedEvent.Payload` + соответствующей JSON Schema, `reserved 5` в `schedule.proto LessonResponse`, цепочка сборки gRPC-ответов и HATEOAS-сборщик вычищены, 8 тестовых файлов обновлены, добавлены два целевых теста (`ScheduleItemEntityTest` — reflection-контроль отсутствия поля, `ScheduleItemSecurityTest` — 4-кейсовая проверка `requireHeadmanForGroup`), schedule-app тесты зелёные, attendance-service сборка успешна.

## What was built

### Task 1 — drop teacher_id end-to-end (commit `a1ca72f`)

**DB & contract core:**
- **Flyway V3** (`V3__drop_teacher_id.sql`): `ALTER TABLE schedule_items DROP COLUMN IF EXISTS teacher_id;` (идемпотентно).
- **ScheduleItem entity** (`item/entity/ScheduleItem.java`): удалено поле `teacherId` и соответствующий `@Column(name="teacher_id")`. Оставлен комментарий про D-16 для ретроспективы.
- **CreateScheduleItemRequest / UpdateScheduleItemRequest** (api-contract records): параметр `Long teacherId` удалён из сигнатур.
- **ScheduleItemResponse** (api-contract class): поле + конструктор + getter удалены. Класс сохраняет HATEOAS `RepresentationModel<ScheduleItemResponse>`.
- **LessonResponse** (api-contract class): `teacherId` удалён из полей, конструктора, getter'а.

**Service & assembler:**
- **ScheduleItemService.createScheduleItem**: `item.setTeacherId(...)` удалён.
- **ScheduleItemService.updateScheduleItem**: `existing.setTeacherId(...)` удалён (scheduleAffected сравнение не включало teacherId — оставлено как было).
- **ScheduleItemAssembler.toModel**: `item.getTeacherId()` удалён из конструктора ScheduleItemResponse.
- **LessonAssembler.toResponse**: `si.getTeacherId()` удалён из конструктора LessonResponse.

**Events:**
- **LessonStartedEvent.Payload**: `@JsonProperty("teacher_id") Long teacherId` удалён из payload record и конструктора.
- **LessonStatusTransitionJob.runTransitions**: вызов `new LessonStartedEvent(...)` обновлён — без `item.getTeacherId()`.
- **event-schemas/lesson.started.json**: `teacher_id` удалён из `required` и `properties`. Описание схемы обновлено с отсылкой на D-16.

**gRPC:**
- **proto/schedule.proto LessonResponse**: вместо `int64 teacher_id = 5;` — `reserved 5; reserved "teacher_id";`. Это защищает wire-формат: номер поля 5 и имя `teacher_id` не могут быть переиспользованы кем-то по ошибке, что важно если где-то остался скомпилированный старый клиент.
- **ScheduleGrpcServiceImpl.buildResponse**: вызов `.setTeacherId(item.getTeacherId())` удалён (сгенерированный getter/setter уже отсутствуют из-за `reserved`).

**Tests cleanup (8 файлов):**
- `ScheduleItemApiTest`: удалена константа `TEACHER_ID`, все `item.setTeacherId(...)` и аргументы `TEACHER_ID` в `CreateScheduleItemRequest`/`UpdateScheduleItemRequest` конструкторах.
- `LessonApiTest`, `LessonCancelEventTest`, `LessonGenerationIntegrationTest`, `ScheduleViewTest`, `LessonStatusTransitionJobTest`, `LessonsByIdsGrpcIT`, `ScheduleGrpcServiceImplTest`: `item.setTeacherId(...)` удалены из seed-хелперов.
- `ScheduleViewTest`: удалён jsonPath `teacherId` ассерт (VIEW-02 ответ больше не содержит это поле).
- `ScheduleGrpcServiceImplTest.getLessonById`: `assertThat(response.getTeacherId()).isEqualTo(1L)` удалён (getter генератора больше нет).

**New unit test:**
- `ScheduleItemEntityTest` (JUnit 5 + reflection, без Spring): 2 теста — `scheduleItem_hasNoTeacherIdField` (reflection проверка отсутствия поля `teacherId`) + `scheduleItem_stillHasCoreSlotFields` (sanity check наличия core slot полей).

### Task 2 — headman guard security test (commit `85e477b`)

- **ScheduleItemSecurityTest** (Mockito unit test, без Spring): 4 теста для `ScheduleItemService.requireHeadmanForGroup` через public `createScheduleItem(...)`:
  1. `requireHeadmanForGroup_headmanOfCorrectGroup_allowed` — STUDENT + `isHeadman=true` + `gRPC.isHeadman(userId, groupId)=true` → guard пропускает (AccessDeniedException НЕ бросается). Проверяется что `academicGrpcClient.isHeadman()` всё-таки был вызван.
  2. `requireHeadmanForGroup_headmanOfWrongGroup_throws403` — STUDENT + `isHeadman=true` + `gRPC.isHeadman(userId, OTHER_GROUP)=false` → `AccessDeniedException` с упоминанием groupId. Проверяется что `validateGroup` НЕ вызывается (guard останавливает до downstream).
  3. `requireHeadmanForGroup_notHeadman_throws403` — STUDENT + `isHeadman=false` → `AccessDeniedException` без gRPC round-trip. Проверяется что `gRPC.isHeadman` и `gRPC.validateGroup` НЕ вызываются (fast-fail по JWT claim).
  4. `requireHeadmanForGroup_adminBypasses` — ADMIN → guard возвращается до `isHeadman()` и `gRPC.isHeadman`. Проверяется что `requestContext.isHeadman()` и `academicGrpcClient.isHeadman(...)` НЕ вызываются.

## Verification

| Команда | Результат |
|---------|-----------|
| `./gradlew :services:schedule-service:schedule-app:compileJava` | BUILD SUCCESSFUL |
| `./gradlew :services:schedule-service:schedule-app:test --tests "ScheduleItemEntityTest"` | 2/2 PASSED |
| `./gradlew :services:schedule-service:schedule-app:test --tests "ScheduleItemSecurityTest"` | 4/4 PASSED |
| `./gradlew :services:schedule-service:schedule-app:test` (все тесты schedule-app) | BUILD SUCCESSFUL |
| `./gradlew :services:schedule-service:schedule-app:build` | BUILD SUCCESSFUL |
| `./gradlew :services:attendance-service:attendance-app:compileJava` | BUILD SUCCESSFUL |
| `./gradlew :services:attendance-service:attendance-app:compileTestJava` | BUILD SUCCESSFUL |
| `grep -rn "setTeacherId" services/schedule-service/` | 0 matches |
| `grep -rn "teacher_id" services/attendance-service/` | 0 matches (подтверждает что teacher-journal путь независим) |

Оставшиеся упоминания `teacher_id` в `services/schedule-service/`:
- `V1__baseline.sql` — историческая миграция (неизменяема);
- `V3__drop_teacher_id.sql` — сама миграция удаления (референс в SQL);
- документационные комментарии `// D-16: ...` в 6 файлах (намеренно).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] Каскадная чистка teacher_id в LessonResponse/LessonStartedEvent/ScheduleGrpcServiceImpl/JSON Schema/proto**

- **Found during:** Task 1 compile после правки ScheduleItem.
- **Issue:** План 60-02 формально перечислял только файлы в `item/` package. Но `ScheduleItem.getTeacherId()` вызывался из `LessonAssembler` (для REST `LessonResponse`), из `LessonStatusTransitionJob` (для `LessonStartedEvent` payload) и из `ScheduleGrpcServiceImpl.buildResponse` (для gRPC LessonResponse). Без их правки:
  - `schedule-app` не компилируется (`getTeacherId()` не существует),
  - `LessonResponse` контракта (REST) сохранял бы устаревшее поле → нарушение D-16 в API,
  - gRPC `LessonResponse` proto-message всё ещё объявлял `teacher_id = 5` и сгенерированный setter бы падал,
  - `lesson.started` event payload всё ещё объявлял `teacher_id`, хотя ни один consumer не читает.
- **Fix:**
  - Удалён `teacherId` из `LessonResponse` (api-contract) — конструктор, поле, getter.
  - `LessonAssembler.toResponse` обновлён (убран `si.getTeacherId()`).
  - `LessonStartedEvent.Payload` + конструктор: убран `teacherId`.
  - `LessonStatusTransitionJob.runTransitions`: вызов constructor'а укорочен.
  - `proto/schedule.proto LessonResponse`: `int64 teacher_id = 5;` заменён на `reserved 5; reserved "teacher_id";` — это стандартный Protobuf способ безопасного удаления поля (сохраняет гарантию что номер 5 и имя никогда не будут переиспользованы).
  - `ScheduleGrpcServiceImpl.buildResponse`: убран `.setTeacherId(...)` — сгенерированный метод больше не существует после `reserved`.
  - `event-schemas/lesson.started.json`: `teacher_id` удалён из `required` и `properties`. Проверено grep'ом что notification-bot `lesson_started.py`, notification-service `EventConsumer.java`, attendance-service `EventConsumer.java` это поле НЕ читают — downstream compatibility сохранена.
- **Files modified:** 11 production + 8 tests (см. `key_files.modified`).
- **Commit:** `a1ca72f`.

**2. [Rule 3 — Blocking] Чистка 8 тестовых файлов от teacher_id seed'ов и ассертов**

- **Found during:** Task 1 compileTestJava после правки entity.
- **Issue:** 8 тестовых файлов создавали `ScheduleItem` через `item.setTeacherId(...)`, часть передавала `TEACHER_ID` в CreateScheduleItemRequest конструкторы с устаревшей 10-аргументной сигнатурой, один тест (`ScheduleViewTest.responseContainsAllFields`) проверял `jsonPath("$...teacherId", is(200))`, один gRPC-тест (`ScheduleGrpcServiceImplTest.getLessonById_*`) вызывал `response.getTeacherId()`.
- **Fix:** во всех 8 файлах вычищены `setTeacherId(...)` и лишние аргументы конструкторов; удалены 2 unused `TEACHER_ID` константы (`ScheduleItemApiTest`, `LessonCancelEventTest`) и 1 константа обновлена (`LessonGenerationIntegrationTest`); удалён `jsonPath teacherId` ассерт и `getTeacherId()` ассерт.
- **Files modified:** 8 test files.
- **Commit:** `a1ca72f`.

## Known Stubs

Нет.

## Threat Flags

Нет нового surface. STRIDE-mapping плана (T-60-01 mitigate) реализован и покрыт тестами:
- **T-60-01 (Broken Access Control on ScheduleItemService.requireHeadmanForGroup):** двухфазная проверка `requestContext.isHeadman()` (JWT claim) + `academicGrpcClient.isHeadman(userId, targetGroupId)` (gRPC round-trip) — уже реализована в коде и теперь явно покрыта 4 unit-тестами `ScheduleItemSecurityTest`. ADMIN bypass подтверждён отдельным кейсом.
- **T-60-06 (Injection on ScheduleItemRepository):** accept — Spring Data JPA использует параметризованные query, конкатенации JPQL нет.

## Self-Check: PASSED

**Created files (existence verified):**
- FOUND: services/schedule-service/schedule-app/src/main/resources/db/migration/V3__drop_teacher_id.sql
- FOUND: services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/item/ScheduleItemEntityTest.java
- FOUND: services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/item/ScheduleItemSecurityTest.java

**Commits (git log --oneline -2):**
- FOUND: a1ca72f feat(60-02): remove ScheduleItem.teacherId (entity + DTO + Flyway V3)
- FOUND: 85e477b test(60-02): add ScheduleItemSecurityTest — headman guard unit coverage

**Tests:** `:services:schedule-service:schedule-app:test` — BUILD SUCCESSFUL. `:services:schedule-service:schedule-app:build` — BUILD SUCCESSFUL. `:services:attendance-service:attendance-app:compileJava` + `compileTestJava` — BUILD SUCCESSFUL.

**Grep verification:**
- `grep -rn "setTeacherId" services/schedule-service/` → 0 совпадений.
- `grep -rn "teacher_id" services/attendance-service/` → 0 совпадений (teacher-journal не читает teacher_id из lessons).
