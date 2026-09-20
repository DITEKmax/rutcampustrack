# STOPPED shutdown checkpoint

Дата: 2026-09-08. Состояние: **STOPPED по прямой команде владельца**. После
этого снимка feature, tests, build, lint, import checks, agents, reset,
clean/rollback и commit не выполняются.

## Exact state

- CWD: `C:\Users\maksd\.codex\worktrees\e31c\rutcampustrack\.agent\worktrees\requests-notification-authority`
- HEAD: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`
- Branch: detached (`codex/student-role-02-requests-transport` is the
  associated transport ref shown by the worktree state).
- Snapshot command: `git status --short --untracked-files=all`.
- The checkpoint file itself is written after the status snapshot and is an
  additional owned untracked evidence path.

Dirty paths captured before this file was written:

```text
 M docs/openapi/mobile-bff.json
 M event-schemas/excuse.decided.json
 M event-schemas/excuse.requested.json
 M event-schemas/late_checkin.decided.json
 M event-schemas/late_checkin.decision.json
 M frontends/mobile-core/scripts/generate-types.mjs
 M frontends/mobile-core/src/api/generated/mobile-bff.ts
 M frontends/mobile-core/src/api/student-client.ts
 M frontends/mobile-core/src/api/types.ts
 M proto/attendance.proto
 M services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/ExcuseTicketStatus.java
 M services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/ExcuseType.java
 M services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/LateCheckinResolutionReason.java
 M services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/MongoConfig.java
 M services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/RabbitConfig.java
 M services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java
 M services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/exception/GlobalExceptionHandler.java
 M services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseController.java
 M services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseEventPublisher.java
 M services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseService.java
 M services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/entity/ExcuseTicket.java
 M services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java
 M services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinController.java
 M services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinEventPublisher.java
 M services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinService.java
 M services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/entity/LateCheckinRequest.java
 M services/attendance-service/attendance-app/src/main/resources/application.yml
 M services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/EventConsumerTest.java
 M services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseEventPublisherTest.java
 M services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java
 M services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinEventContractTest.java
 M services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/api/StudentApi.java
 M services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java
 M services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/error/MobileProblemHandler.java
 M services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClient.java
 M services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/security/MobileIdentityFilter.java
 M services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentApiController.java
 M services/mobile-bff/mobile-bff-app/src/main/resources/application.yml
 M services/notification-bot/bot/__main__.py
 M services/notification-bot/bot/config.py
 M services/notification-bot/bot/consumers/event_consumer.py
 M services/notification-bot/bot/consumers/event_dispatcher.py
 M services/notification-bot/bot/handlers/excuse.py
 M services/notification-bot/bot/notifications/headman_alerts.py
 M services/notification-bot/bot/notifications/student_alerts.py
 M services/notification-bot/bot/services/idempotency_guard.py
 M services/notification-bot/tests/test_headman_alerts.py
