# Shared-shell repair compact contract

Date: 2026-09-07 · Risk: S2 · Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2` · Branch: `codex/student-role-02-shared-shell`

## 1. Goal

Repair the four independently reproduced shared mobile-shell defects before
integration: external navigation reactivity, owning-root Back history, host
adapter replacement lifecycle, and the canonical Attendance accessible name.

## 2. Context / evidence

- Fresh Sol review `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/student-role-02/shared-shell-review-1.md` reproduced all four defects.
- Critical design source `.agent/student-role-02/design-context/4581-3062.txt:278` requires UI-label `Учёт` with accessible name `Посещаемость`.
- Existing shell foundation and Today integration are pre-existing dirty work in this worktree and remain preserved.

## 3. Relevant scope

- `frontends/mobile-core/src/shared/**`
- `frontends/mobile-core/src/styles/tokens.pcss` touch-target alias only
- `frontends/mobile-core/src/features/today/TodayScreen.vue` nav declaration only
- `frontends/mobile-core/tests/navigation.test.mjs`
- `.agent/student-role-02/shared-shell-repair/**` evidence only

## 4. Required behavior

- `MobileNavigationStack` publishes every effective external `push`, `replace`, `goRoot`, and `back` mutation through a framework-independent subscription.
- Initial, cross-root, and replaced nested routes have an explicit owning-root entry; nested Back reaches that root and then stops.
- `MobileShell` resubscribes keyboard and Back callbacks when `host` changes, resets host keyboard state, hides the old host Back control, and cleans the current host on unmount.
- Bottom-nav items may provide a separate accessible label; disabled reasons remain appended. Today maps visible `Учёт` to `Посещаемость`.

## 5. Constraints

Preserve the existing shell foundation, keyboard Boolean default, Onest/tabular
typography, Today data/events, and unrelated dirty work. No App, backend,
adapter, config, lockfile, package, Figma, SDK, or product redesign changes.

## 6. Existing patterns

Vue `computed`/`watch` lifecycle APIs, typed `MobileRoute` values, callback-only
`MobileHostAdapter`, and the existing Node contract-test adapter.

## 7. Acceptance criteria

Contract tests cover initial nested, cross-root, replace, root navigation, and
observable mutations. Actual Vue component evidence covers external push/replace
dock state, host A→B subscriptions/Back/unmount, and the Attendance accessible
name. No critical or high finding remains in this bounded repair.

## 8. Verification

Run focused Node tests, actual Vue probes, mobile-core scoped lint, workspace
typecheck, PWA/TMA production build, full lint for the known unrelated fixture
finding, and `git diff --check`. Record revision, exact commands, exit codes,
environment, runtime output, diff, and limitations in this directory.

## 9. Do not

Do not change the known Today semester/role debt, the host SDK boundary, the
fixture lint error, or any API/backend/generated/config/lockfile scope. Do not
escalate to Terra without a new recorded complexity/defect gate; this repair is
bounded for Luna-level implementation.
