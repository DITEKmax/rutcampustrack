# M08 Checklist

Атомарные задачи в порядке выполнения. Одна строка = 30 мин - 2 часа.
Отмечаются `[x]` после коммита.

## Группа 1 — Testing conventions (P2-8/1)

- [x] Audit: список всех классов с `@SpringBootTest`/`@DataJpaTest`/
      `@WebMvcTest`/Testcontainers по 5 сервисам (grep + отчёт в NOTES)
      — commit `42f9147`
- [x] Переименовать `*Test` → `*IT` по списку (git mv + imports fix)
      — commits `f1ddba7` (attendance, 9), `871910b` (schedule, 10),
      `db4779d` (academic, 9), `0f812d9` (auth, 3)
- [x] Root `build.gradle.kts` — shared `integrationTest` Gradle task
      с `filter { includeTestsMatching "*IT" }`, `shouldRunAfter("test")`
      — commit `4119b5f`
- [x] `services/*/build.gradle.kts` — включить `integrationTest` в
      `tasks.check { dependsOn }` — commit `4119b5f` (через subprojects
      блок в root build.gradle.kts)
- [x] `tasks.test { filter { excludeTestsMatching "*IT" } }` везде
      — commit `4119b5f`
- [x] ArchUnit rule: классы с IT-аннотациями должны заканчиваться на "IT"
      (расширение NEW-109) — commit `8418faa`
      (IntegrationTestNamingRule + 4 per-service wrappers)
- [x] `.github/workflows/ci.yml` — split `unit-test` + `integration-test`
      jobs (parallel, общий artifact cache) — commit `bef5c0a`
- [ ] Измерить CI time до/после → запись в NOTES.md (требует push на
      origin + запустить CI; отложено до закрытия M08)

## Группа 2 — Testcontainers hybrid refactor (P2-8/2)

- [x] Включить `.withReuse(true)` во всех контейнерах shared-test-containers
      — ContainerTestBase уже имел с M01; добавлено в 8 inline-контейнеров
      (4 Abstract базы + 2 api-gateway IT + RateLimitIT/CompositeLoginKeyResolverIT).
      ShedLockSmokeIT — exception (@Container extension, см. NOTES.md).
- [x] Добавить `~/.testcontainers.properties` пример в `docs/runbooks/`
      (dev-setup) — `docs/runbooks/dev-setup.md`
- [x] Audit 36+ `@MockitoBean` мест: таблица «класс / поле / заменить
      на real? / причина» в NOTES.md — 41 мест классифицированы в NOTES.md
- [~] Заменить `@MockitoBean` на real containers где feasible (priority:
      БД-операции, Rabbit publishers) — **defer v0.1**. 37 из 41 мокают
      gRPC clients — full переработка через in-process gRPC требует
      1-2д. Запись в `docs/future-ideas.md`.
- [~] `InProcessGrpcChannelFactory` — новый bean в shared-test-containers
      — уже существует с M01 (`GrpcInProcessFixture`), консумеры появятся
      в v0.1 вместе с @MockitoBean refactor'ом.
- [~] `WireMockExtension` wrapper для HTTP external integrations —
      уже существует с M01 (`WireMockFixture`), используется в api-gateway
      IT (RateLimitIT/CompositeLoginKeyResolverIT).
- [ ] Измерить CI time после hybrid — не должно вырасти больше +15%
      (отложено до push на origin, вместе с Группой 1)

## Группа 3 — Flyway MigrationIT (P2-8/3)

- [x] `MigrationTestUtils.runMigrationsUpTo(version)` helper в
      shared-test-containers — уже готов с M01
- [~] `FlywayMigrationIT` в auth-service (3 test templates) —
      **skip** (auth-service не владеет Flyway-миграциями, users
      table owned academic). Defer до M12 (Auth Contract refactor).
- [x] `FlywayMigrationIT` в schedule-service (3 test templates)
      — V1..V12, зелёный
- [x] `FlywayMigrationIT` в academic-service (3 test templates)
      — V1..V17, зелёный (дополнительно к HomeworkMigrationIT)
