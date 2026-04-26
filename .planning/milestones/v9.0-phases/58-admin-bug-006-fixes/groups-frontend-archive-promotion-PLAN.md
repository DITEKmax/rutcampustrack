---
phase: 58-admin-bug-006-fixes
plan: 08
type: execute
wave: 4
depends_on: [04, 06]
files_modified:
  - frontends/web-panel/src/app/features/admin/groups/groups-page.component.ts
  - frontends/web-panel/src/app/features/admin/groups/groups-page.component.html
  - frontends/web-panel/src/app/features/admin/groups/groups-page.component.scss
  - frontends/web-panel/src/app/features/admin/groups/groups-page.component.spec.ts
  - frontends/web-panel/src/app/features/admin/groups/promotion-preview-dialog/promotion-preview-dialog.component.ts
  - frontends/web-panel/src/app/features/admin/groups/promotion-preview-dialog/promotion-preview-dialog.component.html
  - frontends/web-panel/src/app/features/admin/groups/promotion-preview-dialog/promotion-preview-dialog.component.spec.ts
  - frontends/web-panel/src/app/features/admin/groups/group-history/group-history-page.component.ts
  - frontends/web-panel/src/app/features/admin/groups/group-history/group-history-page.component.html
  - frontends/web-panel/src/app/features/admin/groups/group-history/group-history-page.component.spec.ts
  - frontends/web-panel/src/app/features/admin/admin-routes.ts
  - frontends/web-panel/src/app/features/admin/shared/admin-api.service.ts
  - frontends/web-panel/src/app/features/admin/shared/types.ts
autonomous: true
requirements:
  - BUG-006-5
  - BUG-006-6
  - FR-5
  - FR-6
user_setup: []
must_haves:
  truths:
    - "Страница /admin/groups имеет табы 'Активные' / 'Архив', toggle между ними"
    - "Оба таба поддерживают ILIKE-поиск через ?search=..."
    - "В вкладке 'Активные' — кнопка 'Перевести группы на следующий курс'"
    - "Кнопка открывает модалку с preview (POST /groups/promote/preview) — показывает списки: будут переименованы, будут архивированы, конфликты"
    - "В модалке кнопка 'Подтвердить' → POST /groups/promote → обновляет таблицу"
    - "В вкладке 'Архив' для каждой строки — кнопка 'История' → открывает /admin/groups/:id/history"
    - "Страница истории показывает архивную группу: name (читаемое), год выпуска, студенты, ссылки на журнал (read-only) и посещаемость"
    - "В вкладке 'Архив' кнопки 'Редактировать' НЕТ (архив immutable — planовано разблокировать позже)"
    - "Статус группы подсвечивается badge'ем: Активная (зелёный) / Архив (серый)"
  artifacts:
    - path: frontends/web-panel/src/app/features/admin/groups/promotion-preview-dialog/promotion-preview-dialog.component.ts
      provides: "модалка preview+confirm"
    - path: frontends/web-panel/src/app/features/admin/groups/group-history/group-history-page.component.ts
      provides: "read-only страница архивной группы"
  key_links:
    - from: groups-page tab ACTIVE
      to: GET /groups?status=active&search=
      via: admin-api.service
      pattern: "existing list pattern"
    - from: groups-page tab ARCHIVED
      to: GET /groups?status=archived&search=
      via: admin-api.service
      pattern: "same endpoint, status filter"
    - from: promotion-preview-dialog
      to: POST /groups/promote/preview + POST /groups/promote
      via: admin-api.service
      pattern: "two-step confirmation"
    - from: group-history-page
      to: "GET /groups/:id + existing journal/attendance admin endpoints"
      via: admin-api.service
      pattern: "read-only"
---

<objective>
Frontend часть фазы 58: табы Активные/Архив на странице групп, поиск, модалка preview+confirm для промоушена, read-only страница истории архивной группы.

Depends on:
- Plan 04 — одно поле name, unified types.
- Plan 06 — backend endpoints preview/execute и status filter.

Purpose: закрывает UI-часть BUG-006-5 и BUG-006-6.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
@$HOME/.claude/get-shit-done/references/ui-brand.md
</execution_context>

<context>
@.planning/phases/58-admin-bug-006-fixes/58-CONTEXT.md
@.planning/phases/58-admin-bug-006-fixes/58-04-SUMMARY.md
@.planning/phases/58-admin-bug-006-fixes/58-06-SUMMARY.md
@CLAUDE.md
@docs/design-decisions.md
@frontends/web-panel/src/app/features/admin/groups/groups-page.component.ts
@frontends/web-panel/src/app/features/admin/shared/admin-api.service.ts

