# M05 Checklist

Атомарные задачи в порядке выполнения. Группа = логический коммит.

## Группа 1 — Composite indexes + perf baseline (P2-10/1)

_Уточнено 2026-04-20 по результатам аудита схемы — см. DECISIONS D1-D3._

- [x] Baseline: seed-dataset на 10k+ rows в dev-compose (lessons,
      late_checkin_requests, teacher_subject_groups, homeworks). Скрипт
      `docs/milestones/M05-performance/seed-perf.sql` или Python-script.
- [x] EXPLAIN ANALYZE «before» для 4 hot queries — записано в
      `docs/performance-indexes.md`:
  1. `LessonRepository.findByScheduleItemIdInAndDateBetweenAndStatusIn`
     (schedule) — Hash Join via idx_lessons_date, 0.764ms.
  2. `LateCheckinRepository.findByGroupIdAndStatusOrderByCreatedAtAsc`
     (attendance Mongo) — COLLSCAN, 6000 docs examined, 2ms.
  3. `TeacherSubjectGroupRepository.findByGroupIdAndSemesterId` —
     Bitmap Scan idx_tsg_group + filter 60→30, 0.339ms.
  4. `HomeworkRepository.findByGroupIdAndSemesterId` —
     Bitmap Scan idx_homeworks_group_date + filter 60→30, 0.388ms.
- [x] Flyway миграция `schedule_db` V12: partial
      `idx_lessons_item_date ON lessons (schedule_item_id, date) WHERE status != 'cancelled'`.
      (D1 — `group_id` в `lessons` нет, композит на `schedule_item_id`
      покрывает фактический `IN + BETWEEN`.)
- [x] `schedule_one_off_lessons` — **no-op**, V4 уже содержит
      `UNIQUE (group_id, date, lesson_number)`. Зафиксировано в
      `performance-indexes.md` в секции «Деферренные индексы».
- [x] Mongo index для `late_checkin_requests`: compound
      `{group_id:1, status:1, created_at:1}` в `MongoConfig.initIndexes()`
      (programmatic — проектная convention). Closes 04 P2-9.
- [x] Flyway миграция `academic_db` V17:
      `idx_tsg_group_semester (group_id, semester_id)` +
      `idx_hw_group_semester (group_id, semester_id)`. (D2 — `user_groups`
      таблицы нет; индексы на реальных hot queries.)
- [x] `(group_id, lesson_id)` на `attendances` — **отложено** (D3),
      no-op в M05. Зафиксировано в `performance-indexes.md` в секции
      «Деферренные индексы».
- [x] EXPLAIN «after» в `performance-indexes.md`. Q2/Q3: Bitmap Scan
      точно через composite, Filter Rows Removed → 0. Q4 Mongo: IXSCAN,
      docsExamined=nReturned=120 (50× reduction). Q1 schedule: planner
      предпочёл старый план на seed-объёме — индекс «в резерве» для v0.1
      нагрузки.
- [x] Integration-тесты regression guard (per-сервис), query < 50ms:
      `LessonPerformanceIT` (Q1 best=8ms), `AcademicPerformanceIT`
      (Q2 best=8ms, Q3 best=8ms), `LateCheckinPerformanceIT` (Q4
      best=10ms). Все зелёные.

## Группа 2 — Preventive N+1 guard (P2-10/2, scope D5)

_Переформулировано 2026-04-20 после системного аудита — см. DECISIONS
D5 и NOTES (секция «Группа 2 аудит»). N+1 рисков в текущем коде НЕ
обнаружено (все entity используют FK как Long, без JPA relations).
Группа переключена на preventive-only: ArchUnit guardrail + reference
pattern + docs._

- [x] Audit repository-методов (Explore-агент): все 30+ JPA-методов в
      schedule + academic возвращают entity с FK как Long (без
      `@ManyToOne`/`@OneToMany`). N+1 невозможен by design. Attendance —
      Mongo (не JPA). Результаты в NOTES.md.
- [x] ArchUnit rule NEW-143 в schedule-service + academic-service
      (`arch/RepositoryNPlusOneGuardTest.java`). Две подправила:
      (1) entitiesMustNotUseJpaRelations — invariant v0.0.0;
      (2) repositoriesReturningCollectionsMustGuardNPlusOne — guard
      на будущее (Pageable | @EntityGraph | *Projection | JOIN FETCH).
- [x] Reference projection — `LessonDetailsProjection` +
      `LessonRepository.findLessonDetails(id)` (single-detail, 10
      полей через JOIN schedule_items). Служит whitelist-образцом для
      ArchUnit.
