---
phase: 61-headman-homework-management-ui-homeworkapi-controller-homewo
plan: 02
subsystem: academic-service (entity/DTO/migration + gRPC client)
tags: [academic, flyway, grpc, clock, homework, lesson-binding, d-01, d-02, d-03, d-15]
requires:
  - "Phase 61-01 — rpc ScheduleGrpcService.ResolveLesson (group_id, date, lesson_number)"
provides:
  - "homeworks.lesson_date DATE NOT NULL + homeworks.lesson_number INT NOT NULL (V13)"
  - "idx_homeworks_group_date index"
  - "Homework entity с lessonDate/lessonNumber"
  - "CreateHomeworkRequest с @NotNull @FutureOrPresent lessonDate + @Min(1) @Max(8) lessonNumber"
  - "HomeworkResponse с lessonDate/lessonNumber"
  - "ScheduleGrpcClient.resolveLesson(groupId, date, lessonNumber)"
  - "ScheduleServiceUnavailableException → 503 маппинг"
  - "Clock bean на Europe/Moscow (для D-03)"
affects:
  - services/academic-service/academic-app/build.gradle.kts
  - services/academic-service/academic-app/src/main/resources/application.yml
  - services/academic-service/academic-app/src/main/resources/db/migration/V13__homework_lesson_binding.sql
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Homework.java
  - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/homework/CreateHomeworkRequest.java
  - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/homework/HomeworkResponse.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkAssembler.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/ScheduleGrpcClient.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/ClockConfig.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/ScheduleServiceUnavailableException.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandler.java
tech-stack:
  added:
    - "net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE (перенесено test → main)"
  patterns:
    - "Natural-key binding (group_id, lesson_date, lesson_number) — реюз паттерна attendance-service Phase 60 D-15"
    - "Flyway TRUNCATE + ALTER ADD COLUMN NOT NULL для пустой таблицы (D-02)"
    - "@GrpcClient + withDeadlineAfter(3s) + NOT_FOUND → Optional.empty (паттерн из attendance ScheduleGrpcClient)"
    - "@ExceptionHandler → 503 без stack trace для gRPC-таймаутов (T-61-06)"
    - "Schema-only Testcontainers integration test (information_schema + pg_indexes)"
key-files:
  created:
    - services/academic-service/academic-app/src/main/resources/db/migration/V13__homework_lesson_binding.sql
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/ScheduleGrpcClient.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/ScheduleServiceUnavailableException.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkMigrationIT.java
  modified:
    - services/academic-service/academic-app/build.gradle.kts
    - services/academic-service/academic-app/src/main/resources/application.yml
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Homework.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/homework/CreateHomeworkRequest.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/homework/HomeworkResponse.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkAssembler.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/ClockConfig.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandler.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EventIntegrationTest.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/RestApiIntegrationTest.java
decisions:
  - "ClockConfig уже существовал (BUG-006-6, systemUTC) — обновили zone на Europe/Moscow вместо создания нового бина (конфликт бинов). D-03 требует сравнения по Moscow-дню."
  - "ScheduleServiceUnavailableException — новый класс в exception/, 503 через @ControllerAdvice (mitigation T-61-06: стек не протекает клиенту)."
  - "lessonId поле Entity удалено полностью (колонку drop-нули в V13, ddl-auto: validate упал бы иначе). PLAN-01 уже проверил, что field нигде не читается."
metrics:
  duration: "~10 min"
  completed: "2026-04-15"
  tasks: 2
  commits: 3
---

# Phase 61 Plan 02: Academic Homework Lesson Binding Infrastructure Summary

Подготовлена инфраструктура для PLAN-03 валидаций: миграция V13 привязки ДЗ к паре через natural key `(group_id, lesson_date, lesson_number)`, расширены entity/DTO, ScheduleGrpcClient для вызова PLAN-01 RPC ResolveLesson, Clock bean на Europe/Moscow для тестируемого `LocalDate.now(clock)`.

## Что сделано

### Task 1: Миграция + entity/DTO/Assembler + Migration IT

- **V13 миграция** — `TRUNCATE homeworks, homework_completions` (данных нет, D-02); `DROP COLUMN lesson_id` + `DROP INDEX idx_hw_lesson` (V1 зарезервировал колонку, код её не использовал); `ADD COLUMN lesson_date DATE NOT NULL` + `ADD COLUMN lesson_number INT NOT NULL`; `CREATE INDEX idx_homeworks_group_date ON homeworks(group_id, lesson_date)`.
- **Homework entity** — поле `lessonId` удалено, добавлены `LocalDate lessonDate` + `Integer lessonNumber` (оба NOT NULL). Конструктор расширен с 7 до 9 аргументов.
- **CreateHomeworkRequest** — `@NotNull @FutureOrPresent LocalDate lessonDate` + `@NotNull @Min(1) @Max(8) Integer lessonNumber` (mitigation T-61-04). `UpdateHomeworkRequest` не тронут — привязка ДЗ к паре фиксируется при create (D-04).
- **HomeworkResponse** — добавлены `lessonDate` + `lessonNumber` с сеттерами/геттерами, полноаргументный конструктор расширен.
- **HomeworkAssembler** — `toModel` выставляет новые поля.
- **HomeworkService + EventIntegrationTest** — обновлены вызовы конструктора Homework; `EventIntegrationTest.createHomework_publishesHomeworkPublishedEvent` передаёт будущую дату.
- **HomeworkMigrationIT** — 5 schema-only ассертов (Testcontainers Postgres, information_schema + pg_indexes) для lesson_date NOT NULL, lesson_number NOT NULL, lesson_id отсутствует, idx_hw_lesson отсутствует, idx_homeworks_group_date существует.

