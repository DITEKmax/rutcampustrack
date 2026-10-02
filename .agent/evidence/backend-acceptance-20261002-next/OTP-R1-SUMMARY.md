# AUTH OTP R1 — fixture failure; R2 prepared, not run

Source baseline 2ca6fc7e4301b6547a323b58155135d932080003. Exec61442 terminal1, 18:21:16–18:23:09+03. Main/test compilation passed; one selected testcase failed before first HTTP request: BadSqlGrammarException at AuthOtpFlowIT.java453, auth_sessions ORDER BY id. Canonical V24 PK is sid. Neither natural challenge expiry nor consumed challenge reuse was exercised; both NOT_COVERED. No Auth product defect established.

Cleanup: four own container IDs recovered from raw XML startup markers, exact elevated Docker inspect confirms4/4 No such object. Earlier TC-labelled containers and Java snapshot empty; root independently confirmed Dockerempty and terminal61442. No explicit rm/foreign change; AUTH heavy released, Schedule owns current lease. Evidence otp-r1-owned-ids.json/otp-r1-cleanup.json.

Root-authorized correction changes one fixture line to ORDER BY sid. Full new-method SQL audit opened V1 users and password_reset_tokens (id PK) plus V24 auth_sessions (sid PK); all named columns exist in the canonical migrations loaded by AbstractIntegrationTest. Only assigned test differs; added method remains86lines. Product/config/TTL unchanged. git diff --check0, launcher PowerShell syntax errors0; corrected Java not compiled/run.

R2 test SHA e0d9b710c88d2e8532078e2cf109588f040a0cf3ceb58c201f666c45c081425f. Launcher SHA f5fe11f8f5ce5f1af2c6f220c03fb2736a3bbc39a456f1c12d47c6b201fd9e28. After NEW root lease only:

```powershell
pwsh -NoProfile -File C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/evidence/backend-acceptance-20261002-next/run-otp-check.ps1
```

One existing method; fresh PG/Redis/Rabbit/Ryuk, natural120s TTL, no full stand/suite or jar rebuild. R2 outputs otp-r2-check.log/otp-r2-check-exit.json/otp-r2-AuthOtpFlowIT.xml. Raw R1 log/exit/XML unchanged; executed R1 script/diff saved as run-otp-check-r1.ps1/OTP-scoped-r1.diff. Current corrected diff OTP-scoped.diff. No restart or additional run performed.
