---
phase: 50-basehref-migration-unified-login
verified: 2026-04-09
status: passed
score: 6/6 ROADMAP success criteria verified
requirements_covered:
  - INFRA-v9-04
  - AUTH-v9-01
  - AUTH-v9-02
  - AUTH-v9-03
  - AUTH-v9-04
  - AUTH-v9-05
  - AUTH-v9-06
  - AUTH-v9-07
artifacts_verified: 12
vitest_results: 162/162 passed across 25 files (0 failures)
verifier: gsd-verifier (Claude Opus 4.6)
---

# Phase 50 — baseHref Migration + Unified /login — Verification Report

## Goal (from ROADMAP.md:162)

> Angular web-panel serves all roles from a single app with `baseHref: /`, exposing `/login` as the universal entry point with role-based post-login routing and proper guards for STUDENT and HEADMAN.

**CRITICAL-PATH**: unblocks Blocks B, C, D (все последующие Angular фазы опираются на маршрутную структуру, установленную здесь).

## Must-Haves — Observable Truths (6/6)

| # | ROADMAP Success Criterion | Status | Evidence |
|---|---------------------------|--------|----------|
| 1 | ADMIN → /admin/dashboard, TEACHER → /teacher/dashboard, plain STUDENT → /student/dashboard, headman STUDENT → /headman/dashboard после логина | PASS | `auth.service.ts:73-79` — `resolveDashboardFor()` покрывает все 4 ветки; `login.component.ts:49` вызывает `resolveDashboardFor(currentUser())`; `auth.service.spec.ts:141-166` — 5 unit-тестов (null + 4 роли); `login.component.spec.ts` — 12 тестов включают STUDENT + headman редиректы; UAT 1 passed manually после deploy hotfix |
| 2 | `AuthService.currentUser()` сигнал читает role + isHeadman + groupId из JWT без дополнительного API-запроса | PASS | `auth.service.ts:20-36` — `computed` сигнал парсит `atob(parts[1])`, читает `payload.role`, `payload.is_headman === true`, `payload.group_id ?? null`; тип `AuthUser` (lines 6-11) включает `isHeadman: boolean` + `groupId: number | null`; `auth.service.spec.ts` содержит 21 тест |
| 3 | Plain STUDENT, пытающийся открыть /headman/dashboard, блокируется `headmanGuard` и редиректится на /student/dashboard (через `resolveDashboardFor`, per D-09) | PASS | `headman.guard.ts:6-14` — `CanActivateFn`, условие `user.role === 'STUDENT' && user.isHeadman`, fallback `createUrlTree([auth.resolveDashboardFor(user)])`; зарегистрирован в `app.routes.ts:117`; `headman.guard.spec.ts` — 5 тестов; UAT 3 passed |
| 4 | Headman, посещая /student/schedule, проходит `studentGuard` без редиректа | PASS | `student.guard.ts:6-14` — условие `user.role === 'STUDENT'` пропускает и plain students, и headman; `app.routes.ts:104-110` регистрирует `/student/schedule` под studentGuard; `student.guard.spec.ts` — 5 тестов; UAT 4 passed |
| 5 | Logout на любом dashboard очищает токены, вызывает server revoke endpoint, редиректит на /login | PASS | `auth.service.ts:52-63` — `logout()` вызывает `firstValueFrom(authApi.logout(rt))`, затем `clearTokens()` и `router.navigate(['/login'])`; UAT 5 passed |
| 6 | Все 129 существующих vitest тестов проходят после baseHref миграции (0 failures) | PASS | `npx vitest run --reporter=dot` в `frontends/web-panel` (выполнено gsd-verifier): **Test Files 25 passed (25), Tests 162 passed (162)**, duration 15.83s. Это +33 теста выше baseline-порога 129 (новые тесты из Plans 01, 02, 04) |

**Score: 6/6**

## Must-Haves — Artifacts (12/12)

