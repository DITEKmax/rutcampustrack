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

- [x] build.gradle.kts — shared-security + testFixtures
- [x] application.yml + application-test.yml — rutcampustrack.security.internal-jwt config
- [x] Удалён старый `UserContextFilter`, создан `ScheduleUserContextFilter extends DualModeUserContextFilter`
- [x] `InternalJwtConfig` + `InternalJwtTestConfig` (аналогично academic)
- [x] `AbstractScheduleIntegrationTest` — `@Import(InternalJwtTestConfig.class)`
- [x] `ScheduleUserContextFilterIT` (6) + `StrictModeIT` (3) — endpoint `/schedule/items`
- [x] Build зелёный — 108 тестов (было 99, +9 новых)

## Группа 7 — Downstream миграция (attendance)

- [x] build.gradle.kts — shared-security + testFixtures
- [x] application.yml + application-test.yml — internal-jwt config
- [x] Удалён старый `UserContextFilter`, создан `AttendanceUserContextFilter extends DualModeUserContextFilter`
- [x] `InternalJwtConfig` + `InternalJwtTestConfig`
- [x] `AbstractAttendanceIntegrationTest` — `@Import(InternalJwtTestConfig.class)`
- [x] `AttendanceUserContextFilterIT` (6) + `StrictModeIT` (3) — endpoint `/attendance/reports/student/stats`
- [x] Build зелёный — 157 тестов (было 148, +9 новых)

## Группа 8 — Downstream миграция (notification-web)

