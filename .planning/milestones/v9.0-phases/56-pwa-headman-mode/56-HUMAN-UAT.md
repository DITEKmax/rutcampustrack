---
status: partial
phase: 56-pwa-headman-mode
source: [56-VERIFICATION.md]
started: 2026-04-13T16:35:00Z
updated: 2026-04-13T16:35:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Rapid-tap journal segments + network failure (WR-06 revert pattern)
expected: При быстрых повторных тапах по сегментам в `/group/journal` и принудительном offline/500 от backend оптимистичный UI откатывается к предыдущему значению; previous-state не теряется при серии мутаций
result: [pending]

### 2. End-to-end navigation /group/*
expected: Все 7 секций (`/group/overview`, `/students`, `/subjects`, `/journal`, `/excuses`, `/late-checkin`, `/stats`) открываются через GroupHub, кнопка «Назад» возвращает на хаб, BottomNav «Группа» подсвечивается на всех вложенных страницах
result: [pending]

### 3. DevTools Cache Storage inspection
expected: После открытия headman-страниц в DevTools → Application → Cache Storage появляется кэш `headman-api-cache-v1`, в нём GET-запросы /api/academic/* и /api/schedule/* (TTL 24h, max 100 записей)
result: [pending]

### 4. Threshold edit persistence + Motion success animation
expected: Inline-редактор порога красной зоны в `SubjectStatsCard` сохраняет новое значение, отображает Motion success-анимацию, и после refresh страницы значение остаётся
result: [pending]

### 5. BottomNav visual layout + Motion layoutId animation
expected: На headman-аккаунте видна 5-я вкладка «Группа» перед «Профиль», layoutId-анимация активного индикатора плавно перемещается между вкладками; на student-аккаунте вкладка «Группа» отсутствует
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps
