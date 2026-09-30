# ADMIN recovery link — source + focused verification PASS, 2026-10-01

Writer: `/root/admin_recovery_sol61`, assigned GPT-6.1 Sol/high; no child agents.
Worktree: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/admin-group-promotion-20260927`.
Branch: `codex/admin-recovery-link-20261001`; baseline `387998ca39027446d3a39c1c28b0e21ba2955c4b`.
RULES main path: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md`, SHA `01DAD59DF0F7CFADE73D40BB8F8660F5E1D41017BF5BB34150C79BB460676185`.

## Contract

- Goal: JS-ADMIN-20 / JS-TEACHER-15, administrator issues a one-use recovery link; student or teacher without Telegram sets their password.
- Context/evidence: accepted RecoveryLinkPanel and existing atomic PasswordResetService complete flow; canonical bot currently uses `https://ruttrack.site/password-reset`, PWA route is BASE_URL + `/password-reset`.
- Relevant scope: Auth contract/controller/service/no-store filter and existing integration test; mobile-core admin-users feature; PWA password-recovery feature only. Sole writer in assigned worktree.
- Required behavior: `POST /api/auth/admin/users/{userId}/password-reset-link`, no body, live selected ACTIVE ADMIN; 201 `{url, expiresAt, expiresInSeconds}`; hash-only 32-byte opaque ticket; existing TTL; `#resetTicket=...&expiresAt=...`; no change to credentials/roles/status on issuance. Existing `/api/auth/password-reset/complete` consumes and revokes sessions atomically. 401/403 session/role denial, 404 absent target, 503 authority/config unavailable.
- Constraints: fail closed without explicit `auth.password-reset-url` / `AUTH_PASSWORD_RESET_URL`; absolute HTTPS target without userinfo/query/fragment and ending `/password-reset[/]`. HTTP allowed only explicit localhost/127.0.0.1/[::1] dev configuration. Never incoming Host. No plaintext ticket in logging/storage. Existing public Telegram decoy unchanged.
- Existing patterns: AuthService.admit, SessionLifecycleService.issuePasswordResetTicket, JdbcSessionAuthority.completePasswordReset; accepted inline panel and existing mobile semantic tokens; consume fragment clears browser history before exposing proof.
- Acceptance criteria: ADMIN issue/copy/expiry → existing PWA password form → existing atomic API; no Telegram requirement, one-use/expiry denials, all prior sessions revoked, roles/status same. Browser password input requires owner handoff.
- Verification: focused PWA types/lint/contract tests; targeted AuthOtpFlowIT new adminRecovery methods and existing concurrent passwordResetIsPurposeBoundAtomicAndRevokesEverySession; independent review; common stand through root.
- Do not: public decoy relaxation, initial password exposure, Telegram/email delivery, generic auth/App/SW changes, GlobalExceptionHandler/InternalSessionAdmissionIT edits, main/push/deploy.

## User result and limits

The user's ADMIN card has an inline recovery panel, issue button, expiry, copy and hide actions. Panel clears credentials on expiry, target switch and unmount; late API results cannot reappear. Recipient consumes strict admin fragment, clears browser history through existing App handling and enters the existing new-password form.

Reviewed source `f34ed35208cc975740c8f44b6b41c648e88f658d` PASS (root confirmed independent review, digest `51996804D8C468C418680A26221F86F36826436BC52849B361B7EAF4BE1E831E`). Targeted Java integration PASS after fixture corrections; product bytes unchanged. Root reviewed the two-file fixture correction diff. Common-stand flow, owner browser password handoff and integration remain pending. Configuration must be supplied to the stand; root configured `https://127.0.0.1:18514/password-reset` for controlled acceptance. No integration/push/deploy performed by this author.

## Exact inventory

Created:
- `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/api/AdminPasswordResetApi.java` — protected administrator REST contract.
- `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AdminPasswordResetLinkResponse.java` — URL/expiry DTO with redacted toString.
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AdminPasswordResetController.java` — principal extraction and no-store response.
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/AdminPasswordResetService.java` — fresh ADMIN admission, explicit URL validation, hash-only ticket issuance.
- `frontends/mobile-core/src/features/admin-users/RecoveryLinkPanel.vue` — issue/expiry/copy/hide lifecycle.
- `frontends/mobile-core/src/features/admin-users/recovery-link-panel.pcss` — existing semantic mobile tokens.

