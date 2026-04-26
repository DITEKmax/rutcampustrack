# 07. API Gateway — отчёт аудита

## Сводка

API Gateway — единая точка входа для REST + WebSocket трафика к backend-сервисам. Стек: Spring Cloud Gateway 2024.0.0 на WebFlux, springdoc-openapi-starter-webflux-ui, jjwt 0.12.6 для валидации JWT. Сервис компактный — **6 Java-файлов** (`GatewayApplication`, `OpenApiConfig`, `PublicKeyConfig`, `JwtAuthenticationFilter` + 2 теста). Работает как прокси с глобальным фильтром аутентификации.

Главное достижение архитектуры: в `JwtAuthenticationFilter.filter()` строки 65-69 **вырезаются клиент-переданные внутренние заголовки** (`X-User-Id`, `X-User-Role`, `X-Group-Id`, `X-Is-Headman`) до проверки JWT. Это единственный источник правды для downstream'ов. Значит все P0 из отчётов academic/schedule/attendance/notification про `UserContextFilter` с доверием к `X-User-*` — **работают только при обращении через Gateway**. Если кто-то в `rutcampustrack_private_net` (или, хуже, с публичным доступом к порту сервиса) обратится напрямую — защита не работает. Это важнейший кросс-каттинг факт.

Проблемы: DEBUG-логирование в дефолт-конфиге; устаревший CORS-домен в `application-prod.yml` (`rutcampustrack.ru` вместо прод-домена `ruttrack.site` из CLAUDE.md); CORS `allow-credentials: true` + wildcard localhost'ов в dev-профиле (ОК для dev, но риск если `prod` не активен); `/api/ws/**` в `PUBLIC_PREFIXES` → WebSocket handshake без JWT на уровне Gateway (auth должна быть в notification-service, но мы видели, что нет `GlobalExceptionHandler` и только black-list на STOMP); `/api/auth/otp/**` public prefix охватывает `verify-by-code` → даёт брутфорс-вектор (P1-2 из 01-auth); нет rate limiting ни на один маршрут; нет IP-allowlist для actuator/openapi; при недоступности auth-service на старте gateway поднимается, но первый запрос к защищённому маршруту даёт 500 (IllegalStateException) вместо 503.

**Счётчики:** **P0 = 2**, **P1 = 9**, **P2 = 11**, **P3 = 7**.

## Структура модуля

```
services/api-gateway/
├── Dockerfile                                  ← multi-stage, JRE-alpine, non-root, без HEALTHCHECK
├── build.gradle.kts                            ← Spring Cloud 2024.0.0, jjwt 0.12.6, springdoc webflux
└── src/
    ├── main/
    │   ├── java/ru/rutcampustrack/gateway/
    │   │   ├── GatewayApplication.java         ← @SpringBootApplication + @EnableScheduling
    │   │   ├── config/
    │   │   │   ├── OpenApiConfig.java          ← бин OpenAPI (только title/description)
    │   │   │   └── PublicKeyConfig.java        ← @PostConstruct + @Scheduled(3600000ms) refresh
    │   │   └── filter/
    │   │       └── JwtAuthenticationFilter.java ← GlobalFilter; @Order(-100); strips X-User-* headers
    │   └── resources/
    │       ├── application.yml                 ← CORS, routes, DEBUG logging
    │       └── application-prod.yml            ← узкий CORS, INFO/WARN logging
    └── test/
        ├── java/.../filter/JwtAuthenticationFilterTest.java  ← 11 unit-тестов с моками
        └── java/.../config/PublicKeyConfigTest.java          ← 2 теста
```

Модулей `*-api-contract` / `*-app` нет — gateway как прослойка не имеет API-контракта (прокидывает чужие). Это ок по смыслу, но **в `settings.gradle.kts` он включён как `services:api-gateway` без парного contract-модуля**, как и auth-service. В отличие от auth-service, gateway действительно не нуждается в контракте.

---

## Критичные проблемы (P0)