<interfaces>
<!-- Backend contracts: -->
<!-- GET /api/academic/groups?status=ACTIVE|ARCHIVED|ALL&search=... → Paged<GroupResponse> -->
<!-- POST /api/academic/groups/promote/preview → PromotionSummary -->
<!-- POST /api/academic/groups/promote → PromotionSummary -->
<!-- PromotionSummary: { toPromote:[{id,from,to,action}], toArchive:[{id,from,action}], conflicts:[{prefix,reason,message,groupIds}], dryRun, executed } -->
-->
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: admin-api.service — методы для groups endpoints</name>
  <files>
    frontends/web-panel/src/app/features/admin/shared/admin-api.service.ts,
    frontends/web-panel/src/app/features/admin/shared/types.ts
  </files>
  <action>
    1. Добавить типы в `types.ts`:
       ```ts
       export type GroupStatus = 'ACTIVE' | 'ARCHIVED' | 'ALL';
       export interface PromotionPreviewItem { id: number; from: string; to?: string; action: 'PROMOTE' | 'ARCHIVE'; }
       export interface PrefixConflict { prefix: string; reason: 'name_conflict'|'unknown_type'|'parse_error'; message: string; groupIds: number[]; }
       export interface PromotionSummary { toPromote: PromotionPreviewItem[]; toArchive: PromotionPreviewItem[]; conflicts: PrefixConflict[]; dryRun: boolean; executed: boolean; }
       ```
    2. Добавить методы в `admin-api.service.ts`:
       ```ts
       listGroups(params: { status: GroupStatus; search?: string; page?: number; size?: number }): Observable<PagedModel<Group>> { ... }
       promotePreview(): Observable<PromotionSummary> { return this.http.post<PromotionSummary>('/api/academic/groups/promote/preview', {}); }
       promote(): Observable<PromotionSummary> { return this.http.post<PromotionSummary>('/api/academic/groups/promote', {}); }
       getGroupHistory(groupId: number): Observable<GroupHistory> { /* comb current endpoints for attendance/students */ }
       ```
    3. Spec для service — 4 теста (listGroups с разными status, promotePreview, promote, getGroupHistory).
  </action>
  <verify>
    <automated>cd frontends/web-panel && npm test -- --run admin-api</automated>
  </verify>
  <done>Service methods typed и покрыты тестами.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: groups-page — табы Активные/Архив + поиск</name>
  <files>
    frontends/web-panel/src/app/features/admin/groups/groups-page.component.ts,
    frontends/web-panel/src/app/features/admin/groups/groups-page.component.html,
    frontends/web-panel/src/app/features/admin/groups/groups-page.component.scss,
    frontends/web-panel/src/app/features/admin/groups/groups-page.component.spec.ts
  </files>
  <behavior>
    - Test 1: По умолчанию активный таб — 'Активные', грузит listGroups({status:'ACTIVE'})
    - Test 2: Клик на таб 'Архив' → listGroups({status:'ARCHIVED'})
    - Test 3: Ввод в поле поиска (debounce 300ms) → listGroups с параметром search
    - Test 4: В архивной таблице колонки: name (читаемое с суффиксом), archivedAt, кнопка 'История'
    - Test 5: В активной таблице — колонки: name, isActive, createdAt, кнопки 'Редактировать', 'Удалить'
    - Test 6: В активной таблице — кнопка 'Перевести группы на следующий курс' (сверху, рядом с 'Создать')
    - Test 7: В архивной таблице кнопки 'Редактировать' НЕТ
  </behavior>
  <action>
    1. `groups-page.component.ts`:
       ```ts
       status = signal<GroupStatus>('ACTIVE');
       search = signal<string>('');
       groups$ = combineLatest([toObservable(this.status), toObservable(this.search).pipe(debounceTime(300))])
         .pipe(switchMap(([status, search]) => this.api.listGroups({ status, search })));
       onTabChange(status: GroupStatus) { this.status.set(status); }
       openPromotion() { this.dialog.open(PromotionPreviewDialogComponent); }
       openHistory(groupId: number) { this.router.navigate(['/admin/groups', groupId, 'history']); }
       ```
    2. Template:
       ```html
       <mat-tab-group (selectedIndexChange)="onTabChange($event === 0 ? 'ACTIVE' : 'ARCHIVED')">
         <mat-tab label="Активные">
           <div class="actions">
             <button mat-raised-button color="primary" (click)="openCreate()">Создать</button>
             <button mat-stroked-button color="accent" (click)="openPromotion()">Перевести группы на следующий курс</button>
           </div>
           <mat-form-field><input matInput placeholder="Поиск" (input)="search.set($any($event.target).value)"></mat-form-field>
           <table mat-table [dataSource]="groups$ | async"> ... активные колонки ... </table>
         </mat-tab>
         <mat-tab label="Архив">
           <mat-form-field><input matInput placeholder="Поиск в архиве" (input)="search.set($any($event.target).value)"></mat-form-field>
           <table mat-table [dataSource]="groups$ | async"> ... архивные колонки с кнопкой История ... </table>
         </mat-tab>
       </mat-tab-group>
       ```
    3. SCSS: badge для статуса (зелёный active, серый archived).
    4. Spec — 7 тестов выше.
  </action>
  <verify>
    <automated>cd frontends/web-panel && npm test -- --run groups-page</automated>
  </verify>
  <done>Табы работают; поиск debounced; переходы на историю и промоушен работают.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 3: promotion-preview-dialog — модалка preview+confirm</name>
  <files>
    frontends/web-panel/src/app/features/admin/groups/promotion-preview-dialog/promotion-preview-dialog.component.ts,
    frontends/web-panel/src/app/features/admin/groups/promotion-preview-dialog/promotion-preview-dialog.component.html,
    frontends/web-panel/src/app/features/admin/groups/promotion-preview-dialog/promotion-preview-dialog.component.spec.ts
  </files>
  <behavior>
    - Test 1: ngOnInit → вызывает promotePreview(); loading state пока грузится
    - Test 2: После загрузки показывает 3 секции: "Будут переименованы" (from → to), "Будут архивированы" (name), "Конфликты" (prefix, reason, message)
    - Test 3: Если conflicts не пуст — секция выделена жёлтым/оранжевым (warn), но кнопка 'Подтвердить' активна (префиксы без конфликтов всё равно будут обработаны)
    - Test 4: Если toPromote и toArchive оба пустые — показывает "Нет групп для промоушена", кнопка 'Подтвердить' disabled
    - Test 5: Клик 'Подтвердить' → promote() → успех → dialog.close('done')
    - Test 6: Клик 'Отмена' → dialog.close() без вызова promote
    - Test 7: После promote() отображает итог: "Переименовано: N, Архивировано: M, Конфликтов: K"
  </behavior>
  <action>
    1. Компонент с двумя фазами: `preview` (показывает план) → `executing` (loading) → `done` (итог).
    2. Template:
       ```html
       @if (loading()) { <mat-spinner/> }
       @else if (phase() === 'preview') {
         <h2>Предпросмотр перевода групп</h2>
         @if (summary().toPromote.length) { <section><h3>Будут переименованы ({{summary().toPromote.length}})</h3><ul>@for(i of summary().toPromote; track i.id){<li>{{i.from}} → <b>{{i.to}}</b></li>}</ul></section> }
         @if (summary().toArchive.length) { <section><h3>Будут архивированы ({{summary().toArchive.length}})</h3><ul>@for(i of summary().toArchive; track i.id){<li>{{i.from}}</li>}</ul></section> }
         @if (summary().conflicts.length) { <section class="warn"><h3>Конфликты ({{summary().conflicts.length}})</h3><ul>@for(c of summary().conflicts; track c.prefix){<li><b>{{c.prefix}}</b>: {{c.message}}</li>}</ul></section> }
         <button mat-button (click)="cancel()">Отмена</button>
         <button mat-raised-button color="primary" [disabled]="isEmpty()" (click)="confirm()">Подтвердить</button>
       }
       @else if (phase() === 'done') {
         <h2>Готово</h2>
         <p>Переименовано: {{result().toPromote.length}}, Архивировано: {{result().toArchive.length}}, Конфликтов: {{result().conflicts.length}}</p>
         <button mat-raised-button (click)="close()">Закрыть</button>
       }
       ```
    3. Spec — 7 тестов.
  </action>
  <verify>
    <automated>cd frontends/web-panel && npm test -- --run promotion-preview-dialog</automated>
  </verify>
  <done>Модалка корректно отображает preview и выполняет execute.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 4: group-history-page — read-only страница архивной группы</name>
  <files>
    frontends/web-panel/src/app/features/admin/groups/group-history/group-history-page.component.ts,
    frontends/web-panel/src/app/features/admin/groups/group-history/group-history-page.component.html,
    frontends/web-panel/src/app/features/admin/groups/group-history/group-history-page.component.spec.ts,
    frontends/web-panel/src/app/features/admin/admin-routes.ts
  </files>
  <behavior>
    - Test 1: Маршрут /admin/groups/:id/history загружает компонент
    - Test 2: Компонент грузит getGroup(id) и показывает:
      - Название (полное с суффиксом, крупно): УИТ-411 (выпуск 2026)
      - Год выпуска отдельно
      - archivedAt (дата)
      - Список студентов (read-only)
      - Ссылки: "Журнал группы" → открывает admin journal view с фильтром group_id, "Посещаемость" → открывает admin attendance view
    - Test 3: Если группа не архивная (is_active=true) → перенаправить на /admin/groups с ошибкой "Эта группа активна, история недоступна"
    - Test 4: Если группа не найдена (404) → сообщение "Группа не найдена"
  </behavior>
  <action>
    1. Добавить маршрут в `admin-routes.ts`:
       ```ts
       {
         path: 'groups/:id/history',
         loadComponent: () => import('./groups/group-history/group-history-page.component').then(m => m.GroupHistoryPageComponent)
       }
       ```
    2. Компонент:
       ```ts
       group = signal<Group | null>(null);
       ngOnInit() {
         const id = +this.route.snapshot.params['id'];
         this.api.getGroup(id).subscribe(g => {
           if (g.isActive) { this.router.navigate(['/admin/groups']); return; }
           this.group.set(g);
         });
       }
       ```
    3. Template показывает данные + две кнопки-ссылки на журнал/посещаемость (используют существующие admin journal/attendance страницы с query param `?group_id=N`).
    4. Spec — 4 теста с MockActivatedRoute и AdminApiService stub.
  </action>
  <verify>
    <automated>cd frontends/web-panel && npm test -- --run group-history</automated>
  </verify>
  <done>Страница доступна, показывает данные, редиректит если группа активна.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Browser → Angular → Gateway | admin JWT проверяется gateway'ем + backend guard |
