# Requests UI r3 verification summary

Scope is the frozen Requests UI feature: the 12 files under
`frontends/mobile-core/src/features/requests/`, the three files in
`frontends/.requests-harness/`, and this r3 evidence directory. Risk is S2.
Baseline is revision `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.

The implementation keeps server-provided eligibility, options, reasons and
limits authoritative. A pending request with `decision: null` does not render
a fabricated decision from `detail.reason`. Excuse and late-checkin drafts
remain visible after refreshed options invalidate a retained selection, with a
reason-specific hint and a disabled submit path. The harness covers open,
archive, request type, excuse and late-checkin states at 390x844. The archive
fixture now supplies `createdAt` and a valid decision so its date and decision
copy exercise the guarded card rendering. The late-checkin composition has one
lesson label and the guarded lowercase `н` mark.

Acceptance evidence:

- Exactly 12 product files and 3 harness files are present in the declared
  scope. Compared with the r3 baseline hashes, only five owned files changed:
  `ExcuseRequestScreen.vue`, `LateCheckinRequestScreen.vue`,
  `RequestsScreen.test.ts`, `requests.pcss`, and harness `main.ts`.
- The guarded source snapshot comparison returned `SOURCE_HASHES_MATCH`.
- Typecheck, lint, state Vitest, and the 11-test frontends contract suite exit
  0. The targeted RequestsScreen suite exits 1 before collection because the
  plain Vitest import-analysis path does not load plugin-vue; the same boundary
  reproduces with `--configLoader runner` at `ExcuseRequestScreen.vue:84`.
- `generate:types:check` exits 1 on generated mobile BFF drift. This is outside
  the UI contract, so generated/API files remain untouched.
- A programmatic Vite server using the installed Vue plugin served all five
  states on `127.0.0.1:18540`. Actual CUA screenshots and accessibility trees
  were captured at 390x844. The states were visually usable, labels wrapped
  inside the viewport, native controls and visible focus styling were present,
  the dark guarded palette rendered, and the owned reduced-motion and light
  theme branches remain in PCSS. After the run both ports 18540 and 18541 were
  confirmed free.

The full application was not started and no HTTP or backend integration is
claimed. No product, API, generated contract, global navigation, dependency,
or foreign worktree changes were made.

See `checks.json` for commands and exit codes, and `runtime-evidence.json` for
the five state captures, accessibility observations and cleanup evidence.
