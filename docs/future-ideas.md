# Future ideas — backlog для пост-v0.0.0

Идеи и решения, которые осознанно отложены на будущее. Не блокируют релиз,
но фиксируются здесь, чтобы не потерять.

---

## Безопасность

### Magic-link для первого входа (вместо plaintext initial_password)

**Текущее состояние (v0.0.0, accepted by owner 2026-04-18):**
Поле `users.initial_password` хранится в БД в открытом виде, отдаётся через
REST `/academic/users`, через gRPC `GetUserByTelegramId`, и Telegram-бот
пересылает пароль студенту в `/start`. По решению владельца (см.
`docs/report-before-v0.0.0/OWNER-ANSWERS.md` M1 + 01-Q1) это by design:
проект вне юрисдикции РФ, 152-ФЗ не применяется, технический риск принят.

**Идея на будущее:**
Перейти на одноразовый setup-токен ("magic-link"):
1. При создании пользователя `auth-service` генерирует
   `setup_token` (random 32-byte), хранит хэш в `password_setup_tokens`
   с TTL 24 часа.
2. `academic-service` отдаёт через gRPC не `initial_password`, а ссылку
   вида `https://ruttrack.site/setup-password?token={raw_token}`.
3. Бот шлёт ссылку студенту. По клику — форма «задайте свой пароль»,
   токен сжигается после первого использования.
4. В БД остаётся только bcrypt-хэш пользовательского пароля.
   Поле `initial_password` удаляется.

**Что это закроет (когда сделаем):**
- 01 P0-2 (initial_password в БД)
- 02 P0-1 (initial_password в REST/gRPC)
- 06 P0-3 (plaintext в Telegram)
- 08 P0-1 (initial_password в proto-контракте)
- 10 P2-13 (admin UI показывает пароль)
- Снимает риск утечки через XSS / supply-chain / Telegram-оператора

**Оценка работы:**
~3-4 человеко-дня (auth-service migration + endpoint, academic-service
изменения REST/gRPC, бот update, web-panel/PWA setup-страница, e2e-тест).

**Когда делать:**
Скорее всего v0.1 или позже. Не критично пока проект — узкий круг
пользователей и accept-tradeoff явно зафиксирован.

---

## Архитектура

### Выделить auth-owned схему `users` (вариант b из 01-Q-P0-3)

**Текущее состояние (v0.0.0, accepted by owner 2026-04-18):**
`auth-service` подключён к `academic_db`, таблица `users` владеется
academic-service'ом (Flyway-миграции в academic). Это **shared-DB**
между двумя bounded contexts. Нарушает CLAUDE.md "Database-per-Service",
но осознанно принято: один разработчик, тесная связь auth+users,
performance важнее изоляции.

**Идея на будущее:**
Сделать auth-service владельцем таблицы `users` без выделения отдельного
PostgreSQL-контейнера:
1. Перенести Flyway-миграцию `V1__baseline.sql` (часть про `users`)
   из `academic-app/src/main/resources/db/migration/` в
   `auth-service/src/main/resources/db/migration/V1__users.sql`.
2. В academic-service: `flyway.enabled: false` для users-related таблиц,
   academic читает users через gRPC к auth (как notification-bot уже делает).
3. Деплой-порядок: auth → academic → schedule → ... (auth владеет схемой
   и поднимается первым).
4. Разделить `flyway_schema_history` через разные `flyway.table` или схемы
   (`auth.flyway_schema_history`, `academic.flyway_schema_history`),
   чтобы оба сервиса не топтались по одной таблице.

**Что это закроет (когда сделаем):**
- 01 P0-3 (auth подключён к чужой БД)
- Чёткая граница ownership: auth владеет users
- Изменение users-схемы (mfa_secret, last_login_at, lockout_policy)
  не требует трогать academic
- Архитектура соответствует CLAUDE.md

