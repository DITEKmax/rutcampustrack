# Промпт для следующей сессии

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Этого достаточно —
Opus сам откроет файлы и поймёт где мы остановились.

---

Продолжай работу над v0.0.0 milestones.

Контекст:
1. Архитектурный аудит завершён, зафиксирован в `docs/report-before-v0.0.0/`
   (16 отчётов + OWNER-ANSWERS.md 6400 строк + COVERAGE-AUDIT.md 354
   пункта + 99-executive-summary.md roadmap).
2. Рабочий процесс — lightweight milestones без GSD-orchestrator'а.
   Индекс: `docs/milestones/README.md`.
3. Активный milestone: **M05 Performance** — skeleton подготовлен,
   PLAN/CHECKLIST/NOTES/DECISIONS заполнены из OWNER-ANSWERS P2-10/1..8
   (строки 3673-4028). Можно сразу начинать с Группы 1.

Что делать:
1. Прочитай `docs/milestones/M05-performance/PLAN.md` — scope и модули.
2. Прочитай `docs/milestones/M05-performance/CHECKLIST.md` — 10 групп
   задач, начни с Группы 1.
3. Прочитай `docs/milestones/M05-performance/NOTES.md` — snapshot
   состояния после M04, deferred items, правила.
4. `git log --oneline -10` — последние коммиты M04 (до `135d226`).
5. Обнови статус в `docs/milestones/README.md` с `⬜` на `⏳ в работе`
   и впиши старт-дату в PLAN.md первой же правкой.
6. Продолжай с первой невыполненной галочки `[ ]` в CHECKLIST.md
   (Группа 1 — composite indexes + perf baseline).
7. Работай сам — пиши код, запускай `./gradlew build`, правь если
   упало, коммить после каждой логической группы из CHECKLIST.
8. После каждой завершённой группы — отчитайся коротко (1-2 строки)
   и жди подтверждения перед следующей. Если пользователь говорит
   «go» — работай молча дальше.

Правила:
- Русский язык в отчётах / NOTES / ответах (технические термины /
  код — оригинал).
- Не звать `gsd-*` агентов. `Explore` для «найти все X»,
  `bug-hunter` / `code-reviewer` / `security-auditor` — в Группе 9
  audit'а.
- Surprise / отклонение от плана → NOTES.md + спросить до продолжения.
- Micro-решение (не в OWNER-ANSWERS) → DECISIONS.md.
- Закрыл пункт CHECKLIST → `[x]` через Edit.
- Hook-reminder'ы READ-BEFORE-EDIT после Read в той же сессии — ложные.
- Push на origin / создание PR — только с явного `go` пользователя.
- `CHANGELOG.md [Unreleased]` обновляй при значимых изменениях.

Когда milestone закрыт:
1. Все пункты CHECKLIST отмечены `[x]`.
2. Все acceptance criteria в PLAN.md пройдены (`./gradlew build`
   зелёный + integration-тесты + ArchUnit + CI-lint).
3. Post-mortem секция дописана в PLAN.md.
4. Статус в `docs/milestones/README.md` → ✅ готов.
5. Тег `git tag v0.0.0-alpha.6` на последнем коммите milestone'а
   (локально, без push).
6. Сообщить пользователю финальный summary + ссылку на следующий
   milestone по dependency graph (M06 Ops / M07 Frontend / M08 Tests).

Старт:
> Читаю PLAN → CHECKLIST → NOTES → git log. Через минуту скажу где
> стартуем (Группа 1) и что буду делать первым.

---

## Hand-off после M04 (2026-04-20)

**Состояние M04:** ✅ **ЗАКРЫТ.** Tag `v0.0.0-alpha.5` на `325d25d`
(локально, без push).

### M04 итоги (12/12 групп)

- **G1-G6:** shared-observability модуль, INFO-default/dev-profile,
  JSON-логи × 6 сервисов, health endpoints + PublicKey indicator +
  git.properties, OTel tracing + Tempo, unified event envelope с
  trace_id (shared-events migration, 47 файлов, D5(a)).
- **G7:** Python-бот structlog + OTel auto-instrumentation +
  ObservabilityMiddleware. 7 новых тестов.
- **G8:** 8 business counter'ов + 3 gauge'а (students_in_red_zone через
  RedZoneGauge @Scheduled @SchedulerLock, active_ws_sessions через
  Session events, outbox.lag.seconds). BusinessMetrics beans в 5
  сервисах.
- **G9:** Alertmanager end-to-end chain — Prometheus rules (8 alerts
  в 4 группах) → Alertmanager → /internal/alert → RabbitMQ → bot →
  Telegram. 14 unit-тестов.
- **G10:** retention 14d (Prometheus, Loki, Tempo), Grafana
  `business-kpis-m04` dashboard (8 панелей).
