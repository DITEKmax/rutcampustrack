# JS-STUDENT-01 F3 PWA FIX-r2

Date: 2026-09-06. Risk: S2. Writer: `codex/js-student-01-fix-pwa`. Base:
`cedce8c60aee04261ca87a18b898b148719bee89`. Code commit:
`02cabfdfd2c0b81580833b88cef36d71c7706f4e`.

The production PWA now registers its existing app-shell worker on any secure
origin, including HTTPS hosts that are not `localhost`. Vite enables the
registration define for every production build; browser registration still
requires the compiled production flag, the Service Worker API and
`window.isSecureContext`. Development builds do not install the worker and
remove only a stale registration whose scope and `sw.js` script URL match the
current `BASE_URL`.

The fixture verifier now supports non-localhost loopback, task-owned HTTPS
certificates, `BASE_URL` mapping, bounded waits, generated precache coverage and
an explicit offline reload phase. Existing `public/sw.js` behavior is unchanged:
same-origin GET handling, `/api/` bypass, token safety and non-GET mutation
bypass remain in place. `App.vue` diagnostics report the compiled production
flag instead of a fixture-only local opt-in.

## Scope

Changed files are limited to `frontends/pwa-vue/**`. Evidence, checks and
screenshots are limited to `.agent/js-student-01-fix-pwa/**`. Shared lockfiles,
mobile-core, TMA, backend, generated contracts and common documentation remain
outside the lane. The foundation document's old localhost sentence is
superseded by the owner-directed FIX-r2 delta and was not rewritten.

## Acceptance criteria and evidence

- Default and clean fixture production builds pass without
  `VITE_ENABLE_LOCAL_PWA_SW`.
- The frozen hostname-gated source fails on non-localhost `127.0.0.1` while the
  corrected HTTPS runtime passes with an active registration/controller and
  `rct-student-pwa-v3` coverage `7/7`.
- After the task-owned server stops, offline reload renders the saved Today
  fixture with `appChildren=2`, an offline badge and Today rows. The shared
  `TodayScreen` receives `offline=true`, so its check-in action remains
  disabled.
- PWA typecheck, lint, worker/verifier syntax checks and scoped diff checks exit
  `0`. The standard sandbox build exits `1` only because esbuild cannot traverse
  the protected worktree parent; the identical default and fixture builds exit
  `0` in the approved escalated shell.

Detailed exit codes are in [checks.json](checks.json), and runtime observations
are in [runtime-evidence.json](runtime-evidence.json). The task-owned preview is
available at
`http://127.0.0.1:5185/?fixtureToday=default&fixtureDiagnostics=true` while
terminal session `29848` remains running. Screenshots:

- [HTTPS offline shell](runtime/offline-shell-check-https.png)
- [Loopback fallback](runtime/offline-shell-check.png)

## Diff

Against the frozen base, the code commit changes four scoped frontend files:

```
frontends/pwa-vue/scripts/verify-offline-shell.mjs | 92 ++++++++++++++++------
frontends/pwa-vue/src/App.vue                      |  2 +-
frontends/pwa-vue/src/main.ts                     | 21 +++++-
frontends/pwa-vue/vite.config.ts                  |  2 +-
4 files changed, 89 insertions(+), 28 deletions(-)
```

## Limitations

- Runtime evidence is fixture-only. Live auth, BFF, backend and production
  deployment remain integration-owner work.
- HTTPS used a temporary SAN certificate and certificate bypass only inside the
  task-owned Playwright launch. The certificate and private key were removed
  after verification; no trust store, hosts, firewall or public tunnel changed.
- Runtime used root `BASE_URL` `/`; registration and the verifier derive their
  URLs from `import.meta.env.BASE_URL`, but a non-root deployment was not run
  separately.
- The harness captured an existing static-resource 404 console line while no
  task-origin request failed. It was recorded and left unchanged because it is
  outside FIX-r2.
