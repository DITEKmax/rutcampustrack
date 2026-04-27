# M16 Checklist

Атомарные задачи в порядке выполнения. Группы идут по убыванию impact.

## Группа 1 — OTel exporter port fix (G1, ~0.5д)

- [x] Прочитать текущую `management.otlp.tracing.endpoint` секцию во всех 6 application.yml — убедиться что везде один pattern
- [x] Поменять endpoint default с `:4317` на `:4318/v1/traces` в `auth-service/application.yml`
- [x] То же в `academic-service/application.yml`
- [x] То же в `schedule-service/application.yml`
- [x] То же в `attendance-service/application.yml`
- [x] То же в `notification-service/notification-app/application.yml`
- [x] То же в `api-gateway/application.yml`
- [x] Поменять `OTEL_EXPORTER_OTLP_ENDPOINT` env var в `docker-compose.yml` (notification-web → 4318, notification-bot оставлен на 4317 — gRPC exporter)
- [x] То же в `docker-compose.prod.yml` (6 Java сервисов → 4318, notification-bot → 4317)
- [x] Проверить `infra/tempo/tempo.yml` — 4318 слушается; в `docker-compose.yml` добавлен expose 4318 для dev host-сценария
- [x] Поправить комментарий в `scripts/m07-g3-launch-services.sh` (endpoint там фейковый, sampling 0.0)
- [x] VPS verify (2026-04-27): `docker logs rct-auth-service --since 1h | grep -c "Connection reset"` → **0** ✅
- [ ] Smoke UI: сделать любой request через gateway → найти trace_id в Grafana Tempo — будет в UAT
- [x] Commit: `fix(otel): use HTTP/protobuf port 4318 for Java services (M16 G1)` — `fe652149`

## Группа 2 — Dispatcher exception propagation + DLQ retention (G2 v2, ~1.5-2ч)

**Изменение скоупа vs исходного плана:** идемпотентность уже была сделана
в **M13 G8** через `BotIdempotencyGuard.try_claim` (Redis SET NX TTL 1h
+ 7 unit-тестов в `test_idempotency_guard.py`). `future-ideas.md`
HIGH.2 устарел. Реальная оставшаяся проблема — `dispatcher.dispatch()`
swallow'ил handler exceptions, делая silent loss event'ов и нивелируя
G24-fix-2 в consumer (см. `DECISIONS.md` § D5).

- [x] Прочитать `event_dispatcher.py` + `event_consumer.py` + `idempotency_guard.py` — подтверждено что G8 idempotency полностью wired
- [x] `dispatcher.dispatch()`: убрать `try/except Exception` swallow — exception проходит до consumer'а → DLQ
- [x] `event_consumer.py`: DLQ декларация добавила `x-message-ttl=7d` + `x-max-length=10000` + `x-overflow=drop-head` (без этого DLQ растёт бесконечно — то же что N6 для Java)
- [x] `event_consumer.py`: обновлён комментарий чтобы отражать реальный flow (M13 G24-fix-2 + M16 G2 — handler bug → DLQ)
- [x] `test_event_dispatcher.py`: `test_dispatch_handler_exception_is_caught` перевёрнут → `test_dispatch_handler_exception_propagates` (`pytest.raises`)
- [x] `test_dispatch_handler_exception_is_logged` — удалён (логирование идёт в consumer'е, не dispatcher'е)
- [x] Verify alert уже существует — `DLQBacklog` в `infra/prometheus/rules/rabbitmq.yml` ловит >10/5min на любой `*.dlq` (создавать новый не надо)
- [x] Создан runbook `docs/operations/runbooks/dlq-triage.md`
- [x] Локально / CI: прогнать `pytest services/notification-bot/tests/test_event_dispatcher.py` → зелёный (15 passed 2026-04-27)
- [x] VPS verify (2026-04-27): `rabbitmqctl list_queues name messages | grep dlq` → **5 DLQ queues declared, all empty** (attendance/notification-bot/schedule/notification-web events.dlq + notification-web.history.dlq). Handler-bug сценарий — будет проверен через UAT. ✅
- [x] Commit: `fix(bot): propagate handler exceptions for DLQ routing + 7d retention (M16 G2)` — `f9cbca52`

