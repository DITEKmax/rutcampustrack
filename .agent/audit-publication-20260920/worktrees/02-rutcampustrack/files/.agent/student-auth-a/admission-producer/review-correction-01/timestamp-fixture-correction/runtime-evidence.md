# Runtime evidence

Status: `NOT RUN / root-owned`.

The request explicitly forbids Gradle, Docker and runtime execution in this
bounded leaf. Therefore this directory claims no post-fix PostgreSQL pass and
does not replace runtime evidence with static checks.

The immutable prior failure is
`../runtime-final/failure-05-postgres-it.md`: exact command was the focused
`InternalSessionAdmissionIT` PostgreSQL integration run; exit code 1; 6 tests,
5 passed, 1 failed. The failing HTTP suspended fixture expected 403 and got
503. Its XML SHA-256 is
`E6942C019856054243E3CF1064DD5BDC9AD17AAD514A62CD5A6A70D4B1FF64C2`, 12590
bytes.

The correction is source-ready for root's exact PostgreSQL rerun and
independent recheck. Root should verify the full six-case mapping and the
persisted invariant against the real `JdbcSessionAuthority` after accepting
this source handoff.
