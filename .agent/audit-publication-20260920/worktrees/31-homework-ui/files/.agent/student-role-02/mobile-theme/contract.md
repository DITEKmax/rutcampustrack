# Shared mobile theme и bounded Homework repair — compact contract

Revision baseline: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`  
Worktree: `.agent/worktrees/student-role-02/homework-ui`  
Branch: `codex/student-role-02-homework-ui`  
Risk: S2. Sole writer: fresh `mobile_theme_repair_fresh` leaf, Luna/max. No commit or integration is performed here.

## 1. Goal

Добавить в shared `frontends/mobile-core` caller-owned поддержку semantic light/dark/system и завершить согласованные исправления Homework для клавиатурного фокуса и compound disclosure icons. Today, Homework и bottom navigation должны читать одну theme foundation без изменения композиции.

## 2. Context/evidence

Root froze the mobile-theme packet in `.agent/student-role-02/mobile-theme-implementation-packet.md`. Canonical semantic modes and `color/text/on-fill-strong` are in `docs/design/tokens-v2.json:343-423`; the accepted system default is in `docs/design/COMPONENT_REGISTRY.md:206`. Mobile nav opacity is dark 78/light 90 in `docs/design/figma-spec-mobile.md:236-266`. Root reproduced three bounded defects: only dark tokens in the source; Today light text on fixed dark gradients measured `1.00–1.01:1`; and complete 44×44 Homework expand/return SVGs became opaque circles when used as alpha masks. A same-tick stale Homework retry was separately reproduced and authorized for this repair.

## 3. Relevant scope

Own only shared mobile-core theme binding, semantic token aliases, Today and MobileBottomNav color/icon binding, the Homework focus/mask corrections, focused tests, and evidence under this directory. The inherited Homework feature/domain implementation remains in the same frozen worktree and is preserved. The scoped fixture may import the real shared components and runs only on ports 5181/5182.

## 4. Required behavior

- Expose a minimal `createMobileTheme` controller with explicit `light`, `dark` and `system` modes, system media updates, deterministic listener cleanup, safe absent-DOM behavior, target ownership and restoration of the caller-owned attribute/style.
- Keep explicit modes stable across OS media changes; system follows media and can switch back after an explicit mode. Keep `color-scheme` coherent.
- Map existing canonical semantic primitives to both palettes. Use `color/text/on-fill-strong` for the fixed dark Today role/action gradients; do not invent a hue or derive a screenshot color.
- Preserve layout, dimensions, SVG `viewBox`/paths and accepted navigation behavior. Use CSS masks/currentColor only where the existing asset permits it. Split compound Homework SVGs into a surface layer and a path-only derivative so the disclosure chevron remains visible in both themes.
- After a confirmed completion/reversal ACK and reorder, restore focus to the same assignment control. Do not steal focus after the user moves elsewhere, and do not restore stale feed/owner focus. A failed retry command may only be replayed for its saved scope identity.

## 5. Constraints

Use strict TypeScript, Vue and PostCSS/rem conventions from `frontends/AGENTS.md`. Preserve the frozen source asset hashes, SVG geometry, UI15 behavior and foreign worktree changes; mutate only the explicitly authorized shared files. Do not change API/generated contracts, adapters, auth/session semantics, package or lock files, shell policy, Telegram APIs, persistence, Figma files or unrelated roles. Persistence and product theme controls remain adapter work.

## 6. Existing patterns

Use semantic token names from `tokens-v2.json`, `MobileShell`/`MobileBottomNavItems`, existing Vue lifecycle ownership and Vitest. Both product bootstraps already load `@fontsource-variable/onest`; fixtures must await `document.fonts.ready`. The Homework query/completion owner already captures scope and validates ACKs; the new retry guard is synchronous and narrow.

## 7. Acceptance criteria

- Explicit light/dark and default/system expose distinct canonical palettes; media changes affect system only; cleanup leaves no owned attribute/listener leak.
- Today and the five Homework states have usable controls, focus and contrast at 390×844, with Onest loaded, no horizontal overflow, and root fonts 16/20/24px. Role/action gradient text meets 4.5:1 at every sampled stop/interpolation; primary/secondary/status/accent measurements remain passing.
- Homework completion/reversal preserves assignment focus after ACK, respects a deliberate focus move and rejects stale owner retries; compound disclosure chevrons remain visible.
- Focused/full frontend checks are green; runtime evidence, diff and limitations are recorded. No critical finding is left unresolved in this leaf. Parent still owns adapter/API integration and independent Sol review.

## 8. Verification

Run focused and full Vitest, strict typecheck, lint, PWA/TMA builds, fixture and contract checks, `git diff --check`, and `node --check` for probes. Run deterministic Edge/Playwright fixtures at 390×844 DPR1 with explicit/system theme transitions, loaded Onest and root 16/20/24px. Record revision, commands, exit codes, environment, screenshots, source hashes and limitations in `evidence.md`, `checks.md`, `diff.md` and `summary.md`.

## 9. Do not

Do not reset or overwrite foreign work, commit/integrate/deploy, mutate generated API files, broaden the palette/design system, add a theme chooser, persist preferences, use arbitrary inversion/filters, claim real PWA/TMA/Telegram acceptance, or treat a fixture as full-role runtime evidence.
