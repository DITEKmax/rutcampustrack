# M01 — Shared Foundations

**Статус:** ⏳ в работе
**Старт / финиш:** 2026-04-19 / —
**Estimate:** 5-7 человеко-дней

---

## Scope

Создание четырёх shared-модулей, на которые опираются все последующие
milestones. После M3 половина AUTO-RESOLVED пунктов из `COVERAGE-AUDIT.md`
становится реально закрытой.

**Закрывает полностью:**
- **Q16a** shared-web (GlobalExceptionHandler + ErrorResponse RFC 7807)
- **NEW-34, NEW-35** правила shared-web (без autoconfig, подключение 5 сервисов)
- **NEW-60** shared-events (DomainEvent base с event_version/trace_id/occurred_at/source)
- **NEW-68** shared-logback (JSON encoder + MaskingConverter)
- **NEW-158** shared-test-containers (Testcontainers fixtures для 5 сервисов)

**Закрывает частично (создаёт инфраструктуру, миграция сервисов — в последующих milestones):**
- **05 P0-2 / C1-11** GlobalExceptionHandler в notification-service
- **P2-3/1** correlation=<trace_id> в 500-ответах
- **P2-3/2** RFC 7807 `invalid-params[]` для validation
- **P2-3/3** полный набор `@ExceptionHandler` (9 handler'ов)
- **P2-4/1** custom cross-field annotations (`@StartBeforeEnd`, `@DateRangeValid`)
- **P2-4/6** `@ValidFile` annotation
- **P2-4/8** Jackson `READ_UNKNOWN_ENUM_VALUES_AS_NULL` globally
- **P2-6/1** Logback MaskingConverter (Bearer / telegram_id / FCM endpoint)
- **QA7** unified JSON structured logs
- **P2-8/2** Testcontainers fixtures (hybrid real DB/Rabbit + gRPC in-process + WireMock)

## Модули / изменения

### 1. `services/shared/shared-web/` (новый)

Gradle `java-library` без Spring Boot autoconfiguration. Подключается к
5 сервисам как обычная зависимость (NEW-34).

- `GlobalExceptionHandler` (`@RestControllerAdvice(order=1)`) — 9 handler'ов
  (P2-3/3) + catch-all Exception с `correlation=<trace_id>` (P2-3/1).
- `ErrorResponse` record (canonical из academic) + `InvalidParam` record
  для validation errors (P2-3/2).
- `validation/` пакет (P2-4/1):
  - `@StartBeforeEnd` + validator
  - `@DateRangeValid` + validator
  - `@ValidFile(maxSizeBytes, allowedMediaTypes)` + validator (P2-4/6)
- `JacksonConfig` — `READ_UNKNOWN_ENUM_VALUES_AS_NULL` + `FAIL_ON_UNKNOWN_PROPERTIES=false`
  (P2-4/8 + P2-1/5).
- `OpenApiCustomizer` для будущего M6 (заглушка-bean, наполнение в M6 P2-2/1).
- `@AdminAction` aspect (NEW-72) — заглушка, реализация в M4 (audit через Loki).

### 2. `services/shared/shared-events/` (новый)

Gradle `java-library` без Spring Boot. Минимум классов — только базовые
DTO/utility для DomainEvent.

- `DomainEvent` abstract / record — поля `event_version`, `trace_id`,
  `occurred_at`, `source` (P2-1/5 + QA3 + NEW-60).
- `AbstractEventPublisher` — auto-заполняет поля из MDC (если Sleuth
  активен, заглушка иначе). Интеграция с AMQP — в M2.
- `AbstractEventConsumer` — вытаскивает trace_id и кладёт в MDC перед
  handler'ом.

### 3. `services/shared/shared-logback/` (новый)

Gradle `java-library`, содержит XML-конфиги и custom appender'ы.

- `logback-base.xml` — подключается через `<include resource="shared/logback-base.xml"/>`.
- JSON encoder через `net.logstash.logback:logstash-logback-encoder`.
- `MaskingProvider` с regex-паттернами (P2-6/1):
  - `Bearer\s+eyJ[...]` → `Bearer ***`
  - `"telegram_?id"\s*:\s*\d+` → `"telegram_id":***`
  - `https://fcm\.googleapis\.com/[^\s"]+` → `https://fcm.googleapis.com/***`
- Unified labels: `ts`, `level`, `msg`, `service`, `trace_id`, `user_id`,
  `event_type` (QA7 + NEW-68).

### 4. `services/shared/shared-test-containers/` (новый)

Gradle `java-library` (testFixtures scope), dependency только Testcontainers
+ JUnit 5 API.

- `ContainerTestBase` — abstract class с `@Container` fixtures:
  - `PostgreSQLContainer` (reuse: true)
  - `MongoDBContainer` (reuse: true)
  - `RedisContainer` (reuse: true)
  - `RabbitMQContainer` (reuse: true)
  - `@DynamicPropertySource` для `spring.datasource.url` / `spring.rabbitmq.*` / etc.
- `GrpcInProcessFixture` — helper для real gRPC через `InProcessChannelBuilder` (P2-8/2).
- `WireMockFixture` — helper для HTTP-моков (P2-8/2).
- `MigrationTestUtils.runMigrationsUpTo(version)` — для data-preservation
  тестов (P2-8/3, пригодится в M7).

### 5. `settings.gradle.kts`

Подключить новые 4 модуля:
```kotlin
include(
    "services:shared:shared-web",
    "services:shared:shared-events",
    "services:shared:shared-logback",
    "services:shared:shared-test-containers",
)
```

### 6. Миграция notification-service (единственная в M01)

notification-service сейчас без GlobalExceptionHandler (05 P0-2 / C1-11).
Подключаем shared-web первым — это acceptance-тест что shared-web работает.

Остальные 3 сервиса (academic, schedule, attendance) у них уже есть свои
копии handler'ов. Миграция на shared-web — отдельная задача в M04 (чтобы
не раздувать M01). В M01 — только подключение без замены. Drift между
копиями handler'ов остаётся до M04.

