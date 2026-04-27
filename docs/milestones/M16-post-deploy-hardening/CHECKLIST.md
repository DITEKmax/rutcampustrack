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
- [ ] Локально `docker compose up -d` → проверить логи backend на `Connection reset` (должны исчезнуть) — verify на VPS после redeploy
- [ ] Smoke: сделать любой request через gateway → найти trace_id в Grafana Tempo — verify на VPS после redeploy
- [ ] Commit: `fix(otel): use HTTP/protobuf port 4318 for Java services (M16 G1)`

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
- [ ] Локально / CI: прогнать `pytest services/notification-bot/tests/test_event_dispatcher.py` → зелёный
- [ ] Verify на VPS после redeploy: handler-bug → message в DLQ → alert через 5 мин
- [ ] Commit: `fix(bot): propagate handler exceptions for DLQ routing + 7d retention (M16 G2)`

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
- [ ] Verify на VPS после redeploy: 21-я подряд `verifyOtpByCode` с одного IP → 429
- [ ] Commit: `feat(auth): /otp/verify-by-code brute-force counter + alert (M16 G3)`

## Группа 4 — Loki `InstancesCount <= 0` diagnose (G4, ~0.5д)

- [x] Прочитать `infra/loki/loki.yml` — single-instance с inmemory ring + replication_factor=1
- [x] Research: Mimir/Loki tracker (issue #4662, Loki docs Configure) — root cause = startup race между distributor и ingester gRPC listener (ring видит self но connect fails → InstancesCount=0)
- [x] **Fix**: `ingester.lifecycler.min_ready_duration: 15s` (default, эксплицитный для документации) — `/ready` возвращает 200 только после полного startup
- [x] **Fix**: docker-compose.prod.yml loki healthcheck (`/ready`) + promtail `depends_on: loki: condition: service_healthy` — promtail не push'ит до того как ingester ACTIVE
- [x] Зафиксировать root cause + decision в `DECISIONS.md` § D7
- [x] Создан `docs/operations/runbooks/loki-troubleshooting.md` — 4 типа ошибок (InstancesCount, context canceled, entry too far behind, OOM)
- [ ] Verify на VPS после redeploy + 24h: `docker logs rct-loki --since 24h | grep -c "InstancesCount"` → должно резко упасть (с 1-2/час до 0-1/день)
- [ ] Если **не упало** — escalation per runbook (gRPC keepalive timeout / verify single-instance)
- [ ] Commit: `fix(loki): wait-for-ready before promtail push to fix startup race (M16 G4)`
- [ ] Commit: `fix(loki): <root cause> — eliminates InstancesCount errors (M16 G4)`

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
- [ ] Verify на VPS: `docker compose restart grafana` → `curl https://ruttrack.site/grafana/` → 200/302 в течение 15 сек **без** ручного nginx restart

### verify-deploy.sh обновления (4 false-positive fix)

- [x] `expect_status` поддерживает comma-separated list (`"200,301"` для root)
- [x] Новый `expect_status_retry` (5 attempts × 2s) для post-restart upstream'ов
- [x] Section 2: `/` ожидает 301 (INFRA-v9-01 redirect), `/login`+`/app/`+`/presentation/` через retry
- [x] Section 2: gateway health через `docker exec rct-api-gateway wget /actuator/health` (real endpoint)
- [x] Section 8 Mongo TTL: использует `MONGO_INITDB_ROOT_USERNAME/PASSWORD` (PoLP user не имеет dbAdmin)
- [x] Bash syntax check (`bash -n`) — passes
- [ ] Verify на VPS: `./scripts/verify-deploy.sh` → 0 false-positives (все 4 предыдущих fail'а исчезают)

- [ ] Commit: `fix(nginx,scripts): runtime DNS for upstream + verify-deploy false-positives (M16 G5)`

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
- [ ] Verify на VPS после redeploy: SELECT FROM audit_log WHERE action='user.archive' → видны записи admin actions
- [ ] Commit: `feat(audit): @AdminAction real handler via SPI + 5 user actions (M16 G6)`

### Отложено в M16 follow-up / future-ideas:
- before/after diff (deep cloning + JSON diff)
- `@AuditTarget` param annotation для target_type/target_id
- Разметка GroupController / SemesterController / SubjectController / ThresholdController / AssignmentController

## Группа 7 — headman rate-limit Redis 300/min (G7, ~0.5д)

- [ ] Найти текущую implementation `headmanBuckets` (`ConcurrentHashMap` token bucket)
- [ ] Создать `RedisHeadmanRateLimiter` — INCR/EXPIRE pattern
- [ ] Лимит: **300 req/min** (повышен с 120 — старосты bulk-mark группу из 30 студентов за < 30 сек, прежний лимит триггерился)
- [ ] При наличии cluster-flag (или просто всегда) — использовать новый limiter; старый ConcurrentHashMap удалить
- [ ] Unit-тест на boundary 300 vs 301
- [ ] IT с Testcontainers Redis
- [ ] Обновить `docs/api/api-rate-limits.md` — лимит 300/min для headman bulk-операций
- [ ] Commit: `refactor(academic): headman rate-limit moved to Redis, raised to 300/min (M16 G7)`

## Группа 8 — mTLS Alertmanager → notification-web (G8, ~1-2д)

- [ ] **Decision** в `DECISIONS.md` — выбрать путь (Linkerd / nginx mTLS / cap_drop NET_RAW only)
- [ ] Если nginx-путь: новый `nginx/conf.d/internal.conf` listening на internal port с `ssl_verify_client on`
- [ ] Сгенерировать internal CA + client cert для Alertmanager + server cert для notification-web (`openssl` или local script)
- [ ] `docker-compose.prod.yml` — mount certs в Alertmanager и notification-web (или интерим nginx)
- [ ] `infra/alertmanager/alertmanager.yml` — `tls_config` для webhook
- [ ] **Defense-in-depth (всегда):** добавить `cap_drop: [NET_RAW]` к cadvisor, node-exporter, blackbox-exporter (блокирует sniffing)
- [ ] Verify: alert приходит в Telegram через mTLS-канал; без cert curl падает с 403
- [ ] `docs/operations/runbooks/secret-rotation.md` — добавить раздел про rotation internal CA
- [ ] Commit: `feat(security): mTLS for Alertmanager → notification-web (M16 G8)`

## Группа 9 — cadvisor de-privileged (G9, ~0.5д)

- [ ] Audit текущих метрик cadvisor через `curl localhost:8080/metrics | grep ^container_` (на dev) — снимок до
- [ ] `docker-compose.prod.yml` cadvisor: убрать `privileged: true`
- [ ] Добавить `cap_add: [SYS_PTRACE]`
- [ ] Тест с/без mount `/var/lib/docker:ro` — какие метрики пропадают (если только image labels — можно убрать mount, иначе оставить)
- [ ] Verify: container_* метрики на месте, Grafana dashboard `rct-containers` рендерится
- [ ] Verify: Prometheus alert `ContainerMemoryHigh` срабатывает на искусственный stress test
- [ ] `docker-compose.prod.yml` cadvisor: уменьшение surface — read-only filesystem где возможно
- [ ] Commit: `security(cadvisor): drop privileged in favor of CAP_SYS_PTRACE (M16 G9)`

## Финал

- [ ] Прогон `scripts/verify-deploy.sh` на VPS
- [ ] `CHANGELOG.md` — раздел `[Unreleased]` → версия (тег решается на финале)
- [ ] Обновить `CLAUDE.md` — статус M16 ✅
- [ ] PR через `gsd-pr-branch` (filter `.planning/`) или прямой push если работаем без feature-branch
- [ ] Tag (`v0.1.0-rc.1` или `v0.0.0-alpha.17` — решение в финальном NOTES)

---

_Если задача превращается в 6+ часов — разрезать._
