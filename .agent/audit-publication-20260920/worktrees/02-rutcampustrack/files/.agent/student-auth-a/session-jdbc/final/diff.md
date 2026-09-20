# Diff and limitations

## Intended diff

The source diff is limited to two new Java files plus the accepted Academic V24 SQL source and this evidence directory. The adapter contains the JDBC implementation; the IT is a real PostgreSQL/Testcontainers verification source. No existing foreign modifications were reverted, staged, reformatted, or incorporated.

Recorded SHA-256 values:

- `JdbcSessionAuthority.java`: `05B2FE5045CF8AA9CCAF90010D31726A311E85C5633892EAF1F233E6D8231B48`
- `JdbcSessionAuthorityIT.java`: `0E02899F1467E284A318E96583B2B77F902F9DF98F98233EE09671997649A933`
- accepted `V24__auth_session_authority.sql`: `1D15418FA4869D1288B3FA25F688A237087294F360AEA6729F524DE2F5B67F90` (7618 bytes)

## Recorded correction

Root review reproduced a boundary defect where a current but expired session could enter password change because the gate checked only `revoked_at`. The correction changes the gate to the existing `!isLive(now)` predicate and adds the expired-at-now test. The test verifies no password hash/flags, session rows, or event rows change.

Correction-02 records the parent compile reproduction: exit `1` at `:services:auth-service:auth-app:compileJava` with 36 adapter errors. The minimal source fix qualifies every session result with `SessionStatePort.FailureCode`, retains explicit credential failure qualification, and changes static JDBC RowMapper references from `this::map*` to `JdbcSessionAuthority::map*`. No IT/V24 change was needed.

Correction-03 records the parent real-PG reproduction: compileJava and compileTestJava passed, then 11 tests failed because shared `@BeforeEach cleanOwnData` line 96 attempted a forbidden append-only event delete (P0001). The minimal IT fix removes that method and its unused `BeforeEach` import. Fresh `reuse=false` state, unique `userSequence`, and user-scoped assertions make cleanup unnecessary; adapter and V24 are unchanged.

Correction-04 records the independent Sol high review findings: MEDIUM missing rollback coverage after the final revoke/password events and after `password.sessions`; MEDIUM missing stale authoritative grant/version admission and deterministic snapshot-versus-grant-writer lock-order coverage; LOW stale runtime status after changing the IT. The bounded IT correction adds four fault-injection tests, stale grant/version rejection, and a barrier-synchronized `TransactionTemplate` writer with explicit user `FOR UPDATE` before grant update. Adapter and V24 remain byte-identical.

Correction-04b records the follow-up gate that rejected barrier-only overlap. The race now holds the user lock and signals `writerLockHeld`, starts the snapshot while held, observes a distinct active `pg_stat_activity` backend waiting on `Lock` with `pg_blocking_pids`, then releases the writer. The writer commits the suspended grant and the snapshot requires the wholly-new tuple (`rolesVersion+1`, `sessionVersion+1`, suspended grant, null active role). New IT SHA: `0E02899F1467E284A318E96583B2B77F902F9DF98F98233EE09671997649A933`.

## Limitations and handoff

The parent exact post-correction-04b runtime for IT SHA `0E02899F1467E284A318E96583B2B77F902F9DF98F98233EE09671997649A933` exited `0` with BUILD SUCCESSFUL in `1m15s`; JUnit reports 18 tests, 0 failures, 0 errors, 0 skipped, SHA256 `FBFD63D70E69BA91458CA14207E8987CD65A09384864B408EA23990CA31D6354`, `10769` bytes. The source XML and `final/junit/` copy are byte-exact. Cleanup recorded ports `18100-18119` free, no Java/Gradle process, test PostgreSQL/Ryuk absent, and `RELEASE` at `2026-09-10T00:39:38.6298813+03:00`.

The prior 12/0 runtime report applies to the pre-correction-04 IT SHA `BC689BF4265EF4B3247559A88D8A417300EBF49EFDF2BCD80ACCCD2771362FA3`; its original XML is retained only as historical provenance in the prior record. Historical compile/PG failures remain provenance only.
