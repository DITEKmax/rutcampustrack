# Phase 53: Student Web Cabinet — Excuses + Late Check-in + PWA Install Banner — Research

**Researched:** 2026-04-09
**Domain:** Angular 19 web-panel — форма excuse tickets, таблица late check-in, PWA install banner
**Confidence:** HIGH

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| STU-WEB-07 | `/student/excuses` — список тикетов студента + форма подачи с выбором занятий и прикреплением файлов; файлы пересылаются через Telegram, НЕ хранятся | Endpoint `GET /api/attendance/reports/student/records` возвращает `AttendanceRecordEntry[]` со статусом; backend excuse submit endpoint отсутствует — применяется graceful degradation; MatDialog + file input |
| STU-WEB-08 | `/student/late-checkin` — список past `absent` занятий с кнопкой "Запросить" на каждую строку | Endpoint `GET /api/attendance/reports/student/records` + фильтрация по `status === 'absent'`; backend late-checkin submit endpoint отсутствует — graceful degradation |
| STU-WEB-10 | После первого успешного логина STUDENT — dismissible banner "Установите RutTrack на главный экран"; dismissal сохраняется в `localStorage`; NO forced redirect | `beforeinstallprompt` + `localStorage` flag `pwa-banner-dismissed`; shell.component.html нужно расширить |

</phase_requirements>

---

## Summary

Фаза 53 добавляет три изолированных фичи поверх уже готового Angular student shell (фазы 51–52): страницу управления excuse tickets, страницу late check-in запросов, и PWA install banner. Все три не требуют backend-работы — существующий endpoint `GET /api/attendance/reports/student/records` покрывает обе таблицы. Backend-эндпоинты для `excuse.submitted` и `late_checkin.requested` отсутствуют (defer v5.0), поэтому submit flows используют graceful degradation: API 404 трактуется как успех с альтернативным snackbar-текстом.

Инфраструктура Angular Material, Transit Grid CSS tokens, `StudentApiService`, `@angular/animations`, Phosphor Icons, `MatDialog`, `MatSnackBar`, `ReactiveFormsModule` — всё уже установлено и настроено. Фаза вводит ровно **0 новых npm-зависимостей**.

**Основная рекомендация:** Реализовать три самостоятельных компонента и один сервис (`StudentBannerService`); расширить `StudentApiService` двумя методами (`getStudentRecords`, `submitExcuse`); разместить PWA banner выше `<router-outlet>` в shell template; добавить 2 маршрута и 2 sidebar nav item.

---

## Project Constraints (from CLAUDE.md)

- Angular 19.2, standalone components, `ChangeDetectionStrategy.OnPush` везде [VERIFIED: package.json]
- `ReactiveFormsModule` (FormBuilder) — проектный стандарт форм (Signal Forms — Angular 22+, ещё экспериментальны) [VERIFIED: student-profile.component.ts]
- Все CSS-токены из `tokens.css`; глобальные классы из `styles.css` (`.page-stack`, `.page-card`, `.btn-brand`, `.page-empty`, `.page-error`, `.status-chip`) — не переопределять [VERIFIED: styles.css]
- Phosphor Icons `regular` (inactive) / `fill` (active) / `duotone` (empty states, hero) [CITED: design-decisions.md §1]
- `@angular/animations` для переходов, НЕ Framer Motion [CITED: 53-UI-SPEC.md]
- Vitest + @testing-library/angular — фреймворк тестирования [VERIFIED: vitest.config.ts]
- **Существующие 265 web-panel тестов не должны сломаться** [ASSUMED: подсчёт `it(` по spec-файлам]
- NO Lombok в контрактных модулях (только для backend, не актуально в этой фазе)

---

## Standard Stack

### Core (уже установлено, новые зависимости не нужны)

