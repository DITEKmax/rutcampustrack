# M03a Checklist

Порядок важен — shared-security scaffold первым, чтобы downstream могли
подключаться. Gateway issuer перед downstream validation (иначе нечем
тестировать). Rate-limit последним (независим по коду, но проще дебажить
после того как Internal JWT flow работает).

## Группа 1 — Discovery + решение о ключе

- [ ] Grep `@Scheduled` + `PublicKeyConfig` + `/internal/jwt-public-key` в api-gateway и auth-service — понять текущее состояние публикации RSA public key
- [ ] DECISIONS.md: где держится приватный ключ для Internal JWT issuer — shared с auth-service (env var), или Gateway получает через `/internal/jwt-public-key` из auth-service и подписывает отдельной keypair?
- [ ] DECISIONS.md: `Authorization: Internal <jwt>` header name vs `X-Internal-Token` (рекомендация — `Authorization: Internal` по OWNER-ANSWERS 02-Q2)
- [ ] DECISIONS.md: dual-mode flag дефолт в prod — `true` (на период раскатки) или `false` сразу (breaking)? Рекомендация — `true` + отдельный commit на переключение после UAT

## Группа 2 — shared-security scaffold

- [ ] `services/shared/shared-security/build.gradle.kts` + `settings.gradle.kts` include
- [ ] `InternalJwtProperties` (`@ConfigurationProperties`) + `META-INF/spring.factories` или `AutoConfiguration.imports`
- [ ] `InternalJwtValidator` — парсит/валидирует подписью (`jjwt-api`/`jjwt-impl`/`jjwt-jackson`)
- [ ] `InternalJwtFilter` extends `OncePerRequestFilter` — ставит `Authentication` в `SecurityContext`
- [ ] `DualModeUserContextFilter` — dual-mode: Internal JWT есть → использует, иначе legacy `X-User-*` если flag включён
- [ ] `InternalJwtAutoConfiguration` + tests для autoconfig (ApplicationContextRunner)
- [ ] `src/testFixtures/` — helper `InternalJwtTestFactory.validToken()`, `expiredToken()`, `invalidSignature()`

## Группа 3 — Gateway issuer

- [ ] `services/api-gateway/build.gradle.kts` — `jjwt-*` deps (если нет)
- [ ] `InternalJwtIssuer` service — приватный ключ из env / PEM file, подписывает claims + TTL 5 мин
- [ ] `InternalJwtIssuerFilter implements GlobalFilter, Ordered` — после `JwtAuthenticationFilter`:
  - Читает claims из внешнего JWT (уже в `ServerWebExchange.attributes`)
  - Генерирует Internal JWT
  - Добавляет `Authorization: Internal <jwt>` в downstream request
  - Dual-mode: НЕ strip'ает `X-User-*` (legacy)
- [ ] IT: валидный внешний JWT → downstream видит `Authorization: Internal <jwt>` с правильными claims
- [ ] Endpoint `/internal/jwt-public-key` (или переиспользовать существующий) — downstream пуллит публичный ключ

## Группа 4 — Downstream миграция (academic)

- [ ] `services/academic-service/academic-app/build.gradle.kts` — `implementation(project(":services:shared:shared-security"))`
- [ ] `application.yml` — `rutcampustrack.security.internal-jwt.public-key-url: ...`, `legacy-headers-enabled: true`
- [ ] `UserContextFilter` — либо удалить и использовать `DualModeUserContextFilter`, либо сохранить и перенацелить
- [ ] `RequestContext` читается из `SecurityContext` / `Authentication.getPrincipal()`
- [ ] IT `InternalJwtBypassIT`: прямой `MockMvc` запрос без Internal JWT → 401
- [ ] IT `LegacyHeadersIT`: `legacy-headers-enabled=true` → X-User-* принимается; `=false` → 401

## Группа 5 — Downstream миграция (schedule)

- [ ] build.gradle.kts + application.yml (аналогично academic)
- [ ] `UserContextFilter` replace
- [ ] `InternalJwtBypassIT` + `LegacyHeadersIT`

## Группа 6 — Downstream миграция (attendance)

- [ ] build.gradle.kts + application.yml
- [ ] `UserContextFilter` replace
- [ ] `InternalJwtBypassIT` + `LegacyHeadersIT`

## Группа 7 — Downstream миграция (notification-web)

- [ ] build.gradle.kts + application.yml
- [ ] `UserContextFilter` replace
- [ ] `InternalJwtBypassIT` + `LegacyHeadersIT`
- [ ] WebSocket handshake — отдельно (текущий token-flow НЕ меняется в M03a, это scope M03b через ws-ticket)

## Группа 8 — Rate-limit Gateway deps + infra

