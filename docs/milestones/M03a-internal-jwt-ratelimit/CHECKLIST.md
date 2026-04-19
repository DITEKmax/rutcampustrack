# M03a Checklist

Порядок важен — shared-security scaffold первым, чтобы downstream могли
подключаться. Gateway issuer перед downstream validation (иначе нечем
тестировать). Rate-limit последним (независим по коду, но проще дебажить
после того как Internal JWT flow работает).

## Группа 1 — Discovery + архитектурные решения

- [x] Grep `@Scheduled` + `PublicKeyConfig` + `/auth/public-key` в api-gateway и auth-service — зафиксировано в NOTES 2026-04-19 (surprise block)
- [x] DECISIONS.md: где держится приватный ключ — (a3) Token Exchange endpoint в auth-service, Gateway дёргает с кэшем
- [x] DECISIONS.md: dual-mode flag дефолт в prod — `true` + strict toggle последним commit'ом
- [x] DECISIONS.md: header name — `X-Internal-Token` (отдельный custom header, решение 2026-04-19)

## Группа 2 — shared-security scaffold (validator side)

- [ ] `services/shared/shared-security/build.gradle.kts` + `settings.gradle.kts` include — java-library + testFixtures plugin
- [ ] `InternalJwtProperties` (`@ConfigurationProperties("rutcampustrack.security.internal-jwt")`) — authServiceUrl, publicKeyRefreshMinutes (default 60), clockSkewSeconds (default 30), legacyHeadersEnabled (default true)
- [ ] `PublicKeyProvider` — WebClient-based puller из `/auth/public-key` (паттерн скопировать из `api-gateway/PublicKeyConfig`), `@Scheduled` refresh + `AtomicReference<PublicKey>`, `@SchedulerLock` не нужен (per-instance cache)
- [ ] `InternalJwtValidator` — парсит `Authorization: Internal <jwt>`, валидирует подпись + audience (`rutcampustrack-internal`) + issuer (`rutcampustrack-auth`) + expiration через jjwt
- [ ] `InternalJwtFilter extends OncePerRequestFilter` — ставит `Authentication` с `userId/role/groupId/isHeadman` claims
- [ ] `DualModeUserContextFilter` — Internal JWT есть → использует (приоритет); иначе legacy `X-User-*` если `legacyHeadersEnabled=true`; иначе 401
- [ ] `InternalJwtAutoConfiguration` + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- [ ] Unit-тесты: valid token passes, expired token fails, wrong signature fails, wrong audience fails, missing claims fails
- [ ] `src/testFixtures/` — helper `InternalJwtTestFactory.validToken(userId, role, ...)` + `.expiredToken()` + `.invalidSignature()` используя RSA keypair in-memory

## Группа 3 — Auth-service token exchange endpoint

- [ ] `services/auth-service/.../config/InternalIssuerProperties.java` — `@ConfigurationProperties("rutcampustrack.security.internal-issuer")` с полем `secret`; fail-fast `@PostConstruct` if empty (mirror паттерн `GrpcSecretFilter`)
- [ ] `services/auth-service/.../JwtService.java` — новый метод `generateInternalToken(Long userId, String role, Long groupId, boolean isHeadman)`: TTL 5 мин, `iss=rutcampustrack-auth`, `aud=rutcampustrack-internal`, kid header
- [ ] `services/auth-service/.../dto/InternalIssueRequest.java` — record (userId, role, groupId, isHeadman) + Bean Validation
- [ ] `services/auth-service/.../dto/InternalIssueResponse.java` — record (token, expiresAt)
- [ ] `services/auth-service/.../controller/InternalIssuerController.java` — `POST /internal/issue-internal-jwt`, принимает `InternalIssueRequest`, возвращает `InternalIssueResponse`
- [ ] `services/auth-service/.../security/InternalIssuerSecretFilter.java` — `OncePerRequestFilter` на `/internal/**`, проверяет `X-Internal-Issuer-Secret` через `MessageDigest.isEqual` (timing-safe)
- [ ] `SecurityConfig` — `/internal/**` permit-all + custom filter chain
- [ ] Unit `JwtServiceTest.generateInternalToken_*` — правильные claims, правильный TTL, правильная signature
- [ ] IT `InternalIssuerControllerIT` — valid secret → 200 + signed JWT; wrong secret → 401; missing secret → 401; malformed body → 400; service с empty `INTERNAL_ISSUER_SECRET` fails-fast на старте
- [ ] `application.yml` (dev) — `rutcampustrack.security.internal-issuer.secret: dev-secret-at-least-32-bytes-for-local-testing-only`

## Группа 4 — Gateway issuer client

