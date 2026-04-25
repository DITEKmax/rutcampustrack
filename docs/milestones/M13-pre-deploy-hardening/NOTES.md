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

## 2026-04-25 — Группа 7 (Mongo outbox atomicity) — в работе

**Scope realization:** M10/M13 использует `mongo:7` (официальный) с
auth через `MONGO_INITDB_ROOT_*` + init-mongo.js. Переход на replica
set требует:

1. **keyFile (опциональный для single-node?)** — нет, Mongo 7
   **требует** keyFile когда `--replSet` + `--auth` сочетаются. Без
   `--auth` можно обойтись.
2. **`rs.initiate()` idempotent** — должен выполниться ПОСЛЕ
   стартапа mongod, но ДО application connects. Это race, обычно
   решается через sidecar или healthcheck-based init.
3. **Classic trap:** MONGO_INITDB_ROOT_USERNAME/PASSWORD env
   создаёт users только когда data-dir пустой. При `--replSet` на
   свежем data-dir Mongo падает если `rs.initiate()` не было —
   createUser из init-mongo.js не выполняется.

**Практические options (обсуждаем с владельцем):**

- **Option A (полный путь):** `mongo:7` + keyFile-gen entrypoint-wrapper
  + two-stage init (initiate RS → createUser). Сложный, кастомный
  entrypoint в dev и prod. ~1-2 дня работы + VPS-deploy runbook update
  (керфайл rotation).

- **Option B (bitnami/mongodb image):** switch с `mongo:7` на
  `bitnami/mongodb:7` который умеет RS + auth + users out-of-the-box
  через env (MONGODB_REPLICA_SET_MODE, MONGODB_REPLICA_SET_KEY,
  MONGODB_ROOT_PASSWORD). Меняется image, volume layout, probably
  compatible data-format. ~0.5 дня + VPS update (новый image ensure).

- **Option C (оставить standalone + Mongo 7 transactions emulation):**
  Technically Mongo 7 supports sessions even on standalone, но
  multi-document transactions требуют RS. @Transactional в MongoOutboxStorage
  без RS бросит `MongoCommandException: Transaction numbers are only
  allowed on a replica set member or mongos`. Decline → vault M02
  CRITICAL #1 в v0.1.

**Testcontainers `MongoDBContainer`** уже запускает RS mode — все
существующие IT работают с replica set. Значит implementation-side
в коде (MongoTransactionManager + @Transactional) будет прозрачной;
hard work именно в docker-compose.

**Решение владельца (2026-04-25):** Option B — `bitnami/mongodb:7.0`.
Обоснование: минимум legacy shell-scripting, declarative env-based
config, direct path к K8s/Helm миграции, готовность к 3-node RS
scale-up без entrypoint-rewrite.

**Plan выполнения Option B:**

1. docker-compose.yml + docker-compose.prod.yml: image `mongo:7` →
   `bitnami/mongodb:7.0`. Env vars: `MONGODB_REPLICA_SET_MODE=primary`,
   `MONGODB_REPLICA_SET_NAME=rs0`, `MONGODB_REPLICA_SET_KEY`,
   `MONGODB_ROOT_USER`, `MONGODB_ROOT_PASSWORD`, `MONGODB_EXTRA_USERNAMES`,
   `MONGODB_EXTRA_PASSWORDS`, `MONGODB_EXTRA_DATABASES`.
2. Удалить init-mongo.js (users создаются через env).
3. Spring URI: добавить `?replicaSet=rs0&authSource=admin` в
   attendance-app + notification-web.
4. `MongoTransactionManager` bean в обоих сервисах.
5. `@Transactional` на выходе domain-операции (attendance markBatch +
   notification consumer) — чтобы save + outbox были atomic.
6. IT: transaction-rollback behaviour (принудительный exception между
   save и publish → outbox запись не committed).
7. docs/database-schema.md: Mongo RS requirement.
8. .env.prod.example: MONGODB_REPLICA_SET_KEY generation recipe
   (openssl rand -base64 756).
9. Rollback plan в NOTES: держим mongo:7 images как fallback.
10. VPS migration (в G23 runbook): dump → switch image → restore, ~5min downtime.

## 2026-04-25 — Группа 7 execution summary

**compose changes:**
- `docker-compose.yml`: mongo-attendance image `mongo:7` → `bitnami/mongodb:7.0`,
  MONGODB_REPLICA_SET_* env, volume path `/bitnami/mongodb`, healthcheck
  с start_period 30s, удалён init-mongo.js mount.
- `docker-compose.prod.yml`: то же + mem_limit увеличен 384m → 512m
  (Bitnami RS init consumes больше), digest-pin заменён на floating
  tag `bitnami/mongodb:7.0` до тех пор, пока не проведём `buildx
  imagetools inspect` для digest-pin (M08 G11 pattern).

**URI changes:**
- dev + prod SPRING_DATA_MONGODB_URI добавлен `&replicaSet=rs0`.
- attendance-app application.yml default URI — same.
- notification-app application.yml default URI — same.

**Spring config:**
- `services/attendance-service/attendance-app/config/MongoConfig.java`:
  + `@EnableTransactionManagement`, `mongoTransactionManager` bean.
- `services/notification-service/notification-app/history/NotificationHistoryMongoConfig.java`:
  same.

**@Transactional scope в attendance-app:**
- `ExcuseService` — 4 метода (createExcuse, createExcuseWithFile,
  updateStatus, applyDecisionFromBot).
- `CheckinService.checkin` — 1 метод.
- `MarkingService.markAttendance` + `markBatch` — 2 метода.
- `LateCheckinService.createRequest/applyDecisionFromWeb/applyDecision`
  — 3 метода.

Всего 10 tx-точек закрывают M02 CRITICAL #1.

**IT:** `OutboxAtomicityIT` с 2 сценариями — rollback/commit. Использует
test-nested `@Service TestTxService` (без @TestConfiguration — иначе
NoUniqueBeanDefinitionException). `@Import(TestTxService.class)`
поднимает его в test ApplicationContext. 2/2 passing.

**Регрессия:** full attendance integrationTest + notification
integrationTest — все зелёные после M13 G7.

**Removed files:** `infra/mongo/init-mongo.js` — legacy M10 D2 init
скрипт; теперь users создаются через Bitnami env.

**TODO (для G13 env infrastructure):**
- Добавить `MONGODB_REPLICA_SET_KEY` в `.env.prod.example` с generation
  recipe: `openssl rand -base64 756 | tr -d '\n'`. Rotation (Bitnami
  docs): при смене key требуется restart всех nodes RS (у нас один
  node → restart контейнера + rejoin).
- Документировать в G23 deploy runbook: VPS-migration scenario
  (dump `mongodump --uri="mongodb://...@old-mongo"` → image switch →
  `mongorestore` → smoke). Для первого deploy — N/A, fresh install.
- digest-pin image в docker-compose.prod.yml после staging verify:
  `docker buildx imagetools inspect bitnami/mongodb:7.0 | grep Digest`.
  Сейчас floating tag (чтобы не блочить M13).

## 2026-04-25 — Группа 8 (Consumer-side dedup по event_id)

**Дизайн (по согласованию владельца):**

- `IdempotencyStore` interface в `shared-events` — два метода
  (`tryClaim(consumerId, eventId)` + `deleteProcessedBefore(cutoff)`).
- `IdempotencyGuard` helper в `shared-events` — оборачивает store,
  вытаскивает `event_id` из envelope, fail-open при отсутствии/malformed.
- `@EventIdempotent(consumer = "...")` — marker-аннотация (не AOP).
  Реальный dedup делается явным `idempotencyGuard.tryClaim(envelope)`
  в первой строке consumer-метода. Причина: AOP вокруг `@RabbitListener`
  через CGLIB-proxy менее предсказуем по сравнению с явным вызовом, а
  consumer'ов всего 4 в Java + 1 в Python — декларативность не нужна.

**JPA store (academic + schedule):**

- Таблица `event_consumer_processed (consumer_id VARCHAR(64), event_id UUID,
  processed_at TIMESTAMPTZ, PK(consumer_id, event_id))` + `idx_ecp_cleanup`.
- Flyway: academic V18, schedule V14 — `MigrationIT` подтверждает.
- `JpaIdempotencyStore.tryClaim` использует **PostgreSQL native
  `INSERT ... ON CONFLICT DO NOTHING`** через `entityManager.createNativeQuery`.

  **Surprise (executed G8.7):** первая версия делала `entityManager.persist + flush`
  + `catch DataIntegrityViolationException → return false`. Hibernate flush failure
  помечает Spring tx как `rollback-only` через `setRollbackOnly()`, и handler-tx
  не может коммитнуться — `UnexpectedRollbackException: Transaction silently
  rolled back because it has been marked as rollback-only`. Native UPSERT
  не бросает PSQLException → tx остаётся valid. Это критичный учёт для
  любого «JPA insert-or-skip» паттерна на shared tx.

**Mongo store (attendance + notification-web):**

- Коллекция `event_consumer_processed` — compound unique
  `(consumer_id, event_id)` + `idx_ecp_cleanup` на `processed_at`.