**Оценка работы:**
~1 человеко-день (перенос миграции + правка academic чтобы не валидировал
users + gRPC-вызовы вместо JOIN'ов в admin-flow + тесты).

**Когда делать:**
Когда понадобится auth-only функционал (MFA, login-аналитика, lockout
policies, password recovery поверх magic-link, audit logs логинов).
Сейчас accept tradeoff (вариант c из 01-Q-P0-3).

---

## Frontend

### PWA для admin/teacher ролей

**Текущее состояние (v0.0.0, accepted by owner 2026-04-18):**
PWA (`frontends/pwa/`) обслуживает только роли STUDENT и STUDENT+headman.
Admin и teacher работают исключительно в web-panel (Angular) на десктопе.
`RoleGuard` в роутере PWA пускает только STUDENT — остальные роли
редиректятся на `/forbidden` или на `/login` web-panel'а.

**Идея на будущее:**
Расширить PWA на admin и teacher роли — для работы с мобильного устройства
в рамках дня:
- Teacher: быстрый просмотр журнала текущей пары, отметка «занятие
  проведено», закрытие пары раньше срока.
- Admin: approve excuse-тикетов на ходу, просмотр статистики группы,
  уведомления о красной зоне.

**Что нужно для реализации:**
- Добавить `/teacher/*` и `/admin/*` роуты в PWA роутер.
- Расширить `RoleGuard` (из 09 P0-3 фикса) на допуск роли.
- Адаптировать существующие web-panel фичи под мобильный UX (touch,
  маленький экран, offline-режим для excuse-approval).
- Переиспользовать Angular web-panel'овские API-вызовы — структура
  бэкенда общая, нужен только новый React-UI.

**Оценка работы:**
~1-2 недели (каждая роль — отдельный набор страниц, pagination, поиск,
filtering, push-триггеры).

**Когда делать:**
Когда появится запрос от реальных пользователей («хочу approve на ходу»).
В v0.0.0 фокус на студенческий core-flow.

---

### Mini-app unification: copy+adapt from PWA after M12

**Текущее состояние (v0.0.0, accepted by owner 2026-04-21, M07 старт):**
Telegram Mini App (`frontends/mini-app/`) живёт как отдельный React-проект
с ручными TypeScript interfaces скопированными из backend DTO. M07 G3
(openapi-typescript) затронул только PWA + web-panel — mini-app
**осознанно опущен**, чтобы избежать двойной работы при предстоящем
переезде.

**План миграции:**
1. Дождаться закрытия всех v0.0.0 milestones (M07→M12), стабилизации PWA
   в проде.
2. После M12 — скопировать `frontends/pwa/` → `frontends/mini-app/` как
   новый baseline (с openapi-ts, RFC 7807 interceptor, axe-core baseline,
   ConfirmWithReasonDialog, `useSwipeHandler`/`useDateNavigation`/
   `PullToRefresh`, unified DrawerMenu — всё из M07).
3. Адаптировать под Telegram WebApp SDK constraints:
   - `window.Telegram.WebApp` init (viewport, theme, BackButton, MainButton).
   - Auth через `initData` (HMAC-verify на backend) вместо login-формы.
   - Geolocation через `Telegram.WebApp.requestLocation` (не browser API).
   - Haptic feedback через `Telegram.WebApp.HapticFeedback`.
4. Провести security-audit + code-review diff'а для Telegram-specific кода.

**Что закрывается:**
- Дублирование interfaces между PWA и mini-app.
- Drift-guard наконец начинает защищать mini-app от breaking DTO changes.
- Технический долг v0.0.0: mini-app на ручных interfaces, на которые не
  распространяется CI drift-check.

**Trigger:** после tag `v0.0.0` (вся M07-M12 закрыта) и после минимум
2 недель стабилизации PWA в проде без критичных багов.

**Estimate:** ~3-5 дней (copy = 1д, Telegram SDK adapter = 1-2д,
security/code review + hot-patches = 1-2д).

---

## NEW-146: Mongo aggregation pipeline для ReportService (M05 G5 deferred)

**Origin:** M05 Группа 5 / DECISIONS D9. OWNER-ANSWERS P2-10/5
предписывал «SQL-aggregate вместо in-memory stream», но в текущем
коде применить нельзя без архитектурных изменений.

**Blocker:** `ReportService.filterExistingLessons` (строки 417-426)
делает defence-in-depth фильтр удалённых уроков через gRPC
`scheduleGrpcClient.getLessonsByIds` **после** загрузки attendance
records. Mongo `$group` pipeline не знает про alive-lesson state в
schedule-service (кросс-сервис), поэтому полный aggregation pipeline
нарушит invariant (stale docs попадут в отчёт при missed
`lesson.deleted` event).

**Варианты реализации в будущем:**

1. **Денормализация `lesson_alive` флага в attendance docs.** При
   `lesson.deleted` event'е attendance-service выставляет
   `lesson_alive=false` на всех matching doc'ах (batch update). Mongo
   pipeline фильтрует `$match: { lesson_alive: true }` и делает
   `$group` по статусам. Trade-off: потенциальное рассогласование
   при broker downtime (лечится reconciliation job).

2. **Отдельная коллекция `dead_lessons` с TTL index.** При
   `lesson.deleted` — insert в `dead_lessons`, pipeline делает
   `$lookup` или `$nin`. Проще миграция, но extra collection.

3. **Pre-aggregated stats collection (materialized view).** Раз в
   N минут считаем per-user per-semester stats в отдельную Mongo
   collection. Hot-path reads — тривиальные `findById`. Trade-off:
   eventual consistency + scheduler complexity.

**Ожидаемый выигрыш:** O(N) server RAM → O(1) + Mongo-side
aggregation. Для студента с 200 lesson-records per semester —
~200× reduction на network transfer (stats только, не raw records).

**Когда делать:** M06 (Ops hardening) или M07 (если появится
конкретная latency-проблема на production traffic). Сейчас
single-pass accumulators (D9) закрывают immediate need.

---

## FSM storage миграция в Redis для Telegram-бота (v0.1)

**Источник:** `06-notification-bot.md` P2-7, отложено из M08/M09.

**Текущее состояние:** `notification-bot/__main__.py:105` использует
`MemoryStorage()` для Aiogram FSM. При рестарте контейнера состояние
теряется.

**Почему не блокер сейчас:** FSM реально не используется (bot имеет
только однокадровые callback_query handler'ы). Фикс preventive —
чтобы при добавлении первого multi-step сценария (excuse-wizard,
reminder-customization) не словить regression.

**Идея на будущее:**
```python
from aiogram.fsm.storage.redis import RedisStorage
storage = RedisStorage.from_url(settings.redis_url)
dp = Dispatcher(storage=storage)
```

**Оценка работы:** ~2 часа + тесты (вместе с первой multi-step
фичей).

---

## `/actuator/**` исключить из OTel tracing (v0.1)

**Источник:** M04 backlog → M05 → M06 G8e → M07 NOTES → **v0.1**.

**Текущее состояние:** OpenTelemetry трейсит все HTTP requests,
включая `/actuator/health`, `/actuator/prometheus`, `/actuator/metrics`.
Это засоряет Tempo метриками scraping'а (Prometheus опрашивает
/actuator/prometheus каждые 15s → ~5760 spans/сутки per сервис
только от scraping'а).

**Почему не блокер сейчас:** severity MINOR; отмечено в
`application.yml` комментарием honest-о («до custom Sampler»).

**Идея на будущее:** custom `OtelSampler` bean в shared-observability:
```java
@Bean
Sampler skipActuator(@Value("${otel.sampler.skip-paths}") List<String> paths) {
    return Sampler.parentBased(Sampler.alwaysOn()).and(
        (ctx, name, kind, attrs, links) -> {
            var httpTarget = attrs.get(AttributeKey.stringKey("http.target"));
            if (httpTarget != null && paths.stream().anyMatch(httpTarget::startsWith)) {
                return SamplingResult.create(SamplingDecision.DROP);
            }
            return SamplingResult.recordAndSample();
        }
    );
}
```

**Оценка работы:** ~1 день (shared-observability bean + integration
tests per service).

---

## Real sparklines backend для admin-dashboard (v0.1)

**Источник:** `10-frontend-web-panel.md` QC7, `OWNER-ANSWERS.md`
NEW-94, отложено в M07 (placeholder «доступно в v0.1»).

**Текущее состояние (M07 G9, commit завершает милестоун):**
admin-dashboard показывает skeleton-bars + info-сообщение «Графики
посещаемости появятся в следующем релизе» вместо Chart.js с
псевдо-данными. `buildSpark` helper и `chart.js`/`ng2-charts`
import'ы в admin-dashboard удалены (chart.js остаётся transitive в
teacher-stats и student-stats, где используются реальные данные
per-subject). Реального time-series endpoint'а нет.

**Идея на будущее:** `GET /api/admin/dashboard/metrics` → time-series
агрегаты:
```json
{
  "active_users_7d": 245,
  "new_check_ins_24h": 1820,
  "red_zone_count": 12,
  "sparklines": {
    "check_ins_by_day": [{"day": "2026-04-15", "count": 1820}, ...],
    "active_users_by_day": [...]
  }
}
```

**Архитектурный выбор (NEW-94):**
- v0.1: миграция на **Prometheus** как источник time-series
  (уже запущен в M04 observability stack). Grafana-style query
  через `PrometheusMeterRegistry.query()` или proxy.
- **НЕ** делать SQL-агрегацию в attendance_db (MongoDB `$group`
  тяжёлый, блокировки при writes, через 3-6 месяцев всё равно
  переписывать).

**Оценка работы:** ~2 дня (Prometheus query client + caching +
frontend wiring).

---

## jqwik property-based testing для v0.1

**Источник:** M08 Группа 4 (2026-04-22), `docs/golden-tests.md`.

**Текущее состояние (v0.0.0):**
Property tests реализованы через standard JUnit + random loops +
`@RepeatedTest(100)`. Работает, но не даёт auto-shrinking
counter-examples и statistical distribution controls.

**Идея на будущее:**
- `testImplementation(libs.jqwik)` через `libs.versions.toml`.
- Migrate 2 property-test методов на `@Property` (WeekParityPropertyTest +
  DisplayNamePropertyTest).
- Добавить новые properties через generator composition
  (`@Arbitraries.strings().ofLength(2, 20)` + `@Combinators.combine(...)`).

**Что это даст:**
- Auto-shrinking — если property fails на `"Фёдоров Пётр"`, jqwik
  сам уменьшит до минимального failing input (`"А Я"` или проще).
- Statistical distribution — balance edge-cases (empty strings,
  unicode surrogates, null) vs normal inputs.
- Intentional reproducibility — seed logging для non-flaky runs.

**Blocker:** jqwik имеет известные конфликты с Spring Boot Test
resolver'ами. Требует отдельного runner setup. Не блокирует v0.0.0.

**Estimate:** ~0.5д.

---

## gRPC integration tests — real in-process channels вместо @MockitoBean (v0.1)

**Источник:** M08 Группа 2 @MockitoBean audit (2026-04-22,
docs/milestones/M08-test-infrastructure/NOTES.md).

**Текущее состояние (v0.0.0, M08):**
41 `@MockitoBean` в тестах, 37 из них мокают gRPC/external clients
(ScheduleGrpcClient, AcademicGrpcClient, SemesterCacheService). Это
правильный паттерн для current architecture — предотвращает
external dependency at test startup, изолирует тест от downstream
service availability.

**Blocker:** OWNER-ANSWERS P2-8/2 рекомендует «real Testcontainers
где possible, in-process gRPC для clients». Реализация требует
полноценной Группы 2.5 (~1-2 дня), которая не блокирует v0.0.0
release — accept'ится текущий hybrid-подход.

**Идея на будущее:**

1. `InProcessGrpcServerExtension` — JUnit 5 extension в
   `shared-test-containers`. Создаёт in-process gRPC server на
   unique name per test class, регистрирует bindable services
   (real implementations of Academic/Schedule gRPC services).
2. `@Bean @Primary` в testConfig — inject `InProcessChannelBuilder`
   вместо `NettyChannelBuilder`, подписывается на extension'ом
   созданный server-name.
3. Per-test — `InProcessGrpcServerRegistry` для установки
   per-method mock responses (вместо `when(grpcClient.getLesson())
   .thenReturn(...)` → `registry.register(LessonServiceGrpc.getGetLessonMethod(),
   ServerCalls.asyncUnaryCall(...))`).

**Что это даст:**
- Тесты проверяют real Protobuf serialization + HTTP/2 transport.
- Ловят incompatible proto changes (rename field, change type) —
  сейчас они проходят mimic'ом mock'а.
- Готовность к multi-module proto changes (академик → schedule
  ResolveLesson D-04).

**Estimate:** ~1-2 дня.
- 0.5д — Extension + testConfig scaffold.
- 0.5д — mock-response registry pattern.
- 0.5-1д — миграция 37 существующих @MockitoBean.

**Trigger:** после v0.0.0 release, когда появится реальная нужда
(proto breaking change проскочит в прод через mock).

---

## Full load-testing suite (Gatling / JMeter) для v0.1

**Источник:** M08 DECISIONS D2 (2026-04-22). OWNER-ANSWERS P2-8/7
разрешает v0.0.0 ограничиться minimal-only — k6 scripts + manual
прогон перед релизом. Полноценный нагрузочный стенд отложен.

**Текущее состояние (v0.0.0, M08):**
В `tests/load/` лежат 2 k6-скрипта (`bulk-mark.js`,
`geolocation-flood.js`) + `docs/performance-baseline.md` с первыми
числами p50/p95/p99. Release-engineer прогоняет руками перед каждым
release tag локально против `docker compose up`. CI load-job нет.

**Идея на будущее:**

1. **Gatling** или **JMeter** как основной инструмент для многосценарных
   нагрузок (параллельные потоки «checkin + excuse + schedule view»,
   realistic ramp-up curves, distributed load-generation).
2. **Nightly CI job против dev-инстанса на VPS** (после M12 когда
   prod stable) — ловит регрессии автоматически, trend-графики в
   Grafana через Prometheus exporter (Gatling умеет, k6 тоже через
   `--out experimental-prometheus-rw`).
3. **Dedicated load-runner** (self-hosted GitHub Actions runner на VPS)
   — CPU стабильнее чем GitHub-hosted (где железо плавающее).

**Варианты инструмента:**
- **Gatling** (Scala DSL) — красивые HTML-отчёты, хорош для complex
  scenario, steep learning curve.
- **JMeter** (XML + GUI) — зрелый, много плагинов, но XML-конфиги
  плохо review'ятся в PR.
- **k6 с расширенным scope** — docker-образ `grafana/k6` для CI,
  multiple `scenarios` в одном скрипте, тот же JS DSL. Наиболее
  реалистично если мы останавливаемся на k6 и не мигрируем.

**Что это даст:**
- Continuous detection регрессий между релизами (сейчас ловим только
  если release-engineer не забыл прогнать).
- Realistic load patterns (один k6-скрипт = один сценарий; в реальности
  старост одновременно отмечает, студент подаёт excuse, teacher
  смотрит журнал — k6 minimal scope это не моделирует).
- Trend-графики p95/error_rate по времени, видно «когда поехало».

**Trigger:** после tag `v0.0.0` (все M07-M12 закрыты) + минимум 2 недели
стабильности prod на VPS + накопленная статистика «какие endpoint'ы чаще
всего регрессируют».

**Estimate:** ~3-5 дней.
- 1д — выбор инструмента (k6 scale-up vs Gatling migration) + baseline
  migration из текущих 2 скриптов.
- 1-2д — setup dedicated runner + dev-инстанс deploy automation
  (зависит от M09/M12 VPS stability).
- 1-2д — Grafana dashboard + Prometheus integration + alert rules
  (p95 > threshold → Telegram через M04 Alertmanager).

**Зависимости:** M08 (baseline в `performance-baseline.md`), M09
(prod-deploy-checklist), M12 (VPS stable + dev-инстанс).

---

## NEW-146-checklist: Аудит-чеклист для `.collect(toList())` агрегации

Использовать при PR-ревью service-слоя:

- [ ] Если результат `.collect(toList())` передаётся в `.stream().count()` /
      `.stream().sum()` / `.stream().filter(...).count()` — это **hotspot**.
      Вариант: single-pass accumulator с `for` + int counter'ами (cheap,
      invariant-preserving) или SQL `GROUP BY` (если нет cross-service
      validation после load'а).
- [ ] Если результат `.collect(toList())` передаётся в `.subList(offset, end)`
      — это **in-memory pagination hotspot**. OOM-risk на 1000+ rows.
      Вариант: Spring Data `Pageable` с native `countQuery` (см.
      `LessonRepository.pageByScheduleItemIdInAndDateBetweenAndStatusIn`
      как reference в schedule-service).
- [ ] Если результат используется как `Map<K, V>` с агрегацией
      (count/sum) — `toMap(key, v -> seed, (a,b) -> merge)` уже
      single-pass, менять не нужно.

---

## OTP hardening bundle (v0.1, deferred из M09 G9 audit)

`security-auditor` + `bug-hunter` на M09 diff вытащили 2 HIGH и
несколько MEDIUM по OTP / rate-limit. BLOCK ни одного нет — теги
v0.0.0-alpha.10 валидный, но это всё должно быть закрыто до v0.0.0 GA.

**HIGH.1 — `verifyOtpByCode` без attempts counter (SA-H1).**
`POST /auth/otp/verify-by-code` ищет `otp_code:<code>` в Redis без
`otp_verify_attempts` счётчика. Единственная защита — Gateway
RequestRateLimiter 5 req/min per IP + `FailOpenRateLimiter` fail-open
при Redis outage. При distributed brute-force (botnet) 10^6 кодов
можно перебрать за часы; при одновременных live OTP (множество
parallel logins) birthday-speedup.

Fix план v0.1:
1. Добавить в `OtpService.verifyOtpByCode` counter по IP (через
   `HttpServletRequest` → resolve ip) — `otp_verify_by_code_miss:<ip>`,
   лимит 20 неверных в 5 минут → 429 «Too many verification attempts».
2. Metric `otp_verify_by_code_counter("mismatch"/"success"/"throttled")`.
3. Alert `OtpBruteForceSuspect` в Prometheus rule — `rate(otp_verify_
   by_code_counter{status="mismatch"}[5m]) > 10`.

**HIGH.2 — дубли `lesson.cancelled` событий в боте (BH-H1).**
`OutboxPublisherJob.publishBatch` делает `sender.send()` → `markSent`.
Если `markSent` падает (DB deadlock / connection reset) после того
как Rabbit принял message, запись остаётся `pending` → следующий tick
(5s) публикует её снова. Все consumer'ы получают duplicate, в том
числе бот. Attendance-service идемпотентен на status, но
`notification-bot handle_lesson_cancelled` не проверяет `event_id`
и дублирует `send_queue.put(...)` всем студентам → 2 одинаковых
Telegram-сообщения.

Тот же класс багов может задеть ЛЮБОЙ bot-consumer (lesson.started,
homework.published, group.renamed…).

Fix план v0.1 (dispatcher-уровня идемпотентность):
1. В `EventDispatcher.dispatch` до вызова handler'а — check
   Redis `SET NX PX` на ключе `event_processed:<event_id>` с TTL 24h.
2. Если set failed (уже processed) → skip handler + metric
   `event_duplicate_total{event_type}`.
3. Новая fixture в `conftest.py` + regression-тест в
   `test_event_dispatcher.py` на duplicate-delivery.

**MEDIUM bundle — attendance-consumer защитные проверки.**
1. **SA-M1** — `ExcuseService.applyDecisionFromBot` +
   `LateCheckinService.applyDecision` не валидируют что
   `decision_by.telegram_id` принадлежит старосте ТОЙ группы, что
   студент. `_verify_headman` в боте проверяет global `is_headman`,
   но не group-ownership. Fix: gRPC `academic.isHeadmanOfGroup(
   user_id, ticket.group_id)` до `save + cascade`.
2. **SA-M5** — `decisionBy` хранится как telegram_id (9-10 digit), не
   user_id. Audit trail ломается — нельзя join с users. Fix: resolve
   через academic_client до save.
3. **SA-M4** — OTP plaintext в fanout exchange. Любой consumer
   автоматически bind'нутый к `rut-uit.events` читает `otp.requested`.
   Fix: перейти на topic exchange с routing key filtering, bot
   подписывается только на `otp.*` / other-services — на остальное.
4. **SA-M3** — `FailOpenRateLimiter` fail-open на Redis outage.
   Для critical paths (`/auth/otp/*`, `/auth/login`) — fail-closed
   или in-memory backup bucket. Alert `RateLimiterFailOpen`.
5. **BH-M2** — OTP race: два параллельных `/otp/request` от same
   telegram_id (RL bypass через разные IP) → два live кодов в
   `otp_code:*`. Fix: Lua script atomic step для reserve-cooldown-
   generate, или `setIfAbsent("otp_lock:"+tid, ..., 2s)` serialize.
6. **BH-M5** — orphan pending_user_msg в `OtpMessageTracker` при
   double /login или auth-fail между первой и второй отправкой.
   Fix: List-append вместо overwrite, cleanup-по-list.
7. **BH-M6** — auth/notification-web `mem_limit: 256m` + heap 75%
   тесновато. Native (classloader + metaspace + threads + STOMP
   broker state) может съесть 120+ MB. Fix: поднять до 384m / 512m
   или снизить MaxRAMPercentage до 60%.

**Оценка bundle:** 2-3 дня (1д на OTP hardening, 1д на dispatcher
дедуп + regression, 0.5-1д на остальные).

---

## Notification retention collMod auto-reconciler (v0.1)

**Источник:** M10 G1 D4.

В M10 TTL индекс на `notification_history.sent_at` создаётся один раз
при bootstrap со значением из `NOTIFICATION_HISTORY_TTL_DAYS` (default
30d). Изменить retention — требует ручной команды `collMod` или drop+
recreate индекса. Это OK для редких изменений (compliance retention
раз в год), но не OK для dynamic tuning.

**Идея v0.1:** при старте notification-web в `@PostConstruct` detect
существующий TTL `expireAfterSeconds` через `db.runCommand({listIndexes:
"notification_history"})`. Если отличается от env value →
`db.runCommand({collMod: "notification_history", index: {name:
"sent_at_ttl", expireAfterSeconds: newValue}})`. Это pattern Atlassian
/ Stripe для tunable retention без downtime.

**Оценка:** 0.5 дня + IT тест на TTL reconciliation.

---

## Notification history — bundle deferred из M10 G9 audit (v0.1)

**Источник:** M10 G9 security-auditor + bug-hunter findings
(`docs/milestones/M10-notification-history/NOTES.md` post-mortem).
HIGH (H1 status mapping, H2 Pageable cap) пофикшены hot-patch'ами в
G9. Ниже — MEDIUM/LOW которые не блокируют v0.0.0.

### N1. Payload field allow-list (security MEDIUM)

`NotificationHistoryConsumer` сейчас сохраняет `payload` целиком как
`Map<String,Object>`. При расширении event-schemas (например,
`excuse.requested` обогатится `lessons[].subject_name` или
`decision_comment` с ПДн) — попадёт в Mongo + HATEOAS API без
переоценки. Добавить explicit per-NotificationType `Map<NotificationType,
Set<String>>` allow-list ключей перед `repository.save`.

### N2. Transient Mongo failure differentiation (bug MEDIUM)

`NotificationHistoryConsumer.onEvent` swallow'ит все exceptions. Это
правильно для permanent bug (требуется DLQ replay), но не для
transient `MongoException`/`DataAccessException` (network blip,
primary failover). Разделить: rethrow transient → Rabbit retry/DLQ
nackback; swallow permanent → warn + counter
`notification.history.persist_failed{event_type}`.

### N3. ObjectId format validation (bug MEDIUM)

`NotificationController.markAsRead(id)` — невалидный ObjectId-like
string бросит `IllegalArgumentException` из Mongo driver → 500 вместо
403. Catch + нормализовать к 403 (или 400 BAD_REQUEST).

### N4. extractUserId String-tolerance (bug MEDIUM)

`extractUserId` принимает только `Number`. Если future producer пришлёт
`"user_id": "42"` (String) — silent skip с warn'ом. Добавить
`String → Long.parseLong` fallback.

### N5. CSRF documentation для notification mutations (security LOW)

`POST /notifications/mark-all-read` и `PATCH /notifications/{id}/read`
сейчас защищены Bearer header (не cookie). При future миграции на
cookie-auth важно не упустить добавление CSRF token. Документировать
в `docs/notification-history.md` (создать) или в auth-flow.md.

### N6. DLQ retention для notification-web.history.dlq (security LOW)

`notification-web.history.dlq` declared без `x-message-ttl` /
`x-max-length` — растёт бесконечно. Добавить retention args (или
periodic cleanup job).

### N7. Cache evict exception isolation (bug LOW)

`NotificationHistoryService.invalidateUnreadCount` — если evict
throws (cache disposed at shutdown) — exception пузырится из
`onEvent` → catch'ится в Consumer swallow. Не критично, но добавить
inner `try { cache.evict } catch { log.warn }`.

### N8. PWA optimistic update + STOMP race (UX LOW)

`NotificationCenter.tsx markAllRead`: между `setItems(local)` и
`invalidateQueries(success)` может прилететь STOMP event → unread badge
мигнёт 0→1→0. Добавить cancel mutation если новый event прилетел.

### N9. Multi-instance createCollection TOCTOU (LOW, при scale-out)

`NotificationHistoryMongoConfig` — между `collectionExists` и
`createCollection` есть race window. На single-instance MVP — OK; при
scale-out нужен `try/catch (NamespaceExistsException)`. Caffeine cache
тоже single-instance assumption (см. CaffeineConfig javadoc) —
комплекс мер при scale-out объединить (Caffeine→Redis +
createCollection idempotency + STOMP cluster sync).

### N10. Server-side notification history infinite-scroll UI (UX, M10 D7)

PWA + web-panel на v0.0.0 показывают broadcast events из
sessionStorage; backend-history hook (`useNotificationHistory`) готов,
но не интегрирован в UI как primary source. v0.1: переключить
NotificationCenter на server-side useInfiniteQuery + optimistic mutations.

### N11. Headman-facing items (M10 D6)

`excuse.requested` сейчас persist'ится только инициатору-студенту
(D6). Староста видит этот item только через live STOMP push, без
history. v0.1: gRPC resolve `headman_id` по `group_id` →
дополнительный persist для actionable items.

**Оценка пакета (N1-N11):** 5-7 человеко-дней.

---

## docker-compose build context fix для notification-web (M10 G9 S5)

**Источник:** M10 G9.2 smoke discovery (2026-04-24).

`docker-compose.yml:155` — `notification-web.build.context:
./services/notification-service/notification-app`. Но
`services/notification-service/notification-app/Dockerfile` референсит
файлы относительно monorepo root (`COPY gradlew .`,
`COPY services/notification-service/...`). Build из docker-compose
падает с `failed to compute cache key... not found`.

Текущий running image (`rutcampustrack-notification-web` 2 weeks old)
сбилжен где-то иначе (CI? ручной build?), но `docker compose build
notification-web` локально не работает с момента создания.

**Fix (1 minute):**
```yaml
notification-web:
  build:
    context: .
    dockerfile: services/notification-service/notification-app/Dockerfile
```

То же может понадобиться для других сервисов (audit `docker-compose ls
build context` + сравнение с Dockerfile COPY paths).

**Когда делать:** при следующем M10-orthogonal sweep'е по infrastructure
(M11+ либо v0.1).

---

## Pre-v0.1 (post-M14, после first VPS deploy)

Источник: четыре аудита 2026-04-26 (`docs/milestones/M13-pre-deploy-hardening/G27-cso-comprehensive-audit.md`,
`G27-tech-debt-audit.md`, `G26-code-review-after-g25.md`, `G26-test-audit-findings.md`).
M14 закрыл только блокеры first deploy. Эти пункты — следующий sweep, когда
сервис заработает в проде и появятся real-user signal'ы (Grafana, инциденты,
обратная связь).

