# tests/e2e — current Vue auth smoke

The CI smoke uses the PWA at `/app/`, TMA at `/mini-app/`, and real gateway,
auth-service and ephemeral databases. The Telegram host is synthetic; HMAC
validation and the associated teacher session use real backend HTTP.

```bash
bash tests/e2e/infra/scripts/generate-test-certs.sh
docker compose --env-file tests/e2e/.env.ci -p rct-e2e -f docker-compose.e2e.yml up -d --build --wait --wait-timeout 480
docker compose --env-file tests/e2e/.env.ci -p rct-e2e -f docker-compose.e2e.yml exec -T postgres-academic psql -U rct_user -d academic_db -v ON_ERROR_STOP=1 < tests/e2e/infra/seed-telegram.sql
cd tests/e2e
npm ci
npx playwright install --with-deps chromium
npx playwright test specs/auth-token-lifecycle.spec.ts specs/mini-app.spec.ts --project=chromium --grep @smoke
```

Default edge: `https://localhost:8443`. Containers and volumes belong to the
`rct-e2e` project. The CI-only environment contains synthetic credentials;
do not copy it over a local environment. Stop this stack from the repository
root with `docker compose --env-file tests/e2e/.env.ci -p rct-e2e -f docker-compose.e2e.yml down`.

`auth-token-lifecycle.spec.ts` checks invalid credentials, cookie protection,
student/admin selection, forbidden role selection, refresh rotation, session
recovery and server-confirmed logout. `mini-app.spec.ts` checks valid and tampered
Telegram proofs, the teacher session, profile navigation and host Back.

The root WEB lane uses Vue: `auth.spec.ts`, `role-*.spec.ts`,
`headman-mark.spec.ts`, `student-excuse.spec.ts` and `admin-create-user.spec.ts`.
`fixtures/auth.ts` selects the exact server-granted role before accepting its
canonical landing. `fixtures/web-panel-route.ts` binds each supported route to
completed, decoded API reads and rendered feature values, including forbidden
navigation that must never start foreign-role reads. Its browser-free controls
run as `node fixtures/web-panel-route.control.mjs`; they do not establish a
real browser or backend PASS.

The default config still includes those WEB specs. Run them only against an
explicitly prepared Vue root edge and their owned synthetic dataset. The isolated
Compose command above supplies the mobile auth smoke; its root is not a WEB
acceptance surface. Current WEB runtime acceptance and conditional cases are
recorded in `docs/validation/web-vue-pr3/`; do not infer completion from test
selection or recreate accepted destructive operations merely to fill this lane.

The auth smoke blocks service workers for deterministic online auth; the PWA
worker/offline checks remain in the Vue workspace CI job. Shared/mobile fixture
helpers and artifact privacy controls remain in use.

Requirements: Node 24.14+, Docker Compose v2 with `up --wait`, and an available
isolated runtime lease when running alongside the shared development stand.


## Local remaining UI batch

Run python3 scripts/local_e2e_async.py ui-batch only in a separate disposable checkout after the previous local cohort is qualified stopped. Do not run the 17-service stack beside the frozen 12-service stand on a 12GB host. Preserve the old stand volumes, sessions, DLQ and failed native evidence; suspension is not acceptance cleanup.

This mode selects 36 remaining named Chromium scenarios in ten serial lanes: auth smoke9, FIRST3=3, admin1, role/security/export6, synthetic installation2, geo1, homework4, map1, registry HTML3 and helper6. Already passed client10, lesson-lifecycle4 and DOCX1 are excluded. Each invocation owns a new nonce, and each lane has its own private artifact directory and the sole safe reporter; workers1, retries0 and maxFailures0 are enforced.

A valid safe test-process failure or incomplete/skipped lane is collected as FAIL/INCOMPLETE with notRun; it does not claim an assertion-only cause. Independent lanes continue only after frozen identity, actual declared healthchecks and unchanged shared clock are checked. Running alone is insufficient for a declared healthcheck. Missing/unsafe reports, runner errors, unexpected cases, ownership/readiness loss and unexpected shared-clock changes block dependent lanes. Build, admission, private-artifact and cleanup guards remain mandatory. No CI identity is spoofed.

Thirteen scenarios remain explicitly BLOCKED/DEFERRED: hosted bot1, production service-worker1, OTP recovery1, native PDF/outage3 and ordered semester/assignment/recovery/Today7. They need separate portable ownership/restore admission. Even if all selected36 pass, the overall report is PARTIAL_BLOCKED and exit1, never all49 complete. `artifacts/ui-batch.safe.json` records each lane, exact named counts and failed public spec locations. Fix failures together and select the follow-up scope from this report; do not replay already completed unrelated lanes.