| Библиотека | Версия | Цель | Статус |
|------------|--------|------|--------|
| `@angular/core` | 19.2.x | Signals, OnPush CD | [VERIFIED: package.json] |
| `@angular/material` | 19.2.x | MatDialog, MatSnackBar, MatCheckbox, MatTable | [VERIFIED: package.json] |
| `@angular/forms` | 19.2.x | ReactiveFormsModule, FormBuilder | [VERIFIED: package.json] |
| `@angular/animations` | 19.2.x | Banner slide-in/out | [VERIFIED: package.json] |
| `@phosphor-icons/web` | installed | Иконки ph-file-text, ph-clock-countdown, ph-device-mobile-camera | [VERIFIED: 53-UI-SPEC.md] |
| `rxjs` | ~7.8.0 | HttpClient, Observable | [VERIFIED: package.json] |

**Установка:** не требуется — `npm install` уже выполнен.

### Новые типы в `student-schedule.types.ts`

```typescript
// AttendanceRecordEntry с backend — уже возвращает lessonDate, status, symbol, subjectId
export interface AttendanceRecord {
  lessonId: number;
  subjectId: number;
  lessonDate: string;    // YYYY-MM-DD
  lessonNumber: number;
  status: string;        // 'present' | 'absent' | 'excused' | 'free_attendance' | 'cancelled'
  symbol: string;        // 'б' | 'н' | 'у' | 'сп' | '--'
  source: string;        // 'manual' | 'auto' | 'checkin'
}

export type ExcuseTicketStatus = 'pending' | 'approved' | 'rejected' | 'cancelled';

export interface ExcuseTicket {
  id: number;
  createdAt: string;
  subjectNames: string[];
  status: ExcuseTicketStatus;
  lessonIds: number[];
}

export interface ExcuseSubmitRequest {
  lessonIds: number[];
  comment: string | null;
  // files отправляются через FormData отдельно
}
```

---

## Architecture Patterns

### Структура новых файлов

```
features/student/
├── excuses/
│   ├── student-excuses.component.ts       ← STU-WEB-07 главная страница
│   ├── student-excuses.component.html
│   ├── student-excuses.component.css
│   ├── student-excuses.component.spec.ts
│   ├── excuse-form-dialog/
│   │   ├── excuse-form-dialog.component.ts ← MatDialog внутренний компонент
│   │   ├── excuse-form-dialog.component.html
│   │   └── excuse-form-dialog.component.css
├── late-checkin/
│   ├── student-late-checkin.component.ts   ← STU-WEB-08 главная страница
│   ├── student-late-checkin.component.html
│   ├── student-late-checkin.component.css
│   └── student-late-checkin.component.spec.ts
└── shared/
    ├── student-api.service.ts              ← добавить getStudentRecords(), submitExcuse(), requestLateCheckin()
    ├── student-banner.service.ts           ← НОВЫЙ сервис, localStorage PWA banner state
    └── student-schedule.types.ts           ← добавить AttendanceRecord, ExcuseTicket, ExcuseSubmitRequest
```

```
layout/shell/
├── shell.component.html   ← добавить <app-student-pwa-banner> над <router-outlet>
├── shell.component.ts     ← импортировать StudentPwaBannerComponent
└── student-pwa-banner/    ← НОВЫЙ компонент
    ├── student-pwa-banner.component.ts
    ├── student-pwa-banner.component.html
    ├── student-pwa-banner.component.css
    └── student-pwa-banner.component.spec.ts
```

### Маршруты — расширение `app.routes.ts`

Добавить два дочерних маршрута в блок `student` children:

```typescript
{
  path: 'excuses',
  loadComponent: () =>
    import('./features/student/excuses/student-excuses.component')
      .then(m => m.StudentExcusesComponent),
  data: { title: 'Пропуски', eyebrow: 'Студент' },
},
{
  path: 'late-checkin',
  loadComponent: () =>
    import('./features/student/late-checkin/student-late-checkin.component')
      .then(m => m.StudentLateCheckinComponent),
  data: { title: 'Запрос отметки', eyebrow: 'Студент' },
},
```

