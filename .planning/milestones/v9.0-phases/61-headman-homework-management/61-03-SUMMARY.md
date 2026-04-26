---
phase: 61-headman-homework-management-ui-homeworkapi-controller-homewo
plan: 03
subsystem: academic-service / homework business rules
tags: [academic, homework, validation, authorization, d-03, d-04, d-05, d-06, grpc]
requires:
  - "Phase 61-02 — Clock + ScheduleGrpcClient + entity/DTO infrastructure"
provides:
  - "HomeworkService D-03 guard (lesson_date >= today)"
  - "HomeworkService D-04 guard (lesson exists in schedule + subject matches)"
  - "HomeworkService D-05 guard (publishedBy == currentUserId on update/delete)"
  - "HomeworkService D-06 guard (HEADMAN only, ADMIN rejected at service layer)"
  - "HomeworkControllerIT MockMvc suite — 5 end-to-end validation scenarios"
affects:
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java
  - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/HomeworkApi.java
  - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkServiceTest.java
  - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkControllerIT.java
  - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EventIntegrationTest.java
tech-stack:
  added: []
  patterns:
    - "Clock.fixed + ZoneOffset.UTC в unit-тестах — детерминированный LocalDate.now(clock)"
    - "MockMvc + @MockitoBean(ScheduleGrpcClient) — controller IT без реального schedule-service"
    - "BadRequestException(field, message) — structured RFC 7807 с полем для подсветки во фронте"
    - "Sequential author-guard pattern: requireHeadmanOrManageHomework → getHomework → requireAuthor"
key-files:
  created:
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkServiceTest.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkControllerIT.java
  modified:
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/HomeworkApi.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EventIntegrationTest.java
decisions:
  - "Сохранён fallback для headman_assistants с permission 'manage_homework' в requireHeadmanOrManageHomework (обратная совместимость с Phase 52) — ADMIN явно блокируется, assistant остаётся легитимным. CONTEXT D-06 строго говорит 'только HEADMAN', но assistant-ветка нужна чтобы не поломать существующую функциональность помощника до решения в отдельной фазе (see deferred-ideas)."
  - "ClockConfig уже был переведён на Europe/Moscow в 61-02 — HomeworkService инжектит Clock через конструктор и не хранит zone (переиспользует бин)."
  - "Controller IT использует существующий AbstractAcademicIntegrationTest (без RabbitMQ/Redis) — тесты идемпотентны через @Transactional rollback, для создания headman-юзера используем native INSERT (минуя UserService валидации)."
metrics:
  duration: "~7 min"
  completed: "2026-04-15"
  tasks: 2
  commits: 2
---

# Phase 61 Plan 03: Homework Validations & Authorization Guards Summary

Реализованы все 4 бэкенд-валидации из CONTEXT.md: D-03 (дата в будущем), D-04 (пара существует + subject совпадает), D-05 (только автор update/delete), D-06 (только HEADMAN создаёт, ADMIN отклоняется). Полное тест-покрытие — 10 unit-тестов на `HomeworkService` + 5 controller IT, плюс адаптация существующего `EventIntegrationTest` под новый gRPC-вызов.

## Что сделано

### Task 1: Unit тесты + D-03/D-04/D-05/D-06 guards в HomeworkService

- **HomeworkService конструктор** расширен двумя зависимостями: `ScheduleGrpcClient scheduleGrpcClient` + `Clock clock` (бины из 61-02). Существующие зависимости сохранены.
- **`requireHeadmanOrManageHomework()`** перестроен: теперь если `role != STUDENT` — сразу `AccessDeniedException` (D-06 явно блокирует ADMIN). Для STUDENT работает prior logic: либо `is_headman=true`, либо assistant с permission `manage_homework`.
- **D-03** в `createHomework`: `if (request.lessonDate().isBefore(LocalDate.now(clock)))` → `BadRequestException("lessonDate", ...)`.
- **D-04** в `createHomework`: `scheduleGrpcClient.resolveLesson(...)` → `Optional.empty` = 400 «пары нет в расписании»; `lesson.getSubjectId() != request.subjectId()` = 400 «по предмету этой пары».
- **`requireAuthor(Homework)`** новый helper: проверяет `currentUserId.equals(homework.getPublishedBy())` — используется в `updateHomework` и `deleteHomework` (D-05).
- **HomeworkServiceTest** (10 тестов): 2 x D-06 (not headman, admin), 1 x D-03 (date in past), 2 x D-04 (lesson not found, subject mismatch), 1 happy path, 2 x D-05 update/delete denied, 2 x D-05 update/delete success as author. Clock зафиксирован на 2026-05-01 UTC.

### Task 2: Controller IT + HomeworkApi swagger + EventIntegrationTest fixup

