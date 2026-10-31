# Независимое bounded review headman124334c54

Результат: **нужны corrections: четыре подтверждённых P2**.

Scope: frozen `072edf183..124334c54` (commits07c201c8f +124334c54), journal/requests/statistics/student detail/export и связанный context/routes/controls. Источники читались через gitshow. Home/geo/явно незавершённые download actions не принимались; общий registry/live bus — известный отдельный WIP, не finding этого review. Reviewer read-only, без продуктовых правок/commits/push/Gradle/Docker/install/CI/heavy/детей. Запрошенная модельgpt-6.1-sol/high, фактическая runtime telemetry недоступна.

## Findings одним пакетом

1. **P2 — редактор меняет существующую уважительную причину при обычном сохранении.** `frontends/web-vue/src/features/headman/JournalPage.vue:150–152` (command:191–197). choose переносит status/comment, но не entry.excuseType; draft.excuseType остаётся initial ILLNESS либо предыдущим выбором другой карточки. Existing HeadmanJournalRosterEntry содержит nullable excuseType, а HeadmanJournalMarkCommand для EXCUSED явно передаёт выбранную причину. Repro: открыть existing EXCUSED/OTHER и сохранить, например после изменения комментария → отправляется EXCUSED/ILLNESS. Это незапрошенное изменение данных посещаемости. Переносить текущую допустимую причину в draft; неподдерживаемую причину нельзя молча подменять болезнью.

2. **P2 — unknown EXCUSED mutation считается согласованной только по статусу, теряя изменения payload.** `frontends/web-vue/src/features/headman/JournalPage.vue:210–218`. После network failure/409 код читает текущую запись, снимает uncertainty и скрывает ошибку, если entry.status совпадает с draft.status. EXCUSED command также содержит excuseType/comment/file; старое либо чужое EXCUSED состояние не подтверждает эти поля. Repro: existing EXCUSED/comment=old/revision1 → отправить EXCUSED/comment=new → запрос не применён, ответ потерян → readback возвращает old EXCUSED/revision1. Код оставляет draft.comment=new, selected.entry.comment=old, uncertain=false, error=null. Пользователь не получает предупреждения, хотя его изменение не подтверждено. Заморозить полный intent до отправки и проверять действительно подтверждённые данные/версию; если attachment intent нельзя доказать текущим контрактом, явно сохранять неопределённость вместо status-only подтверждения. Blind replay не требуется.

3. **P2 — поздняя audit page смешивает историю разных пар.** `frontends/web-vue/src/features/headman/JournalPage.vue:226–232`. loadAudit не связывает continuation с открытой парой/cursor/generation запроса, а more добавляет result.items к текущему audit массиву. Repro: открыта историяA → запросить «Ещё» с cursorA и задержать ответ → открыть историюB, получить Bfirst → приходит oldA2. Получается headingA и audit=[Bfirst,Asecond], cursorAend. Существующий HeadmanLessonActionsApi корректно возвращает страницу конкретного lessonId; смешение в новом UI представляет действия другой пары как историюA. Нужны owner/revision для audit selection и append только к той же паре/странице; concurrent duplicate page не должна дублировать rows.

4. **P2 — terminal/expired export намертво удерживает единственный intent.** `frontends/web-vue/src/features/headman/ReportExport.vue:75–90,101–119` (UI:130–140). После получения jobId все последующие попытки принудительно превращаются в GET того же job; intent очищается только после успешного download. Existing ReportDownloadClient явно завершает job.state=error ошибкой и возвращает410 для истёкшего job, но новый wrapper не завершает/не позволяет явно удалить такой intent. Repro: job generation закончилось error либо сохранённый job истёк → нажать retry/выбрать другой report → снова читается старый failed job; создать новую выгрузку нельзя даже после reload, потому что intent сохранён в sessionStorage. Отличать подтверждённый terminal failure/expiry от неизвестного исхода и разрешать явное завершение старой попытки/новый intent; неизвестный POST по-прежнему должен сохранять свой исходный requestId.

## Независимое causal evidence

Дешёвый однократный Node/V8+установленный TypeScript harness исполнил script setup исходников ровно124334c54 с минимальными Vue/transport stubs. Он не менял файлы продукта и не подменяет real browser/backend проверку. Результаты:

- existing reason OTHER → draft ILLNESS → sent command ILLNESS.
- unknown EXCUSED update: storedComment=old, draftComment=new, uncertain=false, error=null.
- late audit page: lesson=1, items=[Bfirst,Asecond], cursor=Aend.
- terminal export retry: POST /api/auth/export-jobs затем GET /api/auth/export-jobs/job-1; intentJob=job-1, persisted intent остаётся. Terminal behavior существующего ReportDownloadClient отдельно прочитан в source; harness stub воспроизводил этот контрактный failure boundary.

`git diff --check 072edf183..124334c54` — PASS. Авторские typed/build PASS заново не запускались; root организует runtime/renderer после corrections/source gate.

## Рассмотренные инварианты без новых findings

- Context получает assistant capabilities из защищённого `/api/academic/assistants/me/permissions`, group scope из selected active grant; writable учитывает readOnly, запросы выполняют существующие server-authorized API. Client guards не объявляются заменой backend admission.
- createWebTransport связывает запросы с identity, а context.assertCurrent дополнительно с mounted lifetime; существующие clients/страницы проверяют current после async decoding. Auth switch не должен переводить старую команду на новый bearer. Shared registry/live bus отсутствием unknown blocker не названы.
- Requests используют существующие freeze/send/reconcile helpers: decisionRevision/current occurrence revisions, stable requestKey, frozen exclusion selection и readback без blind repeat. Поля rejected reason и partial approval проходят существующую domain validation.
- Request attachments скачиваются через авторизованный requestId/attachmentId endpoint, а не server-provided arbitrary downloadUrl; Blob сохраняется только после current guard. Download capabilities не сохраняются в report intent. Browser/runtime secrecy end-to-end этим source review не объявлена проверенной.
- Stats показывают server numerator/denominator; нулевой denominator и nullable trend/detail percent представлены как отсутствие данных, а не выдуманный ноль. Chart имеет focus/Enter/Space points и tabular data alternative; экспорт строится по применённому query. Layout/visual полнота и реальные операции ещё не приняты этим source review.

Все четыре findings переданы root одним пакетом для coordination с sole writer. Нужна независимая узкая recheck стабильных corrections и затронутого поведения; accepted role counts пока не повышаются.
