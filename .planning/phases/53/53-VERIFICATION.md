---
phase: 53-student-excuses-late-checkin-pwa-banner
verified: 2026-04-09T20:10:00Z
status: human_needed
score: 3/3 must-haves verified
re_verification: false
human_verification:
  - test: "Открыть /student/excuses под учёткой студента, нажать 'Подать тикет', выбрать одно занятие, приложить файл и отправить"
    expected: "Диалог закрывается, появляется snackbar 'Запрос отправлен. Подтверждение придёт в Telegram.' (при 404 graceful degradation — тот же результат)"
    why_human: "Требует реального браузера и работающего приложения; файловый drag-drop и MatDialog нельзя верифицировать автотестами виртуального DOM"
  - test: "Открыть /student/late-checkin, нажать 'Запросить отметку' на строке с absent занятием"
    expected: "Кнопка заменяется pill 'Запрос отправлен' без перезагрузки страницы (при 404 graceful — то же поведение)"
    why_human: "Визуальный in-place success state + анимация требуют реального браузера"
  - test: "Войти под студентом в первый раз (очистить localStorage['pwa-banner-dismissed']), открыть любую страницу /student/*"
    expected: "В верхней части страницы появляется баннер 'Установите RutTrack на главный экран' с кнопкой/ссылкой; нажать × — баннер исчезает и больше не появляется"
    why_human: "beforeinstallprompt срабатывает только в реальных Chrome/Edge с PWA-eligible сайтом; localStorage persistence требует ручной проверки"
  - test: "Войти под TEACHER или ADMIN"
    expected: "PWA-баннер НЕ отображается"
    why_human: "Role-guard баннера нельзя полноценно проверить без реального рендера с живым AuthService"
---

# Phase 53: Student Web Cabinet — Excuses + Late Check-in + PWA Install Banner

