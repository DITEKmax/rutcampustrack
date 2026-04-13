---
phase: 50-basehref-migration-unified-login
plan: 01
subsystem: auth
tags: [angular, signals, jwt, vitest, typescript, web-panel]

# Dependency graph
requires:
  - phase: 49-nginx-routing-landing-dead-link-fix
    provides: nginx `location = / -> 301 /login` left intact, web-panel auth pipeline baseline
provides:
  - Расширенный `AuthUser` interface (`role: TEACHER|ADMIN|STUDENT`, `isHeadman: boolean`, `groupId: number|null`)
  - `AuthService.resolveDashboardFor(user)` — единый источник истины для post-login редиректов всех 4 ролей
  - `currentUser` computed парсит `is_headman` (строгое `=== true`) и `group_id` (`?? null`) из JWT
  - `SidebarComponent` тип `NavItem.roles` расширен до `STUDENT`, `sectionLabel` имеет ветку `Учёба`, no nav entries для STUDENT (D-06)
  - 11 новых vitest юнит-тестов (10 в auth.service.spec, 1 в sidebar.component.spec)
affects: [50-02 (новые guards — потребляют isHeadman/resolveDashboardFor), 50-03 (placeholder routes), 50-04 (login.component редирект)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "AuthService = single source of truth for role-based redirects (resolveDashboardFor)"
    - "Strict JWT claim parsing: `payload.is_headman === true` (не `!!`) — безопасный default false при отсутствии claim"
    - "AuthUser interface — расширяемый contract: backend JWT эволюционирует, фронт расширяет union-типы"

key-files:
  created: []
  modified:
    - frontends/web-panel/src/app/core/auth/auth.service.ts
    - frontends/web-panel/src/app/core/auth/auth.service.spec.ts
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.spec.ts

key-decisions:
  - "resolveDashboardFor реализован как public method, не computed signal (детерминированный, тестируется без TestBed)"
  - "STUDENT-кейс в sectionLabel возвращает 'Учёба' — UX-friendly fallback на случай если STUDENT когда-то окажется на shell с sidebar"
  - "Sidebar НЕ получает новых NavItem записей для STUDENT/HEADMAN в Phase 50 (D-06 placeholder phase) — только тип расширен"

patterns-established:
  - "TDD RED-GREEN: тесты пишутся ПЕРЕД изменением реализации, RED фаза подтверждается отдельным коммитом перед GREEN"
  - "Mock AuthUser в spec-файлах должен содержать ВСЕ поля интерфейса (TS-strict в production build)"

requirements-completed: [AUTH-v9-02, AUTH-v9-03]

# Metrics
duration: ~15min
completed: 2026-04-09
---

# Phase 50 Plan 01: AuthService STUDENT/headman support + sidebar widening Summary

**Расширение AuthUser interface (STUDENT + isHeadman + groupId), новый `resolveDashboardFor` метод как single source of truth для post-login редиректов, и type-tightening SidebarComponent под расширенный union без новых записей меню.**

## Performance

- **Duration:** ~15 min (включая baseline runs и full suite verification)
- **Started:** 2026-04-09T02:05:00Z
- **Completed:** 2026-04-09T02:11:00Z
- **Tasks:** 2
- **Files modified:** 4
- **Tests:** 131 baseline → 142 после Plan 01 (+10 auth + 1 sidebar)

## Accomplishments
- `AuthUser` interface расширен полями `isHeadman: boolean`, `groupId: number | null`, и role-union теперь включает `STUDENT`
- `currentUser` computed signal безопасно парсит `is_headman` (`=== true`, дефолт `false`) и `group_id` (`?? null`)
- `AuthService.resolveDashboardFor(user)` — публичный метод-роутер, единый источник истины для всех post-login редиректов (null → /login, ADMIN → /admin/dashboard, TEACHER → /teacher/dashboard, STUDENT+headman → /headman/dashboard, STUDENT → /student/dashboard)
- `SidebarComponent.NavItem.roles` тип расширен до `('TEACHER' | 'ADMIN' | 'STUDENT')[]` (TS-prep для production-сборки), `sectionLabel` имеет ветку `Учёба` для STUDENT
- Полный vitest suite зелёный: 142 passed across 22 test files (включая 4 обновлённых mock-а sidebar и 1 новый STUDENT-тест по D-06)

## Task Commits

Each task was committed atomically:

1. **Task 1 (RED):** test(50-01): add failing tests for AuthUser STUDENT/headman + resolveDashboardFor — `77d03bc`
2. **Task 1 (GREEN):** feat(50-01): extend AuthService with STUDENT role + resolveDashboardFor — `a86e665`
3. **Task 2:** feat(50-01): widen SidebarComponent for extended AuthUser — `a1235d4`

_Note: Task 1 split into RED+GREEN per TDD execution flow. Task 2 не имеет отдельного RED-коммита — sidebar runtime тест прошёл сразу (filteredNavItems по умолчанию вернул [] для STUDENT, потому что в `roles`-массивах нет 'STUDENT'), но компонент всё равно расширен типом + новой sectionLabel-веткой + новым тестом._

## Files Created/Modified

- `frontends/web-panel/src/app/core/auth/auth.service.ts` — расширен `AuthUser` (+isHeadman, +groupId, +STUDENT в union), `currentUser` парсит новые claims, добавлен `resolveDashboardFor` метод
- `frontends/web-panel/src/app/core/auth/auth.service.spec.ts` — добавлены 4 новых JWT fixture (STUDENT, HEADMAN, без is_headman, без group_id), обновлены TEACHER/ADMIN fixtures под полный backend payload, добавлены 10 новых `it()`-блоков
- `frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts` — `NavItem.roles` расширен до `STUDENT`, `sectionLabel` получил ветку `Учёба`, primaryItems/allNavItems НЕ содержат новых записей (D-06)
- `frontends/web-panel/src/app/layout/sidebar/sidebar.component.spec.ts` — 4 существующих `AuthUser` mock расширены полями `isHeadman: false, groupId: null`, добавлен 1 новый тест "STUDENT renders no nav items"

## Decisions Made

- **`resolveDashboardFor` как plain method, не computed signal** — резолвер детерминирован на любой `AuthUser` (не только `currentUser()`), и его удобнее тестировать без TestBed/signal-инфраструктуры. Также используется guards (Plan 02), которым нужен явный вызов с `inject(AuthService).resolveDashboardFor(...)`.
- **Strict `payload.is_headman === true` (не `!!payload.is_headman`)** — гарантирует, что любое не-true значение (`undefined`, `null`, `"false"`, `0`) даёт `isHeadman === false`. Защищает от Pitfall 5 из 50-RESEARCH (T-50-03 в threat model).
- **`sectionLabel` для STUDENT возвращает `'Учёба'`** — D-06 говорит «no sidebar entries for STUDENT», но `sectionLabel` всё же должен иметь sane fallback на случай runtime-сценария (например, если headman получит доступ к shell с STUDENT-пунктами в будущем).
- **Sidebar НЕ получает новых NavItem записей** — только тип расширен. Это явное требование D-06 (placeholder phase): `filteredNavItems` для STUDENT возвращает `[]` natively, потому что в `primaryItems`/`allNavItems` нет ни одного элемента с `roles.includes('STUDENT')`.

## Deviations from Plan

None — план выполнен ровно как написано. Acceptance criteria для Task 1 и Task 2 пройдены полностью, baseline 131 тестов остался green, добавлены 11 новых тестов (10 в auth.service.spec, 1 в sidebar.component.spec), полный suite green (142 passed).

**Note:** Task 2 ожидался как «sidebar упадёт TypeScript compile error из-за расширения union». Фактически vitest + @analogjs/vitest-angular использует JIT-компиляцию без strict-type-check на `Array.includes()`-runtime, поэтому baseline после Task 1 GREEN не упал. Тем не менее, расширение `NavItem.roles` тип ВСЁ РАВНО применено — это требование плана (TS-correctness в production build `ng build` со strict TS) и acceptance criteria. То же касается `sectionLabel` ветки и нового STUDENT-теста.

## Issues Encountered

- **Worktree находился на ветке без файлов фазы 50.** Worktree был создан с `d6df3c24` (deployment-ветка), а файлы плана `50-01-PLAN.md`, `50-CONTEXT.md`, `50-RESEARCH.md` существуют только в основном репозитории. Решение: читал файлы по абсолютным путям из основного репозитория, правки делал в worktree (исходные `.ts/.spec.ts` идентичны в обеих копиях). SUMMARY.md создан в `.planning/phases/50-basehref-migration-unified-login/` директории worktree (создана с нуля).
- **Отсутствие `node_modules` в worktree.** Создан Windows junction `node_modules → C:\Users\maksd\IntelliJIDEA\rutcampustrack\frontends\web-panel\node_modules` для запуска vitest без полного `npm install`.

## User Setup Required

None — изменения чисто фронтендовые в Angular web-panel, не требуют конфигурации внешних сервисов или env-vars. JWT payload backend (Java) уже содержит `is_headman` и `group_id` claims (см. `JwtService.java:94-96`), фронт только начинает их потреблять.

## Next Phase Readiness

- **Plan 02 (новые guards)** разблокирован: `studentGuard`, `headmanGuard`, `guestGuard` могут импортировать `AuthService.resolveDashboardFor` и `AuthUser.isHeadman` без TypeScript ошибок.
- **Plan 03 (placeholder routes)** разблокирован: `app.routes.ts` может ссылаться на новые guards и `STUDENT` роль в `roleGuard(['STUDENT'])` без расширения union.
- **Plan 04 (login.component редирект)** разблокирован: `login.component.ts:50` сможет заменить `role === 'ADMIN' ? '/admin/dashboard' : '/teacher/dashboard'` на `this.router.navigateByUrl(this.authService.resolveDashboardFor(this.authService.currentUser()))`.
- Wave 1 «контракт-первый» завершён, Wave 2 (Plan 02) может стартовать параллельно.

## Self-Check: PASSED

- Проверены файлы созданы/изменены:
  - FOUND: frontends/web-panel/src/app/core/auth/auth.service.ts
  - FOUND: frontends/web-panel/src/app/core/auth/auth.service.spec.ts
  - FOUND: frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts
  - FOUND: frontends/web-panel/src/app/layout/sidebar/sidebar.component.spec.ts
- Проверены коммиты:
  - FOUND: 77d03bc (RED)
  - FOUND: a86e665 (Task 1 GREEN)
  - FOUND: a1235d4 (Task 2)
- Acceptance criteria Task 1: все 12 grep-проверок и `vitest run auth.service.spec.ts` (exit 0) пройдены
- Acceptance criteria Task 2: все grep-проверки + `vitest run` (142 passed) пройдены
- Threat model: T-50-03 mitigation реализован тестом `currentUser() defaults isHeadman to false when claim is absent`

---
*Phase: 50-basehref-migration-unified-login*
*Completed: 2026-04-09*
