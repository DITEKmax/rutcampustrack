# Security Audit Report — RutCampusTrack

**Дата:** 2026-04-08
**Аудитор:** Claude Code (Security Audit)
**Версия проекта:** v6.0 (фазы 27-30 завершены)
**Стек:** Java 21, Spring Boot 3.4, Spring Cloud Gateway, PostgreSQL, MongoDB, Redis, RabbitMQ, gRPC, Python Aiogram 3, React PWA, Angular, Docker Compose, Nginx

---

## Сводка

| Уровень | Количество | Статус |
|---------|-----------|--------|
| КРИТИЧНО | 3 | ALL FIXED |
| ВАЖНО | 11 | ALL FIXED |
| РЕКОМЕНДАЦИЯ | 12 | ALL FIXED |

**Общий security score:** требует исправлений перед production-деплоем.

---

## КРИТИЧНО

### CRIT-01: Header Spoofing — эскалация привилегий

- **Статус:** FIXED (2026-04-08)
- **Файл:** `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java:83-91`
- **Проблема:** Gateway добавляет заголовки `X-User-Id`, `X-User-Role`, `X-Group-Id`, `X-Is-Headman` после валидации JWT, но не удаляет клиентские заголовки перед этим. `ServerHttpRequest.mutate().header()` дописывает значения, а не заменяет. Downstream-сервисы (`UserContextFilter`) читают первое значение — инъецированное атакующим. Любой авторизованный студент может отправить `X-User-Role: ADMIN` и получить полный доступ.
- **Exploit:** `curl -H "X-User-Id: 1" -H "X-User-Role: ADMIN" -H "Authorization: Bearer <valid_student_token>" http://gateway:8080/api/academic/users`
- **Фикс:**
```java
// В начале метода filter(), ДО валидации JWT:
ServerHttpRequest cleaned = exchange.getRequest().mutate()
    .headers(h -> {
        h.remove("X-User-Id");
        h.remove("X-User-Role");
        h.remove("X-Group-Id");
        h.remove("X-Is-Headman");
    }).build();
exchange = exchange.mutate().request(cleaned).build();
```

---

### CRIT-02: MongoDB без аутентификации

- **Статус:** FIXED (2026-04-08)
- **Файлы:**
  - `docker-compose.yml:48-62`
  - `docker-compose.prod.yml:47-62`
- **Проблема:** MongoDB (`mongo-attendance`) запускается без пароля и в dev, и в prod. Любой контейнер в Docker-сети может прочитать/изменить все данные посещаемости без credentials.
- **Фикс:**
```yaml
mongo-attendance:
  environment:
    MONGO_INITDB_ROOT_USERNAME: ${MONGO_USER}
    MONGO_INITDB_ROOT_PASSWORD: ${MONGO_PASSWORD}
```
Обновить connection URI в attendance-service и notification-web: `mongodb://${MONGO_USER}:${MONGO_PASSWORD}@mongo-attendance:27017/attendance_db?authSource=admin`

---

### CRIT-03: Redis без аутентификации

- **Статус:** FIXED (2026-04-08)
- **Файлы:**
  - `docker-compose.yml:64-77`
  - `docker-compose.prod.yml:64-77`
- **Проблема:** Redis хранит JWT refresh-токены, OTP-коды, кэш пользователей — и доступен без пароля из всей Docker-сети. Атакующий может выполнить `KEYS *`, прочитать все сессии, или `FLUSHALL`.
- **Фикс:**
```yaml
redis:
  command: redis-server --requirepass ${REDIS_PASSWORD}
```
Добавить `spring.data.redis.password=${REDIS_PASSWORD}` во все сервисы.

---

## ВАЖНО

### IMP-01: WebSocket подписки без авторизации