### P0-1: CORS-домен в `application-prod.yml` не совпадает с фактическим прод-доменом
- **Где:** `src/main/resources/application-prod.yml:8` — `allowed-origins: https://rutcampustrack.ru`.
- **Что:** CLAUDE.md «URL Layout (v9.0)» указывает прод-домен как `https://ruttrack.site`. CORS разрешает другой origin. При деплое в прод браузер PWA/web-panel получит `Access-Control-Allow-Origin: https://rutcampustrack.ru`, а запрос идёт с `https://ruttrack.site` → браузер отклонит; fetch/axios упадут с CORS error. Либо `CORS_ALLOWED_ORIGIN` переопределяется в `.env.prod` (не проверено в этом отчёте, см. infra 13).
- **Риск:** при доверии к конфигу — весь прод-фронтенд не работает. Единственная причина, почему сейчас работает — `CORS_ALLOWED_ORIGIN` переопределён env-переменной в `/opt/rutcampustrack/.env.prod`. Это **скрытая зависимость от env**, не задокументированная в коде.
- **Как чинить:** поправить default на `https://ruttrack.site`. Или удалить default и заставить fail-fast, если env не задан (`${CORS_ALLOWED_ORIGIN}` без дефолта → Spring упадёт на старте). Добавить смоук-тест в CI, который делает OPTIONS-запрос с целевого домена и проверяет `Access-Control-Allow-Origin`.
- **Зависимости:** 13-infra-docker-ci.md — проверить реальный `.env.prod`; все фронты.

### P0-2: `/api/ws/**` и `/api/auth/otp/**` в `PUBLIC_PREFIXES` — JWT не валидируется на Gateway
- **Где:** `JwtAuthenticationFilter.java:41-47`:
  ```java
  private static final List<String> PUBLIC_PREFIXES = List.of(
      "/api/auth/otp/",
      "/api/ws/",
      "/swagger-ui/",
      "/v3/api-docs",
      "/openapi/"
  );
  ```
- **Что:**
  1. `/api/ws/**` — WebSocket handshake идёт в notification-service **без предварительной проверки JWT** на уровне Gateway. Значит validation ложится полностью на downstream. В отчёте 05 зафиксировано, что notification-service валидирует JWT через query-параметр в `/ws` handshake-interceptor'е — то есть работает. Но это означает, что **любой запрос `/api/ws/xxx` проходит Gateway и долетает до notification-service без аутентификации**. Downstream должен сам вернуть 401/403.
  2. `/api/auth/otp/**` охватывает `/api/auth/otp/request`, `/api/auth/otp/verify`, `/api/auth/otp/verify-by-code`. `verify-by-code` — брутфорс-вектор (см. 01-auth P1-2). Rate limiting на Gateway отсутствует, downstream (`auth-service`) тоже не лимитирует `verify-by-code` по IP.
- **Риск:**
  - DoS: `/api/auth/otp/verify-by-code` — незащищённый endpoint, 6-значный код, 10^6 вариантов. За минуту при 100 RPS/ядро — 6000 попыток, за час — 360k. Учитывая, что вероятность угадать 1/10^6 → mat. ожидание угадывания ~ 3 часа. Реально — защиты нет.
  - Спам: `/api/auth/otp/request` тоже без rate limiting на уровне Gateway. Защита только в `OtpService.requestOtp` (`otp_sent:telegramId` cooldown). Но по одному telegramId можно слать запрос, и бот будет рассылать OTP-сообщения, даже если request пришёл из Интернета, что уже неприятно.
- **Как чинить:**
  1. Добавить `RequestRateLimiter` filter Spring Cloud Gateway с Redis-бакетом — см. [документацию Spring Cloud Gateway](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway/request-rate-limiter.html). Установить ~ 10 req/min/IP для OTP endpoints.
  2. На Gateway валидировать JWT даже для WebSocket handshake: извлечь токен из query (`?token=...`) или из заголовка `Sec-WebSocket-Protocol` (стандартный хак), проверить, и только потом проксировать на notification-service.
