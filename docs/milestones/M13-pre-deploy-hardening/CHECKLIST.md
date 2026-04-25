# M13 Checklist

Атомарные задачи в порядке выполнения. Одна строка = одна единица работы
(~30 мин - 2 часа). Отмечаются `[x]` после коммита.

Порядок выбран так, чтобы early-groups давали dev-feedback loop для
последующих (fixed tests → можно полноценно гонять IT; rate-limit fix
→ Playwright E2E работает предсказуемо; etc.).

## Группа 1 — Pre-existing flaky tests (dev-feedback unblock)

- [x] Fix `EventSchemaRefTest` — обновить fixtures под envelope с `trace_id` после M04 G6 migration _(уже починен в M06 G9 `e0e1881`; локально 3/3 зелёные)_
- [x] Fix `api-gateway:RateLimitIT.sixthRequest_returns429` — assertion / timing (M03a G12 test bug) _(локально 2/2 зелёные в `integrationTest`; Retry-After assertion придётся править в G2 после смены rate-limit math)_
- [x] Fix `attendance:ExcuseEventContractIT.createExcuse` — test seeds, устранить duplicate excuse _(локально 2/2 зелёные в `integrationTest`)_

## Группа 2 — Rate-limit semantics (security, dev unblock)

- [x] Переделать `application.yml` rate-limit на 6 auth роутов: `replenishRate=1`, `burstCapacity=5`, `requestedTokens=12` → ровно 5 req/min _(финальная формула: `burstCapacity=60, requestedTokens=60/X`; owner-формула в NOTES была неточной — `burstCapacity=5 < requestedTokens=12` давало 429 на первом запросе. См. `api-rate-limits.md` «Формула для цели X req/min»)_
- [x] Обновить `RateLimitIT` — тест на 5 req → 6-й 429 с правильным timing _(после фикса config'а тест прошёл без изменения assertion'ов; добавлен comment-clarification про M13 G2)_
- [x] Обновить `docs/api-rate-limits.md` — семантика _(заменена таблица burst/replenish на X req/min + requestedTokens; добавлен блок «Формула для цели X req/min» и уточнение про Retry-After как hardcoded upper bound)_

## Группа 3 — Pagination global cap (security)

- [x] Добавить `spring.data.web.pageable.max-page-size=100` в `shared-web` default config _(`PageableDefaultsPostProcessor` + `META-INF/spring.factories`; `EnvironmentPostProcessor` ставит дефолт с lowest priority, per-service override возможен)_
- [x] IT на 3 endpoint'а (academic/schedule/attendance) — `?size=1000000` → truncated до 100 _(3 × `PaginationCapIT`, все зелёные; schedule проверяет cap=200 из local override, остальные — 100 из shared default)_
- [x] Документация в `docs/api-pagination.md` — max-page-size = 100 _(новый файл: semantics, per-service cap table, client rules, HATEOAS links, monitoring roadmap)_

## Группа 4 — `/auth/refresh-body` removal (sunset guard)

- [x] `grep -rn "refresh-body" frontends/` — аудит usage _(только generated types + 1 комментарий в mini-app; ни одного runtime-вызова)_
- [x] Если usage найден — мигрировать frontend на cookie-based `/auth/refresh` _(N/A — frontend уже на cookie flow; regenerate types после G4.3 удалил упоминания)_
- [x] Удалить `POST /auth/refresh-body` endpoint + DTO + test в auth-app _(удалён: AuthApi.refreshBody, AuthController.refreshBody + REFRESH_BODY_SUNSET, SecurityConfig permitAll, Gateway route Path, JwtAuthenticationFilter PUBLIC_PATHS, AuthIT+TmaIT тесты. RefreshRequest DTO оставлен — ещё нужен для /refresh и /logout (cookie flow wrapper))_
- [x] Обновить `docs/auth-flow.md` — только cookie flow _(удалён deprecated-блок; + убрано упоминание из docs/architecture.md; regenerate auth.json snapshot + frontend types (web-panel + pwa))_

## Группа 5 — `InvalidParam` deprecated alias migration