- [x] `FlywayMigrationIT` в attendance-service (адаптация под Mongo
      миграции — другой механизм, скорее smoke) — добавлен test
      `mongoConfigInitIndexes_createsAllExpectedIndexes` в MongoIndexIT
- [x] `docs/runbooks/migration-testing.md` (NEW-159)

## Группа 4 — Golden tests + Clock-injection (P2-8/4)

- [x] `src/test/resources/golden/week-parity.json` — ≥20 cases с
      pre-computed expected values — 22 cases
- [x] `src/test/resources/golden/display-name.json` — ≥10 cases
      — 12 cases
- [x] `@ParameterizedTest @MethodSource` для week-parity в
      schedule-service — `WeekParityGoldenTest`
- [x] `@ParameterizedTest @MethodSource` для display-name в
      academic-service — `DisplayNameGoldenTest`
- [~] jqwik `@Property` для `parityFlipsEveryWeek`,
      `nameFormatterStable` — реализованы через standard JUnit
      `@RepeatedTest(100)` без jqwik dep (см. `docs/golden-tests.md`
      rationale); migration на jqwik → v0.1
- [x] `CheckinService` — constructor `Clock clock` + `clock.instant()`
      вместо `Instant.now()` — commit `f61537b`
- [x] `LateCheckinService` — аналогично
- [x] `ExcuseService` — аналогично
- [~] Тесты на fixed Clock (`Clock.fixed(Instant.parse(...), ZoneId.of(...))`)
      — pattern документирован в `docs/golden-tests.md`. Конкретные
      fixed-Clock tests добавятся при появлении новых time-зависимых
      фич; существующие тесты не сломались от Clock-injection
      (Spring DI корректно подбирает system Clock).
- [x] `docs/golden-tests.md` (NEW-160)

## Группа 5 — Playwright E2E (P2-8/5 + M03b defer)

- [x] `tests/e2e/` — Playwright скаффолдинг (init config, dependencies)
      — package.json + tsconfig.json + .gitignore + README.md
- [x] `playwright.config.ts` — Chromium + WebKit + mobile-chrome,
      retries=1 в CI, trace on-first-retry
- [x] `tests/e2e/fixtures/users.ts` — test users
      (student/teacher/admin/headman)
- [x] `auth.spec.ts` — login + logout + state clearing (+ @smoke tag)
- [x] `headman-mark.spec.ts` — bulk-mark + WebSocket live-update
- [x] `student-excuse.spec.ts` — 10MB PDF upload + headman approves
- [x] `admin-create-user.spec.ts` — create + initial_password visible
- [x] `role-admin.spec.ts` — admin-only paths + cross-role guard
- [x] `role-teacher.spec.ts` — teacher read-only + cross-role guard
- [x] `role-student.spec.ts` — student paths + headman blocked
- [x] `role-headman.spec.ts` — headman paths + student fallback
- [x] `@axe-core/playwright` integration в каждый spec — через
      `fixtures/axe.ts::assertNoA11yCriticalOrSerious`
- [x] Axe rules scope: WCAG 2.1 AA, severity CRITICAL+SERIOUS
- [~] CI job `e2e-tests` после `integration-test`:
      docker-compose up → Playwright → teardown — **defer в M09**
      (требует stable staging; подробно в `docs/e2e-testing.md`)
- [~] Artifacts upload: screenshots, traces on failure — configured в
      playwright.config.ts (`outputDir`, `reporter html`), CI upload
      вместе с job M09
- [x] `scripts/smoke-prod.sh` (curl /actuator/health + login + schedule)
- [x] `docs/e2e-testing.md` (NEW-161)

## Группа 6 — Frontend unit tests (P2-8/6)

- [~] PWA `src/hooks/useAuth.test.ts` — покрыто `AuthProvider.test.tsx`
      + `AuthProvider.isHeadman.test.tsx` (уже существует)
- [~] PWA `src/hooks/useErrorInterceptor.test.ts` — покрыто
      `problemDetails.test.ts` (API layer)
- [~] PWA `src/hooks/useNotificationCenter.test.ts` — defer v0.1
      (M10 dependency — stateful notification-web)
