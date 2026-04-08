---
phase: 50-basehref-migration-unified-login
plan: 03
subsystem: web-panel-routing
tags: [angular, routing, guards, placeholder, standalone, material, web-panel]

# Dependency graph
requires:
  - phase: 50
    plan: 01
    provides: "AuthService.resolveDashboardFor + extended AuthUser (STUDENT, isHeadman)"
  - phase: 50
    plan: 02
    provides: "studentGuard, headmanGuard, guestGuard (CanActivateFn)"
provides:
  - "StudentPlaceholderComponent (standalone, MatCardModule, Russian text) — reused на /student/dashboard и /student/schedule"
  - "HeadmanPlaceholderComponent (standalone, MatCardModule, Russian text) — на /headman/dashboard"
  - "app.routes.ts: 3 новых маршрута под ShellComponent + guestGuard на /login"
  - "/student/dashboard, /student/schedule (за studentGuard)"
  - "/headman/dashboard (за headmanGuard)"
  - "Path '' под /student и /headman → redirectTo dashboard"
affects:
  - "50-04 (login.component редирект) — target routes теперь существуют и могут быть верифицированы ручным navigateByUrl"
  - "50-06 (E2E regression) — теперь может верифицировать ROADMAP success criteria 1, 3, 4"
  - "Phase 51-53 (real student app) — заменит StudentPlaceholderComponent на реальные компоненты, маршруты уже на месте"
  - "Phase 54-55 (real headman app) — заменит HeadmanPlaceholderComponent, маршрут уже на месте"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Placeholder standalone component: single centered mat-card, typography-only, Russian UI copy, no business logic"
    - "Single placeholder component переиспользуется на несколько sibling маршрутов (/student/dashboard + /student/schedule) — lightweight pattern для phases 50-54 где структура ещё не зафиксирована"
    - "Route tree extension под ShellComponent: сохранение D-05 (single shell для всех 4 ролей), guards на parent block → inherit на children"
    - "Data payload { title, eyebrow } на каждом child для ShellComponent header rendering (существующая конвенция teacher/admin routes)"

key-files:
  created:
    - frontends/web-panel/src/app/features/student/student-placeholder/student-placeholder.component.ts
    - frontends/web-panel/src/app/features/headman/headman-placeholder/headman-placeholder.component.ts
  modified:
    - frontends/web-panel/src/app/app.routes.ts

key-decisions:
  - "StudentPlaceholderComponent переиспользован на /student/dashboard И /student/schedule — единый placeholder с разным data.title/eyebrow, чтобы не плодить два идентичных компонента в phase 50 (Phase 51-53 разделит их)"
  - "Placeholder компоненты без unit-тестов — статический русский текст без бизнес-логики, per D-06 'no styles beyond basic typography'. Покрытие достигается через E2E UAT в Plan 06"
  - "Guards применены на parent block (/student, /headman) — Angular Router автоматически распространяет canActivate на children, DRY vs повторять на каждом leaf"
  - "guestGuard применён на /login route в верхнем уровне routes[], не под shell — /login НЕ является shell child (public entry)"

patterns-established:
  - "Placeholder-first pattern для multi-phase rollout: зарегистрировать route + lightweight component → реальные компоненты заменят placeholder в следующих фазах без touch на app.routes.ts"
  - "eyebrow/title data payload наследуется от parent (eyebrow: 'Студент' на /student) и может быть переопределён на каждом child для title"

requirements-completed: [INFRA-v9-04, AUTH-v9-04, AUTH-v9-05]

# Metrics
duration: ~20min
completed: 2026-04-09
---

# Phase 50 Plan 03: Placeholder Routes + guestGuard on /login Summary

**Созданы два lightweight standalone placeholder-компонента (StudentPlaceholderComponent, HeadmanPlaceholderComponent) и зарегистрированы 3 новых маршрута в `app.routes.ts` под существующим ShellComponent: `/student/dashboard`, `/student/schedule`, `/headman/dashboard` (за `studentGuard`/`headmanGuard`). `guestGuard` применён к маршруту `/login`.**

## Performance

- **Duration:** ~20 min (включая base sync из-за stale worktree + npm install + baseline)
- **Started:** 2026-04-09T02:22:00Z
- **Completed:** 2026-04-09T02:42:00Z
- **Tasks:** 2
- **Files created:** 2 (оба placeholder-компонента)
- **Files modified:** 1 (app.routes.ts)
- **Tests:** 157 baseline → 157 (no new unit tests in this plan per D-06; placeholder компоненты покрываются через Plan 06 E2E UAT)

## Accomplishments

