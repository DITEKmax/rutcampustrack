# Narrow teacher recheck — 210c98202

**Прежние 3 × P2 закрыты. CHANGES REQUIRED: один новый P2 в затронутом drilldown.**

Scope только `ecdc2cec1..210c98202a9b967b2202f523520244569e6b8ce3`: TeacherJournalPage.vue, use-journal-pagination.ts, TeacherStatsPage.vue, TeacherStatsTable.vue, TeacherExcusePanel.vue. Frozen checkout `/workspace/rct-worktrees/teacher-web` имеет exact HEAD210c98202a9b967b2202f523520244569e6b8ce3, tracked clean. Baseline21 files/Figma/64 samples повторно не открывались. Продуктовых правок, children, installs/builds/tests/browser/backend mutations/heavy/CI reviewer не выполнял; новый immutable report — единственная запись.

Запрошенная модель/effort gpt-6.1-sol/high; независимого runtime metadata API нет, runtime model/effort не утверждаются.

## Новое P2 — заголовок студентов остаётся от прежней группы после смены dropdown

Место: `/workspace/rct-worktrees/teacher-web/frontends/web-vue/src/features/teacher/TeacherStatsPage.vue:208`; связанный обработчик — строка221.

`openGroup(B)` сохраняет B в `drillGroup`, переключает students scope и выбирает B. Заголовок затем всегда вычисляет имя по **drillGroup**, тогда как видимый TeacherFilters позволяет выбрать другую разрешённую группу A. Его @change вызывает только `load()`. `selection.groupId` и реальный stats query становятся A, а `drillGroup` остаётся B — ни load, ни filter handler его не обновляют/сбрасывают.

Воспроизведение по стабильному source: открыть «По моим группам» → клавиатурой выбрать B → дождаться студентов B → в доступном dropdown «Группа» выбрать A → дождаться stats ответа. Controls/query/rows показывают A, но крупный заголовок продолжает утверждать «Студенты группы B». Ошибка позволяет приписать показатели студентов другой группе. Серверный scope при этом не расширяется; это неверный пользовательский контекст данных.

Минимальное исправление: вычислять отображаемое имя по текущему `selection.groupId`, либо сбрасывать/переносить drill view при изменении группы. Если B нужно сохранять для return focus, хранить origin отдельно от текущего заголовка. Для recheck достаточен один переход drillB → selectA → return; не нужен полный повтор8flows/64layouts.

## Закрытие предыдущих findings

1. **Автоподгрузка журнала — FIXED.** Новый composable наблюдает горизонтальный край через IntersectionObserver, догружает одну порцию с request/revision/session/data-reference guards. Обычный режим не требует next-page кнопки. При отсутствии observer остаётся доступный fallback. Ошибка append сохраняет ранее загруженные строки, дальнейшая автоматическая попытка останавливается до explicit retry. Изменившиеся query/data/session не принимают поздний page response; явный inconsistent dataset получает recoverable reload-required error вместо ложного полного результата.
2. **Drilldown group→students — FIXED относительно прежнего отсутствия операции.** Group name — keyboard button с доступным названием; parent проверяет allowed group, reconcile выбирает assigned subject и допустимые types. Есть return action, раздельная память group/student фильтров/сортировок и возврат фокуса на исходную group row. Новый P2 выше касается последующей ручной смены группы в этой view.
3. **Даты заявки — FIXED.** Panel показывает createdAt/decisionAt через semantic time/datetime, Moscow date/time helpers и явное nullable/invalid fallback. Данные не синтезируются из дат уроков.

Дополнительно просмотрен numeric range correction: input хранит DOM `.value` как string вместо Vue type=number v-model coercion; существующий parser получает предусмотренный string draft. Scope этой коррекции не расширен на остальной stats backend.

## Проверки и evidence

Собственная проверка: `git diff --check ecdc2cec1 210c98202 -- frontends/web-vue/src/features/teacher` — exit0. Exact revision/clean tracked status подтверждены. Новых mechanical/runtime запусков не выполнялось: авторские проверки адресные, новые findings следуют из простой синхронной связи полей/handlers.

Прочитаны отдельные авторские runtime receipts:

- `/tmp/teacher-review-pagination-1.log`: реальные30→41 automatic, append failure оставляет30, explicit retry возвращает41, late page discarded при смене types, fallback без observer, runtime errors[].
- `/tmp/teacher-review-drill-3.log`: оба timestamps, реальная populated past statistics,8students с denominator1, numeric range server filtering, keyboard groupB→group4/subject4 drill и populated students, return со state/focus, runtime errors[].
- `/tmp/teacher-review-final-build.log`: vue-tsc+Vite build exit0; обычное предупреждение chunk>500KB не принято за failure. Авторский lint PASS передан root отдельно. Старые4unit не повторялись без нового риска.

Эти receipts — внешнее evidence автора, не собственный browser/backend PASS reviewer. Они закрывают прежний pending именно populated-past statistics gate в объёме указанных сценариев; прежний future-only zero-data receipt не выдаётся за новую populated проверку. Новая ошибка drillB→manual groupA этими receipts не проверена.

Нужен один минимальный correction и narrow recheck отображаемого контекста/drill return. Повторный fullsource review исходного21file пакета не требуется.
