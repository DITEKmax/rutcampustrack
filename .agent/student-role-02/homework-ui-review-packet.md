# Homework shared UI — staged independent review

07.09.2026. S2. Fresh Sol high reviewer, read-only, no children. Not dispatched; fill stable manifest/check references after the writer's handoff.

## Goal
Independently assess the shared student Homework feature against its final five Figma states and frozen API, before PWA/TMA adapter integration.

## Context/evidence
Read active-contract.md, homework-ui-packet.md including dispatch appendices, homework-date-delta-packet.md and final five originals/screenshots in design-context. Final decision: completedAt/serverNow Europe/Moscow drives a once-only completed-today group; no-materials action is absent. Accepted baseline d3c31acb8cce53791a4981e5858a37d44fdc9a0e. Stable source manifest, final writer checks and screenshots will be attached at dispatch. Do not consume the author's full transcript.

## Relevant scope
Read-only review of homework-ui worktree's shared domain/composable/screen/PCSS, canonical SVG copies, limited index exports and token aliases, plus focused tests/fixture. Main and other worktrees remain read-only. Public API/generated types/config/locks/Today/adapters are outside implementation scope and must not drift.

## Required behavior
Verify desired-state ACK/unknown response/retry/reversal, repeated activation, date/range/semester grouping, same-query ownership and stale results across identity changes. Check completion/retry intent cannot move between owners sharing a Homework ID. Ensure a valid empty options object preserves defaults and prior scope retry state/ranges do not leak. Review selected historical page plus in-flight completion/refetch behavior, online/offline transition and read-only mutation guard. External links must preserve validated absolute HTTP(S) original targets and reject unsupported navigation. Verify keyboard equivalent for swipe, disclosure, previous/today navigation and dock/back boundaries.

## Constraints
Findings require severity, file:line, direct evidence, impact and reproduction; style preferences are not blockers. No edits, dependency changes, new product tokens/UI, compatibility behavior, external messages or broad role work. Do not call browser fixtures real API or genuine Telegram. No invented visual PASS from successful build.

## Existing patterns
Shared StudentApi typed transport, Vue Query response owner, MobileShell host/nav boundary, canonical tokens-v2 mappings and preserved original assets. Read critical originals independently. Source interpretation comes from frozen root decisions, not generated React reference implementation.

## Acceptance criteria
Stable reviewed source hashes match before/after, all five final states/important transitions are accounted for, unit/component and both builds are actually evidenced, and no blocking correctness/authz/cache ownership/data-loss/accessibility regression remains. Review dark390x844 versus original plus light/root-font/narrow/wide evidence. Missing meaningful coverage must be named precisely. This review may accept only the shared feature; real API adapter flows and full-role review remain open.

## Verification
At dispatch attach exact stable manifest/diff, commands/exits, artifact locations and current fixture ownership. Prefer existing meaningful tests and targeted independent reproduction when a concern warrants it. If a runtime is needed, reserve a separate port or explicitly take released5181; do not interfere with other writers. Return PASS/FAIL with bounded limitations and actionable findings. No repository writes.

Root read-only questions for targeted reproduction, not established findings: retryCompletion reads saved input but calls submitCompletion with only id/completed; scope clearing uses default asynchronous watch. Check identity replacement followed by retry in the same tick, before watcher flush, and require no command under the new owner. Also assess queryFn's reactive scope/range reads on retries relative to its immutable queryKey; prove an obsolete query cannot cache another range/owner's response. Do not report either as a defect without reaching the actual behavior under Vue Query lifecycle. Loaded-font visual metadata now exists under homework-ui-visual; compare date heading18px current alias against17px final source and measured vertical differences. Theme foundation is staged; final review packet must include its accepted ownership/diff if dispatched before this review.

## Do not
No product/full-role PASS, reviewer implementation, fake screenshot/reference claims, reusing prior nonindependent approval, or reliance on stale source hashes. Root will accept evidence and assign any repair to a developer, then request independent recheck.

