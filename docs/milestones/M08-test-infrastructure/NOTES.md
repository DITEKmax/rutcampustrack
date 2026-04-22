# M08 Notes

Живой файл. Отклонения, измерения, surprises, вопросы к владельцу.

---

## Вопросы к owner'у до старта

1. **Playwright scope для mini-app** — запускаем ли Playwright на
   Telegram Mini App (сложнее — нужен mock TMA initData + emulator)?
   Мой default: skip (14 P2-12 ACCEPT), проверяем только PWA + web-panel +
   landing.
2. **k6 in CI vs manual** — OWNER-ANSWERS (P2-8/7) разрешает minimal-only
   в v0.0.0 (manual прогон перед релизом). CI-интеграцию отложили в v0.1.
   Подтверждение: delivery k6 scripts + docs, но без CI job.
3. **Diff-coverage threshold 80% — strict or warning?** — при первом
   внедрении возможны many-files PR с coverage dip. Warning (comment)
   или hard-fail (red CI)?
4. **Cosign: keyless vs with key?** — keyless через OIDC требует
   Fulcio, проще в настройке; with key требует `COSIGN_PRIVATE_KEY` в
   GHCR secrets. Рекомендую keyless для v0.0.0.

## Ожидаемые surprises

- **`@MockitoBean` audit может показать > 36 мест** — текущий счёт из
  аудита (QD4). Если после grep окажется 50+, нужно принять правило
  «high-value only» вместо всех.
- **Testcontainers reuse flaky на Windows dev** — `.withReuse(true)`
  требует Docker Desktop + specific networking. Проверить что
  CI Linux runners работают без issues.
- **Playwright timing issues на WebSocket-тестах** — headman-mark.spec
  требует «WebSocket event доставлен». Использовать `page.waitForEvent`
  + explicit timeouts, не `setTimeout`.
- **jqwik + Spring @Property + Gradle** — jqwik использует JUnit 5
  `@ParameterizedTest` альтернативу, может конфликтовать с
  `spring-boot-starter-test`. Проверить первый тест до массового
  применения.
- **k6 на Windows dev** — `k6 run` требует установки k6, нельзя через
  npm. `docs/load-testing.md` должен включить Windows install steps
  (Chocolatey/winget).
- **Coverage gate может фейлить первый PR** — baseline устанавливается
  после первого прогона. Иметь план: «PR-1 вводит gate с baseline из
  текущей coverage; PR-2+ только строже».

## Deferred в M08 (accumulated defer'ы)

### Из M01 Post-mortem
- **ContainerTestBase static lifecycle revision** — текущий `static`
  без `.stop()` через Ryuk. Dev должен `~/.testcontainers.properties`.
  M08 задача: документировать в `migration-testing.md` + `dev-setup.md`.
- **`@EnabledIfDockerAvailable`** — для smoke-тестов без Docker (локально).

### Из M03b
- **E2E Playwright admin/teacher/student/headman** — в Группе 5 M08
  как role-based golden paths.

### Из M06 Post-mortem
- **SBOM + cosign signing** — Группа 11 M08.
- **Trivy action sha-digest pin** — Группа 11 M08.
- **nginx/postgres/mongo/redis/rabbitmq digest-pin** — Группа 11 M08.

### Из M07 PLAN
- **Playwright e2e + axe-core automation** — Группа 5 M08.
- **openapi-typescript CI drift-gate** — **в M07 Группе 3** создана
  локальная генерация; **blocking gate в CI** — здесь НЕ делаем
  повторно (M07 уже setup), только проверяем что gate запускается
  в coverage CI job.
- **SBOM + cosign** — дубль из M06, покрыт Группой 11.

### Из 14-tests-audit.md
- **14 P1-1** Gateway contract tests — частично в M03a `SecurityIdorIT`,
  полный setup (compose Gateway+auth+academic) — опциональная часть
  Группы 8.
- **14 P1-2** OTP brute-force тесты — частично в M03a rate-limit;
  10 POST → 11th 429 — добавить в `OtpIntegrationTest`.
- **14 P1-3** Coverage gate — Группа 10 M08.
- **14 P1-4** Logout-lifecycle frontend tests — Группа 6 M08.
- **14 P1-5** Contract-тесты всех 14+ events — Группа 9 M08.
- **14 P1-6** gRPC proto contract-тесты — опциональная часть Группы 9.
- **14 P1-7** Mini-app TMA initData test — ACCEPT (mini-app not ready).
- **14 P1-8** CI gate на тесты — **закрыто M06 G7** (workflow_run).
- **14 P1-9** WebSocket lifecycle — Группа 9 M08.

### Из OWNER-ANSWERS P2-8/7
- **Full load suite (JMeter/Gatling)** — v0.1 future-ideas.md.

