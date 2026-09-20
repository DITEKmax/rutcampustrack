# P2 BFF correction — fresh Sol recheck R3

Date: 2026-09-08. Risk: S3. Result: **FAIL — one MEDIUM verification finding**.

The reviewer was read-only and inspected the stable exact-five-file scope in
`.agent/worktrees/requests-bff-contract`. Both earlier functional MEDIUM defects
are corrected in the current production code, but the required executable guard
for the public eligibility wire list is missing.

## MEDIUM — eligibility wire-list has no executable contract guard

- Locations: `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/contract/StudentRequestDetailJsonTest.java:15`
  and `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClientErrorTest.java:34`.
- Evidence: neither focused test class references `EligibilityReason`; a search
  across repository test sources found no check of the full public list. The
  fresh XML contains 23 translator cases and two nullable-detail cases. The
  current list in `StudentApiModels.java:36` is correct, but only statically.
- Impact: reintroducing `EligibilityReason.INTERNAL_ERROR` would leave the
  focused selector green. `StudentQueryService.java:282` uses `valueOf` for an
  incoming proto name and does not create an exhaustive compile guard.
- Reproduction: in a disposable checkout, add `INTERNAL_ERROR` to
  `EligibilityReason` and run the same two-class selector. The current tests do
  not observe the change.
- Correction: unfreeze only `StudentRequestDetailJsonTest.java`; add a JSON/wire
  assertion that serializes `EligibilityReason.values()` and checks exactly the
  eleven canonical names, including absence of `INTERNAL_ERROR`. Re-run the
  exact two-class selector with `--rerun-tasks`, then perform a fresh independent
  recheck.

## Functional conclusions

- `EligibilityReason` currently ends at `DEPENDENCY_UNAVAILABLE` and agrees with
  `attendance.proto`, OpenAPI and generated TypeScript. `ProblemCode.INTERNAL_ERROR`
  remains available where it belongs.
- `MobileAttendanceClient.java:104` applies the raw transport/auth guard before
  typed metadata. Raw authentication, authorization and availability statuses
  map to 401/403/503; `INTERNAL`, `UNKNOWN` and `DATA_LOSS` remain 500 even with
  plausible typed 4xx metadata; malformed decoding fails closed to 500; valid
  cooldown metadata preserves `retryAt`.

## Evidence checked

- Exact-five hashes matched `exact5-manifest.json`; read-only finite guard exited
  0 with `82/2/5` paths and zero mismatches.
- Fresh XML: `MobileAttendanceClientErrorTest` 23/0/0/0, SHA-256
  `2606327692D861F1E47C91530479FBBF6E0DA99AD0C0221BF7F5B118F5D9CED3`;
  `StudentRequestDetailJsonTest` 2/0/0/0, SHA-256
  `53373CCC4B5EAFA9C98869A38D7320B581683866F8A66743E590B59A86CEC89C`.
  Suite timestamp is `2026-09-08T18:14:11Z`; exact-five sources remained older
  than the XML and retained their hashes.
- Focused session `81570`: exit 0, `BUILD SUCCESSFUL` in 2m23s, 39 tasks executed.
  Diagnostic compile session `21236`: exit 0, eight tasks executed. Daemon
  creation/last-write times are not reported as exact Gradle process bounds.
- Live HTTP/gRPC/Gateway/Mongo/Docker runtime was not part of this unit and
  serialization recheck.

Reviewer runtime model/effort were requested as `gpt-5.6-sol/high`; the reviewer
reported that its own surface did not expose independent runtime metadata.
