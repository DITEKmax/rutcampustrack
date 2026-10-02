# Public reset challenge expiry/reuse — R2 PASS

Exact baseline 2ca6fc7e4301b6547a323b58155135d932080003; test SHA e0d9b710c88d2e8532078e2cf109588f040a0cf3ceb58c201f666c45c081425f. Exec78789 terminal0, BUILD SUCCESSFUL3m28, 18:34:08–18:37:37+03. One selected AuthOtpFlowIT method: XML1 test/0 failures/0 errors, 122.988s. No full stand/suite or product/config/TTL change.

- Own challenge naturally expired under unchanged120s policy; all three Redis challenge keys absent, elapsed121571ms. Public POST /auth/password-reset/verify with original correct proof returned410/extras.code OTP_EXPIRED/no-store, no reset ticket/token/session response. Credential fields, all own session rows and ticket rows equal original in-memory snapshots.
- Fresh valid verify200 consumed its code and issued one expected ticket. Immediate identical verify returned410/OTP_EXPIRED/no-store while owner/user keys remained live. No ticket/token/session response; protected state and complete ticket snapshot unchanged from the consume precondition.

Cleanup18:38:16: all four own IDs recovered from raw XML startup markers and exact Docker inspect confirmed absent. Docker TC-labelled resources empty (exit0), Javaempty, no explicit rm/foreign change. HEAVY released. otp-r2-cleanup.json records both sanitized markers and exact absence.

Root-authorized fixture correction after R1 changed only ORDER BY auth_sessions.id to sid. All new-method SQL names audited against canonical V1/V24 (including password_reset_tokens.id PK). Product behavior unchanged. R1 raw failure preserved and credited to neither acceptance criterion; R2 is the sole execution of these criteria. No reset-ticket expiry/purpose/attempts/concurrent-complete claim or product readiness increment from test count.

Scoped integration inventory under .agent/evidence/backend-acceptance-20261002-next:

- OTP-PREPARE.md, OTP-R1-SUMMARY.md, OTP-R2-SUMMARY.md; OTP-scoped.diff, OTP-scoped-r1.diff.
- run-otp-check.ps1, run-otp-check-r1.ps1 (executed source/pins preserved).
- R1: otp-check.log, otp-check-exit.json, otp-AuthOtpFlowIT.xml, otp-preflight.json, otp-owned-resources.json, otp-r1-owned-ids.json, otp-r1-cleanup.json.
- R2: otp-r2-check.log, otp-r2-check-exit.json, otp-r2-AuthOtpFlowIT.xml, otp-r2-preflight.json, otp-r2-owned-resources.json, otp-r2-cleanup.json.

Only tracked runtime edit: services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/AuthOtpFlowIT.java (one86-line method); git diff --check0 and launcher syntax0 before run. Previous DATE/A and other batch files remain untouched. HANDOFF.md is local recovery state, not required for scoped integration.

Root-authorized scoped local test commit f53a0cd35bd4e392e621d0c2faa39dbb61b03084 contains only AuthOtpFlowIT.java; index was empty and accepted source hash rechecked before staging. Evidence/artifacts excluded. Executed launcher retains exact precommit2ca6 baseline pin; no rerun planned.
