# Academic runtime lease failure 2

Date: 2026-09-10. This is evidence-only follow-up; no product source was
changed after the prior correction lease.

## Lease

- Session: `85479`
- Start: `2026-09-10T18:47:50.2474775Z`
- End: `2026-09-10T18:50:11.5164048Z`
- Environment: Windows local worktree, Java 21 / PostgreSQL 16
  Testcontainers lease; heavy lease `RELEASED` after failure.
- Exact command:
  `./gradlew.bat :services:academic-service:academic-app:integrationTest --tests
  ru.rutcampustrack.academic.migration.StudentFoundationMigrationIT --no-daemon
  --no-parallel --max-workers=1 --console=plain --continue`
- Exit code: `1`
- Result: `BUILD FAILED` after `1m55s`; 15 tests, 2 failures, 0 errors,
  0 skipped; test time `35.579s`.

## Runtime evidence

- XML:
  `services/academic-service/academic-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.academic.migration.StudentFoundationMigrationIT.xml`
- XML SHA256: `C228856B16FAD58609851C4E902166BA4C253D1D247FFF6F1AC081649838D348`
- XML bytes: `100991`
- XML mtime: `2026-09-10T18:49:52.5396514Z`

The two failures are `readyMapAssetsRequireExactMetadataAndRemainImmutable`
at line 103 and
`publishedCampusMapFreezesPlanIdentityAndGraphButDraftTransitionsRemainAllowed`
at line 186. Both fail while V26's
`validate_campus_map_ready_asset()` reaches line 30: PostgreSQL SQLSTATE
`42702`, `ambiguous column reference plan_version_id`.

This is a request-linked, reproduced runtime defect in the changed V26 scope.
This follow-up records the defect and new evidence only; it does not edit
product sources or authorize a Terra escalation. Any correction or escalation
must be decided by root under the recorded defect/complexity gate.

## Scope and environment disposition

Schedule was intentionally not run. Java/Javaw processes were clear, ports
`18210` and `18211` were clear, and the heavy lease is `RELEASED`.

The exact four source paths remained unchanged after this run. Read-only
post-run SHA256/byte readback:

- V17 migration: `8D02720F48B67E21C78373B214ADB414A2F435A8016C35987839D330B6042FCC`,
  `18760` bytes.
- Schedule IT: `29433B97247AE23E8BFF9EA83AD94768DC80A329276E565AE3E3C5063F5928D2`,
  `23649` bytes.
- Academic IT: `8A39EC859D8CF2BA2699295A0D60B8B10FA6988B2AA0DB8BCD3E2434115D446B`,
  `51464` bytes.
- V26 migration: `4307D82508FB77784C47473FA8404AB7CFFBE944D41C695BF1297DD7D896CD50`,
  `17076` bytes.

No product source, V17/Schedule artifact, shared status, or external runtime
state was changed by this evidence follow-up.
