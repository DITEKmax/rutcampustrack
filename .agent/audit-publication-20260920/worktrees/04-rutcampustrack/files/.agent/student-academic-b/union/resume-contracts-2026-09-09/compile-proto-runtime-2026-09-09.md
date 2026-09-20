# B0 exact25 compile/proto heavy evidence

Captured: 2026-09-09. Heavy lease is released. This record covers only the two root-owned Gradle checks; it does not claim SQL, migration, full B0, or full-role PASS.

## Command 1 — contract compilation

Command:

    .\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain :services:auth-service:auth-api-contract:compileJava :services:academic-service:academic-api-contract:compileJava :services:schedule-service:schedule-api-contract:compileJava :services:mobile-bff:mobile-bff-api-contract:compileJava

- Unified session: 16185.
- Exit code: 0.
- Result: BUILD SUCCESSFUL in about 36 seconds.
- Gradle summary: 5 tasks, 4 executed, 1 up-to-date.
- Started around 2026-09-09 19:12Z; exact start/end timestamps are unavailable.

## Command 2 — proto generation

Command:

    .\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain :services:academic-service:academic-app:generateProto :services:schedule-service:schedule-app:generateProto

- Unified session: 85875.
- Exit code: 0.
- Result: BUILD SUCCESSFUL in about 34 seconds.
- Gradle summary: 9 tasks, 2 executed, 7 up-to-date.
- Started around 2026-09-09 19:12Z; exact start/end timestamps are unavailable.

## Generated artifact evidence

- Each of academic-app and schedule-app reported Java generated artifact count 137 and gRPC artifact count 4.
- Newest generated timestamps recorded for the two apps: 2026-09-09 19:13:07Z and 2026-09-09 19:13:09Z, in command order.
- These are generated-source artifacts from the proto check; no product implementation or generated-file source edits were made by this leaf.

## Post-check guards

- Root post guard found no java, javaw, or gradle process remaining.
- proto/academic.proto SHA256 remained 2B6206897E7145D3867D7DBA669765843BEFBA44966A4D4EC6110142100B7DBC.
- proto/schedule.proto SHA256 remained 44FFABEB7965D5935D481E1628C3C1DD590CA15E1323E09E2E82376A22ACE858.
- The exact25 source hash manifest remains file-sha256.md in this resume directory.
- Heavy lease: RELEASED.

## Acceptance boundary

- PASS recorded only for the four contract compileJava tasks and the two proto generation tasks above.
- SQL, V24/V25/V26/V17 migration integration, product runtime, full B0 behavior, and full-role acceptance remain unverified and are not claimed here.
- Fresh independent Sol review1 is active after this evidence handoff.