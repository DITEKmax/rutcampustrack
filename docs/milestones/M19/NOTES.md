# M19 Notes

Живой файл для отклонений, измерений, замечаний QA и вопросов по реализации.

---

## 2026-05-07

- M19 подготовлен как план миграции `frontends/mini-app` до parity с
  `frontends/pwa`.
- По документации Mini App реализуется на React + Vite + TypeScript и
  Telegram Mini App SDK; фактический `package.json` подтверждает React 19,
  Vite 7, TypeScript 5.8, `@telegram-apps/sdk-react`, TanStack Query, Axios,
  React Router, Tailwind v4, Motion и Phosphor Icons.
- Текущий Mini App уже имеет TMA-auth через `/api/auth/tma`, но ограничен
  расписанием на сегодня, check-in, статистикой и ДЗ.
- Документы M07/M08 и `future-ideas.md` подтверждают, что Mini App был
  осознанно отложен и должен мигрироваться copy/adapt из PWA после M12.
- Product URL layout может отставать: operations docs и nginx знают
  `/mini-app/*`, а `docs/product/url-layout.md` в v9 таблице перечисляет
  `/app/`, но не `/mini-app/`. Проверить и синхронизировать в G1.
- G1 закрыт: `/mini-app/` добавлен в production path routing в
  `docs/product/url-layout.md`, scope ограничен `STUDENT` и headman, PWA-only
  capabilities исключены.
- G2 стартовал: добавлены `openapi-typescript`, `openapi-fetch`,
  `scripts/generate-types.mjs`, offline generated types для auth/academic/
  schedule/attendance и CI drift-check для Mini App.
- G2 пока частичный по hook migration: создан `src/api/schema.ts`, текущие
  feature `types.ts` переключены на aliases, но полный перенос всех будущих
  PWA hooks будет идти по мере G5-G8.
- G3 закрыт: Mini App AuthProvider понимает `group_id`, legacy `groupId`,
  `is_headman`, показывает отдельный экран для 401/unlinked Telegram и имеет
  local cleanup/logout с повторным TMA-auth.
- G4 стартовал: корневой route теперь редиректит на `/home`, расписание
  переехало на `/schedule`, bottom nav получил primary tab `Главная`.
  Headman-specific tab пока не включался до переноса `GroupHub`, чтобы не
  добавлять новую заглушку.
- G5 стартовал: перенесен student `HomeDashboard` с profile header, stat
  cards, donut, weekly chart и top missed list. Для parity добавлен
  `recharts@2.15.4`; route-level lazy оставляет тяжелый dashboard chunk вне
  initial schedule/check-in path.
- G4/G5 продолжены: добавлен right drawer для secondary routes
  (`/homework`, `/profile`), header получил кнопку меню и haptic feedback,
  а `/profile` перенесен без `PushPermissionCard`, install prompt и
  login/password UI. Кнопка профиля "Перезапустить вход" выполняет local
  cleanup/logout и повторную TMA-авторизацию через Telegram.
- G10 частично: расширен test mock Telegram SDK для `hapticFeedback`, добавлены
  component-тесты `DrawerMenu` и `ProfilePage`.
- G5 schedule продолжен: `SchedulePage` перешел с "только сегодня" на
  week/day UX по PWA-паттерну: week range, переключение недель, табы
  Пн-Сб, swipe по дням, кнопка "Сегодня", недельный API
  `/schedule/groups/{groupId}/lessons?dateFrom&dateTo&size=100` и
  batch subject map через `useQueries`, чтобы убрать per-card waterfall.
  Schedule action sheet, offline/stale notices и semester bounds остаются
  отдельными пунктами G5/G4.
- Проверка: `npm test` в `frontends/mini-app` — 44/44 green. `npm run build`
  — green; остаются существующие CSS warnings про порядок `@import` в theme
  CSS.

## 2026-05-08

- Уточнение scope: notification history/settings/notification center не
  переносим в Mini App. Все уведомления уже настроены и приходят в чат с
  Telegram-ботом. В M19 остается только проверка bot deep links/WebApp buttons.
- G6 student requests перенесен: добавлены `/late-checkin` и `/excuses`,
  общий `studentRequestsApi`, generated aliases для `AttendanceRecord`,
  `ExcuseTicket`, `ExcuseType`, отправка late-checkin через
  `/attendance/late-checkin/{lessonId}`, excuse tickets через
  `/attendance/excuses` и multipart `/attendance/excuses/with-file`.
