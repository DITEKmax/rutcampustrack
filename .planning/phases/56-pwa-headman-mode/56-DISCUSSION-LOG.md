# Phase 56: PWA Headman Mode - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-13
**Phase:** 56-pwa-headman-mode
**Areas discussed:** Navigation structure, Journal mobile UX, Service Worker caching, Code reuse boundaries, BottomNav 5th tab

---

## 1. Navigation Structure

### Q: Как организовать навигацию внутри таба «Группа»?

| Option | Description | Selected |
|--------|-------------|----------|
| Экран-хаб со ссылками | 7 карточек на hub-странице, каждая ведёт на отдельный роут /group/xxx, minimal изменений. | ✓ |
| Вложенные роуты + внутренние табы | Верхний scroll-tab-bar (iOS Segmented Control style) по 7 разделам | |
| Stacked navigation по образцу iOS | Push/pop анимации, hub → detail → sub-screen, требует кастомного back-stack | |

**User's choice:** Экран-хаб со ссылками

### Q: Какие разделы включить в hub первой итерации?

| Option | Description | Selected |
|--------|-------------|----------|
| Обзор (HEAD-WEB-02) | Dashboard-карточка со счётчиками | ✓ |
| Студенты группы (HEAD-WEB-03) | Список + управление помощниками | ✓ |
| Предметы (HEAD-WEB-04) | CRUD предметов + назначение преподавателей | ✓ |
| Журнал (HEAD-WEB-05) | Mass-mark attendance (центральная функция) | ✓ |

### Q: Что делаем с оставшимися 3 разделами (Excuses/Late-checkin/Stats)?

| Option | Description | Selected |
|--------|-------------|----------|
| Все 7 в hub изначально | Graceful degradation для Excuses/Late-checkin, полный Stats | ✓ |
| Только 4 | Сузить scope, требует изменения ROADMAP | |

**User's choice:** Все 7 разделов в hub

---

## 2. Journal Mobile UX

### Q: Как отобразить students × lessons матрицу на 360px?

| Option | Description | Selected |
|--------|-------------|----------|
| Выбор даты → список студентов | 2-шаговый: выбор предмет+дата → плоский вертикальный список студентов с 5 segment-buttons | ✓ |
| Горизонтальная matrix с sticky колонкой | Angular-подобный grid с горизонтальным скроллом | |
| Один студент → swipe между уроками | Day-picker + карточка студента, swipe nav | |

**User's choice:** Выбор даты → список студентов

### Q: Как сменять статус ячейки?

| Option | Description | Selected |
|--------|-------------|----------|
| 5 сегмент-кнопок всегда видны | SegmentedControl [б][н][у][сп][—], 1 тап = статус | ✓ |
| Тап циклит статусы | Как в Angular: absent→present→excused→free→cancelled | |
| Long-press → action sheet | Toggle absent↔present + long-press для полного меню | |

**User's choice:** 5 сегмент-кнопок всегда видны

---

## 3. Service Worker Caching

### Q: Какую стратегию Workbox для headman API endpoints?

| Option | Description | Selected |
|--------|-------------|----------|
| Stale-while-revalidate (SWR) | Из кеша мгновенно, фоновое обновление | ✓ |
| Network-first с cache fallback | Всегда свежие данные онлайн, медленнее | |
| Разные стратегии по эндпоинтам | SWR для стабильного, network-first для журнала | |

**User's choice:** Stale-while-revalidate (SWR)

### Q: Какие детали кеша?

Initial question rejected — user asked for clarification on what SW cache is.
After explanation (SW intercepts HTTP requests → returns from Cache Storage API; Workbox provides
strategies; ROADMAP requirement 4 specifies stale-while-revalidate for headman endpoints so
offline devices still load cached data):

**User's confirmation:** "Супер тогда вот как ты расписал то и оставляем со всеми рекомендациями"

Decisions locked to recommended defaults:
- TTL 24 часа (maxAgeSeconds 86400)
- maxEntries 100 per cache bucket
- Только GET запросы
- CacheableResponsePlugin statuses [200]

**Notes:** User initially unfamiliar with SW cache concept; clarified through explanation that
cache benefits staronta specifically — list of students viewable in metro without internet,
attendance marks go through network (not cached), fresh data syncs when online.

---

## 4. Code Organization Boundaries

### Q: Куда помещать код headman фичей в PWA?

| Option | Description | Selected |
|--------|-------------|----------|
| Новый features/headman/ с нуля | Изолированный каталог, нулевой риск поломки student тестов | ✓ |
| Изолированные features, общие хуки в shared/ | Большее переиспользование через shared primitives | |

**User's choice:** Новый features/headman/ с нуля

### Q: Что считать «нельзя трогать»?

| Option | Description | Selected |
|--------|-------------|----------|
| Только features/{home,schedule,checkin,profile,push,auth} | 6 папок заморожены; shared/lib/main/BottomNav/AuthProvider расширяемы аккуратно | ✓ |
| Всё существующее заморожено | Потребует дублирования BottomNav/Router/AuthProvider | |

**User's choice:** Только features/{home,schedule,checkin,profile,push,auth}

---

## 5. BottomNav 5th Tab

### Q: Где разместить пятый таб?

| Option | Description | Selected |
|--------|-------------|----------|
| После Профиля (крайний справа) | ROADMAP wording: "after the four existing student tabs" | |
| Перед Профилем | Главная → Расписание → Отметка → Группа → Профиль. Функциональные табы вместе | ✓ |

**User's choice:** Перед Профилем (NB: deliberate deviation from ROADMAP wording)

### Q: Название и иконка таба?

| Option | Description | Selected |
|--------|-------------|----------|
| «Группа» + Users icon | Phosphor Users (3 человечка) | ✓ |
| «Староста» + Crown/Star | Акцент на роль | |
| «Группа» + UsersThree | Компактнее | |

**User's choice:** «Группа» + Users icon

### Q: Как фильтровать табы по роли?

| Option | Description | Selected |
|--------|-------------|----------|
| tabs[] → хук useTabs() с фильтром по isHeadman | Чистый React-way, тестируемо | ✓ |
| Инлайн {isHeadman && <Tab />} | Проще, но ломает map() паттерн | |

**User's choice:** useTabs() hook

---

## Claude's Discretion

Deferred to Claude during implementation:
- Visual styling of SegmentedControl (colors, height, padding)
- Motion AnimatePresence for hub card entrance
- Semester window default for stats load
- Exact Phosphor icon choice per hub card
- Preload vs lazy data loading strategy

## Deferred Ideas

- Charts in stats (future phase, needs library choice)
- Excuse/late-checkin approval flow (needs backend)
- Bulk-mark-all on journal (backlog)
- Push notifications for headman events (future Notification phase)
- Offline mutation queue via Background Sync (future)
