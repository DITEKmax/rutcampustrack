# 05. Notification Service (Java / notification-web) — отчёт аудита

## Сводка

Notification Service (Java-контейнер, логическое имя `notification-web`, порт 9094) — самый маленький бизнес-сервис в монорепо: 2 модуля (`notification-api-contract` + `notification-app`), 31 Java-файл на main + 7 тестовых классов. Он реализует три канала доставки уведомлений клиентам: (1) **STOMP WebSocket** с ручной JWT-валидацией на handshake и per-SUBSCRIBE авторизацией топиков; (2) **Web Push (VAPID)** через библиотеку `nl.martijndwars:web-push:5.1.2`, подписки хранятся в MongoDB (коллекция `push_subscriptions` внутри чужой БД `attendance_db`); (3) fanout из **RabbitMQ** (`rut-uit.events`) — единственный источник событий, push и STOMP идут из одного консьюмера. Своей БД и своего Redis у сервиса нет — это стейтлесс event-forwarder, что соответствует CLAUDE.md.

Контракт-first соблюдён: `notification-api-contract` — чистый `java-library` без Spring Boot и без Lombok, `PushApi` с HATEOAS (`EntityModel<VapidPublicKeyResponse>`), Request DTO = `record`, Response = `class extends RepresentationModel`. Контроллер `PushController` implements `PushApi`, маппинги только в интерфейсе. AOP-аспект `RoleCheckAspect` + `@RequireRole` повторяют паттерн attendance-service. Но список проблем длиннее, чем кажется:

1. **P0 — UserContextFilter слепо доверяет `X-User-*` заголовкам без проверки, что запрос реально пришёл от Gateway.** Идентично другим сервисам, вскрытия Gateway достаточно, чтобы выдать себя за любого.
2. **P0 — нет `@ControllerAdvice` вообще.** Кастомный `AccessDeniedException` бросается аспектом, но ни `@ExceptionHandler`, ни RFC 7807 `ErrorResponse` нет — клиент получит 500 InternalServerError, а не 403 Problem Details. CLAUDE.md требует RFC 7807.
3. **P0 — Web Push подписки хранятся в `attendance_db`, в коллекции `push_subscriptions`** (не в своей БД). Архитектурный документ описывает «Notification Web → MongoDB push_subscriptions», но mongoURI прописан как `mongodb://...@mongo-attendance:27017/attendance_db?authSource=admin` и в dev, и в prod. Это явное нарушение Database-per-Service и создаёт скрытую связанность между Attendance и Notification.
4. **P1 — три напоминания об отметке (начало, середина, конец пары) из CLAUDE.md / phases-plan.md не реализованы в Java-контейнере.** По плану это обязанность notification-bot, но в Web Push канале этой логики тоже нет: есть только реактивное `lesson.started` → push «Пара началась». Середины и конца нет, как и reminder-cleanup.
5. **P1 — `SubscriptionAuthInterceptor.preSend` возвращает исключение напрямую из ChannelInterceptor без `@MessageExceptionHandler` + без RFC 7807 формата.** Это генерирует STOMP ERROR frame с текстом «Unauthorized subscription», клиент разрывает соединение вместо тихого отказа от одной подписки.
6. **P1 — логи уровня DEBUG в `application.yml` (dev), плюс DEBUG-строки с `endpoint` в production** — в `WebPushDeliveryService.log.debug("Push sent to {} for event {}", sub.getEndpoint(), eventType)` попадает push endpoint, который является URL с уникальным токеном (на FCM это и есть секрет доставки).
7. **P2 — прод-профиль не переопределяет `allowed-origins`** корректно: WebSocketConfig читает `${notification.ws.allowed-origins:...}` с дефолтом, в котором уже есть `http://localhost:*`; в prod переменная `NOTIFICATION_WS_ALLOWED_ORIGINS` задаётся только в `docker-compose.prod.yml`, но Spring-биндинг идёт через точечный `notification.ws.allowed-origins`, а env-имя `NOTIFICATION_WS_ALLOWED_ORIGINS` совпадает по relaxed-binding. Всё-таки лучше сделать явно.

**Счётчики:** **P0=5, P1=9, P2=10, P3=7**.

Тестов 7 классов, ~40 тестов: JWT handshake, RabbitMQ-конфиг, EventConsumer routing, GroupEvent, PushController, PushSubscriptionRepository (структурно), WebPushDeliveryService, SecurityInfrastructure. Нет ни одного end-to-end теста WebSocket (клиент-подключение + subscribe + receive), нет ни одного теста `SubscriptionAuthInterceptor`. MongoDB тесты «структурные» через рефлексию — Testcontainers MongoDB не задействован.

---

## Структура модулей

### `notification-api-contract` (`java-library`)

```
notification-api-contract/
└── src/main/java/ru/rutcampustrack/notification/contract/
    ├── api/PushApi.java                          — @RequestMapping("/push"), 3 эндпоинта
    ├── dto/push/
    │   ├── SubscribeRequest.java                  — record, @NotBlank endpoint + Keys
    │   ├── UnsubscribeRequest.java                — record, @NotBlank endpoint
    │   └── VapidPublicKeyResponse.java            — class extends RepresentationModel (HATEOAS)
    └── enums/UserRole.java                        — STUDENT, TEACHER, ADMIN
```

- Нет Spring Boot, нет Lombok ✅
- Есть `spring-web`, `spring-hateoas`, `swagger-annotations`, `jackson-annotations`, `jakarta.validation-api` — используется в интерфейсах ✅
- **Замечание P3**: в контракте только `PushApi`, нет интерфейса для WebSocket-URL `/ws` (понятно: это не REST), но нет и `ErrorResponse` record (в академик/schedule/attendance он есть в контракте). Notification на ошибки отвечает голым 500.

### `notification-app` (Spring Boot)

```
notification-app/src/main/java/ru/rutcampustrack/notification/
├── NotificationWebApplication.java               — @SpringBootApplication
├── config/
│   ├── AsyncConfig.java                          — ThreadPoolTaskExecutor "pushTaskExecutor"
│   ├── JwtHandshakeInterceptor.java              — ручная JWT валидация при WS upgrade
│   ├── PushMongoConfig.java                      — индексы MongoDB через @PostConstruct
│   ├── RabbitConfig.java                         — fanout exchange + DLQ
│   ├── SubscriptionAuthInterceptor.java          — STOMP ChannelInterceptor
│   ├── WebPushConfig.java                        — VAPID keys из ENV
│   └── WebSocketConfig.java                      — @EnableWebSocketMessageBroker
├── event/
│   └── EventConsumer.java                        — @RabbitListener на "notification-web.events"
├── push/
│   ├── PushController.java                       — implements PushApi, @RequireRole(STUDENT)
│   ├── PushSubscriptionDocument.java             — @Document(collection = "push_subscriptions")
│   ├── PushSubscriptionRepository.java           — MongoRepository
│   └── WebPushDeliveryService.java               — @Async delivery, 410 auto-cleanup
├── security/
│   ├── RequestContext.java                       — @Scope(request, TARGET_CLASS)
│   ├── RequireRole.java                          — annotation
│   ├── RoleCheckAspect.java                      — @Around
│   └── UserContextFilter.java                    — читает X-User-* → RequestContext
└── exception/
    └── AccessDeniedException.java                — RuntimeException, бросается аспектом
```

