# M16 Notes

Живой файл. Пиши сюда отклонения, измерения, surprises, вопросы.

---

## 2026-04-27

- Milestone стартовал по результату вопросов про `future-ideas.md`. Owner
  выбрал scope: 5 🔴 + 4 🟡, magic-link и 🟢 отложены.
- Headman rate-limit лимит **300 req/min** (не 120 как в `future-ideas.md`)
  — owner мотивировал: «староста иногда быстро прям проставляет отметки
  всем, нехорошо получится». G7 учитывает это.
- M15 — retrospective папка создана пост-фактум. См. `M15-first-vps-deploy/`.

### G1 — surprise: Java HTTP exporter ≠ Python gRPC exporter

При фиксе чуть не сломал notification-bot. Edit на docker-compose
прошёлся `replace_all` и переключил env var у бота тоже на 4318/v1/traces.

**Insight:** notification-bot использует
`opentelemetry.exporter.otlp.proto.grpc.trace_exporter.OTLPSpanExporter`
(см. `services/notification-bot/bot/observability.py:33`) — это **gRPC
exporter**, для него правильный port — **4317** без пути. Java-сервисы
(Spring Boot Micrometer) шлют HTTP/protobuf — для них **4318/v1/traces**.

**Откат:** notification-bot env вернулся на `http://tempo:4317`,
комментарий явно отмечает почему. Tempo слушает оба протокола (см.
`infra/tempo/tempo.yml`), так что mixed-mode работает корректно.

**Lesson learned:** при OTel-миграции в monorepo с разными языками
проверять каждый exporter по импорту (`.proto.grpc.` vs `.proto.http.`),
не только конфиг.

### G1 verify статус

Локально не проверял — нет dev VPS, локальный compose тяжёлый. Verify
шаги (логи без `Connection reset`, trace в Tempo) — на VPS после
redeploy всех Java-сервисов через CI.

### G1 итог

8 файлов изменено: 6 application.yml + docker-compose.yml +
docker-compose.prod.yml + scripts/m07-g3-launch-services.sh
(косметика). Готово к commit.

### G2 — surprise: idempotency уже сделана в M13 G8

`future-ideas.md` § «OTP hardening bundle / HIGH.2» предписывал
добавить idempotency через Redis SET NX в EventDispatcher. При
проверке кода обнаружено что `BotIdempotencyGuard` уже полностью
реализован в M13 G8 (commit `494821fe`):

- `services/notification-bot/bot/services/idempotency_guard.py` — `try_claim()`
  через `redis.set(key, "1", nx=True, ex=3600)`
- 7 unit-тестов в `tests/test_idempotency_guard.py`
- wired в `event_consumer.py:97-99` перед `dispatcher.dispatch(body)`

**`future-ideas.md` HIGH.2 устарел.** Записал в DECISIONS.md § D5.

### G2 — реальная остаточная проблема: silent loss в dispatcher

При чтении кода нашёл inconsistency:
- `event_consumer.py:101-105` комментировал: «handler exceptions
  пробрасываются → DLQ» (G24-fix-2)
- Но `event_dispatcher.py:dispatch()` имел `try/except Exception:` —
  swallow на уровне ниже

Эффект: handler bug → swallow → ack message → silent loss event'а,
DLQ-flow не активировался.

### G2 — обсуждение скоупа с owner

Owner напомнил про retention 14d (Loki) → **должны не плодить
бесконечные DLQ**. Обнаружено: `notification-bot.events.dlq`
declare'ился без `x-message-ttl` / `x-max-length`.

Выбран **B+ скоуп**:
1. Убрать swallow → propagate exception до consumer → DLQ
2. Добавить DLQ retention `7d + max-length 10000 + drop-head`
3. Перевернуть тесты (was: ack safety, now: pytest.raises)
4. Создать runbook `docs/operations/runbooks/dlq-triage.md`
5. Обновить вводящий в заблуждение комментарий в consumer'е
6. Verify alert `DLQBacklog` уже существует, добавлять не надо

