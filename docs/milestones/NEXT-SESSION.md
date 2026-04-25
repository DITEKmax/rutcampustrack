# Промпт для следующей сессии — M13 Pre-Deploy Hardening

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам
откроет нужные файлы и продолжит.

---

**M12 Auth Contract-first Refactor ✅ ЗАВЕРШЁН 2026-04-24
(tag `v0.0.0-alpha.13` локально). Все 12 milestone'ов v0.0.0 закрыты.**

**Сейчас идёт M13 — Pre-Deploy Hardening (VPS GA blockers).**
**После M13 → tag `v0.0.0` GA → push → VPS deploy.**

## Прогресс M13 на 2026-04-25

**14 из 24 групп закрыто** (G1-G14). 83 коммита ahead origin/dev.
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
| 8 | `494821f` | Consumer-side dedup по event_id | shared-events `IdempotencyGuard` + 4 Java consumers + Python bot, closes M02 CRITICAL #2 |
| 9 | `465b9c9` | SecurityIdorIT + 12 IDOR fixes | 9 IDOR в academic, 3 в schedule. 38 IT тестов на 36 endpoints. closes NEW-31 |
| 10 | `9574d91` + `595b6e1` | Actuator tracing exclude | `ActuatorTracingExcludeFilter` (SpanExportingPredicate) + unit/IT. **Sampler не сработал** — url.path устанавливается после shouldSample → заменили на filter-on-export |
| 11 | `472263a` | mem_limit на 9 aux | nginx/certbot/node-exporter/cadvisor/promtail + 4 frontend-nginx. **26/26 контейнеров bounded**, 6.1GB total на 8GB VPS |
| 12 | `8948825` | Health degradation IT | `HealthDegradationIT` в academic — stop Rabbit → /actuator/health = 503 + rabbit DOWN. 4/5 пунктов уже были закрыты M06+M09 |
| 13 | `5e5aad4` | `.env.prod.example` + validator | 22 required vars, pure-bash dot-env parser (без shell eval — пароли содержат shell-specials). **Real-world catch:** validator нашёл 4 проблемы в owner's .env.prod |
| 14 | `1f836da` | Prometheus/Alertmanager lockdown | 2 новых nginx locations за basic-auth + --web.external-url + `nginx/scripts/entrypoint.sh` fail-fast (5 checks на SWAGGER_HTPASSWD) |

**Следующая — Группа 15 (Backup infrastructure)** — **самая большая
из оставшихся**, блокер DR перед prod deploy. Estimate ~2-3 часа.

## Key decisions зафиксированы в NOTES

- **Rate-limit формула** (G2): `burstCapacity=60, requestedTokens=60/X`
  (универсальная для всех X req/min).
- **Mongo RS** (G7): Option B = `bitnami/mongodb:7.0`, declarative
  env-based RS setup.
- **InvalidParam** (G5): удалён `InvalidParam.java` legacy record +
  frontend fallback. Backend canonical `ErrorResponse.fieldErrors` не менялся.
- **Idempotency design** (G8): `IdempotencyGuard` helper-class (не AOP).
  PostgreSQL — `INSERT ... ON CONFLICT DO NOTHING` (важно: persist+flush+catch
  ставит Spring tx в rollback-only). Mongo — `insertOne` + catch DUPLICATE_KEY.
- **IDOR scope creep** (G9): owner ожидал 1-2 баг(а), нашли **12**.
  Owner подтвердил «fix all + IT во всех сервисах». G9 был ~2× больше
  изначального scope.
- **TEACHER read access** (G9): TEACHER bypass'ятся в
  `assertCanReadGroup`/`requireGroupReadAccess` (TEACHER ведёт предмет
  в нескольких группах). Если потом надо ужесточить — единый knob в
  helper'е каждого service'а.
