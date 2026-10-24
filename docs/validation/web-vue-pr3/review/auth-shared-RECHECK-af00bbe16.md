# Narrow recheck — af00bbe16

Verdict: **PASS**. Both P2 findings from `REVIEW-84bd6f3f7.md` are closed. No new P1/P2 found in the five-file correction delta.

Reviewed frozen `af00bbe16625932715eedda7dfd62e0688f90803` only, integrated by root as `ffe412135`. No full baseline or moving worktree review.

## Causal closure

1. **Obsolete authentication recovery:** `frontends/web-vue/src/auth/session.ts` checks both operation revision and `isCurrent()` immediately after successful `refresh()`, before consulting phase. It repeats the check before error/phase writes, covering rejected refresh as well. An obsolete A continuation cannot change B's phase/error; the unknown login mutation is still reconciled rather than repeated.

2. **History cursor chain:** `frontends/web-vue/src/features/profile/use-profile.ts` routes history reads through one feature-owned promise queue. A More request resolves `historyNextCursor` when its queued operation executes. The tail recovers from a preceding rejection without running operations concurrently; authority is checked before dispatch. History pending count keeps the UI and automatic live refresh blocked until all queued work settles. `ProfilePage.vue` routes retry and More through this owner, and `load()` also uses it. Existing `ProfileState` is unchanged.

## Independent bounded verification

Loaded the actual frozen TypeScript session, Auth adapter, transport, profile composable and ProfileState in memory using the installed TypeScript/Vue packages; used dummy data and deferred fetch responses only. Live socket/assistant/receipt integrations were stubbed for this causal check. Four checks completed successfully:

- Cancelled A with a held recovery refresh that resolves: B remains guest while awaiting its snapshot, retains B token and null error, then becomes ready as B with null error.
- Same sequence with rejected A recovery: identical protected B outcome.
- Hold old More page, queue head refresh, then finish More: after loading the new head and its next page, history contains `[e31,e30,e29,e28,e27,e26,e25]`, cursor is null and pending state is clear.
- Hold the new head, queue More while the old cursor is still visible: More uses the new head's cursor at execution and produces the same complete sequence.

The first history interleaving also asserted that live refresh is blocked while history work is pending. Read the new regression source: it asserts successor state for both refresh outcomes and complete history/cursor state for the original missing-boundary case. `git show --check af00bbe16` passed.

Author reports eight targeted behavior cases, typecheck and lint passing; those suites were not rerun by this reviewer. No browser, full suite, build, Gradle, Docker, installation, CI, server mutation or product source write was performed. This is a narrow source/causal closure gate, not a broader runtime or rollout acceptance. Requested model remains `gpt-6.1-sol/high`; runtime telemetry is unavailable and no telemetry/fallback claim is made.
