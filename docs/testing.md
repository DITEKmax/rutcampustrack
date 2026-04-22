# Testing — единый entry-point (NEW-162)

Cross-reference документации по тестированию в RutCampusTrack.

## Обзор

| Layer | Framework | Runbook |
|-------|-----------|---------|
| Java unit (`*Test`) | JUnit 5 + Mockito | — (inline in build.gradle.kts) |
| Java integration (`*IT`) | JUnit 5 + Spring Boot Test + Testcontainers | `docs/runbooks/dev-setup.md` (reuse setup) |
| Flyway миграции | FlywayMigrationIT per-service | `docs/runbooks/migration-testing.md` (NEW-159) |
| Golden fixtures + property-based | JUnit parameterized + `@RepeatedTest` | `docs/golden-tests.md` (NEW-160) |
| Frontend unit (PWA, web-panel) | Vitest + Testing Library | этот файл, секция ниже |
| E2E flows | Playwright + axe-core | `docs/e2e-testing.md` (NEW-161) |
| Load | k6 (локально) | `docs/load-testing.md` (NEW-163) |
| Security contracts | JUnit IT + pytest (notification-bot) | этот файл, секция «Security contract tests» |

## Frontend unit testing

**Stack:**
- PWA (`frontends/pwa/`): Vitest 3 + React Testing Library + jsdom
- web-panel (`frontends/web-panel/`): Vitest 3 + Angular TestBed
- notification-bot: pytest + pytest-asyncio + pytest-cov

**Quick start:**
```bash
cd frontends/pwa && npm test
cd frontends/web-panel && npm test
cd services/notification-bot && pytest
```

**Coverage gate (M08 Группа 10, плaned):**
- Vitest 50% line per-frontend
- pytest-cov 50% line (notification-bot general), 70% для `handlers/`
  (M09 pilot override)

**Критичные покрытия (M08 Группа 6 acceptance):**

| Test | Location | Regression guard |
|------|----------|------------------|
| `clearAllClientState.test.ts` | `frontends/pwa/src/features/auth/__tests__/` | 09 P0-4: logout clears SW cache + PushManager |
| `clear-all-client-state.spec.ts` | `frontends/web-panel/src/app/core/auth/` | 10 P0-4: logout clears localStorage/sessionStorage |
| `auth.service.spec.ts` | `frontends/web-panel/src/app/core/auth/` | Logout → clearTokens + redirect /login |
| `AuthProvider.test.tsx` + `.isHeadman.test.tsx` | `frontends/pwa/src/features/auth/__tests__/` | login flow + headman role resolution |
| `notification-center.service.spec.ts` | `frontends/web-panel/src/app/core/notifications/` | STOMP unified + reconnect (M07 G5) |
| `problemDetails.test.ts` | `frontends/pwa/src/api/__tests__/` | RFC 7807 parsing (M07 G4) |
| `sw-runtime-cache.test.ts` | `frontends/pwa/src/__tests__/` | headman-api route matcher (Phase 56) |
| `CheckInButton.test.tsx` | `frontends/pwa/src/features/checkin/__tests__/` | geolocation permission + window |
| `useSwipeHandler.test.ts` + `useDateNavigation.test.ts` | `frontends/pwa/src/shared/hooks/__tests__/` | M07 G6 UX hooks |

**Что уже покрыто (80+ тестов web-panel, 27+ PWA):**
- Auth flow (login/refresh/logout/guards/interceptor)
- Role guards (admin/teacher/student/headman)
- Schedule view + navigation
- Excuse + late-checkin UIs
- Admin CRUD (users/groups/semesters)
- Teacher journal + stats
- Headman schedule editor, journal, homework, late-checkin decisions

**Defer'ы в v0.1 (P2-8/6 low-priority):**
- MSW (mock service worker) setup для 401 → refresh flow — полное
  coverage уже есть через JwtInterceptor spec'ы; MSW даст HTTP-level
  realism но требует setup.
- HeadmanLessonSheet.test.tsx — heavy BottomSheet UI, ожидает stable
  M10 data layer.
- useNotificationCenter.test.ts — PWA analog web-panel'ного
  notification-center spec'а. Зависит от M10 (stateful notification).

## Security contract tests (M08 Группа 8, NEW-164)

Группа тестов, охраняющая security-контракты от регрессии при рефакторинге.
В отличие от unit-тестов, они фиксируют **наблюдаемое поведение извне**:
HTTP responses, HMAC-валидацию, cookie-атрибуты, startup-валидацию секретов.

### Скоуп

