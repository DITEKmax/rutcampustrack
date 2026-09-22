# Vue release packaging inventory

Date: 2026-09-22
Status: source candidate and bounded image/runtime evidence are ready for
scoped review. The real production gateway and deployment were unavailable, so
this does not claim live gateway acceptance, deploy, push, or registry
publication.

## Scope and criteria

The candidate packages the current Vue PWA and Telegram Mini App workspace into
the existing production image names and gateway routes. It uses the shared
`frontends` workspace so `mobile-core` resolves during the build, publishes the
PWA under `/app/` and TMA under `/mini-app/`, preserves SPA fallback, leaves
hashed assets cacheable, and serves shell/update markers with `no-store`.
Production image arguments explicitly disable fixture mode. The PWA keeps the
existing forced-update contract: a version marker, `X-PWA-Version` on API
requests, `426` hard-block handling, and an update gate that waits for the new
service worker to activate before reloading.

## Contract evidence

- `nginx/conf.d/default.conf:179-192` strips `/app/` and `/mini-app/` before
  proxying to the respective container. Therefore the PWA build uses
  `VITE_PUBLIC_BASE=/app/` and the TMA build uses `VITE_PUBLIC_BASE=/mini-app/`;
  browser-visible asset URLs retain the external prefix while each container
  serves its artifact from `/`.
- `frontends/package.json` requires Node `>=24.14.0` and npm `>=11.9.0`.
  Both Dockerfiles use `node:24.14-alpine`, run `npm ci`, and copy the existing
  workspace lockfile without changing it.
- `docs/operations/deploy/pwa-forced-updates.md` and the gateway policy require
  a release marker, `X-PWA-Version`, no-store policy/shell/worker responses,
  and a hard block when the gateway returns `426`. The Vue PWA emits
  `version.json`, excludes it from precache, bypasses it in the service worker,
  and checks it with `cache: no-store` and a cache-busting query.
- `frontends/pwa-vue/src/App.vue` routes production API clients through the
  versioned fetcher while preserving fixture transport in fixture mode. The
  fetcher retains headers already present on a `Request` and merges explicit
  init headers before adding `X-PWA-Version`.
- `PwaUpdateGate.vue` makes the gate modal and keyboard-contained: the app root
  becomes inert, focus moves to the required update button, Tab remains inside
  the gate, and no postpone action is exposed. `SKIP_WAITING` is sent to the
  waiting worker and reload occurs only after `controllerchange`/activation.
- `frontends/pwa-vue/public/sw.js` keeps a development release marker. The
  production Vite plugin replaces that exact marker with a JSON-encoded build
  version and includes the same version in the cache name, so a new worker does
  not overwrite an active release cache.

## Exact candidate inventory

Modified:

- `.github/workflows/deploy.yml` — Vue PWA/TMA build contexts, Dockerfiles,
  `/app/` and `/mini-app/` bases, release/version and fixture arguments.
- `docker-compose.prod.yml` — matching local production build contexts,
  Dockerfiles, bases, version arguments, and fixture-off arguments.
- `frontends/pwa-vue/public/sw.js` — waiting-worker activation message,
  network-only `version.json` handling, and the production release marker.
- `frontends/pwa-vue/src/App.vue` — production versioned fetcher, client wiring,
  and hard update gate.
- `frontends/pwa-vue/vite.config.ts` — version policy asset, build constants,
  deterministic production `sw.js` emission, and app-shell exclusions for
  `version.json`/`sw.js`.
- `frontends/pwa-vue/Dockerfile` and `frontends/tma-vue/Dockerfile` — copy the
  workspace `tsconfig.foundation.json`, `postcss.config.mjs`, and the two
  existing generated academic type files required by clean Vue builds.

Added:

- `.agent/vue-release-packaging/release-packaging-inventory.md` — this scope,
  evidence, checks, and runtime limits.
- `frontends/.dockerignore` — excludes local dependencies, build output, logs,
  and environment/key material from image contexts.
- `frontends/pwa-vue/Dockerfile` — workspace build and nginx runtime image.
- `frontends/pwa-vue/nginx.conf` — SPA fallback, immutable hashed assets, and
  no-store shell/service-worker/version responses.