**Когда делать:** week 1-2 после first deploy v0.0.0 GA, либо отдельный
M15 «Post-Deploy Cleanup» если накопится критическая масса.

### Frontend — Web-panel

#### v0.1: Headman bulk-mark UI в web-panel

**Текущее состояние (v0.0.0):**
Backend полностью готов:
- `MarkingApi.batchMark()` endpoint в attendance-service
- `MarkBatchRequest` / `MarkBatchResponse` / `MarkBatchItem` DTO
- ScheduleGrpcClient + retention/idempotency contracts покрыты тестами

UI bulk-mark в web-panel **не реализован**:
- `headman-dashboard.component.ts` — только stat-cards (memberCount,
  pendingExcuses), нет lesson-card listings
- `headman-schedule.component.ts` — slot-dialog для CRUD занятий, но
  нет BottomSheet с «Отметить всех» для активных пар
- group-attendance-count stat не выводится на dashboard
- grep по всему `frontends/` (web-panel + pwa): 0 hits для
  `bulk-mark`/`markAll`/«Отметить всех» в UI коде

**Primary path для bulk-mark в v0.0.0** — Telegram bot
(`services/notification-bot/bot/handlers/headman_actions.py`),
УЖЕ реализован, покрыт pytest, доступен через `/start` → меню старосты.

