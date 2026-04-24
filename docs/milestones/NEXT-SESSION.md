# Промпт для следующей сессии — M13 Pre-Deploy Hardening

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам
откроет нужные файлы и продолжит.

---

**M12 Auth Contract-first Refactor ✅ ЗАВЕРШЁН 2026-04-24
(tag `v0.0.0-alpha.13` локально). Все 12 milestone'ов v0.0.0 закрыты.**

**Сейчас идёт M13 — Pre-Deploy Hardening (VPS GA blockers).**
**После M13 → tag `v0.0.0` GA → push → VPS deploy.**

## Прогресс M13 на 2026-04-25

**7 из 24 групп закрыто** (G1-G7). 72 коммита ahead origin/dev.
Push отложен до M13 completion + явного `go`.

| G | Commit | Тема | Итог |
|---|--------|------|------|
| 1 | `7253e59` | Flaky tests unblock | 3 теста подтверждены зелёными (починены ранее в M06 G9) |
| 2 | `76c7e75` | Rate-limit semantics | true X req/min через `replenishRate=1/burstCapacity=60/requestedTokens=60/X` |
| 3 | `dfb5419` | Pagination global cap | shared-web `PageableDefaultsPostProcessor` + 3 IT |
| 4 | `9e0bb6a` | `/auth/refresh-body` removed | backend + frontend regenerate (BREAKING) |
| 5 | `94e1072` | InvalidParam alias | legacy record + frontend fallback удалены |
| 6 | `7073de8` | Mongo indexes fail-fast | `verifyIndexes()` throws + IT + runbook |
| 7 | `0301c1b` | Mongo replica set + @Transactional | Option B (bitnami/mongodb:7.0), closes M02 CRITICAL #1 |

