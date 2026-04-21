# M08 — Test Infrastructure

**Статус:** ⬜ не начат
**Старт / финиш:** — / —
**Estimate:** 10-12 человеко-дней

---

## Scope

Закрывает **Фазу 5 «Test gaps» (P2-8/1..8)** из `99-executive-summary.md`,
а также supply-chain defer'ы M06 и E2E Playwright отложки M03b/M07.
Финальная точка ответственности за test quality релиза v0.0.0.

Источники:
- `99-executive-summary.md` — Фаза 5 (строки 115-160), P2-8/1..8
- `14-tests-audit.md` — P0-1/2, P1-1..9, QD1-QD7 полностью
- `OWNER-ANSWERS.md`:
  - P2-8/1..8 (строки 4475-4893) — acceptance criteria
  - QD2 (2105-2139) — coverage-gate 60/50/50 + diff 80%
  - QD3 (2140-2180) — contract-тесты событий
- `M01/PLAN.md:209-225` — ContainerTestBase lifecycle, MigrationTestUtils
- `M03b/CHECKLIST.md:175-176, 197-198, 203` — Playwright admin/teacher/student/headman
- `M06/PLAN.md:136-137`, `M06/NOTES.md:48` — SBOM/cosign, digest-pin
- `M07/PLAN.md:98-103` — Playwright axe-core, openapi-ts drift-gate

**Включено:**

### Testing conventions (P2-8/1)
- Rename `*Test` → `*IT` для всех с `@SpringBootTest`/`@DataJpaTest`/
  `@WebMvcTest`/Testcontainers (audit ~40 классов)
- Gradle split: `tasks.register<Test>("integrationTest")` с filter `*IT`;
  `tasks.test` исключает `*IT`; `tasks.check dependsOn integrationTest`
- ArchUnit rule (NEW-109 расширение): classes с `@SpringBootTest`/
  `@DataJpaTest`/`@WebMvcTest` или assignable to `Testcontainers` должны
  заканчиваться на `"IT"`
- CI `ci.yml`: параллельные jobs `unit-test` + `integration-test`

### Testcontainers hybrid refactor (P2-8/2)
- **Второе чтение** `shared-test-containers` (M01) — включить
  `.withReuse(true)` + `testcontainers.reuse.enable=true`
- Audit **36 `@MockitoBean`** → заменить на real Testcontainers где
  возможно (hybrid strategy: real БД/Rabbit, WireMock для external HTTP,
  gRPC in-process `InProcessChannelBuilder`)
- gRPC test fixtures: `@Bean @Primary` in-process channel + mock server
- WireMock для HTTP integrations (Telegram API, FCM, etc)

### Flyway MigrationIT + data-preservation (P2-8/3)
- `FlywayMigrationIT` в schedule-service, attendance-service, auth-service
  (academic покрыт ранее, проверить)
- Three test templates:
  - `freshInstallAppliesAllMigrations` — clean → migrate → assert applied>0, pending=0
  - `checksumConsistency` — migrate → validate (без изменений)
  - `softDeleteMigrationPreservesData` — migrate V{N-1} → INSERT → migrate V{N} → assert row status
- `MigrationTestUtils.runMigrationsUpTo(version)` helper в
  `shared-test-containers` (заготовлено в M01)
- NEW-159 runbook `docs/runbooks/migration-testing.md`

### Golden tests + property-based + Clock-injection (P2-8/4)
- `src/test/resources/golden/week-parity.json` — 20+ cases для
  ISO-parity → WeekType маппинга (semester boundaries, year transitions)
- `src/test/resources/golden/display-name.json` — ФИО форматирование
  (NEW-117 display_name_short)
- `@ParameterizedTest @MethodSource` для golden-based coverage
- jqwik `@Property` для corner-cases: `parityFlipsEveryWeek`,
  `nameFormatterStable`
- Clock-injection: `CheckinService` + `LateCheckinService` +
  `ExcuseService` принимают `Clock` через constructor (04 P2-4 — убрать
  hardcoded `Europe/Moscow` без Clock)
- NEW-160 `docs/golden-tests.md`

