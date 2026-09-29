# Assistant homework and permissions delivery

## Goal

Publish homework for the exact selected lesson, reuse an immutable request after an uncertain create, and refresh assistant permissions without invalidating a valid student session.

## Context and evidence

- Canonical orchestration rules: `.agent/orchestration-v2/RULES.md`, SHA-256 `F4986A1834A9175ADBB7DCB3C49483168111A0B60C7EB13ACDA9FBF9D74927F8`.
- Frozen product context: `.agent/orchestration-v2/evidence/2026-09-27-delivery/assistant-homework-context-contract.md` from the main checkout, as supplied in the task packet.
- Writer checkout: `codex/assistant-complete-20260929`, based on `0f331540eab744bfe460fc7fa4480e12383e8edc`.
- Existing patterns reused: generation-bound API clients and current-owner keys in PWA/TMA; `HeadmanGroupApi.listMyPermissions()`; existing homework create input and request key.

## Relevant scope

- `frontends/mobile-core/src/features/homework/AssistantHomeworkScreen.vue`
- `frontends/mobile-core/src/features/homework/assistant-homework-create-intent.ts`
- `frontends/mobile-core/src/features/homework/assistant-homework-create-intent.test.ts`
- `frontends/mobile-core/src/features/headman-group/AssistantActionsScreen.vue`
- `frontends/mobile-core/src/features/headman-group/assistant-permission-refresh.ts`
- `frontends/mobile-core/src/features/headman-group/assistant-permission-refresh.test.ts`
- `frontends/mobile-core/src/index.ts`
- `frontends/pwa-vue/src/App.vue`
- `frontends/tma-vue/src/App.vue`

## Required behavior

- Freeze create payload and lesson/account/group/date/semester context when opening a draft; reject a save if the selected context no longer matches.
- Preserve the same payload object and request key for uncertain retries; allow editing after 400/422; preserve and visibly report the conflict after 409.
- Clear stale data and draft on owner, group, API, mode, date, semester, or selected lesson changes.
- Treat assistant capability 403 as permission refresh, not session loss. Apply results only to the same auth generation and owner; coalesce requests and limit foreground refreshes.
- Hide an assistant surface after its permission is revoked while keeping remaining capabilities available.

## Constraints

- No backend, generated files, lockfiles, API endpoint, or visual redesign changes.
- No full suite or standalone PWA/TMA build/runtime; combined build and app runtime are held by root.
- Preserve the three foreign untracked `.agent/evidence` folders in this worktree.

## Existing patterns

- PWA and TMA capture an authenticated session generation and use generation-bound API clients. Both screen trees are keyed by the current owner identity.
- Assistant permissions are read through the existing `listMyPermissions()` API and passed to the shared `AssistantActionsScreen`.
- Homework uses the existing create API, including its optional `requestKey` field.

## Acceptance criteria

- A retry after a lost response calls create again with the identical frozen input and request key; it cannot reuse lesson A's intent for lesson B.
- A permission 403 refreshes capabilities without logging out; revoking `MANAGE_HOMEWORK` removes homework while `VIEW_STATS` remains available.
- A late permission response or error for a previous owner cannot change the current owner's permissions or session.
- Normal create success reloads homework through the existing list API.

## Verification

- `npm exec -- vitest run mobile-core/src/features/headman-group/assistant-permission-refresh.test.ts mobile-core/src/features/homework/assistant-homework-create-intent.test.ts` from `frontends/` — exit code 0; 2 test files, 6 tests passed.
- `git diff --check` — exit code 0 before evidence staging; final scoped staged diff check is recorded in the handoff.
- Runtime evidence: **not run**. Root owns the combined Vue build and runtime acceptance using the existing API with synthetic permission fixtures. Real Telegram lifecycle was not exercised here.

## Do not

- Do not add endpoints or mock permission APIs. Do not rotate the request key automatically after 409. Do not let stale owner callbacks mutate current state. Do not modify the foreign evidence folders.

## Diff and limitations

- The homework screen now snapshots the selected lesson context, locks uncertain/conflicting create payloads, and resets on context changes. Stale current-owner 403 responses still reach the app permission refresher without restoring an old draft.
- PWA/TMA now refresh permissions after relevant assistant 403 responses and when foregrounded, with generation/owner checks and refresh coalescing. Bootstrap permission-list 403 keeps the authenticated student view usable. `AssistantActionsScreen` closes only a surface whose permission disappeared.
- Server-side idempotency remains dependent on the existing API honoring `requestKey`; this change does not alter or verify backend behavior. Combined app build/runtime validation remains pending root's acceptance pass.
