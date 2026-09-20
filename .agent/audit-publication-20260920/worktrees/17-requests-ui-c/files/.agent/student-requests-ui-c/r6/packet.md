# Requests UI R6 compact contract

Date: 2026-09-09 (Europe/Moscow)
Owner: `/root/requests_ui_resume_r6` (fresh bounded evidence leaf; sole writer of R6 evidence)
Assigned model/effort: `gpt-5.6-luna / max`
Worktree: `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-ui-c`
Baseline revision: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e` (detached)
Risk: S2 within the frozen S3 UI history

## 1. Goal

Complete the missing R6 browser/runtime and final evidence for the frozen
Requests UI. Capture five inspectable 390x844 states from the owned Vite
harness, preserve the R5 source contract, and release a stable evidence bundle
for the root-owned independent review.

## 2. Context/evidence

- Immutable R5 shutdown checkpoint:
  `.agent/student-requests-ui-c/r5/shutdown-checkpoint-2026-09-09.md`, SHA-256
  `1ED29E81E3B802993044BF81064ADE3FCCF58F73B8748C9447B03C12356CBE75`.
- Root-verified preflight: all exact 12 Requests product and 3 harness hashes
  match the checkpoint (15/15); ports `127.0.0.1:18540` and `:18541` were
  free before this packet.
- R5 accepted typecheck, lint, targeted state/SSR, scoped `vue-tsc`, PostCSS
  and contract checks remain historical evidence; they are not rerun unless a
  covered source changes.
- Existing harness and all product files are read-only in this resumed scope.
  The harness currently exposes `open`, `archive`, `type`, `excuse`, and `late`
  query states, with retained missing/ineligible excuse IDs and real Vue
  components.

## 3. Relevant scope

Writable paths are exactly `.agent/student-requests-ui-c/r6/**` in this
worktree. Read-only sources are the 12 frozen Requests product files, the
three harness files, R5 evidence, and the five canonical
`.agent/student-requests-ui-c/source/design-assets/*.svg` files. Runtime is
limited to the existing Vite harness on localhost port 18540, with 18541 as
the only fallback.

## 4. Required behavior

- Start the existing installed Vite harness with an explicit local command.
- Capture persisted PNGs for `open`, `archive`, `type`, `excuse` (recovery plus
  keyboard focus visible), and `late`, each exactly 390x844.
- In the excuse capture, show recoverable present-ineligible and missing
  selections without exposing the raw missing ID, and show the file-picker
  focus ring. Use a deterministic query only if the existing harness already
  supports it; do not edit harness or product during this resume.
- Inspect each PNG for overflow, state visibility, source reduced-motion
  behavior, and represented dark/light behavior. Light-theme QA is deferred by
  the owner and is recorded as a limitation.
- Record PNG paths, SHA-256, dimensions, browser/runtime facts, source hashes,
  checks with exit codes, and listener cleanup.

## 5. Constraints

- Preserve all foreign work and the immutable R5 checkpoint. Do not reset,
  clean, commit, revert, install, use network, or modify product, harness,
  dependencies, configs, navigation, API/BFF/proto/generated code, or assets.
- Use installed local dependencies/browsers only; do not use npm exec/install,
  Docker, Gradle, deploy, or backend runtime.
- Do not claim unpersisted screenshots. If the installed browser path cannot
  persist captures after bounded fallbacks, record the exact limitation and
  stop the owned runtime.
- A real new environment/network/Docker failure is a stop condition: stop only
  owned runtime, checkpoint, and report root. Invocation/config mistakes may
  use the known installed fallback.

## 6. Existing patterns

Use the existing `.requests-harness/main.ts`, real Requests Vue components,
Vite command `node_modules/.bin/vite.cmd --host 127.0.0.1 --port 18540
--strictPort`, and local Chrome at
`C:\Program Files\Google\Chrome\Application\chrome.exe` with Edge as an
installed fallback. Preserve the R5 focus, recovery, token, and SSR behavior.

## 7. Acceptance criteria

1. Five actual local PNGs exist, are inspectable, and each has exact
   dimensions 390x844 and a recorded SHA-256.
2. The excuse image visibly covers recovery and focus requirements without a
   raw missing lesson ID; the other four images show their requested states.
3. A final manifest covers exactly 12 product files, 3 harness files, all five
   canonical SVG assets, and relevant guarded source hashes.
4. Checks, runtime facts, observations, limitations, and scope diff are
   recorded with exact exit codes and evidence paths.
5. The owned Vite/browser processes are stopped and both allowed ports are
   proven free. Product hashes remain unchanged.

## 8. Verification

- Recheck listeners before launch; run Vite only on 127.0.0.1:18540 or 18541.
- Use an installed local headless browser/capture path; verify PNG signature,
  dimensions, and SHA-256 with local commands. Inspect all five files.
- Record `git status`, revision, exact source manifest, runtime evidence,
  checks, and summary under `r6/**`.
- Stop only owned runtime/browser processes, then recheck both ports.
- Historical R5 checks are referenced rather than rerun because no covered
  source is changing.

## 9. Do not

Do not redesign or correct product/harness code, add a light theme, infer
backend behavior, modify source assets, or expand runtime beyond the existing
local harness. Do not rerun broad checks, launch Docker/Gradle/backend, or
escalate to Terra without a recorded defect/complexity gate and root decision.