- [x] Sanity-verify ArchUnit: временный `@ManyToOne` в Lesson →
      `entitiesMustNotUseJpaRelations` упал с понятным сообщением
      («Поле Lesson.scheduleItemRelation помечено JPA relation...»).
      Edit откачен, build зелёный.
- [x] `docs/architecture.md` §11 — раздел «JPA convention: FK как Long,
      без entity relations (NEW-143)» с обоснованием +
      образцом паттерна `collect itemIds → findByIdIn` +
      подробным action-plan «когда relation всё-таки нужна».
- [x] `./gradlew test` зелёный: schedule 111/111, academic 201/201,
      attendance 158/158.

## Группа 3 — Redis cache дополнения для справочников и RBAC (P2-10/3)

_Scope переформулирован 2026-04-20 по результатам аудита — см.
DECISIONS D6. Caffeine НЕ вводится. Academic-service уже имеет Redis
`CacheManager` + 5 namespaces из ранних фаз (59/60). M05 добивает
пробелы: rbac, subject, metrics, docs._

### Уже сделано ранее (фиксируем как baseline)

- [x] `@EnableCaching` + Redis `CacheManager` — `CacheConfig.java:24-94`.
- [x] Namespaces `groups` (5м), `group_members` (5м), `users` (5м),
      `active_semester` (10м), `campus_geofence` (60м).
- [x] `@Cacheable` на `AcademicReadService` — `fetchGroup`,
      `fetchGroupMembers`, `fetchActiveSemester`, `fetchCampusGeofence`,
      `fetchUserById`.
- [x] `@CacheEvict` на `UserService` (updateUser/patchUser/archiveUser/
      transferStudent), `GroupService` (updateGroup/deleteGroup),
      `SemesterService.activateSemester` (allEntries).
- [x] Программатический `cacheManager.getCache("groups"/"group_members")
      .evict(groupId)` в `UserService.patchUser:225-233` при смене
      `is_headman` + в `transferStudent:287-296` при смене группы.

### Добавляется в M05 Группе 3

- [x] `CacheConfig`: добавить namespaces `rbac` (TTL 1м) и `subject`
      (TTL 10м). `application.yml` → `cache.ttl.rbac=PT1M`,
      `cache.ttl.subject=PT10M`.
- [x] `AcademicReadService.isHeadmanOf(Long userId, Long groupId)` —
      новый метод с `@Cacheable(value="rbac", key="#userId + ':' + #groupId")`.
- [x] `AcademicGrpcServiceImpl.isHeadman` — переключить на вызов
      `academicReadService.isHeadmanOf(...)`. Удалить комментарий
      «Not cached (per D-02)», заменить на «Cached via rbac namespace
      (M05 D6)».
- [x] `SubjectService.getSubject` — `@Cacheable(value="subject", key="#id")`.
- [x] `SubjectService.updateSubject` / `deleteSubject` —
      `@CacheEvict(value="subject", key="#id")`.
- [x] `UserService.patchUser` — программатическое eviction `rbac`
      cache при смене `is_headman` или `group_id`. Ключ —
      `#userId + ':' + #oldGroupId` и `#userId + ':' + #newGroupId`
      (evict обоих при переходе). Рядом с существующим groups/group_members
      eviction (:225-233).
- [x] `UserService.transferStudent` — evict `rbac` для старого и
      нового groupId аналогично.
- [~] **Deferred:** `MetricsCacheManagerDecorator` — попытка wrap
      `CacheManager` для hit/miss counter'ов ломает namespace-specific
      TTL в `RedisCacheManager` (reproducible regression в
      `getActiveSemester_ttlMatchesConfiguredValue`). Root cause
      требует глубокого изучения pre-configured cache vs dynamic
      creation в Spring. Отложено в backlog (см. NOTES секция
      «Группа 3 deferred»). Альтернатива — `@Aspect` подход,
      зафиксирован в future-ideas.
- [x] Integration-тест `RbacCacheIT`: 4 теста — isHeadman_secondCall
      (Redis key presence), patchUser_revoke (programmatic evict),
      isHeadman_negative (false кешируется), ttlMatchesConfiguredValue
      (TTL 55-60s). Counter-based hit-rate пришлось заменить на key-
      presence из-за deferred metrics биндинга (см. выше).
- [x] `docs/caching-strategy.md` (NEW-144): TTL matrix (7 namespaces),
      invalidation triggers (declarative + programmatic), consistency
      trade-offs (Q13b race activateSemester), Redis-as-L1 rationale
      (D6), observability секция с deferred metrics, migration plan
      на managed Redis / Sentinel / Caffeine L1+L2 гибрид.

## Группа 4 — Batch endpoints (P2-10/4)

_Scope зауженный согласно D8 — см. DECISIONS. В M05 делаем ядро
(attendance headman bulk-mark). Homeworks/batch и web-panel weekly-
journal bulk-read отложены в backlog._