| # | Artifact | Level 1 (exists) | Level 2 (substantive) | Level 3 (wired) | Status |
|---|----------|------------------|------------------------|-----------------|--------|
| 1 | `frontends/web-panel/src/app/core/auth/auth.service.ts` | PASS (80 lines) | PASS — `AuthUser` с `isHeadman` + `groupId`, `resolveDashboardFor()` 4-ветка, `currentUser` parsing JWT | PASS — импортируется 7+ модулями (login, все guards, interceptor, sidebar, etc.) | VERIFIED |
| 2 | `frontends/web-panel/src/app/core/auth/student.guard.ts` | PASS (14 lines) | PASS — functional `CanActivateFn` с inject() + createUrlTree, fallback через `resolveDashboardFor` | PASS — импортируется в `app.routes.ts:4`, применён к path `student` в `app.routes.ts:92` | VERIFIED |
| 3 | `frontends/web-panel/src/app/core/auth/headman.guard.ts` | PASS (14 lines) | PASS — functional `CanActivateFn`, условие `role === 'STUDENT' && isHeadman` | PASS — импортируется в `app.routes.ts:5`, применён к path `headman` в `app.routes.ts:117` | VERIFIED |
| 4 | `frontends/web-panel/src/app/core/auth/guest.guard.ts` | PASS (12 lines) | PASS — functional `CanActivateFn`, `isAuthenticated()` check, fallback через `resolveDashboardFor` | PASS — импортируется в `app.routes.ts:6`, применён к path `login` в `app.routes.ts:11` | VERIFIED |
| 5 | `frontends/web-panel/src/app/core/auth/role.guard.ts` | PASS (20 lines) | PASS — роль fallback использует `resolveDashboardFor` (не hardcoded /admin) | PASS — применён к `/teacher`, `/admin` в app.routes.ts | VERIFIED |
| 6 | `frontends/web-panel/src/app/features/student/student-placeholder/student-placeholder.component.ts` | PASS (35 lines) | PASS — standalone `@Component`, `MatCardModule` импортирован, текст "Кабинет студента появится в Фазе 51" | PASS — lazy-loaded в `app.routes.ts:98` и `app.routes.ts:106` (schedule re-use) | VERIFIED |
| 7 | `frontends/web-panel/src/app/features/headman/headman-placeholder/headman-placeholder.component.ts` | PASS (35 lines) | PASS — standalone, текст "Кабинет старосты появится в Фазе 54" | PASS — lazy-loaded в `app.routes.ts:123` | VERIFIED |
| 8 | `frontends/web-panel/src/app/app.routes.ts` | PASS (135 lines) | PASS — все 4 роли зарегистрированы под `ShellComponent` children, guestGuard на /login, studentGuard на /student, headmanGuard на /headman | PASS — экспортируется, импортируется `app.config.ts` через `provideRouter(routes)` | VERIFIED |
| 9 | `frontends/web-panel/src/app/features/login/login.component.ts` | PASS | PASS — на строке 49 `resolveDashboardFor(currentUser())` + `navigateByUrl` | PASS — lazy-loaded в `app.routes.ts:13` | VERIFIED |
| 10 | `frontends/web-panel/angular.json` | PASS | PASS — `baseHref: "/"` на строках 44 (options) и 62 (production configuration); нет `"/admin/"` | N/A (config) | VERIFIED |
| 11 | `nginx/conf.d/default.conf` | PASS (144 lines) | PASS — `location = /` → 301 /login (line 127-129); catch-all `location /` → `proxy_pass http://rct-web-panel-nginx:80/` (line 137-143); нет `/admin/` location блока (только комментарий на строке 136); порядок префиксов корректен (/api/, /app/, /presentation/, /mini-app/, /swagger*, /openapi/ до catch-all) | N/A (infra config) | VERIFIED |
| 12 | `frontends/landing/dist/index.html` | PASS | PASS — 4 ссылки `href="/login"` (строки 1029, 1107, 1306, 1330); 0 совпадений `ruttrack.site/admin` | N/A (static asset) | VERIFIED |

## Requirements Coverage (8/8)