[VERIFIED: app.routes.ts — студенческий блок children, паттерн взят с homework/stats/notifications]

### Sidebar — добавление двух nav items

В `sidebar.component.ts`, массив `allNavItems`, после блока студенческих items (после `ph-user-circle`):

```typescript
{ label: 'Пропуски',       icon: 'ph-file-text',      route: '/student/excuses',      roles: ['STUDENT'] },
{ label: 'Запрос отметки', icon: 'ph-clock-countdown', route: '/student/late-checkin', roles: ['STUDENT'] },
```

[VERIFIED: sidebar.component.ts — NavItem interface и allNavItems]

### Pattern 1: Backend endpoint для записей посещаемости студента

Существующий endpoint: `GET /api/attendance/reports/student/records` (с опциональным `?subjectId=`)

**Возвращает:** `CollectionModel<EntityModel<AttendanceRecordEntry>>` — в HATEOAS обёртке.

Разворачивать через `_embedded.attendanceRecordEntryList`. Для `/student/excuses` — все записи (для выбора занятий в форме). Для `/student/late-checkin` — фильтровать по `record.status === 'absent'` на клиенте.

[VERIFIED: ReportApi.java:69 — `getStudentRecords(@RequestParam(required=false) Long subjectId)`; ReportController.java:65]

Добавить в `StudentApiService`:

```typescript
getStudentRecords(subjectId?: number): Observable<AttendanceRecord[]> {
  let params = new HttpParams();
  if (subjectId != null) params = params.set('subjectId', String(subjectId));
  return this.http
    .get<PagedResponse<AttendanceRecord>>('/api/attendance/reports/student/records', { params })
    .pipe(map(resp => resp._embedded?.['attendanceRecordEntryList'] ?? []));
}
```

### Pattern 2: Excuse submit — graceful degradation

Backend endpoint для подачи тикета НЕ существует (deferred v5.0). Подход:

```typescript
submitExcuse(lessonIds: number[], comment: string | null, files: File[]): Observable<void> {
  const body = new FormData();
  body.append('lessonIds', JSON.stringify(lessonIds));
  if (comment) body.append('comment', comment);
  files.forEach(f => body.append('files', f));
  return this.http.post<void>('/api/attendance/excuses', body).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 404) return of(undefined); // graceful degradation
      return throwError(() => err);
    })
  );
}
```

MatSnackBar сообщение:
- HTTP 2xx → `"Тикет подан. Документы переданы старосте через Telegram."` (duration 5000ms)
- HTTP 404 (graceful) → `"Запрос отправлен. Подтверждение придёт в Telegram."` (duration 5000ms)
- HTTP 4xx/5xx (не 404) → inline `.page-error` внутри dialog, dialog остаётся открытым

[VERIFIED: 53-UI-SPEC.md §ExcuseFormDialog — Submit behavior; ROADMAP.md Phase 53 Notes]

### Pattern 3: Late check-in per-row action — graceful degradation

```typescript
requestLateCheckin(lessonId: number): Observable<void> {
  return this.http.post<void>(`/api/attendance/late-checkin/${lessonId}`, {}).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 404) return of(undefined);
      return throwError(() => err);
    })
  );
}
```

После успеха (и после 404-degradation) — заменить кнопку на pill "Запрос отправлен" в сигнале `sentRows = signal<Set<number>>(new Set())`.

[VERIFIED: 53-UI-SPEC.md §StudentLateCheckinComponent — Request submit behavior]

### Pattern 4: PWA Install Banner — StudentBannerService

