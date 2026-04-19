# M03a Notes

Живой файл. Пиши сюда:
- **Отклонения от плана:** «решил сделать X вместо Y, потому что...»
- **Измерения:** «p95 latency до: 450ms, после: 120ms»
- **Surprises:** «обнаружил, что Gateway уже держит RSA keypair, а я думал нужно из auth-service тянуть»
- **Вопросы к владельцу:** «fail-open при Redis down — логировать WARN или ERROR?»
- **Технические долги:** «оставил TODO про X — закрою в M{X}»

Не пиши:
- Общие описания модулей (это в PLAN.md).
- WHY-обоснования (это в OWNER-ANSWERS.md и 99-executive-summary.md).
- Пошаговые инструкции (это в CHECKLIST.md).

---

## 2026-04-19 — Старт M03a

- M03 (original, ~14-18д) разделён на M03a (Internal JWT + rate-limit, 5-8д)
  и M03b (JWT cookie + ws-ticket + logout, 8-12д). Решение владельца —
  промежуточный тег v0.0.0-alpha.3 между ними, снижение риска breaking change.
- M02 закрыт коммитом `da38ef3` (2026-04-19), tag `v0.0.0-alpha.2` ещё не поставлен
  (нужно проверить и сделать).
- PLAN/CHECKLIST заполнены из аудита, dual-mode flag default = `true` в M03a
  (breaking переключение — последний commit перед тегом).
- Tag `v0.0.0-alpha.2` уже установлен — ОК.

## 2026-04-19 — SURPRISE: текущая keypair-архитектура не match решение DECISIONS #1

**Discovery Группы 1 (grep):**
- `auth-service/.../JwtService.java:47-83` — keypair **генерируется в `@PostConstruct`**
  из `jwtProperties.keyDir()` (`/app/keys/private.key` + `public.key` + `kid.txt`).
  Если файлов нет — генерит RSA 3072-bit, пишет на диск, кэширует PEM в Redis
  (`jwt:public_key` TTL 1h). Если есть — читает с диска. Env var `JWT_PRIVATE_KEY_PEM`
  НЕ существует.
- `api-gateway/.../PublicKeyConfig.java:40-70` — Gateway только **read-only consumer**:
  пуллит PEM через `WebClient` на `http://auth-service:9090/auth/public-key` каждый
  час (`@Scheduled(fixedRate = 3_600_000)`), хранит в `AtomicReference<PublicKey>`.
  Приватного ключа у Gateway нет сейчас.
- `JwtAuthenticationFilter.java:86` — Gateway использует public key только для ВАЛИДАЦИИ
  входящего внешнего JWT. Gateway ничего не подписывает.

**Что это меняет в DECISIONS #1:** исходное решение (a) «Shared RSA keypair + env var
`JWT_PRIVATE_KEY_PEM`» требует миграции auth-service с keyDir на env var. Это отдельный
architectural risk — keys сейчас persisted volume в prod (`docker-compose.prod.yml`
mounted `/opt/rutcampustrack/keys:/app/keys`), env var — stateless.

**Реальные варианты (с учётом discovery):**
- **(a1)** Мигрировать auth-service на env var `JWT_PRIVATE_KEY_PEM` + `JWT_PUBLIC_KEY_PEM`,
  удалить keyDir auto-gen. Gateway читает ту же env var. Breaking в деплое — нужно
  вытащить текущие ключи из VPS volume, положить в `.env.prod`, удалить volume. Ротация
  через env + рестарт обоих.
- **(a2)** Оставить keyDir в auth-service, **добавить shared volume** `/opt/rutcampustrack/keys`
  mounted в оба сервиса (RO для Gateway). Gateway читает `/app/keys/private.key` напрямую.
  Минимум изменений, но Gateway получает file access вместо network.
- **(a3)** **Gateway НЕ подписывает.** Auth-service получает новый internal endpoint
  `POST /internal/issue-internal-jwt` (авторизация — shared secret между Gateway и
  auth-service через env `INTERNAL_ISSUER_SECRET`). Gateway на каждый запрос (или
  раз в 4 мин с TTL-cache) дёргает endpoint с claims `userId/role/groupId`, получает
  подписанный Internal JWT. Приватный ключ остаётся ТОЛЬКО в auth-service. Trade-off —
  +1 network hop per request (или кэш с TTL 4 мин, invalidation при logout).