Зависимости: Spring Boot Web (через HATEOAS)/AMQP/Data-MongoDB/AOP/WebSocket/Actuator, jjwt 0.12.6, web-push 5.1.2 + bouncycastle 1.70 + jose4j 0.7.9, BouncyCastle signed-jar fix (`BootJar.loaderImplementation = CLASSIC`).

**Порт:** 9094. **Gateway-префиксы:** `/api/ws/**` (включая SockJS fallback `/api/ws/info`) и `/api/push/**` маршрутизируются на `http://notification-web:9094` с `StripPrefix=1`.

---

## Критичные проблемы (P0)

### P0-1: 🔧 TO-FIX через Internal JWT — UserContextFilter слепо доверяет `X-User-Id` / `X-User-Role` без проверки, что запрос пришёл от Gateway
**Статус (2026-04-18):** будет закрыто фиксом из C0-1 (Internal JWT). См. `OWNER-ANSWERS.md` 02-Q2.



- **Где:** `services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/security/UserContextFilter.java:34-43`
- **Что:** Фильтр читает заголовки `X-User-Id`, `X-User-Role`, `X-Group-Id`, `X-Is-Headman` и складывает их в `RequestContext` без какой-либо аутентификации. Сервис слушает `:9094` внутри docker-network, но если попасть в private_net (скомпрометированный соседний сервис) или если кто-то ошибётся в nginx-правилах, POST `/push/subscribe` с любыми `X-User-Id: 999` + `X-User-Role: STUDENT` + `X-Group-Id: 1` создаст подписку от имени произвольного пользователя.
- **Риск:** Ровно тот же сценарий, что в академик/schedule/attendance. Дополнительный нюанс: push endpoint — это секретный URL, выданный провайдером push-сервиса клиенту. Если атакующий создаст подписку с собственными `endpoint` + `p256dh` + `auth`, привязанную к `user_id` жертвы, то все уведомления, адресованные жертве (включая excuse-тикеты с персональными данными «Иванов И.И., лаб.работа 3 пропущена»), начнут приходить атакующему.
- **Как чинить:** Как и в других сервисах — либо shared-secret между Gateway и backend (см. `GrpcSecretClientInterceptor` в attendance, там уже реализовано для gRPC; нужен HTTP-аналог), либо mTLS, либо явная проверка, что request идёт с IP Gateway. Минимум: вернуть 401, если `X-User-Id` заголовок отсутствует (сейчас — просто пропускает дальше, `RequestContext` остаётся null, `@RequireRole` кидает `AccessDeniedException`, но нет явного 401).
- **Зависимости:** общая проблема по всем Java-сервисам. Фикс должен быть унифицированным на уровне gateway filter config.

### P0-2: Нет `@ControllerAdvice` / `GlobalExceptionHandler` — ошибки возвращаются как 500 без RFC 7807

- **Где:** Отсутствует файл `exception/GlobalExceptionHandler.java`. `AccessDeniedException` (`exception/AccessDeniedException.java`) наследует `RuntimeException` и нигде не перехватывается.
- **Что:** `RoleCheckAspect.checkRole` кидает `AccessDeniedException`. Spring Boot по умолчанию вернёт 500 Internal Server Error с дефолтным `ErrorResponse` (или whitelabel page) — клиент не поймёт, что это 403 / unauthorised role. Аналогично для `SubscriptionAuthInterceptor.preSend`, который кидает `IllegalArgumentException("Unauthorized subscription")` — это летит в STOMP ERROR frame без RFC 7807 полей.
- **Риск:** Нарушение CLAUDE.md ("Ошибки: RFC 7807 Problem Details — `ErrorResponse` record"), клиенты на PWA/web-panel парсят ответ на ошибку (title/detail/status) — ни одного из этих полей не будет. Также утечка stacktrace в ответе при DEBUG (см. P1-3).
- **Как чинить:** Добавить `@RestControllerAdvice` с обработчиками `AccessDeniedException` → 403 Problem Details, `MethodArgumentNotValidException` → 400 Problem Details с validation details, `IllegalArgumentException` (из SubscriptionAuthInterceptor) → 401 Problem Details. `ErrorResponse` record вынести в `notification-api-contract/exception/`.
- **Зависимости:** нет, локальная правка.

### P0-3: Push subscriptions хранятся в чужой БД (`attendance_db`) — нарушение Database-per-Service

- **Где:** `application.yml:14-16` (`spring.data.mongodb.uri: mongodb://.../attendance_db`), `docker-compose.yml:119` (`SPRING_DATA_MONGODB_URI: mongodb://...@mongo-attendance:27017/attendance_db?authSource=admin`), `docker-compose.prod.yml:264` — та же строка.
- **Что:** Коллекция `push_subscriptions` живёт в БД `attendance_db`, которой владеет Attendance Service. Это прямое нарушение принципа **Database per Service** из `docs/architecture.md` §1 и §6. В архитектурном документе написано, что Notification Web имеет свою MongoDB `push_subscriptions` (см. матрицу «сервис → хранилище», строка Notification Web, столбец MongoDB `✅ (push_subscriptions)`).
- **Риск:** (1) Attendance-миграция/бэкап/restore может затронуть notification-данные и наоборот. (2) При выносе сервиса в отдельный контейнер с отдельным MongoDB (что явно задумано архитектурой) придётся отдельно мигрировать коллекцию. (3) При падении `mongo-attendance` notification-web не может принимать subscribe/unsubscribe, хотя по идее это независимый домен. (4) Нет изоляции credentials — один и тот же юзер MongoDB имеет полный доступ и к `attendances`, и к `push_subscriptions`.
- **Как чинить:** Создать отдельный контейнер `mongo-notifications` (или отдельную БД `notifications_db` в том же кластере) и переключить URI. В `docker-compose.yml` + `docker-compose.prod.yml` задеплоить новый volume. Миграция данных: `mongodump --db attendance_db --collection push_subscriptions` → `mongorestore --db notifications_db`.
- **Зависимости:** требует координации с attendance-service (он не должен случайно читать `push_subscriptions`); на уровне кода attendance не читает, но гарантии нет.

### P0-4: `SubscriptionAuthInterceptor` не проверяет, что destination принадлежит именно запрашивающему пользователю — IDOR по чужим /user-destinations