- [x] Attendance: `POST /attendance/marks/batch` — body `@Valid @Size(min=1, max=100) List<MarkBatchItem>`, pseudo-atomic (validation-first). HTTP 200 + `MarkBatchResponse { items, processed }`.
- [~] **Deferred (D8):** Academic `POST /academic/homeworks/batch` partial-success (admin-импорт homework — редкий flow, ROI низкий, отложено).
- [x] Validation: `@Valid @Size` на List + `@NotNull` на каждом поле `MarkBatchItem`. Spring кидает MethodArgumentNotValidException → existing GlobalExceptionHandler → RFC 7807 ErrorResponse с fieldErrors[].
- [x] Frontend PWA: headman `handleBulkMark` → `POST /marks/batch` вместо `for (...) await PUT`. Один toast (success/error), invalidation TanStack Query ключей.
- [~] **Deferred (D8):** Frontend web-panel `HeadmanWeeklyJournal` — использует `forkJoin` для bulk-read (не bulk-mark, HTTP/2 multiplexing даёт параллелизм on-wire). ROI < 2×, отложено.
- [x] `docs/api-error-conventions.md` — раздел «Batch endpoint conventions» (NEW-145): pseudo-atomic rationale, schema, client error handling.
- [x] Unit-тест `MarkingServiceTest.markBatch_*`: 5 сценариев — happy path (3 students 1 lesson = 1 gRPC + 1 groupMembers + 3 upsert), not-headman rejected, student-not-in-group rejected, CANCELLED rejected, wrong-group lesson rejected. Все зелёные.

## Группа 5 — Single-pass accumulators + SQL pagination (P2-10/5, D9)

_Scope уточнён 2026-04-20 по результатам аудита — см. DECISIONS D9.
Mongo `$group` pipeline для ReportService блокируется
`filterExistingLessons` cross-service invariant'ом (деферрено NEW-146).
Реальные hotspot'ы: single-pass refactor в ReportService +
in-memory pagination OOM-risk в LessonService._

- [x] Audit `*Service.java` на `.collect(toList())` (Explore-агент) —
      найдено 5 hotspot'ов в `ReportService` (attendance) + 1 в
      `LessonService.getLessonsForGroup` (schedule). `AttendanceStatsService`
      и `ExcuseAnalyticsService` **не существуют** — логика встроена
      в `ReportService`.
- [x] `ReportService.getStudentStats` — single-pass `Map<Long, int[]>`
      accumulator (total/attended/absent/excused) в одном `for`-loop
      вместо `groupingBy` + 3× `stream.filter.count` на каждый subject.
- [x] `ReportService.buildOverall` — single-pass `int` counter'ы
      вместо 3× `stream.filter.count` на одном списке.
- [x] `ReportService.buildWeekly` — single-pass `TreeMap<Integer, int[]>`
      + `sampleDates` для ISO-week resolve вместо `groupingBy` +
      3× `stream.filter.count` на каждую неделю. O(N) вместо O(K×3N).
- [x] `ReportService.buildTopMissed` — **no-op** (уже single-pass
      `toMap` с merge function, оптимально).
- [~] **Deferred (D9 / NEW-146):** `AttendanceStatsService`/
      `ExcuseAnalyticsService` SQL `GROUP BY` — сервисов не существует,
      а Mongo `$group` в ReportService блокируется
      `filterExistingLessons` cross-service invariant'ом. Варианты
      решения (denormalization, materialized view) — в
      `docs/future-ideas.md` NEW-146.
- [x] `LessonService.getLessonsForGroup` — переписан на SQL
      `LIMIT/OFFSET` через Spring Data `Pageable` с native `countQuery`.
      Новый метод `LessonRepository.pageByScheduleItemIdInAndDateBetweenAndStatusIn`.
      Устраняет OOM-risk на 2000+ lessons/semester.
- [~] **Deferred (D9):** `LessonService.findOneOffLessons` — метода не
      существует. 03 P2-5 разрешён через `getLessonsForGroup` SQL pagination.
- [~] **Deferred (v9+):** Admin-dashboard sparklines (10 P2-4) —
      отдельный scope (NEW-94).
- [x] Existing tests (attendance report + schedule lesson) зелёные —
      подтверждают correctness accumulator рефактора и SQL pagination.
- [x] `docs/future-ideas.md` NEW-146: Mongo aggregation
      pipeline blocker + 3 варианта решения + audit-checklist
      для `.collect(toList())` агрегаций в PR-review.

## Группа 6 — HikariCP connection pool (P2-10/6)

