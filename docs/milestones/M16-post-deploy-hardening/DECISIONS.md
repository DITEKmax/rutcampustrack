# M16 Decisions

Architectural choices с обоснованием. Только то, что не очевидно из кода.

---

## D1 — Headman rate-limit: 300 req/min (повышен с 120)

**Контекст:** в `future-ideas.md` § F05 предлагался Redis-перенос с
сохранением лимита 120/min. По факту M15 staging staros'а делали bulk-mark
группы из 30 студентов меньше чем за минуту, упирались в лимит, бросали
повторы → жалобы.

**Решение:** при переносе в Redis (G7) одновременно поднять лимит до
300/min. Это ~5 запросов в секунду — реально использовать только при
bulk-операциях, не злоупотребление.

**Trade-off:** более слабая защита от compromised headman account, но
рост legitimate traffic compatibility критичнее. Если будет abuse —
понизить через env var (`ACADEMIC_HEADMAN_RL_PER_MINUTE`).

---

## D2 — OTel migration: Java HTTP/protobuf, Python gRPC (mixed-mode)

**Контекст:** Tempo поддерживает оба (4317 gRPC, 4318 HTTP). При
проверке кода обнаружено, что Java-сервисы и Python-бот используют
**разные** OTel exporters:

- Java (Spring Boot Micrometer) → HTTP/protobuf, default port 4318
- Python (notification-bot) → gRPC через
  `opentelemetry.exporter.otlp.proto.grpc.trace_exporter.OTLPSpanExporter`,
  default port 4317

**Что было сломано:** все клиенты были сконфигурированы на 4317.
Java-сервисы шлют HTTP frame в gRPC-порт → Connection reset → шум в
логах. Python-бот шлёт gRPC в gRPC-порт → работало.

**Решение:** mixed-mode.

- Java → 4318 + path `/v1/traces`
- Python → 4317 (без пути)

**Альтернатива:** унифицировать на gRPC (добавить
`opentelemetry-exporter-otlp` в Java и заменить Spring HTTP exporter).

**Почему не выбрали унификацию:** Spring Boot 3.4 Micrometer Tracing
использует HTTP exporter из коробки. Замена требует ручной
TracerProvider конфигурации, конфликт с Micrometer auto-config,
~1д работы + риск регрессии. Mixed-mode даёт zero-cost win.

**Trade-off:** в DECISIONS зафиксировано смешение протоколов. Tempo
парсит оба, но команда должна помнить разницу при дебаге OTel issues.

**Verify-checkpoint:** в M16 G1 implementation проверено через grep
импортов — `.proto.grpc.` (Python bot) vs Java Micrometer default
(HTTP). После migration лог-pattern в проде:
- Java: `OkHttp http://tempo:4318/v1/traces` — успех
- Python: `gRPC tempo:4317` — успех

---

## D3 — Audit log: SPI в shared-web, реализация в academic-app

**Контекст:** `@AdminAction` aspect живёт в `shared-web` (используется
всеми сервисами). Запись в `audit_log` таблицу должна быть в БД,
которая принадлежит конкретному сервису. Shared-модуль не должен знать
про academic_db.

**Решение:** SPI pattern.

```java
// shared-web/audit/AuditLogStorage.java
public interface AuditLogStorage {
    void store(AuditLogEntry entry);
}
```

Aspect вызывает SPI, конкретный сервис предоставляет implementation
(`JdbcAuditLogStorage` в academic-app использует local DataSource).

**Trade-off:** нужно реализовать storage в каждом сервисе который
использует `@AdminAction`. Сейчас только academic — других ADMIN-actions
не предполагается. Если появятся — schedule/attendance ADMIN endpoints
должны будут предоставить свой storage. Это нормально (audit log
хранится в БД того сервиса, который владеет данным доменом).

---

## D4 — mTLS Alertmanager → notification-web: G8a (cap_drop NET_RAW) only, G8b deferred

**Решение принято в M16 G8 implementation (см. также D13).**

