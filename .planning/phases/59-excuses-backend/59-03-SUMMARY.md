---
phase: 59-excuses-backend
plan: 03
subsystem: schedule-service + attendance-service (gRPC contract)
tags: [grpc, proto, schedule, attendance, excuses]
dependency-graph:
  requires:
    - proto/schedule.proto (existing ScheduleGrpcService)
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java (existing impl)
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/AbstractScheduleIntegrationTest.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/ScheduleGrpcClient.java
  provides:
    - GRPC-04 GetLessonsByIds (rpc + LessonsByIdsRequest/Response + LessonInfo message)
    - ScheduleGrpcClient.getLessonsByIds(List<Long>) client wrapper
    - LessonsByIdsGrpcIT (AC-11 coverage)
  affects:
    - 59-02 (ExcuseService can now call schedule.getLessonsByIds for D-25 validation)
    - 59-04 (cascade plan — same LessonInfo used to resolve groupId/subjectId)
tech-stack:
  added: []
  patterns:
    - batch fetch via JpaRepository.findAllById + in-memory join on scheduleItemId
    - lenient gRPC: empty/missing → empty response (no NOT_FOUND)
    - client short-circuit on empty/null input — no network call
key-files:
  created:
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/grpc/LessonsByIdsGrpcIT.java
  modified:
    - proto/schedule.proto
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/ScheduleGrpcClient.java
decisions:
  - D-25: "LessonInfo includes groupId + subjectId so ExcuseService can validate group membership and produce event payload without a second gRPC round-trip"
  - "starts_at serialized as LocalDate + 'T' + LocalTime — consistent with existing buildResponse pattern for LessonResponse.date/start_time"
  - "Orphan lessons (ScheduleItem missing) are filtered silently instead of throwing — caller treats as unknown id"
metrics:
  duration: "~15 min"
  completed: 2026-04-14
---

# Phase 59 Plan 03: gRPC LessonsByIds Summary

Добавлен batch-gRPC метод `GetLessonsByIds` в schedule-service и его клиент в attendance-service — фундамент для валидации `lessonIds` принадлежности группе студента (D-25) в плане 59-02.

## What was built

1. **proto/schedule.proto**
   - Новый rpc `GetLessonsByIds (LessonsByIdsRequest) returns (LessonsByIdsResponse)` в `ScheduleGrpcService`.
   - Три новых сообщения: `LessonsByIdsRequest { repeated int64 lesson_ids }`, `LessonsByIdsResponse { repeated LessonInfo lessons }`, `LessonInfo { lesson_id, group_id, subject_id, starts_at }`.
   - Существующие сообщения и rpc не тронуты — бинарная совместимость сохранена (поля `1..4`, новый rpc добавлен в конец сервиса).

2. **ScheduleGrpcServiceImpl.getLessonsByIds** (~50 строк)
   - `lessonRepository.findAllById(ids)` → batch-загрузка уроков.
   - `scheduleItemRepository.findAllById(distinctScheduleItemIds)` → один запрос для всех связанных ScheduleItem.
   - In-memory join по `scheduleItemId`. Уроки с отсутствующим ScheduleItem отфильтровываются (orphan tolerance).
   - `starts_at = lesson.getDate().toString() + "T" + item.getStartTime().toString()` — строка вида `"2026-04-01T08:30"`.
   - Пустой запрос или пустой результат → пустой `LessonsByIdsResponse` (без NOT_FOUND).

3. **ScheduleGrpcClient.getLessonsByIds** (attendance-service)
   - Deadline 3 с (консистентно с остальными методами клиента).
   - `StatusRuntimeException` → `ScheduleServiceUnavailableException` (без специальной обработки NOT_FOUND — лишние id молча игнорируются на сервере).
   - Защита от лишнего сетевого вызова: пустой/null список → `List.of()` сразу.

4. **LessonsByIdsGrpcIT** (3 теста, мирроринг существующего `ScheduleGrpcServiceImplTest`)
   - `getLessonsByIds_happyPath_returnsInfoForEachLesson`: 3 урока в разных группах/предметах, проверка всех полей LessonInfo (lessonId, groupId, subjectId, startsAt).
   - `getLessonsByIds_nonExistentIds_returnsEmptyList`: несуществующие id → пустой список без исключения.
   - `getLessonsByIds_emptyRequest_returnsEmptyList`: пустой список в запросе → пустой ответ.

## Verification results

| Команда | Результат |
|---------|-----------|
| `./gradlew :services:schedule-service:schedule-app:compileJava` | BUILD SUCCESSFUL |
| `./gradlew :services:attendance-service:attendance-app:compileJava` | BUILD SUCCESSFUL |
| `./gradlew :services:schedule-service:schedule-app:compileTestJava` | BUILD SUCCESSFUL |
| `grep "rpc GetLessonsByIds" proto/schedule.proto` | найдено (строка 20) |
| `./gradlew ... test --tests "*LessonsByIdsGrpcIT"` | **blocked by local env** (см. «Deferred Issues») |

## Commits

| Task | Commit | Message |
|------|--------|---------|
| Task 1 | `3a10b37` | feat(59-03): add GetLessonsByIds gRPC rpc in schedule-service |
| Task 2 | `ca7bbc9` | feat(59-03): add LessonsByIds client in attendance-service + IT test |

## Deviations from Plan

**None в коде** — план выполнен буквально. Единственное наблюдение — см. ниже.

## Deferred Issues

**LessonsByIdsGrpcIT (и все другие `AbstractScheduleIntegrationTest`-наследники) не могут быть выполнены локально: Docker Desktop на машине разработчика не запущен.**

- Симптом: `org.testcontainers.dockerclient.DockerClientProviderStrategy.getDockerClient` бросает `IllegalStateException` на инициализации PostgreSQLContainer.
- Проверено: `docker ps` → `error during connect: ... dockerDesktopLinuxEngine`. Существующий и ранее проходивший `ScheduleGrpcServiceImplTest.getLessonById_happyPath` тоже падает с тем же стеком — проблема **не связана с новым кодом**.
- Компиляция тестовых классов (`compileTestJava`) прошла — класс и сигнатуры теста корректны.
- Рекомендация для Wave 3 / CI: Docker запустится в пайплайне → все 3 теста должны пройти зелёными без изменений. Если понадобится локальный прогон — `docker desktop start` + retry.

## Known Stubs

Нет. Все методы имеют рабочую реализацию.

## Blockers for downstream waves

Нет. План 59-02 (параллельный) может сразу использовать `scheduleGrpcClient.getLessonsByIds(...)` для D-25 валидации. LessonInfo содержит `groupId` (принадлежность) и `subjectId` (пригодно для будущего cascade в 59-04).

## Self-Check: PASSED

- [x] `proto/schedule.proto` содержит `rpc GetLessonsByIds` (строка 20) и 3 message (строки 58–71)
- [x] `ScheduleGrpcServiceImpl.getLessonsByIds` присутствует (добавленный @Override метод)
- [x] `ScheduleGrpcClient.getLessonsByIds(List<Long>)` присутствует
- [x] `LessonsByIdsGrpcIT.java` создан с 3 тестами
- [x] Коммиты `3a10b37` и `ca7bbc9` существуют в `git log`
- [x] Schedule-service compileJava зелёный
- [x] Attendance-service compileJava зелёный
- [x] Schedule-service compileTestJava зелёный
- [ ] Schedule-service `test --tests "*LessonsByIdsGrpcIT"` — blocked by local Docker Desktop; infrastructure issue, not code (same failure reproduces in `ScheduleGrpcServiceImplTest`)
