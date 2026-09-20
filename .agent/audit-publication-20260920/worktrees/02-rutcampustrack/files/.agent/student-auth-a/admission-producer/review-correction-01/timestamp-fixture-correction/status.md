# Status

`SOURCE_READY / RELEASE`

- Scope: bounded to `InternalSessionAdmissionIT.java` plus this new evidence
  directory.
- Criteria: static structure, timestamp source, direct SQL invariant, stale
  pattern absence, scope and hash guards recorded as PASS.
- Runtime: `NOT RUN / root-owned` by explicit task constraint.
- Review gate: source is ready for root's exact PostgreSQL rerun and independent
  recheck; no unresolved critical finding was introduced.
- Repository hygiene: foreign dirty work preserved; no stage, commit, reset,
  revert, delete or secret access.
