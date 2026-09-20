# Academic source-ready follow-up 2

Date: 2026-09-10. This is an immutable evidence-only readiness artifact for
the accepted bounded V26 alias correction. Product sources are frozen; no
source was edited in this follow-up.

## Readiness decision

Root source readback and the scoped `git diff --check` both passed with exit
code `0`. The V26 correction is the exact lookup hunk in
`validate_campus_map_ready_asset()`:

```sql
SELECT asset.* INTO asset_record
FROM campus_map_asset AS asset
WHERE asset.id = NEW.asset_id
  AND asset.plan_version_id = NEW.plan_version_id
  AND asset.format = NEW.format;
```

The explicit `asset` alias removes the reproduced SQLSTATE `42702` ambiguity
for `plan_version_id` while preserving the existing lookup predicates,
metadata checks, trigger timing and all other migration semantics. The prior
V26 source was SHA256
`4307D82508FB77784C47473FA8404AB7CFFBE944D41C695BF1297DD7D896CD50`,
`17076` bytes; the accepted current V26 source is SHA256
`21B3F522634BE7505FB73A613BF5BCB68C1584B7A1724BB45EBA5C767E4058A0`,
`17109` bytes.

## Exact-four source guard

The fail-closed read-only guard compared each current SHA256 and byte count
with the root-provided values and exited `0` only when all four matched:

| Path | Current SHA256 | Bytes |
| --- | --- | ---: |
| `services/schedule-service/schedule-app/src/main/resources/db/migration/V17__student_occurrence_homework_binding.sql` | `8D02720F48B67E21C78373B214ADB414A2F435A8016C35987839D330B6042FCC` | 18760 |
| `services/academic-service/academic-app/src/main/resources/db/migration/V26__campus_map.sql` | `21B3F522634BE7505FB73A613BF5BCB68C1584B7A1724BB45EBA5C767E4058A0` | 17109 |
| `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/migration/StudentOccurrenceMigrationIT.java` | `29433B97247AE23E8BFF9EA83AD94768DC80A329276E565AE3E3C5063F5928D2` | 23649 |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/migration/StudentFoundationMigrationIT.java` | `8A39EC859D8CF2BA2699295A0D60B8B10FA6988B2AA0DB8BCD3E2434115D446B` | 51464 |

The source guard command was a PowerShell `Get-FileHash -Algorithm SHA256`
and `Get-Item.Length` comparison against all four expected pairs; exit `0`.
The V26 alias readback used targeted `rg -n -C 12` and showed
`SELECT asset.*`, `FROM campus_map_asset AS asset`, and all three qualified
predicates; exit `0`. The scoped command
`git diff --check -- <V26> <AcademicIT> .agent/student-academic-b/sql17-v26-runtime-fix`
also exited `0`.

## Runtime handoff

The prior runtime failure evidence remains recorded in
`runtime-fail-2-2026-09-10.md`: session `85479`, exit `1`, XML SHA256
`C228856B16FAD58609851C4E902166BA4C253D1D247FFF6F1AC081649838D348`,
`100991` bytes, with two SQLSTATE `42702` failures. The corresponding prior
failure evidence file has SHA256
`D9B498E1B29F1968068D81FAA91AF7176E304DB43591C84E04A92C31D6AB020F` and
`2523` bytes.

Stage status is `SOURCE_READY_WAIT_HEAVY_LEASE`. No Gradle, Docker,
Testcontainers, PostgreSQL or product runtime was run for this readiness
follow-up. Heavy runtime validation remains for root under the exclusive
lease; the source scope remains `RELEASED`.