### E2E: Playwright + post-deploy smoke (P2-8/5)
- `tests/e2e/` directory с Playwright config (Chromium + WebKit)
- 4 основных flows:
  - `auth.spec.ts` — login + `/schedule` visible + logout clears state
  - `headman-mark.spec.ts` — headman bulk-mark 30 студентов, WebSocket
    event доставлен
  - `student-excuse.spec.ts` — excuse + file upload (10MB PDF) +
    headman approves
  - `admin-create-user.spec.ts` — admin creates student, видит
    `initial_password`
- **Role-based golden paths (из M03b defer):** admin / teacher /
  student / headman — отдельные `*-role.spec.ts` с role-specific UI
- Axe-core integrated: `@axe-core/playwright` + zero CRITICAL/SERIOUS
  (NEW-27/QC2/NEW-110, baseline из M07)
- Screenshots + traces on failure → CI artifacts
- `scripts/smoke-prod.sh` — post-deploy curl: /actuator/health + login
  + schedule (docker-compose.prod.yml context)
- Отдельный CI job `e2e-tests` после `integration-test`
- NEW-161 `docs/e2e-testing.md`

### Frontend unit-тесты (P2-8/6)
- **Hooks (Vitest):**
  - `useAuth.test.ts` — login / refresh / logout flows
  - `useErrorInterceptor.test.ts` — RFC 7807 parsing + toast dispatch
  - `useNotificationCenter.test.ts` — STOMP subscribe / reconnect /
    logout disconnect
  - `useConfirmWithReason.test.ts` — dialog flow
  - `useGroupMembers.test.ts` — pagination
- **Components:**
  - `HeadmanLessonSheet.test.tsx` — bulk-mark + optimistic + error-recovery
  - `CheckInButton.test.tsx` — geolocation permission + window validation
- **Service Worker:**
  - `sw-cache.test.ts` — logout invalidates `headman-api-cache-v1`
    (09 P0-4 regression guard)
- **web-panel Angular:** `auth.service.logout.spec.ts` — спай на 3
  STOMP сервисах + `sessionStorage.removeItem`
- MSW (mock service worker) для 401 → refresh flow
- Mini-app (14 P2-12) → ACCEPT, не делаем
- NEW-162 расширение `docs/testing.md` — «критичные frontend unit»

### Нагрузочные тесты (P2-8/7 — minimal)
- `tests/load/bulk-mark.js` — k6: `vus:10`, `duration:'2m'`,
  thresholds: `http_req_duration p95<500`, `http_req_failed<0.01`
- `tests/load/geolocation-flood.js` — 50 VU одновременно checkin'аются
- `docs/performance-baseline.md` — baseline метрики после первого прогона
- NEW-163 `docs/load-testing.md` + `tests/load/` directory
- Full load-suite (JMeter/Gatling) → v0.1 (future-ideas.md)

### Security contract-тесты (P2-8/8)
- `SecurityContractsIT` объединяет 3 теста:
  - **GrpcSecretFailFastIT** (notification-bot pytest):
    `test_empty_grpc_secret_fails_startup(monkeypatch)` → StartupError
  - **TmaHmacValidationIT** (auth-service): signed → 200; mutated
    signature → 401; replay (same timestamp) → 401
  - **CsrfDoubleSubmitIT** (shared-web или gateway): POST без
    `X-CSRF-TOKEN` → 403; mismatched cookie+header → 403; matched → 200
- Parallel `SecurityIdorIT` (NEW-31 из M03a) — оставить
- NEW-164 расширение `docs/testing.md` — «Security contract tests»

### Coverage gate (P2-8/1 + QD2)
- **Java:** JaCoCo per-module, gate **60% line**, exclude generated/DTO
  getters/main classes (NEW-99)
- **TypeScript (PWA + web-panel):** `vitest --coverage`, gate **50% line**
- **Python (bot):** `pytest --cov`, gate **50% line**
- **Diff-coverage:** `diff-cover` tool поверх всех reports,
  gate **≥ 80% для changed lines**
- Baseline set в первом PR после внедрения
- **M09 selective override** — `latecheckin/` + `notification-bot/handlers/`
  gate **70% line** (stricter pilot, остаётся после M08)
- PR-comments: `madrapps/jacoco-report`, `davelosert/vitest-coverage-report-action`,
  `pytest-coverage-comment`

### Supply chain (из M06 defer'ов)
- **SBOM generation** (`anchore/sbom-action` или `cyclonedx-gradle-plugin`)
  — per-image SBOM artifact на GHCR
