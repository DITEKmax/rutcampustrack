# Defect reproduction before correction — 2026-09-08

- revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2` (nested detached HEAD)
- command: `node C:\Users\maksd\.codex\worktrees\d650\rutcampustrack\frontends\node_modules\vitest\vitest.mjs run frontends/mobile-core/src/features/profile/profile-state.test.ts`
- environment: Windows PowerShell, Node/npm runtime from verified read-only D650 `frontends/node_modules`, Vitest `4.1.4`
- exit code: `1`
- result: `18 tests | 2 failed`, existing 16 tests remained green

## Reproduction

With a ready snapshot and a pending `listHistory()` request, start a newer
`loadSnapshot()` request and reject it with `AUTHORITY_UNAVAILABLE`. The history
response resolves, but `ProfileState.view.historyStatus` remains `loading` and
the returned item is not applied. The current `loadSnapshot()` increments the
same generation used by dependent reads before the snapshot has established a
new authority; a failed refresh therefore discards the still-valid history
request.

With the same setup, resolve the newer snapshot with an equivalent authority.
The response has the same account, session, session version, roles version and
active role, yet `loadSnapshot()` increments the dependent-read generation and
resets history while it is loading. The pending history response is ignored and
the status remains `idle`.

Observed failing assertions:

- `profile-state.test.ts:107`: expected history status `ready`, received `loading`.
- `profile-state.test.ts:131`: expected history status `ready`, received `idle`.

Impact: independent profile sections can remain permanently busy or lose a
valid response after a retry of the snapshot. Correction is bounded to the
owned state module and its behavioral tests: use separate snapshot-request
ordering from authority generation, invalidate dependents only after an
accepted authority change, and retain one bounded automatic refresh for
`SESSION_STATE_STALE` without recursive request storms.