- Для `StudentExcusesPage` добавлен Telegram-aware `BottomSheet` и подключен
  `useMainButton` на submit. Файловый picker и multipart реализованы, но
  реальный Telegram WebView upload нужно проверить вручную.
- Routes/menu обновлены: drawer теперь ведет на ДЗ, запрос отметки,
  уважительные пропуски и профиль. Notification tab/settings не добавлялись.
- Проверка: `npm test` в `frontends/mini-app` — 49/49 green. `npm run build`
  — green; остаются существующие CSS warnings про порядок `@import` в theme
  CSS.
- G5 check-in продолжен: прямой `navigator.geolocation` заменен на
  Telegram-aware adapter. Основной путь поддерживает
  `Telegram.WebApp.requestLocation` и `Telegram.WebApp.LocationManager`;
  browser geolocation доступен только при `VITE_TMA_DEV=true`.
- Для location adapter добавлены unit tests на Telegram requestLocation,
  LocationManager init/getLocation, disabled fallback, dev fallback и mapping
  ошибок доступа к геолокации.
- G5 schedule action sheet перенесен в Mini App: карточки расписания
  подтягивают личный статус из `/attendance/reports/student/records`, показывают
  статус `+ / н / у / сп`, открывают bottom sheet по паре и позволяют отправить
  late-checkin или single-lesson excuse ticket с файлом до 10 МБ. Уведомления и
  notification settings при этом не добавлялись.
- G5 homework приведен ближе к PWA parity: добавлены режимы день/неделя/месяц,
  фильтр "только невыполненные", календарь месяца, ссылки в карточках и
  единый `useDateNavigation` с `day/week/month`. Optimistic toggle покрыт
  rollback-тестом.
- G4/G9 launch params: корневой `/mini-app/` теперь читает
  `tgWebAppStartParam`, initData `startParam` и dev query aliases
  `start_param`/`startapp`. `checkin:<lessonId>` и `lesson:<lessonId>`
  открывают `/checkin/{lessonId}`; прямые params ограничены allowlist routes,
  чтобы не было произвольного redirect. Для dev mock добавлен
  `VITE_TMA_START_PARAM`.
- G8 стартовал: добавлен headman entrypoint `/group` и `/group/overview`.
  Bottom tabs и drawer теперь role-aware и показывают "Группа" только при
  `user.isHeadman=true`; обычный студент headman routes не видит. Добавлен
  `features/headman/shared/headmanApi` с загрузкой members, subjects,
  today lesson и pending counters для overview.
- В `GroupHub` не добавлялись новые заглушки для еще не перенесенных
  headman-экранов: новые карточки будут появляться вместе с реализацией
  соответствующих разделов.
- Проверка: `npm test` в `frontends/mini-app` — 67/67 green. `npm run build`
  — green; остаются существующие CSS warnings про порядок `@import` в theme
  CSS.

- G8 moderation продолжен: добавлены реальные headman routes `/group/excuses` и
  `/group/late-checkin` без notification UI/settings. `GroupHub`, overview, drawer,
  header и `startParam` allowlist ведут на рабочие страницы, а не на заглушки.
- `features/headman/shared/headmanApi` расширен hooks для group excuse tickets,
  решения excuse ticket через `PATCH /attendance/excuses/{id}/status` с
  `APPROVED/REJECTED`, group late-checkin requests через
  `GET /attendance/late-checkin/group/{groupId}` и решения через
  `POST /attendance/late-checkin/{requestId}/decision`.
- Headman moderation использует generated OpenAPI aliases (`ExcuseTicket`,
  `LateCheckinRequest`) и нормализует backend statuses из uppercase в Mini App
  view model lowercase.
- Проверка после G8 moderation: targeted tests
  `npm test -- src/features/headman/shared/__tests__/headmanApi.test.ts src/shared/components/__tests__/DrawerMenu.test.tsx src/shared/lib/__tests__/launchRoutes.test.ts`
  — 17/17 green. Full `npm test` — 71/71 green. `npm run build` — green; остаются
  существующие CSS warnings про порядок `@import`.
- G8 students/assistants продолжен: добавлен рабочий route `/group/students`.
  Экран показывает членов группы, старосту, действующих помощников, назначает
  помощника через `POST /academic/assistants` с `groupId` и permissions, отзывает
  через `DELETE /academic/assistants/{id}`. `GroupHub`, drawer, header и
  `startParam` allowlist обновлены.