## Acceptance criteria

- [ ] `./gradlew build` зелёный для всех 4 новых модулей.
- [ ] notification-service: `POST /api/notifications/` c невалидным body
      → `application/problem+json` RFC 7807 `invalid-params[]` (ранее был
      generic 500).
- [ ] notification-service: `log.info("user", userId, "bearer", jwt)`
      в JSON stdout → поле `bearer` маскировано как `***`.
- [ ] Тест `NotificationIntegrationIT` использует `ContainerTestBase`
      (real Mongo + Rabbit через Testcontainers, НЕ `@MockitoBean`).
- [ ] Пример publisher/consumer (заглушка в test-модуле) публикует/читает
      событие с автозаполнением `trace_id`/`occurred_at`/`event_version`
      из `AbstractEventPublisher` / `AbstractEventConsumer`.
- [ ] Javadoc на публичных API каждого модуля (короткий — 2-3 строки на класс).
- [ ] `docs/architecture.md` — раздел «Shared modules» с описанием 4 модулей.
- [ ] `CHANGELOG.md` → `[Unreleased]` → запись «Added: shared-web, shared-events, shared-logback, shared-test-containers».

## Dependencies

- **Блокирует:** M02, M03, M04, M05, M07, M08 (все опираются на shared-*).
- **Блокируется:** ничем (foundation layer).
- **Parallel safe:** M06 (ops & supply chain — полностью независим).

## Artifacts

- `services/shared/shared-web/` (~400-600 LOC кода + tests)
- `services/shared/shared-events/` (~150-250 LOC)
- `services/shared/shared-logback/` (~100 LOC + XML)
- `services/shared/shared-test-containers/` (~200-300 LOC)
- `docs/architecture.md` — раздел «Shared modules»
- `docs/shared-modules-usage.md` — как подключать к сервису (1-страничный гайд)
- Пример интеграции: notification-service на shared-web

---

## Post-mortem

**Статус:** ✅ завершён 2026-04-19 (один день, 9 атомарных коммитов).

### Измерения

- **Код:** 4 shared-модуля + 1 сервис-потребитель (notification-service).
  ~2600 LOC (+тесты), распределение близко к прогнозу PLAN.md.
- **Тесты:** 75 новых unit+integration в shared-модулях, 6 новых в
  notification-service (acceptance). `./gradlew build` зелёный (1m 1s),
  0 failures во всём репозитории.
