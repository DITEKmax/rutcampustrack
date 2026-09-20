# Root verification of repair 01
2026-09-08. Baseline 8002b9ea4356b10779c5bb9a6d99746d32d78ae2. This append-only record does not replace the author's reports or the original independent review.

Root opened the four changed main classes and focused negative tests. Root independently verified final manifest SHA256 2200AED1C86FA3D54BC2AF1C3E6BD642A2923B36858406B349255935BEA8BA64, all 16 source SHA256/byte counts (0 mismatches), and all three original/copied JUnit pairs with SHA256 plus direct binary-byte comparison (0 mismatches). PowerShell read-only verification exited 0.

The actual authorized command was:
    .\gradlew.bat :services:auth-service:auth-app:test --tests ru.rutcampustrack.auth.session.ActiveRolePolicyTest --tests ru.rutcampustrack.auth.session.PasswordPolicyTest --tests ru.rutcampustrack.auth.session.SessionLifecycleServiceTest --no-daemon --max-workers=1

Root observed the actual tool result: start 2026-09-08T00:13:42.109Z, end 00:14:26.769Z, exit 0, BUILD SUCCESSFUL in 42s. Root independently parsed XML: 4 + 6 + 11 = 21 tests, 0 failures/errors/skips. The Gradle lease was explicitly released and parent acknowledged release. There was no rerun.

Evidence wording correction: the author's final status.md command line uses slashes inside the Gradle project path. That is a transcription typo, not the executed command. The exact executed colon-delimited command above and the manifest/checks.json command are authoritative. The earlier pretest-status hash omitted its last F; the author corrected it before the run. Actual pretest SHA256 is 78DDE3675989DBA9B4FBC0E51D22CC8854163528FFEEB555FDD405E239848ECF.

Author FINAL released the outer checkout. A fresh Sol high read-only recheck started after release. Its verdict is still pending; this is not full domain acceptance. SQL/HTTP/Gateway/WS/scanner integration remains OPEN; product runtime is N/A for this pure-domain patch.
