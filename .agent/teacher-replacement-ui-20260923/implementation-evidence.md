# Implementation evidence — teacher replacement UI

## User result

In the shared PWA/TMA headman subjects screen, a headman can select a replacement teacher from the existing active-teacher search, enter the effective date, and explicitly submit the replacement. The UI saves the request key before sending. After reload, it resumes a known operation through the status endpoint or repeats the same intent with the same key when the response was lost. It reports success only after the server returns `COMMITTED` and the existing subjects/assignments query refreshes successfully.

## Contract and source evidence

- Frozen source in backend worktree: `services/academic-service/academic-api-contract/.../AssignmentApi.java`, `ReplaceAssignmentRequest.java`, `AssignmentReplacementResponse.java`, plus `academic-app/.../AssignmentController.java` and `AssignmentReplacementService.java`.
- The POST body is `{ replacementTeacherId: string, effectiveFrom: LocalDate, requestKey: UUID }`; replacement response is the plain DTO with operation/source/target IDs, subject/group/semester, lesson type, effective/validity dates, state, schedule receipt state, moved count, and skipped count.
- Source-model check confirmed `scheduleReceiptState` is nullable while the durable operation is `PREPARED` (the entity initializes it only after schedule application). The frontend normalizer keeps it nullable so a valid pending GET response is not rejected.
- The existing `GET /api/academic/users/teachers/search` returns only users with an active durable `TEACHER` grant (`UserApi` description and `UserService.searchActiveTeachers`), so the replacement picker uses the required active candidates without adding an API or role.
- The durable operation uses `PREPARED`, `APPLIED`, and `COMMITTED`. The client preserves unknown state strings. Only `COMMITTED` enters the reconciliation path; the list refresh uses the existing `load()` source for subjects, nested assignments, semesters, and teacher display names.
- Session ownership remains in the existing generation-bound `HeadmanSubjectsApi`; requests still run through `assertCurrent` around transport and unauthorized refresh. The screen also invalidates pending component callbacks when API, group, offline scope, or component lifetime changes.
- Durable browser records are now v2 and isolated by `ProfileSnapshot.userId`, group, and UUID `requestKey`. The shared `HeadmanScheduleScreen` passes `profile.userId`; existing PWA/TMA hosts already pass their authenticated profile snapshots, so no host app files changed. Session IDs, access tokens, and secrets are not stored.
- Restore scans only the current actor/group prefix and validates each record against its key. The previous group-only v1 key is neither read nor migrated. Parallel requests have distinct storage keys; storage events rescan the current scope, and completion conditionally deletes only the exact actor/group/requestKey record after validating its immutable intent fields.
- On identity change the screen invalidates API/load/mutation/search revisions, clears the old in-memory subjects and replacement intents, then restores only the new identity's records. Missing identity disables replacement restore, replay, and submit. A restored uncertain operation repeats POST with its stored `requestKey`; one with `operationId` resumes via GET.

## Changed product files

- Modified `frontends/mobile-core/src/features/headman-subjects/headman-subjects-client.ts`: typed request/response, POST and status GET methods, strict DTO normalization while preserving unknown lifecycle state strings.
- Modified `frontends/mobile-core/src/features/headman-subjects/HeadmanSubjectsScreen.vue`: assignment action, active teacher picker, date and confirmation, request key persistence/resume, explicit pending/error/conflict/403 states, and post-commit refetch. Offline and read-only paths stay disabled.
- Modified `frontends/mobile-core/src/features/headman-subjects/headman-subjects-screen.pcss`: token-based replacement panel, selected candidate state and touch-sized actions using existing theme/focus tokens.
- Modified `frontends/mobile-core/src/features/schedule/HeadmanScheduleScreen.vue`: passes the existing authenticated `profile.userId` into the shared subjects screen.

The exact product patch is [source.diff](source.diff). The shared screen is already used by the existing headman schedule flow; PWA/TMA `App.vue` bindings required no edits.

## Diff and repository state

- Base: `1bbb52b02f55ada16183b28107dc41158311f01e` on `codex/pwa-install-20260923`.
- Product delta against base: `HeadmanSubjectsScreen.vue` 737 added / 23 removed; client 84 / 0; PCSS 25 / 1; `HeadmanScheduleScreen.vue` 1 / 0. Exact patch: [source.diff](source.diff), SHA256 `4691A35CCEEE0699DD24BBDBE4530D0E603B183D4343A66B8696E077FF467DE8`.
- Dirty `.agent/orchestration-v2/RULES.md` and `LEAF-PACKET.md` were present before implementation and remain untouched. No commit, integration, push, or deploy was made.

## Verification

`checks.json` records exact commands, exit codes, package, and worktree. PWA host `vue-tsc` passed with exit code 0 after the identity/storage correction; it compiles the shared mobile-core source. Scoped `git diff --check` passed with exit code 0 (Git emitted only working-copy LF→CRLF notices). No TMA host plumbing, new tests, or broad suite was added/run.

## Bounded correction evidence

- **Actor switch:** `HeadmanScheduleScreen.vue` passes `profile?.userId`; the screen's scope watcher invalidates old request revisions and clears prior in-memory data. The v2 key prefix includes the encoded user ID, record validation compares it to the current ID, and all replay/persist helpers require the intent to match the current actor/group. Missing identity returns before restore and blocks submit/resume.
- **Two tabs, distinct intents:** each UUID is the final key segment beneath one actor/group prefix. Restore collects every valid matching record instead of one group slot. Completion reads, validates, and removes only the key derived from its own actor/group/requestKey. A storage event reloads only the matching current actor/group prefix; a different request key is unaffected.
- **Reload and same-key retry:** restore retains the record's `requestKey`; `resumeReplacement(intent)` sends that key unchanged when no operation ID was received, or uses status GET when it exists. New intents still generate a UUID before POST.
- These are bounded source-flow checks, not a browser/runtime claim. The same Sol scoped correction review is pending through the parent.

## Runtime, review, and limits

- Live runtime remains **PENDING** in [runtime-evidence.md](runtime-evidence.md); the parent-owned backend lane must demonstrate the real path. No mock is presented as acceptance.
- The same Sol reviewer passed the scoped recheck for both reported storage-isolation findings. Parent-owned backend runtime and final handoff remain pending; source batch is frozen after the recorded host typecheck.
- Durable intent metadata is stored in browser `localStorage` under actor/group/requestKey-scoped keys, without credentials. Resume is a visible user action. The server remains authoritative for operation ownership and authorization.
- API validation remains authoritative for dates, source lifecycle, target teacher eligibility, schedule effects, and conflicts. The client does not widen mutation permissions or infer backend success from optimistic state.