**Phase Goal:** A student can submit excuse tickets and late check-in requests from the web browser; after their first web login a non-intrusive banner invites them to install the PWA
**Verified:** 2026-04-09T20:10:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (Roadmap Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `/student/excuses` показывает существующие тикеты и кнопку "Подать тикет"; форма позволяет выбрать занятия и приложить файлы; при отправке показывается подтверждение | ✓ VERIFIED | `StudentExcusesComponent` с `getExcuseTickets()`, `openExcuseForm()`, `ExcuseFormDialogComponent` с drag-drop, file validation, `submitExcuse()` с graceful 404 |
| 2 | `/student/late-checkin` показывает past absent занятия с кнопкой "Запросить" на каждой строке; клик отправляет запрос и показывает success-подтверждение | ✓ VERIFIED | `StudentLateCheckinComponent` с `computed absentRecords`, `sentRows` signal, per-row `requestLateCheckin()`, in-place pill "Запрос отправлен" |
| 3 | После первого успешного входа студента появляется dismissible баннер "Установите RutTrack на главный экран" со ссылкой на `/app/`; после dismiss больше не показывается; без принудительного редиректа | ✓ VERIFIED | `StudentBannerService` с `shouldShow` signal, `localStorage['pwa-banner-dismissed']`, `StudentPwaBannerComponent` интегрирован в `ShellComponent` между `<app-header>` и `<main>` |

**Score:** 3/3 truths verified

### Required Artifacts

| Artifact | Provides | Status | Details |
|----------|----------|--------|---------|
| `frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts` | Типы AttendanceRecord, ExcuseTicket, ExcuseTicketStatus, ExcuseSubmitRequest | ✓ VERIFIED | Все 4 типа присутствуют (строки 133, 143, 145, 153) |
| `frontends/web-panel/src/app/features/student/shared/student-api.service.ts` | HTTP-методы: getStudentRecords, getExcuseTickets, submitExcuse, requestLateCheckin | ✓ VERIFIED | Все 4 метода присутствуют с graceful 404 degradation |
| `frontends/web-panel/src/app/app.routes.ts` | Lazy-loaded маршруты /student/excuses и /student/late-checkin | ✓ VERIFIED | Строки 152–164; оба маршрута перед `redirectTo: 'dashboard'` |
| `frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts` | Nav items Пропуски (ph-file-text) и Запрос отметки (ph-clock-countdown) для STUDENT | ✓ VERIFIED | Строки 157–165 |
| `frontends/web-panel/src/app/features/student/excuses/student-excuses.component.ts` | Страница /student/excuses | ✓ VERIFIED | Экспортирует StudentExcusesComponent, вызывает getExcuseTickets(), открывает MatDialog |
| `frontends/web-panel/src/app/features/student/excuses/excuse-form-dialog/excuse-form-dialog.component.ts` | MatDialog форма подачи тикета | ✓ VERIFIED | selectedLessonIds signal, drag-drop, file validation MAX_FILE_SIZE_BYTES=10MB, submitExcuse(), snackbar |
| `frontends/web-panel/src/app/features/student/late-checkin/student-late-checkin.component.ts` | Страница /student/late-checkin | ✓ VERIFIED | absentRecords computed, sentRows/pendingRows/rowErrors signals, requestLateCheckin per-row |
| `frontends/web-panel/src/app/features/student/shared/student-banner.service.ts` | PWA banner state через localStorage | ✓ VERIFIED | DISMISSED_KEY='pwa-banner-dismissed', role check, shouldShow signal, dismiss() |
| `frontends/web-panel/src/app/layout/shell/student-pwa-banner/student-pwa-banner.component.ts` | PWA install banner с beforeinstallprompt и iOS-fallback | ✓ VERIFIED | beforeinstallprompt listener, isIos detection, bannerSlide animation, dismiss() |
| `frontends/web-panel/src/app/layout/shell/shell.component.ts` | ShellComponent с интегрированным баннером | ✓ VERIFIED | StudentPwaBannerComponent в imports[] (строка 19) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `app.routes.ts` student children | `StudentExcusesComponent` | `loadComponent` lazy import | ✓ WIRED | `import('./features/student/excuses/student-excuses.component').then(m => m.StudentExcusesComponent)` |
| `sidebar.component.ts` allNavItems | `/student/excuses` route | `route: '/student/excuses'` | ✓ WIRED | строка 159 |
| `StudentExcusesComponent` | `ExcuseFormDialogComponent` | `MatDialog.open(ExcuseFormDialogComponent)` | ✓ WIRED | `this.dialog.open(ExcuseFormDialogComponent, ...)` строки 70 и 83 |
| `ExcuseFormDialogComponent` | `StudentApiService.submitExcuse` | `this.apiService.submitExcuse(ids, comment, files)` | ✓ WIRED | строка 149 |
| `StudentLateCheckinComponent` | `StudentApiService.getStudentRecords` | фильтр `status === 'absent'` | ✓ WIRED | `this.apiService.getStudentRecords()` + `computed(() => allRecords().filter(r => r.status === 'absent'))` |
| `StudentLateCheckinComponent` | `StudentApiService.requestLateCheckin` | `this.apiService.requestLateCheckin(lessonId)` | ✓ WIRED | строка в методе `requestLateCheckin(lessonId)` |
| `shell.component.html` | `StudentPwaBannerComponent` | `<app-student-pwa-banner />` | ✓ WIRED | строка 6 shell.component.html, между `<app-header />` и `<main>` |
| `StudentPwaBannerComponent` | `StudentBannerService.shouldShow` | `@if (bannerService.shouldShow())` | ✓ WIRED | строка 1 banner HTML |
| `StudentBannerService.dismiss` | `localStorage['pwa-banner-dismissed']` | `localStorage.setItem('pwa-banner-dismissed', 'true')` | ✓ WIRED | строка 43 student-banner.service.ts |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|--------------------|--------|
| `StudentExcusesComponent` | `tickets` signal | `StudentApiService.getExcuseTickets()` → `GET /api/attendance/excuses` | Да (HATEOAS embedded key + 404 graceful fallback to `[]`) | ✓ FLOWING |
| `ExcuseFormDialogComponent` | `recentLessons` | `MAT_DIALOG_DATA.lessons` (из `getStudentRecords()`) → фильтр last 30 days | Да (`getStudentRecords()` → `GET /api/attendance/reports/student/records`) | ✓ FLOWING |
| `StudentLateCheckinComponent` | `absentRecords` | `getStudentRecords()` → `computed filter status==='absent'` | Да (реальный HTTP-вызов, 404 graceful) | ✓ FLOWING |
| `StudentPwaBannerComponent` | `shouldShow` | `StudentBannerService.shouldShow` signal (читает `AuthService.currentUser()` и `localStorage`) | Да (реальный AuthService inject) | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Check | Result | Status |
|----------|-------|--------|--------|
| npm test проходит (все тесты зелёные) | `npx vitest run` в `/frontends/web-panel` | 267 тестов, 39 файлов — все прошли | ✓ PASS |
| Маршруты зарегистрированы | `grep "student/excuses\|student/late-checkin" app.routes.ts` | Оба маршрута найдены | ✓ PASS |
| Баннер интегрирован в Shell | `grep "app-student-pwa-banner" shell.component.html` | Найдено между app-header и main | ✓ PASS |
| Тесты для новых компонентов существуют | Spec-файлы для excuses (3 теста), late-checkin (4 теста), pwa-banner (3 + 4 теста) | Все найдены, 10+ новых тестов в фазе | ✓ PASS |

### Requirements Coverage

| Requirement | Plan | Описание | Status | Evidence |
|-------------|------|----------|--------|----------|
| STU-WEB-07 | 53-01, 53-02 | `/student/excuses` — список тикетов + форма с file upload | ✓ SATISFIED | StudentExcusesComponent + ExcuseFormDialogComponent полностью реализованы; graceful 404 задокументирован в ROADMAP Notes |
| STU-WEB-08 | 53-01, 53-03 | `/student/late-checkin` — запрос поздней отметки для past absent занятий | ✓ SATISFIED | StudentLateCheckinComponent с absentRecords filter, per-row sentRows, graceful 404 |
| STU-WEB-10 | 53-01, 53-04 | PWA install banner после первого входа студента, dismissible, без редиректа | ✓ SATISFIED | StudentBannerService + StudentPwaBannerComponent + ShellComponent интеграция; localStorage persistence; beforeinstallprompt + iOS fallback |

Примечание: REQUIREMENTS.md по-прежнему показывает статус "Pending" для всех трёх ID — требуется ручное обновление статуса.

### Anti-Patterns Found

Сканирование на TODO/FIXME/placeholder в ключевых файлах фазы: не найдено.

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | — | — | Нет нарушений |

Замечание (ℹ️ Info): В `excuse-form-dialog.component.ts` план 53-02 предполагал два разных snackbar-сообщения для HTTP 2xx vs HTTP 404, но реализация использует одно унифицированное сообщение "Запрос отправлен. Подтверждение придёт в Telegram." Это корректно и обоснованно: оба сценария приходят в `next:` handler (404 конвертируется в `of(undefined)` на уровне сервиса), и ROADMAP Notes явно допускают "placeholder confirmation". Гэпом не является.

### Human Verification Required

#### 1. Excuses форма подачи тикета

**Test:** Открыть `/student/excuses` под учёткой студента. Нажать "Подать тикет". В открывшемся диалоге выбрать одно занятие, перетащить файл в drag-drop зону (или кликнуть для выбора), заполнить комментарий, нажать "Отправить".
**Expected:** Диалог закрывается. Появляется snackbar "Запрос отправлен. Подтверждение придёт в Telegram." Список тикетов обновляется.
**Why human:** Drag-drop, MatDialog, файловый upload, snackbar в реальном браузере нельзя верифицировать автотестами jsdom.

#### 2. Late check-in per-row action

**Test:** Открыть `/student/late-checkin`. Если есть absent строки — нажать "Запросить отметку" на первой строке.
**Expected:** Кнопка заменяется pill со значком и текстом "Запрос отправлен" без перезагрузки страницы. Анимация плавная.
**Why human:** In-place visual replacement и CSS-анимация требуют реального браузера.

#### 3. PWA banner при первом входе студента

**Test:** Очистить `localStorage.removeItem('pwa-banner-dismissed')` в DevTools. Войти под студентом или обновить страницу. Открыть любую `/student/*` страницу.
**Expected:** Баннер "Установите RutTrack на главный экран" виден вверху страницы (между хедером и контентом). Нажать ×. Баннер исчезает. Обновить страницу — баннер больше не появляется.
**Why human:** beforeinstallprompt срабатывает только в Chrome/Edge на PWA-eligible сайте; localStorage persistence требует ручной проверки; визуальная вставка между header и main.

#### 4. PWA banner не показывается для TEACHER/ADMIN

**Test:** Войти под учёткой teacher или admin. Открыть любую страницу.
**Expected:** PWA-баннер не отображается нигде.
**Why human:** Role-guard в StudentBannerService.init() проверяет реальный AuthService.currentUser() — значение доступно только в живой сессии.

### Gaps Summary

Гэпов нет. Все три Success Criteria из ROADMAP верифицированы на уровне кода. Автотесты (267 из 267) проходят. Оставшиеся 4 пункта человеческой верификации — поведенческие/визуальные, не структурные.

---

_Verified: 2026-04-09T20:10:00Z_
_Verifier: Claude (gsd-verifier)_
