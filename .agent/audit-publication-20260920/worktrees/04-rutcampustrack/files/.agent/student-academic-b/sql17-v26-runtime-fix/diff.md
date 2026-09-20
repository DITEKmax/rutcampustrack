# Scoped diff

This correction delta is relative to the current prehashes from the recorded
Academic runtime failure. The files already contain the accepted V26/content
repair work; that foreign/preceding work is preserved in place.

## `V26__campus_map.sql`

- Changed the extension declaration to `CREATE EXTENSION IF NOT EXISTS
  pgcrypto WITH SCHEMA public;` so isolated schemas do not control extension
  placement.
- Qualified the asset content hash check as `public.digest(content,
  'sha256')`.

## `StudentFoundationMigrationIT.java`

- Qualified all three owned ad-hoc digest expressions with `public.digest`.
- Added explicit `preFixMode` to the shared race helper. The pre-fix call uses
  `true`; the corrected production race uses `false` and retains its natural
  parent-row serialization and SQLSTATE assertions.
- Added the test-only deferred AFTER DELETE barrier function and lexically
  later `subject_lesson_types_z_pre_fix_barrier_trg`.
- Added coordinator session lock acquisition, database-state polling for two
  ungranted `pg_locks` worker PIDs, and guaranteed session unlock before commit
  futures are joined. The barrier is absent from corrected mode.

No V17, Schedule source, V24/V25, Flyway IT, shared status/manifest,
generated/frontend/proto/build or reserved Auth API path was edited by this
correction.

## Evidence-only follow-up

Added `runtime-fail-2-2026-09-10.md` with the exclusive session `85479`, its
XML hash/bytes, the two SQLSTATE `42702` failures and exact-four source
posthash readback. Updated only the stage `manifest.json`, `evidence.md`,
`checks.md` and `summary.md` to mark latest runtime status
`FAILED_ACADEMIC_LEASE`; `scopeStatus` remains `RELEASED`. No product source
was changed.