- `frontends/pwa-vue/src/PwaUpdateGate.vue` — forced update UI and activation
  flow.
- `frontends/pwa-vue/src/pwa-update-gate.pcss` — token-based gate styling.
- `frontends/pwa-vue/src/pwa-version.ts` — version policy and API fetcher.
- `frontends/tma-vue/Dockerfile` — workspace build and nginx runtime image.
- `frontends/tma-vue/nginx.conf` — SPA fallback, immutable hashed assets, and
  no-store shell response.

Deleted: none.

## Checks

- `npm run typecheck --workspace @rct/pwa-vue` from `frontends` — exit 0.
- `npm run typecheck --workspace @rct/tma-vue` from `frontends` — exit 0.
- `npm run lint --workspace @rct/pwa-vue` from `frontends` — exit 0 after
  formatting the gate template; zero warnings and zero errors.
- `git diff --check -- .github/workflows/deploy.yml docker-compose.prod.yml
  frontends/pwa-vue/public/sw.js frontends/pwa-vue/src/App.vue
  frontends/pwa-vue/vite.config.ts` — exit 0; Git only reported its normal
  working-copy line-ending notices.

- Corrected PWA image build handle `26482`
  (`rct-vue-release-20260922-pwa`, version `1-new-vue-release-20260922`,
  `/app/`) — exit 0. The clean image ran the workspace build and emitted the
  PWA shell, worker, version marker, and hashed assets.
- Corrected TMA image build handle `35496`
  (`rct-vue-release-20260922-tma`, `/mini-app/`) — exit 0. The clean image
  emitted the TMA shell and prefixed asset references.
- Authorized B image build handle `41937`
  (`rct-vue-release-20260922-pwa-b`, version `2-new-vue-release-20260922`,
  `/app/`) — exit 0. Direct HTTP inspection returned the B version JSON and
  showed `sw.js` containing the JSON-encoded release version and versioned
  cache name.

No new basic wiring tests were added. No dependency or lockfile changes were
made. The first PWA attempt (handle not retained) failed on the omitted root
TypeScript/PostCSS configs. The subsequent PWA attempt, handle `33124`, then
exposed the existing type-only imports from `mobile-core` to the two generated
academic type files; both were copied into the bounded image contexts. These
failures are packaging diagnostics only. No backend rebuild or full test
campaign was run.

## Runtime evidence and limits

Runtime evidence from owned containers and a bounded prefix proxy:

- PWA container `d82655732b46...` on port `18080` served `/`, a deep route,
  `version.json`, and `sw.js` with 200; shell/version/worker were no-store and
  hashed JS/CSS were immutable. TMA container `7f528290c532...` on port
  `18081` served `/`, a deep route, and `/mini-app/` asset references with the
  same cache split.
- Prefix proxy `aac0e7e1ae35...` on port `18082` passed `/app/` and
  `/mini-app/` deep routes and assets using the existing strip-prefix shape.
  The real gateway was not running, so this is bounded rewrite evidence only.
- Synthetic 426 probe container `dfd22c876e79...` proved the modal is hard,
  makes the application inert, moves focus to the update button, traps Tab,
  and clears after one update click. It is recorded separately from the
  release A-to-B transition.
- Same-origin A/B proxy `a8d9ee2b0516...` on port `18085` served A
  (`1-new-vue-release-20260922`, `index-BaGeJ0a-.js`). After switching the
  proxy to B (`2-new-vue-release-20260922`, `index-SaI8xb5Y.js`), the old A
  page received the local policy `426`, displayed the update gate, and after
  exactly one click fetched B `sw.js` (1790 bytes), B bundle, and performed one
  post-click shell reload. The final DOM had no update gate. CUA's page
  evaluation sandbox exposed no `navigator`, so controller state could not be
  read directly; worker/bundle/version requests and final DOM are the available
  evidence.
- All six owned containers and four temporary proxy files were removed after
  evidence capture; `docker ps` and the matching `docker ps -a` filter were
  empty. Images are retained under their unique tags; no global prune was
  performed.

The existing foreign worktree/docs/config changes remain outside this inventory
and are intentionally unstaged.
