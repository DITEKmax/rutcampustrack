---
phase: 57-landing-presentation-mode-documentation
plan: 01
subsystem: landing
tags:
  - landing
  - gsap
  - animation
  - scrolltrigger
  - ui
dependency_graph:
  requires: []
  provides:
    - "#architecture-flow section in frontends/landing/dist/index.html"
    - "Updated headman role card (v9.0 capabilities)"
  affects:
    - frontends/landing/dist/index.html
tech_stack:
  added: []
  patterns:
    - "GSAP ScrollTrigger pin+scrub timeline (desktop ≥1024px)"
    - "ScrollTrigger.batch reveal (mobile <1024px)"
    - "gsap.matchMedia() prefers-reduced-motion static fallback"
    - "CSS design-token-only styling (var(--...))"
key_files:
  created: []
  modified:
    - frontends/landing/dist/index.html
decisions:
  - "Arch-flow animation wrapped inside existing ScrollTrigger !== undefined guard — consistent with existing reveal pattern"
  - "isArchDesktop uses window.matchMedia instead of CSS-only breakpoint — needed to branch JS logic at runtime"
  - "arch-arrow uses data-to attribute (not data-from) so GSAP selector targets are unique and predictable"
  - "Headman card updated with 5 bullets (was 4) to accommodate all v9.0 HEAD-WEB-* capabilities"
metrics:
  duration: "~12 minutes"
  completed_date: "2026-04-13"
  tasks_completed: 3
  files_modified: 1
requirements_closed:
  - LAND-v9-02
  - LAND-v9-05
---

# Phase 57 Plan 01: Architecture Flow Section + Headman Card Update Summary

**One-liner:** GSAP ScrollTrigger pin+scrub 6-step architecture flow section (`#architecture-flow`) added to landing, with mobile batch-reveal fallback and reduced-motion static mode; headman role card updated to 5 v9.0 bullets.

## What Was Built

### Task 1 — HTML + CSS for `#architecture-flow`

New `<section id="architecture-flow">` inserted between `#how-it-works` and `#roles` sections in `frontends/landing/dist/index.html`. Contains:
- 6 `<li class="arch-step" data-step="N">` elements representing: git commit → CI/CD → backend microservices → RabbitMQ → WebSocket/Push → user device
- 5 `<span class="arch-arrow" data-to="N">` arrow connectors between steps

CSS block (`/* ============ ARCHITECTURE FLOW (Phase 57 / LAND-v9-02) ============ */`) added before `.how-it-works` CSS, using only design tokens (`var(--bg-secondary)`, `var(--bg-elevated)`, `var(--border-subtle)`, `var(--accent-primary)`, `var(--text-primary)`, `var(--text-secondary)`, `var(--gradient-brand)`). Desktop layout uses `grid-template-columns: repeat(11, 1fr)` (6 steps + 5 arrows).

### Task 2 — GSAP matchMedia Extension

Inside the existing `mm.add('(prefers-reduced-motion: no-preference)', ...)` block, after the feature card hover listener, added `// ---- ARCHITECTURE FLOW (Phase 57 / LAND-v9-02) ----` sub-block:

- **Desktop (≥1024px):** `gsap.timeline({ scrollTrigger: { trigger: '#architecture-flow', start: 'top top', end: '+=500%', scrub: 0.6, pin: true, pinSpacing: true, anticipatePin: 1 } })` — sequentially reveals steps 1–6 and draws arrows between them
- **Mobile (<1024px):** `ScrollTrigger.batch('.arch-step', { start: 'top 85%', once: true, onEnter: ... })` — batch reveal with stagger

Inside the existing `mm.add('(prefers-reduced-motion: reduce)', ...)` block, added one line:
```js
gsap.set('.arch-step, .arch-arrow', { opacity: 1, y: 0, scaleX: 1 });
```

No new `window.addEventListener('load', ...)` created. No `markers: true` left in.

### Task 3 — Headman Role Card Update (LAND-v9-05)

Replaced the `<ul>` inside `data-role="headman"` card with 5 v9.0-aligned bullets:
- Журнал группы и массовая отметка (HEAD-WEB-05)
- Управление предметами и преподавателями (HEAD-WEB-04)
- Рассмотрение excuse-тикетов и late-check-in (HEAD-WEB-06/07)
- Порог красной зоны per-subject (HEAD-WEB-08)
- Web-кабинет + вкладка «Группа» в PWA (PWA-HEAD-01)

Other three cards (student/teacher/admin) untouched.

## Commits

| Task | Commit | Files |
|------|--------|-------|
| Task 1 + 2 + 3 (all in one file) | b4588c4 | frontends/landing/dist/index.html |

## Deviations from Plan

None — plan executed exactly as written. All three tasks targeted the same file (`frontends/landing/dist/index.html`) and were committed together.

## Known Stubs

None. The section renders static HTML with real content. GSAP animation is fully wired.

## Threat Flags

No new threat surface beyond what was already covered in the plan's threat model (T-57-01 through T-57-05). No new network endpoints, auth paths, or file access patterns introduced.

## Self-Check: PASSED

- `frontends/landing/dist/index.html` exists: FOUND
- Commit `b4588c4` exists: FOUND
- `id="architecture-flow"`: 1 occurrence
- `trigger: '#architecture-flow'`: 1 occurrence
- All 4 `data-role` HTML article elements present exactly once each
- `markers: true`: 0 occurrences
- `window.addEventListener.*load`: 1 (no duplicate)
- Section open/close balance: 6/6
