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
      jobs (parallel, общий artifact cache) — commit TBD
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

- [ ] `src/test/resources/golden/week-parity.json` — ≥20 cases с
      pre-computed expected values
- [ ] `src/test/resources/golden/display-name.json` — ≥10 cases
- [ ] `@ParameterizedTest @MethodSource` для week-parity в
      schedule-service
- [ ] `@ParameterizedTest @MethodSource` для display-name в
      academic-service
- [ ] jqwik `@Property` для `parityFlipsEveryWeek`,
      `nameFormatterStable`
- [ ] `CheckinService` — constructor `Clock clock` + `clock.instant()`
      вместо `Instant.now()`
- [ ] `LateCheckinService` — аналогично
- [ ] `ExcuseService` — аналогично
- [ ] Тесты на fixed Clock (`Clock.fixed(Instant.parse(...), ZoneId.of(...))`)
- [ ] `docs/golden-tests.md` (NEW-160)

## Группа 5 — Playwright E2E (P2-8/5 + M03b defer)

- [ ] `tests/e2e/` — Playwright скаффолдинг (init config, dependencies)
- [ ] `playwright.config.ts` — Chromium + WebKit, retries=1, trace on-first-retry
- [ ] `tests/e2e/fixtures/seed.ts` — test users (student/teacher/admin/headman)
- [ ] `auth.spec.ts` — login + logout + state clearing
- [ ] `headman-mark.spec.ts` — bulk-mark 30 students, WebSocket verify
- [ ] `student-excuse.spec.ts` — 10MB PDF upload + approve
- [ ] `admin-create-user.spec.ts` — create + initial_password visible
- [ ] `role-admin.spec.ts` — admin-only paths golden
- [ ] `role-teacher.spec.ts` — teacher-only paths golden
- [ ] `role-student.spec.ts` — student-only paths golden
- [ ] `role-headman.spec.ts` — headman-only paths golden
- [ ] `@axe-core/playwright` integration в каждый spec
- [ ] Axe rules scope: WCAG 2.1 AA, severity CRITICAL+SERIOUS
- [ ] CI job `e2e-tests` после `integration-test`:
      docker-compose up → Playwright → teardown
- [ ] Artifacts upload: screenshots, traces on failure
- [ ] `scripts/smoke-prod.sh` (curl /actuator/health + login + schedule)
- [ ] `docs/e2e-testing.md` (NEW-161)

## Группа 6 — Frontend unit tests (P2-8/6)

- [ ] PWA `src/hooks/useAuth.test.ts`
- [ ] PWA `src/hooks/useErrorInterceptor.test.ts`
- [ ] PWA `src/hooks/useNotificationCenter.test.ts`
- [ ] PWA `src/hooks/useConfirmWithReason.test.ts`
- [ ] PWA `src/hooks/useGroupMembers.test.ts`
- [ ] PWA `src/components/HeadmanLessonSheet.test.tsx`
- [ ] PWA `src/components/CheckInButton.test.tsx`
- [ ] PWA `src/sw-cache.test.ts` — logout cache invalidation
- [ ] MSW setup + 401 refresh flow test
- [ ] web-panel `src/app/core/services/auth.service.logout.spec.ts`
- [ ] Vitest coverage config — exclude generated/`*.d.ts`
- [ ] `docs/testing.md` — раздел «Frontend unit testing» (NEW-162)

## Группа 7 — Load tests (P2-8/7)

- [ ] `tests/load/bulk-mark.js` — k6, 10 VU, 2 min, p95<500ms
- [ ] `tests/load/geolocation-flood.js` — 50 VU concurrent checkin
- [ ] Первый прогон → `docs/performance-baseline.md` с метриками
- [ ] `docs/load-testing.md` (NEW-163) — как запускать, что измерять
- [ ] Full load suite в future-ideas.md (v0.1)

## Группа 8 — Security contract tests (P2-8/8)

- [ ] `GrpcSecretFailFastIT` в notification-bot (pytest + monkeypatch)
- [ ] `TmaHmacValidationIT` в auth-service (signed/mutated/replay)
- [ ] `CsrfDoubleSubmitIT` в shared-web или gateway
- [ ] Объединить в `SecurityContractsIT` suite
- [ ] Проверить `SecurityIdorIT` (NEW-31 M03a) остаётся работоспособным
- [ ] `docs/testing.md` — раздел «Security contract tests» (NEW-164)

## Группа 9 — Contract tests для событий (14 P1-5) + WebSocket (14 P1-9)

- [ ] Параметризованный `EventContractIT` — читает все
      `event-schemas/*.json`, валидирует publisher/consumer
- [ ] Покрывает 14+ events: lesson.*, attendance.*, excuse.*,
      late_checkin.*, otp.requested, user.*
- [ ] `StompIntegrationTest` в notification-web (RANDOM_PORT +
      StandardWebSocketClient)
- [ ] PWA WebSocket reconnect test — mock + `onclose → setTimeout`

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
