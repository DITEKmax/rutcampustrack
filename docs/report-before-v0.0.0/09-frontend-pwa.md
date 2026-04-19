# 09. Frontend PWA — отчёт аудита

## Сводка

`frontends/pwa` — React 19 + Vite + Tailwind v4 + shadcn/ui + Motion + React Router v7 + TanStack Query + vite-plugin-pwa + STOMP/SockJS. Устанавливаемый PWA-клиент «RutTrack», проксируется под `/app/` на проде, ориентирован на мобильные браузеры (iOS Safari standalone + Android Chrome). Выполняет все функции студента и старосты: геоотметка, расписание, ДЗ, уведомления, excuse-тикеты, late-checkin, CRUD предметов/помощников, журнал, статистика красной зоны. Один SPA, 5 feature-папок + shared, ~110 файлов, ~12 000 строк TSX.

В целом ― продукт качественный для мобильного PWA: UI продуман (offline-баннер, стейл-нотис, install-prompt, iOS onboarding), анимации аккуратные, оффлайн-кэш workbox настроен, токены дизайна вынесены в CSS-переменные, тесты есть на критичные блоки (AuthProvider, STOMP, runtime-cache predicate). Но набор дефектов серьёзный: от **безопасных** (XSS-уязвимый JWT в `localStorage`, токен в query string, `Notification` показывает имена чужих студентов), до **сквозных с бэкендом** (нет ролевых guards на фронте — STUDENT может открыть `/group/*`, headman-кэш живёт после logout, cross-user data leak через IndexedDB кэш Workbox), до **функциональных** (hook rules violation в StatsPage решён костыльно, JournalPage один день подряд игнорирует пагинацию, `window.confirm` для delete-ДЗ, FormData кейс ломается на некоторых прокси).

Главные болезненные вещи:
1. **localStorage для JWT** — XSS = кража пары `accessToken/refreshToken` на 7 дней (уже упомянуто в 07-gateway P1-3, но именно PWA пишет в localStorage).
2. **JWT в URL query** (`/api/ws?token=...`) — токены попадают в логи nginx/access.log, в PWA-кэш fetch URLs, в Referer.
3. **Нет роутинг-guard по ролям** — любой STUDENT может зайти по `/group/students`, `/group/subjects` и получить 403 от API, но UI покажет сломанное состояние. Admin/teacher в PWA вообще не предусмотрены.
4. **SW runtime-cache не очищается при смене пользователя** — headman разлогинился, вошёл студент другой группы → кэш `headman-api-cache-v1` ещё 24 часа возвращает данные старосты из предыдущей сессии.
5. **Push-subscription не отвязана от userId** — подписан как студент А, вышел, вошёл студент Б — backend продолжит слать А-push'и на устройство Б, пока endpoint не сменится.
6. **`NotificationCenter` хранит push-истории в `sessionStorage`**, но не очищает на logout → при relogin видны уведомления предыдущего пользователя в той же сессии (пока браузер не закрыт).
7. **VAPID key не кешируется** и дергается каждый subscribe; но главное — **нет fallback при 5xx** на этом endpoint, просто молча падает через `error.message`.
8. **`parseJwt` использует `atob` без обработки URL-safe base64 для всех байт** — на ключах с не-ASCII (русские ФИО в claims) может упасть на этапе декодирования, хотя сейчас не падает, потому что backend кладёт только числа и латиницу. Фрагильно.
9. **`queryClient.refetchOnWindowFocus: false`** — для мобильного PWA с backgrounding это оверкилл: staleTime 5 минут и без focus-refetch значит, что пользователь при возврате из фона вообще не увидит свежих данных пока вручную не потянет.

**Счётчики:** **P0 = 5**, **P1 = 14**, **P2 = 17**, **P3 = 11**.

## Структура модуля

```
frontends/pwa/
├── Dockerfile
├── nginx.conf                       ← SPA fallback + cache headers
├── vite.config.ts                   ← base:/app/, VitePWA injectManifest
├── package.json                     ← React 19, Vite 7, Tailwind 4
├── components.json                  ← shadcn
├── public/                          ← icons, manifest-icons
└── src/
    ├── App.tsx                      ← МЁРТВЫЙ ФАЙЛ (единственное назначение — комментарий «не используется»)
    ├── main.tsx                     ← createBrowserRouter с basename:/app/
    ├── index.css                    ← tailwind + shadcn bridge + tokens
    ├── styles/{fonts.css, tokens.css}
    ├── sw.ts                        ← SW: push, notificationclick, workbox
    ├── sw-runtime-cache.ts          ← predicate isHeadmanApiRequest
    ├── vite-env.d.ts
    ├── test/setup.ts
    ├── shared/
    │   ├── components/{AppShell, AppHeader, BottomNav, DrawerMenu,
    │   │   OfflineBanner, UpdateBanner, ProtectedRoute, LoadingSpinner,
    │   │   Skeleton, SegmentedControl, useTabs.ts}
    │   ├── hooks/{useInstallPrompt, useNetworkStatus}
    │   ├── lib/{axios, dateUtils, queryClient, utils.ts}
    │   ├── theme/{ThemeProvider, ThemeToggle}
    │   └── types/pwa.d.ts
    ├── components/ui/               ← shadcn (button, card, input, label, alert, separator)
    ├── lib/utils.ts                 ← cn() дубликат (см. P3-1)
    └── features/
        ├── auth/{AuthProvider, LoginPage, IOSOnboardingOverlay, api.ts}
        ├── checkin/{CheckInScreen, CheckInButton, CheckInToast,
        │   StompProvider, useStompCheckin, api.ts, types.ts}
        ├── schedule/{SchedulePage, LessonCard, WeekDayTabs, StatusBadge,
        │   LessonActionsSheet, HeadmanLessonSheet, OfflineStaleNotice,
        │   statusSymbols.ts, api.ts, lessonActionsApi.ts,
        │   headmanSheetApi.ts, types.ts}
        ├── home/{HomeDashboard, ProfileHeader, StatCards, StatusDonut,
        │   WeeklyChart, TopMissedList, useCountUp, api.ts}
        ├── homework/{HomeworkPage, DayView, WeekView, MonthView,
        │   HomeworkCard, LessonHomeworkSection, HomeworkInlineForm,
        │   ModeSwitcher, api.ts, types.ts}
        ├── notifications/{NotificationCenter (provider),
        │   NotificationsPage, NotificationSettingsPage, notificationPrefs.ts}
        ├── profile/{ProfilePage, ProfilePlaceholder}  ← ProfilePlaceholder мёртвый (см. P3-2)
        ├── push/{PushPermissionCard, usePushSubscription, pushUtils, api.ts}
        └── headman/
            ├── shared/{headmanApi.ts, types.ts}
            ├── group-hub/GroupHub.tsx
            ├── overview/Overview.tsx
            ├── students/{StudentsList, AddAssistantModal}
            ├── subjects/{SubjectsList, SubjectFormModal}
            ├── journal/{JournalPage, JournalStudentRow}
            ├── excuses/ExcusesPage.tsx
            ├── late-checkin/LessonCheckinPage.tsx
            └── stats/{StatsPage, SubjectStatsCard}
```

