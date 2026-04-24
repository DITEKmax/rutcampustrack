# M10 Decisions

Micro-решения, принятые в ходе milestone'а. Нумерация D1, D2, ...

---

## D1 — Module structure: используем существующий `notification-api-contract`

**Дата:** 2026-04-24 (старт M10, до G1)
**Контекст:** PLAN.md NOTES Q1 предлагал 3 варианта (a/b/c) именования.
**Факт:** модуль `services/notification-service/notification-api-contract/`
УЖЕ существует (создан в v6.0 Phase 27-32 для PushApi / Web Push).
Структура уже — вариант (b) из NOTES.
**Решение:** никакого переименования/создания нового модуля. M10 просто
ДОБАВЛЯЕТ классы (`NotificationApi`, DTO, enum) рядом с существующим
PushApi в том же контрактном модуле. Package structure:
- `ru.rutcampustrack.notification.contract.api` → добавить `NotificationApi.java`
- `ru.rutcampustrack.notification.contract.dto.history` → новый sub-package
  (NotificationHistoryDto, UnreadCountDto)
- `ru.rutcampustrack.notification.contract.enums` → `NotificationType` рядом
  с `UserRole`
**Последствие:** Docker image `rct-notification-web` не переименовывается,
compose не трогаем в части build.context.

---

## D2 — Mongo user: отдельный `notification_user` (PoLP)

**Дата:** 2026-04-24 (G1)
**Контекст:** PLAN.md:51 требует `notification_user`. По факту в
`infra/mongo/init-mongo.js` есть только общий `MONGO_USER` с readWrite
на обе БД (attendance_db + notification_db). M05 «зарезервировал» — но
кода нет. Q1 owner'у.
**Решение:** вариант (a) — создать **отдельного `notification_user`** с
правами `readWrite + dbAdmin` только на `notification_db`.
**Обоснование:**
- PoLP — индустриальный стандарт (MongoDB Atlas / AWS DocumentDB prod
  templates, Netflix/Uber/Shopify «one credential per service boundary»).
- Natural completion существующего pattern'а separation БД per сервис
  (postgres×2 + attendance_db + notification_db) — иначе смысла в
  разделении БД нет.
- Blast-radius: leak credential'а attendance-service не даёт доступ к
  notification_db и наоборот.
- Regression-guard: M09 runbook secret-rotation уже требует ротацию —
  просто +1 credential в checklist.
**Последствие:**
- Новые env vars: `MONGO_NOTIFICATION_USER` (default `rct_notification_user`),
  `MONGO_NOTIFICATION_PASSWORD` (default `rct_dev_pass`).
- `init-mongo.js` создаёт обоих user'ов (общий `MONGO_USER` остаётся для
  attendance, новый `notification_user` только для notification_db).
- `notification-web.SPRING_DATA_MONGODB_URI` использует новый credential.

---

## D3 — URI switch attendance_db → notification_db

**Дата:** 2026-04-24 (G1)
**Контекст:** `docker-compose.yml:161` и prod аналогично — URI указывает
на `attendance_db`. Legacy placeholder, т.к. notification-web stateless
forwarder ничего туда не писал. Q2 owner'у.
**Решение:** переключить URI на `notification_db` в `docker-compose.yml`
и `docker-compose.prod.yml`.
**Последствие:** `mongo-attendance` контейнер остаётся (physical Mongo
instance), но logical DB для notification-web = `notification_db`.
authSource=admin (root там).

---

## D4 — TTL retention как env var: `NOTIFICATION_HISTORY_TTL_DAYS`

**Дата:** 2026-04-24 (G1)
**Контекст:** OWNER-ANSWERS P2-6/4 устанавливает 30d retention. Hard-code
vs env var. Q3 owner'у.
**Решение:** env var `NOTIFICATION_HISTORY_TTL_DAYS` с default 30. TTL
индекс создаётся один раз при bootstrap (либо через Spring
`@Indexed(expireAfterSeconds=...)` с `@Value`, либо init-script).
**Обоснование:** retention values в крупных проектах (Stripe/Slack/
GitHub) — всегда external config, не hard-coded. Но Mongo TTL index —
bootstrap-time concern (изменение requires collMod).
**Последствие:**
- env var в compose обеих версий.
- `future-ideas.md` — «Notification retention collMod auto-reconciler»
  на v0.1 (в @PostConstruct detect existing TTL value и выполнить
  collMod если отличается — pattern Atlassian/Stripe).

---

## D5 — RabbitMQ: реальное имя exchange + queue naming

**Дата:** 2026-04-24 (G3 старт)
**Контекст:** PLAN.md:58 говорит о fanout exchange `notification.events`
и queue `notification.history`. По факту существующий
`RabbitConfig.java` использует exchange **`rut-uit.events`** и
существующая delivery queue = **`notification-web.events`**.
**Решение:**
- Exchange: оставить `rut-uit.events` (существующий, shared между
  всеми сервисами — producers publish туда, все consumers bind к нему).
- History queue: **`notification-web.history`** (aligned с pattern
  `notification-web.events`, не `notification.history`).
- DLQ: **`notification-web.history.dlq`** (симметрично существующему
  `notification-web.events.dlq`).
- Binding: history queue → fanout exchange `rut-uit.events` (получает
  ВСЕ events, denormalize mapper в consumer'е фильтрует).
**Обоснование:** minimal change, consistency с существующими bean
names и queue naming. PLAN не видел реальные имена — документ
корректируется в NOTES.

---

## D6 — Denormalize mapper: target user_id per event

**Дата:** 2026-04-24 (G3)
**Контекст:** `notification_history` индексирован по `user_id`. Для
каждого события нужно определить "кому принадлежит эта запись".
Event-schemas проверены — headman_user_id в events НЕ заложен.

**Маппинг event → target user_id (v0.0.0):**

| Event | target user_id | Заметка |
|-------|----------------|---------|
| `excuse.requested` | `payload.user_id` | студент (инициатор) — confirmation |
| `excuse.decided` | `payload.user_id` | студент (результат) |
| `late_checkin.requested` | `payload.user_id` | студент (инициатор) |
| `late_checkin.decided` | `payload.user_id` | студент (результат) |
| `late_checkin.decision` | `payload.user_id` | студент (результат, legacy name) |
| `lesson.started` | — | **skip** в v0.0.0 (широковещательный event на группу, не user-specific; STOMP push достаточно) |
| `lesson.closed` | — | **skip** (broadcast) |
| `lesson.cancelled` | — | **skip** (broadcast) |
| `attendance.marked` | `payload.user_id` | если есть поле — persist owner'у отметки |

**Решение v0.0.0:** consumer фильтрует через set support'нутых types.
Unsupported types skip'аются с `log.trace` (не warn — это не ошибка,
это design: broadcast events не попадают в per-user history).

**Future (v0.1):** headman-facing items (`excuse.requested` на стороне
старосты как actionable item) — требует резолвить `headman_id` по
`group_id`. Добавляем в `future-ideas.md`. В v0.0.0 староста увидит
input только через live STOMP push + свой webpanel-queue без history
persist.

**Обоснование (industry):** Gmail / Slack / GitHub history — persist
только per-user actionable items; broadcast announcements держат
в отдельной коллекции или не хранят. Мы идём тем же путём.

---
