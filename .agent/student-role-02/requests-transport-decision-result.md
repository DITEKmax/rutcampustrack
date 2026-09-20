## 1. Goal

Freeze an S3 developer contract for the Requests transport boundary: public PWA/TMA REST → Mobile BFF → authenticated gRPC → accepted Requests domain, plus trusted bot decisions, retained attachment fetch, authoritative events, legacy create retirement and upload limits.

## 2. Context/evidence

**Facts**

- Requests domain is accepted as the 32-file snapshot in `.agent/student-role-02/requests-review-3-source/manifest.json`, based on `8002b9ea…`; review3 is PASS. Root acceptance supersedes the manifest’s older “not acceptance” purpose string.
- BFF/proto/shared originals come from integration commit `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.
- `StudentRequestModels.Identity` is immutable and the domain no longer reads servlet/gRPC thread state: `StudentRequestModels.java:30`.
- `StudentGrpcIdentityInterceptor` protects only exact service `rutcampustrack.attendance.AttendanceStudentGrpcService`: `StudentGrpcIdentityInterceptor.java:18,34-38`.
- Existing student gRPC implementation derives identity only from validated `StudentGrpcIdentity.CLAIMS`: `AttendanceStudentGrpcServiceImpl.java:52,67,94-106`.
- Accepted decision paths revalidate headman against persisted resource group and Academic authority: `StudentRequestService.java:1043-1171,1235-1256`.
- `PRESENT_PRIORITY` is currently incorrectly mapped to proto `UNSPECIFIED`: `AttendanceStudentGrpcServiceImpl.java:188`.
- New upload currently inherits nginx `2m`; only legacy `/api/attendance/excuses/with-file` has `25m`. Mobile BFF has no multipart limits, Attendance servlet is `10MB/12MB`, and Gateway’s global codec memory remains `12MB`.
- Bot excuse callback publishes Telegram ID and immediately displays a final verdict; late-checkin publishes resolved internal ID and shows pending: `excuse.py:67-110`, `late_checkin.py:30-68`.
- Bot consumes from `rut-uit.events` via `notification-bot.events`; Attendance consumes through `attendance-service.events`. Both use DLQ. Java claim participates in Mongo transaction; bot Redis claim currently precedes handler completion.
- Accepted LOW limitation remains: after committed decision plus lost ACK, a continuing recovery-read/dependency failure can escape; persisted state and its single outbox event survive, and later ordinary terminal command repeats conflict.

## 3. Relevant scope and ownership

**Decision**

Use one fresh implementation writer in a private checkout based on `d3c31ac…`. First import the exact accepted32 files and verify their hashes and compilation. No domain policy or Identity redesign.

The writer is sole owner of:

- Shared contracts/generated artifacts: `proto/attendance.proto`, `docs/openapi/mobile-bff.json`, `frontends/mobile-core/src/api/generated/mobile-bff.ts`, new bot `attendance_pb2.py` and `attendance_pb2_grpc.py`.
- Attendance adapters: `AttendanceStudentGrpcServiceImpl`, `StudentGrpcIdentityInterceptor` tests, new request mapper/error mapper, new exact-service bot-secret interceptor and bot attachment gRPC service, `EventConsumer`, `AcademicGrpcClient`, and only narrow adapter additions to `StudentRequestService`.
- Public Java contract/BFF: `StudentApi.java`, new `StudentRequestApiModels.java`, `StudentApiController`, new `StudentRequestFacade`, `MobileAttendanceClient`, `MobileProblemHandler`, `MobileIdentityFilter`.
- Legacy seams: `ExcuseApi/Controller/Service`, `LateCheckinApi/Controller/Service`, retirement exception/handler and corresponding tests.
- Events/bot: `excuse.decision.json`, `late_checkin.decision.json`, accepted requested/decided schemas and publishers, `excuse.py`, `late_checkin.py`, `headman_alerts.py`, `event_dispatcher.py`, `event_consumer.py`, `idempotency_guard.py`, new `attendance_client.py`, bot bootstrap/config and tests.
- Transport limits: Mobile BFF and Attendance application YAML, nginx prod/e2e exact locations.

`services/api-gateway/src/main/resources/application.yml` and `application-prod.yml` overlap dependency24. They may be changed only after its corrected baseline is accepted. The current Springdoc 2.8.6/Boot 3.5 failure blocks combined config/runtime acceptance. Nginx edits must also follow the separate XFF repair sequentially.

If root splits the work, bot/event work starts only from the accepted public transport revision; `attendance.proto`, generated files and Attendance adapter files never have concurrent writers.

## 4. Required behavior

### Public REST and Java DTOs

Extend `StudentApi` under `/api/v1/student`:

| Route | Request | Success |
|---|---|---|
| `GET /requests?bucket=OPEN|ARCHIVE&page=0&size=20` | page ≥ 0, size 1..100; bucket defaults `OPEN` | `200 StudentRequestPage` |
| `GET /requests/options` | — | `200 StudentRequestOptions` |
| `GET /requests/{id}` | 24-hex request ID | `200 StudentRequestDetail` |
| `POST /requests/excuse` | multipart `request` JSON plus repeated `files`; required `Idempotency-Key` | `200 StudentRequestDetail` |
| `POST /requests/late-checkin` | JSON `{lessonId}` plus required `Idempotency-Key` | `200 StudentRequestDetail` |
| `POST /requests/{id}/cancel` | — | `200 StudentRequestDetail` |
| `GET /requests/{id}/attachments/{attachmentId}` | — | detected bytes/type/name |

All JSON, Problem Details and attachment responses are `Cache-Control: no-store`. Downloads also set `Content-Disposition`, exact detected `Content-Type`, `Content-Length` and `X-Content-Type-Options: nosniff`.

Java-first public types:

- Enums: `StudentRequestKind(EXCUSE,LATE_CHECKIN)`, `StudentRequestStatus(PENDING,APPROVED,REJECTED,CANCELLED)`, `StudentRequestOrigin(MANUAL,AUTO_GEO_FAILURE)`, `StudentRequestBucket(OPEN,ARCHIVE)`, five accepted `StudentExcuseReason` values, `StudentRequestAttachmentState(ACTIVE,EXPIRED)`.
- `StudentRequestLesson`: required `id`, `lessonNumber`, `status`, `blocked`; nullable `subjectId`, `subjectName`, `subjectType`, `semesterId`, `date`, `startsAt`, `endsAt` to preserve existing stored applications.
- `StudentRequestSummary`: required `id/kind/status/origin/lessons/createdAt/updatedAt`.
- `StudentRequestDecision`: nullable `comment/decidedAt`; do not expose `decidedBy`.
- `StudentRequestAttachment`: `id/name/contentType/sizeBytes/sha256/state/uploadedAt/expiresAt`, nullable `expiredAt`.
- Detail mirrors accepted domain: `summary`, nullable `reason/comment/decision`, required attachments array.
- Page: `content/page/size/totalElements/totalPages`.
- Options: reasons, exact file limits, budget `{semesterId,limit,used,remaining}`, lesson options with separate eligibility flags and `pendingRequests[{id,kind,origin}]`.
- REST relational IDs are decimal strings; Mongo IDs are strings. Dates are ISO `LocalDate`, times ISO `LocalTime`, instants RFC3339 UTC.
- Excuse JSON contains only `lessonIds/reason/comment`; files remain multipart. Late command contains `lessonId`. Idempotency key is visible ASCII `16..128`.

### gRPC

Keep all public student RPCs in the already protected `AttendanceStudentGrpcService`; never add identity fields:

- `ListStudentRequests(StudentRequestListQuery)`
- `GetStudentRequest(StudentRequestId)`
- `GetStudentRequestOptions(StudentRequestOptionsQuery)`
- `SubmitStudentExcuse(SubmitStudentExcuseCommand)`
- `SubmitStudentLateCheckin(SubmitStudentLateCheckinCommand)`
- `CancelStudentRequest(StudentRequestId)`
- `DownloadStudentRequestAttachment(StudentRequestAttachmentId)`

Freeze fields:

- `StudentRequestListQuery`: `bucket=1`, optional `page=2`, optional `size=3`.
- `StudentRequestId`: `request_id=1`; attachment query adds `attachment_id=2`.
- Excuse command: `lesson_ids=1`, `reason=2`, optional `comment=3`, repeated attachment upload `=4`, `idempotency_key=5`.
- Attachment upload: `name=1`, `declared_content_type=2`, `data=3`.
- Late command: `lesson_id=1`, `idempotency_key=2`.
- Summary/detail/page/options messages mirror the public fields; nullable values use proto presence. `decided_by` is absent from the public student projection.
- Download payload: `data=1`, `content_type=2`, `filename=3`, `size=4`.
- Add `AUTOMATIC_CHECKIN_RESOLUTION_REASON_PRESENT_PRIORITY = 5` and map it through BFF `ResolutionReason.PRESENT_PRIORITY`.

Add `StudentRequestErrorDetail` with exact codes:

`INVALID_REQUEST`, `INVALID_IDEMPOTENCY_KEY`, `INVALID_SESSION`, `WRONG_ROLE`, `OUT_OF_SCOPE`, `REQUEST_NOT_FOUND`, `ATTACHMENT_NOT_FOUND`, `REQUEST_CONFLICT`, `ATTACHMENT_EXPIRED`, `PAYLOAD_TOO_LARGE`, `DEPENDENCY_UNAVAILABLE`.

Map without parsing messages:

- `400`: invalid request/key.
- `401`: missing/forged/expired JWT.
- `403`: wrong role/out-of-scope.
- `404`: request/attachment missing.
- `409`: eligibility, terminal state or idempotency payload conflict.
- `410`: expired attachment.
- `413`: per-file/total/transport size.
- `503`: Academic/Schedule/gRPC dependency unavailable.

### Identity and bot attachment service

- Attendance builds `StudentRequestModels.Identity` only from signed JWT claims for public gRPC.
- Existing attendance web headman decision routes build the same immutable record only from the real request-scoped `RequestContext`; no fake request attributes or ThreadLocal mutation.
- Add separate `AttendanceRequestBotGrpcService.FetchExcuseAttachment`.
- Request fields: positive `actor_user_id=1`, `request_id=2`, `attachment_id=3`; group is never supplied.
- An exact-service `x-grpc-secret` interceptor protects only this service and rejects missing, invalid or unconfigured secret. It must not assume the student JWT interceptor applies.
- Fetch ordering: authenticate service → validate actor/IDs → load persisted ticket → reject self → revalidate `Academic.isHeadman(actor, ticket.groupId)` → verify attachment belongs to that ticket/group → enforce logical expiry → return bytes.
- Bot fetches each attachment separately for each target headman. Cached event/group membership cannot authorize a download.

### Events and authoritative decisions

- Keep envelope version `1`, exchange `rut-uit.events`, routing key `""`, existing Attendance/Bot queues and DLQs.
- Add strict `excuse.decision` schema and tighten `late_checkin.decision`: source `notification-bot`, positive internal `decision_by`, ID, boolean `approved`, nullable normalized `decision_comment` for excuse. No Telegram IDs or guessed compatibility.
- Attendance ignores event-supplied group. It loads the persisted request group and performs the accepted Academic headman/self guard before mutation.
- Same `event_id` is consumer-deduplicated. For a new event ID hitting a terminal resource:
  - same trusted actor/outcome/normalized comment: semantic duplicate, ACK as no-op;
  - different outcome/actor/comment: stale conflict, read and retain actual persisted detail, ACK with no mutation and no success message;
  - foreign/forged actor, failed authority revalidation or malformed event: fail closed to DLQ, never treat as no-op.
- Neither semantic case emits a second `*.decided`.
- Excuse callback publishes the resolved internal actor and shows only “Решение отправлено”. Both callback flows remove/disable buttons but final wording comes only from actual `excuse.decided`/`late_checkin.decided`.
- Authoritative mapping distinguishes approved, rejected, student-cancelled, geo-confirmed and `PRESENT_PRIORITY`; excuse cancellation must not appear as rejection.
- Remove the base64 publisher/consumer path. `excuse.requested` contains descriptors only; bot fetches bytes before enqueueing delivery. Expired/missing/transient fetch is explicit, never silently omitted.
- Bot Redis dedup becomes two-phase: short processing lease, completion TTL at least the seven-day DLQ retention, compare-and-release on handler failure. Fetch failure occurs before Telegram tasks are enqueued.

### Legacy and ACK recovery

Externally retire with `410 LEGACY_ENDPOINT_RETIRED`, before dependencies or writes:

- `POST /api/attendance/excuses`
- `POST /api/attendance/excuses/with-file`
- `POST /api/attendance/late-checkin/{lessonId}`

The already retired `/api/attendance/checkin` remains unchanged. Preserve stored requests/files and all necessary read/headman decision endpoints, but make decision endpoints delegate to accepted `StudentRequestService` locks/guards.

A timeout/503 after a compound decision ACK is uncertain, not proof of rollback. Callers with an ID recover through `GET /requests/{id}`; creates replay the same idempotency key. An ordinary terminal mutation repeat remains `409`. Bot semantic recovery reads actual persisted state and never manufactures a second success event.

### Upload path

- Exact domain limits: `0..2` files, each ≤ `10,485,760`, total ≤ `20,971,520`; JPEG/PNG/PDF signature, extension and declared MIME must agree.
- Exact nginx location `/api/v1/student/requests/excuse`: `24m`; global `2m` remains. Mirror the exact location in e2e nginx.
- Add a specific Gateway route before generic mobile BFF with `RequestSize 24MB` and the existing per-user upload rate-limit pattern. Do not raise global codec memory above `12MB`; the route must stream.
- Mobile BFF multipart: `max-file-size 10MB`, `max-request-size 24MB`.
- Attendance gRPC server and Mobile BFF attendance client inbound message limits: `24MB`; bot receive limit at least `24MiB`.
- Add `Idempotency-Key` to production CORS allowed headers.
- Size rejected inside Java returns typed Problem Details. Edge rejection must at minimum preserve external `413` plus `Cache-Control: no-store`; no partial Mongo writes.

## 5. Constraints

- No domain Identity/policy redesign, compatibility create path, browser-stored token, file bytes in events, peer actor ID in student DTOs, production messages or production data.
- No global gRPC-secret interceptor that accidentally weakens or changes the JWT student boundary.
- Do not trust event `group_id`, raw XFF, bot membership cache or callback Telegram ID for authorization.
- Config edits wait for accepted dependency24 baseline; nginx ownership follows XFF work sequentially.
- Shared proto/OpenAPI/generated/config files have one writer.

## 6. Existing patterns/dependencies

- Reuse `MobileGrpcAuth` token forwarding and `MobileAttendanceClient` typed `google.rpc.Status` translation.
- Reuse `StudentCheckinModels.Identity`/`AttendanceStudentGrpcServiceImpl` mapping style, while targeting accepted `StudentRequestModels.Identity`.
- Reuse Mongo transaction/outbox, pair locks and receipt replay from accepted32.
- Java consumer dedup is transaction-bound through `MongoIdempotencyStore`; outbox delivery is at-least-once.
- Reuse bot `AcademicGrpcClient` resolution and existing secret metadata pattern for the new Attendance client.
- Reuse the already accepted legacy checkin `410` seam.
- Current bot external send queue is retrying/best-effort; this contract does not claim exactly-once Telegram delivery.

## 7. Acceptance criteria

- Missing/forged JWT never reaches any new student RPC; valid direct gRPC works without servlet request state.
- Wrong role, missing group, headman submit, foreign request/attachment and forged bot actor fail with the frozen status/code.
- List/detail/options preserve exact sorting, pagination, nullability and omit peer identity.
- Replays use the same resource; key payload mismatch is `409`; no duplicate receipt/files/budget/outbox.
- Exact `2 × 10MiB` succeeds through full ingress; third file, one-byte-over, total-over and spoofed content fail atomically.
- Owner and authorized target headman can fetch only their allowed attachment; cross-group, self, expired, missing and bad-secret cases return no bytes.
- Legacy creates return 410 and create zero domain/outbox/attachment/budget writes; old reads and decisions still work through new invariants.
- Exact event duplicate and semantic duplicate create no second decision/event; stale conflicting command leaves actual persisted state unchanged; authz failure reaches DLQ.
- Bot displays pending after callback and derives final text from persisted `*.decided`, including cancellation and `PRESENT_PRIORITY`.
- No base64/binary occurs in outbox/event schemas.
- Compound lost-ACK plus recovery-read success returns actual detail; continuing recovery failure returns 503, later detail shows actual persisted result, and outbox count remains one.

## 8. Verification

Consultation runtime: **N/A; no files changed**.

Future evidence must record revision, command, exit, environment and artifact:

- Accepted32 SHA recheck, scoped diff and attendance compile.
- Attendance focused unit tests plus real Mongo replica-set transaction/event tests.
- Mobile BFF tests including real HTTP → signed gRPC → Mongo path and typed errors.
- OpenAPI snapshot regenerate/check, then offline generated TypeScript regeneration and clean drift.
- Proto Java generation plus checked-in Python stub regeneration.
- Bot `ruff check`, `ruff format --check`, full pytest/coverage and focused handler security coverage.
- Rabbit integration for exchange/queues/DLQ/dedup, using fake Telegram only.
- Full Docker ingress test: nginx → Gateway → BFF → gRPC → Mongo replica set, including exact `2 × 10MiB`, >24MiB 413/no-store and CORS preflight with `Idempotency-Key`.
- Stable diff receives fresh independent Sol high review. Config/runtime remains BLOCKED until dependency24 correction is accepted.

## 9. Do not

Do not write main, alter accepted domain rules, expose `decidedBy`, add identity fields to public proto, trust event group/Telegram IDs, copy raw-XFF configuration, weaken global limits, send real Telegram messages, publish file bytes, add legacy fallback, claim full-role PASS, or claim final ingress acceptance before the dependency/config baseline is reconciled.
