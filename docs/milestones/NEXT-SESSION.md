# Промпт для следующей сессии — M10 G8+G9 (docs + audit + tag)

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам откроет
нужные файлы и продолжит.

---

**M10 Notification History: G1-G7 ✅ закрыты 2026-04-24 (7 коммитов в
dev ветке). Остались G8 (docs) + G9 (audit + smoke + tag
`v0.0.0-alpha.11`).**

Локальных коммитов ahead origin: **26**. Tags `v0.0.0-alpha.2..10`
локальные. Push всё ещё отложен до явного `go`.

**Старт новой сессии — дословно:**

> Читаю `docs/milestones/NEXT-SESSION.md` →
> `docs/milestones/M10-notification-history/{PLAN,CHECKLIST,NOTES,DECISIONS}.md`
> → `CLAUDE.md` для service table location. Запускаю **G8 Docs**:
>
> 1. `docs/architecture.md` — добавить раздел «Notification History
>    (M10)»: schema (document shape), consumer flow (fanout
>    `rut-uit.events` → queue `notification-web.history` → persist →
>    evict Caffeine), REST surface, Caffeine 30s + STOMP invalidation
>    pattern.
> 2. `docs/database-schema.md` — раздел Mongo `notification_db` →
>    collection `notification_history` + 3 индекса
>    (idx_user_sent_desc, idx_user_read, ttl_sent_at 30d).
> 3. `docs/data-retention-policy.md` — раздел 30d TTL для
>    notification_history (env `NOTIFICATION_HISTORY_TTL_DAYS`).
> 4. `CLAUDE.md` service table — `Notification Web | 9094 | Spring
>    Boot WebSocket (STOMP) + Caffeine | MongoDB (notification_db)`
>    + убрать старое примечание «stateless forwarder…» если есть.
> 5. `CLAUDE.md` v0.0.0 milestones table — обновить M10 статус на ✅.
>
> Коммит `docs(m10 G8): notification history architecture + schema + CLAUDE.md`.
>
> Далее **G9 Audit + smoke + tag**:
>
> 1. Запустить `docker compose down -v` → `docker compose up -d` →
>    дождаться healthy → проверить `docker logs rct-mongo-attendance`
>    что оба users созданы (MONGO_USER + MONGO_NOTIFICATION_USER);
>    `docker logs rct-notification-web` без auth-errors + Mongo URI
>    = notification_db.
> 2. Запустить `NotificationHistoryConsumerIT` Testcontainers:
>    `./gradlew.bat :services:notification-service:notification-app:integrationTest
>    --tests "*NotificationHistoryConsumerIT" --no-daemon`. Если
>    TTL-index creation fail на Mongo 7 — hot-patch.
> 3. Запустить `security-auditor` агента на diff M10 (коммиты
>    `d6c0f14..4615e23`) + `bug-hunter`. Параллельно если
>    возможно.
> 4. HIGH findings — hot-patch commits; MEDIUM/LOW — defer в
>    `future-ideas.md` если не блокер.
> 5. Обновить CHECKLIST G9, добавить **Post-mortem** в PLAN.md
>    (что пошло не по плану, lessons learned).
> 6. Финальный коммит `docs(m10): CHECKLIST G9 final ticks +
>    post-mortem + hand-off для M11`.
> 7. `git tag v0.0.0-alpha.11 -m "M10 Notification History закрыт"`.
> 8. Обновить `NEXT-SESSION.md` на M11 OpenAPI Polish.

Stop при сюрпризе → NOTES + спросить.

---

## M10 G1-G7 summary (что уже сделано)

