# Промпт для следующей сессии — M13 Pre-Deploy Hardening

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам
откроет нужные файлы и продолжит.

---

**M12 Auth Contract-first Refactor ✅ ЗАВЕРШЁН 2026-04-24
(tag `v0.0.0-alpha.13` локально). Все 12 milestone'ов v0.0.0 закрыты.**

**Сейчас идёт M13 — Pre-Deploy Hardening (VPS GA blockers).**
**После M13 → tag `v0.0.0` GA → push → VPS deploy.**

## Прогресс M13 на 2026-04-25 (конец сессии)

**16 из 24 групп закрыто** (G1-G16). **88 коммитов** ahead origin/dev.
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
| 15 | `3c92807` + `c5d8dc2` | Backup infrastructure | `scripts/backup.sh` (pg_dump×2 + mongodump×1 + GPG symmetric) + `restore.sh` + `test-restore.sh` + `docker-compose.test-restore.yml` + cron.d + `runbooks/backup-restore.md` + ShellCheck CI. **Surprise:** одна Mongo хостит 2 БД (`attendance_db`+`notification_db`) → один archive атомарно, не два dump'а |
| 16 | `153de6a` + `3e9566a` | CSP audit + report endpoint | `/csp-report` endpoint (byte[] + manual ObjectMapper — MappingJackson2HttpMessageConverter не знает `application/csp-report`), counter `security.csp.violations{directive, blocked_uri_host}`, 14 unit + 5 IT тестов, nginx `report-uri` + `report-to` + `Report-To` header, `docs/security-headers.md`. Browser smoke deferred в G23 per владелец |

**Следующая — Группа 17 (Grafana dashboards sanity + retention)**.
Маленькая (5 checklist items), ~1 час.

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
- **Backup: одна Mongo** (G15 surprise): hand-off указывал «mongodump × 2»,
  но в prod compose одна `rct-mongo-attendance` с обеими БД
  (`attendance_db` + `notification_db`). Один `mongodump --archive`
  атомарно — проще и FK-consistent между `excuse` и
  `notification_history`.
- **Backup tested-restore design** (G15): `docker-compose.test-restore.yml`
  с tmpfs volumes + ephemeral random passwords, project name
  `rct-test-restore`. Row count verification — smoke (backup валиден),
  **не** parity с prod (риск нагрузки). Guaranteed teardown через
  `trap cleanup EXIT`.
- **Symmetric GPG** (G15): AES256 + passphrase в Bitwarden. Rejected
  asymmetric (меньше friction, всё равно passphrase в password manager).
  Offsite backup (S3/B2) deferred в v0.1 — VPS-snapshot провайдера
  как 2-й слой.
- **ShellCheck CI** (G15 proactive): `.github/workflows/ci.yml` + fixed
  pre-existing SC2064 в `smoke-prod.sh` + SC2164 в
  `m07-g3-launch-services.sh`. 8/8 bash-скриптов проходят
  `--severity=warning`.
- **CSP `application/csp-report` 415 surprise** (G16): Spring MVC
  `MappingJackson2HttpMessageConverter` не матчит non-`+json` MIME
  types → `@PostMapping(consumes=...)` возвращал 415 до handler.
  Решение: `byte[]` + `@RequestHeader Content-Type` + manual
  `ObjectMapper.readValue`. Один endpoint, switch по Content-Type.
- **CSP low-cardinality labels** (G16): `directive` — только имя
  (lowercase, без source list), `blocked_uri_host` — только host
  (без path), special values (inline/eval/data:) обрезаются до 32 chars.
  Критично для Prometheus label cardinality.