- [~] PWA `src/hooks/useConfirmWithReason.test.ts` — покрыто
      `confirm-with-reason-dialog.component.spec.ts` (web-panel
      shared component, один паттерн)
- [~] PWA `src/hooks/useGroupMembers.test.ts` — покрыто через
      `GroupHub.test.tsx` / `StudentsList.test.tsx`
- [~] PWA `src/components/HeadmanLessonSheet.test.tsx` — defer v0.1
      (M10 data layer dependency)
- [x] PWA `src/components/CheckInButton.test.tsx` — **уже существует**
- [x] PWA SW cache invalidation — **новый тест**
      `clearAllClientState.test.ts` (09 P0-4 regression guard)
- [~] MSW setup + 401 refresh flow test — defer v0.1; покрыто через
      существующие JwtInterceptor spec'ы + auth.service.spec.ts
- [x] web-panel `auth.service.logout.spec.ts` — покрыто существующим
      `auth.service.spec.ts` (logout test) + **новый**
      `clear-all-client-state.spec.ts` (10 P0-4 regression guard)
- [~] Vitest coverage config — exclude generated/`*.d.ts` — в Группе 10
      (coverage gate)
- [x] `docs/testing.md` — раздел «Frontend unit testing» (NEW-162)

## Группа 7 — Load tests (P2-8/7)

- [x] `tests/load/bulk-mark.js` — k6, 10 VU, 2 min, p95<500ms
- [x] `tests/load/geolocation-flood.js` — 50 VU × 30s, p95<1000ms
      (rate<0.05 с учётом CHKN-07 rate-limits)
- [~] Первый прогон → `docs/performance-baseline.md` с метриками —
      шаблон создан, реальные числа ожидают первого прогона в
      Группе 12 (финализация) или при следующем session
- [x] `docs/load-testing.md` (NEW-163) — как запускать, interpret
- [x] Full load suite в future-ideas.md (v0.1) — уже есть секция
      Gatling/JMeter в future-ideas.md

## Группа 8 — Security contract tests (P2-8/8)

- [x] `GrpcSecretFailFastIT` в notification-bot (pytest + monkeypatch)
      — commit `bef5c0a`. 7 тестов в `tests/test_grpc_secret_fail_fast.py`;
      добавлен `validate_startup_config()` в `bot/config.py` + вызов
      в `bot/__main__.py:main()` до `run_health_server()`. Защищает
      от silent deploy с пустым `GRPC_SECRET` (UNAUTHENTICATED + UP health).
- [x] `TmaHmacValidationIT` в auth-service (signed/mutated/replay)
      — commit `bef5c0a`. `TmaIT.java` расширен 4 новыми тестами:
      replay-in-window → 200 (by design), bit-flipped signature → 401,
      different bot token → 401, missing hash → 401. Плюс 6 существующих.
      `@Tag("security-contract")`.
- [x] ~~`CsrfDoubleSubmitIT`~~ → **`SameSiteCookieContractIT`** (D6 2026-04-22)
      — commit `bef5c0a`. M03b отверг double-submit token в пользу
      SameSite=Strict. Реальный контракт — cookie-атрибуты + regression
      guard «refresh без X-CSRF-TOKEN → 200». 5 тестов.
      `@Tag("security-contract")`.
- [x] Объединить в `SecurityContractsIT` suite — commit `bef5c0a`.
      Реализовано через JUnit `@Tag("security-contract")` + pytest
      `@pytest.mark.security_contract`. Запуск: Gradle `--tests`
      filter / pytest `-m security_contract`. Gradle-tag filter в
      `integrationTest` task не включён (добавится в G10 coverage-gate
      или v0.1), без нового `junit-platform-suite`-модуля, минимизируя deps.
- [~] Проверить `SecurityIdorIT` (NEW-31 M03a) — **файл не существует
      в codebase.** Упоминался в M03a PLAN как NEW-31, но фактически
      не реализован. Записано в NOTES.md. Defer: создать в M09
      (Prod Release Blockers) как часть ролевой IDOR-защиты, сейчас
      Gateway JWT validator + role-filter покрывает основные векторы.
