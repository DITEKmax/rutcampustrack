# Compact contract — JS-STUDENT-01-r1

Date: 2026-09-06. Risk: S3. Status: foundation authorized; dependent product
implementation starts only after this revision is exported and hashed.

## 1. Goal

Deliver the common Vue/PCSS student “Today → geo check-in” flow for PWA and TMA,
plus JS-STUDENT-02 automatic headman escalation on failed or unavailable geo. The
server is authoritative; the BFF composes REST while attendance owns eligibility,
cooldown, concurrency and mutations.

## 2. Context and evidence

- `docs/research/reference-rutcampustrack-design/knowledge/job-stories.md:702-708`:
  geo failure creates the request automatically, in the same operation, with one
  active request per student/lesson.
- `docs/architecture/reference-rutcampustrack-design/backend-conflicts.md:530-558`:
  inclusive window `lesson start - 5m` through `lesson end + 5m`; automatic request
  does not expire and is distinct from the semester-limited past-lesson appeal.
- Owner decision 2026-09-06 supersedes the former permanent PENDING block and the
  old ability for geo to overwrite a manual headman “н”: retry is permitted at the
  five-minute boundary after PENDING or REJECTED; manual headman “н” blocks geo and
  requires the separate appeal flow, outside this vertical.
- Current code evidence: geo failure returns 422 before persistence; successful geo
  overwrites HEADMAN ABSENT; pending check then insert has a race and no partial
  unique constraint; response lacks durable cooldown/request metadata. Existing
  Mongo transaction/outbox and attendance uniqueness must be retained.

## 3. Relevant scope and ownership

Contract owner/integrator owns `services/mobile-bff/`, the new attendance proto
boundary, exact attendance changes needed by this command, gateway route/auth,
generated OpenAPI/TS/fixtures, shared build/config/lockfiles and this task state.
Frontend later owns only `frontends/mobile-core/`, `frontends/pwa-vue/` and
`frontends/tma-vue/` in its isolated lane. Backend domain work later runs in its
isolated lane. All lanes use the same HEAD plus the same hashed overlay/revision.

## 4. Required behavior

### Public API

Java interfaces/records in `mobile-bff-api-contract` are the sole public source.
Springdoc exports OpenAPI; TypeScript and fixtures derive from that export. JSON
IDs are strings, instants RFC3339 UTC, lesson dates/time interpretation
`Europe/Moscow`, collections `[]`, absent singleton values `null`.

- `GET /api/v1/student/session`: `user{id,displayName}`, `activeRole`,
  `group{id,name}|null`, `semester{id,name}|null`, `capabilities`, `serverNow`.
- `GET /api/v1/student/today`: `date`, `timeZone`, `serverNow`, ordered `lessons[]`;
  each lesson carries schedule fields, attendance provenance, `checkinEligibility`
  with machine reason, and current automatic request/cooldown projection.
- `GET /api/v1/student/schedule?semesterId={id}`: server verifies the requested
  semester belongs to the authenticated student's group/scope and returns the
  bounded semester range, `updatedAt`, and ordered materialized `lessons[]`. The
  current schedule service materializes recurring lessons through semester end and
  already exposes `GetLessonsByGroup(group,semester,dateFrom,dateTo)`; the BFF does
  not synthesize missing future lessons.
- `POST /api/v1/student/lessons/{lessonId}/checkin` requires `Idempotency-Key`.
  Body geo is discriminated: `COORDINATES{latitude,longitude}` or
  `UNAVAILABLE{reason:PERMISSION_DENIED|POSITION_UNAVAILABLE|TIMEOUT}`. Never send
  zero coordinates as an unavailable sentinel.
- POST returns 200 ACK: `outcome=PRESENT|PENDING_CONFIRMATION`, `lessonId`,
  `attendance|null`, `request|null`, `retryAt|null`, `serverNow`, `_links`.
  Request projection is `id,status,origin=AUTO_GEO_FAILURE,resolutionReason|null`.
  Geo failure with a persisted automatic request is an ACK, not 422.

No endpoint is paginated in r1: one server-validated semester is the explicit upper
bound. OpenAPI `info.version` is `JS-STUDENT-01-r1`. Session and Today are
`Cache-Control: no-store` with no ETag because both include transactional/live
projection and `serverNow`. Semester schedule is `Cache-Control: private, no-cache`;
its ETag validates the stable authorized representation and supports
`If-None-Match`/304 after authentication. The ETag is not a mutation precondition.
POST has no ETag; the idempotency receipt is its replay contract.

Problem Details uses `application/problem+json` with standard fields plus stable
`code`; 400 invalid body/key, 401 invalid/expired session, 403 wrong role/scope,
404 unknown lesson, 409 manual headman absence/payload mismatch/ineligible state,
429 cooldown with `retryAt` and `Retry-After`, 503 mandatory dependency unavailable.

### Domain and concurrency

1. Load by path lesson ID; verify student group and inclusive server-time window.
   Do not use an “active lesson” lookup that omits the post-lesson five-minute tail.
2. Persist a 300-second cooldown per `(student, lesson)` from accepted server time.
   `now >= retryAt` is allowed. Auth/validation rejection or rollback consumes none.
3. PENDING retry before boundary returns 429. At/after boundary, failed geo reuses
   the same request ID and emits no second requested event. A failed retry after
   REJECTED creates a new request ID.
