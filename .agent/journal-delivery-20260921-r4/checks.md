# Checks ledger

The root-granted affected batch used handle `19865` and exited 1 after Gradle
reported 1m43s. Unit selections passed before the IT task: MarkingServiceTest
16/16, ReportServiceTest 21/21, and StudentRequestServiceAuthorizationTest
18/18. The IT task initially had two fixture-only failures; no product fallback
was added.

Addressed evidence and corrections:

- `78784`, Gradle 1m04s, exposed the existing IT's missing dated roster echo
  and a duplicate `(lesson_id,user_id)` in the new test setup.
- The IT fixture now supplies `asOfDate=2026-09-06, semesterId=30`; the new
  test mutates the unique attendance document instead of inserting a duplicate.
- Final targeted IT handle `70801` exited 0; Gradle reported 1m28s and the XML
  reports 22 tests, skipped 0, failures 0, errors 0 (suite time 16.888s).
- A source self-review then found that `markWithLesson` needed to evaluate the
  old status before setting the new one. The existing IT was extended with two
  direct port cases: non-EXCUSED plus legacy metadata to EXCUSED deletes the
  pair blob and metadata; EXCUSED plus a live blob to EXCUSED retains it.
- Root-granted final IT-only handle `29410` exited 0; Gradle reported 1m25s and
  the XML reports 22 tests, skipped 0, failures 0, errors 0 (suite time
  10.581s). The heavy lease was released after this run.

The previously accepted journal HIGH, semester, and OpenAPI PASS results were
not repeated.

Frontend dependency setup and checks:

- `npm ci` in `frontends` using the existing lockfile — exit 0, 11s observed;
  no package or lockfile diff. npm reported 4 existing vulnerabilities (2
  moderate, 2 high); `npm audit fix` was not run.
- `npm run typecheck --workspace @rct/mobile-core` — exit 0.
- `npm run typecheck --workspace @rct/pwa-vue` — exit 0.
- `npm run typecheck --workspace @rct/tma-vue` — exit 0.
- targeted `npm exec --workspace @rct/mobile-core vitest run
  src/features/headman-journal/headman-journal-client.test.ts` — exit 0,
  1 file / 6 tests.
- `npm run lint --workspace @rct/mobile-core` — exit 0.

The earlier pre-install exit-1 attempts were dependency-availability failures;
they were resolved by the authorized lockfile-only install. The heavy
commands/results are recorded above with terminal handles and released leases.