- [x] `docs/testing.md` — раздел «Security contract tests» (NEW-164)
      — commit `bef5c0a`. Обновлён с реальными классами, командами запуска
      и объяснением D6 (почему нет `CsrfDoubleSubmitIT`).

## Группа 9 — Contract tests для событий (14 P1-5) + WebSocket (14 P1-9)

- [x] Параметризованный `EventSchemaCoverageTest` в shared-events
      — commit TBD. 40 тестов: schema parseable + envelope complete +
      const matches filename + $refs resolve to _common.json + minimal
      envelope validates + coverage regression guard (19 expected schemas).
      JSON-schema sanity как unit (без Spring/Docker).
- [x] Покрывает 19 events (actual на диске): lesson.* (5), attendance.marked,
      excuse.* (2), late_checkin.* (3), otp.verified, group.* (3),
      semester.archived, homework.* (2). Publisher-side остаётся per-service
      `*ContractIT` (5 файлов: schedule 2, attendance 2, academic 1).
- [x] `StompIntegrationIT` в notification-web (RANDOM_PORT +
      StandardWebSocketClient) — commit TBD. 3 теста: happy path
      (ticket → subscribe → broadcast → receive), missing ticket → reject,
      invalid ticket → reject. `@MockitoBean WsTicketClient` + `PushService`.
      URL: `/ws/websocket` (SockJS-enabled endpoint принимает native WS
      через `/websocket` suffix).
- [x] PWA WebSocket reconnect test — commit TBD. Расширен существующий
      `useStompCheckin.test.ts` на 3 reconnect regression guards:
      reconnectDelay > 0 ∧ ≤ 5000ms, idempotent onConnect (resubscribe),
      fresh WS-ticket per reconnect (single-use).

## Группа 10 — Coverage gate (QD2)

- [ ] JaCoCo config в root `build.gradle.kts`: 60% line per-module
- [ ] JaCoCo exclusions: `**/*Dto.class`, `**/*Record.class`,
      `**/*Application.class`, `**/generated/**` (NEW-99)
- [ ] PWA `vitest.config.ts` — coverage 50% line, `exclude: ['**/*.d.ts']`
- [ ] web-panel аналогично
- [ ] notification-bot `pytest.ini` — `--cov-fail-under=50`
- [ ] `diff-cover` tool в CI — gate 80% на changed lines
- [ ] PR-comments: madrapps/jacoco, vitest-coverage-report,
      pytest-coverage-comment
- [ ] Baseline коммит после первого зелёного прогона
- [ ] **Selective override для M09 pilot** — latecheckin 70%,
      handlers/ 70% (сохранить после M09)

## Группа 11 — Supply chain (M06 defer'ы)

- [ ] SBOM generation в CI — `anchore/sbom-action@{sha}` per image
- [ ] SBOM publish как GHCR artifact
- [ ] Cosign install + sign step — `sigstore/cosign-installer@{sha}`
- [ ] Cosign verify step в deploy workflow
- [ ] Trivy action pin: заменить `@master` → `@{sha}`
- [ ] `docker-compose.prod.yml` — nginx digest-pin
- [ ] `docker-compose.prod.yml` — postgres digest-pin
- [ ] `docker-compose.prod.yml` — mongo digest-pin
- [ ] `docker-compose.prod.yml` — redis digest-pin
- [ ] `docker-compose.prod.yml` — rabbitmq digest-pin
- [ ] `docs/runbooks/image-signing-verification.md` (NEW-165)
- [ ] Renovate/Dependabot rule для digest updates (monthly)

## Группа 12 — Финализация

- [ ] `./gradlew build` + `./gradlew integrationTest` зелёные
- [ ] `./gradlew jacocoTestCoverageVerification` зелёный
- [ ] `npm run test:coverage` в PWA + web-panel зелёные
- [ ] `pytest --cov` в notification-bot зелёный
- [ ] `npm run test:e2e` (docker-compose context) зелёный
- [ ] CI полностью зелёный (все 4+ jobs)
- [ ] Post-mortem секция в PLAN.md
- [ ] Tag `v0.0.0-alpha.9`

---

_Если задача превращается в 6+ часов работы — разрежь её. Если группа
превращается в 30+ задач — вынеси в отдельный milestone._