> **Коррекция checklist (M13 G5):** исходная формулировка путала
> направление миграции. Реальный замысел (из v0.0.0-debt.md:108 и
> M11 NOTES): удалить **legacy `InvalidParam` Java-record** — он был
> deprecated alias после M11 G0 canonical-rename на `FieldError`.
> Backend canonical остался `ErrorResponse.fieldErrors: List<FieldError>`
> (не менялся). Frontend TS-level имя `invalidParams` — это RFC 9457
> compliance на frontend-стороне, остаётся.

- [x] Удалить `fieldErrors` field из `ErrorResponse` в `shared-web-api` _(N/A — canonical, не удалять. Удалён сам `InvalidParam.java` record + `invalidParamAllFields` test в ErrorResponseTest. Backend `ErrorResponse.fieldErrors` не менялся)_
- [x] Удалить маппинг `fieldErrors` из `GlobalExceptionHandler` в `shared-web` _(N/A — canonical format. Handler уже выдаёт fieldErrors с M11 G0. Минорный cleanup: обновлены comments `FieldError.java`/`ErrorResponse.java`/`shared-web/build.gradle.kts` про M13 G5 alias removal + @DisplayName в `NotificationErrorHandlingIT` с "invalidParams[]" на "fieldErrors[]" для синхронизации с реальным форматом)_
- [x] Обновить frontend axios interceptors: убрать fallback на `fieldErrors` _(обратно: убрали fallback на **invalidParams** в `RawErrorBody.invalidParams?` и `coerceInvalidParams`. Теперь frontend читает только canonical backend `fieldErrors` и выдаёт TS `invalidParams`. Удалены `принимает post-M11 invalidParams shape` unit-tests в PWA и web-panel. 166/166 PWA + 476/476 web-panel зелёные)_
- [x] Regenerate OpenAPI snapshots — проверить, что `invalidParams` остаётся единственным _(5 snapshot'ов regenerate через `OpenApiSnapshotIT -Popenapi.snapshot.update=true` — нет diff'а, т.к. `InvalidParam` schema в spec'ах отсутствовала изначально (canonical уже был `fieldErrors`). Frontend types regenerate — нет diff'а)_

## Группа 6 — Notification TTL + compound indexes (M10 S4 audit)

- [x] Аудит `NotificationMongoConfig` — startup verify: TTL 30d + compound `(user_id, created_at)` реально создаются (а не silent no-op) _(аудит ok: `NotificationHistoryMongoConfig` с M10 G9 hot-patch использует `@EventListener(ApplicationReadyEvent)` + `createCollection()` перед `ensureIndex()`. Поле называется `sent_at` (не `created_at` — checklist terminology divergence, зафиксировано в NOTES))_
- [x] Добавить fail-fast на startup если индексы не созданы (log.error + fail context) _(добавлен `verifyIndexes()` в `NotificationHistoryMongoConfig`: читает `getIndexInfo()` и `listIndexes().expireAfterSeconds` — бросает `IllegalStateException` если один из 3 индексов отсутствует или TTL не совпадает. Константы `IDX_USER_SENT_DESC/IDX_USER_READ/IDX_TTL_SENT_AT` вынесены для reuse в IT)_
- [x] IT на чистой Mongo: `getIndexes()` содержит обе expected _(новый `NotificationMongoIndexesIT`: проверяет 3 custom + `_id_` + `expireAfterSeconds=2592000` + compound key `{user_id:1, sent_at:-1}`. 1/1 passing на Testcontainers Mongo)_
- [x] Runbook: `runbooks/mongo-indexes-verify.md` — как проверить вручную на VPS после deploy _(новый `docs/runbooks/mongo-indexes-verify.md`: автоматический startup check, ручная команда `db.getIndexes()`, fix рецепты (рестарт / manual createIndex / collMod TTL), performance IXSCAN check)_

## Группа 7 — Mongo outbox atomicity (M02 CRITICAL #1)

