---
phase: 53
slug: student-web-cabinet-excuses-late-checkin-pwa
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-09
---

# Phase 53 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest 3.2.4 + @testing-library/angular 17.4.0 |
| **Config file** | `frontends/web-panel/vitest.config.ts` |
| **Quick run command** | `cd frontends/web-panel && npm test` |
| **Full suite command** | `cd frontends/web-panel && npm test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd frontends/web-panel && npm test`
- **After every plan wave:** Run `cd frontends/web-panel && npm test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 53-01-01 | 01 | 0 | STU-WEB-07 | — | N/A | unit | `cd frontends/web-panel && npm test` | ❌ W0 | ⬜ pending |
| 53-01-02 | 01 | 0 | STU-WEB-08 | — | N/A | unit | `cd frontends/web-panel && npm test` | ❌ W0 | ⬜ pending |
| 53-01-03 | 01 | 0 | STU-WEB-10 | — | N/A | unit | `cd frontends/web-panel && npm test` | ❌ W0 | ⬜ pending |
| 53-02-01 | 02 | 1 | STU-WEB-07 | — | Валидация file type/size на клиенте | unit | `cd frontends/web-panel && npm test` | ✅ W0 | ⬜ pending |
| 53-02-02 | 02 | 1 | STU-WEB-07 | — | Graceful 404 → success snackbar alt text | unit | `cd frontends/web-panel && npm test` | ✅ W0 | ⬜ pending |
| 53-03-01 | 03 | 1 | STU-WEB-08 | — | Только absent записи попадают в поздние отметки | unit | `cd frontends/web-panel && npm test` | ✅ W0 | ⬜ pending |
| 53-04-01 | 04 | 1 | STU-WEB-10 | — | Banner скрыт при pwa-banner-dismissed=true | unit | `cd frontends/web-panel && npm test` | ✅ W0 | ⬜ pending |
| 53-04-02 | 04 | 1 | STU-WEB-10 | — | Banner не рендерится для TEACHER/ADMIN | unit | `cd frontends/web-panel && npm test` | ✅ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `frontends/web-panel/src/app/features/student/excuses/student-excuses.component.spec.ts` — stubs for STU-WEB-07
- [ ] `frontends/web-panel/src/app/features/student/late-checkin/student-late-checkin.component.spec.ts` — stubs for STU-WEB-08
- [ ] `frontends/web-panel/src/app/layout/shell/student-pwa-banner/student-pwa-banner.component.spec.ts` — stubs for STU-WEB-10

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `beforeinstallprompt` на Chrome/Edge показывает нативный диалог | STU-WEB-10 | Browser API не эмулируется в Vitest | Открыть в Chrome DevTools, эмулировать событие, нажать кнопку в баннере |
| iOS Safari показывает инструкцию "поделиться" | STU-WEB-10 | iOS-специфичный браузер | Открыть в Safari на iPhone/симуляторе, проверить текст баннера |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
