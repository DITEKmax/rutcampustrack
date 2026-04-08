---
phase: 50-basehref-migration-unified-login
plan: 02
subsystem: auth
tags: [angular, guards, vitest, typescript, web-panel, role-based-access]

# Dependency graph
requires:
  - phase: 50
    plan: 01
    provides: "AuthService.resolveDashboardFor + extended AuthUser (STUDENT, isHeadman, groupId)"
provides:
  - "studentGuard (CanActivateFn) — пропускает STUDENT (plain + headman), редиректит ADMIN/TEACHER/unauth"
  - "headmanGuard (CanActivateFn) — пропускает только STUDENT+isHeadman=true"
  - "guestGuard (CanActivateFn) — пропускает unauth к /login, редиректит authenticated на их dashboard"
  - "15 новых vitest юнит-тестов (5 per guard)"
affects:
  - "50-03 (placeholder routes) — может применять guards на /student/*, /headman/* в app.routes.ts"
  - "50-04 (login.component редирект) — login.component больше не нуждается в inline ternary"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "CanActivateFn + inject() + return (true | UrlTree) — никаких imperative router.navigate"
    - "Guards делегируют fallback редиректы в AuthService.resolveDashboardFor (single source of truth)"
    - "TDD RED→GREEN per guard: spec создаётся первым, RED подтверждается отдельным запуском, GREEN — одним commit'ом на (guard + spec) вместе"
    - "TestBed.runInInjectionContext + mocked Router.createUrlTree для проверки CanActivateFn в изоляции"

key-files:
  created:
    - frontends/web-panel/src/app/core/auth/student.guard.ts
    - frontends/web-panel/src/app/core/auth/student.guard.spec.ts
    - frontends/web-panel/src/app/core/auth/headman.guard.ts
    - frontends/web-panel/src/app/core/auth/headman.guard.spec.ts
    - frontends/web-panel/src/app/core/auth/guest.guard.ts
    - frontends/web-panel/src/app/core/auth/guest.guard.spec.ts
  modified: []

key-decisions:
  - "studentGuard пропускает и plain STUDENT, и headman — headman остаётся STUDENT (HEAD-WEB-01 в ROADMAP)"
  - "headmanGuard при отказе plain STUDENT использует resolveDashboardFor → /student/dashboard, НЕ /login — критично для UX и для success criterion 3 в ROADMAP"
  - "guestGuard не проверяет user === null отдельно: резолвер возвращает '/login' для null, но сюда мы заходим только при isAuthenticated() === true, так что currentUser() никогда не null в этой ветке"
  - "Все 3 guard — именованные const CanActivateFn (не factory), потому что не принимают конфигурацию — в отличие от roleGuard"

patterns-established:
  - "Guards на client-side — UX gate, а не security boundary (T-50-08 accept); server-side авторизация — отдельная линия защиты"
  - "resolveDashboardFor как единственный источник истины для fallback редиректов: каждый новый guard должен делегировать туда"

requirements-completed: [AUTH-v9-04, AUTH-v9-05]

# Metrics
duration: ~12min
completed: 2026-04-09
---

# Phase 50 Plan 02: Role-based Guards (student/headman/guest) Summary

**Три функциональных CanActivateFn guard'а (`studentGuard`, `headmanGuard`, `guestGuard`) для контроля доступа к `/student/*`, `/headman/*` и `/login`, все делегируют fallback редиректы в `AuthService.resolveDashboardFor` из Plan 01.**

## Performance

- **Duration:** ~12 min (включая baseline, RED/GREEN для каждого guard, full suite regression)
- **Started:** 2026-04-09T02:15:00Z
- **Completed:** 2026-04-09T02:21:00Z
- **Tasks:** 3
- **Files created:** 6 (3 guards + 3 specs)
- **Tests:** 131 baseline → 146 (+15 новых: 5 per guard)

## Accomplishments

- `studentGuard` реализован как именованная `CanActivateFn`: unauth → `/login` UrlTree, STUDENT (plain/headman) → `true`, TEACHER/ADMIN → `resolveDashboardFor(user)` UrlTree
- `headmanGuard` реализован: unauth → `/login`, STUDENT+isHeadman=true → `true`, все остальные (plain STUDENT, TEACHER, ADMIN) → `resolveDashboardFor(user)`
- `guestGuard` реализован: `!isAuthenticated()` → `true` (форма логина доступна), иначе → `resolveDashboardFor(currentUser())`
- Все 3 guard'а следуют паттерну `inject(AuthService) + inject(Router) + sync check + return (true | UrlTree)` — ни одного `router.navigate`
- 15 новых vitest юнит-тестов (5 per guard), покрывающих все role-branches
- Полный vitest suite зелёный: **146 passed** across 25 test files (baseline 131 + 15 новых)

## Task Commits

Each task was committed atomically with `--no-verify` (parallel executor requirement):

