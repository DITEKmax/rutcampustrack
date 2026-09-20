# Requests notification attachment reconciliation — R3 compact contract

Дата: 2026-09-08. Исполнитель: fresh `gpt-5.6-luna`, effort `max`; sole
writer выделенного worktree, детей не создаю. Risk: S3/P1. Base revision:
`d3c31acb8cce53791a4981e5858a37d44fdc9a0e` (detached). Worktree:
`C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-notification-authority`.

## 1. Goal

Закрыть подтверждённый дефект notification resolution: при embedded
descriptors `[A,B]` и stored documents `[A]` нельзя молча вернуть только A.
Не допускать выдачу `NotificationResolution` с неполным или подменённым
inventory вложений.

## 2. Context / evidence

- Independent review artifact:
  `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/student-gateway-c/reviews/p1-attachment-final-2026-09-08.md`, SHA-256
  `67F540C9BBD485E618D3DE590AB8A96590B4A0A4E8CD626B0B515874AA387C7A`.
- Review finding: `toDetail(ExcuseTicket)` выбирает любой непустой stored list
  и отбрасывает оставшиеся embedded descriptors; при `[A,B]`/`[A]` bot не
  узнаёт о B.
- Owner/correction evidence требует explicit failure при missing/extra,
  duplicate или misbound attachment до actionable notification.
- Exact before hashes записаны в `before-hashes.txt`; inherited dirty baseline
  содержит 106 status rows и сохраняется целиком.

## 3. Relevant scope

Product/test writer scope — ровно два пути:

- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java`;
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestServiceAuthorizationTest.java`.

Evidence writer scope — только `.agent/student-requests-authority-c/r3/**`.
Все остальные tracked/untracked rows и существующие evidence files не менять.

## 4. Required behavior

Только для `resolveRequestNotification(EXCUSE, requestId)`:

- null embedded descriptors трактовать как canonical empty inventory;
- reject null/blank descriptor IDs и duplicate embedded IDs;
- reject null/blank stored documents/IDs и duplicate stored IDs;
- reject stored `requestId` или `ownerStudentId`, не совпадающие с persisted
  ticket;
- reject любую missing/extra ID;
- mismatches бросают существующий safe domain error до возврата
  `NotificationResolution`;
- при полном совпадении проецировать stored documents в repository order,
  включая актуальные metadata/retention state; mutable `state`/`expiredAt`
  embedded snapshot не сравнивать;
- zero attachments остаётся валидным.

## 5. Constraints

Не менять public API/proto/OpenAPI/generated/config/deps/lockfiles, queue/bot,
Fetch deadline, `student_alerts` или другие тесты. Не менять public `toDetail`
fallback, Late resolution, expiry/actor/group checks. Не запускать Gradle до
explicit root GO. Не использовать Docker/Testcontainers/Telegram/runtime.
Не commit/reset/clean и не трогать foreign 106-row dirty scope.

## 6. Existing patterns

Использовать `RequestAttachmentRepository.findByRequestIdAndOwnerStudentIdOrderByPositionAsc`,
`RequestAttachmentDocument`, `RequestAttachmentDescriptorDocument`, existing
`toDescriptor(RequestAttachmentDocument)`, `BadRequestException`, Mockito и
AssertJ. Reconciliation должна быть notification-only; public callers
продолжают идти через текущий `toDetail` fallback.

## 7. Acceptance criteria

- embedded `[A,B]` + stored `[A]` fails closed and returns no resolution;
- null/blank, duplicate, missing, extra, wrong request/owner entries fail
  closed (focused tests cover representative branches);
- complete `[A,B]` match succeeds in stored repository order and returns
  current stored metadata/retention state even when snapshot state differs;
- zero embedded/stored attachments succeeds;
- public detail fallback and all out-of-scope request/queue/bot behavior stay
  unchanged;
- final diff contains only two product/test paths plus r3 evidence.

## 8. Verification

Before product edit: exact hashes, HEAD, status row count and scope inventory.
After edit: exact path guard, target before/after hashes, `git diff --check`,
scoped diff review and light static inspection. Record command, environment and
exit code in r3 evidence. Prepare but do not run before GO:

`./gradlew.bat :services:attendance-service:attendance-app:test --tests ru.rutcampustrack.attendance.grpc.AttendanceRequestBotGrpcServiceTest --tests ru.rutcampustrack.attendance.studentrequest.StudentRequestServiceAuthorizationTest --tests ru.rutcampustrack.attendance.grpc.StudentRequestGrpcErrorsTest --no-parallel --max-workers=1 --console=plain`

Focused Gradle/runtime is pending root GO; no runtime evidence is claimed yet.
Fresh independent Sol review is required after stable pass.

## 9. Do not

Do not alter public `toDetail` semantics, compare mutable retention fields,
trust event payload IDs, invent descriptors, suppress reconciliation errors,
broaden scope, modify four-file future Fetch-deadline/`student_alerts` work,
run heavy checks without GO, or claim full runtime/review before those gates.
