# PWA password recovery UI delivery (S3)

## Fresh correction report — 2026-09-30

This report supersedes the earlier implementation status below for the three bounded Sol findings. The recovery UI correction is implemented in the assigned managed worktree, based on HEAD `2cf14f57d9adb6552747858b6100493614da00db`. It is ready for the independent bounded recheck and root integration; it does not claim completed backend or Telegram acceptance.

The user can now leave or complete a direct `/password-reset` flow and land at the configured PWA login path. App setup consumes and removes a bot proof fragment synchronously before mount hooks can issue app requests. The recovery screen receives that proof once, then clears the parent reference. Its API client receives App's configured `requestFetcher`, preserving version headers and the 426 update gate while keeping `credentials: omit`, `cache: no-store`, and no Authorization header. Return buttons stay enabled during requests; cancel invalidates the operation, aborts its signal, clears local values, and existing current-operation guards discard late results. Ordinary login behavior is unchanged.

Acceptance criteria for this correction: (1) direct-entry fragment is removed before bootstrap/version requests and proof stays in memory; (2) direct-route cancel and completion replace history with the configured base/login path; (3) all recovery calls use the configured PWA fetcher and preserve anonymous no-store transport; (4) exit remains possible while busy and stale results do not update the closed flow. The frozen contract remains at `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/evidence/2026-09-27-delivery/password-recovery-ui-contract.md`.

Changed files in this correction are `frontends/pwa-vue/src/App.vue`, `frontends/pwa-vue/src/features/password-recovery/PasswordRecoveryScreen.vue`, `frontends/pwa-vue/src/features/password-recovery/password-recovery-link.ts`, and the existing focused contract test. Check commands, exit codes, environment, dependency junction cleanup, and prior author evidence are preserved in `checks.json`.

The focused contract suite passes 7/7; Vue/TypeScript typecheck and `git diff --check` pass. Runtime was not repeated: the frozen delivery packet prohibits another Vite dev smoke after the prior smoke stopped before Vue mount with the existing `sockjs-client` `global is not defined` error. The combined production build and real API acceptance remain for root integration. The real Telegram bot is still owner-deferred, and no delivery claim is made.

## External-invalidation follow-up — 2026-09-30

Bounded recheck found that logout invalidation from another tab cleared the auth view but left `/password-reset` in browser history. The branch in `App.vue` confirmed the reproduction path: external invalidation on the direct reset URL changed `authView` to `login` without replacing the pathname, so a reload selected recovery again. It now invokes the same direct-route cleanup before switching views; on ordinary paths that helper is a no-op. Clearing the parent proof lets the screen unmount, whose existing cleanup aborts pending work and retains stale-result guards.

Acceptance for this follow-up is limited to this transition: direct-route external invalidation replaces the path and clears proof before showing login; ordinary invalidation behavior remains unchanged; pending recovery work is still aborted on unmount. The focused 7-case suite, typecheck, and diff check were repeated after this source change. No two-tab runtime was run here; root will cover that branch in shared acceptance. Exact commands and exit codes are in `checks.json` under `externalInvalidationFollowup`.

## Goal
Deliver the frozen password recovery contract in the PWA: a user can start recovery from login, request a Telegram code with a login, verify it and set a new password; a direct Telegram reset link opens the same flow. Verification issues only a short-lived reset ticket, never a login session.

## Context/evidence
- Contract: `.agent/orchestration-v2/evidence/2026-09-27-delivery/password-recovery-ui-contract.md`.
- Worktree: `C:/Users/maksd/.codex/worktrees/password-recovery-ui/rutcampustrack`; initial HEAD: `a227d7a6c40c41471cf1ec59263518c37ebb4282`.
- The frozen API is `POST /api/auth/password-reset/request {login}` → 202 `{challengeId,ttlSeconds}`, `POST .../verify {challengeId,code}` → reset ticket, expiry and attempt count, and `POST .../complete {resetTicket,newPassword}` → 204.
- Backend owner confirmed the API shape on 2026-09-30. Real Telegram delivery remains owner-deferred; this UI delivery does not claim it was accepted end to end.

