# 15. Сквозные проблемы (cross-cutting issues)

## Сводка

Этот отчёт — не дубль P0/P1-списков из 01–14, а **карта фикс-точек**: места, где один инженерный ход закрывает несколько на вид независимых проблем. Проект накопил 53 P0 и 136 P1 на 13 поверхностях, но в основе — ~10 корневых паттернов, раскатанных по всем сервисам. Фиксить «по отчёту» — значит делать одну и ту же работу пять раз; фиксить «по кластеру» — значит за один PR снимать 4–6 P0 из разных слоёв.

**P0-кластеры (10 штук).** C0-1 `UserContextFilter` — самый «жирный», закрывает 5 P0 из academic/schedule/attendance/notification-web плюс Gateway. C0-2 (`initial_password`-цепочка), C0-3 (`AFTER_COMMIT` без outbox), C0-4 (rate-limit ни в одном слое), C0-5 (logout-lifecycle ничего не чистит), C0-6 (CSP корневого nginx ломает лендинг), C0-7 (JWT в localStorage + query-string на WebSocket), C0-8 (CI и deploy — независимые workflow), C0-9 (`.env.prod` с реальными секретами + ротация), C0-10 (путь Let's Encrypt сертификата).

**P1-кластеры (11 штук).** Сосредоточены вокруг: унификации события/тип/схема (C1-2 type-drift, C1-5 contract-тесты событий), масштабирования (C1-7 `@Scheduled` без ShedLock), повторяемости деплоя (C1-3 `:latest` теги, C1-6 base images без digest), зрелости пайплайна (C1-4 coverage-gate), UI-консолидации (C1-1 три STOMP-клиента + дубликаты в PWA), лендинг-актуальности (C1-8 рассинхрон с v9.0) и сетей безопасности (C1-9 `latecheckin`/callback'и без тестов, C1-10 DEBUG-логи + JWT в query, C1-11 отсутствие единого `@ControllerAdvice`).

**Карта покрытия.**
- C0-1 → 02 P0-2, 03 P0-1, 04 P0-1, 05 P0-1, 07 P0-2 (strip headers), 14 P1-1 (нет contract-теста Gateway↔downstream).
- C0-2 → 01 P0-2, 02 P0-1, 06 P0-3, 08 P0-1, 10 P2-13.
- C0-3 → 02 P0-6, 03 P0-2, 04 P0-5 (DLQ-потеря при падении gRPC), 06 P1-7 (dispatcher глушит ошибки).
- C0-4 → 01 P1-2 (verify-by-code), 01 P0-6 (login RL), 07 P1-2 (нет RL в Gateway), 13 P1-3 (нет RL в nginx), 14 P1-2 (нет теста).
- C0-5 → 09 P0-4, 09 P0-5, 10 P0-4, 14 P1-4.
- C0-6 → 12 P0-1, 13 P0-4, 09 P1-3, 10 P0-3.
- C0-7 → 09 P0-1, 09 P0-2, 10 P0-1, 10 P0-2, 07 P1-4 (JWT в query попадает в DEBUG-логи).
- C0-8 → 13 P0-2, 14 P1-8.
- C0-9 → 13 P0-3, 08 P0-1 (общая проблема «секрет в канале»).
- C0-10 → 13 P0-1.

---

## Карта зависимостей между фиксами

```
C0-9 (ротация секретов) ────┐
                            ▼
C0-10 (cert-name LE) ─────► C0-8 (CI→deploy gate) ─────► C0-3 (outbox)
                                  │                         │
                                  ▼                         ▼
                            C0-1 (UserContextFilter) ─► C1-5 (contract-tests событий)
                                  │                         │
                                  ▼                         ▼
                            C0-4 (rate-limit)           C1-7 (ShedLock)
                                  │
                                  ▼
                            C0-2 (initial_password) ──► C1-2 (OpenAPI → TS типы)
                                  │
                                  ▼
                            C0-7 (JWT HttpOnly cookie) ──► C0-5 (logout lifecycle)
                                  │
                                  ▼
                            C0-6 (CSP self-host / whitelist) ──► C1-8 (контент лендинга)
```

Суть графа: сначала чиним инфраструктуру (секреты, cert, CI-gate), потом — backend-инварианты (outbox, ролевой контекст, rate-limit), потом — поверхности (JWT/logout/CSP). C1-фиксы либо делаются внутри кластера (C1-4 coverage-gate — внутри C0-8), либо после C0 (C1-1 unified NotificationCenter — после C0-7).

---

## P0-кластеры (корневые фиксы для блокеров релиза)

### C0-1: 🔧 TO-FIX через Internal JWT — `UserContextFilter` доверяет `X-User-*` — backend RBAC bypass
**Статус (2026-04-18):** выбран **Internal JWT (Уровень 2 Zero Trust)**, см. `OWNER-ANSWERS.md` 02-Q2. Gateway после валидации внешнего JWT генерирует короткоживущий внутренний JWT (RSA, ~5 мин, claims `userId/role/groupId`), сервисы валидируют публичным RSA-ключом. Старые `X-User-*` уходят. Estimate ~3 человеко-дня. Двойной режим на короткий период раскатки. Ниже — оригинальное описание.

- **Затронутые отчёты:** 02 P0-2, 03 P0-1, 04 P0-1, 05 P0-1; 07 P0-2 (единственная защита — strip в Gateway); 14 P1-1 (нет contract-теста, который бы это поймал).
- **Суть.** Четыре Java-сервиса (`academic`, `schedule`, `attendance`, `notification-web`) построены на идее «Gateway гарантированно переписывает `X-User-Id` / `X-User-Role` / `X-Group-Id` / `X-Is-Headman`, downstream доверяет». Фильтры `UserContextFilter.doFilterInternal` читают заголовки без проверки источника — ни mTLS, ни HMAC-подписи, ни allowlist. В docker private-net это работает только пока network изолирован. Любой lateral (скомпрометированный промтейл, cadvisor с `privileged: true`, ошибка в nginx при будущем рефакторинге, переезд на k8s без review-ингресса) — и любой контейнер может прислать `POST http://academic-service:9091/academic/users` с `X-User-Role: ADMIN` → полный контроль, включая удаление групп и выдачу `initial_password`.
- **Что видно в коде.**
  - `services/academic-service/.../security/UserContextFilter.java:22-46`.
  - `services/schedule-service/.../security/UserContextFilter.java:32-41`.
  - `services/attendance-service/.../security/UserContextFilter.java:34-43`.
  - `services/notification-service/.../security/UserContextFilter.java:34-43`.
  - `services/api-gateway/.../filter/JwtAuthenticationFilter.java:65-69` — strip внутренних заголовков перед прокси (единственная защита).
  - `services/schedule-service/src/test/.../SecuritySmokeTest` — в отчёте 14 прямо показано: достаточно трёх строк `.header("X-User-Role","ADMIN")`, чтобы пройти RBAC.
- **Почему один фикс.** Все четыре сервиса реализуют одинаковый паттерн — фильтр, `RequestContext @Scope(request)`, `RoleCheckAspect`. Единая реализация подписи `X-Context-Signature = HMAC(secret, userId|role|groupId|ts)` (симметрично тому, как уже сделан `x-grpc-secret` в 02 P0-5 / attendance / schedule) закрывает их одним PR на общий модуль + четыре бампа зависимости.
- **Корневой фикс.**
  1. Выделить библиотеку `shared-security-headers` (java-library, без Spring Boot) с классом `InternalRequestValidator` + Spring Boot starter-конфиг.
  2. В Gateway — `GatewayFilter`, который перед proxy_pass считает HMAC (`INTERNAL_SECRET` из env, обязательный, fail-fast при отсутствии) и добавляет `X-Context-Signature` и `X-Context-Timestamp`.
  3. В каждом downstream `UserContextFilter` — сначала валидация HMAC, потом парсинг. Окно допустимости `timestamp` — 30 секунд.
  4. `NumberFormatException` / `IllegalArgumentException` / NPE в парсинге обернуть в try/catch → 401 Problem Details (закрывает 02 P0-3, 03 P0-3, 04 P0-1 подкейс).
  5. Добавить прод-профиль: сервис падает на старте, если `INTERNAL_SECRET` пустой (аналогично P0-5 academic по gRPC-секрету).
- **Вспомогательные меры.** Contract-тест модуля Gateway ↔ downstream (14 P1-1): Testcontainers поднимает оба, проверяет «прямой запрос на :9091 без подписи → 401». И параметрически — для каждого сервиса.
- **Estimate:** 3–5 человеко-дней (разработка + тесты + переключение четырёх сервисов + e2e).
- **Блокируется:** C0-8 (без CI-gate рискуем выкатить полуфикс).
- **Разблокирует:** C0-4 (rate-limit можно ставить на Gateway, зная что downstream не обходится); C0-2 (убираем gRPC/REST leaks `initial_password`, зная что канал между сервисами защищён).

### C0-2: ✅ DISSOLVED — `initial_password` plaintext-цепочка
**Статус (2026-04-18):** кластер РАСПУЩЕН по решению владельца. Все 4 P0 + 1 P2 переходят в «Принято как есть» (см. `OWNER-ANSWERS.md` 01-Q1, 02-Q1, 06-Q2, 08-Q1, 10-Q7 + Meta M1). Идея magic-link сохранена в `docs/archive/future-ideas.md` для v0.1+. Зависимости от C0-2 в dependency graph и порядке исполнения должны быть пересчитаны (см. audit-trail в OWNER-ANSWERS.md). Ниже — оригинальное описание для исторической ссылки.

- **Затронутые отчёты:** 01 P0-2 (БД + entity), 02 P0-1 (REST + gRPC), 06 P0-3 (Telegram), 08 P0-1 (proto-контракт), 10 P2-13 (admin-таблица показывает).
- **Суть.** Пароль нового пользователя живёт в колонке `users.initial_password VARCHAR(128)`, возвращается в JSON ответе `GET /academic/users`, передаётся по gRPC `GetUserByTelegramId` в `UserByTelegramIdResponse.initial_password` (proto/academic.proto:155), попадает в Telegram-чат бота в `<code>pass123</code>` без self-destruct, и виден админам в таблице web-panel. Любая компрометация в одном из пяти мест = утечка паролей.
- **Что видно в коде.**
  - `services/auth-service/.../entity/User.java:74-75`, `src/test/resources/db/migration/V1__baseline.sql:37`.
  - `services/academic-service/academic-api-contract/.../dto/user/UserResponse.java:33` — поле `initialPassword`.
  - `services/academic-service/.../user/UserAssembler.java:54-74` — `toResponse(entity, includeInitialPassword=true)`.
  - `services/academic-service/.../grpc/AcademicGrpcServiceImpl.java:245-253`.
  - `proto/academic.proto:155` — `string initial_password = 10;`.
  - `services/notification-bot/bot/handlers/start.py:32-42` — `Ваш пароль: <code>{initial_password}</code>`.
  - `frontends/web-panel/src/app/features/admin/users/...` — отображение в таблице.
- **Почему один фикс.** Пароль не должен существовать в БД вовсе — тогда автоматически исчезают пять каналов утечки. Если сохраняем интерфейс «админ создал — пользователь получил» — заменяем plaintext на одноразовый `setup_token` (хэш + expiry 7 дней) и magic-link `/auth/setup-password?token=...`.
- **Корневой фикс.**
  1. Новая таблица `password_setup_tokens(token_hash, user_id, expires_at, used_at)` (или переиспользуем dead `password_reset_tokens`, см. 01 P1-8).
  2. `POST /academic/users` возвращает `UserCreatedResponse` с `setupUrl` (single-use) вместо пароля. Только этот ответ видит пароль-эквивалент.
  3. `UserResponse` и `GET /academic/users` — убрать поле `initialPassword` совсем. `toAdminModel` удалить.
  4. `proto/academic.proto:155` — `reserved 10; reserved "initial_password";` + перегенерация stubs. Bot получает `setup_link` (или ничего — отдельное событие/поле).
  5. Flyway V15 — `ALTER TABLE users DROP COLUMN initial_password`.
  6. Bot `/start` — отправляет `<tg-spoiler>Ссылка для настройки: https://ruttrack.site/setup?t=...</tg-spoiler>` + `delete_message` через 5 минут.
  7. Web-panel admin users — поле `initialPassword` удалить из таблицы; показывать `Ссылка отправлена`.
- **Вспомогательные меры.** Добавить тест, что `GET /academic/users` никогда не содержит `initialPassword` в JSON (простой `jsonPath().doesNotExist()`).
- **Estimate:** 3–4 человеко-дня (миграция + контракты + bot + UI + тесты).
- **Блокируется:** C0-1 (иначе фикс бесполезен: если внутренний канал доверчив, setup-токен можно выжать через gRPC обходом).
- **Разблокирует:** ротацию `BOT_TOKEN` (C0-9) без риска, что старый бот с plaintext-паролями остаётся живым.

### C0-3: 🔧 TO-FIX через In-app outbox — `@TransactionalEventListener(AFTER_COMMIT)` без outbox — потеря событий
**Статус (2026-04-18):** выбран **(b) In-app outbox table** в каждом из 3 backend-сервисов + `@Scheduled` publisher-job (~5 сек). См. `OWNER-ANSWERS.md` 02-Q3. ShedLock на publisher-job становится критичным (NEW-8). Estimate ~3-4 человеко-дня. Ниже — оригинальное описание.



- **Затронутые отчёты:** 02 P0-6, 03 P0-2, 04 P0-5 (DLQ-потеря на consumer-стороне — симметричная проблема), 06 P1-7 (`EventDispatcher` ACK'ает даже при exception), 14 P1-5 (нет contract-тестов, которые бы поймали потери).
- **Суть.** Три сервиса (`academic`, `schedule`, `attendance`) публикуют события через `@TransactionalEventListener(phase = AFTER_COMMIT)` + прямой `rabbitTemplate.convertAndSend`. Транзакция БД уже закоммичена, брокер может быть недоступен (рестарт, сеть), сериализация Jackson может упасть — и событие теряется безвозвратно. `lesson.started` / `lesson.closed` — единственный сигнал для attendance и notification; их потеря в 8:30 утра = группа не получит push «пара началась», ведомость не откроется.
- **Что видно в коде.**
  - `services/academic-service/.../event/DomainEventListener.java:31-35`.
  - `services/schedule-service/.../event/DomainEventListener.java:31-35`.
  - `services/attendance-service/.../event/AttendanceEventPublisher.java` + `RabbitConfig.java` — `channelTransacted = false`.
  - `services/notification-bot/bot/consumers/event_dispatcher.py` — `async with message.process()` ACK'ает даже при exception.
- **Почему один фикс.** Единый transactional outbox-паттерн: `domain_events(id, event_type, payload_json, occurred_at, sent_at)` + `@Scheduled` sender. Три Java-сервиса получают одинаковый модуль, Python-бот — отдельный retry с `message.reject(requeue=False)` + DLQ consumer.
- **Корневой фикс.**
  1. Модуль `shared-outbox` (java-library) с абстракциями `OutboxEntry`, `OutboxRepository` (JPA/Mongo варианты), `OutboxSender @Scheduled`.
  2. В `academic-service`, `schedule-service`: Flyway-миграция `V{N}__outbox.sql` с таблицей и индексом `(sent_at IS NULL, id)`.
  3. В `attendance-service`: Mongo-коллекция `outbox` с TTL-индексом на `sent_at` (auto-cleanup через 7 дней).
  4. `DomainEventListener` меняется: вместо `rabbitTemplate.convertAndSend` — `outboxRepository.save` **в той же транзакции** (переключить `AFTER_COMMIT` → обычный `@EventListener` + `@Transactional(propagation = REQUIRED)`).
  5. `OutboxSender @Scheduled(fixedDelay=5_000)` + `@SchedulerLock` (ShedLock из C1-7) читает `WHERE sent_at IS NULL ORDER BY id LIMIT 200`, шлёт, помечает.
  6. `event_id` UUID в payload для dedup на consumer-стороне. Consumer'ы держат Redis-SET `seen:{event_id}` с TTL 10 минут (или Caffeine in-memory).
  7. На стороне `notification-bot` — `EventDispatcher.dispatch` при exception в handler'е делает `raise` наверх, aio-pika автоматически отправит в DLQ; отдельный DLQ-consumer (ручной operator) с replay-endpoint'ом.
- **Вспомогательные меры.** Contract-тест (C1-5) каждого события: producer сохраняет в outbox → sender шлёт → consumer валидирует JSON Schema.
- **Estimate:** 5–7 человеко-дней (три сервиса × ~1.5 дня + bot DLQ + dedup + тесты).
- **Блокируется:** C0-1 (ставить outbox поверх ненадёжного ролевого контекста — ломать порядок приоритетов), C0-8 (CI-gate обязателен для миграции БД).
- **Разблокирует:** C1-5, C1-7.

### C0-4: 🔧 TO-FIX через Spring Cloud Gateway + Redis — Rate-limiting отсутствует на всех слоях
**Статус (2026-04-18):** выбран **(c) Spring Cloud Gateway redis-rate-limiter** (вопреки рекомендации nginx — владелец предпочёл архитектурно чистый вариант). См. `OWNER-ANSWERS.md` 02-Q-rate-limit. Лимиты per-route + per-IP/per-user. 13 P1-3 (nginx-RL) → ❌ ОТКЛОНЁН. Estimate ~2-3 человеко-дня. Ниже — оригинальное описание.



- **Затронутые отчёты:** 01 P0-6 (LoginRateLimiter блокирует чужие аккаунты — тоже симптом отсутствия IP-layer), 01 P1-2 (verify-by-code брутфорсится за ~3 часа), 07 P1-2 (нет RL в Gateway), 13 P1-3 (нет `limit_req` в nginx), 14 P1-2 (нет тестов).
- **Суть.** Нет ни одной точки, где запросы ограничивались бы по IP / по пользователю: ни в nginx (`limit_req_zone` отсутствует), ни в Spring Cloud Gateway (нет `RequestRateLimiter` фильтра), ни на downstream-уровне (кроме `LoginRateLimiter` в auth-service, который блокирует по login'у — сам создаёт DoS-вектор). OTP verify-by-code с 6-значным кодом брутфорсится за ~3 часа при 100 RPS.
- **Что видно в коде.**
  - `services/api-gateway/src/main/resources/application.yml:35-104` — routes без `RequestRateLimiter`.
  - `nginx/nginx.conf`, `nginx/conf.d/default.conf` — нет `limit_req_zone`, `limit_req`, `limit_conn`.
  - `services/auth-service/.../service/LoginRateLimiter.java:48-67` — ключ `login_attempts:<login>` без IP.
- **Почему один фикс.** Нужен **один** слой RL, не два. Самый близкий к атакующему — nginx (`limit_req_zone $binary_remote_addr`). Это чинится одним редактированием `nginx/nginx.conf` и не тянет зависимостей. Если выбираем Spring Cloud Gateway с Redis-backed `redis-rate-limiter` — чинит и WebSocket handshake (через GatewayFilter), но добавляет Redis-зависимость к Gateway (её нет сейчас).
- **Корневой фикс (nginx-вариант, рекомендуется).**
  ```nginx
  http {
    limit_req_zone $binary_remote_addr zone=api_common:10m rate=30r/s;
    limit_req_zone $binary_remote_addr zone=api_auth:10m   rate=10r/m;
    limit_req_zone $binary_remote_addr zone=api_otp:10m    rate=5r/m;
  }
  location /api/auth/otp/ { limit_req zone=api_otp burst=3 nodelay; ... }
  location /api/auth/     { limit_req zone=api_auth burst=5 nodelay; ... }
  location /api/          { limit_req zone=api_common burst=50 nodelay; ... }
  ```
  Плюс: `LoginRateLimiter` — переключить ключ с `login` на `(ip, login)` и блокировать по IP (чинит 01 P0-6).
- **Вспомогательные меры.** Тест (14 P1-2): 10 `POST /auth/otp/verify-by-code` с неверным кодом → 11-я 429. Использовать Testcontainers с nginx + RL.
- **Estimate:** 1–2 человеко-дня (редакт nginx + тюнинг LoginRateLimiter + тесты).
- **Блокируется:** C0-8 (без CI-gate рискуем выкатить слишком агрессивный RL).
- **Разблокирует:** ничего нового не блокирует, но снимает один из основных поверхностей DoS/брутфорса.

### C0-5: 🔧 TO-FIX через clearAllClientState() + DELETE /push/subscriptions/me — Logout lifecycle не чистит побочное состояние
**Статус (2026-04-18):** будет закрыто Частью Б из C0-7-связки. Общий клиентский `clearAllClientState()` (caches, push unsubscribe, sessionStorage, STOMP close) + новый backend endpoint `DELETE /api/notifications/push/subscriptions/me`. См. `OWNER-ANSWERS.md` 02-Q-frontend-security. Estimate ~2-3 дня (часть из 8-12 общих).



- **Затронутые отчёты:** 09 P0-4 (SW cache `headman-api-cache-v1` живёт 24ч после logout), 09 P0-5 (push-subscription не отвязывается), 10 P0-4 (sessionStorage уведомлений виден следующему юзеру), 14 P1-4 (нет тестов на logout cleanup).
- **Суть.** В PWA и web-panel logout чистит только `localStorage['rct.auth.v1']` + React/Angular state. Остаются: Workbox runtime-cache, VAPID push-subscription, `sessionStorage['rct-global-notifications']` с ПДн (имена студентов), STOMP-клиенты (в web-panel — три параллельных, 10 P1-5). На общем устройстве (лаборантская, деканат) второй пользователь видит кэш/уведомления первого.
- **Что видно в коде.**
  - `frontends/pwa/src/features/auth/AuthProvider.tsx:200-211` — `logout()` без cleanup.
  - `frontends/pwa/src/sw.ts:104-116` — `headman-api-cache-v1`, 24ч / 100 записей.
  - `frontends/pwa/src/features/push/usePushSubscription.ts:54-70` — `unsubscribe` не вызывается.
  - `frontends/web-panel/src/app/core/auth/auth.service.ts:137-148`.
  - `frontends/web-panel/src/app/core/notifications/notification-center.service.ts:97-112` — `sessionStorage` не чистится.
- **Почему один фикс.** На каждом клиенте — один метод `logout()`, в который дописывается общий `clearAllClientState()` (чистка кэшей, unsubscribe push, закрытие всех STOMP, `sessionStorage.clear()` для известных ключей). Один PR на PWA, один на web-panel; mini-app логически наследует то же (но в 11 — пропущен).
- **Корневой фикс.**
  1. В PWA `AuthProvider.logout()`:
     - `sessionStorage.removeItem('rct.pwa.notifications.v1')`.
     - `caches.keys()` → `caches.delete(k)` для всех `k.startsWith('headman-api-cache')` и `api-cache`.
     - `reg.pushManager.getSubscription()` → `apiClient.delete('/push/subscribe', {endpoint})` → `sub.unsubscribe()`.
     - Отправка MessageEvent в SW `{type:'LOGOUT'}` — SW дропает любые in-flight background-sync.
  2. В web-panel `AuthService.clearTokens()`:
     - `NotificationCenterService.clear()` + `sessionStorage.removeItem('rct-global-notifications')`.
     - `StudentStompService.disconnect()` + `HeadmanStompService.disconnect()` (оба решаются вместе с C1-1 — единый `NotificationCenter`).
     - Отправка `AbortController.abort()` на все in-flight (10 P1-16).
  3. На backend — `POST /api/push/subscribe` сделать `UPSERT BY (endpoint)` (перезаписывать user_id), чтобы второй клиент на том же устройстве не получил дубль (05 P2 + 09 P0-5).
- **Вспомогательные меры.** Тесты (14 P1-4): spy на `caches.delete`, `pushManager.getSubscription().unsubscribe`, `STOMP.deactivate()` при вызове logout.
- **Estimate:** 2 человеко-дня (PWA + web-panel + тесты + backend UPSERT).
- **Блокируется:** C0-7 (вычистить кэш, но не подвинуть JWT из localStorage — половинчатый фикс).
- **Разблокирует:** C1-1 (unified NotificationCenter — естественное продолжение).

### C0-6: 🔧 TO-FIX через self-host лендинга — CSP корневого nginx блокирует CDN лендинга
**Статус (2026-04-18):** выбран **(a) Self-host** — лендинг переходит на собственные assets, CSP не меняется. См. `OWNER-ANSWERS.md` 02-Q-csp-landing. Estimate ~1-2 дня. Связанные 12 P1-4 (SRI) ОТКЛОНЁНЫ. NEW-17 (font licenses), NEW-18 (GSAP license), NEW-19 (CI guard против CDN ссылок). Ниже — оригинальное описание.



- **Затронутые отчёты:** 12 P0-1 (лендинг в проде визуально сломан), 13 P0-4 (CSP в корневом `default.conf:40`), 09 P1-3 (PWA nginx без CSP — отдельная проблема с тем же шагом), 10 P0-3 (web-panel nginx без CSP).
- **Суть.** Корневой reverse-proxy nginx выставляет `Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-hashes' 'sha256-…'; style-src 'self' 'unsafe-inline'; ...; font-src 'self';` — но лендинг тянет Fontshare / Google Fonts / Phosphor Icons (unpkg) / GSAP (jsdelivr). В проде страница `/presentation/` визуально сломана: hero-title съехал под `overflow:hidden` из-за того, что GSAP не отработал `gsap.from`. Параллельно — ни PWA, ни web-panel nginx не добавляют CSP (т.е. на `/app/*` и `/login` CSP отсутствует; корневой nginx страдает от обратной крайности — жёстче, чем может).
- **Что видно в коде.**
  - `nginx/conf.d/default.conf:40` — жёсткая CSP строка.
  - `frontends/landing/dist/index.html:33-41, 1467-1468` — внешние CDN.
  - `frontends/pwa/nginx.conf:1-38` — нет CSP.
  - `frontends/web-panel/nginx.conf:1-32` — нет CSP.
- **Почему один фикс.** Либо self-host CDN (решение обоих симптомов: CSP остаётся строгой, страница работает), либо whitelist конкретных origins в CSP + SRI на каждый внешний ресурс. Рекомендуется self-host — убирает зависимость от uptime Fontshare/Google и закрывает 12 P1-4 (нет SRI).
- **Корневой фикс (self-host, рекомендуется).**
  1. `frontends/landing/dist/assets/vendor/`: положить `ClashDisplay-*.woff2`, `GeneralSans-*.woff2`, `DMSans-*.woff2`, `JetBrainsMono-*.woff2`, `phosphor-icons.css` + `Phosphor-*.woff2`, `gsap.min.js`, `ScrollTrigger.min.js`. Собственные `@font-face` в inline-style.
  2. Убрать все preconnect/stylesheet/script на `*.fontshare.com`, `fonts.googleapis.com`, `fonts.gstatic.com`, `unpkg.com`, `cdn.jsdelivr.net`.
  3. CSP корневого nginx оставить `'self'`-only (после self-host уже ничего не блокируется).
  4. Одновременно — в `frontends/pwa/nginx.conf` и `frontends/web-panel/nginx.conf` добавить идентичные `add_header Content-Security-Policy "..."` + `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy` (закрывает 09 P1-3, 10 P0-3).
  5. Для PWA: `'wasm-unsafe-eval'` для Workbox (если понадобится), `connect-src 'self' wss://ruttrack.site`.
- **Вспомогательные меры.** Playwright smoke-тест (14 P2-6): `page.goto('/presentation/')`, проверка отсутствия `CSP violation` в console.error.
- **Estimate:** 1–2 человеко-дня (скачивание + прошивка `<link>/<script>` на локальные + прогон).
- **Блокируется:** ничем, самый изолированный P0.
- **Разблокирует:** C1-8 (как только лендинг живой — ревизия контента excuse/flow на v9.0-реальность имеет смысл).

### C0-7: 🔧 TO-FIX через HttpOnly cookie + WS-ticket — JWT в localStorage + JWT в WebSocket query string
**Статус (2026-04-18):** Часть А из связки C0-5+C0-7. HttpOnly Secure SameSite=Strict cookie для refresh, in-memory access, `POST /auth/ws-ticket` → 60-сек opaque ticket в Redis для WebSocket. Breaking change без двойных endpoint'ов. См. `OWNER-ANSWERS.md` 02-Q-frontend-security. Estimate ~5-7 дней (часть из 8-12 общих).



- **Затронутые отчёты:** 09 P0-1 (PWA), 09 P0-2 (PWA WS), 10 P0-1 (web-panel), 10 P0-2 (web-panel WS три места), 07 P1-4 (DEBUG-логи Gateway пишут query в access-log), 14 P1-4 (нет тестов).
- **Суть.** JWT access+refresh (7 дней) живут в `localStorage['rct.auth.v1']` — любой XSS/supply-chain компромисс = долгосрочный доступ. JWT также передаётся в URL WebSocket handshake (`/api/ws?token=...`) — попадает в nginx access.log, в DEBUG Spring Cloud Gateway, в PWA SW cache fallback SockJS, в `Referer` при редиректах, в DevTools HAR. Обе дыры решаются одной архитектурной заменой — `HttpOnly Secure SameSite=Strict` cookie для refresh + short-lived `/auth/ws-ticket` для WebSocket.
- **Что видно в коде.**
  - `frontends/pwa/src/features/auth/AuthProvider.tsx:28-63` — `STORAGE_KEY='rct.auth.v1'`.
  - `frontends/pwa/src/features/checkin/useStompCheckin.ts:20` — `new SockJS('/api/ws?token=${accessToken}')`.
  - `frontends/pwa/src/features/notifications/NotificationCenter.tsx:274` — то же.
  - `frontends/web-panel/src/app/core/auth/auth.service.ts:14,21-49,77-112`.
  - `frontends/web-panel/src/app/.../student-stomp.service.ts:88`, `headman-stomp.service.ts:44`, `notification-center.service.ts:135`.
  - `services/api-gateway/src/main/resources/application.yml:141-144` — DEBUG включён по дефолту.
- **Почему один фикс.** Оба вектора — проявления одного паттерна «токен доступен JS / URL». Backend меняется в одном месте (`auth-service`), клиенты — в паре точек. Рекомендуется делать **последним** из P0, чтобы не тратить работу на миграцию, пока не закрыты C0-1/C0-3/C0-8 (без них сам токен бесполезен как точка защиты).
- **Корневой фикс.**
  1. `auth-service`: на `POST /auth/login` — `Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=604800`. Access-token возвращается в body.
  2. `POST /auth/refresh` — читает cookie (body-less), ставит новую cookie + возвращает новый access.
  3. Новый endpoint `POST /auth/ws-ticket` (auth required) → `{ticket: "<opaque 60s>"}` — одноразовый, хранится в Redis `auth:ws:{ticket}` с TTL 60 сек.
  4. `notification-service` `JwtHandshakeInterceptor` — принимает либо `Authorization: Bearer <ticket>` (предпочтительно, через `connectHeaders` STOMP), либо `?ticket=...` (старый endpoint, ticket consumed).
  5. PWA: `AuthProvider` держит access в React state (не в localStorage), рефрешит через `axios.post('/api/auth/refresh', {}, {withCredentials: true})`.
  6. PWA: SockJS → native WebSocket + `connectHeaders: {Authorization: Bearer ticket}`. Либо `?ticket=...` (ticket виден в access-log, но живёт 60 сек и одноразовый).
  7. Web-panel: `provideHttpClient` + `withCredentials: true`; три STOMP-сервиса переключаются на `ticket`-flow (и консолидируются в C1-1).
  8. Gateway: CORS `allow-credentials: true` с конкретным origin (не `*`); убрать `token` из DEBUG-логов (маскировать `?token=` и `?ticket=` в log_format).
- **Вспомогательные меры.** Тесты (14 P1-4): после `logout()` — `document.cookie` не содержит `refresh_token`. Backend тест: cookie ставится с `HttpOnly` и `SameSite=Strict`.
- **Estimate:** 8–12 человеко-дней (auth-service + Gateway CORS + PWA + web-panel + тесты + миграция клиентов).
- **Блокируется:** C0-5 (без logout cleanup cookie останется «полу-чистой»), C0-1 (внутренний ролевой контекст должен быть надёжен раньше).
- **Разблокирует:** C1-1, 09 P1-1 (parseJwt без валидации становится менее критичен — JWT не в руках XSS).

### C0-8: 🔧 TO-FIX через branch protection — CI и deploy — независимые workflow, красный CI не блокирует прод
**Статус (2026-04-18):** branch protection + required_status_checks (без require reviews — single dev) + `workflow_run` trigger в `deploy.yml`. См. `OWNER-ANSWERS.md` 02-Q-ci-deploy-gate. Estimate ~5 мин setup + 1-2 ч на правку workflow.



- **Затронутые отчёты:** 13 P0-2, 14 P1-8.
- **Суть.** `.github/workflows/ci.yml` и `.github/workflows/deploy.yml` оба слушают `push` в `main` независимо. `deploy.yml` не имеет `needs: [ci]`. Красный CI (упавшие Java/Python/Frontend тесты) не мешает образам запушиться в GHCR и SSH-job'у скачать их на VPS.
- **Что видно в коде.**
  - `.github/workflows/ci.yml:3-7` — `on: push/pull_request: branches: ['**']`.
  - `.github/workflows/deploy.yml:3-5` — `on: push: branches: [main]` без `needs`.
- **Почему один фикс.** Либо branch protection (внешне, один чекбокс в GitHub UI), либо `workflow_run` триггер. Предпочтительно — и то, и то.
- **Корневой фикс.**
  1. В GitHub UI: branch protection на `main` с `required_status_checks: ci.yml` (все его jobs).
  2. В `deploy.yml`: поменять триггер на `on: workflow_run: workflows: ["CI"]; types: [completed]; branches: [main]` + условие `if: github.event.workflow_run.conclusion == 'success'`.
  3. Внутри `deploy.yml` — перед `build-push` добавить шаг verify `git rev-parse HEAD == github.event.workflow_run.head_sha`.
  4. Одновременно — внедрить coverage-gate (C1-4) как один из required checks.
- **Вспомогательные меры.** Добавить `paths-ignore: ['docs/**', '.planning/**']` в `ci.yml` (13 P1-11 — не запускать полный CI на правки docs).
- **Estimate:** 0.5 человеко-дня (конфиг + тест прогоном).
- **Блокируется:** ничем — можно и нужно делать первым (чтобы последующие фиксы шли через gate).
- **Разблокирует:** все остальные кластера — каждый следующий фикс не рискует попасть в прод сломанным.

### C0-9: ✅ ACCEPTED (без ротации) + 🔧 .env.prod.example — `.env.prod` с реальными секретами в рабочей копии + отсутствие `.env.prod.example`
**Статус (2026-04-18):** **Ротация ОТКЛОНЕНА** владельцем — файл никогда не был в git, утечки не оценены, риск принят. **Создаём только `.env.prod.example`** в репо для документации (видно какие переменные используются). См. `OWNER-ANSWERS.md` 02-Q-secrets-rotation. Estimate ~30 мин. NEW-20 (CI diff между `.example` и compose), NEW-21 (inline-комментарии), NEW-22 (followup: secrets management в будущем).



- **Затронутые отчёты:** 13 P0-3 + косвенно 08 P0-1 (secret-каналы proto/events).
- **Суть.** `/Users/.../rutcampustrack/.env.prod` лежит на диске с **живыми** секретами: `BOT_TOKEN` (Telegram bot), `GHCR_TOKEN` (PAT на `packages: write`), `VAPID_PRIVATE_KEY`, `BOT_ALERT_TOKEN`, `SWAGGER_PASSWORD`, `MONGO_ROOT_PASSWORD`, `GRAFANA_PASSWORD`, `GRPC_SECRET`, все DB-пароли. Файл в `.gitignore`, но: (а) читается любым инструментом/агентом, работающим в репо (включая нас); (б) при backup/share рабочей копии утечёт; (в) нет шаблона `.env.prod.example` для восстановления (удалён в 2185bec).
- **Что видно в коде.** `.env.prod:10-37`; отсутствие `.env.prod.example` и `.env.example`.
- **Корневой фикс.**
  1. **Ротация всех секретов из файла** (должна быть первой в порядке работ):
     - `BOT_TOKEN` — BotFather: `/revoke` → `/newtoken` (одновременно ротирует `TMA_BOT_TOKEN`, если это один бот — P2-1).
     - `GHCR_TOKEN` — пересоздать PAT + обновить GitHub Secret.
     - `VAPID_PRIVATE_KEY` — `npx web-push generate-vapid-keys`, задеплоить новый public key в PWA manifest, попросить всех пере-подписаться (push-очередь пустая первые сутки).
     - `BOT_ALERT_TOKEN` — BotFather для alert-бота.
     - DB-пароли — `ALTER ROLE ... WITH PASSWORD` в postgres-academic, postgres-schedule, mongo-attendance, redis.
     - `GRAFANA_PASSWORD`, `SWAGGER_PASSWORD` — обновить в `.env.prod` на VPS.
     - `GRPC_SECRET` — новое 32+ байтов, рестарт всех Java-сервисов и бота одновременно.
  2. **Создать `.env.prod.example`** в репо с заглушками (шаблон) и документацией каждой переменной.
  3. **Удалить `.env.prod`** из рабочей копии; держать только на VPS в `/opt/rutcampustrack/.env.prod`.
  4. Добавить в `docs/architecture/architecture.md` раздел «Секреты и их ротация» — какой секрет где используется, как ротировать, кто владеет.
- **Вспомогательные меры.** Pre-commit hook `detect-secrets` + `.secrets.baseline`; CI-check `gitleaks` (13 P1-10 расширение). Image signing + SBOM (13 P2-13) — отдельная работа.
- **Estimate:** 1 человеко-день (ротация — механическая, но последовательная; тесты smoke после ротации).
- **Блокируется:** ничем — делается первым или параллельно с C0-10.
- **Разблокирует:** C0-2 (без ротации `BOT_TOKEN` замена `initial_password`-цепочки на setup-link бессмысленна — старые plaintext-сообщения в TG-истории всё равно работают).

### C0-10: 🔧 TO-FIX через rename + force-renewal — Путь Let's Encrypt сертификата расходится с nginx-конфигом
**Статус (2026-04-18):** переименовать cert-name на `ruttrack.site` + `--force-renewal` + обновить script. ~30 мин downtime для https. См. `OWNER-ANSWERS.md` 02-Q-le-cert. NEW-23 (плановое окно обслуживания + banner). Estimate ~1-2 ч.



- **Затронутые отчёты:** 13 P0-1 — изолированный блокер.
- **Суть.** `nginx/scripts/init-letsencrypt.sh:64,86` выпускает сертификат под `--cert-name rutcampustrack`, а `nginx/conf.d/default.conf:24-25` ждёт его в `/etc/letsencrypt/live/ruttrack.site/`. На работающем VPS сейчас сертификат существует (единожды выпущен вручную), но первый запуск с нуля на новой машине — nginx упадёт на старте.
- **Что видно в коде.** `init-letsencrypt.sh:23,64,86` + `default.conf:22-25`. Hint в скрипте — `"(e.g. rutcampustrack.ru)"` — старый домен.
- **Корневой фикс.**
  1. Убрать `--cert-name rutcampustrack` из `init-letsencrypt.sh` — certbot по дефолту возьмёт `cert-name = first -d domain` (то есть `ruttrack.site`).
  2. Обновить hint в `init-letsencrypt.sh:23` на `"(e.g. ruttrack.site)"`.
  3. Убедиться через `certbot renew --dry-run` в staging-VPS.
  4. Или альтернатива: изменить `default.conf:24-25` на `/etc/letsencrypt/live/rutcampustrack/...` и оставить cert-name — но тогда renew на текущем VPS идёт через старый cert-name, и любая миграция volume `certbot-conf` требует ручного переноса.
- **Вспомогательные меры.** Smoke-тест в `deploy.yml` (уже есть частично, 13 тесты): `curl -sS --fail https://ruttrack.site/login` — расширить до валидации TLS chain.
- **Estimate:** 0.5 человеко-дня (редакт скрипта + dry-run + smoke).
- **Блокируется:** ничем — делается параллельно с C0-9.
- **Разблокирует:** блок «первый деплой с нуля».

---

## P1-кластеры (серьёзные, до релиза)

### C1-1: Три STOMP-клиента в web-panel + дубликаты в PWA → единый NotificationCenter

- **Затронутые отчёты:** 10 P1-5, 10 P1-6, 09 P1-4, 09 P1-6 (reconnect без unsubscribe), 14 P1-9 (нет E2E WebSocket-тестов).
- **Суть.** В web-panel одновременно поднимаются `StudentStompService`, `HeadmanStompService` и `NotificationCenterService` — три сокета на одну вкладку, пересекающиеся подписки `/topic/group/X`, дубликаты событий. В PWA — похожая история: `useStompCheckin` + `NotificationCenter`. Плюс reconnect не `unsubscribe`'ится — после N реконнектов событие приходит N+1 раз.
- **Корневой фикс.** Единый `NotificationCenterService` (уже имеет `onEvent$` raw поток). `StudentStompService.marked$`, `HeadmanStompService.lateCheckinRequested$` — производные через `center.onEvent$.pipe(filter(e => e.type === '...'))`. В `onConnect` сохранять `Subscription`, в `onWebSocketClose` — `unsubscribe` перед новым `subscribe`. `effect()`-зависимость — не `currentUser` (triggers на каждый refresh), а стабильный `userIdentity = {id, groupId, isHeadman}` computed без токена.
- **Estimate:** 2–3 человеко-дня (web-panel + PWA + тесты).
- **Блокируется:** C0-7 (ticket-flow логически ложится на unified client).

### C1-2: Type drift фронт ↔ backend → OpenAPI-генератор

- **Затронутые отчёты:** 08 (все P1 про enum как string и date как string), 09 P2-5, 10 P2-8.
- **Суть.** DTO в `*-api-contract` модулях Java и их TypeScript-зеркала во фронтах живут независимо. Любая переименовка поля ломается в рантайме. Плюс в proto-контрактах даты как `string` без TZ, enum как `string` без типобезопасности (08 P1-1, P1-3).
- **Корневой фикс.**
  1. OpenAPI specs из каждого `*-api-contract` через `springdoc-openapi-maven-plugin` (или Gradle-аналог).
  2. `openapi-generator-cli` → `frontends/shared/api-types/{academic,schedule,attendance,notification,auth}.ts`.
  3. CI-job: build contract-modules → generate OpenAPI → генерировать TS → diff против committed → fail при расхождении.
  4. В `.proto`: `string` времени → `google.protobuf.Timestamp`; `string` enum → proto enum (`LessonStatus`, `UserRole`).
- **Estimate:** 4–5 человеко-дней.
- **Блокируется:** C1-4 (coverage-gate) — часть пайплайна.

### C1-3: `:latest` теги в prod-compose → `IMAGE_TAG=${sha}`

- **Затронутые отчёты:** 13 P1-1, 13 P1-4 (mini-app без SHA-тега — симптом того же).
- **Суть.** `docker-compose.prod.yml` держит `image: ghcr.io/.../*-service:latest`. `deploy.yml` делает `docker compose pull` — всегда подтягивает последний `latest`. Откат на старый SHA — только ручной `docker pull` + `tag`.
- **Корневой фикс.** Параметризовать prod-compose на `${IMAGE_TAG:-latest}`. В `deploy.yml` — `echo "IMAGE_TAG=${{ github.sha }}" >> /opt/rutcampustrack/.env.prod` перед `docker compose up`. Хранить `.deployed-sha` для откатов. Починить mini-app тег симметрично.
- **Estimate:** 1 человеко-день.
- **Блокируется:** C0-8 (CI-gate).

### C1-4: Coverage-gate в CI (JaCoCo + vitest + pytest-cov)

- **Затронутые отчёты:** 14 P1-3, 13 P2-4, косвенно все 14 P0/P1 (latecheckin=0%, callback_query=0%).
- **Суть.** Нет ни JaCoCo, ни Istanbul, ни `pytest-cov` в CI. Новая фаза может залить код без тестов незамеченной.
- **Корневой фикс.**
  1. `build.gradle.kts` root — apply jacoco plugin ко всем `*-app`, `jacocoTestReport` в `check.dependsOn`.
  2. `jacoco-report-aggregation` — единый отчёт.
  3. CI — `madrapps/jacoco-report@v1` с минимальным порогом 60% (начать с низкого и поднимать).
  4. Frontend — `vitest run --coverage` с `thresholds.lines: 60`.
  5. Python — `pytest --cov=bot --cov-fail-under=70 tests/`.
- **Estimate:** 2 человеко-дня.
- **Блокируется:** C0-8.

### C1-5: Contract-тесты RabbitMQ событий (сейчас только excuse)

- **Затронутые отчёты:** 14 P1-5, 08 P1-2 (`additionalProperties: false`), 08 P0-2 (нет схемы `otp.requested`).
- **Суть.** Единственный contract-тест `ExcuseEventContractIT` сверяет payload с JSON-schema. Остальные 14+ событий (`lesson.*`, `attendance.marked`, `homework.*`, `group.*`, `otp.verified`, `latecheckin.*`) — без проверки. Любое изменение поля = прод-инцидент.
- **Корневой фикс.**
  1. Параметризовать `ExcuseEventContractIT` → `EventContractIT` с матрицей `(publisher, event_type, schema)`.
  2. Добавить `additionalProperties: false` во все 19 JSON-схем.
  3. Создать `event-schemas/otp.requested.json` (см. шаблон в 08 P0-2).
  4. CI — отдельный job `contract-tests`, required check.
- **Estimate:** 3 человеко-дня.
- **Блокируется:** C0-3 (outbox) — contract-тест имеет смысл только если события надёжно доходят; C0-8.

### C1-6: Base images не pin'нутся по digest

- **Затронутые отчёты:** 13 P1-10, 13 P2-2.
- **Суть.** `grafana/loki:latest`, `prom/prometheus:latest`, `cadvisor:latest` (с `privileged: true`), `grafana/promtail:latest` (с `docker.sock`) — supply-chain risk. Компрометация любого = root на хост.
- **Корневой фикс.** Pin observability на `@sha256:` digest. Подключить Renovate/Dependabot для `docker-compose.prod.yml` и Dockerfile. Image signing + SBOM (13 P2-13) как дополнение.
- **Estimate:** 1 человеко-день.

### C1-7: `@Scheduled` без ShedLock → double-publish

- **Затронутые отчёты:** 03 P0-4, 04 P0-6 (`AttendanceIndexInitializer.cleanupOrphans` без distributed lock — симметрично).
- **Суть.** `LessonStatusTransitionJob` в schedule-service запускается каждые 60 сек на каждом инстансе. Сейчас инстанс один, но как только HA / blue-green — двойная публикация `lesson.started` / `lesson.closed`, двойные push, двойной `attendance.session.closed`.
- **Корневой фикс.** `net.javacrumbs.shedlock:shedlock-spring` + `shedlock-provider-jdbc-template`, таблица `shedlock`, `@SchedulerLock(name="...")` на `runTransitions`, `cleanupOrphans`, `outboxSender` (C0-3). Или `SELECT ... FOR UPDATE SKIP LOCKED` в find-query.
- **Estimate:** 1.5 человеко-дня.
- **Блокируется:** C0-3 (outbox sender сам нуждается в ShedLock).

### C1-8: Лендинг рассинхронизирован с v9.0

- **Затронутые отчёты:** 12 P1-6 (excuse-тикеты описаны по старому TG-flow), 12 P1-1 (OG/Twitter), 12 P1-2 (robots/canonical).
- **Суть.** v9.0 перенёс excuse-тикеты на backend (Phase 59), лендинг остался с описанием старого TG-flow. Плюс отсутствие `og:image`, `twitter:card`, `robots`, `canonical` — превью в соцсетях и SEO страдают.
- **Корневой фикс.**
  1. Переписать карточку excuse под v9.0 (PWA + web-panel для старосты).
  2. Нарисовать `og-image.png` 1200×630, добавить OG/Twitter meta.
  3. Добавить `robots.txt` + `sitemap.xml` + `<link rel="canonical">`.
  4. **Процесс**: добавить в шаблон phase-отчёта строку «`docs/phase-N-report.md`: ревизия лендинга требуется? да/нет» — закрывает причину рассинхрона.
- **Estimate:** 1 человеко-день (контент) + 0.5 (процесс).
- **Блокируется:** C0-6 (имеет смысл после того, как лендинг вообще рендерится).

### C1-9: `latecheckin/` + callback_query бота — 0% сети безопасности

- **Затронутые отчёты:** 04 P0-5 и 14 P0-1 (latecheckin — 0 тестов при 6 source-классах), 06 P0-5 и 14 P0-2 (callback_query excuse/late_checkin/prefs — 0 unit-тестов).
- **Суть.** Два критичных кода живут в проде без теста: полный `latecheckin/` домен в attendance-service и все callback_query-хендлеры бота (approve/reject excuse-тикетов и late-checkin'ов). Одновременно callback_query **не проверяет, что нажавший — староста** (06 P0-5): любой с угаданным `callback_data=ex:approve:<uuid>` может принять чужой тикет.
- **Корневой фикс.**
  1. `LateCheckinServiceTest` (unit): FSM-переходы, IDOR (староста не из той группы).
  2. `LateCheckinControllerIT` (Testcontainers + MockMvc): request → approve → mark.
  3. `LateCheckinEventPublisherTest` + contract-тест `latecheckin.requested/decided` (в C1-5).
  4. `tests/test_excuse_callback_handler.py`: headman → `gateway.approveExcuse` вызван; student → `callback.answer("Недостаточно прав")`; invalid callback_data → graceful; double-click → защита.
  5. В bot: перед `event_publisher.publish` — `academic_client.get_user_by_telegram_id(callback.from_user.id)` → `is_headman and group_id == ticket.group_id` → иначе отказ. Тест на это добавляется одновременно.
- **Estimate:** 3 человеко-дня.
- **Блокируется:** C1-4 (coverage-gate выявит и зафиксирует gaps).

### C1-10: DEBUG-логи в default-конфигах + JWT в query

- **Затронутые отчёты:** 01 P2-1 (auth), 02 P2 (academic `application-prod.yml` только actuator), 03 P1-5 (schedule prod явно DEBUG — **ухудшение**, не улучшение), 05 P1-2 (push `endpoint` в INFO), 07 P1-4 (Gateway DEBUG → JWT в логах), 13 P2 (CI поднимает SPRING_PROFILES_ACTIVE в prod, но не fail-fast).
- **Суть.** `application.yml` по-дефолту `ru.rutcampustrack: DEBUG` + `org.hibernate.SQL: DEBUG` (schedule). Prod переопределение работает только если `SPRING_PROFILES_ACTIVE=prod` явно выставлен. Gateway DEBUG пишет полные URL с `?token=...` в логи. Web Push endpoint (секретный URL) попадает в INFO-лог на 410 cleanup.
- **Корневой фикс.**
  1. Default в `application.yml` — `INFO`. DEBUG выносится в `application-dev.yml`.
  2. Fail-fast при пустом `SPRING_PROFILES_ACTIVE` в prod-образе (ENV `SPRING_PROFILES_ACTIVE=prod` в Dockerfile или fail в `@Configuration`).
  3. Logback-паттерн маскирует `?token=` / `?ticket=` в URL.
  4. `WebPushDeliveryService` логирует hash endpoint'а, не endpoint.
  5. `logging.level.org.hibernate.SQL` — `WARN` во всех prod-профилях.
- **Estimate:** 1 человеко-день.

### C1-11: Нет единого `@ControllerAdvice` / RFC 7807 (notification-service)

- **Затронутые отчёты:** 05 P0-2 — `notification-service` не имеет `GlobalExceptionHandler`, `AccessDeniedException` возвращается 500 без RFC 7807; 14 соответствие (`@ControllerAdvice централизация` помечено ✅ везде, кроме notification).
- **Суть.** Единственный Java-сервис без `@RestControllerAdvice`. `RoleCheckAspect` кидает `AccessDeniedException` → 500 InternalServerError. `SubscriptionAuthInterceptor.preSend` кидает `IllegalArgumentException` в STOMP → ERROR frame → клиент рвёт соединение (05 P1-4).
- **Корневой фикс.** Добавить `GlobalExceptionHandler` в `notification-app` (скопировать из academic/schedule/attendance), вынести `ErrorResponse` record в `notification-api-contract/exception/`. `SubscriptionAuthInterceptor.preSend` — возвращать `null` (тихо дропнуть SUBSCRIBE) вместо throw.
- **Estimate:** 0.5 человеко-дня.

---

## Порядок исполнения

Правильная последовательность — от инфраструктурных и изолированных к поверхностным и комбинируемым:

1. **C0-9 (ротация секретов)** — first. Любая утечка `.env.prod` = компрометация; фиксится независимо от всего и быстро.
2. **C0-10 (cert-name LE)** — изолированный блокер первого деплоя с нуля. Чинится за полдня параллельно с C0-9.
3. **C0-8 (CI → deploy gate)** — до любых backend-правок, чтобы они не попадали в прод сломанными. Включает branch protection + `workflow_run`. Одновременно — C1-4 coverage-gate заготавливается.
4. **C0-2 (initial_password-цепочка)** — изолированный по сервисам, критичный по безопасности. Нужна ротация `BOT_TOKEN` (сделано в C0-9), чтобы старые TG-сообщения с паролем в истории не оставались действительными для *нового* бота.
5. **C0-3 (outbox для AFTER_COMMIT)** — фундамент для надёжности событий. На нём строятся C1-5 (contract-тесты событий) и C1-7 (ShedLock).
6. **C0-1 (UserContextFilter)** — требует C0-8 (CI-gate) и ставится до C0-4 (иначе rate-limit можно обойти минуя Gateway).
7. **C0-4 (rate-limit в nginx)** — после C0-8 и C0-1.
8. **C0-5 (logout lifecycle)** — фронтовый фикс, ставится до C0-7, чтобы при переключении на cookie-based auth уже был общий `clearAllClientState`.
9. **C0-6 (CSP self-host лендинга)** — инфра/фронт, параллельно с C0-5.
10. **C0-7 (JWT HttpOnly cookie + ws-ticket)** — самый тяжёлый фикс, меняет auth-flow целиком. Ставится последним из P0, когда всё остальное стабильно.
11. **Далее — P1-кластеры** в порядке `C1-4 → C1-1 → C1-2 → C1-5 → C1-7 → C1-3 → C1-6 → C1-9 → C1-10 → C1-11 → C1-8`.

**Обоснование порядка.**
- Ротация секретов (C0-9) не блокируется ничем: отсрочка = рост риска.
- Сначала CI-gate (C0-8), потом всё остальное: 10+ следующих PR'ов будут проходить через него.
- Outbox (C0-3) ставится до C0-1: без надёжных событий дополнительный RBAC в ролевом контексте не даёт выигрыша в связности системы.
- JWT cookie (C0-7) — последний из P0: требует координации backend + двух фронтов; до этого фикса все prерhead-вещи должны быть стабильны.
- Внутри P1 — сначала `C1-4` (coverage-gate) как инструмент, потом рефакторинги (`C1-1`, `C1-2`), потом contract-тесты (`C1-5`), потом масштабирование (`C1-7`), потом деплой-инфра (`C1-3`, `C1-6`), потом сети безопасности (`C1-9`, `C1-10`, `C1-11`), последним — лендинг (`C1-8`).

---

## Метрики после фиксов

### Сколько P0 закроется при выполнении C0-1..C0-10

Грубая оценка (с перекрытием кластеров и реальных тикетов из отчётов):

| Кластер | Закрывает P0 | Комментарий |
|---------|--------------|-------------|
| C0-1 | 02 P0-2, 03 P0-1, 04 P0-1, 05 P0-1, 07 P0-2 (валидация headers между gateway и downstream как дополнение) — **5 P0** | +04 P0-4 (group-id в defense-in-depth), 02 P0-3, 03 P0-3, 04 P0-1 парсинг (P0/P1-зависит от формулировки) |
| C0-2 | 01 P0-2, 02 P0-1, 06 P0-3, 08 P0-1 — **4 P0** | +10 P2-13 (admin UI) |
| C0-3 | 02 P0-6, 03 P0-2, 04 P0-5 (consumer DLQ) — **3 P0** | +06 P1-7 |
| C0-4 | 01 P0-6, 07 P0-2 (подкейс `/api/auth/otp/**`) — **2 P0** | +01 P1-2, 07 P1-2, 13 P1-3 |
| C0-5 | 09 P0-4, 09 P0-5, 10 P0-4 — **3 P0** | +10 P1-16 |
| C0-6 | 12 P0-1, 13 P0-4 — **2 P0** | +09 P1-3, 10 P0-3, 12 P1-4 |
| C0-7 | 09 P0-1, 09 P0-2, 10 P0-1, 10 P0-2 — **4 P0** | +07 P1-4 (DEBUG logs token), 09 P1-1 |
| C0-8 | 13 P0-2 — **1 P0** | +14 P1-8, открывает C1-4 |
| C0-9 | 13 P0-3 — **1 P0** | +ротация снимает часть supply-chain |
| C0-10 | 13 P0-1 — **1 P0** | изолированный |

**Суммарно P0, закрываемых P0-кластерами: ~26 прямых P0** (из 53). Остальные P0 — это более локальные вещи (NotificationService без `@ControllerAdvice` — C1-11, auth-service `verify-by-code` rate-limit — C0-4, `otp.requested` schema — C1-5/C0-2 связаны, и т. д.). С учётом перекрытий P1 **в сумме закрывается ~35 P0** — остаток (~18 P0) это специфичные для сервисов проблемы (например, 02 P0-4 race `activateSemester`, 02 P0-7 N+1 `listHomeworks`, 03 P0-5 week-parity drift, 04 P0-2 координаты не сохраняются, 04 P0-6 mass-delete orphans, 05 P0-3 push_subscriptions в attendance_db, 05 P0-4 whitelist destinations, 05 P0-5 JWT audit, 06 P0-1 insecure_channel, 06 P0-2 `placeholder` token, 08 P0-2 `otp.requested` schema, 10 P2-13 admin UI). Они переходят в **v0.0.0 individual fix list** — каждый закрывается точечным PR.

### Сколько P1 закроется при выполнении C1-1..C1-11

| Кластер | Закрывает P1 | Комментарий |
|---------|--------------|-------------|
| C1-1 | 10 P1-5, 10 P1-6, 09 P1-4, 09 P1-6 — **4 P1** | +14 P1-9 |
| C1-2 | 08 P1-1, 08 P1-3, 09 P2-5, 10 P2-8 — **2 P1 + 2 P2** | |
| C1-3 | 13 P1-1, 13 P1-4 — **2 P1** | |
| C1-4 | 14 P1-3, 13 P2-4 — **1 P1 + 1 P2** | открывает видимость остальных |
| C1-5 | 14 P1-5, 08 P1-2 — **2 P1** | закрывает gaps в contract-тестах |
| C1-6 | 13 P1-10, 13 P2-2 — **1 P1 + 1 P2** | |
| C1-7 | 03 P0-4 (уже в C1-7 как симметричный), 04 P0-6 (подкейс) — **1 P1** | |
| C1-8 | 12 P1-1, 12 P1-2, 12 P1-6 — **3 P1** | |
| C1-9 | 14 P0-1, 14 P0-2 (совместно с C1-4) — **0 чистых P1**, закрывает P0 из 14 | |
| C1-10 | 01 P2-1, 03 P1-5, 05 P1-2, 07 P1-4 — **2 P1 + 2 P2** | |
| C1-11 | 05 P0-2 — закрывает P0 из 05, не P1 | |

**Суммарно P1, закрываемых P1-кластерами: ~18 прямых P1** (из 136). Это ключевые «сквозные» P1. Остаток (~118 P1) — это более мелкие архитектурные долги и специфика (HomeworkService N+1, `headman_alerts` файл в RabbitMQ, etc.), каждый требует отдельного PR.

### Остаточные P2/P3

Большинство P2/P3 из 01–14 — это точечные рефакторинги и nit (именование тестов, cleanup в `@AfterEach`, дубликаты `cn()` в PWA, `version: "3.9"` в compose). Они **не блокируют релиз v0.0.0**; они уходят в **v0.1 backlog** и разбираются в фоне. Исключения: P2-шки, входящие в кластеры (например 12 P2-6 robots file — входит в C1-8).

### Целевое состояние после всех кластерных фиксов

- **Блокеры релиза v0.0.0 (≈26 прямых P0 + C1-9/C1-11 побочно = ~30 P0):** закрыты.
- **Остаточные ~18 P0:** точечные фиксы; 2–3 человеко-дня суммарно на оставшиеся critical (02 P0-4, 02 P0-7, 03 P0-5, 04 P0-2, 05 P0-3/4/5, 06 P0-1/2, 10 admin UI).
- **P1 ~118 штук:** в 2-3 спринтах v0.1.
- **P2/P3 ~275 штук:** фоновые задачи.

---

## Процессные (мета) фиксы

Эти пункты — не конкретные кластеры, а **изменения процесса**, без которых технические фиксы сгниют за следующий релизный цикл:

1. **Branch protection на `main`** (часть C0-8): `required_status_checks: ci, coverage-gate, contract-tests`. Также `require pull request reviews` (сейчас, видимо, нет — дедлайны разработчика одновременно author & reviewer).
2. **Регулярный supply-chain scan** (13 P1-10, 14):
   - `trivy` в CI-стадии для каждого Dockerfile;
   - `gitleaks` pre-commit + CI-job;
   - `npm audit --audit-level=high` в frontend-сборках;
   - `pip-audit` в Python-боте;
   - `gradle dependencyCheck` в Java.
3. **Renovate/Dependabot** для `docker-compose.prod.yml` + Dockerfile + `package.json` + `requirements.txt` + `build.gradle.kts` (часть C1-6).
4. **Процесс «ревизия лендинга при изменении бизнес-логики»** (12 P1-6 → C1-8): в шаблон `docs/phase-N-report.md` добавить обязательный блок «Влияние на landing: <да/нет + что менять>». Без этого пункта любое business-change снова рассинхронизирует маркетинг.
5. **Ротация секретов — раз в квартал** (C0-9): chron-задача в команде, `docs/architecture/architecture.md` → список + последняя ротация.
6. **Release checklist v0.0.0** — внешний документ, где отмечается каждый кластер как «closed» перед публичным анонсом.
7. **Observability retention** (13 P2-9): Loki 7 дней недостаточно для инцидент-разбора; поднять до 30–45 дней, это не технический, а политический фикс (диск VPS).
8. **Load-тест** (14 P2-8): хотя бы 1 прогон k6 перед релизом (login → OTP → геоотметка → schedule). Без него не знаем реальный RPS-профиль.

---

## Вопросы к владельцу проекта

1. **Порядок P0-кластеров.** Согласны с последовательностью C0-9 → C0-10 → C0-8 → C0-2 → C0-3 → C0-1 → C0-4 → C0-5 → C0-6 → C0-7? Если v0.0.0 планируется уже в ближайшие 2 недели — реально ли провести все 10 (≈30–40 человеко-дней для одного разработчика)?
2. ✅ **C0-2 (`initial_password`)** — выбираем «magic-link setup» или «разовый пароль в response при create, никогда больше»? Первый безопаснее, второй — меньше работ на бот/фронт.
   → **AUTO-RESOLVED (2026-04-18)**: ни то, ни другое — **(a) accept tradeoff**. Кластер C0-2 распущен. См. `OWNER-ANSWERS.md` 15-Q2.
3. ✅ **C0-3 (outbox)** — Debezium/CDC-пайплайн или in-app table + scheduled sender? Первый — «правильнее», требует Kafka/Debezium; второй — 1 день работы без новой инфры. Рекомендую второй.
   → **AUTO-RESOLVED через 02-Q3 (2026-04-18)**: выбран **(b) In-app outbox table** во всех 3 сервисах + publisher-job (~5 сек). См. `OWNER-ANSWERS.md` 02-Q3.
4. ✅ **C0-4 (rate-limit)** — в nginx (дешевле, быстрее) или в Spring Cloud Gateway с Redis (правильнее для бизнес-логики, но добавляет Redis к Gateway)? Рекомендую nginx на v0.0.0, Gateway — в v0.1.
   → **AUTO-RESOLVED через 02-Q-rate-limit (2026-04-18)**: выбран **Spring Cloud Gateway + Redis** (вопреки моей рекомендации nginx — владелец предпочёл архитектурно чистый вариант). См. `OWNER-ANSWERS.md` 02-Q-rate-limit.
5. ✅ **C0-6 (CSP)** — self-host CDN (больше bundle, меньше зависимостей от чужих серверов) или whitelist + SRI (меньше bundle, больше CSP-фиддлинга)? Рекомендую self-host.
   → **AUTO-RESOLVED через 02-Q-csp-landing (2026-04-18)**: **(a) Self-host**. См. `OWNER-ANSWERS.md` 02-Q-csp-landing.
6. ✅ **C0-7 (JWT cookie)** — принимаем breaking change для всех клиентов (PWA, web-panel, mini-app) в одном релизе, или делаем дубль endpoint'ов `/auth/refresh` и `/auth/refresh-cookie` для плавной миграции?
   → **AUTO-RESOLVED через 02-Q-frontend-security (2026-04-18)**: **breaking change без двойных endpoint'ов**. `/auth/refresh` меняет поведение (тело → cookie). Все клиенты обновляются одним релизом. См. `OWNER-ANSWERS.md` 02-Q-frontend-security. Migration runbook (NEW-13) обязателен.
7. ✅ **C0-8 (branch protection)** — готовы ли к тому, что каждый PR требует явного approve (даже если author = единственный разработчик)? Если нет — только `required_status_checks` без `require reviews`.
   → **AUTO-RESOLVED через 02-Q-ci-deploy-gate (2026-04-18)**: **только `required_status_checks` без `require reviews`** + `workflow_run` в `deploy.yml`. См. `OWNER-ANSWERS.md` 02-Q-ci-deploy-gate.
8. ✅ **C0-9 (ротация секретов)** — запланирован ли коридор обслуживания (~30 мин downtime всех сервисов для ротации `GRPC_SECRET` и DB-паролей)?
   → **AUTO-RESOLVED через 02-Q-secrets-rotation (2026-04-18)**: ротация **НЕ делается** (файл не утекал). Только создание `.env.prod.example`. Downtime не нужен. См. `OWNER-ANSWERS.md` 02-Q-secrets-rotation.
9. **C1-4 (coverage-gate)** — порог 60%/70% по lines? Для Python-бота рекомендую 70% (меньше кода, проще), для Java — начать с 50% (много integration, часть не ложится на lines).
10. **C1-5 (contract-тесты событий)** — параметризованный `EventContractIT` (один файл, матрица) или 15 отдельных файлов? Первое — чище, второе — проще дебажить.
11. **C1-7 (ShedLock)** — PostgreSQL-provider (есть `schedule_db`) или Redis-provider (уже везде есть)? Оба работают; Redis — меньше миграций.
12. **C1-8 (процесс ревизии лендинга)** — включить проверку в `gsd-verifier` или оставить ручным пунктом в phase-отчёте?
13. **Остаточные P0 после кластеров** — разобрать в том же v0.0.0 (задержка релиза на 3–4 дня) или выпустить с известным списком и закрыть в v0.0.1?
