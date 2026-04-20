# Changelog

Все заметные изменения в проекте RutCampusTrack документируются здесь.

Формат: [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/).
Версионирование: [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **M03b Secure Boundaries Part B** — JWT HttpOnly cookie + WS-ticket
  handshake + logout lifecycle + hot-patches из M03a (tag
  `v0.0.0-alpha.4`). Закрывает C0-5, C0-7, KI-3/6/7/8.
  - `services/auth-service`:
    - `AuthCookies` — factory HttpOnly+Secure+SameSite=Strict+Path=/api/auth
      cookie для refresh-token. Единый `baseBuilder()` для issue и clear.
    - `POST /auth/refresh` — теперь читает `@CookieValue("rct_refresh")`,
      ротирует cookie. Старое body-based поведение перенесено в
      `POST /auth/refresh-body` (**DEPRECATED**, `Deprecation: true` +
      `Sunset: Mon, 01 Jun 2026 00:00:00 GMT`, removal в M04/M05).
    - `POST /auth/logout` — добавлен в `permitAll` (cookie-only logout
      работает без access-JWT). Invalidate'ит все ws-ticket'ы
      пользователя если Bearer есть, revoke'ит refresh-token, clear'ит cookie.
    - `WsTicketController` / `WsTicketService` / `InternalWsTicketController`
      — выдача и consume single-use 30s WebSocket ticket'ов. Storage:
      `ws_ticket:<uuid>` + `ws_ticket_user:<uid>` Set (atomic SADD+EXPIRE
      через Lua). Consume через Lua-script `GET + DEL + SREM`. REST
      internal endpoint `/internal/consume-ws-ticket` защищён
      `X-Internal-Issuer-Secret` (переиспользует паттерн M03a).
    - `BcryptConcurrencyGuard` (KI-7) — Semaphore (fair, N=20) вокруг
      bcrypt в `login` и `changePassword`. Fail-fast 429 при заполнении.
    - `LoginRateLimiter` (KI-6) — atomic INCR+EXPIRE через Lua-script,
      убирает TTL-race при network blip.
    - `IT`: `LogoutLifecycleIT`, `WsTicketIT`, `BcryptDoSMitigationIT`.
  - `services/api-gateway`:
    - `LoginBodyExtractionFilter` (KI-8) — GlobalFilter order=-50,
      читает JSON body POST `/api/auth/login`, извлекает `login`, ставит
      X-Login header в mutated request. Composite `(ip, login)` rate-limit
      теперь реально работает.
    - `InternalJwtIssuerClient` (KI-3) — проверяет `expiresAt < now+5s` →
      invalidate cache + retry loader. Защита от clock drift.
    - `/api/auth/logout` добавлен в PUBLIC_PATHS.
  - `services/notification-service`:
    - `TicketHandshakeInterceptor` заменяет `JwtHandshakeInterceptor` —
      читает `query.ticket`, вызывает internal REST `/internal/consume-ws-ticket`,
      кладёт `userId/role/groupId/isHeadman` в STOMP session attributes.
    - `WsTicketClient` — REST-клиент с `X-Internal-Issuer-Secret`.
  - `frontends/pwa` (breaking):
    - `AuthProvider` — access-token только в memory (React state + tokenRef),
      refresh через cookie auto-send, bootstrap на mount.
    - `clearAllClientState(accessToken?)` — очищает localStorage/sessionStorage,
      SW runtime caches, unsubscribe + DELETE push/subscribe (с Bearer).
    - `wsTicket.ts` — pre-fetch ticket'а перед WebSocket connect.
    - Миграционный helper удаляет legacy `localStorage['rct.auth.v1']`.
  - `frontends/web-panel` (breaking):
    - `auth.service.ts` — access-only, refresh в cookie, `setAccessToken`
      вместо `setTokens`. `bootstrap()` через `provideAppInitializer`.
    - `clear-all-client-state.ts` — local + session storage.clear.
    - `ws-ticket.ts` — `acquireWsTicket` + `buildWsUrl` для 3 STOMP-сервисов.
    - `AuthInterceptor` — cookie-based refresh без body.

### Changed

- **CSRF infrastructure НЕ вводится** в M03b — `SameSite=Strict` +
  same-origin deployment (`ruttrack.site`) делают double-submit token
  избыточным (DECISIONS 2026-04-20, подтверждение OWNER-ANSWERS
  02-Q-frontend-security). Conditional follow-up: добавить double-submit
  token если в v1.0+ появится второй origin (OAuth callback).
- **Breaking**: refresh-token больше не возвращается в body response
  `/auth/login` (устарело, клиенты должны читать из cookie).
- **Breaking**: WebSocket `?token=<JWT>` → `?ticket=<uuid>` через
  pre-connect `POST /auth/ws-ticket`. Старые клиенты получат 401 при
  handshake.
- **Breaking**: `/auth/refresh` игнорирует body. TMA/Mini App на
  `/auth/refresh-body` (deprecated).
- `SecurityConfig` auth-service: `/auth/logout` перенесён в `permitAll`
  (cookie-only flow без Bearer).

### Fixed

- **KI-3** (M03a post-mortem): clock drift edge case — `InternalJwtIssuerClient`
  больше не возвращает токен с `expiresAt` близким к прошлому.
- **KI-6**: Redis TTL race в `LoginRateLimiter` — atomic Lua INCR+EXPIRE.
- **KI-7**: bcrypt DoS — concurrent invalid-password flood теперь
  fail-fast 429 через Semaphore guard, CPU не захлёбывается до того как
  `checkBlocked` сработает.
- **KI-8**: composite `(ip, login)` rate-limit на `/api/auth/login` был
  broken — клиентский `X-Login` strip'ался `JwtAuthenticationFilter`
  (CRIT-01). Теперь `LoginBodyExtractionFilter` извлекает login из
  body внутренне.
- **security-audit MEDIUM-1**: PWA `clearAllClientState` DELETE
  push/subscribe без Bearer → 401 → subscription не удалялась →
  cross-user push leak на shared-устройстве. Фикс: передаём accessToken
  до обнуления.
- **bug-hunt HIGH-2**: `WsTicketService.issue()` SADD+EXPIRE был
  неатомарным — persistent user-set без TTL при network blip. Фикс:
  Lua-script `ADD_TO_SET_SCRIPT`.

### Documentation

- `docs/auth-flow.md` — полный runbook cookie + ws-ticket + logout
  lifecycle (диаграммы, cookie контракт, endpoints, breaking changes,
  security-свойства, rate-limits).
- `docs/milestones/M03b-jwt-cookie-ws-ticket/` — PLAN, CHECKLIST, NOTES,
  DECISIONS (5 micro-ADR'ов: cookie Path, SameSite, CSRF, ws-ticket
  storage scheme, refresh-body deprecation timeline, KI-7 Semaphore
  выбор, event `user.logged-out` откладывается в M04).

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
