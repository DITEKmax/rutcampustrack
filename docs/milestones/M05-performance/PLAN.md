# M05 — Performance (Indexes + Caffeine + EntityGraph + Batch)

**Статус:** ⏳ в работе
**Старт / финиш:** 2026-04-20 / —
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

### Миграции Flyway / индексы (уточнено 2026-04-20, см. DECISIONS D1-D3)

- `academic_db`: `V{N}__add_performance_indexes.sql` —
  `idx_tsg_group_semester` на `teacher_subject_groups(group_id, semester_id)`
  + `idx_hw_group_semester` на `homeworks(group_id, semester_id)`. Таблицы
  `user_groups` не существует — OWNER-ANSWERS 3682 реинтерпретирован на
  реальные hot queries (см. D2).
- `schedule_db`: `V{N}__add_performance_indexes.sql` — partial
  `idx_lessons_item_date ON lessons (schedule_item_id, date) WHERE status !=
  'cancelled'`. `lessons.group_id` не существует; композит на
  `schedule_item_id` покрывает фактический `IN + BETWEEN` запрос без
  денормализации (D1). `schedule_one_off_lessons` уже имеет UNIQUE в V4 —
  no-op.
- `attendance_db` (Mongo, через `MongoConfig.initIndexes` или
  `AttendanceIndexInitializer`): compound `{group_id:1, status:1,
  created_at:1}` на `late_checkin_requests` — закрывает 04 P2-9.
  `(group_id, lesson_id)` на `attendances` **отложено** (D3) — нет hot
  query-потребителя в коде; решение записано в Future-ideas.

### Caching (уточнено 2026-04-20, см. DECISIONS D6)

**Scope переформулирован:** Caffeine НЕ вводится. В academic-service
уже работает Spring `@EnableCaching` + Redis `CacheManager` (ранние
фазы 59/60) с namespaces `groups`/`group_members`/`users`/
`active_semester`/`campus_geofence`. M05 Группа 3 добавляет
**пробелы** поверх существующего решения.

- **`CacheConfig.java`** (academic-service) — добавить namespaces
  `rbac` (TTL 1м) + `subject` (TTL 10м). `application.yml`
  `cache.ttl.rbac=PT1M`, `cache.ttl.subject=PT10M`.
- **`AcademicReadService.isHeadmanOf(Long userId, Long groupId)`** —
  новый метод с `@Cacheable(value="rbac", key="#userId + ':' + #groupId")`.
  Делегирует в `userRepository.findById(userId)` + поле `isHeadman`.
  `AcademicGrpcServiceImpl.isHeadman` (ранее «Not cached per D-02» —
  D6 переопределяет) переключается на этот метод.
- **`SubjectService.getSubject`** — `@Cacheable("subject", key="#id")`.
- **`SubjectService.updateSubject` / `deleteSubject`** —
  `@CacheEvict("subject", key="#id")`.
- **`UserService.patchUser`** — программатический `rbacCache.evict(...)`
  при смене `is_headman` или `group_id` (по аналогии с существующим
  eviction `groups`/`group_members` в строках 225-233).
- **`UserService.transferStudent`** — `rbacCache.evict` для старого
  и нового groupId пользователя.
- **Redis metrics биндинг.** Spring RedisCache нативно не
  экспонирует hit/miss. Реализация: тонкий wrapper
  `MetricsCacheManagerDecorator implements CacheManager`, делегирующий
  в RedisCacheManager и инкрементящий
  `MeterRegistry.counter("cache.gets", "result", hit|miss, "cache", name)`.
  Gauge `cache.size` опустить (Redis Cache.size() возвращает -1 без
  дорогого KEYS scan).

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
- [ ] `docs/caching-strategy.md` (NEW-144) — TTL matrix
      (groups/group_members/users/active_semester/campus_geofence/rbac/subject),
      invalidation triggers, trade-offs, Redis-as-L1 rationale (D6),
      future-ideas «Caffeine L1+L2 при multi-instance».
- [ ] `docs/connection-pool-tuning.md` (NEW-147) — формулы, текущие значения, Grafana alert.
- [ ] `docs/data-retention-policy.md` (NEW-148) — таблица хранения: push-subs 90д, refresh-tokens 7д, OTP 5м, attendance history accept.
- [ ] ArchUnit rule NEW-143 (repo → Pageable/EntityGraph/projection) в `check` phase, ловит violation fake commit.
- [ ] CI-lint NEW-149 (gRPC deadline required) в `check` phase.
- [ ] Bulk mark-attendance: 30 отметок через `/batch` endpoint < 500ms (до было 6000ms через sequential await).
- [~] **Deferred (M05 Группа 3, CHECKLIST строки 124-131):** Redis cache
      metrics `cache.gets{result=hit|miss, cache=...}` не экспонированы.
      Попытка `MetricsCacheManagerDecorator` сломала namespace-specific
      TTL в `RedisCacheManager`. Обоснование и альтернатива
      (`@Aspect`-подход) зафиксированы в `docs/caching-strategy.md`
      §«Observability» и `docs/future-ideas.md`. Integration-тест
      `RbacCacheIT` проверяет presence key'ев + TTL вместо counter'ов.
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

Milestone закрыт **2026-04-21**, tag `v0.0.0-alpha.6`.

### Что пошло по плану

- **Composite indexes (G1)** — 3 Flyway миграции + programmatic Mongo
  index закрыли 04 P2-9 (COLLSCAN → IXSCAN, 50× reduction). Integration
  regression-guards (`*PerformanceIT` × 3) зафиксировали invariant
  «< 50ms на Q1-Q4».
