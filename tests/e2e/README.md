# tests/e2e — current Vue auth smoke

The CI smoke uses the PWA at `/app/`, TMA at `/mini-app/`, and real gateway,
auth-service and ephemeral databases. The Telegram host is synthetic; HMAC
validation and the associated teacher session use real backend HTTP.

Prepare the disposable scope with the local runner described below or the CI
synthetic-baseline Compose profile before running these browser commands. Both
use the initial Auth callback: teacher UID2 owns synthetic Telegram ID900000001
in its PENDING CREATE, then receives the normal COMMITTED/ACTIVE catalog receipt
with binding version1. Other baseline identities remain unlinked. No post-BOOT
identity UPDATE is part of this setup. The Auth marker uses a distinct fingerprint;
an older marker is rejected without repairing existing data. Use a fresh disposable
scope for this baseline variant; preserve any previous scope and its evidence.

```bash
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

This mode selects 36 remaining named Chromium scenarios in ten serial lanes: auth smoke9, FIRST3=3, admin1, role/security/export6, synthetic installation2, geo1, homework4, map1, registry HTML3 and helper6. The historical portable36 campaign excluded its then-passed client10, lesson-lifecycle4 and DOCX1; its saved reports remain historical. Each invocation owns a new nonce, and each lane has its own private artifact directory and the sole safe reporter; workers1, retries0 and maxFailures0 are enforced.


Current client qualification has **11 physical browser cases: nine sibling cases plus two independent D002 stories**. D002 grant readmission uses a genuine offline reload and restored read-only Student cache before going online; late prior-account homework delivery has its own test-scoped actors/data and keeps the same-document account change and real offline reload. CI runs these two stories in a separate required D002 step and uploads only their own safe artifact; deterministic reconnect/bootstrap ordering also has a required targeted Vue step. The existing admitted local `browser-replay.py client --d002-only` selection now binds exactly both case IDs and reports `selected=2/fullAcceptance=false`. It does not replay the nine passed sibling cases or claim full client acceptance.

A valid safe test-process failure or incomplete/skipped lane is collected as FAIL/INCOMPLETE with notRun; it does not claim an assertion-only cause. Independent lanes continue only after frozen identity, actual declared healthchecks and unchanged shared clock are checked. Running alone is insufficient for a declared healthcheck. Missing/unsafe reports, runner errors, unexpected cases, ownership/readiness loss and unexpected shared-clock changes block dependent lanes. Build, admission, private-artifact and cleanup guards remain mandatory. No CI identity is spoofed.

Thirteen scenarios remain explicitly BLOCKED/DEFERRED: hosted bot1, production service-worker1, OTP recovery1, native PDF/outage3 and ordered semester/assignment/recovery/Today7. They need separate portable ownership/restore admission. Even if all selected36 pass, the overall report is PARTIAL_BLOCKED and exit1, never all49 complete. `artifacts/ui-batch.safe.json` records each lane, exact named counts and failed public spec locations. Fix failures together and select the follow-up scope from this report; do not replay already completed unrelated lanes.

For an explicit local frontend dependency adaptation, use `python3 scripts/local_e2e_async.py ui-batch --bundled-dockerfile-frontend`. The flag defaults off and is rejected for other modes. It selects the local default Docker driver and generates inline copies of tracked, bounded UTF-8 Dockerfiles: only the known first `# syntax=docker/dockerfile:1.7` line is removed; the remaining bytes, including literal dollars, retain their meaning. Dockerfiles without a syntax directive retain all their content. Custom/unknown syntax, external or symlinked files, pre-existing inline Dockerfiles and Dockerfile-specific ignore files are refused. Context-root `.dockerignore`, build context/args/targets/cache/network and image ownership remain in place.

The private owner manifest records the opt-in, Docker Engine version, builder/frontend qualification and original/effective Dockerfile hashes; the generated Compose hash binds the escaped inline definitions. Repository Dockerfiles, CI defaults and networking/proxy settings are unchanged. This uses the Engine bundled frontend and does not claim equivalence with external syntax1.7. It avoids that frontend image dependency only; base-image pulls and every runtime/readiness/privacy guard still apply. A successful source check is not a build or E2E PASS.

To repeat only confirmed failed cases, add `--failed-case ID` once per case to the existing `ui-batch` command. The allowed IDs are `TMA_SIGNED_ENTRY`, `HEADMAN_TEACHER_READ`, `ADMIN_HISTORICAL_MARK`, `FULL_ADMIN_RIGHTS`, `OWN_PROFILE_THEME`, `HOMEWORK_NOTIFICATION` and `HELPER_PERMISSIONS`. Unknown IDs, duplicates and use outside ui-batch are refused. Selection binds fixed public titles and expected case IDs into the nonce manifest; source line numbers and failure text never select tests. Without this option the original36 selection is unchanged. The summary reports selected IDs/count, unselected portable cases and the same13 deferred dependencies; it never claims all49 complete.

These seven boundaries retain first-failure observations in `safe-results.json`: closed HTTP status/stage, radio state/count, known theme enums, checkbox actionability primitives and helper request key/permission-set flags. Observations run only after an existing operation/assertion fails, are bounded and cannot replace its primary error. Dependent response waiters are handled immediately while retaining their original await/rejection behavior. Raw matcher results, request/response bodies, headers, storage, DOM text and error messages are not exported. Service raw logs remain in the existing private retention path; this instrumentation does not diagnose or fix product semantics by itself.

The helper request assertion also includes the confirmed canonical CAS field: exact equality requires both `permissions: ['VIEW_STATS']` and `expectedPermissions: ['MANAGE_EXCUSES', 'VIEW_STATS']`. Unknown extra payload keys still fail. PATCH200 and the subsequent persisted-state checks remain unchanged. This corrects the test request expectation; it grants no new capability and does not change application code. The other six failed assertions/actions retain their existing behavior pending diagnosis.
