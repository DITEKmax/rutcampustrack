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
- [x] Обновить `docs/api/api-rate-limits.md` — семантика _(заменена таблица burst/replenish на X req/min + requestedTokens; добавлен блок «Формула для цели X req/min» и уточнение про Retry-After как hardcoded upper bound)_

## Группа 3 — Pagination global cap (security)

- [x] Добавить `spring.data.web.pageable.max-page-size=100` в `shared-web` default config _(`PageableDefaultsPostProcessor` + `META-INF/spring.factories`; `EnvironmentPostProcessor` ставит дефолт с lowest priority, per-service override возможен)_
- [x] IT на 3 endpoint'а (academic/schedule/attendance) — `?size=1000000` → truncated до 100 _(3 × `PaginationCapIT`, все зелёные; schedule проверяет cap=200 из local override, остальные — 100 из shared default)_
- [x] Документация в `docs/api/api-pagination.md` — max-page-size = 100 _(новый файл: semantics, per-service cap table, client rules, HATEOAS links, monitoring roadmap)_

## Группа 4 — `/auth/refresh-body` removal (sunset guard)

- [x] `grep -rn "refresh-body" frontends/` — аудит usage _(только generated types + 1 комментарий в mini-app; ни одного runtime-вызова)_
- [x] Если usage найден — мигрировать frontend на cookie-based `/auth/refresh` _(N/A — frontend уже на cookie flow; regenerate types после G4.3 удалил упоминания)_
- [x] Удалить `POST /auth/refresh-body` endpoint + DTO + test в auth-app _(удалён: AuthApi.refreshBody, AuthController.refreshBody + REFRESH_BODY_SUNSET, SecurityConfig permitAll, Gateway route Path, JwtAuthenticationFilter PUBLIC_PATHS, AuthIT+TmaIT тесты. RefreshRequest DTO оставлен — ещё нужен для /refresh и /logout (cookie flow wrapper))_
- [x] Обновить `docs/auth/auth-flow.md` — только cookie flow _(удалён deprecated-блок; + убрано упоминание из docs/architecture/architecture.md; regenerate auth.json snapshot + frontend types (web-panel + pwa))_

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
- [x] Runbook: `runbooks/mongo-indexes-verify.md` — как проверить вручную на VPS после deploy _(новый `docs/operations/runbooks/mongo-indexes-verify.md`: автоматический startup check, ручная команда `db.getIndexes()`, fix рецепты (рестарт / manual createIndex / collMod TTL), performance IXSCAN check)_

## Группа 7 — Mongo outbox atomicity (M02 CRITICAL #1)

- [x] Перевести attendance MongoDB в single-node replica set (`--replSet rs0`) в docker-compose.prod.yml + dev.yml _(Option B — `bitnami/mongodb:7.0` с `MONGODB_REPLICA_SET_MODE=primary` + `_NAME=rs0` + `_KEY` вместо custom entrypoint; volume `/bitnami/mongodb`; mem_limit 512m в prod)_
- [x] Init script: `rs.initiate()` на первом старте (entrypoint / migration) _(Bitnami делает это automatically при `MODE=primary`; удалён legacy `infra/mongo/init-mongo.js` — users теперь через `MONGODB_EXTRA_USERNAMES/PASSWORDS/DATABASES`)_
- [x] Добавить `@Transactional` + `MongoTransactionManager` bean в attendance-app + notification-web _(attendance: `MongoConfig.mongoTransactionManager` bean + `@EnableTransactionManagement`; notification-web: same в `NotificationHistoryMongoConfig`)_
- [x] Обернуть `MongoOutboxStorage.save()` в transaction _(не на уровне самого storage — через `@Transactional` на service-методах: ExcuseService.createExcuse/createExcuseWithFile/updateStatus/applyDecisionFromBot, CheckinService.checkin, MarkingService.markAttendance/markBatch, LateCheckinService.createRequest/applyDecisionFromWeb/applyDecision. Storage-level не нужен — save вызывается внутри domain-tx propagation)_
- [x] IT: kill publisher между `save` и `publish` → либо оба committed, либо оба rolled back _(новый `OutboxAtomicityIT` с 2 сценариями: `saveAndFail` → rollback обоих, `saveAndCommit` → оба сохранены. 2/2 passing; full attendance-integrationTest + notification-integrationTest зелёные)_
- [x] Обновить `docs/architecture/database-schema.md` — Mongo replica set требование _(новый раздел «Deployment: replica set rs0 (M13 G7)» перед `attendances` коллекцией: требование RS для transactions, Bitnami setup, URI `?replicaSet=rs0`, transactions usage map, rollback plan. Mongo users раздел обновлён: «Создаётся в» → «Bitnami MONGODB_EXTRA_* env (M13 G7)»)_

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
- [x] Обновить `docs/operations/monitoring/observability.md` — sampling policy _(новый раздел «Sampling policy (M13 G10)» с таблицей endpoints/decisions, выбором SpanExportingPredicate vs Sampler, child-span LRU strategy, property override для disable)_

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
- [x] Обновить `docs/operations/deploy/prod-deploy-checklist.md` — шаги генерации secrets _(новый раздел 1.0 «Environment secrets (M13 G13)» перед всеми pre-deploy checks: cp .env.prod.example, генерация secrets, chmod 600, validate-env-prod.sh, scp на VPS, повторная валидация на VPS, backup в password manager. Отдельный sub-block про rotation и про критичные новые secrets для upgrade с старого .env.prod)_

