# Legacy notification WebSocket client correction

## Scope and goal

Correction on top of backend commit `1a66e66f3b4b9b4d0d95db9990fc207091fd28b1` in the assigned worktree. Keep the protected-current `web-panel` and `pwa` clients receiving personal realtime events after backend delivery moved to `/topic/user/{userId}`. Do not change Vue clients, push delivery, auth protocol, broker configuration, or mini-app.

## Context and evidence

- The current authenticated identity already supplies `AuthService.currentUser().id` in web-panel and `useAuth().user.id` in PWA; no user-entered identity is used.
- The legacy consumers were still listening to group destinations for personal attendance/decision events. Their existing teardown paths already disconnect/deactivate on logout, identity change, or unmount.
- Bounded source search found no STOMP client or `/topic/{group,user,headman}` subscription in `frontends/mini-app/src`; it remains unchanged.
- Parent's independent review found the client migration gap limited to these legacy consumers. This correction does not cover the separately owned Vue clients.

## Required behavior and constraints

- Subscribe authenticated sessions to exactly `/topic/user/{positive userId}`.
- Keep existing own-group and headman subscriptions when `groupId` is a positive safe integer; omit those destinations when there is no valid group. Never derive a private destination from group/user input outside the auth session.
- Preserve existing ticket flow and lifecycle cleanup. Do not change backend routing, push audience, or subscribe to arbitrary/wildcard destinations.

## Changes and acceptance

- `frontends/web-panel/src/app/core/notifications/notification-center.service.ts`: private subscription added; own-group/headman routes are conditional on validated group identity; invalid user identity disconnects.
- `frontends/web-panel/src/app/core/notifications/notification-center.service.spec.ts`: existing assertions cover the personal destination and delivery of `attendance.marked` through it while retaining the group/headman expectations.
- `frontends/pwa/src/features/notifications/NotificationCenter.tsx`: private subscription added; existing group/headman subscriptions remain conditional; existing effect teardown still follows session identity/token changes.
- `frontends/pwa/src/features/checkin/useStompCheckin.ts` and `frontends/pwa/src/features/checkin/StompProvider.tsx`: hook receives the auth session's user id, subscribes privately and retains the valid own-group subscription; effect cleanup remains on user/group change and unmount.
- `frontends/pwa/src/features/checkin/__tests__/useStompCheckin.test.ts`: existing destination, private-event callback, and reconnect assertions updated for the two destinations.

Acceptance: both notification centers and check-in hook request the authenticated user's private topic; valid group/headman routes remain; logout/change/unmount cleanup remains tied to the current effect/session. No live browser/broker runtime is claimed.

## Verification

- `git diff --check` — exit 0; no whitespace errors in the tracked source/test diff.
- `web-panel`: `npm test -- src/app/core/notifications/notification-center.service.spec.ts` — exit 1. Initial sandbox run hit esbuild directory access denial; one bounded retry with escalation loaded config but could not find `@analogjs/vite-plugin-angular`.
- `pwa`: `npm test -- src/features/checkin/__tests__/useStompCheckin.test.ts` — exit 1. Initial sandbox run hit esbuild directory access denial; one bounded retry with escalation could not find `@vitejs/plugin-react`.
- `pwa`: `npm run build` — exit 1 before a useful typecheck/build due absent package dependencies, including React, Axios, testing-library, Workbox, and Vite plugins. No dependency install or retry was performed.
- Runtime evidence: source/config inspection only; browser, STOMP broker, and authenticated ticket session were not run in this correction.

## Limits

Checks could not execute the specs because the assigned checkout has no complete legacy frontend dependency installation. This note records dependency-resolution failures rather than attributing them to the changed source. The result preserves delivery wiring for the three named legacy consumers only; it does not claim realtime UI delivery across the separately scoped Vue clients or production WebSocket readiness.

## Lifecycle correction after independent review

The follow-up correction closes stale callbacks from replaced clients without changing routes or auth protocol:

- Web-panel increments a connection generation and clears the current client synchronously before calling `deactivate()`. `onConnect`, STOMP error, and each frame callback verify the captured client/generation and current auth user/group. Each handler closes over its own `Client`; it does not dereference mutable `this.client` to subscribe. Frame processing rechecks identity after event observers and before persisted/history side effects.
- PWA check-in and NotificationCenter each invalidate a client-local `active` flag synchronously in effect cleanup before `deactivate()`. Their callbacks also compare a render-synchronized current-session ref (user/group for check-in; user/group/headman/access-token for NotificationCenter). NotificationCenter checks queued state updaters too, so an update deferred until after a session change is ignored.
- Existing web-panel and check-in specs now replay delayed old `onConnect` and frame callbacks after an account switch and assert they cannot add subscriptions or notify the new account's handler.

Lifecycle-correction verification:

- `git diff --check` — exit 0 for the correction source/test diff.
- No Vitest or build rerun: the previous bounded runs already established that the checkout lacks `@analogjs/vite-plugin-angular` and `@vitejs/plugin-react`; the PWA build also failed on missing runtime/type packages. The parent explicitly limited this correction to source plus scoped diff check and requested no dependency installation or repeat build.
- There is no existing direct NotificationCenterProvider lifecycle spec in the assigned scope; its delayed-callback guards are present in source but not exercised by a dedicated component test here.
- Runtime evidence remains unavailable: no live browser, STOMP broker, or authenticated ticket session was run.