- **Где:** `config/SubscriptionAuthInterceptor.java:26-76`
- **Что:** Паттерн `GROUP_TOPIC = ^/topic/group/(\d+)(/headman)?$` ловит только `/topic/group/{id}` и `/topic/group/{id}/headman`. Но если фронтенд (или злой клиент) подпишется на что-то вне этого паттерна — `/topic/foo`, `/user/queue/*`, `/topic/lesson/123`, любое, что не matches — интерсептор **просто пропускает** (`if (!matcher.matches()) return message;`). При этом `SimpleBroker` на `/topic` принимает любой SUBSCRIBE и будет исправно пересылать сообщения, если туда кто-то публикует.
- **Риск:** Сейчас единственный publisher (EventConsumer) публикует только в `/topic/group/{id}` и `/topic/group/{id}/headman`, так что формально утечки нет. Но любой новый publisher, использующий другой формат destination (например, user-scoped `/user/queue/private`), **по умолчанию открыт любому подключённому пользователю без authorization**. Это bomb на будущее — типичная IDOR-дыра при добавлении фич.
- **Как чинить:** Перевернуть логику — **whitelist, а не blacklist**. Если destination не matches ни один известный паттерн, `throw new IllegalArgumentException("Unknown destination")`. Добавить тесты, ловящие попытки подписаться на случайные destination (`/topic/foo`, `/topic/user/123`).
- **Зависимости:** нет.

### P0-5: JWT handshake не проверяет `exp` explicitly и не логирует `user_id` отклонённых попыток — нет audit trail для реплей-атак

- **Где:** `config/JwtHandshakeInterceptor.java:72-91`
- **Что:** `Jwts.parser().verifyWith(publicKey).requireIssuer(...).requireAudience(...).build().parseSignedClaims(token)` валидирует подпись, issuer, audience и exp (jjwt делает exp автоматически). Но: (1) нет nbf/iat проверки; (2) нет jti/replay-защиты — если токен утёк и клиент отключился, тем же токеном может подключиться атакующий сколько угодно раз; (3) отклонённые попытки логируются как `log.debug("WebSocket handshake rejected — invalid JWT: {}", e.getMessage())` — debug-уровень не попадёт в прод-логи при `root: INFO`, и даже попав, там нет `user_id` и `remote_ip`.
- **Риск:** (1) Утекший JWT даёт атакующему полный доступ к group-topic'у до exp (обычно 15 минут — это достаточно, чтобы сбросить его после аудита). (2) Отсутствие audit trail — не узнаешь, что была попытка подключиться с plausibly leaked token.
- **Как чинить:** Логировать на уровне WARN при rejected handshake с `remote_ip` из `request.getRemoteAddress()`, при валидном — INFO с `user_id`. Рассмотреть требование `requireNotBefore()` и минимальный допустимый возраст токена. Для реплей-защиты ввести Redis-set `jwt:used:{jti}` с TTL равным remaining-lifetime токена — но это добавляет Redis-зависимость notification-web, которой сейчас нет.
- **Зависимости:** Redis зависимость для реплей-защиты — обсуждение архитектурного решения.

---

## Серьёзные проблемы (P1)

### P1-1: Нет `Reminder`-подсистемы (3 напоминания: начало/середина/конец пары) в Java-канале

- **Где:** Должно было быть в `event/` или отдельном пакете `reminder/`.
- **Что:** CLAUDE.md пункт «Ключевые бизнес-правила»: «3 напоминания об отметке: начало, середина, конец пары. После пары — удалить сообщения». `phases-plan.md:404-417` даёт деталь: reminders для notification-bot через Redis-ключ `reminder:msgs:{lesson_id}:{user_id}` с message_id Telegram-сообщений. Для **Web Push / STOMP** канала этой логики нет вообще: `lesson.started` → разовый push, `lesson.closed` → просто STOMP-уведомление старосте «сессия завершена», никаких «середины пары» и «конца пары для неотметившихся».
- **Риск:** Неполная функциональность для PWA-пользователей. Студент, у которого закрыт Telegram, но включены Web Push в PWA, получит только одно уведомление при `lesson.started`. Если пропустил момент — всё, reminder'ов нет.
- **Как чинить:** Либо (а) расширить контракт событий: schedule-service публикует `lesson.mid_reminder` и `lesson.final_reminder` на середине и за N минут до конца, EventConsumer маршрутизирует их на push/WS, либо (б) в notification-web сделать свой @Scheduled task + Redis-очередь. Вариант (а) чище — вся логика времени в schedule-service.
- **Зависимости:** требует changes в schedule-service (publisher) и в event-schemas/.

### P1-2: DEBUG-логирование по умолчанию в dev-профиле + утечка push `endpoint` в логах

- **Где:** `application.yml:42-43` (`ru.rutcampustrack: DEBUG`), `WebPushDeliveryService.java:104, 109, 112` (`sub.getEndpoint()` попадает в log.debug / log.info / log.warn). В prod-профиле (`application-prod.yml:18-20`) перекрыто на INFO — это хорошо — **но** `log.info("Deleted expired push subscription: {}", sub.getEndpoint())` остаётся в INFO и льёт endpoint в production logs.
- **Что:** Web Push endpoint — это URL вида `https://fcm.googleapis.com/fcm/send/c3a...token...` или `https://updates.push.services.mozilla.com/wpush/...`, где последний сегмент — секретный идентификатор клиента. Кто имеет endpoint + VAPID ключи (доступ к notification-web) — имеет возможность слать push на это устройство. Логирование endpoint в INFO создаёт risk при `docker logs`/централизованном logging/Graylog.
- **Риск:** Secret leakage в логи. Не критично (нужен ещё VAPID private key), но неаккуратно.
- **Как чинить:** (1) `application.yml:42-43` понизить `ru.rutcampustrack` до INFO, или наоборот добавить явно WARN/ERROR. (2) В WebPushDeliveryService логировать usedId + hash endpoint'а, не сам endpoint. Например, `endpoint.substring(0, 40) + "..."` или SHA-1.
- **Зависимости:** нет.

### P1-3: `EventConsumer.onEvent` не имеет retry/DLQ-обработки — любая ошибка в `convertAndSend` или `webPushDeliveryService.sendToGroup` уходит в DLQ