| Группа | Commit | Scope |
|--------|--------|-------|
| G1 | `d6c0f14` | Mongo init-script (оба user'а, PoLP), compose×2, secret-rotation runbook, future-ideas collMod |
| G2 | `8746e66` | notification-api-contract: NotificationType enum, NotificationHistoryDto class, UnreadCountDto record, NotificationApi interface, spring-data-commons |
| G3 | `cc4b05b` | NotificationHistoryDocument + 3 индекса (TTL env-driven) + Repository + Rabbit history queue+DLQ + Consumer (маппер 9 events, broadcast skip, error isolation) + 12 unit + IT |
| G4+G5 | `1624346` | CaffeineConfig (unread-count 30s) + NotificationHistoryService (@Cacheable/@CacheEvict + invalidate) + NotificationController (4 endpoints) + gateway route + 11 tests |
| G6 | `e6b3c34` | PWA: notificationsApi (HATEOAS parser) + useNotificationHistory TanStack hooks + NotificationCenter invalidate на STOMP + markAllRead best-effort backend sync + 5 tests |
| G7 | `4615e23` | web-panel: notification-history.api (Signal-based) + integration в NotificationCenterService + 5 tests |

**DECISIONS D1-D7:**
- D1: Модуль notification-api-contract уже был, добавляем классы рядом.
- D2: Отдельный `notification_user` (PoLP) через init-mongo.js.
- D3: SPRING_DATA_MONGODB_URI переключён с `attendance_db` на
  `notification_db`.
- D4: TTL env `NOTIFICATION_HISTORY_TTL_DAYS` default 30.
- D5: Queue `notification-web.history` + binding на существующий
  fanout `rut-uit.events` (PLAN говорил `notification.events`).
- D6: Маппер persist только 9 user-facing events с `payload.user_id`;
  broadcast (lesson.*) skip.
- D7: PWA/web-panel hybrid — sessionStorage+STOMP остаются
  authoritative для live UX; backend REST — для cross-session sync.

**Deferred в v0.1 (записано в future-ideas.md):**
- Notification retention `collMod` auto-reconciler.
- PWA infinite-scroll UI на server-side history.
- Optimistic mutations.
- Headman-facing items (excuse.requested из стороны старосты) с gRPC
  resolve `headman_id`.

**Test counts:**
- backend notification-app: 23 unit (map 7 + consumer 5 + service 6
  + controller 5) + 1 IT (запуск отложен в G9)
- PWA: 5 new (10/10 passing)
- web-panel: 5 new (11/11 passing)

## Правила (без изменений)

- **Русский язык** в отчётах / NOTES / ответах.
- **Ветка `dev`**. Push на `main`/`origin` — только с явного `go`.
- Не звать `gsd-*` агентов. `Explore` для «найти все X»,
  `bug-hunter` + `security-auditor` — только в G9.
- Surprise → NOTES.md + спросить до продолжения.
- Hook-reminder'ы READ-BEFORE-EDIT после Read в той же сессии — **ложные**, edit применяется.
- Атомарные коммиты per группа.

## G8 + G9 точки внимания

**G8:**
- `docs/architecture.md` сейчас описывает notification-web как
  stateless forwarder — надо явно пометить M10 переход на stateful.
- `docs/database-schema.md` — поискать раздел «MongoDB / attendance_db»,
  добавить параллельно notification_db.
- В `CLAUDE.md` таблица services — строка Notification Web уже
  содержит «MongoDB (notification_db) — stateful history store в M10
  (NEW-166/167/168); до M10 stateless event forwarder» — проверить
  корректность и убрать «до M10».

**G9 потенциальные сюрпризы:**
- **Mongo volume уже существует** — init-script запустится ТОЛЬКО
  после `docker compose down -v`. Это **breaking dev data** (attendance
  collections будут пустые до re-seed). Спросить user'а перед `down -v`.
- **Mongo 7 TTL-index**: Spring Data MongoDB 4.x `expire(Duration)`
  — новый API; если не сработает → fallback на `expireAfterSeconds`
  через `IndexInfo.unique(false)`.
- **Testcontainers reuse=true** — `~/.testcontainers.properties`
  должен быть. Без него каждый прогон стартует свежий.
- **Rabbit binding collision**: `notification-web.history` bind'ится
  к существующему `rut-uit.events`. Если queue уже создана другим
  сервисом — проверить declaration.
- **Security-auditor возможные findings**:
  - `@RequireRole({STUDENT, TEACHER, ADMIN})` — TEACHER видит свою
    историю (OK), ADMIN тоже (OK), но admin не привязан к группе —
    ACL по userId достаточно.
  - `payload` stored raw — если event содержит sensitive data (FCM
    token? otp code?) — утекает в notification_history. Проверить
    event-schemas на sensitive fields.
  - `invalidateUnreadCount` вызывается из Rabbit consumer после
    save — если user удалён между save и invalidate, userId stale
    в cache. Low severity.
- **Bug-hunter возможные findings**:
  - TTL index с `expireAfterSeconds` изменяется только через collMod
    — если env var меняется, существующий index остаётся с старым
    TTL. Уже в future-ideas.md.
  - `markRead` `@Query+@Update` возвращает `long` modified count —
    Spring Data derived-update method; если драйвер MongoDB не
    поддерживает — проверить в IT.

---

## История предыдущих milestone (архив)

M01-M08 ✅ (see предыдущие версии NEXT-SESSION.md)
M09 Prod Release Blockers ✅ 2026-04-24 (tag `v0.0.0-alpha.10` локальный)
**M10 Notification History 🔄 G1-G7 ✅, G8+G9 — эта сессия**
M11 OpenAPI Polish ⬜ (SharedOpenApiCustomizer + nginx basic-auth + conformance CI)
M12 Auth Contract-first Refactor ⬜ (планирование в v0.0.0; реализация v0.1)

Dependency graph и полный roadmap — `docs/milestones/README.md`.

## Ожидающие явного `go`

1. `git push origin dev` — **26 коммитов** ahead (станет ~30+ после G8+G9).
2. `git push origin --tags` — **9 tags**, станет 10 после M10 tag.
3. После M10 → **M11 OpenAPI Polish**. См.
   `docs/milestones/M11-openapi-polish/PLAN.md`.