### G2 итог

Изменено 4 файла + 1 новый:
- `services/notification-bot/bot/consumers/event_dispatcher.py` — убран swallow
- `services/notification-bot/bot/consumers/event_consumer.py` — DLQ retention args + комментарий
- `services/notification-bot/tests/test_event_dispatcher.py` — `pytest.raises` вместо «ack safety»
- `docs/operations/runbooks/dlq-triage.md` — **новый** runbook
- M16 PLAN/CHECKLIST/DECISIONS обновлены

Не тронуто (отложено):
- Java side (`notification-web.history.dlq` без TTL) — это отдельный
  пункт N6 в `future-ideas.md` § «Notification history bundle»

### G3 — observation: outcome metric mismatch

При чтении `verifyOtpByCode` обнаружил что mismatch path использовал
counter `otp_verify_total{outcome="expired"}`. Это семантически
неверно — на этом code-path Redis либо уже удалил key (после true
expiry), либо его никогда не было (атакующий перебирает random
codes). Оба случая правильнее называть **mismatch**.

Сменил outcome `expired` → `mismatch` в verify-by-code mismatch
branch. Side-effect: метрика `otp_verify_total{outcome="expired"}` в
проде станет тише (в основном legit `verifyOtp` по telegramId), а
`outcome="mismatch"` вырастет на полный объём verify-by-code probes.

Это **change в naming**, и `OtpBruteForceSuspect` alert использует
именно `mismatch` — alert завязан на новый, корректный outcome.

### G3 — design choice: pre-check + no reset-on-success

См. DECISIONS § D6.

Решающий аргумент: атакующий, случайно угадавший valid code (10 live
OTP / 10^6 кодов = ~1.7×10^-4 за 21 attempt), при reset-on-success
получал бы fresh counter и мог бы продолжать перебор. Без reset —
counter не резетится никогда, ban на 5 минут с IP.

### G3 — verify-by-code путь vs Gateway RateLimiter

Gateway RL уже стоит **перед** auth-service — 5/min/IP на `/auth/otp/*`.
Это первый layer. Counter в auth-service — второй layer, защищает от:
1. Distributed attack'ов (botnet раздаёт IP, Gateway RL обходится)
2. Gateway RL fail-open при Redis outage

Слои не конфликтуют — Gateway 5/min отрубит ~25 attempts за 5 мин,
что под cap'ом 20.

### G3 итог

Изменено 7 файлов:
- `services/auth-service/auth-app/src/main/java/.../OtpProperties.java` — 2 новых поля
- `services/auth-service/auth-app/src/main/java/.../OtpService.java` — pre-check + counter logic
- `services/auth-service/auth-api-contract/.../AuthApi.java` — added HttpServletRequest param + 429 doc
- `services/auth-service/auth-app/src/main/java/.../AuthController.java` — pass IP
- `services/auth-service/auth-app/src/main/resources/application.yml` — defaults
- `services/auth-service/auth-app/src/test/java/.../OtpServiceTest.java` — 5 новых тестов
- `infra/prometheus/rules/service-health.yml` — `OtpBruteForceSuspect` rule

Тесты: 9/9 passed (4 existing + 5 new). OpenAPI snapshot не требует
обновления — `SharedOpenApiCustomizer` уже добавляет 429 для всех
POST endpoint'ов.

### G4 — research finding: hypothesis 1 wrong

`future-ideas.md` § Loki M16 Cleanup предполагал что `InstancesCount
<= 0` errors могут быть от **trace data** misrouted к Loki через
mis-configured Tempo exporter. Это **опровергнуто** проверкой
codebase:

- Java OTel exporter (`management.otlp.tracing.endpoint`) шлёт прямо
  в Tempo (`tempo:4318`)
- Python OTel exporter (notification-bot) — gRPC прямо в Tempo
  (`tempo:4317`)
- Promtail — единственный поставщик в Loki, и он шлёт docker stdout,
  не traces

