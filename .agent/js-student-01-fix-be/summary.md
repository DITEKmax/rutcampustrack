# JS-STUDENT-01 backend summary — FIX-r2

F4 and F5 are implemented in attendance-service.

- Student gRPC mapping now treats either schedule block flag as an effective
  geo block, so coordinates and unavailable geo commands fail with the typed
  `CHECKIN_NOT_ELIGIBLE` precondition before transaction or write operations.
- The legacy REST check-in service is retired at its first instruction. The
  endpoint returns RFC problem JSON HTTP 410, and its OpenAPI operation is
  deprecated. A real integration test preserves a seeded `HEADMAN/ABSENT` row
  and verifies no related Mongo, Redis or outbox side effect.
- The canonical event schema check remains on the new `StudentCheckinService`
  outbox path.

Checks: targeted unit exit 0; retirement integration exit 0; canonical
`StudentCheckinTransactionIT` 17/17 exit 0 with Docker; diff check and OpenAPI
JSON parse exit 0. Full attendance unit exit 1 on an unrelated existing
`LateCheckinServiceTest` line 302 failure, reproduced in isolation. The three
frontend generated attendance type files remain stale relative to the updated
410 snapshot and are handed to the parent contract/FE owner. Combined runtime
belongs to the parent integration lane.
