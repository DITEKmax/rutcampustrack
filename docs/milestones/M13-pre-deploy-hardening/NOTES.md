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

## 2026-04-25 — Группа 2 (rate-limit semantics)

**Surprise #1 (executed G2):** owner-формула в NOTES.md:46
(`burstCapacity=5, requestedTokens=12`) **некорректна** — token-bucket
не может списать больше токенов, чем есть в бакете. При стартовом
бакете=5 первый же запрос (cost=12) → 429. `RateLimitIT` упал с
«expected 2xx but was 429».

**Решённая формула (универсальная для всех X req/min):**

```
replenishRate   = 1 tok/sec   (= 60 tok/min)
burstCapacity   = 60 tok      (= 1 мин запас)
requestedTokens = 60 / X
```

- 5 req/min → `rr=1, bc=60, rt=12` (5 × 12 = 60 tokens = burst)
- 10 req/min → `rr=1, bc=60, rt=6`
- 30 req/min → `rr=1, bc=60, rt=2`
- 1 req/min → `rr=1, bc=60, rt=60`

**Retry-After:** остаётся hardcoded `60` в
`RateLimitProblemDetailsFilter:73`. Не уточняем per-route — это upper
bound, клиент может ждать меньше (реально `60/X` сек). Описано в
`docs/api-rate-limits.md`.

**Проверка:** локально `./gradlew :services:api-gateway:integrationTest
--tests RateLimitIT` → 2/2 passing. Assertion-ы
(`Retry-After=60`, 6-й запрос = 429, проблема `type`) остались без
изменения (`RateLimitProblemDetailsFilter` не route-aware).

**Что не трогал:** generic DDoS-guard (600/600/1 для
academic/schedule/attendance/notification/push) — owner в debt-report'е
упомянул только 6 auth routes. Оставил legacy-конфигурацию с пометкой
в docs «формально не по формуле M13 G2, но выдерживается за счёт
prefill=burst=600 + restore 10 tok/sec».

**Что включил в scope расширенно:** `attendance-excuse-upload`
(multipart 25MB, 5 req/min per-user) — уже был в `5/5/1`, теперь
переделан в `1/60/12` по единой формуле, чтобы не держать два стиля
semantics в одном yml.

## 2026-04-25 — Группа 3 (pagination global cap)

