# Промпт для следующей сессии

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Этого достаточно —
Opus сам откроет файлы и поймёт где мы остановились.

---

**M08 Test Infrastructure — 9/12 групп закрыто (2026-04-22).**
Продолжать с **Группы 10 (Coverage gate)**. План —
`docs/milestones/M08-test-infrastructure/PLAN.md`.

Локальных коммитов ahead origin: **~45** (25 pre-M08 + 20 M08).
Tags `v0.0.0-alpha.2..8` локальные. Push отложен до закрытия M08.

**Старт следующей сессии — дословно:**

> Читаю NEXT-SESSION → CHECKLIST M08 → DECISIONS D1-D6. Стартую с
> Группы 10 — Coverage gate (JaCoCo per-module 60% line + Vitest 50% +
> pytest-cov 50% + diff-cover ≥80% на changed lines + PR-comments actions
> + baseline commit + M09 selective override для latecheckin/handlers).

---

## M08 статус (2026-04-22, 20 коммитов)

### Правила (без изменений с M05-M07)

- **Русский язык** в отчётах / NOTES / ответах.
- **Ветка `dev`**. Push на `main`/`origin` — только с явного `go`.
- Не звать `gsd-*` агентов. `Explore` для «найти все X»,
  `bug-hunter` / `code-reviewer` / `security-auditor` — в G12 audit.
- Surprise → NOTES.md + спросить до продолжения.
- Micro-решение → DECISIONS.md.
- Закрыл пункт CHECKLIST → `[x]` через Edit (commit hash в описании).
- **Hook-reminder'ы READ-BEFORE-EDIT после Read в той же сессии — ложные.**
- `CHANGELOG.md [Unreleased]` обновляй при значимых изменениях (G12).

### M08 DECISIONS (D1-D6, commits `0c8564c`, `bef5c0a`)

| # | Вопрос | Решение |
|---|--------|---------|
| D1 | Playwright scope для mini-app | **skip** (14 P2-12 ACCEPT) |
| D2 | k6 в CI | **manual-only**, CI → v0.1 |
| D3 | Diff-coverage gate | **warning первый PR → hard-fail далее** |
| D4 | Cosign | **keyless** (OIDC Fulcio), публичный репо |
| D5 | Testcontainers reuse | везде + **исключение FlywayMigrationIT** |
| D6 | G8 CSRF test | `CsrfDoubleSubmitIT` → **`SameSiteCookieContractIT`** (M03b отверг double-submit token; реальный контракт = SameSite attributes) |

### Закрытые группы

| Группа | Commits | Ключевое |
|--------|---------|----------|
| G1 Testing conventions | 8 (`42f9147`..`269107c`) | 31 `*Test`→`*IT` rename, Gradle split, ArchUnit IntegrationTestNamingRule, CI yml split |
| G2 Testcontainers hybrid | `68a9ecb` | `.withReuse(true)` × 8 containers, 41 `@MockitoBean` audit (37 gRPC → defer v0.1), `docs/runbooks/dev-setup.md` |
| G3 Flyway MigrationIT | `3781edf` | schedule V1..V12 + academic V1..V17 с 3 template'ами (fresh install / checksum / data-preservation), Mongo indexes smoke, `docs/runbooks/migration-testing.md` (NEW-159) |
| G4 Golden + Clock | `f61537b` + `3a38fc1` | Clock injection в CheckinService/LateCheckinService/ExcuseService, week-parity.json (22 cases) + display-name.json (12), parameterized + property tests, `docs/golden-tests.md` (NEW-160), jqwik defer |
| G5 Playwright E2E | `5191098` | `tests/e2e/` scaffold, 4 core + 4 role specs, axe-core, `scripts/smoke-prod.sh`, `docs/e2e-testing.md` (NEW-161). CI job **defer в M09** |
| G6 Frontend unit | `6df30a6` | 09/10 P0-4 regression guards (`clearAllClientState.test.ts` PWA + `clear-all-client-state.spec.ts` web-panel), `docs/testing.md` (NEW-162) |
| G7 Load tests | `4730dec` | `tests/load/bulk-mark.js` + `geolocation-flood.js`, `docs/load-testing.md` (NEW-163), `docs/performance-baseline.md` (шаблон, первый прогон в G12) |
| G8 Security contracts | `bef5c0a` | GrpcSecretFailFastIT pytest (7) + validate_startup_config() в bot/config.py, TmaIT +4 (bit-flip/diff-bot/missing-hash/replay-in-window, 10 total), SameSiteCookieContractIT (5) вместо CsrfDoubleSubmitIT (D6), @Tag("security-contract") + pytest marker. SecurityIdorIT не существовал → defer M09. |
| G9 Event + WS contract | `b2ae934` | EventSchemaCoverageTest (40 параметризованных, shared-events) + StompIntegrationIT (3, notification-service RANDOM_PORT + StandardWebSocketClient) + PWA useStompCheckin +3 reconnect regression guards (delay, idempotent onConnect, fresh ticket). |

