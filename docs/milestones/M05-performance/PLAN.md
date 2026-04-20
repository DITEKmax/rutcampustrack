# M05 — Performance (Indexes + Caffeine + EntityGraph + Batch)

**Статус:** ⬜ не начат
**Старт / финиш:** — / —
**Estimate:** ~5-6 человеко-дней (P2-10 пачка из аудита)

---

## Scope

Закрывает P2-10 из `docs/report-before-v0.0.0/99-executive-summary.md`
(OWNER-ANSWERS.md строки 3673-4028). 8 подпунктов:

- **P2-10/1** — Composite indexes на hot queries (~3ч)
- **P2-10/2** — `@EntityGraph` / projection против N+1 (~1д)
- **P2-10/3** — Caffeine cache для справочников и RBAC (~1д)
- **P2-10/4** — Batch endpoints (backend + frontend) (~1д)
- **P2-10/5** — SQL-aggregate вместо in-memory stream (~1д)
- **P2-10/6** — HikariCP connection pool tuning (~2ч)
- **P2-10/7** — Cleanup старых push-подписок + refresh-token TTL audit (~3ч)
- **P2-10/8** — Hot-path gRPC: cache + параллелизация + deadlines + metrics (~1д)

Авторазрешает (из COVERAGE-AUDIT): 02 P2-3..7, 03 P2-2/4/5/6/7/8/9, 04 P2-1/2/9, 05 P2-7, 09 P2-11, 10 P2-14.

## Модули / изменения

### Миграции Flyway

- `academic_db`: `V{N}__add_performance_indexes.sql` — `(group_id, semester_id)` в user_groups.
- `schedule_db`: `V{N}__add_performance_indexes.sql` — partial index `(group_id, date) WHERE status != 'cancelled'`, unique `(group_id, lesson_number, date)` на one_off_lessons.
- `attendance_db` (Mongo): index `(group_id, lesson_id)`, `(group_id, status, created_at DESC)` на late-checkin collection.

### Caching

- `services/shared/shared-web/` (или per-service `config/CacheConfig.java`) — Caffeine `@EnableCaching`, namespaces `semester` (TTL 5м), `subject`/`group` (10м), `rbac` (1м).
- `@Cacheable` на `getActiveSemester()`, `isHeadmanFor()`, `getSubject()`, `getGroupById()`.
- `@CacheEvict` на `activateSemester`, `update/delete` subject/group, changeHeadman.

### N+1 fixes

- Repository-методы (academic/schedule/attendance) — `@EntityGraph(attributePaths={...})` для list, projection interface для mobile detail.
- **ArchUnit rule NEW-143** — `repository-метод возвращает коллекцию entity → либо Pageable, либо @EntityGraph, либо projection`.

### Batch endpoints

- `POST /api/attendance/marks/batch` — body `List<MarkRequest>` (max=100), response `207 Multi-Status` или `202 Accepted` с `List<MarkResult>`. Атомарный для headman-mark.
- `POST /api/academic/homeworks/batch` — partial-success (retry safe).
- Frontends (PWA + web-panel): один HTTP-запрос вместо `Promise.all` над loop'ом.

### SQL-aggregate

- `AttendanceStatsService`, `ReportService`, `ExcuseAnalyticsService` — переписать `.collect(toList())` → JPQL `@Query` с DTO projection.
- `GROUP BY status` / `GROUP BY date` (admin sparklines реальные).

### Connection pool

- `academic-service`, `schedule-service`, `attendance-service` `application.yml`:
  ```yaml
  spring.datasource.hikari:
    maximum-pool-size: 20
    minimum-idle: 5
    connection-timeout: 5000
    idle-timeout: 600000
    max-lifetime: 1800000
    leak-detection-threshold: 60000
  ```
- Grafana alert «pool_usage > 80% for 5m» через Alertmanager webhook (M04 reuse).

### Cleanup jobs

- `push_subscriptions.last_seen` column (Flyway V{N+1}) + update в `WebPushDeliveryService`.
- `@Scheduled(cron="0 0 3 * * SUN") @SchedulerLock(name="cleanupStalePushSubs") cleanupStalePush()` — DELETE > 90 дней.
- Audit refresh-token: `redis.set("refresh:<hash>", uid, EX=604800)` (7d) — подтвердить или пофиксить.

### gRPC hot-path

- `CheckinService.checkin` — `CompletableFuture.supplyAsync` параллельно `scheduleClient.getLesson` + `academicClient.getUser`.
- `grpc-micrometer` dependency + `@GrpcClientInterceptor` — RPC timer + counter.
- Явный `.withDeadline(Deadline.after(3, SECONDS))` на каждом gRPC-call.
- **CI-lint NEW-149** — gRPC-call без deadline = fail (ArchUnit).

## Acceptance criteria

- [ ] Все миграции Flyway применены, schema validate проходит.
- [ ] `docs/performance-indexes.md` (NEW-142) — таблица «запрос → indexes → p50 до → p50 после» с EXPLAIN ANALYZE на 10k+ rows.
- [ ] Integration-тесты: query time assertion `< 50ms` (regression guard) для 3 hot queries.
- [ ] `docs/caching-strategy.md` (NEW-144) — TTL matrix, invalidation triggers, trade-offs + migration-план на Redis (v0.1).
- [ ] `docs/connection-pool-tuning.md` (NEW-147) — формулы, текущие значения, Grafana alert.
- [ ] `docs/data-retention-policy.md` (NEW-148) — таблица хранения: push-subs 90д, refresh-tokens 7д, OTP 5м, attendance history accept.
- [ ] ArchUnit rule NEW-143 (repo → Pageable/EntityGraph/projection) в `check` phase, ловит violation fake commit.
- [ ] CI-lint NEW-149 (gRPC deadline required) в `check` phase.
- [ ] Bulk mark-attendance: 30 отметок через `/batch` endpoint < 500ms (до было 6000ms через sequential await).
- [ ] Caffeine gauges exposed: `cache.size`, `cache.gets{result=hit|miss}` — hit-rate > 80% после warm-up в Grafana.
- [ ] `./gradlew build` зелёный (включая integration tests + ArchUnit + CI-lint).

## Dependencies

- **Блокирует:** — (milestone не блокирует другие milestones).
- **Блокируется:** M01 (shared-web/events/logback/test-containers есть). M02 Mongo/PG outbox не затрагивается. M04 — Caffeine gauges регистрируются через `MeterRegistry` из M04 G1.
- **Parallel safe:** M06 (полностью независим), M07 (frontend-only, пересечение только в batch-вызовах — решаемо).

## Artifacts

- `docs/performance-indexes.md` (новый)
- `docs/caching-strategy.md` (новый)
- `docs/connection-pool-tuning.md` (новый)
- `docs/data-retention-policy.md` (новый)
- `docs/architecture.md` — раздел «Caching» + обновление HikariCP sizing
- `CHANGELOG.md [Unreleased]` — M05 секция
- Flyway миграции × 3-4 сервиса
- Grafana dashboard «gRPC latency by method» (NEW-58 дополнение)

---

_Scope фиксирован из OWNER-ANSWERS P2-10. Отклонения — в NOTES.md + спросить владельца._

## Post-mortem

_Заполняется при закрытии milestone'а._
