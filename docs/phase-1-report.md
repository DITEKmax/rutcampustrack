# RutCampusTrack — Отчёт Фазы 1: Auth Service + API Gateway

## Дата: Март 2026

## Цель фазы

Работающая авторизация: пользователь может залогиниться, получить JWT, обновить токен, сбросить пароль через OTP. API Gateway валидирует JWT и маршрутизирует запросы к downstream-сервисам.

---

## Что реализовано

### Подфаза 1.1: Auth Service Core (JWT + Login)

**Порт:** 9090 | **БД:** academic_db (read-only) + Redis

- **JWT**: RSA 2048-bit ключи, генерация при первом старте, сохранение в `jwt.key-dir`
- **Access Token** (15 мин): claims — `sub` (user_id), `role`, `group_id`, `is_headman`
- **Refresh Token** (7 дней): хранение в Redis как `refresh:{userId}:{jti}`, ротация (старый удаляется при использовании)
- **Endpoints:**
  - `POST /auth/login` — аутентификация по логину/паролю, BCrypt проверка, JWT пара в ответе
  - `POST /auth/refresh` — ротация токенов (старый refresh удаляется, новый выдаётся)
  - `POST /auth/logout` — идемпотентная инвалидация refresh token (204 No Content)
  - `GET /auth/public-key` — RSA публичный ключ в PEM формате + algorithm
- **Spring Security**: public routes (`/auth/login`, `/auth/public-key`, `/auth/otp/**`), остальное — authenticated
- **Ошибки**: RFC 7807 Problem Details через `@ControllerAdvice`

### Подфаза 1.2: OTP Flow + Change Password

- **OtpService**: генерация 6-значного кода, Redis-хранение с TTL 120 сек
- **Rate limiting**: макс. 3 попытки за 5 мин (`otp_attempts`), повторная отправка не чаще 60 сек (`otp_sent`)
- **Endpoints:**
  - `POST /auth/otp/request` — генерация OTP (Telegram-доставка отложена до фазы Notification Bot)
  - `POST /auth/otp/verify` — проверка OTP, выдача JWT пары при успехе
  - `POST /auth/change-password` — смена пароля (currentPassword + newPassword), authenticated
- **OtpProperties**: конфигурация через `@ConfigurationProperties` (длина кода, TTL, лимиты)
- **Решение**: `OtpExpiredException` используется и для expired, и для wrong code — не раскрывает состояние OTP (безопасность)

### Подфаза 1.3: API Gateway JWT Filter + Routing

**Порт:** 8080

- **PublicKeyConfig**: загрузка RSA публичного ключа из Auth Service при старте с retry (3 попытки, 5 сек задержка), кэширование в `AtomicReference`, обновление каждый час (`@Scheduled`)
- **JwtAuthenticationFilter**: `GlobalFilter` с order -100 (до routing filters)
  - Валидация Bearer token через JJWT 0.12 (`parseSignedClaims`, `verifyWith`)
  - Инъекция заголовков: `X-User-Id`, `X-User-Role` — всегда; `X-Group-Id`, `X-Is-Headman` — только при non-null (для TEACHER/ADMIN не выставляются)
  - Public routes bypass: `/api/auth/login`, `/api/auth/refresh`, `/api/auth/public-key`, `/api/auth/otp/**`
  - Ошибки: RFC 7807 JSON body (`application/problem+json`) при 401
- **Маршрутизация** (5 групп, в `application.yml`):
  - `/api/auth/**` → auth-service:9090
  - `/api/academic/**` → academic-service:9091
  - `/api/schedule/**` → schedule-service:9092
  - `/api/attendance/**`, `/api/reports/**` → attendance-service:9093
  - `/api/ws/**` → notification-web:9094

### Подфаза 1.4: Seed Data + Integration Testing

- **Flyway V2__seed_test_data.sql**: тестовые пользователи (admin, student, teacher, headman) с BCrypt-паролями, группа IVT-21-1, семестр Spring 2026, campus settings
- **Testcontainers**: PostgreSQL 16 + Redis, `AbstractIntegrationTest` базовый класс с `@SpringBootTest` + `@DynamicPropertySource`
- **AuthIntegrationTest** (9 тестов): логин 3 ролей, невалидные credentials (401), refresh + ротация, logout (204), public-key
- **OtpIntegrationTest** (6 тестов): OTP запрос/проверка/неверный код/несуществующий пользователь, смена пароля success/failure, Redis cleanup между тестами
- **Gateway E2E скрипт** (`scripts/verify-gateway-e2e.sh`): 4 curl-теста через Gateway (login, public-key, protected route с JWT, protected route без JWT → 401)

---

## Ключевые технические решения

