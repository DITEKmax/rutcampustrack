# RutCampusTrack — Отчёт Фазы 5: Notification Service (Web + Bot)

## Дата: Апрель 2026

## Цель фазы

Notification Service v5.0: real-time push-уведомления через WebSocket (STOMP) для веб-панели и Telegram-бот для студентов. Оба канала потребляют события RabbitMQ от существующих сервисов. Бот отправляет inline-кнопки для отметки, напоминания с полным жизненным циклом (создание → удаление), команды /start, /login, /status.

---

## Что реализовано

### Подфаза 20: Shared Infrastructure

**Цель:** Общая инфраструктура — две durable RabbitMQ-очереди с DLQ, docker-compose контейнеры, Redis namespace для напоминаний.

- **RabbitMQ**: `notification-web.events` и `notification-bot.events` очереди с DLQ, привязка к `rut-uit.events` fanout exchange
- **Docker-compose**: контейнеры notification-web (порт 9094) и notification-bot с health checks и depends_on
- **Redis**: namespace `reminder:msgs:{lesson_id}:{user_id}` для хранения message_id напоминаний (RPUSH/LRANGE)
- **3 плана**, завершено 2026-04-04

### Подфаза 21: Notification Web — WebSocket Core

**Цель:** STOMP WebSocket с JWT-аутентификацией на handshake, маршрутизация событий по группам.

- **WebSocketConfig**: STOMP endpoint `/api/ws` с SockJS fallback
- **JwtHandshakeInterceptor**: JWT валидация при подключении, извлечение group_id и user_id в session attributes
- **EventConsumer**: RabbitMQ → WebSocket маршрутизация 5 типов событий (lesson.started, lesson.closed, lesson.cancelled, homework.*, attendance.marked)
- **Group topics**: `/topic/group/{groupId}`, `/topic/headman/{groupId}` для headman-only событий
- **20 Java тестов**
- **2 плана**, завершено 2026-04-05

### Подфаза 22: Bot Infrastructure Layer

**Цель:** Три инфраструктурных клиента для бота — aio-pika consumer с watchdog, gRPC async клиент, Redis async клиент, throttled send queue.

- **aio-pika consumer**: watchdog-корутина с 5s retry loop, автовосстановление после рестарта RabbitMQ
- **gRPC async client**: `GetGroupMembers` с 5-минутным кэшем, неблокирующий для asyncio event loop
- **Redis async client**: RPUSH/LRANGE/DELETE для reminder message_ids
- **Throttled send queue**: token bucket 30 msg/s, retry backoff [1, 2, 4]s с duck-typed retry_after
- **3 плана**, завершено 2026-04-05

### Подфаза 23: Bot Telegram Commands

**Цель:** /start (привязка аккаунта), /login (OTP), /status (текущая посещаемость).

- **/start**: `GetUserByTelegramId` gRPC RPC → приветствие с логином/паролем или инструкция обратиться к старосте
- **/login**: запрос OTP через Auth Service → студент вводит код → подтверждение логина
- **/status**: текущая пара и статус посещаемости через Schedule + Attendance gRPC
- **OTP в HTTP response body**: бот получает OTP и пересылает студенту через Telegram
- **3 плана**, завершено 2026-04-05

### Подфаза 24: Bot Event Notifications

**Цель:** Telegram-уведомления с inline-кнопками при начале пары, plain-уведомления при отмене и ДЗ, headman-алерты.

- **lesson.started**: inline-кнопка с WebAppInfo для Mini App check-in, message_id сохраняется в Redis (RPUSH)
- **lesson.cancelled**: plain text уведомление через throttled send queue
- **homework.published/updated**: уведомление о новом/изменённом ДЗ
- **excuse.requested / late_checkin.requested**: headman-only алерты (хендлеры готовы, но publisher ещё не существует)
- **EventDispatcher**: маршрутизация по event_type к соответствующим хендлерам
- **2 плана**, завершено 2026-04-05

### Подфаза 25: Bot Reminder Lifecycle

**Цель:** Полный жизненный цикл напоминаний — 3 сообщения (начало, середина, конец пары), удаление при закрытии пары или после отметки.

- **ReminderScheduler**: asyncio timers для midpoint и near-end напоминаний
- **lesson.closed handler**: LRANGE всех message_ids → bulk delete_message → DEL Redis keys
- **attendance.marked handler**: немедленное удаление напоминаний конкретного студента при status=present
- **3 напоминания**: начало пары (при lesson.started), середина, ближе к концу — как указано в CLAUDE.md
- **2 плана**, завершено 2026-04-05

### Подфаза 26: Notification Deployment Hardening

**Цель:** Закрытие аудит-гэпов — None guard для reminder_scheduler, JWT key volume mount, docker-compose env vars.

- **lesson_closed.py**: `reminder_scheduler is None` → warning log, продолжает удаление сообщений
- **JWT public key**: volume mount в docker-compose для notification-web
- **Docker-compose env vars**: 6 полей notification-bot (SCHEDULE_GRPC_HOST/PORT, AUTH_SERVICE_HOST/PORT, API_GATEWAY_URL, MINI_APP_URL)
- **1 план**, завершено 2026-04-05

---

## Статистика

| Метрика | Значение |
|---------|----------|
| Фаз | 7 (фазы 20-26) |
| Планов | 16 |
| Тестов | ~128 (20 Java + 108 Python) |
| Timeline | 2 дня (2026-04-04 → 2026-04-05) |
| Коммитов | 101 |
| Файлов | 463 |
| Требований | 19/25 satisfied (6 partial) |

## Известные пробелы

| ID | Описание | Причина |
|----|----------|---------|
| WS-05, WS-06 | Headman WebSocket push handlers | Хендлеры готовы, но publisher для excuse.requested / late_checkin.requested ещё не существует |
| NOTIF-08, NOTIF-09 | Headman Telegram alert handlers | Аналогично — хендлеры готовы, awaiting publisher |
| WS-07 | Group isolation verification | Нужна live-проверка на уровне брокера |
| NOTIF-02, NOTIF-03 | Timer accuracy | TZ fix применён, но live timer testing ещё не проведён |

## Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| STOMP in-memory broker (без external broker) | Достаточно для single-instance VPS |
| JWT claims только при handshake | Упрощает WebSocket lifecycle; expired JWT клиенты продолжают получать |
| Отдельный /headman topic (не ChannelInterceptor ACL) | Проще архитектура; honor system допустим для MVP |
| aio-pika watchdog с 5s retry loop | Graceful restart после перезагрузки RabbitMQ |
| Token bucket 30 msg/s | Нет 429 ошибок от Telegram API |
| Redis RPUSH list для reminder message_ids | LRANGE возвращает все ID в порядке вставки для bulk delete |
| grpcio 1.73.0 + protobuf 6.31.0 | Совместимая пара; 1.80.x требует breaking change в protobuf |

## Архитектура

```
RabbitMQ (rut-uit.events fanout)
    ├── notification-web.events queue
    │   └── EventConsumer → STOMP SimpMessagingTemplate
    │       ├── /topic/group/{groupId}     (все события)
    │       └── /topic/headman/{groupId}   (excuse/late-checkin)
    └── notification-bot.events queue
        └── aio-pika consumer → EventDispatcher
            ├── lesson.started  → inline button + Redis reminder
            ├── lesson.cancelled → plain notification
            ├── lesson.closed   → cleanup reminders
            ├── attendance.marked → cleanup student reminders
            ├── homework.*      → homework notification
            └── excuse/late_checkin → headman alert
```
