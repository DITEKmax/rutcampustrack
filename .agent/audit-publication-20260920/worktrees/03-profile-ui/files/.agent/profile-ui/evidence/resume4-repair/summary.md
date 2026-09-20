# Resume 4 Security repair summary

Date: 2026-09-08. Risk S3. Revision `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

The fresh independent review found one MEDIUM defect in the first correction: after `ACCOUNT_INVALIDATED` cleared the existing form, password inputs and reveal buttons remained interactive, so data entered during invalidation survived when the external error was removed. Root reproduced that exact mounted sequence; the earlier mounted PASS is superseded for this boundary.

The bounded repair changed only `frontends/mobile-core/src/features/profile/SecurityScreen.vue`:

- the immediate error-code watcher clears sensitive state when entering or leaving `ACCOUNT_INVALIDATED`;
- all three password inputs are disabled while invalidated;
- all three reveal buttons are disabled while invalidated;
- the external error, independent submit guard, optional callback absence guard, ordinary-error retry behavior, success clear and unmount clear remain in place.

Stable product hash: `5305389822B603A9727E6E9E8F391314F7B7900EF7AEF92B55C22B57BE91771E`, 11269 bytes. Pre-repair hash: `AB039523841BEE93E78ABCA535DEAB7400DFA36003E1102D83F782911E2B659E`, 10943 bytes. All six non-owned profile SFCs still match the prior source manifest.

Current lightweight gates are PASS: pilot strict `vue-tsc`; Security-only ESLint; full profile strict `vue-tsc`; seven-SFC ESLint with zero warnings; profile-state Vitest 20/20; Vite evidence harness build with 44 modules and `write:false`. Exact commands, UTC times, outputs and exit codes are recorded in `checks-output.md` and `checks.json`.

Root still owns the post-repair mounted invalidation/ordinary-error/success matrix, exact port cleanup and the fresh independent recheck. This leaf does not claim backend/Auth, PWA, TMA or production integration.
