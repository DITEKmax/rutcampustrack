# Checks

Environment: Windows PowerShell checkout at C:/Users/maksd/.codex/worktrees/1456/rutcampustrack.
Baseline revision: 8002b9ea4356b10779c5bb9a6d99746d32d78ae2.

| Check | Command | Exit | Evidence |
|---|---|---:|---|
| Revision | git rev-parse HEAD | 0 | HEAD equals baseline above |
| Scoped paths | git diff --name-only -- [four owned paths] | 0 | exactly the four owned source/test paths |
| Whitespace | git diff --check -- [four owned paths] | 0 | no whitespace errors; Git emitted only LF-to-CRLF advisory warnings |
| Scoped stat | git diff --numstat -- [four owned paths] | 0 | 552/30, 274/29, 15/6, 65/67 |
| Current hashes | Get-FileHash -Algorithm SHA256 [four owned paths] | 0 | values recorded in evidence.md |
| Source symbols | rg -n for sig allowlist, JOSE names, duplicate-key parser, alternate algorithms, duplicate fixtures, and full tuple assertion | 0 | evidence.md observations and source locations |
| Direct javac probe | javac validator with Gradle-cache JJWT classpath | 1 | environment-only probe: Java reported AccessDeniedException for the dependency archive and missing classpath symbols; not an acceptance result |

The direct javac probe was stopped after the environment failure. It did not mutate
repository files. Root's focused Gradle check is the authoritative compile/test gate.

No Gradle, Docker, Testcontainers, product runtime, staging, commit, reset, or deploy
was run by this leaf.