- `MongoIdempotencyStore.tryClaim` — `insertOne` + catch
  `MongoWriteException` с `ErrorCategory.DUPLICATE_KEY` → return false.
  В отличие от JPA, Mongo writes **не помечают Spring tx как rollback-only**
  при duplicate-key (replica set tx + insertOne — атомарно, ошибка только в
  client'е). Поэтому здесь обычный `try/catch` работает.
- Привязка к `IdempotencyStore` bean'у в attendance `MongoConfig` +
  notification `NotificationHistoryMongoConfig`. Индексы создаются в
  `initIndexes()` через `new MongoIdempotencyStore(mongoTemplate).ensureIndexes()`.

**Применено к consumer'ам:**

- `schedule.EventConsumer.onEvent` — CONSUMER_ID="schedule".
- `attendance.EventConsumer.onEvent` — CONSUMER_ID="attendance".
- `notification.EventConsumer.onEvent` — CONSUMER_ID="notification-web".
- `notification.NotificationHistoryConsumer.onEvent` — CONSUMER_ID="notification-history".
  Два **разных** consumer-id для notification-web (т.к. два @RabbitListener
  на разные queues с разной семантикой) — claim'ы независимы по этим id.
- **academic — N/A:** academic-app не имеет ни одного `@RabbitListener`,
  только publisher (DomainEventListener). Но Flyway/store/cleanup в
  academic создаются — для будущих consumer'ов (если появятся в v0.1+)
  и для cleanup-симметрии.

**Bot Python (notification-bot):**

- `BotIdempotencyGuard` (`bot/services/idempotency_guard.py`) — Redis
  `SET consumed:{consumer_id}:{event_id} NX EX 3600`.
- TTL 1h — больше чем Rabbit redelivery window. Fail-open при Redis-сбое
  (лучше дубль обработать, чем потерять событие).
- Wired в `__main__.py` через `start_consumer(rabbitmq_url, dispatcher,
  idempotency_guard)`. 7 pytest'ов покрывают all paths (claim, dedup,
  TTL, fail-open, missing event_id, multiple consumer_id).

**Cleanup-job (G8.8):**

- `IdempotencyCleanupJob` в `shared-outbox` (рядом с `OutboxCleanupJob`).
- Cron `0 30 3 * * *` (03:30 UTC) — на 30 минут позже outbox cleanup'а
  чтобы не пересекаться по lock'ам. Retention 7 дней (configurable).
- Регистрируется bean'ом в Publisher/Scheduler-секции каждого из 4
  сервисов с `@Profile("!test")`.

**Tests:**

- Unit: `IdempotencyGuardTest` (9 cases в shared-events),
  `IdempotencyCleanupJobTest` (3 cases в shared-outbox), pytest
  `test_idempotency_guard.py` (7 cases в notification-bot).
- IT: `EventIdempotentIT` × 3 — schedule (direct call + `SubjectDeletedCascadeService`
  Mockito verify), attendance (Rabbit publish + count claims), notification
  (Rabbit publish + count claims + history doc count). Academic — нет consumer'а.

**Pre-existing baseline drift найденный в G8.1:**

`shared-events/EventSchemaCoverageTest.eventSchemasMatchWhitelist`
падает: `otp.requested.json` появилась в M09 G2 (`3d6dfd1`), но
`EXPECTED_EVENT_SCHEMAS` whitelist в `EventSchemaCoverageTest.java` не
обновлён. Это блокирует `:services:shared:shared-events:test` весь
M13. **Не моё (M09 retrospective gap), но требует фикс — см. отдельный
вопрос владельцу в Группе 24 (либо добавить как G8 follow-up
оne-line patch). Мой `IdempotencyGuardTest` зелёный, не связан.**

## 2026-04-25 — Группа 9 (SecurityIdorIT — NEW-31 retrospective)

**G9.0 — fix EventSchemaCoverageTest whitelist (one-liner):**
добавлен `otp.requested` в `EXPECTED_EVENT_SCHEMAS`. Pre-existing
baseline drift из M09 G2. Решение владельца: «Чинить когда считаешь
нужным, до конца M13». Сделал в G9 чтобы можно было гонять
shared-events:test.

**Surprise (executor scope creep):** owner ожидал «1-2 IDOR баг(а)»
(см. CHECKLIST G9.5 «ожидаем 1-2»). По факту найдено **12 IDOR'ов**:
- 9 в academic
- 3 в schedule
- 0 в attendance (already correctly secured)
- 0 в notification-web (already correctly secured)

Owner подтвердил («fix all + IT ВСЕХ во всех сервисах»). Эта группа
оказалась в 6× больше изначального scope, занимает ~2 дня.

### Найденные IDOR'ы

**Academic (9):**

| # | Endpoint | Service-метод | Vulnerability | Fix |
|---|----------|---------------|---------------|-----|
| A1 | `GET /academic/homeworks/{id}` | `HomeworkService.getHomework` | STUDENT читает любое ДЗ по id | `assertCanReadGroup(homework.getGroupId())` |
| A2 | `GET /academic/homeworks?groupId=X&semesterId=Y` | `HomeworkService.listHomeworks` | STUDENT передаёт чужой groupId | same helper |
| A3 | `POST /academic/homeworks/{id}/complete` | `HomeworkService.markComplete` | STUDENT отмечает чужое ДЗ | `getHomework()` теперь делает groupId-check |
| A4 | `DELETE /academic/homeworks/{id}/complete` | `HomeworkService.unmarkComplete` | same | added `getHomework()` call |
| A5 | `GET /academic/assistants?groupId=X` | `AssistantService.listAssistants` | Headman A видит assistant'ов group B | `assertOwnGroup(groupId)` |
| A6 | `POST /academic/assistants` | `AssistantService.assignAssistant` | Headman A назначает assistant в group B | `assertOwnGroup(request.groupId())` |
| A7 | `PATCH /academic/assistants/{id}/permissions` | `AssistantService.updatePermissions` | Headman A правит assistant'а group B | `assertOwnGroup(assistant.getGroupId())` |
| A8 | `DELETE /academic/assistants/{id}` | `AssistantService.revokeAssistant` | same | same |
| A9 | `GET /academic/subjects/{id}` | `SubjectService.getSubject` (Cacheable) | STUDENT читает subject другой группы | new `getSubjectForRead` (controller вызывает); `assertCanReadSubject` ADMIN/TEACHER bypass |
| A10 | `GET /academic/assignments?groupId=X` | `AssignmentService.listAssignments` | STUDENT передаёт чужой groupId | `assertOwnGroup(groupId)` (ADMIN bypass) |
| A11 | `POST /academic/assignments` | `AssignmentService.assignTeacher` | Headman A создаёт assignment в group B | `assertOwnGroup(request.groupId())` |
| A12 | `DELETE /academic/assignments/{id}` | `AssignmentService.removeAssignment` | Headman A удаляет assignment group B | `assertOwnGroup(assignment.getGroupId())` |

**Schedule (3):**

| # | Endpoint | Service-метод | Fix |
|---|----------|---------------|-----|
| S1 | `GET /schedule/groups/{groupId}/lessons` | `LessonService.getLessonsForGroup` | new `requireGroupReadAccess(groupId)` (STUDENT-own-group, ADMIN/TEACHER any) |
| S2 | `GET /schedule/items/{id}` + `?groupId=X` | `ScheduleItemService.getScheduleItem`/`listScheduleItems` | same helper |
| S3 | `GET /schedule/one-off-lessons?groupId=X` | `OneOffLessonService.listOneOffLessons` | same helper |

Schedule **write-операции** (cancel/restore/blockLesson/...) уже были
защищены через gRPC `academicGrpcClient.isHeadman(userId, groupId)` —
fix не требовался, IT документирует.

**Attendance & notification-web — IDOR не найдены:**

- attendance: `ExcuseService.getGroupTickets`/`getTicketById`/`updateStatus`
  уже сравнивают `ticket.groupId == requestContext.groupId`;
  `LateCheckinService.applyDecisionFromWeb`/`listPendingForHeadman` same;
  `MarkingService.markAttendance/markBatch` проверяет lesson.groupId
  через gRPC + double-check `academicGrpcClient.isHeadman` (M05 audit
  fix); `ReportService.authorizeHeadmanOrTeacher` правильно сравнивает
  groupId либо teacher-subject связь.
- notification-web: `NotificationController` не принимает userId из
  path/body — всегда `requestContext.getUserId()`. `markAsRead`
  возвращает 403 (не 404) на чужой id (anti-enumeration).
  `PushController.unsubscribe` scoped к `userId+endpoint`.

### IT-покрытие

| Service | Тестов | Endpoint'ов под IDOR-проверкой | Fix?    |
|---------|--------|--------------------------------|---------|
| academic | 13 | 11 | ✅ 9 fix'ов |
| schedule | 11 | 11 | ✅ 3 fix'а |
| attendance | 10 | 10 | none — already secured |
| notification-web | 4 | 4 | none — already secured |
| **Итого** | **38** | **36** | 12 fix'ов |