**Реализация:** `EnvironmentPostProcessor` вместо добавления yml в
`shared-web` (starter не поддерживает nested application.yml как
дефолты без дополнительного hack'а). `PageableDefaultsPostProcessor`
добавляет lowest-priority `MapPropertySource` с
`spring.data.web.pageable.max-page-size=100`. Per-service override
в `application.yml` перебивает дефолт естественным образом.

**Не тронул:** schedule-app `max-page-size: 200` (из v3.0 phase
11 RESEARCH — week-range query ~640 lessons/semester; 200 покрывает
большинство практических запросов). Отдельно задокументирован override
в `docs/api-pagination.md` как legitimate per-service exception.

**Удалил:** `max-page-size: 100` из `notification-app/application.yml`
(дубль с shared-web default после M13 G3). `default-page-size: 20` в
notification-app оставил — это осмысленный local override для UX
feed'а.

**IT-тесты:** 3 × `PaginationCapIT` в academic/schedule/attendance.
Каждый вызывает `GET …?size=1000000` и проверяет `page.size ≤ {cap}`.
Все зелёные. Attendance/academic — cap=100 (shared default),
schedule — cap=200 (local override).

**Что не покрыл IT:** auth-service и notification-web — у auth
практически нет пагинации (OTP/login/refresh не Page), а у
notification-web уже был hot-patch `max-page-size=100` в M10 G9 H2,
который M13 перевёл в shared default. Если нужны IT на
`/api/notifications?size=…` — добавлю по запросу.

## 2026-04-25 — Группа 4 (`/auth/refresh-body` removal)

**Usage audit (G4.1):** `grep -rn refresh-body` по `frontends/` нашёл:
- `web-panel/src/app/api/generated/auth.types.ts` — generated types.
- `pwa/src/api/generated/auth.types.ts` — generated types.
- `mini-app/src/shared/lib/axios.ts:15` — однострочный **комментарий**,
  не runtime.

Ни одного `fetch('/auth/refresh-body')` или `refreshBody()` вызова в
app-code. Значит миграция фронта не требуется, только regenerate.

**Backend удалено:**
- `AuthApi.refreshBody(...)` — метод + `@PostMapping("/refresh-body")`.
- `AuthController.refreshBody(...)` + константа `REFRESH_BODY_SUNSET`
  (`Mon, 01 Jun 2026 00:00:00 GMT`).
- `SecurityConfig.permitAll` убран `/auth/refresh-body`.
- Gateway `application.yml` `auth-refresh` route — убран `,/api/auth/refresh-body` из Path predicate.
- Gateway `JwtAuthenticationFilter.PUBLIC_PATHS` — убран `/api/auth/refresh-body`.
- Тесты `AuthIT.refreshBody_withValidToken_returnsDeprecationHeader`
  и `TmaIT.refreshBody_withValidToken_returnsNewPair` +
  `TmaIT.refreshBody_withInvalidToken_returns401` удалены. Unused
  import `RefreshRequest` в AuthIT/TmaIT — удалён.

**Оставлено (используется cookie-flow):**
- `ru.rutcampustrack.auth.dto.RefreshRequest` DTO — всё ещё нужен:
  `/auth/refresh` обёртывает cookie в `new RefreshRequest(refreshCookie)`,
  `/auth/logout` принимает legacy body `RefreshRequest` (для TMA logout).
- Owner в NEXT-SESSION.md:87 явно сказал: «refresh-body удаляем; mock
  data, пользователей попросим перезайти» → не удалял DTO преждевременно.

**Docs обновлены:**
- `docs/auth-flow.md` — удалён раздел `POST /auth/refresh-body —
  DEPRECATED`.
- `docs/architecture.md` — удалён подпункт про refresh-body legacy
  (заменён на однострочное упоминание что endpoint удалён в M13 G4).
- `docs/openapi/auth.json` — regenerate через OpenApiSnapshotIT
  c `-Popenapi.snapshot.update=true`. Путь `/auth/refresh-body`
  удалён из spec.
- `frontends/{web-panel,pwa}/src/*/generated/auth.types.ts` —
  regenerate `npm run generate:types:offline` из обновлённого
  `docs/openapi/auth.json`.

**Комментарий в mini-app axios.ts:15** — поправлен на «re-auth via
initData, NOT cookie /auth/refresh» (была отсылка к refresh-body).

**Проверка:** локально
`./gradlew :services:auth-service:auth-app:integrationTest` (AuthIT,
TmaIT, OpenApiSnapshotIT) + `:services:api-gateway:integrationTest`
(все RL/security IT) — BUILD SUCCESSFUL, 0 failures.

## 2026-04-25 — Группа 5 (`InvalidParam` deprecated alias migration)

**Surprise #1:** исходная формулировка checklist'а («удалить
`fieldErrors` из ErrorResponse», «убрать fallback на `fieldErrors` в
frontend») была **инвертирована** относительно реального замысла
v0.0.0-debt.md:108 и M11 G0 history.

**Реальное состояние до M13 G5:**
- Backend canonical (M11 G0): `ErrorResponse.fieldErrors: List<FieldError>`.
- `InvalidParam.java` — **legacy record** в shared-web-api, помечен
  deprecated в M11 (migration path → v0.1), **не используется** ни в
  одном production-коде (только в `ErrorResponseTest.invalidParamAllFields`).
- Frontend `invalidParams: InvalidParam[]` — **TS-level имя** для
  RFC 9457 compliance, coercer читает backend `fieldErrors` и переименовывает.
- Frontend coercer имел **pre-M11 fallback** — читал также
  `raw.invalidParams` (на случай если backend когда-то его отдавал).

**Выполнено (правильное направление):**

Backend:
- Удалён `services/shared/shared-web-api/.../InvalidParam.java`.
- Удалён `invalidParamAllFields` test в `ErrorResponseTest.java`.
- Обновлены comments: `FieldError.java`, `ErrorResponse.java`,
  `shared-web/build.gradle.kts` — M13 G5 alias removal записан.
- Фикс misleading `@DisplayName` в `NotificationErrorHandlingIT`:
  «invalidParams[]» → «fieldErrors[]».