- Проверка после G8 students/assistants: targeted tests — 20/20 green. Full
  `npm test` — 74/74 green. `npm run build` — green; остаются существующие CSS
  warnings про порядок `@import`.
- G8 subjects management продолжен: добавлен рабочий route `/group/subjects`.
  Экран показывает предметы группы, тип занятия и назначенных преподавателей,
  создает предмет через `POST /academic/subjects` с `type` и `teacherIds`,
  обновляет название/тип через `PUT /academic/subjects/{id}` и синхронизирует
  преподавателей через `POST/DELETE /academic/subjects/{id}/teachers/{teacherId}`,
  удаляет предмет через `DELETE /academic/subjects/{id}`. `GroupHub`, drawer,
  header и `startParam` allowlist обновлены.
- Проверка после G8 subjects management: targeted tests — 24/24 green. Full
  `npm test` — 78/78 green. `npm run build` — green; остаются существующие CSS
  warnings про порядок `@import`.
- G8 journal продолжен: добавлен рабочий route `/group/journal` для журнала старосты.
  Экран выбирает предмет и дату, загружает `GET /attendance/reports/journal`,
  flatten-ит строки студентов в mobile-friendly список и позволяет быстро менять
  отметку через `PUT /attendance/lessons/{lessonId}/students/{studentId}` с
  optimistic UI и rollback при ошибке. `GroupHub`, drawer, header и `startParam`
  allowlist обновлены. Notification UI/settings не добавлялись.
- Проверка после G8 journal: targeted tests
  `npm test -- src/features/headman/shared/__tests__/headmanApi.test.ts src/shared/components/__tests__/DrawerMenu.test.tsx src/shared/lib/__tests__/launchRoutes.test.ts`
  — 26/26 green. Full `npm test` — 80/80 green. `npm run build` — green; остаются
  существующие CSS warnings про порядок `@import`.
- G8 headman lesson/manual mark sheets продолжен: в расписание Mini App добавлен
  full-screen `HeadmanLessonSheet` для старосты. Как в PWA, action button на паре
  для `user.isHeadman=true` открывает roster/manage/report sheet вместо student
  excuse/late-checkin sheet. Поддержаны `GET /attendance/reports/lesson/{lessonId}`,
  ручной `PUT /attendance/lessons/{lessonId}/students/{userId}`, batch
  `POST /attendance/marks/batch` и отмена пары через
  `PATCH /schedule/lessons/{lessonId}/cancel`. Notification UI/settings не добавлялись.
- Проверка после G8 lesson/manual mark sheets: первый targeted run упал на sandbox
  `spawn EPERM` при старте esbuild; повтор вне sandbox — 30/30 green. Full
  `npm test` — 87/87 green. `npm run build` — green; остаются существующие CSS
  warnings про порядок `@import`.
- G8 headman stats/thresholds продолжен: добавлен route `/group/stats`, карточка
  в `GroupHub`, drawer/header/startParam allowlist. Mini App считает статистику
  по тем же данным журнала, что PWA: present/excused/free_attendance считаются
  посещением, absent — пропуском, cancelled исключается из denominator. Для
  порогов добавлены `GET /academic/thresholds/resolve` и
  `PUT /academic/thresholds/subject` с inline editor на карточке предмета.
  Notification UI/settings не добавлялись.
- Проверка после G8 stats/thresholds: targeted tests — 35/35 green после
  повторного запуска вне sandbox из-за `spawn EPERM`. Full `npm test` —
  89/89 green. `npm run build` — green; остаются существующие CSS warnings про
  порядок `@import`.
- G8 `HeadmanWeeklyReportCard` перенесен в Mini App `GroupHub`: загружает недели
  активного семестра через `GET /attendance/reports/headman-weekly/weeks`,
  скачивает одну неделю через `GET /attendance/reports/headman-weekly/current`
  с `responseType: blob` и несколько недель через
  `POST /attendance/reports/headman-weekly/export`. Реальный Telegram WebView
  smoke для бинарного скачивания оставлен отдельным открытым пунктом checklist.
  Notification UI/settings не добавлялись.
- Проверка после G8 weekly report card: targeted tests — 31/31 green. Full
  `npm test` — 92/92 green. `npm run build` — green; остаются существующие CSS
  warnings про порядок `@import`.
