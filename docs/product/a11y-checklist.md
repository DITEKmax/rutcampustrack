# A11y Checklist — RutCampusTrack

Статус: **WCAG 2.1 AA baseline** (M07 G10, 2026-04-22).
Scope: frontend (PWA React, web-panel Angular, landing static HTML).
Acceptance criteria M07 (D1 решение 3): **CRITICAL + SERIOUS = 0**.
MODERATE/MINOR — «a11y pass 2» в v0.1.

---

## Pass 1 — M07 baseline (**выполнено**)

### Глобальная структура

- [x] **Landing** — `<main>` в `<body>`, headings-иерархия h1→h2→h3
      (M07 G2 закрыл landing meta + prefers-reduced-motion).
- [x] **PWA** — `<main ref={mainRef}>` в `AppShell`; все нагруженные
      через `<Outlet />` страницы живут под `<main>`. Drawer/sheet
      компоненты используют `role="dialog" aria-modal="true"
      aria-label="..."`.
- [x] **web-panel** — `<main class="shell__main">` в `ShellComponent`
      (Phase 49), все feature routes рендерятся внутри.

### Навигация

- [x] **PWA BottomNav** — `<nav role="navigation"
      aria-label="Основная навигация">`.
- [x] **PWA DrawerMenu** — `<aside role="dialog" aria-modal="true">`.
- [x] **web-panel Shell** — `<nav>` в header + sidebar, `<a
      routerLink>` вместо кликабельных `<div>`.

### Интерактивные элементы

- [x] **Buttons** — все `<button type="button">` с `aria-label`
      или текстом; iconn-only кнопки обязательно с `aria-label`
      (спецпроверка grep: 66 aria-label в PWA, 131 в web-panel).
- [x] **Links** — `<a routerLink>` / `<NavLink>` с descriptive text.
      Нет bare `<div onClick>` в hot-path UI (admin-dashboard
      `<a routerLink>` обёртки для stat-cards — BUG-005).
- [x] **Geolocation CTA** (`CheckInButton`) — `aria-busy`,
      `aria-label="Отметиться"`, focus-ring через `focus-visible`.

### Уведомления и обратная связь

- [x] **Error toasts (M07 G4)** — `role="status"` / `role="alert"`
      через MatSnackBar (web-panel) и Motion-toast (PWA).
- [x] **Notifications list** (PWA) — `role="list"` + `<li>` per item
      + `aria-label` describing actionable state.
- [x] **Sparklines placeholder** (M07 G9) — `role="status"
      aria-live="polite"` на admin-dashboard placeholder.

### Keyboard navigation

- [x] **Focus management** — Drawer/Sheet компоненты ловят Escape,
      BottomSheet (M07 G7) включает focus-trap через MatDialog
      (web-panel) и useEffect для Escape handler (PWA).
- [x] **Skip-to-main** — **отложено** в pass 2 (для PWA обычно не
      критично, т.к. BottomNav фиксирована и доступна сразу).

### Motion и prefers-reduced-motion

- [x] **Landing** — `@media (prefers-reduced-motion: reduce)` global
      override (M07 G2).
- [x] **PWA AppShell** — `useReducedMotion()` в route transitions.
- [x] **PWA DrawerMenu / BottomSheet** — `useReducedMotion()` через
      `motion/react`, fade вместо slide для reduce.
- [x] **Check-in pulse animation** — media query `prefers-reduced-
      motion: reduce { animation: none }` (CheckInButton inline
      style block).

### Форма checkin / excuse / homework

- [x] **LessonActionsSheet Excuse** — все inputs с `<label>` или
      `aria-label`. `<textarea>` — `aria-label`-attribute + maxLength
      counter.
- [x] **ConfirmWithReasonDialog** (web-panel, M07 G7) — textarea
      с `matFormField` label.

### Semantic HTML

- [x] **web-panel admin-dashboard** — `<section>`, `<header>`,
      `<table>` + `<thead>/<tbody>` для recent-groups table.
- [x] **PWA ProfilePage** — `<section>` + `<article>` для attendance
      groups.
- [~] **PWA HomeDashboard, SchedulePage, HomeworkPage, CheckInScreen,
      NotificationsPage** — выполняют роль `<main>` child через
      `<div>` wrappers. Landmark-иерархия от AppShell `<main>`
      достаточна для screen-reader'ов (добавление явных `<section>`
      — pass 2).

---

## Pass 2 — v0.1 (**отложено**)

### Audit tools

- [ ] **`@axe-core/cli`** run на dev PWA + web-panel + landing.
      Acceptance: **CRITICAL + SERIOUS = 0**. MODERATE/MINOR
      перечислить и закрыть here.
  - Требует running dev server — интеграция в M08 Playwright e2e.