### Task 2: gRPC client + application.yml + Clock (+ exception mapping)

- **build.gradle.kts** — `net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE` перенесён из `testImplementation` в `implementation` (production runtime).
- **application.yml** — `grpc.client.schedule-service` с адресом `static://schedule-service:19092` + `negotiation-type: plaintext` (совпадает с настройкой attendance-service).
- **ScheduleGrpcClient** — `@Component` с `@GrpcClient("schedule-service")`; метод `resolveLesson(Long, LocalDate, int) → Optional<LessonResponse>`; `withDeadlineAfter(3s)` (T-61-05); `NOT_FOUND` → `Optional.empty()`, остальные `StatusRuntimeException` → `ScheduleServiceUnavailableException`.
- **ClockConfig** — уже существовал с `Clock.systemUTC()`; переключён на `Clock.system(ZoneId.of("Europe/Moscow"))`. Комментарий расширен объяснением D-03.
- **ScheduleServiceUnavailableException** — новый класс в `exception/`, наследует `RuntimeException`.
- **GlobalExceptionHandler.handleScheduleUnavailable** — маппит исключение в 503 RFC 7807 без stack trace (T-61-06 mitigation).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] RestApiIntegrationTest.testHomeworkCompletionToggle**
- **Found during:** полный regression после Task 2
- **Issue:** тест делает прямой SQL `INSERT INTO homeworks (...)` без новых NOT NULL колонок → нарушение constraint.
- **Fix:** backfill `lesson_date='2040-03-01'`, `lesson_number=1` (согласуется с далёким семестром 2040 из того же теста).
- **Files:** `RestApiIntegrationTest.java`
- **Commit:** a8943fc

**2. [Rule 3 - Blocking] ClockConfig уже существовал**
- **Found during:** Task 2, `ls config/`
- **Issue:** План требовал создать новый `ClockConfig.java`, но он уже есть с Phase 58 (BUG-006-6). Второй `@Bean Clock` сломал бы Spring context.
- **Fix:** обновил существующий бин вместо создания нового: `Clock.systemUTC()` → `Clock.system(ZoneId.of("Europe/Moscow"))` + расширенный javadoc про D-03.
- **Files:** `ClockConfig.java`
- **Commit:** ec9687f

**3. [Rule 2 - Coverage] ScheduleServiceUnavailableException → 503 mapping**
- **Found during:** Task 2
- **Issue:** План создаёт исключение, но не упоминает его маппинг в HTTP. T-61-06 threat mitigation требует явного handler-а чтобы stack trace не утекал клиенту.
- **Fix:** добавил `@ExceptionHandler(ScheduleServiceUnavailableException.class)` в `GlobalExceptionHandler` → 503 с RFC 7807 телом и WARN log серверно.
- **Files:** `GlobalExceptionHandler.java`
- **Commit:** ec9687f

## Verification

- `./gradlew :services:academic-service:academic-app:test --tests '*HomeworkMigrationIT*'` → 5/5 green
- `./gradlew :services:academic-service:academic-app:test --tests '*EntityMappingIntegrationTest*'` → green (ddl-auto: validate проходит после V13)
- `./gradlew :services:academic-service:academic-app:test` (full regression) → **170/170 green**

## Success criteria

- [x] `homeworks.lesson_date NOT NULL`, `homeworks.lesson_number NOT NULL`, `lesson_id` отсутствует, `idx_homeworks_group_date` существует (HomeworkMigrationIT)
- [x] Entity Homework совпадает со схемой (ddl-auto: validate зелёный)
- [x] CreateHomeworkRequest требует lessonDate+lessonNumber с корректной валидацией
- [x] HomeworkResponse отдаёт lessonDate+lessonNumber
- [x] ScheduleGrpcClient + Clock bean инжектятся (Spring context стартует)
- [x] academic-app test suite 170/170

## Self-Check: PASSED

- FOUND: services/academic-service/academic-app/src/main/resources/db/migration/V13__homework_lesson_binding.sql
- FOUND: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/ScheduleGrpcClient.java
- FOUND: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/ScheduleServiceUnavailableException.java
- FOUND: services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkMigrationIT.java
- FOUND commit: a1ff52b (feat(61-02): bind homework to lesson via (date, number) natural key)
- FOUND commit: ec9687f (feat(61-02): wire schedule-service gRPC client + Moscow Clock in academic-service)
- FOUND commit: a8943fc (fix(61-02): backfill lesson_date/lesson_number in legacy test SQL insert)