Кандидаты были:
1. Linkerd auto-mTLS sidecar — overhead (extra container per service), но zero-config
2. Custom certs + nginx proxy — контроль, но manual rotation
3. Минимальный путь: только `cap_drop: NET_RAW` для cadvisor + node-exporter + blackbox-exporter + alertmanager, оставив plaintext (sniffing capability убрана у потенциального compromised peer, MitM проблема остаётся theoretical)

**Выбран вариант 3 (G8a).** Полный mTLS (вариант 2) перенесён в
`future-ideas.md` MED-11 с trigger condition: «при подготовке к
horizontal scaling либо после first incident, либо при появлении
compliance requirement». См. D13 для полного reasoning.

Linkerd (вариант 1) отвергнут — нет other Linkerd usage в проекте,
adding sidecar per service = большая зависимость без proportionate
benefit для single-host setup.

---

## D5 — G2 переориентирован: idempotency уже была, fix exception swallow

**Контекст:** исходный план G2 (взято из `future-ideas.md` § OTP
hardening / HIGH.2) — добавить idempotency через Redis SET NX в
EventDispatcher. При работе обнаружено что `BotIdempotencyGuard`
уже реализован в **M13 G8** (commit `494821fe`):
- `services/notification-bot/bot/services/idempotency_guard.py` — `try_claim()` через `SET NX EX 3600`
- `event_consumer.py:97-99` — wraps dispatch
- 7 unit-тестов покрывают первый/duplicate/independent event_id, missing event_id (fail-closed), Redis error (fail-closed), TTL expire

`future-ideas.md` HIGH.2 текст устарел.

**Реальный остаточный bug:** `dispatcher.dispatch()` имел
`try/except Exception` swallow handler-exception'ов с комментарием
«ack safety». Это **противоречило** comment'у в consumer.py о M13
G24-fix-2 («handler exceptions → DLQ»). На практике:
- Handler bug → exception caught в dispatcher → ack message → silent loss
- DLQ flow в consumer.py никогда не активировался для handler-bug'ов

**Решение:** убрать swallow в dispatcher → exception проходит до
`consumer.process(requeue=False)` → message в DLQ → alert
`DLQBacklog` (Prometheus) → manual triage по
`docs/operations/runbooks/dlq-triage.md`.

**Расширение скоупа (B+):** `notification-bot.events.dlq` declare'ился
без `x-message-ttl` / `x-max-length` — без propagation он бы рос
безгранично при flood handler bug'ов. Добавлены:
- `x-message-ttl: 7d` (синхронно с разумным окном для triage)
- `x-max-length: 10000` (cap)
- `x-overflow: drop-head` (старые сначала, чтобы новые landed)

**Trade-off:** при handler-bug'е во flood'е (например, fan-out на
1000 студентов с broken Telegram API token) DLQ может достичь cap
за минуту. После cap → старые messages дропаются. Acceptable: `DLQBacklog`
alert (>10 за 5 мин) сработает **до** cap'а, времени на reaction
достаточно.

**Java-сторона (notification-web.history.dlq) НЕ тронута.** Та же
проблема для Java consumer'ов отслежена отдельно как N6 в
`future-ideas.md` § «Notification history bundle». Должна быть
закрыта тем же паттерном (`arguments` в `@RabbitListener` queue
declaration), но это отдельный PR — не в скоуп G2.

---

## D6 — G3 OTP brute-force: pre-check + no reset-on-success

**Контекст:** реализация counter'а для `/otp/verify-by-code` (HIGH SA-H1).
Три варианта:

1. **Counter ставится перед каждым attempt, reset при success.** Простая
   логика, но атакующий может угадать valid code (10 live OTP / 10^6
   space → ~1.7×10^-4 за 21 попытку), reset'нуть counter, продолжить.
2. **Counter ставится только при mismatch, без reset.** Защита
   корректная — даже случайное попадание не помогает дальше брутить.
   Legit users не штрафуются (paste typo с попаданием в чужой live
   OTP — рано в верификации, code mismatch'ит на проверке кода в
   отдельном flow).
3. **Counter увеличивается на каждый attempt.** Самое грубое — legit
   user с typo съест 1 attempt, что для 20-attempt window нормально.

**Решение:** **вариант 2** — counter инкрементится **только при
mismatch**, **reset не делается**.

