# Resume 4 repair pre-state

- Recorded UTC: 2026-09-08T19:52:51.3486217Z
- Revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`
- CWD: `C:\Users\maksd\.codex\worktrees\1456\rutcampustrack\.agent\worktrees\profile-ui`
- Product path: `frontends/mobile-core/src/features/profile/SecurityScreen.vue`
- Source bytes: `10943`
- Source SHA256: `AB039523841BEE93E78ABCA535DEAB7400DFA36003E1102D83F782911E2B659E`
- The six non-owned profile SFCs matched the prior manifest bytes and hashes.

## Independently confirmed defect

The source has an immediate clear only when the watched error code enters
`ACCOUNT_INVALIDATED` (lines 64–69). The submit guard and submit button already
block invalidation (lines 93 and 302), but the three password inputs (lines
174, 210, 238) and their three `.profile-field__toggle` buttons (lines 185,
221, 249) have no invalidation disabled binding. The watcher has no
`ACCOUNT_INVALIDATED` previous-code handling, so removing the external code
does not clear state entered or revealed while the error is active.

This matches the supplied mounted reproduction: all three fields and the
current-password reveal remained enabled during external
`ACCOUNT_INVALIDATED`; after synthetic entry and reveal, clearing the external
error left the form populated/revealed and submit-ready. Password values are
intentionally absent from this record.

## Scope and checks before edit

- Authorized product edit: `SecurityScreen.vue` only.
- Evidence edit: this file under `.agent/profile-ui/evidence/resume4-repair/`.
- Runtime/server/browser: not run by this leaf; root owns post-repair mounted QA.
- Existing dirty worktree contents were preserved; no reset, clean, commit, or
  revert was used.
