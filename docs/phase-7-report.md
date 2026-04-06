# RutCampusTrack — Отчёт Фазы 7: PWA Mobile Client

## Дата: Апрель 2026

## Цель фазы

PWA Mobile Client «RutTrack»: полнофункциональное мобильное веб-приложение для студентов. API Gateway CORS + nginx для раздачи статики, React PWA с cookie-based авторизацией, расписание с swipe-навигацией, гео-отметка с GPS + STOMP real-time обновлениями, Web Push уведомления с foreground suppression, статистика посещаемости с red zone индикаторами и ДЗ трекер с optimistic updates.

---

## Что реализовано

### Подфаза 28: API Gateway CORS + nginx

**Цель:** API Gateway принимает cross-origin запросы от PWA origin, nginx контейнер раздаёт статический билд PWA.

**28-01: Gateway CORS + OPTIONS Bypass**

- **OPTIONS bypass**: `HttpMethod.OPTIONS` проверка в `JwtAuthenticationFilter` ДО проверки JWT — все preflight-запросы проходят без авторизации
- **Global CORS**: `globalcors` YAML конфигурация с explicit origins (`localhost:5173` для Vite dev, `localhost:80` для nginx), `allow-credentials: true`, `add-to-simple-url-handler-mapping: true`
- **DedupeResponseHeader**: default filter `RETAIN_UNIQUE` предотвращает дублирование CORS-заголовков от backend-сервисов
- **2 новых теста**: OPTIONS bypass для academic и push маршрутов (всего 11 gateway тестов)

**28-02: PWA nginx Container**