**Тест в backlog:** `tests/e2e/specs/headman-mark.spec.ts` —
помечен `test.describe.skip` в M14 G7. Восстановить после реализации UI.

**Что нужно для v0.1:**

1. **UX review** с пользователем: где список активных пар (на
   `/headman/schedule` или отдельно `/headman/lessons`)? BottomSheet vs
   Material Dialog для action-sheet? Live-update счётчика через STOMP
   subscribe (если headman сидит на dashboard) или polling?

2. **Lesson-card listings** — компонент `<app-headman-lesson-card>`,
   статусы (planned/active/closed/cancelled), CTA «Отметить всех» только
   для active.

3. **Bulk-mark sheet** — список студентов группы с чекбоксами +
   default-state «все present», submit → `attendance.markBatch()` (через
   StudentApiService + gRPC).

4. **Group-attendance-count stat** — новый `<app-stat-card>` на
   `headman-dashboard` показывает «X / Y студентов отмечено сегодня»
   с live-update через STOMP `lesson.marked` events.

5. **WebSocket integration** — subscribe на `/topic/headman/{groupId}/marks`,
   при events update счётчиков (M07 STOMP unified service).

6. **Tests** — unit (Karma) + integration (TestBed) + восстановление
   `headman-mark.spec.ts` Playwright e2e.

