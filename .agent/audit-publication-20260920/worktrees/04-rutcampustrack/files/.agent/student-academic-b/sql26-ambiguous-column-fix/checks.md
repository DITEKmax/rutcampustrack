# Checks

Environment: Windows PowerShell, shared checkout `C:\Users\maksd\.codex\worktrees\34a5\rutcampustrack`, revision `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. No heavy runtime was run.

| Check | Result | Exit |
| --- | --- | ---: |
| Guarded prehash and pre-bytes | `4307D82508FB77784C47473FA8404AB7CFFBE944D41C695BF1297DD7D896CD50`, `17076` | 0 |
| Source readback | Function lines 242–257 show `SELECT asset.*`, `FROM campus_map_asset AS asset`, and all three qualified predicates | 0 |
| Focused alias marker search | `rg -n -C 7 'SELECT asset\\.\\* INTO asset_record|FROM campus_map_asset AS asset|asset\\.id = NEW\\.asset_id|asset\\.plan_version_id = NEW\\.plan_version_id|asset\\.format = NEW\\.format' -- services/academic-service/academic-app/src/main/resources/db/migration/V26__campus_map.sql` | 0 |
| Dangerous unqualified predicate absence | PowerShell `Select-String` search for `WHERE id = NEW.asset_id`, `AND plan_version_id = NEW.plan_version_id`, and `AND format = NEW.format` returned no matches | 0 |
| Trailing whitespace scan | PowerShell scan of V26 returned no `[ \\t]+$` matches | 0 |
| Scoped whitespace diff | `git diff --check -- services/academic-service/academic-app/src/main/resources/db/migration/V26__campus_map.sql .agent/student-academic-b/sql26-ambiguous-column-fix` | 0 |
| Posthash and post-bytes | `21B3F522634BE7505FB73A613BF5BCB68C1584B7A1724BB45EBA5C767E4058A0`, `17109` | 0 |

The scoped Git check has exit 0. Because the pre-existing V26 source is untracked in this checkout, the exact changed hunk and the evidence-directory files are read back independently and recorded in `diff.md`/`evidence.md`.
