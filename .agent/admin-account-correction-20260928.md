# Admin account correction evidence

Scope: correction to `f19269b0f71dd93c5bf20b13cf3415ec4795e333`, limited to the two Sol findings. Explicit PWA logout clears local authority immediately, checks Auth's HTTP result, persists confirmed/unconfirmed sign-out across reload, and blocks automatic bootstrap/reconnect until a manual login. Admin role selection keeps choices visible with an inline error, refreshes the current snapshot after 409/503, resets stale errors on reopen, and catches callback rejections in both clients.

Acceptance evidence: PWA auth tests cover HTTP 503 and network logout failures, reload lockout, confirmed logout, and 409/503 role retry with refreshed `sessionVersion`. TMA auth tests cover 409/503 role retry with refreshed `sessionVersion`; the TMA component catches rejected selections and preserves retry choices. In App, both initial/retry bootstrap and the online handler pass through the explicit-logout guard.

Checks (all PASS):

- PWA: `vitest run src/auth.test.ts --config vitest.admin-review.config.mjs --configLoader runner` — exit 0; 28 tests.
- TMA: `npm.cmd run test --workspace @rct/tma-vue` — exit 0; 13 Vitest tests and 1 entry test. The sandbox's first stock-config invocation was blocked while esbuild read the worktree parent; the same command then passed with the authorized escalation and stock Vite config.
- Frontend typecheck: `npm.cmd run typecheck --workspaces --if-present` — exit 0 (mobile-core, PWA, TMA).
- Scoped ESLint: changed Vue/TS source and test files with `--max-warnings=0` — exit 0.
- `git diff --check` — exit 0.

Runtime evidence: no runtime check of the newly corrected failure/retry paths was run in this leaf. Root reported the prior real-Auth happy path (STUDENT→ADMIN, reload retains ADMIN, Groups→Profile, no duplicate role); that does not verify these correction paths. Root owns the available runtime stand.

Diff inventory: `frontends/mobile-core/src/features/profile/RoleSwitchScreen.vue`; `frontends/pwa-vue/src/App.vue`, `src/auth.ts`, `src/auth.test.ts`; `frontends/tma-vue/src/App.vue`, `src/tma-auth.test.ts`.

Limits: no backend/API change; no full lint, build, Docker, or runtime stand run here. Historical and concurrent untracked `.agent` items were left untouched and are outside the scoped commit.