**Оценка:** 6-10 ч feature work (включая UX iteration с пользователем).
Не блокирует v0.0.0 deploy — bot path для bulk-mark уже работает.

**Когда делать:** после v0.0.0 GA когда (a) есть real headman users
которые попросили web UI вместо/вместе с bot, либо (b) выявлены
сценарии где bot UX недостаточен (например, когда нужно отметить большую
группу с per-student override → удобнее на широком экране).

---

### Безопасность

#### MED-08: Реальный audit log вместо `@AdminAction` aspect-заглушки (CSO + tech-debt F02)

**Текущее состояние:** `@AdminAction` annotation существует, но не используется
ни в одном controller/service. `AdminActionAspect.around()` пишет
`log.debug("@AdminAction pointcut hit: ...")` и `proceed()`. Обещано в M04
(Observability), но не реализовано.

**Идея:** написать реальный handler — запись в `audit_log` таблицу (PG)
+ JSON channel в Loki, include `user_id` + before/after diff +
`correlation_id`. Loki retention 90+ days. Помечать `@AdminAction("user.archive")`
все ADMIN-методы (academic ~15 endpoints).

**Что закроет:** insider threat detection, forensics при инциденте,
compliance (если когда-то понадобится).

**Когда делать:** не нужно пока нет реальных users + ADMIN actions с impact.

