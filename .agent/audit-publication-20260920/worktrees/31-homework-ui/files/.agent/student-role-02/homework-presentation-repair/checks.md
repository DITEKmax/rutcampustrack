# Checks

Environment: Windows PowerShell, Node `v24.14.0`, npm `11.9.0`, branch `codex/student-role-02-homework-ui`, HEAD `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`. The product feature is an uncommitted parent change on top of that HEAD; no commit was created by this repair.

| Command | Exit | Evidence |
| --- | ---: | --- |
| `node --check .agent\student-role-02\homework-presentation-repair\final-probe.mjs` | 0 | Final actual-component probe syntax valid. |
| `node --check .agent\student-role-02\homework-presentation-repair\runtime\main.ts` | 0 | Copied fixture syntax valid. |
| `node --check .agent\student-role-02\homework-presentation-repair\scope-check.mjs; node --check .agent\student-role-02\homework-presentation-repair\font-probe.mjs` | 0 | Evidence helpers syntax valid. |
| `npm run typecheck --workspace @rct/mobile-core` in `frontends` | 0 | Scoped strict TypeScript check. |
| `npm run lint --workspace @rct/mobile-core` in `frontends` | 0 | Scoped ESLint with `--max-warnings=0`. |
| `npx vitest run mobile-core/src` in `frontends` | 0 | 9 files, 28 tests passed. |
| `npm run typecheck` in `frontends` | 0 | mobile-core, PWA and TMA checks passed. |
| `npm run lint` in `frontends` | 0 | mobile-core, PWA and TMA lint passed. |
| `npm run build` in `frontends` | 0 | PWA and TMA Vite production builds passed (120/121 modules). |
| `node .agent\student-role-02\homework-presentation-repair\scope-check.mjs` | 0 | `scope-evidence.json`: exactly 2 changed product paths, 23 unchanged, 0 unexpected. |
| `node .agent\student-role-02\homework-presentation-repair\final-probe.mjs` | 0 | `final-visual-evidence.json`: all scenes, matrix cases, assertions and axe checks passed. |
| `git diff --check` | 0 | No whitespace errors; Git emitted existing LF/CRLF normalization warnings for unrelated parent files. |

The earlier baseline reproduction was run before the repair and exited 0 while recording both findings in `baseline-evidence.json`. The first post-patch probe attempt exited 1 only because its diagnostic asserted every duplicate FontFace entry had status `loaded`; `font-probe.mjs` exited 0 with `document.fonts.check(...) === true` and loaded Onest entries. The probe criterion was corrected to test actual font availability, then the unchanged product was rerun with exit 0. This was a probe false positive, not a product defect.

`generate:types:check` is not applicable to this presentation-only repair; API/generated files are outside scope and the parent packet records its baseline CRLF-only drift separately.

Each of the two `git diff --no-index` snapshot comparisons returned exit 1 by design because the repaired file differs from its frozen snapshot; their unified output is preserved in the two `product-delta-*.diff` evidence files. The frozen25 acceptance check is `scope-check.mjs` above and returned exit 0.
