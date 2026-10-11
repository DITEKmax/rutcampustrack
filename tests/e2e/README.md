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

The older `auth.spec.ts`, `role-*.spec.ts`, `headman-mark.spec.ts`,
`student-excuse.spec.ts` and `admin-create-user.spec.ts` target the deferred
Angular web-panel. They are retained for that client and are excluded from the
current CI smoke. Their `fixtures/auth.ts` and legacy paths do not describe the
Vue entry. The auth smoke blocks service workers for deterministic online auth;
the PWA worker/offline checks remain in the mobile Vue CI job.

Requirements: Node 24.14+, Docker Compose v2 with `up --wait`, and an available
isolated runtime lease when running alongside the shared development stand.
