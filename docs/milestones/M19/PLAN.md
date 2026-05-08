# M19 — Перенос функционала PWA в Telegram Mini App

**Статус:** code complete; осталась ручная Telegram WebView приемка  
**Старт / финиш:** 2026-05-07 / 2026-05-08 code complete  
**Estimate:** 12-18 человеко-дней

---

> Update 2026-05-08: все кодовые задачи M19 реализованы и покрыты автоматикой.
> Открыты только ручные Telegram WebView проверки: multipart upload,
> weekly report binary download/open и smoke обычным студентом/старостой.

## Scope

M19 закрывает отложенный долг по Telegram Mini App: текущий `frontends/mini-app`
доводится до функционального parity с готовым PWA-клиентом `frontends/pwa`.
Единственное намеренное отличие — авторизация: в Mini App нет ввода логина и
пароля, пользователь определяется через активный аккаунт Telegram и связанный
`telegram_id`.

В scope входит:

- перенос текущих пользовательских сценариев PWA для `STUDENT` и
  `STUDENT + is_headman`;
- сохранение отдельного Telegram Mini App проекта в `frontends/mini-app`;
- замена ручных mini-app DTO/interfaces на сгенерированные OpenAPI-типы по
  PWA-паттерну;
- единый подход к RFC 7807/Problem Details, retry, optimistic UI и cache
  invalidation;
- TMA-auth через `POST /api/auth/tma` с `initData`, без `LoginPage` и без
  password-form;
- Telegram-specific адаптеры: viewport, theme seed, BackButton, MainButton,
  haptic feedback, launch/deep-link params;
- перенос PWA-экранов: главная, расписание, отметка, ДЗ,
  late-checkin, excuse, профиль, group/headman hub;
- перенос headman-функций из PWA: обзор группы, студенты/помощники,
  предметы, журнал, excuse moderation, late-checkin moderation, статистика,
  weekly report export;
- unit/integration component tests для Mini App и smoke/E2E с mock Telegram SDK.

Не входит:

- логин/пароль в Mini App;
- notification history/settings/notification center в Mini App: уведомления
  настроены на стороне Telegram-бота и приходят в чат с ботом;
- PWA-only функции: install prompt, iOS install onboarding, Service Worker,
  Web Push subscription, offline runtime cache, PWA version gate;
- desktop/admin сценарии web-panel;
- teacher/admin parity в Mini App, если пользователь явно не расширит scope.

## Текущее состояние

Документация фиксирует Mini App как отдельный React/Vite frontend:

- `README.md` и `docs/architecture/architecture.md`: Mini App — React + Vite +
  TypeScript, отдельный frontend рядом с PWA и web-panel.
- `docs/meta/phases-plan.md`: исходная цель Mini App — геоотметка, статистика,
  расписание, excuse, late-checkin, ДЗ.
- `docs/archive/future-ideas.md`: принятое решение после M12 — делать
  `copy+adapt` из PWA в Mini App.
- `docs/milestones/M07-frontend-hardening/PLAN.md`: Mini App был out of scope,
  миграция отложена.
- `docs/milestones/M08-test-infrastructure/DECISIONS.md`: Mini App E2E был
  пропущен до миграции на PWA baseline.

Фактический `frontends/mini-app` сейчас реализован на:

- React 19 + Vite 7 + TypeScript 5.8;
- `@telegram-apps/sdk` и `@telegram-apps/sdk-react`;
- TanStack Query;
- Axios;
- React Router;
- Tailwind CSS v4 + shadcn/base-ui primitives;
- Motion;
- Phosphor Icons;
- dev port `5174`, production path `/mini-app/`.

Сейчас Mini App содержит ограниченный набор экранов:

- `SchedulePage` — расписание только на сегодня;
- `CheckInPage` — геоотметка через browser `navigator.geolocation`;
- `StatsPage` — статистика студента;
- `HomeworkPage` — список/личный toggle ДЗ;
- TMA-auth provider через `/api/auth/tma`.

