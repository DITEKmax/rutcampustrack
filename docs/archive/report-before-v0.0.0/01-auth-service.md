# 01. Auth Service — отчёт аудита

## Сводка

Auth Service — сервис аутентификации (логин/пароль + OTP через Telegram + Telegram Mini App initData). Стек: Spring Boot 3.4, Spring Security, JPA (PostgreSQL), Redis, RabbitMQ. JWT — RS256, 3072-bit RSA ключи, jjwt 0.12.6. Общий уровень безопасности **средний-выше среднего**: реализованы правильные вещи (RS256, ротация refresh, rate-limiter на login, OTP с collision-retry, HMAC-SHA256 для TMA, constant-time compare в TMA, BCrypt). Но есть несколько **критичных дефектов** уровня архитектуры и безопасности, которые обязательно закрывать до релиза v0.0.0.

Самое болезненное:
1. Нарушен принцип contract-first: нет `auth-api-contract` — единственный сервис, который не соответствует правилу CLAUDE.md.
2. Сервис подключён к чужой БД `academic_db` и выполняет `ddl-auto: validate` по чужой схеме без Flyway в проде — поднятие сервиса зависит от того, что academic-service успел применить миграции.
3. Таблица `users` содержит колонку `initial_password VARCHAR(128)` с паролем в открытом виде.
4. OTP-код возвращается в теле HTTP-ответа `POST /auth/otp/request` — вместо того чтобы передаваться исключительно в Telegram через ноту notification-bot. Нарушает задумку «секрет в отдельном канале».
5. Timing-атака в `OtpService.verifyOtp()` — `storedCode.equals(request.code())`.
6. `LoginRateLimiter` позволяет третьей стороне блокировать любой логин (DoS через enumeration + массовый fail).

**Счётчики:** **P0 = 6** (2 ACCEPTED, 4 TO-FIX), **P1 = 10** (2 ACCEPTED, 8 TO-FIX), **P2 = 12** (1 TO-FIX, 11 AUTO-RESOLVED), **P3 = 9** (разбираются пачкой P3, см. 16-nit-backlog.md).

## Структура модуля

```
services/auth-service/
├── Dockerfile
├── build.gradle.kts                       ← нет зависимости auth-api-contract
└── src/main/java/ru/rutcampustrack/auth/
    ├── AuthApplication.java
    ├── config/
    │   ├── EnumConverters.java            ← UserRoleConverter, AccountStatusConverter
    │   ├── JwtAuthenticationFilter.java   ← проглатывает все исключения
    │   ├── JwtProperties.java
    │   ├── LowercaseEnumConverter.java
    │   ├── OtpProperties.java
    │   ├── SecurityConfig.java            ← CSRF отключён, всё stateless
    │   └── TmaProperties.java
    ├── controller/
    │   └── AuthController.java            ← контроллер без контрактного интерфейса
    ├── dto/                               ← все DTO — records, включая Response (нарушение CLAUDE.md)
    ├── entity/
    │   ├── User.java                      ← содержит `initialPassword` в plaintext
    │   └── enums/ {UserRole, AccountStatus}
    ├── event/                             ← RabbitConfig, DomainEvent, OtpVerifiedEvent, DomainEventListener
    ├── exception/                         ← GlobalExceptionHandler + custom exceptions
    ├── repository/
    │   └── UserRepository.java
    └── service/
        ├── AuthService.java
        ├── JwtService.java                ← хранит RSA ключи в ФС, kid из UUID, rotation не реализована
        ├── LoginRateLimiter.java          ← блокировка по login-имени, без учёта IP
        ├── OtpService.java                ← timing-атака на сравнение, нет лимита для verify-by-code
        └── TmaService.java                ← initData используется многократно в окне 24ч (replay)
```

Расхождения со структурой, декларированной в CLAUDE.md:
- Нет модуля `services/auth-service/auth-api-contract` (и `auth-app`). Есть одна корневая папка `services/auth-service`.
- Миграции Flyway для prod схемы отсутствуют: `src/main/resources/db/migration/` нет. В `src/test/resources/db/migration/` лежит схема, но она применима только в тестах и принадлежит academic domain (таблицы `groups`, `subjects`, `homeworks` и т.д.).

---

## Критичные проблемы (P0)

### P0-1: 🔧 TO-FIX — Отсутствует `auth-api-contract` модуль (нарушение contract-first)
**Статус (2026-04-18):** будет создан `auth-api-contract` (java-library), `AuthController implements AuthApi`. Estimate ~1 день. См. `OWNER-ANSWERS.md` 01-Q-P0-1.


- **Где:** `settings.gradle.kts:9-13` (в `include(...)` нет auth-api-contract); `services/auth-service/build.gradle.kts`.
- **Что:** CLAUDE.md требует разделения на `*-api-contract` + `*-app`. У auth единый модуль. Контроллер `AuthController` не реализует никакого интерфейса-контракта из api-модуля.
- **Риск:** клиенты (web-panel, pwa, mini-app) не могут опираться на общий контрактный jar и дублируют DTO. Любое изменение `TokenResponse`, `LoginRequest` ломается молча. OpenAPI-спецификация не гарантируется.
- **Как чинить:** создать `services/auth-service/auth-api-contract` (java-library без Spring Boot) с интерфейсом `AuthApi` и DTO (records для request, classes для response — см. P1-1); вынести туда все аннотации `@Operation`, `@ApiResponse`, `@PostMapping`. В `auth-app` — `AuthController implements AuthApi`. Добавить include в `settings.gradle.kts`.
- **Зависимости:** правка затронет web-panel/pwa/mini-app — им надо переключиться на общий контракт (возможно, через генерацию TS-типов из OpenAPI).

### P0-2: ✅ ACCEPTED — В БД хранится пароль в открытом виде (`initial_password`)
**Статус:** by design (см. `OWNER-ANSWERS.md` 01-Q1 + Meta M1, 2026-04-18). Plaintext остаётся в БД и в Telegram-чатах. Идея magic-link отложена в `docs/archive/future-ideas.md`. Ниже — оригинальное описание для исторической ссылки.

