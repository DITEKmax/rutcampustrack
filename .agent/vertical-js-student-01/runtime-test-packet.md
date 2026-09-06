# FE runtime test packet — JS-STUDENT-01-r1

Date: 2026-09-06. Author: Terra high. This packet separates fixture evidence from
the unavailable real-host/offline capability.

## PWA fixture runtime

Build from `frontends`:

```powershell
$env:VITE_ENABLE_LOCAL_PWA_SW='true'
$env:VITE_MOBILE_FIXTURE_MODE='true'
npm run build --workspace @rct/pwa-vue
```

Preview with Vite on `127.0.0.1:5175`. The test-only selectors are compile-gated by
`VITE_MOBILE_FIXTURE_MODE=true` and never request a real geolocation permission:

| URL query | Expected result |
|---|---|
| `fixtureToday=default&fixtureGeo=unavailable` | Three-row default. Keyboard Enter on CTA ACKs the unavailable command and refetches PENDING with a disabled five-minute timer. |
| `fixtureToday=default&fixtureGeo=coordinates` | Three-row default. CTA ACKs coordinates and refetches confirmed Today: raised hero/current row, mint action and present marks. |
| First `fixtureToday=default`, then reload with `fixtureToday=default&fixtureApi=network` | The normal fixture request first writes the user-partitioned IndexedDB snapshot. The reload makes the transport reject while `navigator.onLine` remains true; bootstrap recovers the snapshot through `offlineToday`, shows the offline label and disables mutations. Tokens are not persisted. |
| First `fixtureToday=default`, then reload with `fixtureToday=default&fixtureApi=unauthorized` | The first fixture request writes the snapshot. The reload returns API 401 twice through `StudentApi` retry; accepted session expiry recovers the existing snapshot. HTTP 403 is deliberately excluded from recovery. |
| `fixtureToday=default` | Fixture schedule has 6 September and 7 September; expand “Расписание семестра” and switch the selector to verify both materialized dates. |
| `fixtureRootFont=20` or `fixtureRootFont=24` | Test-only root size for no-clipping/wrapping evidence. Production HTML root size is unchanged. |

## TMA mock-host runtime

Build from `frontends` with `VITE_MOBILE_FIXTURE_MODE=true`, then preview the TMA
build on `127.0.0.1:5176`:

```powershell
$env:VITE_MOBILE_FIXTURE_MODE='true'
npm run build --workspace @rct/tma-vue
npm exec vite -- preview --host 127.0.0.1 --port 5176 --strictPort
```

Expected: `installFixtureTelegramHost` supplies only the narrow injected WebApp
shape, `initData` authentication reaches the fixture transport, and its local
LocationManager coordinates produce the confirmed fixture state after CTA. This is
mock-host evidence only; it does not prove a real Telegram host.

## Real runtime gates

The browser capability supplied to root cannot turn the network offline. The fixture
transport is nevertheless a controller-level recovery proof: it rejects while
`navigator.onLine` stays true after a normal IndexedDB seed. It does not prove the
production service worker. The service worker source has a network-first navigation
response with a cached fallback and an update cache version, but a browser/network-
throttling capable environment must verify an installed SW after an actual offline
transition.

## Standalone browser shell verification

The test-only script `pwa-vue/scripts/verify-offline-shell.mjs` starts its own static
server for `pwa-vue/dist`, opens one online page in system Chrome through the existing
Playwright package, waits for `serviceWorker.ready` and a controller, verifies the
generated v3 cache list, stops the server, then reloads with browser offline. It must
not download a browser. Root ran it after the fixture build with exit 0: active
registration/controller, shell cache 7/7, offline app children 2, offline label and
Today rows present, and no task-origin failed request. `offline-shell-check.png` is a
runtime output, not source to commit.

CUA rendered an empty app on its analogous reload despite its in-app diagnostics
reporting active registration/controller and 7/7 cache coverage. Treat that as a CUA
tool-environment divergence; the isolated standard Chrome run is the accepted browser
runtime evidence.

The real Telegram host is similarly pending an actual injected `window.Telegram.WebApp`
session. Record both as BLOCKED runtime gates instead of translating mock results into
integration claims.
