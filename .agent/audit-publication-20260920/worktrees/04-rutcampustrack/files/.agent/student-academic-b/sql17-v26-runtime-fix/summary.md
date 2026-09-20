# Correction summary

Status: `SOURCE_READY_WAIT_HEAVY_LEASE`; product scope remains `RELEASED`.

The reproduced V26 function-resolution defect is corrected by deterministic
public extension placement and qualified `public.digest` calls. The reproduced
pre-fix race now uses a test-only deferred post-check advisory phase barrier;
the unsafe proof remains default READ COMMITTED with exact two commits and zero
remaining rows. The corrected production race remains unbarriered with exact
one-commit/one-expected-failure behavior.

Static checks and evidence readback passed with exit code `0` as recorded in
`checks.md`. The accepted V26 alias correction is captured in the immutable
`source-ready-2-2026-09-10.md` artifact after the exact-four source guard and
root readback passed. The prior exclusive session `85479` remains recorded as
two V26 tests reproducing SQLSTATE `42702` from the ambiguous
`plan_version_id` reference in `validate_campus_map_ready_asset()` line 30.
The heavy lease is `RELEASED`; no new runtime or Gradle command was run in this
follow-up. Runtime validation waits for the root heavy lease.
