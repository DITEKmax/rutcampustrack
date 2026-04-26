# tests/e2e — Playwright E2E suite

Main runbook: `docs/testing/e2e-testing.md`.

## Quick reference

```bash
npm install
npm run install:browsers
npm test
```

Specs:
- `specs/auth.spec.ts` — login/logout
- `specs/headman-mark.spec.ts` — bulk-mark + WebSocket
- `specs/student-excuse.spec.ts` — excuse + file upload
- `specs/admin-create-user.spec.ts` — create user
- `specs/role-*.spec.ts` — per-role golden paths

Fixtures:
- `fixtures/users.ts` — test user credentials (seed-synced)
- `fixtures/auth.ts` — loginAs / logout helpers
- `fixtures/axe.ts` — a11y assertion helper

## Requirements для прогона

- Node 22+
- Docker + live backend (см. main runbook)
- Optional: `fixtures/test-excuse.pdf` (10MB PDF для excuse spec)
