# Scoped diff

## Added

- `frontends/mobile-core/src/shared/navigation.ts` — typed roots, nested routes,
  nav item tuple, and navigation stack.
- `frontends/mobile-core/src/shared/host.ts` — optional host callback adapter.
- `frontends/mobile-core/src/shared/shell-contract.ts` — pure dock/Back policy.
- `frontends/mobile-core/src/shared/components/MobileBottomNav.vue` and
  `mobile-bottom-nav.pcss` — reusable five-slot dock.
- `frontends/mobile-core/src/shared/components/MobileShell.vue` and
  `mobile-shell.pcss` — route-aware shell, host lifecycle wiring, and explicit
  shared typography inheritance.
- `frontends/mobile-core/tests/navigation.test.mjs` — stack and shell policy
  contract coverage.

## Updated

- `frontends/mobile-core/src/features/today/TodayScreen.vue` — wraps the
  existing Today content in `MobileShell`, supplies the five nav items, and
  preserves check-in/date events.
- `frontends/mobile-core/src/features/today/today-screen.pcss` — removes the
  duplicated Today dock styles and keeps the title layout stable at full width.
- `frontends/mobile-core/src/index.ts` — exports the shared shell APIs.

## Preserved

- Existing Today data loading, loading/error/empty states, check-in behavior,
  semester schedule, props, and emitted `checkin`/`selectDate` events.
- Existing SVG assets and PCSS token usage.

## Known limitations

- Other root screens and app-level route ownership are intentionally out of
  scope; their dock entries remain disabled until their contracts exist.
- The canonical Figma blur variable is `25`; `get_design_context` may show `12.5`
  as a CSS conversion representation. No blur token discrepancy was changed.
  Role-gradient decisions remain outside this scope.
- Full lint remains blocked by the unrelated pre-existing `no-this-alias` error
  at `src/test-adapter/fixture-transport.test.ts:94`.
- The shell probe is retained under `.agent/student-role-02/shared-shell` as
  runtime evidence for the Vue Boolean default correction; it is not product
  source.