- **Actuator exclusion** (G10): **Sampler-подход не работает в Spring
  Boot 3.4** — `url.path` устанавливается на http span после
  `Sampler#shouldSample`. Заменили на `SpanExportingPredicate`
  (fire-on-export) + `trace_id` LRU (256 entries) для child spans.
- **mem_limit scope extension** (G11): к 5 checklist containers добавлены
  4 frontend-nginx. Финал: 26/26 bounded, 6.1GB total на 8GB VPS.
- **Health IT workaround** (G12): `application-test.yml` отключает
  `management.health.rabbit.enabled` для остальных IT — override через
  `@DynamicPropertySource` только в `HealthDegradationIT`.
- **.env.prod quoting** (G14 surprise): docker-compose env-parser
  валится на unquoted base64 с `+`/`/` (напр. `MONGODB_REPLICA_SET_KEY`).
  Fix — wrap в `"..."`. `.env.prod.example` теперь имеет CRITICAL
  warning про это. `validate-env-prod.sh` уже strip'ает quotes при парсинге.
- **Secret rotation** (G13 side-effect): owner во время G13 session
  провёл полную rotation всех passwords в `.env.prod` + добавил 3
  missing secrets (MONGODB_REPLICA_SET_KEY, INTERNAL_ISSUER_SECRET,
  ALERT_WEBHOOK_SECRET) — готово для VPS deploy.

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

Полный checklist — `M13-pre-deploy-hardening/CHECKLIST.md`.

| G | Тема | Cross-ref v0.0.0-debt | Статус |
|---|------|----------------------|--------|
| 1 | 3 pre-existing flaky тестов fix | dev-unblock | ✅ |
| 2 | Rate-limit `replenishRate` семантика | M03a | ✅ |
| 3 | Pageable max-size cap global | M10 H2 | ✅ |
| 4 | `/auth/refresh-body` удаление | M03b | ✅ |
| 5 | `InvalidParam` deprecated alias migration | M11 | ✅ |
| 6 | Mongo TTL + compound indexes startup verify | M10 S4 | ✅ |
| 7 | Mongo replica set + `@Transactional` | M02 CRITICAL #1 | ✅ |
| 8 | Consumer `event_id` dedup | M02 CRITICAL #2 | ✅ |
| 9 | `SecurityIdorIT` в 4 сервисах + 12 IDOR fixes | M03a / NEW-31 | ✅ |
| 10 | `/actuator/**` exclude from tracing | M06 | ✅ |
| 11 | `mem_limit` на nginx/certbot/exporters/cadvisor/promtail | M09 | ✅ |
| 12 | `healthcheck:` в docker-compose + Spring health indicators | M04 G4 | ✅ |
| 13 | `.env.prod.example` + `scripts/validate-env-prod.sh` | deploy hygiene | ✅ |
| 14 | Prometheus + Alertmanager UI за nginx basic-auth + nginx fail-fast | M11 G4 | ✅ |
| **15** | **`scripts/backup.sh` + `scripts/restore.sh` + tested restore + `.env.prod` GPG** | **DR** | ⬜ **СЛЕДУЮЩАЯ** |
| 16 | CSP audit + `/api/csp-report` endpoint + metric | M07 + NEW-54 | ⬜ |
| 17 | Grafana dashboards sanity + retention (Prometheus/Tempo 14d) | M04 | ⬜ |
| 18 | WebSocket nginx config + STOMP heartbeat + offline/online smoke | M07 | ⬜ |
| 19 | Alertmanager → Telegram E2E smoke + `docs/alerts.md` каталог 15+ alert'ов | M04 G9 | ⬜ |
| 20 | Certbot renewal hook + blackbox-exporter SSL expiry alert < 30d | availability | ⬜ |
| 21 | Flyway `CONCURRENTLY` ArchUnit guard + runbook EXPLAIN 2-weeks | M05 | ⬜ |
| 22 | Playwright E2E auth flow (login/logout/refresh/WS reconnect) | M03b → M08 → M13 | ⬜ |
| 23 | VPS deploy runbook dry-run (fresh docker-compose / Ubuntu VM) | M09 | ⬜ |
| 24 | Финальная верификация + code-reviewer + security-auditor + tag | GA | ⬜ |