- [ ] **ESLint a11y plugins:**
  - `eslint-plugin-jsx-a11y` для PWA (новый ESLint config).
  - `@angular-eslint/eslint-plugin-template` с accessibility rules
    для web-panel.
  - Запустить CI gate в M08.
- [ ] **Lighthouse A11y audit** — score ≥ 95 для каждой frontend
      поверхности (landing, PWA home, web-panel dashboard).

### Semantic HTML pass 2

- [ ] PWA HomeDashboard, SchedulePage, HomeworkPage, CheckInScreen,
      NotificationsPage — `<section>`/`<article>` wrappers вокруг
      content blocks.
- [ ] PWA StatsPage (headman) — перейти на `<table>` вместо
      `<div>`-grid для per-student statistics.
- [ ] web-panel **journal tables** — `<th scope="col">` / `scope="row"`
      для каждой колонки/строки.

### Skip-links

- [ ] PWA: `<a href="#main" class="skip-link">Пропустить навигацию</a>`
      в `<body>` (видим только через :focus). Важно для screen-reader
      users, идущих по tab-order.
- [ ] web-panel: аналогичный skip-link в shell.

### Form labels

- [ ] web-panel admin `<form>` surveys — заменить Material
      `<input>` inside `<mat-form-field>` на explicit `<label>` +
      `aria-describedby` для hints/errors (MatFormField уже
      генерит `<label>`, но нужно проверить что `for` matches `id`).
- [ ] PWA Login form — убедиться, что все inputs имеют `for`/`id`
      pair (не только `aria-label`).

### Color contrast

- [ ] WCAG AA contrast check для всех `var(--text-*)` токенов поверх
      `var(--bg-*)` в dark/light/transit themes. Используем
      `color-mix(in oklab, ...)` — нужна формальная проверка через
      Stark / axe-color-contrast.
- [ ] Landing hero gradient — text over image contrast ratio.

### SMIL → CSS keyframes

- [ ] `frontends/landing/index.html` — оставшиеся 3 `<animate>` SMIL
      элемента заменить на CSS keyframes где возможно, или оставить
      с `svg.pauseAnimations()` fallback для prefers-reduced-motion
      (уже реализовано в G2).

### Touch targets

- [ ] WCAG 2.5.5 — minimum 44×44 CSS px для всех interactive
      elements. Проверить brandbook compliance (`min-h-[44px]` уже
      применён в PWA WeekDayTabs и DrawerMenu). Audit остальные
      compact-UI.

### Screen reader testing

- [ ] NVDA + VoiceOver manual test для критических flow:
  - PWA login → check-in → отметиться.
  - web-panel admin login → create user → create group.
  - PWA headman → open action sheet → submit excuse.

### Heading иерархия

- [ ] Проверить каждую route-level страницу: ровно один `<h1>`
      (обычно page title), `<h2>` для section headings, `<h3>` для
      подразделов. Нет перепрыгиваний (h1 → h3).

### Live regions

- [ ] Network status banner (PWA `OfflineBanner`) — `role="status"
      aria-live="polite"` для автообновления.
- [ ] STOMP notification arrival — `role="status"` для
      динамически вставляемых уведомлений в NotificationCenter.

---

## Инструменты и команды

### Local axe-core run (для v0.1 pass 2)

```bash
# Установка
npm install -g @axe-core/cli

# PWA
cd frontends/pwa && npm run dev &
npx axe http://localhost:5173 --exit

# web-panel
cd frontends/web-panel && npm start &
npx axe http://localhost:4200 --exit

# landing
cd frontends/landing && py -m http.server 8080 &
npx axe http://localhost:8080 --exit
```

Интерпретация severity:
- **CRITICAL** — screen reader полностью сломан (missing `<main>`,
  duplicate ids, invalid ARIA). **Must fix before release.**
- **SERIOUS** — существенное влияние на UX (no alt text, color
  contrast fail, no keyboard handler). **Must fix before release.**
- **MODERATE** — umbrella WCAG violation без critical impact
  (non-unique landmarks, nested interactive). **Pass 2 (v0.1).**
- **MINOR** — best practice без functional impact. **Pass 2
  (v0.1).**

### Reference — WCAG 2.1 AA criteria

[W3C WCAG 2.1 AA](https://www.w3.org/WAI/WCAG21/quickref/?currentsidebar=%23col_customize&levels=aaa)
— авторитетный чеклист. RutCampusTrack стремится к AA, AAA — non-goal
(типовой requirement для public-facing приложений).

---

_Обновляется при каждом a11y fix'е. Last updated: 2026-04-22
(M07 G10 baseline)._
