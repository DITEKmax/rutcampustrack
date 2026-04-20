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

- [x] `application.yml` academic/schedule: `hikari.{maximum-pool-size:20, minimum-idle:5, connection-timeout:5000, idle-timeout:600000, max-lifetime:1800000, leak-detection-threshold:60000}`. auth-service меньше (pool=10, idle=3 — read-only login). attendance — no-op (MongoDB).
- [x] Prometheus scrape `hikaricp_connections_*` автоматически через Micrometer + spring-boot-actuator (M04 baseline).
- [x] Alert rule `HikariPoolExhaustion` в `infra/prometheus/rules/service-health.yml` — `(active/max) > 0.80 for 5m`, severity warning.
- [x] `docs/connection-pool-tuning.md` (NEW-147) — формула `cpu_cores × 2 + spindles`, текущие значения, триггеры пересмотра (HikariPoolExhaustion firing / pending>0 / scale-out / read-replicas), smoke-тест в документе.
- [~] **Manual smoke-тест** (30 concurrent HTTP): процедура описана в docs/connection-pool-tuning.md, выполнение отложено до production-deploy (не блокирует M05 — integration-тесты 3 сервисов зелёные с новым pool).

## Группа 7 — Cleanup push-subs + retention audit (P2-10/7)

_Scope уточнён 2026-04-20 по результатам аудита — см. DECISIONS D10.
`push_subscriptions` живёт в MongoDB (не PostgreSQL), Flyway неприменим.
Использована конвенция проекта — programmatic `ensureIndex` +
`shedlock-provider-mongo`._

- [~] **Снято (D10):** Flyway V{N+1} на `attendance_db`. `push_subscriptions` —
      MongoDB коллекция в notification-web. Вместо миграции — поле
      `last_seen: Instant` в `PushSubscriptionDocument` + `ensureIndex
      idx_last_seen` в `PushMongoConfig.initIndexes()` (та же конвенция
      что M05 G1 для `late_checkin_requests`).
