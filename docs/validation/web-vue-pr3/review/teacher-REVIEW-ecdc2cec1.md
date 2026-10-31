# Независимое review TEACHER8 — ecdc2cec1

**CHANGES REQUIRED: 3 × P2. P1 не обнаружено.**

Scope: только `frontends/web-vue/src/features/teacher/**`, полный diff `2863ee7f6..ecdc2cec147461529c1f4ade120d2e874d78701a`, 21 файл. Checkout `/workspace/rct-worktrees/teacher-web` на момент review имеет этот exact HEAD; tracked source clean. Shared transport/session/teacher API/report/attachment зависимости прочитаны только для проверки контрактов, вне inventory findings. Reviewer не менял продуктовые файлы, зависимости, серверы, Git refs, fixtures или backend; без children/heavy/CI/новых Figma запросов. Создан только этот отдельный immutable report.

Запрошенная модель/effort — gpt-6.1-sol/high. Независимого runtime metadata API нет; фактические runtime model/effort reviewer не утверждает.

## P2 — журнал требует ручного перехода вместо принятой автоподгрузки

Место: `/workspace/rct-worktrees/teacher-web/frontends/web-vue/src/features/teacher/TeacherJournalPage.vue:282` (запрос первой страницы: строка 49, `more`: строки 69–76).

Журнал запрашивает только первые 30 пар. Единственный вызов `more()` находится на кнопке «Ещё пары»; scroll/intersection/иной автоматической подгрузки в компоненте нет. При `hasMore=true` горизонтальная прокрутка к концу уже загруженного журнала не приводит к получению следующих столбцов. Для полного семестра преподаватель обязан отдельно нажимать кнопку снова на каждую порцию.

Это конкретное расхождение с текущим `OWNER-BRIEF.md` (таблицы/списки без лишних «следующая страница», данные общим потоком с автоподгрузкой) и `DESIGN-PREFERENCES-FROM-PWA-TMA.md`, §5: для посещаемости владелец выбрал автоподгрузку, web может порционно подгружать «без кнопок дальше», источник — teacher-attendance-pagination. Это не просьба загрузить весь семестр одним запросом и не замечание к стилю кнопки.

Воспроизведение по source: открыть разрешённый subject/group/type с более чем 30 уроками; первая страница приходит с `hasMore=true`. Прокрутить журнал до последнего столбца — API следующей страницы не вызывается до отдельного клика. Исправление: связать bounded следующую порцию с достижением края/сентинела, сохранив current-query ownership, merge и защиту от одновременной загрузки/смены фильтра.

## P2 — из сводки групп нельзя перейти к студентам выбранной строки

Место: `/workspace/rct-worktrees/teacher-web/frontends/web-vue/src/features/teacher/TeacherStatsTable.vue:199`; связанная композиция — `TeacherStatsPage.vue:79`.

Во всех строках сводки имя группы выводится простым текстом `<th>{{ row.name }}</th>`. У строки/имени нет ссылки, кнопки или события выбора группы. Публичные emits таблицы содержат только sort/filter/reset. Переключатель «По студентам группы» вызывает `changeScope('students')`, но оставляет прежний `selection.groupId`, то есть не выбирает группу, увиденную в cross-group summary.

JS-TEACHER-14 явно требует найти проблемную группу и «провалиться в неё до конкретных студентов». При сводке A/B и ранее выбранной A преподаватель не может открыть студентов B из её строки; переключение scope снова открывает A, затем требуется вручную искать B в отдельном dropdown. Последняя часть принятого сценария не реализована.

Воспроизведение по source: текущая students selection=A; переключить на groups, получить rows A/B. В B нет доступного действия drilldown. Нажать students scope — query остаётся для A. Исправление: добавить доступное действие выбранной group row, которое переключит students scope, установит разрешённую группу и согласованный assigned subject/type, затем загрузит её серверную сводку; не агрегировать чужие предметы на клиенте.

## P2 — карточка заявки скрывает предусмотренные даты подачи и решения

Место: `/workspace/rct-worktrees/teacher-web/frontends/web-vue/src/features/teacher/TeacherExcusePanel.vue:76`.

Панель выводит студента, причину/комментарий, статус, имя принявшего решение и его комментарий, но вообще не использует `createdAt` и `decisionAt`. Оба поля доступны в существующем `TeacherExcuseResponse` и сохраняются normalizer. JS-TEACHER-12 требует в карточке дату подачи и дату подтверждения старостой. Даже для APPROVED ответа с обеими датами преподаватель не видит, когда заявку подали и когда приняли решение; по существующему UI этот контекст получить нельзя.