- **nginx.conf**: PWA-оптимизированные cache rules — `no-cache` для `sw.js` и `index.html`, 1-year `immutable` cache для hashed assets
- **SPA fallback**: `try_files $uri $uri/ /index.html`
- **Docker-compose**: `pwa-nginx` сервис (`nginx:1.27-alpine`, порт 80, read-only bind mounts)
- **Placeholder dist/**: `index.html` + `sw.js` для верификации контейнера

### Подфаза 29: PWA Scaffold + Auth

**Цель:** Студенты могут установить RutTrack на домашний экран, войти с username/password и увидеть app shell, работающий офлайн.

**Backend (Auth Service):**

- **Cookie-based auth**: `POST /auth/login` возвращает `accessToken` в JSON body И устанавливает `httpOnly` cookie `refresh_token` (`Secure`, `SameSite=Strict`, `Path=/api/auth`)
- **Token refresh**: `POST /auth/refresh` читает refresh token из cookie (`@CookieValue`), возвращает новый `accessToken` + ротация cookie
- **Logout**: `POST /auth/logout` очищает cookie (`maxAge=0`) и инвалидирует refresh token в Redis
- **AccessTokenResponse**: record с `accessToken` + `expiresIn` (без refreshToken)
- **11 интеграционных тестов** для cookie-based auth flow

**Frontend (React PWA):**

- **Vite + vite-plugin-pwa**: `injectManifest` стратегия, manifest с `name: RutCampusTrack`, `short_name: RutTrack`, `display: standalone`
- **Service Worker**: `precacheAndRoute(self.__WB_MANIFEST)`, 9 precache entries в билде
- **AuthProvider**: in-memory JWT (`tokenRef`), `setAccessTokenGetter` для Axios interceptor
- **Axios interceptor**: `withCredentials: true`, silent 401 refresh через `isRefreshing` + `pendingQueue`
- **LoginPage**: форма «Войти в систему», offline состояние «Нет подключения»
- **ProtectedRoute**: `useAuth().isAuthenticated` → `Navigate to="/login"`
- **AppShell + BottomNav**: 4-tab layout с `Outlet`
- **IOSOnboardingOverlay**: 3-шаговая инструкция установки для iOS Safari, `localStorage` flag для dismiss
- **useInstallPrompt**: захват `beforeinstallprompt` для Android A2HS
- **useNetworkStatus**: `useSyncExternalStore` с online/offline events
- **OfflineBanner**: AnimatePresence + motion, «Нет подключения к интернету»
- **shadcn/ui**: components.json + Button компонент
- **9 vitest тестов** (3 AuthProvider + 6 LoginPage)

### Подфаза 30: Schedule + Check-in UI

**Цель:** Студенты видят дневное/недельное расписание и могут геоотметиться с активной пары.

**30-01: Schedule View**

- **SchedulePage**: недельная навигация (CaretLeft/CaretRight), форматированный диапазон недели, auto-scroll к текущей/следующей паре (D-03), floating pill «Сегодня» для не-текущей недели (D-04), stagger animation (0.04s), empty state «Занятий нет»
- **WeekDayTabs**: Пн-Сб (без воскресенья, D-01), swipe-to-change-week (80px threshold), ARIA tablist/tab, 44px touch targets, sticky positioning
- **LessonCard**: время (HH:mm), предмет (через `useSubjectName`), аудитория, StatusBadge, check-in slot для ACTIVE, opacity-60 для cancelled, motion.div mount animation
- **StatusBadge**: 8 вариантов (present/absent/excused/free_attendance/ACTIVE/PLANNED/CANCELLED/CLOSED) с цветами и русскими метками
- **OfflineStaleNotice**: «Офлайн · обновлено N мин назад» с 60-секундным интервалом, «Нет данных» при отсутствии кэша
- **API hooks**: `useWeekSchedule` (staleTime 1hr, refetchOnReconnect), `useSubjectName` (staleTime 24hr), `usePrefetchSubjects`
- **11 тестов** (SchedulePage, OfflineStaleNotice, CheckInButton stub, useStompCheckin stub)

**30-02: Check-in Flow**

- **CheckInButton**: GPS capture (`getCurrentPosition` timeout:10000, maximumAge:30000), `useCheckin` mutation, spinner с aria-busy, disabled офлайн
- **GPS error handling**: «Нет доступа к GPS. Разрешите доступ в настройках браузера» (точный текст D-09)
- **API error mapping**: 404 (нет активной пары), 409 (уже отмечен), 422 (не в зоне), 403, 429
- **CheckInToast**: motion.div slide-up, success (3000ms auto-dismiss, `role=status`), error (5000ms, `role=alert`)
- **useStompCheckin**: `@stomp/stompjs` Client с SockJS, `getAccessToken` factory (Pitfall 7 — защита от stale token на reconnect), подписка на `/topic/group/{groupId}`, фильтр `attendance.marked`
- **StompProvider**: shared React context на уровне AppShell (D-11 — единое STOMP соединение), `attendanceCounts` + `personalStatuses` per lesson
- **LessonCard integration**: CheckInButton для ACTIVE без personalStatus, Check icon + StatusBadge после отметки (D-08), motion.span count animation (stiffness:300, damping:30)
- **CheckInScreen**: dedicated `/checkin` tab, active lesson с check-in или empty state «Сейчас нет активных пар»
- **13 новых тестов** (10 CheckInButton + GPS/toast + 5 useStompCheckin; итого 31)

### Подфаза 31: Push Frontend + End-to-End Integration

**Цель:** Студенты получают Web Push уведомления и при нажатии попадают на нужный экран.

**31-01: SW Push Handlers + Push Utilities**

- **push event handler**: defensive JSON parsing (T-31-01), foreground suppression через `clients.matchAll` (PUSHUI-04), `event.waitUntil()` (T-31-02)
- **notificationclick handler**: закрывает уведомление, фокусирует существующий tab или открывает новое окно на правильном маршруте
- **Notification dedup**: `tag` field (`event_type-lesson_id`)
- **pushUtils.ts**: `getUrlForEventType` (lesson.started → /checkin, lesson.cancelled → /schedule), `urlBase64ToUint8Array` (VAPID key conversion)
- **7 тестов** для push utilities

**31-02: Push Subscription UI**

- **Push API client**: `fetchVapidPublicKey` (GET), `subscribePush` (POST), `unsubscribePush` (DELETE) — маппинг на backend контракт
- **usePushSubscription**: iOS standalone guard, `Notification.requestPermission` только по explicit gesture (PUSHUI-03), VAPID key fetch, `pushManager.subscribe`, backend registration
- **PushPermissionCard**: state-based rendering — enable button (default), disable (granted), unsupported, denied warning, iOS standalone warning. Phosphor icons (Bell, BellSlash, Warning)
- **ProfilePage**: заменяет `ProfilePlaceholder`, содержит PushPermissionCard + logout confirmation flow
- **8 тестов** PushPermissionCard (все UI states); итого 46 PWA тестов

### Подфаза 32: Stats + Homework

**Цель:** Статистика посещаемости по предметам с red zone индикаторами и ДЗ трекер с optimistic toggle.

**32-01: Attendance Stats Pages**

- **AttendanceStatsPage**: список предметов с `useStudentStats`, pull-to-refresh через `usePullToRefresh`, Motion stagger, navigate к `/stats/:subjectId` с `subjectName` как route state
- **SubjectStatRow**: процент (right-aligned с CaretRight), разбивка `б/н/у`, red zone: `border-l-4 border-destructive` + красный процент + `RedZoneBadge`
- **RedZoneBadge**: pill badge «Красная зона» с `WarningCircle`
- **AttendanceRecordsPage**: список записей предмета, сортировка по `lessonDate` desc, русская дата (`dd MMM`), номер пары (`N-я пара`), `StatusBadge`, кнопка «Назад к статистике»
- **API hooks**: `useStudentStats` (staleTime 60min), `useThreshold` (null на 404, D-06), `useAttendanceRecords`
- **usePullToRefresh**: reusable hook (touchstart/touchmove/touchend), `containerRef`, `isRefreshing`, `pullDistance`
- **BottomNav**: 5 tabs (Статистика, Расписание, Отметка, Задания, Профиль) — замена 4-tab layout, удалён `/home`
- **Routing**: index redirect → `/schedule`, `/stats`, `/stats/:subjectId`
- **8 тестов** (4 API hooks + 4 AttendanceStatsPage); итого 54

**32-02: Homework List Page**

- **HomeworkPage**: цепочка `useActiveSemester` → `useHomework`, pull-to-refresh, `errorMap: Record<number, string>` для per-item ошибок, сортировка (undone first), Motion stagger + `layout` animation
- **HomeworkItem**: accessible checkbox (`role="checkbox"`, `aria-checked`), Phosphor `Check` icon, `line-through opacity-60` when done, `useSubjectName`, inline error `text-destructive text-xs`, Motion spring animation (scale: 0.85 → 1)
- **useToggleHomework**: TanStack Query optimistic mutation — `onMutate` (flip cache), `onError` (revert + errorMap), `onSettled` (invalidate). POST `/complete` для done, DELETE `/complete` для undone
- **useActiveSemester**: fetches `/academic/semesters`, finds `active=true`, staleTime 24hr
- **useHomework**: `enabled: !!groupId && !!semesterId` (chain dependency), staleTime 60min
- **9 тестов** (4 API + 5 HomeworkItem); итого 63

---

## Стек PWA

| Библиотека | Назначение |
|------------|-----------|
| React 19 + Vite | SPA framework + bundler |
| vite-plugin-pwa (injectManifest) | Service Worker + manifest |
| TanStack React Query | Data fetching, caching, optimistic updates |
| React Router | SPA routing с lazy imports |
| Axios | HTTP client с interceptor для silent refresh |
| @stomp/stompjs + sockjs-client | STOMP WebSocket для real-time updates |
| motion (Framer Motion) | Animations, gestures, layout transitions |
| Tailwind CSS | Utility-first styling |
| shadcn/ui | UI component library |
| @phosphor-icons/react | Iconography |
| Vitest + @testing-library/react | Unit/integration testing |

---

## API Endpoints (потребление PWA)

| Метод | URL | Описание | Компонент |
|-------|-----|----------|-----------|
| POST | /api/auth/login | Авторизация (cookie + JWT) | LoginPage |
| POST | /api/auth/refresh | Silent token refresh | Axios interceptor |
| POST | /api/auth/logout | Выход | ProfilePage |
| GET | /api/schedule/groups/{id}/lessons | Расписание недели | SchedulePage |
| GET | /api/academic/subjects/{id} | Имя предмета | useSubjectName |
| POST | /api/attendance/checkin | Гео-отметка | CheckInButton |
| GET | /api/push/vapid-public-key | VAPID public key | usePushSubscription |
| POST | /api/push/subscribe | Push подписка | usePushSubscription |
| DELETE | /api/push/subscribe | Push отписка | usePushSubscription |
| GET | /api/attendance/reports/student/stats | Статистика по предметам | AttendanceStatsPage |
| GET | /api/attendance/reports/student/records | Записи предмета | AttendanceRecordsPage |
| GET | /api/academic/thresholds/resolve | Порог red zone | useThreshold |
| GET | /api/academic/semesters | Активный семестр | useActiveSemester |
| GET | /api/academic/homeworks | Список ДЗ | useHomework |
| POST/DELETE | /api/academic/homeworks/{id}/complete | Toggle ДЗ | useToggleHomework |
| WS | /api/ws (STOMP over SockJS) | Real-time attendance | StompProvider |

---

## Ключевые технические решения

| Решение | Обоснование |
|---------|------------|
| Cookie-based refresh (httpOnly) | Access token в памяти React (не localStorage), refresh в httpOnly cookie — защита от XSS |
| injectManifest (не generateSW) | Полный контроль над SW: push handlers, foreground suppression, custom precaching |
| Single shared StompProvider | Одно STOMP-соединение на AppShell — не создаёт новое при навигации между tabs (D-11) |
| getAccessToken factory для STOMP | Защита от stale token при reconnect — factory вызывается в момент подключения (Pitfall 7) |
| TanStack Query staleTime 1hr | Расписание кэшируется 1 час, работает офлайн при потере сети |
| Optimistic mutations для ДЗ | `onMutate` flip → `onError` revert → per-item errorMap — мгновенный отклик UI |
| useThreshold returns null on 404 | 404 = порог не настроен → red zone индикаторы не показываются (D-06) |
| Foreground suppression в SW | `clients.matchAll` проверяет focused WindowClient — push не показывается если PWA открыта |
| Push permission на explicit gesture | `requestPermission` только по нажатию кнопки — не на load/navigation (PUSHUI-03) |
| 5-tab BottomNav | Статистика/Расписание/Отметка/Задания/Профиль, default landing → /schedule |
| Pull-to-refresh shared hook | Reusable `usePullToRefresh` с touchstart/touchmove/touchend — используется на stats и homework |

---

## Файловая структура (PWA)

```
frontends/pwa/
├── package.json                         ← React + Vite + PWA deps
├── vite.config.ts                       ← injectManifest, manifest config
├── vitest.config.ts                     ← jsdom environment
├── nginx.conf                           ← PWA cache rules + SPA fallback
├── public/icons/                        ← icon-192.png, icon-512.png
├── dist/                                ← Build output (sw.js, index.html)
└── src/
    ├── sw.ts                            ← Service Worker: precache + push + notificationclick
    ├── main.tsx                         ← Router, providers, lazy imports
    ├── shared/
    │   ├── lib/
    │   │   ├── axios.ts                 ← Axios + 401 interceptor
    │   │   └── queryClient.ts           ← TanStack Query client
    │   ├── hooks/
    │   │   ├── useNetworkStatus.ts       ← online/offline
    │   │   ├── useInstallPrompt.ts       ← A2HS capture
    │   │   └── usePullToRefresh.ts       ← Touch pull-to-refresh
    │   ├── components/
    │   │   ├── AppShell.tsx              ← Layout + Outlet
    │   │   ├── BottomNav.tsx             ← 5 tabs
    │   │   ├── ProtectedRoute.tsx        ← Auth guard
    │   │   ├── OfflineBanner.tsx         ← «Нет подключения»
    │   │   └── LoadingSpinner.tsx
    │   └── types/pwa.d.ts               ← BeforeInstallPromptEvent
    ├── features/
    │   ├── auth/
    │   │   ├── AuthProvider.tsx          ← In-memory JWT + context
    │   │   ├── LoginPage.tsx             ← «Войти в систему»
    │   │   ├── IOSOnboardingOverlay.tsx  ← iOS install instructions
    │   │   └── api.ts                    ← Auth API calls
    │   ├── schedule/
    │   │   ├── SchedulePage.tsx          ← Week nav, day tabs, lesson list
    │   │   ├── WeekDayTabs.tsx           ← Mon-Sat tabs + swipe
    │   │   ├── LessonCard.tsx            ← Lesson + check-in slot
    │   │   ├── StatusBadge.tsx           ← 8 status variants
    │   │   ├── OfflineStaleNotice.tsx    ← Stale data notice
    │   │   ├── types.ts                  ← LessonResponse, SubjectResponse
    │   │   └── api.ts                    ← useWeekSchedule, useSubjectName
    │   ├── checkin/
    │   │   ├── CheckInButton.tsx         ← GPS capture + submit
    │   │   ├── CheckInToast.tsx          ← Success/error feedback
    │   │   ├── CheckInScreen.tsx         ← /checkin tab
    │   │   ├── StompProvider.tsx         ← Shared STOMP context
    │   │   ├── useStompCheckin.ts        ← STOMP hook
    │   │   ├── types.ts                  ← CheckinRequest
    │   │   └── api.ts                    ← useCheckin mutation
    │   ├── push/
    │   │   ├── pushUtils.ts             ← getUrlForEventType, urlBase64ToUint8Array
    │   │   ├── api.ts                    ← fetchVapidPublicKey, subscribePush
    │   │   ├── usePushSubscription.ts   ← Push lifecycle hook
    │   │   └── PushPermissionCard.tsx   ← Soft-ask UI
    │   ├── attendance/
    │   │   ├── AttendanceStatsPage.tsx   ← Subject stats list
    │   │   ├── SubjectStatRow.tsx        ← Subject card + red zone
    │   │   ├── RedZoneBadge.tsx          ← «Красная зона» pill
    │   │   ├── AttendanceRecordsPage.tsx ← Per-subject records
    │   │   ├── AttendanceRecordRow.tsx   ← Date + StatusBadge
    │   │   ├── types.ts                  ← Stats/records types
    │   │   └── api.ts                    ← useStudentStats, useThreshold
    │   ├── homework/
    │   │   ├── HomeworkPage.tsx          ← Homework list + pull-to-refresh
    │   │   ├── HomeworkItem.tsx          ← Checkbox + optimistic toggle
    │   │   ├── types.ts                  ← HomeworkResponse
    │   │   └── api.ts                    ← useHomework, useToggleHomework
    │   └── profile/
    │       └── ProfilePage.tsx           ← Push settings + logout
    └── test/setup.ts                     ← Mocks (geolocation, scrollIntoView, Notification)
```

---

## Тестовое покрытие

| Модуль | Тесты | Тип | Фреймворк |
|--------|-------|-----|-----------|
| Auth Service (cookie auth) | 11 | Integration | Spring Boot Test |
| Gateway (OPTIONS bypass) | 2 | Unit | JUnit + Mockito |
| AuthProvider | 3 | Unit | Vitest + RTL |
| LoginPage | 6 | Unit | Vitest + RTL |
| SchedulePage | 4 | Unit | Vitest + RTL |
| OfflineStaleNotice | 2 | Unit | Vitest + RTL |
| CheckInButton + Toast | 10 | Unit | Vitest + RTL |
| useStompCheckin | 5 | Unit | Vitest |
| Push Utilities | 7 | Unit | Vitest |
| PushPermissionCard | 8 | Unit | Vitest + RTL |
| Attendance API hooks | 4 | Unit | Vitest + RTL |
| AttendanceStatsPage | 4 | Unit | Vitest + RTL |
| Homework API hooks | 4 | Unit | Vitest + RTL |
| HomeworkItem | 5 | Unit | Vitest + RTL |
| **Итого PWA** | **63** | | Vitest |
| **Итого Backend** | **13** | | JUnit |
| **Итого** | **~76** | | |

Все PWA тесты: `npx vitest run` — 63 tests, 12 test files, 0 failures
Auth Service тесты: `./gradlew :services:auth-service:test` — BUILD SUCCESSFUL
Gateway тесты: `./gradlew :services:api-gateway:test` — BUILD SUCCESSFUL (11 total)

---

## Требования (покрытие)

| Категория | ID | Статус |
|-----------|-----|--------|
| Infrastructure | INFRA-01 (Gateway CORS) | ✅ |
| Infrastructure | INFRA-03 (nginx PWA serving) | ✅ |
| Auth | PWA-01 (login cookie-based) | ✅ |
| Auth | PWA-02 (silent token refresh) | ✅ |
| Auth | PWA-03 (logout) | ✅ |
| Auth | PWA-04 (manifest + branding) | ✅ |
| Auth | PWA-05 (SW + offline shell) | ✅ |
| Auth | PWA-06 (A2HS prompt capture) | ✅ |
| Auth | PWA-07 (iOS install overlay) | ✅ |
| Schedule | SCHED-01 (daily schedule) | ✅ |
| Schedule | SCHED-02 (weekly nav + swipe) | ✅ |
| Schedule | SCHED-03 (offline cache 1hr) | ✅ |
| Check-in | CHKIN-01 (GPS check-in) | ✅ |
| Check-in | CHKIN-02 (success/error feedback) | ✅ |
| Check-in | CHKIN-03 (STOMP real-time) | ✅ |
| Push UI | PUSHUI-01 (SW push handler) | ✅ |
| Push UI | PUSHUI-02 (notificationclick deep-link) | ✅ |
| Push UI | PUSHUI-03 (explicit gesture only) | ✅ |
| Push UI | PUSHUI-04 (foreground suppression) | ✅ |
| Attendance | ATT-01 (subject stats) | ✅ |
| Attendance | ATT-02 (red zone threshold) | ✅ |
| Attendance | ATT-03 (per-subject records) | ✅ |
| Homework | HW-01 (homework list) | ✅ |
| Homework | HW-02 (optimistic toggle) | ✅ |
| **Итого** | **24/24** | **100%** |

---

## Известный tech debt

| Проблема | Серьёзность | Описание |
|----------|-------------|----------|
| Attendance count starts at 0 | Low | Нет backend endpoint для начального count — счётчик начинается с 0 при каждой сессии, инкрементируется только через STOMP |
| HomePlaceholder / ProfilePlaceholder | Info | Dead code в main.tsx — не удалён per plan instruction |
| Pull-to-refresh touch behavior | Info | Покрыт unit-тестами структурно, но touchstart/touchmove/touchend требуют ручного тестирования на реальном устройстве |
| Optimistic revert end-to-end | Info | TanStack Query onError → cache revert цикл покрыт mock-тестами, полный e2e требует network failure simulation |
| A2HS trigger deferred | Info | `useInstallPrompt` захватывает `beforeinstallprompt`, но trigger после первого check-in отложен (future work) |

---

## Следующая фаза

Milestone v6.0 (PWA + Web Push) завершён. Все 6 фаз (27-32) реализованы и верифицированы.
