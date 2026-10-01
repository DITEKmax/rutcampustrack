# Б: password/session — existing acceptance confirmed

## Compact contract

- Goal: пользователь меняет пароль, прежние сессии теряют доступ, новый пароль позволяет повторный вход; accepted recovery сохраняет актуальные роли. S3, evidence-only closure.
- Context/evidence: owner/root backend-first packet; root explicitly accepted original runtime results plus exact source continuity instead of replay. Canon [RULES](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md), SHA256 `A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA`. Requested model/effort `gpt-6.1-sol`/`high`; explicit spawn previously supplied that pair, resolved runtime metadata was not echoed by the tool, so no separate runtime confirmation is claimed.
- Relevant scope: sole writer `/root/account_lifecycle_sol61`, assigned `map-usage-delivery-20260922` worktree, branch `codex/lesson-transfer-ui-20260929`; read Auth account/password/session and directly related existing ITs. Merged main `90566541c4cf5eed9f1898e3328fc3bbd39796f8` normally; resulting baseline `5a71629ca93b30162c10dfa95864857f2b7b9bd5`. Only this evidence file is created.
- Required behavior: valid change persists password hash, marks password changed and clears initial password atomically with revoking all prior sessions and recording the security event; wrong current password/invalid session cannot change credentials; recovery is purpose-bound, expiring and one-use; credentials do not rewrite role grants.
- Constraints: preserve foreign edits and existing own runtime probe; no new product behavior, shared contracts or resources; root owns integration/runtime. No concrete uncovered password/session defect was found.
- Existing patterns: `AuthService` verifies current credential and policy, `SessionLifecycleService` delegates the transactional command, `JdbcSessionAuthority` locks authoritative user/session rows and commits credential/session/event changes together.
- Acceptance criteria: original successful change/re-entry and recovery results remain applicable to unchanged critical source; existing negative/atomicity guards remain present. No new first-login semantics are invented: persisted `password_changed=TRUE` and `initial_password=NULL` are the accepted credential transition.
- Verification: read original accepted result; compare critical password/login methods and SQL byte-for-byte after LF normalization; full-file comparisons for policy/controller/HTTP IT and accepted recovery implementation/IT. No Gradle, Docker, browser or new runtime run.
- Do not: redo role revoke probe81841 or consumed admin-recovery scenario, modify Academic lifecycle, expand auth audit, build a new recovery scheme, alter UI/configuration, push/deploy or touch production data.

## Accepted user result

The accepted [Live14 original report](C:/Users/maksd/.codex/worktrees/1267/rutcampustrack/.agent/student-role-orchestrator/live14-root-evidence-2026-09-13.md) records revision `ffde92fdd1a7791ec3fb375102dc878d2fb2adc9`, run `20260913-145432113-0d489149`, runner exit **0**. Its password assertions: wrong-current **400** preserves the session; change **204**; previously admitted bearer **200 → 401** and refresh **401**; old-password login **401**, replacement-password login **200**. Original command: `pwsh -NoLogo -NoProfile -File .agent/student-role-orchestrator/browser-runtime/runner.ps1 -UnionRepo C:/Users/maksd/.codex/worktrees/6a61/rutcampustrack`. This is retained acceptance, not a new run at the current revision.

The accepted [admin-recovery original report](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/evidence/2026-09-27-delivery/admin-recovery-main-28bb3627.md) records main `28bb3627`/runtime `12db180a`, probe **77439 exit0**: issuance by student **403**, by ADMIN **201**; issuance preserves the live account/session; completion **204**, old session **401**, replay **410**, old-password login **401**, new-password login **200**; roles/status/grants unchanged.

Existing [R2 integration evidence](admin-recovery-20261001/summary.md) records handle **37008 exit0**, reviewed source `f34ed35208cc975740c8f44b6b41c648e88f658d` and fixture `5d4e6753`: `AuthOtpFlowIT` 3/0fail/0error/0skip, included Arch3 and naming1 pass. Command: `gradlew.bat :services:auth-service:auth-app:integrationTest --tests '*.AuthOtpFlowIT.adminRecovery*' --tests '*.AuthOtpFlowIT.passwordResetIsPurposeBoundAtomicAndRevokesEverySession' --no-daemon --no-parallel --max-workers=1 --no-problems-report --console=plain --system-prop=org.gradle.java.compile-classpath-packaging=true`. Negative case `adminRecoveryRejectsWrongRoleRevokedSessionAndExpiredTicket` requires expired recovery **410**, unchanged target session **200**, revoked issuer **401**. This packet did not rerun those checks.

## Source continuity and checks

All following current checks use baseline `5a71629ca93b30162c10dfa95864857f2b7b9bd5`. Paths below are relative to `services/auth-service/auth-app/src/`.