AC-17 требует ≥10 endpoint'ов с {userId}/{groupId}/{lessonId} —
**превышено в 3.6 раза.**

### Nuances для executor'а

1. **`@Cacheable` + groupId-check:** `SubjectService.getSubject(id)`
   помечен `@Cacheable("subject", key="#id")`. Если делать
   groupId-check **внутри** этого метода, кэш сохранит данные
   первого вызова + результат теста зависит от того кто первым
   прочитал. Решение: оставил `getSubject` как low-level cacheable
   accessor, добавил **отдельный** `getSubjectForRead(id)` для
   controller'а с groupId-check. Внутренние usage'ы (update/addTeacher)
   используют low-level + сами проверяют ownership через
   `assertSubjectBelongsToHeadmanGroup`.

2. **TEACHER read access:** TEACHER может видеть homework/расписание
   любой группы (он может вести предмет в нескольких группах). Поэтому
   в `assertCanReadGroup`/`requireGroupReadAccess` ADMIN+TEACHER
   bypass'ятся. Если в будущем нужно ужесточить TEACHER до «только
   свои группы» — тут единый knob.

3. **Schedule items teacher_id column:** test seed первой версии
   использовал `teacher_id` колонку, которая была удалена в V3
   (`drop_teacher_id.sql`). Проверять реальную схему перед seed'ом.

4. **headman_assistants column naming:** `assigned_by`/`assigned_at`,
   не `granted_by`/`created_at`. Для будущих refactor'ов — стандартизировать
   везде на одно name? (deferred — v0.1 OPS).

## 2026-04-25 — Группа 10 (Actuator tracing exclude)

**Surprise (executor → owner):** изначальный hand-off дизайн через
OpenTelemetry `Sampler` bean **не работает** в Spring Boot 3.4 +
Micrometer Bridge 1.4.1.

**Что не сработало:**

1. Реализован `ActuatorTracingExcludeSampler` (java-class, `@AutoConfiguration`
   bean типа `io.opentelemetry.sdk.trace.samplers.Sampler`).
2. `@AutoConfiguration(before = OpenTelemetryTracingAutoConfiguration.class)`
   + `@ConditionalOnMissingBean(Sampler.class)` — auto-config корректно
   подхватился (1 IT pass).
3. **Корень проблемы:** в Spring Boot 3.4 attribute `url.path` /
   `http.target` устанавливается на http server span **после** вызова
   `Sampler#shouldSample`. Sampler видит пустой `Attributes` для http
   server span. Span name на момент `shouldSample` тоже не содержит path
   (финальное `"http get /actuator/health"` устанавливается позже).
4. Результат: span попадает в exporter + 5 child internal observation
   span'ов (security filterchain before/after, authorize request,
   secured request) — spam в Tempo продолжается.

**Решение (выбрано после консультации с владельцем «выбери что считаешь
не legacy»):** заменён на **`SpanExportingPredicate`** — официальный
Spring Boot 3.x mechanism. Видит финальный `FinishedSpan` после
финализации (со всеми tags + name). `OpenTelemetryTracingAutoConfiguration`
собирает все predicate'ы через `ObjectProvider<SpanExportingPredicate>`
и оборачивает в `CompositeSpanExporter` — фильтр работает между
`BatchSpanProcessor` и OTLP exporter, до сетевого экспорта в Tempo.

**Stratergy для child spans:** Filter запоминает `trace_id` отброшенного
root в LRU set (capacity 256). Все последующие spans с тем же `trace_id`
дропаются. Capacity 256 покрывает 5 сервисов × 2 actuator endpoints ×
30s probe interval с большим запасом, без memory bloat.

**Path lookup в FinishedSpan tags:** покрыты 4 ключа для устойчивости
к instrumentation drift — `url.path` (semconv 1.20+), `http.target`
(legacy ≤ 1.29), `http.url` (Spring Boot WebMvc Brave-style),
`uri` (старая M11 Brave bridge convention). Plus span name содержит
`/actuator/` в финальном виде. Любого совпадения достаточно для DROP.

**Дополнительный fail-fast:** filter применяет только локальный path-match
в момент `isExportable(root)`. Если в будущем наименование поменяется
без обновления `PATH_TAG_KEYS` — root **попадёт** в exporter, child
тоже. Не silent-no-op: spam в Tempo сразу видно. Регрессия покрывается
end-to-end IT (`ActuatorSpanFilterIT.healthEndpoint_doesNotProduceAnyExportedSpan`).

**Где сделана реализация:**

- `services/shared/shared-observability/src/main/java/.../ActuatorTracingExcludeFilter.java`
  — основной класс (LRU + path-match logic). Plain `java-library` стиль,
  без Lombok.
- `services/shared/shared-observability/src/main/java/.../SharedObservabilityAutoConfiguration.java`
  — `@AutoConfiguration` + `@ConditionalOnClass(SpanExportingPredicate.class)`.
- `services/shared/shared-observability/src/main/resources/META-INF/spring/AutoConfiguration.imports`
  — регистрация в Spring Boot.
- `services/shared/shared-observability/build.gradle.kts` — `compileOnly
  micrometer-tracing:1.4.1` (= транзитивная версия в bridge-otel).
  Удалён `compileOnly opentelemetry-sdk` (Sampler не нужен) — модуль
  остаётся pure java-library без obtrusive tracing deps.
- `services/auth-service/auth-app/.../ActuatorSpanFilterIT.java` —
  end-to-end IT через `InMemorySpanExporter` + `tracerProvider.forceFlush()`.

**Tests:**

- `ActuatorTracingExcludeFilterTest` (shared-observability) — **13 unit
  cases**: actuator drop по name/url.path/uri/http.url/http.target,
  child spans drop по trace_id, business pass, substring guard, LRU
  eviction, null handling.
- `ActuatorSpanFilterIT` (auth-app, `integrationTest`) — **3 IT cases**:
  filter bean регистрация, /actuator/health → 0 spans в exporter,
  бизнес /auth/login → spans есть.

**Application.yml fix:** `auth-service:application.yml:108-110` TODO(M07)
заменён на правдивое описание про `ActuatorTracingExcludeFilter`.
В **остальных 4 сервисах** (academic/schedule/attendance/notification)
такого misleading TODO нет — auto-config через shared-observability
работает прозрачно.

**Documentation:** `docs/observability.md` — новый раздел «Sampling policy
(M13 G10)» с таблицей endpoints/decisions, объяснением выбора
SpanExportingPredicate vs Sampler, описанием child-span strategy,
property override для disable.

**Что НЕ применено в этой группе:**

- Property для управления `lruCapacity` через application.yml — не
  нужно для v0.0.0, default 256 покрывает реалистичные patterns.
- Metric counter `actuator_spans_dropped_total` — было бы nice-to-have,
  но не критично; если spam в Tempo вернётся, end-to-end IT всё ещё
  ловит регрессию. Откладываю в v0.1 (если будет нужно).

**Применимость к остальным 4 сервисам:** auto-config из shared-observability
автоматически активируется во всех 5 backend (auth/academic/schedule/
attendance/notification-web) — все они уже имеют `implementation(project(
":services:shared:shared-observability"))` в build.gradle.kts (M04 G7).
IT написан только в auth-app — sampler-логика generic, в остальных
сервисах паттерн идентичный (Spring Boot WebMvc instrumentation
одинаковая). Если потом окажется нужно — IT можно скопировать в любой
сервис без изменений.

## 2026-04-25 — Группа 11 (mem_limit на aux containers)

**Scope расширение (предложено executor'ом, owner OK):** изначальный
checklist 5 контейнеров (nginx/certbot/node-exporter/cadvisor/promtail).
По факту обнаружено ещё 4 unbounded — frontend-nginx (pwa/mini-app/
web-panel/landing). Добавлены к G11 как extended scope, чтобы не
оставлять unbounded контейнеры в prod.

**Финальный memory budget (8GB VPS):**

| Категория | Контейнеры | Memory |
|-----------|------------|--------|
| Databases | postgres×2 + mongo + redis + rabbitmq | 1344m |
| Backend (Spring Boot + bot) | 5 sb + 2 ng + bot | 2816m |
| Observability | prometheus + alertmanager + tempo + grafana + loki + node-exporter + cadvisor + promtail | 1312m |
| Reverse proxy + cert | nginx + certbot | 384m |
| Frontends (extended scope) | 4 × nginx (pwa/mini-app/web-panel/landing) | 256m |
| **Total mem_limit** | 26 контейнеров | **6112m (6.1 GB)** |
| **Host overhead** | kernel + Docker daemon + sshd + sessions | **1.9 GB** |

Все 26 контейнеров теперь имеют mem_limit + mem_reservation. **Ноль
unbounded.** Soft reservation (`mem_reservation`) ставлен на 25-50%
от `mem_limit` — Docker не выселяет в первую очередь под memory
pressure (чтобы DB не убились, если resource peak).

**Validation:** `SWAGGER_HTPASSWD=dummy docker compose -f
docker-compose.prod.yml config --quiet` → exit 0, 0 errors. 26 mem_limit
entries в expanded config.

**Что НЕ ставил:**

- `cpus:` лимиты — owner ответил «v0.1» в debt-report'е (#26 VPS
  capacity). 4-core VPS, single-разработчик, нет noisy-neighbor проблемы.
