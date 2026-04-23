# Промпт для следующей сессии — M09 Группа 9 (Audit + Release)

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам откроет
нужные файлы, поймёт где мы стоим и добьёт релиз.

---

**M09 Prod Release Blockers почти готов: G1-G8 закрыты, осталась G9 Audit + tag.**
Нет приоритетных bug'ов в очереди. В новой сессии нужно: прогнать
полный build + pytest, запустить 2 агента на diff M09, применить
hot-patches если найдутся, поставить tag `v0.0.0-alpha.10` локально.

Локальных коммитов ahead origin: **17**. Tags `v0.0.0-alpha.2..9`
локальные. Push всё ещё отложен до явного `go`.

**Старт следующей сессии — дословно:**

> Читаю NEXT-SESSION → CHECKLIST M09 Группу 9 → последний commit
> `4fa58a4`. **Сначала прогон** `./gradlew build` +
> `cd services/notification-bot && py -m pytest tests/ --override-ini="addopts=" --cov=bot/handlers --cov-fail-under=70`.
> Если красное — фикс, коммит `fix(m09 G9): <explanation>`. Когда
> зелёное — **параллельные агенты** `security-auditor` и `bug-hunter`
> на diff M09 (25 commits от `2996652` до `4fa58a4`) — focus areas
> ниже. Hot-patches → commit'ом, повторный прогон, green → **tag**
> `git tag -a v0.0.0-alpha.10 -m "M09 Prod Release Blockers"` +
> финальный **`docs(m09): close M09 + hand-off для M10`** commit.
> Обновить `NEXT-SESSION.md` под M10.

---

## M09 — текущий статус (на 2026-04-24)

| Группа | Статус | Commit |
|--------|--------|--------|
| G1 Quick wins (P0-5 MessageDigest + P0-6 cleanupOrphans + P0-2 landing) | ✅ | `2996652..0c465f1` |
| G2 OTP через RabbitMQ event (08 P0-2) | ✅ | `3d6dfd1..bda6a35` |
| G3 latecheckin tests (14 P0-1) | ✅ | `48a63f7` |
| G4 bot callback tests + 70% handlers (14 P0-2, 14 P1-7) | ✅ | `25da2d9` |
| G5 lesson.cancelled full snapshot + V13 migration (02 P2-11/5) | ✅ | `b5a7e2e` |
| G6 headman role check + NEW-121 audit (06 P1-1) | ✅ | `e332d41` |
| G7 prod-deploy-checklist + runbooks + compose mem_limits (NEW-154/155/157) | ✅ | `c5bf621` |
| G8 admin-scripts + future-ideas + CLAUDE/CHANGELOG | ✅ | `4fa58a4` |
| **G9 Audit + tag** | ⏳ | — |

## G9 Scope (~0.5д)

### 1. Зелёный прогон — до запуска агентов

```bash
# Backend
export JAVA_HOME="C:/Users/maksd/.jdks/ms-21.0.9"
./gradlew.bat build --no-daemon
# Должно быть BUILD SUCCESSFUL. Ratchet 60% LINE + latecheckin 70% активен.

# Python bot
cd services/notification-bot
py -m pytest tests/ --override-ini="addopts=" --cov=bot/handlers --cov-fail-under=70
# Должно: 198 passed, handlers coverage 92.83%.

py -m pytest tests/
# Должно: 198 passed, bot/ coverage 76.86% (>50% baseline).
```

Если красное — **не двигаться к агентам**, фикс + коммит, повторный прогон.

### 2. Параллельные агенты (single message, 2 Agent tool calls)

**security-auditor** — focus:
- OTP event flow: кто в Rabbit читает `otp.requested` кроме bot? DLQ на bot-down (TTL Redis 120s vs Rabbit retry)? Race при parallel `/auth/otp/request` от того же telegramId (cooldown/attempts, но что внутри окна Rabbit-retry)?
- `OtpService.verifyOtp` constant-time корректность — ветвление по input-size ещё остаётся?
- `_verify_headman` fail-closed на gRPC error — не ломает UX при кратковременном academic outage?
- headman role check coverage (что если `found=true` + `is_headman=null`?)
- V13 миграция: legacy cancelled-строки с `cancelled_by=NULL` — downstream consumer'ы на это готовы?
- `lesson.deleted` grep — не осталось ли orphan-references после G5 scope-decision D5.

**bug-hunter** — focus:
- Outbox publisher retry для `lesson.cancelled` full snapshot — дубли приводят к двойному edit_text у студентов? (Schema `event_id` должен уникализировать через attendance idempotency, проверить).
- `otp.requested` retry → bot получит 2 кода, студент увидит второе сообщение → first message stale. Tracker `store_pending_user_msg` handle'ит?
- Aiogram fake-updates edge cases в новых тестах (forwarded callback'и с старым data → handler не должен falsly publish).
- Flyway V10 → V13 rename (я переименовал из-за conflict с V10__shedlock_table.sql) — проверить что MigrationIT test'ы не broken, checksum validation в prod'е не ломается после upgrade.
- Role check — user.found=True но `is_headman=None` (proto optional) — `getattr(user, "is_headman", False)` returns `None`, `not None == True` → **пропустит студента**? Проверить.

### 3. Hot-patches

Любой finding — отдельный fix-commit: `fix(m09 G9): <short>`. НЕ
squash'ить в G-commits (audit trail важнее pretty log'а для M09).

