# M03a — Internal JWT + Rate-limiting

**Статус:** ⏳ в работе
**Старт / финиш:** 2026-04-19 / —
**Estimate:** 5-8 человеко-дней

---

## Scope

Первая половина секьюрити-hardening'а перед релизом v0.0.0. Две независимые
по коду, но связанные по тесту линии:

1. **Internal JWT (C0-1)** — Gateway после валидации внешнего JWT выпускает
   короткоживущий внутренний JWT (RSA, TTL ~5 мин, claims
   `userId/role/groupId`). Downstream-сервисы (academic/schedule/attendance/
   notification-web) валидируют подписью, перестают доверять `X-User-*`
   заголовкам. Двойной режим на период раскатки, потом strict.
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
- **NEW-3** `docs/internal-jwt-spec.md` — формат токена, claims, TTL, ключи.
- **NEW-4** двойной режим: Gateway шлёт И `X-User-*`, И `Authorization: Internal`, сервисы принимают оба, потом strict.
- **NEW-5** smoke-тест в `deploy.yml`: порты 9091-9094 недоступны снаружи VPS.
- **NEW-9** fail-open стратегия при Redis недоступности.
- **NEW-10** Grafana счётчик rate-limit отказов (передаётся в M04).
- **NEW-11** `docs/api-rate-limits.md` — документация лимитов для клиентов.

**Не входит в M03a (отложено в M03b):**
- JWT HttpOnly cookie для refresh (C0-7 Часть А).
- `POST /auth/ws-ticket` + Redis storage (C0-7 Часть А).
- `clearAllClientState()` в PWA/web-panel (C0-5 / C0-7 Часть Б).
- `DELETE /api/notifications/push/subscriptions/me`.
- Breaking frontend migration (`localStorage['rct.auth.v1']` удаление).

## Модули / изменения

### 1. Shared-security библиотека (Internal JWT validator)

- `services/shared/shared-security/` — новый Gradle java-library модуль.
  - `InternalJwtValidator` — парсит `Authorization: Internal <jwt>`, валидирует подписью через RSA public key.
  - `InternalJwtFilter` — Spring `OncePerRequestFilter`, ставит `Authentication` с `userId/role/groupId` claims в `SecurityContext`.
  - `InternalJwtProperties` — `@ConfigurationProperties("rutcampustrack.security.internal-jwt")` (publicKeyUrl, clockSkew).
  - `InternalJwtAutoConfiguration` — `@AutoConfiguration` в `META-INF/spring/...AutoConfiguration.imports`.
  - `DualModeUserContextFilter` — на период раскатки принимает и Internal JWT, и legacy `X-User-*`. Property `rutcampustrack.security.legacy-headers-enabled: true/false` (default true в M03a).
- `gradle/libs.versions.toml` — версия jjwt (уже есть у auth-service) переиспользуется.

### 2. Gateway issuer

- `services/api-gateway/src/main/java/.../security/InternalJwtIssuerFilter.java` — новый `GlobalFilter`:
  - Читает внешний JWT из `Authorization: Bearer`, парсит claims.
  - Генерирует внутренний JWT с теми же RSA-ключами (той же keypair, что auth-service — уже есть `PublicKeyConfig`).
  - TTL 5 мин, claims `userId/role/groupId` + `iss=rutcampustrack-gateway`, `aud=rutcampustrack-internal`.
  - Добавляет `Authorization: Internal <jwt>` И ПОКА ОСТАВЛЯЕТ старые `X-User-*` для dual-mode (NEW-4).
  - После `strict` переключения — `X-User-*` strip.
- `InternalJwtIssuer` — сервис, использует приватный ключ (shared с auth-service через env var / secret path).
- Gateway нужен доступ к приватному ключу (или публикует auth-service через endpoint + Gateway запрашивает). Решение в DECISIONS.md.

### 3. Downstream-сервисы (4 адаптации)

- `services/academic-service/academic-app/build.gradle.kts` — `implementation(project(":services:shared:shared-security"))`.
- `services/academic-service/.../security/UserContextFilter.java` → удалить (или оставить на dual-mode, заменить на `InternalJwtFilter`).
- `application.yml` — `rutcampustrack.security.internal-jwt.public-key-url: http://api-gateway:8080/internal/jwt-public-key` (или direct RSA строка).
- Аналогично в schedule/attendance/notification-web.
- `application.yml` в dev — `legacy-headers-enabled: true` (чтобы локальный dev-stack не сломался мгновенно).

### 4. Rate-limiting в Gateway

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

### 5. LoginRateLimiter рефактор (01 P0-6)