Расхождения с `docs/design-decisions.md` и CLAUDE.md минимальные — PWA заявлена в v6.0, `/app/` путь поднят в v9.0 (`docs/url-layout.md`). Но:

- В `components/ui/` shadcn-компоненты импортируются как `@/components/ui/*`, а в `shared/components/` лежат кастомные — это два разных алиаса с пересекающейся семантикой. Разведение хрупкое.
- `src/lib/utils.ts` и `src/shared/lib/utils.ts` — дубликат (оба экспортируют `cn`). См. P3-1.

---

## Критичные проблемы (P0)

### P0-1: 🔧 TO-FIX через JWT cookie — JWT хранится в `localStorage` → полная компрометация при XSS
**Статус (2026-04-18):** будет закрыто фиксом из C0-7 (HttpOnly Secure SameSite=Strict cookie + access in-memory). См. `OWNER-ANSWERS.md` 02-Q-frontend-security.



- **Где:** `src/features/auth/AuthProvider.tsx:28-63` (STORAGE_KEY=`rct.auth.v1`, `writePersisted/readPersisted`); `AuthProvider.tsx:110-123` (восстановление из localStorage на каждом маунте).
- **Что:** access-token (15 мин) + refresh-token (7 дней) пишутся в `localStorage` в JSON. Любой XSS (даже reflected через незаэкранированные названия предметов/ФИО, или через библиотечный инцидент — PWA тянет 90+ транзитивных зависимостей, включая `@stomp/stompjs`, `motion`, `recharts`, `lucide-react@^1.7.0` и `shadcn@^4.1.2` с широкой surface) — `localStorage.getItem('rct.auth.v1')` → один POST на злоумышленника, 7 дней доступа. Параметр `HttpOnly` недоступен для JS — нужен серверный механизм: либо cookie с `HttpOnly; Secure; SameSite=Strict`, либо in-memory access token + refresh в cookie.
- **Риск:** **критично** для релиза. localStorage + ~100 npm-пакетов = один скомпрометированный пакет даёт долгосрочный access, refresh живёт 7 дней и ротируется — украденный refresh действует пока жив.
- **Как чинить:** (а) auth-service на login ставит `refresh_token` в cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=604800`, access возвращается в body и живёт в React state (не в localStorage). Axios-interceptor шлёт `credentials: 'include'` на `/api/auth/refresh`. При рефреше cookie обновляется сервером, access в ответе. (б) Для STOMP WebSocket — вопрос открыт (см. P0-2), но туда можно отдавать отдельный short-lived ticket через `POST /auth/ws-ticket`. Если всё же оставить localStorage, минимум — добавить CSP-header строгий (см. P1-3).
- **Зависимости:** auth-service (новый endpoint refresh с Set-Cookie), api-gateway (CORS `credentials: true` с конкретным Origin). Полный переход займёт 1-2 недели; до этого — абсолютная CSP и dependabot-audit.

### P0-2: 🔧 TO-FIX через WS-ticket — JWT передаётся в query string WebSocket (`/api/ws?token=...`)
**Статус (2026-04-18):** будет закрыто фиксом из C0-7 — `POST /auth/ws-ticket` возвращает 60-сек opaque ticket в Redis, ws подключается с `?ticket=<uuid>`. См. `OWNER-ANSWERS.md` 02-Q-frontend-security.



- **Где:** `src/features/checkin/useStompCheckin.ts:20`; `src/features/notifications/NotificationCenter.tsx:274`.
- **Что:** SockJS конструируется как `new SockJS(\`/api/ws?token=${accessToken}\`)`. Query string попадает в:
  - nginx `access.log` (если не настроено `log_format` с маскированием — не настроено, см. 07-api-gateway отчёт и 13-infra будет);
  - серверный лог Spring Cloud Gateway при любом DEBUG/TRACE (см. 05-notification P3-0 о debug логах);
  - `Referer` header при редиректах;
  - **PWA SW может кэшировать ответ fallback'а SockJS `/api/ws/info?cb=...`** — URL с токеном рискует быть проиндексирован `workbox-precaching` если попадёт в manifest (не попадает сейчас, но достаточно случайного изменения `registerRoute`).
  - DevTools Network tab при отладке — токен пишется в историю HAR.
- **Риск:** краденый access-token из логов = ворота в систему (как P0-1, но с доступом даже к тем, у кого нет XSS-поверхности на странице).
- **Как чинить:** либо (а) заменить SockJS на нативный WebSocket и передать токен в первом STOMP-кадре CONNECT через заголовок `Authorization`, как уже умеет `@stomp/stompjs`:
  ```ts
  new Client({
    brokerURL: 'wss://...',
    connectHeaders: { Authorization: `Bearer ${token}` },
  })
  ```
  либо (б) выделить короткоживущий WS-ticket: `POST /api/auth/ws-ticket` → `{ticket: "<opaque 60s>"}`, подключение `/api/ws?ticket=...`. SockJS fallback поддерживается через `Authorization` заголовок в `transports: ['websocket']` (но нужен серверный interceptor).
- **Зависимости:** notification-service (STOMP handshake должен принимать `Authorization`), api-gateway (прокси-proxy WebSocket с Authorization проброшен). Breaking change для текущего клиента.

### P0-3: Нет фронтовых guards по ролям → STUDENT может зайти на headman-маршруты и сломать UI

- **Где:** `src/main.tsx:36-71` — единственный guard это `ProtectedRoute` по `isAuthenticated`; все headman-маршруты доступны всем авторизованным; `src/shared/components/ProtectedRoute.tsx:1-13` — не принимает никакого `requiredRole`; `src/shared/components/useTabs.ts:20-37` — скрывает только вкладку в нижнем навбаре, но сам маршрут жив.
- **Что:** если студент введёт в адресной строке PWA `https://ruttrack.site/app/group/journal` — роут зарезолвится, запрос уйдёт на `/academic/groups/my/members` и `/academic/subjects`, back 403 Forbidden, query упадёт в error state, юзер увидит бесконечные skeletons или пустоту в зависимости от экрана. Admin/teacher в PWA не предусмотрены, но если они всё-таки попробуют войти (они могут — backend не различает PWA от web-panel), PWA покажет им BottomNav из 4 вкладок (Главная/Расписание/Отметка/Уведомл.), `/home` запросит `/attendance/reports/student/dashboard` → 403 (не студент), экран встанет в ошибке.
- **Риск:** не security (backend проверяет роли, это 07 P1/P2), но: UX ломается. При онбординге TEACHER/ADMIN в PWA (если когда-то разрешим) — будут бесконечные fetch'и, лог бэкенда засорится 403-шками, пользователь не поймёт, что делать. Также headman-маршруты в URL открывают возможность скрытой утечки роли через 403 ответа.
- **Как чинить:** расширить `ProtectedRoute`:
  ```tsx
  <ProtectedRoute requiredRole="STUDENT" requireHeadman>
  ```
  и для headman-веток в `main.tsx` обернуть их отдельной Route-группой с `<ProtectedRoute requireHeadman>`. Не-headman при попытке `/group/*` → `Navigate to="/home"`. Admin/teacher при попытке входа в PWA → экран «Для администраторов — web-panel», редирект или toast. Лучше всего централизованно в `main.tsx`:
  ```tsx
  { path: 'group', element: <RoleGuard requireHeadman><GroupHub/></RoleGuard> }
  ```
- **Зависимости:** ничто критичное, просто лишние роуты дизейблить.

### P0-4: 🔧 TO-FIX через logout cleanup — Service Worker runtime-кэш не очищается при logout → cross-user leak на публичных устройствах
**Статус (2026-04-18):** будет закрыто фиксом из C0-5 — общий `clearAllClientState()` вызывает `caches.keys().then(k => k.forEach(caches.delete))` при logout. См. `OWNER-ANSWERS.md` 02-Q-frontend-security.



- **Где:** `src/sw.ts:104-116` — SWR кэш `headman-api-cache-v1` держится 24 часа / 100 записей; `src/features/auth/AuthProvider.tsx:200-211` — `logout()` чистит только localStorage + state, НЕ сбрасывает CacheStorage и НЕ шлёт `MessageEvent` SW.
- **Что:** сценарий: староста группы A вошёл в PWA → его `GET /api/attendance/reports/journal` закэшировался в `headman-api-cache-v1`; староста нажал «Выйти»; с того же устройства заходит студент группы B. При первом запросе `/api/attendance/reports/journal` (например, старостой группы B или даже студентом-студент не умеет, но headman группы B умеет) — SWR стратегия первого отдаёт **кэшированный ответ старосты группы A**, потом валидирует в фоне. Cross-group data leak. Аналогично `excuses/pending`, `late-checkins/pending`, `academic/subjects*`, `academic/thresholds/resolve`.
- Также: `NotificationCenter` хранит записи в `sessionStorage` (`rct.pwa.notifications.v1`), который очищается только при закрытии вкладки, не при logout. После relogin в той же сессии видна **история чужих уведомлений** (пары, decision'ы по тикетам с именами студентов другой группы).
- **Риск:** утечка ПДн (имена студентов, статусы их пропусков, «одобрено/отклонено»). На общем ноутбуке/планшете в старостате — серьёзно.
- **Как чинить:** в `logout()`:
  ```ts
  // 1) Очистить sessionStorage уведомлений
  sessionStorage.removeItem('rct.pwa.notifications.v1')
  // 2) Очистить все workbox-кэши (не precache)
  if ('caches' in window) {
    const keys = await caches.keys()
    await Promise.all(
      keys.filter(k => k.startsWith('headman-api-cache')).map(k => caches.delete(k))
    )
  }
  // 3) Отписать push-subscription от backend (см. P0-5)
  ```
  Дополнительно — **сделать cacheName зависимым от userId**: `headman-api-cache-v1-u{userId}`; старые кэши удалять в `activate`.
- **Зависимости:** sw.ts (ключ кэша), AuthProvider.logout.

### P0-5: 🔧 TO-FIX через logout cleanup — Push-subscription не отвязывается при logout → чужие пуши приходят следующему пользователю на устройстве
**Статус (2026-04-18):** будет закрыто фиксом из C0-5 — `clearAllClientState()` вызывает `pushSubscription.unsubscribe()` + `DELETE /api/notifications/push/subscriptions/me` (новый endpoint в notification-service). См. `OWNER-ANSWERS.md` 02-Q-frontend-security.



- **Где:** `src/features/auth/AuthProvider.tsx:200-211` (logout); `src/features/push/usePushSubscription.ts:54-70` (unsubscribe — не вызывается автоматически); `src/features/push/api.ts:26-29` (DELETE `/push/subscribe` по endpoint).
- **Что:** флоу: студент A подписался на push (`pushManager.subscribe` → `POST /push/subscribe`); нажал «Выйти» из PWA; входит студент B. Endpoint push-подписки один и тот же (привязан к браузеру + origin), backend продолжает отправлять на него события студента A. Если studentB не делает Ы`subscribe` повторно — устройство получает уведомления про чужую пару.
- Повторный subscribe студентом B добавит новую `push_subscription` строку в БД с тем же endpoint, но backend в Phase 31 не имеет UNIQUE(endpoint) — будут две записи с разным user_id, один endpoint → **двойная рассылка** на одно устройство (см. также 05-notification-service P1/P2 зоны).
- **Риск:** а) утечка приватных уведомлений (студент B видит «Пара началась» для пары студента A), б) noise, в) путаница в UX.
- **Как чинить:** в `AuthProvider.logout` **перед** обнулением токенов:
  ```ts
  try {
    const reg = await navigator.serviceWorker.ready
    const sub = await reg.pushManager.getSubscription()
    if (sub) {
      await apiClient.delete('/push/subscribe', { data: { endpoint: sub.endpoint } })
      await sub.unsubscribe()
    }
  } catch { /* best-effort */ }
  ```
  Второй слой защиты: на backend при `POST /push/subscribe` делать UPSERT по `(endpoint)`, перезаписывая `user_id` (см. 05-notification-service будущий фикс).
