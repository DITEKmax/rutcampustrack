# R3 scoped diff

Base revision: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.

Product/test paths changed:

- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java`
  — notification EXCUSE resolution now reads stored attachments once,
  validates canonical/stored IDs and request/owner binding, rejects mismatch,
  and projects matched stored documents in repository order. The existing
  public `toDetail(ExcuseTicket)` fallback remains separate.
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestServiceAuthorizationTest.java`
  — focused observable coverage for partial, complete/order/current retention,
  zero, malformed, duplicate, misbound and extra inventories.

Evidence-only paths changed are under `.agent/student-requests-authority-c/r3/`.
No API/proto/OpenAPI/generated/config/dependency/lockfile/queue/bot/Fetch or
`student_alerts` path was changed. Foreign dirty rows remain preserved.
