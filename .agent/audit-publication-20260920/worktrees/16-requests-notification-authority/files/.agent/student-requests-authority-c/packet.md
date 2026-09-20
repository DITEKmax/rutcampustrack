# Requests notification authority — compact contract

Дата: 2026-09-08. Риск: S3/P1 (граница авторизации и приватных данных в
межсервисном gRPC и доставке уведомлений). Исполнитель: свежий `gpt-5.6-luna`,
effort `max`, sole writer этого worktree; детей не создавать.

## 1. Goal

Закрыть подтверждённый дефект: приватное уведомление старосте не должно
опираться на `event.group_id` или payload, а должно строиться только из
канонической persisted-заявки и текущего состава её академической группы.
Добавить узкий authenticated bot gRPC lookup и перевести headman alerts на него,
сохранив существующую attachment authorization и policy решения.

## 2. Context / evidence

- Frozen baseline: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`, detached assigned
  worktree `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-notification-authority`.
- Root/fresh Sol evidence: `headman_alerts.py:34-44` использует
  `event.group_id` для recipient lookup; persisted group читается только при
  attachment fetch (`headman_alerts.py:111-113`). Late/no-attachment payload
  поэтому может отправить private text/buttons в чужую группу.
- Exact source transport handoff is immutable at
  `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/requests-transport`;
  `.agent/student-role-02/diff.json` SHA-256
  `4725C54BF3F6300557697C4A756ED1565D5CCD8005F229C6C60AE3250744B2C4`.
  Import only its exact 82 product paths; exclude its 9 `.agent` artifacts,
  including unaccepted `services/notification-bot/uv.lock`.
- Accepted repair source is the immutable requests-isolation manifest
  `.agent/student-requests-isolation-c/repair-manifest.json` SHA-256
  `7B64350F73272787BD383E89611B75BE10959B02B4A0342695E45C46769462F3`;
  import only the two accepted IT files and preserve their evidence.
- No addressable memory/ownership gateway tool or source was available; ownership
  is therefore enforced by this assigned worktree and hash manifests.

## 3. Relevant scope

Own only: additive `proto/attendance.proto`; `StudentRequestService.java`,
`StudentRequestModels.java`, `grpc/AttendanceRequestBotGrpcServiceImpl.java`
and small helpers in the same namespace; focused Java tests; bot
`attendance_client.py`, generated `attendance_pb2.py`/
`attendance_pb2_grpc.py`, `headman_alerts.py` and focused Python tests; plus
`.agent/student-requests-authority-c/` evidence. Import the exact released
transport product paths and the exact two accepted IT repairs before adapting.
No other product paths are writable in this task.

## 4. Required behavior

Add `ResolveRequestNotification` to a private bot service:
`ResolveRequestNotificationRequest { StudentRequestKind kind=1,
string request_id=2 }` → `ResolveRequestNotificationResponse { int64 group_id=1,
int64 student_id=2, string student_name=3, StudentRequestDetail detail=4 }`.
The server validates the exact service secret, kind and id, resolves the
persisted Excuse/Late request, and returns canonical group/student/name/detail;
unknown or invalid values fail closed. Null/invalid raw status is rejected
before any legacy `mapStatus` normalization. Terminal APPROVED/REJECTED/
CANCELLED requests are non-actionable.

The bot uses event kind/id only as lookup keys. Payload group, student, reason,
lesson, comment, attachment metadata and buttons never override the canonical
response. Lookup failure or payload/group mismatch queues zero private
notifications. For a valid pending projection it enumerates current Academic
members of the canonical group and sends only current headmen with Telegram IDs;
current authority is checked at processing time. Existing per-actor attachment
fetch authorization remains intact.

## 5. Constraints

Use fake bot/fake queue tests only; no real Telegram, OTP, network, production,
secrets or containers. Keep existing decision/owner restrictions, `mapStatus`
and decision policy unchanged; add no Late self-decision rule. Do not change
public Student GET/JWT, REST/OpenAPI/shared non-attendance proto, global queue,
idempotency or delivery semantics. Do not hand-edit generated stubs; use the
existing pinned protoc generator. Preserve the imported empty `uv.lock` as a
baseline guard without changing it. Do not add unowned config; any new client
timeout must be a finite argument/default consistent with existing patterns.

## 6. Existing patterns

Reuse `StudentRequestGrpcMapper.detail`, `StudentRequestService` repository and
private helper patterns, `StudentRequestStatus.PENDING`, and
`AcademicGrpcClient.getGroupMembers/isHeadman/getUserDisplayName`. Reuse the
exact `AttendanceRequestBotGrpcSecretInterceptor` boundary for the whole private
service and current attachment actor/group checks. Existing event queue remains
best effort; no exactly-once Telegram claim.

## 7. Acceptance criteria

- Wrong event group with no attachment, Late requests, spoofed private fields,
  missing request, invalid kind, raw-null/invalid status and lookup/dependency
  failures enqueue no wrong recipient and no actionable buttons.
- Terminal request states enqueue no actionable notification; only a valid
  pending/DRAFT request builds canonical Russian reason/detail/content.
- Current revoked headman receives nothing; rightful current headmen receive the
  canonical private content and buttons. Attachment fetch still requires the
  persisted group/actor authorization.
- Missing or wrong `x-grpc-secret` is rejected; public student auth/protection is
  unchanged. No canonical response with missing required identity/detail is
  accepted.
- Existing 2×10 MiB attachment acceptance is not broadened or claimed by fake
  tests. Imported EventConsumerIT and RabbitDecisionRetryIT remain byte-for-byte
  at their accepted repair hashes.

## 8. Verification

Record exact revision, source/destination hashes, commands, exit codes, environment
and evidence under `.agent/student-requests-authority-c/`. First run focused
Python fake tests in the existing environment; do not mutate global installs or
`uv.lock`. Then use the ready Java/proto selectors only when available, generate
Java/Python stubs with repository tooling, and run focused Java unit/proto tests.
Runtime is limited to fake/in-process gRPC and fake queue/bot; external TG and
Testcontainers are N/A without a new root GO. Preserve repair-only diff versus
the imported baseline. Fresh independent Sol recheck is arranged by root after
the diff is stable.

## 9. Do not

Do not create children, write another worktree/main, cancel foreign changes,
trust payload or event group as authority, hand-edit generated stubs, normalize
invalid status to pending, alter public schemas, add delivery/idempotency
refactors, read/log secrets, run real messages or containers, or claim broader
transport/full ingress acceptance. Product/scope decisions become a recorded
delta request to root instead of a redesign.
