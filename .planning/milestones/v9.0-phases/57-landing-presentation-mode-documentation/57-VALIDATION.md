---
phase: 57
slug: landing-presentation-mode-documentation
status: ready
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-13
updated: 2026-04-13
---

# Phase 57 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | grep-based smoke + manual visual UAT (Chrome DevTools). Static HTML + Markdown — dedicated unit test framework не применим. |
| **Config file** | — (landing); для regression reference — `frontends/web-panel/vitest.config.ts` не затрагивается фазой 57 |
| **Quick run command** | `bash .planning/phases/57-landing-presentation-mode-documentation/scripts/smoke.sh` (inline, см. per-task automated) |
| **Full suite command** | Комбинация всех Task-level automated греп-проверок + human UAT на viewport 360/768/1440px |
| **Estimated runtime** | ~5 секунд для smoke, ~10 минут для human UAT |

---

## Sampling Rate

- **After every task commit:** Run task's `<automated>` grep command (< 2 sec)
- **After every plan wave:** Run combined smoke (plan 01 + plan 02 smoke: `./smoke.sh`)
- **Before `/gsd-verify-work`:** Plan 03 human UAT approved + all smoke green
- **Max feedback latency:** 10 сек для auto проверки; 10 мин для human UAT (visual)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 57-01-T1 | 01 | 1 | LAND-v9-02 | T-57-03, T-57-10 | HTML секции + CSS token-based без хард-кодед hex | smoke | `grep -c 'id="architecture-flow"' frontends/landing/dist/index.html` = 1 AND `grep -c 'data-step='` = 6 | frontends/landing/dist/index.html | ⬜ pending |
| 57-01-T2 | 01 | 1 | LAND-v9-02 | T-57-04 | GSAP pin+scrub desktop / batch mobile / reduced-motion fallback | smoke | `grep -c "trigger: '#architecture-flow'"` = 1 AND `grep -c "ScrollTrigger.batch('.arch-step'"` = 1 | frontends/landing/dist/index.html | ⬜ pending |
| 57-01-T3 | 01 | 1 | LAND-v9-05 | — | 4 role cards, обновлённая headman карточка | smoke | `grep -c 'data-role="headman"'` = 1 AND `grep -q "Web-кабинет"` | frontends/landing/dist/index.html | ⬜ pending |
| 57-02-T1 | 02 | 1 | DOCS-v9-01 | T-57-07 | CLAUDE.md статус актуален + URL Layout раздел | smoke | `! grep -q "v6.0\*\*: В РАБОТЕ" CLAUDE.md` AND `grep -q "## URL Layout" CLAUDE.md` | CLAUDE.md | ⬜ pending |
| 57-02-T2 | 02 | 1 | DOCS-v9-02 | T-57-06 | url-layout.md Production Path Routing таблица | smoke | `grep -q "## Production Path Routing" docs/url-layout.md` AND 8 путей | docs/url-layout.md | ⬜ pending |
| 57-02-T3 | 02 | 1 | DOCS-v9-03 | T-57-08 | 18 web-cabinet историй с traceability | smoke | `grep -cE "JS-(STUDENT\|HEADMAN)-WEB-" docs/job-stories.md` ≥ 18 | docs/job-stories.md | ⬜ pending |
| 57-02-T4 | 02 | 1 | DOCS-v9-04 | — | PROJECT.md ship-ready mark | smoke | `grep -q "ship-ready after Phase 57" .planning/PROJECT.md` | .planning/PROJECT.md | ⬜ pending |
| 57-03-T1 | 03 | 2 | LAND-v9-04 | T-57-09, T-57-10 | HTML structural integrity + reduced-motion fallback | smoke | section balance + `arch-step` в reduced-motion блоке | frontends/landing/dist/index.html | ⬜ pending |
| 57-03-T2 | 03 | 2 | LAND-v9-02, LAND-v9-04 | T-57-04 | Responsive 360/768/1440 + dark/light + reduced-motion + scroll animation | manual-only | Chrome DevTools device toolbar + Rendering panel | — | ⬜ pending |
| 57-03-T3 | 03 | 2 | DOCS-v9-01 | T-57-11 | phase-57-report.md создан со всеми requirement IDs | smoke | `test -f docs/phase-57-report.md` AND grep 7 req IDs | docs/phase-57-report.md | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

**Нет Wave 0 задач.** Фаза 57 не вводит новую тестовую инфраструктуру: лендинг — static HTML без build pipeline, правки Markdown не требуют тестов. Все автоматические проверки — это grep smoke tests, встроенные в `<automated>` блоки каждой задачи. `wave_0_complete: true` потому что waves 0 не существует.

Regression reference: `cd frontends/web-panel && npm test` (162 vitest) — не затрагивается фазой 57, фаза не вносит правок в Angular код. Если executor хочет паранойю-запустить как sanity — команда есть, но не требуется.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Scroll-driven анимация секции `#architecture-flow` на desktop | LAND-v9-02 | Визуальное воспроизведение scroll поведения; headless тестирование pin+scrub требует playwright + нестабильно | Chrome 1440×900, прокрутить секцию, убедиться что: секция пинится, шаги появляются последовательно, стрелки прорисовываются, pin отпускает после последнего шага. См. plan 03 Task 2 step D. |
| Dark/light визуальная проверка | LAND-v9-04 | Требует человеческого глаза для contrast / читаемости | Theme toggle → 360/1440px → визуальная оценка секции `#architecture-flow` и `#roles` в обеих темах. См. plan 03 Task 2 step B. |
| Responsive 360px / 768px / 1440px | LAND-v9-04 | DevTools device toolbar — human-in-the-loop | Chrome DevTools → Toggle device toolbar → каждый из 3 размеров → убедиться нет overflow, тексты читаемы. См. plan 03 Task 2 step A. |
| `prefers-reduced-motion: reduce` fallback | LAND-v9-02 | Требует DevTools emulation + визуальной проверки, что секция статична | DevTools Rendering panel → `prefers-reduced-motion` = reduce → reload → проверить static layout. См. plan 03 Task 2 step C. |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or are marked manual-only with clear step-by-step instructions
- [x] Sampling continuity: каждый commit получает минимум 1 automated grep; нет 3 подряд без automated verify
- [x] Wave 0 covers all MISSING references (нет MISSING — фаза не требует нового тест-framework)
- [x] No watch-mode flags (`vitest --watch`, etc.)
- [x] Feedback latency target documented (< 10 сек smoke, ~10 мин UAT)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** ready
