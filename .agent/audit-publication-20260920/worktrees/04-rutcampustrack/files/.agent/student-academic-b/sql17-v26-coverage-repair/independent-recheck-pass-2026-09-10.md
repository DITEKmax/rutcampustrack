# Independent recheck pass — SQL17/V26 exact coverage

Дата: 2026-09-10. Fresh independent read-only reviewer:
`/root/sql17_26_coverage_recheck`, model `gpt-5.6-sol`, effort `high`,
`fork-none`.

## Verdict

`FINAL PASS — SQL17_V26_COVERAGE_RECHECK_PASS`; findings: none.

Оба MEDIUM finding свежего Sol review закрыты в стабильном diff:

- V17: recurring и one-off valid control rows, cross-origin INSERT и UPDATE,
  exact SQLSTATE/message и rollback invariants для count/origin/state.
- V26: exact READY dimension mismatch, post-publication format INSERT,
  missing-intent и mismatched-intent dedupe; проверены unchanged
  graph/format/asset/dedupe/daily-aggregate invariants.

## Frozen source and runtime evidence

Exact-four source guard PASS; production SQL unchanged:

| Path | SHA256 | Bytes |
| --- | --- | ---: |
| `services/schedule-service/schedule-app/src/main/resources/db/migration/V17__student_occurrence_homework_binding.sql` | `8D02720F48B67E21C78373B214ADB414A2F435A8016C35987839D330B6042FCC` | 18760 |
| `services/academic-service/academic-app/src/main/resources/db/migration/V26__campus_map.sql` | `21B3F522634BE7505FB73A613BF5BCB68C1584B7A1724BB45EBA5C767E4058A0` | 17109 |
| `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/migration/StudentOccurrenceMigrationIT.java` | `633285777706AF26FEF3B847B172BA1805C41E6607B5170025F4DE6A02AF1A68` | 28608 |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/migration/StudentFoundationMigrationIT.java` | `14C68464CFBD29EDF20C87021D9353863C81E5B0044C193D09930FBBCEE7FB8B` | 58295 |

Focused runtime evidence accepted by the reviewer:

- Academic `StudentFoundationMigrationIT`: session `75913`, exit `0`,
  15/15; XML `20715B9C9782407A9842AC0324C2CF9EE577445AD2001DCF2FEA89FCDE4C4254`
  / `94655` bytes.
- Schedule `StudentOccurrenceMigrationIT`: session `2390`, exit `0`, 7/7;
  XML `7E316529DAD60FDB6814B9FDE3014B30B2137F47ADB39BF92505AFE9F509367F`
  / `36150` bytes.
- Prior unchanged Flyway `3/3` evidence is accepted with XML SHA256
  `36C4E903234ACF3158E0CFD716D08D82B176B4E18C8430AA274D5E9484340BAA` /
  `18743` bytes. Its current XML is absent after focused-task cleanup; this is
  an expected limitation and Flyway was intentionally not rerun.

## Scope decision

Focused SQL17/V26 migration scope is accepted and remains `RELEASED`. Full-role
acceptance outside this migration scope remains open. No production SQL,
product/test source, shared status, stage, commit, reset or clean changed in
this evidence-only follow-up.
