**FAIL — S3 numeric slice.**

1. **MEDIUM — `CANCELLED` mark is accepted for included lifecycle states.**  
   `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/AttendanceMetricCalculator.java:304-308`

   **Evidence:** `PLANNED` and `ACTIVE` share a branch that rejects only `FREE_ATTENDANCE`, so `AttendanceStatus.CANCELLED` is accepted and the occurrence is added to `plannedCount`. `contract.md:48` requires malformed lifecycle/mark combinations to fail. Existing domain evidence says `CANCELLED` is system-only (`MarkingService.java:62,94,201`) and cancellation writes that status (`LessonEventService.java:138`). A persisted `PRESENT`, `ABSENT`, or `EXCUSED` mark may be retained for either `PLANNED` or `ACTIVE` and remains outside `H`; this review does not infer a date or forbid those marks.

   **Impact:** cancelled/inconsistent data with an included lifecycle is silently represented as a planned occurrence instead of producing the typed integrity error, overstating `plannedCount` and any downstream future/status-strip projection.

   **Reproduction:** call `AttendanceMetricCalculator.calculate(List.of(new Occurrence(1L, OccurrenceState.ACTIVE, AttendanceStatus.CANCELLED)))` or the same with `PLANNED`. Current result has `plannedCount=1`, `heldCount=0`; expected `StudentProjectionException.Kind.INVALID_OCCURRENCE`.

2. **LOW — verification evidence is internally stale.**  
   `.agent/student-academic-b/checks.json:67,96`; `.agent/student-academic-b/summary.md:34`

   **Evidence:** `checks.json` says nine manifest entries and ten owned files; the current manifest has 13 entries and the Git-visible scoped set has 13 files after XML snapshots were added. Mutable Gradle XML contains 19 passing tests: metric 8, rank 8, isolation 1, and two architecture tests; the summary reports only 17.

   **Impact:** the recorded check inventory cannot be audited against the frozen manifest without reconstructing which files and suites were present.

   **Reproduction:** count nonblank lines in `manifest.sha256`, compare with `checks.json:67`, then inspect the five XML suites under `services/attendance-service/attendance-app/build/test-results/test`.

**Repair contract**

- **Defect:** incomplete lifecycle/mark validation for `CANCELLED` on included states.
- **Evidence:** production branch at lines 304–308 and the reproduction above.
- **Correction:** use the explicit matrix: `PLANNED`/`ACTIVE` accept `null`, `PRESENT`, `ABSENT`, or `EXCUSED` and ignore the mark outside `H`; both reject `CANCELLED` as `INVALID_OCCURRENCE`; `CLOSED` keeps current handling; `CANCELLED`/`TRANSFERRED_OUT` retain any non-FREE invalidated mark and remain excluded; `FREE_ATTENDANCE` always yields `UNSUPPORTED_ATTENDANCE_STATUS`.
- **Scope:** calculator and its matching focused test only; evidence writer may then correct stale evidence prose/copies and refresh hashes. Do not change MarkingService or expand the foundation schema.
- **Verification:** add observable tests for `ACTIVE+CANCELLED` and `PLANNED+CANCELLED`, retain positive tests for included `PLANNED`/`ACTIVE` persisted P/A/E marks, rerun the same three Gradle selectors with `--no-parallel --max-workers=1`, record every actually executed XML suite, validate the refreshed manifest, then require a fresh independent recheck of the changed calculator/test/evidence.

The remaining numerical gates pass: four `0..100` scale-2 `HALF_UP` metrics, `H=0 → null`, missing-closed diagnostic projection, duplicate physical-ID rejection, exact `BigInteger` competition rank, authoritative roster preservation, unavailable own-rank rules, no peer payload, pure dependency boundary, and no write/data-loss surface. Production hashes matched the supplied frozen values before author changes: calculator `b3825952eeb00cecbd76d71160a77d0b404bd22fbd5babdd3014547682364098`, rank `78ce5a5949c673e303adfa13fbde6bbf3c4d0bafa4263e5b6cf73499a3eaab15`, exception `2a7929e7a48234b7bcca956822fb6e59f61d1676ac6c26f377bf4405baca1261`. Product runtime `N/A` is appropriate for this pure scope. The independently opened originals were `statistics-root-decision.md:9-17`, `statistics-decision-result.md:44-61`, `backend-conflicts.md:127-132` (R4), and `backend-conflicts.md:431-446` (R28). Production disconnection remains an explicit integration gate and is not a defect in this bounded foundation.

**Independent recheck is required after repair.**
