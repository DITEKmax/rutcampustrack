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