- [x] Перевести attendance MongoDB в single-node replica set (`--replSet rs0`) в docker-compose.prod.yml + dev.yml _(Option B — `bitnami/mongodb:7.0` с `MONGODB_REPLICA_SET_MODE=primary` + `_NAME=rs0` + `_KEY` вместо custom entrypoint; volume `/bitnami/mongodb`; mem_limit 512m в prod)_
- [x] Init script: `rs.initiate()` на первом старте (entrypoint / migration) _(Bitnami делает это automatically при `MODE=primary`; удалён legacy `infra/mongo/init-mongo.js` — users теперь через `MONGODB_EXTRA_USERNAMES/PASSWORDS/DATABASES`)_
- [x] Добавить `@Transactional` + `MongoTransactionManager` bean в attendance-app + notification-web _(attendance: `MongoConfig.mongoTransactionManager` bean + `@EnableTransactionManagement`; notification-web: same в `NotificationHistoryMongoConfig`)_
- [x] Обернуть `MongoOutboxStorage.save()` в transaction _(не на уровне самого storage — через `@Transactional` на service-методах: ExcuseService.createExcuse/createExcuseWithFile/updateStatus/applyDecisionFromBot, CheckinService.checkin, MarkingService.markAttendance/markBatch, LateCheckinService.createRequest/applyDecisionFromWeb/applyDecision. Storage-level не нужен — save вызывается внутри domain-tx propagation)_
- [x] IT: kill publisher между `save` и `publish` → либо оба committed, либо оба rolled back _(новый `OutboxAtomicityIT` с 2 сценариями: `saveAndFail` → rollback обоих, `saveAndCommit` → оба сохранены. 2/2 passing; full attendance-integrationTest + notification-integrationTest зелёные)_
- [x] Обновить `docs/database-schema.md` — Mongo replica set требование _(новый раздел «Deployment: replica set rs0 (M13 G7)» перед `attendances` коллекцией: требование RS для transactions, Bitnami setup, URI `?replicaSet=rs0`, transactions usage map, rollback plan. Mongo users раздел обновлён: «Создаётся в» → «Bitnami MONGODB_EXTRA_* env (M13 G7)»)_

## Группа 8 — Consumer-side dedup по `event_id` (M02 CRITICAL #2)