- `services/auth-service/.../service/LoginRateLimiter.java` — ключ `login_attempts:<login>` → `login_attempts:<ip>:<login>` (composite).
- Отдельный IP-only счётчик в Gateway (см. выше) — защищает от distributed brute-force.
- Тест: попытка логина из 100 разных IP с одним `login` — Gateway global-RL остановит; попытка с одного IP на 100 разных `login` — Gateway `/auth/login` по IP остановит; точечный login+IP — LoginRateLimiter.

### 6. Contract-тесты (14 P1-1, 14 P1-2)

- `services/shared/shared-security/src/testFixtures/` — helper для генерации валидных / невалидных Internal JWT (wrong signature, expired, missing claims).
- `services/{academic,schedule,attendance,notification-web}/src/test/.../security/InternalJwtBypassIT.java` — Testcontainers + прямой запрос к порту сервиса (в обход Gateway) без/с кривым Internal JWT → 401.
- `services/api-gateway/src/test/.../RateLimitIT.java` — Testcontainers Redis, 11 запросов на `/otp/verify-by-code` за минуту → 11-й получает 429.
- `services/api-gateway/src/test/.../InternalJwtIssuerIT.java` — запрос с валидным внешним JWT → downstream получает Internal JWT в header.

### 7. ArchUnit / documentation

- `docs/internal-jwt-spec.md` (NEW-3) — формат токена, claims, TTL, ротация ключей, dual-mode flag, миграционный путь.
- `docs/api-rate-limits.md` (NEW-11) — таблица лимитов, 429 поведение, Retry-After header, рекомендации клиенту.
- `docs/architecture.md` — раздел «Internal JWT и rate-limiting» после «Reliable eventing».
- `CLAUDE.md` — статус M03a + обновление раздела архитектуры.
- `CHANGELOG.md [Unreleased]` — Added/Changed M03a.

## Acceptance criteria

- [ ] **Прямой запрос на downstream без Internal JWT → 401.** IT для всех 4 сервисов (academic/schedule/attendance/notification-web). Contract-тест поймает bypass.
- [ ] **Gateway генерирует валидный Internal JWT.** IT: внешний JWT → Gateway → downstream получает `Authorization: Internal <jwt>` с claims `userId/role/groupId`, подпись совпадает с публичным ключом.
- [ ] **Dual-mode работает.** `legacy-headers-enabled=true` → downstream принимает и Internal JWT, и старые `X-User-*` (переходный период). `legacy-headers-enabled=false` → только Internal JWT.
- [ ] **Rate-limit срабатывает.** 11 `/auth/otp/verify-by-code` за минуту → 11-й возвращает 429 с `Retry-After`. Testcontainers Redis.
- [ ] **Fail-open при Redis down.** Testcontainers Redis → `docker stop redis` → запрос проходит + WARN в логе.
- [ ] **`LoginRateLimiter` композитный ключ.** Unit-тест: попытки с разных IP на один login не аккумулируются в одной корзине.
- [ ] **RFC 7807 для 429.** Gateway возвращает `application/problem+json` с `type/title/status=429/detail`.
- [ ] **`./gradlew build` зелёный** для shared-security + 4 downstream + Gateway.
- [ ] **`docs/internal-jwt-spec.md` + `docs/api-rate-limits.md`** написаны (NEW-3, NEW-11).

## Dependencies

- **Блокирует:** M03b (JWT cookie + ws-ticket) — `/auth/ws-ticket` endpoint защищается Internal JWT; rate-limit на `/auth/refresh` настроен в M03a. M07 (Frontend Hardening) — openapi-typescript spec учитывает `/internal/*` endpoints. M04 (Observability) — метрики rate-limit отказов.
- **Блокируется:** M01 (shared-web для RFC 7807), M02 (ShedLock — используется для periodic publish JWT public key, если Gateway пуллит auth-service вместо shared-secret).
- **Parallel safe:** M04 Observability, M05 Performance, M06 Ops & Supply Chain.

## Artifacts

- `services/shared/shared-security/` — новый модуль (validator + filter + autoconfig).
- `services/api-gateway/.../security/InternalJwtIssuerFilter.java`, `IpKeyResolver.java`, etc.
- 4 миграции downstream: `UserContextFilter` → `InternalJwtFilter` + dual-mode.
- `docs/internal-jwt-spec.md` (NEW-3).
- `docs/api-rate-limits.md` (NEW-11).
- `docs/architecture.md` — новый раздел.
- `CHANGELOG.md [Unreleased]` — Added/Changed.

---

_Никаких «why», «motivation», «background» — это уже в 99-executive-summary.md
и OWNER-ANSWERS.md. Здесь только WHAT и DONE-критерии._

## Post-mortem

_Заполняется в конце milestone'а._