Pre-check (counter ≥ 20 → 429 БЕЗ проверки кода) экономит Redis
ops при flood'e, и не даёт side-channel «counter check timing» —
атакующему всё равно неинформативно (кол-во attempts visible).

**Сменил outcome `expired` → `mismatch` в `verifyOtpByCode` mismatch
branch.** На этом code-path Redis уже удалил key (либо никогда не
было), это семантически **mismatch**, не «expired». Старый outcome
`expired` остаётся в `verifyOtp` (по telegramId) — там может быть
true expiry после legit `requestOtp`. Это change в метриках
(`otp_verify_total{outcome="expired"}` в проде упадёт, `mismatch`
вырастет на verify-by-code volume), но более точно семантически.

**Trade-off:** один legit user с 20 typo подряд получит 5-минутный
ban с этого IP. Acceptable — typo 20× подряд индикатор либо broken
keyboard, либо probe.

**Defense-in-depth:** существующий Gateway RateLimiter (5 req/min/IP
на `/auth/otp/*`) — **первый** layer, рубит до auth-service. Counter в
auth-service — **второй**, защищает от distributed attack'ов где Gateway
RL обходится через раздачу IP. Слои не конфликтуют — Gateway 5/min
никак не вступает с counter 20/5min (Gateway отрубит ~25 attempts за
5 min, что под cap'ом).

---

## D7 — G4 Loki: startup race fix через healthcheck-gated promtail

**Контекст:** в Loki логах 1-2 ошибки/час `pusher failed to consume
trace data, err="DoBatch: InstancesCount <= 0"`. Симптом обнаружен
в M15 после first VPS deploy.

**Гипотеза 1 (по тексту future-ideas.md):** Tempo шлёт trace data
в Loki по ошибке (mis-routed exporter). **Опровергнута:** OTel
exporters в проекте — Java HTTP/protobuf и Python gRPC, оба идут
прямо в Tempo (`tempo:4318` / `tempo:4317`). Текст в Loki errors —
это **внутренний термин ring'а** про "trace" (trace context, не
distributed trace). Misleading naming.

**Гипотеза 2 (research-confirmed):** startup race. Distributor
(в monolithic mode он же ingester) пытается DoBatch до того как
ingester прошёл lifecycle JOINING → ACTIVE. Ring видит self но
gRPC connect к 127.0.0.1:9096 fails → instance unhealthy →
InstancesCount=0.

**Источники:**
- Loki docs Configure §ingester.lifecycler — `min_ready_duration`
- Mimir issue #4662 — той же ошибки в Mimir, root cause = gRPC
  connectivity (в нашем случае startup, не Istio как у них).

**Решение (двухуровневое):**

1. **Server-side** (`infra/loki/loki.yml`): эксплицитно
   `ingester.lifecycler.min_ready_duration: 15s`. Это default, но
   явно зафиксирован для документации и защиты от изменения default'а
   в future versions.

2. **Client-side** (`docker-compose.prod.yml`):
   - Loki container получает healthcheck на `/ready` (проверяет что
     `min_ready_duration` прошёл).
   - Promtail `depends_on: loki: condition: service_healthy` —
     не стартует до ready.

Single-fix (только sever-side `min_ready_duration`) не достаточно
если promtail подключился до `/ready` и кэшировал stale connection.
Healthcheck-gating обеспечивает что push'и физически не идут в
unready ingester.

**Trade-off:** start-up sequence удлиняется на 15-30 секунд (loki
startup + healthcheck retry). Acceptable — promtail tail'ит позицию
из `/tmp/positions.yaml`, ничего не теряется.

**Verify по runbook:** после redeploy + 24h наблюдения
`docker logs rct-loki --since 24h | grep -c "InstancesCount"` должно
быть 0 (или близко). Если ошибки остаются — escalation per
`docs/operations/runbooks/loki-troubleshooting.md`.

**Что НЕ сделали:**
- Multi-tenancy (`auth_enabled: true`) — single-org setup, не нужно.
- gRPC keepalive tuning — оставлено as escalation step в runbook.
- Disable inmemory kvstore → memberlist — overkill для single-instance.

---

