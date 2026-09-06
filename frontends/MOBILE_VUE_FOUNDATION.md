# Mobile Vue foundation — JS-STUDENT-01-r1

This workspace contains the new shared student mobile implementation. The legacy
React applications remain active until an explicit integration cutover.

| Surface | Local runtime | Existing production URL | Foundation behavior |
|---|---:|---|---|
| PWA Vue | http://localhost:5175/ | /app/ | local root only; no service worker is emitted or registered |
| TMA Vue | http://localhost:5176/ | /mini-app/ | local root only; online-only |
| Mobile BFF | http://localhost:9080 | /api/v1/student/** via gateway | contract-export/runtime foundation |

VITE_PUBLIC_BASE may set a non-root preview mount and must begin and end with
/. A build must not claim /app/ while the legacy PWA is still served there.
The integration owner may switch the Vue PWA to /app/ only in the explicit
legacy-retirement revision. That same revision may introduce a service worker
whose manifest id, start URL, registration scope, navigation fallback and
cache cleanup are all bounded to /app/. Until then this workspace owns no
service-worker scope and cannot control or evict the legacy application.

The TMA target URL remains /mini-app/, with no service worker. Its production
mount changes only during an explicit TMA cutover.

Both surfaces send API calls to /api. PWA session bootstrap keeps the existing
memory access token and the HttpOnly refresh cookie at /api/auth; it does not
move or copy that cookie. TMA uses signed init data at POST /api/auth/tma and
memory-only access tokens. Its host adapter owns a narrow typed boundary around
Telegram's injected `window.Telegram.WebApp` API; the deprecated third-party SDK
is not part of this foundation. Product navigation, Today footer destinations and role
switching are implemented only when their actual routes and permissions are
present; the foundation does not expose placeholder success actions.

Run these commands from frontends:

    npm ci
    npm run generate:types
    npm run generate:types:check
    npm run validate:fixtures
    npm run test:contract
    npm run typecheck:foundation
    npm run typecheck
    npm run lint
    npm run build

The committed root package-lock.json is the sole lock for these three new
workspaces. Legacy pwa and mini-app retain their current independent locks
until their explicit retirement. `npm run build` becomes runnable when the FE lane
adds the two owned application entrypoints; the contract foundation deliberately
does not publish placeholder UI. The fixture validator implements only the schema
features used by r1; the integration lane must also validate actual HTTP responses
against this same canonical snapshot and fixture set.
