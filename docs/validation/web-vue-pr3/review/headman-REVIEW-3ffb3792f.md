# Independent headman source review — 3ffb3792f

**CHANGES REQUIRED: 3 новых P2; P1 не найдено.** Пять findings из REVIEW-a6b16f3bd устранены на уровне consumer/source, с отдельным незакрытым runtime gate helper CAS. Четыре findings124/f0 повторно не открывались.

## Scope и метод

Frozen author checkout `/workspace/rct-worktrees/headman-web`, exact HEAD `3ffb3792f8e6708f68d6bf2e082b9b5d07051737`, tracked source clean. Рассмотрен новый headman source после a6: subjects/assignments/deletion, recurring schedule, physical lesson lifecycle, Home, affected homework/group/requests/statistics/context/routes/styles, и адресные shared next-occurrence/helper-consumer deltas. Исходные immutable commits1f9/f108/e94/ab6/16b/18f/ba385/02cf/3ff доступны через Git. Closed f0 baseline не переоткрывался; прежние reports прочитаны для границ recheck.

Прочитаны `/tmp/rct-headman/HANDOFF-3ffb3792f.md` и bounded `*-results.json`, без private credentials/tokens. Три адресных causal harness исполнили точные frozen SFC script setup через установленный TypeScript transpiler и реальные Vue refs/computed, с минимальными API stubs. Для replacement preflight использован **реальный HeadmanSubjectsApi**, для decision — реальные freeze/reconcile/restore helpers. Harness не меняли продукт и не отправляли backend/browser запросы; результаты ниже не являются runtime acceptance.

Без source edits/commits/push/install/Figma/browser/Gradle/Docker/CI/children; только этот новый immutable report. Запрошенная модель/effort gpt-6.1-sol/high; независимой runtime telemetry нет, фактическая модель не утверждается.

## P2 — поздний ответ старого семестра заменяет текущую сетку расписания

Место: `/workspace/rct-worktrees/headman-web/frontends/web-vue/src/features/headman/SchedulePage.vue:108` (load:84–115, read:79–82).

`load()` не имеет request revision и не связывает полученные items/assignments с захваченным semesterId. После await проверяется только authority. Semester select и кнопка «Показать» не отключены loading, поэтому пользователь может запустить новый запрос B, пока предыдущий A ещё выполняется. Если A завершается после B, его rows/assignments безусловно публикуются под уже выбранным B. Здесь session guard не заменяет latest-query ownership.

Воспроизведение: загрузить archive semester1, задержать его response → выбрать active semester2 и загрузить его → завершить semester2 первым, затем semester1. Causal harness получил `selectedSemester=2`, `publishedRows=[{id:11,semesterId:1}]`, `publishedAssignments=[{id:1,semesterId:1}]`. Пользователь читает/выбирает регулярные слоты старого семестра под новым контекстом; writable при этом вычисляется из semester2.

Исправление: захватить semester/query и revision до первого await, публиковать rows/assignments/pending/loading/error только для актуального run; тот же owner нужен вспомогательному read. Достаточна адресная A-slow/B-fast проверка, без полного suite.

## P2 — недошедшее решение заявки навсегда блокирует страницу после reload

Место: `/workspace/rct-worktrees/headman-web/frontends/web-vue/src/features/headman/RequestsPage.vue:188` (open guard:115; единственный pending control:230–238).

При unknown POST frozen decision сохраняется в sessionStorage. Единственное продолжение `reconcile()` делает GET; helper возвращает null, пока заявка PENDING. Эта ветка не очищает/не завершает pending и не предлагает продолжить **исходную** keyed команду. При этом open любой другой заявки запрещён, если pending существует. После reload lock восстанавливается, но detail пуст; доступна только бесконечная GET-проверка.

Воспроизведение: EXCUSE решение с сохранённым requestKey; POST обрывается **до admission**, поэтому сервер продолжает возвращать PENDING. Нажать «Проверить результат», reload, проверить ещё раз, попробовать открыть другую заявку. Exact-source harness: writes1, reads3, `pendingAfterReload=true`, persistedEntries1, openOtherDetail=null, ошибка «Решение ещё не подтверждено…». Исходная команда сервером не выполнялась и сама больше никогда не будет отправлена; дальнейшие GET не могут превратить её в terminal receipt.

Нужна разрешимая recovery ветка: после актуального readback дать явное безопасное продолжение того же EXCUSE requestKey/expectedRevision/payload под текущим writable owner либо другой подтверждаемый протокол завершения. **Не** удалять неизвестный intent молча, не выдавать новый key и не добавлять blind automatic retry. Существующий keyed helper допускает явный same-command replay после чтения; GET-only проверка сама по себе не завершает недошедшую запись.

## P2 — невалидная дата замены преподавателя фиксирует вечный локальный pending

Место: `/workspace/rct-worktrees/headman-web/frontends/web-vue/src/features/headman/SubjectsPage.vue:317` (client invocation324, cleanup340–346; type=button control664–670).

Replacement сохраняется до typed-client validation. Кнопка замены type=button, поэтому HTML required у поля «С даты» не выполняет form validation. Очистить дату, выбрать замену и подтвердить — `HeadmanSubjectsApi.replaceAssignment()` синхронно отклоняет effectiveFrom до fetch. Это RangeError/local Error, не HeadmanSubjectsApiError с HTTP status, поэтому catch pending не очищает. Поля заблокированы через `locked=...replacement`; explicit recovery повторяет ту же невалидную input, отмены такой replacement UI не содержит. Persisted intent загружается после reload без восстановления редактируемого draft.