- [ ] `services/api-gateway/build.gradle.kts` — `com.github.ben-manes.caffeine:caffeine` dep
- [ ] `services/api-gateway/.../security/InternalIssuerClientProperties.java` — authServiceUrl, secret (из env `INTERNAL_ISSUER_SECRET`), cacheTtlMinutes (default 4), timeoutMillis
- [ ] `InternalJwtIssuerClient` — WebClient + Caffeine cache (`user:${userId}:${role}` → `CachedToken{token, expiresAt}`, `expireAfterWrite(4min)`); method `Mono<String> issueFor(Long userId, String role, Long groupId, boolean isHeadman)`
- [ ] Error handling: auth-service 5xx / timeout → Mono.error(ServiceUnavailableException) → 503 клиенту + WARN в лог
- [ ] `InternalJwtIssuerFilter implements GlobalFilter, Ordered` — после `JwtAuthenticationFilter` (order +10):
  - Читает claims из внешнего JWT (уже валидирован и лежит в attributes)
  - Вызывает `issuerClient.issueFor(...)`, ждёт Mono
  - Добавляет header `Authorization: Internal <jwt>` в mutated request
  - Dual-mode: НЕ strip'ает `X-User-*` (остаются как fallback)
- [ ] Unit `InternalJwtIssuerClientTest` — cache hit (1 WebClient invocation на N запросов), cache expiry, error propagation
- [ ] IT `InternalJwtIssuerClientIT` (WireMock auth-service) — 2 запроса одного user → 1 network call; после TTL (форсированно) — refetch
- [ ] IT `InternalJwtIssuerFilterIT` — full Gateway stack + WireMock auth-service + WireMock downstream → downstream видит `Authorization: Internal <jwt>` с правильными claims

## Группа 5 — Downstream миграция (academic)

- [ ] `services/academic-service/academic-app/build.gradle.kts` — `implementation(project(":services:shared:shared-security"))`
- [ ] `application.yml` — `rutcampustrack.security.internal-jwt.public-key-url: ...`, `legacy-headers-enabled: true`
- [ ] `UserContextFilter` — либо удалить и использовать `DualModeUserContextFilter`, либо сохранить и перенацелить
- [ ] `RequestContext` читается из `SecurityContext` / `Authentication.getPrincipal()`
- [ ] IT `InternalJwtBypassIT`: прямой `MockMvc` запрос без Internal JWT → 401
- [ ] IT `LegacyHeadersIT`: `legacy-headers-enabled=true` → X-User-* принимается; `=false` → 401

## Группа 6 — Downstream миграция (schedule)

- [ ] build.gradle.kts + application.yml (аналогично academic)
- [ ] `UserContextFilter` replace
- [ ] `InternalJwtBypassIT` + `LegacyHeadersIT`

## Группа 7 — Downstream миграция (attendance)

- [ ] build.gradle.kts + application.yml
- [ ] `UserContextFilter` replace
- [ ] `InternalJwtBypassIT` + `LegacyHeadersIT`

## Группа 8 — Downstream миграция (notification-web)

- [ ] build.gradle.kts + application.yml
- [ ] `UserContextFilter` replace
- [ ] `InternalJwtBypassIT` + `LegacyHeadersIT`
- [ ] WebSocket handshake — отдельно (текущий token-flow НЕ меняется в M03a, это scope M03b через ws-ticket)

## Группа 9 — Rate-limit Gateway deps + infra

- [ ] `services/api-gateway/build.gradle.kts` — `spring-boot-starter-data-redis-reactive` (если нет), `spring-cloud-starter-gateway-redis-rate-limiter` (проверить artifact name для Spring Cloud 2024.x)
- [ ] `application.yml` spring.data.redis host/port — такой же Redis что и auth-service (re-use)
- [ ] `RedisRateLimiterConfig` — бины `IpKeyResolver`, `UserIdKeyResolver`, `LoginKeyResolver`, `CompositeIpLoginKeyResolver`
- [ ] Fail-open wrapper: кастомный `RateLimiter` ловит `RedisConnectionFailureException` → `Response(allowed=true)` + WARN лог

## Группа 10 — Rate-limit routes

- [ ] `/api/auth/otp/request` — RL 1 req/min per IP
- [ ] `/api/auth/otp/verify-by-code` — RL 5 req/min per IP
- [ ] `/api/auth/login` — RL 5 req/min per IP + 10 req/min per login (два последовательных фильтра)
- [ ] `/api/auth/refresh` — RL 30 req/min per user (UserIdKeyResolver из Internal JWT)
- [ ] `/api/attendance/check-in` — RL 10 req/min per user
- [ ] `/api/**` — глобальный RL 600 req/min per IP (последний — DDoS guard)
- [ ] RFC 7807 Problem Details для 429: `type=...rate-limit`, `title="Too Many Requests"`, `status=429`, `detail`, `Retry-After` header