Текст ошибки `pusher failed to consume **trace data**` в Loki — это
**Loki internal terminology** для ring trace context (как
distributed-trace span context), а не для Tempo span data. Misleading
log message в Loki 3.x.

### G4 — root cause: startup race (Mimir #4662 pattern)

Гипотеза 2 confirmed через research:
- Loki docs Configure: `ingester.lifecycler.min_ready_duration` (default 15s)
- Mimir #4662 discussion: same error pattern — gRPC connectivity
  issue между distributor и ingester instance

В нашем monolithic single-instance setup distributor + ingester в
одном процессе, но они общаются через gRPC `127.0.0.1:9096`. На
старте: ring registered self → distributor пытается push → gRPC
listener ещё не запущен → connect fails → instance helper'ом
помечается unhealthy → `InstancesCount = 0`.

### G4 — fix scope: server-side + client-side

Изменено 3 файла + 1 новый:
- `infra/loki/loki.yml` — `ingester.lifecycler.min_ready_duration: 15s`
  (эксплицитный default)
- `docker-compose.prod.yml` (loki) — healthcheck на `/ready` с 30s
  start_period
- `docker-compose.prod.yml` (promtail) — `depends_on: loki: condition:
  service_healthy`
- `docs/operations/runbooks/loki-troubleshooting.md` — **новый** runbook
  на 4 типа ошибок (InstancesCount, context-canceled, entry-too-far-behind,
  OOM) с диагностикой и escalation criteria

### G4 verify

- Локально не проверял (single-instance monolithic Loki deploy
  тяжеловат для local).
- На VPS после redeploy + 24h: `docker logs rct-loki --since 24h |
  grep -c "InstancesCount"` должно упасть с **1-2/час** до близкого
  к 0.
- Если ошибка остаётся → next steps в runbook (gRPC keepalive,
  verify single-instance scale).

### G4 итог

Уверен что fix работает в **80%**. Единственный риск — promtail
healthcheck-wait делает startup сцеление длиннее (15-30s вместо
сразу), что увеличивает окно потери логов в случае cold deploy. Но
positions file персистится в volume (`/tmp/positions.yaml`), так что
docker stdout cursor не сбросится — promtail подберёт где остановился.

### G5 — surprise: scope расширился до verify-deploy.sh

Изначально G5 был только nginx DNS race (~0.5д). При чтении
`future-ideas.md` обнаружил что 4 false-positive'а в
`verify-deploy.sh` тоже были связаны:
- 1/4 случаев (`/login` 502) — это **симптом** того же DNS race, не
  отдельный bug. Логично fix'ить вместе.

Расширил скоуп до **G5 = nginx DNS race + verify-deploy false-positive
fixes**. Время выросло до ~1ч (вместо 0.5д), потому что я переиспользовал
existing infra (`expect_status` + новый `expect_status_retry` helper,
docker exec для health probe).

### G5 — почему variables в proxy_pass обязательны

Долго думал — может ли просто `resolver` директивы быть достаточно?
**Нет.** Без переменной nginx делает DNS resolve **только при загрузке
config'а** (не runtime), и `resolver` директива игнорируется. Это
известный gotcha из nginx docs:
> "If the proxy_pass directive contains a variable, the resolver
> directive is consulted on each request"

Без переменной `resolver` буквально not-on-effect для proxy_pass.
Это объясняет почему наш текущий config (без переменных) не помогает
даже если бы был resolver.

### G5 — alternative rejected: shared upstream{} blocks

Рассматривал заменить 8 переменных на 8 `upstream { }` блоков:
```nginx
upstream api_gateway { server rct-api-gateway:8080; }
location /api/ { proxy_pass http://api_gateway; }
```

**Не подошло**: `upstream{}` блок с `server <hostname>` тоже DNS-resolves
**только при load config**, а не runtime. Чтобы получить runtime resolve
в `upstream{}`, нужен **commercial nginx Plus** (`server <host> resolve;`)
или сторонний модуль (`nginx-upstream-dynamic-servers`).

Variables в `proxy_pass` — это **the only OSS way** для runtime DNS
без extra modules.

