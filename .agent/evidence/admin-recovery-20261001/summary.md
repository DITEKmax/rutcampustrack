# ADMIN recovery link — source-ready, 2026-10-01

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

Source-ready only. Targeted Java integration, independent review, common-stand flow and owner browser password handoff remain pending. Configuration must be supplied to the stand. No integration/push/deploy performed by this author.

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
- `frontends/mobile-core/src/features/admin-users/admin-users-client.ts` — typed authenticated no-store issuance and URL/expiry validation.
- `frontends/mobile-core/src/features/admin-users/AdminUsersScreen.vue` — keyed inline panel.
- `frontends/pwa-vue/src/features/password-recovery/password-recovery-link.ts` — strict ticket/expiry fragment union, clear-before-return.
- `frontends/pwa-vue/src/features/password-recovery/PasswordRecoveryScreen.vue` — direct ticket password entry, admin expiry feedback; existing screen lint corrections without behavior changes.
- `frontends/pwa-vue/src/features/password-recovery/password-recovery-contract.test.ts` — admin fragment/duplicate/malformed/calendar/history-failure coverage.

Created evidence: `.agent/evidence/admin-recovery-20261001/summary.md`. Deleted: none.
Foreign dirty `.agent/transfer-attendance-evidence.md` and existing untracked evidence preserved. Three absent existing dependency package directories copied from main node_modules into ignored worktree node_modules; no manifests/lockfiles edited.

## Checks at source freeze

- PASS: `npm run typecheck --workspace @rct/pwa-vue`, exit0, after reusing existing missing @stomp/stompjs + sockjs-client + @types/sockjs-client dependencies. Initial dependency failures and one corrected optional-fetcher typing failure were not product passes.
- PASS: `node node_modules/eslint/bin/eslint.js mobile-core/src/features/admin-users/AdminUsersScreen.vue mobile-core/src/features/admin-users/RecoveryLinkPanel.vue mobile-core/src/features/admin-users/admin-users-client.ts pwa-vue/src/features/password-recovery/password-recovery-link.ts pwa-vue/src/features/password-recovery/PasswordRecoveryScreen.vue --max-warnings=0`, exit0.
- PASS: `node node_modules/vitest/vitest.mjs run --configLoader runner --config pwa-vue/vite.config.ts pwa-vue/src/features/password-recovery/password-recovery-contract.test.ts mobile-core/src/features/admin-users/admin-users-client.test.ts`, exit0, 2 files / 10 tests. Correct configured rerun followed initial missing PWA compile defines and bundled-loader sandbox path failure; runner uses existing config without edits.
- PASS: `git diff --check -- services frontends`, exit0.
- PENDING: targeted AuthOtpFlowIT (heavy lease requested), independent review, stand and browser. No full suite requested.
