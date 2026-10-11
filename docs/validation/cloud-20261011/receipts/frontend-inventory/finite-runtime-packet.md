# Finite Vue real-server acceptance package

Baseline 3908fad9624f97aec8891fc79338ceb6b0616bf3; source-ready proposal, NOT_RUN. Root assigns one writer to tests/e2e plus any narrowly necessary synthetic seed/harness change. No UI fixes in this package. No use of private localhost:18514/scripts/local-stand-acceptance.mjs. One runtime lease and one existing shared stack; no new stack per flow.

## Reuse available path

Use docker-compose.e2e.yml, /app/ and /mini-app/, real api-gateway/mobile-bff/auth/academic/schedule/attendance/notification-web/renderer, Postgres16 x2, Mongo7 replica set, Redis7, RabbitMQ3.13, two client nginx and TLS edge. Default ports 8088/8443; all DB/internal services exposed only internally; private subnet fixed by synthetic .env.ci. Docker project rct-e2e. Root must reserve project, ports, IPAM subnet and jwt-keys volume. If shared backend already serves those clients, set E2E_BASE_URL to that exact task-owned HTTPS origin and reuse it.

Installed environment metadata: Node24.19.0, npm11.9.0, Python3.12.14; frontends/node_modules exists. tests/e2e/node_modules absent; no observed root browser cache. Browser availability NOT verified beyond these metadata checks. No existing local backend/auth suite rerun performed.

Production Docker builds default VITE_MOBILE_FIXTURE_MODE=false. Preserve that flag. PWA build enables SW only mode=production. Existing auth spec blocks SW; dedicated offline spec must use test.use({serviceWorkers:'allow'}) and wait for controller/activation. Reusing Vite dev server cannot prove SW offline.

## Ordered acceptance slice (11 bounded flows)

1. Existing current Vue auth smoke unchanged: seven PWA auth tests plus two TMA signed/tampered teacher tests. Command below. Confirms valid/invalid login, cookie attributes, denied role selection, admin explicit selection, refresh rotation, reload recovery, current logout/revoked bearer, online reconnect, TMA proof/profile/Back. Existing source tests do not prove runtime until run.
2. PWA STUDENT: real Today + homework list; own completion toggle then reload/server readback; empty semester separately via seeded second student. FE-STUDENT-01/05. Deny foreign homework/own completion owner crossing with actual server response.
3. PWA STUDENT: synthetic geolocation success and far/GPS-denied result; actual check-in or recovery ticket persisted, stable command/idempotency retry. FE-STUDENT-02. Browser geolocation may be synthetic; domain HTTP/DB must stay real.
4. PWA HEADMAN: explicit role selection for existing multirole student; journal mark one synthetic roster student, reload/readback; publish one unlinked-date homework and edit it. FE-HEADMAN-01/07. Invalid archived/foreign command response must be asserted server-side.
5. PWA STUDENT assistant: root-seeded MARK_ATTENDANCE-only grant; permitted journal action and denied MANAGE_HOMEWORK action; revoke while browser open, foreground/403/retry updates capability and subsequent server mutation denied. FE-HEADMAN-06. Tests own authority changes; no production resource.
6. PWA TEACHER: real active group assignments, attendance journal includes earlier marks under accepted 2026-09-23 widened read rule; journal remains read-only; one supported export produces actual bytes. Foreign group request denied. FE-TEACHER-01/02.
7. PWA ADMIN FULL: create one synthetic student through current Vue form, reload/show same server object; users/search and groups list; missing required input yields contracted 4xx/no object. FE-ADMIN-02. Do not display synthetic password/token in screenshot/trace artifact.
8. PWA ADMIN LIMITED: USERS_VIEW only; accounts readable, credentials/manage/permission routes denied; root FULL revokes rights while open→stale authority cannot perform next mutation. FE-ADMIN-03/07. Catalog from live /api/academic/admin-access/catalog, no invented grant names. Last-FULL/concurrency stays backend scope, reused evidence.
9. PWA offline D002: warm actual schedule/homework; wait production SW controller; offline reload preserves only read model; no write/attachment/role switching; expire/revoke server session while disconnected; offline read allowed; reconnect requires online admission, revoked owner cleared; explicit logout warm reload shows no cached previous user. FE-OFFLINE-01/02/03.
10. TMA signed STUDENT and HEADMAN entry: synthetic Telegram host, real proofs/bindings, shared homework completion / headman journal one mutation (same seeded records); auth/role server responses real. Verify TMA offline/reload never hydrates persistent PWA read model. FE-TMA-01/FE-OFFLINE-04. Existing installTelegramHost helper currently generic; teacherInitData must accept synthetic identity input in assigned harness writer.
11. Current notification inbox + recipient action (one event): actual event→history/unread→read→target; revoke open session→no new private WS delivery; synthetic host action stays same recipient. FE-NOTIFY-01. If backend event generator/WS harness already belongs to another writer, reuse that seed/evidence; do not create broad new realtime harness.