- **Где:** `event/EventConsumer.java:30-69`, `config/RabbitConfig.java:42-52`
- **Что:** DLQ-инфраструктура объявлена (`notification-web.events` → `rut-uit.events.dlq`) — это хорошо. Но: (1) нет явных retry-настроек в `spring.rabbitmq.listener.simple` (по умолчанию Spring делает 3 попытки при auto-ack, но `application.yml` это не задаёт). (2) `sendToGroup` помечен `@Async` и возвращается immediate — если Executor-queue переполнен (`queueCapacity=50`), `RejectedExecutionException` полетит в синхронном `@Async` вызове **после** successful `convertAndSend`, и весь consumer-thread упадёт, сообщение пойдёт в retry. Получим "at-least-once" с дубликатами STOMP-уведомлений и потенциально zero Web Push при rejection. (3) Нет dedup — если событие пришло дважды (или retry после partial failure), все клиенты получат two STOMP messages, а в Web Push — два push'а.
- **Риск:** Flap-штормы при любых скачках нагрузки. Особенно на `lesson.started` когда одновременно 30 групп стартуют: publisher'ы шлют 30 event'ов, WS broadcast — это 30×N клиентов, push — ещё 30×N.
- **Как чинить:** (1) В `application.yml` задать явно `spring.rabbitmq.listener.simple.{retry.enabled=true, retry.max-attempts=3, default-requeue-rejected=false}`. (2) EventConsumer — try/catch со сбором метрики `push_delivery_errors_total`. (3) Dedup: Redis-ключ `seen:{event_id}` с TTL 60 с — требует Redis. Можно проще: in-memory `Caffeine` cache на 10 тыс. event_id.
- **Зависимости:** Redis зависимость — архитектурное обсуждение.

### P1-4: `SubscriptionAuthInterceptor.preSend` бросает `IllegalArgumentException` — Spring WebSocket отдаёт клиенту STOMP ERROR frame, который **рвёт соединение**

- **Где:** `config/SubscriptionAuthInterceptor.java:47, 57, 63, 70`
- **Что:** `throw new IllegalArgumentException("Unauthorized subscription")` из ChannelInterceptor.preSend приводит к тому, что Spring Messaging отправит STOMP ERROR клиенту, и большинство клиентских STOMP-библиотек (stompjs) закроют соединение при ERROR. Если пользователь пытается подписаться на невалидный топик, он теряет всё соединение, а не только ту подписку.
- **Риск:** Плохой UX. Особенно при реактивной проверке — frontend может попытаться subscribe на `/topic/group/{oldGroupId}/headman` после того, как старосту перевыбрали — соединение рвётся, все валидные подписки теряются, надо переподключаться.
- **Как чинить:** Возвращать `null` из `preSend` — это тихо отбросит SUBSCRIBE frame (ни sub, ни ошибка). Логировать отказ, оставить соединение живым. Либо создать `MessageExceptionHandler` на уровне `@ControllerAdvice` для messaging.
- **Зависимости:** нет.

### P1-5: `PushController.unsubscribe` не проверяет, что `endpoint` в request body принадлежит запрашивающему пользователю

- **Где:** `push/PushController.java:70-72`, `PushSubscriptionRepository.deleteByUserIdAndEndpoint`
- **Что:** Метод помечен @RequireRole(STUDENT) и использует `requestContext.getUserId()` + `request.endpoint()`. Это семантически правильно — удалить можно только **свою** подписку, а не чужую. НО: (1) Нет тела ответа, если `deleteByUserIdAndEndpoint` не нашёл ничего — возвращается 204 NO_CONTENT независимо. Клиент не узнает, был ли endpoint вообще в БД. Это скорее OK (idempotency), но **атакующий может перебирать chunk-endpoint'ов чужих пользователей и по времени ответа/метрикам понимать, какие реально существуют**. (2) Нет rate limit на unsubscribe — можно за секунду flood'ить delete-запросы.
- **Риск:** Мелкая side-channel утечка, side-channel timing attack — средний.
- **Как чинить:** Добавить rate-limit (например, Bucket4j по user_id, 10 req/min). Timing-leak решается принятием того, что это idempotent 204 с `@Async` delete или с delay — излишне.
- **Зависимости:** Нет.

### P1-6: Handshake-level Origin check через `setAllowedOriginPatterns(allowedOrigins.split(","))` — список не триммится, дефолты содержат dev-origins, прод перекрывается через env но binding хрупок

- **Где:** `config/WebSocketConfig.java:28, 43`
- **Что:** (1) `allowedOrigins.split(",")` не делает `.trim()` — если переменная `NOTIFICATION_WS_ALLOWED_ORIGINS=" https://ruttrack.site , https://app.ruttrack.site"` будет задана с пробелами, CORS откажет. (2) Дефолт включает `http://localhost:5173, http://localhost:4200, http://localhost:3000, https://ruttrack.site` — если в prod переменная забыта, localhost-origins будут разрешены и в prod. (3) `notification.ws.allowed-origins` относится к кастомному property с точками — relaxed binding из `NOTIFICATION_WS_ALLOWED_ORIGINS` работает, но лучше явно в `application-prod.yml` прописать `notification.ws.allowed-origins: ${NOTIFICATION_WS_ALLOWED_ORIGINS:https://ruttrack.site}` без localhost-дефолтов.
- **Риск:** Prod случайно получает localhost-origins в whitelist → CORS-бypass со стороны злоупотребительных клиентов.
- **Как чинить:** `Arrays.stream(allowedOrigins.split(",")).map(String::trim).toArray(String[]::new)`. В `application-prod.yml` переопределить `notification.ws.allowed-origins` без dev-дефолтов.
- **Зависимости:** нет.

### P1-7: `PushSubscriptionDocument.isHeadman` — snapshot на момент subscribe, без механизма ре-синхронизации

- **Где:** `push/PushSubscriptionDocument.java:45-54`, `push/WebPushDeliveryService.filterRecipients:130-134`
- **Что:** При subscribe записывается `headman = requestContext.isHeadman()`. Если статус старосты меняется (перевыборы — `is_headman` на пользователе переключается в academic-service), подписки в `push_subscriptions` остаются stale. Комментарий в коде честно предупреждает: «Если староста перевыбирается, PWA должна переподписаться для обновления флага».
- **Риск:** (1) Новый староста не получает Web Push excuse.requested, пока не переподпишется вручную (или пока сервис-воркер не обновит подписку). (2) Бывший староста продолжает получать push'ы excuse-тикетов, пока его подписка не обновится.
- **Как чинить:** Либо (а) брать `headman` из academic-service через gRPC в момент доставки push'а, не из документа (плохо для latency), либо (б) слушать событие `group.updated` / новое `headman.changed` и обновлять поле в `push_subscriptions` массово. Подход (б) требует publisher'а в academic-service.
- **Зависимости:** academic-service publisher.

### P1-8: `EventConsumer` не проверяет схему события — любой misbehaving publisher может отправить мусор, который пройдёт silently

