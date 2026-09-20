# Terra repair gate

- **Request/reference:** root assigned a fresh Terra high repair after Fresh Sol HIGH findings: bot audit actor uses Telegram id and backend lacks group-scoped authorization.
- **Reproduction:** a resolved academic caller `user_id=42` with Telegram id `123456789` causes the bot to publish `decision_by=123456789`; a delivery for a request in group B can be applied by a headman from group A because `applyDecision` has no `isHeadman(actor, request.groupId)` guard.
- **New evidence:** on frozen base, `services/notification-bot/bot/handlers/late_checkin.py:41-57` discards the resolved response and publishes `callback.from_user.id`; `EventConsumer.java:146-153` admits null actor; `LateCheckinService.java:273-303` writes after a lock without actor/group authorization. `event-schemas/late_checkin.decision.json` makes `decision_by` nullable and describes it as Telegram id.
- **Correction:** return the resolved user from the shared verification helper for the late-checkin handler; publish its positive internal id; require and validate that id in schema/consumer; authorize it with the existing group-scoped academic gRPC call before coordination and writes.
- **Bounded scope:** files and tests named in `contract.md`; no API/proto change.
- **Root decision:** authorized isolated worktree, repair, targeted validation, and commit; independent Sol recheck follows the stable hash.

## R5 snapshot ordering repair

- **Request/reference:** root assigned confirmed Fresh Sol MEDIUM defect in the same attendance lane.
- **Reproduction:** with a PENDING auto-geo request and a future `retryAt`, `StudentAttendanceSnapshotService.eligibility` returns `COOLDOWN` because the generic cooldown branch precedes the specific pending branch.
- **New evidence:** frozen source `StudentAttendanceSnapshotService.java:149-155` contains both conditions; the first condition makes the second unreachable. The canonical today-pending fixture expects distinct `PENDING_CONFIRMATION`.
- **Correction:** evaluate the PENDING + future retry branch first; retain generic cooldown for other request states and existing equality behavior.
- **Bounded scope/root decision:** this source and a focused service test only; no API or enum shape change, authorized by root for the combined repair commit.