Относительно PWA отсутствуют или неполны:

- home dashboard;
- недельная навигация расписания, offline/stale notices и actions sheet;
- профиль и настройки;
- student late-checkin;
- student excuses с файлами;
- headman group hub;
- headman overview/students/subjects/journal/excuses/late-checkin/stats;
- headman weekly report export;
- STOMP/live feedback для check-in остается отдельным техническим вопросом,
  но notification center в Mini App не переносится;
- shared generated OpenAPI types и drift guard;
- browser/PWA-specific код должен быть явно исключен или адаптирован.

## Целевой UX

Mini App после M19 должен ощущаться как Telegram-вход в тот же мобильный
RutTrack, что и PWA:

- студент открывает Mini App из бота и сразу попадает в приложение без
  login screen;
- если Telegram аккаунт не связан со студентом, показывается понятная ошибка
  с инструкцией открыть `/start` в боте или обратиться к администратору;
- обычный студент видит основные рабочие разделы: главная, расписание,
  отметка, ДЗ, запрос отметки, уважительные причины, профиль;
- староста дополнительно видит `Группа` и все headman-разделы, как в PWA;
- Telegram native controls используются только там, где они улучшают сценарий:
  MainButton для primary submit, BackButton для modal/fullscreen flows,
  haptic feedback для успешных/ошибочных действий;
- PWA-only баннеры установки, Web Push и offline install UX не показываются.

## Авторизация

Целевой flow:

1. Mini App получает raw `initData` через `@telegram-apps/sdk-react`.
2. Клиент вызывает `POST /api/auth/tma` с `{ "initData": "<raw>" }`.
3. Auth-service валидирует HMAC по `TMA_BOT_TOKEN`, `auth_date` и связанный
   `telegram_id`.
4. Клиент хранит access token только в памяти.
5. При `401` клиент повторяет TMA-auth с тем же `initData`.
6. Logout в Mini App означает локальный сброс клиентского состояния и
   best-effort `/auth/logout`, но не возврат на login/password screen.

JWT payload должен маппиться так же, как в PWA:

- `sub` -> `user.id`;
- `role`;
- `group_id` или `groupId`;
- `is_headman` -> `user.isHeadman`.

Mini App не должен требовать, принимать или сохранять логин/пароль.

## Модули / изменения

### Frontend: `frontends/mini-app`

- Перестроить Mini App от PWA baseline: переносить функциональные PWA-модули
  по одному, сохраняя TMA-specific shell и auth.
- Добавить `scripts/generate-types.mjs`, `openapi-typescript`,
  `openapi-fetch` и `src/api/generated/` по PWA-паттерну.
- Унифицировать `src/shared/lib/axios.ts` с PWA: Problem Details parsing,
  retry policy, bearer access token, TMA re-auth callback.
- Расширить `AuthProvider`: `isHeadman`, `group_id`/`groupId`, invalid
  `initData` UX, linked-account error state, local cleanup.
- Добавить role-aware navigation: bottom tabs для primary flows, drawer/menu
  для secondary flows, headman tab только при `isHeadman=true`.
- Перенести shared PWA компоненты: `AppShell`, `BottomSheet`,
  `PullToRefresh`, `SegmentedControl`, skeleton/loading/error/toast patterns,
  error boundary.
- Удалить или не переносить PWA-only компоненты: `IOSOnboardingOverlay`,
  `PushPermissionCard`, `UpdateBanner`, service worker hooks, install prompt.
- Перенести student flows: home dashboard, schedule, check-in, homework,
  late-checkin, excuses, profile.
- Перенести headman flows: group hub, overview, students, subjects, journal,
  excuses moderation, late-checkin moderation, stats, weekly report export.
- Заменить browser geolocation в check-in на Telegram location API, если
  доступен; browser geolocation оставить только dev/browser fallback.
- Поддержать Telegram `start_param`/launch params для deep links:
  `checkin:<lessonId>`, `lesson:<lessonId>`, `group`.

### Backend/API

