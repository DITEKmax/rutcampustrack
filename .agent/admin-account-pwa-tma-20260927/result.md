# Admin profile PWA/TMA — leaf evidence

- **Risk / goal:** S2. Give ADMIN one profile entry reachable from each existing section; preserve the accepted five-section navigation; switch roles only from Overview using real Auth; reuse the existing profile state, port, screens, theme controller, and stale-generation guards.
- **Authoritative contract:** `.agent/orchestration-v2/evidence/2026-09-27-delivery/admin-account-contract.md` in the main checkout, under RULES SHA256 `F4986A1834A9175ADBB7DCB3C49483168111A0B60C7EB13ACDA9FBF9D74927F8`. Owner correction keeps role switching on ADMIN Overview and removes role/current-role rows from the profile.
- **Criteria addressed:** profile entry from all five Admin areas; profile provides appearance, password, sessions/revoke-all, history, and logout; role switch opens from Overview and calls the existing Auth-backed selection flow; PWA and TMA immediately detach the old owner; TMA clears its in-memory bearer before requesting current-session logout, reports HTTP/network revocation failures, and blocks bootstrap after explicit sign-out.
- **Scope:** shared `AdminProfileOwner`, profile row/logout options, Admin navigation and Overview trigger, PWA/TMA wiring, TMA current-session adapter and Telegram close capability, plus one TMA lifecycle test. No backend/API schema, dependency, generated file, or lockfile changes.

## Evidence and checks

- `npm run typecheck` from `frontends/` — **exit 0** for `mobile-core`, `pwa-vue`, and `tma-vue`.
- Changed-file ESLint — **exit 0**: mobile-core owner/navigation/profile/export; PWA `App.vue`; TMA `App.vue`, `telegram.ts`, `tma-session.ts`, and `tma-auth.test.ts`. `AdminDashboardScreen.vue` also passes with only its pre-existing `vue/singleline-html-element-content-newline` warnings suppressed; its role-trigger changes add no such warning.
- `npm test` from `frontends/tma-vue/` — **exit 0**: 11 Vitest tests passed, then the TMA entry test passed. Added lifecycle evidence covers synchronous local clear, bearer-authenticated `/api/auth/logout`, HTTP 503 and network failure reporting.
- `git diff --check` — **exit 0**.
- A broader `npm run lint` attempt exited 1 on unrelated existing findings in `mobile-core/src/features/headman-group/HeadmanGroupScreen.vue` (`optionLabel` unused) and `pwa-vue/vite.config.ts` (`_`/`bundle` unused), plus repository warnings. This aggregate lint is not reported as a pass; address-specific lint above is the scoped result. Per root direction, it was not rerun.
- Harness overhead: an initial `pnpm --dir <package>` call treated a workspace package as standalone, attempted registry installation, and failed on private `@rct/mobile-core` 404. The accidentally created `mobile-core/pnpm-lock.yaml` was removed. No product/dependency/lockfile change resulted.

## Runtime, diff, and limits

- No runtime/build was run in this leaf. Root owns the shared Admin PWA runtime/build after integration; true Telegram acceptance remains deferred as specified by the contract. No runtime pass is claimed here.
- Product diff: `frontends/mobile-core/src/features/admin-dashboard/AdminDashboardScreen.vue`, `frontends/mobile-core/src/features/admin-dashboard/admin-dashboard-screen.pcss`, `frontends/mobile-core/src/features/admin-semester/AdminRoleNavigation.vue`, `frontends/mobile-core/src/features/admin-semester/admin-role-navigation.pcss`, `frontends/mobile-core/src/features/profile/ProfileScreen.vue`, `frontends/mobile-core/src/index.ts`, `frontends/mobile-core/src/shared/components/AdminProfileOwner.vue`, `frontends/pwa-vue/src/App.vue`, `frontends/tma-vue/src/App.vue`, `frontends/tma-vue/src/telegram.ts`, `frontends/tma-vue/src/tma-auth.test.ts`, and `frontends/tma-vue/src/tma-session.ts`.
- Existing unrelated untracked `.agent` artifacts in this worktree were preserved and excluded from the commit.
