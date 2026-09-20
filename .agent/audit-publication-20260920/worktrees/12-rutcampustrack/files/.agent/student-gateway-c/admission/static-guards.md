# Admission stage static evidence

- Date: 2026-09-10 (Europe/Moscow)
- Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`
- Environment: Windows 11 amd64, PowerShell, shared e31c worktree.

| Check | Command | Exit code | Evidence |
| --- | --- | ---: | --- |
| obsolete issuer/cache API guard | `rg issueFor|invalidateAll|/internal/issue-internal-jwt|estimatedSize|cacheTtl` over Gateway admission source/tests; fail on a match | `0` | No old endpoint, cache API, or legacy issuer method remains. |
| raw NumericDate/downstream guard | `rg getIssuedAt|getExpiration|isIntegralNumber|readOriginalPayload|DownstreamChainFailure` over both admission filters | `0` | Both validators inspect original compact JSON numeric types and use typed date getters; the sentinel path is present. |
| Auth13 destination hashes | `Get-FileHash -Algorithm SHA256 -LiteralPath <13 accepted auth paths>` | `0` | All destination hashes equal the accepted B0 manifest; exact values are in `post-copy-auth13.md`. |
| canonical manifest JSON | `Get-Content -Raw admission/canonical-changed-paths.json | ConvertFrom-Json` | `0` | Manifest parses. |
| scoped whitespace | `git diff --check -- services/api-gateway .agent/student-gateway-c/admission` | `0` | No whitespace errors in the Gateway/evidence scope. |

The focused Gradle selector and runtime remain root-owned gates. No unit or
runtime PASS is claimed here after the raw-time/downstream correction.