- [x] `WebPushDeliveryService` — bulk `$set last_seen=NOW()` через
      `mongoTemplate.updateMulti` для всех endpoint'ов с successful send
      (одна Mongo-op на fanout, не N save'ов). HTTP 410 Gone → мгновенный
      delete (D-10 было до M05).
- [x] `@Scheduled(cron="0 0 3 * * SUN") @SchedulerLock(name="cleanupStalePushSubs")`
      в `PushSubscriptionCleanupJob.cleanupStalePushSubs()` —
      `repository.deleteByLastSeenBefore(now - retention)`. Retention-days
      configurable (`rutcampustrack.push.cleanup.retention-days=90`).
- [x] `PushCleanupConfig` — `@EnableScheduling` + `@EnableSchedulerLock`
      + Mongo LockProvider + bootstrap `backfillMissingLastSeen` на
      `ApplicationReadyEvent`.
- [x] Audit auth-service Redis `refresh:<hash>`: ✅ `EX=604800` (7d)
      подтверждено в 4 call-site'ах: `AuthService.login:88` /
      `AuthService.refresh:125` / `TmaService:78` / `OtpService:192`.
      `refresh-token-expiration=604800` в `application.yml:57`. Фикс не
      требуется.
- [x] `docs/data-retention-policy.md` (NEW-148): таблица с 12 видами
      данных — retention, mechanism, триггеры пересмотра. Bootstrap
      backfill описан, deferred items M06+ зафиксированы.
- [x] Integration-тест `PushSubscriptionCleanupJobIT` (ContainerTestBase):
      3 сценария — `cleanup_deletesDeadSubs_keepsFreshOnes` (dead/fresh/
      89d boundary), `cleanup_onExactBoundary_treats90daysAsEligible`,
      `backfill_setsLastSeenForDocsMissingField` (legacy doc без поля).
      Все зелёные.

## Группа 8 — gRPC hot-path (P2-10/8)

_Scope уточнён 2026-04-21 после аудита gRPC callsite'ов — см. DECISIONS
D11. Deadline уже везде (19 callsite'ов), параллелизация неприменима к
`CheckinService.checkin` (только 1 gRPC-call), runtime-guard отклонён
после сломанных integration-тестов._

- [~] **Снято (D11):** `CheckinService.checkin` parallelization —
      checkin делает **1** gRPC call (`scheduleGrpcClient.getActiveLesson`),
      параллелить нечего. PLAN.md гипотеза «scheduleClient + academicClient»
      не соответствует коду (`semesterCacheService` — Redis cache,
      `geofenceService` — Redis cache).
- [x] `grpcTaskExecutor` bean — `GrpcParallelExecutorConfig`
      (attendance-app), `ThreadPoolTaskExecutor` core=2/max=8/queue=100,
      prefix `grpc-parallel-`.
- [x] Deadline audit: ✅ все 19 gRPC callsite'ов имеют
      `.withDeadlineAfter(3, SECONDS)`. Ручной fix не требуется,
      guard в ArchUnit.
- [x] ArchUnit NEW-149: `GrpcClientDeadlineTest` в attendance + schedule
      + academic. Проверяет что каждый public method `*GrpcClient` класса
      вызывающий `*BlockingStub` содержит `withDeadlineAfter`/`withDeadline`
      в том же методе (byte-code scan). Sanity-verify: удалён
      `withDeadlineAfter` в `ScheduleGrpcClient.getActiveLesson` →
      build failed с сообщением «Метод ... вызывает gRPC stub без
      .withDeadlineAfter ...». Откачено.
- [~] **Снято (D11):** runtime `GrpcDeadlineEnforcingInterceptor` —
      попытка global interceptor сломала 15+ integration-тестов
      (AcademicGrpcIntegrationTest/CacheIntegrationTest/RbacCacheIT),
      которые легитимно не используют deadline для in-process stub'ов.
      ArchUnit достаточен для production regression prevention.
- [x] `GrpcClientMetricsInterceptor` в shared-observability — ClientInterceptor,
      регистрирует `grpc.client.duration` Timer (histogram) с тегами
      `service` / `method` / `status` для каждого outgoing gRPC-call.
      Per-app wrapper (`@GrpcGlobalClientInterceptor`) — в attendance,
      schedule, academic. Без новой `grpc-micrometer` dependency.
- [x] Parallel refactor:
  - [x] `LessonEventService.processLessonClosed` —
        `getLessonById` + `getGroupMembers` параллельно через
        `CompletableFuture.supplyAsync + grpcTaskExecutor + unwrap()`.
        Wall-time ~200ms вместо ~400ms.
  - [x] `MarkingService.markBatch` — N уникальных `getLessonById`
        + 1 `getGroupMembers` fan-out параллельно.
  - [~] `ReportService.getLessonAttendance` — **не параллелится**:
        `getGroupMembers` требует `lesson.groupId` из результата
        `getLessonById` (dependency chain).
- [x] Grafana dashboard `infra/grafana/provisioning/dashboards/grpc-latency.json`
      — p50/p95/p99 histogram_quantile panels per `(service, method)`
      + error-rate panel (non-OK статусы) + active-methods stat.
- [x] Unit-тест `LessonEventServiceParallelTest` — mock gRPC 200ms
      каждый, wall-time < 350ms (параллельно) vs > 400ms sequential
      baseline. Sanity: `SyncTaskExecutor` для существующих
      correctness-тестов сохраняет детерминизм Mockito verify'ов.

## Группа 9 — Audit (bug-hunter + code-reviewer + security)

- [x] Полный `./gradlew build` — всё зелёное (unit + integration + ArchUnit + CI-lint) на `3fae923` HEAD.
- [x] `bug-hunter` на diff M05 — 8 findings (1 CRITICAL, 3 HIGH, 2 MEDIUM, 4 LOW/NIT). Детали в NOTES.md «Группа 9 — Audit».
- [x] `security-auditor` на batch + rbac + Grafana dashboard + ShedLock — 8 findings (2 HIGH, 1 MEDIUM, 2 LOW, 3 INFO).
- [x] `code-reviewer` на docs + DRY + parallel refactor — 6 priority-fixes (2 MAJOR, 4 MINOR).
- [x] Hot-patches → отдельный коммит `fix(m05): hot-patches after audit`.

## Группа 10 — Documentation + закрытие milestone

- [x] `docs/performance-indexes.md` (создан в G1).
- [x] `docs/caching-strategy.md` (создан в G3).
- [x] `docs/connection-pool-tuning.md` (создан в G6).
- [x] `docs/data-retention-policy.md` (создан в G7).
- [x] `docs/api-error-conventions.md` (создан в G4, sibling runbook).
- [x] `docs/future-ideas.md` (создан в G5, sibling runbook).
- [x] `docs/architecture.md` §11.1 — блок «Performance & Ops runbooks»
      с 6 ссылками (обновлено в G9 hot-patches, commit `ba0b233`).
- [x] `CHANGELOG.md [Unreleased]` — секции Added для M05 Групп 3-9
      (commit `<G10>`).
- [x] `CLAUDE.md` — статус M05 → ✅ 2026-04-21 (commit `<G10>`).
- [x] `docs/milestones/README.md` — M05 ✅ 2026-04-21 (commit `<G10>`).
- [x] PLAN.md → Post-mortem секция (commit `<G10>`).
- [x] `git tag v0.0.0-alpha.6` (локально, без push).
- [x] Hand-off для M06/M07/M08 в NEXT-SESSION.md (commit `<G10>`).

---

_Если задача превращается в 6+ часов — разрежь._