```typescript
@Injectable({ providedIn: 'root' })
export class StudentBannerService {
  private readonly DISMISSED_KEY = 'pwa-banner-dismissed';
  private readonly SHOWN_KEY    = 'pwa-banner-shown';
  
  readonly shouldShow = signal(false);

  // Вызывать из ShellComponent.ngOnInit или студенческого shell после login
  init(user: AuthUser | null): void {
    if (user?.role !== 'STUDENT') { this.shouldShow.set(false); return; }
    if (localStorage.getItem(this.DISMISSED_KEY) === 'true') { this.shouldShow.set(false); return; }
    localStorage.setItem(this.SHOWN_KEY, 'true');
    this.shouldShow.set(true);
  }

  dismiss(): void {
    localStorage.setItem(this.DISMISSED_KEY, 'true');
    this.shouldShow.set(false);
  }
}
```

**Где вызывать `init()`:** В `ShellComponent` или в `StudentPwaBannerComponent.ngOnInit()` через `inject(AuthService).currentUser()`. Предпочтительно — в `StudentPwaBannerComponent` как standalone, чтобы не загрязнять Shell.

[VERIFIED: 53-UI-SPEC.md §StudentPwaBannerComponent — Trigger logic]

### Pattern 5: `beforeinstallprompt` + iOS detection

```typescript
export class StudentPwaBannerComponent implements OnInit, OnDestroy {
  private deferredPrompt: BeforeInstallPromptEvent | null = null;
  readonly isIos = signal(false);
  readonly hasPrompt = signal(false);
  private readonly handler = (e: Event) => {
    e.preventDefault();
    this.deferredPrompt = e as BeforeInstallPromptEvent;
    this.hasPrompt.set(true);
  };

  ngOnInit(): void {
    const ua = navigator.userAgent.toLowerCase();
    this.isIos.set(/iphone|ipad|ipod/.test(ua) && !(window as any).MSStream);
    window.addEventListener('beforeinstallprompt', this.handler);
  }
  
  ngOnDestroy(): void {
    window.removeEventListener('beforeinstallprompt', this.handler);
  }

  async install(): Promise<void> {
    if (this.deferredPrompt) {
      this.deferredPrompt.prompt();
      const { outcome } = await this.deferredPrompt.userChoice;
      if (outcome === 'accepted') this.bannerService.dismiss();
      this.deferredPrompt = null;
    }
    // iOS и другие — просто ссылка /app/ (уже в шаблоне)
  }
}
```

**Тип `BeforeInstallPromptEvent`:** нестандартный, нужен локальный `declare`:

```typescript
interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}
```

[ASSUMED: MDN документация по beforeinstallprompt — не верифицировано в этой сессии через Context7]

### Pattern 6: Shell template — размещение banner

```html
<!-- shell.component.html -->
<div class="shell">
  <app-sidebar class="shell__sidebar" />
  <div class="shell__column">
    <app-header />
    <app-student-pwa-banner />   <!-- добавить сюда, над main -->
    <main class="shell__main" tabindex="-1">
      <div class="shell__content">
        <router-outlet />
      </div>
    </main>
  </div>
</div>
```

Banner рендерится через `@if (bannerService.shouldShow())` внутри самого компонента. ShellComponent должен импортировать `StudentPwaBannerComponent`.

[VERIFIED: shell.component.html — текущая структура]

### Pattern 7: `MatDialog` — открытие ExcuseFormDialog

```typescript
// В StudentExcusesComponent:
readonly dialog = inject(MatDialog);

openExcuseForm(): void {
  const ref = this.dialog.open(ExcuseFormDialogComponent, {
    width: '560px',
    maxWidth: '100vw',
    maxHeight: '80vh',
    ariaLabel: 'Новый тикет о пропуске',
    data: { lessons: this.records() },
  });
  ref.afterClosed().subscribe((submitted: boolean | undefined) => {
    if (submitted) this.loadTickets();
  });
}
```

[VERIFIED: student-profile.component.ts — паттерн MatDialog уже применяется в проекте через imports]

### Anti-Patterns to Avoid