## Relevant scope
Only the PWA login, app auth-view routing and isolated `features/password-recovery` UI/client were changed. No backend, generated types, lockfiles, global tokens, unrelated role routing or shared/main checkout files were changed. The worktree's existing baseline and other checkout changes were preserved.

## Required behavior
- Manual recovery posts only `{login}`, presents the server's TTL, accepts the code, then accepts a matching new-password pair.
- Direct `/password-reset#challengeId=…&code=…` entry consumes the fragment on recovery-screen mount and calls `history.replaceState` before passing the proof to the API. Duplicate or unrelated fragment parameters are rejected after clearing the fragment.
- The recovery client uses the three frozen endpoints, sends credentials in JSON bodies with `credentials: omit` and `cache: no-store`, and never adds the current Authorization header.
- TTLs and attempt counts are server-derived. `OTP_INVALID`, `OTP_EXPIRED`, `OTP_RATE_LIMITED`, `RESET_TICKET_INVALID` and password policy errors return actionable states. A rate-limited bot-link verification returns to the login-entry step while keeping the server-provided retry delay visible.
- Closing the flow aborts and invalidates pending work. Completion clears recovery values and returns to normal login with a success notice; it does not bootstrap or issue a session.
- Recovery proof and passwords remain in component memory only. No recovery value is written to browser storage, a query string, or a log.

## Constraints
The implementation reuses the existing Vue login card and PCSS. UI text is Russian and addresses the user informally. Bot username, email flow, global password policy and Figma conformity were not invented. The direct reset route suppresses ordinary session bootstrap while recovery is open.

## Existing patterns
The app's existing `LoginScreen.vue`, `App.vue` auth-view state and mobile login styles are reused. Recovery transport is isolated from `auth-client.ts`; it deliberately omits session credentials. API error parsing is local to this feature and only exposes a sanitized bounded password-policy detail.

## Acceptance criteria
- Both manual and bot-link entry reach the new-password form only after successful ticket verification.
- Invalid/expired/exhausted proof, server rate limiting and expired tickets expose a path to request fresh proof; successful completion returns to ordinary login.
- No recovery credential persists, enters URL query/log output, or creates a full session.
- The existing login remains reachable and controls retain keyboard focus visibility and mobile-safe scrolling/touch size.

## Verification
Checks and exact commands/results are in `checks.json`. The focused Vitest file has five cases covering these five invariants:
1. Exact reset-entry path matching supports both `/password-reset` and a configured `/pwa/password-reset`, while rejecting a nested non-entry path.
2. Bot proof is cleared before the fragment parser returns it; duplicate and unrelated parameters are rejected after clearing.
3. Invalidating the operation gate aborts a pending request and makes its result stale.
4. `OTP_INVALID` preserves the server's `attemptsRemaining` value.
5. All three API calls use the frozen endpoint/body pairs, `credentials: omit`, `cache: no-store`, and no Authorization header; success responses preserve server TTL/ticket/attempt data and enforce 202/204 expectations.

One local Vite browser smoke was attempted before integration. It stopped before Vue mounted with `ReferenceError: global is not defined` in the existing `sockjs-client` bundle; no recovery API request was made. Root identified this as the shared PWA SockJS baseline and asked not to change it or repeat the dev smoke. Combined production build and backend runtime acceptance are intentionally deferred to root's integrated pass.

## Do not
Do not treat the local SockJS startup error as a recovery regression, re-run the dev smoke, deploy, configure bot secrets, or claim real Telegram delivery. Do not expand into backend/generated/shared UI ownership or change unrelated source-history/archive files.

## Delivery notes and limitations
- The API contract provides no anonymous password-policy endpoint or documented client-side rule set. The UI lets the server validate on completion, displays a bounded sanitized server detail when available, and otherwise shows a generic policy error. If users must see password requirements before submission, the contract needs an agreed public policy hint/source; no rule was guessed here.
- The combined production build and real backend/bot acceptance remain pending integration. No production acceptance is claimed.
- Runtime smoke limitation: existing `sockjs-client` `global` reference error before mount; no recovery request reached the backend. The server smoke was not repeated after root's instruction.
