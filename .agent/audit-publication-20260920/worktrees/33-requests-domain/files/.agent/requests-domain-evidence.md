# Requests-domain evidence

## Implementation evidence

- `StudentRequestService.submitExcuse` and `submitLateCheckin` validate the
  command, replay receipts before external dependencies, resolve current
  semester/group/subject snapshots, then write ticket/request, attachments,
  receipt, budget and outbox data in one Mongo transaction.
- `StudentRequestService.resolveLessons` calls the schedule semester query and
  `AcademicGrpcClient.getSubjectDetailsByIds`; persisted
  `StudentLessonSnapshotDocument` contains immutable subject name/type,
  schedule, status and blockage fields.
- `StudentRequestService.consumeBudget` uses the unique student/semester
  budget document and conditional increment; `executeWithRetry` handles
  transient transaction and duplicate-key outcomes.
- `list`, `get`, `options`, `cancel`, `download` and `expireAttachments` cover
  the owner union projection, pending kind/origin details, cancellation,
  attachment binding/expiry and scheduled byte clearing.
- `decideExcuse` and `decideLateCheckin` re-read under sorted pair locks and
  require positive authenticated scope, exact `STUDENT` role, headman flag,
  matching decision actor and matching resource group. Existing `PRESENT`
  attendance is never overwritten.
- `MongoConfig` adds receipt, budget, owner attachment, attachment expiry and
  excuse projection indexes. `ExcuseEventPublisher` emits descriptor metadata
  through the existing outbox; new flow never passes attachment bytes.
- Contract enums and event schemas carry student request kinds/origins/statuses,
  new excuse reasons, cancellation/present-priority reasons and attachment
  descriptors. Existing legacy base64 publisher overload remains untouched for
  the later consumer/adapter decision.

## Runtime evidence

Final command:

```text
.\\gradlew.bat --no-daemon :services:attendance-service:attendance-app:integrationTest --rerun-tasks --tests ru.rutcampustrack.attendance.studentrequest.StudentRequestDomainIT
```

It exited `0` on 2026-09-07. The result XML reports `tests=10`,
`skipped=0`, `failures=0`, `errors=0`, timestamp `2026-09-07T08:03:48`.
Testcontainers started a unique `mongo:7.0` container on an ephemeral port;
the Mongo driver log reports `type=REPLICA_SET_PRIMARY` and `setName=docker-rs`.
The task-owned database name and container name include a random run id, and
the test never cleans an existing runtime.

The ten runtime cases cover: six concurrent manual submissions charging
exactly five; same-key single ticket/receipt; package rollback; overlapping
packages; exact two-file 10 MiB storage and cancel retention; spoofed,
third-file and oversize rejection; logical expiry/download denial and byte
clearing; mixed owner list/options without peer data; `PRESENT` approval
priority; and rejection/cancellation without budget refund.

Final focused command exited `0`. XML counts are 5 authorization tests, 4
publisher tests, 10 excuse tests, 5 late event-contract tests and 21 late
service tests: 55 total, all passing with no skips/failures/errors.

## Defect gates and corrections

Earlier task-owned runtime reproduction found four domain defects: year
retention used an unsupported `Instant.plus` unit; BSON date/time values were
parsed only as strings; the union open bucket compared a lowercase late status
with uppercase projection; and concurrent initial budget upserts needed bounded
duplicate/transient retry plus an exhausted-budget pre-read. The implementation
now uses UTC zoned `plusYears(1)`, Date/Instant conversion, normalized status
projection and bounded transaction retries/conditional budget increment. The
runtime suite was rerun after each correction and passes.

The final authz audit reproduced a separate boundary gap: `role == null` was
accepted by student and decision scopes. The frozen contract requires an
authenticated `STUDENT` role, so both checks now reject null/non-student roles.
Two negative tests cover missing-role decision and submit paths; they pass in
the final 55-test focused run.

## Source/event handoff

The new producer is `ExcuseEventPublisher.publishRequested(ticket, lessons,
descriptors)` and uses the existing `excuse.requested` outbox event and fanout
path. It carries descriptor metadata only. The existing notification-bot
consumer still reads the legacy `file_payload_b64` shape, so an adapter or
consumer contract decision is required before attachment notifications claim
end-to-end completion. `PRESENT_PRIORITY` has no matching automatic-checkin
proto enum and is mapped to proto `UNSPECIFIED`; root must decide the later
transport/proto delta.