- **Commits:** 10 атомарных коммитов (8 групп кода + docs + final fixes).
- **Длительность:** один рабочий день (план: 5-7 д.). Быстрее, потому что
  аудит сделал research (OWNER-ANSWERS.md дал все архитектурные ответы
  заранее), а существующий `ErrorResponse` в academic дал canonical-reference.

### Что пошло по плану

- Все 8 CHECKLIST-групп выполнены с минимальными отклонениями.
- Notification-service acceptance прошёл с первого прогона после Testcontainers
  и mock PushService — integration-тесты реально запустили Mongo+Rabbit.
- Version Catalog (gradle/libs.versions.toml) оправдал себя — единое место
  версий shared-модулей, готово к Renovate в M06.

### Surprises / отклонения от PLAN.md

1. **ErrorResponse — 9 полей вместо 8.** PLAN.md предполагал
   `(status, type, title, detail, instance, timestamp, traceId, invalidParams)`,
   но academic-service уже имел `field` + `extras` для BUG-006-2 и каскадного
   удаления. Сохранили оба (Вариант B в DECISIONS.md). Миграция фронтов
   (rename `fieldErrors` → `invalidParams`) откладывается до M04.
2. **InvalidParam — 3 поля вместо 2** (добавлен опциональный `rejectedValue`)
   — zero-cost, сохраняет debug-полезный `FieldError.rejectedValue` из legacy.
3. **notification-service — два пункта CHECKLIST оказались N/A.** «Удалить
   локальный error-handling» и «Мигрировать `NotificationIntegrationIT` с
   @MockitoBean» — этих артефактов в сервисе не было. Вместо миграции —
   `NotificationErrorHandlingIT` сразу расширяет `ContainerTestBase`.
4. **Notification-service получил `spring-security-core`** в runtime.
   shared-web требует `AccessDeniedException` для handler'а; без этого
   класса Spring падает на рефлексии. Остальные 4 сервиса при миграции
   в M03/M04 получат то же самое.
5. **shared-events: testImplementation jackson** явно добавлены — без этого
   `spring-boot-starter-test` не тащит `JavaTimeModule` транзитивно.
6. **AOP-тест `@AdminAction`** — пришлось переписать с counter-based
   assert'а (CGLIB proxy не делит state с target) на `AopUtils.isAopProxy`.

### Bug-hunter findings (инвестированы обратно в M01)

Вызван bug-hunter subagent на diff milestone'а. 7 находок, разбивка:
- **Исправлено сейчас (3):** `NotificationExceptionHandler` теперь использует
  FQN и `HIGHEST_PRECEDENCE + 100` offset; `JacksonConfig` получил
  `@Order(LOWEST_PRECEDENCE)` чтобы сервис-локальные customizers имели
  приоритет; `GrpcInProcessFixture.startServer` больше не оставляет
  half-started server в undefined state при IOException.
- **Задокументировано как known limitations (3):** regex маскирует только
  поле `msg` (stack traces — backlog M04); `Bearer regex` узкий (только JWT);
  `telegram_id` переписывает имя поля. Всё в `shared-logback/README.md`.
- **Отложено до M04/M08 (1):** `ContainerTestBase` static containers без
  `.stop()` + требует `.testcontainers.properties` на dev-машинах — CI
  работает через Ryuk, dev требует manual setup. Документация уже есть
  в README, улучшение фиксирован lifecycle будет в M08 (Playwright + coverage-gate).

### TODO для следующих milestones

- **M02:** подключить `AbstractEventPublisher`/`Consumer` к реальному
  RabbitListener (сейчас чистое API). Добавить contract-тесты JSON Schema.
- **M03:** мигрировать academic/schedule/attendance на shared-web
  (drift handlers). Добавить Spring Security + Internal JWT.
- **M04:** реальный audit handler за `@AdminAction`. Расширить masking
  на stack traces. Использовать capture-group в telegram_id regex.
- **M06:** наполнить `SharedOpenApiCustomizer` — единое описание
  стандартных 4xx/5xx errors со ссылкой на shared `ErrorResponse` schema.
- **M08:** пересмотреть `ContainerTestBase` static lifecycle, добавить
  `@EnabledIfDockerAvailable` для smoke-тестов.