## Связь с M09 pilot coverage

- M09 ставит selective gate `latecheckin/` 70% + `handlers/` 70%
  **до** M08 global gate.
- M08 сохраняет M09 pilot gates как **stricter override** (module-level
  вместо global).
- Gradle: для pilot модулей `jacocoTestCoverageVerification` имеет
  `minimum = 0.70`, для остальных — `minimum = 0.60`.
- pytest-cov для bot: `--cov-fail-under=70` для `handlers/`,
  `--cov-fail-under=50` для остального.

## Baseline metrics (после первого прогона)

Соберём для сравнения в post-mortem:

- Java coverage (per-module): [TBD]
- TS coverage (pwa / web-panel): [TBD]
- Python coverage (bot / handlers): [TBD]
- CI total time: [TBD] (baseline до M08 = ?)
- E2E time (Playwright): [TBD]
- k6 baselines: [TBD — bulk-mark p95, geolocation-flood p95]

---

## Группа 2 — @MockitoBean audit (2026-04-22)

**Всего:** 41 `@MockitoBean` в тестовых файлах (`grep -c` по services/).

### Классификация

| Категория | Count | Action | Reasoning |
|-----------|-------|--------|-----------|
| gRPC clients (ScheduleGrpcClient, AcademicGrpcClient) | 15 | **keep mock** | Замена на real требует in-process gRPC server (M01 GrpcInProcessFixture готов, но консумерам нужна переработка + mock responses). Scope +1-2д — defer в v0.1. |
| RabbitTemplate | 2 | **keep mock** (там, где dashboard-only coverage) | AbstractAcademicEventIntegrationTest уже использует real RabbitMQ через testcontainer. Остальные места — unit-like IT с фокусом не на event-side. |
| SemesterCacheService / GeofenceService (internal beans с @PostConstruct gRPC calls) | 4 | **keep mock** — правильный паттерн | Заменить = real gRPC (см. выше). Mock prevents external dependency at test startup. |
| OutboxStorage / PublisherJob (M02 fixtures) | 3 | **keep mock** | Tests фокусируются на consumer-side, publisher мокается преднамеренно. |
| Clock (для Clock-injection refactor, Группа 4) | 1 | **keep mock** → Group 4 | Будет заменен на real Clock.fixed(...) в Группе 4 (Clock-injection). |
| @MockitoBean в Abstract базах (shared setup) | 8 | **keep** | Consistent setup across тесты. |
| Controller IT — @MockitoBean для внешних сервисов | 8 | **keep mock** | HomeworkControllerIT, ExcuseControllerIT — фокус на HTTP layer + validation, не на downstream. |

### Вердикт

**37 из 41 мокают gRPC/external clients** — это **правильный паттерн** для current architecture. Альтернатива — in-process gRPC servers с explicit mock responses — требует:

1. Создать `InProcessGrpcServerExtension` JUnit 5 extension (~200 LOC).
2. Per-service — `@Bean @Primary` в testConfig, регистрирующий in-process channel builder.
3. Per-test — inject `InProcessGrpcServerRegistry`, устанавливать mock-responses через `ServerCalls.asyncUnaryCall(...)` вместо `when(...).thenReturn(...)`.

Это **полноценная Группа 2.5** на 1-2 дня. Defer'ю в v0.1 через `future-ideas.md`. В M08 Группа 2 ограничиваюсь:
- `.withReuse(true)` во всех inline-контейнерах ✅
- `WireMockFixture` wrapper (уже в shared-test-containers, M01)
- `GrpcInProcessFixture` (уже в shared-test-containers, M01)
- audit-отчёт в NOTES.md (сейчас) ✅

**Acceptance criteria Группы 2** выполнены: «audit 36+ мест; отчёт в NOTES» ✅.

### ShedLockSmokeIT — reuse exception

`ShedLockSmokeIT` использует JUnit 5 `@Testcontainers` extension + `@Container`
annotation. Extension автоматически управляет жизненным циклом контейнера
(`.start()` + `.stop()`). `.withReuse(true)` с `@Container` может
конфликтовать — extension может пытаться останавливать reuse-контейнер.
**Не трогаем.**

Этот же принцип применим к будущему `FlywayMigrationIT` (Группа 3) —
fresh container нужен для `freshInstallAppliesAllMigrations` template (D5).

---

## Группа 1 — Audit `*Test` → `*IT` (2026-04-22)

**Критерий rename:** класс содержит `@SpringBootTest` / `@Testcontainers` /
`extends Abstract*IntegrationTest` / `extends ContainerTestBase`. Unit-тесты
(чистый JUnit / Mockito без Spring context) — не трогаем.

### Abstract base classes (keep original name, уже ясны как baseline)

