# Checks

| Check | Command/evidence | Exit | Result |
|---|---|---:|---|
| Auth13 exact hash guard | PowerShell `Get-FileHash -Algorithm SHA256` over the 13 manifest paths plus producer structure assertions | 0 | PASS |
| Diff whitespace | `git diff --check` over the changed tracked producer/shared files | 0 | PASS |
| Source whitespace | owned-source line scan for trailing spaces/tabs | 0 | PASS |
| Structure | one `SessionStatePort.snapshot` call; no old `generateInternalToken(long,...)`; old controller/IT absent; frozen record signature; `@Autowired`; `getExpiration`; success `no-store` | 0 | PASS |
| Bearer disclosure | `rg` scan of producer DTO/service/controller/exception/validator for token logging/string exposure | 0 | PASS (`token-disclosure-scan=none`) |
| Focused Java/Gradle tests | Reserved for root after runtime lease | N/A | OPEN |
| PostgreSQL/Testcontainers IT | Reserved for root after runtime lease | N/A | OPEN |

No mandatory source-stage check failed. N/A rows are intentionally not claimed
as PASS.
