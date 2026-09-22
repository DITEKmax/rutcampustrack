# Runtime evidence — JS-HEADMAN-05/07/43/44

Status: **PENDING ROOT INTEGRATED RUNTIME ACCEPTANCE**.

The source checkout has actual server-side Mongo evidence. Direct native
session 16442 compiled the attendance app and executed 27 selected integration
cases: 26 passed and one existing pair-race helper exhausted four immediate
retries on Mongo code 112 (TransientTransactionError). After the permitted
test-only 100/200/400 ms transient backoff, direct native session 49505
reran exactly that race test and exited 0 with Docker/Mongo runtime success.
The targeted PRESENT preservation, attachment lifecycle, timestamp, union
status, coverage from/to/gap/empty, archive sort and context wiring cases are
therefore source-integrated evidence.

This checkout did not call the frozen d6 HTTP harness. The source checks do not
claim live /api/attendance/requests, browser session behavior, or Student
refetch acceptance.

Root/runtime still needs to exercise the real route for both request kinds and
buckets, type/FIO/coverage-overlap filters, exact headman/foreign-group
authorization, detail and attachment lifecycle, whole-ticket approve/reject
with persisted non-blank rejection reason, GET refetch plus Student read,
PRESENT preservation in old/controller and bot entry paths, and PWA/TMA stale
generation invalidation.
