# Карта: админская публикация — evidence

Baseline: `70e84a62cbfc2004ac9e2f388f64af7cf706962b`.
Scope: JS-ADMIN-23/24/25, только admin-map UI и адресат загрузки.

## Результат

- После смены корпуса этаж выбирается из его списка; старый floor ID не может
  стать адресатом загрузки.
- Выбранные PNG/SVG привязаны к паре корпус/этаж. Смена этой пары очищает поля
  файлов и сообщает об этом. В запрос попадают неизменяемые ID и файлы,
  захваченные при публикации; результат не меняет выбор после смены контекста
  или размонтирования компонента.
- Реестр после публикации перечитывает текущую версию. Статус неполного плана
  сообщает последствие: этаж нельзя открыть без готового SVG, PNG нельзя скачать
  без готового PNG.
- Проверка и безопасность PNG/SVG, лимит 10 MiB, backend URL/asset политика и
  серверное версионирование не менялись.

## Проверки

- `npx vitest run mobile-core/src/features/admin-map/admin-map-state.test.ts` —
  PASS, 2 проверки адресата и изоляции файлов.
- `npm run typecheck --workspace @rct/pwa-vue` — PASS.
- `npm run typecheck --workspace @rct/tma-vue` — PASS.
- `npx eslint src/features/admin-map/AdminMapScreen.vue src/features/admin-map/admin-map-state.ts src/features/admin-map/admin-map-state.test.ts --max-warnings=0` — PASS.
- `git diff --check` — PASS.
- `npm run typecheck --workspace @rct/mobile-core` — FAIL на неизменённом
  `src/features/headman-journal/headman-journal-client.test.ts:285:7`:
  `{ status: number }` не удовлетворяет типу `Response`. PWA/TMA SFC typechecks
  прошли; соседний тест не изменялся.

## Review correction

Sol bounded review found P2: subsequent refresh unmounted file inputs while the
selected File draft remained in memory. `initialLoadComplete` now keeps the
form mounted after the first load and shows a refresh status in place. Targeted
ESLint and `git diff --check` passed for this correction; bounded Sol recheck
is pending root.

Общий PWA/TMA runtime и реальный API путь ожидают объединённого запуска root.
Gradle/Docker и backend-файлы этой веткой не затрагивались.