**Estimate remaining:** ~2-3 человеко-дня (G15-G24).

## Запрос владельца по тестам

Владелец на старте M13 сказал: «при выполнении скажу написать тесты».
**Значит при старте каждой группы, где задача требует новые тесты
(new endpoint / new service / new security check), executor должен
явно спросить у владельца: "писать тесты для группы X?"** До ответа —
реализация без тестов (только implementation + runtime smoke).

**G9 решение владельца:** «fix all + IT ВСЕХ во всех сервисах,
важно сразу настроить корректно логику и избавиться от таких багов».
Применять этот же стандарт к остальным группам если возникнет
choice между «минимальный fix» и «полный hardening».

## Git state на 2026-04-25 (после сессии G10-G14)

```
1f836da feat(infra): Prometheus/Alertmanager UI lockdown + nginx fail-fast (M13 G14)
5e5aad4 feat(infra): .env.prod.example + validate-env-prod.sh (M13 G13)
8948825 test(academic): IT health degradation при stopped Rabbit (M13 G12)
472263a feat(infra): mem_limit на 9 aux containers (M13 G11)
595b6e1 test(observability): IT actuator-exclusion filter (M13 G10)
9574d91 feat(observability): drop /actuator/** spans до OTLP exporter (M13 G10)
37b4795 docs(m13): hand-off для следующей сессии — G1-G9 ✅, старт G10
465b9c9 fix(security): IDOR — все 12 находок в academic+schedule (M13 G9, NEW-31)
494821f feat(events): consumer-side dedup по event_id (M13 G8, M02 CRITICAL #2)
...
```

Ahead origin/dev: **83 коммита** (push отложен до GA tag после M13).
Локальный tree чистый (untracked: `.coverage`, `docs/report-before-v0.0.0/v0.0.0-debt.md`).

## Hand-off для Группы 15 (Backup infrastructure)

**Cross-ref debt:** DR — **нет ничего**. Сейчас если VPS rm-rf'нут,
всё потеряно. После G15 — 7-day daily backup + tested restore
procedure. **Блокер VPS deploy — без этого GA нельзя.**

**7 checklist items для G15:**

1. `scripts/backup.sh` — pg_dump × 2 (academic + schedule) + mongodump
   × 2 (attendance + notification) + `.env.prod` GPG-encrypt. Output —
   `/opt/backups/{date}/` с 4 `.gz` + 1 `.env.prod.gpg`.
2. Retention: `find /opt/backups -name "*.gz" -mtime +7 -delete` + same
   для `.gpg`. Встроено в `backup.sh` либо отдельный `cleanup-backups.sh`.
3. `scripts/restore.sh $date` — параметризованный restore: принимает
   date suffix и восстанавливает все 4 БД.
4. **Tested restore:** backup → локальная Postgres/Mongo (Docker-compose
   test setup) → `restore.sh` → row count matches. Acceptance criteria.
5. Cron config: `/etc/cron.d/rutcampustrack-backup` — раз в сутки 03:00
   UTC (06:00 MSK — вне пар).
6. GPG key generation: добавить в `docs/prod-deploy-checklist.md`
   (G13 раздел 1.0) — `gpg --gen-key` + private key backup в password
   manager (Bitwarden/1Password).
7. `runbooks/backup-restore.md` (новый) — полный runbook: setup GPG,
   daily automation, test restore quarterly, disaster recovery scenario
   (VPS wiped, restore из remote backup on fresh VPS).

**Сложности / nuances для executor'а:**

