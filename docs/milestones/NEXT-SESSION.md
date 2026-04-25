# Промпт для следующей сессии — M13 Pre-Deploy Hardening

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам
откроет нужные файлы и продолжит.

---

**M12 Auth Contract-first Refactor ✅ ЗАВЕРШЁН 2026-04-24
(tag `v0.0.0-alpha.13` локально). Все 12 milestone'ов v0.0.0 закрыты.**

**Сейчас идёт M13 — Pre-Deploy Hardening (VPS GA blockers).**
**После M13 → tag `v0.0.0` GA → push → VPS deploy.**

## Прогресс M13 на 2026-04-25

**9 из 24 групп закрыто** (G1-G9). 74 коммита ahead origin/dev.
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

**Следующая — Группа 10 (Actuator tracing exclude, M06 misleading comment fix).**
Должна быть быстрой — однострочный config fix + custom OpenTelemetry Sampler.

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

## Старт новой сессии — дословно

> Читаю в порядке:
>
> 1. `docs/milestones/NEXT-SESSION.md` — этот файл (cursor позиция, текущая группа)
> 2. `docs/milestones/M13-pre-deploy-hardening/PLAN.md` — scope, 23 acceptance criteria
> 3. `docs/milestones/M13-pre-deploy-hardening/CHECKLIST.md` — 24 группы, атомарные задачи
> 4. `docs/milestones/M13-pre-deploy-hardening/NOTES.md` — решения владельца + nuance'ы для executor'а (особенно G9 раздел про 12 IDOR)
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
| **10** | **`/actuator/**` exclude from tracing sampling** | **M06** | ⬜ **СЛЕДУЮЩАЯ** |
| 11 | `mem_limit` на nginx/certbot/exporters/cadvisor/promtail | M09 | ⬜ |
| 12 | `healthcheck:` в docker-compose + Spring health indicators | M04 G4 | ⬜ |
| 13 | `.env.prod.example` + `scripts/validate-env-prod.sh` | deploy hygiene | ⬜ |
| 14 | Prometheus + Alertmanager UI за nginx basic-auth + nginx fail-fast | M11 G4 | ⬜ |
| 15 | `scripts/backup.sh` + `scripts/restore.sh` + tested restore + `.env.prod` GPG | DR | ⬜ |
| 16 | CSP audit + `/api/csp-report` endpoint + metric | M07 + NEW-54 | ⬜ |
| 17 | Grafana dashboards sanity + retention (Prometheus/Tempo 14d) | M04 | ⬜ |
| 18 | WebSocket nginx config + STOMP heartbeat + offline/online smoke | M07 | ⬜ |
| 19 | Alertmanager → Telegram E2E smoke + `docs/alerts.md` каталог 15+ alert'ов | M04 G9 | ⬜ |
| 20 | Certbot renewal hook + blackbox-exporter SSL expiry alert < 30d | availability | ⬜ |
| 21 | Flyway `CONCURRENTLY` ArchUnit guard + runbook EXPLAIN 2-weeks | M05 | ⬜ |
| 22 | Playwright E2E auth flow (login/logout/refresh/WS reconnect) | M03b → M08 → M13 | ⬜ |
| 23 | VPS deploy runbook dry-run (fresh docker-compose / Ubuntu VM) | M09 | ⬜ |
| 24 | Финальная верификация + code-reviewer + security-auditor + tag | GA | ⬜ |

**Estimate remaining:** ~3-4 человеко-дня (G10-G24).

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

## Git state на 2026-04-25 (после сессии G8-G9)

```
465b9c9 fix(security): IDOR — все 12 находок в academic+schedule (M13 G9, NEW-31)
494821f feat(events): consumer-side dedup по event_id (M13 G8, M02 CRITICAL #2)
e839887 docs(m13): hand-off для следующей сессии — G1-G7 ✅, старт G8
0301c1b feat(attendance): Mongo replica set + @Transactional для outbox atomicity (M13 G7)
7073de8 feat(notification): fail-fast startup verify Mongo indexes (M13 G6)
94e1072 refactor: удалён InvalidParam deprecated alias (M13 G5)
9e0bb6a feat(auth)!: удалён /auth/refresh-body (M13 G4)
dfb5419 feat(shared-web): global Pageable cap max-page-size=100 (M13 G3)
76c7e75 fix(gateway): rate-limit semantics настоящий X req/min (M13 G2)
7253e59 docs(m13): старт M13 + закрытие Группы 1 (flaky tests уже зелёные)
...
```