## Группа 11 — LoginRateLimiter рефактор (01 P0-6)

- [ ] `services/auth-service/.../service/LoginRateLimiter.java` — ключ Redis: `login_attempts:<ip>:<login>` (composite)
- [ ] IP извлекается из `X-Forwarded-For` (первый IP) или `RemoteAddr` fallback
- [ ] Unit-тест: 5 попыток с `ip1+login1` не блокируют `ip2+login1`
- [ ] Integration: 5 failed login с одного IP + login → 6-й 429 (или `LoginRateLimiter` exception)
- [ ] Документация в `docs/api-rate-limits.md` — различие Gateway global-RL vs auth-service LoginRateLimiter

## Группа 12 — Rate-limit тесты (14 P1-2)

- [ ] `services/api-gateway/src/test/.../RateLimitIT.java` — Testcontainers Redis:
  - 11 запросов на `/otp/verify-by-code` за минуту → 11-й 429
  - `Retry-After` header присутствует
  - Problem Details body
- [ ] `RateLimitFailOpenIT` — Redis недоступен (`container.stop()` или wrong port) → запрос проходит, WARN в логах
- [ ] `CompositeLoginKeyResolverIT` — разные IP одного login'а считаются раздельно

## Группа 13 — Contract-тест Gateway↔downstream (14 P1-1)

- [ ] `services/api-gateway/src/test/.../InternalJwtIssuerIT.java` — Testcontainers (Gateway + downstream mock):
  - Валидный внешний JWT → downstream видит Internal JWT с правильными claims
  - Невалидный внешний JWT → 401 (не доходит до downstream)
  - Истёкший внешний JWT → 401
- [ ] Smoke: попытка прямого запроса на :9091 без Internal JWT → 401

## Группа 14 — Смена на strict mode (подготовка к M03b)

- [ ] `application.yml` в prod — `legacy-headers-enabled: false` (отдельный commit, применяется после UAT)
- [ ] `InternalJwtIssuerFilter` в Gateway — добавить strip `X-User-*` headers (вторая фаза, отдельный commit/flag)
- [ ] NOTES.md — отметить, что переключение запланировано как последний commit M03a перед тегом v0.0.0-alpha.3
- [ ] UAT golden path checklist: admin login, teacher journal, student check-in, headman operations — все проходят на strict mode

## Группа 15 — Документация + artifacts

- [ ] `docs/internal-jwt-spec.md` (NEW-3) — формат, claims, TTL, ключи, dual-mode, миграционный путь
- [ ] `docs/api-rate-limits.md` (NEW-11) — таблица лимитов, 429 поведение, Retry-After, клиентский backoff
- [ ] `docs/architecture.md` → раздел «Internal JWT и rate-limiting» после «Reliable eventing»
- [ ] `CHANGELOG.md [Unreleased]` → Added (shared-security, Internal JWT issuer, rate-limit 6 routes) + Changed (`UserContextFilter` → Internal JWT, `LoginRateLimiter` composite key)
- [ ] `CLAUDE.md` — статус M03a → ✅ + обновить архитектурный раздел (порт 8080 теперь issuer, shared-security в структуре)
- [ ] `docs/milestones/README.md` → M03a ✅ (если M03 строка — заменить на M03a/M03b)
- [ ] Закрыть пункты COVERAGE-AUDIT.md, relevant to M03a: колонка «Closed in» — commit SHA

## Группа 16 — Финал

- [ ] Все acceptance criteria из PLAN.md отмечены `[x]`
- [ ] `./gradlew build` зелёный — полный snapshot (shared-security + 4 backend + Gateway)
- [ ] Smoke-тест NEW-5: локально попытаться обратиться напрямую на :9091 без Internal JWT — 401 (мануальный, записать в NOTES)
- [ ] `bug-hunter` subagent на полный diff M03a milestone'а
- [ ] `security-auditor` subagent (по правилу из README — для M03 цена бага выше цены токенов)
- [ ] Все CRITICAL/HIGH findings — fix в M03a, MEDIUM/LOW — в NOTES как known
- [ ] Post-mortem в PLAN.md: commits list, surprises, lessons learned, M03b/M04 follow-ups
- [ ] Финальный коммит `chore(m03a): close Internal JWT + rate-limit`
- [ ] `git tag v0.0.0-alpha.3` на финальном коммите (БЕЗ push — жду явного «go»)

---

_Если задача занимает > 4 часов — разрежь её прямо здесь и отметь._