These are a finite first runtime package, not the full 147-story denominator. Root reconciles them with existing real backend receipts; uncovered upload/archive/map/provider/export combinations stay explicit UNKNOWN in scenarios.csv, not silently passed. Do not repeat a passed independent flow after unrelated fixes.

## Required deterministic synthetic prestate

V2 seed includes admin/teacher/student and one group id1, semester Spring2026 Feb-Jun; student has HEADMAN grant via V24 migration; V51 assigns existing admin FULL. It has no teacher assignments/homework/schedule/second-group or limited-admin/assistant test rows. Seed alone supports auth and empty states, not current-day domain operations.

Use an assigned runtime seed/fixture owner: create task-labelled group A/B, current Moscow-bounded semester/slots, second plain student+assistant, limited ADMIN USERS_VIEW, linked Telegram IDs for STUDENT/HEADMAN/TEACHER (existing teacher id900000001). Seed through current accepted APIs/typed create inputs where possible; exact DB seeding requires sole DB writer and current schema/constraints. Store generated IDs in memory or safe test JSON; no bearer/password/reset ticket in receipts. Verify actual grants from /api/auth/session and /api/academic/admin-access/me, not fixtures/users.ts scalar role or expectedLandingPath (legacy Angular metadata). No curriculum assumption from historical Spring2026 seed.

Seed reference paths: tests/e2e/fixtures/users.ts; tests/e2e/infra/seed-telegram.sql; services/academic-service/academic-app/src/main/resources/db/migration/V2__seed_test_data.sql; V24__auth_session_authority.sql; V51__admin_access_permissions.sql. Typed payloads live in mobile-core features admin-users/admin-users-client.ts (CreateAdminUserInput), homework/headman-homework-client.ts (HeadmanHomeworkCreateInput), schedule/headman-schedule-client.ts, headman-journal/headman-journal-client.ts (HeadmanJournalMarkCommand).

UI selectors: fixtures/pwa-auth.ts handles current entry; role dialog title 'Сменить роль' with labels from profile-types; STUDENT tabs Сегодня/Задания/Посещаемость/Ещё/Профиль, TEACHER Сегодня/Посещаемость/Итоги/Карта/Профиль, ADMIN Обзор/Аккаунты/Группы/Ещё/Профиль. HeadmanJournalScreen.vue has accessible mark label `Отметка: ${entry.displayName}`. Shell owns route state; avoid Angular URL assertions.

## Existing exact commands (run only after lease)

From repository root, after Docker runtime/proxy/CA skill is applied by runtime owner:

```bash
bash tests/e2e/infra/scripts/generate-test-certs.sh
docker compose --env-file tests/e2e/.env.ci -p rct-e2e -f docker-compose.e2e.yml config --quiet
docker compose --env-file tests/e2e/.env.ci -p rct-e2e -f docker-compose.e2e.yml up -d --build --wait --wait-timeout 480
docker compose --env-file tests/e2e/.env.ci -p rct-e2e -f docker-compose.e2e.yml exec -T postgres-academic psql -U rct_user -d academic_db -v ON_ERROR_STOP=1 < tests/e2e/infra/seed-telegram.sql
```

From tests/e2e:

```bash
npm ci
npx playwright install --with-deps chromium
E2E_BASE_URL=https://localhost:8443 npx playwright test specs/auth-token-lifecycle.spec.ts specs/mini-app.spec.ts --project=chromium --grep @smoke --workers=1
```

New spec proposal owned by harness writer: specs/mobile-role-flows.spec.ts + specs/mobile-offline.spec.ts. After writing and seeded preflight, exact target command:

```bash
E2E_BASE_URL=https://localhost:8443 npx playwright test specs/mobile-role-flows.spec.ts specs/mobile-offline.spec.ts --project=chromium --workers=1
```

The proposed new filenames do not exist yet. CI current smoke command is ci.yml:310 and includes only current two auth specs. WebKit is configured but not installed/run by current CI; if accepted browser coverage requires it, install/run once for changed critical routes after Chromium PASS with separate receipt. mobile-chrome only matches legacy role-student/headman/student-excuse filenames and would not discover new mobile-role-flows spec until testMatch ownership is explicitly updated.

Close only owned disposable runtime after capture: `docker compose --env-file tests/e2e/.env.ci -p rct-e2e -f docker-compose.e2e.yml down`. Do not add down -v or other deletion by habit. Capture revision, tests/result, command/exit, data IDs, browser version, trace redaction and event/DB evidence. Existing config retains first-retry trace/video: synthetic credentials still should not become pushed raw artifacts.

Owner source refresh before final inventory handoff: D003 permits signed initData reuse within86400s with fresh live authority; no same-session promise. D004 excludes only map icon catalogue/text legend. D002 offline rule remains as above. Use current TASK-CONTRACT/decisions when author assignment starts.
