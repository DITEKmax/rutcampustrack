# Vue notification realtime — compact contract

## 1. Goal
Deliver live notification invalidation to an authenticated, online user in both Vue PWA and TMA; an open notification history refreshes without being reopened. Risk S3 because the transport crosses authenticated session and role boundaries.

## 2. Context/evidence
- Frozen base: `f10e3c39df5ab44b4fa47937acc8cd65bfe45dd1` (`main`); implementation branch: `codex/vue-notification-realtime-20260927`.
- Canonical project rules: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md`, SHA-256 `F4986A1834A9175ADBB7DCB3C49483168111A0B60C7EB13ACDA9FBF9D74927F8`. Canonical main `CURRENT.md` is 2026-09-27 and names this base/owner GO. The older worktree `CURRENT.md` says 2026-09-20 and is superseded for this task by the root assignment and canonical main state.
- Existing history API and focus/lifecycle guards: `frontends/mobile-core/src/features/notifications/notifications-client.ts`, `NotificationsScreen.vue`.
- Vue auth generation owners and current profile/group projections: `frontends/pwa-vue/src/App.vue`, `frontends/pwa-vue/src/auth.ts`, `frontends/tma-vue/src/App.vue`, `frontends/tma-vue/src/tma-session.ts`, `frontends/mobile-core/src/features/profile/profile-types.ts`.
- Server transport contract: `services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java`, `TicketHandshakeInterceptor.java`, `SubscriptionAuthInterceptor.java`; auth ticket contract `/api/auth/ws-ticket`.
- Root reproduced a PWA startup defect on the shared f10 runtime: anonymous `GET /api/auth/session` returns 401 and the login screen also shows a technical bootstrap error. This task includes the narrow App fix: an initial unauthenticated current-session 401 shows clean login; known-owner or explicit revocation still clears owner/cache and gives a Russian session-expired message. Network/5xx and login-form errors remain visible.
- Two unrelated dirty files already present at task start, `.agent/evidence/headman-trend-export-20260926/{contract,result}.md`, were preserved through branch preparation and are outside this task.

## 3. Relevant scope
Sole writer: shared `frontends/mobile-core` notification adapter/API/screen/export/test, PWA Vue auth-session wiring, TMA Vue auth-session wiring, and frontend workspace dependency manifest/lock only if the shared STOMP/SockJS transport requires it. Task evidence lives in this directory. No ownership of other worktrees or the foreign evidence files.

## 4. Required behavior
- Start one notification lifecycle only for a valid authenticated user while online; bind every ticket request, socket callback, and history API operation to its captured auth generation.
- Request a fresh short-lived single-use ticket before every connection/reconnection. Keep ticket and bearer only in memory/request headers; never log or persist them.
- Subscribe only to the exact authenticated numeric user destination and the current group destination from the active server-backed role context; subscribe to the headman-only destination only for an active headman role. Client sends no STOMP messages.
- Invalidate the old lifecycle synchronously on logout, auth-generation/user/role/group change, offline transition, or app teardown before initiating asynchronous close. Ignore all late callbacks from it.
- Treat frames as invalidation only. Coalesce and bound history/count refreshes from the canonical REST API; do not synthesize duplicate history records or add notification/banner behavior.
- Reconnect with bounded backoff while current and online; stop timers/subscriptions/socket when the lifecycle is invalidated.
- During initial PWA bootstrap, a plain anonymous current-session 401 must not add a technical error card beside login; known or explicitly revoked sessions retain invalidation/cache cleanup and a Russian session message.

## 5. Constraints
No backend changes, legacy React/PWA edits, push enrollment/native notifications, new visual design, client-authored history, token/ticket storage or logging, production/deploy actions, blanket tests, or child agents. Preserve unrelated work. Do not change code because of a bare warning/error; link it to the request and reproduce first.

## 6. Existing patterns
Use `createGenerationBoundNotificationsApi` and the screen’s existing `loadHistory`/`loadUnreadCount` as the sole history owner. Reuse PWA `usePwaAuth` and TMA `useTmaSession` generation checks. Match server SockJS/STOMP private destinations: `/topic/user/{id}`, `/topic/group/{id}`, and `/topic/group/{id}/headman`; the ticket handshake authorizes session identity and the inbound interceptor enforces exact subscription targets and denies client SEND.

## 7. Acceptance criteria
1. A valid user connects with a fresh ticket and subscribes only to the server-authenticated user/current-group destinations, plus headman topic when authorized.
2. A received event causes one bounded/coalesced canonical history/count refresh; an open PWA and TMA history updates in place.
3. A stale user/role/group generation cannot receive callbacks, refresh history, or expose a previous user’s items; old lifecycle is inactive before close completes.
4. Offline/logout/role switch/teardown dispose the lifecycle; current online sessions reconnect with capped backoff and fresh tickets.
5. Relevant mobile-core/PWA Vue/TMA Vue typechecks, both Vue builds and scoped lint pass; one focused lifecycle test proves fresh-ticket retry/reconnect and stale-event suppression.
6. Initial anonymous PWA current-session 401 reaches clean login; known/explicitly revoked session still clears owner/cache and displays a Russian session message.

## 8. Verification
Run the focused lifecycle test, workspace typechecks for mobile-core/PWA Vue/TMA Vue, both affected production builds, and scoped ESLint for touched source/tests. Record command, exit code, revision, environment, and evidence in `checks.json`. Root owns runtime testing on the shared stand; do not start Docker or another runtime. Runtime evidence for this new Vue WebSocket client is explicitly pending root integration acceptance.

## 9. Do not
Do not redesign notification UX, show a banner/toast, add push/native browser behavior, maintain a second history cache, subscribe using untrusted numeric coercion, send client STOMP frames, alter backend or legacy React, touch the two foreign dirty notes, merge/push/main, or claim live runtime acceptance from typechecks/tests.