- **Где:** `event/EventConsumer.java:30-69`
- **Что:** Consumer просто приводит `envelope.get("payload")` к `Map<String, Object>` и `envelope.get("group_id")` к `Number`. Если в payload нет `group_id` → пропускаем (это сейчас). Если тип неверный (`group_id` пришёл строкой) → `ClassCastException` → ушло в DLQ и не обработано. Нет валидации event-schemas (в `event-schemas/*.json` есть JSON Schema, но её никто не проверяет).
- **Риск:** Тихий дропфейл при schema mismatch. Атакующий, получивший доступ к RabbitMQ, может положить mal-formed payload и, например, сделать `payload.user_id = "' OR 1=1"` — не страшно в Mongo, но показательно.
- **Как чинить:** Подключить `networknt/json-schema-validator` или `everit-org/json-schema`, валидировать событие против схемы `event-schemas/{event_type}.json` перед обработкой. Если невалидно — в DLQ с явной причиной.
- **Зависимости:** схемы уже лежат в репо, но не packaged в jar.

### P1-9: `WebPushDeliveryService.sendToGroup` — N+1 при доставке: каждый push отправляется синхронно в `for`-loop на одном thread-е

- **Где:** `push/WebPushDeliveryService.java:100-115`
- **Что:** Метод аннотирован `@Async("pushTaskExecutor")` — ОК, но внутри он делает `for (PushSubscriptionDocument sub : targets) { ... webPushService.send(notification); ... }` **на одном из 10 потоков пула**. При 200 студентах в группе и `lesson.started` один thread будет занят ~200×300ms = 60 секунд, блокируя pushTaskExecutor. Если одновременно стартует 30 групп (типичный случай 8:30), после 10 групп все потоки забиты, `queueCapacity=50` ещё выдержит, дальше `RejectedExecutionException`.
- **Риск:** Под нагрузкой push delivery деградирует, Web Push опаздывает на минуты.
- **Как чинить:** (1) Параллелить внутри группы: `targets.parallelStream()` не подойдёт (ForkJoinPool), но можно запускать `CompletableFuture.allOf(targets.stream().map(sub -> CompletableFuture.runAsync(() -> send(sub), pushTaskExecutor)))`. (2) Увеличить `maxPoolSize` и `queueCapacity`. (3) Кардинально — перейти на reactive `WebClient` push-service (webpush-java не reactive, но есть форки).
- **Зависимости:** нет.

---

## Средние (P2)

### P2-1: `Dockerfile` `eclipse-temurin:21-jre-alpine` без `--with curl/wget`, healthcheck зависит от наличия wget в alpine-образе

- **Где:** `Dockerfile:36`, `docker-compose.yml:135`
- **Что:** Alpine JRE-образ обычно **не содержит wget**. Healthcheck `wget -qO- http://localhost:9094/actuator/health` может сломаться на новых base-image, если wget убрали. Нужен explicit `RUN apk add --no-cache wget` в runtime stage.
- **Риск:** Healthcheck ложно падает → docker перезапускает контейнер → cascade.
- **Как чинить:** Добавить `RUN apk add --no-cache wget` или использовать `curl`, либо перейти на k8s-style HTTP healthcheck (без CLI-tool).

### P2-2: `PushController.getVapidPublicKey` помечен `@RequireRole({UserRole.STUDENT})`, хотя по спеку PUSH-01 VAPID public key **должен быть публичным**

- **Где:** `push/PushController.java:46`, комментарий в `PushApi.java:23`: «Per PUSH-01: VAPID public key endpoint is public (no auth required)»
- **Что:** Интерфейс PushApi декларирует, что endpoint public; имплементация навешивает `@RequireRole({UserRole.STUDENT})`. Противоречие между контрактом и имплементацией. На практике это значит, что получить VAPID public key можно только уже авторизованному студенту — а frontend должен иметь его до авторизации, чтобы даже PWA install-prompt работал.
- **Риск:** PWA падает при первом запуске, если юзер не залогинен. Или frontend хардкодит public key — что совсем плохо.
- **Как чинить:** Убрать `@RequireRole` c `getVapidPublicKey` либо переделать в `/api/public/vapid-public-key` без auth. Уточнить с владельцем: должна ли это быть статика в nginx.

### P2-3: `PushController` — `@Valid @RequestBody` дублируется и в интерфейсе, и в controller

- **Где:** `PushApi.java:38, 43`, `PushController.java:54, 70`
- **Что:** `@Valid` уже в интерфейсе; в имплементации `@Valid` продублирован. Это работает, но стилистически — избыточно. По CLAUDE.md "Контроллер implements интерфейс, Маппинги ТОЛЬКО в интерфейсе". Аннотации валидации — тоже формально маппинги.

### P2-4: Нет CSRF-защиты на REST-endpoint'ах

- **Где:** Нигде.
- **Что:** `/push/subscribe` и `/push/subscribe DELETE` принимают `application/json` body. CORS настроен (предположительно на Gateway), но CSRF-токенов нет. Для JWT-авторизации в header это OK, но если JWT хранится в cookie — уязвимо.
- **Риск:** Зависит от того, где JWT лежит в браузере. Реально на PWA — `localStorage`, тогда CSRF не применим.

### P2-5: `WebPushDeliveryService.buildTitle` / `buildBody` — переключения по eventType в одном файле; нет изоляции per-event handler

- **Где:** `push/WebPushDeliveryService.java:171-248`
- **Что:** Два switch'а (title + body) на 12 event-типов, плюс три Set'а (`PUSH_EVENT_TYPES`, `HEADMAN_ONLY_EVENT_TYPES`, `USER_SCOPED_EVENT_TYPES`) — при добавлении нового event-типа надо править в 3-4 местах, легко забыть.
- **Как чинить:** Паттерн `Map<String, EventHandler>` или enum с методами. Не срочно.

### P2-6: `application.yml:16` — `mongodb://localhost:27017/attendance_db` — явное упоминание `attendance_db` в дефолте

- **Где:** `application.yml:16`
- **Что:** Дублирует P0-3 — notification-web даже в dev ходит в attendance_db по дефолту.

### P2-7: `@Scheduled` не используется, нет cleanup-задачи для старых/мёртвых подписок

- **Где:** Нет.
- **Что:** Подписки удаляются только при HTTP 410 от push-сервиса во время delivery. Если сервис перестаёт присылать события группе (например, пустая группа), подписки живут вечно. Нет TTL.
- **Риск:** Рост коллекции `push_subscriptions`.
- **Как чинить:** `@Scheduled(cron = "0 0 3 * * *")` — раз в сутки удалять подписки с `created_at < now - 180 days` или **не видевшие push > 60 days**.

### P2-8: Нет metric'ов для push/STOMP доставки

- **Где:** Нигде.
- **Что:** `micrometer-registry-prometheus` подключён в build.gradle, но кастомных метрик нет: `notification_events_received_total`, `push_deliveries_sent_total`, `push_deliveries_failed_total{reason=410|other}`, `stomp_subscriptions_total`, `websocket_sessions_active`. Без них prod-мониторинг слепой.
- **Как чинить:** `@Counted` / `Counter.increment()` в EventConsumer и WebPushDeliveryService.