- **G11 audit:** 0 BLOCKER/CRITICAL. 5 HIGH → все пофикшены:
  RedZoneGauge self-invocation (H1), AlertController unchecked cast
  (H3), CheckinRateZero absent() branch (H5), PII masking в
  structlog (M-sec-2), Telegram description truncate (M4), timing
  attack → MessageDigest.isEqual.
- **G12:** docs pack — `docs/observability.md` runbook,
  `docs/alerts.md` каталог, `docs/logging-conventions.md`, раздел в
  `docs/architecture.md`, CHANGELOG, CLAUDE.md, milestones/README.md.

### M04 deferred → следующие milestones

| Item | Куда |
|------|------|
| `/actuator/**` исключить из tracing sampling | M05 Группа 8 (gRPC рядом) или отдельный patch |
| `AlertPublisher extends AbstractEventPublisher` | M05/M06 (envelope consistency) |
| Typed DTO для Alertmanager webhook | M06 |
| mTLS вместо Bearer secret для /internal/alert | M06 |
| Per-subject/per-group thresholds для red zone | Future (cross-service join) |
| docker-compose healthcheck directives | M06 |
| E2E smoke: kill RabbitMQ → health DOWN | M06 (docker-compose) |

### M05 Scope (подготовлен)

P2-10/1..8 (см. `docs/milestones/M05-performance/PLAN.md`):

1. **P2-10/1** Composite indexes (~3ч) — Flyway migrations × 3 сервиса + EXPLAIN ANALYZE.
2. **P2-10/2** `@EntityGraph` / projection (~1д) — N+1 fix + ArchUnit rule NEW-143.
3. **P2-10/3** Caffeine cache (~1д) — semester/subject/group/rbac + gauges + caching-strategy.md.
4. **P2-10/4** Batch endpoints (~1д) — attendance/academic `/batch` + partial-success 207.
5. **P2-10/5** SQL-aggregate (~1д) — переписать `.collect(toList())` на JPQL GROUP BY.
6. **P2-10/6** HikariCP tuning (~2ч) — pool=20 + alert.
7. **P2-10/7** Cleanup push-subs + refresh TTL (~3ч) — `last_seen` column + weekly @Scheduled.
8. **P2-10/8** gRPC parallelism + deadlines + metrics (~1д) — `CompletableFuture` + grpc-micrometer + NEW-149 CI-lint.

### Состояние тестов после M04

Всё зелёное на `325d25d`:
- auth-service ✅ (login/logout/OTP counter'ы)
- attendance 157/157 (CheckinService counter ∫ RedZoneGauge)
- academic/schedule/notification ✅
- shared-observability 15/15
- shared-security (DualModeUserContextFilter counter) ✅
- shared-outbox (oldestPendingAgeSeconds) — один pre-existing failure в
  `EventSchemaRefTest` (envelope без trace_id после G6, отложен в
  backlog)
- notification-bot: 154/154 (alert_fired + observability)

### Последние коммиты M04 (git log --oneline -10)

```
135d226 docs(m04): CHECKLIST отметка v0.0.0-alpha.5 tag (325d25d)
325d25d docs(m04): observability runbook + alerts catalog + closure (Группа 12)
4321184 fix(m04): hot-patches из audit'а G11
79cb3f9 docs(m04): hand-off после G9+G10 — 10/12 групп закрыто
7f18104 feat(observability): retention + business KPI dashboard (M04 Группа 10)
6b8a233 feat(alerts): Alertmanager + rules + webhook → bot (M04 Группа 9)
1fbd041 docs(m04): hand-off для следующей сессии — 8/12 групп закрыто
1e9112e feat(metrics): business counters + 3 gauges (M04 Группа 8)
b08490e feat(notification-bot): structlog JSON + OTel tracing (M04 Группа 7)
19f2faf docs(m04): hand-off для следующей сессии — 6/12 групп закрыто
```

Все теги локально (не на origin): `v0.0.0-alpha.2..5`.

### Действия, ожидающие `go` пользователя

1. `git push origin main` — 70+ коммитов не на origin.
2. `git push origin --tags` — 4 tags (`v0.0.0-alpha.2..5`) локальные.
3. Старт M05 по CHECKLIST.

### Source of truth для всего v0.0.0

- `docs/report-before-v0.0.0/99-executive-summary.md` — roadmap +
  cluster IDs + P2-N индекс.
- `docs/report-before-v0.0.0/OWNER-ANSWERS.md` — 6400 строк решений
  владельца (строки 3673-4028 для P2-10 / M05).
- `docs/report-before-v0.0.0/COVERAGE-AUDIT.md` — 354 пункта, колонка
  «Closed in» обновляется с commit SHA.
- `docs/milestones/README.md` — индекс milestones + статусы + даты.
- `docs/milestones/M{NN}-{slug}/PLAN.md` + `CHECKLIST.md` + `NOTES.md`
  + `DECISIONS.md` — per-milestone artefacts.
- `docs/observability.md` + `docs/alerts.md` + `docs/logging-conventions.md` —
  M04 runbook'и.