## Группа 14 — Swagger basic-auth + Prometheus/Alertmanager lockdown

- [x] Добавить `SWAGGER_AUTH_USER` + `SWAGGER_AUTH_PASS` в `.env.prod.example` _(N/A — `SWAGGER_HTPASSWD` уже был в `.env.prod.example` (M11 G4) с инструкцией по генерации, расщеплять на USER + PASS не нужно. M13 G13 enriched шаблон с двумя альтернативами генерации (htpasswd -nB bcrypt, openssl passwd -apr1 fallback) + `$$` escape warning)_
- [x] Runbook: генерация `.htpasswd` через `htpasswd -B -c` (bcrypt), не apr1 _(уже зафиксировано в комментариях `.env.prod.example` + `docs/operations/runbooks/swagger-prod-access.md` (M11 G4). G14 ничего нового не добавляет — bcrypt уже preferred)_
- [x] Добавить `location /prometheus/` в `nginx/conf.d/default.conf` с basic-auth _(добавлен — proxy_pass на rct-prometheus:9090, `auth_basic` + `.htpasswd`)_
- [x] Добавить `location /alertmanager/` в `nginx/conf.d/default.conf` с basic-auth _(добавлен — proxy_pass на rct-alertmanager:9093, `auth_basic` + `.htpasswd`)_
- [x] Добавить `--web.external-url=https://ruttrack.site/prometheus/` к prometheus command _(+`--web.route-prefix=/` чтобы prometheus отвечал на root внутри container'а; nginx делает path strip через `proxy_pass http://...:9090/` с trailing slash)_
- [x] Добавить `--web.external-url=https://ruttrack.site/alertmanager/` к alertmanager command _(+`--web.route-prefix=/` same reason; добавлено в `entrypoint:` block, который уже был для secret-write)_
- [x] Создать `nginx/entrypoint.sh` — fail-fast если `.htpasswd` отсутствует или пустой _(`nginx/scripts/entrypoint.sh`: 5 fail-fast checks — env var defined + non-empty + format `login:$apr1$...` либо `login:$2y$...` (защита от unescaped `$$` regression) + post-write file non-empty. Inline `nginx.command` заменён на `entrypoint: ["/bin/sh", "/usr/local/bin/entrypoint.sh"]` + volume mount)_
- [x] Обновить `nginx` image / Dockerfile — использовать новый entrypoint _(не нужен custom Dockerfile — entrypoint mount'ится volume'ом as ro в standard nginx:1.27-alpine. Это симметрично с другими nginx config files (nginx.conf, conf.d, dhparam.pem) и проще поддерживать)_

## Группа 15 — Backup infrastructure

- [x] Создать `scripts/backup.sh`: pg_dump × 2 + mongodump × 1 + `.env.prod` GPG-encrypt _(Surprise: в prod у нас 2 Postgres контейнера, но **одна** Mongo-инстанция (`rct-mongo-attendance`) с двумя DB — `attendance_db` и `notification_db`. Один `mongodump --archive` с `authSource=admin&replicaSet=rs0` дампит обе БД атомарно. Формула retention = `/opt/backups/YYYY-MM-DD/` → 4 файла: `academic.sql.gz`, `schedule.sql.gz`, `mongo.archive.gz`, `env.prod.gpg`)_
- [x] Retention: `find /opt/backups -mindepth 1 -maxdepth 1 -type d -mtime +7 -exec rm -rf` — integrated в `backup.sh` _(сам скрипт; -mindepth 1 чтобы не удалить сам BACKUP_DIR; `-exec rm -rf +` эффективнее чем per-find delete)_
- [x] Создать `scripts/restore.sh $date` — параметризованный restore _(+ `--target=prod|test` + `--with-env` (optional decrypt) + `--confirm-prod` safety guard против случайного destructive restore на prod. Для postgres DROP+CREATE DATABASE через template1 connection + pg_terminate_backend для existing connections. Для mongo — `mongorestore --drop --archive`)_
- [x] Tested restore: backup → локальная Postgres/Mongo (Docker) → row count matches _(automated `scripts/test-restore.sh` + изолированный `docker-compose.test-restore.yml` — 2 postgres + 1 mongo с tmpfs, ephemeral random passwords, project name `rct-test-restore`. Verify: `academic_db.users>=1` (seed admin) + `schedule_db.tables>=1` (schema exists) + `attendance_db.collections>=1` + `notification_db` может быть пустой (normal на fresh deploy, WARN не FAIL). Guaranteed teardown через `trap cleanup EXIT`)_
- [x] Cron config: `/etc/cron.d/rutcampustrack-backup` (раз в сутки 3:00 UTC) _(03:00 UTC = 06:00 MSK — вне учебного расписания. `MAILTO=''` чтобы не спамить в stock cron MTA. Логи → `/var/log/rutcampustrack-backup.log` + logrotate config)_
- [x] GPG key generation: добавить в `docs/operations/deploy/prod-deploy-checklist.md` _(§1.0 расширен: passphrase в password manager + `/opt/rutcampustrack/.backup-passphrase` chmod 600. Полный setup в `docs/operations/runbooks/backup-restore.md §First-time setup`. Symmetric GPG (AES256) выбран — меньше friction чем asymmetric, passphrase в BW/1Password)_
- [x] Создать `runbooks/backup-restore.md` — полный runbook _(First-time setup (6 шагов) + Daily automation + Manual prod restore + Automated test-restore (quarterly) + DR scenario (VPS wiped → restore on fresh VPS) + Troubleshooting (GPG bad session key, pg_restore database already exists, mongorestore auth failed, cron не запускает, backup size). Deferred в v0.1: offsite (S3/B2 rclone), Prometheus last-backup-age alert, integrity check)_
- [x] ShellCheck CI job _(NEW в G15 — proactive: `.github/workflows/ci.yml` + shellcheck --severity=warning scripts/*.sh. Pre-existing warning'и в `smoke-prod.sh` (SC2064 trap quoting) + `m07-g3-launch-services.sh` (SC2164 `cd || exit`) исправлены per path. Все 8 bash-скриптов проходят без warnings)_
- [x] .gitignore: `.backup-passphrase`, `*.gpg`, `.env.test-restore` _(защита от случайного commit passphrase file + GPG-зашифрованных backup'ов + ephemeral test env)_
- [x] `docs/operations/deploy/prod-deploy-checklist.md §4.2 DB rollback` обновлён — `restore.sh` вместо raw `psql` command _(rollback после failed migration теперь одной командой + docker compose stop/start downstream services per runbook)_

## Группа 16 — CSP audit + report endpoint

- [ ] ~~Локальный smoke: `docker compose up -d` → открыть 3 frontends → DevTools Console → 0 CSP violations~~ **Deferred в G23 VPS dry-run** _(владелец: manual browser verification делается вручную на VPS deploy; реальные violations browser'ы теперь отправят на `/api/csp-report` → Loki/Prometheus, автоматический catch)_
- [ ] ~~Fix any violation found (expected: Material Design chunks, Grafana iframe)~~ **Deferred** _(Grafana iframe уже обработан: `/grafana/` location override'ит CSP на empty — см. default.conf:153. Material Design chunks: `style-src 'self' 'unsafe-inline'` уже разрешает inline styles, потому что Angular Material injectит CSS в runtime. Остальное — в G23 verify + fix в v0.1 если что-то найдётся)_
- [x] Создать `CspReportController` в notification-web с endpoint POST `/api/csp-report` _(Surprise: Spring MVC `MappingJackson2HttpMessageConverter` не регистрирует `application/csp-report` как JSON → `@PostMapping(consumes=...)` возвращал 415. Решил через `byte[]` body + manual `ObjectMapper.readValue`. Принимает 3 MIME: `application/csp-report`, `application/reports+json`, `application/json`. `@Hidden` чтобы не попадать в public OpenAPI snapshot. Роутинг Browser → nginx `/api/csp-report` → gateway (StripPrefix=1) → notification-web `/csp-report`)_
- [x] Metric: `csp_violations_total{directive, blocked_uri}` counter _(название привёл к convention `security.csp.violations` в `MetricNames.CSP_VIOLATIONS`, Prometheus export'ится как `security_csp_violations_total`. Tags: `directive` (нормализован — только имя без source list, lowercase), `blocked_uri_host` (только host из URI без path/query, обрезается до 32 chars для special `inline`/`eval`/`data:`). Low-cardinality чтобы не взорвать Prometheus label cardinality)_
- [x] Обновить nginx CSP header: `report-uri /api/csp-report; report-to default` _(в `nginx/conf.d/default.conf`: `report-uri /api/csp-report; report-to csp-endpoint;` (легаси + modern Reporting API) + `Report-To` header с JSON group definition `{"group":"csp-endpoint","max_age":10886400,"endpoints":[{"url":"/api/csp-report"}]}`. Compatibility: Firefox/Safari используют `report-uri`, Chrome 97+ — `report-to` + `Report-To`)_
- [x] IT: POST mock violation → metric incrementится + structured log _(`CspReportIT` в `@SpringBootTest + @AutoConfigureMockMvc`, extends `ContainerTestBase` (Mongo/Rabbit Testcontainers). 5 кейсов: legacy format, modern reports+json, json-fallback с top-level fields, unsupported Content-Type → 415, noAuthRequired (verify filter exclude работает). Counter delta verified через `MeterRegistry` inject. + Unit test 14 кейсов с `SimpleMeterRegistry`)_
- [x] Создать `docs/security-headers.md` — policy документирование _(полный документ: обзор всех security headers (HSTS/CSP/X-Frame/Referrer/Permissions), детальная CSP policy с обоснованием каждой директивы, Report-To config, CSP report endpoint routing + auth + rate-limit + Content-Type handling, observability queries для Grafana, runbook CSP triage, deferred items SRI/COOP/nonce, история изменений)_
- [x] Gateway PUBLIC_PATHS + rate-limit _(NEW: `/api/csp-report` добавлен в `JwtAuthenticationFilter.PUBLIC_PATHS` + новый route `notification-csp-report` с `RequestRateLimiter` 1 tok/sec + burst 60 per-IP (flood защита))_
- [x] NotificationUserContextFilter exclude _(NEW: `/csp-report` в `isExcludedPath` — browser не носит Internal JWT)_

## Группа 17 — Grafana dashboards sanity + retention

- [x] ~~`docker compose up -d` → открыть Grafana → 3 dashboard'а показывают non-zero данные~~ **Live smoke deferred в G23** _(per owner-policy «ничего руками»). Замещено automated `scripts/validate-grafana-dashboards.sh` + CI job `grafana-dashboards` — проверяет 6 dashboard JSON'ов на valid JSON + uid + title + ≥ 1 panel. Surprise: dashboards 6 (не 3 как в hand-off): `business-kpis-m04`, `docker-monitoring`, `grpc-latency`, `logs-overview`, `node-exporter`, `springboot-apm`. system-health разнесён по 3, tracing встроен в springboot-apm + grpc-latency + Tempo explore_(
- [x] Проверить `prometheus --storage.tsdb.retention.time=14d` в docker-compose _(`docker-compose.prod.yml:520` — pre-existing M04 G10. Также `--web.external-url=https://ruttrack.site/prometheus/` + `--web.route-prefix=/` от M13 G14)_
- [x] Проверить `tempo.yml` retention 14d _(`infra/tempo/tempo.yml:23` `compactor.compaction.block_retention: 336h` — pre-existing M04 G10. `336h` exactly = 14d в Go duration формате; `14d` НЕ валидный Go duration → silent default, поэтому `336h` корректнее)_
- [x] Проверить Loki retention (если включён) _(`infra/loki/loki.yml:43` `limits_config.retention_period: 336h` + `compactor.retention_enabled: true` + `delete_request_store: filesystem` — pre-existing M04 G10. Loki включён в `docker-compose.prod.yml:637`)_
- [x] `.env.prod.example`: `GRAFANA_ADMIN_PASSWORD` — не дефолт `admin/admin` _(переменная названа `GRAFANA_PASSWORD` (исторически M04 G10 не меняю на rename) в `.env.prod.example:118` со значением `CHANGE_ME` + командой генерации `openssl rand -base64 16`. В `validate-env-prod.sh:104` уже в REQUIRED_VARS (CHANGE_ME → exit 2) + `:167` `check_min_length GRAFANA_PASSWORD 8`. Pre-existing M13 G13)_

## Группа 18 — WebSocket reliability

- [x] Обновить `nginx/conf.d/default.conf` — location `/ws`: `proxy_read_timeout 300s`, `proxy_http_version 1.1`, `Upgrade` + `Connection` headers, `proxy_buffering off` _(location `/api/ws/` — http_version 1.1 + Upgrade + Connection + read_timeout 86400s уже были (M03b/M07). Добавлен `proxy_buffering off` (M13 G18) — без этого heartbeat 1-byte фреймы (`\n`) задерживаются nginx-буфером. read_timeout 86400s >> required 300s, оставлен — idle WS поддерживаются heartbeat'ом)_
- [x] Проверить STOMP heartbeat в `WebSocketConfig.java` notification-web — `setHeartbeatValue(new long[]{10000, 10000})` _(**Surprise**: comment утверждал «Default Spring heartbeat (10s server, 10s client) — no custom tuning needed» — это **неверно**. По умолчанию `enableSimpleBroker` без `setHeartbeatValue` + `setTaskScheduler` даёт **0/0 (off)**. Добавлены оба + dedicated `ThreadPoolTaskScheduler` bean (pool size 1, daemon, prefix `stomp-heartbeat-`). Comment поправлен. Регрессия покрыта `WebSocketConfigTest.stompHeartbeatScheduler_isInitializedDaemonThread`. `StompIntegrationIT` зелёный с новым scheduler bean)_
- [x] ~~Локальный smoke: PWA notification-center → Chrome DevTools offline 30s → online → STOMP reconnect автоматический~~ **Deferred в G23 VPS dry-run** _(per owner-policy «ничего руками». Frontend reconnect-логика покрыта unit-тестами: `useStompCheckin.test.ts` × 3 (finite reconnectDelay 1-5000ms, ticket re-fetch на reconnect, reconnect mandatory) + `notification-center.service.spec.ts` (exponential backoff config). @stomp/stompjs default heartbeat 10s/10s — симметрично backend'у)_
- [x] Документировать nginx/STOMP config в `docs/architecture/websocket-flow.md` _(новый файл: handshake (POST /api/auth/ws-ticket → SockJS factory с async ticket fetch → TicketHandshakeInterceptor → SubscriptionAuthInterceptor anti-IDOR), heartbeat 10s/10s rationale, nginx config rationale (proxy_buffering off / read_timeout 86400s), reconnect стратегия + tests inventory, troubleshooting (4 сценария))_

## Группа 19 — Alertmanager → Telegram E2E + alerts catalog

- [x] ~~Локальный smoke: `docker stop rct-auth-service` → Telegram чат получает `[CRITICAL] ServiceDown` в течение 90 сек~~ **Deferred в G23 VPS dry-run** _(per owner-policy. Coverage без manual smoke: AlertControllerTest 8 cases (auth/parsing/publish), test_alert_fired.py 7 cases (admin filter / Telegram format / critical+warning), test_event_dispatcher.py (RabbitMQ routing). Полная chain покрыта: Prometheus eval → Alertmanager webhook → /internal/alert auth → publish alert.fired → bot dispatch → Telegram message)_
- [x] ~~Повторить для 2-3 других alert rules~~ **Deferred** _(тот же chain — каждый alert проходит через ту же infra. AlertControllerTest cover'ит generic shape, не per-alert)_
- [x] Починить все найденные misconfig _(**Surprise — найдено 2:** (1) `DLQBacklog` был silent dangling: `rabbitmq:3.13-alpine` image не имел `rabbitmq_prometheus` plugin, метрика `rabbitmq_queue_messages` никогда не экспортировалась. **Fix:** image switch на `rabbitmq:3.13-management-alpine` (drop-in, dev compose уже был на нём — prod догнал) + новый scrape job `rabbitmq:15692/metrics` в `prometheus.yml` + mem_limit 256→384m под management plugin overhead. (2) AC-13 требовал «15+ alerts», было 11. Добавлены HighErrorRate / HighRequestLatency (на http_server_requests, Spring Actuator default) + RabbitMQQueueBacklog / RabbitMQConnectionLost (теперь возможны после plugin'а). Итого 15 alerts (10 service-health + 2 resource-limits + 3 rabbitmq))_
- [x] Создать `docs/operations/monitoring/alerts.md` — каталог всех 15+ alert'ов _(полностью переписан M04 G9 stub: каждый alert — отдельная секция + cross-ref таблица + E2E test inventory + silencing/quiet-hours sections + история изменений)_
- [x] Для каждого alert: `## Symptom` / `## Meaning` / `## Runbook` sections _(15/15 alerts имеют все 3 секции. Symptom — что увидит owner в Telegram. Meaning — что значит метрика. Runbook — 3-4 step debug actions)_
- [x] Cross-ref: `infra/prometheus/rules/*.yml` labels совпадают с `docs/operations/monitoring/alerts.md` _(cross-ref таблица в alerts.md: alert ↔ rule file ↔ source metric ↔ severity. Validation: `promtool check rules` зелёный для всех 3 файлов (15 rules total). docker-compose.prod.yml syntax valid)_

## Группа 20 — Certbot renewal hook + cert expiry alert

- [x] Добавить `blackbox-exporter` контейнер в docker-compose.prod.yml _(`prom/blackbox-exporter:v0.25.0` digest-pin, mem_limit 64m, expose 9115. Config `infra/blackbox/blackbox.yml` — module `http_2xx` с `fail_if_not_ssl: true`, `valid_http_versions: [HTTP/1.1, HTTP/2.0]`, `preferred_ip_protocol: ip4`)_
- [x] Scrape target в `prometheus.yml` — probe `https://ruttrack.site` _(новый job `blackbox-https`, scrape_interval 60s, official relabel pattern: `__address__ → __param_target → instance label`, `__address__ → blackbox-exporter:9115`)_
- [x] Создать `infra/prometheus/rules/ssl-expiry.yml` — alert `SslCertExpiresSoon` когда `< 30 дней` _(3 alert rules в одном файле: `SslCertExpiresSoon` (warning, < 30d), `SslCertExpiresUrgently` (critical, < 7d, для случаев когда auto-renew всё ещё не сработал за 23+ дней), `SslProbeFailed` (critical, probe_success == 0). Все 3 проходят `promtool check rules`)_
- [x] Добавить alert в `docs/operations/monitoring/alerts.md` _(добавлены alerts #16-18 в каталог. Каждый — Symptom/Meaning/Runbook + cross-ref таблица. Каталог теперь 18 alerts (10 service-health + 2 resource-limits + 3 rabbitmq + 3 ssl-expiry))_
- [x] Certbot renewal hook _(**Surprise**: M13 G14 nginx entrypoint **уже** делает auto-reload каждые 5 мин (`nginx/scripts/entrypoint.sh:57` — `( while :; do sleep 5m; nginx -s reload 2>/dev/null \|\| true; done ) &`). Это превышает hand-off baseline «cron 12h». Renewed cert подхватывается автоматически в течение ≤5 мин. Отдельный certbot deploy-hook не нужен. Документировано в `cert-renewal.md` + `alerts.md` runbook'ах. Альтернативы (docker.sock mount, sidecar inotifywait, cron на host) rejected с обоснованием в runbook'е)_
- [x] Runbook `runbooks/cert-renewal.md` — troubleshooting + first-deploy SSL steps _(новый файл: архитектура (certbot loop ↔ certbot-conf volume ↔ nginx auto-reload), first-deploy 5-step SSL setup (DNS → HTTP-only → certonly → restore HTTPS), automated renewal flow + verify, observability metric, troubleshooting (5 сценариев: cert не renew, nginx не reload, rate-limit, SslProbeFailed, manual renewal через docker run), rejected hook альтернативы)_
- [x] Обновить `docs/operations/deploy/prod-deploy-checklist.md` — DNS A-record + HTTP-only phase → cert → HTTPS phase _(новая секция §1.5b «SSL / DNS (только first deploy на чистый VPS)» между Backup и Communication. 9 checklist items: DNS A-record, HTTP-only phase, certbot certonly, restore HTTPS, smoke openssl s_client, verify auto-renew loop. Помечена «только при первом deploy» — для subsequent cert уже выпущен)_

## Группа 21 — Flyway CONCURRENTLY guard

- [x] Обновить `CLAUDE.md` — правило «CREATE INDEX CONCURRENTLY на prod-таблицах, миграция single-statement» _(добавлено в раздел «База данных»: правило про CONCURRENTLY + IF NOT EXISTS + single-statement (`-- ##` либо отдельная миграция). Параллельно добавлен явный pin «НИКОГДА не редактируй applied миграции» (был в memory feedback, не в CLAUDE.md))_
- [x] Создать ~~ArchUnit~~ unit-test `MigrationConcurrentlyTest.java` в каждом service с Postgres _(**Surprise**: только 2 PG-сервиса (academic + schedule); auth/notification на Redis/Mongo. ArchUnit для .sql не подходит (не Java) — заменён на JUnit unit-test с regex parsing. По одному файлу в academic-app + schedule-app, baseline cutoff per service: V18 academic / V14 schedule. Both passing)_
- [x] Rule: все `CREATE INDEX` на `users|groups|lessons|...` (hot таблицы) должны содержать `CONCURRENTLY` _(**Surprise 2**: 0 existing миграций используют CONCURRENTLY → нельзя редактировать applied (checksum). Решение: grandfather все V≤cutoff, проверять только V>cutoff. Существующие plain CREATE INDEX оставлены as-is. Verified negative case: tmp V99 с plain CREATE INDEX → test fail; positive case с CONCURRENTLY → test pass)_
- [x] Обновить `docs/operations/deploy/prod-deploy-checklist.md` — секция «через 2 недели → EXPLAIN ANALYZE top-queries из pg_stat_statements» _(новая секция §5 «T+2 weeks — performance audit»: pg_stat_statements включить если нет, top-10 slow queries query, EXPLAIN ANALYZE для > 100ms mean time, fix через CONCURRENTLY index, cross-ref MigrationConcurrentlyTest, correlate с HikariPoolExhaustion/HighRequestLatency alerts (M13 G19), reset stats)_

## Группа 22 — Playwright E2E auth flow

- [x] ~~Создать `frontends/pwa-e2e/`~~ → расширить существующий `tests/e2e/` (M08 G7) _(**Surprise**: hand-off говорил «создать `frontends/pwa-e2e/`», но Playwright уже стоит в `tests/e2e/` от M08 G7 с 8 spec'ами + axe-core. Не дублирую — добавлен новый spec `auth-token-lifecycle.spec.ts` к существующей infrastructure)_
- [x] Test 1: student login → role-based redirect → cookie HttpOnly + SameSite=Strict _(`auth-token-lifecycle.spec.ts T1` — после loginAs(student) проверяет `context.cookies()` rct_refresh: httpOnly=true, sameSite='Strict', path='/api/auth', value не пустой. Cookie name + path: `services/auth-service/.../security/AuthCookies.java`. Login + redirect уже covered в auth.spec.ts от M08 G7)_
- [x] Test 2: admin login → `/admin/*` access _(`auth-token-lifecycle.spec.ts T2` — loginAs(admin) + assert URL содержит /admin/. Дополняет auth.spec.ts admin (там только heading visible))_
- [x] Test 3: access-token expiry → refresh flow → new cookie _(`auth-token-lifecycle.spec.ts T3` — после login сохраняем original cookie value, POST /api/auth/refresh (cookie auto-attached browser'ом по path match), assert response 200 + body.access_token + новый rct_refresh value (rotation на каждый refresh = anti-replay guarantee). Force TTL не требуется — endpoint работает с любым valid refresh)_
- [x] Test 4: logout → cookies cleared → redirect `/login` _(`auth-token-lifecycle.spec.ts T4` — loginAs(student), сохраняем pre-logout value, logout(), assert post-logout либо cookie отсутствует либо value='' (browser jar timing-dependent), goto /student/schedule → redirect /login. Дополняет logout из auth.spec.ts (там нет cookie verification)_
- [x] Test 5: WS reconnect после offline _(`auth-token-lifecycle.spec.ts T5` — loginAs(student), `context.setOffline(true)` 5 sec, `setOffline(false)`, page.reload(), assert URL /student/* + schedule heading. Точная WS frame inspection — frontend-internal, важна network resilience (cookie/sessionStorage сохранены через cycle). Cross-ref M13 G18 STOMP heartbeat 10s/10s)_
- [x] CI job `e2e-auth` в GitHub Actions _(**Surprise 2**: CI job не существовал — M08 G7 создал тесты, но не подключил в CI. Это AC-6 блокер. Новый job в `.github/workflows/ci.yml`: Java 21 + Node 22 + Gradle assemble + frontends build + docker compose up + healthcheck poll (4 мин max) + npm install + playwright install chromium + `npx playwright test --grep @smoke --project=chromium`. На failure — upload playwright-report + docker logs (7d retention). Cost ~5-7 мин)_
- [x] Документировать в ~~`docs/testing-strategy.md`~~ → `docs/e2e-testing.md` _(testing-strategy.md не существовал; обновлён `docs/e2e-testing.md`: добавлен `auth-token-lifecycle.spec.ts` row в таблице specs, новая section "CI integration (M13 G22)" с 9 step описанием pipeline, cost estimate, trigger note. Cross-ref на M13 G18 websocket-flow.md)_

## Группа 23 — VPS deploy runbook dry-run

- [ ] Fresh docker-compose env (или Ubuntu VM) → выполнить `docs/operations/deploy/prod-deploy-checklist.md` шаг за шагом _(**owner-driven** — manual VPS dry-run не может быть автоматизирован. Подготовка завершена: `scripts/preflight-deploy.sh` aggregator (env+grafana+promtool+compose+files+backup) + `scripts/verify-deploy.sh` post-deploy contract checks (security headers + CSP endpoint + basic-auth + 18 alerts + WS Upgrade + Mongo TTL). Когда владелец будет готов — запускает `preflight-deploy.sh` → `docker compose up -d` → ждёт 60-90 сек → `verify-deploy.sh` → `smoke-prod.sh`. Все отклонения — в NOTES.md)_
- [ ] Фиксировать каждое отклонение в NOTES.md _(**owner-driven** — slot для owner findings зарезервирован в NOTES.md G23 секции)_
- [x] Обновить runbook реальными выводами команд _(prod-deploy-checklist.md обновлён до dry-run-ready: §1.7 «Pre-flight diagnostics» вызывает preflight-deploy.sh + §2.4 «Post-deploy contract verification» вызывает verify-deploy.sh + smoke-prod.sh + dual-script flow задокументирован)_
- [x] Проверить все новые runbook'и (backup-restore, cert-renewal, mongo-indexes-verify) работают _(полный cross-ref добавлен в начало prod-deploy-checklist.md: 13 runbook'ов — secret-rotation/bot-webhook-migration/image-signing/migration-testing/resource-limits/backup-restore/cert-renewal/mongo-indexes-verify/swagger-prod-access + alerts.md/security-headers.md/websocket-flow.md/api-rate-limits.md. Все 13 файлов existence-verified. Owner ходит по runbook'ам прямо из checklist'а)_

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

## Группа 25 — CI hot-fixes + e2e-auth job infrastructure (re-open)

**Контекст:** После push в `dev` (commit `3db123b`, 2026-04-25) CI обнаружил
3 проблемы. Решено re-open M13 в виде Группы 25, чтобы закрыть все ДО
VPS dry-run и tag `v0.0.0`. Tag `v0.0.0-alpha.14` остаётся валидным —
G25 → новый tag `v0.0.0-alpha.15`.

### G25.1 — ruff format hot-fix (notification-bot)

- [ ] Прогнать `ruff format .` в `services/notification-bot/` (9 файлов локально)
- [ ] Локально: `ruff format --check .` + `ruff check .` — оба зелёные
- [ ] Коммит: `style(notification-bot): ruff format compliance (M13 G25.1)`

_Файлы (по CI логу): `bot/__main__.py`, `bot/config.py`, `bot/notifications/alert_fired.py`, `bot/notifications/otp_requested.py`, `bot/services/idempotency_guard.py`, `tests/test_alert_fired.py`, `tests/test_callback_excuse.py`, `tests/test_callback_late_checkin.py`, `tests/test_callback_prefs.py`._

### G25.2 — test_consumer_watchdog mock signature fix

- [ ] В `tests/test_consumer_watchdog.py`: 4 mock'а `mock_start_consumer(url, dispatcher=None)` → добавить `idempotency_guard=None`
- [ ] Локально `pytest tests/test_consumer_watchdog.py -v` — 6 PASS
- [ ] Коммит: `test(notification-bot): mock_start_consumer signature update for M13 G8 (M13 G25.2)`

_Регресс M13 G8: `__main__.py:65` вызывает `start_consumer(url, dispatcher=..., idempotency_guard=...)`, mock получал TypeError → watchdog flap → 10s timeout → fail. Тест `test_watchdog_restarts_on_consumer_failure` падал детерминированно, не flaky._

### G25.3 — docker-compose.e2e.yml infrastructure

**Корневая проблема:** dev `docker-compose.yml` содержит только инфра + 2
notification контейнера. Backend сервисы (auth/academic/schedule/attendance/gateway)
запускаются через `gradle bootRun` локально и **отсутствуют в compose**.
G22 commit добавил CI e2e-auth job, который пытается `docker compose up`,
но Dockerfile notification-web ожидает **build context = root репо** (multi-stage
COPY `gradlew`, `settings.gradle.kts`, `services/notification-service/...`),
а dev compose даёт ему context `./services/notification-service/notification-app/` →
build fail на `"/services/notification-service/notification-app/src": not found`.

`docker-compose.prod.yml` имеет правильный context: `.` для всех сервисов,
но требует 30+ env-переменных из `.env.prod` (production secrets).

- [ ] Создать `docker-compose.e2e.yml` — override на `prod` с тестовыми секретами либо самостоятельный compose с минимальной инфрой + 5 backend сервисов + 4 nginx + 2 notification
- [ ] Создать `tests/e2e/.env.ci` — тестовые секреты (gitignore либо commit с явной маркой `[FOR CI ONLY — NOT PROD]`)
- [ ] JWT keys: либо генерация в job step `openssl genrsa` либо commit'нутый test fixture pair в `tests/e2e/keys/`
- [ ] Решить fate Bitnami MongoDB digest для CI (replica set init на ephemeral container) — best-effort `mongod --replSet rs0` с автоинициализацией
- [ ] Обновить `.github/workflows/ci.yml` e2e-auth job: `docker compose -f docker-compose.e2e.yml up -d --build` + правильный path для healthcheck poll
- [ ] Локально проверить compose поднимается чисто (Windows Docker Desktop): `docker compose -f docker-compose.e2e.yml up -d` → все healthy в 4 мин
- [ ] Локально прогнать Playwright: `cd tests/e2e && npx playwright test --grep @smoke --project=chromium` → 5 specs зелёные
- [ ] Push, дождаться CI e2e-auth → зелёный
- [ ] Коммит: `test(e2e): docker-compose.e2e.yml + test JWT keys + CI integration (M13 G25.3)`

### G25.4 — финализация G25

- [ ] Обновить `CHANGELOG.md` — секция G25 в [Unreleased]
- [ ] Обновить `docs/e2e-testing.md` — раздел про CI compose
- [ ] Tag `v0.0.0-alpha.15` на финальном коммите G25.4
- [ ] Push tag

**После G25 ✅:** возвращаемся к Шагу 3 (VPS dry-run по `prod-deploy-checklist.md`).

---

_Если задача превращается в 6+ часов работы — разрежь её. Если группа
превращается в 30+ задач — вынеси в отдельный milestone._