**Рекомендация: (a2)** — shared volume. Минимум кода (Gateway не дёргает сеть per
request), минимум миграции (keyDir остаётся), приватный ключ не публикуется нигде
кроме volume в VPS, ротация = подменить файл + `docker compose restart`. Risk —
Gateway получает file read access к секрету, но они уже в одной private-net
через docker-compose.

**Вопрос владельцу:** (a1 / a2 / a3)? Жду решения.

**Ответ:** выбран (a3) Token Exchange endpoint — индустриальный стандарт
(RFC 8693, Google Cloud IAM signJwt). Приватный ключ только в auth-service.

## 2026-04-19 — SURPRISE Группа 5: WebClient требует webflux, academic — MVC

**Проблема:** при попытке подключить `shared-security` в academic-app контекст
не поднимается: `NoClassDefFoundError: WebClient`. `PublicKeyProvider` был
написан на Reactor WebClient, а academic/schedule/attendance/notification-web
используют Spring MVC (`starter-web`), не `starter-webflux`.

**Варианты:**
- (a) Добавить `starter-webflux` в downstream — но MVC+WebFlux на classpath
  вместе даёт Spring Boot автоматический переключатель в reactive mode →
  ломает существующие REST-контроллеры.
- (b) Переписать `PublicKeyProvider` на синхронный `RestClient` (Spring 6.1+).
  Servlet-friendly, не требует Reactor, работает в обоих stack (Gateway WebFlux
  тоже поддерживает RestClient).

**Выбрано: (b)** — `RestClient` в `PublicKeyProvider`. Изменения:
- `shared/shared-security/PublicKeyProvider` — WebClient → RestClient,
  синхронный retrieve(), unchecked exception instead of Mono.error
- `shared-security/build.gradle.kts` — убраны `starter-webflux` + `spring-webflux`
  compileOnly/testImplementation; добавлен `jakarta.annotation-api` явно
  (раньше приходил транзитивно через webflux)
- 18 unit-тестов shared-security всё ещё зелёные

Gateway `InternalJwtIssuerClient` по-прежнему на WebClient (api-gateway
нативно reactive). Gateway `PublicKeyConfig` собственный, НЕ трогаем.

**Последствия:** во всех 4 downstream-сервисах НЕ нужен webflux dep. Пятница
уже добавляла тесты с `starter-webflux` testImplementation в shared-security —
это удалено. Academic build зелёный (197 тестов, +9 новых filter IT).

## 2026-04-19 — Группа 9: deps + infra для rate-limit

**Пояснение по артефактам:** CHECKLIST упоминал
`spring-cloud-starter-gateway-redis-rate-limiter` — такого отдельного артефакта
в Spring Cloud 2024.0.x (gateway-server 4.2.0) **не существует**. RedisRateLimiter
и RequestRateLimiterGatewayFilterFactory идут из `spring-cloud-starter-gateway`
(уже есть). Нужен только redis-reactive client, чтобы Spring Boot autoconfig
поднял `ReactiveStringRedisTemplate` + `RedisScript` для Lua-скрипта
RedisRateLimiter. Добавлен `spring-boot-starter-data-redis-reactive`.

**Про `loginKeyResolver`:** Gateway — reactive WebFlux, body потребляется один
раз, чтобы прочитать `login` из JSON надо ставить `CacheRequestBody` filter
перед rate-limit. Для простоты в Группе 9 резолвер читает login из заголовка
`X-Login`; клиент frontend будет слать его дубликатом при логине (или Gateway
в Группе 10 поставит его сам через `CacheRequestBody` + простая SpEL mutation).
Решение переводится в Группу 10 при оформлении роутов.

**Fail-open wrapper подход:** `@Primary FailOpenRateLimiter extends
AbstractRateLimiter<RedisRateLimiter.Config>` — делегирует стандартному
`redisRateLimiter` (autoconfig-bean), на `RedisConnectionFailureException` /
`QueryTimeoutException` / Lettuce exceptions возвращает `Response(allowed=true,
headers={X-RateLimit-FailOpen: true})` + WARN. `RequestRateLimiterGatewayFilter`
по-умолчанию резолвит `RateLimiter`-бин из контекста — с `@Primary` все роуты
автоматически получают fail-open, без явных `rate-limiter: "#{@bean}"`.

**Результат:** Gateway build зелёный — 47 тестов (было 32, +15 новых:
9 KeyResolver + 6 FailOpenRateLimiter).

