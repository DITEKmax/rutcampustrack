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

- [x] `services/shared/shared-security/build.gradle.kts` + `settings.gradle.kts` include — java-library + testFixtures plugin
- [x] `InternalJwtProperties` (record с `@ConfigurationProperties("rutcampustrack.security.internal-jwt")`) — authServiceUrl, publicKeyRefreshMinutes (default 60), clockSkewSeconds (default 30), legacyHeadersEnabled, expectedIssuer/Audience, headerName (default `X-Internal-Token`)
- [x] `PublicKeyProvider` — WebClient-based puller из `/auth/public-key` (паттерн скопирован из `api-gateway/PublicKeyConfig`), `@Scheduled` refresh + `AtomicReference<PublicKey>`, `@SuppressWarnings("SingleInstance")` — per-instance cache
- [x] `InternalJwtValidator` — парсит `X-Internal-Token`, валидирует подпись + audience + issuer + expiration + clockSkew через jjwt. Возвращает `InternalJwtClaims` record (userId/role/groupId/isHeadman) или бросает `InternalJwtException`
- [x] `DualModeUserContextFilter` (abstract) — Internal JWT priority, fallback legacy X-User-* если `legacyHeadersEnabled=true`, иначе 401. Hooks `applyInternalJwt(claims)` + `applyLegacyHeaders(request)` для сервис-специфичного `RequestContext`
- [x] `InternalJwtException` — runtime exception для 401 mapping
- [x] `src/testFixtures/InternalJwtTestFactory` — in-memory RSA keypair, методы validToken / expiredToken / invalidSignature / wrongIssuer / wrongAudience / missingRole
- [x] Unit-тесты: 18 зелёных (9 Validator + 7 DualModeFilter + 2 Properties). Покрывают: valid/expired/invalid-signature/wrong-iss/wrong-aud/missing-role tokens; dual-mode precedence, legacy fallback, strict 401, no-headers passthrough
- [x] ~~`InternalJwtAutoConfiguration` + `AutoConfiguration.imports`~~ — не нужен: паттерн M01 без autoconfig, сервис-потребитель регистрирует бины через `@ComponentScan` / явные `@Bean`

## Группа 3 — Auth-service token exchange endpoint

- [x] `services/auth-service/.../config/InternalIssuerProperties.java` — класс с `@ConfigurationProperties("rutcampustrack.security.internal-issuer")`, fail-fast `@PostConstruct` при empty/short secret (MIN 32 bytes) или TTL вне (0, 3600]
- [x] `services/auth-service/.../JwtService.java` — новый метод `generateInternalToken(userId, role, groupId, isHeadman, ttlSeconds)`: `iss=rutcampustrack-auth`, `aud=rutcampustrack-internal` (константа `INTERNAL_JWT_AUDIENCE`), kid header, подпись тем же приватным ключом
- [x] `services/auth-service/.../dto/InternalIssueRequest.java` — record (userId, role, groupId, isHeadman) + `@NotNull`/`@Positive`/`@NotBlank`
- [x] `services/auth-service/.../dto/InternalIssueResponse.java` — record (token, expiresAt)
- [x] `services/auth-service/.../controller/InternalIssuerController.java` — `POST /internal/issue-internal-jwt`, принимает `InternalIssueRequest`, возвращает `InternalIssueResponse` с `@Operation`/`@ApiResponse`
- [x] `services/auth-service/.../security/InternalIssuerSecretFilter.java` — `OncePerRequestFilter` на `/internal/**` через `shouldNotFilter`, проверяет `X-Internal-Issuer-Secret` через `MessageDigest.isEqual` (timing-safe); при ошибке — `setStatus(401)` + JSON body (не `sendError` — Spring Security мапит на 403)
- [x] `SecurityConfig` — `/internal/**` добавлен в permit-all + `InternalIssuerSecretFilter` перед `UsernamePasswordAuthenticationFilter`
- [x] `AuthApplication` — `InternalIssuerProperties.class` добавлен в `@EnableConfigurationProperties`
- [x] `application.yml` + `application-test.yml` — `rutcampustrack.security.internal-issuer.secret` (dev default + ENV override) + `token-ttl-seconds: 300`
- [x] Unit `InternalIssuerPropertiesTest` — 6 тестов: empty/blank/short secret, valid, ttl=0, ttl>3600
- [x] IT `InternalIssuerIT` — 5 тестов: valid secret → signed JWT c правильными claims (проверка через `/auth/public-key`), missing/wrong secret → 401, malformed body → 400, null groupId (teacher) accepted
- [x] Build `./gradlew :services:auth-service:build` зелёный — 40 тестов (было 29, +11 новых)

## Группа 4 — Gateway issuer client

