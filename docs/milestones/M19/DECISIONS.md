# M19 Decisions

Архитектурные и продуктовые решения по milestone.

Формат: `## Dx (YYYY-MM-DD) — короткое название`, затем контекст / решение / последствия.

---

## D1 (2026-05-07) — Mini App остается отдельным React/Vite frontend

**Контекст:** документация и текущий repo уже держат Mini App в
`frontends/mini-app`, отдельно от PWA и web-panel.

**Решение:** не объединяем PWA и Mini App в один deployable frontend. M19
переносит функциональность из PWA copy/adapt подходом в существующий
`frontends/mini-app`.

**Последствия:** production path `/mini-app/` и Telegram SDK lifecycle остаются
изолированными. Дублирование кода сокращается за счет shared patterns и
generated types, но не за счет monolithic frontend.

---

## D2 (2026-05-07) — Авторизация только через Telegram initData

**Контекст:** пользователь в Mini App уже известен Telegram-боту через
активный Telegram аккаунт и связанный `telegram_id`.

**Решение:** в Mini App нет login/password screen. Единственный entrypoint
авторизации — `POST /api/auth/tma` с raw `initData`.

**Последствия:** password reset/change/login UX остается в web-panel/PWA.
Mini App показывает ошибку привязки Telegram, если `telegram_id` не найден или
не связан с активным студентом.

---

## D3 (2026-05-07) — Scope parity: STUDENT и headman

**Контекст:** Telegram Mini App в документации описан как студенческий канал,
а запрос владельца говорит, что бот знает студента.

**Решение:** parity делаем для текущих PWA-сценариев обычного студента и
старосты (`STUDENT + is_headman`). Teacher/admin сценарии не входят в M19.

**Последствия:** если позже нужен Mini App для преподавателя, это отдельный
milestone с другим UX и правами.

---

## D4 (2026-05-07) — PWA-only capability не переносится

**Контекст:** PWA имеет Service Worker, install prompt, Web Push, offline cache
и version gate. В Telegram WebView эти механики либо недоступны, либо дают
другой UX.

**Решение:** M19 переносит бизнес-функции PWA, но исключает PWA-platform
capabilities.

**Последствия:** Mini App зависит от онлайн API. Уведомления остаются в
Telegram-боте, без Web Push.

---

## D5 (2026-05-07) — Generated OpenAPI types обязательны для Mini App

**Контекст:** M07/M12 осознанно пропустили Mini App, поэтому там остались
ручные interfaces и нет drift guard.

**Решение:** M19 добавляет Mini App в OpenAPI generation flow по PWA-паттерну.

**Последствия:** breaking DTO changes должны ловиться CI для Mini App так же,
как для PWA/web-panel.

---

## D6 (2026-05-07) — Telegram location primary, browser geolocation fallback только для dev

**Контекст:** текущий `CheckInPage` использует `navigator.geolocation`, но
Telegram Mini App должен учитывать Telegram WebApp API и permissions model.

**Решение:** в Telegram WebView основной путь — Telegram location API. Browser
geolocation остается только для `VITE_TMA_DEV=true` и локальной разработки.

**Последствия:** production поведение проверяется в реальном Telegram WebView,
а локальные tests используют mock adapter.

---

## D7 (2026-05-08) — Уведомления остаются в Telegram-боте

**Контекст:** уведомления уже настроены и приходят студенту в чат с ботом.
Отдельные notification history/settings внутри Mini App не нужны.

**Решение:** M19 не переносит PWA notification history, notification settings,
unread badge и STOMP notification center в Mini App.

**Последствия:** Mini App navigation не получает tab "Уведомления". В M19
остается только проверка bot WebApp buttons/deep links, чтобы уведомления из
чата открывали нужный сценарий Mini App.