### 4. Финализация

- **Post-mortem** в `docs/milestones/M09-prod-release-blockers/PLAN.md`:
  календарное время, reality vs estimate, lessons learned (особенно
  G2.6 RabbitConfig debug'инг — 2 неверных гипотезы до root cause).
- **Tag** локально:
  ```bash
  git tag -a v0.0.0-alpha.10 -m "M09 Prod Release Blockers — closed"
  ```
- **Финальный коммит**: `docs(m09): close M09 + hand-off для M10`:
  - CHECKLIST Группа 9 → ✅
  - NEXT-SESSION под M10 (см. M10 CHECKLIST — `docs/milestones/M10-notification-history/`)
  - PLAN.md post-mortem выше

---

## Правила (без изменений)

- **Русский язык** в отчётах / NOTES / ответах.
- **Ветка `dev`**. Push на `main`/`origin` — только с явного `go`.
- Не звать `gsd-*` агентов. `Explore` для «найти все X»,
  `bug-hunter` + `security-auditor` — только в G9 (этой сессии).
- Surprise → NOTES.md + спросить до продолжения.
- Micro-решение → DECISIONS.md (D7, D8... — продолжаем нумерацию, D1-D6 уже).
- Закрыл пункт CHECKLIST → `[x]` через Edit (commit hash в описании).
- **Hook-reminder'ы READ-BEFORE-EDIT после Read в той же сессии — ложные**, edit применяется.

## Ожидающие явного `go`

1. `git push origin dev` — **17 коммитов** ahead (25 всего после G9).
2. `git push origin --tags` — **8 tags** (`v0.0.0-alpha.2..9`), станет 9 после G9 tag.
3. После M09 → **M10 Notification History** (stateful notification-web
   + Mongo notification_db + TTL 30d + Caffeine unread-count). См.
   `docs/milestones/M10-notification-history/PLAN.md`.

---

## Ключевые факты для G9 agents (опорный context)

**Root cause G2.6 AuthOtpFlowIT** (для security-auditor — похожие
ловушки могут быть в attendance/schedule): user `@Configuration` +
`@ConditionalOnBean(ConnectionFactory.class)` оценивается ДО
`RabbitAutoConfiguration`, condition всегда false. Fix: убран
`@ConditionalOnBean`, listener через `@Bean` в RabbitConfig.
`catch(AmqpException)` → `catch(Exception)` (Jackson бросает
MessageConversionException, не AmqpException).

**DECISIONS накопленные в M09** (6 штук):
- **D1-D3** — G1 детали.
- **D4** — OtpRequestedPublisher как отдельный класс НЕ создан (OTP
  эфемерен, shared-outbox persistence нарушает security-модель).
- **D5** — `lesson.deleted` НЕ удаляется в G5 (physical DELETE, не
  синоним cancelled; attendance orphan-cleanup зависит).
- **D6** — `excuse.decided` остаётся single event (status=approved|rejected),
  не разбиваем на `excuse.approved/rejected` (симметрия с late_checkin,
  избегаем дублирования consumer-кода).

**Deviations от CHECKLIST** (помечены `[~]`, не блокируют закрытие):
- G2: OtpRequestedPublisher не создан (D4); Python contract-тест
  пропущен (jsonschema не в deps, Java publisher-side достаточно).
- G3: event-schemas approved/rejected не созданы (в коде single
  `late_checkin.decided`); integration fake-updates test пропущен.
- G4: integration fake-updates test пропущен.
- G5: `lesson.deleted` НЕ удалено (D5); NEW-119 UI one-off lessons
  отложен в M09 G9 cleanup (теперь = эта сессия, можно проверить).
- G6: excuse.approved/rejected schemas не созданы (D6); integration
  fake-updates test пропущен.
- G7: nginx/certbot/node-exporter/cadvisor/promtail без mem_limit
  (safety-alert `ContainerWithoutMemoryLimit` напомнит); staging
  smoke — пройдёт при prod deploy (не в этой сессии).

**Coverage**: handlers bot = 92.83%, latecheckin jacoco 70% LINE
активирован, bot/ overall 76.86% (>50% baseline), JaCoCo ratchet 60%
LINE держится.

**Ключевые commits M09 для diff-агентов:**
```bash
git log --oneline 2996652..4fa58a4
# или полный diff: git diff 2996652~1..4fa58a4
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
**M09 Prod Release Blockers ⏳ G1-G8 ✅ / G9 Audit — в этой сессии (tag `v0.0.0-alpha.10` локально после G9)**
M10 Notification History ⬜ (следующая — stateful notification-web)
M11 OpenAPI Polish ⬜
M12 Auth Contract-first Refactor ⬜ (планирование v0.0.0; фактическая реализация — v0.1, см. future-ideas.md)

Dependency graph и полный roadmap — `docs/milestones/README.md`.
