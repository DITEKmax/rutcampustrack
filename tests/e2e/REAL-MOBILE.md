# Real Vue PWA/TMA bounded acceptance

This package supplements the mandatory existing `auth-token-lifecycle` and `mini-app` smoke specs. It uses the production PWA at `/app/`, TMA at `/mini-app/`, gateway and real services in `docker-compose.e2e.yml`. Fixture mode must remain false. The browser host for Telegram is synthetic; external/native Telegram admission is a separate receipt.

After the shared Docker/browser resource lease is granted and the disposable E2E stack is healthy:

```sh
cd tests/e2e
RCT_E2E_SYNTHETIC_SEED=true E2E_BASE_URL=https://localhost:8443 npm run test:real-mobile
```

The opt-in and exact HTTPS loopback port guard prevent accidental use of production or the private local stand. Run with the separate config: one worker, no retries, no parallel dataset writers. The fixture bootstraps only the existing synthetic CI admin, then creates API-owned groups and accounts, a current Moscow semester if necessary, headman and assistant grants, LIMITED admin, teacher assignment, one-off lesson and DATE homework. It waits for published authority and publication instead of changing database fences. Generated credentials and bearer tokens stay in memory. Traces/video/screenshots are disabled for these specs because they otherwise record generated login/password responses. Diagnostics include status/path only. Existing smoke artifact policy and all gates are retained.

The eight bounded checks cover persisted student completion and foreign-group denial; headman browser marking, real PNG upload/read and foreign attachment denial plus teacher read/write denial; assistant grant and live revocation; LIMITED-admin account view with create denial; empty state and one transport failure followed by real retry; browser session revocation from another live session; D003 signed TMA reuse and live suspension; D002 production service-worker offline reload, read-only homework, reconnect rejection and user/logout cleanup.

The root owns stack disposal and resource mapping. Do not delete unrelated data or invoke `scripts/local-stand-acceptance.mjs`. This package does not claim the whole accepted scenario matrix: successful geolocation check-in depends on the real educational clock and accepted geofence; attachment/upload/export/map/version/history flows need additional bounded data and browser receipts. Map legend alone is deferred by D004. No UI implementation is included. Source/type/list checks are preparation; only browser results on a recorded revision establish runtime PASS.

Preparation checks (install the existing frontends and E2E lockfiles first):

```sh
cd tests/e2e
node ../../frontends/node_modules/typescript/bin/tsc --noEmit -p tsconfig.real-mobile.json
npx --no-install playwright test --config playwright.real-mobile.config.ts --list --reporter=line
```

The separate config selects exactly eight flows in Chromium and WebKit. Default E2E config excludes only this opt-in package; existing specs and mandatory smoke commands are unchanged. Normal sequential execution continues after an individual failure, so one failure does not create serial-suite skips. The original broad E2E typecheck currently reports the legacy Angular `admin-create-user.spec.ts` RegExp `selectOption` mismatch; the scoped config provides a check for this current Vue package without changing that source or any existing gate.
