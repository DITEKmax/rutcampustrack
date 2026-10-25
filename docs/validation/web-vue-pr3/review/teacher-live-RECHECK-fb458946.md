# Teacher WEB Journal F1 — independent source recheck: PASS

Exact revision `fb45894662db261cc1e1935bd11e77be136c5beb`, parent `58e7314f64f86cd366042027438026f697cc040c`, worktree `/workspace/rct-worktrees/teacher-web`. **F1/P2 из [исходного review](REVIEW-58e7314f.md) закрыт. Других существенных findings в correction/influence scope не обнаружено.**

Прочитан весь трёхфайловый diff и связанные resource/transport/query/merge paths. Вместо замены ранее открытых60 пар первой страницей background load вызывает `refreshWindow`: privately читает страницы0…последняя открытая, объединяет существующим `mergeJournalPage`, затем единственным завершённым ответом публикует результат через прежний `journal.load`. Старый DOM остаётся до завершения всего окна. Keyed lesson nodes для сохранившихся ID могут сохранять фокус; реальное исчезновение разрешённой пары из нового серверного результата не маскируется.

## Связанные границы

- Cursor: completed union несёт page/hasMore последней полученной страницы, поэтому после обновления60 из90 следующая append остаётся page2. При исчерпании нового списка цикл заканчивается раньше. Page index, totalLessons между страницами, отсутствие прогресса и итоговый полный count проверяются; несогласованный ответ не публикует частичное окно. Server snapshot protocol этим не добавляется: используется прежний paginated read contract.
- Concurrent append: начало refresh увеличивает pagination revision, останавливает старый append и временно запрещает manual/IntersectionObserver append. Старый response не может приписаться к новому окну. Refreshing участвует в observer lifecycle; старый observer callback защищён существующей проверкой его identity.
- Lifetime/privacy: перед и после каждой страницы проверяются alive/current owner, pagination revision и identity applied-query ref; transport дополнительно проверяет owner. После завершения resource проверяет собственную revision/current owner перед публикацией. Явный apply/scope change сбрасывает applied query и отменяет старую continuation; новая identity не получает старый union и не запускает его следующую страницу.
- Failure: частично собранные страницы остаются локальными. При transient failure сохраняется прежняя admitted projection и виден resource error; append заблокирован до window reload. Existing retry ведёт через scope reload. Existing typed401/403/404 resource retirement очищает data, а catalog loss также очищает applied query. Correction не добавляет новый допуск или автоматический replay command.
- Draft/export: refresh использует сохранённый applied query, включая applied dates/pastOnly; dirty fields не становятся параметрами background request. Он не сбрасывает loadedQuery/ticket и не включает foreground loading, поэтому прежний export selector/request identity не снимается только из-за refresh. Export/report port и unknown-command behavior не менялись.

## Evidence и пределы

Новый mounted regression открывает60 из90, удерживает page1 refresh и фокус на реальной keyed ссылке второй страницы: проверяет прежние60 nodes во время ожидания, тот же focused node после completed refresh, обновлённые данные и последующий append до90. Existing dirty-date Journal case сохранён. Это причинная проверка исходного F1, а не только первой страницы. Author сообщил baseline58 FAIL на disconnected focused link и финальные **2 Journal PASS**, strict WEB TypeScript/scoped lint/diff PASS; остальные7 ранее принятых mounted cases переиспользованы. Указанный в старом review `.agent/evidence/teacher-live-focus/20261024` отсутствует в текущем WT, поэтому статусы здесь квалифицированы как author-reported, без независимого повторного запуска.

HEAD совпал с reviewed commit; product inventory ровно `TeacherJournalPage.vue`, `use-journal-pagination.ts`, `teacher-live-focus.test.ts`. Catalog/Stats/export/focus остальной source58 не менялся и полный review переиспользован. Reviewer не запускал tests/browser/API/runtime и не читал private values; product edits отсутствуют. Actual natural live/WS и after visuals этим source recheck не квалифицированы.

Requested model: gpt-6.1-sol/high; served-model telemetry недоступна.

## Фактическое уточнение evidence

Предыдущая фраза об отсутствии evidence path ошибочна: search result не доказывал отсутствие каталога. При адресном чтении каталог существует; `scoped-final.log` содержит native4files/14PASS (21:58:14,2.74s), `f1/journal-final.log` — native2PASS/7skipped (22:08:36,1.98s). Эти существующие логи прочитаны без повторного запуска. Strict/lint logs пусты, author exit0 qualification сохраняется. Root отдельно сообщил independent all-three integrated build/typecheck EXIT0 наd69 и byte equality11 files с принятыми author finals. Verdict и source scope не изменены.