---

#### MED-11: mTLS Alertmanager → notification-web (вместо Bearer over cleartext в private_net)

**Текущее состояние:** Alertmanager шлёт POST `http://notification-web:9094/internal/alert`
с `Authorization: Bearer`. Bearer защищён (timing-safe `MessageDigest.isEqual`),
но transport — plaintext HTTP. Combined с CRIT-01 (флипнут в M14) impact
снизился, но в comment'е alertmanager.yml уже зафиксировано «M06 заменит на mTLS».

**Идея:** Linkerd sidecar с auto-mTLS, либо custom certs + nginx proxy с
client-cert auth. Pre-step: cap_drop NET_RAW в node-exporter/cadvisor
(блокирует sniffing capability у потенциального compromised peer).

**Когда делать:** при подготовке к horizontal scaling либо после first incident.

---

#### MED-12: cadvisor — убрать `privileged: true`

**Текущее состояние:** `privileged: true` + mounts `/:/rootfs:ro` + `/var/lib/docker:ro`.
Любой compromise cadvisor = root host access. Image SHA-pinned (M06 D2),
без known unpatched RCEs в v0.49.1.

**Идея:** заменить на `cap_add: [SYS_PTRACE]` (cadvisor docs allow). Drop
`/var/lib/docker` mount если не нужен (audit metric coverage без него).

