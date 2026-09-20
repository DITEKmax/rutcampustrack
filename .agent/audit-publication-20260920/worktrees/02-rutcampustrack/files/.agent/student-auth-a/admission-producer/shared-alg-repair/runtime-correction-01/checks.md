# Checks

Environment: Windows PowerShell, `C:/Users/maksd/.codex/worktrees/1456/rutcampustrack`.
Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

| Check | Command | Exit | Evidence |
|---|---|---:|---|
| Revision | `git rev-parse HEAD` | 0 | Baseline revision matches. |
| Target hash/bytes | `Get-FileHash -Algorithm SHA256 -LiteralPath services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java` + `Get-Item -LiteralPath services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java` | 0 | `23230`, `E8633DE82330BCCB8A9805E37351F5E9F439A27FD1C95524B760592B614E3FF5`. |
| Scoped whitespace | `git diff --check -- services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java` | 0 | No whitespace errors; Git emitted only LF/CRLF advisory. |
| Scoped name | `git diff --name-only -- services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java` | 0 | Target path only for this scoped query. |
| Raw parse location | `rg -n -F 'RawJwtToken raw = RawJwtToken.parse(token);' -- services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java` | 0 | Line 59. |
| Raw header call | `rg -n -F 'requireJoseHeader(raw.header());' -- services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java` | 0 | Line 60. |
| JJWT key verification | `rg -n -F 'verifyWith(publicKeyProvider.getPublicKey())' -- services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java` | 0 | Line 63. |
| Exact raw algorithm | `rg -n -F '"RS256".equals(header.requiredPlainString("alg"))' -- services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java` | 0 | Line 110. |
| Removed mutation guard | `rg -n -F '.sig().clear().add(Jwts.SIG.RS256).and()' -- services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java` | 1 | Expected absence; correction is present. |
| Other source/test hashes | `Get-FileHash -Algorithm SHA256 -LiteralPath services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/InternalJwtValidatorTest.java,services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/DualModeUserContextFilterTest.java,services/shared/shared-security/src/testFixtures/java/ru/rutcampustrack/shared/security/InternalJwtTestFactory.java` | 0 | Values match pre-correction manifest in `evidence.md`. |

No Gradle, Docker, Testcontainers, product runtime, staging, commit, reset or
deploy was run by this leaf.
