# Correction-02 runtime evidence

Environment: Windows PowerShell on `DITEK-PK`, shared checkout, Java 21 and
Gradle wrapper. The affected selector used task-owned PostgreSQL and Redis
Testcontainers. Session `65967` completed the behavior run with exit 0;
session `67640` was already started before the fast-path no-rerun instruction
and completed the same affected selectors with exit 0, producing the saved
JUnit XML. No further rerun is performed.

Observable runtime cases covered by the saved suites:

- Cookie-only logout revokes the refresh session, clears the cookie, removes
  the associated WS ticket and makes internal ticket consume return 404.
- Concurrent direct and by-code verification of one OTP produces exactly one
  successful response, one unauthorized response and one new DB session.
- Redis cleanup failures after successful durable logout/password operations
  preserve 204 and cookie clearing in the focused controller tests.
- Repository dependency failures are surfaced as typed
  `AUTHORITY_UNAVAILABLE` in focused service tests.

Root-observed Docker/process state after the run is stopped/empty; it is not
re-probed during evidence finalization.

