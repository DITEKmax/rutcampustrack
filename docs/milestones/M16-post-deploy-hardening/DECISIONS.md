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

## D4 — TBD: mTLS Alertmanager → notification-web путь

Зафиксируется при выполнении G8.

Кандидаты:
1. Linkerd auto-mTLS sidecar — overhead (extra container per service), но zero-config
2. Custom certs + nginx proxy — контроль, но manual rotation
3. Минимальный путь: только `cap_drop: NET_RAW` для cadvisor + node-exporter + blackbox-exporter, оставив plaintext (sniffing capability убрана у потенциального compromised peer, MitM проблема остаётся)

Решение принимается с учётом текущей архитектуры (нет other Linkerd
usage → adding sidecar = большая зависимость).

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
