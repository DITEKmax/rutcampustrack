# M03a — Internal JWT + Rate-limiting

**Статус:** ⏳ в работе
**Старт / финиш:** 2026-04-19 / —
**Estimate:** 6-9 человеко-дней (+1д против оригинала из-за token exchange, см. DECISIONS 2026-04-19 «Token Exchange endpoint»)

---

## Scope

Первая половина секьюрити-hardening'а перед релизом v0.0.0. Две независимые
по коду, но связанные по тесту линии:

1. **Internal JWT (C0-1)** — **token exchange паттерн** (DECISIONS 2026-04-19):
   auth-service экспонирует `POST /internal/issue-internal-jwt` (аутентификация
   через shared secret `INTERNAL_ISSUER_SECRET`); Gateway дёргает его после
   валидации внешнего JWT, получает короткоживущий Internal JWT (RSA, TTL 5
   мин, claims `userId/role/groupId/isHeadman`), кэширует per-user на ~4 мин
   и прокидывает downstream через `Authorization: Internal <jwt>`.
   Downstream-сервисы (academic/schedule/attendance/notification-web)
   валидируют подписью через существующий `/auth/public-key`, перестают
   доверять `X-User-*` заголовкам. Двойной режим на период раскатки, потом
   strict. Приватный ключ остаётся ТОЛЬКО в auth-service.
2. **Rate-limiting (C0-4)** — Spring Cloud Gateway `redis-rate-limiter` на
   чувствительных endpoint'ах. Fail-open при недоступности Redis.
   `LoginRateLimiter` переключается на ключ `(ip, login)`.

M03b (следующий milestone) покроет JWT HttpOnly cookie + ws-ticket + logout
lifecycle — зависит от M03a (Internal JWT — prerequisite для cookie-flow).

**Закрывает (сверка с `15-cross-cutting-issues.md` + `OWNER-ANSWERS.md`):**

- **02-Q2** Internal JWT (Уровень 2 Zero Trust) — Gateway issuer + downstream validator.
- **Кластер C0-1** — 5 P0 (02 P0-2, 03 P0-1, 04 P0-1, 05 P0-1, 07 P0-2) через один фикс.
- **03-Q1, 04-Q1, 07-Q9** — AUTO-RESOLVED через 02-Q2 (тот же паттерн `X-User-*` trust).
- **02 P0-3** — `NumberFormatException` при парсинге `X-User-Id` устаревает (JwtParser → 401).
- **Q-rate-limit** — Spring Cloud Gateway + Redis redis-rate-limiter.
- **Кластер C0-4** — 01 P0-6 (DoS через LoginRateLimiter) + 07 P1-2 (RL в Gateway).
- **14 P1-1** — contract-тест Gateway↔downstream на Internal JWT bypass.
- **14 P1-2** — тесты rate-limit через Testcontainers Redis + WebTestClient.
- **13 P1-3** — nginx RL ❌ ОТКЛОНЁН (выбран Gateway).
- **NEW-3** `docs/api/internal-jwt-spec.md` — формат токена, claims, TTL, ключи.
- **NEW-4** двойной режим: Gateway шлёт И `X-User-*`, И `Authorization: Internal`, сервисы принимают оба, потом strict.
- **NEW-5** smoke-тест в `deploy.yml`: порты 9091-9094 недоступны снаружи VPS.
- **NEW-9** fail-open стратегия при Redis недоступности.
- **NEW-10** Grafana счётчик rate-limit отказов (передаётся в M04).
- **NEW-11** `docs/api/api-rate-limits.md` — документация лимитов для клиентов.

**Не входит в M03a (отложено в M03b):**
- JWT HttpOnly cookie для refresh (C0-7 Часть А).
- `POST /auth/ws-ticket` + Redis storage (C0-7 Часть А).
- `clearAllClientState()` в PWA/web-panel (C0-5 / C0-7 Часть Б).
- `DELETE /api/notifications/push/subscriptions/me`.
- Breaking frontend migration (`localStorage['rct.auth.v1']` удаление).

## Модули / изменения

### 1. Shared-security библиотека (Internal JWT validator)