0. **Base sync (pre-Task 1):** chore(50-02): sync auth.service.ts from main — `aa88139`
   _(worktree был на stale base d6df3c2; этот коммит восстановил auth.service.ts до main HEAD с Plan 01 changes — будет пропущен orchestrator-ом при cherry-pick на main, поскольку main уже содержит его содержимое)_
1. **Task 1 (studentGuard):** feat(50-02): add studentGuard with spec (AUTH-v9-05) — `ec53716`
2. **Task 2 (headmanGuard):** feat(50-02): add headmanGuard with spec (AUTH-v9-04) — `85b8566`
3. **Task 3 (guestGuard):** feat(50-02): add guestGuard with spec (D-08) — `25f3bfb`

_Note: TDD RED/GREEN per task НЕ разбит на два commit'а — RED фаза подтверждалась отдельным запуском `vitest` перед созданием guard-файла (vitest выдавал "Cannot find module './{guard}.guard'"), затем GREEN фиксировался одним commit'ом `feat(50-02)` с обоими файлами сразу (spec + guard). Это соответствует TDD-паттерну Plan 01 (Task 2 там тоже commit'ил spec+impl вместе после локальной RED-проверки)._

## Files Created/Modified

**Created:**
- `frontends/web-panel/src/app/core/auth/student.guard.ts` — 14 строк, CanActivateFn: if !user → `/login`, if STUDENT → true, else → `resolveDashboardFor(user)`
- `frontends/web-panel/src/app/core/auth/student.guard.spec.ts` — 81 строка, 5 it-блоков (plain STUDENT, headman STUDENT, TEACHER, ADMIN, unauthenticated)
- `frontends/web-panel/src/app/core/auth/headman.guard.ts` — 14 строк, CanActivateFn: if !user → `/login`, if STUDENT && isHeadman → true, else → `resolveDashboardFor(user)`
- `frontends/web-panel/src/app/core/auth/headman.guard.spec.ts` — 81 строка, 5 it-блоков (headman → true, plain STUDENT → `/student/dashboard`, TEACHER → `/teacher/dashboard`, ADMIN → `/admin/dashboard`, unauth → `/login`)
- `frontends/web-panel/src/app/core/auth/guest.guard.ts` — 12 строк, CanActivateFn: if !isAuthenticated() → true, else → `resolveDashboardFor(currentUser())`
- `frontends/web-panel/src/app/core/auth/guest.guard.spec.ts` — 81 строка, 5 it-блоков (unauth → true, 4 authenticated roles → dashboards)

**Modified:** None — план явно требовал "никаких изменений в существующих файлах". Единственное исключение — base sync commit `aa88139` для `auth.service.ts`, который восстановил stale worktree copy до main HEAD (не считается изменением плана).

## Decisions Made

- **studentGuard не различает plain STUDENT и headman** — оба проходят. Это явное требование HEAD-WEB-01 (headman остаётся STUDENT) и Plan 02 behavior spec. `user.role === 'STUDENT'` истинен для обоих, ветка `user.isHeadman` не нужна.
- **headmanGuard редиректит plain STUDENT на `/student/dashboard`, а не на `/login`** — критично для UX и для того, чтобы ROADMAP success criterion #3 был verifiable. Это реализуется автоматически через `resolveDashboardFor(user)` — plain STUDENT (isHeadman=false) резолвится в `/student/dashboard`.
- **guestGuard реализован через `isAuthenticated()`, не через `currentUser() !== null`** — оба варианта эквивалентны для данного кода (авторизованный = токен есть = currentUser не null), но `isAuthenticated()` семантически точнее и читаемее, а также соответствует коду `authGuard` (`if auth.isAuthenticated() return true`).
- **Все guards — именованные const** (`export const xxxGuard: CanActivateFn = () => ...`), а не factory functions как `roleGuard`. Это потому что они не принимают параметров — нет конфигурации, нет замыкания. Тот же паттерн, что и у `authGuard`.
- **TDD RED-фаза — локальная проверка, не отдельный commit** — следует паттерну Plan 01 Task 2. Сначала создаётся spec-файл, запускается `vitest run {spec}.spec.ts`, проверяется что упал с "Cannot find module './{guard}.guard'", затем создаётся guard-файл, запускается vitest снова (5/5 green), commit'ится одним commit'ом оба файла. Это быстрее чем 6 commit'ов (3 RED + 3 GREEN) и всё равно сохраняет TDD-дисциплину.

## Deviations from Plan

**None for the 3 tasks themselves** — все 3 guard-файла и 3 spec-файла созданы ровно как описано в action блоках, все acceptance criteria пройдены, full suite зелёный (146 passed).

**One infrastructure-level deviation** (Rule 3 — blocking): worktree был создан с stale base `d6df3c24` (parallel deployment branch), где `auth.service.ts` ещё не содержит Plan 01 changes (`STUDENT` role, `isHeadman`, `groupId`, `resolveDashboardFor`). Без Plan 01 foundation невозможно компилировать и тестировать guard'ы, потому что они ссылаются на `auth.resolveDashboardFor(user)` и `user.isHeadman`. Решение: как указано в prompt'е worktree_branch_check — прочитал `auth.service.ts` из main (C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/core/auth/auth.service.ts), перезаписал worktree copy, закоммитил отдельным `chore(50-02): sync auth.service.ts from main` commit. Orchestrator при cherry-pick на main увидит, что этот коммит уже применён, и пропустит его (no-op).

**Infrastructure note:** worktree не имел `node_modules` — создал Windows junction `node_modules → C:\Users\maksd\IntelliJIDEA\rutcampustrack\frontends\web-panel\node_modules` (тот же подход, что и в Plan 01 SUMMARY). Junction не версионится, не коммитится.

## Issues Encountered

- **Stale worktree base** (см. Deviations выше) — решено base sync commit'ом.
- **Отсутствие `node_modules` в worktree** — решено Windows junction.
- **Отсутствие `.planning/phases/50-basehref-migration-unified-login/` в worktree** — все файлы плана читал из main repo по абсолютным путям. SUMMARY.md создал в worktree с нуля (директория создана `mkdir -p`).

## User Setup Required

None — все изменения чисто фронтовые в Angular web-panel, не требуют конфигурации внешних сервисов или env-vars.

## Next Phase Readiness

- **Plan 03 (placeholder routes)** разблокирован: `app.routes.ts` может импортировать все 3 guard'а и применять `canActivate: [studentGuard]`, `canActivate: [headmanGuard]`, `canActivate: [guestGuard]` без TypeScript ошибок.
- **Plan 04 (login.component редирект)** может стартовать параллельно с Plan 03 (Wave 3) — login.component.ts не зависит от guard'ов напрямую, зависит только от `AuthService.resolveDashboardFor` (уже доступен из Plan 01).
- Wave 2 завершён с точки зрения этого worktree. Orchestrator может запустить Wave 3 после merge всех worktree агентов Wave 2.

## Verification Summary

| Verification | Expected | Actual | Status |
|---|---|---|---|
| `student.guard.spec.ts` vitest run | 5 passed | 5 passed | PASS |
| `headman.guard.spec.ts` vitest run | 5 passed | 5 passed | PASS |
| `guest.guard.spec.ts` vitest run | 5 passed | 5 passed | PASS |
| Full suite regression | ≥146 passed (baseline+15) | 146 passed | PASS |
| `grep -c "export const studentGuard: CanActivateFn"` | 1 | 1 | PASS |
| `grep -c "export const headmanGuard: CanActivateFn"` | 1 | 1 | PASS |
| `grep -c "export const guestGuard: CanActivateFn"` | 1 | 1 | PASS |
| `grep -c "router.navigate"` in all 3 guards | 0 | 0 | PASS |
| `grep -c "auth.resolveDashboardFor"` in student/headman guards | 1 each | 1 each | PASS |
| `grep -c "auth.resolveDashboardFor(auth.currentUser())"` in guest | 1 | 1 | PASS |
| `grep -c "^  it("` in each spec | 5 | 5 | PASS |

## Self-Check: PASSED

- Проверены файлы созданы:
  - FOUND: frontends/web-panel/src/app/core/auth/student.guard.ts
  - FOUND: frontends/web-panel/src/app/core/auth/student.guard.spec.ts
  - FOUND: frontends/web-panel/src/app/core/auth/headman.guard.ts
  - FOUND: frontends/web-panel/src/app/core/auth/headman.guard.spec.ts
  - FOUND: frontends/web-panel/src/app/core/auth/guest.guard.ts
  - FOUND: frontends/web-panel/src/app/core/auth/guest.guard.spec.ts
- Проверены коммиты:
  - FOUND: aa88139 (base sync — будет no-op при cherry-pick на main)
  - FOUND: ec53716 (Task 1 studentGuard)
  - FOUND: 85b8566 (Task 2 headmanGuard)
  - FOUND: 25f3bfb (Task 3 guestGuard)
- Все acceptance criteria для Task 1, 2, 3 пройдены (grep + vitest)
- Full suite gate: 146 passed (131 baseline + 15 new guard tests) — exit code 0
- Threat model:
  - T-50-05 (EoP: /headman/* доступен plain STUDENT) mitigate — тест `redirects plain STUDENT (isHeadman=false) to /student/dashboard` проходит
  - T-50-06 (EoP: /student/* доступен TEACHER/ADMIN) mitigate — тесты `redirects TEACHER/ADMIN` проходят
  - T-50-07 (Spoofing: сессия-fixation через /login) mitigate — тесты guestGuard `redirects authenticated [ROLE] to [dashboard]` проходят (будет применено к маршруту в Plan 03)
  - T-50-08 (Tampering: guard bypass) accept — guards на client-side, не security boundary, отмечено в плане
  - T-50-09 (DoS: guard throws exception) mitigate — все guards только sync checks + return, никакого try/catch/await

---
*Phase: 50-basehref-migration-unified-login*
*Completed: 2026-04-09*