- **Где:** `src/test/resources/db/migration/V1__baseline.sql:37`; `entity/User.java:74-75`; `repository/UserRepository.java:18` (обнуление при смене).
- **Что:** при создании учётки админ, очевидно, заполняет `initial_password` plain-паролем и показывает юзеру. Колонка есть в схеме продакшена. Даже при корректном обнулении после смены пароля — между созданием и первым логином юзер и админ видят пароль, он лежит в бекапах БД, в логах Flyway seed, в репликах.
- **Риск:** утечка БД = компрометация всех "свежих" учёток. Нарушение ПДн (пароль = чувствительные данные). Нельзя получить сертификацию.
- **Как чинить:** либо (а) выдавать одноразовый токен `initial_setup_token` (хэш + expiry) и не хранить пароль вовсе; админ отдаёт токен пользователю на бумажке, пользователь ставит свой пароль через `/auth/setup-password?token=...`. Либо (б) не хранить вообще, просто генерировать и отдавать один раз в response при создании, админ сразу передаёт пользователю. Удалить колонку миграцией V{N+1}; обновить UserService в academic-service, web-panel admin.
- **Зависимости:** admin-страница в web-panel; API academic-service для создания пользователя; bot-инструкция для старосты.

### P0-3: ✅ ACCEPTED — Сервис подключён к чужой БД `academic_db` без собственной схемы и без Flyway
**Статус (2026-04-18):** by design — shared-DB между auth и academic осознанное решение при проектировании. Один разработчик, тесная связь, performance важнее изоляции, JOIN'ы возможны. Документировать в `docs/architecture/architecture.md` (NEW-1). Вариант auth-owned schema перенесён в `docs/archive/future-ideas.md` для v0.1+ (когда понадобится MFA/lockout/login-аналитика). См. `OWNER-ANSWERS.md` 01-Q-P0-3.


- **Где:** `src/main/resources/application.yml:21` (`jdbc:postgresql://postgres-academic:5432/academic_db`); `application.yml:35-36` (`flyway.enabled: false`); в `src/main/resources/db/migration/` пусто.
- **Что:** auth-service — это «читающий» соавтор схемы, но не владелец. Его зависимость от миграций academic-service делает порядок запуска хрупким. `ddl-auto: validate` при пустой `public.users` вызовет падение. Нарушается изоляция сервисов по БД.
- **Риск:** развёртывание в проде зависит от порядка старта контейнеров; невозможно поднять auth без academic; изменение схемы academic'а сломает auth без автоматического detection.
- **Как чинить:** (а) выделить auth'у свою БД `auth_db` с таблицей `users` (логически достаточно: login/password/role/telegram_id/name) — тогда academic запрашивает auth по gRPC за профилем. Либо (б) если оставляем единую БД — сделать auth владельцем `users` и миграций, а academic — читателем через view. Второй вариант требует переноса миграций и пересмотра owner. Оптимально — вариант (а).
- **Зависимости:** academic-service (его UserRepository), тесты обоих сервисов, docker-compose, backup policy.

### P0-4: 🔧 TO-FIX через otp.requested event — OTP-код возвращается в теле HTTP-ответа
**Статус (2026-04-18):** будет закрыто фиксом — auth публикует `otp.requested {telegram_id, code, ttl_seconds}` в RabbitMQ, бот читает и шлёт в Telegram. HTTP-ответ — `204` или `{"delivery": "telegram"}`. Estimate ~1-2 дня. Зависит от 08 P0-2 (нужна схема события). См. `OWNER-ANSWERS.md` 01-Q-P0-4.


- **Где:** `controller/AuthController.java:74-77`; `service/OtpService.java:49-107` (`return code`).
- **Что:** `POST /auth/otp/request` → `{ "code": "123456" }`. Предполагается, что код дальше пересылается в notification-bot. Но в текущей архитектуре auth-service публикует событие в RabbitMQ только **после verify** (`OtpVerifiedEvent`), а сам код уже отдан HTTP-клиенту (PWA/miniapp). Значит: любой, кто делает запрос с чужим `telegramId`, получает OTP в ответе HTTP — и может его использовать на `/auth/otp/verify-by-code`.
- **Риск:** полноценный компромисс. Атакующий перебирает `telegramId` (это просто публичный Telegram ID), получает OTP в теле ответа, выполняет `verify-by-code` — и входит за жертву. Единственная защита — rate-limit в 60 секунд на `otp_sent:telegramId`, но он обходится подменой `telegramId`.
- **Как чинить:** убрать `code` из тела ответа. Auth-service должен (1) записать код в Redis, (2) опубликовать событие `otp.requested {telegram_id, code}` в RabbitMQ, (3) notification-bot читает событие и шлёт код в Telegram. Тело HTTP-ответа — `204 No Content` либо `{ "delivery": "telegram" }`. Сам код не покидает сервис через HTTP никогда.
- **Зависимости:** notification-bot (новый слушатель `otp.requested`); PWA/miniapp/web-panel login-компоненты (перестают читать `code`).

### P0-5: 🔧 TO-FIX через MessageDigest.isEqual — Timing-атака на сравнение OTP-кода
**Статус (2026-04-18):** будет закрыто 1-строчным фиксом — заменить `String.equals` на `MessageDigest.isEqual(storedCode.getBytes(UTF_8), request.code().getBytes(UTF_8))`. Estimate ~5 минут. См. `OWNER-ANSWERS.md` 01-Q-P0-5.


- **Где:** `service/OtpService.java:124` — `if (!storedCode.equals(request.code()))`.
- **Что:** `String.equals` прерывается на первом несовпадающем байте. На достаточно мощной сети атакующий может измерить время ответа и восстановить код символ за символом (6 * 10 = 60 вариантов вместо 10^6).
- **Риск:** теоретический, но для security audit — обязательное замечание. Особенно учитывая П0-4 — если исправить П0-4, П0-5 становится реальной дырой.
- **Как чинить:** `java.security.MessageDigest.isEqual(stored.getBytes(UTF_8), received.getBytes(UTF_8))`. Или привести к HMAC: хранить `HMAC(secret, code)` вместо самого кода, сравнивать digests.
- **Зависимости:** ничто.