**Когда делать:** часть «container hardening sweep» в pre-v0.1.

---

#### MED-14: Cosign-verify config files (`infra/`, `nginx/`, `scripts/`)

**Текущее состояние:** `deploy.yml` SSH step делает `git pull --ff-only`
на VPS — притягивает infra configs БЕЗ cosign signature check (только
images verified). Если attacker compromise main branch (через CI cascade),
malicious nginx config может proxy_pass на attacker upstream.

**Идея (3 варианта):**
1. Commit `infra/`, `nginx/`, `scripts/` в специальный `config` image, cosign-sign и verify.
2. Sign git commits + `git verify-commit HEAD` перед apply.
3. CODEOWNERS на `infra/`, `nginx/`, `scripts/` + required reviews.

**Когда делать:** вместе с general hardening sweep, когда добавим second maintainer.

---

#### MED-13: Caffeine cache для last-known-good public key в `PublicKeyProvider`

**Текущее состояние:** G25.22 fix добавил synchronous lazy retry если
`init()` failed (auth-service не ready при старте downstream). Между
container start и first authenticated request — окно ~1-30 сек где
downstream бросает 500. Не security exploit, availability concern.

**Идея:** добавить Caffeine cache с TTL 24h на last-known-good public key.
Fail-CLOSED семантика остаётся (key=null → IllegalStateException), но
после первого успеха downstream tolerate auth-service downtime.

**Когда делать:** если в Grafana увидим notable количество 500 от
downstream после rolling restart.

---

#### MED-15: pre-commit hook «`.env.prod` must NOT exist in working copy»

**Текущее состояние:** `.env.prod` gitignored ✅, но если разработчик
случайно zip'нет working copy / share / ноут украдут — leak. Audit doc
зафиксировал что `.env.prod` лежал в working copy с реальными секретами
prod (BOT_TOKEN, GHCR_TOKEN, DB passwords и т.д.).