- **Зависимости:** API push (backend должен идемпотентно принимать upsert/DELETE — сейчас DELETE есть).

---

## Серьёзные проблемы (P1)

### P1-1: `parseJwt` доверяет payload без валидации подписи

- **Где:** `src/features/auth/AuthProvider.tsx:65-85, 87-108`.
- **Что:** `parseJwt` декодирует base64url середину JWT и возвращает claims. `tokenToUser` берёт `sub/role/group_id/is_headman` и строит `AuthUser`, на которой основано **всё** RBAC-UI: видимость headman-вкладки, группы в запросах, тип dashboard. Если злоумышленник зальёт в localStorage любой JWT с `role:ADMIN,is_headman:true` — PWA покажет headman-UI (а backend откажет — но это downgrade UX и дополнительный лог ошибок, плюс могут быть endpoints, где фронт по claim решает что показывать, а backend закрывает позже).
- **Как чинить:** клиентская валидация подписи не нужна — нужно: не доверять localStorage, см. P0-1. Дополнительно — получать `AuthUser` через `GET /auth/me` или `GET /academic/users/me` (уже используется в `useMe()`) и **сравнивать** с claim'ами JWT; при расхождении — logout. В `AuthProvider` seeding user из JWT оставить как оптимизацию, но финальный источник истины — `/users/me`.
- **Зависимости:** P0-1 решает эту же проблему архитектурно.

### P1-2: Axios-интерсептор не обновляет baseURL для refresh-запроса и обходит собственные interceptors

- **Где:** `src/shared/lib/axios.ts:79` — `axios.post('/api/auth/refresh', { refreshToken })` (прямой `axios`, не `apiClient`).
- **Что:** если API base сменится (`baseURL: '/api'` жёстко забит и повторён как строка в refresh), — разойдётся. Также при рефреше новые `Authorization` заголовок уже не ставится (мы не в retry-цикле), но добавлять Bearer и не надо — refresh берёт refresh-токен из body. Проблема тонкая: **при рефреше не применяется запрос-interceptor**, то есть если вдруг добавят CSRF-header / `X-Trace-Id` — его не будет.
- **Как чинить:** использовать отдельный сырой `axios.create({baseURL:'/api'})` без interceptors для refresh (документировать), либо `apiClient.post('/auth/refresh', {...}, { _skipAuthInterceptor: true })` и в интерсепторе проверять флаг. Сейчас поведение ОК, но хрупкое.

### P1-3: Отсутствует строгая Content Security Policy

