# M13 Notes

Живой файл. Пиши сюда:
- **Отклонения от плана:** «решил сделать X вместо Y, потому что...»
- **Измерения:** «p95 latency до: 450ms, после: 120ms»
- **Surprises:** «обнаружил, что в academic N+1 ещё и в getGroup»
- **Вопросы к владельцу:** «надо ли кэшировать X на Redis в v0.0.0 или Caffeine достаточно?»
- **Технические долги:** «оставил TODO в LessonService.java:234 про retry policy — закрою в M{X}»

Не пиши:
- Общие описания модулей (это в PLAN.md).
- WHY-обоснования (это в OWNER-ANSWERS.md и v0.0.0-debt.md).
- Пошаговые инструкции (это в CHECKLIST.md).

---

## 2026-04-24

- M13 создан на основе сессии обсуждения кандидатов #1-25 + #16 InvalidParam.
- Источник: `docs/report-before-v0.0.0/v0.0.0-debt.md` (создан в той же сессии).
- 23 группы задач, ~150 checklist items, estimate 5-7 человеко-дней.
- **Итоговые решения владельца по спорным пунктам:**
  - Кандидат #5 (consumer dedup) — **вариант A** (полный фикс всех consumer'ов, не только OTP в боте).
  - Кандидат #7 (X-Forwarded-For) — **v0.1** (VPS приватный, single-разработчик).
  - Кандидат #8 (Mongo outbox) — **вариант A** (replica set + @Transactional, не workaround).
  - Кандидат #10 (refresh-body) — **вариант A** (удалить, даже если grep покажет usage — mock data).
  - Кандидат #13 (Playwright E2E) — **вариант B** (автоматизация, не manual smoke).
  - Кандидат #14 (Swagger) — **вариант C** (runbook + fail-fast).
  - Кандидат #15 (env.prod) — **вариант C** (example + validation script).
  - Кандидат #16 (deploy runbook) — **вариант C** (dry-run only, backup/rollback/zero-downtime → v0.1).
  - Кандидат #17 (Alertmanager E2E) — **вариант B** (smoke + docs/alerts.md каталог).
  - Кандидат #18 (healthcheck) — **вариант B** (directives + health indicators).
  - Кандидат #19 (backup) — **вариант C** (backup + tested restore + .env.prod GPG, 7d retention, VPS-snapshot хостера как 2-й слой).
  - Кандидат #20 (WebSocket) — **вариант A** (nginx + STOMP smoke).
  - Кандидат #21 (CSP) — **вариант B** (audit + report endpoint, NEW-54 перенесён из v0.1).
  - Кандидат #22 (rate-limit auth endpoints `/change-password` + `/ws-ticket`) — **v0.1** (authenticated, low priority).
  - Кандидат #23 (Grafana/Prometheus/Alertmanager) — **вариант B** (dashboards sanity + открыть Prometheus/Alertmanager за basic-auth).
  - Кандидат #24 (CORS) — **v0.1** (same-origin, риск околонулевой).
  - Кандидат #25 (certbot) — **вариант A** (renewal hook + cert expiry alert).
  - Кандидат #26 (VPS capacity) — **пропущено** (8GB/120GB/4core — достаточно).
  - **22 пропущенных пункта** debt-report'а — все v0.1 (разобраны по критерию "дорожает ли со временем"). Единственный перенесённый в M13 — **#16 InvalidParam migration** (legacy risk).
- Mini-app не упоминается в M13 scope (решение владельца: отдельная сессия после отточки PWA).

## Важные nuance'ы для executor'а

- **Rate-limit precise math:** `replenishRate=1` (1 токен/сек) + `requestedTokens=12` (запрос стоит 12 токенов) + `burstCapacity=5`. Первые 5 запросов = 60 токенов сразу (burst), далее 1 запрос каждые 12 сек ≈ 5/min. UX: после 5 ошибок — 12 сек на следующий запрос.
- **Mongo replica set migration:** `rs.initiate()` на запущенной standalone Mongo → restart с `--replSet rs0` → confirm `rs.status()`. Нужна в **dev compose** и **prod compose**. `MongoTransactionManager` bean требуется **и** для attendance-app, **и** для notification-web.
- **Consumer dedup schema:** `event_consumer_processed (consumer_id VARCHAR, event_id VARCHAR, processed_at TIMESTAMPTZ)` + `UNIQUE(consumer_id, event_id)`. В Mongo — тот же pattern через compound unique index. Retention: cleanup через ShedLock-job раз в день, delete `processed_at < now() - 7 days` (events старше 7 дней в Rabbit уже не existуют).
- **SecurityIdorIT pattern:** параметризованный тест `@ParameterizedTest(name = "student A cannot read {0}")` с Stream provider из endpoint'ов. Подкладывать JWT student A, path с userId student B.
- **CSP report-uri:** `application/csp-report` — deprecated MIME, новый `application/reports+json` (Reporting API). Support обоих для compatibility.
- **Healthcheck curl/wget:** Eclipse Temurin JRE не имеет curl. Использовать `wget --quiet --tries=1 --spider` либо установить curl в Dockerfile.
- **Certbot deploy-hook:** `docker exec` из certbot контейнера требует mount `docker.sock` (security risk). Альтернатива — shared volume + nginx sidecar inotifywait, или cron `nginx -s reload` каждые 12h (simple).

## Вопросы к владельцу (при возникновении)

_пусто пока_

## 2026-04-25 — Группа 1 (pre-existing flaky tests)

**Все 3 теста уже зелёные** в текущем состоянии `dev` — checklist сработал с
устаревшей информацией из debt-report'а:

- `EventSchemaRefTest` — починен **M06 G9** (коммит `e0e1881`, добавлены
  `event_version`/`trace_id`/`source` в test payload'ы). Сейчас `trace_id`
  присутствует на строках 57 + 90, `_common.json#/$defs/traceId`
  проверяется в `commonJson_declaresExpectedDefs`. Локальный запуск —
  1 suite / 3 tests / 0 failures.
- `api-gateway:RateLimitIT` — `integrationTest` source set (в `test` SS его
  нет). Локально Testcontainers Redis + WireMock: 2 teста / 0 failures.
  Assertion-ы (`expectStatus().isEqualTo(TOO_MANY_REQUESTS)`,
  `Retry-After: "60"`, `problems/rate-limit-exceeded`) работают при
  текущем `replenishRate=5/burstCapacity=5/requestedTokens=1` (= 300
  req/min, но 5 burst подряд триггерят 6-й 429). Semantic-фикс в **G2**
  будет менять семантику на 5 req/min и требовать обновления этих
  assertion-ов (особенно `Retry-After`).
- `attendance:ExcuseEventContractIT` — тоже `integrationTest`. 2 теста / 0
  failures. `createExcuse_publishesRequestedEvent_matchingBotContract`
  проходит без duplicate excuse.

**Вывод:** 3 checklist item-а `[x]` без изменений кода. Зафиксировано
отдельным doc-коммитом без code-diff. Dev-feedback loop (цель G1) и так
работает.

**Surprise, зафиксированный для G2 executor'а:** при переделке rate-limit
на `replenishRate=1/burstCapacity=5/requestedTokens=12` нужно пересчитать
ожидаемый `Retry-After` в `RateLimitIT.sixthRequest_returns429` (с "60"
на "12" или что даст Spring Cloud Gateway из остатка токенов).