Ahead origin/dev: **74 коммита** (push отложен до GA tag после M13).
Локальный tree чистый (untracked: `.coverage`, `docs/report-before-v0.0.0/v0.0.0-debt.md`).

## Hand-off для Группы 10 (Actuator tracing exclude)

**Cross-ref debt:** M06. Comment в `application.yml:108` «Health-check
исключены» **misleading** — sampling не выключен, "spam в Tempo
продолжается". Defer M07 → не сделано → defer M13 G10.

**6 checklist items для G10:**

1. Создать `shared-observability/ActuatorTracingExcludeSampler` —
   OpenTelemetry Sampler bean. Sampler interface от
   `io.opentelemetry.sdk.trace.samplers.Sampler` — метод
   `shouldSample(...)` возвращает `SamplingResult.drop()` если span
   из `/actuator/*` URL, иначе делегирует на parent sampler.
2. Wire в Spring Boot config — заменить default sampler. Обычно через
   `@Bean public Sampler customSampler()` либо через micrometer
   `OpenTelemetryAutoConfiguration` customizer.
3. IT: `GET /actuator/health` → проверить что span не попадает в
   in-memory exporter. Использовать `InMemorySpanExporter` из
   opentelemetry-sdk-testing.
4. Поправить misleading comment в `application.yml:108` — теперь
   правда «sampler drops actuator spans».
5. Обновить `docs/observability.md` — sampling policy section.
6. Применить к 5 сервисам (auth, academic, schedule, attendance,
   notification-web) либо положить в shared-observability auto-config.

**Сложности / nuances для executor'а:**

- **Sampler Bean placement:** OpenTelemetry instrumentation
  настраивается через `OpenTelemetrySdk.builder().setTracerProvider(...)`.
  В Spring Boot 3.4 + Micrometer Tracing Bridge — bean type
  `io.opentelemetry.sdk.trace.samplers.Sampler` подхватывается
  через `OpenTelemetryAutoConfigurationCustomizer`. Проверить что
  обычный `@Bean Sampler` сработает; если нет — использовать
  `@Bean OpenTelemetryAutoConfigurationCustomizer`.
- **URL extraction из span:** в OpenTelemetry HTTP server span
  `name` = `HTTP GET` либо custom; URL в attribute
  `url.path` (semconv 1.20+) либо `http.target` (legacy).
  Sampler читает attribute через `Attributes attributes` параметр
  `shouldSample`. Tested: `attributes.get(AttributeKey.stringKey("url.path"))`.
- **shared-observability vs per-service:** sampler — generic, place
  в shared-observability как `@Bean` через `@AutoConfiguration`.
  Все 5 сервисов уже зависят от shared-observability (M04 G7).
- **Test pattern:** `InMemorySpanExporter` + `SimpleSpanProcessor`
  собирают spans, после `mockMvc.perform(get("/actuator/health"))`
  assert что `exporter.getFinishedSpanItems()` НЕ содержит span
  с `url.path=/actuator/health`. Бизнес-endpoint (не actuator)
  должен генерить span — sanity-check.
- **Tests:** для G10 нужны IT (1-2 теста на shared-observability либо
  любого сервиса). Перед запуском спросить у владельца «писать тесты для G10?».

**Рекомендую разбить G10 на 2 коммита:**
- G10-impl: Sampler + bean wiring + comment fix + doc update.
- G10-test: IT для actuator-exclusion + sanity-check бизнес span'ов.

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

1. `git push origin dev` — **74 коммита** ahead (будет больше после M13).
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
**M13 Pre-Deploy Hardening ⬜ В РАБОТЕ (G1-G9 ✅, старт G10).**

Debt report (источник scope M13) — `docs/report-before-v0.0.0/v0.0.0-debt.md`.
Dependency graph и полный roadmap — `docs/milestones/README.md`.