- [x] Создать `shared-events/EventIdempotent` helper (аннотация + aspect) _(не AOP — `IdempotencyStore` interface + `IdempotencyGuard` helper + `@EventIdempotent` marker; явный вызов `guard.tryClaim` в первой строке consumer-метода. См. NOTES «Дизайн»)_
- [x] Schema: `event_consumer_processed (consumer_id, event_id, processed_at)` unique index _(PG: composite PK `(consumer_id, event_id)` + `idx_ecp_cleanup` на `processed_at`. Mongo: compound unique `(consumer_id, event_id)` через ensureIndexes)_
- [x] Flyway V{N} migration для academic-app + schedule-app (PostgreSQL) _(academic V18, schedule V14; FlywayMigrationIT зелёный для обоих)_
- [x] Collection init для attendance-app + notification-web (MongoDB) _(attendance MongoConfig.initIndexes + NotificationHistoryMongoConfig.initIndexes — обе вызывают `new MongoIdempotencyStore(mongoTemplate).ensureIndexes()` после доменных индексов)_
- [x] Добавить `@EventIdempotent(consumer="academic")` на consumer methods в academic _(N/A — academic-app не имеет @RabbitListener; только publisher через DomainEventListener. Store/Flyway/cleanup всё равно созданы для симметрии и future consumer'ов в v0.1+)_
- [x] Добавить `@EventIdempotent(consumer="schedule")` на consumer methods в schedule _(`schedule.EventConsumer.onEvent` + `@Transactional` + `idempotencyGuard.tryClaim`)_
- [x] Добавить `@EventIdempotent(consumer="attendance")` на consumer methods в attendance _(`attendance.EventConsumer.onEvent` + `@Transactional` + guard. Lombok `@RequiredArgsConstructor` подхватывает новое final-поле)_
- [x] Добавить `@EventIdempotent(consumer="notification-web")` на consumer methods в notification-web _(2 consumer'а с разными CONSUMER_ID: `notification.EventConsumer` (`"notification-web"`) для STOMP/push routing + `NotificationHistoryConsumer` (`"notification-history"`) для persistence)_
- [x] Python equivalent в notification-bot: Redis SET `consumed:{event_id}` TTL 1h + early-return при hit _(`bot/services/idempotency_guard.py` BotIdempotencyGuard — SET NX EX 3600. Wired в `__main__.py` → `start_consumer(..., idempotency_guard=...)` → проверка перед `dispatcher.dispatch`)_
- [x] IT per service: publish event × 2 → consumer видит обработку × 1 _(3 EventIdempotentIT: schedule (Mockito verify cascade × 1 + claim count = 1), attendance (Rabbit publish × 2 → claim count = 1 в Mongo), notification-web (Rabbit publish → claims у обоих consumer-id = 1 + history.size = 1). Academic нет — нет consumer'а. Bot — 7 pytest'ов в `test_idempotency_guard.py`)_
- [x] **(дополнено)** ShedLock cleanup-job для retention 7 дней _(IdempotencyCleanupJob в shared-outbox, cron `0 30 3 * * *`, lock `idempotency-cleanup`. Зарегистрирован в Publisher/Scheduler-секции 4 сервисов под `@Profile("!test")`)_

## Группа 9 — SecurityIdorIT (NEW-31 retrospective)

- [x] **G9.0 (pre-req fix):** EventSchemaCoverageTest whitelist обновлён — добавлен `otp.requested` (M09 G2 baseline drift)
- [x] Создать `services/academic-app/src/test/java/.../SecurityIdorIT.java` — student A читает student B → 403 _(13 тестов: 11 IDOR + 2 sanity, покрытие homework/assistant/assignment/subject)_
- [x] Создать `services/schedule-app/src/test/java/.../SecurityIdorIT.java` — lesson/group access control _(11 тестов: 4 read + 7 write IDOR через gRPC isHeadman + new requireGroupReadAccess)_
- [x] Создать `services/attendance-app/src/test/java/.../SecurityIdorIT.java` — attendance/excuse/late-checkin per-user _(10 тестов; existing security уже корректен — IT документирует)_
- [x] Создать `services/notification-web/src/test/java/.../SecurityIdorIT.java` — notification history per-user _(4 теста; userId всегда из RequestContext, IT подтверждает)_
- [x] Исправить найденные IDOR-баги — **9 IDOR'ов в academic + 3 в schedule** (см. NOTES.md G9 — `assertCanReadGroup` в HomeworkService, `assertOwnGroup` в AssistantService/AssignmentService, `assertCanReadSubject` в SubjectService, `requireGroupReadAccess` в LessonService/ScheduleItemService/OneOffLessonService)
- [x] Каждый IT покрывает ≥ 3 endpoint'а с `{userId}/{groupId}/{lessonId}` в URL _(38 тестов на 22+ unique endpoints, сильно превышает AC-17 минимум 10)_

## Группа 10 — Actuator tracing exclude (M06 misleading comment fix)

- [x] Создать `shared-observability/ActuatorTracingExcludeSampler` — OpenTelemetry Sampler bean _(заменён на `ActuatorTracingExcludeFilter` — `SpanExportingPredicate`. Sampler не работает в Spring Boot 3.4 — `url.path` устанавливается на span после `Sampler#shouldSample`. См. NOTES «Группа 10 — Surprise». Auto-config в shared-observability через `@ConditionalOnClass(SpanExportingPredicate.class)`)_
- [x] IT: GET `/actuator/health` → span не попадает в in-memory exporter _(`ActuatorSpanFilterIT` в auth-app: filter bean регистрация + 0 spans для /actuator/health (root + children через trace_id LRU) + sanity для бизнес /auth/login. 3/3 passing)_
- [x] Поправить misleading comment в `application.yml:108` — теперь правда _(TODO(M07) → описание про ActuatorTracingExcludeFilter в shared-observability M13 G10. Только auth-service application.yml имел этот TODO; в 4 остальных нет)_
- [x] Обновить `docs/observability.md` — sampling policy _(новый раздел «Sampling policy (M13 G10)» с таблицей endpoints/decisions, выбором SpanExportingPredicate vs Sampler, child-span LRU strategy, property override для disable)_

## Группа 11 — `mem_limit` на aux containers

- [x] Добавить `mem_limit: 256m` на nginx в docker-compose.prod.yml _(reverse proxy + SSL termination + WebSocket fan-out, 5m residual + buffers под high keepalive)_
- [x] Добавить `mem_limit: 128m` на certbot _(Python ACME client, peak только во время renew)_
- [x] Добавить `mem_limit: 128m` на node-exporter _(резидент ~15-25m, запас под /proc reading peaks)_
- [x] Добавить `mem_limit: 256m` на cadvisor _(scrape всех containers, ~50-150m под нагрузкой)_
- [x] Добавить `mem_limit: 256m` на promtail _(tails docker logs всех containers, batch buffer + positions file)_
- [x] **(extended scope)** `mem_limit: 64m` на 4 frontend-nginx (pwa/mini-app/web-panel/landing) — статика, ~5-10m residual. Симметрично с reverse-proxy nginx, чтобы не оставлять unbounded контейнеры в prod.
- [x] `docker compose config` — валидация syntax _(0 errors с dummy SWAGGER_HTPASSWD; 26/26 контейнеров имеют mem_limit, total budget 6.1GB на 8GB VPS, 1.9GB host overhead)_

## Группа 12 — Healthcheck directives + health indicators

- [x] Добавить `healthcheck:` directive на 5 backend в docker-compose.prod.yml _(уже сделано M06 G2 + M09 G7 — все 5 backend имеют compose-level `healthcheck:` блок с wget /actuator/health, interval 15s, timeout 5s, retries 5, start_period 30-60s)_
- [x] Проверить что curl доступен в backend image (Dockerfile), если нет — использовать wget/--spider _(все Dockerfile уже используют `wget -qO- http://localhost:PORT/actuator/health` — alpine wget доступен. HEALTHCHECK дублирован на image level и compose level)_
- [x] Обновить `depends_on: service_healthy` на критичные зависимости (gateway → auth, consumers → rabbitmq) _(уже сделано M06 G2 + M09 G7 — все backend ждут DB/Redis/Rabbit `service_healthy`. api-gateway ждёт `service_healthy` для всех 5 downstream)_
- [x] Проверить Spring Boot actuator health indicators: `rabbit`, `db`, `mongo`, `redis` включены во всех 5 backend _(дефолт Spring Boot — все включены автоматически. В application.yml не выставлено `enabled: false`. Test profile `application-test.yml` отключает rabbit/redis indicators чтобы остальные IT не падали без containers — это корректно, prod profile не затронут)_
- [x] IT: kill rabbitmq → academic-service `/actuator/health` возвращает DOWN _(`HealthDegradationIT` в academic-app: own dedicated RabbitMQContainer без reuse + override `management.health.rabbit.enabled=true` чтобы не конфликтовать с application-test.yml. 2 теста: healthUp pre-stop + healthDown post-stop через `RABBITMQ.stop()` → 503 SERVICE_UNAVAILABLE с `components.rabbit.status: DOWN`. Awaitility 10s window для health indicator update. 2/2 passing)_

## Группа 13 — Environment secrets infrastructure

- [x] Создать `.env.prod.example` — все 20+ переменных с командой генерации и описанием _(обновлён существующий — был неполный: добавлены MONGODB_REPLICA_SET_KEY (M13 G7), INTERNAL_ISSUER_SECRET (M03a), ALERT_WEBHOOK_SECRET (M04 G9), NOTIFICATION_HISTORY_TTL_DAYS, NOTIFICATION_WS_ALLOWED_ORIGINS, ADMIN_TELEGRAM_IDS. Удалены лишние GHCR_TOKEN/GRAFANA_LOGIN не используемые в compose. 22 required + 5 optional vars, каждая с командой генерации и описанием)_
- [x] Добавить `.env.prod` в `.gitignore` (проверить, что уже есть) _(уже было — `.env.prod` + `.env` + `*.env.local` все в .gitignore)_
- [x] Создать `scripts/validate-env-prod.sh` — shell script, проверяет наличие + формат (JWT_SECRET base64 ≥32, TELEGRAM_BOT_TOKEN regex, etc.) _(чистый bash, dot-env parser через ассоциативный массив (не shell eval — пароли могут содержать `;~'()@`). Format checks: passwords ≥8 chars, GRPC_SECRET/INTERNAL_ISSUER ≥32 chars, MONGODB_REPLICA_SET_KEY ровно ~1024 chars, Telegram tokens regex, VAPID длины 87/43, mailto:/https:// для VAPID_SUBJECT, $$apr1$$/$$2y$$ для SWAGGER_HTPASSWD, hex 64 для ALERT_WEBHOOK_SECRET, https:// для URLs)_
- [x] Тест: broken `.env.prod` (missing JWT_SECRET) → script exit 1 с явным сообщением _(exit codes: 1 file missing, 2 required vars missing, 3 format errors. Validator реально нашёл 4 проблемы в актуальном `.env.prod` владельца — 3 missing secrets + 1 placeholder-not-replaced — пользователь регенерировал и validator показал ✓ all passed. Real-world value доказан)_
- [x] Обновить `docs/prod-deploy-checklist.md` — шаги генерации secrets _(новый раздел 1.0 «Environment secrets (M13 G13)» перед всеми pre-deploy checks: cp .env.prod.example, генерация secrets, chmod 600, validate-env-prod.sh, scp на VPS, повторная валидация на VPS, backup в password manager. Отдельный sub-block про rotation и про критичные новые secrets для upgrade с старого .env.prod)_

## Группа 14 — Swagger basic-auth + Prometheus/Alertmanager lockdown

- [ ] Добавить `SWAGGER_AUTH_USER` + `SWAGGER_AUTH_PASS` в `.env.prod.example`
- [ ] Runbook: генерация `.htpasswd` через `htpasswd -B -c` (bcrypt), не apr1
- [ ] Добавить `location /prometheus/` в `nginx/conf.d/default.conf` с basic-auth
- [ ] Добавить `location /alertmanager/` в `nginx/conf.d/default.conf` с basic-auth
- [ ] Добавить `--web.external-url=https://ruttrack.site/prometheus/` к prometheus command
- [ ] Добавить `--web.external-url=https://ruttrack.site/alertmanager/` к alertmanager command
- [ ] Создать `nginx/entrypoint.sh` — fail-fast если `.htpasswd` отсутствует или пустой
- [ ] Обновить `nginx` image / Dockerfile — использовать новый entrypoint

## Группа 15 — Backup infrastructure

- [ ] Создать `scripts/backup.sh`: pg_dump × 2 + mongodump × 2 + `.env.prod` GPG-encrypt
- [ ] Retention: `find /opt/backups -name "*.gz" -mtime +7 -delete` + `*.gpg -mtime +7 -delete`
- [ ] Создать `scripts/restore.sh $date` — параметризованный restore
- [ ] Tested restore: backup → локальная Postgres/Mongo (Docker) → row count matches
- [ ] Cron config: `/etc/cron.d/rutcampustrack-backup` (раз в сутки 3:00 UTC)
- [ ] GPG key generation: добавить в `docs/prod-deploy-checklist.md`
- [ ] Создать `runbooks/backup-restore.md` — полный runbook

## Группа 16 — CSP audit + report endpoint

- [ ] Локальный smoke: `docker compose up -d` → открыть 3 frontends → DevTools Console → 0 CSP violations
- [ ] Fix any violation found (expected: Material Design chunks, Grafana iframe)
- [ ] Создать `CspReportController` в notification-web с endpoint POST `/api/csp-report`
- [ ] Metric: `csp_violations_total{directive, blocked_uri}` counter
- [ ] Обновить nginx CSP header: `report-uri /api/csp-report; report-to default`
- [ ] IT: POST mock violation → metric incrementится + structured log
- [ ] Создать `docs/security-headers.md` — policy документирование

## Группа 17 — Grafana dashboards sanity + retention

- [ ] `docker compose up -d` → открыть Grafana → 3 dashboard'а (business-kpis, system-health, tracing) показывают non-zero данные
- [ ] Проверить `prometheus --storage.tsdb.retention.time=14d` в docker-compose
- [ ] Проверить `tempo.yml` retention 14d
- [ ] Проверить Loki retention (если включён)
- [ ] `.env.prod.example`: `GRAFANA_ADMIN_PASSWORD` — не дефолт `admin/admin`

## Группа 18 — WebSocket reliability

- [ ] Обновить `nginx/conf.d/default.conf` — location `/ws`: `proxy_read_timeout 300s`, `proxy_http_version 1.1`, `Upgrade` + `Connection` headers, `proxy_buffering off`
- [ ] Проверить STOMP heartbeat в `WebSocketConfig.java` notification-web — `setHeartbeatValue(new long[]{10000, 10000})`
- [ ] Локальный smoke: PWA notification-center → Chrome DevTools offline 30s → online → STOMP reconnect автоматический
- [ ] Документировать nginx/STOMP config в `docs/websocket-flow.md`

## Группа 19 — Alertmanager → Telegram E2E + alerts catalog

- [ ] Локальный smoke: полный stack → `docker stop rct-auth-service` → Telegram чат получает `[CRITICAL] ServiceDown: auth-service` в течение 90 сек
- [ ] Повторить для 2-3 других alert rules (`HighErrorRate`, `HighDatabaseConnectionPoolUsage`, `ContainerMemoryHigh`)
- [ ] Починить все найденные misconfig (webhook secret, routing, bot token)
- [ ] Создать `docs/alerts.md` — каталог всех 15+ alert'ов
- [ ] Для каждого alert: `## Symptom` / `## Meaning` / `## Runbook` sections
- [ ] Cross-ref: `infra/prometheus/rules/*.yml` labels совпадают с `docs/alerts.md`

## Группа 20 — Certbot renewal hook + cert expiry alert

- [ ] Добавить `blackbox-exporter` контейнер в docker-compose.prod.yml
- [ ] Scrape target в `prometheus.yml` — probe `https://ruttrack.site`
- [ ] Создать `infra/prometheus/rules/ssl-expiry.yml` — alert `SslCertExpiresSoon` когда `probe_ssl_earliest_cert_expiry - time() < 30*86400`
- [ ] Добавить alert в `docs/alerts.md`
- [ ] Certbot renewal hook: `--deploy-hook "touch /shared/reload-nginx"` + nginx sidecar inotifywait → `nginx -s reload` (или cron-based reload каждые 12h как simpler fallback)
- [ ] Runbook `runbooks/cert-renewal.md` — troubleshooting + first-deploy SSL steps
- [ ] Обновить `docs/prod-deploy-checklist.md` — DNS A-record + HTTP-only phase → cert → HTTPS phase

## Группа 21 — Flyway CONCURRENTLY guard

- [ ] Обновить `CLAUDE.md` — правило «CREATE INDEX CONCURRENTLY на prod-таблицах, миграция single-statement»
- [ ] Создать ArchUnit test `MigrationArchTest.java` в каждом service с Postgres
- [ ] Rule: все `CREATE INDEX` на `users|groups|lessons|attendances|...` (hot таблицы) должны содержать `CONCURRENTLY`
- [ ] Обновить `docs/prod-deploy-checklist.md` — секция «через 2 недели → EXPLAIN ANALYZE top-queries из pg_stat_statements»

## Группа 22 — Playwright E2E auth flow

- [ ] Создать `frontends/pwa-e2e/` или расширить существующий — structure `auth.spec.ts`
- [ ] Test 1: student login → role-based redirect на `/student/*` → cookie HttpOnly + SameSite=Strict
- [ ] Test 2: admin login → `/admin/*` access
- [ ] Test 3: access-token expiry (force TTL 15s in test) → refresh flow → new cookie
- [ ] Test 4: logout → cookies cleared → redirect `/login`
- [ ] Test 5: WS reconnect после offline (переиспользование для группы 18)
- [ ] CI job `e2e-auth` в GitHub Actions
- [ ] Документировать в `docs/testing-strategy.md`

## Группа 23 — VPS deploy runbook dry-run

- [ ] Fresh docker-compose env (или Ubuntu VM) → выполнить `docs/prod-deploy-checklist.md` шаг за шагом
- [ ] Фиксировать каждое отклонение в NOTES.md
- [ ] Обновить runbook реальными выводами команд
- [ ] Проверить все новые runbook'и (backup-restore, cert-renewal, mongo-indexes-verify) работают

## Группа 24 — Финальная верификация

- [ ] `./gradlew clean build` — зелёный full build
- [ ] `./gradlew integrationTest` — 0 failures
- [ ] `docker compose up -d` clean → все 14 containers healthy в 90 сек
- [ ] Все Playwright E2E зелёные
- [ ] Все 23 AC из PLAN.md `[x]`
- [ ] code-reviewer agent на `v0.0.0-alpha.13..HEAD` diff
- [ ] security-auditor agent на M13 изменения (особенно auth, CSP, rate-limit, IDOR)
- [ ] Tag `v0.0.0-alpha.14` (или сразу `v0.0.0`)
- [ ] Обновить `CLAUDE.md` статус: M13 ✅
- [ ] Обновить `docs/milestones/README.md` — M13 row
- [ ] Обновить `CHANGELOG.md` [Unreleased] → [0.0.0]

---

_Если задача превращается в 6+ часов работы — разрежь её. Если группа
превращается в 30+ задач — вынеси в отдельный milestone._
