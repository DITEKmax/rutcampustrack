# Final runtime pass handoff

Date: 2026-09-10. This is an evidence-only final runtime artifact. Product
sources are frozen and unchanged during this handoff. Stage status is
`RUNTIME_PASS_REVIEW_ACTIVE`; product scope remains `RELEASED`.

## Academic runtime

- Command:
  `.\gradlew.bat :services/academic-service/academic-app:integrationTest --tests
  ru.rutcampustrack.academic.migration.StudentFoundationMigrationIT --no-daemon
  --no-parallel --max-workers=1 --console=plain --continue`
- Session: `5891`
- Start: `2026-09-10T19:12:58.6500149Z`
- End: `2026-09-10T19:14:48.7736980Z`
- Exit code: `0`
- Result: `BUILD SUCCESSFUL` after `1m25s`; 37 tasks, 2 executed.
- XML:
  `services/academic-service/academic-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.academic.migration.StudentFoundationMigrationIT.xml`
- XML SHA256: `3486C95606EA3E03B29635F9CAD52A32E95AB05820CC18A7CF9657809FEE1196`
- XML bytes: `94655`
- XML mtime: `2026-09-10T19:14:34.8538714Z`
- Tests: 15 total, 0 failures, 0 errors, 0 skipped; time `34.479s`.

## Schedule runtime

- Command:
  `.\gradlew.bat :services/schedule-service/schedule-app:integrationTest --tests
  ru.rutcampustrack.schedule.migration.StudentOccurrenceMigrationIT --tests
  ru.rutcampustrack.schedule.migration.FlywayMigrationIT --no-daemon
  --no-parallel --max-workers=1 --console=plain --continue`
- Session: `60350`
- Start: `2026-09-10T19:14:57.8350413Z`
- End: `2026-09-10T19:17:24.8592003Z`
- Exit code: `0`
- Result: `BUILD SUCCESSFUL` after `1m51s`; 37 tasks, 5 executed.
- StudentOccurrence XML:
  `services/schedule-service/schedule-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.schedule.migration.StudentOccurrenceMigrationIT.xml`
- StudentOccurrence XML SHA256: `CB3119D6C0F7E5CD9866E85A785D4545832A55D86EF8F6E973FA0135E46C90A3`
- StudentOccurrence XML bytes: `33863`
- StudentOccurrence XML mtime: `2026-09-10T19:16:55.8111545Z`
- StudentOccurrence tests: 7 total, 0 failures, 0 errors, 0 skipped; time `10.66s`.
- Flyway XML:
  `services/schedule-service/schedule-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.schedule.migration.FlywayMigrationIT.xml`
- Flyway XML SHA256: `36C4E903234ACF3158E0CFD716D08D82B176B4E18C8430AA274D5E9484340BAA`
- Flyway XML bytes: `18743`
- Flyway XML mtime: `2026-09-10T19:16:55.7984759Z`
- Flyway tests: 3 total, 0 failures, 0 errors, 0 skipped; time `7.708s`.

Aggregate runtime result: `25/25` tests passed, with 0 failures, 0 errors and
0 skipped across the Academic and Schedule sessions. Java/Javaw processes and
ports `18210`/`18211` were clear, Docker was empty, and the heavy lease is
`RELEASED`.

## Source and evidence guards

The fail-closed exact-four source guard remained green with the source-ready
values: V17 `8D02720F48B67E21C78373B214ADB414A2F435A8016C35987839D330B6042FCC`
(`18760` bytes), V26
`21B3F522634BE7505FB73A613BF5BCB68C1584B7A1724BB45EBA5C767E4058A0`
(`17109` bytes), StudentOccurrenceMigrationIT
`29433B97247AE23E8BFF9EA83AD94768DC80A329276E565AE3E3C5063F5928D2`
(`23649` bytes), and StudentFoundationMigrationIT
`8A39EC859D8CF2BA2699295A0D60B8B10FA6988B2AA0DB8BCD3E2434115D446B`
(`51464` bytes). The V26 alias correction and its source readback remain
captured in `source-ready-2-2026-09-10.md`.

The preceding failure evidence remains linked: `runtime-fail-2-2026-09-10.md`
has SHA256 `D9B498E1B29F1968068D81FAA91AF7176E304DB43591C84E04A92C31D6AB020F`
and `2523` bytes; its failed Academic XML was SHA256
`C228856B16FAD58609851C4E902166BA4C253D1D247FFF6F1AC081649838D348` and
`100991` bytes.

Read-only recomputation of all four source hashes and three runtime XML hashes
passed with exit `0`. Artifact readback, manifest JSON validation and scoped
`git diff --check` passed with exit `0`. No runtime was rerun by this evidence
handoff; the recorded sessions are the source of the PASS facts above.

## Handoff

Runtime acceptance is `PASS` for the recorded Academic and Schedule scopes.
The stage is handed to the required fresh independent review under
`RUNTIME_PASS_REVIEW_ACTIVE`. No product source, shared status outside this
stage, or external runtime state was changed by this follow-up.