## D8 — G5 nginx: per-upstream variables + Docker embedded DNS

**Контекст:** после каждого `docker compose up -d --no-deps <X>` или
deploy.yml пробежки upstream X отдаёт **502 Bad Gateway** через main
`rct-nginx`. Манyally исправляется через `docker compose restart nginx`.

**Root cause:** nginx без `resolver` директивы резолвит DNS **один раз
при старте** и кэширует IP. Когда docker recreate'ит upstream-контейнер,
он получает **новый** IP (Docker embedded DNS обновляется), но nginx
ходит в старый → connection refused → 502.

**Решение (двухуровневое):**

1. `resolver 127.0.0.11 valid=10s ipv6=off;` — Docker embedded DNS,
   re-resolve каждые 10 секунд.

2. **Переменные** в `proxy_pass` (`set $upstream_X "rct-X:N"; proxy_pass
   http://$upstream_X;`). Без переменной nginx делает DNS resolve **только
   при загрузке config'а**, и `resolver` директива игнорируется.
   Переменная заставляет nginx делать **runtime resolve** на каждый
   запрос (с TTL caching из `valid=10s`).

**Альтернативы:**
- **Single shared `resolver` в `nginx.conf` http{}**: тоже работает, но
  тогда `resolver` глобален и применим ко всем server-блокам. Outer
  proxy резолвит публичный DNS — там Docker resolver неприменим.
  Решено: `resolver` в server-блоке, scope ограничен HTTPS-сервером.
- **`upstream { server rct-X:N; }` блоки**: nginx читает upstream{} при
  старте конфига и игнорирует resolver. Без commercial nginx Plus
  upstream{} dynamic-resolution невозможен.

**Trade-off:** runtime DNS resolve добавляет ~0.1ms на запрос
(Docker resolver = local socket, dirt-cheap). За 10s window nginx
кэширует resolved IP, реальный resolve только раз в 10s per
upstream. Acceptable.

**Verify:** на VPS после redeploy:
```bash
docker compose -f docker-compose.prod.yml restart grafana
sleep 5  # дать compose recreate'нуть
curl -s -o /dev/null -w "%{http_code}\n" https://ruttrack.site/grafana/
# Ожидание: 200/302 в течение 15 сек, без ручного `restart nginx`
```

---

## D9 — G5 verify-deploy.sh: 4 false-positive fixes

**Контекст:** `scripts/verify-deploy.sh` имел 4 false-positive fail'а
на полностью здоровом проде, описанные в `future-ideas.md` §
«verify-deploy.sh устарел до v9.0»:

1. `Landing redirect — expected 200, got 301`: проверка `/` ожидала
   200, но v9.0 INFRA-v9-01 делает 301 на `/login`.
2. `/login — got 502`: nginx DNS race (D8) — transient после restart.
3. `/api/health — got 404`: endpoint никогда не существовал, реальный
   путь `/actuator/health` через docker exec.
4. `TTL index check failed`: `mongosh -u $MONGO_NOTIFICATION_USER` (PoLP
   user) не имеет привилегий на `getIndexes()`. Нужен `root` user.

**Решение:** все 4 fix'а в одном Edit:
1. `expect_status` поддерживает comma-separated list → `/` принимает
   `"200,301"`.
2. Новый `expect_status_retry` helper (5 × 2s) для post-restart
   tolerance — используется на `/login`, `/app/`, `/presentation/`.
3. Gateway health через `docker exec rct-api-gateway wget
   localhost:8080/actuator/health` + grep status:UP.
4. Mongo TTL check переключён на `MONGO_INITDB_ROOT_USERNAME/PASSWORD`
   с auth source admin.

**Trade-off:** retry loop добавляет до 10 секунд к runtime скрипта при
transient nginx DNS issue. Acceptable — verify-deploy.sh не на hot
path, цель — корректность результата.

**Что НЕ сделали:**
- Не переключили на real probe `/api/health` (запрос пользователя был
  бы добавить такой endpoint). Оставлено docker exec workaround,
  возможно в M17 добавим публичный health endpoint.
- Mongo TTL check **fail'ит** теперь (раньше был warn) если creds
  не выставлены — это намеренно (теперь это P2-блокер для prod).