### G5 итог

Изменено 4 файла:
- `nginx/conf.d/default.conf` — `resolver` + 8 переменных + 15 `proxy_pass` через переменные
- `scripts/verify-deploy.sh` — comma-list status check + retry helper + section 2 fixes (root 301, gateway via docker exec, retry для /login /app /presentation) + section 8 Mongo TTL через root creds
- `docs/milestones/M16/CHECKLIST.md` — checkbox progress
- `docs/milestones/M16/DECISIONS.md` — D8 (DNS race) + D9 (verify-deploy)

Validate:
- `nginx -t` через docker run nginx:alpine → syntax OK
- `bash -n scripts/verify-deploy.sh` → syntax OK

Verify на VPS отложен — реальный test возможен только после redeploy
+ ручной `docker compose restart grafana` чтобы проверить что nginx
re-resolve'ит DNS вместо отдачи 502.

### G6 — scope reduction (initial 1-2д → ~1д actual)

Initial план (DECISIONS § D3) обещал before/after diff + разметку 15+
ADMIN endpoints. По исследованию сложности diff (deep cloning JPA
proxies + JSON diff library + PII masking) сократил до v1:
- Real handler через SPI ✅
- 5 endpoints в UserController ✅
- Без diff ❌ (отложено в future-ideas)
- Без auto target_type/target_id ❌ (требует @AuditTarget annotation)
- Без разметки 4 других контроллеров ❌ (incremental, aspect автомат)

Это **минимально полезное audit log** — закрывает insider threat
detection для user-management (most sensitive ADMIN domain) + всё
infrastructure (SPI, aspect, storage, indexes) готова к расширению.

См. DECISIONS § D10 для подробного scope reasoning.

### G6 — surprise: V19 + V20 split для CONCURRENTLY

Сначала пытался сделать одну V19 миграцию с CREATE TABLE + CREATE
INDEX в одном файле. Migration guard `MigrationConcurrentlyTest` ругался:
все CREATE INDEX в **новых** миграциях (после baseline cutoff 18)
обязаны использовать CONCURRENTLY (CLAUDE.md правило).

Но CONCURRENTLY **не работает в transaction**, а Flyway оборачивает
каждый файл в transaction. Разделил на 2 миграции:
- V19__audit_log.sql — CREATE TABLE (transactional, plain)
- V20__audit_log_indexes.sql — `-- ##` + 3 CREATE INDEX CONCURRENTLY

Это standard pattern в проекте (см. CLAUDE.md DB rules).

### G6 — surprise: aspect перешёл на ObjectProvider

Initial implementation использовала прямые `@Autowired
AuditLogStorage`. Проблема: shared-web используется и api-gateway
(WebFlux, без БД) — вынудит api-gateway предоставлять stub. Перешёл
на `ObjectProvider<AuditLogStorage>` — graceful degradation: если bean
отсутствует, aspect логирует warn и продолжает. Standard Spring
pattern для optional dependencies.

`@ConditionalOnWebApplication(SERVLET)` уже отсекает api-gateway от
shared-web auto-config — но defense-in-depth через ObjectProvider
покрывает edge cases (тесты без full Spring context, future
non-servlet servlets).

### G6 итог

Изменено 8 файлов + 4 новых:

Новые:
- `services/shared/shared-web/.../audit/AuditLogEntry.java` — value object
- `services/shared/shared-web/.../audit/AuditLogStorage.java` — SPI
- `services/shared/shared-web/.../audit/AuditLogContextProvider.java` — SPI
- `services/academic-service/.../audit/JdbcAuditLogStorage.java` — JDBC impl
- `services/academic-service/.../audit/AcademicAuditLogContextProvider.java` — context impl
- `services/academic-service/.../db/migration/V19__audit_log.sql` — table
- `services/academic-service/.../db/migration/V20__audit_log_indexes.sql` — indexes
- `docs/operations/runbooks/audit-log.md` — runbook