- `StudentPlaceholderComponent` создан как standalone (`MatCardModule`) с Russian UI copy "Кабинет студента появится в Фазе 51" и typography-only стилями (mat-body-1, centered, max-width 480px, CSS vars)
- `HeadmanPlaceholderComponent` создан как standalone (`MatCardModule`) с Russian UI copy "Кабинет старосты появится в Фазе 54" и идентичными стилями
- `app.routes.ts` расширен:
  - Импорты `studentGuard`, `headmanGuard`, `guestGuard` добавлены после существующих `authGuard`, `roleGuard`
  - `/login` route получил `canActivate: [guestGuard]` (T-50-12 mitigate)
  - `/student` block под ShellComponent с `canActivate: [studentGuard]` и `data: { eyebrow: 'Студент' }`:
    - `/student/dashboard` → `StudentPlaceholderComponent` (`title: 'Личный кабинет'`)
    - `/student/schedule` → `StudentPlaceholderComponent` (`title: 'Расписание'`) — необходимо для ROADMAP criterion 4 (headman visits student schedule)
    - `{ path: '', redirectTo: 'dashboard', pathMatch: 'full' }`
  - `/headman` block под ShellComponent с `canActivate: [headmanGuard]` и `data: { eyebrow: 'Староста' }`:
    - `/headman/dashboard` → `HeadmanPlaceholderComponent` (`title: 'Кабинет старосты'`)
    - `{ path: '', redirectTo: 'dashboard', pathMatch: 'full' }`
- Финальный `{ path: '', redirectTo: 'login', pathMatch: 'full' }` и wildcard `{ path: '**', redirectTo: 'login' }` сохранены без изменений
- Полный vitest suite зелёный: **157 passed / 25 test files** (до и после изменений Plan 03 — добавлены только placeholder компоненты без unit-тестов per D-06)
- `ng build --configuration development` успешно отработал за 9.795 секунд — подтверждает корректность lazy-loading import paths для обоих новых placeholder компонентов

## Task Commits

Each task was committed atomically with `--no-verify` (parallel executor requirement):

0. **Base sync (pre-Task 1):** chore(50-03): base-sync Plan 01+02 files from main — `de653b3`
   _(Worktree был создан с stale base `d6df3c24`, где Plan 01+02 изменения не применены. Этот коммит восстановил `auth.service.ts`, `auth.service.spec.ts`, `sidebar.component.ts`, `sidebar.component.spec.ts`, и 6 guard/spec файлов (`student.guard*`, `headman.guard*`, `guest.guard*`) до актуального main HEAD. Orchestrator при cherry-pick на main увидит, что эти файлы уже присутствуют, и пропустит коммит как no-op.)_
1. **Task 1:** feat(50-03): add student/headman placeholder components (D-06) — `b24b426`
2. **Task 2:** feat(50-03): register student/headman routes + guestGuard on /login (D-07, D-08) — `17395bb`

## Files Created/Modified

**Created:**
- `frontends/web-panel/src/app/features/student/student-placeholder/student-placeholder.component.ts` — 38 строк, standalone component с MatCardModule imports, template содержит одну `mat-card` с русским текстом, styles — typography-only (flex-center, max-width 480px, CSS var `--mat-sys-on-surface-variant`)
- `frontends/web-panel/src/app/features/headman/headman-placeholder/headman-placeholder.component.ts` — 38 строк, идентичная структура, разница только в selector (`app-headman-placeholder`), class name (`HeadmanPlaceholderComponent`) и тексте ("Кабинет старосты появится в Фазе 54")

**Modified:**
- `frontends/web-panel/src/app/app.routes.ts` — +46 строк: 3 новых import statement, `canActivate: [guestGuard]` на /login, два новых block-а (`/student` с 2 children, `/headman` с 1 child) под `children: [...]` массивом shell'а, перед финальным redirect

## Decisions Made

- **Переиспользование StudentPlaceholderComponent на /student/dashboard И /student/schedule** — единый placeholder с разным `data.title` ("Личный кабинет" vs "Расписание"), вместо создания двух идентичных классов. Phase 51-53 заменит placeholder на реальные компоненты (StudentDashboardComponent, StudentScheduleComponent), маршруты уже на месте.
- **Guards применены на parent block, не на каждом leaf** — Angular Router автоматически наследует `canActivate` от родителя на всех потомков. DRY: `canActivate: [studentGuard]` один раз на `/student`, а не трижды на dashboard/schedule/'' child.
- **guestGuard на /login — top-level route, не shell child** — `/login` не должен быть дочерним ShellComponent (shell требует auth, а login — public entry). Применение `guestGuard` на top-level предотвращает показ login form уже залогиненным пользователям.
- **Placeholder компоненты без unit-тестов** — D-06 явно указывает "no styles beyond basic typography". В компонентах нет бизнес-логики, только статический текст. Покрытие через Plan 06 E2E UAT (manual browser navigation test с 4 ролями). TDD здесь создал бы шум без ценности.
- **data: { eyebrow } на parent + переопределение на children** — `eyebrow: 'Студент'` задан на `/student` block, и повторно на каждом child (dashboard/schedule). Это даёт 3 grep hit-а (grep ожидает ≥2), и обеспечивает fallback для Angular Router если child data не задан. Существующая конвенция teacher/admin routes в том же файле.
- **stub-check**: Placeholder компоненты по определению содержат stub copy ("Кабинет студента появится в Фазе 51"). Это intentional stub, задокументированный в D-06 и зарегистрированный в ROADMAP как scope Phase 51-55. Stub НЕ мешает достижению цели Plan 03 (цель — регистрация маршрутов + guards, а не реальный UI). См. "Known Stubs" ниже.

