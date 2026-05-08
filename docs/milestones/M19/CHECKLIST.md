# M19 Checklist

Атомарные задачи отмечаются `[x]` после реализации и проверки. `[~]` означает, что направление начато, но parity еще не закрыт полностью.

## G1 — Discovery и матрица parity

- [x] Составить route/function matrix `frontends/pwa` -> `frontends/mini-app`.
- [x] Зафиксировать PWA-only exclusions: install prompt, iOS onboarding, SW, Web Push, offline cache, version gate.
- [x] Зафиксировать Mini App target roles: `STUDENT`, `STUDENT + is_headman`.
- [x] Зафиксировать auth claim mapping `group_id`/`groupId`/`is_headman`.
- [x] Проверить production route `/mini-app/` в nginx и документации.

## G2 — OpenAPI и API layer

- [x] Добавить `openapi-typescript` и `openapi-fetch` в `frontends/mini-app`.
- [x] Добавить `scripts/generate-types.mjs`.
- [x] Сгенерировать `src/api/generated/*`.
- [x] Перевести auth/schedule/attendance/academic hooks на generated types.
- [x] Перенести Problem Details parser из PWA.
- [x] Добавить drift guard/CI step для Mini App.

## G3 — TMA auth

- [x] Расширить `AuthUser` полем `isHeadman`.
- [x] Поддержать JWT claim `group_id` и legacy `groupId`.
- [x] Обработать unlinked Telegram account отдельным экраном ошибки.
- [x] Реализовать повторный TMA-auth при `401`.
- [x] Реализовать local cleanup/logout без login redirect.
- [x] Покрыть `AuthProvider` unit tests для success/error/re-auth/headman.

## G4 — Shell и Telegram adapters

- [x] Перенести PWA `AppShell` patterns без PWA-only баннеров.
- [x] Добавить role-aware bottom tabs.
- [x] Добавить drawer/menu для secondary routes.
- [x] Подключить Telegram BackButton adapter.
- [x] Подключить Telegram MainButton adapter.
- [x] Подключить haptic feedback adapter.
- [x] Подключить launch params router.
- [x] Проверить safe area и viewport expand.

## G5 — Student core screens

- [x] Перенести `HomeDashboard`.
- [x] Перенести профиль без `PushPermissionCard` и install prompt.
- [x] Расширить расписание до week/day UX из PWA.
- [x] Перенести schedule action sheets, кроме PWA-only offline notices.
- [x] Перенести check-in UX из PWA.
- [x] Заменить browser geolocation на Telegram location API с dev fallback.
- [x] Перенести homework page и optimistic toggle.

## G6 — Student requests

- [x] Перенести `StudentLateCheckinPage`.
- [x] Перенести `StudentExcusesPage`.
- [ ] Ручная приемка: проверить multipart/file upload в Telegram WebView.
- [x] Добавить Telegram MainButton для submit в полноэкранных формах.
- [x] Покрыть errors `403/409/413/422/429`.

## G7 — Notifications excluded / bot delivery

- [x] Зафиксировать, что notification history/settings/notification center не входят в Mini App.
- [x] Не переносить Web Push, notification settings, unread badge и STOMP notification center из PWA.
- [x] Оставить доставку уведомлений в Telegram-чате с ботом.
- [x] Проверить bot deep links/WebApp buttons, которые открывают Mini App.
- [x] Не добавлять notification tab в Mini App navigation.

## G8 — Headman screens

- [x] Перенести `GroupHub`.
- [x] Перенести headman overview.
- [x] Перенести students/assistants management.
- [x] Перенести subjects management.
- [x] Перенести headman excuses moderation.
- [x] Перенести headman late-checkin moderation.
- [x] Перенести journal.
- [x] Перенести headman lesson/manual mark sheets.
- [x] Перенести headman stats и thresholds.
- [x] Перенести `HeadmanWeeklyReportCard`.
- [ ] Ручная приемка: проверить weekly report binary download в Telegram WebView.

## G9 — Bot deep links

- [x] Проверить текущие WebApp URL в `notification-bot`.
- [x] Передать `lessonId` из `lesson.started` в Mini App launch params.
- [x] Открывать check-in flow по launch params.
- [x] Добавить tests для Mini App launch-param resolver.
- [x] Добавить tests для bot URL/deep-link builder.
- [x] Smoke-test mock launch params в браузере.

## G10 — Tests

- [x] Добавить общий Telegram SDK mock для unit/component tests.
- [x] Покрыть shell navigation tests.
- [x] Покрыть student screens tests.
- [x] Покрыть headman API/navigation tests.
- [x] Добавить component tests для headman moderation screens.
- [x] Добавить `/mini-app/` Playwright smoke с mock initData.
- [x] Проверить viewport 390x844.
- [x] Проверить viewport 430x932.

## G11 — QA / приемка

- [x] `npm test` в `frontends/mini-app`.
- [x] `npm run build` в `frontends/mini-app`.
- [x] PWA tests для затронутых shared-copy modules.
- [x] Покрыть `/mini-app/` через production nginx route в Playwright smoke.
- [ ] Ручная приемка: Real Telegram WebView smoke обычным студентом.
- [ ] Ручная приемка: Real Telegram WebView smoke старостой.
- [x] Зафиксировать отклонения и остаточные риски в `NOTES.md`.

---

Если задача превращается в 6+ часов работы, разрезать ее на меньшие задачи перед реализацией.
