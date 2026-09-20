# Checks

Environment: Windows, PowerShell, Node `v24.14.0`, npm `11.9.0`; working tree `codex/student-role-02-homework-ui`; baseline `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.

| Command | Exit | Evidence |
| --- | ---: | --- |
| `npm ci` in `frontends` | 0 | Installed unchanged lockfile dependencies; audit reported 0 vulnerabilities. |
| `npx vitest run mobile-core/src` in `frontends` | 0 | 7 test files, 21 tests passed, including homework domain and scope/completion harness. |
| `npm run typecheck` in `frontends` | 0 | `mobile-core` `tsc`, PWA `vue-tsc`, TMA `vue-tsc` all passed. |
| `npm run lint --workspace @rct/mobile-core` in `frontends` | 0 | Scoped ESLint passed with `--max-warnings=0`. |
| `npm run lint` in `frontends` | 0 | mobile-core, PWA and TMA lint passed. |
| `npm run build` in `frontends` | 0 | PWA and TMA Vite production builds passed; each transformed 118/119 modules. |
| `git diff --check` | 0 | No whitespace errors; Git emitted only existing LF/CRLF normalization warnings. |

The first pre-install mobile-core typecheck exited 1 because `tsc` was unavailable; the unchanged `npm ci` resolved that environment prerequisite. An intermediate lint/typecheck run exposed local Vue prop/default typing issues; those were corrected and the final rows above are clean.