Modified:
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/PasswordResetNoStoreFilter.java` — admin recovery route no-store including errors.
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/AuthOtpFlowIT.java` — two focused security/atomic integration scenarios and explicit test URL.
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/arch/AuthApiContractTest.java` — existing contract whitelist extended with four actual contract interfaces: PasswordResetApi, InternalWsSessionAdmissionApi, InternalSemesterDeletionConfirmationApi and AdminPasswordResetApi. Rule remains active.
- `frontends/mobile-core/src/features/admin-users/admin-users-client.ts` — typed authenticated no-store issuance and URL/expiry validation.
- `frontends/mobile-core/src/features/admin-users/AdminUsersScreen.vue` — keyed inline panel.
- `frontends/pwa-vue/src/features/password-recovery/password-recovery-link.ts` — strict ticket/expiry fragment union, clear-before-return.
- `frontends/pwa-vue/src/features/password-recovery/PasswordRecoveryScreen.vue` — direct ticket password entry, admin expiry feedback; existing screen lint corrections without behavior changes.
- `frontends/pwa-vue/src/features/password-recovery/password-recovery-contract.test.ts` — admin fragment/duplicate/malformed/calendar/history-failure coverage.

Created evidence: `.agent/evidence/admin-recovery-20261001/summary.md`, unique R1/R2 logs, R1/R2 compact XML summaries and exact R1 failure XML copies. Logs/XML remain local evidence, not part of the integration inventory. Deleted: none. Total final inventory: 6 created + 8 modified product/test files, plus own summary.
Foreign dirty `.agent/transfer-attendance-evidence.md` and existing untracked evidence preserved. Three absent existing dependency package directories copied from main node_modules into ignored worktree node_modules; no manifests/lockfiles edited.

## Checks at source freeze

- PASS: `npm run typecheck --workspace @rct/pwa-vue`, exit0, after reusing existing missing @stomp/stompjs + sockjs-client + @types/sockjs-client dependencies. Initial dependency failures and one corrected optional-fetcher typing failure were not product passes.
- PASS: `node node_modules/eslint/bin/eslint.js mobile-core/src/features/admin-users/AdminUsersScreen.vue mobile-core/src/features/admin-users/RecoveryLinkPanel.vue mobile-core/src/features/admin-users/admin-users-client.ts pwa-vue/src/features/password-recovery/password-recovery-link.ts pwa-vue/src/features/password-recovery/PasswordRecoveryScreen.vue --max-warnings=0`, exit0.
- PASS: `node node_modules/vitest/vitest.mjs run --configLoader runner --config pwa-vue/vite.config.ts pwa-vue/src/features/password-recovery/password-recovery-contract.test.ts mobile-core/src/features/admin-users/admin-users-client.test.ts`, exit0, 2 files / 10 tests. Correct configured rerun followed initial missing PWA compile defines and bundled-loader sandbox path failure; runner uses existing config without edits.
- PASS: `git diff --check -- services frontends`, exit0.
- PASS: independent product review, root accepted stable source digest above.
- R1 FAIL: handle66518, exit1, selected Auth IT 3 failures; forced Arch 1 failure, no skipped cases. Causes: new ADMIN fixture used bootstrap token without existing explicit role selection; existing cache assertions rejected repeated safe no-store directive; hardcoded Arch whitelist lacked three existing APIs plus the new API. No product defect. Evidence `auth-recovery-it-20261001-0228.log`, `auth-recovery-it-r1-results.json`, `r1-TEST-ru.rutcampustrack.auth.integration.AuthOtpFlowIT.xml`, `r1-TEST-ru.rutcampustrack.auth.arch.AuthApiContractTest.xml`.
- Minimal correction: test helper selects ADMIN through existing `PUT /auth/session/active-role`; cache assertions split comma-separated directives and require only no-store (duplicates accepted, caching directives rejected); Arch whitelist adds the exact four interfaces without disabling rules. Root reviewed this test-only diff; product source unchanged.
- R2 PASS: handle37008, exit0, 64s. Command JDK `C:/Users/maksd/.jdks/ms-21.0.10`, `gradlew.bat :services:auth-service:auth-app:integrationTest --tests '*.AuthOtpFlowIT.adminRecovery*' --tests '*.AuthOtpFlowIT.passwordResetIsPurposeBoundAtomicAndRevokesEverySession' --no-daemon --no-parallel --max-workers=1 --no-problems-report --console=plain --system-prop=org.gradle.java.compile-classpath-packaging=true`. Fresh XML: AuthOtpFlowIT 3/0fail/0error/0skip timestamp2026-09-30T23:20:33UTC; automatically included Arch3/0fail/0skip and naming1/0fail/0skip. Evidence `auth-recovery-it-20261001-r2.log`, `auth-recovery-it-r2-results.json`. Three actual IT cases: adminRecoveryWithoutTelegramRestoresStudentAndTeacher, adminRecoveryRejectsWrongRoleRevokedSessionAndExpiredTicket, passwordResetIsPurposeBoundAtomicAndRevokesEverySession.
- Heavy lease returned to root immediately after R2 terminal. No additional full suite or repeated frontend PASS. PENDING: common stand, owner browser handoff and integration.