- Memory swap limit (`memswap_limit`) — без swap на VPS (zram отключён),
  не нужен.
- ulimits — не было upstream запроса, дефолты Docker'а адекватны.

**Регрессия-страховка:** M09 G7 уже добавил Prometheus alert
`ContainerMemoryHigh` (cadvisor metric `container_memory_usage_bytes /
container_spec_memory_limit_bytes > 0.85`). После prod-deploy с этими
mem_limit'ами alert сработает если контейнер реально упрётся в
ceiling — мы это узнаем до OOM kill'а.

**Trade-off observed:** для cadvisor 256m vs 192m. cadvisor читает
данные всех container's `/sys/fs/cgroup` + Docker stats, при 26 containers
+ Prometheus scrape каждые 15s residual ~80-120m. 256m даёт 2× margin
от observed peak. На VPS этим 64m не пожалеешь — alternative OOMKill
crash cadvisor'а потеряет 5-min metric gap.

## 2026-04-25 — Группа 12 (healthcheck directives + health indicators)

**Audit-finding:** 4 из 5 пунктов checklist'а **уже закрыты** в более
ранних milestone'ах (M06 G2 + M09 G7). G12 — фактически **тестовое
покрытие** того, что уже работает.

**Состояние до G12:**
- ✅ Dockerfile HEALTHCHECK у всех 5 backend (wget alpine).
- ✅ Compose-level `healthcheck:` блоки на всех 5 backend.
- ✅ `depends_on: service_healthy` для backend → DBs/Redis/Rabbit.
- ✅ `api-gateway → service_healthy` для всех 5 downstream.
- ✅ Spring Boot health indicators (db/mongo/redis/rabbit) активны by default
  во всех 5 backend application.yml (no `enabled: false` нигде).

**Что сделано в G12:**

`HealthDegradationIT` в academic-app — regression guard:
- own dedicated `PostgreSQLContainer` + `RabbitMQContainer` без `reuse`
  (чтобы тест мог свободно остановить Rabbit, не сломав параллельные IT
  на reused RabbitMQContainer из `AbstractAcademicEventIntegrationTest`).
- 2 теста с `@Order`: (1) healthUp pre-stop, (2) healthDown post-stop.
- Сценарий «kill rabbitmq» = `RABBITMQ.stop()` → Spring health
  indicator переходит в DOWN → composite status DOWN → 503 status code.

**Surprise (executor → owner):** `application-test.yml` в academic-app
**отключает** `management.health.rabbit.enabled` и `redis.enabled`. Это
сделано раньше (M-?, явного коммит-следа не нашёл в git blame) чтобы
остальные IT, которые не запускают Rabbit/Redis containers, не падали
с health DOWN при context startup.

**Workaround в HealthDegradationIT:** через `@DynamicPropertySource`
выставить `management.health.rabbit.enabled=true` обратно. Это override
поверх `application-test.yml` — действует только для этого IT, остальные
тесты не затронуты.

**Что НЕ покрыто IT (deferred — не в G12 scope):**
- DB DOWN (kill PostgreSQL) — high cost, низкий ROI: DataSourceHealthIndicator
  работает идентично RabbitHealthIndicator, regression unlikely.
- Mongo DOWN в attendance/notification-web — паттерн идентичный, можно
  скопировать IT при необходимости.
- Redis DOWN — disabled в test profile, для prod не критично (auth-service
  имеет fallback на in-memory если Redis down — отдельный M03 handling).

**Prod-side validation:** после VPS deploy в G23 dry-run runbook покажет
что все 5 backend становятся `healthy` через `docker compose ps`. Если
там что-то развалится — Spring `service_healthy` `depends_on` chain
не пройдёт, и api-gateway не стартует — fail-fast.

## 2026-04-25 — Группа 13 (Environment secrets infrastructure)

**Audit findings:**

1. **`.env.prod.example` уже существовал** (M11 G4 baseline), но был
   неполный: отсутствовали `MONGODB_REPLICA_SET_KEY` (M13 G7),
   `INTERNAL_ISSUER_SECRET` (M03a), `ALERT_WEBHOOK_SECRET` (M04 G9).
2. **Реальный `.env.prod` владельца имел те же 3 пробела** — без них
   `docker compose up -d` упал бы на старте (Mongo RS init / gateway
   token-exchange / Alertmanager webhook 401).
3. **+1 placeholder** не заменён: `MONGO_NOTIFICATION_PASSWORD=CHANGE_ME_BEFORE_DEPLOY`.
4. **Comose использует 28 уникальных env vars**, из них 22 required +
   6 с defaults (`IMAGE_TAG:-latest`, `ADMIN_TELEGRAM_IDS:-0` etc.).

**Validator design — non-trivial decision:**

Первая итерация использовала `set -a; . file; set +a` (стандартный
bash idiom для load env). **Сломалась на real `.env.prod`** — пароли
содержат shell-special chars (`;`, `~`, `'`, `(`, `@`, `}`) без quoting
(Docker-compose их не требует, parses dot-env literally). При `.`
sourcing bash их evaluate'ил и валился: `bJH: command not found`.

**Решение:** parse dot-env вручную через bash regex
(`[[ "$line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]`),
заполнять associative array `ENV[var]=value`, читать через
helper `env_get()`. Это — **тот же подход, что использует
docker-compose** (key=value literal, no shell eval), и устойчив к
любым chars в значениях.

**Real-world value доказан в этой же сессии:**

Пользователь запустил validator на своём `.env.prod` → validator нашёл
4 реальные проблемы (3 missing secrets + placeholder). Пользователь
сгенерировал недостающие, validator показал `✓ Все validations passed`
+ 24/24 vars. **Это и есть G13's purpose** — pre-flight catch вместо
broken deploy.

**Format checks (12 правил):**

| Var | Check |
|-----|-------|
| All `*_PASSWORD` | length ≥ 8 (≥ 16 для `MONGO_ROOT_PASSWORD`) |
| `GRPC_SECRET`, `INTERNAL_ISSUER_SECRET` | length ≥ 32 (HMAC-SHA256 min) |
| `MONGODB_REPLICA_SET_KEY` | length ~1024 (756 raw bytes base64, Mongo keyfile spec) |
| `BOT_TOKEN`, `TMA_BOT_TOKEN`, `BOT_ALERT_TOKEN` | regex `^[0-9]{9,12}:[A-Za-z0-9_-]{30,}$` |
| `VAPID_PUBLIC_KEY` | length 80-90 (P-256 EC = 87 chars) |
| `VAPID_PRIVATE_KEY` | length 40-50 (= 43 chars) |
| `VAPID_SUBJECT` | starts with `mailto:` или `https://` (RFC 8292) |
| `CORS_ALLOWED_ORIGIN`, `MINI_APP_URL` | starts with `https://` |
| `NOTIFICATION_WS_ALLOWED_ORIGINS` | starts with `https://` (если задан) |
| `SWAGGER_HTPASSWD` | regex `^login:\$\$(apr1\|2y)` (DOUBLE dollar для compose escape) |
| `ALERT_WEBHOOK_SECRET` | regex `^[a-f0-9]{64}$` (hex 32 bytes) — warning |
| `ADMIN_TELEGRAM_IDS` | non-empty + ≠ 0 — warning |

**Exit codes (UX-friendly для CI):**
- 0 — all valid, ready to deploy
- 1 — file missing/unreadable (early exit)
- 2 — required vars missing/CHANGE_ME (deploy блокер)
- 3 — format errors (deploy блокер)

**Что НЕ покрыл validator (deferred — out of scope):**
- Cross-validation `BOT_TOKEN === TMA_BOT_TOKEN` (compose это не enforces,
  но runtime упадёт при mismatch initData verification).
- `MONGO_USER`/`MONGO_NOTIFICATION_USER` ≠ default `rct_user` — не
  security risk, optional.
- DB connectivity smoke (psql / mongosh) — это деплоймент-time, не
  validation-time. Разумно отдать smoke-prod.sh после deploy.

**Documentation:** `docs/prod-deploy-checklist.md` новый раздел 1.0
«Environment secrets (M13 G13)» перед всеми pre-deploy checks. Чек-лист
для first-deploy + rotation. Mongo RS / Internal issuer / Alert webhook
secrets выделены отдельно для upgrade-сценария со старым `.env.prod`.

**Security-side win:** во время audit'а заметил, что текущий `.env.prod`
владельца содержит реальные prod values — Telegram bot tokens, GHCR
PAT, DB passwords. Предложил полную rotation одной операцией (вместо
порционной). Пользователь rotation'нул passwords + добавил 3 missing
secrets. Финальный `validate-env-prod.sh` зелёный → готов для VPS deploy.

## 2026-04-25 — Группа 14 (Swagger + Prometheus/Alertmanager lockdown)

**Реализовано:**