Frontend (PWA + web-panel):
- `RawErrorBody.invalidParams?` удалён — остался только `fieldErrors?`.
- `coerceInvalidParams(raw)` читает только `raw.fieldErrors ?? []`
  (раньше `raw.invalidParams ?? raw.fieldErrors ?? []`).
- Обновлены comments в `pwa/src/api/problemDetails.ts` и
  `web-panel/src/app/core/errors/problem-details.ts`.
- Удалены unit-tests «принимает post-M11 invalidParams shape»
  (PWA problemDetails.test.ts и web-panel problem-details.spec.ts).

**Проверка:**
- `shared-web-api:build` — SUCCESSFUL.
- PWA `npm test --run` — **166/166** passing.
- web-panel `npm test` — **476/476** passing (67 test files).
- OpenAPI snapshots regenerate через `OpenApiSnapshotIT -Popenapi.snapshot.update=true`
  в 5 сервисах — **0 diff** (InvalidParam schema в spec'ах не было).
- frontend types regenerate через `npm run generate:types:offline` —
  **0 diff**.

**Итог:** чистое deprecated-alias удаление без runtime-impact. 1 java-класс,
1 unit-test, 2 TS-fallback'а и 2 TS-unit-testа удалены; ErrorResponse
canonical format не менялся.

## 2026-04-25 — Группа 6 (Mongo TTL + compound indexes audit)

**Terminology divergence (checklist vs code):** checklist говорит про
compound `(user_id, created_at)`, но **реальное поле** в `NotificationHistoryDocument`
называется `sent_at` (M10 schema). Наш IT проверяет именно
`{user_id: 1, sent_at: -1}`. При общении с владельцем / ops эту
замену «created_at → sent_at» упоминать.

**M10 G9 hot-patch был good enough для создания индексов, но не для
fail-fast:** если в будущей версии Spring Data Mongo / Mongo driver
появится regression в silent-no-op (как в Mongo 6→7 transition),
мы бы опять хреново нашли это только через COLLSCAN-алёрт продакшена.
M13 G6 `verifyIndexes()` — страховка: после `ensureIndex` читаем
`getIndexInfo()` + `listIndexes().expireAfterSeconds` и бросаем
`IllegalStateException` если что-то не совпадает.

**Реализация:**

- `NotificationHistoryMongoConfig`:
  - Вынесены именованные константы `IDX_USER_SENT_DESC/IDX_USER_READ/IDX_TTL_SENT_AT`
    (не magic strings) — используются и в config, и в IT.
  - `verifyIndexes(IndexOperations)` вызывается в конце `initIndexes()`.
    Читает `ops.getIndexInfo()` (имена) + `collection.listIndexes()`
    (для `expireAfterSeconds` — Spring Data `IndexInfo` не мапит этот
    атрибут в доступный accessor).
  - Если хотя бы один из 3 missing или TTL не совпадает с
    `ttlDays * 86400` — `log.error` + `throw new IllegalStateException`.
    В `ApplicationReadyEvent` handler exception пробрасывается и
    Spring ApplicationContext падает с exit code → docker restart
    policy перезапускает контейнер (pod будет в CrashLoopBackOff при
    реальной проблеме).

**IT (`NotificationMongoIndexesIT`):**

- Загружает полный Spring context через `ContainerTestBase` (Mongo
  7 Testcontainer). Сам `initIndexes()` срабатывает из
  `ApplicationReadyEvent` — к моменту теста индексы уже должны быть.
- Проверки: 3 custom + `_id_` (= 4 total), `expireAfterSeconds=2592000`
  (30 дней), compound key `{user_id:1, sent_at:-1}` с правильными
  направлениями.
- `@MockitoBean PushService` необходим — иначе VAPID public-key с
  test stub'ом парсится и падает при создании `webPushService` bean'а.

**Runbook `docs/runbooks/mongo-indexes-verify.md`:** 4 раздела —
automatic startup check, manual `db.getIndexes()`, fix рецепты
(рестарт / manual `createIndex` / `collMod TTL`), performance
`explain().winningPlan` (IXSCAN vs COLLSCAN). TTL-rotation рецепт
через `collMod` (Mongo ≥ 5.0) avoid drop+recreate downtime.

**Проверка:**
- `notification-app:compileJava` — SUCCESSFUL.
- `NotificationMongoIndexesIT` — 1/1 passing.
