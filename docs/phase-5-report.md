# RutCampusTrack — Отчёт Фазы 5: Web Push Backend

## Дата: Апрель 2026

## Цель фазы

Web Push Backend: реструктуризация notification-web в notification-service (contract + app), генерация VAPID-ключей, хранение push-подписок в MongoDB, асинхронная доставка Web Push уведомлений для событий lesson.started, lesson.cancelled и homework.published. Маршрутизация через API Gateway, AOP-безопасность @RequireRole, автоматическая очистка истёкших подписок по HTTP 410.

---

## Что реализовано

### Подфаза 27-01: Module Restructure and API Contract

**Цель:** Реструктуризация notification-web в notification-service с двумя подмодулями (contract + app), создание PushApi контракта и WebPushConfig бина.

- **Реструктуризация модулей**: `services/notification-web/` заменён на `services/notification-service/notification-api-contract/` (java-library) и `services/notification-service/notification-app/` (Spring Boot)
- **PushApi контракт**: интерфейс с 3 эндпоинтами — `getVapidPublicKey`, `subscribe`, `unsubscribe`
- **DTO**: `SubscribeRequest` (record с `@NotBlank endpoint`, `@NotNull Keys`), `UnsubscribeRequest` (record с `@NotBlank endpoint`), `VapidPublicKeyResponse` (extends `RepresentationModel`)
- **WebPushConfig**: `@Value("${vapid.public-key}")`, bean `webPushService` с `BouncyCastleProvider`, `LoaderImplementation.CLASSIC` для совместимости с Spring Boot 3.2+ signed-JAR
- **Зависимости**: `nl.martijndwars:web-push:5.1.2`, `bcprov-jdk15on:1.70`, `spring-boot-starter-data-mongodb`, `spring-boot-starter-aop`, `spring-boot-starter-hateoas`
- **Gateway**: маршрут `notification-push` — `/api/push/**` → `notification-web:9094`, `StripPrefix=1`
- **Docker-compose**: VAPID env vars (`VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT`), MongoDB URI, `depends_on` mongo-attendance

### Подфаза 27-02: Push Subscription CRUD and Security AOP

**Цель:** @RequireRole AOP-безопасность, PushController с MongoDB-хранением подписок, CRUD для subscribe/unsubscribe.

- **@RequireRole AOP**: 4 файла (RequireRole, RoleCheckAspect, RequestContext с `ScopedProxyMode.TARGET_CLASS`, UserContextFilter) — паттерн из attendance-service с переименованием пакетов
- **PushController**: `implements PushApi`, `@RequireRole({UserRole.STUDENT})` на всех 3 методах
  - `getVapidPublicKey` — возвращает VAPID public key (200 + HATEOAS)
  - `subscribe` — сохраняет подписку в MongoDB с `userId`/`groupId` из `RequestContext` (201)
  - `unsubscribe` — удаляет по `userId` + `endpoint` (204)
- **PushSubscriptionDocument**: `@Document(collection = "push_subscriptions")`, 7 полей с `@Field` (snake_case)
- **PushSubscriptionRepository**: `findAllByGroupId`, `deleteByUserIdAndEndpoint`, `deleteByEndpoint`
- **PushMongoConfig**: `@PostConstruct` — compound unique index `uniq_user_endpoint` (userId + endpoint), index `idx_group_id`
- **14 тестов**: 4 SecurityInfrastructure + 8 PushController + 2 PushSubscriptionRepository (structural)

### Подфаза 27-03: Async Web Push Delivery Engine

**Цель:** Асинхронная доставка Web Push уведомлений через @Async thread pool, интеграция в EventConsumer после STOMP-отправки.

- **AsyncConfig**: `@EnableAsync`, `pushTaskExecutor` (`ThreadPoolTaskExecutor`, core=4, max=10, queue=50, keepAlive=60s) — ограниченный пул предотвращает starving потока RabbitMQ consumer
- **WebPushDeliveryService**: `@Async("pushTaskExecutor")`, `CompletableFuture<Void> sendToGroup()`:
  - Fanout: `repository.findAllByGroupId(groupId)` → отправка каждому подписчику
  - 3 типа событий: `lesson.started` (Пара началась), `lesson.cancelled` (Пара отменена), `homework.published` (Новое ДЗ)
  - HTTP 410 auto-cleanup: `isGone()` проверяет `HttpResponseException.getStatusCode() == 410` → `repository.deleteByEndpoint(sub.getEndpoint())`
  - Payload JSON: `{title, body, event_type, data}` — формат для Service Worker
- **EventConsumer**: push hook ПОСЛЕ `messagingTemplate.convertAndSend` (STOMP):
  - `shouldPush(eventType)` — gate через `PUSH_EVENT_TYPES` (3 типа)
  - `sendToGroup(groupId, eventType, payload)` — асинхронный вызов, не блокирует STOMP
- **22 теста**: 7 WebPushDeliveryServiceTest (fetch, fanout, 410 delete, non-410 skip, 3 payload content) + 15 EventConsumerTest (9 existing + 6 new push hook tests)

---

## API Notification Service (Push)

### REST Endpoints

| Метод | URL | Описание | Роль |
|-------|-----|----------|------|
| GET | /push/vapid-public-key | Получить VAPID public key | STUDENT |
| POST | /push/subscribe | Подписка на push-уведомления | STUDENT |
| DELETE | /push/subscribe | Отписка от push-уведомлений | STUDENT |

**Gateway**: все через `Path=/api/push/**`, `StripPrefix=1`, маршрут `notification-push`

### RabbitMQ Events (потребление + push-доставка)

