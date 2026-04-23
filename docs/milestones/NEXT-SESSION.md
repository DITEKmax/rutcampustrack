# Промпт для следующей сессии — M10 Notification History

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам откроет
нужные файлы и стартанёт milestone.

---

**M09 Prod Release Blockers ✅ закрыт 2026-04-24, tag `v0.0.0-alpha.10`
локально. Следующий milestone — M10 Notification History:
`notification-web` из stateless event-forwarder'а в stateful сервис
с Mongo + pagination REST + Caffeine unread-count.**

Локальных коммитов ahead origin: **20**. Tags `v0.0.0-alpha.2..10`
локальные. Push всё ещё отложен до явного `go`.

**Старт следующей сессии — дословно:**

> Читаю `docs/milestones/NEXT-SESSION.md` →
> `docs/milestones/M10-notification-history/{PLAN,CHECKLIST,NOTES}.md` →
> `99-executive-summary.md` строки 120 и 170 (P2-6/4) →
> `05-notification-service.md` P2-6/4 → `OWNER-ANSWERS.md` строки
> 5021-5131 (P2-6/4 вариант b FULL). Старт с **Группы 1 MongoDB
> schema setup (NEW-166)**: `infra/mongo-init/notification-db-init.js`
> + `docker-compose*.yml` mount init-script и env
> `MONGO_NOTIFICATION_USER/PASSWORD`. Коммит
> `feat(infra): notification_db init + mongo user (M10 G1, NEW-166)`.
> Далее **Группа 2 notification-api-contract module** — новый модуль
> per contract-first rule (БЕЗ Lombok): `NotificationType enum`,
> `NotificationHistoryDto record`, `UnreadCountDto record`,
> `NotificationApi interface` с `@RequestMapping + @Operation +
> @ApiResponse`. Коммит
> `feat(notif): notification-api-contract module (M10 G2, NEW-167)`.

Каждая группа — отдельный атомарный коммит. Stop при сюрпризе → NOTES
+ спросить.

---

## M10 — исходный статус (на 2026-04-24 старт)

Все 9 групп в CHECKLIST:

| Группа | Scope |
|--------|-------|
| G1 MongoDB schema setup | `notification_db` init + user + docker-compose mount |
| G2 notification-api-contract module | DTO + interface + enum |
| G3 Backend entity + repository + consumer | Entity + Mongo indexes + `@RabbitListener` |
| G4 Service + Caffeine cache | `CaffeineConfig` + `@Cacheable unread-count` + evict |
| G5 Controller + Gateway route | `NotificationController implements NotificationApi` + routing |
| G6 Frontend integration | PWA NotificationCenter backed by REST + STOMP |
| G7 STOMP cache invalidation | Publisher evicts cache при publish new event |
| G8 Docs | `docs/architecture.md` раздел 3.5 expansion + `database-schema.md` notification_db |
| G9 Audit + tag | security-auditor + bug-hunter на diff M10, hot-patches, tag `v0.0.0-alpha.11` |

## Правила (без изменений)

- **Русский язык** в отчётах / NOTES / ответах.
- **Ветка `dev`**. Push на `main`/`origin` — только с явного `go`.
- Не звать `gsd-*` агентов. `Explore` для «найти все X»,
  `bug-hunter` + `security-auditor` — только в G9.
- Surprise → NOTES.md + спросить до продолжения.
- Micro-решение → DECISIONS.md (D1, D2... свежий счёт в M10).
- Закрыл пункт CHECKLIST → `[x]` через Edit (commit hash в описании).
- **Hook-reminder'ы READ-BEFORE-EDIT после Read в той же сессии — ложные**, edit применяется.
- Атомарные коммиты per группа. Если группа >6ч работы — разрежь её.

## Ожидающие явного `go`

1. `git push origin dev` — **20 коммитов** ahead (rising до ~35+
   после M10).
2. `git push origin --tags` — **9 tags** (`v0.0.0-alpha.2..10`),
   станет 10 после M10 tag.