- **Не делать file upload через base64** — использовать `FormData` с `File[]`, браузер сам выставит `Content-Type: multipart/form-data`.
- **Не делать full page reload** после отправки late-checkin строки — только локальное обновление через signal.
- **Не использовать `@NgModule`** — всё standalone, паттерн всего проекта.
- **Не проверять `isPlatformBrowser`** в этом проекте — web-panel не имеет SSR, `localStorage` доступен напрямую.
- **Не вызывать `dismiss()` автоматически при переходе на `/app/`** — только при явном × нажатии или принятии промта.

---

## Don't Hand-Roll

| Проблема | Не строить | Использовать | Причина |
|----------|-----------|--------------|---------|
| Диалоговое окно формы | Кастомный overlay/portal | `MatDialog` из `@angular/material` | Уже используется, `aria-modal`, trap focus, ESC-dismiss |
| Toast-уведомление | Кастомный toast | `MatSnackBar` | Уже в проекте (паттерн из Phase 52) |
| File drag-drop zone | Кастомный DragEvent handler | HTML5 `dragover`/`drop` events на `<div>` + `<input type="file">` | Этого достаточно; `@angular/cdk/drag-drop` для D&D списков, не для upload zone |
| Чекбоксы выбора занятий | Кастомные toggle элементы | `MatCheckbox` | Уже в зависимостях, A11y out of the box |
| beforeinstallprompt state | Redux/NgRx | Простой сервис + signal | Локальный ephemeral state, global store избыточен |

---

## Backend Availability

### Существующие endpoints (VERIFIED)

| Endpoint | Метод | Кто вызывает | Статус |
|----------|-------|-------------|--------|
| `GET /api/attendance/reports/student/records` | GET | Обе страницы | Существует [VERIFIED: ReportApi.java:69] |

### Отсутствующие endpoints (DEFERRED v5.0)

| Endpoint | Ожидаемое событие | Статус | Обработка |
|----------|------------------|--------|-----------|
| `POST /api/attendance/excuses` | `excuse.requested` | НЕ существует | Graceful degradation: 404 → success snackbar alt text |
| `POST /api/attendance/late-checkin/{lessonId}` | `late_checkin.requested` | НЕ существует | Graceful degradation: 404 → row success state |

[VERIFIED: ROADMAP.md Phase 53 Notes — "if endpoints are absent, the form submits gracefully"]
[VERIFIED: attendance-app/ source — нет controller или service для excuse/late-checkin submit]

### ExcuseTicketStatus enum (существует в контракте)

`ExcuseTicketStatus.java`: DRAFT, SUBMITTED, APPROVED, REJECTED [VERIFIED: attendance-api-contract]

Фронтенд-тип: `'pending' | 'approved' | 'rejected' | 'cancelled'` — маппинг при получении с API (когда он появится); пока для graceful degradation нет реальных тикетов с сервера, показываем список только если endpoint вернёт данные.

**Практическое следствие:** `/student/excuses` в graceful mode не показывает реальных тикетов (endpoint не существует). Компонент должен обрабатывать как:
- `GET /api/attendance/excuses` → 404 → показывать empty state без error (graceful)
- `POST /api/attendance/excuses` → 404 → success snackbar (graceful)

Или, если endpoint GET тоже не существует, показывать empty state вместо `.page-error`.

---

## Common Pitfalls

### Pitfall 1: `localStorage` на старте приложения при Server-Side Rendering
**Что идёт не так:** `localStorage.getItem()` бросает ошибку в SSR-окружении.
**Почему:** Angular может запускаться в Node.js.
**Как избежать:** Не актуально — web-panel не использует SSR. `localStorage` безопасен напрямую.
**Вердикт:** НЕ проблема в этом проекте [VERIFIED: vitest.config.ts — jsdom environment].