- [ ] `services/api-gateway/build.gradle.kts` — `spring-boot-starter-data-redis-reactive` (если нет), `spring-cloud-starter-gateway-redis-rate-limiter` (проверить artifact name для Spring Cloud 2024.x)
- [ ] `application.yml` spring.data.redis host/port — такой же Redis что и auth-service (re-use)
- [ ] `RedisRateLimiterConfig` — бины `IpKeyResolver`, `UserIdKeyResolver`, `LoginKeyResolver`, `CompositeIpLoginKeyResolver`
- [ ] Fail-open wrapper: кастомный `RateLimiter` ловит `RedisConnectionFailureException` → `Response(allowed=true)` + WARN лог

## Группа 9 — Rate-limit routes

- [ ] `/api/auth/otp/request` — RL 1 req/min per IP
- [ ] `/api/auth/otp/verify-by-code` — RL 5 req/min per IP
- [ ] `/api/auth/login` — RL 5 req/min per IP + 10 req/min per login (два последовательных фильтра)
- [ ] `/api/auth/refresh` — RL 30 req/min per user (UserIdKeyResolver из Internal JWT)
- [ ] `/api/attendance/check-in` — RL 10 req/min per user
- [ ] `/api/**` — глобальный RL 600 req/min per IP (последний — DDoS guard)
- [ ] RFC 7807 Problem Details для 429: `type=...rate-limit`, `title="Too Many Requests"`, `status=429`, `detail`, `Retry-After` header

## Группа 10 — LoginRateLimiter рефактор (01 P0-6)

- [ ] `services/auth-service/.../service/LoginRateLimiter.java` — ключ Redis: `login_attempts:<ip>:<login>` (composite)
- [ ] IP извлекается из `X-Forwarded-For` (первый IP) или `RemoteAddr` fallback
- [ ] Unit-тест: 5 попыток с `ip1+login1` не блокируют `ip2+login1`
- [ ] Integration: 5 failed login с одного IP + login → 6-й 429 (или `LoginRateLimiter` exception)
- [ ] Документация в `docs/api-rate-limits.md` — различие Gateway global-RL vs auth-service LoginRateLimiter

## Группа 11 — Rate-limit тесты (14 P1-2)

- [ ] `services/api-gateway/src/test/.../RateLimitIT.java` — Testcontainers Redis:
  - 11 запросов на `/otp/verify-by-code` за минуту → 11-й 429
  - `Retry-After` header присутствует
  - Problem Details body
- [ ] `RateLimitFailOpenIT` — Redis недоступен (`container.stop()` или wrong port) → запрос проходит, WARN в логах
- [ ] `CompositeLoginKeyResolverIT` — разные IP одного login'а считаются раздельно

## Группа 12 — Contract-тест Gateway↔downstream (14 P1-1)

- [ ] `services/api-gateway/src/test/.../InternalJwtIssuerIT.java` — Testcontainers (Gateway + downstream mock):
  - Валидный внешний JWT → downstream видит Internal JWT с правильными claims
  - Невалидный внешний JWT → 401 (не доходит до downstream)
  - Истёкший внешний JWT → 401
- [ ] Smoke: попытка прямого запроса на :9091 без Internal JWT → 401

## Группа 13 — Смена на strict mode (подготовка к M03b)

- [ ] `application.yml` в prod — `legacy-headers-enabled: false` (отдельный commit, применяется после UAT)
- [ ] `InternalJwtIssuerFilter` в Gateway — добавить strip `X-User-*` headers (вторая фаза, отдельный commit/flag)
- [ ] NOTES.md — отметить, что переключение запланировано как последний commit M03a перед тегом v0.0.0-alpha.3
- [ ] UAT golden path checklist: admin login, teacher journal, student check-in, headman operations — все проходят на strict mode

## Группа 14 — Документация + artifacts

- [ ] `docs/internal-jwt-spec.md` (NEW-3) — формат, claims, TTL, ключи, dual-mode, миграционный путь
- [ ] `docs/api-rate-limits.md` (NEW-11) — таблица лимитов, 429 поведение, Retry-After, клиентский backoff
- [ ] `docs/architecture.md` → раздел «Internal JWT и rate-limiting» после «Reliable eventing»
- [ ] `CHANGELOG.md [Unreleased]` → Added (shared-security, Internal JWT issuer, rate-limit 6 routes) + Changed (`UserContextFilter` → Internal JWT, `LoginRateLimiter` composite key)
- [ ] `CLAUDE.md` — статус M03a → ✅ + обновить архитектурный раздел (порт 8080 теперь issuer, shared-security в структуре)
- [ ] `docs/milestones/README.md` → M03a ✅ (если M03 строка — заменить на M03a/M03b)
- [ ] Закрыть пункты COVERAGE-AUDIT.md, relevant to M03a: колонка «Closed in» — commit SHA

## Группа 15 — Финал

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