- **Где:** `frontends/pwa/nginx.conf:1-38` — нет `add_header Content-Security-Policy`; `src/index.html` (не читал, но заголовок в nginx важнее).
- **Что:** PWA без CSP открыта для inline-skriptov injection (если когда-нибудь в JSX попадёт `dangerouslySetInnerHTML` с backend-данными), для загрузки любых сторонних картинок и iframe. При XSS атакующий может пинговать любой хост наружу, выводить токен из localStorage (см. P0-1).
- **Как чинить:** в nginx.conf:
  ```nginx
  add_header Content-Security-Policy "default-src 'self'; \
    script-src 'self' 'wasm-unsafe-eval'; \
    style-src 'self' 'unsafe-inline'; \
    font-src 'self' data:; \
    img-src 'self' data: https:; \
    connect-src 'self' https://ruttrack.site wss://ruttrack.site; \
    frame-ancestors 'none'; \
    base-uri 'self'; \
    form-action 'self'" always;
  add_header X-Content-Type-Options "nosniff" always;
  add_header Referrer-Policy "no-referrer" always;
  add_header Permissions-Policy "geolocation=(self), camera=(), microphone=()" always;
  ```
- **Зависимости:** тестировать на preview (shadcn stylesheet inline — `style-src 'unsafe-inline'` неизбежен для Tailwind в v4).

### P1-4: STOMP-подписка в `NotificationCenter` переподключается при каждом изменении accessToken → race с refresh

- **Где:** `src/features/notifications/NotificationCenter.tsx:269-345` — effect зависит от `[groupId, userId, isHeadman, accessToken]`.
- **Что:** accessToken меняется раз в 15 минут (при refresh). При каждом refresh — эффект деактивирует текущего STOMP-клиента и создаёт нового. Между `client.deactivate()` и `client.activate()` есть окно 100-500мс, в котором прилетающее `lesson.started` **теряется** (нет backend-replay). Для уведомлений о начале пары — критично, пользователь просто не узнает.
- Аналогично `StompProvider` (`useStompCheckin` зависит только от `groupId` — ОК, токен берётся getter'ом каждый раз при новом connect'е, но client всё равно пересоздаётся при ре-auth? Нет, он стабилен. Только NotificationCenter страдает).
- **Как чинить:** убрать `accessToken` из deps — use tokenRef как в `StompProvider`. Либо подписаться на custom event `rct:token-refreshed` и только в onConnect-заголовке обновлять. Простой фикс:
  ```ts
  useEffect(() => { ... }, [groupId, userId, isHeadman])
  // токен берётся из tokenRef внутри webSocketFactory
  ```
- **Зависимости:** ничто.

### P1-5: `STOMP WebSocket` нет reconnect strategy и нет замораживания при `visibilityState=hidden`

- **Где:** `src/features/checkin/useStompCheckin.ts:22` — `reconnectDelay: 1000`; `NotificationCenter.tsx:275` — `reconnectDelay: 2000`.
- **Что:** (а) на мобильном PWA при фоновом состоянии iOS Safari / Android Chrome **принудительно закроет WebSocket** через 30-60 секунд. При выходе из фона — клиент попытается переподключиться. Но `reconnectDelay:1000` означает фиксированный 1 секунда — без exponential backoff при проблемах с сервером = штормим сервер. (б) в фоне WebSocket не имеет смысла держать открытым — всё равно не пришлёт уведомления (для этого push). Сейчас держится, ест батарею.
- **Как чинить:** добавить `heartbeatIncoming/Outgoing`; добавить `onWebSocketClose` handler, который считает попытки и экспоненциально увеличивает `reconnectDelay`; подписка на `visibilitychange` — при hidden `client.deactivate()`, при visible — `client.activate()`.
- **Зависимости:** ничто.

### P1-6: `useStompCheckin` при повторном `onConnect` после reconnect заново делает `subscribe`, но предыдущая подписка не отписана → дубли