- **CSP routing** (G16): Browser → nginx `/api/csp-report` → gateway
  (StripPrefix=1, rate-limit 60/min per-IP, PUBLIC_PATHS) →
  notification-web `/csp-report` (`@Hidden`, excluded из
  `NotificationUserContextFilter`).

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
| 15 | `scripts/backup.sh` + `scripts/restore.sh` + tested restore + `.env.prod` GPG | DR | ✅ |
| 16 | CSP audit + `/api/csp-report` endpoint + metric | M07 + NEW-54 | ✅ |
| **17** | **Grafana dashboards sanity + retention (Prometheus/Tempo 14d)** | **M04** | ⬜ **СЛЕДУЮЩАЯ** |
| 18 | WebSocket nginx config + STOMP heartbeat + offline/online smoke | M07 | ⬜ |
| 19 | Alertmanager → Telegram E2E smoke + `docs/alerts.md` каталог 15+ alert'ов | M04 G9 | ⬜ |
| 20 | Certbot renewal hook + blackbox-exporter SSL expiry alert < 30d | availability | ⬜ |
| 21 | Flyway `CONCURRENTLY` ArchUnit guard + runbook EXPLAIN 2-weeks | M05 | ⬜ |
| 22 | Playwright E2E auth flow (login/logout/refresh/WS reconnect) | M03b → M08 → M13 | ⬜ |
| 23 | VPS deploy runbook dry-run (fresh docker-compose / Ubuntu VM) | M09 | ⬜ |
| 24 | Финальная верификация + code-reviewer + security-auditor + tag | GA | ⬜ |

**Estimate remaining:** ~1-2 человеко-дня (G17-G24).

## Запрос владельца по тестам и manual verification

Владелец сказал на G16: **«Я не хочу руками делать пока ничего если
что потом сообщу обо всех ошибках. сделай что можно автоматически
сделать»**.

**Значит:**
1. **НЕ ПРОСИТЬ** владельца открывать frontends в браузере, делать
   manual smoke, щёлкать DevTools.
2. **Всё что возможно автоматизировать** — автоматизировать (IT +
   unit + Testcontainers + Playwright).
3. Manual verification items в checklist — помечать **«deferred в G23
   VPS dry-run»** и двигать дальше. В G23 owner сам пройдёт весь
   runbook руками на fresh VPS / Ubuntu VM и сообщит о реальных
   ошибках одним batch'ем.

**Исключения:** если checklist item требует **owner decision** (scope,
design choice) — спрашивать в начале группы. Если **реализация** — делать.

**G9 стандарт (сохраняется):** при choice между «минимальный fix» и
«полный hardening» — полный hardening, «важно сразу настроить корректно
логику и избавиться от таких багов».

## Git state на 2026-04-25 (после сессии G15-G16)

```
3e9566a feat(nginx): CSP report-uri + report-to + docs/security-headers.md (M13 G16)
153de6a feat(notification): /csp-report endpoint + counter (M13 G16)
c5d8dc2 test(infra): test-restore.sh + shellcheck CI + G15 checklist (M13 G15)
3c92807 feat(infra): backup.sh + restore.sh + daily cron (M13 G15)
8e23aed docs(m13): hand-off для следующей сессии — G1-G14 ✅, старт G15
1f836da feat(infra): Prometheus/Alertmanager UI lockdown + nginx fail-fast (M13 G14)
5e5aad4 feat(infra): .env.prod.example + validate-env-prod.sh (M13 G13)
...
```

Ahead origin/dev: **88 коммитов** (push отложен до GA tag после M13).
Локальный tree чистый (untracked: `.coverage`,
`docs/report-before-v0.0.0/v0.0.0-debt.md`).

## Hand-off для Группы 17 (Grafana dashboards sanity + retention)

**Cross-ref debt:** M04 observability. После M04 G10 Grafana
dashboards + Prometheus/Tempo retention были настроены, но проверка
«non-zero данные показываются» была deferred. Теперь перед GA
убеждаемся, что dashboards живы + retention правильный (14 дней,
not default 15d или infinite).

**5 checklist items для G17:**

1. `docker compose up -d` → открыть Grafana → 3 dashboard'а
   (business-kpis, system-health, tracing) показывают non-zero данные.
2. Проверить `prometheus --storage.tsdb.retention.time=14d` в
   docker-compose.
3. Проверить `tempo.yml` retention 14d.
4. Проверить Loki retention (если включён).
5. `.env.prod.example`: `GRAFANA_ADMIN_PASSWORD` — не дефолт
   `admin/admin`.