4. Successful retry writes PRESENT and atomically closes PENDING as
   `CANCELLED/GEO_CONFIRMED`. It never fabricates headman approval; stale decision
   cannot alter attendance.
5. Enforce a partial unique PENDING index and a durable pair coordination record.
   Geo, headman decision and manual HEADMAN marking share one transaction/CAS rule.
6. Existing manual HEADMAN ABSENT is rejected before geo provider/command and
   points to the separate appeal route. A race must recheck after conflict.
7. Attendance/request/cooldown/idempotency receipt/outbox commit in one Mongo
   transaction. Same key+payload replays the original ACK; changed payload is 409.
8. Inject `Clock`. Geo provenance is `STUDENT_GEO`, `markedBy=null`; approved late
   check-in is `LATE_CHECKIN` with the real internal actor. Preserve lesson metadata.
9. Automatic requests do not expire and do not consume the five-per-semester appeal
   limit. A headman student cannot self-escalate; return journal-path eligibility.
10. A confirmed PRESENT row is disabled with `ALREADY_PRESENT`. A new check-in key
   for the same PRESENT lesson is a mutation-free 200 PRESENT ACK with the current
   attendance; the original key still replays its original ACK. A cancelled lesson
   remains visible when returned by schedule, is disabled with `LESSON_CANCELLED`,
   and a POST attempt returns 409 `CHECKIN_NOT_ELIGIBLE`.

### Trust and auth bootstrap

Gateway → BFF → attendance validates signed JWT at each new boundary (signature,
issuer, audience, expiry, student/group scope). Client `X-User-*` headers are never
identity, including local/test profiles. PWA reuses memory access token plus the
HttpOnly Secure SameSite=Strict `rct_refresh` cookie at `/api/auth/refresh`. TMA
reuses server-validated `POST /api/auth/tma` signed init data and memory-only tokens;
401 reauthenticates through init data. No new role system is introduced.

### Consumer closure

All consumers of late-check-in status must accept `CANCELLED` as terminal and
`GEO_CONFIRMED` as its resolution. A late Telegram/web decision on a cancelled
request is a no-effect terminal response. Do not publish a false approved decision;
notification/history projections stop showing the action and may show resolved by
geo. Exhaustive enum switches and contract tests must identify every consumer.

### Offline boundary

PWA persists the server-scoped semester schedule and other stable read data in one
IndexedDB/query owner partitioned by user. Today composes its schedule rows from that
snapshot offline, shows an explicit offline badge and `updatedAt`, and never treats
cached eligibility/attendance as fresh. Check-in and every mutation are disabled
offline and never queued. Cached schedule remains visible after token/session expiry
until explicit logout or account switch, which clears the partition; tokens are not
cached. TMA remains online-only. Statistics are allowed by the owner under the same
offline-label rule, but no new statistics UI/API is added by JS-STUDENT-01.

## 5. Constraints

Preserve transaction/outbox/dedup/authz invariants. One contract owner; no parallel
manual OpenAPI/DTO source. No secrets in evidence. No production mutation, Figma
write, push, deletion or unrelated refactor. Local commits/branches/merges are
authorized, with Git mutation coordinated by root; no remote push. Vue strict TS,
PCSS/rem, one query owner, no optimistic count or offline mutation queue.

## 6. Existing patterns

Existing `*-api-contract` Java interfaces/DTOs feed `OpenApiSnapshotIT`, committed
`docs/openapi/*.json`, then `openapi-typescript` through
`npm run generate:types:offline`. Existing PWA uses cookie refresh single-flight;
TMA validates Telegram init data server-side. Existing outbox envelopes/delivery
dedup remain; repeating PENDING adds no event type. Packet SVG assets are reused;
React Phosphor components are not copied into Vue. Onest bytes are currently absent
and must be supplied as a traceable asset or an explicit fallback before visual DONE.

## 7. Acceptance criteria

- Successful geo and outside/unavailable geo produce the correct atomic ACK.
- PENDING and REJECTED retry at 299.999s/300s and both window boundaries are tested.
- Concurrent same/different keys yield one active request and one initial notice;
  replay after network timeout returns the original ACK.
- Geo success races approve/reject/manual HEADMAN ABSENT without stale overwrite;
  provenance and actor are correct.
- Invalid/expired JWT, spoofed headers, foreign lesson/group and headman self-request
  fail at the expected boundary.
- Outbox failure rolls back; later broker delivery is deduplicated.
- OpenAPI export, TS generation, fixtures and actual responses conform to one schema.
- FE checks and browser flows cover both shells; real TMA remains a separate runtime
  gate. BE uses Mongo replica-set integration and real HTTP→gRPC runtime.
- Stable combined diff receives fresh Sol high review with no unresolved critical
  finding.

## 8. Verification

Use Gradle wrapper/CI tasks, OpenAPI snapshot update then non-update drift check,
offline TS generation with a no-diff second pass, schema fixture validation, unit
and Mongo integration race tests. Use task-owned ports/data. Record revision,
command, exit code, environment and evidence in `checks.json`. WARN/ERROR changes
code only after a request link and reproduction.

## 9. Do not

Do not implement absence forms, past-lesson appeals, the full auth/role subsystem,
desktop/web, broad legacy retirement, general offline schedule/JS10, or fake
backend/mocks presented as integration. Do not let BFF calculate geofence, window,
cooldown or final eligibility.