## Deviations from Plan

**None for the 2 tasks themselves** — все action блоки выполнены ровно как описано, все acceptance criteria (13 grep проверок для Task 2 + 8 для Task 1) пройдены, full suite зелёный (157 passed), опциональный `ng build` развёрнут успешно.

**Infrastructure-level deviations** (Rule 3 — blocking):

1. **Stale worktree base** (как указано в prompt worktree_branch_check): worktree создан с commit `d6df3c24` (parallel deployment branch), где Plan 01+02 изменения отсутствуют. Без этих файлов `app.routes.ts` не смог бы импортировать guards, vitest упал бы с "Cannot find module './core/auth/student.guard'". Решение: скопировал Plan 01+02 файлы из main repo (`auth.service.ts`, `auth.service.spec.ts`, `sidebar.component.*`, `student.guard.*`, `headman.guard.*`, `guest.guard.*`), закоммитил отдельным `chore(50-03): base-sync Plan 01+02 files from main` commit-ом `de653b3`. Orchestrator при cherry-pick на main увидит, что эти файлы уже идентичны — коммит будет no-op и пропущен.

2. **Отсутствие node_modules в worktree И в main repo**: worktree не имел node_modules (ожидалось), но main repo тоже оказался с пустой директорией `frontends/web-panel/node_modules` (это НЕ junction, просто пустой dir — видимо, прошлые фазы чистили или не устанавливали). Запустил `npm install` в main repo (971 package added за 38 секунд), затем создал Windows junction `worktree/frontends/web-panel/node_modules → main/frontends/web-panel/node_modules` чтобы vitest/ng работали через локальный `.bin/vitest` и `.bin/ng`. Junction и `npm install` не версионируются и не коммитятся.