Causal exact-SFC harness с реальным typed API: fetches0, locked=true, pending=true, persistedEntries1; после «Проверить замену» тот же error «Сервер вернул пустое поле effectiveFrom». Это доказанный local preflight failure, а не неизвестный серверный исход.

Исправление: валидировать полный replacement payload до persistence/locked, включая дату и выбранное назначение, либо отдельно освобождать доказанно неотправленный local failure. Не менять идентичность действительно переданной неизвестной замены. Нужен scoped invalid-date→исправление→новая отправка flow, без зеркального full-suite тестирования.

## Recheck прежних пяти a6 findings

1. **Helper stale grant — исправлен consumer/source dependency.** Web передаёт `frozen.before` в updatePermissions и для empty after; unconditional revoke path заменён conditional PATCH. Client сериализует expectedPermissions, mobile compatibility сохраняет confirmed.before. При409 web сохраняет draft, перечитывает actual, требует нового явного подтверждения; автоматического повторного grant нет. Backend CAS рассматривается отдельным root review950; его deployment/реальная stale-write проверка не выполнены этим review и остаются runtime gate.
2. **Budget aggregate false ACK — FIXED.** Frozen student/semester/increase/requestKey сохраняется до POST; recovery сначала читает актуальный scope и затем использует исходный keyed increase для immutable receipt. Чужой total increase больше не считается доказательством своей команды. После receipt читается latest budget, не вычисляется fake ACK по сумме.
3. **Same-day homework occurrence — FIXED.** Новая selected occurrence считается placement edit даже при равной дате/типе; lessonNumber/selectedOccurrenceId/Revision входят в PUT через placement.
4. **False unknown-edit ACK — FIXED.** Изменение revision больше не принимается за собственный успех. Recovery выполняет исходный keyed PUT и проверяет canonical text + binding/date/type/number. Неподтверждённое изменение не закрывает editor как успешно выполненное.
5. **Whitespace title lock — FIXED.** canonicalHomeworkText вызывается до freeze/persistence; восстановление старого локально невалидного intent освобождает его. Title/description/link preflight отделён от неизвестного исхода переданной mutation.

Shared nextHomeworkLesson delta нормализует wire lessonType регистром и продолжает проверять group/subject/semester/occurrence identities; permissive произвольный type не добавлен. Прочитаны соответствующие regression tests, повторных запусков ради PASS не было.

## Остальные рассмотренные границы

- Captured page transport и feature alive guard защищают другую admitted identity/закрытую страницу. Не добавлены credentials в sessionStorage; password/code/URL находятся в volatile UI refs и очищаются существующим owner. Private fixture файлы не читались.
- Recurring mutations используют durable original intent, expected revision/key, explicit lifecycle preview и authoritative state/transfer readback; physical lessons используют существующий LessonManagementState. Geo unknown path перечитывает пару и не отправляет повторный block POST автоматически.
- Subject create/update/adoption/closure переиспользуют typed domain save flow, replacement сверяет полученный receipt tuple/group, deletion проверяет preview digest/subject/group/semester и operation identity. Новая preflight проблема replacement выделена выше, не смешана с валидным pending server operation.
- Request decisions требуют current affected occurrences/revision; self request read-only. Persisted decision восстанавливается в текущий API owner без credentials и не меняет requestKey/expectedRevision. Exact terminal receipt отличается от чужого решения. New finding касается unresolved not-admitted case, а не требования ослабить эту защиту.
- Home использует реальные current lesson/report/audit/counters, applied statistics/trend отделены от draft. History UI показывает semantic before/after. Source no fake calculated backend ACK не обнаружен помимо описанных конкретных gaps.
- Closed124/f0 OTHER reason, full EXCUSED payload proof, audit ownership и terminal export restart не переоткрывались; несовместимые изменения в затронутых deltas не обнаружены.

## Проверки, receipts и оставшиеся runtime gates

Собственное: три лёгких source-causality harness, каждый один запуск, все exit0 и воспроизвели указанный defect. Никаких backend mutations. `git diff --check a6b16f3bd 3ffb3792f -- frontends/web-vue/src/features/headman` — exit0; frozen revision/clean tracked status подтверждены. Existing author unit/build/typecheck не повторялись; это не новый mechanical PASS от reviewer.

Внешние bounded receipts подтверждают авторские реальные endpoints/bytes для homework binding/create, physical lesson create/room/cancel/restore/transfer, recurring create/update/remove, populated stats/trend/export, teacher assignment replacement, subject deletion and recovery-link UI. Их проверки принадлежат автору; один summary с outcome не переименован reviewer в собственную сквозную приёмку. Budget/mark causal proofs описаны handoff как tool output без JSON; не выдуман отсутствующий файл.

Не закрыты этим review: helper CAS deployment + stale expected-right409/no resurrection/last-right inactive/live revoke; положительное решение nonself request после проблемы student14first request401; positive HEADMAN cross-client live с draft preservation; subject-journal export branch; affected journal768 recapture и independent visual acceptance canonical gallery. Старые некорректно названные dark captures и historical/busy frames не приняты за актуальный visual PASS.

Все38 actions сохраняются в принятом denominator. Этот source report не объявляет их все locally verified и не повышает readiness counts. Автору нужны три scoped correction; root продолжает runtime gates независимо от исправлений.
