# Независимое review foundation — 4c020f332

Вердикт: **CHANGES REQUIRED — 2 × P2**. P1 не найдено.

Scope: только `git diff 4c020f332^..4c020f332`, 10 файлов shared foundation и mobile compatibility imports. Baseline задания 60346b26b; интеграция, переданная root, bf0ad0a02. Продуктовые файлы, tests, конфигурация, Git refs и зависимости reviewer не менял. Проверки без shared node_modules, установки, сборки, Gradle/Docker/CI. Авторский transcript не использовался. Модель/effort заданы в packet как gpt-6.1-sol/high; отдельного runtime metadata API reviewer не получал, поэтому фактическую модель не утверждает.

## P2 — мигрировать реальные ключи существующего web

Место: `/workspace/rutcampustrack/frontends/shared/foundation/theme-preference.ts:3`.

`LEGACY_THEME_PREFERENCE_KEYS` перечисляет `rct.mobile.theme` и `rct.web.theme`. Действующий web пишет `ruttrack.theme` и ранее использовал `web-panel.theme`: `/workspace/rutcampustrack/frontends/web-panel/src/app/core/theme/theme.service.ts:17` и `:19`. Оба действительных ключа новый reader игнорирует. Это расходится с THEME-CONTRACT — «Единый validated storage key мигрирует rct.mobile.theme и legacy web keys».

Воспроизведение: создать storage с единственной записью `ruttrack.theme=light` (также отдельно `web-panel.theme=light`), передать его в `createThemePreference(() => storage, null)`, вызвать `read()`. Фактический результат в обоих случаях — `null`, `rct.theme` не записан. При подключении нового web старый ручной выбор теряется, а bootstrap применяет fallback/system, который может дать противоположную тему. Это дефект нового общего reader, не требование реализовать будущий route shell в текущем patch.

Исправление: добавить реальные legacy keys с определённым приоритетом, сохраняя авторитет валидного `rct.theme`.

## P2 — не терять прочитанную тему при ошибке migration write

Место: `/workspace/rutcampustrack/frontends/shared/foundation/theme-preference.ts:24`.

При валидной legacy теме вызов `setItem()` выполняется до `return legacy` внутри общего try/catch. Если чтение доступно, а запись отклонена (например, quota exceeded), catch возвращает `null`. Уже прочитанный ручной выбор исчезает из bootstrap. Старый mobile reader для такого storage возвращал валидное значение без необходимости записи; это регрессия существующих PWA/TMA пользователей с legacy key.

Воспроизведение: storage.getItem('rct.mobile.theme') возвращает `light`, остальные getItem — `null`; storage.setItem бросает `QuotaExceededError`. `createThemePreference(() => storage, null).read()` фактически возвращает `null` вместо `light`.

Исправление: migration write сделать best effort отдельно от успешного чтения, вернуть валидное legacy значение независимо от успеха записи.

## Проверки и пределы

- Read-only `git diff --check 4c020f332^ 4c020f332`: exit 0.
- Прочитаны repository/frontends/tests AGENTS, RULES.md, CURRENT.md, docs/agent-workflow.md, GOAL-CONTRACT, OWNER-BRIEF, THEME-CONTRACT, design README, brandbook/token sources, сохранённый project design SKILL.md и применимые части BRAND_DIRECTION/A11Y_REQUIREMENTS. Последние два лежат в docs/product/reference-rutcampustrack-design, а не по устаревшим ссылкам docs/design. Разрешение владельца на необходимые технические токены имеет приоритет над старым запретом skill.
- Node v24.19.0, один успешный dependency-free harness через `node --input-type=module`, built-in `node:module.stripTypeScriptTypes`, ESM data imports. Исходники theme.ts/theme-preference.ts перед запуском сверены `git diff 4c020f332 -- <оба пути>`: diff пуст. Проверены реальные функции, fake target/source/storage, synthetic storage event. Результаты ниже. Это адресное воспроизведение логики, не browser или сквозная runtime приёмка.
- Первая попытка того же harness читала revision через Node execFileSync(git show): sandbox вернул spawnSync git EPERM. Повтор изменил гипотезу доступа: прямое чтение файлов после независимой git diff сверки; shared dependencies не использовались.
- Controller с injected source: system dark→light, ручной dark поверх source light, возврат system, resolved `data-theme`, ownership rejection и dispose/restoration — PASS в fake-target harness. Preference: canonical storage event, фильтр другого storageArea и dispose — PASS в synthetic-event harness.
- Static palette comparison: все 72 semantic color tokens обоих режимов присутствуют и совпадают с docs/design/tokens-v2.json; только текстовое представление четырёх dark alpha-значений отличается `.6/.7/.5` против `.60/.70/.50`, что численно эквивалентно. Новых размерных CSS px нет; root font не фиксируется. Onest/tabular правило вынесено в neutral base, mobile viewport/scrollbar globals остаются в mobile base. MobileTheme aliases и структурная ProfileTheme совместимость сохранены.
- Existing theme.test.ts не запускался: root удержал shared node_modules на время восстановления baseline. В нём есть старые assertions `data-theme=system` и точного набора прочитанных storage keys, расходящиеся с новым принятым resolved DOM/migration контрактом; требование сохранять эти assertions в findings не включено. Это не test PASS.
- Авторский source-only tsc, заблокированный missing @sentry/vue, не принят как PASS. Typecheck/build/browser/visual и реальный PWA/TMA flow reviewer не запускал и не утверждает. Backend mirror/reconciliation, stale server responses и Telegram adapter — другие ownership scopes; их отсутствие не выставлено дефектом данного foundation patch.

### Наблюдённые результаты Node harness

```json
[
  {"legacyKey":"rct.mobile.theme","read":"light","canonical":"light"},
  {"legacyKey":"rct.web.theme","read":"light","canonical":"light"},
  {"legacyKey":"ruttrack.theme","read":null,"canonical":null},
  {"legacyKey":"web-panel.theme","read":null,"canonical":null},
  {"case":"valid rct.mobile.theme=light; setItem throws QuotaExceededError","read":null},
  {"case":"injected source/manual priority/resolved DOM/disposal","result":"PASS — fake target/source"},
  {"case":"storage event/area guard/disposal","result":"PASS — synthetic event"}
]
```

Review завершено без исправлений. Автор fixes — /root/theme_foundation; интегратор — /root. Нужна независимая recheck двух исправленных условий после нового стабильного SHA.