- **Batch endpoint (G4)** — `POST /attendance/marks/batch` пошёл
  прямо в ядро, 10× latency reduction на 30-student batch
  подтверждён в unit-тестах.
- **HikariCP tuning (G6)** — 2h реализации + alert-правило уложились
  в оценку; прод-smoke deferred до первого deploy (не блокирующе).
- **Audit Группы 9** — три внешних агента на свежем контексте нашли
  10 значимых findings (1 CRITICAL, 4 HIGH, 3 MEDIUM, 2 LOW), все
  hot-patch'нуты в один commit `ba0b233`.

### Ключевые отступления (D1-D11)

| D | Группа | Отступление |
|---|--------|-------------|
| D1 | G1 | `lessons.group_id` не денормализован → индекс на `schedule_item_id` вместо `group_id` |
| D2 | G1 | Таблицы `user_groups` не существует → индексы на `(group_id, semester_id)` для реальных hot queries |
| D3 | G1 | `attendances (group_id, lesson_id)` — нет hot query-потребителя, отложено |
| D4 | G1 | `schedule_one_off_lessons` UNIQUE уже в V4, no-op |
| D5 | G2 | N+1 не существует by design → переформулирование в preventive ArchUnit guard |
| D6 | G3 | **Caffeine не вводится** — academic уже имеет Redis L1 с Фазы 59/60. Добавили namespaces `rbac`/`subject` поверх |
| D7 | G4 | pseudo-atomic (validation-first) вместо partial-success — headman-UX проще |
| D8 | G4 | Academic `/homeworks/batch` + web-panel weekly-journal bulk-read отложено (ROI низкий) |
| D9 | G5 | Mongo `$group` в ReportService блокируется cross-service invariant'ом — single-pass accumulators как best achievable, полный aggregation отложен в NEW-146 |
| D10 | G7 | `push_subscriptions` — MongoDB, Flyway неприменим → programmatic index + `shedlock-provider-mongo` |
| D11 | G8 | Deadline already everywhere; runtime `GrpcDeadlineEnforcingInterceptor` сломал 15+ integration-тестов → откачен, ArchUnit достаточен |

### Audit findings (Группа 9 summary)

Closed (commit `ba0b233`):
- rbac afterCommit race (CRITICAL)
- markBatch per-item event publish (HIGH)
- deadline propagation / CallerRunsPolicy (HIGH)
- JWT is_headman re-check (HIGH)
- uniqueLessons DoS cap (HIGH)
- archiveUser rbac evict (HIGH)
- error-msg enumeration (LOW)
- cron UTC zone (LOW)
- Clock consistency (MINOR)
- Javadoc `GrpcParallelExecutorConfig` / `PushSubscriptionDocument` (MINOR)
- PLAN.md AC alignment (MAJOR doc)
- architecture.md cross-links (MAJOR doc)

DRY refactor'ы (commit `ff3d6e3`):
- GrpcClientDeadlineTest × 3 → shared `GrpcDeadlineArchRules` (-74 LOC net)
- `unwrap()` → shared `AsyncGrpcUtils.joinOrUnwrap`

Deferred (обоснованные):
- Redis Jackson `LaissezFaireSubTypeValidator` → M06 supply-chain
- Mozilla/Apple push endpoint masking → M07 frontend hardening
- `isHeadman` rate-limit → M06
- Redis cache hit/miss metrics → M06 (`@Aspect` подход)
- Mongo `$group` в ReportService → M07 (денормализация `lesson_alive`)

### Ключевые уроки для следующих milestones

1. **Audit со свежим контекстом работает.** Три независимых агента
   (bug-hunter / security / code-review) на diff без истории
   реализации нашли 22 findings из которых 10 критичных/значимых.
   Повторять для M06/M07/M08.
2. **Caffeine вводить не надо.** Redis L1 с namespaces + TTL + `@Cacheable`
   закрывает все use-cases без L2 hybrid. Документ
   `docs/caching-strategy.md` — single source of truth.
3. **Lightweight milestone-workflow без GSD-агентов** сработал для
   8-групповой M05 полностью. Сэкономил ~60-70% токенов относительно
   M01-M03b (где был RESEARCH/VERIFICATION). Оставить формат для M06+.
4. **ArchUnit invariant'ы > runtime enforcement** — попытка
   `GrpcDeadlineEnforcingInterceptor` сломала 15+ integration-тестов.
   Byte-code guard в тесте достаточен для production regression.
5. **afterCommit-eviction для cross-service RBAC** — урок,
   применимый ко всем `@Cacheable` на rbac/authz hot-path'ах в M07+.

### Метрики milestone'а

- **Commits:** 11 (включая 3 docs hand-off'а)
- **LOC:** +2668/-59 (before audit fix), +308/-59 (audit patches),
  +167/-241 (DRY refactor)
- **Новые docs:** 5 (caching-strategy, performance-indexes,
  connection-pool-tuning, data-retention-policy, api-error-conventions)
- **Integration-тесты добавлены:** 3 (`LessonPerformanceIT`,
  `AcademicPerformanceIT`, `LateCheckinPerformanceIT`) +
  `RbacCacheIT` + `PushSubscriptionCleanupJobIT`
- **Unit-тесты:** `MarkingServiceTest` расширен на BATCH-01..07,
  `LessonEventServiceParallelTest` (wall-time ассерт parallel < 350ms)
- **Build:** зелёный на всех 4 сервисах, ArchUnit × 5 rules passing
- **Estimate accuracy:** ~6-7д план / ~3 сессии факт (Opus 4.7 автономный режим)