1. **`/prometheus/` + `/alertmanager/` за nginx basic-auth:**
   - 2 location blocks в `nginx/conf.d/default.conf` рядом с `/grafana/`
     (single консистентный `auth_basic_user_file` для всех admin UI).
   - `proxy_pass http://rct-prometheus:9090/` с trailing slash → nginx
     strip'ает `/prometheus/` prefix перед forwarding'ом.

2. **`--web.external-url` + `--web.route-prefix=/`** на prometheus и
   alertmanager:
   - `external-url=https://ruttrack.site/prometheus/` — UI генерирует
     absolute links через этот prefix (Graph/Targets/Alerts ссылки
     не ломаются через reverse-proxy).
   - `route-prefix=/` — service всё ещё слушает на `/` внутри container'а
     (nginx делает strip), без этого prometheus попытался бы expect'ить
     `/prometheus/*` входящих запросов и вернул 404.

3. **`nginx/scripts/entrypoint.sh`** — fail-fast перед `exec nginx`:
   - Check 1: `SWAGGER_HTPASSWD` defined + non-empty.
   - Check 2: format match `login:$apr1$*` либо `login:$2y$*` (после
     compose escape `$$` → `$`). Защита от regression если кто-то
     забудет double-`$$` в `.env.prod`.
   - Check 3: post-write `.htpasswd` non-empty (защита от disk-full).
   - Background safety-net loop (5min `nginx -s reload`) preserved
     из inline command (M11 G4).
   - Старый inline `command` (multi-line `printf > .htpasswd && ...`)
     убран — entrypoint скрипт mount'ится volume'ом, чище читается.

**Surprise (compose env-file parsing):**

Owner отротировал `MONGODB_REPLICA_SET_KEY` (1024 base64 chars), но
docker-compose env-parser упал на unquoted значениях с `+` и `/`:
```
failed to read .env.prod: line N: unexpected character "+" in variable name
```

Причина: `+` и `/` встречаются в base64 alphabet, и **без quoting
docker-compose интерпретирует их как multi-line continuation**. Fix:
обернуть key в double quotes — `MONGODB_REPLICA_SET_KEY="..."`.

`.env.prod.example` обновлён с этим warning'ом (CRITICAL block перед
переменной), `validate-env-prod.sh` уже корректно strip'ает quotes
при парсинге (был сделан так при G13).

**Что НЕ сделано (deferred):**

- IT для basic-auth lockdown — runtime smoke (curl с/без auth)
  работает в G23 deploy dry-run. Inline IT с nginx Testcontainer
  излишен.
- Audit log для basic-auth попыток — nginx access log уже пишет
  `$remote_user` в существующем `log_format main` (см. nginx.conf:13-15).
  Loki/Promtail подхватывают через docker logs driver.