### Остались (3 группы)
- Параметризованный `EventContractIT` — читает
  `event-schemas/*.json`, валидирует publisher+consumer для всех
  14+ событий (lesson.*, attendance.*, excuse.*, late_checkin.*,
  otp.requested, user.*). M02 покрыл только `LessonStartedContractIT`,
  остальные — через pattern.
- `StompIntegrationTest` в notification-web (RANDOM_PORT +
  StandardWebSocketClient) — полный WebSocket lifecycle.
- PWA WebSocket reconnect test — mock WebSocket + `onclose → setTimeout(reconnect)` cycle.

**G10 — Coverage gate (QD2).** ~3-4ч.
- **Java JaCoCo** per-module, gate **60% line**, exclude
  generated/DTO getters/main (NEW-99).
- **TypeScript (PWA + web-panel)**: `vitest --coverage`, gate **50% line**,
  `exclude: ['**/*.d.ts']`.
- **Python (bot)**: `pytest --cov`, gate **50% line**.
- **diff-cover** tool поверх всех reports, gate **≥ 80%** на changed
  lines (D3 — warning первый PR → hard-fail со второго).
- PR-comments: `madrapps/jacoco-report`, `davelosert/vitest-coverage-report-action`,
  `pytest-coverage-comment`.
- **M09 selective override** — `latecheckin/` + `notification-bot/handlers/`
  gate **70% line** (stricter pilot, сохраняется после M09).
- Baseline commit после первого зелёного прогона.

**G11 — Supply chain (M06 defer).** ~2-3ч.
- **SBOM generation** (`anchore/sbom-action@{sha}` или
  `cyclonedx-gradle-plugin`) — per-image SBOM artifact на GHCR.
- **Cosign keyless signing** (D4) через `sigstore/cosign-installer@{sha}`.
- **Trivy action sha-pin** — текущий `@master`, заменить на sha.
- **Digest-pin** для base images в `docker-compose.prod.yml`: nginx,
  postgres×2, mongo, redis, rabbitmq → `image@sha256:...`.
- `docs/runbooks/image-signing-verification.md` (NEW-165) — verify
  команда с `--certificate-identity-regexp` pattern для VPS.

**G12 — Финализация.** ~1-2ч.
- `./gradlew build` + `./gradlew integrationTest` зелёные.
- `./gradlew jacocoTestCoverageVerification` зелёный.
- `npm run test:coverage` в PWA + web-panel зелёные.
- `pytest --cov` в notification-bot зелёный.
- **Первый прогон k6** → записать числа в `performance-baseline.md`.
- `CHANGELOG.md [Unreleased]` — M08 summary.
- `CLAUDE.md` — статус M08 → ✅.
- `docs/milestones/README.md` — M08 ✅ + дата.
- Post-mortem секция в `docs/milestones/M08-test-infrastructure/PLAN.md`.
- `git tag v0.0.0-alpha.9` на последнем коммите M08 (локально).
- Hand-off в `NEXT-SESSION.md` для M09/M10/M11/M12.

### Важные артефакты M08 (source of truth)

- `docs/milestones/M08-test-infrastructure/{PLAN,CHECKLIST,NOTES,DECISIONS}.md`
- `services/shared/shared-test-containers/src/testFixtures/java/ru/rutcampustrack/shared/testcontainers/IntegrationTestNamingRule.java` (G1)
- `docs/runbooks/dev-setup.md` (G2 — Testcontainers reuse инструкция)
- `docs/runbooks/migration-testing.md` (G3, NEW-159)
- `docs/golden-tests.md` (G4, NEW-160)
- `docs/e2e-testing.md` (G5, NEW-161)
- `tests/e2e/` — Playwright suite (scaffold + 8 specs)
- `scripts/smoke-prod.sh` — curl-based post-deploy smoke
- `docs/testing.md` (G6, NEW-162) — единый entry-point
- `docs/load-testing.md` (G7, NEW-163)
- `docs/performance-baseline.md` (G7) — шаблон для baseline чисел
- `docs/future-ideas.md` — M08-добавки: full load suite (v0.1),
  gRPC in-process tests (v0.1), jqwik (v0.1)

### Состояние окружения

- `dev` branch чистый. 45 коммитов ahead `origin/dev`.
- Docker-compose containers: подняты или могут быть подняты через
  `docker compose up -d`.
- Все тесты зелёные (attendance, schedule, academic, auth,
  notification, shared).
- `./gradlew compileTestJava` — clean.

### Действия, ожидающие `go` пользователя

1. `git push origin dev` — 45+ коммитов ahead origin.
2. `git push origin --tags` — 7 tags (`v0.0.0-alpha.2..8`) локальные.
3. Старт Группы 8 в новой сессии.

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
**M08 Test Infrastructure ⏳ 9/12 групп закрыто, продолжать с G10.**
M09 Prod Release Blockers ⬜
M10 Notification History ⬜
M11 OpenAPI Polish ⬜
M12 Auth Contract-first Refactor ⬜

Dependency graph и полный roadmap — `docs/milestones/README.md`.
