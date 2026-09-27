# Превью вложения заявки — evidence S1

Контракт: основной проект `.agent/orchestration-v2/evidence/2026-09-27-delivery/request-attachment-preview-contract.md`. База worktree: `5215dd0e3597a2edf3e3f90e7038063cf1f0578c`.

**Scope:** `RequestAttachmentField.vue`, `requests.pcss`, маленький owner object URL и lifecycle test. Другие формы, transport, retention, backend, shared contracts и admin не менялись.

**Критерии:** JPEG/PNG показывают локальное изображение; PDF передаётся нативному просмотрщику с отдельной ссылкой на открытие; для неподдерживаемого MIME, отсутствующего файла и ошибок есть текстовое состояние; файл можно удалить и выбрать снова; URLs освобождаются при удалении, замене и размонтировании; пользовательская HTML/SVG разметка не вставляется и не рендерится; лимиты, текущая валидация, disabled и indeterminate отправка сохранены.

**Evidence и проверки** (команды запускались из `frontends/` на Node/npm worktree):

| Команда | Exit | Результат |
|---|---:|---|
| `npx vitest run mobile-core/src/features/requests/request-attachment-preview.test.ts` | 0 | 2 lifecycle checks прошли: reuse/revoke при замене и удалении, cleanup; SVG не получает URL, отказ создания URL отражается как недоступный preview. |
| `npx eslint mobile-core/src/features/requests/RequestAttachmentField.vue mobile-core/src/features/requests/request-attachment-preview.ts mobile-core/src/features/requests/request-attachment-preview.test.ts --max-warnings=0` | 0 | scoped lint прошёл без warnings. |
| `npm run typecheck --workspace @rct/pwa-vue` | 0 | `vue-tsc --noEmit` прошёл. |
| `npm run typecheck --workspace @rct/tma-vue` | 0 | `vue-tsc --noEmit` прошёл. |
| `git diff --cached --check` | 0 | staged diff без ошибок пробелов; inventory содержит только назначенные 4 product-файла и этот evidence note. |

**Runtime:** product browser/visual smoke не запускался в leaf; root выполнит единый runtime после интеграции PWA/TMA и параллельного admin изменения. Реальная поддержка встроенного PDF в Telegram/WebView этим diff не подтверждена; интерфейс оставляет отдельное открытие и сообщает о недоступности встроенного просмотра.

**Diff:** список файла теперь включает preview; helper владеет только object URLs локально в смонтированной форме; PCSS добавляет размер preview через rem, существующие семантические цвета и радиус; lifecycle test покрывает освобождение URL. Новых зависимостей и токенов нет.

**Ограничения:** client preview — удобство, не проверка подписи файла и не замена серверной валидации; WebView может не показывать встроенный PDF. Объектные URL не сохраняются в draft/API и не используются вне компонента.