- **Где:** `src/features/checkin/useStompCheckin.ts:22-33`.
- **Что:** `onConnect` вызывается каждый раз при подключении (первичное + все reconnect'ы). Внутри — `client.subscribe(\`/topic/group/${groupId}\`, ...)`. Каждый reconnect добавляет подписку. После 5 reconnect'ов события приходят 6 раз. Даже если backend дедуплицирует — `attendanceCounts[id]++` крутится 6 раз.
- **Как чинить:** использовать `subscribeOnce` либо хранить `Subscription` в ref и `subscription.unsubscribe()` перед новой подпиской. Либо просто `client.subscribe` возвращает объект — сохранить его и отписаться в cleanup эффекта.
- **Зависимости:** ничто.

### P1-7: Handler push в SW использует `data.lesson_id` напрямую — без валидации типа → `tag` ломается если бэкенд пришлёт объект

- **Где:** `src/sw.ts:64-66` — `tag: \`${event_type}-${(data as Record<string, unknown>).lesson_id ?? Date.now()}\``.
- **Что:** если `lesson_id` окажется объектом (баг на бэкенде), template-string превратит в `[object Object]` → все такие push'и получат один и тот же tag → notification replace чужие. При штатной работе всё ок, но любая регрессия на бэкенде ломает UI тихо.
- **Как чинить:** `typeof data.lesson_id === 'number' ? data.lesson_id : Date.now()`.

### P1-8: `NotificationCenter` не сохраняет `notification.click` deep-link через SW — только native Notification сам по себе кликабелен лишь в фокусе

- **Где:** `src/features/notifications/NotificationCenter.tsx:384-418` (`showNativeNotification`) — создаётся `new Notification(...)` без `data.url`, без обработчика `onclick`.
- **Что:** штатное window-level `Notification` при клике просто закрывается. Нет навигации. Пользователь видит баннер «Пара началась» → кликает → ничего. Для SW-push клик обрабатывается (`notificationclick` handler в sw.ts), а для fallback на передний план — нет.
- **Как чинить:**
  ```ts
  const n = new Notification(title, { body, ... })
  n.onclick = () => {
    window.focus()
    const route = notificationRoute(record.type) ?? '/'
    window.location.hash = `#${route}`  // или React Router navigation
    n.close()
  }
  ```
  Навигация через React Router потребует пробрасывать navigate — проще через `window.location.assign(\`/app${route}\`)`.

### P1-9: STOMP-сообщения не валидируются по схеме → любой сломанный payload перезаписывает состояние

- **Где:** `NotificationCenter.tsx:277-323` — `JSON.parse(body) as StompEnvelope`; никаких проверок кроме `!envelope.type`.
- **Что:** если прилетит сообщение `{type:"lesson.started", payload:null}`, `str(payload, key)` вернёт `''` — обработка сносная. Но `payload.lesson_ids` (в `excuse.requested`) без проверки → `lessonIds.length` при не-Array → исключение в `handle` → ломает всю подписку. Есть `try/catch` на JSON.parse, но нет на общий обработчик.
- **Как чинить:** завести zod-схемы (`zod` — ещё не в deps; аналог — `@hapi/joi`, `valibot`) для envelope и validate перед dispatch. Либо минимально — `try { ... } catch { /* skip */ }` вокруг всего body-handler'а.

### P1-10: `SchedulePage.handleToggleBlock` не откатывает optimistic UI при ошибке, и TanStack invalidate работает только по ключу `['schedule']`

- **Где:** `src/features/schedule/SchedulePage.tsx:189-205` + `lessonActionsApi.ts:76-104` — `onSuccess` инвалидирует `['schedule']`, и только; нет optimistic update.
- **Что:** headman нажимает «Заблокировать» → запрос стартует → UI не реагирует пока не вернётся ответ. При задержке сети юзер жмёт ещё раз — double-block запросы. При ошибке — никакого визуального отката, просто toast.
- **Как чинить:** `useMutation({ onMutate: async (lessonId) => { ... setQueryData optimistically ..., onError: (e, _, ctx) => { setQueryData(rollback) } })`. Также `disabled={blockMutation.isPending}` на кнопке.
- **Зависимости:** ничто.

### P1-11: Excuse file upload `multipart/form-data` — axios auto-Content-Type перезаписывается вручную → теряет boundary

- **Где:** `src/features/schedule/lessonActionsApi.ts:41-44` — `headers: { 'Content-Type': 'multipart/form-data' }`.
- **Что:** axios автоматически выставляет `Content-Type: multipart/form-data; boundary=...` когда `data instanceof FormData`. **Явное** указание заголовка `'multipart/form-data'` БЕЗ boundary убивает boundary — сервер не может распарсить. На практике сейчас работает, потому что axios **видит** несовпадение и частично игнорирует, но поведение зависит от версии axios. С axios v1.14 (как в PWA) — нужно проверить; самая безопасная стратегия — не указывать header вовсе.
- **Как чинить:** убрать `headers:{Content-Type:...}` — FormData сам.
- **Зависимости:** ничто.

### P1-12: `ExcusesPage` и `LateCheckinPage` swallow network errors → пользователь видит «Не удалось» без кода причины и без retry

- **Где:** `src/features/headman/excuses/ExcusesPage.tsx:53-64` (`catch { setToast('Не удалось одобрить пропуск') }`); `LateCheckinPage.tsx:46-57` аналогично.
- **Что:** при сетевой ошибке (offline), 403, 404, 409 — одно и то же сообщение. Headman не понимает, что произошло. Также нет кнопки retry, нет подсветки что «уже одобрено другим» (возможно, race с Telegram-ботом).
- **Как чинить:** использовать `mapHeadmanApiError` из `shared/headmanApi.ts:421-434` (он уже есть!) в обработчике. При 409 — «Решение уже принято — список обновлён», перезапросить.

### P1-13: `StatsPage` обходит правило хуков через SubjectStatsCollector, но создаёт N `useJournal`+`useResolveThreshold` запросов одновременно → DDoS собственного бэкенда при 20+ предметах

- **Где:** `src/features/headman/stats/StatsPage.tsx:155-184, 271-280`.
- **Что:** на каждый предмет создаётся `SubjectStatsCollector`, который вызывает 2 React Query хука. При 20 предметах — **40 параллельных запросов** на `/api/attendance/reports/journal` (каждый по всему семестру!) и `/api/academic/thresholds/resolve`. staleTime 5 мин / 24 часа снижает повторения, но первый заход тяжёлый. Бэкенд attendance-report без лимитов (см. 04-attendance-service отчёт).
- Дополнительно: JournalPage/StatsPage грузят **за весь семестр** один запрос на предмет без пагинации — при 100+ парах в семестре ответ жирный.
- **Как чинить:** завести агрегирующий endpoint `/api/attendance/reports/group-stats?groupId=X&dateFrom=...&dateTo=...` который сразу отдаёт все предметы группы с посчитанной статистикой (бэкенду сильно дешевле — одна SQL-агрегация вместо N). До внедрения — добавить `queryClient.setQueryDefaults(['journal'], { staleTime: 30*60_000 })` и ограничить первые 10 предметов с lazy-load остальных.
- **Зависимости:** attendance-service (новый report endpoint).

### P1-14: Deep-copy `window.confirm` для подтверждения удаления ДЗ — блокирующий UX, не работает в iOS standalone

- **Где:** `src/features/homework/LessonHomeworkSection.tsx:68-71`.
- **Что:** нативный `confirm()` в iOS standalone PWA показывается криво (невозможно стилизовать, модалка блокирует поток). Везде в проекте уже есть `DeleteConfirmDialog` — здесь его забыли.
- **Как чинить:** переиспользовать `DeleteConfirmDialog` из `features/headman/students/StudentsList.tsx:112-145` (перенести в `shared/components/ConfirmDialog`).

---

## Средние (P2)

### P2-1: `queryClient.refetchOnWindowFocus: false` — фоновая свежесть теряется

- **Где:** `src/shared/lib/queryClient.ts:3-12`.
- **Что:** для мобильного PWA с агрессивным backgrounding — отключённый `refetchOnWindowFocus` при staleTime 5 мин = устаревшие данные. При возврате из фона расписание не обновляется пока пользователь не потянет pull-to-refresh (а его нет!). Для headman-экранов (`ExcusesPage`) — тем более критично.
- **Как чинить:** `refetchOnWindowFocus: 'always'` либо `refetchOnReconnect: 'always'` (уже есть для schedule). Pull-to-refresh через `motion.div` с `onDragEnd` — в roadmap v9.1.

### P2-2: Нет pull-to-refresh на мобильном PWA

- **Где:** все страницы: `/home`, `/schedule`, `/homework`, `/notifications`, `/group/*`.
- **Что:** стандарт для мобильного UX. Без него юзер не понимает, как обновить список. Есть `refetchOnReconnect` — но он срабатывает только при реальном смене онлайна.
- **Как чинить:** в `AppShell` обернуть `<main>` в scroll-контейнер с `motion.div drag="y" dragConstraints={{top:0,bottom:100}} onDragEnd={...}` триггерящий `queryClient.invalidateQueries()`.

### P2-3: `usePrefetchSubjects` префетчит даже предметы, которые уже в кэше → бесполезные запросы

- **Где:** `src/features/schedule/api.ts:70-86`.
- **Что:** `queryClient.prefetchQuery` сам проверяет staleTime, но `uniqueIds` пересчитываются при каждом изменении `subjectIds` (разные ссылки массива) → effect перезапускается. Работает, но шумит логами.
- **Как чинить:** `useEffect(() => { ... }, [subjectIds.join(',')])` либо мемоизация на уровне SchedulePage.

### P2-4: `parseJwt` падает на кириллических claims через `atob`+`%XX` path

- **Где:** `AuthProvider.tsx:76-84`.
- **Что:** код рассчитан на UTF-8 payload — работает. Но если когда-то backend добавит `full_name: "Иван Иванов"` в JWT (не стоит, но «вдруг»), этот кусок `decodeURIComponent` на невалидном base64 может кинуть `URIError`. Try/catch вокруг — нет; есть только на уровне `tokenToUser`-вызова в AuthProvider useState initializer.
- **Как чинить:** использовать `TextDecoder('utf-8').decode(Uint8Array.from(atob(base64), c=>c.charCodeAt(0)))` — надёжнее.

### P2-5: `useTodayLesson` переопределяет схему LessonResponse (читает `startsAt/endsAt/subjectName`), но api возвращает `startTime/endTime`

- **Где:** `src/features/headman/shared/headmanApi.ts:122-144`.
- **Что:** хук маппит `first.subjectName`, `first.startsAt`, `first.endsAt`, `first.room` — но backend (см. `schedule-api-contract`) возвращает поля `startTime/endTime/subjectId/room` (как в `features/schedule/types.ts`). `subjectName` на response не ходит. Компонент `Overview` рендерит `todayLesson.subjectName` → **всегда пустая строка**, а `startsAt.slice(11,16)` → `.slice(...)` от `undefined` упадёт.
- **Как чинить:** перейти на настоящий формат: `subjectId` + хук `useSubjectName(subjectId)`; `startTime`/`endTime` как `"HH:mm:ss"`. **Проверить `OverviewPage` — возможно, уже сломан в проде.**
- **Зависимости:** тесты ручные.

### P2-6: `useGroupMembers`/`useGroupSubjects` хардкодят URL без поддержки пагинации для больших групп

- **Где:** `headmanApi.ts:20-45` — `size=50`/`size=100` без next-link следования.
- **Что:** группы обычно <50, но при университетских потоках 100+ студентов — отваливается. HATEOAS PagedModel это поддерживает (`_links.next`), PWA просто игнорирует.
- **Как чинить:** либо использовать `useInfiniteQuery`, либо на бэкенде добавить `GET /groups/{id}/members/all` без пагинации.

### P2-7: `SchedulePage` авто-скролл к активной паре работает один раз на весь жизненный цикл компонента

- **Где:** `src/features/schedule/SchedulePage.tsx:94, 143-156` — `hasAutoScrolled = useRef(false)` и в effect условие `if (hasAutoScrolled.current || !dayLessons.length) return`.
- **Что:** пользователь переключил день (`selectedDayIndex`) → dayLessons сменились → effect не сработал (hasAutoScrolled=true). Это правильно (не хотим скроллить при смене дня). Но при смене **недели** — тоже не срабатывает. При возврате на «сегодня» через pill — hasAutoScrolled всё ещё true, active лессон не в центре экрана.
- **Как чинить:** ресетить `hasAutoScrolled` при смене `weekStart` или при клике на pill «Сегодня». Либо ref → `useRef(new Set<string>())` по ключу `weekStart+dayIndex`.

### P2-8: Отсутствуют тесты для headman-экранов (excuses/late-checkin decision, subjects CRUD, stats red-zone), для SW push-handler и для `AuthProvider` refresh flow

- **Где:** `src/**/__tests__/*.test.{ts,tsx}` — всего **11 файлов** тестов на ~110 компонентов.
- **Что покрыто:** `AuthProvider` (базовый login/logout/restore), `useStompCheckin` (5 кейсов), `CheckInButton` (геоотметка), `BottomNav` тест, `SegmentedControl`, `SchedulePage` (минимально), `NotificationsPage` (минимально), `LoginPage`, `OfflineStaleNotice`, `sw-runtime-cache` (predicate), `PushPermissionCard`, `PWAHeadmanRole`.
- **Что НЕ покрыто:**
  - Axios refresh interceptor (queueing при параллельных 401), логика race condition в `flushQueue`.
  - Headman decision flows: approve/reject excuse, late-checkin decision.
  - StatsPage (вычисление `computeStudentStats`, sortSubjectsBySeverity) — чистые функции без тестов.
  - Service worker push handler (`self.addEventListener('push', ...)`).
  - Deep-link routing из `notificationclick`.
  - FormData excuse upload (`useCreateExcuse` с файлом).
  - `useInstallPrompt` beforeinstallprompt flow.
  - `IOSOnboardingOverlay` standalone detection.
- **Как чинить:** минимум 20 новых специфичных тестов. Приоритет — `computeStudentStats`, `sortSubjectsBySeverity` (чистые функции, 5 минут каждая), axios refresh queueing (критично для UX), FormData excuse.

### P2-9: `SchedulePage.handleDaySwipe` игнорирует момент `dragElastic`, может триггериться случайно при pointer-wiggle

- **Где:** `src/features/schedule/SchedulePage.tsx:169-175`.
- **Что:** порог `50px` — маленький, на touchpad от случайного пальца срабатывает. UX ломается — при попытке scroll пальцем лист выбирает другой день.
- **Как чинить:** повысить порог до 80, добавить `dragDirectionLock` (есть), проверять velocity: `if (info.velocity.x < -200 || ...)`.

### P2-10: `BottomNav` использует `layoutId` для активной пилюли — работает только с `motion.span`; при возврате на `/` через Navigate мгновенно прыгает

- **Где:** `src/shared/components/BottomNav.tsx:58-68`.
- **Что:** `Navigate to="/home"` (в main.tsx) плюс `end={to === '/home'}` — правильный матч. Но при первом рендере NavLink `isActive` мог бы не совпасть, и `layoutId` дать baseline со скачком. Не критично.

### P2-11: `HeadmanLessonSheet.handleBulkMark` делает последовательные `await` для массовой отметки

- **Где:** `src/features/schedule/HeadmanLessonSheet.tsx:100-122`.
- **Что:** при 30 студентах — 30 последовательных HTTP-запросов (каждый ~200ms) = **6 секунд**. Headman ждёт, тапает — не понятно что происходит.
- **Как чинить:** либо backend `POST /attendance/lessons/{id}/bulk-mark [{userId, status}]`, либо на клиенте `Promise.allSettled` с батчем 5 за раз. Также — показывать progress `2/30`.
- **Зависимости:** attendance-service bulk endpoint.

### P2-12: `NotificationCenter` хранит лимит 200, но после 200 теряет самые старые без предупреждения

- **Где:** `NotificationCenter.tsx:61, 316-319`.
- **Что:** silent truncation; нет indicator «старые уведомления удалены».
- **Как чинить:** либо IndexedDB архивирование (вне sessionStorage), либо минимум — toast «Старые уведомления удалены» при первом truncate. На практике не критично, 200 — достаточно для обычного пользователя.

### P2-13: `HomeworkPage` default selectedDate = tomorrow (`addDays(new Date(), 1)`), но для WeekView/MonthView открывается текущая неделя/месяц → inconsistency с выделением

- **Где:** `src/features/homework/HomeworkPage.tsx:39-49`.
- **Что:** студент открывает ДЗ → День показывает завтра (ок, это D-09), Неделя показывает эту неделю (не подсвечен завтрашний день как selected), Месяц показывает этот месяц. Переключение режимов теряет selectedDate. UX непоследовательный.
- **Как чинить:** единое состояние `selectedDate`, которое используется как центр во всех трёх режимах.

### P2-14: `SubjectsList` открывает `openEdit` при клике на весь `<div role="button">` — включая область кнопок действий, хоть там и `stopPropagation`

- **Где:** `src/features/headman/subjects/SubjectsList.tsx:119-165`.
- **Что:** `<div role="button" onClick={() => openEdit(subject)}>` — такой паттерн в shadcn не одобряется: нажатие Enter/Space не работает (нет keyboard handling), screen-reader'ы читают всё содержимое как кнопку. Лучше обернуть в `<button>` с `display:flex`.

### P2-15: `IOSOnboardingOverlay` не проверяет, что пользователь уже авторизован — оверлей на `/login`

- **Где:** `src/main.tsx:77-80` — `<IOSOnboardingOverlay />` рендерится на корне `<AuthProvider>`, до Router, значит и на LoginPage тоже.
- **Что:** человек открывает `/login` впервые на iOS Safari → видит полноэкранный оверлей «Нажми Поделиться → На экран Домой» **не войдя**. Флоу сломан: установят PWA → откроют standalone → надо снова входить.
- **Как чинить:** либо показывать после успешного логина, либо вообще убрать до ProfilePage («Добавить на главный экран»).

### P2-16: `DrawerMenu` содержит только 2 пункта (ДЗ, Профиль) — устаревший комментарий «чтобы бар не перегружать 5-6 вкладками»

- **Где:** `src/shared/components/DrawerMenu.tsx:13-16`.
- **Что:** сейчас BottomNav имеет 4 вкладки (+5 для старосты). Drawer с 2 пунктами = overengineering. Можно встроить ДЗ в BottomNav (5-я вкладка для всех) или перенести Профиль в BottomNav как иконку с аватаром. Drawer тогда убрать.
- **Как чинить:** решение за UX; если оставить drawer — добавить в него «Настройки уведомлений», «О приложении», «Выход».

### P2-17: Геолокация `{timeout:10000, maximumAge:30000}` без `enableHighAccuracy` — внутри здания GPS ловит точку на улице

- **Где:** `src/features/checkin/CheckInButton.tsx:55`.
- **Что:** по умолчанию `enableHighAccuracy: false` → используется network-positioning (WiFi/CellID) — точность 100-500 м. В учебном корпусе точка может быть «на парковке». Backend проверяет координаты с радиусом 100м (см. 04-attendance-service) → могут быть ложные 422.
- **Как чинить:** `{enableHighAccuracy: true, timeout: 15000, maximumAge: 10000}` — дольше, точнее. Добавить fallback на low-accuracy при timeout.

---

## Мелкие и nit (P3)

### P3-1: Дубликат `cn` в `src/lib/utils.ts` и `src/shared/lib/` (нет второго utils, но путь выглядит подозрительно)

Фактически есть только `src/lib/utils.ts`, но импорт идёт под двумя путями через алиасы. Nit: унифицировать на `@/lib/utils`.

### P3-2: `src/features/profile/ProfilePlaceholder.tsx` — мёртвый (не импортируется)

- **Где:** `src/features/profile/ProfilePlaceholder.tsx`.
- **Как чинить:** удалить.

### P3-3: `src/App.tsx` — мёртвый файл с комментарием «not used»

- **Где:** `src/App.tsx:1-5`.
- **Как чинить:** удалить + обновить `tsconfig.app.json` если он там явно.

### P3-4: `AppHeader` показывает кнопку «Установить» дважды: в header и на Profile

- **Где:** `src/shared/components/AppHeader.tsx:89-106`, `src/features/profile/ProfilePage.tsx:137-164`.
- **Как чинить:** выбрать одно место. Profile — логичнее (разовая акция), header тогда убрать.

### P3-5: `AppHeader` использует Phosphor CSS-иконку `<i className="ph ph-download-simple">` вместо React-компонента

- **Где:** `src/shared/components/AppHeader.tsx:102-103`.
- **Что:** в package.json только `@phosphor-icons/react`, CSS-пак `@phosphor-icons/web` не подключён — иконка не отрендерится.
- **Как чинить:** `<DownloadSimple size={16} weight="bold" />`.

### P3-6: `features/headman/shared/headmanApi.ts` смешивает 10+ разнородных хуков в одном файле

- **Где:** одноимённый файл на 435 строк.
- **Как чинить:** разнести по фичам (`journal/api.ts`, `stats/api.ts`, `members/api.ts`, `subjects/api.ts`, `excuses/api.ts`, `late-checkin/api.ts`). Уже есть `lessonActionsApi.ts`, `headmanSheetApi.ts` — последовательность нарушена.

### P3-7: `AddAssistantModal` позволяет сохранить ассистента с пустыми permissions — backend должен валидировать

- **Где:** `src/features/headman/students/AddAssistantModal.tsx:33-45`.
- **Что:** минимум — disable кнопки если `selectedPermissions.length === 0`. Сейчас можно создать бесправного «помощника».
- **Как чинить:** `disabled={!selectedStudentId || selectedPermissions.length === 0 || createAssistant.isPending}`.

### P3-8: `ExcusesPage` в state держит `toast: string | null` — при быстрых действиях следующий toast перезаписывает предыдущий до его исчезновения

- **Где:** `src/features/headman/excuses/ExcusesPage.tsx:34, 56-63`.
- **Как чинить:** единая Toaster-очередь на весь app. Уже есть `CheckInToast` — можно экстракт и переиспользовать.

### P3-9: `useInstallPrompt` глобальная `BeforeInstallPromptEvent` типизирована через `<any>`-фантом `pwa.d.ts`

- **Где:** `src/shared/types/pwa.d.ts` (не читал — но судя по импорту, там `any`).
- **Как чинить:** типизировать корректно через `PromptOutcome`.

### P3-10: `useNetworkStatus.getServerSnapshot` возвращает `true` — для SSR это правильно, но PWA не SSR

- **Где:** `src/shared/hooks/useNetworkStatus.ts:16-18`.
- **Что:** не критично, просто dead-code для PWA-only.

### P3-11: `describeNotification` использует ru-локализацию через hardcoded таблицы — не i18n

- **Где:** `NotificationCenter.tsx:107-249`.
- **Что:** если когда-либо потребуется английская локаль — рефакторинг большой.
- **Как чинить:** перенести в i18n JSON. На v0.0.0 — не блокирует.

---

## Мёртвый код

- `src/App.tsx` — комментарий «kept for backwards compatibility but is no longer used».
- `src/features/profile/ProfilePlaceholder.tsx` — не импортируется нигде.
- В `shared/components/Skeleton.tsx` экспортируется `SkeletonList`, но также используется `animate-pulse` inline во многих местах (SubjectSkeleton, MemberSkeleton) — дубликаты.
- `src/features/schedule/statusSymbols.ts` не читал — возможно, частично мёртвый (StatusBadge использует `personalStatus ?? lesson.status`, а не конкретные символы «б/н/у/сп»).

## Костыли и TODO/FIXME

- `src/main.tsx:33-35` — комментарий «BUG-008: PWA proxied под /app/» — это не FIXME, а объяснение, ок.
- `src/vite.config.ts:8-11` — аналогичный «BUG-008» — ок.
- `src/shared/hooks/useInstallPrompt.ts:1-9` — «BUG-008: useInstallPrompt» — ок.
- `src/features/homework/LessonHomeworkSection.tsx:68` — `if (!confirm(...))` — см. P1-14.
- `src/features/auth/AuthProvider.tsx:94-101` — комментарий про дубликат `groupId / group_id` — исторический долг, не блокирует, но указывает на нестабильность JWT claim naming.
- `src/features/headman/stats/StatsPage.tsx:100-104` — комментарий «fixes the Rules-of-Hooks violation from the previous loop-based impl» — костыль работает, но архитектура сомнительная (см. P1-13).
- `src/features/schedule/HeadmanLessonSheet.tsx:31-33` — `window.Telegram?.WebApp?.HapticFeedback` — код попал из mini-app, в PWA не нужен.

## Тесты

### Что покрыто хорошо

- `AuthProvider` — базовый login/logout/restore/malformed storage. Хорошие unit тесты.
- `useStompCheckin` — 5 кейсов: создание, subscribe, onMarked filter, type ignore, cleanup. Правильные моки.
- `sw-runtime-cache.isHeadmanApiRequest` — predicate покрыт (положительные и отрицательные URL).
- `CheckInButton`, `SegmentedControl`, `OfflineStaleNotice`, `BottomNav` — поверхностные, но есть.

### Что покрыто плохо / не покрыто

- Axios refresh interceptor (критичная логика 401 → refresh → retry queue) — **нет тестов**.
- Real FormData upload path в excuse — **нет**.
- Service worker push/notificationclick handlers — **нет**.
- Stats page red-zone логика, sortSubjectsBySeverity — **нет**.
- Headman decision flows (approve/reject excuse, late-checkin) — **нет**.
- Push-subscription lifecycle (iOS standalone guard, VAPID key, unsubscribe) — только минимальный smoke.
- Notification center (storage persist + limit, quiet hours, category filter) — **нет**.

### Некорректные/подозрительные тесты

- `AuthProvider.test.tsx` использует `groupId` в payload JWT, но реальный backend кладёт `group_id` (snake_case). Тест зелёный, потому что `tokenToUser` fallback читает оба. Риск: тест не валидирует «правильный» формат, а «в шутку». Желательно минимум один тест с `group_id:5` и один с `groupId:5`.
- `useStompCheckin.test` не проверяет **повторный subscribe на reconnect** (см. P1-6) — значит, P1-6 и не знали.

### Кандидаты на удаление/рефакторинг

- `PWAHeadmanRole.test.tsx` — не читал, но по имени — потенциально дублирует тест BottomNav.

---

## Соответствие CLAUDE.md

Раздел CLAUDE.md про фронты — короткий (просто «frontends/pwa — React PWA»), нет строгих правил как для бекенда. Принципы, которые применимы:

- **Именование пакетов:** `ru.rutcampustrack.{service}` — не применимо к фронту. ✅ игнорируем.
- **REST пути `/api/...`:** ✅ всё через `apiClient.baseURL = '/api'`.
- **Contract-first:** на фронте нет контрактного джара — типы DTO дублируются вручную (`types.ts` в каждом feature). ⚠ Риск рассинхрона при эволюции API; уже видно в `useTodayLesson` (P2-5).
- **Lowercase enum в БД, UPPER_CASE в Java:** на фронте лессон-статусы приходят в `UPPER_CASE` (`'PLANNED'|'ACTIVE'|'CLOSED'|'CANCELLED'`), attendance-статусы в lowercase (`'present'|'absent'|...`). ⚠ **Несогласованность**. Фронт это принимает, но напрягает.
- **Soft delete:** не касается.
- **TIMESTAMPTZ UTC:** LocalDate в формате `YYYY-MM-DD` (schedule), ISO 8601 с TZ для `receivedAt` notification-центра. ✅.
- **Design decisions (docs/design-decisions.md):** PWA использует Phosphor иконки, Motion, shadcn, токены CSS. ✅.

Единственное жёсткое замечание — дублирование типов. При v0.0.0 можно ввести OpenAPI-generator (уже обсуждалось в 01-auth-service P0-1).

---

## Зависимости между проблемами

- **P0-1 (XSS + localStorage) + P0-2 (JWT в URL)** — одна и та же угроза «кража токена», правильное решение — HttpOnly cookie для refresh + short-lived WS ticket. Один архитектурный сдвиг закрывает оба.
- **P0-4 (SW cache не чистится) + P0-5 (push не отвязан)** — оба про lifecycle `logout`. Фикс в `AuthProvider.logout`.
- **P1-4 (STOMP reconnect при token refresh) + P1-5 (visibility)** — один пакет «STOMP lifecycle».
- **P2-1 (refetchOnFocus) + P2-2 (pull-to-refresh)** — UX-пара. Решается одним спринтом.
- **P0-3 (нет role guards) + 07 P1 (Gateway не различает admin/teacher/student)** — на фронте можно закрыть даже если бек не менять.
- **P1-13 (StatsPage N+N запросов) + 04 attendance-service P2** — новый endpoint `/reports/group-stats` решает обе.

---

## Вопросы к владельцу проекта

1. ✅ **Ротируемся ли на HttpOnly-cookie refresh-токен?** (P0-1, P0-2). Это ~1-2 недели работы, затрагивает auth-service, api-gateway, PWA, web-panel, mini-app.
   → **AUTO-RESOLVED через Q-frontend-security (2026-04-18)**: ДА, делаем JWT HttpOnly cookie + WS-ticket в v0.0.0 + breaking change без двойных endpoint'ов. Estimate 8-12 дней. См. `OWNER-ANSWERS.md` 02-Q-frontend-security.
2. **PWA должна обслуживать TEACHER и ADMIN?** Сейчас роуты есть только студент/staroста. Если «никогда» — надо закрыть редиректом на login с сообщением «Для сотрудников — /login веб-панели». Если «когда-нибудь» — закладывать архитектуру ролей.
3. ✅ **Кэш headman-API в SW нужен?** (P0-4). 24 часа + общее устройство в старостате = серьёзный leak. Готов ли отказаться от SW-кэша headman-эндпоинтов в пользу TanStack Query (in-memory, чистится на logout)?
   → **AUTO-RESOLVED через Q-frontend-security (2026-04-18)**: SW cache очищается при logout через общий `clearAllClientState()` (`caches.keys() → caches.delete`). Сам кеш как фича остаётся, но логаут гарантирует cleanup. См. `OWNER-ANSWERS.md` 02-Q-frontend-security.
4. **`HeadmanLessonSheet` и bulk-mark** (P2-11) — готовы добавить backend `bulk-mark` endpoint в attendance-service, или оставить последовательные запросы с progress-индикатором?
5. **StatsPage N предметов → N×2 запросов** (P1-13) — готовы добавить агрегат `/reports/group-stats`? Иначе headman с 20 предметами будет ждать 40 запросов на каждый открытый `/group/stats`.
6. ✅ **Push-уведомления с именами студентов** — является ли ПДн «Иван Иванов подал пропуск» на экране lock-screen? Если да — надо заменить «Иван Иванов» на «Студент группы X» в `buildBody` для excuse/late-checkin (`NotificationCenter.tsx:212-228`).
   → **ACCEPTED BY OWNER (2026-04-18)**: оставляем имена в push, не ПДн по M1, UX-риск принят. См. `OWNER-ANSWERS.md` 09-Q6.
7. **Pull-to-refresh** (P2-2) — добавить в v0.0.0 или отложить в v0.0.1? Мобильные пользователи будут спрашивать часто.
8. **iOS standalone onboarding** (P2-15) показывается до логина — это задумано, чтобы пользователь сразу поставил PWA, или баг?
9. **Удаление `window.confirm` для ДЗ** (P1-14) — единый `<ConfirmDialog>` компонент завести, или оставить per-feature?
10. **Что делать с `DrawerMenu`**, если там только 2 пункта? (P2-16). Упразднить и вернуть Профиль в BottomNav?
