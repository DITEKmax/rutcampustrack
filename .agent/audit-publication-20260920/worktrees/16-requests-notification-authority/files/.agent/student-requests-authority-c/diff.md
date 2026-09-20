# Scope diff record

Recorded 2026-09-08 at HEAD `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.

The inherited transport snapshot contains 82 product paths and is excluded from
the authored delta count. The accepted requests-isolation repair contains two
additional IT paths and is preserved byte-for-byte; both are excluded from the
authored delta count. The cumulative P1 delta against those immutable baselines
is listed in `repair-manifest.json`.

## Product behavior delta

- `proto/attendance.proto`: private bot Resolve RPC contract is present.
- `AttendanceRequestBotGrpcServiceImpl.java`: exact bot adapter validates lookup
  fields and maps the canonical domain projection.
- `StudentRequestService.java` / `StudentRequestModels.java`: persisted request
  resolution and bot attachment authorization are present in the inherited P1
  implementation.
- `attendance_client.py` and generated `attendance_pb2*.py`: authenticated bot
  lookup with a finite Resolve timeout and package-correct generated imports.
- `headman_alerts.py`: event fields are lookup keys only; canonical response and
  current Academic membership drive content/recipients; invalid canonical excuse
  reason/attachment descriptors now raise before queueing.

## Test delta

- `AttendanceRequestBotGrpcServiceTest.java`: canonical adapter mapping,
  typed validation failures, exact bot-secret Resolve/Fetch boundary and public
  Student boundary.
- `StudentRequestServiceAuthorizationTest.java`: null persisted status fails
  before notification projection.
- `test_headman_alerts.py`: fake queue regression for invalid attachment
  descriptor and canonical authority/failure cases.
- `test_attendance_request_grpc_client.py`: private client metadata/timeout and
  failure cases from the inherited P1 WIP.

## Preservation

No foreign dirty path outside this assigned scope was edited. No source
re-import, reset, generated-stub hand edit, config/lockfile/public schema or
runtime mutation was performed.
