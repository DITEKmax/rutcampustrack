# JS-HEADMAN-05/07/43/44 delivery evidence

Baseline: `fc7f649ce12e04e1c2bbe45cc096f0d97b68ae20`.  Risk: S3 because the
change crosses authenticated Mongo union reads, canonical attendance decisions,
pair locking and two session-bound frontends.

## Scope delivered

The source diff adds one authenticated `/attendance/requests` transport over
the existing excuse and late-check-in collections.  It provides server-side
pagination, kind/FIO/coverage-overlap filters, persisted lesson and attachment
detail, whole-ticket approve/reject, and a nullable late decision comment for
historical compatibility.  Decisions call `StudentRequestService`; attachment
bytes use the existing lifecycle and actor checks.

The Mongo union normalizes legacy `SUBMITTED` to `PENDING`, excludes `DRAFT`
from `OPEN`, sorts `OPEN` by `created_at` and `ARCHIVE` by `decision_at` with
stable kind/id tie-breakers, and computes coverage extrema with a typed
`$reduce` over snapshot dates.  From/to filters reject unknown extrema, so an
empty snapshot array cannot match a date range.

`AttendanceWritePort` preserves every current `PRESENT` row for
`HEADMAN_EXCUSE` and `LATE_CHECKIN` decisions in both port entry methods under
the existing pair lock.  Manual marking remains able to change attendance.
PWA and TMA use the shared generation-bound mobile client; PWA also treats
`HeadmanRequestsApiError(401)` as session invalidation.

## Exact product inventory

### Modified (M)

- `frontends/mobile-core/src/features/schedule/HeadmanScheduleScreen.vue`
- `frontends/mobile-core/src/index.ts`
- `frontends/pwa-vue/src/App.vue`
- `frontends/pwa-vue/src/auth.ts`
- `frontends/tma-vue/src/App.vue`
- `frontends/tma-vue/src/tma-session.ts`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/AttendanceWritePortImpl.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/entity/LateCheckinRequest.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseServiceApproveIT.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/student/StudentCheckinTransactionIT.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java`

### Added (A)

- `frontends/mobile-core/src/features/headman-requests/headman-requests-client.ts`
- `frontends/mobile-core/src/features/headman-requests/headman-requests-screen.pcss`
- `frontends/mobile-core/src/features/headman-requests/HeadmanRequestsScreen.vue`
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/HeadmanRequestApi.java`
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/headman/HeadmanRequestAttachmentResponse.java`
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/headman/HeadmanRequestDecision.java`
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/headman/HeadmanRequestDecisionRequest.java`
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/headman/HeadmanRequestDetailResponse.java`
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/headman/HeadmanRequestLessonResponse.java`
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/headman/HeadmanRequestPageResponse.java`
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/headman/HeadmanRequestSummaryResponse.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/HeadmanRequestController.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/HeadmanRequestService.java`

### Deleted (D)

- None in the assigned product scope.

### Own evidence committed with the package (A)

- `.agent/headman-requests-delivery/contract.md`
- `.agent/headman-requests-delivery/checks.json`
- `.agent/headman-requests-delivery/runtime-evidence.md`
- `.agent/headman-requests-delivery/summary.md`
- `.agent/headman-requests-delivery/scoped-it-16442-cases.json` (root-authored case inventory)

## Acceptance and evidence status

- Source review: Sol targeted review passed the semantic date, status, sort,
  auth/session and PRESENT corrections.  The later parentheses-only rewrite
  compiled in the direct integration run and did not alter the rule.
- Backend compile and Mongo integration: session `16442` compiled and ran 27
  selected IT cases; 26 passed and one existing pair-race test reached a
  labelled transient Mongo write conflict after its four immediate retries.
  The permitted test-only 100/200/400 ms backoff was then verified by session
  `49505`, which ran that race test to `exit 0`.
- The 16442 XML recorded PASS for the targeted excuse PRESENT/timestamp case,
  the journal attachment/PRESENT case, all 25 `StudentRequestDomainIT` cases
  except that transient race, including OPEN status, coverage from/to/gap/
  empty and archive decision-time assertions.
- `mobile-core`, PWA and TMA typechecks all passed with exit code 0.  The PWA
  result was obtained after the `HeadmanRequestsApiError(401)` invalidation
  addition.
- Live d6 route/runtime acceptance remains pending root: source Mongo tests
  prove the server projection and decision rules, but they do not claim live
  `/api/attendance/requests` behavior, browser session wiring, or Student
  refetch against the integrated runtime.

## Limits

No full-suite campaign, dependency update, push, deploy, production data
operation or foreign WIP change was made.  The runtime evidence file records
the remaining live endpoint and UI acceptance criteria.