| Requirement ID | Source Plan(s) | Description (из REQUIREMENTS.md) | Status | Evidence |
|----------------|----------------|----------------------------------|--------|----------|
| INFRA-v9-04 | 50-03, 50-05 | Angular web-panel serves /login, /admin/\*, /teacher/\*, /student/\*, /headman/\* as single SPA with baseHref: / | SATISFIED | angular.json baseHref: "/" × 2 + nginx catch-all `location /` + app.routes.ts регистрирует все 4 role-path под одним ShellComponent |
| AUTH-v9-01 | 50-04 | User can log in at /login with username+password; access token в Angular signal memory-only (D-06) | SATISFIED | login.component.ts POST /api/auth/login flow; auth.service.ts `_accessToken = signal<string>(null)` (line 15) — в памяти, без localStorage; UAT 1+5 passed |
| AUTH-v9-02 | 50-01, 50-04 | Role-based post-login routing to 4 dashboards | SATISFIED | resolveDashboardFor() (auth.service.ts:73-79) + вызов в login.component.ts:49; auth.service.spec.ts 21 тест; UAT 1 passed |
| AUTH-v9-03 | 50-01, 50-04 | AuthService.currentUser сигнал читает role, is_headman, group_id из JWT | SATISFIED | auth.service.ts:20-36 — `computed` signal с `atob(parts[1])`, парсит все 3 claim'а; 21 auth.service test |
| AUTH-v9-04 | 50-02, 50-03 | headmanGuard пропускает только STUDENT+isHeadman=true + доступ к /student/\* тоже | SATISFIED | headman.guard.ts:12 + student.guard.ts:12 (допускает любой STUDENT); 5+5 guard тестов; UAT 3+4 passed |
| AUTH-v9-05 | 50-02, 50-03 | studentGuard пропускает любого STUDENT (plain + headman) | SATISFIED | student.guard.ts:12 `user.role === 'STUDENT' → true`; 5 тестов в student.guard.spec.ts; UAT 4 passed |
| AUTH-v9-06 | 50-04 | Logout button на каждом dashboard очищает токены, revoke на сервере, редирект на /login | SATISFIED | auth.service.ts:52-63 `logout()`; UAT 5 passed |
| AUTH-v9-07 | 50-06 | 129+ существующих vitest тестов продолжают проходить после baseHref миграции | SATISFIED | **162/162 passed, 25 files, 0 failures** (verified live by gsd-verifier); +33 тестов выше baseline 129 |

**Плагин requirements orphan check**: все 8 ID в REQUIREMENTS.md строках 132, 136-142 замаплены на Phase 50. Все 8 заявлены в frontmatter хотя бы одного plan. 0 orphaned requirements.

## Anti-Pattern Scan

Проверены файлы, затронутые фазой 50: auth.service.ts, role.guard.ts, student.guard.ts, headman.guard.ts, guest.guard.ts, app.routes.ts, login.component.ts, student-placeholder.component.ts, headman-placeholder.component.ts, angular.json, nginx/conf.d/default.conf, frontends/landing/dist/index.html.

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| — | — | — | Анти-паттернов не найдено. Все placeholder'ы — намеренные (текст "появится в Фазе 51/54" — документированный D-07 в 50-03-PLAN), routes под placeholder'ами wired через studentGuard/headmanGuard, никаких TODO/FIXME/HACK/stub в затронутых файлах не обнаружено. |

## Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| vitest suite green | `cd frontends/web-panel && node node_modules/vitest/dist/cli.js run --reporter=dot` | Test Files 25 passed (25), Tests 162 passed (162), duration 15.83s, exit 0 | PASS |
| angular.json baseHref | grep `"baseHref"` angular.json | 2 matches, оба `"/"`, 0 matches `"/admin/"` | PASS |
| nginx exact-match root redirect | grep `location = /` nginx/conf.d/default.conf | 1 match (line 127) с `return 301 /login` (line 128) | PASS |
| nginx catch-all для web-panel | grep `location /` (без =) nginx/conf.d/default.conf | catch-all на line 137 → `proxy_pass http://rct-web-panel-nginx:80/` | PASS |
| Landing footer login CTA | grep `href="/login"` frontends/landing/dist/index.html | 4 matches (lines 1029, 1107, 1306, 1330); 0 matches `ruttrack.site/admin` | PASS |
| resolveDashboardFor sole source of truth | grep `resolveDashboardFor` frontends/web-panel/src | 5 caller sites (login, role.guard, student.guard, headman.guard, guest.guard) + определение в auth.service.ts + 9 references в specs | PASS |
| Placeholder components standalone | inspect `@Component({ standalone: true })` | оба компонента: `standalone: true`, import `MatCardModule` | PASS |