- G7/G9 bot deep links продолжены: `notification-bot` теперь строит Mini App
  ссылку через общий helper, который поддерживает оба production shape:
  `https://t.me/<bot>/<app>?startapp=...` для Telegram deep link и прямой
  WebApp URL `/mini-app/?tgWebAppStartParam=...`. Значение по умолчанию
  `MINI_APP_URL` выровнено с `.env.prod.example`: `https://t.me/ruttrack_bot/ruttrack`.
- `lesson.started` теперь добавляет inline-кнопку "Открыть отметку" и передаёт
  `checkin:<lessonId>` в Mini App launch params. При пустом/некорректном
  `MINI_APP_URL` сообщение остаётся plain chat message, чтобы уведомления бота
  не зависели от настройки Mini App.
- Проверка после G9 bot deep links: `ruff check` по изменённым Python-файлам —
  green. Targeted `pytest --no-cov tests/test_mini_app_links.py
  tests/test_lesson_started.py tests/test_event_dispatcher.py` — 27/27 green.
  Full `pytest` в `services/notification-bot` — 224/224 green, coverage 77.03%.

## 2026-05-08 final code audit

- Кодовые задачи M19 закрыты, кроме ручной приемки внутри реального Telegram
  WebView. В Mini App не добавлялись notification history/settings/center:
  уведомления остаются только в Telegram-чате с ботом.
- Runtime TODO/заглушки в `frontends/mini-app/src` не найдены. Найденные
  в audit строки не являются продуктовыми заглушками: `placeholder` в bot config
  fail-fast проверяется тестами, `stub` в gRPC клиентах/сгенерированных файлах
  относится к grpc tooling, `placeholder` в JSX — обычный атрибут input/textarea.
- Закрыт последний runtime TODO в student stats: красная зона теперь берет
  backend threshold через `GET /academic/thresholds/resolve`, с fallback `60`
  только пока конкретный threshold не загружен.
- Добавлены component tests для headman moderation screens, student request
  screens, shell navigation и общий Telegram SDK mock для Vitest.
- Добавлен Playwright smoke `tests/e2e/specs/mini-app.spec.ts`: mock
  `/api/auth/tma`, запуск `/mini-app/` с `tgWebAppStartParam=checkin:77`,
  проверка basename route `/mini-app/checkin/77` и viewport 390x844/430x932
  без horizontal overflow. `npm test -- --list --grep "Telegram Mini App"`
  в `tests/e2e` показывает 6 smoke cases в chromium/webkit; полный браузерный
  прогон остается за CI с поднятым e2e stack.
- Production build warning по CSS `@import` устранен: внешние font imports
  вынесены в начало `frontends/mini-app/src/index.css`.

Проверки:

- `frontends/mini-app`: `npm test` — 20 files, 102 tests green.
- `frontends/mini-app`: `npm run build` — green, без CSS warnings.
- `tests/e2e`: `npm ci --ignore-scripts` — green; `npm test -- --list --grep "Telegram Mini App"` — 6 tests listed.
- `frontends/pwa`: shared-copy targeted `npm test -- ...` — 7 files, 30 tests green.
- `services/notification-bot`: full `.venv\Scripts\python.exe -m pytest` —
  224 passed, coverage 77.03%. Узкий запуск `tests/test_mini_app_links.py
  tests/test_lesson_started.py` логически прошел 14/14, но ожидаемо упал на
  coverage fail-under из-за малого набора.

Остаточные риски, оставленные как ручная приемка:

- Telegram WebView multipart/file upload для student excuses.
- Telegram WebView binary download/open для headman weekly report.
- Real Telegram WebView smoke обычным студентом.
- Real Telegram WebView smoke старостой.

## Open questions

- Blocking open questions для code complete нет. Пользовательский logout/cleanup
  оставлен как "перезапустить вход через Telegram"; Teacher/Admin остаются out
  of scope для M19.

## Technical notes

- Auth-service endpoint по OpenAPI: `POST /auth/tma`; через gateway клиент
  вызывает `/api/auth/tma`.
- Gateway уже исключает `/api/auth/tma` из JWT-filter.
- JWT parser в Mini App должен принять оба варианта group claim:
  `group_id` и `groupId`, как PWA.
- `is_headman` claim сейчас критичен для role-aware navigation и headman tab.
- Binary download в Telegram WebView нужно проверять отдельно: weekly report
  export может потребовать Telegram-specific download/open handling.