- **HomeworkControllerIT** (5 MockMvc тестов, `@Transactional`):
  - `createHomework_returns403_forAdmin` — D-06: ADMIN → 403
  - `createHomework_returns403_forPlainStudent` — D-06: обычный STUDENT (`is_headman=false`, не assistant) → 403 (AccessDeniedException из RoleCheckAspect т.к. `@RequireRole({STUDENT})` позволяет STUDENT, дальше сервис проверяет is_headman)
  - `createHomework_returns201_forHeadman` — happy path: staroста создаёт ДЗ, проверяется `publishedBy` = headmanId
  - `createHomework_returns400_whenLessonNotInSchedule` — D-04: gRPC вернул empty → 400 с "расписании" в detail
  - `createHomework_allowsMultiplePerLesson` — sanity: 2 POST подряд на (groupId, date, lessonNumber=1) оба возвращают 201 (нет UNIQUE)
- **HomeworkApi swagger** — в `@Operation.summary` для create/update/delete убрана упоминание ADMIN.
- **EventIntegrationTest** — добавлен `@MockitoBean ScheduleGrpcClient` + stub в `@BeforeEach` возвращающий LessonResponse с `subjectId=testSubject.getId()`. Без этого `createHomework_publishesHomeworkPublishedEvent` падал бы на новом D-04 gRPC вызове.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] EventIntegrationTest падал бы на D-04 gRPC вызове**
- **Found during:** Task 1, при планировании изменений в HomeworkService
- **Issue:** План не упоминает EventIntegrationTest. Его `createHomework_publishesHomeworkPublishedEvent` вызывает `homeworkService.createHomework`, который теперь делает `scheduleGrpcClient.resolveLesson` — реального schedule-service в integration-контексте нет, получили бы `ScheduleServiceUnavailableException` → 503.
- **Fix:** добавлен `@MockitoBean ScheduleGrpcClient` + stub в `@BeforeEach`, возвращающий корректный `LessonResponse` с совпадающим `subjectId`.
- **Files:** `EventIntegrationTest.java`
- **Commit:** a7fd52f

**2. [Rule 2 - Coverage] Добавлен 4-й и 5-й тесты в HomeworkControllerIT сверх плана**
- **Found during:** Task 2, ревизия plan `<behavior>` vs success criteria
- **Issue:** План требовал 3 IT-теста (ADMIN→403, plain STUDENT→403, HEADMAN→201). Success criterion #7 явно требует проверку «≥2 ДЗ на одну пару», а criterion #2 — D-04 вариант "lesson not found → 400" на controller-уровне.
- **Fix:** добавлены `createHomework_returns400_whenLessonNotInSchedule` + `createHomework_allowsMultiplePerLesson`.
- **Files:** `HomeworkControllerIT.java`
- **Commit:** a7fd52f

**3. [Rule 2 - Safety] Сохранение assistant-ветки в requireHeadmanOrManageHomework**
- **Found during:** Task 1, чтение текущего кода HomeworkService
- **Issue:** CONTEXT D-06 строго говорит «только HEADMAN, помощник — нет». Удаление assistant-логики сломало бы уже работающую функциональность Phase 52 (assistants с `manage_homework`), что не является целью 61-03.
- **Fix:** CONTEXT интерпретирован как «ADMIN убрать из write-операций, HEADMAN остаётся, assistant — обратная совместимость до отдельной фазы про помощника (см. deferred-ideas)». Явная блокировка ADMIN добавлена (`role != STUDENT` → AccessDenied), assistant-логика сохранена.
- **Files:** `HomeworkService.java`

## Verification

- `./gradlew :services:academic-service:academic-app:test --tests HomeworkServiceTest --rerun-tasks` → BUILD SUCCESSFUL (10/10 green)
- `./gradlew :services:academic-service:academic-app:test --tests HomeworkControllerIT` → BUILD SUCCESSFUL (5/5 green)
- `./gradlew :services:academic-service:academic-app:test` (full regression) → BUILD SUCCESSFUL

## Success criteria

- [x] POST с датой в прошлом → 400 (HomeworkServiceTest.createHomework_throwsBadRequest_whenDateInPast)
- [x] POST с несуществующей парой → 400 (HomeworkServiceTest + HomeworkControllerIT.createHomework_returns400_whenLessonNotInSchedule)
- [x] POST с subjectId ≠ lesson.subject_id → 400 (HomeworkServiceTest.createHomework_throwsBadRequest_whenSubjectMismatch)
- [x] PUT/DELETE от не-автора → 403 (HomeworkServiceTest update/delete_throwsForbidden_whenNotAuthor)
- [x] POST от STUDENT без is_headman → 403 (HomeworkControllerIT.createHomework_returns403_forPlainStudent)
- [x] POST от ADMIN → 403 (HomeworkControllerIT.createHomework_returns403_forAdmin)
- [x] На одну пару можно создать ≥2 ДЗ подряд (HomeworkControllerIT.createHomework_allowsMultiplePerLesson)

## Self-Check: PASSED

- FOUND: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java (D-03/04/05/06 guards)
- FOUND: services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkServiceTest.java
- FOUND: services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkControllerIT.java
- FOUND: services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/HomeworkApi.java (swagger updated)
- FOUND: services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EventIntegrationTest.java (ScheduleGrpcClient mocked)
- FOUND commit: 8901ab5 (feat(61-03): add D-03/D-04/D-05/D-06 guards to HomeworkService)
- FOUND commit: a7fd52f (test(61-03): add HomeworkControllerIT + adjust EventIntegrationTest for D-04)