### P2-9: `PushSubscriptionDocument` использует Lombok `@Data` — сгенерирован `equals()/hashCode()` на всех полях, включая `_id`. Для Mongo-документов это обычно OK, но сочетание @Builder + @AllArgsConstructor + @NoArgsConstructor избыточно.

- **Где:** `push/PushSubscriptionDocument.java:20-24`
- **Что:** Стилистический nit — Lombok используется щедро. CLAUDE.md разрешает Lombok в app-модулях.

### P2-10: `RequestContext.setRole(UserRole.valueOf(request.getHeader("X-User-Role").toUpperCase()))` — падает с NPE/IllegalArgumentException на невалидных ролях

- **Где:** `security/UserContextFilter.java:37`
- **Что:** Если `X-User-Role` отсутствует (null) — `.toUpperCase()` → NPE. Если значение невалидное (`X-User-Role: GUEST`) — `UserRole.valueOf` → `IllegalArgumentException`. Оба варианта обрабатываются Spring'ом как 500.
- **Как чинить:** Try/catch, на невалидную роль вернуть 400. Плюс — проверить, что при отсутствии `X-User-Id` filter корректно отрабатывает (сейчас — пропускает дальше, это OK).

---

## Мелкие и nit (P3)

### P3-1: `AccessDeniedException` дублирует стандартный `org.springframework.security.access.AccessDeniedException`

Свой класс — потому что Spring Security не подключён. Но это путает. Лучше назвать `RoleAccessDeniedException` или переехать на Spring Security с proper JWT resource-server config.

### P3-2: `JwtHandshakeInterceptor.extractTokenFromQuery` — ручной парсинг query-string

Вместо `UriComponentsBuilder` — дубль велосипеда. Работает, но стилистически плохо.

### P3-3: `AsyncConfig.pushTaskExecutor` — hardcoded `corePoolSize=4, maxPoolSize=10, queueCapacity=50`

Без вынесения в properties. Тюнинг невозможен без пересборки.

### P3-4: `VapidPublicKeyResponse` — POJO с `@Data`-подобными геттерами написан вручную, хотя в app можно было бы Lombok. Нельзя — это контракт, Lombok запрещён. Но `publicKey` не `final` + есть no-arg constructor для Jackson-десериализации — норм.

### P3-5: `notification-bot.events` DLQ-queue объявлен в `docker-compose.yml:88` (?). В RabbitConfig только `notification-web.events`-DLQ. Если в одной infra-цепочке bot тоже пишет в тот же DLQ-exchange — надо проверить cross-сервисно.

### P3-6: В `build.gradle.kts:11` версия `spring-web:6.2.1` **явно зафиксирована** в контракте — это может разойтись с Spring Boot 3.4 BOM в app-модуле (Boot 3.4.x ↔ Spring 6.1.x/6.2.x). Проверить совместимость при upgrade.

### P3-7: Нет `@Profile` разделения между dev и prod конфигами — всё через env-переменные, что гибко, но когда надо отключить что-то в dev (скажем, Web Push полностью), приходится ENV'ами.

---

## WebSocket-раздел

### Топики

| Топик | Публикатор | Подписчики | Авторизация |
|-------|-----------|-----------|-------------|
| `/topic/group/{id}` | `EventConsumer` → `lesson.started`, `lesson.cancelled`, `attendance.marked`, `homework.published`, `group.renamed`, `group.archived`, `excuse.decided`, `late_checkin.decided`, unknown | все студенты + headman группы `{id}` | `SubscriptionAuthInterceptor`: `user.group_id == {id}` |
| `/topic/group/{id}/headman` | `EventConsumer` → `excuse.requested`, `late_checkin.requested` | только headman группы `{id}` | `user.group_id == {id}` + `user.is_headman == true` |
| Любой другой `/topic/*` | Никто (сейчас) | Любой авторизованный | **Не проверяется** (P0-4 / blacklist не whitelist) |

### JWT-аутентификация handshake

1. Клиент открывает `ws://notification-web:9094/ws?token={jwt}` через SockJS (`ws://.../info`, `ws://.../{session}/websocket`).
2. `JwtHandshakeInterceptor.beforeHandshake` парсит `?token=` из query, валидирует подпись RSA-ключом, требует iss=`rutcampustrack-auth`, aud=`rutcampustrack`, exp.
3. При успехе кладёт в session attributes: `user_id`, `group_id`, `role`, `is_headman`.
4. Attribute'ы используются `SubscriptionAuthInterceptor` на per-SUBSCRIBE.

**Проблемы:**
- JWT попадает в query-string → логируется nginx access logs → **утечка токена в логи** (P1 — можно добавить, пропустил в основном списке).
- `request.getRemoteAddress()` не логируется — нет audit trail (P0-5).
- Нет heartbeat-кастомизации (дефолт Spring 10s/10s — OK, но на мобильной связи часто рвёт, `sockJS` fallback это лечит).

### Heartbeat и reconnect

- Spring дефолт 10s/10s. Клиенту SockJS достаточно.
- **Нет мониторинга активных сессий**: `WebSocketConfig` не использует `SessionDisconnectEvent` / `SessionSubscribeEvent` listeners.
- **Утечка подключений** маловероятна (Spring управляет), но **нет metric'и** `websocket_sessions_active_total`, `websocket_subscriptions_total`.

### CORS

- `setAllowedOriginPatterns(allowedOrigins.split(","))` — см. P1-6 (нет trim, dev-дефолт в prod).

---

## Web Push — раздел

### VAPID ключи

- **Хранение:** `${VAPID_PUBLIC_KEY}` и `${VAPID_PRIVATE_KEY}` в environment (через `docker-compose.yml` / `docker-compose.prod.yml` → `.env.prod`). Не в Redis (архитектурный документ обещал `vapid:public_key` / `vapid:private_key` в Redis — это НЕ реализовано, и это хорошо для dev, но нужно сверить с decision log).
- **В коде:** `WebPushConfig` вкалывает `@Value("${vapid.public-key}")` / `@Value("${vapid.private-key}")` в `PushService` при startup. Если ENV пуст → `PushService` с пустыми ключами → при первом `send()` упадёт.
- **Ротация:** Никакой. Чтобы сменить ключи, нужно: (1) обновить ENV, (2) рестарт контейнера, (3) **все существующие подписки инвалидируются** (они были сделаны под старый public key — браузер их больше не признает). Нет процедуры ротации.
- **Subject:** `mailto:noreply@rut.ru` — fine.

### Endpoint регистрации

- `POST /push/subscribe` (через Gateway: `POST /api/push/subscribe`).
- Request body: `{ endpoint: string, keys: { p256dh, auth } }`.
- Контекст (`user_id`, `group_id`, `is_headman`) — из заголовков Gateway.
- `@RequireRole(STUDENT)` на всех трёх методах — включая `getVapidPublicKey` (см. P2-2, противоречие с PUSH-01).

### Хранение подписок

