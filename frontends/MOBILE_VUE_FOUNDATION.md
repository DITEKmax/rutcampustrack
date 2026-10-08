# Mobile Vue workspace

PWA (`pwa-vue`) and Telegram Mini App (`tma-vue`) use the shared `mobile-core`
features for student, headman, teacher and admin. The source cutover does not
install new frontend bundles on any running stand. Legacy React artifacts remain
retained until the separate retirement gate and rollback receipt are accepted.

| Surface | Local runtime | Public mount | Behavior |
|---|---|---|---|
| PWA Vue | http://localhost:5175/ | /app/ | production service worker; student read cache is session scoped |
| TMA Vue | http://localhost:5176/ | /mini-app/ | online only; Telegram host Back and actions |
| Mobile BFF | internal port9080 | /api/v1/** through API Gateway | role and resource authority remain server side |

Use Node>=24.14 and npm>=11.9. Run from `frontends`:

```sh
npm ci
npm run dev:pwa
# or npm run dev:tma
npm run test:contract
node --test pwa-vue/tests/*.test.mjs
npm run build
```

The workspace `package-lock.json` is the sole lock for these three packages.
Web-panel and landing keep their separate packages. Local development defaults to
root mount; release Dockerfiles set `/app/` and `/mini-app/`. `VITE_PUBLIC_BASE`
starts and ends with `/`. `VITE_API_PROXY_TARGET` points `/api` to the API Gateway
(default `http://localhost:8080`), never directly to an internal service.

Production PWA registers its worker at the selected public base. Development
unregisters only its own exact scope/script registration. Existing `/app/`
updates remove retired React Workbox/headman API caches before claiming clients;
other application caches and authentication/session storage are preserved.

PWA access tokens stay in memory and refresh cookies stay HttpOnly at `/api/auth`.
TMA authenticates signed init data via `POST /api/auth/tma`; its access token is
memory only. The shared features see a typed host adapter, without Telegram APIs
inside domain state. Native synthetic review entries are DEV-only and do not
prove backend authority or real Telegram file saving.

Canonical contracts live in `mobile-core/src/api/generated`. Offline checks:

```sh
npm run generate:types:check
npm run generate:auth-types:check
npm run generate:academic-schedule-types:check
```

BFF/Auth generators consume committed `docs/openapi/mobile-bff.json` and
`auth.json`; Academic/Schedule use their matching committed specs. Only regenerate
the contract that changed, then verify its actual consumers. No generator fetches
an unpinned running backend by default.
