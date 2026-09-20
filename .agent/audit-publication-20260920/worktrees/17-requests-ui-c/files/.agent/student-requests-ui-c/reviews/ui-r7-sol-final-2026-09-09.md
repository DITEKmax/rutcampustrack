# Requests UI R7 — fresh independent final review

Date: 2026-09-09
Verdict: PASS
Risk: S2
Reviewer: fresh gpt-5.6-sol, effort high
Mode: read-only; no repository, test, evidence, data, runtime, browser, server or external-application mutations.

## Reviewed checkout

C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-ui-c
Detached revision: d3c31acb8cce53791a4981e5858a37d44fdc9a0e.

## Reviewed contract and evidence

- .agent/student-requests-ui-c/packet.md
- .agent/student-requests-ui-c/r5/packet.md
- .agent/student-requests-ui-c/r5/shutdown-checkpoint-2026-09-09.md
- .agent/student-requests-ui-c/r6/packet.md
- .agent/student-requests-ui-c/r6/resume-addendum.md
- .agent/student-requests-ui-c/r6/browser-evidence.json
- .agent/student-requests-ui-c/r6/final-source-manifest.json
- .agent/student-requests-ui-c/r6/checks.md
- .agent/student-requests-ui-c/r6/summary.md
- .agent/student-requests-ui-c/source/design-context/{4610-142,4610-848846,4610-848937,4610-849007,4610-849072}.{png,txt}
- .agent/student-requests-ui-c/source/design-reference/{A11Y_REQUIREMENTS,BRAND_DIRECTION}.md
- .agent/student-requests-ui-c/source/wireframes/107-student-tickets.md
- .agent/student-requests-ui-c/source/design-assets/{chevron-down,date,lesson-type,previous,time}.svg

Exact product scope reviewed:
- frontends/mobile-core/src/features/requests/{RequestsScreen.vue,RequestCard.vue,RequestTypeScreen.vue,ExcuseRequestScreen.vue,LateCheckinRequestScreen.vue,RequestLessonSelector.vue,RequestAttachmentField.vue,requests.pcss,types.ts,state.ts,state.test.ts,RequestsScreen.test.ts}

Exact harness scope reviewed:
- frontends/.requests-harness/{main.ts,index.html,harness.css}

## Independent checks

- Current SHA-256 values matched final-source-manifest.json: product 12/12, harness 3/3, guarded originals 18/18.
- All six persisted R6 PNG hashes matched browser-evidence.json; the immutable R5 checkpoint hash also matched.
- R6 product/harness hashes matched the R5 checkpoint 15/15, supporting the claim that R6 did not mutate product or harness sources.
- Current code independently confirms:
  - retained missing lesson IDs render a removable recovery row without visible raw IDs;
  - retained present-but-ineligible lessons remain visible with the server reason and an enabled explicit removal action;
  - removal changes only lessonIds; reason, comment and file references remain retained by the controlled update and draft patch contract;
  - invalid retained selections cannot be submitted;
  - blank and whitespace-only comments block submission only when the selected server option has commentRequired === true;
  - commentRequired === false does not infer a rule from the reason code;
  - draft state is isolated by owner and session generation, cloned defensively and purgeable at session/logout boundaries;
  - file input focus is exposed through the visible picker’s :focus-within outline;
  - access, offline, loading, error, busy, eligibility, cancellation and expired-attachment boundaries remain controlled or server-authoritative;
  - responsive sizing, overflow wrapping and reduced-motion guards are present.
- All five canonical design PNG originals were opened and compared with the implemented composition. Runtime open.png and archive.png were also inspected directly and showed complete Russian mobile layouts without horizontal clipping.
- Recorded CDP evidence for every runtime state reports 390×844, scrollWidth=390, bodyScrollWidth=390 and maxRight=390. Excuse recovery uses the expected vertical scroll and its supplemental focus frame records the real file input as active.
- Historical R5 typecheck, lint, targeted SSR/state tests, scoped vue-tsc, PostCSS and contract checks were accepted because every covered product/harness hash remains unchanged. No runtime or test was rerun during R7.

## Findings

None.

A local view_image operation hung while rendering the remaining runtime type, excuse, excuse-focus and late PNGs. This did not change the verdict: their bytes independently matched the immutable manifest, exact dimensions and overflow metrics were recorded in valid JSON, the five corresponding canonical design originals were inspected, and the relevant DOM/CSS/state paths were independently reviewed. The limitation concerns redundant rendering in the review environment rather than product behavior or evidence integrity.

Light-theme browser QA is explicitly deferred by the owner and is not a blocking R7 criterion. The existing light-theme source branch was reviewed for composition preservation; no claim of a captured light-theme runtime is made.