- **Статус:** FIXED (2026-04-08)
- **Файл:** `services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java:34`
- **Проблема:** Любой авторизованный пользователь может подписаться на `/topic/group/{ЛЮБОЙ_ID}` и `/topic/group/{ID}/headman`, получая уведомления чужих групп, включая данные excuse-запросов с персональной информацией студентов.
- **Фикс:** Добавить `ChannelInterceptor` на `configureClientInboundChannel`, который при SUBSCRIBE проверяет `groupId` из JWT-сессии и блокирует подписку на чужие группы. Подписки на `/headman` — только при `is_headman=true`.

---

### IMP-02: Нет rate limiting на /auth/login

- **Статус:** FIXED (2026-04-08)
- **Файл:** `services/auth-service/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java:42-43`
- **Проблема:** Endpoint `/auth/login` не имеет защиты от brute-force. Минимальная длина пароля — 6 символов, логины предсказуемы (`student00001`, `teacher00001`).
- **Фикс:** Redis-based rate limiter с временной блокировкой и прогрессией:

  **Per account** (`login_attempts:{login}`):

  | Неудачных попыток | Блокировка |
  |-------------------|-----------|
  | 5 за 5 мин | 5 минут |
  | 10 за 30 мин | 30 минут |
  | 20 за час | 2 часа |

  TTL ключа = окно блокировки. Истёк TTL — разблокировка автоматическая.

  **Per IP** (`login_attempts:ip:{ip}`): 20 попыток за 5 мин, TTL 15 минут.

  **Аварийный разблок админом:** `DELETE login_attempts:{login}` в Redis, или endpoint `POST /auth/admin/unlock/{login}` с `@RequireRole(ADMIN)`.

  Перманентная блокировка (lockout) не используется — создаёт вектор DoS (атакующий блокирует чужой аккаунт намеренно).

---

### IMP-03: OTP brute-force (нет лимита верификаций)

- **Статус:** FIXED (2026-04-08)
- **Файл:** `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/OtpService.java:95-101`
- **Проблема:** Rate limit есть на запрос OTP (3 за 5 мин), но нет лимита на проверку. 6-значный код за 120 секунд при неограниченных попытках — brute-forceable (1 000 000 комбинаций, ~120 000 попыток за 120с при 1000 rps = 12% шанс угадать).
- **Фикс:** Счётчик попыток верификации в Redis:

  1. При каждой неудачной проверке: `INCR otp_verify_attempts:{telegramId}` (TTL 120s)
  2. При достижении лимита (3 попытки): удалить OTP (`DEL otp:{telegramId}`), удалить счётчик, вернуть `429 "OTP аннулирован, запросите новый"`
  3. Пользователь вынужден запросить новый OTP (где уже есть rate limit — 3 запроса за 5 мин)

  **Итог:** максимум 3 попытки × 3 OTP = 9 попыток за 5 минут вместо 120 000. Шанс угадать: 0.0009%.

---

### IMP-04: Утечка exception messages

- **Статус:** FIXED (2026-04-08)
- **Файл:** `services/auth-service/src/main/java/ru/rutcampustrack/auth/exception/GlobalExceptionHandler.java:186-199`
- **Проблема:** Catch-all handler возвращает `ex.getMessage()` клиенту. При ошибках БД или NPE — утечка внутренних деталей (хосты, таблицы, классы, SQL state).
- **Фикс:** Заменить `ex.getMessage()` на `"An unexpected error occurred"`. Логировать полный exception серверно на уровне ERROR.

---

### IMP-05: Swagger UI доступен в production

- **Статус:** FIXED (2026-04-08)
- **Файлы:**
  - `nginx/conf.d/default.conf:82-112`
  - `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java:38-46`
- **Проблема:** Swagger UI и OpenAPI docs проксируются через Nginx и доступны без авторизации в production. Полная карта API (эндпоинты, параметры, DTO) для атакующего.
- **Фикс:** Закрыть Swagger за basic auth в Nginx:

  ```nginx
  location /swagger-ui/ {
      auth_basic "Dev Access";
      auth_basic_user_file /etc/nginx/.htpasswd;
      proxy_pass http://api-gateway:8080;
  }

  location /v3/api-docs {
      auth_basic "Dev Access";
      auth_basic_user_file /etc/nginx/.htpasswd;
      proxy_pass http://api-gateway:8080;
  }

  location /openapi/ {
      auth_basic "Dev Access";
      auth_basic_user_file /etc/nginx/.htpasswd;
      proxy_pass http://api-gateway:8080;
  }
  ```

  Генерация `.htpasswd`: `htpasswd -c /etc/nginx/.htpasswd admin`. Пароль хранить в `.env.prod`.