**docker-compose.prod.yml fix:** обнаружил что api-gateway в prod не получал
`INTERNAL_ISSUER_SECRET` (должен был попасть в Группе 4, но было забыто —
Gateway там стартовал с dev-default секретом из application.yml, что = FAIL в prod,
т.к. auth-service уже требует его). Добавил `REDIS_HOST/PORT/PASSWORD` +
`INTERNAL_ISSUER_SECRET` + `depends_on: redis` в один commit с Группой 9.

## 2026-04-19 — Группа 10: routes + 429 Problem Details

**Принятый trade-off для `/api/auth/login` composite key:** в WebFlux
Gateway тело запроса — streaming Publisher, читается один раз. Для корректного
извлечения `login` из JSON body необходим `CacheRequestBody` + кастомный
filter, прокидывающий поле в attribute/header → overkill для M03a. Вместо
этого `ipLoginKeyResolver` читает `X-Login` header; клиент frontend
(PWA/web-panel) дублирует `{login}` из body в header при POST /auth/login.
При отсутствии header resolver fallback'ится на только-IP ключ — защита
от брута сохраняется (IP-RL 5/min + composite RL становится дубликатом).
Документируется в `docs/api-rate-limits.md` (Группа 15) как контракт для
клиентов. Фронтенд-миграция — отдельный small commit в M07 либо hotfix.

**RFC 7807 через ResponseDecorator:** `RequestRateLimiterGatewayFilterFactory`
пишет только status 429 + headers, не body. `RateLimitProblemDetailsFilter`
(order=-40) оборачивает response; на commit с status=429 меняет body на
Problem Details JSON + Content-Type `application/problem+json` +
`Retry-After: 60`. Работает поверх writeWith/writeAndFlushWith (оба маршрута
сброса буфера в WebFlux).

**Per-downstream 600/min:** глобальный `/api/**` DDoS-guard из PLAN
реализован как per-downstream (academic/schedule/attendance/push — каждый
со своим RL-фильтром ipKeyResolver 600/min). Это даёт ту же защиту, но
позволяет в будущем настраивать лимиты отдельно по домену. `/api/auth/**`
намеренно без global guard — SMS/login уже имеют специфичные лимиты; auth
public endpoints (public-key, health) не DoS-sensitive.

**Результат:** 52 Gateway-теста (было 47, +5 RateLimitProblemDetailsFilter).
Build зелёный. Фактическое поведение 429+Problem Details будет провалидировано
Testcontainers-тестом в Группе 12.

## 2026-04-19 — Группа 11: LoginRateLimiter composite key (01 P0-6)

**Breaking API change:** `LoginRateLimiter` — 3 метода `(ip, login)` вместо
`(login)`. `AuthService#login(request)` → `login(request, ipAddress)`.
`AuthController` извлекает IP в новом helper'е `resolveClientIp(req)`:
приоритет `X-Forwarded-For` → `RemoteAddr` → `"unknown"`. auth-service всегда
за Gateway/nginx в prod → XFF всегда присутствует; RemoteAddr — это адрес прокси.

**Почему это важно (01 P0-6):** старый ключ `login_attempts:<login>` позволял
атакующему DoS-лочить чужой аккаунт — 20 неудач → 2 часа лока жертвы. Теперь
каждая (ip, login)-пара ведёт собственный счётчик: атакующий с одного botnet-узла
лочит только СВОЮ попытку, жертва с её IP продолжает логиниться. Distributed
brute через много IP останавливается раньше — Gateway IP-RL 5/min per IP
на `/api/auth/login` (Группа 10).

**Тесты:** 11 unit + 3 IT. Integration-тесты поднимают реальный Redis через
`AbstractIntegrationTest` (Testcontainers), проверяют Redis-ключи напрямую.
Ключевой IT — `attackerIpDoesNotBlockVictimIp`: 5 failed с IP-атакующего не
мешают жертве с другого IP залогиниться корректно.

**Результат auth-service build:** 54 теста (было 40, +14: 11 unit + 3 IT).
0 failures.

**Trade-off null IP:** если каким-то образом `X-Forwarded-For` и `RemoteAddr`
оба `null` — fallback `"unknown"`. Все запросы без IP попадают в общую корзину
`login_attempts:unknown:<login>` — это хуже чем composite, но всё же не хуже
оригинального «по login только». В prod такого не случается (Gateway ставит
XFF всегда).
