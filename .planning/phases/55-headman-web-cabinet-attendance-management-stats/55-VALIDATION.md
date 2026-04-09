---
phase: 55
slug: headman-web-cabinet-attendance-management-stats
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-09
---

# Phase 55 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest + @testing-library/angular (Angular) / JUnit (Backend) |
| **Config file** | `frontends/web-panel/vitest.config.ts` |
| **Quick run command** | `npx vitest run --reporter=verbose` (из `frontends/web-panel/`) |
| **Full suite command** | `npx vitest run` (из `frontends/web-panel/`) |
| **Backend command** | `./gradlew :services:attendance-service:attendance-app:test` |
| **Estimated runtime** | ~30 seconds (frontend) / ~60 seconds (backend) |

---

## Sampling Rate

- **After every task commit:** Run `npx vitest run --reporter=verbose`
- **After every plan wave:** Run `npx vitest run`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds (frontend), 60 seconds (backend)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 55-01-01 | 01 | 1 | HEAD-WEB-05 | — | N/A | integration | `./gradlew :services:attendance-service:attendance-app:test --tests "*.ReportServiceTest"` | ✅ расширить | ⬜ pending |
| 55-02-01 | 02 | 1 | HEAD-WEB-05 | — | N/A | unit | `npx vitest run src/app/features/headman/journal/` | ❌ Wave 0 | ⬜ pending |
| 55-02-02 | 02 | 1 | HEAD-WEB-05 | — | N/A | unit | `npx vitest run src/app/features/headman/journal/` | ❌ Wave 0 | ⬜ pending |
| 55-03-01 | 03 | 2 | HEAD-WEB-06 | — | N/A | unit | `npx vitest run src/app/features/headman/excuses/` | ❌ Wave 0 | ⬜ pending |
| 55-03-02 | 03 | 2 | HEAD-WEB-07 | — | N/A | unit | `npx vitest run src/app/features/headman/late-checkin/` | ❌ Wave 0 | ⬜ pending |
| 55-04-01 | 04 | 2 | HEAD-WEB-08 | — | N/A | unit | `npx vitest run src/app/features/headman/stats/` | ❌ Wave 0 | ⬜ pending |
| Regression | all | — | — | — | N/A | unit | `npx vitest run` | ✅ существуют | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `frontends/web-panel/src/app/features/headman/journal/headman-journal-page.component.spec.ts` — охватывает HEAD-WEB-05 (рендер grid после выбора предмета, клик ячейки вызывает `markAttendance`, оптимистичный UI с откатом)
- [ ] `frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.spec.ts` — охватывает HEAD-WEB-06 (empty-state при 404)
- [ ] `frontends/web-panel/src/app/features/headman/late-checkin/headman-late-checkin.component.spec.ts` — охватывает HEAD-WEB-07 (empty-state при 404)
- [ ] `frontends/web-panel/src/app/features/headman/stats/headman-stats.component.spec.ts` — охватывает HEAD-WEB-08 (вычисление процентов, инлайн-порог)
- [ ] Расширить `ReportServiceTest.java` — добавить тест: `JournalCell` содержит `lessonId` в ответе `getJournal()`

*Существующие 40+ spec-файлов в `frontends/web-panel/` покрывают регрессию — инфраструктура уже установлена.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Клик по ячейке журнала циклически меняет статус в UI | HEAD-WEB-05 | Требует браузер для проверки визуального отклика | Перейти на `/headman/journal`, выбрать предмет, кликнуть на ячейку — статус должен смениться |
| Empty-state сообщение отображается корректно на `/headman/excuses` | HEAD-WEB-06 | UI копирайт | Открыть страницу, убедиться что текст "Функция находится в разработке..." виден |
| Инлайн-редактирование порога на `/headman/stats` сохраняется | HEAD-WEB-08 | Требует живого backend для проверки персистентности | Изменить порог, нажать Enter, обновить страницу — значение должно сохраниться |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