---

## D10 — G6 audit log v1: minimum viable scope

**Контекст:** initial G6 план обещал before/after diff + разметку
~15 ADMIN endpoints. По факту реализованы:
- Real audit handler через SPI (storage + context provider)
- 5 endpoints в UserController
- **БЕЗ** before/after diff
- **БЕЗ** target_type/target_id auto-extraction
- **БЕЗ** разметки 4 других контроллеров (Group / Semester / Subject /
  Threshold / Assignment)

**Почему сужен:**

1. **Before/after diff** требует:
   - Deep cloning entity до изменения (entity потенциально lazy-loaded
     JPA proxy, проблемы с serialization).
   - JSON diff library (например `zjsonpatch`) — новая зависимость,
     security review surface.
   - Решение что включать в diff (passwords/PII должны быть masked).

   Минимум 1-2 дня работы только на это. Отложено до появления
   реальной нужды (compliance / forensic incident).

2. **target_type/target_id** требует `@AuditTarget` param annotation
   которая extract'ит значения из `@PathVariable Long id` + класс из
   контекста (метода / endpoint). Это не trivially expressible через
   AOP — нужен extra reflection. Тоже отложено.

3. **Разметка остальных контроллеров** — incremental work, можно
   делать постепенно. Aspect автоматом подхватит новые `@AdminAction`
   методы. Сейчас покрытие user-management actions достаточно для
   first deploy (это самая чувствительная часть — кто пользователей
   архивирует / создаёт / role меняет).

**Что НЕ потеряли:**
- Insider threat detection: видим WHO + WHEN + WHAT для user actions
  (5 endpoints). Атрибуция доступна.
- Forensic correlation: correlation_id связывает audit row с
  distributed trace в Tempo.
- Failed-attempt tracking: `succeeded=false` + `error_message` ловит
  attempts, которые упали на validation/RoleCheck.

**Trade-off:** при инциденте «admin изменил роль user'а X с STUDENT на
ADMIN» мы знаем кто (`user_id`) + когда (`created_at`) + что
(`action='user.update'` + `target_id` через path). Но **не** знаем что
именно поменяли (нет diff). Acceptable для baseline; future incident
с этим smell triggers будут реализацию diff.

**Graceful degradation:**
- `AuditLogStorage` bean missing → log.warn, ADMIN endpoint работает.
- Storage exception → log.warn, ADMIN endpoint работает.
- `RequestContext` out of scope → user_id=NULL, остальное пишется.

Best-effort принцип: audit log **не должен ломать** ADMIN endpoint.

---

## D11 — G7 headman RL: Redis fail-open vs OTP fail-closed