**Идея:** pre-commit hook + dev workflow:
1. `.env.prod` ТОЛЬКО на VPS, не на dev машинах.
2. `scp` или 1Password CLI / `pass` / Bitwarden для retrieval когда нужно.
3. Pre-commit hook: `[ ! -f .env.prod ] || exit 1` (с явным error message).
4. Ротация всех secrets из `.env.prod` после first deploy если они хоть
   раз были на dev машине.

**Когда делать:** часть pre-v0.1 hardening.

---

#### TENT-16: Удалить dev CORS origins из base `application.yml` Gateway

**Текущее состояние:** `services/api-gateway/src/main/resources/application.yml:23-30`
содержит 6 hardcoded `http://localhost:*` origins + `${CORS_ALLOWED_ORIGIN:...}`.
`application-prod.yml:8` overrides, но Spring profile-specific properties
для list types ведут себя неочевидно (replace vs merge).

**Идея:** удалить dev origins из base, оставить только в `application-dev.yml`.
Базовый default = production-only.

**Когда делать:** zero-cost cleanup, можно сделать в любом следующем PR
trogающем gateway config.

---

### Поддерживаемость / supply chain

#### MED-09: SHA-pin gitleaks/gitleaks-action (если не сделано в M14)

**Заметка:** включено в M14 G6, но если пропустили — здесь.

---

#### MED-10: Миграция с `bitnamilegacy/mongodb:7.0` → `mongo:7-jammy` + custom init script

**Текущее состояние:** В August 2025 Bitnami убрали versioned tags из
bitnami/. `bitnamilegacy/` — официальный frozen fallback **без security
updates**. Acceptable risk зафиксирован (single-node RS, internal network only),
но combined с CRIT-01 (флипнут в M14) — argument «нельзя атаковать извне»
slightly weaker.

**Идея:** переехать на upstream `mongo:7-jammy` + custom entrypoint c
`rs.initiate()` init-script, либо `percona/percona-server-mongodb:7`.
Re-evaluate через 3 месяца после v0.0.0 GA.

**Когда делать:** при первом CVE на bitnamilegacy либо через 3 месяца
после deploy (whichever first).

---

### Code review P2/P3 (G26)

12 находок log-noise / комментарии / закомментированный YAML — собрать
в один **cleanup PR** после first deploy:

- F04-F07, F11, F15: понизить `log.info` → `log.debug` в `JwtService` (RSA parsed bytes, kid resolved, caching public key) + сократить Javadoc `nonBlockingSecureRandom()` (убрать G25.13/G25.14 history) + сократить inline-комментарии M13 G25.NN.
- F08: удалить закомментированный YAML блок `# default-filters: # - DedupeResponseHeader=...` (комментарий-объяснение оставить).
- F09: заменить `NEW-XXX` placeholder на реальный issue номер, либо `TODO(backlog)` без псевдо-номера.
- F10: упростить `kid.txt` генерацию в Dockerfile — `openssl rand -hex 4` вместо `head -c 4 | od | tr` pipeline.
- F12: убрать упоминание `CSRF token generator` из comment в `AuthApplication.main()` (auth-service stateless, нет CSRF).
- F13: добавить issue number к `test.skip` для headman color-contrast теста + сократить runtime skip message.
- F14: ruff reformatting в `test_callback_excuse.py`/`test_callback_late_checkin.py` — оставить как есть, P3 cosmetic.

**Когда делать:** один cleanup PR после first deploy (~1ч), low risk.

---

### E2E / unit test cleanup (G26 P2)

- **Cat. C:** `auth-token-lifecycle.spec.ts:122` — заменить `waitForTimeout(5_000)` + `reload` на `setOffline(false) → networkidle`.
- **Cat. D:** `auth.spec.ts:101-106` `test.skip(true, ...)` headman color-contrast — убрать или завести GitHub issue с условным skip.
- **Cat. F:** `login.component.spec.ts:121` — переименовать test description на `setAccessToken` (assertion корректна, но название вводит в заблуждение).
- **Cat. G:** `NotificationHistoryConsumerIT` — заменить `Thread.sleep(1500)` на Awaitility `await().during(1, SECONDS).atMost(3, SECONDS)`.
- **Cat. H:** `StompIntegrationIT` — заменить `Thread.sleep(300)` перед subscribe на `CountDownLatch`/`BlockingQueue.poll`.
- **Cat. I:** `test_idempotency_guard.py` — заменить `@pytest.fixture` на `@pytest_asyncio.fixture` для async coroutine (5 мин fix, можно сделать сразу при первом сбое).
- **Cat. J:** `AuthOtpFlowIT` — вынести Rabbit-aware базовый класс `AbstractAuthEventIntegrationTest extends AbstractIntegrationTest`.
- **Cat. K:** `RestApiIT` — убрать `@TestMethodOrder` + перенести seed/teardown в `@BeforeEach`/`@AfterEach`.

**Когда делать:** один тестовый cleanup PR (~2ч), когда подготовка к
horizontal scale потребует надёжных integration tests.

---

### ~~CSO HIGH-06: fail-fast secrets через ApplicationContextInitializer~~ ✅ ЗАКРЫТО в M14 G4 v2

**Закрыто 2026-04-26** через `RequiredSecretsValidator` (`EnvironmentPostProcessor`)
в `shared-web/autoconfigure/`. Commit `bf915ec`.

Оригинальная идея этого entry была написана после v1 attempt (~1ч),
который провалился из-за Spring Boot ограничения. По pushback пользователя
("давай сделаем правильнее ... зачем плодить legacy") G4 закрыта в той
же сессии через **EnvironmentPostProcessor** (вместо изначально
предложенного `ApplicationContextInitializer` — EnvPostProcessor
работает раньше, ДО Spring banner и bean creation).

См. `docs/milestones/M14-post-audit-fixes/NOTES.md` § "Group 4 v2 — SUCCESS"
для полного post-mortem.

---
