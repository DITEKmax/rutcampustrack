# Requests notification attachment reconciliation — correction contract

Дата: 2026-09-08. Риск: S3/P1. Исполнитель: свежий `gpt-5.6-luna`, effort
`max`; sole writer выделенного worktree, детей не создаю. Базовая ревизия:
`d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.

## 1. Goal

Закрыть подтверждённый P1-дефект: notification resolution не должна молча
усекать канонический inventory вложений, если persisted
`RequestAttachmentDocument` неполон или относится к другой заявке/владельцу.

## 2. Context / evidence

- Review: `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/student-gateway-c/reviews/p1-attachment-final-2026-09-08.md`, SHA-256
  `67F540C9BBD485E618D3DE590AB8A96590B4A0A4E8CD626B0B515874AA387C7A`.
- Frozen finding: `StudentRequestService.java:1134-1141` выбирает любой
  непустой stored list и отбрасывает остальные embedded descriptors; при
  `[A,B]` и stored `[A]` Resolve отдаёт только A, после чего бот может успешно
  fetch/queue A и никогда не узнает про B.
- Owner original `requests-transport-decision-result.md:131` требует, чтобы
  missing/expired/transient attachment был явной ошибкой до queue; текущий
  review подтверждает ту же границу.
- Parent/root decision: сверять inventory identity/count/uniqueness и
  `RequestAttachmentDocument.requestId`/`ownerStudentId`, затем проецировать
  stored documents, чтобы актуальный retention state дошёл до бота. Полное
  равенство `state`/`expiredAt` не требуется.
- Before target hashes: `StudentRequestService.java`
  `BD054202143BEB11265608080373BD874228C4057DB64ABC8F17A3C141B9DE1F`;
  `StudentRequestServiceAuthorizationTest.java`
  `0591DAC19F2FD5BDB7C3E41C9F66106FD9D0799945F8CFBD66141FEE9261BD7C`.

## 3. Relevant scope

Writable product/test scope is only:

- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java`;
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestServiceAuthorizationTest.java`;
- `.agent/student-requests-authority-c/` evidence.

All other dirty/imported files in this worktree are foreign baseline and stay
untouched. The source/test paths are imported untracked files at this frozen
revision; their current contents are preserved and only the bounded correction
is added.

## 4. Required behavior

`resolveRequestNotification(EXCUSE, requestId)` performs one stored attachment
repository read and a notification-only reconciliation:

- treat null embedded descriptors as an empty canonical inventory;
- reject null/blank descriptor IDs, duplicate embedded IDs, null/blank stored
  documents/IDs, duplicate stored IDs, wrong stored `requestId` or
  `ownerStudentId`, and any missing/extra ID;
- on a mismatch throw the existing safe domain error before returning any
  `NotificationResolution`;
- for a complete match, build detail descriptors from the stored documents in
  repository order, retaining current stored metadata and retention state;
- retain existing public `toDetail` fallback behavior, Late resolution,
  expiry/actor/group authorization, and queue/bot code.

## 5. Constraints

No public API/proto/OpenAPI/generated/config/dependency/lockfile/queue/bot/
Fetch-deadline/`student_alerts`/other-test changes. Do not alter public detail
behavior, normalize failures, catch/suppress reconciliation errors, invent an
attachment, run Docker/Testcontainers/real Telegram, or commit. No Gradle or
product runtime before an explicit root lease.

## 6. Existing patterns

Reuse `RequestAttachmentRepository.findByRequestIdAndOwnerStudentIdOrderByPositionAsc`,
`RequestAttachmentDocument` and `RequestAttachmentDescriptorDocument`,
`toDescriptor(RequestAttachmentDocument)`, `BadRequestException`, existing
Mockito/AssertJ focused test style, and the current private notification path.
The repository result is the authoritative projection only after the embedded
inventory has matched.

## 7. Acceptance criteria

- `[A,B]` embedded plus only stored A fails closed and cannot produce a
  `NotificationResolution` or actionable notification detail.
- Duplicate and wrong request/owner stored/embedded entries fail closed.
- Complete matched inventory succeeds and exposes current stored descriptor
  metadata/state; a retention state difference from the embedded snapshot is
  accepted.
- Zero attachments remains a valid notification resolution.
- Public detail callers still use their existing fallback semantics; no other
  frozen product path changes.

## 8. Verification

Before/after SHA-256 manifests, `git diff --check`, scoped diff/path guard, and
focused Java selector are recorded under this directory with command, exit
code, revision and environment. The exact selector to run after root lease is:

`./gradlew.bat :services:attendance-service:attendance-app:test --tests ru.rutcampustrack.attendance.grpc.AttendanceRequestBotGrpcServiceTest --tests ru.rutcampustrack.attendance.studentrequest.StudentRequestServiceAuthorizationTest --tests ru.rutcampustrack.attendance.grpc.StudentRequestGrpcErrorsTest --no-parallel --max-workers=1 --console=plain`

No Gradle/runtime is claimed before that lease. Existing historical evidence is
retained as context: selected Java 20/20, Python 18/18, generated outputs and
84-path source-freeze; a fresh independent Sol recheck is required after this
correction is stable.

## 9. Do not

Do not change `toDetail` behavior for public callers, compare mutable
`state`/`expiredAt`, trust event/payload fields, broaden the API, modify
generated/proto/config files, touch foreign work, run heavy checks without GO,
or claim full runtime/independent review before root completes those gates.
