---
phase: 58-admin-bug-006-fixes
plan: 08
subsystem: web-panel (admin)
tags: [bug-006, groups, archive, promotion, frontend, angular, material]
dependency-graph:
  requires:
    - "Plan 04: GroupResponse без поля code, единое поле name (ХХ(х)-NNN)"
    - "Plan 06: backend endpoints POST /groups/promote[/preview] + GET /groups?status=... + archivedAt"
  provides:
    - "GroupStatus / PromotionPreviewItem / PrefixConflict / PromotionSummary — TS-типы"
    - "AdminApiService.listGroupsByStatus / promotePreview / promote / getGroup"
    - "groups-page с MatTabGroup (Активные/Архив) + debounced ILIKE-поиск"
    - "PromotionPreviewDialogComponent — 5-фазный flow (loading → preview → executing → done/error)"
    - "GroupHistoryPageComponent — read-only /admin/groups/:id/history"
  affects:
    - "Plan 09 (final-verification-report): фронтенд плана 08 готов к UAT"
tech-stack:
  added:
    - "@angular/material MatTabsModule (таб-группа)"
    - "@angular/material MatProgressSpinnerModule (в модалке preview)"
  patterns:
    - "Signal-based component state (phase/summary/result) без RxJS BehaviorSubjects"
    - "Commands forkJoin({ groups, students }) для одновременной загрузки — переиспользует паттерн старого reload"
    - "catchError(() => of(null)) → notFound-флаг вместо перехвата в ngOnInit"
    - "Защита от двойного confirm через phase='executing' + disabled кнопка"
key-files:
  created:
    - frontends/web-panel/src/app/features/admin/groups/groups-page.component.scss
    - frontends/web-panel/src/app/features/admin/groups/promotion-preview-dialog/promotion-preview-dialog.component.ts
    - frontends/web-panel/src/app/features/admin/groups/promotion-preview-dialog/promotion-preview-dialog.component.html
    - frontends/web-panel/src/app/features/admin/groups/promotion-preview-dialog/promotion-preview-dialog.component.scss
    - frontends/web-panel/src/app/features/admin/groups/promotion-preview-dialog/promotion-preview-dialog.component.spec.ts
    - frontends/web-panel/src/app/features/admin/groups/group-history/group-history-page.component.ts
    - frontends/web-panel/src/app/features/admin/groups/group-history/group-history-page.component.html
    - frontends/web-panel/src/app/features/admin/groups/group-history/group-history-page.component.scss
    - frontends/web-panel/src/app/features/admin/groups/group-history/group-history-page.component.spec.ts
  modified:
    - frontends/web-panel/src/app/features/admin/shared/types.ts
    - frontends/web-panel/src/app/features/admin/shared/admin-api.service.ts
    - frontends/web-panel/src/app/features/admin/shared/admin-api.service.spec.ts
    - frontends/web-panel/src/app/features/admin/groups/groups-page.component.ts
    - frontends/web-panel/src/app/features/admin/groups/groups-page.component.html
    - frontends/web-panel/src/app/features/admin/groups/groups-page.component.spec.ts
    - frontends/web-panel/src/app/app.routes.ts
decisions:
  - "Route added to app.routes.ts (single-file routing), not admin-routes.ts — проект не имеет отдельного admin-routes файла; правка минимальна (12 строк), совместима с существующей архитектурой roleGuard"
  - "status signal + searchControl (FormControl) вместо toObservable(signal) чейна — обычный debounceTime + reload() проще для тестов и даёт explicit trigger из onTabChange"
  - "Модалка: sealed 5-фазная state-machine (loading/preview/executing/done/error) через единый signal — упрощает template и предотвращает race (двойной клик → isEmpty или phase!==preview гард)"
  - "isEmpty() = true при conflicts-only — backend не сможет ничего сделать если toPromote.length=0 && toArchive.length=0; disabled Подтвердить избегает no-op POST"
  - "Журнал/Посещаемость links используют /admin/dashboard?groupId=... (заглушка под будущие admin-отчёты) — planом не был указан конкретный маршрут admin journal"
  - "archivedAt — опциональное поле в GroupResponse (null у активных), чтобы не ломать существующие тесты/моки"
  - "onTabChange() сбрасывает searchControl без emitEvent — предотвращает двойной reload (один от onTabChange, один от valueChanges)"