| Решение | Обоснование |
|---------|------------|
| Auth Service читает `academic_db` через JPA (Flyway disabled, `ddl-auto: validate`) | Auth не владеет схемой, только читает `users` для проверки credentials |
| Локальные копии enum-ов в auth-service | Нет зависимости на `academic-api-contract` |
| RSA ключи на файловой системе (`jwt.key-dir`) | Простота для dev/prod, без Key Management Service |
| Refresh token rotation | Старый токен удаляется из Redis при каждом использовании — защита от replay |
| Logout идемпотентный | Невалидные токены молча игнорируются |
| OTP Telegram-доставка отложена | Код хранится в Redis, фактическая отправка — в фазе Notification Bot |
| `parsePemPublicKey` package-private | Тестируемость без reflection |
| Null-safe headers | `X-Group-Id`/`X-Is-Headman` не отправляются для TEACHER/ADMIN (claims = null) |
| `@DynamicPropertySource` для Redis | `GenericContainer` не распознаётся `@ServiceConnection` |
| Redis cleanup между OTP-тестами | Обход 60-секундного cooldown `otp_sent` |

---

## Файловая структура

```
services/auth-service/
├── build.gradle.kts
├── src/main/java/ru/rutcampustrack/auth/
│   ├── AuthApplication.java
│   ├── config/
│   │   ├── JwtProperties.java
│   │   ├── OtpProperties.java
│   │   └── SecurityConfig.java
│   ├── controller/
│   │   └── AuthController.java
│   ├── dto/
│   │   ├── LoginRequest.java
│   │   ├── TokenResponse.java
│   │   ├── RefreshRequest.java
│   │   ├── PublicKeyResponse.java
│   │   ├── OtpRequest.java
│   │   ├── OtpVerifyRequest.java
│   │   └── ChangePasswordRequest.java
│   ├── entity/
│   │   └── User.java
│   ├── enums/
│   │   ├── UserRole.java
│   │   └── UserStatus.java
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── InvalidCredentialsException.java
│   │   ├── OtpExpiredException.java
│   │   └── OtpRateLimitException.java
│   ├── repository/
│   │   └── UserRepository.java
│   └── service/
│       ├── AuthService.java
│       ├── JwtService.java
│       └── OtpService.java
├── src/main/resources/
│   └── application.yml
└── src/test/
    ├── java/ru/rutcampustrack/auth/integration/
    │   ├── AbstractIntegrationTest.java
    │   ├── AuthIntegrationTest.java
    │   └── OtpIntegrationTest.java
    └── resources/
        ├── application-test.yml
        ├── db/migration/
        │   ├── V1__baseline.sql
        │   └── V2__seed_test_data.sql
        └── sql/
            ├── set-telegram-id.sql
            ├── clear-telegram-id.sql
            └── reset-student-password.sql

services/api-gateway/
├── build.gradle.kts
├── src/main/java/ru/rutcampustrack/gateway/
│   ├── GatewayApplication.java          ← @EnableScheduling
│   ├── config/
│   │   └── PublicKeyConfig.java         ← RSA key fetch + cache + hourly refresh
│   └── filter/
│       └── JwtAuthenticationFilter.java ← GlobalFilter, order -100
├── src/main/resources/
│   └── application.yml                  ← 5 route groups + gateway.auth-service-url
└── src/test/java/ru/rutcampustrack/gateway/
    ├── config/
    │   └── PublicKeyConfigTest.java      ← 2 unit tests
    └── filter/
        └── JwtAuthenticationFilterTest.java ← 9 unit tests

scripts/
└── verify-gateway-e2e.sh               ← Manual Gateway E2E verification
```

---

## Тестовое покрытие

| Модуль | Тесты | Тип | Фреймворк |
|--------|-------|-----|-----------|
| API Gateway — JwtAuthenticationFilter | 9 | Unit | JUnit 5 + MockServerWebExchange |
| API Gateway — PublicKeyConfig | 2 | Unit | JUnit 5 |
| Auth Service — AuthIntegrationTest | 9 | Integration | Testcontainers (PostgreSQL + Redis) |
| Auth Service — OtpIntegrationTest | 6 | Integration | Testcontainers (PostgreSQL + Redis) |
| **Итого** | **26** | | |

Все тесты проходят: `./gradlew.bat :services:auth-service:test :services:api-gateway:test`

---

## Redis-ключи (итоговая схема)

```
refresh:{userId}:{jti}     → "valid"     TTL: 7 дней
otp:{telegramId}            → "481927"    TTL: 120 сек
otp_attempts:{telegramId}   → "2"         TTL: 300 сек (макс 3)
otp_sent:{telegramId}       → "true"      TTL: 60 сек
```

---

## Требования (покрытие)

| ID | Описание | Статус |
|----|----------|--------|
| FR-1 | JWT генерация/валидация | ✅ |
| FR-2 | Login endpoint | ✅ |
| FR-3 | Refresh token | ✅ |
| FR-4 | Logout | ✅ |
| FR-5 | OTP flow | ✅ |
| FR-6 | Change password | ✅ |
| FR-7 | Gateway JWT filter | ✅ |
| FR-8 | Gateway routing | ✅ |
| FR-9 | Public routes bypass | ✅ |
| FR-10 | Seed data + E2E testing | ✅ |
| NFR-1 | Security (BCrypt, RSA) | ✅ |
| NFR-4 | Error handling (RFC 7807) | ✅ |

---

## Следующая фаза

**Фаза 2: Academic Service** — CRUD структуры вуза (пользователи, группы, семестры, предметы), gRPC-сервер, Redis-кэширование, RabbitMQ-события.
