# JS-STUDENT-01 backend evidence — FIX-r2

Date: 2026-09-06. Branch: `codex/js-student-01-fix-be`. Baseline:
`cedce8c60aee04261ca87a18b898b148719bee89`. Scope: attendance backend F4/F5 and
their targeted tests. Model/effort: `gpt-5.6-luna`, `max`.

## Original reproductions

F4 was reproduced by the added gRPC regression scenario before the production
mapping fix: Schedule returned `is_geo_blocked=false` and
`is_blocked_by_headman=true`; the old mapping passed `false` to the domain
service, so the command could continue. The same control covers coordinates and
unavailable geo input and also the teacher geo block variant.

F5 source inspection showed the legacy `CheckinService.checkin` performed rate
limit, schedule, geofence, Redis dedup, Mongo upsert and event publication. Its
upsert could replace a seeded `HEADMAN/ABSENT` record with
`PRESENT/STUDENT_GEO`. The endpoint had no retirement response.

## Correction evidence

- `AttendanceStudentGrpcServiceImpl` now maps the effective block with the OR of
  both schedule flags. The public proto and domain record shape are unchanged.
- `CheckinService` is a compatibility bean whose first operation throws
  `LegacyCheckinRetiredException`; no legacy dependency is called.
- Attendance `GlobalExceptionHandler` maps that exception to HTTP 410 with
  problem type `https://api.rutcampustrack.ru/problems/legacy-checkin-retired`.
- `CheckinApi` and `docs/openapi/attendance.json` describe the operation as
  deprecated and document 410. Canonical student check-in behavior remains in
  `StudentCheckinService`.
- The old event contract assertion was retained on the canonical
  `StudentCheckinTransactionIT` outbox path. The retired endpoint test asserts
  that it creates no event.

## Verification

1. Targeted unit test: 4 F4 parameter cases + 1 F5 service guard passed; Gradle
   exit 0.
2. F5 integration test: 1/1 passed with real HTTP, Mongo, Redis and Rabbit
   Testcontainers; Gradle exit 0. The seeded manual record kept its id,
   `ABSENT` status, `HEADMAN` source, `marked_by`, `created_at` and `updated_at`;
   attendance count, receipts, pair state, outbox and Redis remained unchanged.
3. Canonical `StudentCheckinTransactionIT`: 17/17 passed with elevated Docker
   ACL and `--rerun-tasks`; the changed real outbox path exercised the
   `attendance.marked` schema validator (Gradle exit 0).
4. `git diff --check` passed (exit 0).
5. OpenAPI JSON parse passed (exit 0).

The three committed frontend `attendance.types.ts` files still describe the
retired endpoint's old 201/403/404/409/422/429 responses and description, so
they are stale relative to this backend snapshot. The offline generator was
not run here because frontend `node_modules` are absent; generated clients are
outside this backend packet and are handed to the parent contract/FE owner.

The full attendance unit task compiled successfully but exited 1 because the
pre-existing `LateCheckinServiceTest.applyDecisionFromWeb_approveHappyPath_upsertsAttendanceAndPublishes`
failed at line 302 (`WantedButNotInvoked`). Running that class alone reproduced
the same 1 failure out of 19. No latecheckin source is in this diff, so it was
not changed.

The first sandbox Gradle attempt failed before test compilation because javac
could not read the existing `shared-web-api` project classes (49/53 import
errors); the elevated rerun compiled the same project dependency and reached
the tests. This is an environment ACL limitation, not a product correction.
