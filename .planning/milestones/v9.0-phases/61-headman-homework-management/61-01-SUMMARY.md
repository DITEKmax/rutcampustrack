---
phase: 61-headman-homework-management-ui-homeworkapi-controller-homewo
plan: 01
subsystem: schedule-service / gRPC
tags: [grpc, proto, schedule, lesson-resolve, d-04]
requires: []
provides:
  - "rpc ScheduleGrpcService.ResolveLesson (group_id, date, lesson_number) -> LessonResponse"
  - "LessonRepository.findByGroupDateAndLessonNumber native query"
affects:
  - proto/schedule.proto
  - services/schedule-service/schedule-app
tech-stack:
  added: []
  patterns:
    - "Native SQL с status::text IN (...) для фильтрации LessonStatus enum (совместимость PG custom enum)"
    - "JOIN lessons × schedule_items для резолва по natural key"
    - "ResourceNotFoundException → @GrpcAdvice маппит на Status.NOT_FOUND"
key-files:
  created:
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcResolveLessonIT.java
  modified:
    - proto/schedule.proto
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java
decisions:
  - "IT-тесты следуют pattern ScheduleGrpcServiceImplTest (direct invocation + mock StreamObserver) — assertion на ResourceNotFoundException, а не StatusRuntimeException (deviation Rule 3: consistency с существующим кодом)"
  - "Добавлен 4-й тест (date без пар) сверх плановых 3 — coverage для типичного scenario"
metrics:
  duration: "~3 min"
  completed: "2026-04-15"
  tasks: 1
  commits: 1
---

# Phase 61 Plan 01: Schedule gRPC ResolveLesson Summary

Добавлен gRPC RPC `ResolveLesson(group_id, date, lesson_number)` в schedule-service — server-side часть D-04: academic-service будет вызывать его для проверки существования пары перед созданием/апдейтом ДЗ.

## Что сделано

- **proto/schedule.proto** — добавлен `rpc ResolveLesson` + message `ResolveLessonRequest`. Реюзается существующий `LessonResponse` (уже содержит subject_id, status, lesson_number).
- **LessonRepository** — native query `findByGroupDateAndLessonNumber` c JOIN на `schedule_items`, фильтром по статусам `planned/active/closed` (cancelled исключается) и `LIMIT 1`.
- **ScheduleGrpcServiceImpl.resolveLesson** — override по pattern `getLessonById`: парсит date, резолвит lesson, резолвит scheduleItem, `buildResponse`. NOT_FOUND через `ResourceNotFoundException`, который маппится на `Status.NOT_FOUND` через существующий `@GrpcAdvice`.
- **ScheduleGrpcResolveLessonIT** — 4 integration-теста (Testcontainers Postgres через `AbstractScheduleIntegrationTest`):
  - happy path: пара существует → возвращает LessonResponse с subject_id, lesson_number, status=planned
  - wrong group → ResourceNotFoundException
  - lesson cancelled → ResourceNotFoundException (status filter)
  - date without lesson → ResourceNotFoundException

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Тесты ассертят ResourceNotFoundException вместо StatusRuntimeException**
- **Найдено при:** Task 1, написание IT-тестов
- **Issue:** План говорит "NOT_FOUND → StatusRuntimeException code=NOT_FOUND". Но тесты используют прямой вызов `grpcService.method(request, observer)` без in-process gRPC канала (pattern `ScheduleGrpcServiceImplTest`) — `@GrpcAdvice` срабатывает только при прохождении через gRPC server. Существующие тесты ассертят `ResourceNotFoundException`.
- **Fix:** Тесты ассертят `ResourceNotFoundException.class` по pattern существующих `ScheduleGrpcServiceImplTest.getActiveLesson_noActiveLessons_throwsResourceNotFoundException` и `getLessonById_nonExistentId_throwsResourceNotFoundException`. Маппинг на `Status.NOT_FOUND` через `@GrpcAdvice` уже покрыт существующей инфраструктурой.
- **Commit:** cdd9228

**2. [Rule 2 - Coverage] Добавлен 4-й тест (date without lesson)**
- **Найдено при:** Task 1
- **Issue:** План просил 3 теста (happy, wrong group, cancelled). Сценарий "дата, на которой пары нет" явно упомянут в `<behavior>`, но не был отдельным тестом.
- **Fix:** Добавлен `resolveLesson_throwsNotFound_whenDateHasNoLesson` для полноты coverage.
- **Commit:** cdd9228

## Verification

- `./gradlew :services:schedule-service:schedule-app:test --tests '*ScheduleGrpcResolveLessonIT*'` → BUILD SUCCESSFUL (4/4 зелёных)
- `./gradlew :services:schedule-service:schedule-app:test` (полный regression) → BUILD SUCCESSFUL, существующие тесты проходят

## Success criteria

- [x] proto содержит `rpc ResolveLesson` + `ResolveLessonRequest`
- [x] `LessonRepository.findByGroupDateAndLessonNumber` работает
- [x] `ScheduleGrpcServiceImpl.resolveLesson` override возвращает LessonResponse или бросает NOT_FOUND
- [x] Integration-тесты покрывают happy / wrong group / cancelled / missing date
- [x] `:schedule-app:test` зелёный

## Self-Check: PASSED

- FOUND: proto/schedule.proto (rpc ResolveLesson)
- FOUND: services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java (findByGroupDateAndLessonNumber)
- FOUND: services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java (resolveLesson)
- FOUND: services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcResolveLessonIT.java
- FOUND commit: cdd9228 (feat(61-01): add ResolveLesson gRPC RPC for natural-key lesson lookup)
