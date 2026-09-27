# Result — Vue notification realtime lifecycle

## Outcome

Both Vue apps now bind a private STOMP/SockJS notification lifecycle to the current authenticated user, role, group, and auth generation. A received frame only invalidates the existing server-backed history; while the history screen is open it coalesces one refresh of canonical history and unread count. Every successful connect also queues this refresh, so a reconnect covers events missed during the disconnect even when the broker does not replay them. Reconnects fetch a fresh ticket and use capped exponential backoff, including failures while obtaining a ticket. Old lifecycles are synchronously disabled before transport teardown and late callbacks are ignored.

The PWA bootstrap now treats an initial anonymous `current-session` 401 as a normal login state without a technical error card. Existing known-owner and explicit-revocation handling still clears owner/cache data and shows a Russian session message. Network/5xx errors and login-form errors are not suppressed. This narrow behavior change follows the root-reported reproduction recorded in `contract.md`.

## Changed files

- `.agent/evidence/vue-notification-realtime-20260927/contract.md`
- `.agent/evidence/vue-notification-realtime-20260927/checks.json`
- `.agent/evidence/vue-notification-realtime-20260927/result.md`
- `frontends/mobile-core/package.json`
- `frontends/mobile-core/src/features/notifications/notifications-realtime.ts` — generation-bound ticket, STOMP/SockJS subscriptions, refresh/reconnect lifecycle
- `frontends/mobile-core/src/features/notifications/notifications-realtime.test.ts` — focused reconnect/stale-event lifecycle test
- `frontends/mobile-core/src/features/notifications/NotificationsScreen.vue` — bounded/coalesced REST history and unread-count refresh
- `frontends/mobile-core/src/index.ts`
- `frontends/package-lock.json`
- `frontends/pwa-vue/src/App.vue` — PWA lifecycle wiring and anonymous initial 401 handling
- `frontends/tma-vue/src/App.vue` — TMA lifecycle wiring

The package lock adds `@stomp/stompjs` 7.3.0, `sockjs-client` 1.6.1, and `@types/sockjs-client` 1.5.4. No backend or legacy frontend file changed.

## Evidence and checks

`checks.json` contains commands and exit codes. Mobile-core, PWA, and TMA typechecks passed; scoped ESLint over every changed source/test file passed; the focused lifecycle test passed; both PWA/TMA production builds passed; `git diff --check` passed. After review, the focused test was extended for reconnect-without-replay and rerun with mobile-core typecheck and scoped lint; all three exited 0. The PWA package-wide lint command separately reports two unused arguments in untouched `pwa-vue/vite.config.ts:28`; the changed-file lint is clean. A package-wide mobile-core lint attempt was made before correcting a test mock parameter; it is recorded as a diagnostic, not a passing acceptance check.

Build output included Vite's warning that the main JavaScript chunks exceed 500 kB. No bundle-size change was made from the warning alone. Root owns live verification against the shared runtime; this leaf did not start Docker or claim that the new WebSocket client has runtime acceptance.

Root also reported a shared-runtime reproduction of the PWA's anonymous session-request 401 and login error card. That evidence motivated the limited App fix; the separate live WebSocket behavior remains pending root integration verification.

## Preserved state and limitations

The two pre-existing dirty files under `.agent/evidence/headman-trend-export-20260926/` remain untouched and are excluded from the scoped commit. The original main checkout's scoped product status is empty. During initial setup, two package-manifest edits were made in main by mistake; they were restored to their clean original state. Per root's instruction, the ignored `frontends/node_modules` in main was left as-is.

This change does not add banners, toast/native notifications, push enrollment, a client history cache, or STOMP client sends. Runtime server/ticket interaction remains to be confirmed on root's shared stand.
