# Real Vue PWA/TMA bounded acceptance

This package supplements the mandatory existing `auth-token-lifecycle` and `mini-app` smoke specs. It uses the production PWA at `/app/`, TMA at `/mini-app/`, gateway and real services in `docker-compose.e2e.yml`. Fixture mode must remain false. The browser host for Telegram is synthetic; external/native Telegram admission is a separate receipt.

After the shared Docker/browser resource lease is granted and the disposable E2E stack is healthy:

```sh
cd tests/e2e
RCT_E2E_SYNTHETIC_SEED=true E2E_BASE_URL=https://localhost:8443 npm run test:real-mobile
```

The opt-in and exact HTTPS loopback port guard prevent accidental use of production or the private local stand. Run with the separate config: one worker, no retries, no parallel dataset writers. The fixture bootstraps only the existing synthetic CI admin, then creates API-owned groups and accounts, a current Moscow semester if necessary, headman and assistant capabilities, LIMITED admin, teacher assignment, one-off lesson and DATE homework. Helpers retain STUDENT rather than acquiring a HEADMAN role. It waits for published authority and publication instead of changing database fences.

Generated credentials and bearer tokens stay in memory. The dedicated safe reporter writes only project/file/line, outcome, timing and error counts/locations to stdout and `test-results/real-mobile/safe-results.json`. It does not serialize step titles (Playwright includes filled passwords there), raw errors or worker stdout/stderr. An afterEach hook sanitizes errors before automatic error contexts; the reporter replaces attached contexts with the same safe metadata as a second boundary. Trace/video/screenshots are off. Before creating any credentials, the dataset accepts only the actual dedicated reporter's canonical full Node-resolved path and refuses alternate paths (even with the same filename), extra reporters or debug logging. Existing smoke artifact policy and all failure/gate statuses are retained. Do not override the reporter for an actual credential-bearing run; a `--list` discovery run does not create the dataset.

The eight bounded checks cover persisted student completion and foreign-group denial; headman browser marking, real PNG upload/read and foreign attachment denial plus teacher read/write denial; STUDENT helper permission-array read and successful marking before live revocation, then denied mutation/unchanged effect/empty permissions; LIMITED-admin account view with create denial; empty state and one transport failure followed by real retry; browser session revocation from another live session; D003 signed TMA reuse and legitimate neutral200 bootstrap after suspension, with denied protected role/read/write; D002 production service-worker offline reload, read-only homework, reconnect rejection and user/logout cleanup.

The root owns stack disposal and resource mapping. Do not delete unrelated data or invoke `scripts/local-stand-acceptance.mjs`. This package does not claim the whole accepted scenario matrix: successful geolocation check-in depends on the real educational clock and accepted geofence; attachment/upload/export/map/version/history flows need additional bounded data and browser receipts. Map legend alone is deferred by D004. No UI implementation is included. Source/type/list checks are preparation; only browser results on a recorded revision establish runtime PASS.

Preparation checks (install the existing frontends and E2E lockfiles first):

```sh
cd tests/e2e
node ../../frontends/node_modules/typescript/bin/tsc --noEmit -p tsconfig.real-mobile.json
npx --no-install playwright test --config playwright.real-mobile.config.ts --list --reporter=line
npm run test:artifact-privacy
```

The separate config selects exactly eight flows in Chromium and WebKit. Default E2E config excludes only this opt-in package; existing specs and mandatory smoke commands are unchanged. Normal sequential execution continues after an individual failure, so one failure does not create serial-suite skips. The original broad E2E typecheck currently reports the legacy Angular `admin-create-user.spec.ts` RegExp `selectOption` mismatch; the scoped config provides a check for this current Vue package without changing that source or any existing gate.

The browser-free artifact controls use a public random marker, reproduce the pinned Playwright fill-title leak, and exercise successful sensitive metadata, failed call diagnostics, failed fixture setup, the revoked-array permission counterexample, and the canonical reporter guard with an existing alternate file of the same name, an extra reporter and a trace override. The launcher captures child output and scans every emitted artifact for the marker without forwarding raw diagnostics. Deliberate expected failures belong only to these reporter controls; product acceptance has no expected-failure declarations or skips. This validates artifact handling and assertion sensitivity, not backend authority or browser acceptance.