- [ ] `application.yml` academic/schedule/attendance — `spring.datasource.hikari.{maximum-pool-size:20, minimum-idle:5, connection-timeout:5000, idle-timeout:600000, max-lifetime:1800000, leak-detection-threshold:60000}`.
- [ ] Prometheus scrape проверка: `hikaricp_connections_active` / `hikaricp_connections_pending` в `/actuator/prometheus`.
- [ ] Alert rule в `infra/prometheus/rules/service-health.yml` — `HikariPoolExhaustion` (pool > 80% for 5m, warning).
- [ ] `docs/connection-pool-tuning.md` (NEW-147) — формула `cpu_cores × 2 + effective_disk_spindles`, текущие значения, когда пересматривать (scale-out, read-replicas).
- [ ] Smoke-тест на dev: 30 concurrent HTTP-запросов — pool не исчерпан.

## Группа 7 — Cleanup push-subs + retention audit (P2-10/7)

- [ ] Flyway V{N+1} на `attendance_db` или где живёт push: `ALTER TABLE push_subscriptions ADD COLUMN last_seen TIMESTAMPTZ NOT NULL DEFAULT NOW()`.
- [ ] `WebPushDeliveryService` — update `last_seen = NOW()` на successful send. На `410 Gone` — сразу delete (уже было до M05).
- [ ] `@Scheduled(cron="0 0 3 * * SUN") @SchedulerLock(name="cleanupStalePushSubs") cleanupStalePush()` в notification-web: DELETE > 90 дней.
- [ ] Audit auth-service Redis `refresh:<hash>`: подтвердить `EX=604800` (7d). Grep + тест. Если EX отсутствует — фикс.
- [ ] `docs/data-retention-policy.md` (NEW-148): таблица «что → retention → mechanism».
- [ ] Integration-тест: seed dead subs (last_seen=now()-100d) + run scheduled → удалены.

## Группа 8 — gRPC hot-path (P2-10/8)

- [ ] `CheckinService.checkin` — `CompletableFuture.supplyAsync(..., taskExecutor)` параллельно для `scheduleClient.getActiveLesson` + `academicClient.*`. `.join()` + `.get()` с timeout.
- [ ] Явный `taskExecutor` bean (либо reuse `TaskExecutor` из Spring Boot autoconfig).
- [ ] `grpc-micrometer` dependency + autoconfig. `@GrpcClientInterceptor` применяется на каждый клиент.
- [ ] Deadline audit: `.withDeadline(Deadline.after(3, SECONDS))` на каждый gRPC-call в 6 сервисах (сейчас 3 сервиса имеют gRPC clients: academic, schedule, attendance).
- [ ] CI-lint NEW-149: ArchUnit rule — gRPC-client `*Grpc.newBlockingStub(...)` method должен быть followed by `.withDeadline(...)` в том же chain. Или regex-lint если ArchUnit не достанет.
- [ ] Grafana dashboard «gRPC latency by method» — `grpc_client_processing_duration_seconds` histogram, p50/p95/p99 панели, error-rate panel.
- [ ] Integration-test: `CheckinService.checkin` latency улучшилась (parallel vs sequential).

## Группа 9 — Audit (bug-hunter + code-reviewer + security)

- [ ] Полный `./gradlew build` — всё зелёное (unit + integration + ArchUnit + CI-lint).
- [ ] `bug-hunter` на diff M05 — фокус: race conditions в @Cacheable (stale read после update), transactional boundaries для batch, deadline propagation.
- [ ] `security-auditor` на batch endpoints (input validation, @Size limits, PII в errors), Caffeine cache leak (sensitive data в `@Cacheable("rbac")` ключах).
- [ ] `code-reviewer` на repository audit + caching-strategy.md + performance-indexes.md.
- [ ] Hot-patches → отдельный коммит.

## Группа 10 — Documentation + закрытие milestone

- [ ] `docs/performance-indexes.md` (новый, ~150 строк).
- [ ] `docs/caching-strategy.md` (новый, ~120 строк).
- [ ] `docs/connection-pool-tuning.md` (новый, ~80 строк).
- [ ] `docs/data-retention-policy.md` (новый, ~80 строк).
- [ ] `docs/architecture.md` — разделы «Caching layer» + «Batch operations» + обновлённый HikariCP sizing.
- [ ] `CHANGELOG.md [Unreleased]` — M05 секция Added/Changed.
- [ ] `CLAUDE.md` — статус M05 → ✅ + дата.
- [ ] `docs/milestones/README.md` — M05 ✅ + дата.
- [ ] PLAN.md → Post-mortem секция.
- [ ] `git tag v0.0.0-alpha.6` (локально, без push).
- [ ] Hand-off для M06/M07/M08 в NOTES.md.

---

_Если задача превращается в 6+ часов — разрежь._
