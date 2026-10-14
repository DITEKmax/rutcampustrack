# Disposable teaching-clock lane

Application/browser use remains **blocked pending CC01 independent recheck and actual consumer controls**. The original global primary `Clock` reached mixed Academic session-expiry and Attendance attachment-retention consumers. The corrected source selects a composed `EducationalTime`; passing generic-context controls alone still does not prove security isolation. The commands below describe the prepared lane and are not authorization to launch it.

This explicit E2E-only jar supplies a primary `EducationalTime` to explicitly enrolled consumers in Academic, Schedule, Attendance and mobile BFF. `EducationalTime` composes a Clock; it does not extend Clock and cannot replace unqualified real Clock injection. Normal production configuration wraps the existing real Clock. Mixed Academic session expiry and Attendance attachment retention/download/upload use the separate real Clock; BFF diagnostic timestamps do likewise. Only domain Retry-After uses educational time. DB NOW, JWT validators, TLS, real timers, ShedLock and cache TTL sources remain independently real in source. Production main classes and images are reused; no production fixture property/dependency is added. The initializer exists only in this external source set and requires `rct.e2e.clock.enabled=true`, an absolute instant file, the disposable-target marker and one of the four allowed service identities; invalid input fails startup.

All commands below require a root-owned isolated heavy lease before Gradle, Docker or browser execution. Source/type/parser checks alone do not prove injection/startup or client behavior.

```bash
./gradlew e2eClockFixtureJar e2eClockFixtureControl --no-daemon --max-workers=1
RCT_E2E_SYNTHETIC_SEED=true node tests/e2e/backend-clock/prepare-clock.mjs /absolute/new/owned-clock-directory "$PWD/build/e2e-clock/clock-fixture.jar"
```

The preparation command creates a NEW directory only and chooses the current Moscow weekday09:00; a weekend advances to Monday. The same `current-instant` is read by the explicitly enrolled educational consumers and dedicated Playwright config/dataset. Other ordinary Clock consumers keep real time. Mount the directory, rather than a single inode, so atomic file replacement propagates. Files are readable by the existing nonroot container users; mounts remain read-only. Store this synthetic date/time and final runtime SHA in evidence. All actual receipts/log timestamps remain real UTC.

```bash
export RCT_E2E_TEACHING_CLOCK_DIR=/absolute/new/owned-clock-directory
# Root reserves the exact disposable compose project, ports, IPAM and volumes first.
docker compose --env-file tests/e2e/.env.ci -p rct-e2e -f docker-compose.e2e.yml -f tests/e2e/infra/docker-compose.clock-fixture.yml up -d --build --wait --wait-timeout 480
cd tests/e2e
export RCT_E2E_GEO_RUN=/absolute/new/owned-geo-run-directory
umask 077
mkdir -m 700 -- "$RCT_E2E_GEO_RUN"
mkdir -m 700 -- "$RCT_E2E_GEO_RUN/artifacts"
NODE_EXTRA_CA_CERTS="$PWD/infra/certs/server.crt" RCT_E2E_SYNTHETIC_SEED=true node node_modules/playwright/cli.js test --config playwright.teaching-clock.config.ts --project=chromium
```

The override changes only entrypoints and mounts for the four educational services. It starts each existing production main class with `/app/BOOT-INF/classes:/app/BOOT-INF/lib/*` plus the externally mounted fixture jar. Base profiles/environments, authenticated TLS routes, status scheduler, publish/adoption fences and geofence remain enabled. Explicit security services reject this initializer identity, and they receive no fixture mount/property. OS time and authentication proof age are unchanged; actual downstream session/retention isolation requires the pending mixed-consumer tests and review.

The dedicated selector now requires a new absolute private run and artifact directory and keeps HTTPS verification enabled. The disposable certificate must also be trusted by the selected Chromium profile, as in the normal CI trust step. Run this finite 09:00 scenario before the application-recovery seed advances the shared educational instant to noon; do not rewind a shared clock after durable lifecycle effects. CI uploads only the closed safe-results.json, never private request/session state.

The dedicated spec supplies device coordinates at the accepted synthetic CI geofence center through the browser Geolocation API. Browser `Date` shares the teaching instant and Moscow zone via `setFixedTime`, which leaves timers running. Node dataset dates use the same worker option; the original real-mobile config defaults to real dates. The scenario polls actual ACTIVE status/eligibility, confirms a fresh plain student's browser check-in200/PRESENT/STUDENT_GEO, and checks an identical receipt replay. Reusing that key with changed valid coordinates must return409/IDEMPOTENCY_PAYLOAD_MISMATCH, preserve the student snapshot and authorized headman journal status/source/markRevision, then permit the original receipt replay. A different student's HEADMAN manualABSENT blocks both valid coordinates and unavailable-position attempts409/MANUAL_ABSENCE_REQUIRES_APPEAL, preservesABSENT and creates no automatic request. No mocked API/fixtureMode or direct DB status write.

Only the dedicated safe reporter is allowed before credentials; trace/video/screenshots/debug/alternate reporters remain forbidden. Tests live in a separate clock-specs directory, so default mandatory smoke is unchanged. A discovery-only `--list --reporter=list` may be used after preparing a synthetic instant/marker (no dataset/browser creation); never override reporter for an actual run.

After a lease, build controls check typed generic-context injection, unchanged unqualified real Clock selection, atomic update visible to withZone clocks, invalid/missing file or marker rejected, and Auth/gateway identity rejected. Separate actual mixed-consumer checks exercise live/expired Academic sessions, retained/expired Attendance downloads and retention byte cleanup, plus BFF Retry-After versus diagnostic timestamps. Inspect each production Boot jar with `verify-jar-isolation.py`; that check requires four built jars and the external fixture jar and reads nested JAR contents, regardless of dependency filename. It fails on absent/contaminated/uninspectable input. Run `python3 tests/e2e/backend-clock/verify-jar-isolation-control.py` for pure structural ZIP controls; these synthetic ZIPs prove inspector sensitivity, not production artifact isolation. Static source-set separation is preparation only. Final production jar/classpath isolation and real application startup remain NOT_RUN until those exact receipts exist.

Browser Date is pinned only in this finite geo lane. Source inspection found no Date/JWT-exp comparison in PWA auth/client, shared session ownership or TMA session admission; backend authentication remains authoritative. Attachment previews, password-recovery/archive/report proof UI and notification throttles do use browser time. This lane does not establish their expiry/security behavior. Use ordinary real-mobile configuration for those scenarios; do not disable expiry or generalize the pinned browser clock to the whole matrix.

Root owns cleanup: stop/dispose only the reserved task stack, or recreate the four services without this override; never leave teaching-clock containers for ordinary acceptance. No private/local-stand resources or actual credentials are read. This focused lane does not claim teacher direct-Instant views, security expiry boundary UI or the rest of the accepted matrix.