### Pitfall 2: `beforeinstallprompt` срабатывает только один раз
**Что идёт не так:** Второй `prompt()` не показывается после dismiss.
**Почему:** Браузер не повторяет промт после отказа пользователя.
**Как избежать:** После `userChoice` сбрасывать `deferredPrompt = null`. Если пользователь dismissed (не accepted) — сохранять `hasPrompt = false`, но NOT устанавливать `pwa-banner-dismissed` — баннер остаётся видимым (только без кнопки prompt, либо с ссылкой на `/app/`).
**Сигнал:** `hasPrompt.set(false)` после любого outcome.

### Pitfall 3: HATEOAS embedded key для `getStudentRecords`
**Что идёт не так:** `_embedded?.['attendanceRecordEntryList']` возвращает `undefined`.
**Почему:** HAL embedded key = lowercase Java class name + "List". Нужно проверить точное имя.
**Как избежать:** В `ReportController` `CollectionModel.of(entityModels, ...)` — Spring HATEOAS автогенерирует ключ. Для `AttendanceRecordEntry` → ключ будет `attendanceRecordEntryList`. Написать defensive: `resp._embedded?.['attendanceRecordEntryList'] ?? []`.
[VERIFIED: student-api.service.ts:51 — паттерн `?? []` уже применён для lessonResponseList]

### Pitfall 4: MatDialog import в standalone компоненте
**Что идёт не так:** `MatDialog` не открывает диалог или выбрасывает ошибку.
**Почему:** Нужен `provideAnimations()` в `app.config.ts` AND импорт `MatDialogModule` или использование standalone `MatDialog`.
**Как избежать:** В Angular 17+ MatDialog можно inject без импорта модуля; AnimationsModule нужен. Проверить `app.config.ts` — он уже содержит `provideAnimations`.
[ASSUMED: проверить app.config.ts перед выполнением]

### Pitfall 5: File upload — `accept` атрибут недостаточен для валидации
**Что идёт не так:** Пользователь обходит accept-фильтр через drag&drop.
**Почему:** `accept` — только UI-hint, не валидация.
**Как избежать:** Валидировать тип файла программно по `file.type` / расширению в обработчике. 10 МБ лимит: `file.size > 10 * 1024 * 1024`.

### Pitfall 6: Shell component не conditional-render banner для non-STUDENT
**Что идёт не так:** Баннер мелькает у TEACHER/ADMIN.
**Почему:** Shell используется для всех ролей.
**Как избежать:** `StudentBannerService.init(user)` проверяет `user.role !== 'STUDENT'` и устанавливает `shouldShow = false`. Banner компонент сам рендерит `@if (bannerService.shouldShow())`.

---

## Code Examples

### File Upload Drop Zone (HTML5 нативный паттерн)

```typescript
// Source: UI-SPEC §ExcuseFormDialog — File upload section
readonly dragOver = signal(false);
readonly files = signal<File[]>([]);
readonly fileErrors = signal<string[]>([]);

onDrop(event: DragEvent): void {
  event.preventDefault();
  this.dragOver.set(false);
  const droppedFiles = Array.from(event.dataTransfer?.files ?? []);
  this.addFiles(droppedFiles);
}

onFileInputChange(event: Event): void {
  const input = event.target as HTMLInputElement;
  this.addFiles(Array.from(input.files ?? []));
}

private addFiles(newFiles: File[]): void {
  const errors: string[] = [];
  const valid: File[] = [];
  for (const f of newFiles) {
    if (f.size > 10 * 1024 * 1024) {
      errors.push(`"${f.name}": Файл превышает 10 МБ`);
      continue;
    }
    valid.push(f);
  }
  const combined = [...this.files(), ...valid].slice(0, 5); // max 5 files
  this.files.set(combined);
  this.fileErrors.set(errors);
}

removeFile(index: number): void {
  this.files.update(arr => arr.filter((_, i) => i !== index));
}
```

### PWA Banner Animation (Angular @angular/animations)

