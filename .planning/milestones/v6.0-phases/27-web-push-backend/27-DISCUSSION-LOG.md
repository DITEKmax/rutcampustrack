# Phase 27: Web Push Backend - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-05
**Phase:** 27-web-push-backend
**Areas discussed:** VAPID-ключи, Push-подписки, Доставка push, REST API контракт

---

## VAPID Key Management

| Option | Description | Selected |
|--------|-------------|----------|
| Redis (как в STATE.md) | Persist in Redis (no TTL), auto-generate on first start. Redis already in docker-compose. | |
| Файловая система (/keys/) | Files vapid_public.key and vapid_private.key in /keys/ volume. Analogous to JWT RSA keys. Volume is :ro — auto-generation impossible. | |
| Environment variables | VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY in docker-compose env. Simple but keys are long (Base64). | ✓ |

**User's choice:** Environment variables
**Notes:** Follows docker-compose env pattern. Keys read via application.yml ${VAPID_PUBLIC_KEY}. Pre-generated externally.

---

## Push Subscription Storage

| Option | Description | Selected |
|--------|-------------|----------|
| MongoDB (attendance_db) | Reuse existing MongoDB container and attendance_db. Collection push_subscriptions alongside attendance data. No new container needed. | ✓ |
| MongoDB (отдельная notification_db) | Separate database for notification-web. Same MongoDB container, different database name. Microservice data isolation. | |
| Redis | Hash per user (push:user:{userId}). notification-web already depends on Redis. But poor querying by groupId — needs double index maintenance. | |

**User's choice:** MongoDB (attendance_db)
**Notes:** Reuses existing infrastructure. notification-web gains MongoDB dependency.

---

## Push Delivery

| Option | Description | Selected |
|--------|-------------|----------|
| Async в EventConsumer | EventConsumer calls PushService.sendToGroup() async after STOMP routing. Parallel execution, push errors don't block STOMP. | ✓ |
| Отдельный RabbitMQ listener | Second consumer on separate queue (notification-web.push). Full STOMP/Push isolation. But duplicates queue + binding. | |
| Фильтрация событий | Push only for lesson.started, lesson.cancelled, homework.published (3 types per requirements). | ✓ (combined) |

**User's choice:** Async в EventConsumer + strict event type filtering
**Notes:** Only 3 event types (PUSH-04/05/06). No lesson.closed — strictly per requirements.

---

## REST API Contract

| Option | Description | Selected |
|--------|-------------|----------|
| Без api-contract | Controller + DTOs directly in notification-web. Service not called by other Java services — contract unnecessary. | |
| Создать api-contract | Follow project convention: api-contract (java-library) + app module. Requires restructuring notification-web → notification-service/. | ✓ |

**User's choice:** Create api-contract module

### Follow-up: Module Structure

| Option | Description | Selected |
|--------|-------------|----------|
| Переименовать в notification-service/ | notification-service/notification-api-contract/ + notification-service/notification-app/. Matches academic-service/, schedule-service/ pattern. | ✓ |
| Добавить contract рядом | notification-api-contract/ at same level as notification-web/. Minimal changes but breaks convention. | |

**User's choice:** Restructure into notification-service/ parent directory

### Follow-up: Authorization

| Option | Description | Selected |
|--------|-------------|----------|
| Gateway фильтр (как все сервисы) | Push endpoints through API Gateway. Gateway validates JWT, injects X-User-Id/X-User-Role headers. | (base) |
| @RequireRole + заголовки | Gateway filter + @RequireRole(STUDENT) on controller. AOP infrastructure added to notification-app. | ✓ |

**User's choice:** @RequireRole(STUDENT) + Gateway headers

### Follow-up: VAPID Public Key Endpoint Access

| Option | Description | Selected |
|--------|-------------|----------|
| Только STUDENT | Only authorized students can get VAPID public key. | ✓ |
| Публичный | VAPID key is public by definition, accessible before login. | |

**User's choice:** Only STUDENT — no anonymous access

---

## Claude's Discretion

- Web Push Java library choice
- Push notification payload format
- MongoDB indexes on push_subscriptions
- @Async thread pool configuration
- Error retry strategy for non-410 failures

## Deferred Ideas

None — discussion stayed within phase scope
