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
