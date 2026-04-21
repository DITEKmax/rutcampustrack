# Changelog

Все заметные изменения в проекте RutCampusTrack документируются здесь.

Формат: [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/).
Версионирование: [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **M06 Ops & Supply Chain** — supply-chain guard + CI/CD hardening
  (~21ч, 9 групп, 12 коммитов `b6a0cc3..e0e1881`).
  - **HEALTHCHECK в 7 Dockerfile** (P2-9/1, NEW-150) — metadata
    живёт с образом, `docker run` без compose тоже работает. Java
    через `wget -qO- /actuator/health`, bot через `curl /health`.
    `docs/dockerfile-conventions.md`.
  - **SHA-tagging через `${IMAGE_TAG:-latest}`** (QD1, 13 P1-1/2/4) —
    11 образов параметризованы; `deploy.yml` передаёт `github.sha` →
    reproducible rollback. Убран дублирующий `up -d` после `sleep 30`,
    заменён на `--wait --wait-timeout 120`. mini-app tag `:${sha}`.
  - **Digest-пин cadvisor + promtail** (QD4, NEW-102) — multi-arch
    manifest-list digests. `docs/infra/container-trust.md` — policy.
  - **Observability semver-pin** (P2-9/2, NEW-151) — `loki:3.2.1`,
    `prometheus:v2.55.1`, `grafana:11.3.1`, `node-exporter:v1.8.2`.
    `docs/runbooks/loki-major-upgrade.md` — expand-contract schema.
  - **Renovate + Dependabot** (QD4+QD6, NEW-105) — auto-merge
    patch/pin/digest после CI, manual minor/major, groupings,
    loki major manual-only. Dependabot security-only × 7 ecosystems.
    `docs/ci-cd.md` — полный CI/CD runbook.
  - **Trivy + Gitleaks CI + SECURITY.md** (QD5, NEW-103) — 4 jobs
    (trivy-repo SARIF + trivy-config + gitleaks + weekly trivy-images
    matrix). `.pre-commit-config.yaml`. `SECURITY.md` disclosure policy.
  - **CI↔deploy gate** (C0-8, 13 P0-2/11) — `paths-ignore` docs/
    .planning/MD. `workflow_run: [CI]: completed` + strict guards
    (`head_branch==main && event==push`). `concurrency: production-
    deploy` (avoid concurrent JWT keygen). `DEPLOY_SHA` env из
    `workflow_run.head_sha`.

### Fixed (security)

- **Redis Jackson whitelist** (M05 defer, security #3) —
  `LaissezFaireSubTypeValidator` → `BasicPolymorphicTypeValidator`
  с narrow whitelist (`ru.rutcampustrack.*` + explicit collection
  types + `java.time.*` + `java.math.BigDecimal/BigInteger`).
  Block'ирует gadget-chains через `@class` в Redis payload'е.
- **gRPC isHeadman rate-limit** (M05 defer, security #5) — token
  bucket 120 calls/мин per userId в `AcademicGrpcServiceImpl`,
  lock-free CAS, `RL_MAX_BUCKETS=10k`. `Status.RESOURCE_EXHAUSTED`
  при превышении. Integration-тест покрывает.
- **GrpcClientMetricsInterceptor** (M05 defer, bug-hunter 5.1+5.3) —
  Timer cache ConcurrentHashMap по `(service|method|status)`; `startNs`
  перенесён в `start()` listener.
- **Pre-existing EventSchemaRefTest fix** — M04 G6 (08fbd1f) добавил
  required envelope fields, test payload'ы не обновлены. Восстановлен
  zeлёный build.

### Deferred (M07+)

- Redis cache metrics `@Aspect` (MINOR) — требует `RedisCacheMeterBinder`
  без namespace-TTL регрессии.
- `/actuator/**` excluded from tracing (M04 backlog) — custom OTel
  `Sampler` bean + integration tests.
- isHeadman principal-based userId — M07 (gRPC proto redesign).
- rct-nginx 5-min reload + nginx/postgres/mongo/redis/rabbitmq digest-
  pin — M07/M09.

- **M05 Performance — Группа 9 Audit + hot-patches** — три внешних
  агента (bug-hunter / security-auditor / code-reviewer) на diff M05
  в чистом контексте. 22 findings; закрыто 10 критичных/high/medium,
  12 отложено в M06/M07 как обоснованные accept'ы или pre-existing.
  - **rbac afterCommit fix** (CRITICAL) — `UserService` eviction'ы
    rbac-namespace перенесены в `TransactionSynchronization.afterCommit()`
    helper `evictRbacAfterCommit`. Раньше concurrent `isHeadmanOf`
    читал pre-commit snapshot и кешил stale-значение до истечения TTL
    (60s), что позволяло ex-headman'у удерживать privileges.
    `archiveUser` теперь тоже evict'ит rbac.
  - **MarkingService.markBatch security hardening** —
    (a) re-check `academicGrpcClient.isHeadman()` перед fan-out'ом
    (source-of-truth вместо 15-минутного JWT claim'а),
    (b) `MAX_UNIQUE_LESSONS_PER_BATCH=10` кэп защищает
    `grpcTaskExecutor` от DoS через 100-item payload,
    (c) `findAndModify(returnNew=true)` заменил `upsert+findOne`
    (один round-trip на item),
    (d) события публикуются после успешного прогона всего батча
    (исключает duplicate-события при partial failure),
    (e) error messages без id's (enumeration side-channel).
  - **GrpcParallelExecutorConfig CallerRunsPolicy** — под
    event-storm'ом AMQP consumer gracefully деградирует до sequential
    вместо `RejectedExecutionException` и потери heartbeat'а.
  - **WebPushDeliveryService Clock injection** — `touchLastSeen`
    использует `Instant.now(clock)`, консистентно с `PushSubscriptionCleanupJob`.
  - **@Scheduled zone="UTC"** в cron cleanup push-subs — зависимость
    от JVM timezone устранена.

- **M05 Performance — Группа 8 (gRPC hot-path, NEW-149)** —
  P2-10/8 из аудита, scope уточнён по DECISIONS D11.
  - **`GrpcClientMetricsInterceptor`** (shared-observability) —
    ClientInterceptor на `grpc.client.duration` Timer (histogram)
    с тегами `service` / `method` / `status`. Per-app wrappers через
    `@GrpcGlobalClientInterceptor` в attendance/schedule/academic.
  - **Параллельный fan-out** в `LessonEventService.processLessonClosed`
    (`getLessonById` + `getGroupMembers` через
    `CompletableFuture.supplyAsync + grpcTaskExecutor`, wall-time
    ~200ms вместо ~400ms) и `MarkingService.markBatch` (N уникальных
    `getLessonById` + 1 `getGroupMembers` параллельно).
  - **`GrpcParallelExecutorConfig`** (attendance) — bounded
    `ThreadPoolTaskExecutor` core=2/max=8/queue=100, `CallerRunsPolicy`.
  - **`GrpcDeadlineArchRules`** (shared-observability testFixtures) —
    byte-code ArchUnit rule: каждый публичный метод `*GrpcClient`
    вызывающий `*BlockingStub` обязан содержать `withDeadlineAfter`
    / `withDeadline` в том же методе. Вынесено из 3-кратного
    дубликата per-service, сокращено ~150 LOC.
  - **`AsyncGrpcUtils.joinOrUnwrap`** (shared-observability) —
    distill `CompletableFuture.join()` → оригинальный
    `RuntimeException`. Раньше дублировался inline в двух сервисах.
  - **`infra/grafana/provisioning/dashboards/grpc-latency.json`** —
    p50/p95/p99 histogram_quantile panels per `(service, method)`
    + error-rate panel + active-methods stat.

- **M05 Performance — Группа 7 (Push-subs retention + cleanup,
  NEW-148)** — P2-10/7 из аудита, scope уточнён по DECISIONS D10
  (MongoDB, не PostgreSQL).
  - **`last_seen: Instant`** в `PushSubscriptionDocument` —
    обновляется bulk `$set` через `WebPushDeliveryService.touchLastSeen`
    (одна Mongo-op на fanout вместо N save'ов).
  - **`idx_last_seen`** добавлен в `PushMongoConfig.initIndexes()`
    (programmatic, конвенция проекта).
  - **`PushSubscriptionCleanupJob`** —
    `@Scheduled(cron="0 0 3 * * SUN", zone="UTC") @SchedulerLock`
    с retention 90d (configurable). `PushCleanupConfig` подключает
    `shedlock-provider-mongo` LockProvider + bootstrap
    `backfillMissingLastSeen` на `ApplicationReadyEvent`.
  - **`docs/data-retention-policy.md`** (NEW-148) — 12-rows matrix
    (push-subs 90д, refresh-tokens 7д, OTP 5м, attendance accept,
    outbox 48h, ...).

- **M05 Performance — Группа 6 (HikariCP tuning, NEW-147)** —
  P2-10/6 из аудита.
  - **`application.yml`** academic/schedule:
    `pool=20, idle=5, timeout=5s, idle-timeout=10m, max-lifetime=30m,
    leak-detection=60s`. auth-service `pool=10` (read-only login).
    Attendance — no-op (MongoDB).
  - **Alert `HikariPoolExhaustion`** в
    `infra/prometheus/rules/service-health.yml` — `(active/max) > 0.80
    for 5m`, severity warning, routed через M04 Alertmanager.
  - **`docs/connection-pool-tuning.md`** (NEW-147) — формула sizing,
    текущие значения, триггеры пересмотра, smoke-тест процедура.

- **M05 Performance — Группа 5 (Single-pass accumulators + SQL
  pagination, NEW-146, D9)** — P2-10/5 из аудита, scope уточнён.
  - **`ReportService.{getStudentStats, buildOverall, buildWeekly}`** —
    single-pass `for`-loop + int counter'ы вместо 3-4× `stream.filter.count`
    на одном списке. O(N) вместо O(K×3N).
  - **`LessonService.getLessonsForGroup`** — SQL `LIMIT/OFFSET`
    через Spring Data `Pageable` + native `countQuery` в
    `LessonRepository.pageByScheduleItemIdInAndDateBetweenAndStatusIn`.
    Устраняет OOM-risk на 2000+ lessons/semester.
  - **`docs/future-ideas.md`** (NEW-146) — 3 варианта решения для
    Mongo `$group` в `ReportService` (блокируется
    `filterExistingLessons` cross-service invariant'ом; денормализация
    `lesson_alive` как M06/M07 scope).

- **M05 Performance — Группа 4 (Batch endpoints, D7, D8, NEW-145)** —
  ядро P2-10/4.
  - **`POST /attendance/marks/batch`** — body
    `@Valid @Size(min=1, max=100) List<MarkBatchItem>`, pseudo-atomic
    (validation-first). 1 gRPC `getLessonById` на уникальный lessonId
    + 1 `getGroupMembers` + N upsert (вместо N × 3 gRPC + N upsert).
    ~10× latency reduction на 30-student batch (~6000ms → ~500ms).
  - **`MarkBatchItem`/`MarkBatchRequest`/`MarkBatchResponse`** DTOs
    в `attendance-api-contract`.
  - **PWA `useHeadmanMarkBatch`** — TanStack mutation,
    `handleBulkMark` переключён с for-loop `await` на один batch call.
  - **`docs/api-error-conventions.md`** (NEW-145) — RFC 7807 error
    schema, pseudo-atomic vs partial-success patterns.

- **M05 Performance — Группа 3 (Redis cache дополнения, D6, NEW-144)** —
  P2-10/3 из аудита, scope уточнён по DECISIONS D6 (Caffeine не
  вводится — academic-service уже имеет Redis `CacheManager` + 5
  namespaces с Фазы 59/60).
  - **Namespaces `rbac` (TTL 1м) и `subject` (TTL 10м)** в `CacheConfig`.
  - **`AcademicReadService.isHeadmanOf(userId, groupId)`** —
    `@Cacheable("rbac")`. Переключает hot-path
    `AcademicGrpcServiceImpl.isHeadman` с «Not cached per D-02» на
    кешируемый.
  - **`@Cacheable("subject")`** на `SubjectService.getSubject` +
    `@CacheEvict` на updateSubject/deleteSubject.
  - **Programmatic rbac eviction** в `UserService.patchUser` /
    `transferStudent` (при смене `is_headman` / `group_id`) — в M05 G9
    перенесено в afterCommit-хук.
  - **`docs/caching-strategy.md`** (NEW-144) — TTL matrix
    (7 namespaces), invalidation triggers, consistency trade-offs,
    Redis-as-L1 rationale, migration plan.

- **M05 Performance — Группа 2 (Preventive N+1 guard, NEW-143)** —
  P2-10/2 из аудита, переформулирован в preventive-only scope (D5)
  после системного audit'а: все JPA entity в schedule + academic
  используют FK как Long, без `@ManyToOne`/`@OneToMany` — N+1 невозможен
  by design.
  - **ArchUnit `RepositoryNPlusOneGuardTest`** — в schedule-service и
    academic-service. Две rule'а: (1) `entitiesMustNotUseJpaRelations`
    фиксирует convention v0.0.0; (2)
    `repositoriesReturningCollectionsMustGuardNPlusOne` активируется
    как только появится первая relation — требует Pageable |
    @EntityGraph | *Projection return type | JOIN FETCH. Sanity-verify
    пройден (fake `@ManyToOne` в Lesson → build failed с информативным
    сообщением, откачено).
  - **`LessonDetailsProjection`** + `LessonRepository.findLessonDetails`
    (schedule-service) — reference-pattern для Spring Data interface
    projection. Native JOIN lessons + schedule_items в одном SELECT,
    вместо 2-step `findById(lessonId)` + `findById(scheduleItemId)`.
  - **`docs/architecture.md` §11** — новый раздел «JPA convention: FK
    как Long, без entity relations (NEW-143)» с обоснованием (прозрачный
    SQL, нет lazy surprises, cross-service FK через gRPC), образцом
    паттерна `collect itemIds → findByIdIn` из
    `LessonService.massCancelLessons:137-142`, action-plan если relation
    всё-таки потребуется.

- **M05 Performance — Группа 1 (Composite indexes + perf baseline)**
  — P2-10/1 из аудита v0.0.0, закрывает 04 P2-9 и раскрывает capacity
  для v0.1 масштаба.
  - **`schedule_db` V12** — partial composite
    `idx_lessons_item_date ON lessons (schedule_item_id, date)
    WHERE status != 'cancelled'` для hot query
    `LessonRepository.findByScheduleItemIdInAndDateBetweenAndStatusIn`
    (week-journal). D1 — `group_id` в `lessons` не денормализован,
    композит на FK к `schedule_items` покрывает фактический
    `IN + BETWEEN` без изменения схемы.
  - **`academic_db` V17** — `idx_tsg_group_semester` +
    `idx_hw_group_semester` для hot queries
    `findByGroupIdAndSemesterId` в `TeacherSubjectGroupRepository` +
    `HomeworkRepository`. D2 — таблицы `user_groups` (из OWNER-ANSWERS)
    не существует; индексы на реальные hot queries.
  - **`attendance_db` Mongo** — compound
    `lcr_group_status_created (group_id, status, created_at)` на
    collection `late_checkin_requests` через
    `MongoConfig.initIndexes()` (programmatic management,
    проектная convention). Закрывает **04 P2-9**: COLLSCAN
    → IXSCAN, docsExamined 6000 → 120 (50× reduction), SORT stage
    ушёл (index order совпадает с ORDER BY).
  - **Integration tests regression guard** — `LessonPerformanceIT`,
    `AcademicPerformanceIT`, `LateCheckinPerformanceIT`. 4 запроса <
    50ms на seed-dataset (12k lessons, 1800 TSG, 1800 homeworks,
    6000 late_checkin). Best: 8/8/8/10 ms.
  - **`docs/performance-indexes.md`** — runbook: seed-dataset
    spec, EXPLAIN before/after по 4 hot queries, деферренные
    индексы (D3 на `attendances`, D4 no-op для `one_off_lessons`),
    expand/contract процесс для новых индексов.
  - **`docs/milestones/M05-performance/seed-perf.sql` + `.js`** —
    idempotent seed-скрипты (id ≥ 900000), reusable в dev + CI.

- **M04 Observability** (tag `v0.0.0-alpha.5`) — end-to-end наблюдаемость.
  - **shared-observability** модуль — `BusinessMetrics` fluent-helper,
    `MetricNames` единые имена, `MdcKeys`, `GrpcClientHealthIndicator`,
    `PublicKeyHealthIndicator`. testFixtures: `MetricsTestSupport`.
  - **JSON-логи во всех 6 сервисах** — logstash-logback-encoder схема
    `{ts, v, level, logger, thread, msg, service, <MDC>}`. Masking
    Bearer / telegram_id / FCM через shared-logback. CI-check
    `verifyLogbackJsonInAllServices` + `verifyNoDebugInProd`.
  - **Health endpoints** — `show-details: always` во всех 6 сервисах,
    `probes.enabled: true`, `PublicKeyHealthIndicator` в api-gateway,
    `git.properties` генерация через `generateGitProperties` task.
  - **Distributed tracing OTel + Tempo** — `micrometer-tracing-bridge-otel`
    + OTLP exporter в 6 сервисах, Python-бот через `opentelemetry-sdk`
    1.41 + auto-instrumentation aio-pika/aiohttp/grpc/redis. Tempo
    container (grafana/tempo:2.3.1) с retention 14d.
  - **Unified event envelope** (`shared-events.DomainEvent`) — поля
    `trace_id`, `event_version`, `source` добавлены required во все
    19 event-schemas. 3 Java publisher'а мигрированы на
    `AbstractEventPublisher.fillDefaults()`; Python `event_publisher.py`
    пишет тот же envelope. `AbstractEventConsumer.withTraceContext()`
    восстанавливает MDC на стороне consumer'а. Cross-service trace
    correlation Java ↔ Python ↔ RabbitMQ.
  - **Python-бот observability** — `bot/observability.py`: structlog
    JSON-processor + stdlib bridge (aiogram/aio_pika/grpcio один
    формат) + OTLP tracer + auto-instrumentation. `bind_trace_context`
    context manager; `ObservabilityMiddleware` на `dp.update` с
    user_id/callback_type/trace_id. `_mask_pii` processor маскирует
    Telegram ID (M04 G11).
  - **Business metrics** — 8 counter'ов (`auth.login{role}`,
    `auth.logout{cause}`, `otp.request{channel}`, `otp.verify{outcome}`,
    `attendance.checkin{status}`, `excuse.created{kind}`,
    `late_checkin.created`, `internal_jwt.fallback{from,to}` для KI-2)
    + 3 gauge (`attendance.students_in_red_zone` через RedZoneGauge
    с @Scheduled + @SchedulerLock, `notification.active_ws_sessions`
    через SessionConnected/DisconnectEvent, `outbox.lag.seconds` через
    новый `OutboxStorage.oldestPendingAgeSeconds()`).
  - **Alertmanager end-to-end** — `prom/alertmanager:v0.27.0` контейнер,
    `alertmanager.yml` с routing (critical всегда, warning muted
    22-08 MSK через `time_intervals` v0.27+), 8 alert rules в 4
    группах (service-health / outbox-eventing / infra / business-anomaly).
    `POST /internal/alert` в notification-web (Bearer auth, constant-time
    secret check) → RabbitMQ `alert.fired` → notification-bot handler
    → Telegram админам (`ADMIN_TELEGRAM_IDS`). HTML-escape + truncate
    description до 3500 chars. 7 Java + 7 Python unit-тестов.
  - **Retention** — Prometheus 14d, Loki 336h + compactor, Tempo 14d.
  - **Grafana dashboard** `business-kpis-m04` — 8 панелей (checkin/login
    rate, red zone stat, WS sessions stat, outbox lag, JVM heap %,
    RabbitMQ queue depth, KI-2 fallback rate).
  - **Audit** — bug-hunter / security-auditor / code-reviewer subagents.
    0 BLOCKER/CRITICAL. 5 HIGH (все пофикшены: RedZoneGauge
    self-invocation, AlertController unchecked cast, CheckinRateZero
    absent() branch, PII masking, Telegram description truncate).
    Подробности в `docs/milestones/M04-observability/NOTES.md`.
  - **Документация** — `docs/observability.md` runbook,
    `docs/alerts.md` каталог, раздел Observability в
    `docs/architecture.md`, `docs/logging-conventions.md`.

- **M03b Secure Boundaries Part B** — JWT HttpOnly cookie + WS-ticket
  handshake + logout lifecycle + hot-patches из M03a (tag
  `v0.0.0-alpha.4`). Закрывает C0-5, C0-7, KI-3/6/7/8.
  - `services/auth-service`:
    - `AuthCookies` — factory HttpOnly+Secure+SameSite=Strict+Path=/api/auth
      cookie для refresh-token. Единый `baseBuilder()` для issue и clear.
    - `POST /auth/refresh` — теперь читает `@CookieValue("rct_refresh")`,
      ротирует cookie. Старое body-based поведение перенесено в
      `POST /auth/refresh-body` (**DEPRECATED**, `Deprecation: true` +
      `Sunset: Mon, 01 Jun 2026 00:00:00 GMT`, removal в M04/M05).
    - `POST /auth/logout` — добавлен в `permitAll` (cookie-only logout
      работает без access-JWT). Invalidate'ит все ws-ticket'ы
      пользователя если Bearer есть, revoke'ит refresh-token, clear'ит cookie.
    - `WsTicketController` / `WsTicketService` / `InternalWsTicketController`
      — выдача и consume single-use 30s WebSocket ticket'ов. Storage:
      `ws_ticket:<uuid>` + `ws_ticket_user:<uid>` Set (atomic SADD+EXPIRE
      через Lua). Consume через Lua-script `GET + DEL + SREM`. REST
      internal endpoint `/internal/consume-ws-ticket` защищён
      `X-Internal-Issuer-Secret` (переиспользует паттерн M03a).
    - `BcryptConcurrencyGuard` (KI-7) — Semaphore (fair, N=20) вокруг
      bcrypt в `login` и `changePassword`. Fail-fast 429 при заполнении.
    - `LoginRateLimiter` (KI-6) — atomic INCR+EXPIRE через Lua-script,
      убирает TTL-race при network blip.
    - `IT`: `LogoutLifecycleIT`, `WsTicketIT`, `BcryptDoSMitigationIT`.
  - `services/api-gateway`:
    - `LoginBodyExtractionFilter` (KI-8) — GlobalFilter order=-50,
      читает JSON body POST `/api/auth/login`, извлекает `login`, ставит
      X-Login header в mutated request. Composite `(ip, login)` rate-limit
      теперь реально работает.
    - `InternalJwtIssuerClient` (KI-3) — проверяет `expiresAt < now+5s` →
      invalidate cache + retry loader. Защита от clock drift.
    - `/api/auth/logout` добавлен в PUBLIC_PATHS.
  - `services/notification-service`:
    - `TicketHandshakeInterceptor` заменяет `JwtHandshakeInterceptor` —
      читает `query.ticket`, вызывает internal REST `/internal/consume-ws-ticket`,
      кладёт `userId/role/groupId/isHeadman` в STOMP session attributes.
    - `WsTicketClient` — REST-клиент с `X-Internal-Issuer-Secret`.
  - `frontends/pwa` (breaking):
    - `AuthProvider` — access-token только в memory (React state + tokenRef),
      refresh через cookie auto-send, bootstrap на mount.
    - `clearAllClientState(accessToken?)` — очищает localStorage/sessionStorage,
      SW runtime caches, unsubscribe + DELETE push/subscribe (с Bearer).
    - `wsTicket.ts` — pre-fetch ticket'а перед WebSocket connect.
    - Миграционный helper удаляет legacy `localStorage['rct.auth.v1']`.
  - `frontends/web-panel` (breaking):
    - `auth.service.ts` — access-only, refresh в cookie, `setAccessToken`
      вместо `setTokens`. `bootstrap()` через `provideAppInitializer`.
    - `clear-all-client-state.ts` — local + session storage.clear.
    - `ws-ticket.ts` — `acquireWsTicket` + `buildWsUrl` для 3 STOMP-сервисов.
    - `AuthInterceptor` — cookie-based refresh без body.

### Changed

- **CSRF infrastructure НЕ вводится** в M03b — `SameSite=Strict` +
  same-origin deployment (`ruttrack.site`) делают double-submit token
  избыточным (DECISIONS 2026-04-20, подтверждение OWNER-ANSWERS
  02-Q-frontend-security). Conditional follow-up: добавить double-submit
  token если в v1.0+ появится второй origin (OAuth callback).
- **Breaking**: refresh-token больше не возвращается в body response
  `/auth/login` (устарело, клиенты должны читать из cookie).
- **Breaking**: WebSocket `?token=<JWT>` → `?ticket=<uuid>` через
  pre-connect `POST /auth/ws-ticket`. Старые клиенты получат 401 при
  handshake.
- **Breaking**: `/auth/refresh` игнорирует body. TMA/Mini App на
  `/auth/refresh-body` (deprecated).
- `SecurityConfig` auth-service: `/auth/logout` перенесён в `permitAll`
  (cookie-only flow без Bearer).

### Fixed

- **KI-3** (M03a post-mortem): clock drift edge case — `InternalJwtIssuerClient`
  больше не возвращает токен с `expiresAt` близким к прошлому.
- **KI-6**: Redis TTL race в `LoginRateLimiter` — atomic Lua INCR+EXPIRE.
- **KI-7**: bcrypt DoS — concurrent invalid-password flood теперь
  fail-fast 429 через Semaphore guard, CPU не захлёбывается до того как
  `checkBlocked` сработает.
- **KI-8**: composite `(ip, login)` rate-limit на `/api/auth/login` был
  broken — клиентский `X-Login` strip'ался `JwtAuthenticationFilter`
  (CRIT-01). Теперь `LoginBodyExtractionFilter` извлекает login из
  body внутренне.
- **security-audit MEDIUM-1**: PWA `clearAllClientState` DELETE
  push/subscribe без Bearer → 401 → subscription не удалялась →
  cross-user push leak на shared-устройстве. Фикс: передаём accessToken
  до обнуления.
- **bug-hunt HIGH-2**: `WsTicketService.issue()` SADD+EXPIRE был
  неатомарным — persistent user-set без TTL при network blip. Фикс:
  Lua-script `ADD_TO_SET_SCRIPT`.

### Documentation

- `docs/auth-flow.md` — полный runbook cookie + ws-ticket + logout
  lifecycle (диаграммы, cookie контракт, endpoints, breaking changes,
  security-свойства, rate-limits).
- `docs/milestones/M03b-jwt-cookie-ws-ticket/` — PLAN, CHECKLIST, NOTES,
  DECISIONS (5 micro-ADR'ов: cookie Path, SameSite, CSRF, ws-ticket
  storage scheme, refresh-body deprecation timeline, KI-7 Semaphore
  выбор, event `user.logged-out` откладывается в M04).

- **M03a Internal JWT + Rate-limiting** — Zero Trust Level 2 + brute/DoS защита
  (tag `v0.0.0-alpha.3`). Закрывает 02-Q2, кластер C0-1, C0-4, NEW-3, NEW-4,
  NEW-9, NEW-11, 14 P1-1, 14 P1-2.
  - `services/shared/shared-security/` — Internal JWT validator библиотека
    (`InternalJwtValidator`, `PublicKeyProvider` RestClient,
    `DualModeUserContextFilter` abstract, `InternalJwtTestFactory` testFixtures,
    `InternalJwtProperties`). 18 unit-тестов.
  - `auth-service` — `POST /internal/issue-internal-jwt` token-exchange endpoint
    (RFC 8693 pattern). `InternalIssuerSecretFilter` с timing-safe
    `MessageDigest.isEqual`. `JwtService.generateInternalToken` (RS256, TTL 5
    мин, aud=`rutcampustrack-internal`). 11 тестов.
  - `api-gateway` — `InternalJwtIssuerClient` (WebClient + Caffeine `AsyncCache`
    `(userId, role) → IssuedToken` TTL 240s) + `InternalJwtIssuerFilter`
    (GlobalFilter order=-50) прокидывает `X-Internal-Token` downstream.
    Error 503 на недоступность auth-service. 19 тестов через WireMock.
  - **Downstream миграция** (academic/schedule/attendance/notification):
    каждый имеет `{Service}UserContextFilter extends DualModeUserContextFilter`,
    `InternalJwtConfig` (+PublicKeyProvider bean), `InternalJwtTestConfig`
    (@Primary in-memory keypair для IT). IT-тесты dual-mode + strict-mode
    (9+3 per service). Build: academic 197, schedule 108, attendance 157,
    notification 59 тестов.
  - **Rate-limiting Gateway** (Spring Cloud Gateway `RedisRateLimiter`):
    - 4 `KeyResolver` бина: ip / userId (X-User-Id) / login (X-Login) /
      `ip+login` composite.
    - `@Primary FailOpenRateLimiter` обёртка — на Redis-outage
      (`RedisConnectionFailureException`, Lettuce timeouts) возвращает
      `allowed=true` + WARN + `X-RateLimit-FailOpen: true`.
    - 6 rate-limited роутов: `/auth/otp/request` 1/burst IP, `otp/verify-by-code`
      5/burst IP, `/auth/login` 5 IP + 10 composite, `/auth/refresh[-body]`
      30/user, `/attendance/check-in` 10/user, `/api/{academic,schedule,
      attendance,push}/**` 600/IP DDoS guard.
    - `RateLimitProblemDetailsFilter` response-decorator (perехватывает
      `setComplete()` от `RequestRateLimiter`): 429 → `application/problem+json`
      body + `Retry-After: 60`.
  - **`LoginRateLimiter` рефактор** (01 P0-6): composite `(ip, login)` Redis
    key, AuthService/Controller прокидывают IP из `X-Forwarded-For`. Фикс
    DoS-by-rate-limit — атакующий с одного IP больше не может залочить
    чужой аккаунт. 14 тестов (11 unit + 3 IT).
  - **Strict-mode toggle** (Группа 14): `InternalIssuerClientProperties.stripLegacyHeaders`
    (env `GATEWAY_STRIP_LEGACY_HEADERS`, default false). При `true` Gateway
    удаляет `X-User-Id/Role/Group-Id/Is-Headman` после issue — downstream
    работает только через Internal JWT. Парный флаг downstream
    `RUTCAMPUSTRACK_SECURITY_LEGACY_HEADERS_ENABLED=false`.
  - **Contract-тесты** (14 P1-1): `InternalJwtIssuerIT` — E2E через WireMock
    (auth-service + downstream). Валидный JWT → downstream видит
    X-Internal-Token; невалидная подпись/истекший/нет header → 401
    без вызова downstream. 4 теста.
  - **Rate-limit тесты** (14 P1-2): `RateLimitIT` (Testcontainers Redis,
    429 + Problem Details + Retry-After, composite isolation),
    `FailOpenIT` (Redis connection refused → 10 запросов проходят),
    `CompositeLoginKeyResolverIT`. 5 тестов.
  - **Документация:**
    - `docs/internal-jwt-spec.md` (NEW-3) — формат, claims, ключи,
      token-exchange flow, dual/strict mode, downstream-валидация.
    - `docs/api-rate-limits.md` (NEW-11) — таблица лимитов, 429 поведение,
      fail-open, клиентские рекомендации retry-with-backoff и `X-Login`.
    - `docs/architecture.md` — раздел «Internal JWT и rate-limiting» после
      «Reliable eventing».
  - **3 критичных фикса context startup** (обнаружены первым @SpringBootTest
    Gateway): `@Primary` на `ipKeyResolver` (RequestRateLimiterGatewayFilterFactory
    требует уникальный default bean), `@Autowired` на primary-конструктор
    `InternalJwtIssuerClient` (2 конструктора без аннотации давали
    NoSuchMethodException), `RateLimitProblemDetailsFilter` перехватывает
    `setComplete()` (RequestRateLimiter не вызывает writeWith на denied path).

- **M02 Reliable Eventing** — гарантированная доставка событий RabbitMQ:
  - `services/shared/shared-outbox/` — storage-agnostic API `OutboxStorage` с
    двумя реализациями (`JpaOutboxStorage<E>` для PG, `MongoOutboxStorage` для
    Mongo). `OutboxPublisherJob` (`@Scheduled` + `@SchedulerLock`),
    `OutboxCleanupJob` (cron 3am + retention 7д), `OutboxMetrics` (gauge).
  - Flyway миграции: `schedule V10__shedlock_table` + `V11__schedule_outbox`,
    `academic V15__shedlock_table` + `V16__academic_outbox` (partial-индексы
    `(status=pending, created_at)` и `(status=sent, sent_at)`). Mongo
    collection `attendance_outbox` auto-created.
  - ShedLock infrastructure: `shedlock-spring` + `shedlock-provider-jdbc-template`
    (PG) / `shedlock-provider-mongo` (Mongo). `@SchedulerLock` на
    `LessonStatusTransitionJob.runTransitions`, `OutboxPublisherJob.tick`,
    `OutboxCleanupJob.tick`. `PublicKeyConfig.refresh` в api-gateway помечен
    `@SuppressWarnings("SingleInstance")` (single-instance by design).
  - Micrometer метрики: `outbox.lag` (gauge pending count), `outbox.published.total`,
    `outbox.failed.total` (counter, tag `event_type`).
  - `event-schemas/_common.json` с `$defs` (eventId, occurredAt, traceId,
    eventVersion, lessonNumber). 19 существующих schemas отрефакторены на `$ref`.
  - `docs/event-schemas.md` — versioning policy + bump процедура + $ref guide.
  - Раздел «Reliable eventing» в `docs/architecture.md` с диаграммой outbox flow.
  - 5 contract-тестов per event-type через 3 сервиса (lesson.started/closed/
    cancelled, group.updated, attendance.marked). Используют
    networknt json-schema-validator 1.5.4 против реального payload из outbox.
  - ArchUnit rule `ScheduledMustHaveSchedulerLockTest` в каждом сервисе —
    защита от NEW-28 (любой `@Scheduled` метод без `@SchedulerLock`).
  - Конфиг: `rutcampustrack.outbox.publisher.fixed-delay-ms:5000`,
    `rutcampustrack.outbox.cleanup.cron:"0 0 3 * * *"`,
    `rutcampustrack.outbox.retention-days:7`.

- **M01 Shared Foundations** — 4 shared-модуля под `services/shared/`:
  - `shared-web` — RFC 9457 `ErrorResponse` + `GlobalExceptionHandler`
    (9 стандартных Spring MVC handlers + catch-all с correlation=traceId),
    validation-аннотации `@StartBeforeEnd`, `@DateRangeValid`, `@ValidFile`,
    `JacksonConfig` с едиными настройками, `@AdminAction` marker +
    aspect-заглушка (audit handler в M04), `SharedOpenApiCustomizer`
    заглушка (обогащение OpenAPI-спеки в M06).
  - `shared-events` — `DomainEvent` abstract base (`event_version`/`trace_id`/
    `occurred_at`/`source` в snake_case JSON), `@EventVersion` marker,
    `AbstractEventPublisher.fillDefaults()`, `AbstractEventConsumer.withTraceContext()`.
  - `shared-logback` — `shared/logback-base.xml` (JSON stdout через
    `LoggingEventCompositeJsonEncoder`), `MaskingJsonProvider` с regex-маскированием
    Bearer JWT / telegram_id / FCM endpoint.
  - `shared-test-containers` — `java-test-fixtures` модуль с `ContainerTestBase`
    (Postgres/Mongo/Redis/RabbitMQ, `reuse=true`), `GrpcInProcessFixture`,
    `WireMockFixture`, `MigrationTestUtils`.
- `gradle/libs.versions.toml` — Version Catalog с версиями shared-модулей.
- `docs/shared-modules-usage.md` — quick-start для подключения shared-модулей.
- Раздел «Shared modules» в `docs/architecture.md`.
- `notification-service` — первый сервис-потребитель shared foundations
  (acceptance-тесты `NotificationErrorHandlingIT` + `NotificationLoggingIT`).

### Changed

- `notification-service/NotificationWebApplication` — `scanBasePackages`
  расширен до `ru.rutcampustrack.shared.web` для подхвата shared beans.
- `notification-service` — добавлен runtime `spring-security-core` для
  `AccessDeniedException` handler'а из shared-web.
- **M02**: `DomainEventListener` в academic и schedule переведён с
  `@TransactionalEventListener(AFTER_COMMIT) → rabbitTemplate.convertAndSend`
  на `BEFORE_COMMIT → outboxStorage.save`. Actual publish в Rabbit делает
  `OutboxPublisherJob` асинхронно. Закрывает 02 P0-6 message loss.
- **M02**: 3 direct-publisher'а в attendance (`AttendanceEventPublisher`,
  `ExcuseEventPublisher`, `LateCheckinEventPublisher`) — переписаны с
  `rabbitTemplate.convertAndSend` на `outboxStorage.save` через
  `ObjectMapper.writeValueAsString(envelope)`.
- **M02**: `OutboxEntity.payload` — `@JdbcTypeCode(SqlTypes.JSON)` для
  корректного mapping на PG jsonb в Hibernate 6.

### Fixed

- `notification-service` ранее не имел `@RestControllerAdvice` — любая
  validation-ошибка отвечала generic 500. Теперь — RFC 9457 `application/problem+json`
  с `invalidParams[]` (closes 05 P0-2).
