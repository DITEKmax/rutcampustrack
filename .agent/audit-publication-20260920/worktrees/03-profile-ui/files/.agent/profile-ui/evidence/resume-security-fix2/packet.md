# Security invalidation repair — compact packet

## 1. Goal

Close the verified mounted UI defect where an external `ACCOUNT_INVALIDATED`
error left password inputs populated and allowed the password callback.

## 2. Context/evidence

Base revision is `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. The pre-edit target
was 10437 bytes with SHA-256 `BCC12C11D21BD9F0F13544C5FBA5E6B34BD8FD5EAFED6D61D2E0D8208C5139AD`.
Mounted reproduction is recorded in `.agent/student-auth-a/profile/root-mounted-baseline.md`
and the nested preserved evidence; it observed retained entered values and a
callback count increase while the invalidating error persisted.

## 3. Relevant scope

Sole product target: `frontends/mobile-core/src/features/profile/SecurityScreen.vue`
inside the nested `profile-ui` checkout. Evidence target:
`.agent/profile-ui/evidence/resume-security-fix2/**`. No shared state, types,
styles, harness product logic, lockfiles or other worktrees are in scope.

## 4. Required behavior

When external `props.error?.code` is `ACCOUNT_INVALIDATED`, clear current/new/
repeat values, hide all reveal controls, reset `submitted` and local error,
preserve the external error, disable the button and reject submit. Removal of
the external error leaves an empty usable form. Current-password, policy,
network and authority errors retain values for retry. Success and unmount keep
clearing secrets. Password values are never logged or stored in evidence.

## 5. Constraints

No children, commits, deploys, backend/Gradle/Docker/runtime start, dependency
or lockfile changes, Figma writes, shell changes, or edits outside the nested
target and this evidence directory. Preserve all foreign work.

## 6. Existing patterns

The component uses Vue 3 `<script setup lang="ts">`, typed props and existing
`ProfileRequestError`/policy helpers. The source already clears secrets after a
successful callback and on unmount; the correction centralizes that cleanup in
one helper and adds a Vue `watch` with `immediate: true`.

## 7. Acceptance criteria

- Invalidation entry and transition clear all three fields and reveal state.
- Invalidation keeps the external error visible and blocks both click/submit
  paths while active.
- Clearing the external error exposes an empty, enabled form.
- Ordinary field/policy/network/authority errors preserve retry input.
- Strict Vue typecheck, focused lint, focused profile-state Vitest and the
  existing Vite API harness build pass; no unrelated files change.

## 8. Verification

Run from the nested checkout with exact command, exit code, UTC time, Node/tool
versions and captured output recorded in `checks.json`. Root owns the mounted
browser and will issue any runtime GO; this leaf does not start port 18110.

## 9. Do not

Do not redesign the screen, broaden the contract to local/non-external errors,
persist or print password values, edit sibling SFC callbacks, or claim full
PWA/TMA/backend/auth integration from this bounded correction.