- [x] `services/api-gateway/build.gradle.kts` — `com.github.ben-manes.caffeine:caffeine` + test deps (reactor-test, WireMock, webflux-starter)
- [x] `services/api-gateway/.../security/InternalIssuerClientProperties.java` — authServiceUrl, secret (из env `INTERNAL_ISSUER_SECRET`), cacheTtlSeconds (default 240, < 290 ← auth-service TTL 300), cacheMaxSize, timeoutMillis; fail-fast validation
- [x] `InternalJwtIssuerClient` — WebClient + Caffeine `AsyncCache<CacheKey(userId, role), IssuedToken>` с `expireAfterWrite`; `Mono<String> issueFor(userId, role, groupId, isHeadman)`
- [x] Error handling: auth-service 4xx/5xx / timeout → Mono.error(`InternalIssuerUnavailableException`) с message, пробрасывается через onErrorMap
- [x] `InternalJwtIssuerFilter implements GlobalFilter, Ordered` — order=-50 (после JwtAuthenticationFilter=-100):
  - Читает `X-User-Id`/`X-User-Role`/`X-Group-Id`/`X-Is-Headman` (уже поставлены JwtAuthenticationFilter'ом)
  - Парсит userId (non-numeric → skip), groupId (null-tolerant)
  - Вызывает `issuerClient.issueFor(...)`, добавляет `X-Internal-Token: <jwt>` в downstream request
  - На `InternalIssuerUnavailableException` → 503 Problem Details
  - Dual-mode: НЕ strip'ает `X-User-*` headers (остаются как fallback для dual-mode downstream)
- [x] `GatewayApplication` + `application.yml` — `@EnableConfigurationProperties(InternalIssuerClientProperties.class)` + секция `rutcampustrack.security.internal-issuer-client` с dev defaults и ENV override
- [x] Unit `InternalJwtIssuerClientTest` (7): first call hits network, second hit cached, different users → separate entries, role change → new entry, auth 500/401 → unavailable, invalidateAll forces refetch. WireMock для auth-service
- [x] Unit `InternalJwtIssuerFilterTest` (6): X-Internal-Token ставится, no headers → skip, non-numeric userId → skip, unavailable → 503, null groupId → passes null, filter order > -100
- [x] Unit `InternalIssuerClientPropertiesTest` (6): empty/short secret, valid secret, ttl edges, defaults
- [x] `./gradlew :services:api-gateway:build` зелёный (19 новых тестов)
- [ ] IT `InternalJwtIssuerClientIT` — отложен в Группу 13 (contract-тест Gateway↔downstream), т.к. unit-тесты c WireMock покрывают тот же functional ground

## Группа 5 — Downstream миграция (academic)

- [x] `services/academic-service/academic-app/build.gradle.kts` — `implementation(project(":services:shared:shared-security"))` + `testImplementation(testFixtures(...))`
- [x] `shared-security/PublicKeyProvider` мигрирован с `WebClient` на `RestClient` — servlet-friendly, работает в обоих stack (surprise Группа 5, 3 лишних байта в NOTES)
- [x] `application.yml` — секция `rutcampustrack.security.internal-jwt` (auth-service-url, public-key-refresh-minutes, clock-skew-seconds, legacy-headers-enabled=true default + ENV override)
- [x] `application-test.yml` — test-specific internal-jwt config (localhost:9999 URL — реальных HTTP-запросов к auth-service в IT не будет)
- [x] Удалён старый `UserContextFilter`, создан `AcademicUserContextFilter extends DualModeUserContextFilter` с hooks applyInternalJwt / applyLegacyHeaders в `RequestContext` через `UserRole.valueOf(claims.role())`
- [x] `InternalJwtConfig @Configuration` — @Bean PublicKeyProvider + InternalJwtValidator + @EnableConfigurationProperties
- [x] `InternalJwtTestConfig @TestConfiguration` — @Primary PublicKeyProvider subclass с no-op init/refresh и ключом из InternalJwtTestFactory
- [x] `AbstractAcademicIntegrationTest` — `@Import(InternalJwtTestConfig.class)` (валидно для всех существующих IT и новых)
- [x] IT `AcademicUserContextFilterIT` (6): invalid/expired/wrong-signature Internal JWT → 401, valid token passes filter (no 401), Internal JWT takes precedence over legacy, legacy headers work while dual-mode on
- [x] IT `AcademicUserContextFilterStrictModeIT` (3): `legacy-headers-enabled=false` → no headers 401, legacy headers 401, valid Internal JWT passes
- [x] `./gradlew :services:academic-service:academic-app:build` зелёный — 197 тестов (было 185, +9 новых filter IT + 3 strict mode)

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
