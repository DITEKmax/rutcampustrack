# Lesson transfer UI — compact contract and evidence

## Goal
Для старосты в общей PWA/TMA-фиче: перенести допустимую будущую пару через подтверждённый backend POST, видеть PENDING/COMPLETED/ERROR, безопасно возобновлять статус/повторять uncertain запрос и открыть новую дату только после COMPLETED.

## Context/evidence
- Base `0f331540eab744bfe460fc7fa4480e12383e8edc`, branch `codex/lesson-transfer-ui-20260929`; canonical RULES SHA `F4986A1834A9175ADBB7DCB3C49483168111A0B60C7EB13ACDA9FBF9D74927F8`.
- Frozen backend contract: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/evidence/2026-09-27-delivery/lesson-transfer-contract.md`. Accepted backend runtime `lesson-transfer-runtime-0f331540.json` proves A→B→C, bound homework, exact replay/conflict denial and one current occurrence; its explicit limit says PWA control was not connected.
- Resume needs immutable operation `targetDate`; permitted Schedule mapping dependency is commit `43b4a162`, integrated by root as `90979088` with bounded Sol PASS. Date is read from operation target snapshot.
- Owner approved existing-component anatomy followed by Figma comparison. Registry has desktop `LessonTransferControl`/wireframe 118; mobile transfer frame not found. No Figma edits/new tokens.

## Relevant scope
Product files: `frontends/mobile-core/src/features/headman-journal/{headman-journal-client.ts,HeadmanJournalScreen.vue,headman-journal-screen.pcss,headman-journal-client.test.ts}`. Evidence is this file. Diff: 4 product files, +1195/-24 before commit. Foreign modified `.agent/evidence/headman-trend-export-20260926/{contract.md,result.md}` preserved and excluded.

## Required behavior
Use latest-list `occurrenceRevision`; only current future `PLANNED` source with role access may start. Load target date, offer free slots 1–8 on future Mon–Sat dates, confirm, then POST frozen body with UUID requestKey and exact revision. 202 is PENDING only; scoped GET resumes status; 200 must be COMPLETED; 409 body remains error. Uncertain retry and 401 refresh retain exact payload/key. Owner/group/session/role/date/source changes invalidate late callbacks. Lock active source/target lifecycle actions. Only COMPLETED navigates/reloads target date and focuses targetLessonId. Client does not mutate marks, attachments or bound homework.

## Constraints
No bootstrap/session/homework/AssistantActionsScreen, Telegram adapter, generated/proto/schema/lockfile, Figma, new token, cancel+create fallback, or unrelated edits. Heavy Vue build/browser runtime remain with root’s shared lease.

## Existing patterns
Reuse `HeadmanJournalApi` auth refresh/session-generation checks, HeadmanJournalScreen’s lesson selection/action/dialog, permission rules, existing feature PCSS and RCT semantic tokens; PWA/TMA share mobile-core but retain own bootstrap/adapters.

## Acceptance criteria
Eligible headman can choose/confirm and observe server operation; past/canceled/non-current/pending/inaccessible lessons cannot begin. Pending can resume; uncertain request retries exact bytes/key. Conflicts never look successful. After COMPLETED the client reloads target date and target lesson ID, preserving source lineage. Root still needs actual PWA browser confirmation of current target lesson and prior backend evidence covers bound homework.

## Verification
- `git diff --check`: exit 0.
- Existing `headman-journal-client.test.ts`: 1 file / 13 tests passed, exit 0. Command: `npm exec -- vitest run --config .vitest.lesson-transfer.config.mjs mobile-core/src/features/headman-journal/headman-journal-client.test.ts` from `frontends/`; the temporary no-import config (`root: process.cwd()`, node environment, globals) was removed. Assertions cover POST/status normalization, latest occurrence revision, uncertain network exact retry key/body, 401 exact retry, 409 `SOURCE_STATE_CONFLICT`, and late transfer response after session generation change.
- Initial default-config Vitest attempt exited 1 before tests because esbuild hit sandbox parent-path `Access is denied`; rerun of the same file with justified sandbox escalation and the temporary local config passed.
- PWA/TMA build, browser/screenshot/keyboard and end-to-end transfer runtime are not run here. Backend runtime above is not UI evidence. Root owns combined build/browser acceptance after integration; until then no integrated UI/runtime PASS.

## Do not
Do not interpret 202 as success; derive expectedRevision from physical revision; retry with new key; derive resumed target date from later current occurrence; bypass transfer via cancel/create; or claim frontend integration/runtime acceptance before root verification.
