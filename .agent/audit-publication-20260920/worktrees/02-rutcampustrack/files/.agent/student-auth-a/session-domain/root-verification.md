# Root verification of frozen session domain
2026-09-08. Revision 8002b9ea4356b10779c5bb9a6d99746d32d78ae2. Windows/PowerShell, Java21, Gradle8.12. This is a bounded candidate pending independent review, not whole-story acceptance.

Root opened critical domain originals and verified every one of the 16 sourceFiles against manifest SHA256 DD21586B84D7C8AB07CABDEC53483183F183EDAFFD1F585B0620FD21F28B9DF4: zero hash mismatches. Read-only trailing-whitespace scan of all 16 files returned exit0 and no matches; ordinary git diff --check alone does not include untracked new files.

Actual focused command: `.\gradlew.bat :services:auth-service:auth-app:test --tests ru.rutcampustrack.auth.session.ActiveRolePolicyTest --tests ru.rutcampustrack.auth.session.PasswordPolicyTest --tests ru.rutcampustrack.auth.session.SessionLifecycleServiceTest --no-daemon --max-workers=1`.

Actual tool execution at 2026-09-07 23:08:21–23:08:53 UTC returned exit0 / BUILD SUCCESSFUL. Author's surrounding clock capture is 23:08:16.9004833–23:08:54.9301771 UTC. Original JUnit timestamps are 23:08:48–49 UTC. Root and parent independently parsed 4+5+8 tests, all zero failures/errors/skips. Earlier root no-start updates were stale; actual completed execution and release were subsequently corrected in coordination. No repeat is authorised without a new defect and a new resource lease.

Root independently compared original build/test-results/test XML to exact binary copies under evidence/junit:

| Test class | SHA256 of original and copy | Bytes | Tests |
|---|---|---:|---:|
| ActiveRolePolicyTest | 0371F06571125119D38384DFC7ACBE503FFA0AD0843FE0B1369DE7EAFA8918D4 | 920 | 4 |
| PasswordPolicyTest | CD44597B55D8A0D84100630F1115F16ACD8FAD4D39CD0642BE5818FB20C37901 | 1104 | 5 |
| SessionLifecycleServiceTest | AC9BE989F360F131AFC4866DD749994EA387C65A83D237E5CF93C728493FAF63 | 1685 | 8 |

Static corrections made before the passing run: inconsistent same-ID active grant and duplicate grant rejection; authoritative create-session recheck obligation on the port; wrong password test vectors/counts; missing Java string quote. The combining-mark negative also distinguishes non-normalized input from its NFC scalar count. Prior standalone javac exit3 was a compiler-resource closing warning; it is recorded in the author manifest and is not a successful check. The subsequent actual Gradle compile/test is the authoritative compilation evidence.

No SQL adapter, migration, HTTP/Gateway admission, scanner, production operation or genuine TMA run is claimed. Parent launched a fresh Sol high reviewer against this immutable source manifest because the local spawn surface returned an agent-limit error. Its unchanged report will be preserved separately.