Backend не должен получать новые public endpoints только ради Mini App, если
уже есть PWA endpoint.

Проверить и при необходимости закрыть небольшие gaps:

- `POST /auth/tma` возвращает claims `group_id` и `is_headman`, достаточные
  для PWA parity.
- Gateway `permitAll` и route `/api/auth/tma` остаются открытыми без JWT.
- CORS/dev proxy для `localhost:5174` работает.
- File upload для student excuses работает из Telegram WebView.
- Binary download для headman weekly report работает в Telegram WebView.
- STOMP/ws-ticket flow совместим с in-memory token Mini App только если он
  нужен для live feedback check-in; notification center в Mini App не делаем.

### Notification bot

- Проверить inline WebApp buttons из `lesson.started`: URL должен вести на
  production path `/mini-app/` и передавать enough context для открытия
  конкретной отметки.
- Для deep links использовать BotFather/WebApp `start_param` совместимо с
  Telegram Mini App launch params.
- Не переносить бизнес-логику check-in в Python bot: бот только открывает Mini
  App и доставляет Telegram-уведомления.

### Infra / routing

- Production path Mini App — `/mini-app/` через reverse proxy.
- Внутренний `frontends/mini-app/nginx.conf` должен отдавать SPA fallback и
  не кэшировать `index.html`.
- При необходимости синхронизировать `docs/product/url-layout.md`: сейчас
  operation docs уже знают `/mini-app/*`, а product URL layout может отставать.

## Пошаговый план реализации

### G1 — Baseline и контракт parity

1. Составить route/function matrix PWA -> Mini App.
2. Зафиксировать что переносим, что исключаем как PWA-only.
3. Подтвердить target roles: `STUDENT` и `STUDENT + is_headman`.
4. Синхронизировать auth claim mapping с PWA.
5. Зафиксировать browser fallback только для dev mode.

### G2 — OpenAPI types и shared API layer

1. Добавить генерацию OpenAPI types в Mini App.
2. Удалить ручные DTO там, где есть generated types.
3. Перенести Problem Details parser и error toast conventions.
4. Добавить drift guard в CI для Mini App.
5. Обновить unit tests на API hooks.

### G3 — TMA auth hardening

1. Расширить `AuthProvider` до parity с PWA user model.
2. Обработать `group_id` и `is_headman`.
3. Развести states: loading, unauthorized/unlinked, backend unavailable.
4. Реализовать local cleanup/logout без login redirect.
5. Покрыть TMA-auth unit tests и auth-service contract references.

### G4 — Shell, navigation, Telegram adapters

1. Перенести PWA shell patterns без PWA-only баннеров.
2. Добавить role-aware tabs/drawer.
3. Подключить Telegram BackButton/MainButton adapters.
4. Подключить launch params router.
5. Проверить safe-area, viewport expand, light/dark theme seed.

### G5 — Student core parity

1. Перенести `HomeDashboard`.
2. Расширить расписание до PWA-week/day UX.
3. Перенести check-in screen и адаптировать geolocation.
4. Перенести homework page с optimistic toggle.
5. Перенести profile без PWA install/push blocks.

### G6 — Student requests parity

1. Перенести student late-checkin.
2. Перенести student excuses.
3. Проверить file picker/upload в Telegram WebView.
4. Добавить Telegram MainButton для submit flows там, где это уместно.
5. Покрыть success/error/409/413/429 cases.

### G7 — Notifications excluded / bot delivery

1. Зафиксировать, что notification history/settings/notification center не
   входят в Mini App.
2. Не переносить Web Push, notification settings, unread badge и STOMP
   notification center из PWA.
3. Оставить уведомления в Telegram-чате с ботом.
4. Проверить только bot deep links/WebApp buttons, которые открывают Mini App.
5. Не добавлять notification tab в Mini App navigation.

### G8 — Headman parity