Root update07.09: the same-tick retry ownership question above is now a CONFIRMED defect, reproduced against exact UI15 SHA in homework-owner-retry-probe/evidence.md. Active mobile_theme_repair_fresh received a bounded two-file correction gate before final handoff. Review the correction and true failed-A-intent→B-same-tick regression independently. The queryFn/immutable-key concern remains only a question. Combined final scope now includes canonical theme foundation in Today/nav/tokens and Homework focus/date/owner-retry repair under mobile-theme-implementation-packet.md; the earlier Today-outside-scope statement applies only to the original UI15, not these authorized theme bindings.

Root intermediate visual finding07.09: homework-theme-mask-repro/evidence.md preserves both themes' plain-circle disclosure regression, SVG and source snapshots. The compound SVG's opaque rounded rect masks over its chevron when used as one alpha mask. Active writer notified to fix within existing theme scope and recapture. Final reviewer must verify layered disclosure icons plus actual viewport390×844 five-state reference captures; intermediate full-page keyboard fixture captures are insufficient. Separately inspect Today foregrounds on its fixed dark role/action gradients in light mode; root flagged source contrast concern but awaits runtime proof before labeling that a confirmed defect.

Root runtime update07.09: Today light-gradient concern is now CONFIRMED, see today-theme-contrast-repro/evidence.md (actual screenshot + computed report role1.01/action1.00 contrast). Canonical color/text/on-fill-strong already exists at tokens-v2.json414–417; active writer authorized to expose/use it in current theme scope and recapture both modes. This replaces the earlier unconfirmed label for that concern only.

DISPATCH07.09.2026 ~18:13UTC: writer explicitly stable, stopped writes, ports5181/5182 released. Combined25 source frozen at homework-combined-review-source/manifest.json SHA A95351F7282536C75D0F84416986098684953655114D26B67AFD4428C72DD0FA; all25 source/copy pairs match (0mismatch). Review worktree .agent/worktrees/student-role-02/homework-ui at d3c31 + exact25files, tracked-diff.patch plus added files. Writer checks/evidence/summary at worktree .agent/student-role-02/mobile-theme, inherited UI tests at homework-ui. Full28tests, typecheck/lint/bothbuilds reported exit0; generate:types:check exit1, unchanged generatedBFF source, reconciliation separately pending. Root will examine raw/EOL drift independently; do not label fullproject checksPASS.

Visual freshness GAP: five state images named01–05 in homework-ui-visual/screenshots are timestamp17:19–17:20UTC, preceding final17:59 mask and Today contrast corrections. They are historical evidence only. Current mobile-theme runtime captures show corrected themes but are fullPage keyboardfixture. Root is recapturing five exact390x844 states on the frozen25 source and will send immutable new evidence location. You may start stable code/originals review independently; await this evidence before final visual conclusion. Do not accept stale images as final source proof. Root owns released5181 for this capture; reserve a different port if necessary.


Post-dispatch immutable evidence: homework-final-visual-root/evidence.md + manifest/visual-evidence.json + five01–05 viewportPNGs and matrix. New actual frozen25 recapture exit0, Onestloaded/date17px/nooverflow/axe0, source25postruntime0mismatch. Rootopenedfive, fixedchevronsvisible. homework-owner-retry-corrected-probe proves same original failedA→Bsame-tick scenario nowexit0, retry=null/APIAonly. Generated-type drift independently narrowed by homework-combined-review-source/generated-drift-probe.mjs + generated-drift-result.json: exact AST/header check differs onlyCRLF; normalizedLineEndingsEqual=true, specSHAalreadyexactmatch. Original generate:types:check remains exit1; downstream bounded EOL policy gate unresolved, no product/schemafixhere.


Interim review range concern closed: homework-owner-range-probe/evidence.md contains actual composable negative evidence and reviewer explicit retraction after independent rerun. Only A historical→B current observed; equivalent lifecycle model was not valid defect evidence. No implementation change for that withdrawn MEDIUM. This does not supersede the separate confirmed and corrected saved-retry ownership bug.