---

### IMP-06: Wildcard CORS на WebSocket

- **Статус:** FIXED (2026-04-08)
- **Файл:** `services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java:34`
- **Проблема:** `setAllowedOriginPatterns("*")` позволяет любому origin установить WebSocket-соединение. Атакующий на вредоносной странице может использовать украденный JWT для подключения.
- **Фикс:** Указать конкретные домены фронтендов. Использовать env variable для конфигурации по окружению.

---

### IMP-07: Контейнеры notification-web и notification-bot запускаются от root

- **Статус:** FIXED (2026-04-08)
- **Файлы:**
  - `services/notification-service/notification-app/Dockerfile`
  - `services/notification-bot/Dockerfile`
- **Проблема:** Два контейнера работают от root, в то время как все остальные Java-сервисы уже имеют `USER app`. Exploit уязвимости в этих сервисах даёт root-доступ в контейнере.
- **Фикс:**
```dockerfile
RUN addgroup -S app && adduser -S app -G app
USER app
```

---

### IMP-08: JWT private key без ограничения прав на файловой системе

- **Статус:** FIXED (2026-04-08)
- **Файл:** `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/JwtService.java:60-62`
- **Проблема:** RSA private key записывается через `Files.writeString()` с дефолтными правами. Может быть world-readable. Директория `keys/` не в `.gitignore`.
- **Фикс:**
```java
Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
```
Добавить `keys/` и `*.pem` в `.gitignore`.

---

### IMP-09: gRPC без аутентификации между сервисами

- **Статус:** FIXED (2026-04-08)
- **Файлы:**
  - `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java`
  - `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java`
- **Проблема:** Все gRPC-методы доступны без авторизации. Любой контейнер в Docker-сети может вызвать любой метод без проверки identity вызывающего сервиса.
- **Фикс:** Добавить gRPC interceptor с shared secret или mTLS. См. также IMP-11 по `initialPassword` в gRPC response.

---

### IMP-10: Нет ревокации сессий при смене пароля

- **Статус:** FIXED (2026-04-08)
- **Файл:** `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/AuthService.java:102-109`
- **Проблема:** При `changePassword` старые refresh-токены не инвалидируются. Украденный токен продолжает работать до 7 дней после смены пароля.
- **Фикс:** После смены пароля удалять все ключи `refresh:{userId}:*` из Redis, принудительно разлогинивая все устройства.

---

### IMP-11: initial_password передаётся по gRPC при каждом запросе

- **Статус:** FIXED (2026-04-08)
- **Файлы:**
  - `proto/academic.proto:151`
  - `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java:247`
  - `services/notification-bot/bot/handlers/start.py:27-32`
- **Проблема:** Поле `initial_password` передаётся в plaintext по gRPC при каждом вызове `getUserByTelegramId`, хотя бот использует его только один раз — при первом `/start`. До смены пароля значение хранится в открытом виде в БД (обнуляется при `changePassword` через `initial_password = NULL`). Хранение plaintext до первой смены пароля — принятое решение: админ должен иметь возможность сообщить credentials студенту напрямую. Telegram-сообщение с паролем остаётся в чате — пользователь удаляет его сам при необходимости.
- **Фикс:** Убрать `initial_password` из gRPC response `GetUserByTelegramIdResponse`. Передавать пароль боту однократно через RabbitMQ event `user.created` при создании пользователя.

---

## РЕКОМЕНДАЦИЯ

### REC-01: Слабая политика паролей

- **Статус:** FIXED (2026-04-08)
- **Файл:** `services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/ChangePasswordRequest.java:10`
- **Проблема:** Минимум 6 символов, нет требований к сложности.
- **Фикс:** `@Pattern(regexp="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$", message="Password must be at least 8 characters with uppercase, lowercase, and digit")`