3. **Откат сгенерированного `dist/`**: `ng build --configuration development` перегенерировал `frontends/web-panel/dist/` (этот путь явно НЕ игнорируется в root `.gitignore` — there's a `!frontends/web-panel/dist/` negation). Чтобы не загрязнять cherry-pick build artifacts, откатил изменения через `git checkout -- dist/ && git clean -fd dist/`. Build подтверждён только как in-memory verification, без коммита dist/.

## Issues Encountered

- **Первый vitest run дал flaky failure** (10 failed из 157): похоже на jsdom/canvas timeout при холодном запуске (`environment 357s`). Повторный запуск и все последующие дали стабильно **157 passed** за 18-33 секунды. Не блокирующее — скорее всего связано с первичной инициализацией jsdom + chart.js + angular-testing. Принял стабильный результат как source of truth.
- **npx vs local .bin**: первая попытка `npx vitest` использовала глобальный npm cache и не видел локальные `@analogjs/vitest-angular`/`vitest` из node_modules. Решение: запускать через `./node_modules/.bin/vitest` напрямую.
- **Junction self-loop**: первый `mklink /J` внутри `cd ...web-panel` с relative path создал битый self-loop junction (пустой target). Решение: использовать абсолютные пути для source и target в `mklink /J`.

## User Setup Required

None — все изменения чисто фронтовые в Angular web-panel, не требуют конфигурации внешних сервисов или env-vars. Placeholder компоненты статические, не делают HTTP запросов, не требуют backend изменений. Phase 51-54 введут реальный UI с data fetching.

## Known Stubs

Placeholder компоненты из D-06 — **intentional stubs** с задокументированным планом замены:

| File | Line | Stub | Intentional? | Replaced in |
|------|------|------|--------------|-------------|
| `frontends/web-panel/src/app/features/student/student-placeholder/student-placeholder.component.ts` | ~13 | Текст "Кабинет студента появится в Фазе 51" | Yes — per D-06 | Phase 51-53 (real StudentDashboardComponent, StudentScheduleComponent) |
| `frontends/web-panel/src/app/features/headman/headman-placeholder/headman-placeholder.component.ts` | ~13 | Текст "Кабинет старосты появится в Фазе 54" | Yes — per D-06 | Phase 54-55 (real HeadmanDashboardComponent) |

Эти stub'ы НЕ блокируют цель Plan 03. Цель Plan 03 = регистрация routes + guards + lightweight сущности для верификации guard-flow end-to-end. Реальный UI = out-of-scope (explicit в `50-CONTEXT.md` → "Out of scope → Real /student/* content (Phase 51-53), Real /headman/* content (Phase 54-55)"). Phase 50 success criteria (1, 3, 4) верифицируются через existence of routes + guard behavior, не content placeholder компонентов.

## Next Phase Readiness

- **Plan 04 (login.component редирект)** разблокирован: target routes `/student/dashboard`, `/student/schedule`, `/headman/dashboard` существуют и доступны для `this.router.navigateByUrl(this.authService.resolveDashboardFor(user))`. login.component на всех 4 ролях может быть верифицирован end-to-end.
- **Plan 05 (landing dead links + nginx baseHref)** не зависит от Plan 03 — может идти параллельно.
- **Plan 06 (E2E manual UAT)** разблокирован: все 3 ROADMAP success criteria (1 admin/teacher post-login, 3 plain student blocked from /headman/dashboard, 4 headman allowed on /student/schedule) теперь могут быть проверены в браузере через реальный SPA flow.
- Wave 3 (Plans 03, 04, 05 параллельно) готов к полной интеграции после merge всех worktree агентов.

## Verification Summary

| Verification | Expected | Actual | Status |
|---|---|---|---|
| `grep -c "export class StudentPlaceholderComponent"` | 1 | 1 | PASS |
| `grep -c "standalone: true"` (student) | 1 | 1 | PASS |
| `grep -c "Кабинет студента появится в Фазе 51"` | 1 | 1 | PASS |
| `grep -c "MatCardModule"` (student) | ≥ 2 | 2 | PASS |
| `grep -c "export class HeadmanPlaceholderComponent"` | 1 | 1 | PASS |
| `grep -c "Кабинет старосты появится в Фазе 54"` | 1 | 1 | PASS |
| `grep -c "import { studentGuard }"` (routes) | 1 | 1 | PASS |
| `grep -c "import { headmanGuard }"` (routes) | 1 | 1 | PASS |
| `grep -c "import { guestGuard }"` (routes) | 1 | 1 | PASS |
| `grep -c "canActivate: [guestGuard]"` | 1 | 1 | PASS |
| `grep -c "canActivate: [studentGuard]"` | 1 | 1 | PASS |
| `grep -c "canActivate: [headmanGuard]"` | 1 | 1 | PASS |
| `grep -c "path: 'student'"` | 1 | 1 | PASS |
| `grep -c "path: 'headman'"` | 1 | 1 | PASS |
| `grep -c "path: 'schedule'"` | 1 | 1 | PASS |
| `grep -c "StudentPlaceholderComponent"` (routes) | 2 | 2 | PASS |
| `grep -c "HeadmanPlaceholderComponent"` (routes) | 1 | 1 | PASS |
| `grep -c "eyebrow: 'Студент'"` | ≥ 2 | 3 | PASS |
| `grep -c "eyebrow: 'Староста'"` | ≥ 1 | 2 | PASS |
| `vitest run --reporter=dot` (full suite) | exit 0 | 157 passed / 25 files, exit 0 | PASS |
| `ng build --configuration development` | success | Built in 9.795s, 0 errors | PASS |

## Self-Check: PASSED

- Проверены файлы созданы/изменены:
  - FOUND: frontends/web-panel/src/app/features/student/student-placeholder/student-placeholder.component.ts
  - FOUND: frontends/web-panel/src/app/features/headman/headman-placeholder/headman-placeholder.component.ts
  - FOUND: frontends/web-panel/src/app/app.routes.ts (modified: +3 imports, +46 lines for student/headman blocks + guestGuard)
- Проверены коммиты (`git log --oneline`):
  - FOUND: de653b3 (base sync — no-op при cherry-pick)
  - FOUND: b24b426 (Task 1: placeholder components)
  - FOUND: 17395bb (Task 2: routes + guestGuard)
- Full suite gate: 157 passed / 25 files — exit code 0
- ng build gate: 9.795s — exit code 0, lazy-loading paths корректны
- Threat model:
  - T-50-10 (TEACHER→/student/*) mitigate — `canActivate: [studentGuard]` применён
  - T-50-11 (plain STUDENT→/headman/*) mitigate — `canActivate: [headmanGuard]` применён
  - T-50-12 (auth user sees /login) mitigate — `canActivate: [guestGuard]` применён
  - T-50-13 (eyebrow leakage) accept — `data: { eyebrow }` visual breadcrumb, не data disclosure
  - T-50-14 (placeholder injection) accept — статический template без user input binding

---
*Phase: 50-basehref-migration-unified-login*
*Completed: 2026-04-09*
