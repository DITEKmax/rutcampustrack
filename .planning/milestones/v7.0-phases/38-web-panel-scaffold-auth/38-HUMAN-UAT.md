---
status: partial
phase: 38-web-panel-scaffold-auth
source: [38-VERIFICATION.md]
started: "2026-04-07T09:15:00.000Z"
updated: "2026-04-07T09:15:00.000Z"
---

## Current Test

[awaiting human testing]

## Tests

### 1. Full login flow visual check
expected: Login form centered on page, Material M3 theme renders correctly, nav items match role (Teacher: Журнал посещаемости, Статистика; Admin: Пользователи, Группы, Семестры, Статистика)
result: [pending]

### 2. Dark mode toggle
expected: Visual color change on toggle, `dark` class added/removed on `<html>`, preference persists in localStorage under `web-panel.theme` key across page reloads
result: [pending]

### 3. Sidebar collapse animation
expected: Sidebar animates from 240px to 64px width in 200ms ease-in-out, chevron rotates 180deg, labels hidden in collapsed mode (icon-only), collapse state persists in localStorage
result: [pending]

### 4. Viewport responsive collapse
expected: Sidebar auto-collapses when viewport width < 1024px
result: [pending]

### 5. Role guard enforcement
expected: Navigating to /admin/* as TEACHER redirects to /teacher/dashboard; navigating to /teacher/* as ADMIN redirects to /admin/dashboard
result: [pending]

### 6. Memory-only token behavior
expected: Page reload (F5) clears auth state and redirects user back to /login (tokens not persisted to localStorage/sessionStorage)
result: [pending]

## Summary

total: 6
passed: 0
issues: 0
pending: 6
skipped: 0
blocked: 0

## Gaps