Воспроизведение по source: открыть разрешённую approved заявку, чей API ответ содержит non-null `createdAt`/`decisionAt`. Карточка не отображает ни одно значение, независимо от того, есть ли вложения. Исправление: показать обе доступные даты с локализованным временем и понятным nullable состоянием; существующий read-only/partial-scope API достаточен, расширять права или endpoint не требуется.

## Остальной просмотренный риск

- У всех page instances собственный `createWebTransport`; captured authority identity включает session/roles versions, active role/readOnly. Guard стоит перед/после HTTP и после decode; `useTeacherResource` проверяет current identity и latest revision перед публикацией. Scope invalidation очищает catalog; страницы сбрасывают соответствующие projections. Source дефекта выдачи foreign-subject данных не найдено; серверная граница не заменена client filtering.
- Teacher операции read/export-only. Catalog/subjects/types строятся из серверного разрешённого scope; group AND subject применяется API. Foreign lesson не загружается через общий roster endpoint.
- Future unmarked и canonical effective absence различены до общего `journalMark`; autoAbsent/explicit absence остаются `н`, отсутствие будущей записи — `—`. Action accessible name включает статус/pending request.
- Journal pagination отправляет snapshot `loadedQuery`, последующие страницы сохраняют его контекст, merge сохраняет исходные metrics. Export selector содержит полный загруженный filter context, без ограничивающих page/pageSize. Stats selector содержит sorts/filters/types/scope, а не только видимые строки.
- Export wrapper сохраняет только requestId/expiry по exact authority + whole report selector, повторный create после потерянного ответа использует прежний requestId даже после remount. Другая admitted identity имеет другой storage key; bearer/ticket не сохраняются. Late authority guards стоят и перед issuing ticket, и перед чтением/download body; capability GET выполняется отдельным bare fetch без bearer. Нового P1/P2 в этих границах не найдено.
- Attachment bytes загружаются teacher-authorized API; preview проверяет тип/signature, текущего владельца и срок, владеет Blob URL и освобождает его. Native PDF preview/open/download используется вместо screenshot-макета. Expired/foreign-subject backend policy для fixture не изменялась и не оспаривается.
- ecdc2cec1 focused delta ограничивает deferred focus restoration применённым filter. TeacherRegion удерживает live refresh при pointer/focus/dirty и отпускает при реальном выходе фокуса. Дополнительного source finding здесь не найдено.

## Проверки, evidence и ограничения

Прочитаны AGENTS/frontends/tests/RULES и актуальные goal/owner/action-map contracts; применены применимые инструкции сохранённых rct-source-resolution/rct-verification, текущие решения владельца выше старых ограничений. Прочитаны все 21 teacher source files, четыре meaningful unit cases и referenced shared dependencies. Figma extraction/PNG/SVG provenance повторно не выполнялись; source `document.svg` включён в inventory.

Собственная адресная механическая проверка: `git diff --check 2863ee7f6 ecdc2cec1 -- frontends/web-vue/src/features/teacher` — exit 0. Tracked status clean, exact HEAD подтверждён. Новых unit/browser/full-suite запусков нет: обнаруженные gaps однозначно следуют из стабильного source и принятых критериев, авторские адресные механические проверки уже переданы. Root получает воспроизводимые causal cases, не пожелание написать зеркальные тесты.

Внешнее evidence `/tmp/teacher-web-evidence/CHECKPOINT.json`, summary/followup/marks summaries прочитано как **авторское evidence**, не как собственный runtime PASS reviewer. Оно заявляет реальные восемь positive flows/PDF, same-group foreign-subject403/no roster, abort GET/retry и lost create acceptance с same-key recovery/no-bearer capability. Source исправленных marks соответствует 341710643; ecdc добавляет только focus flags. Авторские unit4, types/build/lint PASS не приписаны reviewer.

64 width/theme samples — layout samples, не 64 независимых E2E. Только восемь `final-*` corrected journal/lesson captures отмечены стабильными source341 defaults; прочие PNG могут быть provisional/busy/historical и не служат текущим default proof. Скриншоты reviewer самостоятельно не воспроизводил.

**Populated past-statistics fixture остаётся pending.** Подтверждённый автором future-only zero-data response (`lessonsCount=0`, без nonzero denominator) валиден, но не проверяет заполненные group/student summaries, numeric ranges и drilldown на настоящих прошлых данных. Нельзя объявить все TEACHER8 locally verified этим review. Reviewer не запускал backend mutations, чужие sessions, heavy checks, CI или серверы; dev14240 не менял.

Нужны исправления трёх P2 тем же sole author и narrow independent recheck изменённых мест. Повторное полное review всех 21 файлов без изменения архитектуры не требуется.