Изменены:
- `services/shared/shared-web/.../audit/AdminActionAspect.java` — real handler
- `services/shared/shared-web/.../audit/AdminActionAspectTest.java` — 6 тестов (расширены)
- `services/academic-service/.../user/UserController.java` — 5 `@AdminAction` annotations
- M16 PLAN/CHECKLIST/DECISIONS/NOTES

Tests: 24/24 shared-web (включая 6 AdminActionAspectTest), academic-app
compileJava clean, MigrationConcurrentlyTest зелёный.

### G7 — surprise: rate limit на gRPC, не REST

Изначально думал что headman RL — это для REST endpoint'ов старосты
(bulk-mark, excuse approve и т.п.). По факту обнаружил что **rate
limit стоит на gRPC `isHeadman()` методе** — внутренней RBAC-проверке,
которую schedule/attendance/notification дёргают для каждого request.

Bulk-mark группы 30 студентов в attendance делает 30 × `isHeadman()`
gRPC calls (по одному на каждого студента) → 30 calls/sec → за 4
секунды 120 hit достигнут → 121-й RESOURCE_EXHAUSTED → bulk-mark
ломается на середине. **Старый лимит 120 был too tight для bulk
operations** — owner и сообщил.

300/min = 5/sec sustained, 30/sec burst (в начале минуты bucket
полный). Покрывает bulk-mark группы 60 студентов за 2 секунды.

### G7 — дизайн: Redis primary + InMemory fallback

Initial мысль была заменить ConcurrentHashMap на Redis-only impl.
Понял что break'у unit-тесты без Testcontainers Redis. Решение:

1. `HeadmanRateLimiter` interface
2. `RedisHeadmanRateLimiter` — `@ConditionalOnProperty redis-enabled=true` (default)
3. `InMemoryHeadmanRateLimiter` — `@ConditionalOnProperty redis-enabled=false` fallback
4. Spring conditional autowiring выбирает один из двух

`InMemoryHeadmanRateLimiter` — буквально existing TokenBucket код
вынесенный в отдельный bean. Zero behaviour change для tests без
Redis. См. DECISIONS § D12.

### G7 — Redis fail-open vs G3 OTP fail-closed

Принял разную fail policy для двух RL в одном milestone:
- **G3 OTP brute-force** — fail-closed (Redis exception → 429)
- **G7 headman RL** — fail-open (Redis exception → counter + skip)

Reasoning в DECISIONS § D11. Кратко: что хуже если RL bypassed?
- Headman: bursty admin work, legitimate в природе
- OTP: password equivalent compromise

### G7 — что НЕ сделали

- IT с Testcontainers Redis для нового limiter — existing
  `AcademicGrpcIT.isHeadman_rateLimitExceeded_throwsResourceExhausted`
  теперь проходит через RedisHeadmanRateLimiter (тестконтейнеры Redis
  включены в IT setup) и должен проверять реальное поведение. Запуск
  IT отложен до VPS verify.
- `docs/api/api-rate-limits.md` обновление — это не публичный API
  (gRPC internal), документировать там излишне.
- Prometheus alert на `headman_rl_redis_failures_total` — existing
  `RedisDown` через `redis_up` метрику покрывает root cause.

### G7 итог

Изменено 2 файла + 5 новых:

Новые:
- `HeadmanRateLimiter.java` — interface
- `RedisHeadmanRateLimiter.java` — Redis-backed impl (primary)
- `InMemoryHeadmanRateLimiter.java` — fallback
- `HeadmanRateLimitProperties.java` — config record
- `HeadmanRateLimitConfig.java` — `@EnableConfigurationProperties`
- `RedisHeadmanRateLimiterTest.java` — 6 unit-тестов
- `InMemoryHeadmanRateLimiterTest.java` — 4 unit-теста

Изменены:
- `AcademicGrpcServiceImpl.java` — inline ConcurrentHashMap+TokenBucket удалён, инжектируется HeadmanRateLimiter
- `application.yml` — defaults `academic.headman-rate-limit.*`
- `AcademicGrpcIT.java` — лимит 120→300 в тесте