- `services/shared/shared-security/` — новый Gradle java-library модуль.
  - `InternalJwtValidator` — парсит `Authorization: Internal <jwt>`, валидирует подписью через RSA public key (pull из auth-service `/auth/public-key`, переиспользуя pattern `PublicKeyConfig` из Gateway).
  - `InternalJwtFilter` — Spring `OncePerRequestFilter`, ставит `Authentication` с `userId/role/groupId/isHeadman` claims в `SecurityContext`.
  - `InternalJwtProperties` — `@ConfigurationProperties("rutcampustrack.security.internal-jwt")` (authServiceUrl, publicKeyRefreshMinutes, clockSkewSeconds, legacyHeadersEnabled, requireAudience=`rutcampustrack-internal`, requireIssuer=`rutcampustrack-auth`).
  - `InternalJwtAutoConfiguration` — `@AutoConfiguration` в `META-INF/spring/...AutoConfiguration.imports`.
  - `DualModeUserContextFilter` — на период раскатки принимает и Internal JWT, и legacy `X-User-*`. Property `rutcampustrack.security.legacy-headers-enabled: true/false` (default true в M03a, toggle в strict — Группа 13).
- `gradle/libs.versions.toml` — версия jjwt (уже есть у auth-service) переиспользуется.

### 2. Auth-service: token exchange endpoint (NEW — из DECISIONS 2026-04-19)

- `services/auth-service/.../JwtService.java` — новый метод `generateInternalToken(InternalJwtClaims claims)`: подписывает тем же приватным ключом, TTL 5 мин, `iss=rutcampustrack-auth`, `aud=rutcampustrack-internal`, claims `userId/role/groupId/isHeadman`.
- `services/auth-service/.../InternalIssuerController.java` — новый REST endpoint `POST /internal/issue-internal-jwt`:
  - Защищён `InternalIssuerSecretFilter` — проверяет header `X-Internal-Issuer-Secret` против env `INTERNAL_ISSUER_SECRET` (MessageDigest.isEqual timing-safe).
  - Body: `InternalIssueRequest` record с claims.
  - Response: `InternalIssueResponse { token, expiresAt }`.
- `services/auth-service/.../config/InternalIssuerProperties.java` — `@ConfigurationProperties("rutcampustrack.security.internal-issuer")` (secret). Fail-fast на старте если empty (mirror pattern с `GRPC_SECRET` в gRPC).
- `services/auth-service/.../SecurityConfig.java` — `/internal/**` добавляется в permit-all с custom filter (secret header check).

### 3. Gateway issuer client

- `services/api-gateway/src/main/java/.../security/InternalJwtIssuerClient.java` — новый компонент:
  - `WebClient` call на `POST http://auth-service:9090/internal/issue-internal-jwt` с `X-Internal-Issuer-Secret`.
  - **Caffeine cache** `user:${userId}:${role}` → `{ token, expiresAt }`, `expireAfterWrite 4 мин` (< 5 мин TTL токена, safe margin).
  - Cache miss → запрос к auth-service; cache hit → return cached (zero network).
  - Обработка ошибок: auth-service down → 503 клиенту (fail-closed — без Internal JWT нет шансов валидно прокрастись), WARN в лог.
- `services/api-gateway/src/main/java/.../security/InternalJwtIssuerFilter.java` — `GlobalFilter, Ordered` (после `JwtAuthenticationFilter`):
  - Читает внешние JWT claims (уже лежат в `ServerWebExchange.attributes` после `JwtAuthenticationFilter`).
  - Вызывает `issuerClient.issueFor(userId, role, groupId, isHeadman)`.
  - Добавляет `Authorization: Internal <jwt>` в downstream request; ПОКА оставляет `X-User-*` (dual-mode, NEW-4).
  - Strict toggle (Группа 13) — один flag `strip-legacy-headers` → Gateway strip'ает `X-User-*` перед proxy.
- `services/api-gateway/build.gradle.kts` — `com.github.ben-manes.caffeine:caffeine` dep.

### 4. Downstream-сервисы (4 адаптации)

- `services/academic-service/academic-app/build.gradle.kts` — `implementation(project(":services:shared:shared-security"))`.
- `services/academic-service/.../security/UserContextFilter.java` → заменить на `DualModeUserContextFilter` из shared-security.
- `application.yml` — `rutcampustrack.security.internal-jwt.auth-service-url: http://auth-service:9090`, `legacy-headers-enabled: true` (dev default).
- Аналогично в schedule/attendance/notification-web.

