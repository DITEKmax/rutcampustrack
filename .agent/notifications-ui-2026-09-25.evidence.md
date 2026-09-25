# Уведомления PWA/TMA — evidence

**Scope:** общий мобильный журнал уведомлений и настройки категорий/паузы через текущий API, доступные для активной авторизованной роли в PWA и TMA. Ветка `codex/notifications-ui-20260925`, база `c1b4bb210300fdbf3477507a6231cd114e0b3269`. Применимые правила: `.agent/orchestration-v2/RULES.md`, SHA-256 `F4986A1834A9175ADBB7DCB3C49483168111A0B60C7EB13ACDA9FBF9D74927F8` (проверен по каноническому файлу в основном репозитории; локальная копия worktree старее); также `frontends/AGENTS.md`, `rutcampustrack-design`, `COMPONENT_REGISTRY.md`, `JS-SYSTEM-10/11`, `JS-STUDENT-WEB-06` и решение владельца `docs/product/decisions/2026-09-25-notification-history-membership.md`.

## Критерии

- Один server-backed экран для всех текущих ролей PWA/TMA; закрытие возвращает к тому же смонтированному экрану и его вложенному состоянию.
- Серверная пагинация, фильтр непрочитанных, счётчик, одиночная/общая отметка прочтения и независимые загрузка, ошибка и повтор для истории, счётчика и настроек.
- Настройки при открытии читаются с сервера; изменения подтверждаются только ответом PUT. Экран не включает web-push и не обещает Telegram-доставку.
- Клиент привязан к поколению сессии, делает один refresh/retry на 401, очищает данные на 403 и не публикует устаревшие ответы. Telegram BackButton не меняет скрытый маршрут во время overlay и восстанавливает запрошенную им видимость после закрытия.
- Overlay — modal dialog в `body`: роль остаётся смонтированной под `inert`, Tab/Shift+Tab остаются внутри окна, начальный фокус попадает на «Назад», закрытие возвращает фокус к входной кнопке.
- UI показывает только подписи известных типов и ограниченный белый список полей payload. `userId`, `_links`, причины, произвольные URL и полный payload не передаются компоненту/не отображаются.

## Inventory

- `frontends/mobile-core/src/features/notifications/notifications-client.ts` — generation-bound API, DTO parsing, safe payload projection и фиксированные сообщения об ошибках.
- `frontends/mobile-core/src/features/notifications/NotificationsScreen.vue` — история и настройки с независимыми состояниями.
- `frontends/mobile-core/src/features/notifications/notifications-screen.pcss` — экранные стили на текущих semantic tokens.
- `frontends/mobile-core/src/features/notifications/NotificationsEntryButton.vue` и `notifications-entry.pcss` — общий вход для PWA/TMA.
- `frontends/mobile-core/src/features/notifications/notifications-client.test.ts` — stale generation, однократный refresh, whitelist payload и отказ небезопасному ID.
- `frontends/mobile-core/src/features/notifications/notifications-focus.ts` и `notifications-focus.test.ts` — изоляция tab sequence, Enter activation только внутри modal scope и восстановление фокуса.
- `frontends/mobile-core/src/shared/host.ts`, `src/index.ts` — экспорт feature API и optional back suspension на host boundary.
- `frontends/pwa-vue/src/App.vue`, `frontends/tma-vue/src/App.vue` — authenticated entry, owner-bound client и возврат к активному экрану.
- `frontends/tma-vue/src/telegram.ts` — подавление скрытых back listeners и восстановление видимости host-кнопки.
- `.agent/notifications-ui-2026-09-25.evidence.md` — этот пакет evidence.

## Проверки

Итоговый прогон после исправлений, команды запущены из корня назначенного worktree:

- `tsc -p frontends/mobile-core/tsconfig.json --noEmit` — exit 0.
- `vue-tsc -p frontends/pwa-vue/tsconfig.json --noEmit` — exit 0.
- `vue-tsc -p frontends/tma-vue/tsconfig.json --noEmit` — exit 0.
- `vitest run --root frontends/mobile-core src/features/notifications/notifications-client.test.ts` — exit 0, 1 file / 4 tests passed.
- `eslint` по перечисленным изменённым TS/Vue файлам с `--max-warnings=0` — exit 0.
- `git diff --check` — exit 0.