---

### REC-02: Default bot token в конфигурации

- **Статус:** FIXED (2026-04-08)
- **Файл:** `services/auth-service/src/main/resources/application.yml:50`
- **Проблема:** `tma.bot-token` имеет fallback `test_bot_token_for_dev`. Если переменная не задана в production — верификация TMA init-data использует слабый default.
- **Фикс:** Убрать default: `bot-token: ${TMA_BOT_TOKEN}`. Spring не запустится без переменной — это желаемое поведение.

---

### REC-03: Actuator endpoints слишком открыты

- **Статус:** FIXED (2026-04-08)
- **Файл:** `services/auth-service/src/main/java/ru/rutcampustrack/auth/config/SecurityConfig.java:39`
- **Проблема:** `/actuator/**` = `permitAll()`. При добавлении новых actuator endpoint-ов (env, configprops, heapdump) они станут публичными.
- **Фикс:** Ограничить до `.requestMatchers("/actuator/health").permitAll()`.

---

### REC-04: RSA 2048-bit без ротации ключей

- **Статус:** FIXED (2026-04-08)
- **Файл:** `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/JwtService.java:56-57`
- **Проблема:** RSA 2048-bit на нижней границе рекомендаций NIST. Нет механизма ротации ключей — компрометация ключа = полный доступ навсегда.
- **Фикс:** RSA 3072+. Добавить `kid` (Key ID) в JWT header. Реализовать процедуру ротации с поддержкой нескольких ключей.

---

### REC-05: Нет iss/aud claims в JWT

- **Статус:** FIXED (2026-04-08)
- **Файл:** `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/JwtService.java:70-83`
- **Проблема:** Access tokens не содержат `iss` и `aud` claims. Риск confused deputy если другой сервис в организации использует RSA JWT.
- **Фикс:** Добавить `.issuer("rutcampustrack-auth")` и `.audience().add("rutcampustrack")` в builder. Валидировать на gateway.

---

### REC-06: Нет CSP и Permissions-Policy заголовков

- **Статус:** FIXED (2026-04-08)
- **Файл:** `nginx/conf.d/default.conf:36-39`
- **Проблема:** Nginx добавляет HSTS, X-Frame-Options, X-Content-Type-Options, но нет Content-Security-Policy и Permissions-Policy.
- **Фикс:**
```nginx
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self' wss:;" always;
add_header Permissions-Policy "geolocation=(self), camera=(), microphone=()" always;
```

---

### REC-07: HSTS без preload

- **Статус:** FIXED (2026-04-08)
- **Файл:** `nginx/conf.d/default.conf:36`
- **Проблема:** HSTS header без `preload` — первый визит уязвим для MITM.
- **Фикс:** `add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;` + регистрация на hstspreload.org.

---

### REC-08: RabbitMQ management UI на хосте

- **Статус:** FIXED (2026-04-08)
- **Файл:** `docker-compose.yml:88`
- **Проблема:** Порт 15672 RabbitMQ management UI проброшен на хост с dev-credentials.
- **Фикс:** Использовать `expose` вместо `ports`. Доступ через SSH tunnel при необходимости.

---

### REC-09: RabbitMQ management image в production

- **Статус:** FIXED (2026-04-08)
- **Файл:** `docker-compose.prod.yml:79`
- **Проблема:** В production используется `rabbitmq:3.13-management-alpine` с management UI внутри контейнера.
- **Фикс:** Использовать `rabbitmq:3.13-alpine` без management plugin.

---

### REC-10: Docker images без version pinning

- **Статус:** FIXED (2026-04-08)
- **Файл:** `.github/workflows/deploy.yml`
- **Проблема:** Все images тегируются только как `:latest`. Невозможен rollback, нет audit trail.
- **Фикс:** Тегировать по git SHA: `ghcr.io/.../service:${{ github.sha }}` + `:latest`.

---