- **Cosign signing** — подпись всех GHCR images в CI
- **Trivy action sha-digest pin** — текущий `@master`, заменить на sha
- **Digest-pin для base images** — nginx / postgres / mongo / redis /
  rabbitmq в `docker-compose.prod.yml` → `image@sha256:...`
- NEW-165 `docs/runbooks/image-signing-verification.md`

### Contract-тесты RabbitMQ events (14 P1-5)
- M02 закрыл только `LessonStartedContractIT`. Audit: **все 14+ events**
  покрыты pattern'ом (P2-11 schemas уже созданы в M02 + M09)
- Параметризованный тест: публикация → consumer получает → schema
  validation обязательна (QD3)
- Покрывает: `lesson.started`, `lesson.closed`, `lesson.cancelled`
  (NEW в M09), `attendance.marked`, `attendance.updated`,
  `excuse.requested`, `excuse.approved`, `excuse.rejected` (NEW в M09),
  `late_checkin.requested`, `late_checkin.approved`, `late_checkin.rejected`,
  `otp.requested` (NEW в M09), `user.created`, `user.logged-out`
- WebSocket/STOMP lifecycle тесты (14 P1-9):
  - `StompIntegrationTest` в notification-web (RANDOM_PORT +
    StandardWebSocketClient)
  - PWA — mock WebSocket + проверить `onclose → setTimeout(reconnect)` цикл

**Исключено (другие milestones):**
- `latecheckin/` + bot `handlers/` selective coverage — **M09** (pilot 70%)
- Mini-app unit tests — **ACCEPT** v0.1 (mini-app не ready)
- Landing unit tests — **ACCEPT** (визуальное ревью через PR)
- Gateway contract tests — **частично в M03a** (SecurityIdorIT), остаток в M08
- Full load suite (JMeter/Gatling) — **v0.1** future-ideas

## Модули / изменения

### Gradle / build
- `build.gradle.kts` (root) — shared `integrationTest` task config
- `services/*/build.gradle.kts` — include integrationTest в check
- `gradle/libs.versions.toml` — `org.testcontainers`, `net.jqwik`,
  `com.github.tomakehurst:wiremock-standalone`, `io.github.deweyjose:k6`

### Shared modules
- `services/shared/shared-test-containers/` — second iteration:
  - `.withReuse(true)` everywhere
  - `MigrationTestUtils.runMigrationsUpTo(version)` (NEW)
  - `InProcessGrpcChannelFactory` (NEW)
  - `WireMockExtension` wrappers

### Per-service IT
- `services/auth-service/.../FlywayMigrationIT.java` (NEW)
- `services/schedule-service/.../FlywayMigrationIT.java` (NEW)
- `services/attendance-service/.../FlywayMigrationIT.java` (NEW)
- `services/*/test/golden/*.json` — golden fixtures
- `SecurityContractsIT` в auth-service (shared pattern)
- `GrpcSecretFailFastIT` в notification-bot (pytest)

### E2E / Playwright
- `tests/e2e/` — новая top-level директория
- `tests/e2e/playwright.config.ts` — Chromium + WebKit
- `tests/e2e/auth.spec.ts`, `headman-mark.spec.ts`, `student-excuse.spec.ts`,
  `admin-create-user.spec.ts`
- `tests/e2e/role-admin.spec.ts`, `role-teacher.spec.ts`,
  `role-student.spec.ts`, `role-headman.spec.ts`
- `tests/e2e/fixtures/` — test users, test data seed
- `scripts/smoke-prod.sh` (NEW)

### Load
- `tests/load/bulk-mark.js`, `geolocation-flood.js`
- `docs/performance-baseline.md` (NEW)
- `docs/load-testing.md` (NEW — NEW-163)

### Frontend
- `frontends/pwa/src/**/*.test.{ts,tsx}` — новые hooks/components tests
- `frontends/pwa/src/sw-cache.test.ts`
- `frontends/web-panel/src/**/*.spec.ts` — logout-lifecycle
- `frontends/*/package.json` — `@axe-core/playwright`, `msw`

### CI
- `.github/workflows/ci.yml`:
  - split `unit-test` + `integration-test` jobs (parallel)
  - `e2e-tests` job после integration (docker-compose up + Playwright)
  - `coverage` job с JaCoCo + vitest-coverage + pytest-cov + diff-cover
