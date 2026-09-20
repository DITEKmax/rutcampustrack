# Resume 4 — Security invalidation verification

Date: 2026-09-08. Risk S3. Baseline revision `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. Coordinator: Sol high, product read-only. Writer: fresh Luna max, fork none, canonical nested `profile-ui` worktree.

## 1. Goal

Verify and, only on reproduced failure, repair current `SecurityScreen.vue` SHA256 `51FD2424CB07C6EBEC6DF93033D5E1F515D6BA6326034DEF69B554AFB50687FA`: external `ACCOUNT_INVALIDATED` clears three password fields/reveal state and independently blocks submit while ordinary errors preserve retry input. Complete lightweight checks, stable evidence and hand off for mounted browser QA plus fresh independent review.

## 2. Context/evidence

Authoritative common checkpoint: `C:/Users/maksd/.codex/worktrees/1267/rutcampustrack/.agent/student-role-orchestrator/pause-2026-09-08-1155.md`, SHA `E21DF0F3A121368B6190B74DC9082E41209AF4A8CB1B59DC3FCE2F6F2D0C441A`. A checkpoint: `.agent/student-auth-a/shutdown/pause-2026-09-08-1200.md`, SHA `DD4C6E535CEB90A76E1AD4DCC9835765238976AF6D317297491B625F9CEA8D30`. Pre-edit packet nested `.agent/profile-ui/evidence/resume-security-fix2/packet.md`, SHA `8E44D502E8F42AEC0A0ACC5879C25038E5919BCE2A5A7A6961EDB09C25A17DB8`; pre-state SHA `DF086C885330F999B89F824AFF89B6E6C7FF85E238F7B36E838B74DF0A4467A2`. Historical mounted old-source FAIL is outer `.agent/student-auth-a/profile/root-mounted-baseline.md`. Historical profile state20/20 and harness44-module build do not verify current bytes. Last strict vue-tsc failed6 TS2379 before callback cleanup and was not rerun.

Current audit: outer and nested HEAD match baseline; existing dirty scopes preserved; Security hash/10825 bytes exactly match checkpoint; no known prior PIDs; ports18110–18119 have zero listeners. No addressable project ownership gateway tool is exposed; canonical registry explicitly retains A Auth/profile ownership and nested worktree sole-writer boundary.

## 3. Relevant scope

Sole product writer of nested `frontends/mobile-core/src/features/profile/SecurityScreen.vue` only if verification finds a concrete defect. Evidence files under nested `.agent/profile-ui/evidence/resume4-security/**` and status/final manifest. Harness may be changed only for observable local controls, never product logic. Other six SFCs are read-only unless strict checks reproduce a callback-cleanup defect and root amends scope. Outer duplicates/shared files remain unchanged.

## 4. Required behavior

Exactly external `props.error?.code === 'ACCOUNT_INVALIDATED'` triggers immediate/on-transition clearing of current/new/repeat values, reveal flags, submitted and local error. External error remains visible. Submit function guards invalidation independently of the disabled button. Removing invalidation leaves an empty usable form. Wrong-current, policy, network and authority errors retain values and permit retry when existing busy/offline rules allow. Success/unmount clearing remains. Password values never enter logs or evidence.

## 5. Constraints

Preserve others' work; one writer; resolve writes under nested root. No children, commits, dependencies, lockfiles, shared API/types/config/tokens, backend, Gradle/Docker, real credentials, deploy/reset/clean/external messages. Read required workflow/frontend/tests/design/verification instructions. Runtime only exact `127.0.0.1:18110` after fresh free-port proof and root lease; record own PID/start/command and cleanup only that process in finally.

## 6. Existing patterns

Vue3 strict script setup; current source already has one `accountInvalidated` computed, central `clearSensitiveForm`, immediate `watch`, submit guard and disabled binding. Prefer verification over rewriting. Existing harness imports real components and exposes synthetic local callback counters. Reuse installed d650 dependencies without installation.

## 7. Acceptance criteria

Strict vue-tsc PASS for all seven SFCs; focused ESLint zero errors and no new warnings; focused profile-state test remains20/20; Vite harness build PASS. Mounted QA proves invalidation clears/hides and callback remains unchanged through button/form path, then error removal permits fresh valid submit. Ordinary current/password-policy/network errors retain input and retry. Stable manifest has SHA/bytes and no outer/shared changes. Fresh Sol review has no blocking correctness/security finding.

## 8. Verification

Record revision, cwd, exact commands, tool versions, UTC times, exit codes, output artifacts and input/output hashes. Run only existing lightweight vue-tsc, focused ESLint, focused Vitest and Vite build. Send READY with source/check manifest and exact18110 command/free proof before server start. Root owns actual IAB verdict and review handoff. Skipped or historical checks are not current PASS.

## 9. Do not

Do not broaden invalidation codes or auth behavior, expose password values, weaken policy/checks, edit PCSS/types/state/assets/outer copies, claim backend/full PWA/TMA/full39 acceptance, or start heavy runtime. Preserve accepted purpose13/domain21+Sol bundles without rerun.

## Dated additive correction — 2026-09-08

Actual current lint reproduced 17 `vue/require-default-prop` warnings and zero errors, all on optional callback props across the seven SFCs. The earlier removal of `undefined` defaults made strict `vue-tsc` pass but left this lint failure. No-op callback defaults are rejected because they change observable guarded semantics such as `if (!props.onChangePassword) return`.

The sole-writer product scope is extended only to optional callback declarations/default entries in the seven named SFCs. Pilot on `SecurityScreen.vue`: explicitly include `| undefined` in each optional callback prop type and restore the matching `withDefaults` entry as literal `undefined`. Run strict project `vue-tsc` plus Security-only ESLint before propagation. If both pass and the absence guard remains effective, apply the same mechanical type/default pattern to the other six SFC callback props only, then rerun full seven-SFC typecheck/lint. Do not change guarded logic, add no-op functions, suppress/configure rules, or touch non-callback props/shared types. Record exact before/after lines and command evidence.