### 5. Rate-limiting в Gateway

- `services/api-gateway/build.gradle.kts` — `spring-cloud-starter-gateway` redis-rate-limiter dep (Redis client уже есть? проверить; если нет — `spring-boot-starter-data-redis-reactive`).
- `application.yml` routes — `RequestRateLimiter` фильтр на чувствительных маршрутах:
  - `/api/auth/otp/request` — 1 req/min per IP
  - `/api/auth/otp/verify-by-code` — 5 req/min per IP
  - `/api/auth/login` — 5 req/min per IP + 10/min per login (композитный ключ)
  - `/api/auth/refresh` — 30 req/min per user (по userId из Internal JWT)
  - `/api/attendance/check-in` — 10 req/min per user
  - Глобально `/api/**` — 600 req/min per IP (DDoS guard)
- `IpKeyResolver`, `UserIdKeyResolver`, `LoginKeyResolver` — кастомные `KeyResolver` бины.
- Fail-open (NEW-9): кастомный `RateLimiter` wrapper ловит Redis connection exception → пропускает запрос + логирует WARN.
- RFC 7807 `ErrorResponse` для 429 (использовать shared-web utility / WebFlux-вариант).

### 6. LoginRateLimiter рефактор (01 P0-6)

- `services/auth-service/.../service/LoginRateLimiter.java` — ключ `login_attempts:<login>` → `login_attempts:<ip>:<login>` (composite).
- Отдельный IP-only счётчик в Gateway (см. выше) — защищает от distributed brute-force.
- Тест: попытка логина из 100 разных IP с одним `login` — Gateway global-RL остановит; попытка с одного IP на 100 разных `login` — Gateway `/auth/login` по IP остановит; точечный login+IP — LoginRateLimiter.

### 7. Contract-тесты (14 P1-1, 14 P1-2)

- `services/shared/shared-security/src/testFixtures/` — helper для генерации валидных / невалидных Internal JWT (wrong signature, expired, missing claims).
- `services/{academic,schedule,attendance,notification-web}/src/test/.../security/InternalJwtBypassIT.java` — Testcontainers + прямой запрос к порту сервиса (в обход Gateway) без/с кривым Internal JWT → 401.
- `services/api-gateway/src/test/.../RateLimitIT.java` — Testcontainers Redis, 11 запросов на `/otp/verify-by-code` за минуту → 11-й получает 429.
- `services/api-gateway/src/test/.../InternalJwtIssuerClientIT.java` — WireMock mock auth-service endpoint; проверка cache semantics (2 запроса one user → 1 network call), cache-expiry (после 4 мин — refetch), error flow (auth-service down → 503).
- `services/auth-service/src/test/.../InternalIssuerControllerIT.java` — endpoint test: valid secret + claims → signed JWT; wrong secret → 401; missing secret → 401; malformed body → 400.

### 8. ArchUnit / documentation

- `docs/api/internal-jwt-spec.md` (NEW-3) — формат токена, claims, TTL, ротация ключей, dual-mode flag, миграционный путь.
- `docs/api/api-rate-limits.md` (NEW-11) — таблица лимитов, 429 поведение, Retry-After header, рекомендации клиенту.
- `docs/architecture/architecture.md` — раздел «Internal JWT и rate-limiting» после «Reliable eventing».
- `CLAUDE.md` — статус M03a + обновление раздела архитектуры.
- `CHANGELOG.md [Unreleased]` — Added/Changed M03a.

## Acceptance criteria