- **pg_dump vs. pg_dumpall:** у нас 2 разных Postgres containers
  (academic + schedule) с разными паролями. `pg_dump` per-DB — проще
  чем unified `pg_dumpall`. Путь: `docker exec rct-postgres-academic
  pg_dump -U rct_user academic_db | gzip > /opt/backups/{date}/academic.sql.gz`.
  Password приходит через env var `PGPASSWORD` внутри container'а
  (auto-set Postgres image).

- **mongodump для bitnami/mongodb RS (M13 G7):** `docker exec rct-mongo-attendance
  mongodump --archive=/tmp/attendance.archive --uri="mongodb://root:${MONGO_ROOT_PASSWORD}@localhost:27017?authSource=admin&replicaSet=rs0"`.
  Мы НЕ можем hardcode пароль в `backup.sh` — читать из `/opt/rutcampustrack/.env.prod`
  через `source` (или parser dot-env как в validate-env-prod.sh).

- **notification-web Mongo отдельный user (PoLP):** `MONGO_NOTIFICATION_USER`
  (не root). Root credential нужен для dump, notification user —
  scoped для runtime.

- **GPG encryption `.env.prod`:** `gpg --symmetric --cipher-algo AES256
  --output .env.prod.gpg .env.prod`. Passphrase — из password manager
  (НЕ в скрипте!). Альтернатива — asymmetric с public key recipient
  (меньше friction для decrypt, но требует keyring на VPS).

- **Tested restore — что именно:** row count matches на academic.users,
  schedule.lessons, attendance.attendances, notification_history. Не
  full data equality (test'ы лечат это через IT), a liveness row count.

- **Cron on bitnami-Debian VPS:** `/etc/cron.d/` OR systemd timer.
  Simpler — cron.d для idempotency. Script должен быть self-contained
  (resolve `.env.prod` path, logging в `/var/log/rutcampustrack-backup.log`).

- **Storage location:** `/opt/backups/` на VPS hosted disk. Дополнительно
  **offsite copy** (S3/Backblaze B2 / provider snapshot) — **deferred
  в v0.1** (owner решение в debt-report #19: «VPS hostpr snapshot как
  2-й слой»). Для M13 — только local 7-day retention.

- **Tests:** для G15 IT не нужен — restore idempotency проверяется
  manual через docker-compose test-stack. Перед запуском спросить
  у владельца «писать automated tested-restore test или manual ok?».

**Рекомендую разбить G15 на 3 коммита:**
- G15-impl: `backup.sh` + `restore.sh` + `.env.prod` GPG encrypt logic.
- G15-cron-doc: cron.d конфиг + `runbooks/backup-restore.md` + update
  `prod-deploy-checklist.md` (секция 1.0 добавить шаг про GPG key gen).
- G15-tested-restore: если владелец OK на tested restore workflow —
  отдельный `scripts/test-restore.sh` + CI job (optional).

## Mini-app

**НЕ упоминать в M13 scope.** Релиз в v0.0.0 после отточки PWA,
отдельная сессия.

## Что ждёт после M13 (для контекста, не для работы)

После закрытия всех 23 AC:
1. Tag `v0.0.0-alpha.14` или сразу `v0.0.0` (решит владелец).
2. `CHANGELOG.md` `[Unreleased]` → `[0.0.0]` с сегодняшней датой.
3. Version bump: root `build.gradle.kts` + `frontends/*/package.json` на `0.0.0`.
4. `docker-compose.prod.yml` image tags на `:v0.0.0`.
5. `git push origin dev` + `git push origin --tags`.
6. VPS deploy по `docs/prod-deploy-checklist.md` (обновлённый в M13 G23).

## Pending-действия, ожидающие явного `go`

1. `git push origin dev` — **83 коммита** ahead (будет больше после M13).
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
**M13 Pre-Deploy Hardening ⬜ В РАБОТЕ (G1-G14 ✅, старт G15).**

Debt report (источник scope M13) — `docs/report-before-v0.0.0/v0.0.0-debt.md`.
Dependency graph и полный roadmap — `docs/milestones/README.md`.
