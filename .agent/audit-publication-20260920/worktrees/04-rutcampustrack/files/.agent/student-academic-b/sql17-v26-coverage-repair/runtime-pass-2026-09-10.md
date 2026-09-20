# Runtime pass recheck — SQL17/V26 exact coverage

Дата: 2026-09-10. Root выполнил только affected exact selectors под
exclusive heavy lease. Product/test source после runtime не менялся.

## Academic

- Exact command:
  `.\gradlew.bat :services:academic-service:academic-app:integrationTest --tests ru.rutcampustrack.academic.migration.StudentFoundationMigrationIT --no-daemon --no-parallel --max-workers=1 --console=plain --continue`
- Session: `75913`.
- Start: `2026-09-10T19:46:54.4893287Z`.
- End: `2026-09-10T19:48:47.3402424Z`.
- Exit code: `0`.
- Result: `BUILD SUCCESSFUL` after `1m34`; 37 tasks, 2 executed.
- XML:
  `services/academic-service/academic-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.academic.migration.StudentFoundationMigrationIT.xml`
- XML SHA256: `20715B9C9782407A9842AC0324C2CF9EE577445AD2001DCF2FEA89FCDE4C4254`.
- XML bytes: `94655`; mtime `2026-09-10T19:48:36.4401730Z`.
- Tests: 15 total, 0 failures, 0 errors, 0 skipped; time `29.813s`.
- Reported warnings: two unrelated warnings in `UserRepositorySearchIT` and an
  unrelated unchecked note elsewhere. They have no link to this request and
  caused no code change.

## Schedule

- Exact command:
  `.\gradlew.bat :services:schedule-service:schedule-app:integrationTest --tests ru.rutcampustrack.schedule.migration.StudentOccurrenceMigrationIT --no-daemon --no-parallel --max-workers=1 --console=plain --continue`
- Session: `2390`.
- Start: `2026-09-10T19:48:53.6122527Z`.
- End: `2026-09-10T19:50:44.8751284Z`.
- Exit code: `0`.
- Result: `BUILD SUCCESSFUL` after `1m20`; 37 tasks, 2 executed.
- XML:
  `services/schedule-service/schedule-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.schedule.migration.StudentOccurrenceMigrationIT.xml`
- XML SHA256: `7E316529DAD60FDB6814B9FDE3014B30B2137F47ADB39BF92505AFE9F509367F`.
- XML bytes: `36150`; mtime `2026-09-10T19:50:19.0916824Z`.
- Tests: 7 total, 0 failures, 0 errors, 0 skipped; time `14.026s`.

## Flyway and runtime state

FlywayMigrationIT was intentionally not rerun. The prior unchanged evidence
remains `3/3`, exit `0`, XML SHA256
`36C4E903234ACF3158E0CFD716D08D82B176B4E18C8430AA274D5E9484340BAA`,
`18743` bytes. New exact scope is `22/22` tests passed with 0
failures/errors/skipped. Root reported processes/ports clear, Docker empty and
heavy lease `RELEASED`.

## Source and artifact guards

Post-runtime source SHA256/bytes remain:

| Path | SHA256 | Bytes |
| --- | --- | ---: |
| `services/schedule-service/schedule-app/src/main/resources/db/migration/V17__student_occurrence_homework_binding.sql` | `8D02720F48B67E21C78373B214ADB414A2F435A8016C35987839D330B6042FCC` | 18760 |
| `services/academic-service/academic-app/src/main/resources/db/migration/V26__campus_map.sql` | `21B3F522634BE7505FB73A613BF5BCB68C1584B7A1724BB45EBA5C767E4058A0` | 17109 |
| `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/migration/StudentOccurrenceMigrationIT.java` | `633285777706AF26FEF3B847B172BA1805C41E6607B5170025F4DE6A02AF1A68` | 28608 |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/migration/StudentFoundationMigrationIT.java` | `14C68464CFBD29EDF20C87021D9353863C81E5B0044C193D09930FBBCEE7FB8B` | 58295 |

Current XML hash/byte recomputation, manifest JSON parse, source readback and
scoped diff-check all exited `0`. This artifact records runtime evidence only;
no new product/test source edit, stage, commit, reset or clean was performed.

Status: `RUNTIME_PASS_RECHECK_ACTIVE`; source scope remains `RELEASED`.
