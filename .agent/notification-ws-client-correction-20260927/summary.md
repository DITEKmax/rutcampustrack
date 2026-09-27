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