## Группа 3 — OTP brute-force counter (G3, ~1д)

- [x] Прочитать текущий `OtpService.verifyOtpByCode` — signature `(OtpVerifyByCodeRequest)`, вызывается из `AuthController:133`
- [x] Добавить параметр `String clientIp` в `OtpService.verifyOtpByCode`
- [x] `AuthApi.verifyOtpByCode` — добавлен `HttpServletRequest httpRequest` (тот же pattern что у `login`)
- [x] `AuthController.verifyOtpByCode` — pass `resolveClientIp(httpRequest)` в service
- [x] Реализован counter в Redis: `INCR otp_verify_by_code_miss:<ip>` + `EXPIRE 300` (только при первом INCR)
- [x] Pre-check: если counter ≥ 20 → `OtpRateLimitException` (429) **без** проверки кода
- [x] Decision: reset-on-success **НЕ делается** (атакующий мог бы случайно угадать valid code и обнулить counter — см. DECISIONS § D6)
- [x] Counter уже использует существующий `BusinessMetrics.otpVerifyCounter(outcome)` — добавлены outcome'ы `throttled` и сменён `expired` → `mismatch` на verify-by-code path
- [x] `OtpProperties` — добавлены поля `verifyByCodeMissesPerWindow=20`, `verifyByCodeWindowSeconds=300`
- [x] `application.yml` — defaults для новых полей
- [x] 5 unit-тестов: under-limit mismatch, first-mismatch EXPIRE, at-limit 429, different IPs independent, null IP → unknown
- [x] Prometheus rule `OtpBruteForceSuspect` в `infra/prometheus/rules/service-health.yml` — `rate(otp_verify_total{outcome="mismatch"}[5m]) > 10` for 2m
- [x] OpenAPI snapshot `docs/openapi/auth.json` — 429 уже был включён через `SharedOpenApiCustomizer`, новый `@ApiResponse(429)` идемпотентен
- [x] Прогон тестов: `./gradlew :services:auth-service:auth-app:test --tests OtpServiceTest` → 9/9 passed
- [x] VPS verify (2026-04-27): прямой HTTP-test через `/api/auth/otp/verify-by-code` упирается в **Gateway RateLimiter (5/min/IP)** РАНЬШЕ чем в OTP-counter (20/5min/IP) — это **корректное поведение по design** (defense-in-depth, см. DECISIONS § D6). Code deployed (commit ce5c404d), unit/IT 9/9 passed. UI flow проверим через UAT — Prometheus метрика `otp_verify_total{outcome="mismatch"}` должна расти при попытках неверного OTP. ✅
- [x] Commit: `feat(auth): /otp/verify-by-code brute-force counter + alert (M16 G3)` — `ce5c404d`

## Группа 4 — Loki `InstancesCount <= 0` diagnose (G4, ~0.5д)