**Коллекция `push_subscriptions` в MongoDB `attendance_db`** (P0-3 — должна быть в своей БД).

Поля:
| Поле | Тип | Индекс |
|------|-----|--------|
| `_id` | String (ObjectId) | PK |
| `user_id` | Long | compound uniq с endpoint |
| `group_id` | Long | idx_group_id |
| `endpoint` | String | compound uniq с user_id |
| `p256dh` | String | — |
| `auth` | String | — |
| `is_headman` | boolean | — (snapshot, см. P1-7) |
| `created_at` | Instant | — |

**TTL индекс отсутствует** (P2-7).

### Отправка push

- `WebPushDeliveryService.sendToGroup(groupId, eventType, payload)` — `@Async("pushTaskExecutor")`.
- Маршрутизация:
  - `HEADMAN_ONLY_EVENT_TYPES` = {`excuse.requested`, `late_checkin.requested`} → только `headman=true`.
  - `USER_SCOPED_EVENT_TYPES` = {`excuse.decided`, `late_checkin.decided`} → только `userId == payload.user_id`.
  - Остальные (`lesson.started`, `lesson.cancelled`, `lesson.one_off.*`, `homework.*`, `group.*`) → вся группа.
- Payload JSON: `{title, body, event_type, data: payload}`.
- Шифрование — стандартное Web Push (P-256 ECDH + AES-128-GCM) через `webpush-java`.
- Обработка ответа:
  - HTTP 201/204 → OK.
  - HTTP 410 Gone → `repository.deleteByEndpoint(endpoint)` (`D-10/PUSH-07`).
  - Иные ошибки → `log.warn`, continue.

**Проблемы:**
- Нет retry при transient ошибках (500 от push-сервиса).
- Нет deadline / timeout.
- Нет batch'ирования.
- Нет metric'и.

### Валидация endpoint

**Отсутствует.** Любая строка в `endpoint` попадает в БД. Можно записать `endpoint=http://attacker.com/log?token=secret` — и `webPushService.send()` попытается HTTP POST с VAPID-подписью на этот URL. **Это не критично (атакующий должен быть уже авторизованным студентом)**, но это вектор SSRF / internal network probe. webpush-java сам фильтрует по https-scheme (надо проверить!).

**Как чинить:** `SubscribeRequest` — ввести `@Pattern` whitelist на известные push-provider endpoint'ы (`fcm.googleapis.com`, `updates.push.services.mozilla.com`, `web.push.apple.com`). Это поднимает поддержку до P1.

---

## Event-routing

| Event type | STOMP destination | Web Push target | Доп. фильтр |
|-----------|-------------------|------------------|-------------|
| `lesson.started` | `/topic/group/{id}` | вся группа | — |
| `lesson.cancelled` | `/topic/group/{id}` | вся группа | — |
| `lesson.one_off.created` | `/topic/group/{id}` | вся группа | — |
| `lesson.one_off.cancelled` | `/topic/group/{id}` | вся группа | — |
| `homework.published` | `/topic/group/{id}` | вся группа | — |
| `homework.updated` | `/topic/group/{id}` | вся группа | — |
| `group.renamed` | `/topic/group/{id}` | вся группа | — |
| `group.archived` | `/topic/group/{id}` | вся группа | — |
| `excuse.requested` | `/topic/group/{id}/headman` | только headman'ы группы | `headman==true` |
| `late_checkin.requested` | `/topic/group/{id}/headman` | только headman'ы группы | `headman==true` |
| `excuse.decided` | `/topic/group/{id}` | один студент | `userId == payload.user_id` |
| `late_checkin.decided` | `/topic/group/{id}` | один студент | `userId == payload.user_id` |
| `attendance.marked` | `/topic/group/{id}` | **не доставляется** | `shouldPush()=false` |
| `lesson.closed` | **не доставляется в STOMP** (нет `group_id` по дефолту в payload?) | не доставляется | архитектурный пробел |
| `lesson.deleted` | не обрабатывается явно | не обрабатывается | unknown → STOMP default |
| `otp.verified` | не обрабатывается | не обрабатывается | — |
| `semester.archived` | не обрабатывается | не обрабатывается | — |
| unknown event | `/topic/group/{id}` (если есть group_id) | — (`shouldPush=false`) | см. test `unknownEventType_routesToGroupTopic` |

**Проблема P1**: `excuse.decided` идёт в `/topic/group/{id}` на **всю группу**, хотя для Web Push фильтруется по user_id. То есть через WebSocket **любой член группы** получит событие «студенту X одобрено» — вместе с `decision_comment`, `student_name`, `lesson_date`. Это утечка персональных данных (PII, staff-only).

**Как чинить:** Для `excuse.decided` / `late_checkin.decided` в STOMP — использовать `/user/queue/notifications` (user-scoped destination), чтобы только тот student получил событие. Это потребует `setUserDestinationPrefix("/user")` и `convertAndSendToUser(userId, ...)`.

---

## Мёртвый код

- `PushSubscriptionRepository.deleteByEndpoint(String)` — используется только в `WebPushDeliveryService` на HTTP 410, не тестируется.
- `NotificationWebApplication` без `@EnableScheduling` — видимо, намеренно (нет @Scheduled задач). Не мёртвый, но наводит на P2-7.
- `UserRole.ADMIN` / `UserRole.TEACHER` в `notification-api-contract/enums/UserRole` — не используются в notification (все эндпоинты `@RequireRole(STUDENT)`). Можно удалить, но стилистически enum держит все роли ради консистентности.
- В `build.gradle.kts:32` комментарий «Apache HttpClient and jose4j transitively pulled by web-push at runtime; needed at compile time…» — транзитивные деп-ы прописаны explicitly, чтобы compile-time видел `HttpResponseException` и `JoseException`. Это workaround, код чистый.

---

## Костыли и TODO/FIXME

**`grep -ri "TODO\|FIXME\|XXX\|HACK" notification-service/` — ничего.** Чисто, без technical-debt-маркеров.

Одно «workaround»:
- `BootJar.loaderImplementation = LoaderImplementation.CLASSIC` в `notification-app/build.gradle.kts:57-59` — для BouncyCastle signed-jar compatibility c Spring Boot 3.2+. Комментарий есть, проблема известна.

---

## Тесты

### Что есть