```typescript
// Source: 53-UI-SPEC.md §Animation
import { trigger, transition, style, animate } from '@angular/animations';

animations: [
  trigger('bannerSlide', [
    transition(':enter', [
      style({ transform: 'translateY(-100%)', opacity: 0 }),
      animate('200ms ease-out', style({ transform: 'translateY(0)', opacity: 1 })),
    ]),
    transition(':leave', [
      animate('150ms ease-in', style({ opacity: 0, transform: 'translateY(-8px)' })),
    ]),
  ]),
],
```

```typescript
// prefers-reduced-motion guard
private prefersReducedMotion(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}
```

### Виест-тест: banner dismiss

```typescript
// Pattern from student-checkin.component.spec.ts
it('sets pwa-banner-dismissed в localStorage при нажатии ×', async () => {
  localStorage.removeItem('pwa-banner-dismissed');
  const { getByLabelText } = await render(StudentPwaBannerComponent, {
    providers: [/* mock StudentBannerService */],
  });
  await userEvent.click(getByLabelText('Закрыть баннер'));
  expect(localStorage.getItem('pwa-banner-dismissed')).toBe('true');
});
```

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Vitest 3.2.4 + @testing-library/angular 17.4.0 |
| Config file | `frontends/web-panel/vitest.config.ts` |
| Quick run command | `cd frontends/web-panel && npm test` |
| Full suite command | `cd frontends/web-panel && npm test` |

### Phase Requirements → Test Map

| Req ID | Behaviour | Test Type | Automated Command | File Exists? |
|--------|-----------|-----------|-------------------|-------------|
| STU-WEB-07 | Excuses page loads records, renders empty state | unit | `npm test -- --reporter=verbose` | ❌ Wave 0 |
| STU-WEB-07 | ExcuseFormDialog: валидация "хотя бы одно занятие" | unit | same | ❌ Wave 0 |
| STU-WEB-07 | ExcuseFormDialog: graceful 404 → success snackbar alt text | unit | same | ❌ Wave 0 |
| STU-WEB-08 | Late-checkin page: фильтрует только absent, renders rows | unit | same | ❌ Wave 0 |
| STU-WEB-08 | Late-checkin: click row button → success state in-place | unit | same | ❌ Wave 0 |
| STU-WEB-10 | Banner не показывается если `pwa-banner-dismissed=true` | unit | same | ❌ Wave 0 |
| STU-WEB-10 | Banner dismiss → localStorage flag + shouldShow false | unit | same | ❌ Wave 0 |
| STU-WEB-10 | Banner не рендерится для TEACHER/ADMIN | unit | same | ❌ Wave 0 |

### Sampling Rate

- **На каждый коммит:** `cd frontends/web-panel && npm test`
- **Phase gate:** все spec зелёные, включая 265 существующих

### Wave 0 Gaps

- [ ] `src/app/features/student/excuses/student-excuses.component.spec.ts`
- [ ] `src/app/features/student/late-checkin/student-late-checkin.component.spec.ts`
- [ ] `src/app/layout/shell/student-pwa-banner/student-pwa-banner.component.spec.ts`

---

## Environment Availability

Фаза 53 — чисто frontend Angular. Внешних зависимостей нет. Step 2.6: SKIPPED (no external dependencies beyond already-installed npm packages).

---

## Security Domain

Применимые ASVS категории для данной фазы:

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V5 Input Validation | yes | Валидация file type/size на клиенте + сервер должен валидировать (backend deferred) |
| V4 Access Control | yes | `studentGuard` уже защищает `/student/*` маршруты [VERIFIED: app.routes.ts] |
| V2 Authentication | no | Токен уже управляется AuthService |
| V6 Cryptography | no | Не применимо |

**Угрозы специфичные для этой фазы:**