3. После M10 → **M11 OpenAPI Polish** (SharedOpenApiCustomizer
   наполнение + @Schema на DTO + nginx basic-auth на prod /swagger-ui
   + OpenAPI↔runtime conformance CI). См.
   `docs/milestones/M11-openapi-polish/PLAN.md`.

---

## Ключевые факты для M10 старта (опорный context)

**Почему переделываем notification-web (P2-6/4).** Сервис был заложен
в M04 как stateless forwarder (RabbitMQ → STOMP push), но по факту в
prod'е потребовалось:
- Пользователь хочет видеть **историю** уведомлений после login (сейчас
  потерянные при disconnect).
- Badge unread-count в UI требует persistent state.
- Owner-решение (OWNER-ANSWERS.md:5021-5131) — вариант (b) FULL:
  новая DB `notification_db`, pagination REST API, TTL 30d на
  документах, Caffeine для unread-count.

**Архитектурно:**
- Fanout exchange `notification.events` → 2 queue: `notification.delivery`
  (существующий STOMP push) + `notification.history` (новый persister).
- Два consumer'а в одном контейнере `notification-web` (разделены по
  queue'am, error в одном не влияет на другой через ACK).
- Mongo DB `notification_db` — отдельная от `attendance_db` (разные
  сервисы по P2-9/6 principle).
- Caffeine L1 cache per-instance (единственный instance в MVP; если
  будет scale — evict через STOMP broadcast event).

**M09 достижения (для context'а):**
- OTP через RabbitMQ event (не HTTP body)
- `lesson.cancelled` — full snapshot schema
- latecheckin/bot handlers — 70% coverage gate
- docker-compose.prod.yml — mem_limits, JVM opts, Prom alert'ы
- 2 HIGH findings из G9 audit deferred в v0.1 — см.
  `docs/future-ideas.md` «OTP hardening bundle (v0.1)»:
  - SA-H1 `verifyOtpByCode` без attempts counter
  - BH-H1 bot dispatcher event_id дедуп отсутствует

**DECISIONS накопленные в M09** (D1-D7, пример для следования
pattern'у в M10):
- **D1-D3** — G1 детали.
- **D4** — OTP через DomainEventListener, НЕ shared-outbox.
- **D5** — `lesson.deleted` оставлен как отдельный use-case.
- **D6** — `excuse.decided` single event со status-полем.
- **D7** — G9 audit HIGH findings deferred в v0.1.

**Coverage на момент закрытия M09**:
- handlers bot = 92.83%, bot overall = 77.17% (baseline 50%)
- JaCoCo ratchet 60% LINE + latecheckin 70% activated

**Ключевые commits M09 для context'а:**
```bash
git log --oneline 2996652~1..HEAD  # все 20 коммитов M09 (G1-G9)
```

---

## История предыдущих milestone (архив)

M01 Shared Foundations ✅ 2026-04-19
M02 Reliable Eventing ✅ 2026-04-19
M03a Internal JWT + Rate-limit ✅ 2026-04-20
M03b Secure Boundaries Part B ✅ 2026-04-20
M04 Observability ✅ 2026-04-20
M05 Performance ✅ 2026-04-21
M06 Ops & Supply Chain ✅ 2026-04-21
M07 Frontend Hardening ✅ 2026-04-22 (tag `v0.0.0-alpha.8` локальный)
M08 Test Infrastructure ✅ 2026-04-23 (tag `v0.0.0-alpha.9` локальный)
M09 Prod Release Blockers ✅ 2026-04-24 (tag `v0.0.0-alpha.10` локальный)
**M10 Notification History ⏳ — эта сессия (tag `v0.0.0-alpha.11` после G9)**
M11 OpenAPI Polish ⬜
M12 Auth Contract-first Refactor ⬜ (планирование v0.0.0; реализация v0.1, см. future-ideas.md)

Dependency graph и полный roadmap — `docs/milestones/README.md`.
