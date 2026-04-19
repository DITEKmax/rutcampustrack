# Changelog

Все заметные изменения в проекте RutCampusTrack документируются здесь.

Формат: [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/).
Версионирование: [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **M03a Internal JWT + Rate-limiting** — Zero Trust Level 2 + brute/DoS защита
  (tag `v0.0.0-alpha.3`). Закрывает 02-Q2, кластер C0-1, C0-4, NEW-3, NEW-4,
  NEW-9, NEW-11, 14 P1-1, 14 P1-2.
  - `services/shared/shared-security/` — Internal JWT validator библиотека
    (`InternalJwtValidator`, `PublicKeyProvider` RestClient,
    `DualModeUserContextFilter` abstract, `InternalJwtTestFactory` testFixtures,
    `InternalJwtProperties`). 18 unit-тестов.
  - `auth-service` — `POST /internal/issue-internal-jwt` token-exchange endpoint
    (RFC 8693 pattern). `InternalIssuerSecretFilter` с timing-safe
    `MessageDigest.isEqual`. `JwtService.generateInternalToken` (RS256, TTL 5
    мин, aud=`rutcampustrack-internal`). 11 тестов.
  - `api-gateway` — `InternalJwtIssuerClient` (WebClient + Caffeine `AsyncCache`
    `(userId, role) → IssuedToken` TTL 240s) + `InternalJwtIssuerFilter`
    (GlobalFilter order=-50) прокидывает `X-Internal-Token` downstream.
    Error 503 на недоступность auth-service. 19 тестов через WireMock.
  - **Downstream миграция** (academic/schedule/attendance/notification):
    каждый имеет `{Service}UserContextFilter extends DualModeUserContextFilter`,
    `InternalJwtConfig` (+PublicKeyProvider bean), `InternalJwtTestConfig`
    (@Primary in-memory keypair для IT). IT-тесты dual-mode + strict-mode
    (9+3 per service). Build: academic 197, schedule 108, attendance 157,
    notification 59 тестов.
  - **Rate-limiting Gateway** (Spring Cloud Gateway `RedisRateLimiter`):
    - 4 `KeyResolver` бина: ip / userId (X-User-Id) / login (X-Login) /
      `ip+login` composite.
    - `@Primary FailOpenRateLimiter` обёртка — на Redis-outage
      (`RedisConnectionFailureException`, Lettuce timeouts) возвращает
      `allowed=true` + WARN + `X-RateLimit-FailOpen: true`.
    - 6 rate-limited роутов: `/auth/otp/request` 1/burst IP, `otp/verify-by-code`
      5/burst IP, `/auth/login` 5 IP + 10 composite, `/auth/refresh[-body]`
      30/user, `/attendance/check-in` 10/user, `/api/{academic,schedule,
      attendance,push}/**` 600/IP DDoS guard.
    - `RateLimitProblemDetailsFilter` response-decorator (perехватывает
      `setComplete()` от `RequestRateLimiter`): 429 → `application/problem+json`
      body + `Retry-After: 60`.
  - **`LoginRateLimiter` рефактор** (01 P0-6): composite `(ip, login)` Redis
    key, AuthService/Controller прокидывают IP из `X-Forwarded-For`. Фикс
    DoS-by-rate-limit — атакующий с одного IP больше не может залочить
    чужой аккаунт. 14 тестов (11 unit + 3 IT).
  - **Strict-mode toggle** (Группа 14): `InternalIssuerClientProperties.stripLegacyHeaders`
    (env `GATEWAY_STRIP_LEGACY_HEADERS`, default false). При `true` Gateway
    удаляет `X-User-Id/Role/Group-Id/Is-Headman` после issue — downstream
    работает только через Internal JWT. Парный флаг downstream
    `RUTCAMPUSTRACK_SECURITY_LEGACY_HEADERS_ENABLED=false`.
  - **Contract-тесты** (14 P1-1): `InternalJwtIssuerIT` — E2E через WireMock
    (auth-service + downstream). Валидный JWT → downstream видит
    X-Internal-Token; невалидная подпись/истекший/нет header → 401
    без вызова downstream. 4 теста.
  - **Rate-limit тесты** (14 P1-2): `RateLimitIT` (Testcontainers Redis,
    429 + Problem Details + Retry-After, composite isolation),
    `FailOpenIT` (Redis connection refused → 10 запросов проходят),
    `CompositeLoginKeyResolverIT`. 5 тестов.
  - **Документация:**
    - `docs/internal-jwt-spec.md` (NEW-3) — формат, claims, ключи,
      token-exchange flow, dual/strict mode, downstream-валидация.
    - `docs/api-rate-limits.md` (NEW-11) — таблица лимитов, 429 поведение,
      fail-open, клиентские рекомендации retry-with-backoff и `X-Login`.
    - `docs/architecture.md` — раздел «Internal JWT и rate-limiting» после
      «Reliable eventing».
  - **3 критичных фикса context startup** (обнаружены первым @SpringBootTest
    Gateway): `@Primary` на `ipKeyResolver` (RequestRateLimiterGatewayFilterFactory
    требует уникальный default bean), `@Autowired` на primary-конструктор
    `InternalJwtIssuerClient` (2 конструктора без аннотации давали
    NoSuchMethodException), `RateLimitProblemDetailsFilter` перехватывает
    `setComplete()` (RequestRateLimiter не вызывает writeWith на denied path).

- **M02 Reliable Eventing** — гарантированная доставка событий RabbitMQ:
  - `services/shared/shared-outbox/` — storage-agnostic API `OutboxStorage` с
    двумя реализациями (`JpaOutboxStorage<E>` для PG, `MongoOutboxStorage` для
    Mongo). `OutboxPublisherJob` (`@Scheduled` + `@SchedulerLock`),
    `OutboxCleanupJob` (cron 3am + retention 7д), `OutboxMetrics` (gauge).
  - Flyway миграции: `schedule V10__shedlock_table` + `V11__schedule_outbox`,
    `academic V15__shedlock_table` + `V16__academic_outbox` (partial-индексы
    `(status=pending, created_at)` и `(status=sent, sent_at)`). Mongo
    collection `attendance_outbox` auto-created.
  - ShedLock infrastructure: `shedlock-spring` + `shedlock-provider-jdbc-template`
    (PG) / `shedlock-provider-mongo` (Mongo). `@SchedulerLock` на
    `LessonStatusTransitionJob.runTransitions`, `OutboxPublisherJob.tick`,
    `OutboxCleanupJob.tick`. `PublicKeyConfig.refresh` в api-gateway помечен
    `@SuppressWarnings("SingleInstance")` (single-instance by design).
  - Micrometer метрики: `outbox.lag` (gauge pending count), `outbox.published.total`,
    `outbox.failed.total` (counter, tag `event_type`).
  - `event-schemas/_common.json` с `$defs` (eventId, occurredAt, traceId,
    eventVersion, lessonNumber). 19 существующих schemas отрефакторены на `$ref`.
  - `docs/event-schemas.md` — versioning policy + bump процедура + $ref guide.
  - Раздел «Reliable eventing» в `docs/architecture.md` с диаграммой outbox flow.
  - 5 contract-тестов per event-type через 3 сервиса (lesson.started/closed/
    cancelled, group.updated, attendance.marked). Используют
    networknt json-schema-validator 1.5.4 против реального payload из outbox.
  - ArchUnit rule `ScheduledMustHaveSchedulerLockTest` в каждом сервисе —
    защита от NEW-28 (любой `@Scheduled` метод без `@SchedulerLock`).
  - Конфиг: `rutcampustrack.outbox.publisher.fixed-delay-ms:5000`,
    `rutcampustrack.outbox.cleanup.cron:"0 0 3 * * *"`,
    `rutcampustrack.outbox.retention-days:7`.

- **M01 Shared Foundations** — 4 shared-модуля под `services/shared/`:
  - `shared-web` — RFC 9457 `ErrorResponse` + `GlobalExceptionHandler`
    (9 стандартных Spring MVC handlers + catch-all с correlation=traceId),
    validation-аннотации `@StartBeforeEnd`, `@DateRangeValid`, `@ValidFile`,
    `JacksonConfig` с едиными настройками, `@AdminAction` marker +
    aspect-заглушка (audit handler в M04), `SharedOpenApiCustomizer`
    заглушка (обогащение OpenAPI-спеки в M06).
  - `shared-events` — `DomainEvent` abstract base (`event_version`/`trace_id`/
    `occurred_at`/`source` в snake_case JSON), `@EventVersion` marker,
    `AbstractEventPublisher.fillDefaults()`, `AbstractEventConsumer.withTraceContext()`.
  - `shared-logback` — `shared/logback-base.xml` (JSON stdout через
    `LoggingEventCompositeJsonEncoder`), `MaskingJsonProvider` с regex-маскированием
    Bearer JWT / telegram_id / FCM endpoint.
  - `shared-test-containers` — `java-test-fixtures` модуль с `ContainerTestBase`
    (Postgres/Mongo/Redis/RabbitMQ, `reuse=true`), `GrpcInProcessFixture`,
    `WireMockFixture`, `MigrationTestUtils`.
- `gradle/libs.versions.toml` — Version Catalog с версиями shared-модулей.
- `docs/shared-modules-usage.md` — quick-start для подключения shared-модулей.
- Раздел «Shared modules» в `docs/architecture.md`.
- `notification-service` — первый сервис-потребитель shared foundations
  (acceptance-тесты `NotificationErrorHandlingIT` + `NotificationLoggingIT`).

### Changed

- `notification-service/NotificationWebApplication` — `scanBasePackages`
  расширен до `ru.rutcampustrack.shared.web` для подхвата shared beans.
- `notification-service` — добавлен runtime `spring-security-core` для
  `AccessDeniedException` handler'а из shared-web.
- **M02**: `DomainEventListener` в academic и schedule переведён с
  `@TransactionalEventListener(AFTER_COMMIT) → rabbitTemplate.convertAndSend`
  на `BEFORE_COMMIT → outboxStorage.save`. Actual publish в Rabbit делает
  `OutboxPublisherJob` асинхронно. Закрывает 02 P0-6 message loss.
- **M02**: 3 direct-publisher'а в attendance (`AttendanceEventPublisher`,
  `ExcuseEventPublisher`, `LateCheckinEventPublisher`) — переписаны с
  `rabbitTemplate.convertAndSend` на `outboxStorage.save` через
  `ObjectMapper.writeValueAsString(envelope)`.
- **M02**: `OutboxEntity.payload` — `@JdbcTypeCode(SqlTypes.JSON)` для
  корректного mapping на PG jsonb в Hibernate 6.

### Fixed

- `notification-service` ранее не имел `@RestControllerAdvice` — любая
  validation-ошибка отвечала generic 500. Теперь — RFC 9457 `application/problem+json`
  с `invalidParams[]` (closes 05 P0-2).