Для этих команд временно создан junction `frontends/node_modules` в worktree, направленный на `C:\Users\maksd\IntelliJIDEA\rutcampustrack\frontends\node_modules`; использовались только локальные compiler/linter/test executables, package install/autoinstall не запускался. После проверок junction удалён и его отсутствие подтверждено; целевой dependency tree не изменялся.

**Runtime evidence:** браузерный PWA и Telegram WebView в этой задаче не запускались; проверены типы, lint и API-boundary тесты. Серверный runtime/API scenario не объявляется проверенным этим пакетом.

## Исправление по Sol review

Sol review исходного commit `968840c799a4720ac073d99b33681273093ef339` обнаружил P2: смонтированная фоновая роль оставалась доступна с клавиатуры за notification overlay. Исправление ограничено modal/navigation поведением: `NotificationsScreen` теперь Teleport в `body`, помечает `#app` inert с восстановлением прежнего значения, ставит фокус на кнопку возврата и держит Tab/Shift+Tab внутри видимых элементов. Кнопка открытия остаётся смонтирована под modal и получает фокус после закрытия, поэтому роль и её вложенное состояние не пересоздаются.

После исправления выполнены целевые проверки (без повтора API client suite):

- `tsc -p frontends/mobile-core/tsconfig.json --noEmit` — exit 0.
- `vue-tsc -p frontends/pwa-vue/tsconfig.json --noEmit` — exit 0.
- `vue-tsc -p frontends/tma-vue/tsconfig.json --noEmit` — exit 0.
- `vitest run --root frontends/mobile-core src/features/notifications/notifications-focus.test.ts` — exit 0, 1 file / 1 interaction test passed: Shift+Tab/Tab wrap, Enter activates the overlay Back action, background route action remains untouched, and focus returns to the opener.
- Scoped ESLint по изменённым NotificationsScreen/focus helper/focus test и PWA/TMA App с `--max-warnings=0` — exit 0.
- `git diff --check` — exit 0.

Для correction checks junction `frontends/node_modules` временно создан в назначенном worktree после проверки точной цели `C:\Users\maksd\IntelliJIDEA\rutcampustrack\frontends\node_modules`. Он удалён после прогонов, отсутствие junction подтверждено; install и изменение dependency tree не выполнялись. Interaction test запускается в Vitest Node с контролируемым DOM focus test double; реальный browser/TMA WebView не проверялся, этот пакет не заявляет browser runtime.

## Ограничения

- История — ровно то, что API сохранил для текущего пользователя; экран не собирает события из `sessionStorage` и не подменяет отсутствующие события. По owner evidence, текущий `NotificationHistoryConsumer` ещё пропускает часть group broadcasts (`lesson.started/cancelled`, `homework.published`); полный fanout/history остаётся отдельной backend задачей. Для уже известного `HOMEWORK_PUBLISHED` добавлена только безопасная подпись.
- Нет разрешений/подписок Web Push, отправки через Telegram или подтверждений доставки. Встроенные переходы из событий не добавлены, произвольные URL не открываются.

## Дифф и операционная заметка

Целевая проверка диффа: `git diff c1b4bb210300fdbf3477507a6231cd114e0b3269..HEAD -- frontends .agent/notifications-ui-2026-09-25.evidence.md`; полный inventory выше включает новые файлы, которые не попадают в обычный unstaged `git diff --stat`.

Во время работы один `apply_patch` сначала указал `telegram.ts` в MAIN вместо worktree. Перед исправлением проверен diff этого единственного MAIN-файла: там находился только мой delta; он удалён точечно, без checkout/сброса сторонней работы. Root отдельно подтвердил чистый diff MAIN `frontends/tma-vue/src/telegram.ts`; тот же delta применён в назначенном worktree.
