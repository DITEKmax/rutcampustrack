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

Scope:
- `GrpcSecretFailFastIT` — notification-bot pytest, fail-fast при
  отсутствии `GRPC_SERVICE_SECRET`
- `TmaHmacValidationIT` — auth-service, Telegram Mini App initData
  HMAC (signed OK, mutated sig 401, replay 401)
- `CsrfDoubleSubmitIT` — shared-web / gateway, double-submit cookie
  для state-changing requests

Все 3 консолидируются в `SecurityContractsIT` suite. Details —
Группа 8 PLAN.md M08.

## Event contract tests (M08 Группа 9, 14 P1-5)

Параметризованный `EventContractIT` читает все `event-schemas/*.json`,
валидирует publisher + consumer per schema. Покрывает 14+ событий:
`lesson.*`, `attendance.*`, `excuse.*`, `late_checkin.*`, `otp.*`,
`user.*`.

WebSocket/STOMP lifecycle (14 P1-9) — `StompIntegrationTest` в
notification-web (RANDOM_PORT + StandardWebSocketClient) + PWA
WebSocket reconnect test (M07 G5).

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
