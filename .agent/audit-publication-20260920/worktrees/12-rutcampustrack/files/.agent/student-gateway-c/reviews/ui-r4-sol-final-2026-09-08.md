# Requests UI independent final review — R4

Status: **FAIL**. Risk: S3. Reviewer runtime: `gpt-5.6-sol` / `high`.
Revision: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.

The corrected targeted command using the existing Vue config passed: Vitest 4.1.4, one file, 4/4 tests. The previous zero-test tooling blocker is resolved, but the suite did not catch the findings below.

## HIGH — stale lesson selection irreversibly blocks the excuse form

Files: `RequestLessonSelector.vue:83-90,126-129`; `ExcuseRequestScreen.vue:48-58`.

`canSubmit` requires every retained ID to remain eligible. A lesson that becomes unavailable stays checked while its checkbox is disabled; an ID removed from options is not rendered. Adding a new eligible lesson begins with the full stale Set, so the invalid ID remains and submit stays disabled. Reproduce with `lessonIds=['lesson-1']`, then mark that option `excuseEligible:false` or remove it from `lessons`.

## MEDIUM — authoritative reason contract loses required-comment metadata

Files: `types.ts:56-59`; `ExcuseRequestScreen.vue:48-58,75-82`; critical original `107-student-tickets.md:96,150`; BFF `StudentRequestApiModels.java:127-129`.

The source requires `commentRequired`, but BFF/local `ReasonOption` expose only `code,label`; submit checks only that the code remains listed. An `OTHER` reason can therefore submit with an empty comment.

## MEDIUM — file picker has no visible keyboard focus

Files: `RequestAttachmentField.vue:148-162`; `requests.pcss:672-684,698-706`; `tokens.pcss:100-104`.

Focus lands on a visually hidden input while the focus-ring class sits on a nonfocusable label and has only `:focus-visible`, without a focus-within/input-focused rule. Tabbing to “Выбери фото или файл” does not show a visible ring.

## MEDIUM — recorded typecheck excludes Vue SFC

Files: `frontends/mobile-core/package.json:7`; `frontends/mobile-core/tsconfig.json:7`; `r3/checks.json:13-16`.

The check uses `tsc`, and include is only `src/**/*.ts`; seven `.vue` files are excluded. A scoped task-owned `vue-tsc` configuration and exit-0 run are required.

## MEDIUM — stable diff and visual/source evidence are not independently verifiable

Files: `source-sha256.json:3-16`; `r3/runtime-evidence.json:17-47`; `r3/baseline-hashes.json:9-24`; `r3/checks.json:71-83`.

The source manifest omits five critical `source/design-assets/*.svg`; runtime records `captured:true` without image paths/hashes; r3 has no runtime images; there is no final 12+3 hash manifest. Since all scoped files are untracked, normal git diff is empty. Preserve the five 390x844 captures and complete source/final SHA-256 manifests.

## Required correction and verification

Provide an explicit recovery path that removes retained ineligible or missing IDs without clearing other draft fields or permitting invalid submission. Preserve and enforce authoritative `commentRequired` through the correct BFF/generated/UI ownership chain. Add a visible nested-input focus state. Run the corrected targeted Vitest, interaction tests for present-ineligible and missing IDs, comment-required cases, scoped `vue-tsc`, hash guards, and five stored 390x844 screenshots with hashes and released ports. A fresh independent recheck is required.