| Pattern | STRIDE | Mitigation |
|---------|--------|------------|
| File upload malicious content | Tampering | Validate `file.type` + extension на клиенте; backend deferred — пометить как TODO в комментарии |
| Открытый localStorage flag | Information Disclosure | Низкий риск — флаг содержит только boolean, не токен |
| XSS через filename в UI | Tampering | Angular template auto-escaping, НЕ использовать `innerHTML` для отображения имён файлов |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `beforeinstallprompt` тип нужно объявлять локально через `declare interface` | Architecture Patterns §5 | Если в `@types/web` уже есть — дублирование, не поломка |
| A2 | HATEOAS embedded key для AttendanceRecordEntry = `attendanceRecordEntryList` | Architecture Patterns §1 | Wrong key → пустой массив, нужно проверить реальный ответ API |
| A3 | `provideAnimations()` уже есть в `app.config.ts` | Common Pitfalls §4 | Без него MatDialog animations не работают |
| A4 | 265 it() = суммарное количество тестов (подсчёт по grep) | Validation Architecture | Цифра приблизительная; реальный pass/fail — npm test |

---

## Open Questions

1. **GET /api/attendance/excuses — существует ли?**
   - Что мы знаем: POST endpoint отсутствует. GET endpoint для списка своих тикетов не найден в ReportApi.java.
   - Что неясно: Нужен ли отдельный endpoint или использовать student/records с фильтром?
   - Рекомендация: Реализовать GET `/api/attendance/excuses` как graceful — при 404 показывать empty state (не `.page-error`). В UI-SPEC это отображение как empty state при "never submitted".

2. **Точное имя HATEOAS embedded key для AttendanceRecordEntry**
   - Что мы знаем: Spring HATEOAS генерирует ключ автоматически из имени класса.
   - Что неясно: `attendanceRecordEntryList` vs `attendanceRecordEntries` — зависит от Spring версии.
   - Рекомендация: Проверить реальный HTTP response через `curl` или Swagger UI при первом запросе; добавить defensive `Object.values(resp._embedded ?? {})[0] ?? []` как fallback.

---

## Sources

### Primary (HIGH confidence)
- [VERIFIED: `frontends/web-panel/src/app/features/student/shared/student-api.service.ts`] — существующие методы, URL patterns
- [VERIFIED: `frontends/web-panel/src/app/app.routes.ts`] — текущая структура маршрутов
- [VERIFIED: `frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts`] — NavItem structure
- [VERIFIED: `frontends/web-panel/src/app/layout/shell/shell.component.html`] — shell template
- [VERIFIED: `services/attendance-service/attendance-api-contract/src/main/java/.../ReportApi.java`] — `getStudentRecords` endpoint
- [VERIFIED: `services/attendance-service/attendance-app/src/main/java/.../report/ReportController.java`] — нет excuse/late-checkin controller
- [VERIFIED: `frontends/web-panel/package.json`] — версии Angular 19.2, зависимости
- [VERIFIED: `.planning/phases/53/53-UI-SPEC.md`] — полная UI спецификация
- [VERIFIED: `frontends/web-panel/src/styles.css`] — глобальные классы `.btn-brand`, `.page-empty`, etc.
- [VERIFIED: `frontends/web-panel/vitest.config.ts`] — vitest конфиг

### Secondary (MEDIUM confidence)
- [CITED: 53-UI-SPEC.md] — beforeinstallprompt + iOS detection pattern
- [CITED: ROADMAP.md Phase 53 Notes] — graceful degradation policy

### Tertiary (LOW confidence)
- [ASSUMED] A1-A4 — см. Assumptions Log

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — все зависимости верифицированы в package.json
- Architecture: HIGH — patterns взяты из существующих Phase 51/52 компонентов
- Backend availability: HIGH — ReportApi.java проверен, отсутствие excuse endpoints подтверждено
- PWA banner API: MEDIUM — beforeinstallprompt assumed from UI-SPEC and training knowledge

**Research date:** 2026-04-09
**Valid until:** 2026-05-09 (стабильный стек, Angular 19.2 LTS)