**Следующая — Группа 8 (Consumer-side dedup по event_id, M02 CRITICAL #2).**
Самая большая группа M13: 10 checklist items.

## Key decisions зафиксированы в NOTES

- **Rate-limit формула** (G2): `burstCapacity=60, requestedTokens=60/X`
  (владелец в NOTES.md:46 описал `bc=5`, это было некорректно —
  Spring не списывает больше токенов чем в бакете).
- **Mongo RS** (G7): Option B = `bitnami/mongodb:7.0`, declarative
  env-based RS setup. Альтернативы A (custom entrypoint) и C (skip
  transactions) отклонены владельцем 2026-04-25.
- **InvalidParam** (G5): checklist описывал направление миграции
  инвертированно — реально удалён `InvalidParam.java` + frontend
  fallback на pre-M11 shape. Backend canonical `ErrorResponse.fieldErrors`
  не менялся (он и был canonical после M11 G0).

## Старт новой сессии — дословно

> Читаю в порядке:
>
> 1. `docs/milestones/NEXT-SESSION.md` — этот файл (cursor позиция, текущая группа)
> 2. `docs/milestones/M13-pre-deploy-hardening/PLAN.md` — scope, 23 acceptance criteria
> 3. `docs/milestones/M13-pre-deploy-hardening/CHECKLIST.md` — 24 группы, атомарные задачи
> 4. `docs/milestones/M13-pre-deploy-hardening/NOTES.md` — решения владельца + nuance'ы для executor'а
> 5. `docs/report-before-v0.0.0/v0.0.0-debt.md` — debt report (контекст зачем делаем каждую группу)
> 6. `docs/milestones/README.md` — таблица всех milestone'ов
> 7. `CLAUDE.md` — правила кодирования проекта
>
> Далее ищу в `CHECKLIST.md` **первую группу, где есть unchecked `[ ]` пункты**,
> и продолжаю с неё. Перед началом работы спрашиваю владельца:
> «Начинаю **Группу X — {название}**. OK или взять другую?»

## Правила M13 (как во всех предыдущих milestone'ах)

- **Русский язык** в отчётах / NOTES / вопросах / checklist updates.
- **Ветка `dev`**. Push на `main`/`origin` — только с явного `go` после GA tag.
- Не звать `gsd-*` агентов. `code-reviewer` + `security-auditor` — на финал milestone'а (Группа 24).
- **Surprise** (отклонение от PLAN / неожиданный scope / разногласие) → фикс в `M13-pre-deploy-hardening/NOTES.md` + **спросить владельца до продолжения**.
- Hook-reminder'ы READ-BEFORE-EDIT после Read в той же сессии — **ложные**, edit применяется.
- **Атомарные коммиты per checklist item** (или per группа если group 3-5 related items).
- CRLF warnings на Windows — **нормально**.
- `$env:JAVA_HOME = "C:\Users\maksd\.jdks\ms-21.0.9"` перед `.\gradlew.bat build`.

## Scope M13 (23 тематических группы + G24 финализация)

Порядок выбран так, чтобы early-groups давали dev-feedback loop для последующих.
Полный checklist — `M13-pre-deploy-hardening/CHECKLIST.md`.

| G | Тема | Cross-ref v0.0.0-debt |
|---|------|----------------------|
| 1 | 3 pre-existing flaky тестов fix (`EventSchemaRefTest`, `RateLimitIT`, `ExcuseEventContractIT`) | dev-unblock |
| 2 | Rate-limit `replenishRate` семантика (реальные 5 req/min вместо 300) | M03a |
| 3 | Pageable max-size cap global (`spring.data.web.pageable.max-page-size=100`) | M10 H2 |
| 4 | `/auth/refresh-body` удаление (Sunset 2026-06-01) | M03b |
| 5 | `InvalidParam` deprecated alias migration (удалить `fieldErrors`) | M11 |
| 6 | Mongo TTL + compound indexes startup verify | M10 S4 |
| 7 | Mongo replica set + `@Transactional` (attendance + notification-web) | M02 CRITICAL #1 |
| 8 | Consumer `event_id` dedup — 5 consumer'ов (4 Java + bot) + Python equiv | M02 CRITICAL #2 |
| 9 | `SecurityIdorIT` в 4 сервисах (NEW-31, призрак через 3 milestone) | M03a |
| 10 | `/actuator/**` exclude from tracing sampling (custom OTel Sampler) | M06 |
| 11 | `mem_limit` на nginx/certbot/exporters/cadvisor/promtail | M09 |
| 12 | `healthcheck:` в docker-compose + Spring health indicators | M04 G4 |
| 13 | `.env.prod.example` + `scripts/validate-env-prod.sh` | deploy hygiene |
| 14 | Prometheus + Alertmanager UI за nginx basic-auth + nginx fail-fast | M11 G4 |
| 15 | `scripts/backup.sh` + `scripts/restore.sh` + tested restore + `.env.prod` GPG | DR |
| 16 | CSP audit + `/api/csp-report` endpoint + metric | M07 + NEW-54 |
| 17 | Grafana dashboards sanity + retention (Prometheus/Tempo 14d) | M04 |
| 18 | WebSocket nginx config + STOMP heartbeat + offline/online smoke | M07 |
| 19 | Alertmanager → Telegram E2E smoke + `docs/alerts.md` каталог 15+ alert'ов | M04 G9 |
| 20 | Certbot renewal hook + blackbox-exporter SSL expiry alert < 30d | availability |
| 21 | Flyway `CONCURRENTLY` ArchUnit guard + runbook EXPLAIN 2-weeks | M05 |
| 22 | Playwright E2E auth flow (login/logout/refresh/WS reconnect) | M03b → M08 → M13 |
| 23 | VPS deploy runbook dry-run (fresh docker-compose / Ubuntu VM) | M09 |
| 24 | Финальная верификация + code-reviewer + security-auditor + tag | GA |

**Estimate:** 5-7 человеко-дней.

## Ключевые решения владельца (из сессии 2026-04-24)

Контекст почему каждая группа входит в M13 — `v0.0.0-debt.md`. Конкретные
варианты решений записаны в `M13-pre-deploy-hardening/NOTES.md` раздел
«Итоговые решения владельца по спорным пунктам».

**Топ-5 самых важных для executor'а:**

1. **Rate-limit math (G2):** `replenishRate=1`, `burstCapacity=5`, `requestedTokens=12` → ровно 5 req/min. UX: после 5 ошибок — 12 сек на следующий запрос.
2. **Mongo replica set (G7):** `rs.initiate()` на standalone Mongo → restart с `--replSet rs0`. И в dev, и в prod. `MongoTransactionManager` bean нужен для **обоих** сервисов (attendance + notification-web).
3. **Consumer dedup schema (G8):** `event_consumer_processed (consumer_id, event_id, processed_at)` + `UNIQUE(consumer_id, event_id)`. В Mongo — compound unique index. Cleanup через ShedLock-job раз в день, delete `processed_at < now() - 7 days`.
4. **refresh-body удаляем (G4):** даже если grep покажет usage в frontend — прод с тестовыми данными, пользователей попросим перезайти. Сначала меняем frontend на cookie-flow, потом удаляем endpoint.
5. **Backup (G15):** 4 DB + `.env.prod` GPG-encrypted, 7-day retention, VPS-snapshot хостера остаётся как страховка 2-го уровня. Tested restore обязателен при setup'е.

## Запрос владельца по тестам

Владелец на старте M13 сказал: «при выполнении скажу написать тесты».
**Значит при старте каждой группы, где задача требует новые тесты
(new endpoint / new service / new security check), executor должен
явно спросить у владельца: "писать тесты для группы X?"** До ответа —
реализация без тестов (только implementation + runtime smoke).

## Git state на 2026-04-25 (после сессии G1-G7)

```
0301c1b feat(attendance): Mongo replica set + @Transactional для outbox atomicity (M13 G7)
7073de8 feat(notification): fail-fast startup verify Mongo indexes (M13 G6)
94e1072 refactor: удалён InvalidParam deprecated alias (M13 G5)
9e0bb6a feat(auth)!: удалён /auth/refresh-body (M13 G4)
dfb5419 feat(shared-web): global Pageable cap max-page-size=100 (M13 G3)
76c7e75 fix(gateway): rate-limit semantics настоящий X req/min (M13 G2)
7253e59 docs(m13): старт M13 + закрытие Группы 1 (flaky tests уже зелёные)
...
```

Ahead origin/dev: **72 коммита** (push отложен до GA tag после M13).
Локальный tree чистый (untracked: `.coverage`, `docs/report-before-v0.0.0/v0.0.0-debt.md`).

## Hand-off для Группы 8 (Consumer-side dedup по event_id)

**Cross-ref debt:** M02 CRITICAL #2. Consumer duplicates (RabbitMQ
re-delivery) могут обработать один event дважды. Сейчас дедупа нет
ни в academic/schedule/attendance/notification-web (Java consumer'ы),
ни в notification-bot (Python).

**Owner-sanctioned schema (NOTES.md:48, debt.md и NEXT-SESSION.md:116):**

```sql
-- PostgreSQL (academic + schedule)
CREATE TABLE event_consumer_processed (
    consumer_id   VARCHAR(100) NOT NULL,
    event_id      VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(consumer_id, event_id)
);
CREATE INDEX idx_ecp_cleanup ON event_consumer_processed (processed_at);
```

```javascript
// MongoDB (attendance + notification-web): compound unique index
// на коллекции event_consumer_processed
db.event_consumer_processed.createIndex(
  { consumer_id: 1, event_id: 1 },
  { unique: true, name: 'uniq_consumer_event' }
);
db.event_consumer_processed.createIndex(
  { processed_at: 1 },
  { name: 'idx_ecp_cleanup' }
);
```

**Cleanup policy:** ShedLock-job раз в день, delete
`processed_at < now() - 7 days`. Retention 7d достаточно — RabbitMQ
не хранит события дольше (dead-letter TTL + autoAck pattern).

**10 checklist items для G8:**

1. `shared-events/EventIdempotent` — аннотация + aspect (вокруг @RabbitListener).
2. Schema + Flyway migration V{N+1} для academic + schedule (PostgreSQL).
3. Collection + index init для attendance + notification-web (Mongo).
4. `@EventIdempotent(consumer="academic")` × academic consumer methods.
5. `@EventIdempotent(consumer="schedule")` × schedule consumer methods.
6. `@EventIdempotent(consumer="attendance")` × attendance consumer methods.
7. `@EventIdempotent(consumer="notification-web")` × notification consumer methods.
8. Python equiv в notification-bot: Redis `SET consumed:{event_id} NX EX 3600` + early-return при hit.
9. IT per service (4 Java + Python): publish event × 2 → consumer видит × 1.
10. ShedLock cleanup job для Postgres + Mongo (может быть shared в shared-events).

**Сложности / nuances для executor'а:**

- **Aspect около @RabbitListener:** нужно Spring-AOP pointcut
  `@annotation(EventIdempotent)`. Method должен принимать `Message` или
  `@Header("event_id")`. Extract event_id из payload (JSON) либо из
  `x-rut-event-id` RabbitMQ header (проверить что публишер его ставит —
  `shared-outbox` OutboxPublisherJob должен это делать).
- **Mongo collection для dedup** — deploy'ить как часть shared-outbox
  (та же Mongo connection) или как отдельную collection в domain-db?
  Скорее всего в domain-db каждого сервиса (простое `@EnableMongoAuditing`
  на Config).
- **Postgres Flyway versioning:** проверить текущий V{N} на каждом
  сервисе: academic последний был, schedule, etc. (`grep "V[0-9]" services/*/src/main/resources/db/migration`).
- **Aspect — unit-testable**: можно вывести в абстракцию `IdempotencyStore`
  с Jpa/Mongo impl (аналог `OutboxStorage` pattern из M02).
- **Python dedup**: использовать существующий Redis connect в
  `notification-bot`. Pattern — `SET consumed:{event_id} 1 NX EX 3600`.
  Ключ-префикс отдельный от OTP/reminder ключей.
- **Tests:** для G8 **нужны** новые IT (checklist требует IT per service).
  Перед стартом каждого new-IT item в G8 sprosi владельца «писать
  тесты для G8.X?».

**Рекомендую разбить G8 на 2-3 коммита:**
- G8-infra: `EventIdempotent` aspect + helper + schema/collection
  (без attach к consumer'ам). Тесты helper'а unit-level.
- G8-apply-java: `@EventIdempotent` на 4 сервисах + IT × 4.
- G8-apply-python: Redis dedup в bot + pytest dedup test.

## Mini-app

**НЕ упоминать в M13 scope.** Владелец решил: релиз в v0.0.0 после
отточки PWA, отдельная сессия (не в M13). Mini-app CORS проверен —
same-origin через `/mini-app/`, дополнительных config'ов не требуется.

## Что ждёт после M13 (для контекста, не для работы)

После закрытия всех 23 AC:
1. Tag `v0.0.0-alpha.14` или сразу `v0.0.0` (решит владелец).
2. `CHANGELOG.md` `[Unreleased]` → `[0.0.0]` с сегодняшней датой.
3. Version bump: root `build.gradle.kts` + `frontends/*/package.json` на `0.0.0`.
4. `docker-compose.prod.yml` image tags на `:v0.0.0`.
5. `git push origin dev` + `git push origin --tags`.
6. VPS deploy по `docs/prod-deploy-checklist.md` (обновлённый в M13 G23).

## Pending-действия, ожидающие явного `go`

1. `git push origin dev` — **65+ коммитов** ahead (будет больше после M13).
2. `git push origin --tags` — 13 tags локально (alpha.1-13), после M13 +1-2.
3. Final `v0.0.0` tag.
4. VPS migration.

---

## История milestone'ов (архив)

M01-M08 ✅ (см. git tags `v0.0.0-alpha.1..alpha.9`).
M09 Prod Release Blockers ✅ 2026-04-24 (`v0.0.0-alpha.10`).
M10 Notification History ✅ 2026-04-24 (`v0.0.0-alpha.11`).
M11 OpenAPI Polish ✅ 2026-04-24 (`v0.0.0-alpha.12`).
M12 Auth Contract-first Refactor ✅ 2026-04-24 (`v0.0.0-alpha.13`).
**M13 Pre-Deploy Hardening ⬜ В РАБОТЕ (start 2026-04-25).**

Debt report (источник scope M13) — `docs/report-before-v0.0.0/v0.0.0-debt.md`.
Dependency graph и полный roadmap — `docs/milestones/README.md`.