- **Зависимости:** auth-service (согласование limit'ов), notification-service (упростить handshake-auth, раз gateway уже всё проверил).

---

## Серьёзные проблемы (P1)

### P1-1: `PublicKeyConfig.init()` не валит контейнер при старте, если auth-service недоступен
- **Где:** `PublicKeyConfig.java:31-34, 49-71`.
- **Что:** `@PostConstruct init()` → `fetchAndCachePublicKey()`. При ошибке — `log.error` + не throw. `publicKeyRef` остаётся `null`. Gateway стартует «здоровым» для Kubernetes/docker healthcheck. Первый запрос на защищённый маршрут → `getPublicKey()` бросает `IllegalStateException`, который **не ловится** в `JwtAuthenticationFilter` (там только `catch (JwtException | IllegalArgumentException)`). Ошибка проваливается как unhandled exception → WebFlux вернёт 500 без RFC 7807.
- **Риск:** видимая недоступность прод-фронта без понятного сообщения. Мониторинг Prometheus (`management.endpoints.web.exposure.include: health,info,prometheus`) сообщит UP, хотя сервис фактически не работает.
- **Как чинить:** (а) в `init()` при ошибке бросать исключение и падать; (б) добавить health indicator, который возвращает DOWN, пока `publicKeyRef.get() == null`; (в) в фильтре ловить `IllegalStateException` и возвращать 503 + Retry-After.
- **Зависимости:** auth-service (доступность на старте), K8s readiness probe.

### P1-2: Rate limiting полностью отсутствует на всех маршрутах
- **Где:** `application.yml:35-104` (routes), `JwtAuthenticationFilter.java`.
- **Что:** ни одна route не имеет `RequestRateLimiter` фильтра. Нет лимита на `/api/auth/login` (DoS через перебор пароля + LoginRateLimiter заблокирует аккаунт — см. 01-auth P0-6). Нет лимита на `/api/push/subscribe`. Нет лимита на admin-endpoints.
- **Как чинить:** общий рейт-лимит по IP (например 100 rps) для всех маршрутов, отдельные жёсткие лимиты для login/otp (10/min), отдельно для admin-операций (50/min). Использовать `RequestRateLimiter` + Redis (`redis-rate-limiter`).
- **Зависимости:** Redis (уже в инфре).

### P1-3: `allow-credentials: true` + широкий CORS в `application.yml` (dev)
- **Где:** `application.yml:15-32`.
- **Что:** дефолт-конфиг разрешает куки с credentials на 5 разных localhost-портов. В проде переопределяется `application-prod.yml`, но только если активирован профиль `prod`. Если контейнер стартует без `SPRING_PROFILES_ACTIVE=prod` (распространённый баг) — прод «выглядит» как dev. Исторически такие промахи — источник CSRF-уязвимостей.
- **Как чинить:** (а) fail-fast если профиль не задан в проде; (б) убрать default и требовать `CORS_ALLOWED_ORIGIN` из env.
- **Зависимости:** docker-compose.prod.yml, deployment.

### P1-4: DEBUG-логирование Spring Cloud Gateway в `application.yml`
- **Где:** `application.yml:141-144`:
  ```yaml
  logging:
    level:
      org.springframework.cloud.gateway: DEBUG
      ru.rutcampustrack: DEBUG
  ```
- **Что:** при активации prod профиля переопределяется в `application-prod.yml:34-38` на INFO/WARN — ОК. Но при misconfiguration (см. P1-3) DEBUG остаётся в проде. Gateway DEBUG логирует **полные URL с query-параметрами** — включая `?token=...` для WebSocket handshake. Токен попадает в логи.
- **Риск:** утечка JWT через логи → доступ от имени жертвы.
- **Как чинить:** (а) сделать дефолт INFO, а DEBUG — только в `application-dev.yml`; (б) на уровне Logback паттерна маскировать `token=` в URL; (в) не пропускать токен в query-string — переключиться на `Sec-WebSocket-Protocol` механизм.
- **Зависимости:** notification-service (handshake).

### P1-5: `JwtAuthenticationFilter` — catch только `JwtException | IllegalArgumentException`
- **Где:** `JwtAuthenticationFilter.java:110-113`.
- **Что:** `Jwts.parser().parseSignedClaims(token)` может бросить `WeakKeyException`, `SecurityException` — не `JwtException` (в зависимости от версии jjwt, разные ветки). При неожиданной ошибке — unhandled exception, 500. Та же проблема, что P1-1 с `IllegalStateException`.
- **Как чинить:** `catch (RuntimeException e)` и логировать stack trace на WARN.

### P1-6: Отсутствие health-check на downstream-сервисы; routes жёстко привязаны к сервисам
- **Где:** `application.yml:36-104` — `uri: http://auth-service:9090` (и т.д.).
- **Что:** если auth-service не поднят, gateway всё равно пропускает `/api/auth/login` (public route) → получает connection refused → отдаёт 500. Нет circuit breaker, нет retry.
- **Как чинить:** добавить `Retry` фильтр + `CircuitBreaker` (Resilience4j) с быстрым fail-fast 503.

### P1-7: `/api/auth/logout` требует JWT (по умолчанию), хотя часто должен принимать expired
- **Где:** `PUBLIC_PATHS` не содержит `/api/auth/logout`.
- **Что:** если токен истёк, пользователь не может нормально logout (чтобы инвалидировать refresh). Фронтенды обычно должны обращаться к `/auth/logout` даже без валидного access-токена. Сейчас запрос вернёт 401, и клиент молча «выйдет» — refresh на стороне сервера останется.
- **Как чинить:** добавить `/api/auth/logout` в PUBLIC_PATHS. Либо переделать логику так: `logout` берёт refresh из body (не access) и удаляет его — access вообще не требуется.
- **Зависимости:** auth-service `/auth/logout` уже принимает `RefreshRequest` body — то есть access-токен формально не нужен.

### P1-8: Gateway не отправляет `Content-Type: application/problem+json` для 401
- **Где:** `JwtAuthenticationFilter.unauthorized()` устанавливает content-type правильно (`MediaType.APPLICATION_PROBLEM_JSON`), но тело ответа неполное по RFC 7807: нет `type`, `instance`. Минимум: `type`, `title`, `status`, `detail`, `instance`, `timestamp`.
- **Где:** `JwtAuthenticationFilter.java:121-131` — тело только `{"status":401,"title":"Unauthorized","detail":"..."}`.
- **Как чинить:** добавить `type: "about:blank"`, `instance: request.getPath()`, `timestamp: Instant.now()`. Согласовать формат с `GlobalExceptionHandler` downstream-сервисов.

### P1-9: Нет маршрутов для `/api/schedule/ws` и специфичных WebSocket endpoints, только общий `/api/ws/**`
- **Где:** `application.yml:64-69`.
- **Что:** `/api/ws/**` идёт на notification-web. Если в будущем schedule-service захочет выставить WebSocket — нет пути. Сейчас это не блокер.
- **Как чинить:** оставить как есть, но добавить в doc намёк, что WebSocket единственный на notification-web.

---

## Средние (P2)

### P2-1: `management.endpoint.health.show-details: always` в dev-конфиге
- **Где:** `application.yml:133-134`.
- **Что:** при misconfiguration prod профиля — внутренняя структура health раскроется анонимно. В prod переопределено на `never`. ОК, если prod активируется.

### P2-2: `prometheus.access: unrestricted` — Prometheus endpoint без ACL
- **Где:** `application.yml:135-136`.
- **Что:** любой может снять метрики по `/actuator/prometheus`. В проде, если nginx не закрывает этот путь, открыта статистика внутренней работы сервиса (uptime, JVM heap, http-bindings по клиентам).
- **Как чинить:** либо настроить nginx allowlist по IP, либо Basic auth через `spring-security-actuator`.

### P2-3: `DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_UNIQUE`
- **Где:** `application.yml:33-34`.
- **Что:** хак для совместимости с downstream'ами, которые могут сами добавлять CORS-заголовки (вопрос: зачем им это при gateway?). Downstream не должны отдавать CORS. Если отдают — это источник несогласованности.
- **Как чинить:** убедиться, что downstream не отдают CORS. Если отдают — удалить их. Убрать этот dedupe.

### P2-4: Springdoc OpenAPI без авторизации
- **Где:** `application.yml:106-122` и OpenApiConfig.
- **Что:** `/swagger-ui.html`, `/v3/api-docs` публичны. Swagger выставляет описание ВСЕХ API downstream-сервисов, включая админ-операции, detailed schema, потенциально sensitive эндпоинты. В проде это раскрытие.
- **Как чинить:** (а) отключить springdoc в prod (`springdoc.swagger-ui.enabled: false`, `springdoc.api-docs.enabled: false`); либо (б) закрыть basic-auth'ом.

### P2-5: `Set-Cookie` для WebSocket handshake не учитывается
- **Где:** `application.yml` — нет `RequestHeaderToRequestUri` или `PreserveHostHeader`.
- **Что:** если когда-либо понадобятся куки для ws-аутентификации — не поддержано.

### P2-6: Нет HEALTHCHECK в Dockerfile
- **Где:** `Dockerfile:38`.
- **Что:** docker-compose не узнает, что gateway уже готов. Зависит от healthcheck в docker-compose.yml.
- **Как чинить:** `HEALTHCHECK CMD wget --spider http://localhost:8080/actuator/health || exit 1`.

### P2-7: `max-in-memory-size: 12MB` (codec)
- **Где:** `application.yml:7-8`.
- **Что:** WebFlux лимит на буфер. 12MB — явно для файлов/больших payload'ов. Подозрительно много для gateway, который должен проксировать. Может ли это быть точкой DoS — отправлять 11.99MB requests и тратить heap?
- **Как чинить:** уменьшить до 2-4MB + ввести Content-Length-Filter для отказа больших POST.

### P2-8: `auth-service-url` настройка дублируется
- **Где:** `application.yml:124-125` — `gateway.auth-service-url: ${AUTH_SERVICE_URL:http://auth-service:9090}`; routes ссылаются на `http://auth-service:9090` напрямую (строка 37).
- **Что:** два источника правды. Если ENV переопределит только `AUTH_SERVICE_URL` — route всё равно пойдёт на жёсткую строку 37.
- **Как чинить:** использовать `${AUTH_SERVICE_URL}` и в route.

### P2-9: `@EnableScheduling` у GatewayApplication — только для PublicKeyConfig
- **Где:** `GatewayApplication.java:8`.
- **Что:** только одна `@Scheduled` в PublicKeyConfig. OK, но хорошо бы комментарий-назначение.

### P2-10: `allowed-headers: "*"` в dev CORS
- **Где:** `application.yml:30`.
- **Что:** в проде правильно список `Authorization, Content-Type, X-Requested-With`, в dev — `*`. Согласованности нет.

### P2-11: Springdoc `enable-native-support: true`
- **Где:** `application.yml:107`.
- **Что:** опция для spring-native; возможно не нужна (не используется native image). Мелкая тревога.

---

## Мелкие и nit (P3)

### P3-1: Log-уровень в `JwtAuthenticationFilter.java:111` — `log.debug`
- При DEBUG выключенном информация не пишется → невозможно диагностировать «почему клиент получил 401». Использовать WARN с рандом-id (не content).

### P3-2: `PUBLIC_PATHS` содержит `/swagger-ui.html`, но `PUBLIC_PREFIXES` — `/swagger-ui/`
- Дублируется логика доступа. Можно объединить.

### P3-3: тест JwtAuthenticationFilterTest использует RSA-2048, а сервис использует RSA-3072
- `PublicKeyConfigTest.java:18`, `JwtAuthenticationFilterTest.java:34`. Тест не повторяет проду.

### P3-4: Нет теста `INTERNAL_HEADERS` strip
- Тесты не проверяют, что `X-User-Id`, отправленный клиентом, действительно вырезается. Это важнейшая безопасная функция — она не покрыта.

### P3-5: Нет теста на `OPTIONS` + `X-User-Id` (что он тоже удаляется)
- Хотя OPTIONS пропускается без валидации, заголовки должны также очищаться.

### P3-6: `record PublicKeyResponse(String publicKey, String algorithm)`
- В `PublicKeyConfig.java:83` — `record` на package-private уровне. ОК, но можно вынести в отдельный DTO.

### P3-7: `Retry.fixedDelay(3, Duration.ofSeconds(5))`
- Retry 3× с 5-сек delay = 15 сек ожидания. В `@PostConstruct` — блокирует старт контейнера на 15 сек в худшем случае. На CI это может замедлять compose up.

---

## Мёртвый код

- **`OpenApiConfig`** — бин `gatewayOpenAPI()` только задаёт title/description. Не делает agregation из `springdoc.swagger-ui.urls:`. Можно удалить, если OpenAPI aggregation работает через YAML.
- **`application.yml:124-125`** — `gateway.auth-service-url` не читается нигде (см. P2-8). Либо перенести в использование, либо удалить.

---

## Костыли и TODO/FIXME

- `JwtAuthenticationFilter.java:65` — комментарий `// CRIT-01: Strip client-supplied internal headers to prevent privilege escalation` — ссылка на несуществующий в репо phase-id `CRIT-01`. См. аналогичные маркеры в auth-service (IMP-XX, REC-XX).
- `PublicKeyConfig.java:69` — `// Do not crash on refresh failure — keep existing key cached` — комментарий про отсутствие fail-fast. См. P1-1.

Нет ни одного `TODO/FIXME/HACK` маркера в коде.

---

## Тесты

Всего 2 тестовых файла на 13 методов:

### Что покрыто хорошо
- `JwtAuthenticationFilterTest`:
  - Public routes (`/api/auth/login`, `/api/auth/otp/verify`) проходят без JWT — 2 теста
  - `Authorization` отсутствует → 401 — 1 тест
  - Authorization без `Bearer ` → 401 — 1 тест
  - Valid JWT → X-User-Id/X-User-Role инъекция — 1 тест
  - X-Group-Id → инъекция если claim есть — 1 тест
  - X-Group-Id → НЕ инъектируется если claim отсутствует — 1 тест
  - Expired JWT → 401 — 1 тест
  - Malformed JWT → 401 — 1 тест
  - OPTIONS preflight → pass — 2 теста
- `PublicKeyConfigTest`: parse PEM → корректный ключ; getPublicKey до init → IllegalStateException.

### Что покрыто плохо / не покрыто
- **Strip of `X-User-*` headers** (main security feature) — НЕ покрыто тестом (см. P3-4).
- **Invalid issuer / audience** (JWT с `iss: other`) — НЕ покрыто.
- **JWT signed by wrong key** — НЕ покрыто (отдельно от malformed).
- **Public key refresh flow** (`@Scheduled` refresh) — НЕ покрыто. Что если auth-service вернёт другой ключ? Если `PublicKeyConfig.refresh()` упал — остаётся ли старый ключ кэшированным?
- **`PublicKeyConfig.init()` retry behavior** — при connection error retry 3×5сек — не покрыто.
- **Gateway routing** (routes в application.yml корректно настроены, StripPrefix работает) — нет integration теста.
- **CORS preflight** (фактическая работа) — нет.
- **Rate limiting** — нечего тестировать, его нет.
- **Circuit breaker / Retry filter** — нет.
- **WebSocket upgrade** — нет теста, что handshake доходит до notification-web с правильными заголовками.

### Некорректные / подозрительные тесты
- `JwtAuthenticationFilterTest` использует `KeyPair.initialize(2048)`, в проде 3072 — ОК для поведенческих тестов.
- `JwtAuthenticationFilterTest.generateToken` не устанавливает `notBefore` / `iat` — токен валиден «с нуля», но jjwt требует `issuedAt`. Проверить, не падает ли из-за этого.

### Кандидаты на рефакторинг/удаление
- Тесты публичных маршрутов (`publicRoute_login_passesThroughWithoutJwt`, `publicRoute_otp_passesThroughWithoutJwt`) — дубли. Параметризовать `@ParameterizedTest` с `ValueSource(strings = {...})` над всем `PUBLIC_PATHS + PUBLIC_PREFIXES`.
- `optionsRequest_passesThroughWithoutJwt` и `optionsRequest_pushRoute_passesThroughWithoutJwt` — аналогично дубли.

---

## Соответствие CLAUDE.md

| Правило | Статус | Комментарий |
|---------|:------:|-------------|
| Contract-first (`*-api-contract` + `*-app`) | — | Gateway не имеет API, не применимо |
| Request DTO = record, Response = class | — | Нет DTO |
| Lombok запрещён в контрактах | — | Нет контракта |
| Enum lowercase в БД / UPPER_CASE в Java | — | Нет БД |
| @Enumerated(ORDINAL) запрещён | — | Нет JPA |
| Soft delete | — | Не применимо |
| RFC 7807 Problem Details | ⚠ | Возвращает минимальный problem-json (см. P1-8) |
| HATEOAS | — | Gateway не собирает hypermedia |
| `@ControllerAdvice` | — | Gateway использует кастомный filter |
| REST пути `/api/{service}/...` | ✅ | Корректно: `/api/auth/`, `/api/academic/`, `/api/schedule/`, `/api/attendance/`, `/api/ws/`, `/api/push/` |
| Именование пакетов `ru.rutcampustrack.{service}.*` | ✅ | `ru.rutcampustrack.gateway.*` |
| gRPC в `grpc/` | — | Gateway не использует gRPC (только REST/WS) |
| Event types | — | Не publish'ит |
| Flyway миграции | — | Нет БД |

Gateway — инфраструктурный слой, большинство правил неприменимо. Из применимых — `/api/{service}/...` routing корректный.

---

## Зависимости между проблемами

- **P0-1 (CORS-домен prod)** блокирует выход всех фронтендов — самый быстрый фикс.
- **P0-2 (rate limiting на OTP/ws)** решается вместе с P1-2 (общий rate limiting).
- **P1-1 (publicKey null при старте)** блокирует устойчивость deploy — без readiness probe ошибки неочевидны.
- **P1-3 (CORS в dev) + P1-4 (DEBUG)** — решаются одним фиксом fail-fast, если профиль не задан.
- **P1-8 (неполный RFC 7807)** — прямо увязан с downstream GlobalExceptionHandler'ами, стоит согласовать шаблон.
- **P2-4 (Swagger в проде)** — зависит от 13-infra-docker-ci.md (может быть закрыт на nginx).

---

## Вопросы к владельцу проекта

1. **CORS_ALLOWED_ORIGIN**: почему в `application-prod.yml` default `https://rutcampustrack.ru`, а в CLAUDE.md домен `https://ruttrack.site`? Это легаси или опечатка?
2. ✅ **Rate limiting**: где предполагается делать (gateway / nginx)? Если nginx — подтвердить; если gateway — план на реализацию.
   → **AUTO-RESOLVED через 02-Q-rate-limit (2026-04-18)**: выбран **(c) Spring Cloud Gateway + Redis** — `spring-cloud-starter-gateway` redis-rate-limiter. Лимиты per-route: OTP 1/min, login 5/min IP + 10/min login, attendance check-in 10/min user, глобально 600/min IP. См. `OWNER-ANSWERS.md` 02-Q-rate-limit.
3. **WebSocket auth**: как планируется аутентифицировать `/api/ws/**` handshake? Сейчас gateway не проверяет JWT. Работает notification-service, но это single-point-of-failure.
4. **Actuator**: какая политика доступа к `/actuator/*`? Предполагается ли `nginx allow 10.0.0.0/8 deny all` или basic-auth?
5. **OpenAPI в проде**: нужен ли `/swagger-ui/**` в проде? Если да — под basic-auth?
6. **Circuit breaker**: предполагается ли Resilience4j для downstream fault-tolerance? В текущем виде gateway возвращает 500 при любом connection refused.
7. **Fail-fast на отсутствие `SPRING_PROFILES_ACTIVE=prod`**: согласны ли сделать сервис падающим, если профиль не `prod` в прод-окружении? Это сильно снижает риск CORS misconfiguration.
8. **Retry PublicKey на старте**: 3 попытки × 5 сек. Нужно больше (например 10 раз)?
9. ✅ **Префикс `X-User-*`**: используется ли где-то downstream, помимо `UserContextFilter`? Есть ли план перевести на подписанный JWT / mTLS вместо заголовков?
   → **AUTO-RESOLVED через 02-Q2 (2026-04-18)**: выбран **Internal JWT** — Gateway генерирует короткоживущий RSA-подписанный JWT и передаёт downstream через `Authorization: Internal <jwt>`. Старые `X-User-*` уходят полностью; `JwtAuthenticationFilter.java:65-69` (strip) перестаёт быть нужен (он удалится вместе с заголовками). См. `OWNER-ANSWERS.md` 02-Q2.
10. **Нужен ли `/api/auth/logout` в PUBLIC_PATHS**, чтобы клиенты могли «разлогиниться» при истёкшем access-токене?

---

_Конец отчёта 07-api-gateway.md_