| Событие | STOMP | Web Push |
|---------|-------|----------|
| `lesson.started` | Да | Да — «Пара началась» |
| `lesson.cancelled` | Да | Да — «Пара отменена» |
| `homework.published` | Да | Да — «Новое ДЗ» |
| `excuse.requested` | Да | Нет |
| `attendance.marked` | Да | Нет |

---

## Ключевые технические решения

| Решение | Обоснование |
|---------|------------|
| VAPID ключи в env vars (не Redis) | Пользователь явно выбрал env vars вместо Redis (Discussion Log). Функциональная персистентность обеспечена конфигурацией |
| `LoaderImplementation.CLASSIC` | Обходит конфликт BouncyCastle signed-JAR с Spring Boot 3.2+ nested-JAR loader |
| `@Async("pushTaskExecutor")` | Асинхронная доставка push не блокирует RabbitMQ consumer thread и STOMP routing |
| Push hook ПОСЛЕ convertAndSend | STOMP-доставка всегда происходит первой, push — асинхронно после неё |
| `shouldPush()` gate в WebPushDeliveryService | EventConsumer не знает о типах событий — делегирует фильтрацию в delivery service |
| `createNotification()` protected factory | Testability: `Notification` конструктор парсит EC ключи — mock через `@Spy` + `doReturn` |
| MongoDB `push_subscriptions` | Переиспользует существующий MongoDB контейнер (attendance_db) |
| Compound unique index `userId + endpoint` | Предотвращает дубликаты подписок |

---

## Файловая структура

```
services/notification-service/
├── notification-api-contract/
│   └── src/main/java/ru/rutcampustrack/notification/contract/
│       ├── api/PushApi.java              ← Push REST контракт (3 эндпоинта)
│       ├── dto/push/
│       │   ├── SubscribeRequest.java     ← record с @NotBlank endpoint, @NotNull Keys
│       │   ├── UnsubscribeRequest.java   ← record с @NotBlank endpoint
│       │   └── VapidPublicKeyResponse.java ← extends RepresentationModel
│       └── enums/UserRole.java           ← STUDENT, TEACHER, ADMIN
└── notification-app/
    └── src/
        ├── main/java/ru/rutcampustrack/notification/
        │   ├── config/
        │   │   ├── WebPushConfig.java       ← VAPID PushService bean
        │   │   ├── PushMongoConfig.java     ← MongoDB indexes
        │   │   └── AsyncConfig.java         ← @EnableAsync + pushTaskExecutor
        │   ├── push/
        │   │   ├── PushController.java      ← implements PushApi, @RequireRole
        │   │   ├── PushSubscriptionDocument.java ← @Document(push_subscriptions)
        │   │   ├── PushSubscriptionRepository.java ← MongoRepository
        │   │   └── WebPushDeliveryService.java ← @Async push delivery
        │   ├── event/EventConsumer.java     ← STOMP + push hook
        │   ├── security/                    ← RequireRole, RoleCheckAspect, RequestContext, UserContextFilter
        │   └── exception/AccessDeniedException.java
        └── test/java/ru/rutcampustrack/notification/
            ├── security/SecurityInfrastructureTest.java  ← 4 tests
            ├── push/
            │   ├── PushControllerTest.java               ← 8 tests
            │   ├── PushSubscriptionRepositoryTest.java   ← 2 tests
            │   └── WebPushDeliveryServiceTest.java        ← 7 tests
            └── event/EventConsumerTest.java               ← 15 tests (9+6)
```

---

## Тестовое покрытие

| Модуль | Тесты | Тип | Фреймворк |
|--------|-------|-----|-----------|
| Security Infrastructure | 4 | Unit | Mockito |
| PushController | 8 | Unit | Mockito |
| PushSubscriptionRepository | 2 | Unit (structural) | JUnit |
| WebPushDeliveryService | 7 | Unit | Mockito (@Spy) |
| EventConsumer | 15 | Unit | Mockito |
| **Итого** | **47** | | |

Все тесты проходят: `./gradlew :services:notification-service:notification-app:test` — BUILD SUCCESSFUL

---

## Требования (покрытие)

| Категория | ID | Статус |
|-----------|-----|--------|
| Web Push | PUSH-01 (VAPID keys) | ✅ |
| Web Push | PUSH-02 (subscribe) | ✅ |
| Web Push | PUSH-03 (unsubscribe) | ✅ |
| Web Push | PUSH-04 (lesson.started push) | ✅ |
| Web Push | PUSH-05 (lesson.cancelled push) | ✅ |
| Web Push | PUSH-06 (homework.published push) | ✅ |
| Web Push | PUSH-07 (HTTP 410 auto-delete) | ✅ |
| Infrastructure | INFRA-02 (Gateway push route) | ✅ |
| **Итого** | **8/8** | **100%** |

---

## Известный tech debt

| Проблема | Серьёзность | Описание |
|----------|-------------|----------|
| ROADMAP SC-1 Redis vs env-var | Info | ROADMAP упоминает «VAPID key persisted in Redis», но пользователь явно выбрал env vars. Функционально эквивалентно |
| ROADMAP SC-1..3 путь /api/ws/push/ | Info | ROADMAP использует `/api/ws/push/...`, реализация — `/api/push/...` с отдельным gateway route. Изменение намеренное |
| End-to-end push delivery | Info | Требует живую инфраструктуру (Docker, VAPID ключи, браузер с push permission) для полной верификации |

---

## Следующая фаза

**Фаза 7: PWA Mobile Client** — React PWA с авторизацией (cookie-based refresh), расписание, гео-отметка, push-уведомления, статистика посещаемости и ДЗ трекер.