### REC-11: Отсутствуют @Size на текстовых DTO полях

- **Статус:** FIXED (2026-04-08)
- **Файлы:**
  - `services/academic-service/academic-api-contract/.../CreateUserRequest.java` — `displayName`
  - `services/academic-service/academic-api-contract/.../CreateHomeworkRequest.java` — `title`, `description`, `link`
  - `services/academic-service/academic-api-contract/.../PatchUserRequest.java` — `displayName`
  - `services/academic-service/academic-api-contract/.../TransferStudentRequest.java` — `reason`
- **Проблема:** Текстовые поля имеют `@NotBlank` но не `@Size`. Возможен storage bloat при сохранении мегабайтных строк.
- **Фикс:** Добавить `@Size(max=255)` на `displayName`, `title`; `@Size(max=4000)` на `description`; `@Size(max=2048)` на `link`; `@Size(max=1000)` на `reason`.

---

### REC-12: CORS allow-credentials с широкими origins

- **Статус:** FIXED (2026-04-08)
- **Файл:** `services/api-gateway/src/main/resources/application.yml:11-29`
- **Проблема:** CORS с `allow-credentials: true` и `allowed-headers: "*"` для localhost origins. В `application-prod.yml` нет переопределения CORS.
- **Фикс:** В `application-prod.yml` указать только production-домены. Заменить `allowed-headers: "*"` на конкретный список.

---

## Позитивные находки

| # | Область | Описание |
|---|---------|----------|
| 1 | JWT storage | Токены хранятся в памяти (React `useState`/`useRef`, Angular `signal()`), не в localStorage |
| 2 | .env файлы | `.env` и `.env.prod` в `.gitignore`, никогда не коммитились в git history |
| 3 | RSA ключи | Генерируются в runtime, не хранятся в репозитории. Docker volume `jwt-keys` |
| 4 | SQL инъекции | Все запросы параметризованы через Spring Data JPA `@Param` |
| 5 | XSS | Нет `dangerouslySetInnerHTML` с пользовательскими данными. Angular без `bypassSecurityTrustHtml` |
| 6 | MongoDB запросы | Используют `Criteria` API (параметризованные), нет инъекций |
| 7 | OTP генерация | Использует `SecureRandom`, не `Random` |
| 8 | Refresh token rotation | Корректно реализована с Redis-backed jti tracking |
| 9 | Push subscriptions | `PushController` использует `requestContext.getUserId()`, а не user-supplied ID |
| 10 | Nginx | `server_tokens off`, HSTS enabled, TLS 1.2/1.3, strong ciphers |
| 11 | Health endpoints | `show-details: never` во всех `application-prod.yml` |
| 12 | CI/CD | GitHub Actions использует `secrets.*`, нет inline credentials |
| 13 | Network isolation | Инфраструктурные БД в prod используют `expose`, не `ports`. Только Nginx на 80/443 |
| 14 | Error responses | RFC 7807 Problem Details без stack traces (кроме catch-all, см. IMP-04) |
| 15 | RabbitMQ consumers | Не пробрасывают exceptions (предотвращение infinite requeue DoS) |
| 16 | CSRF | Корректно отключён для stateless JWT API |

---

## Приоритет исправлений

| Приоритет | ID | Описание | Срок |
|-----------|-----|----------|------|
| Немедленно | CRIT-01 | Header spoofing — эскалация привилегий | До деплоя |
| До деплоя | CRIT-02 | MongoDB auth | До деплоя |
| До деплоя | CRIT-03 | Redis auth | До деплоя |
| До деплоя | IMP-01 | WebSocket authz | До деплоя |
| До деплоя | IMP-05 | Swagger в prod | До деплоя |
| Ближайший спринт | IMP-02..IMP-04 | Rate limiting, OTP, exception messages | 1-2 недели |
| Ближайший спринт | IMP-06..IMP-11 | CORS, Docker, keys, gRPC, sessions, initial_password в gRPC | 1-2 недели |
| Планово | REC-01..REC-12 | Hardening | 2-4 недели |