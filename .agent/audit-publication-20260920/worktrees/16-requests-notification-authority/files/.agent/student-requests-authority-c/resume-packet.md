# Requests notification authority — resume packet

Дата: 2026-09-08. Resume от STOPPED WIP; риск S3/P1. Исполнитель: свежий
`gpt-5.6-luna`, effort `max`, sole writer выделенной области; детей не создаю.
Baseline/HEAD на resume: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e` (detached).
Shutdown checkpoint сохраняется неизменным.

## 1. Goal

Завершить существующий WIP private `ResolveRequestNotification` и headman
projection так, чтобы текст, detail, group/student identity и recipients
строились из persisted Attendance request и текущего Academic состава; закрыть
известные authz/failure/import-test дефекты без redesign.

## 2. Context / evidence

- Frozen local WIP: `.agent/student-requests-authority-c/{packet.md,shutdown-checkpoint.md,import-guard.md}`.
- Root resume audit: HEAD `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`, dirty
  state соответствует STOPPED snapshot; scoped hashes записаны в
  `exact-path-manifest.txt`.
- Canonical owner original открыт по
  `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/student-role-02/requests-transport-decision-result.md`
  (lines 121–172); это источник требований, а не область записи.
- Transport/repair imports уже приняты и guard не переимпортируется:
  transport manifest SHA-256
  `4725C54BF3F6300557697C4A756ED1565D5CCD8005F229C6C60AE3250744B2C4`, repair
  manifest SHA-256
  `7B64350F73272787BD383E89611B75BE10959B02B4A0342695E45C46769462F3`.
- Known reproduction: focused Python collection fails because generated
  `attendance_pb2_grpc.py` imports top-level `attendance_pb2`; production
  package pattern is `from bot.grpc_client import ...` and generated files must
  be refreshed by the pinned generator, never hand-edited.

## 3. Relevant scope

Only these product/test paths are writable in this worktree:

- `proto/attendance.proto`;
- Attendance `StudentRequestService.java`, `StudentRequestModels.java`,
  `AttendanceRequestBotGrpcServiceImpl.java` and their focused Java tests;
- notification-bot `attendance_client.py`, generated `attendance_pb2.py` and
  `attendance_pb2_grpc.py`, `headman_alerts.py`,
  `test_headman_alerts.py`, `test_attendance_request_grpc_client.py`;
- `.agent/student-requests-authority-c/` evidence.

No foreign dirty paths are touched. New Java test is allowed by the frozen
contract; generated files are changed only through the pinned generator.

## 4. Required behavior

The private bot RPC accepts only valid persisted kind/id and returns canonical
group/student/name/detail. Raw null/invalid status is rejected before any
`mapStatus` normalization; pending/DRAFT are actionable and terminal states
are no-op. Bot event payload (including group, reason, lesson, comment,
attachments and buttons) is untrusted. Resolve and current Academic membership
failures propagate before queueing; canonical group mismatch queues zero.
Attachment fetch keeps persisted group/actor authorization and invalid,
expired, missing or transient descriptors fail before enqueue. Current headmen
with Telegram IDs receive canonical Russian content; revoked headmen do not.

## 5. Constraints

Preserve existing decision/domain policy, private secret boundary, attachment
authorization and queue/dedup semantics. Public Student JWT/API remains
unchanged. No REST/OpenAPI/config/lockfile/global changes, Docker,
Testcontainers, real Telegram, secrets, deploy, migration, or future
Fetch-deadline/student-alerts work. No payload-authority workaround or
exactly-once redesign.

## 6. Existing patterns

Reuse `StudentRequestGrpcMapper`, `StudentRequestService` repository/status
guards, `AttendanceRequestBotGrpcSecretInterceptor`, `AcademicGrpcClient`
current membership methods, fake queue/bot tests, and the package-correct
generated-stub pattern used by `academic_pb2_grpc.py` and
`schedule_pb2_grpc.py`. Use source venv
`C:/Users/maksd/IntelliJIDEA/rutcampustrack/services/notification-bot/.venv/Scripts/python.exe`
without global installs or lockfile mutation.

## 7. Acceptance criteria

Canonical-only content/recipients for late, no-attachment and excuse flows;
wrong/missing secret, ID, kind, null/invalid status, terminal, mismatch and
dependency failure yield no wrong notification. Pending/DRAFT produces
canonical buttons/detail. Revoked headman is excluded. Attachment permission
checks remain enforced. Imported two accepted IT files stay byte-identical.

## 8. Verification

Run focused Python compile/pytest with explicit `addopts=` and resolved package
imports; record command and exit code. Add/run focused Java selectors only when
the permitted Gradle lease is available; do not run full Gradle/Testcontainers.
Record revision, exact path/hash checks, runtime fake/in-process evidence,
failures, and limitations under `.agent/student-requests-authority-c/`.
Fresh Sol review remains root's next phase after stable diff.

## 9. Do not

Do not create children, other worktrees, shared/main edits, source reimports,
resets, generated-stub hand edits, queue/dedup redesign, public schema changes,
or external messages/runtime. Do not convert WARN/ERROR into code changes
without request + reproduction + evidence. A product/contract/scope decision
is recorded as a delta request to root instead of redesign.
