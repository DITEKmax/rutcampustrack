# M3 — Shared Foundations

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

### 6. Миграция notification-service (единственная в M3)

notification-service сейчас без GlobalExceptionHandler (05 P0-2 / C1-11).
Подключаем shared-web первым — это acceptance-тест что shared-web работает.

Остальные 3 сервиса (academic, schedule, attendance) у них уже есть свои
копии handler'ов. Миграция на shared-web — отдельная задача в M4 (чтобы не
раздувать M3). В M3 — только подключение без замены. Drift между копиями
handler'ов остаётся до M4.

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

- **Блокирует:** M1, M2, M4, M5, M6, M7 (все опираются на shared-*).
- **Блокируется:** ничем (foundation layer).
- **Parallel safe:** M8 (ops & supply chain — полностью независим).

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

_Заполняется в конце milestone'а (измерения, surprises, что пошло не
по плану, что надо исправить в следующих milestones)._