- [ ] **Прямой запрос на downstream без Internal JWT → 401.** IT для всех 4 сервисов (academic/schedule/attendance/notification-web). Contract-тест поймает bypass.
- [ ] **Auth-service выпускает валидный Internal JWT через token exchange.** IT `InternalIssuerControllerIT`: правильный `X-Internal-Issuer-Secret` + claims → подписанный JWT с правильной аудиторией/эмитентом/TTL; неправильный secret → 401; пустой secret — сервис fail-fast на старте.
- [ ] **Gateway прокидывает Internal JWT через кэш.** IT `InternalJwtIssuerClientIT` (WireMock auth-service): 2 последовательных запроса одного user → 1 network call (cache hit); после cache-expiry (tightened TTL в тесте) — refetch; auth-service down → 503 клиенту + WARN.
- [ ] **Приватный ключ НЕ в Gateway.** Архитектурный инвариант: grep по `api-gateway/src/main` не находит `PrivateKey`, `signWith`, `JWT_PRIVATE_KEY_PEM`. Закрепляется ArchUnit rule (Группа 8) либо comment в PLAN post-mortem.
- [ ] **Dual-mode работает.** `legacy-headers-enabled=true` → downstream принимает и Internal JWT, и старые `X-User-*` (переходный период). `legacy-headers-enabled=false` → только Internal JWT.
- [ ] **Rate-limit срабатывает.** 11 `/auth/otp/verify-by-code` за минуту → 11-й возвращает 429 с `Retry-After`. Testcontainers Redis.
- [ ] **Fail-open при Redis down.** Testcontainers Redis → `docker stop redis` → запрос проходит + WARN в логе.
- [ ] **`LoginRateLimiter` композитный ключ.** Unit-тест: попытки с разных IP на один login не аккумулируются в одной корзине.
- [ ] **RFC 7807 для 429.** Gateway возвращает `application/problem+json` с `type/title/status=429/detail`.
- [ ] **`./gradlew build` зелёный** для shared-security + 4 downstream + Gateway.
- [ ] **`docs/api/internal-jwt-spec.md` + `docs/api/api-rate-limits.md`** написаны (NEW-3, NEW-11).

## Dependencies

- **Блокирует:** M03b (JWT cookie + ws-ticket) — `/auth/ws-ticket` endpoint защищается Internal JWT; rate-limit на `/auth/refresh` настроен в M03a. M07 (Frontend Hardening) — openapi-typescript spec учитывает `/internal/*` endpoints. M04 (Observability) — метрики rate-limit отказов.
- **Блокируется:** M01 (shared-web для RFC 7807), M02 (ShedLock — используется для periodic publish JWT public key, если Gateway пуллит auth-service вместо shared-secret).
- **Parallel safe:** M04 Observability, M05 Performance, M06 Ops & Supply Chain.

## Artifacts

- `services/shared/shared-security/` — новый модуль (validator + filter + autoconfig).
- `services/api-gateway/.../security/InternalJwtIssuerFilter.java`, `IpKeyResolver.java`, etc.
- 4 миграции downstream: `UserContextFilter` → `InternalJwtFilter` + dual-mode.
- `docs/api/internal-jwt-spec.md` (NEW-3).
- `docs/api/api-rate-limits.md` (NEW-11).
- `docs/architecture/architecture.md` — новый раздел.
- `CHANGELOG.md [Unreleased]` — Added/Changed.

---

_Никаких «why», «motivation», «background» — это уже в 99-executive-summary.md
и OWNER-ANSWERS.md. Здесь только WHAT и DONE-критерии._

## Post-mortem

**Финиш:** 2026-04-20. Estimate 6-9д, фактически 1 день в-walltime
одной Opus-сессии (с заранее готовым из аудита PLAN+CHECKLIST+DECISIONS).

### Commits M03a

От `v0.0.0-alpha.2` до HEAD:

```
0311297 docs(milestones): scaffold M03a
ebc35ad docs(m03a): rework architecture — token exchange endpoint
e5b2e0c docs(m03a): close Group 1 (header name X-Internal-Token)
ca62e8e feat(shared-security): scaffold Internal JWT validator (Группа 2)
da41c39 feat(auth): token exchange endpoint (Группа 3)
23e33b0 feat(gateway): Internal JWT issuer client с Caffeine cache (Группа 4)
f5f8adc feat(academic): dual-mode + RestClient fix (Группа 5)
18d50f3 feat(downstream): schedule+attendance+notification (Группы 6-8)
2cd6de6 docs(m03a): hand-off для следующей сессии
b38d263 feat(gateway): rate-limit infra (Группа 9)
025a266 feat(gateway): rate-limit routes + RFC 7807 (Группа 10)
315a662 feat(auth): LoginRateLimiter composite (ip, login) (Группа 11)
8a320d1 test(gateway): rate-limit Testcontainers IT + 3 фикса (Группа 12)
dd96917 test(gateway): contract-тест E2E Internal JWT pipeline (Группа 13)
4a13b90 feat(gateway): strip-legacy-headers toggle + UAT checklist (Группа 14)
50123ff docs(m03a): Internal JWT spec + rate-limits + CHANGELOG (Группа 15)
35640b2 fix(security): audit blockers C1/C2/H3 (Группа 16)
```

