# PK-113 — итоговый evidence

## Scope и критерии

В feature `headman-stats` добавлены реальные режимы динамики `SEMESTER`, `WEEK`, `SUBJECT` и inline-подробности студента. Запросы используют frozen backend DTO; фильтр предмета/типов динамики передаётся отдельно от таблицы. Детальная панель запрашивает actual `studentId`, независимо меняет страницы поздних отметок и заявлений, показывает метрики по предметам/типам и недельную динамику. Истории остаются видны и при `NO_COMPLETED_LESSONS`; черновики исключаются из отображения. График ограничен 0–100%, пропускает `null` как разрыв, а доступная клавиатурой таблица показывает «Нет данных» вместо синтетического нуля.

Проверены originals wireframe `docs/wireframes/headman/113-headman-stats.md` §§4.7/4.9/5/6 и decision `docs/product/decisions/2026-09-25-headman-student-stats-detail.md`. Контрактные shapes сверены с read-only `HeadmanStatsTrendQueryRequest`, `HeadmanStatsTrendResponse` и `HeadmanStatsStudentDetailResponse` backend WT; endpoint/DTO не изменялись. В scopes нет изменений App/auth/session/navigation, backend, shared tokens или headman-stats owner files.

## Изменённые файлы

- `frontends/mobile-core/src/features/headman-stats/headman-stats-client.ts` — frozen trend/detail types, exact request projection, validation и whitelist-нормализация DTO; nullable zero-denominator метрики становятся `null`.
- `frontends/mobile-core/src/features/headman-stats/HeadmanStatsScreen.vue` — переключение трёх режимов, server queries, generation/stale guards, retry/offline/403 handling, student-ID entry point и возврат keyboard focus.
- `frontends/mobile-core/src/features/headman-stats/headman-stats-screen.pcss` — scoped controls and panel entry styling на существующих токенах.
- `frontends/mobile-core/src/features/headman-stats/HeadmanStatsTrendChart.vue` и `headman-stats-trend-chart.pcss` — общий процентный график, две визуально различимые серии, focusable points и таблица-альтернатива.
- `frontends/mobile-core/src/features/headman-stats/HeadmanStatsDetailPanel.vue` и `headman-stats-detail-panel.pcss` — немодальная личная панель, метрики, subject/type table, weekly graph и две истории с независимой пагинацией.
- `.agent/evidence/headman-stats-detail-trend-20260925/packet.md` — девятисекционный frozen packet.
- `.agent/evidence/headman-stats-detail-trend-20260925/result.md` — этот результат.

## Проверки

Финальные targeted команды завершились с кодом **0**:

- `npm --prefix mobile-core run typecheck` (mobile-core).
- `npm --prefix pwa-vue run typecheck` (Vue templates приложения PWA).
- `npm --prefix tma-vue run typecheck` (Vue templates приложения TMA).
- ESLint с `--max-warnings=0` по четырём owned feature-файлам.
- `git diff --check`.

Зависимости не устанавливались. Для checks использован временный junction только по пути `frontends/node_modules` этого owned WT к уже существующему `C:\Users\maksd\IntelliJIDEA\rutcampustrack\frontends\node_modules`; он был удалён после проверки. После удаления `Test-Path frontends/node_modules` дал `False`, существующая shared dependency directory осталась на месте (`True`).

Browser/runtime evidence для экранов не снималось: в этой задаче доступного TMA/PWA runtime нет; runtime готовит root/backend owner в отдельном scope. Полный frontend build и общий тестовый suite не запускались.

## Ограничения

Экспорт графика/личной панели PNG/HTML, backend implementation и wiring вне `HeadmanStatsScreen` остаются вне PK-113. Контент ticket histories должен соответствовать frozen server policy: submitted/current/cancelled записи включены, `DRAFT` скрыт сервером и исключён клиентским отображением.