?? .agent/student-requests-authority-c/import-guard.md
?? .agent/student-requests-authority-c/imported-repair-evidence/EventConsumerIT.xml
?? .agent/student-requests-authority-c/imported-repair-evidence/RabbitDecisionRetryIT.xml
?? .agent/student-requests-authority-c/packet.md
?? event-schemas/excuse.decision.json
?? frontends/mobile-core/src/api/student-client.test.ts
?? services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/StudentRequestKind.java
?? services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/StudentRequestOrigin.java
?? services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/StudentRequestStatus.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/exception/InvalidIdempotencyKeyException.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/exception/LegacyEndpointRetiredException.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/exception/PayloadTooLargeException.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceRequestBotGrpcSecretInterceptor.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceRequestBotGrpcServiceImpl.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/StudentRequestGrpcErrors.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/StudentRequestGrpcMapper.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/StudentRequestTransportException.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/AttachmentState.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/RequestAttachmentRepository.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/RequestAttachmentRetentionJob.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/RequestBucket.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentLateCheckinBudgetRepository.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestModels.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestReceiptRepository.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/entity/RequestAttachmentDescriptorDocument.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/entity/RequestAttachmentDocument.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/entity/StudentLateCheckinBudgetDocument.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/entity/StudentLessonSnapshotDocument.java
?? services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/entity/StudentRequestReceiptDocument.java
?? services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/grpc/StudentRequestGrpcErrorsTest.java
?? services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/RabbitDecisionRetryIT.java
?? services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java
?? services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestServiceAuthorizationTest.java
?? services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentRequestApiModels.java
?? services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentRequestFacade.java
?? services/notification-bot/bot/grpc_client/attendance_client.py
?? services/notification-bot/bot/grpc_client/attendance_pb2.py
?? services/notification-bot/bot/grpc_client/attendance_pb2_grpc.py
?? services/notification-bot/tests/test_attendance_request_grpc_client.py
?? services/notification-bot/uv.lock
```

Scoped file SHA-256 values captured at shutdown:

```text
proto/attendance.proto                                      54CC1E7EFFAB9860390555FA2B345798230A884E2F1E1974F0931DAF34D27D2B
.../grpc/AttendanceRequestBotGrpcServiceImpl.java           DDD7394E4133C92D544773F68097E975E2586CC349EC3332C401C574258A3F9E
.../studentrequest/StudentRequestModels.java                142609485B22E52C9A6AA2506D332FD42E8F37CFEC178FCC22910777C9CB6D10
.../studentrequest/StudentRequestService.java                BD054202143BEB11265608080373BD874228C4057DB64ABC8F17A3C141B9DE1F
services/notification-bot/bot/grpc_client/attendance_client.py 8BF8A0BB9BBDD7A4C122F7406592052BC0F6088CB7920E92884256648C04C37A
services/notification-bot/bot/grpc_client/attendance_pb2.py 302C8FDF383776F260201901DA35CB244A975A41E5779C6670A335EC5BAD2681
services/notification-bot/bot/grpc_client/attendance_pb2_grpc.py 554871E7BA61B1D4F18F6BEF0206D24A6ECA0B117EB460778855BD4E5DA707D1
services/notification-bot/bot/notifications/headman_alerts.py B895E15CA2669AC38DD2533F4B0345F68FE0F971F9B284A530EF0071E43A0550
services/notification-bot/tests/test_headman_alerts.py 15F834A69C30BC8C2C6DCD7E3BC232DB50841FAC316E0F207337A87C061A2A17
services/notification-bot/tests/test_attendance_request_grpc_client.py B79A43C0C56631FB439C166BD20F008B9A4FDEC84466306D8E5B456337318573
```

The imported transport/repair hash guard remains in
`import-guard.md`: transport manifest SHA
`4725C54BF3F6300557697C4A756ED1565D5CCD8005F229C6C60AE3250744B2C4`, repair
manifest SHA
`7B64350F73272787BD383E89611B75BE10959B02B4A0342695E45C46769462F3`, accepted
repair destination hashes `1FE05ED7FA57A2DC3CFA1EA77635B414D94B986D7DFFEAFC78EA544D3485FF32`
and `95BFF3CE808623BFCD370499410A33BFC3C38465AF9097FE3016F80D49E51EDA`.
The baseline `services/notification-bot/uv.lock` guard remains the imported
hash `C59E3D361F8F175C3D661018029AEB9DF00761B74D70F79D6D1E3971FCC59082`.

## Ready and incomplete

Ready WIP in this scope:

- additive bot-only `ResolveRequestNotification` proto and generated Python
  stubs;
- canonical persisted Attendance lookup/model/service and gRPC adapter;
- finite resolve timeout in the bot client, preserving attachment fetch
  timeout behavior;
- canonical headman handler, current Academic membership invalidation and
  authority filter;
- attachment fetch correction: backend/error/invalid-size failures escape
  before any Telegram task is enqueued; Resolve and Academic `UNAVAILABLE`
  also escape, while `NOT_FOUND`/`INVALID_ARGUMENT` lookup responses are
  terminal noqueue;
- focused Python test WIP for canonical projection, spoof/mismatch/terminal
  rejection, current headman filtering, RPC timeout/metadata and failure
  propagation.

Incomplete and explicitly open:

- focused Python tests were not made green or fully collected;
- Java `AttendanceRequestBotGrpcServiceTest` and additional notification
  service assertions were not added;
- Gradle compile/unit selectors were not run (no Gradle GO was issued);
- full Python pytest, ruff, runtime/in-process gRPC and independent Sol review
  remain open; no production/runtime messages or containers were used.

## Last actual checks (before STOPPED)

- Import guard: `GUARD_OK`, exit `0`, source/destination hash verification and
  accepted repair evidence completed before the latest handler/test WIP.
- `py_compile` for `attendance_client.py` and `headman_alerts.py`: exit `0`
  before the latest canonical handler rewrite/correction; no post-correction
  compile is claimed.
- `uv run --offline --frozen pytest tests/test_headman_alerts.py
  tests/test_attendance_request_grpc_client.py --override-ini="addopts=" -q`:
  exit `1`; environment error initializing protected global UV cache
  (`C:\Users\maksd\AppData\Local\uv\cache`, access denied).
- Existing source venv command
  `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\notification-bot\.venv\Scripts\python.exe -m pytest tests/test_headman_alerts.py tests/test_attendance_request_grpc_client.py --override-ini="addopts=" -q`:
  exit `1` during collection; generated `attendance_pb2_grpc.py` imports
  top-level `attendance_pb2`, which was unavailable on that runner's import
  path. No focused test PASS is claimed.

## Next specific step after a new owner GO

Run the focused Python tests in the repository's pinned environment with the
generated-stub import path/tooling resolved (without editing generated files),
then add/run the narrow Java service/domain tests and record fresh exit codes.
Recheck the attachment `UNAVAILABLE`/`NOT_FOUND` propagation and zero queued
tasks before any broader gate or review.

## Owned processes / stop results

- No dev server, watcher, Gradle process, container or persistent runtime was
  started by this leaf.
- The only leaf test command completed with exit `1`; there was no active leaf
  test process to stop.
- Observed Java/Node/Python processes were not started by this leaf and were
  left untouched to preserve other work. No process stop, data deletion,
  volume operation or rollback was performed.