| Promotion confirm dialog | требует ADMIN роли (backend 403 если не admin) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-58-08-01 | Tampering (client bypasses preview) | promotion-preview-dialog | accept | Backend не требует preview перед execute; но UX требует. Защита от двойного клика — через disabled button во время request |
| T-58-08-02 | Elevation of Privilege (non-admin видит архив) | groups-page tab ARCHIVED | mitigate | Маршрут `/admin/**` защищён AdminGuard (существующий); GET /groups сам по себе требует ADMIN+TEACHER, TEACHER может увидеть архив — это ок (read-only) |
| T-58-08-03 | Information Disclosure (архивные данные в URL) | /admin/groups/:id/history | accept | URL с group_id — ok; AdminGuard ограничивает доступ |
| T-58-08-04 | Integrity (race на execute) | two admins click | accept | Backend natural protection (см. план 06 T-58-06-04) |
</threat_model>

<verification>
- `cd frontends/web-panel && npm test` — все зелёные
- `npm run build` — без TS-ошибок
- Manual:
  1. Залогиниться как ADMIN
  2. /admin/groups → видны 2 таба. Активный таб — список групп с кнопкой "Перевести"
  3. Клик "Перевести" → модалка с preview → Подтвердить → обновляется список
  4. Переключиться на таб "Архив" → видны архивированные
  5. Клик на "История" → открывается страница с деталями и ссылками
  6. Поиск "УИТ" в архиве → фильтрует
</verification>

<success_criteria>
- AC-6 UI: одно поле name, визуально через план 04 (этот план не добавляет новых полей)
- AC-7 UI: кнопка Promote + preview + execute работают
- Архив отдельно от активных
- История архивной группы доступна read-only
- PUT архивной группы из UI невозможен (кнопки редактирования нет в таблице архива)
</success_criteria>

<output>
Создать `.planning/phases/58-admin-bug-006-fixes/58-08-SUMMARY.md`.

## Commit message
`feat(admin-ui): groups archive tab, promotion preview dialog, group history page (BUG-006-5/6)`
</output>

## UAT Steps
1. Frontend запущен (`npm start` web-panel, docker compose backend)
2. /admin/groups видит 2 таба
3. Создать УИТ-111, УИТ-411
4. Клик "Перевести" → модалка: "Будут переименованы: УИТ-111 → УИТ-211", "Будут архивированы: УИТ-411"
5. Подтвердить → список обновляется: УИТ-211 активная
6. Таб "Архив" → видно УИТ-411 (выпуск YYYY)
7. Клик "История" → страница с данными архивной группы
8. Поиск "УИТ" в архиве → находит (ILIKE работает и по суффиксу)
