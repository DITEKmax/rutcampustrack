# Requests UI r3 compact contract

Date: 2026-09-08 (Europe/Moscow)
Owner: `/root/requests_ui_finish_r3` (fresh bounded implementation leaf; sole writer in this worktree)
Assigned runtime: `gpt-5.6-luna / max`; no child agents.
Baseline revision: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`; detached worktree.
Risk: S2.

## 1. Goal

Finish and verify the current compact Requests UI contract on the preserved bytes:
decision/date/icon fidelity, server eligibility and unavailable states, and the
five-state 390x844 temporary harness for open, archive, request type, excuse,
and late-checkin views.

## 2. Context / evidence

- r2 checkpoint records that all 12 product files and 3 harness files matched
  the latest checkpoint hashes before this resume, ports 18540/18541 were free,
  and no post-r2 checks or final runtime inspection were performed.
- Guarded source snapshots are under
  `.agent/student-requests-ui-c/source/`: five design PNG/text contexts,
  five functional SVGs, BRAND_DIRECTION.md, A11Y_REQUIREMENTS.md, and
  107-student-tickets.md. `source-sha256.json` records their hashes.
- r2 already gated card decision text on a non-null decision and valid
  `decision.comment`/`decision.decidedAt`, replaced applicable SVG geometry with
  guarded paths and currentColor, used lower-case `н`, and removed the
  non-source trailing chevron. The incomplete test edit and final verification
  remain open.
- Existing DTO/view-model props provide authoritative eligibility, options,
  reasons, attachment limits and pending refs. The feature does not call HTTP or
  duplicate backend policy.

## 3. Relevant scope

Sole-writable product paths are the 12 files under
`frontends/mobile-core/src/features/requests/`: the five views, four supporting
components, `requests.pcss`, `types.ts`, `state.ts`, and the two tests listed in
the prior packet. Harness scope is exactly
`frontends/.requests-harness/{main.ts,index.html,harness.css}`. Evidence scope is
`.agent/student-requests-ui-c/r3/**`; preserve all other worktree files and
parent-owned work.

## 4. Required behavior

- Keep controlled open/archive, request-type, excuse and late-checkin flows
  coherent with the existing feature contract and guarded compositions.
- Render a pending request with `decision: null` without inventing a decision
  from `detail.reason` or other summary metadata.
- Preserve selected lesson/reason drafts through feature navigation, but make
  the submit action non-submittable when refreshed server options no longer
  include the selection or mark it unavailable; explain the supplied reason.
- Keep server-provided options, reasons, limits and eligibility authoritative;
  use DTO flags/list membership instead of client policy rules.
- Preserve exact guarded SVG path geometry, `currentColor`, labels, keyboard
  focus, status/error/access/offline/loading semantics, responsive overflow,
  and reduced-motion behavior. Finish PCSS only where the guarded design needs
  icon/select/type-choice sizing.

## 5. Constraints

- Vue 3 `<script setup>`, strict TypeScript, existing PCSS tokens and patterns;
  authored dimensions in rem. No inline authored styles, utility framework,
  new dependencies, generated/API/navigation/shared-shell/global changes, or
  backend changes.
- No npm install/network, Gradle, Docker, full-app or production runtime,
  remote Figma writes, commits, reset/clean, or changes outside declared scope.
- Do not hide errors with snapshots, invent assets or eligibility, keep a server
  alive, or claim full application integration.

## 6. Existing patterns

Follow the existing Vue feature props/emits and tokens, `rct-focus-ring`,
semantic `--rct-*` variables, native select semantics, and guarded source SVGs.
The temporary harness mounts actual Vue components and owns no product
navigation or transport.

## 7. Acceptance criteria

1. The 12 product files and 3 harness files have a scoped diff only, with
   guarded source hashes unchanged.
2. Five mounted states remain recognizable and usable at 390x844; no horizontal
   overflow, missing labels, or broken reduced-motion/focus affordances.
3. Pending null-decision/reason behavior and refreshed selected-option
   invalidation are observable in targeted tests.
4. Applicable lint, type, Vitest/SSR, CSS and contract checks are run with exact
   commands and exits; tooling-baseline failures are isolated honestly.
5. Runtime screenshots and listener cleanup evidence cover all five states;
   ports 18540/18541 are free after the run.

## 8. Verification

Record baseline and final revision, hashes, command/exit/environment/evidence in
`.agent/student-requests-ui-c/r3/{baseline-hashes.json,checks.json,runtime-evidence.json,summary.md}`.
Run light checks first, then applicable targeted checks. Free-check and bind the
owned Vite harness strictly to 127.0.0.1:18540, falling back to 18541 only when
18540 is occupied; stop the owned server and confirm both ports free. Capture
actual 390x844 screenshots and proportional keyboard/focus/overflow/theme/
reduced-motion/contrast observations.

## 9. Do not

Do not broaden scope, redesign the product, alter business eligibility, change
API/generated contracts or host navigation, overwrite foreign work, or perform
Terra escalation without the recorded defect/complexity gate (request,
reproduction, new evidence, correction, bounded scope and root decision).