**Сложности / nuances для executor'а:**

- **Item 1 — manual browser** → per owner policy «ничего руками», **defer**
  smoke'а в G23. Но automated check возможен:
  `docker exec rct-grafana wget -qO- http://localhost:3000/api/dashboards/uid/business-kpis`
  → JSON dashboard definition. Grep за panel `"type":` count → gauge
  что dashboard structure OK. Data validation — только при running
  стеке с синтетическим load, **not practical** в CI. Принять: item 1
  = «dashboard structure validates + provisioning files present».
- **Item 2 — Prometheus retention.** Проверить в
  `docker-compose.prod.yml` на prometheus service `command:` args.
  Должен быть `--storage.tsdb.retention.time=14d` (не default 15d,
  не выше). М06 ставил digest-pin, M13 G14 добавил `--web.external-url`
  — проверить что оба присутствуют + retention явно прописан.
- **Item 3 — Tempo retention.** Искать `infra/tempo/tempo.yml`,
  секция `compactor: block_retention: 14d` (Tempo convention). Опечатка
  (как `14 days` вместо `14d`) — silent accepted by Tempo но uses
  default. Tight check.
- **Item 4 — Loki retention.** Loki может быть не включён — проверить
  `docker-compose.prod.yml`. Если есть — искать в `infra/loki/loki.yml`
  `limits_config: retention_period: 336h` (= 14d). Если Loki отключён —
  mark item as «N/A (Loki не используется на v0.0.0)».
- **Item 5 — GRAFANA_ADMIN_PASSWORD в .env.prod.example.** Проверить
  что переменная уже есть (она добавлялась в M04). Если default выставлен
  как пример — заменить на CHANGE_ME + добавить generation hint
  (`openssl rand -base64 16`). Также добавить в
  `scripts/validate-env-prod.sh` → REQUIRED_VARS (если нет).

**Что точно automated possible:**
- Items 2-5 — grep / config parsing / validation script tweak.
- Item 1 partial — dashboard JSON structure validation без running стека.

**Что deferred в G23:**
- Item 1 живой smoke (non-zero данные в UI) — manual, G23.

**Рекомендую разбить G17 на 1-2 коммита:**
- G17-config: retention checks (items 2-4) + .env.prod.example item 5.
  Один коммит, мелкий diff.
- G17-dashboards (если нужно): JSON structure check (item 1 automated
  partial) + NOTES обновление с «живой smoke deferred G23».

**Estimate G17:** ~30-60 минут (самая маленькая из оставшихся групп).

**После G17 — G18 (WebSocket reliability).** Также 4 checklist items,
manual smoke (PWA offline/online) deferred в G23. Остальное (nginx
config + STOMP heartbeat + docs) — automated.

## Mini-app

**НЕ упоминать в M13 scope.** Релиз в v0.0.0 после отточки PWA,
отдельная сессия.

## Что ждёт после M13 (для контекста, не для работы)

После закрытия всех 23 AC:
1. Tag `v0.0.0-alpha.15+` либо сразу `v0.0.0` (решит владелец).
2. `CHANGELOG.md` `[Unreleased]` → `[0.0.0]` с сегодняшней датой.
3. Version bump: root `build.gradle.kts` + `frontends/*/package.json` на `0.0.0`.
4. `docker-compose.prod.yml` image tags на `:v0.0.0`.
5. `git push origin dev` + `git push origin --tags`.
6. VPS deploy по `docs/prod-deploy-checklist.md` (обновлённый в M13 G23).

## Pending-действия, ожидающие явного `go`

1. `git push origin dev` — **88 коммитов** ahead (будет больше после M13).
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
**M13 Pre-Deploy Hardening ⬜ В РАБОТЕ (G1-G16 ✅, старт G17).**

Debt report (источник scope M13) — `docs/report-before-v0.0.0/v0.0.0-debt.md`.
Dependency graph и полный roadmap — `docs/milestones/README.md`.
