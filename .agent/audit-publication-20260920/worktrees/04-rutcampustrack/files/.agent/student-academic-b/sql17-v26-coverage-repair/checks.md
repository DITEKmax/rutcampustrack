# Checks — SQL17/V26 exact coverage repair

Environment: Windows PowerShell, Java/Gradle project checkout
`C:\Users\maksd\.codex\worktrees\34a5\rutcampustrack`; revision
`8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

| Check | Command / scope | Exit | Evidence |
| --- | --- | ---: | --- |
| Revision | `git rev-parse HEAD` | 0 | `8002b9ea4356b10779c5bb9a6d99746d32d78ae2` |
| Pre-write exact-four guard | PowerShell `Get-FileHash -Algorithm SHA256` + `Get-Item.Length` against the four frozen pairs | 0 | Four prehashes in `packet.md` |
| Post SQL guard | PowerShell SHA256/bytes comparison for V17 SQL and V26 SQL | 0 | `evidence.md` source table |
| Post test hashes | PowerShell `Get-FileHash -Algorithm SHA256` + `Get-Item.Length` for the two target IT files | 0 | `manifest.json` |
| Exact marker/readback | `rg -n --fixed-strings` for P0001, all exact messages, both origin UPDATEs, exact helper and nested SQLException extraction | 0 | `evidence.md` line map |
| Java delimiter/trailing-space | PowerShell brace-depth scan for both files plus `rg -n '[\t ]+$'` | 0 | Both brace depths 0; trailing hits 0 |
| Import/class sanity | PowerShell duplicate-import scan and one class declaration per target | 0 | No duplicate imports; class count 1 each |
| Scoped whitespace check | `git diff --check -- services/.../StudentOccurrenceMigrationIT.java services/.../StudentFoundationMigrationIT.java` | 0 | No diagnostics |
| Evidence manifest | PowerShell `Get-Content -Raw manifest.json | ConvertFrom-Json` plus status/product-count assertions | 0 | `SOURCE_READY`, `RELEASED`, four product records |
| Evidence whitespace | `rg -n '[\t ]+$' .agent/student-academic-b/sql17-v26-coverage-repair` | 0 | Zero evidence trailing-whitespace hits |
| Scope readback | `git status --short -- <two IT paths> <two SQL paths> <new evidence directory>` | 0 | Only the two IT files and this evidence directory are this leaf's scope |
| Exact Academic runtime | Root command from frozen packet; session `75913`; `StudentFoundationMigrationIT` | 0 | 15 tests, 0 failures/errors/skipped; XML hash recorded in `runtime-pass-2026-09-10.md` |
| Exact Schedule runtime | Root StudentOccurrence-only selector; session `2390` | 0 | 7 tests, 0 failures/errors/skipped; XML hash recorded in `runtime-pass-2026-09-10.md` |
| Runtime XML/source rehash | PowerShell SHA256/bytes for two new XMLs, prior Flyway XML evidence and four source paths | 0 | Current values match root report and frozen SQL guard |
| Independent Sol recheck | Fresh read-only `/root/sql17_26_coverage_recheck`, `gpt-5.6-sol` high, fork-none | 0 | `SQL17_V26_COVERAGE_RECHECK_PASS`, findings none |
| Final artifact/hash readback | PowerShell readback of independent pass, manifest status/reviewer fields and SHA256/bytes | 0 | Review artifact `FC223B4A026AC6F4A3A028537CDA6CC707CB4768118B063836A821E693637CA4` / 2613; manifest updated after rehash |

The target IT files were already untracked in the dirty shared checkout, so a
raw `git diff --no-index --check NUL <target>` returns exit `1` for ordinary
file-versus-empty differences; it produced no whitespace diagnostics. The
actual scoped `git diff --check` and the direct trailing-whitespace scan above
both exit `0`. No stage/commit/reset/clean was performed.

## Runtime / compile checks

The two exact selectors were run by root under the exclusive lease; Flyway was
intentionally not rerun and its prior `3/3` XML evidence remains authoritative
for that unchanged path. Processes/ports were clear, Docker empty and the heavy
lease was released. Current Flyway XML is absent after focused-task cleanup and
is recorded as an expected limitation. No Terra gate was opened.