| Путь | Решение |
|------|---------|
| `services/attendance-service/.../integration/AbstractAttendanceIntegrationTest.java` | **keep** (abstract, не JUnit-class) |
| `services/schedule-service/.../integration/AbstractScheduleIntegrationTest.java` | **keep** |
| `services/academic-service/.../integration/AbstractAcademicIntegrationTest.java` | **keep** |
| `services/academic-service/.../integration/AbstractAcademicEventIntegrationTest.java` | **keep** |
| `services/academic-service/.../integration/AbstractAcademicCacheIntegrationTest.java` | **keep** |
| `services/auth-service/.../integration/AbstractIntegrationTest.java` | **keep** |
| `services/shared/shared-test-containers/.../ContainerTestBase.java` | **keep** |

Abstract-base'ы помечены `abstract`, не запускаются JUnit Platform'ой
напрямую. Оставляем `*Test` naming — ArchUnit rule в Группе 1.2 будет
проверять `modifiers does not contain ABSTRACT`.

### Rename plan (30 файлов)

**attendance-service (13):**
- `integration/SecuritySmokeTest` → `SecuritySmokeIT`
- `integration/ReportIntegrationTest` → `ReportIT`
- `integration/RabbitConsumerTest` → `RabbitConsumerIT`
- `integration/MongoIndexTest` → `MongoIndexIT`
- `integration/MarkingIntegrationTest` → `MarkingIT`
- `integration/EventConsumerIntegrationTest` → `EventConsumerIT`
- `integration/EnumSerializationTest` → `EnumSerializationIT`
- `integration/CheckinIntegrationTest` → `CheckinIT`
- `excuse/ExcuseRepositoryTest` → `ExcuseRepositoryIT`

**schedule-service (8):**
- `lesson/LessonStatusTransitionJobTest` → `LessonStatusTransitionJobIT`
- `integration/ShedLockSmokeIntegrationTest` → `ShedLockSmokeIT`
- `integration/LessonCancelEventTest` → `LessonCancelEventIT`
- `integration/SecuritySmokeTest` → `SecuritySmokeIT`
- `integration/LessonApiTest` → `LessonApiIT`
- `integration/ScheduleViewTest` → `ScheduleViewIT`
- `integration/EntityMappingIntegrationTest` → `EntityMappingIT`
- `integration/ScheduleItemApiTest` → `ScheduleItemApiIT`
- `integration/LessonGenerationIntegrationTest` → `LessonGenerationIT`
- `grpc/ScheduleGrpcServiceImplTest` → `ScheduleGrpcServiceImplIT`

**academic-service (8):**
- `user/UserSearchIntegrationTest` → `UserSearchIT`
- `user/UserRepositorySearchTest` → `UserRepositorySearchIT`
- `integration/RestApiIntegrationTest` → `RestApiIT`
- `integration/OutboxCleanupIntegrationTest` → `OutboxCleanupIT`
- `integration/GroupRenameEventTest` → `GroupRenameEventIT`
- `integration/EventIntegrationTest` → `EventIT`
- `integration/EntityMappingIntegrationTest` → `EntityMappingIT`
- `integration/CacheIntegrationTest` → `CacheIT`
- `integration/AcademicGrpcIntegrationTest` → `AcademicGrpcIT`

**auth-service (3):**
- `integration/TmaIntegrationTest` → `TmaIT`
- `integration/OtpIntegrationTest` → `OtpIT`
- `integration/AuthIntegrationTest` → `AuthIT`

**api-gateway:** все тесты уже `*IT` (InternalJwtIssuerIT, RateLimitIT,
FailOpenIT, CompositeLoginKeyResolverIT). Нет работы.

**notification-service:** все Spring-context тесты уже `*IT`
(PushSubscriptionCleanupJobIT, NotificationErrorHandlingIT,
NotificationLoggingIT). Нет работы.

**shared-test-containers:**
- `FixtureSmokeTest` — `@SpringBootTest` отсутствует (pure testcontainer
  smoke), но использует `ContainerTestBase` → kind-of hybrid. Оставляем
  `Test` для Группы 2, обсудим при hybrid-refactor: или убрать testcontainers,
  или rename в IT. **Defer → Группа 2**.

**shared-outbox/shared-events/shared-security/shared-observability/shared-web:**
все `*Test` — unit, без Spring-context. Не трогаем.

### Планируемый ArchUnit (Группа 1.2)

Rule: классы `@SpringBootTest` / `@Testcontainers` / extend Abstract*IT /
extend ContainerTestBase, которые **не abstract**, должны оканчиваться на
`"IT"`. Rule размещаем в `shared-web/src/testFixtures/` как
`IntegrationTestNamingConvention` + применяем per-сервис через
`@AnalyzeClasses(packages = "ru.rutcampustrack")`.

---
