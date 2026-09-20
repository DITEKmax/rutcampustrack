# Academic runtime lease failure

Date: 2026-09-10. This is evidence-only follow-up; no product source was
changed after the failed lease.

## Lease

- Session: `81124`
- Start: `2026-09-10T18:23:12.0321344Z`
- End: `2026-09-10T18:25:24.3765572Z`
- Environment: Windows local worktree, Java 21 / PostgreSQL 16
  Testcontainers lease; heavy lease `RELEASED` after failure.
- Exact command:
  `.\gradlew.bat :services:academic-service:academic-app:integrationTest --tests ru.rutcampustrack.academic.migration.StudentFoundationMigrationIT --no-daemon --no-parallel --max-workers=1 --console=plain --continue`
- Exit code: `1`
- Result: `BUILD FAILED` after `1m52s`; 37 tasks, 5 executed.

## Runtime evidence

- XML:
  `services/academic-service/academic-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.academic.migration.StudentFoundationMigrationIT.xml`
- XML SHA256: `D474F46C3F2976D4119BB590B3FB75C19C9E4AE9EA1CCB0D9264651F56044C2C`
- XML bytes: `133618`
- XML mtime: `2026-09-10T18:25:10.5638266Z`
- Result counts: 15 tests, 7 failures, 0 errors, 0 skipped; time `26.65s`.

Six failures occur while the isolated-schema Flyway chain reaches V26:
V26 line 129 raises SQLSTATE `42883` because PostgreSQL cannot resolve
`digest(bytea, unknown)` in that isolated schema. One separate failure is the
existing `preFixDeferredChildOnlyCheckReproducesConcurrentZero` case, which
observed `expected 2` but got `1`.

This is a request-linked, reproduced runtime defect against the changed V26
scope. The evidence records the correction request for root review; this
follow-up makes no product correction and does not authorize Terra escalation.

The Schedule command was intentionally not run. Root's post-run checks found no
`java`/`javaw` processes and an empty `docker ps`; the heavy lease is released.
Warnings reported during the run are unrelated existing compile warnings.

## Source integrity after the run

All four product source paths remain at the source-ready hashes:

- V17 `8D02720F48B67E21C78373B214ADB414A2F435A8016C35987839D330B6042FCC`,
  18,760 bytes.
- Schedule IT `29433B97247AE23E8BFF9EA83AD94768DC80A329276E565AE3E3C5063F5928D2`,
  23,649 bytes.
- Academic IT `FCF37A0EA283CB159FC9BEF638B5FD2366775B8F519AF7399A85A97866095F5B`,
  47,643 bytes.
- V26 `51AF63A7A591E278353B66816A01294E4823A705269D88C16BFABF007A3F13B8`,
  17,050 bytes.

## Scope disposition

Product ownership remains `RELEASED` for the exact four source paths. Runtime
acceptance is `BLOCKED` on the reproduced V26 function-resolution defect and
the unrelated pre-fix race result pending root's correction/decision. No other
path was edited by this follow-up.
