# JS-STUDENT-01 F3 PWA compact contract — FIX-r2

Date: 2026-09-06
Base revision: `cedce8c60aee04261ca87a18b898b148719bee89`
Implementation branch: `codex/js-student-01-fix-pwa`
Implementation model: `gpt-5.6-luna`, effort `max`
Risk: S2
Ownership: sole writer for `frontends/pwa-vue/**`; evidence files in this task packet only.

## 1. Goal

Make the production PWA app shell install and recover offline on any valid secure
origin, including normal HTTPS hosts whose hostname is not `localhost`. Keep API
and token traffic out of the service-worker cache and keep offline mutations
disabled.

## 2. Context/evidence

- `frontends/pwa-vue/src/main.ts:10` currently requires
  `window.location.hostname === 'localhost'`; a production build on a normal
  HTTPS host never registers its worker, so IndexedDB snapshots cannot be reached
  after an offline shell reload.
- `frontends/pwa-vue/vite.config.ts:16` currently emits the worker registration
  define only when production is paired with `VITE_ENABLE_LOCAL_PWA_SW=true`.
  FIX-r2 explicitly requires the normal production build to work without a
  fixture or local-only opt-in.
- `frontends/pwa-vue/public/sw.js` already limits handling to same-origin GETs,
  bypasses requests whose path contains `/api/`, uses network-first navigation,
  and precaches the Vite-generated shell asset list.
- `frontends/pwa-vue/scripts/verify-offline-shell.mjs` is a fixture-only browser
  check. It currently targets `localhost`; the regression must run on a
  non-`localhost` secure loopback origin so the old hostname gate demonstrably
  fails.
- The old localhost-only statement in `frontends/MOBILE_VUE_FOUNDATION.md` is
  superseded for this narrow F3 defect by the owner/root FIX-r2 request. The
  shared foundation document is outside this worktree's write scope.

## 3. Relevant scope

Owned files: `frontends/pwa-vue/**`, including `vite.config.ts`, `src/main.ts`,
fixture-only verification scripts, and task-owned `.agent/js-student-01-fix-pwa/**`
evidence. The shared `frontends/package-lock.json`, mobile-core, TMA, backend,
contract/generated files, and common docs are outside scope.

## 4. Required behavior

- A production/default Vite build defines worker registration as enabled without
  `VITE_ENABLE_LOCAL_PWA_SW` or fixture mode.
- The browser registers `sw.js` only when the compiled production flag is on,
  `navigator.serviceWorker` exists, and `window.isSecureContext` is true.
  No hostname allowlist is used; HTTPS non-`localhost` origins work, while
  insecure non-loopback HTTP origins do not register.
- Development builds do not register the production worker and remove only this
  app's stale root-scoped worker registration when one was left by a local
  production preview.
- The registration URL and scope continue to derive from `import.meta.env.BASE_URL`.
- Existing `sw.js` API/token exclusions and offline mutation behavior remain
  unchanged.
- The fixture-only browser checker can run on a non-`localhost` loopback host,
  supports a task-owned HTTPS certificate when supplied, otherwise records the
  limited secure-loopback HTTP fallback, and proves one online load followed by
  server stop and offline shell reload.

## 5. Constraints

- Do not weaken browser certificate validation globally; no hosts/CA/firewall,
  public tunnel, deployment, or live production data changes.
- Do not present fixture transport as backend integration; live auth/BFF remains
  an integration-owner check.
- Do not redesign Today UI, alter auth/token persistence, touch shared lockfiles,
  mobile-core/TMA, generated contract, or unrelated offline behavior.
- Do not create child agents or alter other worktrees.

## 6. Existing patterns

- Vite emits `sw-assets.js` during `generateBundle`; `sw.js` imports it and uses
  cache `rct-student-pwa-v3`.
- `App.vue` exposes a fixture-only diagnostics panel and `createFixtureTransport`
  serves explicit fixture states; production never selects that adapter unless
  `VITE_MOBILE_FIXTURE_MODE=true`.
- PWA HTTP calls use `/api`; the worker currently bypasses same-origin API paths
  and non-GET requests.

## 7. Acceptance criteria

1. Default `vite build` output enables the worker; no local flag is required.
2. A non-`localhost` secure origin registers an active controller and caches all
   generated shell assets; `BASE_URL` remains the registration scope.
3. After one online fixture load, stopping the task-owned server and reloading
   offline renders Today from the saved snapshot, shows the offline state, and
   leaves check-in unavailable.
4. API/token requests are not added to the app-shell cache and non-GET requests
   remain bypassed by the worker.
5. Local dev has no worker install path and cleans only its own stale root scope.
6. The regression run fails the frozen implementation on a non-`localhost`
   origin, then passes with FIX-r2.

## 8. Verification

- From `frontends`: `npm ci`; targeted PWA `typecheck`, `lint`, and production
  build; `node --check pwa-vue/public/sw.js`.
- Fixture-only runtime: clean production fixture build, task-owned preview port,
  system Chrome/Playwright, online bootstrap, active controller/cache coverage,
  server stop, offline reload, screenshot, offline badge/Today rows and disabled
  mutation state.
- Run the changed runtime script against the frozen hostname-gated source before
  the code correction to capture its required non-`localhost` regression failure;
  rerun after correction for PASS.
- Record every check with revision, exact command, exit code, environment and
  observable evidence in `checks.json`; runtime observations go in
  `runtime-evidence.json`; stable diff and limitations go in `summary.md`.

## 9. Do not

Do not change shared foundation docs to hide the product delta, add a new service
worker library, cache API responses or tokens, enable SW in Vite dev mode, make
certificate trust broader than the task-owned browser context, or expand into
TMA/backend/animation work.