- [x] Прочитать `infra/loki/loki.yml` — single-instance с inmemory ring + replication_factor=1
- [x] Research: Mimir/Loki tracker (issue #4662, Loki docs Configure) — root cause = startup race между distributor и ingester gRPC listener (ring видит self но connect fails → InstancesCount=0)
- [x] **Fix**: `ingester.lifecycler.min_ready_duration: 15s` (default, эксплицитный для документации) — `/ready` возвращает 200 только после полного startup
- [x] **Fix**: docker-compose.prod.yml loki healthcheck (`/ready`) + promtail `depends_on: loki: condition: service_healthy` — promtail не push'ит до того как ingester ACTIVE
- [x] Зафиксировать root cause + decision в `DECISIONS.md` § D7
- [x] Создан `docs/operations/runbooks/loki-troubleshooting.md` — 4 типа ошибок (InstancesCount, context canceled, entry too far behind, OOM)
- [x] VPS verify (2026-04-27, freshly deployed Loki): `docker logs rct-loki --since 24h | grep -c "InstancesCount"` → **0** (vs прошлое 1-2/час). Loki started 17:56:44, ingester ACTIVE через 50ms, promtail healthcheck-gated — race не триггерится. ✅
- [x] Escalation runbook не понадобился — root cause устранён через `min_ready_duration: 15s` + healthcheck depends_on.
- [x] Commit: `fix(loki): wait-for-ready before promtail push to fix startup race (M16 G4)` — `221daf6b` (single combined commit, root cause + fix вместе)

## Группа 5 — nginx DNS race + verify-deploy.sh fix (G5, ~0.5д)

**Расширенный скоуп:** в одном PR закрываем (a) DNS race (root) и (b)
4 false-positive fail'а в `verify-deploy.sh`, описанные в
`future-ideas.md` § «verify-deploy.sh устарел до v9.0».

### nginx DNS race fix

- [x] Прочитать `nginx/conf.d/default.conf` — 15 `proxy_pass http://rct-*` upstream'ов
- [x] Добавлен `resolver 127.0.0.11 valid=10s ipv6=off;` в HTTPS server-блок (Docker embedded DNS)
- [x] Объявлены 8 переменных-upstream'ов (`$upstream_gateway`, `$upstream_landing`, `$upstream_pwa`, `$upstream_mini_app`, `$upstream_web_panel`, `$upstream_grafana`, `$upstream_prometheus`, `$upstream_alertmanager`)
- [x] Заменены все 15 `proxy_pass http://rct-X:N` на `proxy_pass http://$upstream_X` (variable triggers runtime resolve)
- [x] Validate: `docker run nginx:alpine nginx -t` → syntax OK
- [x] VPS verify (2026-04-27): `docker compose restart grafana && sleep 5 && curl /grafana/` → **401** (basic-auth required) — это **нормальный** код (M11 G4 SwaggerUI/Grafana basic-auth). Главное — **не 502**: nginx alive после restart, runtime DNS resolve работает. ✅

### verify-deploy.sh обновления (4 false-positive fix)

- [x] `expect_status` поддерживает comma-separated list (`"200,301"` для root)
- [x] Новый `expect_status_retry` (5 attempts × 2s) для post-restart upstream'ов
- [x] Section 2: `/` ожидает 301 (INFRA-v9-01 redirect), `/login`+`/app/`+`/presentation/` через retry
- [x] Section 2: gateway health через `docker exec rct-api-gateway wget /actuator/health` (real endpoint)
- [x] Section 8 Mongo TTL: использует `MONGO_INITDB_ROOT_USERNAME/PASSWORD` (PoLP user не имеет dbAdmin)
- [x] Bash syntax check (`bash -n`) — passes
- [ ] Verify на VPS UAT: `./scripts/verify-deploy.sh` → 0 false-positives (deferred — пользователь прогонит при желании, не блокирует closure)

- [x] Commit: `fix(nginx,scripts): runtime DNS for upstream + verify-deploy false-positives (M16 G5)` — `b0e351ee`

## Группа 6 — @AdminAction audit log v1 (G6, ~1д)

**Reduced scope vs initial plan:** v1 без before/after diff и без
target_type/target_id (требует deep cloning + JSON diff lib + param
annotation infrastructure). Разметка только UserController (5 actions)
— остальные 5 контроллеров отложены в follow-up. Aspect автоматом
подхватит новые `@AdminAction` помеченные методы без code changes.

- [x] Flyway V19 — таблица `audit_log` (BIGSERIAL PK, user_id, action, target_type, target_id, correlation_id, extras JSONB, succeeded, error_message, created_at)
- [x] Flyway V20 — индексы `CREATE INDEX CONCURRENTLY` (отдельная миграция с `-- ##`, согласно CLAUDE.md)
- [x] `MigrationConcurrentlyTest` зелёный — V19 (без index) + V20 (CONCURRENTLY) проходят guard
- [x] SPI `AuditLogStorage` interface в `services/shared/shared-web/.../audit/`
- [x] SPI `AuditLogContextProvider` interface (current userId + correlationId, ANONYMOUS fallback)
- [x] Value object `AuditLogEntry` record + Builder
- [x] `AdminActionAspect` переписан: real handler через ObjectProvider'ы (graceful degradation если storage/context отсутствуют)
- [x] `JdbcAuditLogStorage` (academic-app) — INSERT через JdbcTemplate, JSONB extras сериализация
- [x] `AcademicAuditLogContextProvider` (academic-app) — `RequestContext.userId` + MDC `traceId`
- [x] 6 unit-тестов AdminActionAspectTest (extended) — proxy/storage write/exception path/storage failure swallow/no-annotation/exception propagation
- [x] Разметка `@AdminAction` в UserController: `user.create`, `user.update`, `user.patch`, `user.archive`, `user.transfer`
- [x] Compile + tests passing: `:services:shared:shared-web:test` 24/24, `:services:academic-service:academic-app:compileJava` clean
- [x] Runbook `docs/operations/runbooks/audit-log.md`
- [x] VPS verify (2026-04-27): `SELECT * FROM audit_log WHERE action='user.archive' LIMIT 5;` → **0 rows** (deployed table & schema confirmed, **никто ещё не делал admin.archive после deploy**). UAT proverit через UI: archive test user → row appears. ✅ table готова.
- [x] Commit: `feat(audit): @AdminAction real handler via SPI + 5 user actions (M16 G6)` — `5c3b7e93`

### Отложено в M16 follow-up / future-ideas:
- before/after diff (deep cloning + JSON diff)
- `@AuditTarget` param annotation для target_type/target_id
- Разметка GroupController / SemesterController / SubjectController / ThresholdController / AssignmentController

## Группа 7 — headman rate-limit Redis 300/min (G7, ~0.5д)

- [x] Прочитан текущий `headmanBuckets` ConcurrentHashMap в `AcademicGrpcServiceImpl` (M06 G8b)
- [x] Создан `HeadmanRateLimiter` interface (`tryConsume(userId)`)
- [x] `RedisHeadmanRateLimiter` — INCR `rl:headman:{userId}:{minute}` + EXPIRE 65s, primary impl
- [x] `InMemoryHeadmanRateLimiter` — fallback (тот же ConcurrentHashMap + TokenBucket код вынесен), `@ConditionalOnProperty redis-enabled=false`
- [x] `HeadmanRateLimitProperties` (perMinute / windowSeconds) + `HeadmanRateLimitConfig` (`@EnableConfigurationProperties`)
- [x] Externalized в `application.yml` под `academic.headman-rate-limit.*`, default `per-minute: 300` (с env override `ACADEMIC_HEADMAN_RL_PER_MINUTE`)
- [x] `AcademicGrpcServiceImpl` рефакторен — inline ConcurrentHashMap+TokenBucket удалён, инжектируется `HeadmanRateLimiter`
- [x] Existing IT обновлён: лимит 120→300 (`AcademicGrpcIT.isHeadman_rateLimitExceeded_throwsResourceExhausted`)
- [x] 4 unit-теста на InMemoryHeadmanRateLimiter (under/over/independent/300-default)
- [x] 6 unit-тестов на RedisHeadmanRateLimiter (mock'и StringRedisTemplate — first/subsequent/at-limit/over/fail-open/independent users)
- [x] Fail-open при Redis exception — counter `headman_rl_redis_failures_total` для visibility
- [x] Compile + tests passing: `:services:academic-service:academic-app:test` 10 new tests + 32 existing unit suite, BUILD SUCCESSFUL
- [ ] VPS verify UAT: bulk-mark группы 30 студентов <30 сек без RESOURCE_EXHAUSTED — будет проверено через staros UI flow
- [ ] VPS verify UAT: `redis-cli KEYS "rl:headman:*"` после bulk-mark — должны появиться entries (требует $REDIS_PASSWORD из .env.prod)
- [x] Commit: `refactor(academic): headman rate-limit moved to Redis, raised to 300/min (M16 G7)` — `7a280a01`

## Группа 8 — mTLS Alertmanager → notification-web (G8a only, ~30 мин)

**Reduced scope vs initial plan (D13):** owner выбрал **G8a** (cap_drop NET_RAW
defense-in-depth), G8b (full mTLS с internal CA) отложен в `future-ideas.md`
с explicit trigger condition (multi-host deploy / compliance). Reasoning —
текущая single-VPS topology + digest-pinned images дают low surface для
sniff-vector'а; полный mTLS overkill для текущего scale.

- [x] **Decision D13** зафиксирован: G8a-only, G8b deferred
- [x] `docker-compose.prod.yml` — `cap_drop: [NET_RAW]` для cadvisor
- [x] `docker-compose.prod.yml` — `cap_drop: [NET_RAW]` для node-exporter
- [x] `docker-compose.prod.yml` — `cap_drop: [NET_RAW]` для blackbox-exporter
- [x] `docker-compose.prod.yml` — `cap_drop: [NET_RAW]` для alertmanager (symmetric defense)
- [x] Validate compose syntax — 4 cap_drop entries + parsed correctly
- [x] G8b (full mTLS) перенесён в `future-ideas.md` MED-11 с trigger condition
- [ ] VPS verify UAT: контейнеры живые (визуально через Grafana или `docker ps`), webhook flow работает — будет проверено в UAT
- [x] Commit (комбинированный с G9): `security: drop privileged + NET_RAW from infra containers (M16 G8a + G9)`

## Группа 9 — cadvisor de-privileged (G9, ~0.5д)

**Подход (Decision D14):** убрать `privileged: true`, добавить `devices:
[/dev/kmsg:/dev/kmsg]` (per upstream docs `running.md`), default Docker
capabilities + read-only mounts достаточны. `cap_add: [SYS_PTRACE]` НЕ
требуется — research confirmed (cadvisor issue #3051: «running without
privileged but as root was necessary to access all required metrics»).

- [x] Research: cadvisor docs `running.md` + issue #3051 (запуск без privileged как root work'ает с default caps)
- [x] `docker-compose.prod.yml` cadvisor: убрать `privileged: true`
- [x] Добавить `devices: [/dev/kmsg:/dev/kmsg]` для kernel-log access (OOM events tracking)
- [x] **НЕ** добавлять `cap_add: [SYS_PTRACE]` — не требуется (default caps + read-only mounts достаточны)
- [x] Сохранены existing read-only mounts: `/`, `/var/run`, `/sys`, `/var/lib/docker`, `/dev/disk`
- [x] Decision D14 зафиксирован
- [x] Validate compose syntax — privileged: true исчез из parsed output
- [ ] VPS verify UAT: `up{job="cadvisor"} == 1` в Prometheus — UI test
- [ ] VPS verify UAT: `count by (name) (container_memory_usage_bytes)` > 20 (per-container metrics на месте) — Grafana check
- [ ] VPS verify UAT: Grafana dashboard `rct-containers` рендерится — UI test
- [ ] VPS verify UAT: `rate(container_oom_events_total[5m])` (deferred — не stress'им сами, smoke только что метрика exposed)
- [x] Commit (комбинированный с G8a): `security: drop privileged + NET_RAW from infra containers (M16 G8a + G9)`

## Финал

- [x] Production deployed (2026-04-27, run 25010954397) — commit `070369d6` на VPS, все 13 deploy jobs ✅. Verify-deploy.sh — optional UAT, не блокирует closure.
- [x] `CHANGELOG.md` — раздел `[Unreleased]` обновлён (M16 entry, 9 групп) — `2c45e71b`. Версия (tag) решена: оставляем без тега до VPS verify, post-redeploy решим `v0.0.0-alpha.17` либо `v0.1.0-rc.1`
- [x] Обновить `CLAUDE.md` — статус M16 ✅ — `2c45e71b` (заголовок «Текущий статус» + строка таблицы M16)
- [ ] PR через `gsd-pr-branch` (filter `.planning/`) или прямой push если работаем без feature-branch — **прямой push в main выбран** (consistent with M15 / M14 workflow, owner approved 2026-04-27)
- [ ] Tag (`v0.1.0-rc.1` или `v0.0.0-alpha.17`) — **отложен до VPS verify** (redeploy сначала, tag post-factum если verify passes)

---

_Если задача превращается в 6+ часов — разрезать._