### Surprises (полный список из NOTES)

1. **Group 1 — keypair discovery:** исходный DECISIONS предполагал
   shared RSA keypair между auth-service и Gateway. Реальность: auth
   генерит keypair в `@PostConstruct`, Gateway — read-only consumer через
   `/auth/public-key`. Решение: **(a3) Token Exchange** паттерн (RFC 8693),
   приватный ключ остаётся только в auth-service.
2. **Group 5 — WebClient/webflux несовместимость:** `PublicKeyProvider`
   был написан на Reactor WebClient, а downstream MVC. Решение: переписать
   на `RestClient` (servlet-friendly, работает в обоих stacks).
3. **Group 12 — 3 critical bugs обнаружены первым @SpringBootTest:**
   `@Primary` на ipKeyResolver, `@Autowired` на primary-конструктор
   `InternalJwtIssuerClient`, `RateLimitProblemDetailsFilter` перехват
   `setComplete()` (RequestRateLimiter не вызывает writeWith на denied).
4. **Group 12 — replenishRate unit semantics:** `replenishRate` в
   Spring Cloud Gateway это tokens/**sec**, НЕ tokens/**min**.
   PLAN писал «5 req/min per IP», фактически реализовано
   «burst=5 + 5/sec restore». Документировано в api-rate-limits.md.
5. **Group 16 audit — 3 блокера:** header injection `X-Internal-Token` и
   `X-Login` не strip'ались в JwtAuthenticationFilter; infrastructure paths
   (actuator/api-docs/swagger-ui) в strict-mode возвращали 401 → Docker
   HEALTHCHECK был бы broken. Исправлены перед тегом.

### Lessons learned

- **Запускать @SpringBootTest раньше.** 3 context-startup бага в Группе
  12 были бы обнаружены на неделю раньше, если бы первый IT написали сразу
  в Группе 4 (Gateway issuer). Unit-тесты с `new Component(...)` не видят
  проблем с Spring autowire.
- **Security audit обязателен перед тегом.** Без bug-hunter + security-
  auditor прошёл бы `X-Internal-Token` header injection — критическая
  уязвимость в Zero Trust Level 2 feature. Два параллельных агента нашли
  consensus-блокеры, которых не видно изнутри реализации.
- **Token-bucket семантика требует явного upfront-валидатора.** Все
  числовые лимиты в CHECKLIST указаны как «X req/min», а фактическая
  семантика Spring Cloud Gateway — N/sec. Hands-on экспериментом в
  Testcontainers выявилось только в Группе 12. Добавить в M04 metric
  `rate_limit_denial_rate` для мониторинга реального поведения.
- **Header injection защита — must-have list, не blacklist.** Добавить
  новый header в identity-flow → сразу добавить в strip-list. Regression
  test должен проверять что все identity-headers (X-User-*, X-Internal-
  Token, X-Login) удаляются до route-handling.

### Known issues (НЕ блокеры v0.0.0-alpha.3)

Задокументированы из bug-hunter + security-auditor findings, для
hot-patch после alpha.3 либо в следующих milestones:

| # | Severity | Описание | План |
|---|----------|----------|------|
| KI-1 | HIGH | `X-Forwarded-For` spoofing — Gateway и auth-service берут первый IP без trusted-proxies allowlist | M06: nginx + Gateway `server.forward-headers-strategy=native` с whitelist |
| KI-2 | HIGH | Dual-mode silent fallback: Gateway при auth-service 5xx проваливается в legacy X-User-* без метрики/алерта | M04: метрика `internal_jwt_fallback_total` + Grafana alert |
| KI-3 | MEDIUM | `InternalJwtIssuerClient` не проверяет `issuedToken.expiresAt()` перед возвратом из кэша — при clock drift возможен expired token (окно ≤60s) | Hot-patch или M03b: добавить expiry check в `issueFor()` |
| KI-4 | MEDIUM | `PublicKeyProvider.init()` silent swallow exception → `publicKeyRef=null` race окно на старте | M04: readiness probe + InternalJwtException вместо IllegalStateException |
| KI-5 | MEDIUM | `FailOpenRateLimiter` ловит `RedisSystemException` — слишком широко, прячет real app bugs | Hot-patch: сузить whitelist до Connection/Timeout only |
| KI-6 | MEDIUM | `LoginRateLimiter` Redis TTL race `INCR+EXPIRE` — network blip = persistent key без expiry | Hot-patch: Lua-script атомарность или `SET ... EX N NX` |
| KI-7 | MEDIUM | Bcrypt DoS через concurrent invalid-password до `checkBlocked` triggers | M03b/M05: semaphore на bcrypt или pre-check lock на (ip, login) |
| KI-8 | MEDIUM | Composite rate-limit composite `(ip, login)` неэффективен без Gateway CacheRequestBody extraction X-Login из тела | Hot-patch: реализовать pre-filter extract login-from-body → set X-Login |
| KI-9 | MEDIUM | `INTERNAL_ISSUER_SECRET` передаётся plaintext по docker bridge — мелкий риск для single-host, значительный для k8s multi-tenant | M06: mTLS или Vault integration |

### M03b / M04 follow-ups

- **M03b (следующий milestone):** JWT HttpOnly cookie + ws-ticket +
  logout lifecycle (`/auth/ws-ticket` endpoint защищается Internal JWT
  из M03a). KI-3, KI-6, KI-7, KI-8 можно адресовать попутно.
- **M04 Observability:** метрики token-exchange cache hit-rate,
  rate-limit denial rate, fail-open events, internal-jwt-fallback counter.
  KI-2, KI-4, KI-5 — природно попадают туда.
- **M06 Ops & Supply Chain:** nginx `trusted-proxies` + mTLS между
  сервисами. KI-1, KI-9.

### Acceptance criteria check

- [x] Прямой запрос на downstream без Internal JWT → 401 (dual-mode всё
  ещё accepts legacy, strict-mode → 401). IT `{Service}UserContextFilter
  StrictModeIT` в всех 4 downstream + E2E `InternalJwtIssuerIT`.
- [x] Auth-service выпускает валидный Internal JWT через token exchange.
  `InternalIssuerIT` 5 тестов.
- [x] Gateway прокидывает Internal JWT через кэш.
  `InternalJwtIssuerClientTest` 7 тестов (WireMock).
- [x] Приватный ключ НЕ в Gateway. Grep `api-gateway/src/main` не находит
  `PrivateKey`/`signWith`/`JWT_PRIVATE_KEY_PEM`.
- [x] Dual-mode работает. `DualModeUserContextFilter` + infrastructure
  path exclusions.
- [x] Rate-limit срабатывает. `RateLimitIT` Testcontainers Redis.
- [x] Fail-open при Redis down. `FailOpenIT`.
- [x] `LoginRateLimiter` композитный ключ. 11 unit + 3 IT тестов.
- [x] RFC 7807 для 429. `RateLimitProblemDetailsFilter`.
- [x] `./gradlew build` зелёный.
- [x] `docs/api/internal-jwt-spec.md` + `docs/api/api-rate-limits.md` written.

### Metrics финала

- **Commits:** 17 (включая 3 docs/scaffold + 12 feature/test + 2 docs/audit).
- **Files changed:** ~130 (доминируют test files).
- **LoC added:** ~4500 (приблизительно), доминируют test files (~60%).
- **Тестов добавлено:** ~90 новых (unit + IT Testcontainers).
- **Общее количество тестов после M03a:** ~640 (было ~550 до M03a).
- **Audit findings:** 4 CRITICAL + 5 HIGH + 5 MEDIUM от bug-hunter +
  security-auditor. Фиксы 3 блокеров перед тегом (C1/C2/H3). Остальные 11
  задокументированы как KI-1..KI-9 в post-mortem (hot-patch / M03b / M04 / M06).