### P0-6: 🔧 TO-FIX через Gateway+Redis rate-limit — `LoginRateLimiter` позволяет блокировать чужие аккаунты (DoS)
**Статус (2026-04-18):** будет закрыто фиксом из C0-4 (Spring Cloud Gateway + Redis rate-limiter). См. `OWNER-ANSWERS.md` 02-Q-rate-limit. Конкретно: `/auth/login` — 5 req/min per IP **И** 10 req/min per login. Per-IP лимит делает невозможной DoS-атаку на конкретный логин (атакующий быстро упрётся в свой IP-лимит). Внутренний `LoginRateLimiter` (по логину) можно оставить как defense-in-depth или удалить. Ниже — оригинальное описание.


- **Где:** `service/LoginRateLimiter.java:48-67`; ключи `login_attempts:<login>`, `login_blocked:<login>` — по логину, без IP.
- **Что:** любая анонимная строна может 20 раз постучать `POST /auth/login` с `{"login":"admin","password":"x"}` и заблокировать админа на 2 часа. При этом админ не знает, что его заблокировали, пока не попытается войти. В случае `admin` — блокировка равна отключению поддержки на 2 часа.
- **Риск:** targeted DoS на конкретных пользователей. Особенно опасно для `admin` и старост, которые несут бизнес-критичные функции (открытие пары).
- **Как чинить:** считать попытки по `(ip, login)` и блокировать по `ip`, а не по `login`. Либо блокировать по IP и требовать CAPTCHA после N неудач. Обязательно логировать попытки для последующего анализа. Также в `recordFailure` при `count >= 10` после блокировки не сбрасывается `attemptsKey`, счётчик продолжает расти и немедленно триггерит `>=20` — неявный баг, но мелочь.
- **Зависимости:** api-gateway (прокидывать `X-Forwarded-For`); потенциально CAPTCHA-модуль (вне scope v0.0.0, но заложить архитектурно).

---

## Серьёзные проблемы (P1)

### P1-1: ✅ AUTO-RESOLVED через 01 P0-1 + P3 HATEOAS — Response DTO — records, а должны быть classes (HATEOAS)
**Статус (2026-04-19):** CLAUDE.md contract-first уже предписывает Response=class. При создании `auth-api-contract` (01 P0-1) DTO правятся. HATEOAS-недочёты сгруппированы в P3 тема F (16-nit-backlog), закрываются P3-пачкой.

- **Где:** `dto/TokenResponse.java`, `dto/PublicKeyResponse.java`, `dto/OtpCodeResponse.java`, `dto/ErrorResponse.java`.
- **Что:** CLAUDE.md: «Request DTO = record. Response DTO = класс (для HATEOAS RepresentationModel)». Все auth'овские response — records. HATEOAS нигде не применён (нет `_links`, нет `EntityModel`).
- **Как чинить:** переписать на классы, наследующие `RepresentationModel<T>`, добавить `_links` (минимум `self`, `refresh`, `logout`).
- **Зависимости:** клиенты-потребители должны толерировать дополнительные поля (JSON расширение безопасно).

### P1-2: 🔧 TO-FIX через C0-4 Gateway+Redis rate-limit — `verify-by-code` не ограничен по числу попыток
**Статус (2026-04-19):** закрыто per-IP лимитом `/auth/otp/verify-by-code` 5 req/min + per-telegram_id guard (07 P0-2 (c)). См. `OWNER-ANSWERS.md` 02-Q-rate-limit и 07-Q-P0-2.

- **Где:** `service/OtpService.java:143-161`.
- **Что:** `verifyOtp(byCode)` не считает попытки. С `otp:*` ключом живут 120s — но за это время можно перебрать 10^6 кодов, если в Redis живёт хотя бы один OTP. `verifyOtp` в отличие учитывает `otp_verify_attempts`.
- **Риск:** 6-значный код не является криптографическим, 10^6 вариантов при слабом rate-limit Redis — реально пробрутить за минуты.
- **Как чинить:** ввести глобальный rate-limit по IP на `/auth/otp/verify-by-code`. Например, 10 попыток/минута/IP. Или требовать валидный `telegramId` (отказаться от этого endpoint — через web-panel всё равно пользователь сначала в Telegram получает код, зная свой id).
- **Зависимости:** api-gateway (IP в header), клиенты.

### P1-3: 🔧 TO-FIX через QE5 + P2-6/2 — `JwtAuthenticationFilter` молча проглатывает все исключения
**Статус (2026-04-19):** закрыто — WARN + `AuthFailureReason` enum (EXPIRED/INVALID_SIGNATURE/MALFORMED/MISSING/REVOKED/UNSUPPORTED) + ip + path, Loki brute-force alert через Alertmanager (P2-9/5). Связка: P2-3/8 (empty catch audit + Checkstyle EmptyCatchBlock). См. `OWNER-ANSWERS.md` QE5, P2-6/2, P2-3/8.

- **Где:** `config/JwtAuthenticationFilter.java:49-51` — `catch (Exception ignored) {}`.
- **Что:** нет различия между expired / invalid-signature / malformed. Логов нет. Отладка невозможна.
- **Как чинить:** поймать `ExpiredJwtException` отдельно (логировать на DEBUG), `SignatureException`/`MalformedJwtException` — на WARN, прочие — на WARN+stacktrace. Также: при expired можно явно вернуть `401 + WWW-Authenticate: Bearer error="invalid_token"`.
- **Зависимости:** ничто.

### P1-4: 🔧 TO-FIX через C0-7 cookie-based refresh — `refresh()` не проверяет, что refresh-токен не истёк
**Статус (2026-04-19):** закрыто в рамках C0-7 (HttpOnly cookie refresh flow, single-use rotation, Redis sessionId-таблица). См. `OWNER-ANSWERS.md` 02-Q-frontend-security (Часть А). Устраняет дублирование истины JWT exp + Redis TTL.

