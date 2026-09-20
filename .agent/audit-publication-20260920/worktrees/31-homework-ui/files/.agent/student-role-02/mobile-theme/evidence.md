# Mobile theme evidence

Captured 07.09.2026 on Windows/PowerShell, Node `v24.14.0`, Edge headless, DPR1. Baseline and branch are recorded in [`contract.md`](contract.md). The worktree is intentionally uncommitted; existing owner changes were inspected and preserved.

## Canonical source evidence

- `docs/design/tokens-v2.json:343-423` defines semantic `dark`/`light` modes and the existing `color/text/on-fill-strong` role, mapped to `color/neutral/0` in both modes.
- `docs/design/COMPONENT_REGISTRY.md:206` defines the three-value ThemeSwitcher with system as the default source.
- `docs/design/figma-spec-mobile.md:236-266` fixes the mobile dock anatomy, dark/light opacity (78/90) and 20px blur source decision. The implementation keeps the accepted 1.5625rem mobile value.
- [`source-hashes.json`](source-hashes.json) records all seven frozen Homework source asset hashes as `MATCH`; the two new derivatives contain only the original chevron path and `viewBox`.

## Recorded defect gates and corrections

1. **Today light contrast.** Before correction, the actual fixture at 390×844 measured `.today-role` `1.01:1` and `.today-hero__action` `1.00:1` in explicit light and system-light, while dark measured `7.90:1` and `11.79:1`. Reproduction JSON and screenshots are in [`today-defect-reproduction.json`](today-defect-reproduction.json) and `screenshots/today-defect-*.png`. Root accepted the bounded correction: map both controls to the existing canonical `color/text/on-fill-strong` semantic role (`#ffffff` in both modes), leaving the fixed gradient stops and composition unchanged.
2. **Compound Homework disclosure mask.** Root reproduced that the opaque rounded rect inside `homework-expand.svg`/`homework-return-today.svg` swallowed the chevron when the full SVG was used as one alpha mask. The correction imports `homework-expand-chevron.svg` and `homework-return-today-chevron.svg` for the path layer and leaves the original source assets unchanged. The existing surface is rendered separately by `.homework-expand`/`.homework-return-today`.
3. **Same-tick stale retry.** Root's isolated reproduction in `../homework-owner-retry-probe/evidence.md` showed a failed student-A command replayed under student-B before the scope watcher flushed. `retryCompletion` now compares the saved command scope synchronously before calling `submitCompletion`; `use-homework.test.ts` covers the same-tick owner replacement.

## Correction runtime

- [`today-evidence.json`](today-evidence.json), produced by `node .agent/student-role-02/mobile-theme/today-probe.mjs .agent/student-role-02/mobile-theme/today-evidence.json today`, exited `0`. At 390×844/DPR1, Onest was loaded and `scrollWidth === 390` for explicit dark/light and system dark/light at root 16px and explicit dark/light at 20/24px. Light and dark role/action minimum sampled gradient contrast is `9.39:1`/`14.02:1`; hero/accent is `7.10:1` light and `8.57:1` dark. Explicit light stayed stable when the emulated OS changed; system changed and restored in both directions. Dock opacity/blur are `78%`/`90%` and `1.5625rem` (computed 25px at root 16px).
- [`runtime-evidence.json`](runtime-evidence.json), produced by the Homework theme probe after the icon correction, exited `0` and records dark/light/system palette values, Onest, no overflow, foreground masks, completion handles and dock blur at 390×844 with root 16/20/24px captures. The path-only chevrons are visible in the corresponding screenshots.
- [`focus-evidence.json`](focus-evidence.json), produced by [`focus-probe.mjs`](focus-probe.mjs), records activeElement as the same completion button at 0/1/20/100/500ms after Space ACK reorder. The isolated focus unit test is included in the full Vitest run.

## Limitations

These are deterministic shared-component fixtures, not real PWA/TMA shell or backend/API runs. The parent owns adapter wiring, real authenticated scope, production service scenarios, full role acceptance and fresh independent Sol high review. `generate:types:check` remains a pre-existing generated mobile-BFF drift outside this frontend-only scope; it is recorded as BLOCKED in `checks.md` and no generated file was changed.