**Контекст:** в M16 уже два rate-limit'а с разной semantics:
- **G3 OTP brute-force counter** — fail-closed (RuntimeException пробрасывается caller'у при Redis failure через M13 G24-fix-2 inheritance в OTP path)
- **G7 headman RL** — fail-open (логируем `headman_rl_redis_failures_total`, пропускаем call)

**Почему разная политика?**

Headman RL защищает от **flood** (legitimate user × горизонт scaling).
Atacker scenario: compromised headman account → bulk operations →
DoS на academic. Acceptable degradation: при Redis outage limit
снимается, attacker может сделать 4 × 300 = 1200 calls/min (4 pods).
Это всё ещё значительно меньше unrate-limited (10K/sec) — Gateway
RateLimiter перед академиком даст cap. **Trade-off:** при Redis
outage legitimate users не должны блокироваться от headman actions.

OTP brute-force защищает от **credential discovery**. Atacker scenario:
brute-force 6-digit OTP space. Acceptable degradation: НЕ снимать
limit при outage — иначе botnet может попробовать все 10^6 кодов
за минуту. **Trade-off:** при Redis outage legitimate users не могут
сделать verify-by-code — они **должны** ждать восстановления Redis.
Это лучше чем дать attacker'у свободный доступ.

Иначе говоря: **what's worse if RL bypassed?**
- Headman RL bypass → bursty admin work, но legitimate в природе.
- OTP RL bypass → password equivalent compromise.

Поэтому fail-closed только в OTP, fail-open в headman.

**Visibility:** counter `headman_rl_redis_failures_total` инкрементится
при каждом fail-open пути → можно alert'ить если > 1/min sustained
(индикатор Redis outage). Не добавляю Prometheus rule в этом G7 —
наш existing `RedisDown` через `redis_up` метрику и так покрывает.

---

## D12 — G7 headman RL: in-memory остаётся как fallback

**Контекст:** при изначальной идее G7 я думал просто заменить
ConcurrentHashMap на Redis-only impl. По факту оставил **обе**:
- `RedisHeadmanRateLimiter` — primary (default `redis-enabled: true`)
- `InMemoryHeadmanRateLimiter` — `@ConditionalOnProperty redis-enabled=false`

**Почему две?**
1. **Тесты без Testcontainers Redis.** Многие unit-тесты раньше
   получали `ConcurrentHashMap` бесплатно. Теперь без in-memory
   fallback они бы падали на missing Redis bean. `redis-enabled=false`
   в test profile — кратчайший путь.
2. **Local dev без Redis.** Хотя у нас docker-compose всегда
   запускает Redis, разработчик может временно его выключить и
   academic должна продолжать работать.
3. **Migration safety.** Если RedisHeadmanRateLimiter откажет на
   проде, env-var override `ACADEMIC_HEADMAN_RL_REDIS_ENABLED=false`
   снимет dependency без redeploy.

**Trade-off:** дублирование кода (TokenBucket класс существует в
fallback). Acceptable: ~50 строк, minimal maintenance burden, и
это explicit migration safety.

**Что НЕ сделали:**
- IT с Testcontainers Redis для RedisHeadmanRateLimiter — unit-тестов
  с mock'ами StringRedisTemplate достаточно для логики, а existing
  AcademicGrpcIT.isHeadman_rateLimitExceeded_throwsResourceExhausted
  теперь проходит через RedisHeadmanRateLimiter (тестконтейнеры Redis
  включены в IT setup). VPS verify подтвердит реальное поведение.

---

## D13 — G8 reduced to G8a (cap_drop NET_RAW); G8b (full mTLS) deferred

**Контекст:** initial G8 план обещал full mTLS Alertmanager →
notification-web с internal CA, client/server certs, tls_config в
alertmanager.yml, `server.ssl.client-auth=need` в notification-web.
Estimated 1.5-2 days of work + ongoing operational overhead (CA
rotation runbook, expired-cert silent-failure mode).

**Owner-driven scope reduction (2026-04-27 conversation):**
> «берём вариант а тогда и остальное закидываем во future ideas»

**Reasoning от Claude (presented в conversation, accepted by owner):**

Threat model assessment для (b) full mTLS:
- Attacker должен иметь RCE в одном infra container (cadvisor / node-exporter /
  blackbox-exporter / alertmanager) **без** escape в host
- И хотеть именно sniff Bearer token / forge fake alerts (не exfiltration / cryptomining)

Реальная вероятность low:
- Single VPS, single tenant, single owner
- Все images digest-pinned (M06 D2) — supply-chain attack contained
- Public exposure только nginx :443 — infra containers без публичного access
- Bearer token хранится в `.env.prod` (не в env infra-контейнеров)

(b) защищает от очень узкого scenario «full RCE в Alertmanager-контейнере»,
который сам не имеет public exposure. Cost-benefit ratio плохой при
текущем scale.

(a) `cap_drop: [NET_RAW]` даёт ~80% той же защиты за 30 минут:
- Скомпрометированный peer не может sniff Bearer token из plaintext-трафика
- Это главный realistic vector для leak'а текущего mechanism
- Plaintext остаётся, но без sniff capability MitM невозможен «from inside»

**Решение:** реализовать G8a (4 × `cap_drop: [NET_RAW]` в compose —
cadvisor, node-exporter, blackbox-exporter, alertmanager). G8b отложить
в `future-ideas.md` MED-11 с trigger condition:
- Horizontal scaling (multi-host deploy)
- Compliance requirement (PCI/HIPAA/ISO 27001)
- First incident с подозрением на insider threat

**Trade-off:**
- (b) дала бы cryptographic identity verification + encryption-in-transit
- (a) даёт только защиту от sniff'а, MitM (теоретически) остаётся
- Acceptable: MitM требует уже compromised peer, который без NET_RAW не
  может redirect трафик

**Verify-checkpoint:** на VPS после redeploy убедиться что:
- Все 4 контейнера (cadvisor, node-exporter, blackbox-exporter, alertmanager) живые
- Webhook flow работает (alert → Telegram через notification-bot)
- Нет регрессии в metrics (cadvisor, node-exporter scrape continues)

---

## D14 — G9 cadvisor: drop privileged без cap_add; +/dev/kmsg device

**Контекст:** изначальный план G9 предписывал заменить
`privileged: true` на `cap_add: [SYS_PTRACE]`. По research
(cadvisor docs `running.md` + cadvisor issue #3051) обнаружено:

1. **cadvisor `running.md` (upstream docs):** «If cAdvisor needs to be
   run in Docker container without `--privileged` option it is
   possible to add host devices to container using `--dev` and specify
   security options using `--security-opt`». **Никаких `cap_add`
   директив явно не упомянуто.** Пример:
   ```
   docker run \
     --volume=/:/rootfs:ro \
     ...
     --device=/dev/kmsg \
     --security-opt seccomp=default.json \
     cadvisor:<tag>
   ```

2. **cadvisor issue #3051 (user report):** «running without privileged
   but **as root** was necessary to access all required metrics».
   Permission denied только если non-root.

**Вывод research:** `privileged: true` нужен **не** для специфических
capabilities (типа SYS_PTRACE / SYS_ADMIN), а для:
- Default Docker capabilities + uid=0 (root inside container)
- Read-only mounts to host filesystems
- `/dev/kmsg` device access (для kernel events / OOM tracking)

`SYS_PTRACE` нужен если cadvisor сам ptraces process'ы — это **не его
обычный mode** (cadvisor читает /proc + /sys, не ptraces). Adding
SYS_PTRACE без real need = unnecessary attack surface.

**Решение:**
1. Убрать `privileged: true`
2. Добавить `devices: [/dev/kmsg:/dev/kmsg]` (per upstream docs)
3. Сохранить existing read-only mounts unchanged
4. **НЕ** добавлять `cap_add` — default Docker caps достаточны
5. cadvisor продолжит запускаться как root (default user) — это OK
   per #3051

**Что меняется в attack surface:**

Was (privileged: true):
- ✗ Full host root access на write/exec
- ✗ All capabilities (CAP_SYS_ADMIN, CAP_DAC_READ_SEARCH, CAP_NET_ADMIN, …)
- ✗ Может modify kernel parameters via /proc, manipulate cgroups, etc.

After (no privileged + /dev/kmsg + default caps - NET_RAW):
- ✓ Read-only host filesystem access (existing mounts, unchanged)
- ✓ Default Docker capabilities (≈14 capabilities, vs ~37 при privileged)
- ✓ NET_RAW dropped (G8a) — sniffing capability removed
- ✓ Cannot modify host state (writes to mounted /:/rootfs:ro fail)

**Trade-off:**
- Теоретически возможно регрессия в exotic метриках (perf events, cpu
  scheduler details). Audit на VPS — `count by (name)
  (container_memory_usage_bytes)` should remain >20 (one per running
  container). `container_oom_events_total` — критическая метрика для
  alert'а ContainerMemoryHigh, depends on /dev/kmsg access.
- Если **что-то сломается** (метрика недоступна) — rollback тривиален:
  вернуть `privileged: true` + удалить `devices:`. Документировано
  в comment'е docker-compose.prod.yml.

**Verify (post-deploy):**
1. `docker logs rct-cadvisor --since 5m | grep -i "permission\|denied\|error"` → пусто
2. `up{job="cadvisor"}` == 1 (Prometheus target healthy)
3. `count by (name) (container_memory_usage_bytes)` > 20
4. Grafana dashboard `rct-containers` рендерится без gaps
5. Artificial OOM stress test на canary container → `container_oom_events_total` > 0 → ContainerMemoryHigh alert firing