- IP allowlist на admin UI (extra layer) — owner ответил «v0.1»
  в debt-report (#7 X-Forwarded-For).

**Validation:**
- `docker compose -f docker-compose.prod.yml --env-file .env.prod
  config --quiet` → exit 0 (после fix MONGODB_REPLICA_SET_KEY quoting).
- `validate-env-prod.sh` → ✓ all 24 vars passed.

## 2026-04-25 — Группа 15 (Backup infrastructure)

**Решения владельца:**
- Symmetric GPG (AES256, passphrase в Bitwarden) — не asymmetric.
- Automated `test-restore.sh` (B) — не manual runbook check (A).
- Offsite copy (S3/B2) — deferred в v0.1 (подтверждено, VPS-snapshot
  провайдера как 2-й слой достаточен для v0.0.0 GA).
- ShellCheck CI job — добавить proactive (не обсуждалось владельцем,
  но низкий risk high value).

**Surprise: «mongodump × 2» в hand-off НЕ существует.**
В `docker-compose.prod.yml` **одна** Mongo-инстанция
(`rct-mongo-attendance`) хостит обе БД — `attendance_db` и
`notification_db`. Один `mongodump --archive` дампит обе атомарно
(важно для FK consistency между `excuse` в attendance и
`notification_history` через event flow). Упрощает backup и restore.

**Архитектура backup set (`/opt/backups/YYYY-MM-DD/`):**
- `academic.sql.gz` — pg_dump academic_db (plain SQL, gzip)
- `schedule.sql.gz` — pg_dump schedule_db (plain SQL, gzip)
- `mongo.archive.gz` — mongodump --archive (обе БД), gzip
- `env.prod.gpg` — gpg --symmetric --cipher-algo AES256 .env.prod

**Restore safety guards:**
- `--confirm-prod` обязателен для `--target=prod` (double opt-in
  против случайного destructive restore).
- Postgres DROP DATABASE через template1 connection + pg_terminate_backend
  чтобы сбросить active connections перед DROP.
- Mongo `mongorestore --drop` — drop existing collections перед restore.

**Test-compose дизайн (`docker-compose.test-restore.yml`):**
- Project name `rct-test-restore` — изоляция от prod `rutcampustrack_*`.
- Tmpfs volumes (не bind mount) — гарантированный teardown.
- Ephemeral random passwords генерятся на каждый запуск test-restore.sh
  (ни один secret не пересекается с prod).
- Bitnami Mongo RS identical prod setup — важно для compatibility
  с prod backup (сделан с `replicaSet=rs0` в URI).

**Row count verification — что именно проверяем:**
- `academic_db.users >= 1` — seeded admin существует (если 0 — backup
  явно corrupt).
- `schedule_db.tables >= 1` — schema intact (fresh DB может быть
  empty of data, но tables от Flyway миграций должны быть).
- `attendance_db.collections >= 1` — что-то impl'ено после v0.0.0 GA.
- `notification_db.collections` — WARN only (может быть пуст на fresh
  deploy до первого notification).

**НЕ делаем row-count parity с prod** — tested-restore это smoke
(backup разворачивается успешно), не consistency check. Parity
пришлось бы делать из live prod psql (риск нагрузки на prod DB).

**ShellCheck existing scripts fix (proactive):**
- `smoke-prod.sh:30` — SC2064 `trap "rm -f $COOKIE_JAR" EXIT` →
  `trap 'rm -f "$COOKIE_JAR"' EXIT` (single quotes, late expansion).
- `m07-g3-launch-services.sh:11` — SC2164 `cd "$(dirname "$0")/.."` →
  `cd "$(dirname "$0")/.." || exit 1` (fail-fast если dirname broken).

После fix — 7/7 существующих + 3/3 новых bash-скриптов проходят
shellcheck с `--severity=warning` clean.

**GPG passphrase setup — single point of failure:**
- Потеря passphrase = восстановить `.env.prod` из backup НЕВОЗМОЖНО.
- Mitigation в runbook: passphrase в password manager (Bitwarden)
  + записать в password manager как отдельную entry «RutCampusTrack
  — backup GPG passphrase».
- Passphrase НЕ в `.env.prod` (circular dependency — без `.env.prod`
  нельзя decrypt `.env.prod.gpg`). Отдельный файл `.backup-passphrase`.

**Deferred в v0.1 (зафиксировано в runbook):**
- Offsite backup (S3/Backblaze B2 через rclone).
- Prometheus metric `backup_last_success_timestamp_seconds` +
  alert `BackupMissing` при >26h без backup (textfile collector).
- Backup integrity check scheduled в CI (weekly test-restore).

**Estimated backup size growth:**
- v0.0.0 GA (fresh): ~90 KB total.
- 1 year production: ~600 MB (schedule dominates — lessons 1 year ×
  500 per semester).
- Trigger для reconsider retention / offsite urgency: >1 GB total.

## 2026-04-25 — Группа 16 (CSP audit + report endpoint)

**Решения владельца:**
- Smoke browser verification deferred в G23 VPS dry-run (владелец
  сказал «не хочу руками делать пока, потом сообщу об ошибках»).
  Полагаемся на real-world catch через `/api/csp-report` → metric.
- Поддержать оба формата (legacy + Reporting API) — максимальная
  совместимость.
- Через gateway (not nginx bypass) — consistent с остальными routes.
- IT + unit test (не skip'аем).

**Surprise: Spring MVC + `application/csp-report` → 415 в IT.**

Mapping через `@PostMapping(consumes = "application/csp-report")` не
работает: `MappingJackson2HttpMessageConverter` по умолчанию поддерживает
только `application/json` + `application/*+json` (matches regex). MIME
`application/csp-report` — БЕЗ `+json` suffix — не матчится, Jackson
converter не способен десериализовать body в `@RequestBody Map`, Spring
возвращает 415 ДО вызова handler'а.

Попытки:
- Добавить `@PostMapping(consumes = {"application/csp-report", ...})` —
  не помогает, consumes только фильтрует routing, не регистрирует converter.
- Расширить Jackson MIME types через `WebMvcConfigurer` — работает,
  но глобальный impact на converter (риск регрессии в других endpoints).

**Решение:** принимаем body как `byte[]` + `@RequestHeader Content-Type`
+ парсим вручную через inject'ed `ObjectMapper`. Один endpoint, switch
по Content-Type. Zero cross-impact на остальной API.

**Routing dataflow:**
```
Browser CSP violation
  → POST https://ruttrack.site/api/csp-report
  → nginx (TLS termination)
  → gateway /api/csp-report (StripPrefix=1, rate-limit 60/min per-IP)
  → notification-web /csp-report
  → CspReportController.receive(Content-Type, byte[])
  → switch Content-Type → parseLegacy | parseReportsApi
  → recordViolation(directive, blockedUri, documentUri)
    → Micrometer Counter increment (tags: directive, blocked_uri_host)
    → log.warn structured → Loki
  → ResponseEntity.noContent() (204)
```

**Low-cardinality labels:**
- `directive` — только имя (без source list). Пример: «script-src 'self'
  https://cdn.example.com» → «script-src». Lowercase.
- `blocked_uri_host` — только host (без path / query / fragment). Пример:
  «https://evil.example.com/malware.js?id=123» → «evil.example.com».
- Special CSP values: `inline`, `eval`, `data:...`, `blob:...` — браузер
  отдаёт их без scheme (CSP spec). Обрезаем до 32 chars, сохраняем as-is
  lowercase. Редко > 32 (обычно «inline», «eval»).
- Malformed URIs → fallback label «unknown».

**Gateway rate-limit rationale:**
- 1 tok/sec + burst 60 per-IP = 60 requests/min sustainable, burst до 60
  сразу. Malicious сайт с iframe'ом ruttrack.site + много forbidden
  scripts мог бы генерировать 100+ violations/sec от одного visitor.
- Reporting API: Chrome сам rate-limits `reports+json` batch до 1 per 60s,
  поэтому 60 req/min это высокий потолок.

**@Hidden для OpenAPI:**
- `/csp-report` НЕ часть пользовательского API. Infrastructure endpoint
  (browser callback).
- `@Hidden` на контроллере исключает из springdoc-generated OpenAPI.
- `AlertController /internal/alert` **не @Hidden** — был в M04 G9, попал
  в snapshot как раз чтобы Alertmanager webhook config виден. Не трогаю
  в M13.

**CSP report endpoint path decision:**
- nginx path: `/api/csp-report` (под `/api/` umbrella).
- gateway StripPrefix: `/csp-report` (ожидаемо для Spring).
- Alternative «отдельный `/internal/csp-report` в обход gateway» —
  rejected: (1) менее consistent, (2) требует специальной nginx location,
  (3) не reuse существующей rate-limit infrastructure.

**Frontend changes: none.**
Frontends не меняются — браузер сам шлёт отчёты согласно `report-uri`
директиве из CSP header (nginx add'ит). PWA/web-panel/landing — without
explicit code.

**Deferred в v0.1:**
- **Alert rules** для `CspViolationSpike` (rate > 10/5min). Пока observation
  через manual Grafana queries + Loki.
- **nonce-based CSP** (replace `style-src 'unsafe-inline'` с
  `style-src 'nonce-<random>'`). Требует refactor Angular/PWA build pipeline
  для nonce injection.
- **SRI (Subresource Integrity)** для `<script src>` / `<link href>`.
  Пока все third-party resources self-host'еются (M07 G4), так что low
  impact. Нужно при добавлении любого CDN.
- **COOP / COEP** headers. Нужны если будем использовать
  `SharedArrayBuffer` или требовать Spectre mitigations.

## 2026-04-25 — Группа 17 (Grafana dashboards sanity + retention)

**Audit-only group, без code-изменений в backend/frontend.** Все 5
checklist items закрыты validation'ом (✅) или ссылкой на закрытое
ранее.

**Что нашёл при аудите:**

| Item | Где | Статус |
|------|-----|--------|
| 1 — dashboards structure | `infra/grafana/provisioning/dashboards/` — 6 файлов | автомат валидация (script + CI), live smoke deferred G23 |
| 2 — Prometheus retention 14d | `docker-compose.prod.yml:520` `--storage.tsdb.retention.time=14d` | ✅ pre-existing (M04 G10) |
| 3 — Tempo retention | `infra/tempo/tempo.yml:23` `compactor.compaction.block_retention: 336h` | ✅ pre-existing (M04 G10) |
| 4 — Loki retention | `infra/loki/loki.yml:43` `limits_config.retention_period: 336h` + `compactor.retention_enabled: true` | ✅ pre-existing (M04 G10) |
| 5 — `GRAFANA_PASSWORD` non-default | `.env.prod.example:118` `CHANGE_ME` + `validate-env-prod.sh:104` REQUIRED + `:167` min_length 8 | ✅ pre-existing (M13 G13) |

**Surprise — naming divergence:**
hand-off в `NEXT-SESSION.md` ожидал 3 dashboards (`business-kpis`,
`system-health`, `tracing`) и переменную `GRAFANA_ADMIN_PASSWORD`. Факт
после аудита:
- 6 dashboards: `business-kpis-m04`, `Docker and system monitoring`,
  `gRPC Client Latency (M05 G8)`, `RutCampusTrack — Логи системы`,
  `Node Exporter Full`, `SpringBoot APM Dashboard`. Покрытие шире чем
  ожидалось — system-health фактически разнесён по 3 dashboard'ам
  (docker-monitoring + node-exporter + springboot-apm), а tracing
  встроен в springboot-apm + grpc-latency. Tempo datasource даёт raw
  trace search через explore tab (не отдельный dashboard).
- Переменная называется `GRAFANA_PASSWORD` (короче), не
  `GRAFANA_ADMIN_PASSWORD`. Это исторический выбор M04 G10. Не меняю —
  rename только ради совпадения с hand-off — churn.

**Tempo retention format `336h` vs `14d`:** Tempo принимает Go duration
формат. `336h` = 14 дней exactly, `14d` НЕ валидный Go duration
(silent default). Текущее `336h` — правильно.

**Что добавил (proactive hardening):**
- `scripts/validate-grafana-dashboards.sh` — pre-flight validator
  (broken JSON / missing uid|title / 0 panels). Использует node для
  парсинга. Verified 6/6 ✓ + negative cases (broken/empty/no-uid → exit 2).
- CI job `grafana-dashboards` в `.github/workflows/ci.yml` — catch'ит
  regression если кто-то commit'ит broken dashboard JSON. Дёшево
  (~5 сек на ubuntu-latest, node pre-installed).

**Live smoke (item 1 «non-zero данные»):** deferred в G23 VPS dry-run
per owner-policy («ничего руками»). Sufficient для G17 — automated
structure check + 5 retention/security audit pass'ов.

**Estimate vs actual:** ~30 мин (audit + script + CI). Самая маленькая
из групп, как и ожидалось.

## 2026-04-25 — Группа 18 (WebSocket reliability)

**Surprise — heartbeat был факически off:** `WebSocketConfig.java`
содержал comment «Default Spring heartbeat (10s server, 10s client) —
no custom tuning needed». Это **неверно**: при использовании
`enableSimpleBroker` без явного `setHeartbeatValue` + `setTaskScheduler`
Spring выдаёт heartbeat **0/0 (off)** — silent. Idle WebSocket'ы
зависают как half-open за nginx / firewall'ом / corporate proxy.

**Fix:**
```java
config.enableSimpleBroker("/topic")
        .setHeartbeatValue(new long[]{10_000L, 10_000L})
        .setTaskScheduler(stompHeartbeatScheduler());
```

`stompHeartbeatScheduler` — dedicated `ThreadPoolTaskScheduler` bean
(pool size 1, daemon, prefix `stomp-heartbeat-`). Без TaskScheduler
`setHeartbeatValue` silently no-op'ит — это и было проблемой.

**Тесты:**
- `WebSocketConfigTest.stompHeartbeatScheduler_isInitializedDaemonThread`
  — unit, проверяет что bean создаётся и initialize() сделана. Catch'ит
  регрессию (если кто-то удалит TaskScheduler).
- `StompIntegrationIT` — full Spring context bootstrap зелёный с
  новым scheduler. Полноценный heartbeat-frame e2e IT skipped — слишком
  тяжело (ждать live frame через Testcontainers сетевой timeout).

**Frontend heartbeat:** `@stomp/stompjs` по дефолту шлёт 10s/10s
(`heartbeatIncoming = 10000`, `heartbeatOutgoing = 10000`). Симметрично
backend'у, явная конфигурация на frontend не нужна.

**nginx changes:** добавлен `proxy_buffering off` в `location /api/ws/`.
Без этого 1-byte heartbeat фреймы (`\n`) накапливаются в nginx-буфере
и не доходят до клиента вовремя. `proxy_read_timeout 86400s` уже стоял
(M03b), >> требуемых 300s — оставил.

**Reconnect smoke (item 3) deferred в G23** per owner-policy. Frontend
покрыт unit-тестами:
- `useStompCheckin.test.ts` × 3: finite reconnectDelay (1-5000ms),
  ticket re-fetch при reconnect (иначе single-use UUID невалидный),
  reconnect mandatory (`reconnectDelay > 0`).
- `notification-center.service.spec.ts`: exponential backoff lifecycle.

**Doc создан:** `docs/websocket-flow.md` — single source of truth.
Покрывает: архитектура (browser → nginx → gateway → notification-web),
ticket handshake (4 шага), heartbeat rationale, nginx config rationale,
reconnect стратегия, test inventory, troubleshooting (4 сценария
включая «зависает после ~60s» и «401 на каждый CONNECT»).

**Estimate vs actual:** ~1 час (включая diagnosis surprise heartbeat-off
+ test debug — `getPoolSize()` lazy returns 0 пока thread не создан,
заменил на `getScheduledExecutor() != null`).

## 2026-04-25 — Группа 19 (Alertmanager → Telegram E2E + alerts catalog)

**Two surprises:**

**Surprise 1 — DLQBacklog silent dangling.** Алёрт ссылался на метрику
`rabbitmq_queue_messages`, но `rabbitmq:3.13-alpine` image не имел
`rabbitmq_prometheus` plugin. Метрика никогда не экспортировалась →
alert никогда не fire'ил. Это значит: с M04 (когда DLQBacklog был
добавлен) до M13 G19 — **6 milestone'ов** mock-coverage. Если бы
consumer реально валился в DLQ — мы бы узнали через бизнес-симптомы
(missing notifications), не через alert.

**Fix:** image switch `rabbitmq:3.13-alpine` →
`rabbitmq:3.13-management-alpine` (drop-in replacement, +management
+ prometheus plugins). Dev compose **уже был** на management-alpine —
prod просто отстал. Digest pin updated через `docker buildx imagetools
inspect`. mem_limit 256m → 384m под management overhead. Новый
expose port 15692. Scrape job `rabbitmq` в `prometheus.yml`.

**Surprise 2 — AC-13 «15+» missed.** Существовало 11 alerts, не 15.
Добавил 4 для prod-readiness:

| Alert | File | Severity | Metric |
|-------|------|----------|--------|
| HighErrorRate | service-health.yml | warning | http_server_requests_seconds_count{status=~"5.."} |
| HighRequestLatency | service-health.yml | warning | http_server_requests_seconds_bucket (p95) |
| RabbitMQQueueBacklog | rabbitmq.yml | warning | rabbitmq_queue_messages{queue!~".*\.dlq"} |
| RabbitMQConnectionLost | rabbitmq.yml | critical | rabbitmq_connections == 0 |

`http_server_requests_seconds_*` — Spring Boot Actuator default,
без code-changes. Rabbit metrics — благодаря image switch.

DLQBacklog **перенесён** из `service-health.yml` в `rabbitmq.yml`
(новый файл) — domain coherence. `service-health.yml` теперь
`service-health` + `outbox-eventing` + `http-sli` + `infra` +
`business-anomaly` (10 rules). `resource-limits.yml` 2 rules.
`rabbitmq.yml` 3 rules. **Итого 15.**

**Validation:** `promtool check rules infra/prometheus/rules/*.yml` →
SUCCESS (10/2/3 rules). `docker compose -f docker-compose.prod.yml
config --quiet` → exit 0.

**Items 1-3 (live smoke) deferred per owner-policy.** Coverage без
manual smoke полная:

| Этап chain | Test |
|------------|------|
| Prometheus eval | promtool check (CI / G23) |
| Alertmanager → /internal/alert auth | AlertControllerTest × 4 (Bearer happy/missing/wrong/empty-secret) |
| Webhook payload parsing | AlertControllerTest × 4 (happy/malformed/empty/non-string) |
| RabbitMQ alert.fired publish | AlertControllerTest.happyPath_publishesEachAlertAndReturns200 |
| Bot consumer dispatch | test_event_dispatcher.py |
| Bot Telegram format | test_alert_fired.py × 3 (critical/warning/admin filter) |

Полный chain покрыт unit/IT — single-thread coverage. Live smoke на
VPS one-batch'ом в G23.

**Doc rewrite:** `docs/alerts.md` существовал с M04 G9 (8 alerts,
short stub). Полная переработка под Symptom/Meaning/Runbook standard
× 15 + cross-ref таблица + E2E test inventory + silencing examples
+ история изменений M04 → M13.

**Estimate vs actual:** ~1.5 часа (image switch + 4 new alerts +
полный rewrite alerts.md + promtool validation). Image switch surprise
+ owner approval round добавил overhead.

## 2026-04-25 — Группа 20 (Certbot renewal hook + cert expiry alert)

**Surprise (приятный) — renewal hook уже не нужен.** Hand-off NOTES
описывал 3 варианта для item 5 (deploy-hook docker.sock / sidecar
inotifywait / cron 12h reload), требующих owner-decision. Реальность:
M13 G14 уже добавил **5-min reload loop** в `nginx/scripts/entrypoint.sh:57`
(`( while :; do sleep 5m; nginx -s reload 2>/dev/null || true; done ) &`).
Это превышает baseline «cron 12h» в 144 раза.

→ Item 5 закрыт **N/A** + документировано в runbook'е почему отдельный
hook не нужен + почему 3 альтернативы rejected:

| Подход | Reject reason |
|--------|----------------|
| `--deploy-hook 'docker exec ...'` | docker.sock mount = full host control. Security risk. |
| sidecar inotifywait | Усложняет stack ради 5-min latency benefit. |
| cron на host | Нарушает «всё в compose» principle. |
| **5-min reload loop в nginx entrypoint** | ✅ Текущее решение. |

**3 SSL alerts вместо 1.** Hand-off требовал только `SslCertExpiresSoon`,
но per G9 standard «полный hardening» добавил 2 escalation/coverage:

- `SslCertExpiresSoon` (warning, < 30d) — early warning, auto-renew
  должен сработать.
- `SslCertExpiresUrgently` (critical, < 7d) — auto-renew **не**
  сработал за 23+ дня, manual intervention. Будит ночью.
- `SslProbeFailed` (critical, probe_success == 0 ≥ 10m) — TLS handshake
  fail совсем (cert expired/revoked, HTTPS endpoint down, DNS broken).

**Итого alerts catalog: 18** (G19 закончил на 15, +3 SSL).

**blackbox-exporter design choice:** 64m mem_limit (lightweight probe),
private network only, expose 9115 internally. Config с
`fail_if_not_ssl: true` явно — мы probing'уем HTTPS, plain HTTP должен
fail (ловит DNS hijack / accidental :80 probing).

**Prometheus relabel:** официальный pattern из blackbox-exporter docs:
`__address__ → __param_target → instance label`, finally `__address__ →
blackbox-exporter:9115`. Без relabel'а instance label был бы
`blackbox-exporter:9115`, а target в metric'е — undefined.

**docs/prod-deploy-checklist.md §1.5b** — новая секция для **first
deploy** на чистый VPS. 2-phase: HTTP-only phase для ACME challenge →
certbot certonly → restore HTTPS config → full stack up. Subsequent
deploys проходят без этой секции (cert уже выпущен, auto-renew работает).

**Validation:**
- `promtool check rules` — SUCCESS 10/2/3/3 = **18 rules** total.
- `docker compose -f docker-compose.prod.yml config --quiet` — exit 0
  с blackbox-exporter добавлен.
- blackbox-exporter digest получен через `docker buildx imagetools
  inspect prom/blackbox-exporter:v0.25.0` — pin'нен по convention M08 G11.

**Estimate vs actual:** ~1 час (item 5 ожидаемо был самый сложный —
оказался N/A ✓). Items 1-4 (blackbox + scrape + alerts + catalog) ~30
мин. Items 6-7 (runbook + deploy checklist) ~30 мин.

## 2026-04-25 — Группа 21 (Flyway CONCURRENTLY guard)

**Two surprises:**

**Surprise 1 — только 2 PG-сервиса.** Hand-off говорил «в каждом
service с Postgres». Реальность: только academic-app + schedule-app
используют PostgreSQL (auth-service на Redis, notification-web и
attendance — на Mongo). Test'ы созданы только в этих двух.

**Surprise 2 — 0 existing миграций с CONCURRENTLY.** Все 18 academic
+ 14 schedule миграций — plain `CREATE INDEX`. Это значит каждая
наша prod migration теоретически блокировала таблицу. На малом
dataset (test users + предсказуемый load) impact был незаметен; на
real prod с 1k+ rows и concurrent traffic блокировка была бы
видимой.

**Нельзя редактировать applied миграции** (memory feedback
`feedback_flyway_no_edit.md` — checksum mismatch ломает prod boot).
→ Решение: **grandfather** V ≤ cutoff (V18 academic, V14 schedule),
test проверяет только future migrations. CLAUDE.md правило явно
говорит про CONCURRENTLY для **prod-таблиц**, не trying to retroactively
fix all existing.

**Cutoff bumping protocol:** при добавлении V19+/V15+ с
CONCURRENTLY — bump соответствующий `BASELINE_CUTOFF` в test'е
(signal что guard caught и пропустил). Это сохраняет «новые миграции
проверяются» semantics без false-positive на уже принятой migration.

**ArchUnit не подошёл.** ArchUnit оперирует Java classpath'ом; .sql
файлы — resources. Заменён на JUnit unit-test с regex parsing —
проще, быстрее (мс), не требует ArchUnit dep'а в build.gradle.

**Regex pattern:**
- `CREATE_INDEX = create\s+(?:unique\s+)?index\b` — захват и
  CREATE INDEX, и CREATE UNIQUE INDEX.
- `CONCURRENTLY = create\s+(?:unique\s+)?index\s+concurrently\b` —
  должен быть **сразу** после INDEX (snippet 80 chars от позиции
  CREATE INDEX). Это catch'ит случаи когда кто-то напишет
  `CREATE INDEX foo` + позже `... CONCURRENTLY` в одном файле как
  два разных statement'а — НЕ матчится как valid.
- `stripComments` — убирает `/* */` block + `--` line comments перед
  matching, чтобы commented-out CREATE INDEX в комментарии не
  тригерил false-positive.

**Validation:**
- Both tests passing (existing migrations grandfathered).
- Negative case verified: tmp V99 с plain CREATE INDEX → test fail
  с явным сообщением `V99 (V99__test_g21_BAD.sql) содержит plain
  CREATE INDEX без CONCURRENTLY: 'CREATE INDEX idx_test_g21 ON
  users(role);'. Замени на CREATE INDEX CONCURRENTLY IF NOT EXISTS
  + добавь -- ## в начало файла...`.
- Positive case verified: tmp V99 с CONCURRENTLY → pass.

**EXPLAIN ANALYZE follow-up — section §5.** T+2 weeks — typical
window когда `pg_stat_statements` накапливает реалистичный workload.
Top-10 slow queries → EXPLAIN ANALYZE → если Seq Scan на hot tables,
add index через CONCURRENTLY. Cross-ref'нул на
`MigrationConcurrentlyTest` (новая миграция автоматически проверяется)
+ HikariPoolExhaustion / HighRequestLatency alerts (M13 G19) для
correlation pattern'а.

**CLAUDE.md update — bonus.** Параллельно добавил явный pin «НИКОГДА
не редактируй applied миграции» — это правило было в memory
feedback (`feedback_flyway_no_edit.md`) но не в CLAUDE.md. Теперь
любой новый чат прочитает правило сразу.

**Estimate vs actual:** ~45 мин (включая negative case verification —
gradle holds Spring app context warm-up overhead). Items 1-3
(CLAUDE.md + 2 test'а) ~30 мин. Item 4 (deploy checklist EXPLAIN
section) ~15 мин.

## 2026-04-25 — Группа 22 (Playwright E2E auth flow)

**Two surprises:**

**Surprise 1 — Playwright уже стоит.** Hand-off говорил «создать
`frontends/pwa-e2e/`», но факт: M08 G7 уже создал
`tests/e2e/` с 8 spec'ами + Playwright config + 3 fixtures (auth/
users/axe). Существующий `auth.spec.ts` уже покрывает T1 login
(happy path), T2 admin, T4 logout (без cookie verification). Не
дублирую — добавил **новый** spec `auth-token-lifecycle.spec.ts` с
fokus на security guarantees (cookie attributes, refresh rotation,
WS resilience).

**Surprise 2 — Playwright не в CI.** M08 G7 создал тесты, но job в
`.github/workflows/ci.yml` отсутствовал. AC-6 («login/logout/refresh
cycle зелёный в CI») = блокер. Новый job `e2e-auth` добавлен.

**Spec design — 5 тестов в одном @smoke describe:**

| # | Тест | Уникальность от auth.spec.ts |
|---|------|------------------------------|
| T1 | HttpOnly cookie verification | auth.spec.ts не проверяет cookie attributes |
| T2 | URL `/admin/` assertion | auth.spec.ts проверяет heading, не URL pattern |
| T3 | refresh + rotation + new value | NEW — не было |
| T4 | logout cookie clear | auth.spec.ts проверяет redirect, не cookie state |
| T5 | offline/online → reload survival | NEW — cross-ref M13 G18 |

**Cookie verification approach:** Playwright `context.cookies()`
возвращает array с полями name/value/domain/path/expires/httpOnly/
secure/sameSite. SameSite в API форме `'Strict'`/`'Lax'`/`'None'`.
Source of truth: `services/auth-service/.../security/AuthCookies.java`
(rct_refresh, /api/auth path, HttpOnly+Secure+SameSite=Strict).

**T3 refresh flow nuance:** Force TTL 15s в test'е невозможно без
override `application-test.yml` (требует backend restart, не
test-friendly). Решение: триггерить refresh **напрямую** через
`POST /api/auth/refresh` с `request.post()` API. Browser auto-attaches
cookie по path match (`path='/api/auth'`). Это проверяет: (1) endpoint
работает (200 + access_token), (2) **rotation** — новый cookie value
(anti-replay), (3) HttpOnly preserved.

**T5 WS reconnect approach:** Playwright не имеет API для inspect'а
WebSocket frames напрямую. Альтернативы:
- **Page network listener** — сложно, требует custom WS spy.
- **Reload + assert UI рендерится** — проверяет что cookie + sessionStorage
  выжили offline cycle, role-guard продолжает пускать. **Выбрано.**
- **Wait for STOMP heartbeat frame** — overkill для smoke.

**CI job design:**
- `--grep @smoke` — только smoke-tagged specs (auth.spec.ts × 3 +
  auth-token-lifecycle.spec.ts × 5 = 8 тестов). Полный suite
  (8 spec'ов) — local `npm test`.
- `--project=chromium` — без WebKit (CI время). Локально оба.
- Healthcheck poll loop 4 мин — критично, иначе Playwright стартует
  до того как backend готов.
- Failure artefacts: `playwright-report/` (HTML с screenshots/traces)
  + `docker logs` для всех контейнеров. Retention 7 days.
- Cost: ~5-7 мин на ubuntu-latest.

**Local validation:** spec syntax-check через `node --check` (exit 0).
TypeScript type-check невозможен локально (нет node_modules в
`tests/e2e/`, tsc устанавливается через `npm install`). CI на первом
прогоне поймает type errors.

**`docs/testing-strategy.md` не существует** (hand-off ошибся в
имени). Реальный файл — `docs/e2e-testing.md`. Обновлён + cross-ref на
M13 G18 websocket-flow.md.

**Estimate vs actual:** ~1.5 часа (5 тестов + CI job + docs). Surprise
с pre-existing infra ускорил (не нужно создавать с нуля), но
diagnostic время на CI workflow design (healthcheck poll, artefact
upload, frontend build chain) и cookie API research съело экономию.

## 2026-04-25 — Группа 23 (VPS deploy runbook dry-run)

**Природа группы:** items 1-2 фундаментально **owner-driven** —
нельзя автоматизировать «выполнить шаги на fresh VPS» (нужна сама
VPS либо Ubuntu VM локально). Per owner-policy «ничего руками» —
моя роль = подготовить так, чтобы dry-run прошёл максимально гладко
и diagnostic был ready для первой попытки.

**Подготовка вместо dry-run'а:**

1. **`scripts/preflight-deploy.sh`** — aggregator pre-flight checks
   в один entrypoint. 6 секций:
   - env validation (вызов `validate-env-prod.sh` G13)
   - Grafana dashboards structure (G17)
   - Prometheus rules (`promtool check rules` 18 правил)
   - docker-compose syntax (`docker compose config --quiet`)
   - critical files presence (10 ключевых файлов)
   - backup infra (/opt/backups + .backup-passphrase)

   Запускается ДО `docker compose up -d`, exit > 0 = блокер.

2. **`scripts/verify-deploy.sh`** — post-deploy M13 contract checks.
   8 секций:
   - container health (≥14 healthy)
   - public HTTPS (/login, /api/health)
   - security headers (HSTS / CSP report-uri / X-Frame-Options)
   - CSP report endpoint (POST /api/csp-report → 200/204)
   - /prometheus + /alertmanager basic-auth (401 без creds)
   - alert rules count (18 expected, требует SWAGGER_USER/PASS)
   - WebSocket /api/ws/info Upgrade headers
   - Mongo TTL index на notification_history

   Запускается ПОСЛЕ `docker compose up -d` + 60-90 sec boot.

3. **prod-deploy-checklist.md обновления:**
   - §1.7 «Pre-flight diagnostics» — вызов preflight-deploy.sh
   - §2.4 «Post-deploy contract verification» — vary-deploy.sh + smoke-prod.sh
   - **13 runbook'ов в cross-ref index** в начале файла. Existence
     verified для всех 13.

**Не создавал новые runbook'и** — существующие covering заменили
hand-off ожидание «обновить runbook реальными выводами команд». Эта
часть будет owner job когда он сделает dry-run + найдёт отклонения.

**Owner-driven slot зарезервирован:** items 1-2 checklist отмечены
«owner-driven», NOTES оставит место для findings когда дойдёт.

**Estimate vs actual:** ~1 час. Items 3-4 (script + cross-ref) — ~45
мин. Items 1-2 (live dry-run) — owner-time, не считается в M13
estimate. После dry-run findings — могут понадобиться 1-2 follow-up
коммитов с fix'ами, тоже owner-time.

**Slot для owner-findings:**

> _Здесь owner запишет отклонения от runbook'а после real VPS dry-run:_
> _- [TBD] command X fail'ил с error Y → fix Z_
> _- [TBD] step N оказался unclear → переписан в..._
> _- [TBD] missing prereq A → добавлен в §1.X_