1. Перенести `GroupHub`.
2. Перенести headman overview.
3. Перенести students/assistants management.
4. Перенести subjects management.
5. Перенести journal с ручной отметкой.
6. Перенести headman excuses moderation.
7. Перенести headman late-checkin moderation.
8. Перенести headman stats и threshold UI.
9. Перенести weekly report export.

### G9 — Bot deep links и launch flows

1. Проверить URL Mini App в bot messages.
2. Открывать конкретный `lessonId` из Telegram notification.
3. Открывать нужный экран из action buttons.
4. Добавить regression tests для link builder в bot.
5. Smoke-test в dev через mock launch params.

### G10 — Test automation

1. Расширить Vitest coverage Mini App до всех перенесенных flows.
2. Добавить mock Telegram SDK utilities.
3. Добавить Playwright smoke для `/mini-app/` с mock initData.
4. Проверить mobile viewport 390x844 и 430x932.
5. Добавить visual/manual checklist для реального Telegram WebView.

### G11 — QA и release

1. `npm run build` и `npm test` для Mini App.
2. `npm test` для PWA на затронутых shared-copy modules.
3. Backend tests только если менялись API/bot/backend.
4. Проверка production path `/mini-app/` через nginx.
5. Проверка реального Telegram запуска с тестовым студентом и старостой.

## Acceptance criteria

- [x] Mini App собирается и тестируется командой `npm run build` / `npm test`
  в `frontends/mini-app`.
- [x] Mini App не содержит login/password UI и не требует ручного ввода
  credentials.
- [x] TMA-auth через `/api/auth/tma` восстанавливает access token после `401`.
- [x] Claims `group_id`/`groupId` и `is_headman` корректно превращаются в
  role-aware UI.
- [x] Обычный студент видит рабочие Mini App flows: home, schedule, check-in,
  homework, late-checkin, excuses, profile.
- [x] Староста видит PWA-equivalent headman flows: group hub, overview,
  students/assistants, subjects, journal, excuses, late-checkin, stats,
  weekly report export.
- [x] PWA-only install/offline/Web Push UI не отображается в Mini App.
- [x] Check-in использует Telegram location API в Telegram WebView и dev
  fallback в обычном браузере.
- [x] Bot deep link на начавшуюся пару открывает Mini App сразу на сценарии
  отметки нужной пары.
- [x] OpenAPI generated types покрывают Mini App, ручные DTO не расходятся с
  backend contracts.
- [x] Mini App smoke/E2E покрыт mock Telegram SDK и Playwright smoke с mock initData.
- [ ] Ручная приемка: хотя бы один реальный Telegram WebView прогон обычным студентом и старостой.

## Dependencies

- **Блокируется:** стабильным PWA baseline после M18.
- **Блокирует:** полноценный запуск Mini App как основного Telegram-клиента
  для студентов/старост.
- **Parallel safe:** UI перенос, TMA-auth hardening и bot deep-link audit можно
  делать параллельно после фикса route/function matrix.

## Artifacts

- `frontends/mini-app/src/api/generated/` — generated OpenAPI types.
- `frontends/mini-app/scripts/generate-types.mjs` — генерация типов.
- `frontends/mini-app/src/features/...` — PWA-equivalent feature modules.
- `frontends/mini-app/src/shared/...` — Telegram-aware shared shell/API layer.
- `frontends/mini-app/src/test/telegramMock.ts` — reusable Telegram SDK mock.
- `tests/e2e/` — smoke coverage для `/mini-app/`, если добавляется Playwright.
- `docs/product/url-layout.md` — синхронизация `/mini-app/`, если нужна.
- `docs/milestones/M19/` — план, чеклист, решения, заметки.

## Open questions

- Нужен ли в Mini App экран profile logout, если Telegram сам является
  identity provider? Предложение: оставить локальный "перезапустить вход" /
  cleanup, но не показывать password/login действия.
- Notification history/settings/notification center исключены из M19: все
  уведомления приходят в чат с Telegram-ботом.
- Считаем ли `TEACHER` в Mini App out of scope? Предложение: да, пока
  Telegram Mini App описан как студенческий канал.