## Summary-to-Reality Audit

| Claim (SUMMARY / VALIDATION) | Reality Check | Match |
|-------------------------------|---------------|-------|
| 162/162 vitest tests в Plan 06 Task 1 (50-VALIDATION.md:61) | Verified live: 162/162, 25 files, exit 0 | YES |
| `base href="/"` в dist/index.html | Не пере-builded (dist не коммитится), но source angular.json = "/", Plan 05 Task 1 зафиксировал результат `ng build` 1 match | YES (source) |
| nginx `/admin/` location удалён | Подтверждено: только 1 упоминание в комментарии на line 136 | YES |
| 6/6 UAT passed после deploy hotfix | 50-HUMAN-UAT.md status: resolved, approver: maksd, 6 passed / 0 issues | YES |
| 50-VALIDATION.md Approval | "approved 2026-04-09 by maksd (after deploy hotfix b391fb9)" (line 119) | YES |
| Commit b391fb9 — deploy auto-reload hotfix | git log: "fix(deploy): auto-reload nginx + smoke test /login after git pull" | YES |

## Deploy Incident Note (Informational — Not a Code Gap)

Во время UAT на продакшене (2026-04-09) все 4 роли ошибочно попадали на `/home` (mini-app fallback). Расследование показало, что `rct-nginx` держал старый конфиг в памяти процесса: файл на диске обновился через `git pull`, но сам nginx-процесс никогда не получал `nginx -s reload` в workflow. Ручная команда `docker exec rct-nginx nginx -s reload` мгновенно исправила всё.

**Это был дефект deploy pipeline, а не код фазы 50.** Код фазы 50 был всегда корректен. Hotfix зафиксирован в commit **b391fb9** (`fix(deploy): auto-reload nginx + smoke test /login after git pull`):
- `deploy.yml` теперь выполняет `nginx -t && nginx -s reload && curl smoke test` после `git pull`
- Safety-net reload loop в `docker-compose.prod.yml` уменьшен с 6ч до 5мин
- Smoke test завершает деплой с ошибкой, если `/login` отвечает меньше 5000 байт

Этот инцидент задокументирован в `50-HUMAN-UAT.md` раздел Gaps/Notes как `status: resolved`, upstream-риск для будущих deploy workflow, не регрессия phase 50.

## Issues Found

Не обнаружено. Никаких code defects, unwired артефактов, stub'ов, анти-паттернов или отсутствующих requirement'ов. UAT пройдены 6/6. vitest suite green (162/162). Единственное отклонение — deploy-pipeline инцидент, уже исправленный в commit b391fb9 (не относится к коду фазы 50).

## Final Verdict

**STATUS: passed**

Все 6 ROADMAP success criteria и все 8 requirement IDs (INFRA-v9-04, AUTH-v9-01..07) удовлетворены. 12 must-have артефактов прошли все 3 уровня верификации (existence → substantive → wired). `AuthService.resolveDashboardFor()` корректно служит единственным источником истины для post-login routing с 5 call-site'ами и 5 unit-тестами покрытия. Три functional `CanActivateFn` guard'а (studentGuard, headmanGuard, guestGuard) существуют и зарегистрированы в `app.routes.ts`. Оба standalone placeholder компонента смонтированы под `ShellComponent` children. `angular.json` baseHref мигрирован на `"/"` в обоих местах. `nginx/conf.d/default.conf` имеет корректный exact-match `= /` → 301 /login + catch-all `/` → rct-web-panel-nginx с правильным prefix-match порядком. Landing footer указывает на `/login` (4 ссылки), 0 stale `/admin/` URL. vitest полная регрессия: **162/162 passed across 25 files**.

Deploy incident (auto-reload nginx) полностью устранён в commit b391fb9 — это deployment fix, не code defect фазы 50.

**Фаза 50 готова. Milestone v9.0 может переходить к Phase 51 (Student Web Cabinet — Shell + Schedule + Check-in).**

---

_Verified: 2026-04-09_
_Verifier: Claude (gsd-verifier, Opus 4.6)_
_vitest run by verifier: 162 passed / 162 total / 25 files / 0 failures / exit 0_
