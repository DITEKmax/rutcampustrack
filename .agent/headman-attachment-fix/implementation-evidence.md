# Headman attachment content negotiation fix

Date: 2026-09-22
Baseline: `e4d0c152d22f968c81de2c6a7feb5fb76d43634f`
Status: scoped source correction and targeted checks PASS; ready for Sol
review. No deploy or live service acceptance is claimed.

## Scope and contract

The headman attachment download must return stored bytes in the stored MIME
type in PWA and TMA. JSON request endpoints keep their existing
`Accept: application/json` default. Attachment requests must negotiate binary
responses while preserving bearer auth, generation assertions, and the existing
single 401 refresh/retry.

## Defect and correction

`HeadmanRequestsApi.downloadAttachment` previously called the shared
`response()` helper without headers. That helper therefore assigned
`Accept: application/json`, while `HeadmanRequestApi.downloadAttachment`
declares `produces = application/octet-stream`. The known diagnostic
`62981/run190057` observed the distinct-owner attachment request as HTTP 500;
the source path explains the negotiation mismatch, and the shared catch-all
handled the unrecognised not-acceptable exception as generic 500.

The client now passes `Accept: */*` only for the attachment request. The server
can consequently return the actual stored MIME from the controller while the
existing `Authorization`, generation guard, and 401 retry path remain in the
same `response()` helper. `GlobalExceptionHandler` now maps an incompatible
Accept header to a deliberate 406 RFC problem response instead of generic 500.

## Exact inventory

Modified:

- `frontends/mobile-core/src/features/headman-requests/headman-requests-client.ts`
  — binary-only Accept override for `downloadAttachment`.
- `services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/exception/GlobalExceptionHandler.java`
  — explicit `HttpMediaTypeNotAcceptableException` → 406 mapping.

Added:

- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/HeadmanRequestControllerMvcTest.java`
  — bounded Spring MVC HTTP regression for incompatible JSON Accept and a
  binary response with stored MIME/filename/bytes.
- `.agent/headman-attachment-fix/implementation-evidence.md` — this evidence.

Deleted: none.

## Checks

- Frontend: from `frontends`,
  `npm run typecheck --workspace @rct/mobile-core` — exit 0.
- Backend: native Gradle handle `81538`,
  `.:gradlew.bat :services:attendance-service:attendance-app:test
  --tests ru.rutcampustrack.attendance.studentrequest.HeadmanRequestControllerMvcTest
  --no-daemon --no-parallel --max-workers=1 --no-problems-report` — exit 0.
  Test result XML reports `tests=1`, `skipped=0`, `failures=0`, `errors=0`.

The HTTP test uses the real Spring MVC request mapping, produces negotiation,
controller response headers, and response bytes. `Accept: application/json`
returns 406 with `application/problem+json`; `Accept: */*` returns 200 with
`application/pdf`, attachment filename `report.pdf`, and the exact four-byte
payload. The service is stubbed only to provide a deterministic stored payload;
the assertion is on the HTTP result, not a mock invocation.

No full suite, aggregate backend build, Docker run, or production gateway check
was started. Foreign WIP and the prior packaging commit remain untouched.