| Файл | Покрытие | Комментарий |
|------|----------|-------------|
| `JwtHandshakeInterceptorTest` (5 тестов) | valid, headman, missing token, invalid token, expired token, null query | ✅ |
| `RabbitConfigTest` (5) | Queue-bean, DLQ-bean, Fanout/Direct exchanges, Jackson converter | Структурные |
| `EventConsumerTest` (14) | STOMP routing для 8 events + 6 push-trigger-hook тестов | Хорошо |
| `GroupEventTest` (2) | group.renamed, group.archived — STOMP + push | Для BUG-006-6 |
| `PushControllerTest` (8) | VAPID, subscribe + userId+groupId from context, unsubscribe, @RequireRole annotations | Хорошо |
| `PushSubscriptionRepositoryTest` (2) | Через рефлексию — PushMongoConfig existence | Недостаточно |
| `WebPushDeliveryServiceTest` (12) | fetchByGroupId, send per sub, 410 auto-delete, non-410 no-delete, title/body для 5 event'ов | Хорошо |
| `SecurityInfrastructureTest` (4) | RoleCheckAspect allow/reject, UserContextFilter populates | Базово |

### Что отсутствует

- **End-to-end WebSocket-тест**: поднять embedded tomcat, подключиться STOMP-клиентом с JWT, subscribe на `/topic/group/42`, проверить, что сообщение получено при publish → RabbitMQ. Нужен `spring-websocket-test` / `WebSocketStompClient`. Отсутствует.
- **SubscriptionAuthInterceptor unit-test** — нет вообще! P0-4 / P1-4 не покрыты никаким тестом.
- **Integration-тест с Testcontainers MongoDB** — `PushSubscriptionRepository` индексы и `deleteByUserIdAndEndpoint` на живой БД. Сейчас — рефлексия.
- **`RabbitTemplate` + EventConsumer с реальным RabbitMQ (Testcontainers)** — нет.
- **`WebPushDeliveryService` параллелизм (P1-9) и deadline** — нет.
- **CORS и allowed-origins** — нет.
- **`AccessDeniedException` → 500 vs 403** — некуда падать, т.к. `@ControllerAdvice` нет (P0-2).

### Оценка покрытия

~40 тестов при 31 main Java-файле — формально плотно, **но большая часть — unit / структурные**. Нет ни одного теста реальной доставки WebSocket/Web Push. Отмечаю: для stateless event-forwarder integration-тест критичен.

---

## Соответствие CLAUDE.md

| Правило | Статус | Комментарий |
|---------|--------|-------------|
| Contract-first | ✅ | `PushApi` в контракте, Controller implements. |
| Без Lombok в `*-api-contract` | ✅ | В контракте нет. |
| Request DTO = record | ✅ | `SubscribeRequest`, `UnsubscribeRequest` — records. |
| Response DTO = class extends RepresentationModel | ✅ | `VapidPublicKeyResponse`. |
| Маппинги ТОЛЬКО в интерфейсе | ✅ | В `PushController` нет `@RequestMapping`. |
| HATEOAS Level 3 | ⚠️ | `EntityModel<VapidPublicKeyResponse>` есть, но без `_links` (`EntityModel.of(response)` без addLink). |
| RFC 7807 Problem Details | ❌ | `ErrorResponse` record отсутствует в контракте, нет `@ControllerAdvice` (P0-2). |
| Swagger/OpenAPI в интерфейсе | ✅ | `@Operation`, `@ApiResponse`, `@Tag` есть. |
| `@ControllerAdvice` — центр. обработка | ❌ | Отсутствует (P0-2). |
| Enum в PostgreSQL lowercase | N/A | Нет PostgreSQL. |
| `@Enumerated(EnumType.ORDINAL)` — никогда | N/A | Нет JPA. |
| Soft delete | N/A | Нет владения пользователями. |
| PK BIGSERIAL (Long) | N/A | MongoDB `_id` — String ObjectId. |
| Временные метки UTC | ✅ | `Instant createdAt`. |
| REST пути `/api/{service}/...` | ✅ | Через Gateway `/api/push/**` + `/api/ws/**`. |
| gRPC `ru.rutcampustrack.{service}.grpc` | N/A | Нет gRPC. |
| Event types `{domain}.{action}` | ✅ | `lesson.started`, `excuse.decided` и т.д. |
| Пакеты `ru.rutcampustrack.{service}.{module}` | ✅ | `ru.rutcampustrack.notification.{config,event,push,security,exception}`. |
| Database per Service | ❌ | Использует `attendance_db` (P0-3). |
| 3 напоминания об отметке | ❌ | Не реализованы для Web Push / STOMP (P1-1). |
| Cleanup напоминаний после пары | ❌ | Не реализован. |

**Итого: 4 явных нарушения (P0-2, P0-3, P1-1, HATEOAS links).**

---

## Зависимости между проблемами

- **P0-1 (trust X-User-*) + P0-3 (shared DB) + P0-4 (blacklist subscribe)** — это три кита безопасности. Фикс P0-1 делает P0-4 менее критичным (Gateway уже гарантирует auth), но не устраняет.
- **P0-2 (нет ControllerAdvice) + P1-4 (Interceptor бросает raw exception)** — одна и та же недоработка глобальной обработки исключений. Фикс P0-2 автоматически дисциплинирует подход и для messaging.
- **P1-1 (reminders) + P1-3 (retry/dedup) + P1-9 (параллелизм доставки)** — всё это deliverability-кластер. Надо решать вместе.
- **P1-7 (stale headman flag) + архитектурная «подписки snapshot'ом»** — требует publisher'а в academic-service, выходит за границы notification-service.
- **P0-3 (shared DB) + P2-6 (defaults на attendance_db)** — единая фикс: separate DB + обновить configs.

---

## Вопросы к владельцу проекта

1. **Web Push reminders.** В CLAUDE.md 3 напоминания (начало/середина/конец) — это только для Telegram-бота или для Web Push тоже? Если тоже — где публикатор timer-событий (sched, @Scheduled в notification-web, новый publisher в schedule-service)?
2. **Почему `push_subscriptions` в attendance_db?** Это намеренное решение «временно экономим на контейнерах MongoDB»? Или ошибка миграции?
3. **VAPID ротация.** Есть ли план на ротацию VAPID-ключей? Если да — как инвалидируются старые подписки?
4. **`excuse.decided` в STOMP.** Событие летит на `/topic/group/{id}` — всей группе (см. таблицу event-routing выше). Это намеренно (для UX прозрачности «староста согласовал N тикетов»), или утечка PII?
5. **Handshake-level audit log.** Нужно ли писать в INFO/WARN о каждом rejected handshake с `remote_ip`? Сейчас это DEBUG, т.е. в прод-логах отсутствует.
6. **`getVapidPublicKey` — public или STUDENT-only?** Контракт `PushApi` говорит public (комментарий PUSH-01), имплементация требует STUDENT-роль (P2-2). Что правильно?
7. **Валидация endpoint'а.** Ограничивать ли subscribe только до known-provider-URL (fcm.googleapis.com + mozilla + apple) — SSRF-защита?
8. **DLQ-обработка.** Что делать с событиями, улетевшими в `notification-web.events.dlq`? Сейчас их никто не читает. Нужен dlq-consumer + алерт?

---

_Подготовлено 2026-04-17 для предрелизного аудита RutCampusTrack v0.0.0._
