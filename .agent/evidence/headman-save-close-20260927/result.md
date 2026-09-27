# Headman schedule save close — result

Contract: [headman-navigation-next-contract.md](../../orchestration-v2/evidence/2026-09-27-delivery/headman-navigation-next-contract.md).
Scope: the successful save branch in `HeadmanScheduleScreen.vue` only.
Reproduction: `createScheduleItem` resolves, then `closeForm()` returns because `formBusy` is still true; the acknowledged draft remains open.
Acceptance: after ACK, clear the pending guard and use the existing guarded close; failures keep the form and idempotency key for retry.
Constraints: retain offline/read-only/API guards, pending-save close guard, and host primary-action ownership.
Diff: one line releases `formBusy` after ACK and before `closeForm()`; no route or API changes.
Checks: PWA Vue `npm run typecheck` — exit 0; scoped ESLint for `HeadmanScheduleScreen.vue` — exit 0; `git diff --check` — exit 0.
Runtime: no browser session run; integrated acceptance remains with root.
Limits: this fixes the inherited form close defect only; it does not claim full role readiness.
