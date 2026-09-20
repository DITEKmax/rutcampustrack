# Source-ready result

Date: 2026-09-10. Base revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
Environment: Windows PowerShell, worktree
`C:/Users/maksd/.codex/worktrees/34a5/rutcampustrack`.

Root's preguard matched all four paths before the first product write. The
original V26 guard was `315E3F72392404622901E0EA88B24C3F9FBF6E49FAFBDE646377B23AD06BB01B`
and 15,099 bytes. Root then explicitly expanded this bounded repair to V26
after the recorded Sol content FAIL; V26 is therefore intentionally changed.

## Product paths released by this leaf

| Path | Post SHA256 | Bytes |
| --- | --- | ---: |
| `services/schedule-service/schedule-app/src/main/resources/db/migration/V17__student_occurrence_homework_binding.sql` | `8D02720F48B67E21C78373B214ADB414A2F435A8016C35987839D330B6042FCC` | 18760 |
| `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/migration/StudentOccurrenceMigrationIT.java` | `29433B97247AE23E8BFF9EA83AD94768DC80A329276E565AE3E3C5063F5928D2` | 23649 |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/migration/StudentFoundationMigrationIT.java` | `FCF37A0EA283CB159FC9BEF638B5FD2366775B8F519AF7399A85A97866095F5B` | 47643 |
| `services/academic-service/academic-app/src/main/resources/db/migration/V26__campus_map.sql` | `51AF63A7A591E278353B66816A01294E4823A705269D88C16BFABF007A3F13B8` | 17050 |

## Product delta

- V17's physical snapshot trigger now compares both origin IDs with
  null-safe `IS DISTINCT FROM`.
- Schedule IT adds independent recurring-template and one-off crosswire
  rejection cases.
- V26 freezes plan-version identity and published format graph with parent-row
  locking, permits draft format transitions, and removes the intent/dedupe FK
  that blocked independent TTL cleanup. Dedupe insertion locks and validates
  its matching intent before the exactly-once aggregate increment.
- Academic IT adds PG16 behavior cases for asset metadata/immutability,
  published graph and plan identity, draft transitions, publication/edit
  serialization, intent identity/conflicts, dedupe idempotency/aggregate
  effects, and both retention boundaries.

## Runtime and ownership

No Gradle, Java compilation, Docker, Testcontainers or PostgreSQL runtime was
run, as required by the frozen leaf contract. Runtime evidence is pending the
root's exclusive heavy lease. Ownership is RELEASED for exactly the four
product paths in this file and the evidence directory
`.agent/student-academic-b/sql17-v26-content-repair/`; no other path was edited
by this leaf.