- `.github/workflows/security.yml`:
  - SBOM generation (`anchore/sbom-action@{sha}`)
  - Cosign signing (`sigstore/cosign-installer@{sha}`)
  - Trivy action digest pin

### Docs
- `docs/runbooks/migration-testing.md` (NEW-159)
- `docs/golden-tests.md` (NEW-160)
- `docs/e2e-testing.md` (NEW-161)
- `docs/testing.md` — расширение (NEW-162, NEW-164)
- `docs/load-testing.md` (NEW-163)
- `docs/performance-baseline.md`
- `docs/runbooks/image-signing-verification.md` (NEW-165)

## Acceptance criteria

- [ ] Все IT классы переименованы в `*IT`; ArchUnit rule enforces naming
- [ ] `./gradlew test` (unit) + `./gradlew integrationTest` (IT)
      работают раздельно; `check` запускает оба
- [ ] CI jobs `unit-test` и `integration-test` параллельны; total
      CI time не более +15% от baseline
- [ ] 36+ `@MockitoBean` мест аудированы, real Testcontainers где
      possible; отчёт в `NOTES.md`
- [ ] `FlywayMigrationIT` в schedule/attendance/auth services, все
      3 test templates проходят
- [ ] Golden fixtures: ≥20 week-parity cases, ≥10 display-name cases
- [ ] Clock-injection в CheckinService/LateCheckinService/ExcuseService;
      тесты используют fixed Clock
- [ ] Playwright: 4 core flows + 4 role flows проходят в CI
      (Chromium + WebKit)
- [ ] `@axe-core/playwright` запускается в каждом E2E flow, zero
      CRITICAL/SERIOUS
- [ ] Frontend unit coverage: hooks + components + sw-cache + logout
      spec — все зелёные в CI
- [ ] k6 `bulk-mark.js` и `geolocation-flood.js` runnable локально,
      baseline записан в `performance-baseline.md`
- [ ] `SecurityContractsIT` (3 теста) + `SecurityIdorIT` зелёные
- [ ] Coverage gates активны в CI:
  - Java 60% line per-module
  - TS 50% line per-frontend
  - Python 50% line (notification-bot)
  - Diff-coverage ≥ 80% для changed lines
- [ ] M09 selective 70% gate (latecheckin/ + handlers/) сохраняется как
      stricter override
- [ ] SBOM генерится для каждого image, публикуется как GHCR artifact
- [ ] Все GHCR images подписаны cosign
- [ ] Trivy action pinned to sha digest; nginx/postgres/mongo/redis/
      rabbitmq в `docker-compose.prod.yml` — digest-pinned
- [ ] Contract-тесты для всех 14+ RabbitMQ events (parametrized)
- [ ] WebSocket/STOMP lifecycle tests: notification-web `StompIntegrationTest`,
      PWA reconnect cycle test
- [ ] Post-mortem секция в PLAN.md, tag `v0.0.0-alpha.9`

## Dependencies

- **Блокируется:** M01 (shared-test-containers ✅), M02 (outbox + first
  contract-тесты ✅), M03b (cookie + ws-ticket — Playwright testing
  требует ✅), M07 (axe-core baseline + openapi-ts generator ✅
  после завершения)
- **Блокирует:** v0.0.0 release tag (coverage-gate + e2e — release blocker)
- **Parallel safe:** M09, M10, M11 (разные scope файлов)

## Artifacts

- `tests/e2e/` — top-level Playwright suite
- `tests/load/` — k6 nagрузочные
- `scripts/smoke-prod.sh` — post-deploy smoke
- `services/shared/shared-test-containers/` (iteration 2)
- `docs/runbooks/migration-testing.md` (NEW-159)
- `docs/golden-tests.md` (NEW-160)
- `docs/e2e-testing.md` (NEW-161)
- `docs/load-testing.md` (NEW-163)
- `docs/performance-baseline.md`
- `docs/runbooks/image-signing-verification.md` (NEW-165)
- `docs/testing.md` — расширения NEW-162/164
- `.github/workflows/ci.yml` — split jobs + coverage gate
- `.github/workflows/security.yml` — SBOM + cosign + Trivy sha-pin

---

_Никаких «why», «motivation», «background» — это уже в 99-executive-summary.md
и OWNER-ANSWERS.md (P2-8/1..8, QD2/QD3). Здесь только WHAT и DONE-критерии._
