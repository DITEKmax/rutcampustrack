# Checks and exit codes

Environment: Windows PowerShell, worktree `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-integration-l5a`, author static stage only.

| Check | Command | Exit | Evidence |
|---|---|---:|---|
| whitespace/error scan | `git diff --check` | 0 | no diff errors; Git emitted known LF→CRLF checkout warnings for copied source files |
| conflict markers | `rg -n '^(<<<<<<<|=======|>>>>>>>)'` with no-match guard | 0 | `no conflict markers` |
| constructor/handler structure | PowerShell assertions over `AcademicGrpcServiceImpl.java` | 0 | one `@Autowired`; assignment/semester/grant/map/projection markers; source authority; assignment-ID handler; signed identity; 64 KiB bound |
| Gradle dependency structure | PowerShell assertions over `build.gradle.kts` | 0 | target common protos plus source compile/test PostgreSQL and one runtime PostgreSQL |
| scoped product diff | inventory-driven exact path assertion | 0 | 46 allowed, 46 changed, 0 missing, 0 extra |
| exact source blobs | inventory-driven `git hash-object --no-filters` assertion | 0 | 44/44 exact |

Gradle/Docker and product runtime checks are intentionally not run by this leaf; root owns the H76/H77 lease and runtime evidence.
