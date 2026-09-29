# Assistant permission repair — evidence

## Goal

После операционного 403 открытый экран помощника получает свежий список прав: отозванные capabilities исчезают, сохранённые права остаются, действующая сессия студента не завершается. Ошибка статистики также доходит до уже подключённого shell handler.

## Context and evidence

- Risk: S2; bounded correction in the assigned worktree `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/headman-assistants-delivery-20260922`, branch `codex/assistant-complete-20260929`.
- Baseline: `dc1e9b071b24826716d968be24c81613b5d4578c`.
- Canonical rules: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md`, SHA-256 `F4986A1834A9175ADBB7DCB3C49483168111A0B60C7EB13ACDA9FBF9D74927F8`.
- Contract: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/evidence/2026-09-27-delivery/assistant-permission-repair-contract.md`.
- The deferred race reproduced before the implementation change: the post-403 call reused the pending foreground request, so the test expected 2 permission loads and observed 1 (Vitest exit 1; 3 other tests passed).
- Stats control-flow reproduction: the nested trend, detail and trend-export 403 catches called `clearOnDenied()` and returned before `emit('error', cause)`; ordinary export 403 cleared only the main response. `HeadmanStatsScreen` already declares `error`, and `AssistantActionsScreen` already forwards it through `reportError` to its parent.

## Relevant scope

- `frontends/mobile-core/src/features/headman-group/assistant-permission-refresh.ts`
- `frontends/mobile-core/src/features/headman-group/assistant-permission-refresh.test.ts`
- `frontends/mobile-core/src/features/headman-stats/HeadmanStatsScreen.vue`
- This evidence file.
- `checks.json` in this task's evidence directory records commands, exit codes, environment and runtime status.

## Required behavior

- A `forbidden` refresh starts a new read after the denial, bypasses cooldown, and invalidates older in-flight responses for permission application and error propagation.
- A safe foreground request may coalesce with the active read. Owner and generation guards remain active.
- Stats trend, detail, main export and trend export 403 paths clear inaccessible local data, then emit the error through the existing shell path.
- Preserve 401/session handling and assistant homework retry identity.

## Constraints

- Only the three listed implementation/test files are changed; no backend, API, visual design, shell, runtime or unrelated evidence changes.
- No child agents, push, deployment or main-worktree writes. Three pre-existing untracked evidence directories remain untouched and unstaged.
- The tracked shared `.agent/checks.json` belongs to `admin-group-promotion`; it remains unchanged. This task's checks are recorded in its own evidence directory.

## Existing patterns

- Keep the refresher's owner/generation checks and foreground cooldown.
- Reuse the existing `clearOnDenied()` invalidation and `emit('error', cause)` shell route.
- Keep and strengthen the existing deferred ownership test; retain the existing homework intent retry test unchanged.

## Acceptance criteria

1. A pending foreground read with `MANAGE_HOMEWORK` cannot apply after a 403; a subsequent authoritative read applies `VIEW_STATS`, retaining the valid right and removing the revoked right.
2. Foreground calls coalesce safely; a response from a no-longer-current owner cannot apply; a new generation can still refresh.
3. Every stats 403 path clears inaccessible data and emits the original error to the shell.
4. The two authorized Vitest files pass, including the homework retry identity assertions.

## Verification

| Check | Command / evidence | Exit |
|---|---|---:|
| Pre-fix race reproduction | From `frontends`: `npm exec -- vitest run mobile-core/src/features/headman-group/assistant-permission-refresh.test.ts`; expected 2 loads, observed 1. | 1 (reproduced) |
| Scoped Vitest checks | From `frontends`: `npm exec -- vitest run mobile-core/src/features/headman-group/assistant-permission-refresh.test.ts mobile-core/src/features/homework/assistant-homework-create-intent.test.ts`; 2 files, 6 tests passed. | 0 |
| Whitespace check | `git --no-optional-locks diff --check` | 0 |
| Evidence format | PowerShell `ConvertFrom-Json` on this task's `checks.json` | 0 |
| Staged whitespace check | `git --no-optional-locks diff --cached --check` | 0 |

Environment: Node `v24.14.0`, npm `11.9.0`, Vitest `4.1.4`, Windows worktree. Product/browser runtime: **N/A** for this bounded packet; no app or backend was started, and root owns grouped runtime acceptance.

The full command records and outcomes are in this directory's `checks.json`.

## Diff

- Refresher: forbidden reads bypass an older flight and cooldown; request IDs prevent older results/errors from overtaking the newest read.
- Deferred race test: covers stale foreground result, fresh retained permission, safe coalescing, owner change and generation refresh.
- Stats screen: 403 clears all inaccessible local stats and emits the same error on trend, detail and both export paths.
- Correction commit is the worktree HEAD at handoff; its full SHA is returned separately for independent Sol recheck.

## Limitations and do not

- No component/browser runtime, full build or broader suite was run. Independent Sol bounded recheck remains required.
- Do not treat this leaf evidence as runtime acceptance or integrate beyond the assigned correction without root's review flow.
