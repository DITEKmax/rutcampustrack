# M05 Checklist

Атомарные задачи в порядке выполнения. Группа = логический коммит.

## Группа 1 — Composite indexes + perf baseline (P2-10/1)

- [ ] Baseline: seed-dataset на 10k+ rows в dev-compose (lessons, attendance, users). Скрипт `docs/milestones/M05-performance/seed-perf.sql` или Python-script.
- [ ] EXPLAIN ANALYZE «before» для 3 hot queries — записать в `docs/performance-indexes.md`:
  1. `getWeekLessons(groupId, dateFrom, dateTo)` — schedule.
  2. `getHeadmanDashboardLateCheckins(groupId, status)` — attendance.
  3. `getGroupMembers(groupId, semesterId)` — academic.
- [ ] Flyway миграция `schedule_db`: partial `idx_lessons_group_dates (group_id, date) WHERE status != 'cancelled'` + unique `idx_oneoff_dedup (group_id, lesson_number, date)`.
- [ ] Flyway миграция `attendance_db` (MongoDB — скрипт в `AttendanceIndexInitializer`): `(group_id, lesson_id)` + `(group_id, status, created_at DESC)` на late-checkin.
- [ ] Flyway миграция `academic_db`: `(group_id, semester_id)` на user_groups.
- [ ] EXPLAIN «after» + сравнение p50/p99 в `performance-indexes.md`.
- [ ] Integration-тест (per-сервис): query < 50ms regression guard на seed-dataset.

## Группа 2 — N+1 fixes через @EntityGraph / projection (P2-10/2)

- [ ] Audit repository-методов: grep `findAll`, `findBy*` в academic/schedule/attendance → таблица в NOTES.md `метод → нужен ли fix`.
- [ ] `@EntityGraph(attributePaths = {...})` для list-endpoints: `LessonRepository.findByDateBetween`, `AttendanceRepository.findByGroupAndPeriod`, `GroupRepository.findMembers`.
- [ ] Projection interface для mobile detail (1 entity, только needed fields): `LessonDetailsProjection`, `AttendanceDetailsProjection`.
- [ ] ArchUnit rule NEW-143: `repository returning Collection<entity> → либо Pageable, либо @EntityGraph, либо projection`. Добавить в `arch/` каждого сервиса.
- [ ] Verify через sanity-test: нарушение (ArchUnit-violation) rule'а → BUILD FAILED.
- [ ] Unit/integration tests: существующие запросы возвращают правильные данные (non-breaking).

## Группа 3 — Caffeine cache для справочников и RBAC (P2-10/3)

- [ ] Caffeine dep в `services/shared/shared-web/build.gradle.kts` (api — чтобы `@EnableCaching` видел namespace).
- [ ] `CacheConfig` bean per-сервис (или в shared-web с namespace-per-service). Cache-specific TTLs: `semester` 5м, `subject`/`group` 10м, `rbac` 1м.
- [ ] `@Cacheable("semester") getActiveSemester()` в academic-service.
- [ ] `@Cacheable(value="rbac", key="#userId + ':' + #groupId") isHeadmanFor()` в academic.
- [ ] `@Cacheable("subject")` + `@Cacheable("group")` на read-side academic.
- [ ] `@CacheEvict` на `activateSemester`, `update/delete subject/group`, `changeHeadman`.
- [ ] Micrometer binding: `CaffeineCacheMetrics.monitor(meterRegistry, cache, name)` — gauges `cache.size`, `cache.gets{result=hit|miss}` доступны в Grafana.
- [ ] Unit-тест: `@Cacheable` сам по себе сложно (Spring proxy нужен) — integration-тест с SpringBootTest, проверить counter hits.
- [ ] `docs/caching-strategy.md` (NEW-144): TTL matrix, invalidation triggers, consistency trade-offs, раздел «Migration на Redis при multi-instance».

## Группа 4 — Batch endpoints (P2-10/4)

- [ ] Attendance: `POST /api/attendance/marks/batch` — body `@Valid @Size(max=100) List<MarkRequest>`, atomic (headman-action). Response `202` + `List<MarkResult>` для optimistic lock / conflict.
- [ ] Academic: `POST /api/academic/homeworks/batch` — partial-success (207 Multi-Status или 202 с per-row result).
- [ ] Validation: `invalid-params[]` с `name="marks[3].lessonId"` (RFC 7807 extension, P2-3/2).
- [ ] Frontend PWA: headman `handleBulkMark` → один HTTP-запрос (вместо 30 Promise.all). Один progress indicator.
- [ ] Frontend web-panel: аналогично `HeadmanWeeklyJournal.loadWeek`.
- [ ] `docs/api-error-conventions.md` — раздел «Batch endpoint conventions» (NEW-145): atomic vs partial-success, schema `MarkResult`, client handling 207.
- [ ] Integration-тест: bulk-mark 30 отметок < 500ms (до было 6000ms).

## Группа 5 — SQL-aggregate вместо in-memory stream (P2-10/5)

- [ ] Audit `*Service.java` на `.collect(toList())` → `.stream().filter().count()` где результат используется только для aggregate.
- [ ] `AttendanceStatsService`: JPQL `SELECT new AttendanceStatsDto(a.status, COUNT(a)) ... GROUP BY a.status`.
- [ ] `ExcuseAnalyticsService`: aggregate по `ExcuseStatus`.
- [ ] `LessonService.findOneOffLessons` — SQL WHERE вместо `stream.filter`.
- [ ] Admin-dashboard sparklines (10 P2-4): реальные SQL-aggregate (или Prometheus PromQL fetch).
- [ ] Integration-тесты: корректность агрегации + query time < 100ms.
- [ ] `docs/future-ideas.md` (NEW-146): audit-checklist для `.collect(toList())` для агрегации.

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