- [x] build.gradle.kts — shared-security + testFixtures
- [x] application.yml — internal-jwt config
- [x] Удалён старый `UserContextFilter`, создан `NotificationUserContextFilter` с `isExcludedPath(/ws)` для WebSocket handshake (ws-ticket — scope M03b)
- [x] `InternalJwtConfig` (wire bean'ы)
- [x] Обновлён `SecurityInfrastructureTest` — тесты 3-4 теперь тестируют `NotificationUserContextFilter` с in-memory keypair (legacy-mode verification)
- [x] Build зелёный — 59 тестов (унаследованные от M01/M02, + фикс тестов для нового filter)
- [x] Notification IT-файлы не добавляю — `ContainerTestBase`-based тесты без общего Abstract class; M03b добавит WS-ticket IT с учётом full stack

## Группа 9 — Rate-limit Gateway deps + infra

- [x] `services/api-gateway/build.gradle.kts` — `spring-boot-starter-data-redis-reactive` (artifact `spring-cloud-starter-gateway-redis-rate-limiter` для 2024.x не существует; `RedisRateLimiter` идёт из `spring-cloud-starter-gateway` + требует redis-reactive client — см. NOTES Группа 9)
- [x] `application.yml` spring.data.redis host/port — re-use того же Redis, timeout 1s для fail-fast
- [x] `RedisRateLimiterConfig` — бины `ipKeyResolver`, `userIdKeyResolver`, `loginKeyResolver`, `ipLoginKeyResolver` + `@Primary FailOpenRateLimiter` wrapper (9 unit-тестов для resolvers)
- [x] Fail-open wrapper `FailOpenRateLimiter`: ловит `RedisConnectionFailureException` / `QueryTimeoutException` / Lettuce exceptions → `Response(allowed=true)` + WARN (6 unit-тестов)
- [x] `docker-compose.prod.yml` — api-gateway получает `REDIS_HOST/PORT/PASSWORD` + `INTERNAL_ISSUER_SECRET` + `depends_on: redis` (M03a Группа 4 env тоже фиксим в этом коммите — отсутствовал в prod compose)

## Группа 10 — Rate-limit routes

- [x] `/api/auth/otp/request` — RL 1 req/min per IP (новый роут `auth-otp-request` с Method=POST)
- [x] `/api/auth/otp/verify-by-code` — RL 5 req/min per IP (`auth-otp-verify`)
- [x] `/api/auth/login` — RL 5 req/min per IP + 10 req/min per `ip+login` composite (два последовательных RL-фильтра, `auth-login`). X-Login header от клиента, при отсутствии — fallback на IP (документируется в Группе 15)
- [x] `/api/auth/refresh` (+`/refresh-body`) — RL 30 req/min per user (`auth-refresh`, userIdKeyResolver)
- [x] `/api/attendance/check-in` — RL 10 req/min per user (`attendance-checkin`)
- [x] Глобально per-downstream — 600 req/min per IP на `academic`/`schedule`/`attendance`/`push` (DDoS guard через `ipKeyResolver`)
- [x] RFC 7807 Problem Details для 429: `RateLimitProblemDetailsFilter` — response-decorator, `type=https://ruttrack.site/problems/rate-limit-exceeded`, `title/status/detail`, `Retry-After: 60` (5 unit-тестов)

## Группа 11 — LoginRateLimiter рефактор (01 P0-6)

- [x] `services/auth-service/.../service/LoginRateLimiter.java` — ключи Redis: `login_attempts:<ip>:<login>` / `login_blocked:<ip>:<login>` (composite). API `checkBlocked(ip, login)` / `recordFailure(ip, login)` / `clearFailures(ip, login)`. Null/blank IP → fallback `"unknown"`
- [x] `AuthController#login(request, HttpServletRequest)` извлекает IP из `X-Forwarded-For` (первый IP) или `RemoteAddr` fallback — auth-service всегда за прокси
- [x] `AuthService#login(request, ipAddress)` прокидывает IP в все вызовы LoginRateLimiter
- [x] Unit-тесты `LoginRateLimiterTest` — 11: composite key; разные IP НЕ аккумулируются; 5/10/20 thresholds; IP-jack victim НЕ блокируется
- [x] Integration `LoginRateLimiterIT` — 3: 5 попыток с IP-A → 6-й 429; IP-A лочит login → IP-B логинится успешно; successful login clear'ит только свою корзину
- [ ] Документация в `docs/api/api-rate-limits.md` — различие Gateway global-RL vs auth-service LoginRateLimiter _(отложено в Группу 15 документации)_

## Группа 12 — Rate-limit тесты (14 P1-2)

- [x] `RateLimitIT` (2 теста) Testcontainers Redis + WireMock: 5 req/burst `/otp/verify-by-code` → 6-й 429 + Retry-After + Problem Details body; разные IP имеют отдельные корзины (IP-A исчерпан, IP-B первый запрос проходит)
- [x] `FailOpenIT` (1 тест) Redis указан на connection-refused порт — 10 запросов все проходят (X-RateLimit-FailOpen ставится на RateLimiter.Response, но до клиента может быть затёрт downstream'ом — проверяется FailOpenRateLimiterTest unit)
- [x] `CompositeLoginKeyResolverIT` (2 теста): composite (ip, login) изолирует корзины по IP; IP-RL burst применяется ко всем login'ам с одного IP
- [x] Фиксы для стабилизации IT: `@Primary` на `ipKeyResolver` (RequestRateLimiterGatewayFilterFactory требует уникальный bean), `@Autowired` на primary-конструктор `InternalJwtIssuerClient`, route URIs в `application.yml` переведены на `${*_SERVICE_URL:...}` placeholders для WireMock override, `RateLimitProblemDetailsFilter` перехватывает `setComplete()` (RequestRateLimiter никогда не вызывает writeWith на denied path)

## Группа 13 — Contract-тест Gateway↔downstream (14 P1-1)

- [x] `InternalJwtIssuerIT` (4 теста, WireMock auth-service + downstream):
  - Валидный внешний JWT → downstream получает `X-Internal-Token`, token-exchange вызван
  - Невалидная подпись внешнего JWT → 401, downstream НЕ вызван, token-exchange НЕ вызван
  - Истёкший внешний JWT → 401, downstream НЕ вызван
  - Отсутствие Authorization header → 401
- [x] Smoke-тест прямого запроса к downstream без Internal JWT покрыт IT в M03a
  Группах 5-7 (`{Service}UserContextFilterStrictModeIT`) — в strict-mode запрос без
  X-Internal-Token → 401

## Группа 14 — Смена на strict mode (подготовка к M03b)

- [x] `InternalJwtIssuerFilter` в Gateway — добавлен `stripLegacyHeaders` flag в `InternalIssuerClientProperties` (default `false`); при `true` Gateway после issue удаляет `X-User-Id/Role/Group-Id/Is-Headman` перед proxy. 2 новых unit-теста (dual-mode keep + strict-mode strip)
- [x] application.yml — `strip-legacy-headers: ${GATEWAY_STRIP_LEGACY_HEADERS:false}` (toggle через env var, dev default остаётся dual-mode)
- [x] NOTES.md UAT golden path checklist — см. секцию "Группа 14: UAT checklist"
- [ ] docker-compose.prod.yml env: `GATEWAY_STRIP_LEGACY_HEADERS=true` + `RUTCAMPUSTRACK_SECURITY_LEGACY_HEADERS_ENABLED=false` — **ОТЛОЖЕНО** в Группу 16 как финальный commit перед тегом `v0.0.0-alpha.3` (UAT golden path должен пройти перед переключением)

## Группа 15 — Документация + artifacts

- [x] `docs/api/internal-jwt-spec.md` (NEW-3) — формат, claims, TTL, ключи, token-exchange flow, dual/strict mode, downstream-валидация, security properties, roadmap
- [x] `docs/api/api-rate-limits.md` (NEW-11) — семантика token-bucket, таблица лимитов, 429 Problem Details, Retry-After, fail-open, LoginRateLimiter composite key, клиентские рекомендации (retry-with-backoff + X-Login header)
- [x] `docs/architecture/architecture.md` → раздел «Internal JWT и rate-limiting» после «Reliable eventing (M02)» — token exchange pipeline, ключевые инварианты, rate-limit таблица
- [x] `CHANGELOG.md [Unreleased]` → M03a секция (Added) с детализацией shared-security, token-exchange, Gateway issuer, downstream миграция, rate-limiting, LoginRateLimiter, strict-mode toggle, contract-тесты, документация, 3 critical fixes
- [x] `CLAUDE.md` — таблица milestones: M03 строка разделена на M03a ✅ 2026-04-20 + M03b; shared-security добавлен в структуру репозитория
- [x] `docs/milestones/README.md` → M03a ✅ 2026-04-20
- [x] COVERAGE-AUDIT.md — политика файла «не добавлять колонку Closed in, grep по git log» (см. лид статьи, feedback_audit_markup_economy.md)

## Группа 16 — Финал

- [x] Все acceptance criteria из PLAN.md отмечены `[x]` (см. Post-mortem)
- [x] `./gradlew build` зелёный — full snapshot (M01+M02+M03a), 3m 8s, 105 tasks, 0 failures
- [x] Smoke-тест (Internal JWT bypass) покрыт через `{Service}UserContextFilterStrictModeIT` + E2E `InternalJwtIssuerIT`
- [x] `bug-hunter` subagent на полный diff — 4 CRITICAL + 5 HIGH + 5 MEDIUM
- [x] `security-auditor` subagent — 2 CRITICAL + 3 HIGH + 5 MEDIUM, consensus с bug-hunter на 3 блокерах
- [x] **3 блокера фикшены** перед тегом (commit `35640b2`):
  - C1: X-Internal-Token strip (header injection mitigation)
  - C2: X-Login strip (composite rate-limit protection)
  - H3: infrastructure paths в `DualModeUserContextFilter.isInfrastructurePath` (actuator/swagger в strict-mode)
- [x] 9 known issues (KI-1..KI-9) задокументированы в Post-mortem для hot-patch / M03b / M04 / M06
- [x] Post-mortem в PLAN.md: commits list, surprises, lessons learned, acceptance criteria check, metrics, M03b/M04/M06 follow-ups
- [ ] Финальный коммит `chore(m03a): close Internal JWT + rate-limit`
- [ ] `git tag v0.0.0-alpha.3` на финальном коммите (БЕЗ push — жду явного «go»)

---

_Если задача занимает > 4 часов — разрежь её прямо здесь и отметь._
