# PR3 admin/visual publication — independent staged review

Вердикт: **нужны corrections документации**: три P2 ниже. Product/visual77f не пересматривался. Проверена публикация 61 staged docs/evidence/gallery файлов поверх `77fbd7a5e79bcaf3185f60e4ee2f18c7a3e3896b`: truth/status, safety опубликованных текстов и доступность ссылок. Это не runtime/visual повтор и не полная ревизия репозитория.

## P2-1 — заголовки реестра вопросов приписывают Q3 успешную квалификацию Q7 и переносят ACL pending на map

Места: `docs/plans/web-vue-pr3/OPEN-QUESTIONS.md:74`, `:289`, `:320`.

Q-ENV-003 помечен `APPROVED, actual positive UI +SQL+Auth qualification COMPLETED`, хотя текущие Q3 evidence и заключительный раздел этого же файла фиксируют три committed credential commands, native receipt UI FAIL, verified cleanup и незакрытое пользовательское поведение. Это формулировка map qualification. Q-ENV-007, напротив, получил `effective-access check pending`, хотя это оставшаяся проверка LIMITED ACL/Q8; map принят после отдельной Auth/PG qualification. У Q8 заголовок `actual case pending` не различает выполненный original mutation-case и оставшийся effective-access-case.

Влияние: реестр даёт противоположные статусы при сканировании заголовков и допускает неверное закрытие Q3 либо повтор уже выполненного map case. Нижний правильный narrative не устраняет противоречие в основном status field. Исправить статусы соответственно: Q3 partial native FAIL / cleanup verified / source diagnosis; Q7 completed qualified acceptance; Q8 mutation+SQL completed / effective access pending. Сохранить approvals и исходные raw failures.

## P2-2 — action map утверждает, что own8 credentials/rights ещё не менялись

Место: `docs/plans/web-vue-pr3/SCREEN-ACTION-MAP.csv:121`, action `ACT-ADMIN-REGENERATE-PASSWORD`.

Новая staged заметка говорит `real native/privatecleanup pending. No user8rights/credential changes run yet`. При этом `password-known-partial.json`, PROGRESS, ROLE-ACCEPTANCE и CURRENT фиксируют уже выполненные три команды `2→3→4→5`, временное назначение/отзыв FULL, finalprofile4 и независимый19/19 cleanup postguard. Полный action PASS правильно не добавлен; неверна именно отправная точка дальнейшей работы.

Влияние: каноническая action map может направить следующий пакет на повтор security mutations вместо причинной диагностики уже известного частичного исхода. Заменить заметку фактическим partial FAIL/cleanup verified/source diagnosis и ссылкой на новый receipt; не менять знаменатель и статус `implemented`.

## P2-3 — новая evidence ссылка принятого map action ведёт в отсутствующий checkpoint

Место: `docs/plans/web-vue-pr3/SCREEN-ACTION-MAP.csv:114`, action `ACT-ADMIN-MAP-DELETE`.

Добавлен путь `docs/validation/web-vue-pr3/checkpoints/map-runtime-20261024/manifest.json`, которого нет ни в staged inventory, ни в checkout. Действительная новая qualified acceptance опубликована как `docs/validation/web-vue-pr3/checkpoints/admin-visual-20261024/map-local-acceptance.json`.

Влияние: newly accepted behavior action не позволяет перейти по новой canonical evidence reference и проверить qualified native1/guest403 boundary. Исправить ссылку на существующий receipt; повтор runtime не требуется.

## Остальная проверенная граница

- Числа согласованы: implemented116/117, behavior114/117, ADMIN33/31 of34; group final source в отдельных worktrees не повышает numerator до117. Actions/behavior/visual/Angular delivery разделены. Old portable204 явно назван snapshot при общем index220; staged PNG действительно16 (before8 + map1 + after7).
- Новые summaries сохраняют raw map helper native1/passedfalse/guest403, отдельную source qualification и postguard; успешное удаление не выдано за повторный Auth test. ACL mutations/state отделены от непройденного target effective access. Q3 receipt сохраняет partial FAIL и оба19-check cleanup witnesses, не выдаёт known committed commands за полный UI PASS.
- Visual receipts/галереи явно маркируют synthetic GET/focus causes, narrow77f representative criteria, исходный cap3/loading failures, первый postfix failure и unconfirmed oldtoast7 logout. New all4 postfix cleanup не подменяет old cleanup gap. Existing screenshots лично повторно не просматривались: Root/независимый visual actor уже сделали visual inspection; hashes/metadata checks остаются у Root.
- Во всех staged текстовых `.md/.csv/.json/.html` проверены patterns UUID/JWT/private key/long secret assignments и sensitive JSON key locations; фактических password/token/session IDs, operation UUIDs или private binding digests не найдено. Публичные version/boolean/evidence hashes и synthetic notification IDs этому не равны. Credential/private sources не открывались. Raw public error поля прочитаны в пределах опубликованных receipts; секретных call-log values не найдено.
- Relative Markdown/HTML ссылки staged файлов разрешаются в checkout. CSV missing checkpoint описан выше. Review reports сохраняют разграничение source PASS / compile PASS / runtime pending; CI PAUSED не объявлен PASS. Новых workflow dispatch/merge/deploy действий нет.
- Текущий packet Root уже сообщает r2 fixture-helper early failure. Staged CURRENT/SLOTS/API/metrics ещё описывают retry active/follows; при финализации текущего среза обновить их по уже имеющемуся результату, сохранив оба raw остановленных IT и отсутствие behavior verdict. Аналогично group action note ещё ссылается на старые clientffc/Vue38aa и compile pending вместо текущего v6/ae546/f67. Эти устаревшие snapshots не превращены здесь в отдельный blocker сверх findings выше: они не заявляют behavior PASS.

Reviewer не запускал браузер, Gradle, тесты, HTTP/БД/Docker, не читал private runtime handles, не менял index/product/docs автора. Создан только этот отчёт. Gitleaks и окончательные hash/metadata/JSON проверки не подменены этим source/publication review; один финальный запуск остаётся у Root по packet.