metrics:
  duration: "~15 min executor time"
  completed: "2026-04-14"
  tests_added: "5 admin-api + 3 groups-page + 7 promotion-dialog + 5 group-history = 20"
  files_changed: 16
  commits: 4
---

# Phase 58 Plan 08: Groups Frontend — Archive tab + Promotion dialog + History page

BUG-006 п.5 и п.6 закрыты со стороны UI. Админ-панель теперь содержит:
- два таба в `/admin/groups` (Активные и Архив) с независимым ILIKE-поиском;
- модалку перевода групп на следующий курс (preview → confirm → итог);
- read-only страницу истории архивной группы `/admin/groups/:id/history` с защитой от открытия активных групп.

Фронтенд подключается к готовым backend endpoints из плана 06 без правок API.

## What Changed

### Types & Service (`shared/`)

- **`types.ts`** — добавлены типы из контракта плана 06: `GroupStatus`, `PromotionPreviewItem`, `PrefixConflict`, `PromotionSummary`. `GroupResponse` расширен опциональным `archivedAt: string | null`.
- **`admin-api.service.ts`** — четыре новых метода:
  - `listGroupsByStatus({status, search, page, size})` → `{items, total}`.
  - `promotePreview()` → `PromotionSummary` (dryRun=true).
  - `promote()` → `PromotionSummary` (executed=true).
  - `getGroup(id)` → `GroupResponse`.
- **Spec** — +5 тестов (13→18) покрывают оба status-фильтра, preview/promote body и getGroup URL.

### Groups page (tabs + search + promotion entry)

- Импортирован `MatTabsModule`; `ReactiveFormsModule` + `FormControl` для поиска.
- Два набора `displayedColumns`:
  - Активные: `name | headman | studentCount | actions` (как раньше, + badge-точка).
  - Архив: `name | archivedAt | actions` (без редактирования — T-58-08 «архив immutable»).
- `status = signal<GroupStatus>('ACTIVE')` + `searchControl` с `debounceTime(300) + distinctUntilChanged` → `reload()` делает `forkJoin({listGroupsByStatus, listUsers})`.
- `onTabChange(index)` — переключает status, сбрасывает searchControl без emitEvent (предотвращает двойной reload), тут же делает reload.
- В активном табе — кнопки «Создать группу» + «Перевести группы на следующий курс» (зелёная для create, ghost для promote).
- В архивном — только кнопка «История» на каждой строке (без edit/delete).
- SCSS: `.group-badge--active` (green-500), `.group-badge--archived` (gray-400), `.btn-brand--ghost`.

### Promotion preview dialog

**`promotion-preview-dialog.component.ts`** — sealed 5-фазная state-machine:

```
loading  → preview  → executing  → done     (success path)
                              ↓
                          error          (любая ошибка)
```

- `ngOnInit()` → `POST /groups/promote/preview`, переход `loading → preview`.
- Template разбит на секции: «Будут переименованы» (зелёная стрелка from → **to**), «Будут архивированы» (plain list), «Конфликты» (orange warn block с hint «Эти префиксы не будут затронуты»).
- `isEmpty()` = true если `toPromote.length === 0 && toArchive.length === 0`; тогда `[disabled]="isEmpty()"` на «Подтвердить». Conflicts-only случай тоже isEmpty — кнопка заблокирована (backend не сможет ничего сделать).
- `confirm()` → `POST /groups/promote`, переход `preview → executing → done`.
- `cancel()` → `dialogRef.close()`. `close()` (после done) → `dialogRef.close('done')` — триггерит reload списка в родителе.
- Кнопки во фазе executing — disabled (T-58-08-01 защита от двойного клика).
- В phase=error есть кнопка «Повторить» (reloadPreview) + «Закрыть».

### Group history page

**`group-history-page.component.ts`** — `/admin/groups/:id/history`:

- `ngOnInit`: разбирает `id` из route, делает `forkJoin({getGroup, listUsers})`.
- `catchError → of(null)` на getGroup → если группа не пришла, `notFound=true` (безопасная деградация вместо throw).
- Guard: если `group.active === true` → snackbar + `router.navigate(['/admin/groups'])` (T-58-08-03 прикрытие случая когда архив открывают по URL у активной группы).
- Нечисловой id → `notFound=true` без HTTP-запросов.
- Вычислимые геттеры:
  - `graduationYear()` — из regex `(выпуск YYYY)` или из `archivedAt.getFullYear()`.
  - `activeName()` — имя без суффикса (крупный заголовок).
- Template: крупный заголовок (активное имя), «выпуск YYYY», серый badge «Архив», meta-grid (полное имя / архивирована / создана / студентов), список студентов, две ссылки на `/admin/dashboard?groupId=N` (журнал/посещаемость — plan не указал конкретный admin-роут для истории, использован dashboard как placeholder).

### Routing

Маршрут добавлен в `src/app/app.routes.ts` под `path: 'admin' > children`:

```ts
{
  path: 'groups/:id/history',
  loadComponent: () => import('./features/admin/groups/group-history/group-history-page.component').then(m => m.GroupHistoryPageComponent),
  data: { title: 'История группы', eyebrow: 'Администратор' },
}
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] План упоминает `admin-routes.ts`, в проекте — single-file `app.routes.ts`**
- **Found during:** Task 4.
- **Issue:** Plan `<files_modified>` и action step просили `frontends/web-panel/src/app/features/admin/admin-routes.ts`. Такого файла нет: все маршруты (teacher/admin/student/headman) — в `src/app/app.routes.ts`, admin-ветка это children массива под path='admin' с roleGuard(['ADMIN']).
- **Fix:** Добавил маршрут `groups/:id/history` inline в admin children. roleGuard уже применён к родительскому пути. 12 строк добавлено, существующие маршруты не тронуты.
- **Commit:** `e34d11b`.

**2. [Rule 2 — Missing critical functionality] Защита от двойного reload при смене таба**
- **Found during:** Task 2 тестирование.
- **Issue:** Наивная реализация `onTabChange(index) { this.status.set(...); this.searchControl.setValue(''); }` выдавала бы два reload: один от `onTabChange.reload()`, второй от `valueChanges` после setValue. `expectOne` в тестах ловил бы лишний запрос.
- **Fix:** `searchControl.setValue('', { emitEvent: false })` — тихо очищаем без триггера valueChanges; затем одиночный `reload()`. Один запрос на смену таба.
- **Commit:** `f4c44f0`.

**3. [Rule 1 — Bug] Existing test fixtures использовали `displayName`, но types.ts — `lastName/firstName`**
- **Found during:** Task 2 переписывание spec.
- **Issue:** Старый `groups-page.component.spec.ts` использовал `displayName: 'Иванов Иван'` в моках UserResponse, но реальный тип имеет `lastName/firstName/middleName`. Тесты проходили только потому что fixture принималась через type inference без validated literal.
- **Fix:** Новые моки используют правильные поля `lastName/firstName/middleName: null`. Assertions на `headman.lastName` вместо `headman.displayName`.
- **Commit:** `f4c44f0`.

## Commits

| Hash    | Title                                                                                               |
| ------- | --------------------------------------------------------------------------------------------------- |
| 65bad2d | feat(web-panel-58-08): admin-api типы + методы для archive/promotion (BUG-006-6)                    |
| f4c44f0 | feat(web-panel-58-08): табы Активные/Архив + модалка перевода групп (BUG-006-5/6)                   |
| e34d11b | feat(web-panel-58-08): read-only страница истории архивной группы (BUG-006-6)                       |

## Verification

- `cd frontends/web-panel && npx vitest run` — **49 файлов / 346 тестов passed**.
- Целевые unit-тесты:
  - `admin-api.service.spec.ts` — 18/18 (13 старых + 5 новых).
  - `groups-page.component.spec.ts` — 7/7 (переписан под табы/поиск, все зелёные).
  - `promotion-preview-dialog.component.spec.ts` — 7/7 (preview, conflicts, isEmpty, confirm, cancel, close, error).
  - `group-history-page.component.spec.ts` — 5/5 (happy, graduationYear, active-redirect, 404, invalid-id).
- `cd frontends/web-panel && npm run build` — **Output dist/** успешно. Pre-existing warnings: NG8102 в student-homework.component.html (Plan 08 не трогал), bundle size (pre-existing), CommonJS stompjs/sockjs (pre-existing). **TS-ошибок нет.**

## Threat Model Coverage

| Threat | Disposition | Status |
|--------|-------------|--------|
| T-58-08-01 — double-click confirm | accept | ✅ Защита: `phase='executing'` блокирует кнопки + `confirm()` no-op если phase≠'preview' |
| T-58-08-02 — non-admin видит архив | mitigate | ✅ `roleGuard(['ADMIN'])` уже на `/admin/**` parent route — унаследовано |
| T-58-08-03 — InfoDisclosure history URL | accept | ✅ roleGuard + T-58-08-03: guard на активную группу (snackbar + redirect) |
| T-58-08-04 — race на execute | accept | ✅ Backend natural protection (plan 06 T-58-06-04). UI не может инициировать два параллельных promote — единственный путь через модалку, которая disabled во время executing |

## Success Criteria

- [x] **AC-6 UI**: одно поле name — унаследовано от плана 04, визуально корректно в табах.
- [x] **AC-7 UI**: кнопка «Перевести» + preview + execute полноценно работают.
- [x] Архив отдельно от активных (MatTabGroup).
- [x] История архивной группы — read-only (edit/delete buttons отсутствуют).
- [x] PUT архивной группы из UI невозможен — в архивном табе нет кнопки редактирования.
- [x] ILIKE поиск debounced (300ms) в обоих табах.

## Known Stubs

**Ссылки «Журнал» и «Посещаемость» на странице истории ведут на `/admin/dashboard?groupId=N`** — placeholder, т.к. plan не указал конкретный admin-маршрут для просмотра журнала архивной группы. В тестах проверены через `data-testid="journal-link"` / `"attendance-link"`. Для UAT admin сможет увидеть кнопки и узнать id; dashboard принимает query param, но фильтрация по groupId в нём пока не реализована. Не блокирует основной функционал (AC-6/AC-7) — история сама по себе показывает имя/год/студентов/метаданные.

## Threat Flags

Нет новых threat surface-ов. Все 4 угрозы из `<threat_model>` учтены (см. таблицу выше). Новых сетевых endpoints, файловых операций, auth-flow — не добавлено; страница истории использует уже защищённый `/api/academic/groups/{id}` (роль ADMIN+TEACHER по плану 06).

## Cross-Plan Notes

- **Plan 07 (group-rename-archive-events)** — выполняется параллельно, Java/backend only. Никаких файловых пересечений.
- **Plan 09 (final-verification-report)** — ожидает gsd-verifier прогон; этот план (08) полностью завершает UI-часть BUG-006 и готов к интеграционным проверкам.

## Self-Check: PASSED

Файлы созданы (absolute paths):

- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\frontends\web-panel\src\app\features\admin\groups\promotion-preview-dialog\promotion-preview-dialog.component.ts` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\frontends\web-panel\src\app\features\admin\groups\promotion-preview-dialog\promotion-preview-dialog.component.html` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\frontends\web-panel\src\app\features\admin\groups\promotion-preview-dialog\promotion-preview-dialog.component.spec.ts` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\frontends\web-panel\src\app\features\admin\groups\group-history\group-history-page.component.ts` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\frontends\web-panel\src\app\features\admin\groups\group-history\group-history-page.component.html` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\frontends\web-panel\src\app\features\admin\groups\group-history\group-history-page.component.spec.ts` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\frontends\web-panel\src\app\features\admin\groups\groups-page.component.scss` — **FOUND**.

Commits (verified via `git log --oneline`):

- `65bad2d` — FOUND in git log.
- `f4c44f0` — FOUND in git log.
- `e34d11b` — FOUND in git log.