- **Где:** `service/AuthService.java:83-115`; `JwtService.parseToken` бросает `ExpiredJwtException`, но `AuthService.refresh` оборачивает любой Exception в generic `TokenRefreshException`. ОК — всё же expired словится. Но Redis TTL = `refreshTokenExpiration` (7d) совпадает с JWT exp, и если когда-то поменять только конфиг JWT exp — Redis останется с неверным TTL.
- **Что:** фактически поведение корректное, но хрупкое из-за дублирования истины в двух местах (JWT exp + Redis TTL). Плюс — нет связки с конкретной сессией (один юзер может иметь N refresh'ей, нет способа увидеть или принудительно убить отдельную).
- **Как чинить:** сделать Redis-ключ таблицей `session_id → {user_id, created_at, last_used, device_info}` и возвращать `sessionId` в ответе `/auth/login`. Ввести `/auth/sessions` (list) и `/auth/sessions/{id}` (revoke).
- **Зависимости:** UI сессий; изменение refresh flow.

### P1-5: 🔧 TO-FIX через SCAN + NEW-45 — `changePassword` использует `KEYS` на продакшене
**Статус (2026-04-19):** закрыто audit-ом Redis-ключей (`docs/redis-keyspace.md`, NEW-45) — заменить `KEYS` на `SCAN` либо на `SADD sessions:{userId} jti` + `SMEMBERS/DEL`. См. `OWNER-ANSWERS.md` NEW-45 и 07-Q-P0-2.

- **Где:** `service/AuthService.java:139` — `redisTemplate.keys("refresh:" + userId + ":*")`.
- **Что:** `KEYS` блокирует Redis; на ноде с тысячами ключей сервер лагает. Canonical рекомендация: `SCAN`.
- **Как чинить:** `redisTemplate.execute((RedisCallback<Void>) con -> { ScanOptions opts = ScanOptions.scanOptions().match("refresh:" + userId + ":*").count(200).build(); try (var cur = con.scan(opts)) { cur.forEachRemaining(k -> con.del(k)); } return null; });`. Или хранить `SADD sessions:{userId} jti`, `SREM` при logout, чтобы `changePassword` делал `SMEMBERS sessions:{userId}` → delete.
- **Зависимости:** ничто.

### P1-6: 🔧 TO-FIX через P2-8/8 + P2-1/6 — TMA initData допускает replay в окне 24 часа
**Статус (2026-04-19):** закрыто — (a) Redis-хэш initData с TTL (single-use) + (b) reject duplicate keys в `parseQueryString` (P2-1/6) + security contract-test `TmaHmacValidationIT` (P2-8/8). Max-age сокращается до 5-10 мин. См. `OWNER-ANSWERS.md` P2-8/8, P2-1/6.

- **Где:** `service/TmaService.java:60-64`; `application.yml:58` — `auth-date-max-age-seconds: 86400`.
- **Что:** после первой успешной TMA-аутентификации тот же `initData` можно использовать повторно в течение 24 часов. Утечка `initData` (XSS, превью ссылки, скрин на Mac-share) = возможность войти от имени юзера.
- **Как чинить:** (а) хранить в Redis хэш initData с TTL = max-age и при повторе — 401. (б) сократить max-age до 5-10 минут для первого входа, а дальше жить на refresh-токене.
- **Зависимости:** mini-app (флоу логина).

### P1-7: ✅ ACCEPTED — `changePassword` не требует MFA/OTP подтверждения и не проверяет историю паролей
**Статус (2026-04-19):** ACCEPTED by owner (связано с 01-Q1 + M1 tradeoff). MFA и history policy — отдельная фича v0.1+ (в `docs/archive/future-ideas.md` раздел «Безопасность»). См. `OWNER-ANSWERS.md` 01-Q1 audit trail.

- **Где:** `service/AuthService.java:127-143`; `controller/AuthController.java:118-124`.
- **Что:** любой с валидным access-токеном может сменить пароль, зная текущий. Нет (а) second factor confirmation, (б) проверки, что новый пароль ≠ последним N, (в) cooldown'а между сменами.
- **Риск:** XSS → access-токен → замена пароля → блокировка настоящего владельца.
- **Как чинить:** минимум — требовать свежий login ≤ 5 минут назад (`auth_time` claim), логировать все смены пароля, отправлять уведомление в Telegram о смене.
- **Зависимости:** notification events, UI.

### P1-8: ✅ ACCEPTED — Отсутствует flow восстановления пароля
**Статус (2026-04-19):** ACCEPTED by owner (связано с 01-Q1 + M1). Password recovery — отдельная фича v0.1+. Таблица `password_reset_tokens` остаётся зарезервированной (см. P0-2 accepted plaintext + admin reset через web-panel). См. `OWNER-ANSWERS.md` 01-Q1.

- **Где:** схема БД имеет `password_reset_tokens` (`test/db/migration/V1__baseline.sql:62-70`), но нет ни Repository, ни Service, ни endpoint. Таблица мёртвая.
- **Что:** пользователь, забывший пароль и без привязки к Telegram, не может восстановить доступ. Админ вынужден ручной reset + plaintext пароль.
- **Как чинить:** либо удалить таблицу (если действительно не нужна), либо реализовать: `POST /auth/password-reset/request { email|phone }` → одноразовый токен → `POST /auth/password-reset/confirm { token, newPassword }`. Токен хранить как SHA-256, expiry 30 минут, single-use.
- **Зависимости:** notification (отправка ссылки), email/sms-канал (пока отсутствует).

### P1-9: 🔧 TO-FIX через C0-7 + NEW-16 — Endpoint `/auth/refresh-body` дублирует `/auth/refresh` с тем же контрактом
**Статус (2026-04-19):** закрыто — после C0-7 cookie-based flow `/auth/refresh` меняет поведение (тело→cookie), `/auth/refresh-body` удаляется (NEW-16). Breaking change без двойных endpoint'ов. См. `OWNER-ANSWERS.md` 02-Q-frontend-security, NEW-16.

- **Где:** `controller/AuthController.java:50-53` и `:110-113`; обе вызывают `authService.refresh(request)` с тем же `RefreshRequest`.
- **Что:** две POST-точки с body RefreshRequest, возвращающие TokenResponse. Swagger description говорит «for Mini App clients that cannot use httpOnly cookies», но `/auth/refresh` уже принимает тот же JSON — различия нет.
- **Как чинить:** если планируется cookie-based refresh — реализовать его (отдельный endpoint, читающий `Cookie: refresh_token`). Сейчас endpoint — дубль. Либо удалить `/auth/refresh-body`, либо реализовать полноценный cookie-based `/auth/refresh`.
- **Зависимости:** клиенты — проверить, какие используют `/refresh-body` и перевести.

### P1-10: 🔧 TO-FIX через P2-1/3 — `@EnableConfigurationProperties` на JwtProperties/OtpProperties/TmaProperties — но Spring не находит `EnumConverters`
**Статус (2026-04-19):** закрыто в рамках P2-1/3 (String→typed enum + активация converter-ов) — выносим converter-ы в top-level классы, подтверждаем autoApply в JPA packageScan. См. `OWNER-ANSWERS.md` P2-1/3.

- **Где:** `config/EnumConverters.java` — `@Converter` на **вложенных** статических классах. Hibernate требует, чтобы converter-ы были зарегистрированы. `@Converter(autoApply = true)` требует scan. Inner static classes сканятся, но только если enclosing class — не abstract, без `@Configuration`. Здесь `EnumConverters` — обычный public class, не Spring-бин. Вопрос: подхватывает ли JPA такие классы.
- **Что:** нужно проверить фактом запуска теста. Если конверторы не подхвачены, Hibernate попытается использовать ORDINAL (по умолчанию для enum) против PG ENUM колонки `user_role` — будет ошибка. Тесты проходят = значит как-то работает. Возможно работает именно благодаря autoApply + scan classpath.
- **Как чинить:** вынести converter-ы в отдельные top-level классы `UserRoleConverter.java`, `AccountStatusConverter.java` — так надёжнее. Плюс документировать, что `@Converter(autoApply=true)` должен быть в packageScan JPA.
- **Зависимости:** academic-service, schedule-service — проверить, как конверторы реализованы у них.

---

## Средние (P2)

### P2-1: 🔧 TO-FIX через QA1 + P2-6/1 — DEBUG-логирование в проде
**Статус (2026-04-19):** QA1 (a) INFO-дефолт + NEW-57 CI-check + P2-6/1 MaskingConverter (whitelist безопасных kv-полей). См. `OWNER-ANSWERS.md` QA1, P2-6/1.

- **Где:** `application.yml:61-62` — `logging.level.ru.rutcampustrack: DEBUG`. Не переопределено в `application-prod.yml`.
- **Что:** на проде в логи летят DEBUG-строки от `JwtService` («RSA key pair ready (kid=…), public key cached in Redis»), `DomainEventListener` («Published event: …»). Объём логов + возможные чувствительные значения.
- **Как чинить:** переопределить в `application-prod.yml`: `logging.level.ru.rutcampustrack: INFO`.

### P2-2: ✅ AUTO-RESOLVED через P2-6/1 — Логи могут содержать JTI/UserId при WARN уровне
**Статус (2026-04-19):** закрыто P2-6/1 (c) hybrid — ручной audit `event.toString()` + Logback MaskingConverter в shared-logback (NEW-68, regex Bearer/telegram_id/FCM). См. `OWNER-ANSWERS.md` P2-6/1.

- **Где:** `event/DomainEventListener.java:37-41`.
- **Что:** event.toString() в логах при ошибке AMQP может раскрыть payload с telegramId. Не критично, но стоит сделать structured logging без полного event toString.

### P2-3: ✅ AUTO-RESOLVED через P2-4/2 — Нет валидации формата OTP-кода на endpoint `verify`
**Статус (2026-04-19):** закрыто P2-4/2 (a) — `@Pattern("^\\d{6}$")` на `OtpVerifyByCodeRequest.code` + полный audit format-patterns. См. `OWNER-ANSWERS.md` P2-4/2.

- **Где:** `dto/OtpVerifyRequest.java` (и `OtpVerifyByCodeRequest` хоть и есть `@Size(6,6)` — нет `@Pattern("^\\d{6}$")`).
- **Что:** `"abcdef"` пройдёт `@Size(6,6)` и попадёт в сервис. Там не сломается, но без нужды делает запрос к Redis.
- **Как чинить:** `@Pattern(regexp = "^\\d{6}$", message = "code must be 6 digits")`.

### P2-4: ✅ AUTO-RESOLVED через NEW-45 — `otp` Redis-ключи не имеют namespace
**Статус (2026-04-19):** закрыто — `docs/redis-keyspace.md` (NEW-45) вводит префиксы `auth:otp:*`, `auth:refresh:*`, `auth:rl:*`. Один источник правды для keyspace всех сервисов. См. `OWNER-ANSWERS.md` NEW-45.

- **Где:** `service/OtpService.java` — ключи `otp:<id>`, `otp_code:<code>`, `otp_attempts:<id>`, `otp_sent:<id>`, `otp_verify_attempts:<id>`; `service/AuthService.java:76` — `refresh:<id>:<jti>`; `service/LoginRateLimiter.java` — `login_attempts:<login>`, `login_blocked:<login>`.
- **Что:** Redis общий для всех сервисов (и для notification-bot reminder storage). Потенциальная коллизия ключей.
- **Как чинить:** единый префикс `auth:` — `auth:otp:<id>`, `auth:refresh:<id>:<jti>` и т.д. Задокументировать в `docs/architecture/architecture.md`.

### P2-5: ✅ AUTO-RESOLVED через P2-1/4 — `generateAccessToken` включает `group_id: null` в JWT для admin/teacher
**Статус (2026-04-19):** закрыто P2-1/4 (a) — `@JsonInclude(NON_NULL)` на JWT claims DTO; QC2 openapi-typescript → `group_id?: number` (optional). См. `OWNER-ANSWERS.md` P2-1/4.

- **Где:** `service/JwtService.java:95` — `.claim("group_id", user.getGroupId())` даже если `null`.
- **Что:** формально не баг, но раздувает JWT и снижает читабельность. Также `is_headman: false` включается всегда.
- **Как чинить:** для не-студентов не включать `group_id`/`is_headman` (условно). Или документировать, что null — допустимо.

### P2-6: ✅ AUTO-RESOLVED через P2-1/5 — `@JsonIgnoreProperties({"source", "timestamp"})` на `DomainEvent`
**Статус (2026-04-19):** закрыто P2-1/5 (a+b) — удалить `@JsonIgnoreProperties`, ввести `shared-events` DomainEvent base (NEW-60, поля `event_version`/`trace_id`/`occurred_at`/`source`), Jackson `FAIL_ON_UNKNOWN_PROPERTIES=false` globally. См. `OWNER-ANSWERS.md` P2-1/5, NEW-60.

- **Где:** `event/DomainEvent.java:18`.
- **Что:** поля `source`/`timestamp` Spring'овского `ApplicationEvent` не нужны в сериализации — ОК. Но при десериализации на receiver'е (bot) это игнор не сработает (Python aiogram). Нужно убедиться, что payload-схема документирована в `event-schemas/`.

### P2-7: ✅ AUTO-RESOLVED через P2-1/6 — `parseQueryString` в `TmaService` не проверяет уникальность ключей
**Статус (2026-04-19):** закрыто P2-1/6 (a) — reject duplicate keys в parseQueryString, defense-in-depth поверх HMAC. Security contract-test (P2-8/8). См. `OWNER-ANSWERS.md` P2-1/6, P2-8/8.

- **Где:** `TmaService.java:116-128`.
- **Что:** если `initData` содержит дважды `user=...`, второй перезапишет первый. Телеграм не шлёт дубликаты, но атакующий — может (для уклонения от HMAC). HMAC защищает от этого, если дубликат включается в data-check-string. Но `parseQueryString.remove("hash")` удаляет ВСЕ hash, а не один — если атакующий добавит вторую подпись, может сломать порядок sort. Проверить.
- **Как чинить:** при парсинге detect duplicate key → throw.

### P2-8: ✅ AUTO-RESOLVED через P2-1/7 — `LowercaseEnumConverter.convertToEntityAttribute` не обрабатывает неизвестное значение
**Статус (2026-04-19):** закрыто P2-1/7 (a) — graceful log.warn + Prometheus counter `unknown_enum_total{enum_class,value}` + return null. Consistent с Jackson `READ_UNKNOWN_ENUM_VALUES_AS_NULL` (P2-4/8). Alertmanager rule (P2-9/5) «counter > 0 per deploy». См. `OWNER-ANSWERS.md` P2-1/7, P2-4/8.

- **Где:** `config/LowercaseEnumConverter.java:33`.
- **Что:** `Enum.valueOf` бросит `IllegalArgumentException` если в БД новое/некорректное значение. Нет fallback/логгирования. У админа нет способа отследить, в какой колонке мусор.
- **Как чинить:** try/catch → возвращать null или специальный UNKNOWN, логировать warning.

### P2-9: ✅ AUTO-RESOLVED через P2-12/5 — `JwtService.init` публикует public key в Redis, но не обновляет при ротации
**Статус (2026-04-19):** закрыто P2-12/5 (a) — `@Scheduled(cron="0 */5 * * * *")` + `@SchedulerLock` (NEW-28) публикует current public key с TTL 10 мин; downstream 1-мин local cache. Propagation ≤ 6 мин. NEW-155 (secret-rotation) +section JWT rotation. См. `OWNER-ANSWERS.md` P2-12/5.

- **Где:** `service/JwtService.java:81`.
- **Что:** ключ кэшируется с TTL=1ч. Если сервис перезапустится с новым ключом (ключи отсутствуют в ФС, сгенерированы заново), клиенты в Redis продолжат видеть старый public key в течение часа. API-Gateway валидирует JWT через этот Redis-ключ? Проверить (в отчёте по api-gateway).
- **Как чинить:** при `init()` удалить ключ Redis перед write; не использовать TTL (либо infinite с принудительным инвалидированием); ввести `kid` в токен и хранить мапу `kid → public_key`.

### P2-10: `TokenResponse.expiresIn` — секунды, но тип `long` (без документации единицы)
- **Где:** `dto/TokenResponse.java:6`.
- **Что:** клиенты могут спутать с ms. Нет comment/javadoc.
- **Как чинить:** переименовать в `expiresInSeconds` или добавить javadoc + Swagger `@Schema(description="seconds")`.

### P2-11: `changePassword` возвращает `200 OK` вместо `204 No Content`
- **Где:** `controller/AuthController.java:123`.
- **Что:** контракт: изменяющие операции без тела обычно `204`. `logout` возвращает `204`, `changePassword` — `200`. Непоследовательно.
- **Как чинить:** `ResponseEntity.noContent().build()`.

### P2-12: `TMA_BOT_TOKEN` обязателен без default — сервис падает без env
- **Где:** `application.yml:57` — `bot-token: ${TMA_BOT_TOKEN}`.
- **Что:** отсутствие dev-defaults. В локальной разработке без bot token сервис не поднимается.
- **Как чинить:** `${TMA_BOT_TOKEN:}` + при пустом — отключить `TmaService` (не регистрировать бин), либо использовать `NoOpTmaService`, который всегда возвращает 503.

---

## Мелкие и nit (P3)

### P3-1: JWT `kid` из UUID substring(0,8)
- **Где:** `JwtService.java:76`.
- **Что:** достаточно для текущего сценария (один ключ), но для ротации надо хранить мапу `kid → key`. Текущая логика при повторном запуске с отсутствующим `kid.txt` сгенерирует новый kid и затрёт валидные токены.
- **Как чинить:** описать в RUNBOOK, что `kid.txt` должен persist'иться; либо хранить в Redis с TTL бесконечность; либо использовать определённый формат `v1`, `v2`.

### P3-2: Тестовые логины `student`/`teacher`/`admin` с паролем `password`
- **Где:** `test/resources/db/migration/V2__seed_test_data.sql:16-20`.
- **Что:** CLAUDE.md упоминает их как «тестовые» — но никаких guards в проде. Если prod Flyway включится и выполнит этот файл (например, копирование по недосмотру) — в проде появятся 3 плохо-запароленных admin/teacher/student.
- **Как чинить:** вынести в отдельный профиль/каталог миграций, применять только `test`-профилем. Либо — оставить, но ДОБАВИТЬ assertion в CI, что prod-schema не содержит таких логинов.

### P3-3: Многословные комментарии-ссылки на несуществующие phase-id (IMP-02, IMP-03, IMP-08, IMP-10, REC-04)
- **Где:** `service/LoginRateLimiter.java:10`; `service/OtpService.java:125`; `service/JwtService.java:60,72,144`; `service/AuthService.java:45,138`.
- **Что:** комментарии вида `IMP-02` видимо ссылаются на требования фазы (Security Audit). Но требования не содержатся в коде/репо и недоступны.
- **Как чинить:** оставить ссылку на документ в `docs/` где эти IMP описаны; либо убрать маркер, если документ утерян.

### P3-4: `JwtProperties` использует `record` с методом `accessTokenExpiration()` возвращающим `long` (секунды)
- **Где:** не открыт в этом ревью, но судя по `application.yml:46-47` — ok. Nit: `long`-секунды vs `Duration` — `Duration` идеоматичнее.

### P3-5: `AuthService` держит ссылку на `JwtProperties` только ради `refreshTokenExpiration()`
- **Где:** `service/AuthService.java:27,77,111`. Можно обернуть в `SessionService`.

### P3-6: `publishEvent` для OTP использует Spring `ApplicationEventPublisher` вместо прямой публикации в RabbitMQ
- **Где:** `service/OtpService.java:182`.
- **Что:** двухэтапная публикация (spring event → AMQP listener). Добавляет latency и точку отказа. Можно писать в AMQP напрямую.
- **Как чинить:** оставить (плюс — inter-service изоляция), либо упростить.

### P3-7: `@JsonIgnore @Transient getDisplayName()` возвращает строку с двойным пробелом, если middleName blank
- **Где:** `entity/User.java:41-47`.
- **Что:** `lastName + ' ' + firstName`, если middleName пустой — ок. Но trailing space возможен при некорректных входных.

### P3-8: Hardcoded RUT MIIT координаты в seed
- **Где:** `V2__seed_test_data.sql:28`.
- **Что:** только тестовые данные, не критично. Но всё же не место в auth-service (это campus_settings academic'а).

### P3-9: Нет `@PreAuthorize("hasRole('...')")` на endpoints
- **Где:** `AuthController.java`.
- **Что:** access control на /change-password — любой аутентифицированный. ОК для этого endpoint'а, но нигде не применяется `@PreAuthorize`, что несколько нарушает явность.

---

## Мёртвый код

- **Таблица `password_reset_tokens`** (`test/db/migration/V1__baseline.sql:62-70`) — ни Repository, ни Service, ни endpoint. Либо реализовать P1-8, либо удалить.
- **Таблица `student_group_history`** (`test/db/migration/V1__baseline.sql:50-58`) — отсутствует JPA-entity и Repository в auth-service (возможно, используется в academic-service — проверить в следующем отчёте).
- **Field `avatar_id`** в таблице `users` (строка 39 V1) — нет соответствующего Java-поля в `User.java`. Либо добавить, либо убрать колонку.
- **Entity `User.employeeNumber`** — есть в entity, но нигде не используется в логике auth (только в seed). Возможно используется в academic-service.
- **Класс `EnumConverters`** как «public class» без instance — пакетно норм, но можно сделать `abstract`.

---

## Костыли и TODO/FIXME

- `service/JwtService.java:151` — `// Windows doesn't support POSIX permissions — skip silently` — не TODO, но показывает, что на Windows dev-машине приватный ключ лежит с дефолтными правами. В проде-контейнере — не проблема, но стоит добавить Windows-вариант через `AclFileAttributeView`.
- `config/JwtAuthenticationFilter.java:50` — `catch (Exception ignored) { /* let Spring Security handle */ }` — см. P1-3.
- `service/AuthService.java:123` — `// Idempotent logout — silently ignore unparseable tokens` — ок по смыслу, но без логирования (на какое событие опираться, если юзер жалуется, что logout не сработал?).
- Нет ни одного TODO/FIXME комментария — что хорошо.

---

## Тесты

Все 26 тестов — **интеграционные** на Testcontainers (PostgreSQL + Redis). Unit'ов нет.

### Что покрыто хорошо
- Login happy-path для всех трёх ролей (admin/teacher/student) — `AuthIntegrationTest.java:18-54`.
- Refresh rotation: токен используется один раз — `:79-120`.
- Logout invalidates refresh — `:122-149`.
- Public key endpoint — `:151-160`.
- OTP happy path (request + verify + verify-by-code) — `OtpIntegrationTest.java:44-112`.
- Change password happy/unhappy — `:134-184`.
- TMA auth — `TmaIntegrationTest.java` (не открывал, но контент, судя по названиям, покрывает корректный HMAC).
- Actuator — `ActuatorIT.java`.

### Что покрыто плохо / не покрыто
- **Rate limiting login** — нет теста, что после 5/10/20 неудач блокируется (P0-6). Критично.
- **Rate limiting OTP verify-by-code** — нет теста на брутфорс (P1-2).
- **Timing на OTP/login** — нет, и не нужен в интеграции, но unit-тест с мок-временем был бы уместен.
- **Истёкший access-token** — нет теста `JwtAuthenticationFilter` с expired-JWT.
- **Истёкший refresh** — нет теста с `refresh` после 7 дней. (Можно сделать через установку прошлого `now` или override jwtProperties).
- **Конкурентный refresh** — нет теста, что два параллельных запроса с одним refresh — один проходит, другой 401.
- **TMA replay (P1-6)** — нет теста, что один initData используется многократно.
- **OTP collision retry** — нет теста, что при занятом коде происходит retry.
- **changePassword revokes all sessions** — есть только проверка, что новый пароль работает, но нет проверки, что старый refresh аннулирован.
- **SecurityConfig permit/auth** — нет теста, что `/auth/change-password` без Bearer возвращает 401.
- **OtpRequest отправляет событие в RabbitMQ** — не проверяется.
- **EnumConverter edge cases** (P2-8) — unit не покрывает.

### Некорректные / подозрительные тесты
- `AuthIntegrationTest.login_withSeedStudent_returnsTokenPair` ожидает `expiresIn == 900L`. Хардкод 15 мин — если в конфиге изменить, тест упадёт. Ок, защита от накрутки, но без комментария.
- `OtpIntegrationTest.otpRequest_withValidTelegramId_returns200WithCode` по факту тестирует P0-4: что код приходит в теле ответа. После фикса P0-4 этот тест должен измениться.
- `OtpIntegrationTest.@BeforeEach cleanOtpRedisKeys` использует `KEYS otp:*` — уязвимо к медленному cleanup между тестами, и не изолирует тесты по telegramId. При параллельном запуске это сломается. Хорошо, что Testcontainers обычно запускается sequential.
- `AbstractIntegrationTest` использует `withDatabaseName("academic_db")` — то же имя БД, что в проде — ок; но значит тест **не изолирован** по БД, если кто-то запустит тесты academic-service одновременно. Учитывая одиночный Testcontainers — ок.
- `TmaIntegrationTest` использует `tma.bot-token: "test_bot_token_12345"` — тест должен генерировать корректный HMAC под этот токен. Если нет — тест check, что невалидный hash отклонён, но не проверяет валидный flow.

### Кандидаты на удаление/рефакторинг
- Тесты `login_withSeedAdmin_returnsTokenPair` и `login_withSeedTeacher_returnsTokenPair` — почти копии `login_withSeedStudent`. Можно параметризовать `@ParameterizedTest`.
- SQL-скрипты `clear-telegram-id.sql` / `set-telegram-id.sql` — хрупкая зависимость. Лучше `@Sql` с inline-скриптом или `TestEntityManager`.

---

## Соответствие CLAUDE.md

| Правило | Статус | Комментарий |
|---------|:------:|-------------|
| Contract-first (отдельный `*-api-contract` + `*-app`) | ❌ | Нет auth-api-contract (см. P0-1) |
| Request DTO = record | ✅ | LoginRequest, RefreshRequest, OtpRequest, OtpVerifyRequest, OtpVerifyByCodeRequest, TmaAuthRequest, ChangePasswordRequest — все records |
| Response DTO = class (HATEOAS) | ❌ | TokenResponse, PublicKeyResponse, OtpCodeResponse, ErrorResponse — records (см. P1-1) |
| Lombok запрещён в `*-api-contract` | ⚠ | Модуля контракта нет; в entity (User.java) Lombok используется — допустимо по правилу |
| Enum в БД lowercase, Java UPPER_CASE, `LowercaseEnumConverter` с autoApply | ✅ | Реализовано корректно; но см. P1-10 про корректность автоматического применения |
| Не `@Enumerated(ORDINAL)` | ✅ | Не используется |
| Soft delete (status = 'archived') | ✅ | AccountStatus.ARCHIVED существует |
| Все тайм-метки TIMESTAMPTZ (UTC) | ✅ | В миграции |
| RFC 7807 Problem Details | ✅ | GlobalExceptionHandler, ErrorResponse, media type `application/problem+json` |
| HATEOAS Level 3 (`_links`, EntityModel, PagedModel) | ❌ | Нет нигде (см. P1-1) |
| Swagger/OpenAPI аннотации на контракте | ⚠ | Аннотации в контроллере; должны быть в интерфейсе контракта (см. P0-1) |
| `@ControllerAdvice` вместо catch в контроллере | ✅ | Да |
| PUT = full, PATCH = partial | — | Не применимо (нет PUT/PATCH в auth) |
| Именование пакетов `ru.rutcampustrack.{service}.{module}` | ✅ | `ru.rutcampustrack.auth.*` |
| REST пути `/api/{service}/...` через Gateway | ⚠ | Сервис использует `/auth/*` (без `/api/`); маппинг `/api/auth/` прокидывается Gateway — проверить в 07-api-gateway |
| gRPC `ru.rutcampustrack.{service}.grpc` | — | Auth gRPC не использует (только REST + RabbitMQ) |
| Event types `{domain}.{action}` | ✅ | `otp.verified` формат (см. OtpVerifiedEvent) |
| Миграции Flyway в `src/main/resources/db/migration/V{N}__...` | ❌ | Нет прод-миграций. В тестах — V1, V2 |
| `ddl-auto: validate` | ✅ | Задано. Но при отсутствии миграций — ломает старт |
| Никогда не редактировать применённые миграции | — | В main нет миграций, нечего проверять |

---

## Зависимости между проблемами

- **P0-1 (нет contract)** блокирует: HATEOAS (P1-1) удобно реализовывать одновременно; @Operation/@ApiResponse переезжают в интерфейс контракта.
- **P0-2 (initial_password)** связан с P1-8 (password recovery): если есть полноценный recovery, `initial_password` не нужен.
- **P0-3 (общая БД)** блокирует: чистая архитектура, независимые миграции, тесты изолированы.
- **P0-4 (код в теле)** связан с P0-5 (timing): после скрытия кода в отдельный канал, timing-атака становится не теоретической, а reportable.
- **P0-6 (DoS)** блокирует выход в прод для открытого интернета.
- **P1-3 (silent catch)** зависит от P0-1 — решение exception-обработки переезжает в общий контракт.
- **P1-5 (KEYS)** решается параллельно с ведением `sessions:<userId>` (P1-4).
- **P2-1 (DEBUG)** — независимая, быстрая правка.

---

## Вопросы к владельцу проекта

1. ✅ **initial_password**: это временная мера или намеренная функция «распечатка первого пароля для студента»? Если да — предлагаю переход на одноразовый setup-токен (см. P0-2).
   → **ACCEPTED BY OWNER (2026-04-18)**: by design, plaintext остаётся в БД и в Telegram. См. `OWNER-ANSWERS.md` 01-Q1 и Meta-решение M1. Идея magic-link сохранена в `docs/archive/future-ideas.md` для v0.1+.
2. **auth и academic БД**: почему они объединены? Историческое решение или сознательное? Если сознательное — нужно документировать как "shared database with auth as reader", а владельцем миграций сделать academic (или наоборот).
3. **`/auth/refresh-body` vs `/auth/refresh`**: есть ли реальный случай, когда одна используется, а другая нет? Или это legacy?
4. **`password_reset_tokens`**: план реализовать восстановление пароля, или таблицу можно удалить?
5. **TMA `auth-date-max-age-seconds = 86400`**: почему такое большое окно? Предлагаю сократить до 300.
6. ✅ **Rate-limit по логину vs по IP**: как предполагается защищаться от DoS (P0-6)? Есть ли перед auth-service WAF/Cloudflare?
   → **AUTO-RESOLVED через Q-rate-limit (2026-04-18)**: выбран **(c) Spring Cloud Gateway + Redis** redis-rate-limiter. Лимит: `/auth/login` — 5 req/min per IP + 10 req/min per login. См. `OWNER-ANSWERS.md` 02-Q-rate-limit.
7. **Тестовые аккаунты `student`/`teacher`/`admin` с паролем `password`**: применяются ли они в prod-seed? Если нет — как валидируется, что их нет в проде?
8. **Ротация RSA ключей**: предполагается или один ключ на всё время? Если ротация — нужен план (два ключа одновременно, kid-based).
9. **HATEOAS для auth**: нужен ли он реально на /auth/login? Это чисто RPC-endpoint. Может, смягчить требование CLAUDE.md для auth?
10. **MFA**: планируется ли второй фактор (кроме OTP по Telegram)?

---

_Конец отчёта 01-auth-service.md_