Tests: 10/10 unit + compile clean. IT отложен до VPS.

### G8 — owner-driven scope reduction: G8a only

При планировании G8 я предложил owner'у три варианта (после рассмотрения
полного scope):

- **(a)** только G8a (cap_drop NET_RAW для 4 infra containers) + G9 — bundle ~30 минут
- **(b)** полный G8 (mTLS с internal CA) + G9 — ~1.5-2 дня + ongoing CA rotation overhead
- **(c)** только G9, G8 → отложить в M17

Owner спросил «что даст вариант b? Или для моего проекта нет смысла?». Дал
threat-model assessment:

- Полный mTLS защищает от очень узкого scenario «full RCE в одном infra
  container без host escape, attacker хочет именно sniff/forge alerts»
- Single VPS, single tenant, digest-pinned images = low surface
- (a) даёт ~80% той же защиты (NET_RAW = sniffing capability) за 1/30 cost
- (b) оправдывает себя при multi-host deploy / compliance / first incident

Owner: **«берём вариант а тогда и остальное закидываем во future ideas»**.
Решение зафиксировано в DECISIONS § D13.

### G8a — почему 4 контейнера, не 3

Initial plan (NEXT-SESSION.md) указывал 3 контейнера: cadvisor + node-exporter
+ blackbox-exporter. По логике symmetric defense я добавил **4-й — alertmanager**:

- Alertmanager — sender side того flow, который мы защищаем
- Если он скомпрометирован, он не должен иметь capability sniff'ить _other_
  трафик в private_net (например, RabbitMQ AMQP frames с reminder messages)
- Cost zero (alertmanager не нуждается в raw-sockets)

Это **defense-in-depth** через симметрию: drop'аем NET_RAW у каждого
контейнера, который НЕ нуждается в нём. Если бы мы ограничились только
sniffer-side, sender remained as potential attack pivot.

### G9 — surprise: SYS_PTRACE НЕ нужен

Initial CHECKLIST G9 v1 предписывал заменить `privileged: true` на
`cap_add: [SYS_PTRACE]` (per NEXT-SESSION.md). По research понял что
это **incorrect assumption**:

- `cadvisor docs running.md` показывает только `--device=/dev/kmsg` +
  `--security-opt seccomp=default.json` для де-привилегированного
  запуска. **Zero `cap_add` директив.**
- cadvisor issue #3051: «running without privileged but as root was
  necessary to access all required metrics» — root user + default
  caps = sufficient.
- cadvisor читает /proc + /sys, **не ptraces** — SYS_PTRACE was
  cargo-cult. Adding capability без real need = unnecessary attack
  surface.

**Lesson learned:** при де-привилегировании контейнера research
upstream docs до того как добавлять capabilities. Default Docker
caps минус specific drops часто достаточно. См. DECISIONS § D14.

### G9 — fallback strategy зафиксирован

Если на VPS после redeploy `up{job="cadvisor"}` падает либо метрика
`container_oom_events_total` теряется — rollback тривиален: вернуть
`privileged: true` + убрать `devices:` секцию. Это документировано
в comment'е `docker-compose.prod.yml` (cadvisor block).

Лучше bias на сторону «попробовать де-привилегировать, rollback если
что» чем оставить privileged: true forever.

### G8a + G9 итог

Изменено 1 файл:
- `docker-compose.prod.yml` — 4 секции (node-exporter, cadvisor,
  blackbox-exporter, alertmanager) получили `cap_drop: [NET_RAW]`;
  cadvisor дополнительно — убран `privileged: true`, добавлен
  `devices: [/dev/kmsg:/dev/kmsg]`.

Validate:
- `docker compose config --quiet` (с stub SWAGGER_HTPASSWD) — syntax OK
- `parsed config | grep cap_drop` — 4 entries
- `parsed config | grep privileged` — 0 entries (privileged: true исчез)

Verify на VPS отложен до redeploy. Risk-reward в favor of trying.