| Check | Command/operation | Exit | Evidence |
|---|---|---:|---|
| Original lineage diagnostic | `git merge-base --is-ancestor ffde92 HEAD` | 1 | Historical acceptance revision is not an ancestor; no ancestry claim. Use equality below. |
| Critical method/SQL equality | `git show ffde92:<path>` vs `Get-Content <path>`; LF-normalized complete method/SQL blocks, SHA256 equality asserted | 0 | Five equal regions and hashes below. Initial extraction used an incorrect JDBC path and exited1 before that region; corrected actual `session/jdbc` path, without changing any source. |
| Unchanged full password files | `git diff --exit-code ffde92 HEAD -- <PasswordPolicy.java> <AuthSessionController.java> <SessionAuthFlowIT.java>` | 0 | Empty diff; actual paths `main/java/.../auth/session/PasswordPolicy.java`, `main/java/.../auth/controller/AuthSessionController.java`, `test/java/.../auth/integration/SessionAuthFlowIT.java`. |
| Accepted recovery production equality | `git diff --exit-code f34ed352 HEAD -- <PasswordResetService.java> <AuthService.java> <JdbcSessionAuthority.java>` | 0 | Empty diff; actual main paths `auth/service/PasswordResetService.java`, `auth/service/AuthService.java`, `auth/session/jdbc/JdbcSessionAuthority.java`. |
| Accepted recovery IT equality | `git diff --exit-code 5d4e6753 HEAD -- services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/AuthOtpFlowIT.java` | 0 | Empty diff. |
| Documentation whitespace | `git diff --cached --check -- .agent/evidence/account-password-continuity-20261001.md` | 0 | Evidence-only staged diff. |

Complete block hashes (UTF-8, LF, no trailing newline; regex anchored at four-space method/constant start and closing method/SQL delimiter):

| File / region | Lines | SHA256, identical old/current |
|---|---:|---|
| `main/java/ru/rutcampustrack/auth/service/AuthService.java` / `login` | 32 | `A757719B0B76D38BC3475D3292F55E7CE5D1A3826697571890D408FE4DEFB6D4` |
| same / `changePassword` | 53 | `81C1D6C76D013CB0C60C34322529B9CB683811E22B8CA2B743945013E0F8754D` |
| `main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java` / `changePasswordInTransaction` | 53 | `D9BFFB6084C890514A5CD58F72A0862086ACC49BA2E5DF9F5AFD6251B2F10C7A` |
| same / `UPDATE_PASSWORD_SQL` | 5 | `4DFED3288B398D2E5776F63141F959A81F9830016E54481DF5D476D18EB725BC` |
| same / `REVOKE_PASSWORD_SESSIONS_SQL` | 5 | `5F15DD198C29D1513F26B2A1735AC3EE2EB17360EB225F31D69D6DE84D7EDA50` |

Current existing guards: `SessionAuthFlowIT.java:199` successful HTTP credential/session transition; `JdbcSessionAuthorityIT.java:466` all-session revocation/flags/audit, `:501` and `:522` rollback after injected failure, `:543` expired-current-session rejection; `AuthOtpFlowIT.java:178` purpose-bound atomic reset, `:302` failed-attempt persistence, `:350` no-active-session reset, `:437` wrong-role/revoked/expired admin recovery. These are source locations, not new claimed executions.

Existing real transaction excerpt (`JdbcSessionAuthority.java:599`, unchanged):

```java
if (revoked == 0) {
    throw new IllegalStateException("password change revoked no session");
}
failureInjector.after("password.sessions");
insertEvent(command.passwordChangedEvent());
failureInjector.after("password.event");
return ChangePasswordResult.success(revoked);
```

## Inventory, limits and handoff

Created: this evidence report. Modified/deleted product, tests, harness: **none**. Diff is documentation only; current runtime **N/A**. No processes, containers or heavy lease used. Existing `.agent/evidence/account-lifecycle-20261001/runtime-probe.mjs` preserved; foreign `headman-trend-export-20260926/contract.md` and `result.md` preserved (SHA256 `B883F73A83EA69C10776DB5E5CB9B088C97DCBE1E85E42644702F765920BE656` and `27883582E547397C599CA3B38DF5A279116241C99B801252D08B62E8346D46E0`).

No unresolved server residual was found in this bounded password persistence/session revocation/re-login/recovery contract. Wider existing limitation remains explicitly recorded in [recovery corrections evidence](password-recovery-review-corrections-5b42b4e.md:58): `password.changed` publication is best effort with no durable Auth outbox/retry; actual Telegram delivery remains unconfirmed/deferred. This is not silently counted as complete and is not expanded into this packet. Root updates the Б ledger and integrates this evidence; no new initiative or acceptance replay required.
