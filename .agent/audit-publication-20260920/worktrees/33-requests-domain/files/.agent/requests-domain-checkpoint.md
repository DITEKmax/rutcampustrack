# Requests-domain stop checkpoint — 2026-09-07

## Scope and frozen contract

- Worktree: `codex/student-role-02-requests-domain`, baseline `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
- Bounded scope: attendance student-request domain for EXCUSE/manual LATE_CHECKIN, owned attendance enums/entities/event schema/Mongo indexes; no proto/BFF/frontend/generated/root config changes; no commit, rollback, deploy or data cleanup.
- Contract source: root `.agent/student-role-02/requests-domain-packet.md` plus accepted `.agent/student-role-02/excuse-source-resolution.md`.
- Owner correction received immediately before stop: immutable lesson snapshots must preserve `subjectName` and `subjectType`, loaded through Academic before the write transaction.

## Changes present

- Added `StudentRequestKind`, `StudentRequestOrigin`, `StudentRequestStatus` and expanded excuse/late enums for the student projection and cancellation/present-priority paths.
- Added `studentrequest/**`: Java domain records, Mongo receipt/budget/attachment/snapshot documents and repositories, `StudentRequestService`, attachment retention job, validation, idempotency/replay, pair locks, Mongo transaction writes, union list/detail/options/cancel/download, decision paths, file magic/MIME/extension checks and attachment expiry.
- Extended `ExcuseTicket`, `LateCheckinRequest`, and immutable lesson snapshots with semester, schedule, and subject display snapshots. `StudentRequestService.resolveLessons` calls the semester-scoped schedule query and `AcademicGrpcClient.getSubjectDetailsByIds` before transaction; options are enriched and scope-checked.
- Added receipt/budget/attachment/ticket indexes in `MongoConfig`.
- Added descriptor-only overload to `ExcuseEventPublisher`; event schema now allows five new student reasons and attachment metadata, and late decision schema includes `student_cancelled`/`present_priority`. Existing legacy base64 method and old REST routes remain for the later adapter/retirement decision; bot is intentionally untouched.
- Added gRPC mapping for `CANCELLED_BY_STUDENT`; `PRESENT_PRIORITY` maps to proto unspecified because the existing automatic-checkin proto has no matching value.

## Checks and evidence

- `./gradlew.bat :services:attendance-service:attendance-app:compileJava` — exit `0` (after initial six-error compile fix).
- `./gradlew.bat :services:attendance-service:attendance-app:testClasses` — exit `0`.
- PowerShell `ConvertFrom-Json` on `event-schemas/excuse.requested.json` and `event-schemas/late_checkin.decided.json` — exit `0`, output `json-ok`.
- No active foreground check sessions remain. The last Gradle sessions ended; shared Gradle daemons were not stopped. No container or runtime was started.

## Unfinished / required next steps

- Re-read and review the complete `StudentRequestService` after subject-snapshot correction; add focused tests and run applicable existing attendance tests plus task-owned Mongo replica-set/runtime evidence.
- Decide with root whether legacy `/attendance/excuses` and `/attendance/late-checkin` create paths must delegate to this domain or remain as an explicitly recorded transport/retirement gap; they can currently bypass the new budget/eligibility and legacy excuse publisher still has a base64 file method.
- Review decision authority and approval integration: existing EventConsumer still calls `ExcuseService`/`LateCheckinService`; new domain decision methods need a deliberate adapter/integration choice before claiming race-safe approval for legacy events. Do not redesign while stopped.
- Validate the `RequestAttachmentRetentionJob` profile/scheduling behavior, indexes against real Mongo, and descriptor event consumer handoff (notification-bot currently reads `file_payload_b64`; bot changes are outside this scope).
- Check `LessonSnapshot` component ordering/API compatibility with the future transport writer after root review.

## Working-tree state

- Tracked files modified: event schemas, attendance enums, `MongoConfig`, `ExcuseEventPublisher`, `ExcuseTicket`, `LateCheckinRequest`, `AttendanceStudentGrpcServiceImpl`.
- Untracked: new student-request package and student-request enums, plus this checkpoint.
- Preserve all existing files and foreign work; do not commit or reset from this checkpoint.
