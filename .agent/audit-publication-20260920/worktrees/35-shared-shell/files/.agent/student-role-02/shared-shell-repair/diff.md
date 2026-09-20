# Shared-shell repair diff

Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`  
Working tree: `codex/student-role-02-shared-shell` (intentionally uncommitted)

## Repair-owned changes

- `frontends/mobile-core/src/shared/navigation.ts`
  - added framework-independent `subscribe` notifications;
  - seeded owning-root entries for initial, cross-root, and replaced nested
    routes;
  - kept root navigation clearing nested history and nested Back stopping at the
    owning root.
- `frontends/mobile-core/src/shared/components/MobileShell.vue`
  - subscribed to external stack mutations;
  - resubscribed host keyboard/Back callbacks on replacement, reset keyboard
    state, and cleaned subscriptions on unmount;
  - removed duplicate manual stack invalidation now that the stack publishes
    its own changes.
- `frontends/mobile-core/src/shared/components/MobileBottomNav.vue`
  - resolved optional `accessibleLabel` before appending disabled reason.
- `frontends/mobile-core/src/shared/components/mobile-shell.pcss`
  - used the semantic touch-target alias for Back minimum dimensions.
- `frontends/mobile-core/src/styles/tokens.pcss`
  - added `--rct-control-min-touch: 2.75rem`, mapped by comment to canonical
    `control/min-touch = 44px`.
- `frontends/mobile-core/src/shared/navigation.ts` and
  `frontends/mobile-core/src/features/today/TodayScreen.vue`
  - added typed Attendance accessible label `Посещаемость` while preserving
    visible label `Учёт`.
- `frontends/mobile-core/tests/navigation.test.mjs`
  - added observable stack and initial/cross-root/replace history regressions.
- `.agent/student-role-02/shared-shell/mobile-shell-lifecycle-probe.mjs`
  - added actual compiled Vue component coverage for external stack, host
    lifecycle, and accessible DOM attributes.
- `.agent/student-role-02/shared-shell-repair/**`
  - this packet, evidence, checks, and handoff records.

## Preserved pre-existing work

The dirty foundation files `frontends/mobile-core/package.json`,
`src/features/today/TodayScreen.vue` (outside the nav declaration),
`src/features/today/today-screen.pcss`, `src/index.ts`, the existing shared
component files, and `.agent/student-role-02/shared-shell/**` were present in
the assigned worktree before this repair. They were not reset, rewritten, or
committed. The repair only extends the declared shared-shell/Today scope.

## Exclusions

No App, backend, API/generated type, adapter, package, lockfile, Figma source,
host SDK, or known fixture lint file was changed. No commit or runtime server
was created.