| Тест | Слой | Защищает от |
|------|------|-------------|
| `test_grpc_secret_fail_fast.py` | notification-bot pytest | Silent start с пустым `GRPC_SECRET` — бот бы молча отправлял каждый gRPC-вызов с UNAUTHENTICATED и `/health` всё равно показывал UP. Новый `validate_startup_config()` в `bot/config.py` бросает `StartupError` до `run_health_server`. |
| `TmaIT` | auth-service IT | Подделки Telegram Mini App initData: мутация hash, подпись другим bot-token'ом, missing hash. `@Tag("security-contract")`. Replay — тест-guard что принимается (by design: Telegram разрешает replay в пределах 24h). |
| `SameSiteCookieContractIT` | auth-service IT | Регрессия атрибутов `rct_refresh` cookie (HttpOnly/Secure/SameSite=Strict/Path). Отдельный guard-тест: refresh **с** cookie **без** `X-CSRF-TOKEN` header → 200 (защита от случайного внедрения double-submit без обновления frontend-interceptor'ов). `@Tag("security-contract")`. |

### Почему нет `CsrfDoubleSubmitIT`

M03b (`docs/milestones/M03b-jwt-cookie-ws-ticket/DECISIONS.md`,
2026-04-20) явно отверг double-submit token: для v0.0.0 защиту от CSRF
обеспечивает `SameSite=Strict` + same-origin. Double-submit token
планируется ввести только при переходе на `SameSite=Lax` (OAuth-callback
в v1.0). M08 DECISIONS D6 (2026-04-22) заменил `CsrfDoubleSubmitIT` на
`SameSiteCookieContractIT`.

### Как запустить

**Java (auth-service):**
```bash
./gradlew :services:auth-service:integrationTest \
  --tests "ru.rutcampustrack.auth.integration.TmaIT" \
  --tests "ru.rutcampustrack.auth.integration.SameSiteCookieContractIT"
```

JUnit-tag `@Tag("security-contract")` проставлен на обоих IT — Gradle
tag-filtering через `useJUnitPlatform { includeTags("security-contract") }`
в `integrationTest`-task **не включён** (будет добавлен вместе с
coverage-gate в Группе 10 или в v0.1). Пока фильтруем по `--tests`.

**Python (notification-bot):**
```bash
cd services/notification-bot
pytest -m security_contract
```

### Как добавить новый security-contract тест

1. Java: `@Tag("security-contract")` на классе, extend
   `Abstract*IntegrationTest`, имя файла `*IT.java`.
2. Python: `pytestmark = pytest.mark.security_contract` сверху модуля
   (marker зарегистрирован в `pytest.ini`).
3. Тест должен падать при **сломанном security-контракте**, не при
   flaky infrastructure. Без глобальных side-effects — каждый IT
   запускается в isolated Postgres/Redis (testcontainers).

## Event contract tests (M08 Группа 9, 14 P1-5)

Двухслойный подход:

1. **Schema sanity + coverage guard** — `EventSchemaCoverageTest` в
   `services/shared/shared-events/src/test/java/.../EventSchemaCoverageTest.java`.
   Параметризованный по всем `event-schemas/*.json` (19 файлов), проверяет:
   - schema — корректная JSON Schema draft 2020-12
   - envelope содержит полный набор required-полей (event_type / event_id /
     occurred_at / payload / event_version / trace_id / source)
   - `properties.event_type.const` совпадает с именем файла
   - `$ref` на envelope-поля ведут к `_common.json`
   - минимальный envelope с пустым payload проходит валидацию по envelope-shape
   - **coverage regression guard** — whitelist `EXPECTED_EVENT_SCHEMAS`
     synced с диском. Новый файл → тест падает → автор добавляет entry
     + `*ContractIT` в сервисе-источнике.
2. **Publisher-side contract tests** — по одному `*ContractIT` в каждом
   publishing-сервисе:
   - `services/schedule-service/.../LessonStartedContractIT` — lesson.started + lesson.closed
   - `services/schedule-service/.../LessonCancelledContractIT`
   - `services/attendance-service/.../AttendanceMarkedContractIT`
   - `services/attendance-service/excuse/ExcuseEventContractIT` — excuse.requested + excuse.decided
   - `services/academic-service/.../GroupUpdatedContractIT`

## WebSocket / STOMP lifecycle (M08 Группа 9, 14 P1-9)

- **Server-side**: `services/notification-service/.../StompIntegrationIT` —
  `@SpringBootTest(RANDOM_PORT)` + `StandardWebSocketClient` +
  `WebSocketStompClient`. Подключение через `/ws/websocket` (SockJS-enabled
  endpoint принимает native WS через `/websocket` suffix). 3 теста:
  happy path (valid ticket → subscribe → broadcast → receive),
  missing ticket → reject, invalid ticket → reject.
- **Client-side**: `frontends/pwa/.../useStompCheckin.test.ts` — reconnect
  regression guards. Mock `@stomp/stompjs::Client`, проверяет
  `reconnectDelay > 0 ∧ ≤ 5000ms`, idempotent `onConnect` (resubscribe
  при reconnect), fresh WebSocket ticket per reconnect (single-use
  обязательно — stale ticket → handshake UNAUTHORIZED).

## Как добавить новый тест

### Java integration

1. Положить в `services/<svc>/.../test/java/.../integration/MyFeatureIT.java`.
2. Extend `Abstract*IntegrationTest` для Spring context + testcontainers.
3. Имя класса строго оканчивается на `IT` (ArchUnit rule M08 Группа 1).
4. Default reuse — Abstract база имеет `.withReuse(true)`. Исключения —
   FlywayMigrationIT-подобные тесты (fresh DB).

### Frontend unit

1. Положить в `__tests__/MyFeature.test.ts{x}` (PWA) или рядом с
   компонентом как `*.spec.ts` (web-panel).
2. Mock external dependencies через `vi.mock(...)` (vitest) или
   `MockBuilder` (Angular TestBed).
3. Запустить: `npm test -- MyFeature` для фильтра.

### Property-based / golden fixtures

См. `docs/golden-tests.md`.

## Источники

- M08 PLAN.md — `docs/milestones/M08-test-infrastructure/PLAN.md`
- OWNER-ANSWERS P2-8/1..8 — `docs/report-before-v0.0.0/OWNER-ANSWERS.md`
- 14-tests-audit.md — `docs/report-before-v0.0.0/14-tests-audit.md`
