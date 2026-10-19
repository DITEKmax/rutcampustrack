# CURRENT — Auth/Academic, пользовательские операции и наблюдаемость

Persisted Goal Q031/D037 **IN_PROGRESS**, без дедлайна: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md), [TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md), [ASYNC](TASK-ADDENDUM-ASYNC-20261017.md).
Ветка `codex/server-validation-20261011`, [draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2). Starting `26cbe591`; published HEAD **855beb1b** (exact SHA — attempts/handoff/resume). Published8file checkpoint сохранён; новые Security/pipeline patches интегрированы точно, независимо APPROVE. Все9 candidate source файлов exact интегрированы, независимое APPROVE; Native50 compile/unit PASS. Foreign25 сохранены, index empty. Main не merged, deploy не выполнялся. App attachment SUCCESS; supplemental UNKNOWN-вызов не повторять.

[Матрица v3 FROZEN](selected-results-20261018-v3.csv) / [веса](selected-results-20261018-v3.meta.json): **37/39, 183/196 = 93.37% принятого плана**, 35 DONE_CHANGED +2 ALREADY_SATISFIED. Это не production readiness. Веса/знаменатель прежние; исторические v1 23/25, v2 24/38, TASK17 9/9 сохранены.

| КТ | Закрыто / всего | Вес / всего |
| --- | --- | --- |
| КТ2: границы/AAA/контракты/сохранность | 13/14 | 63/71 |
| КТ3: бот/приложения | 9/9 | 37/37 |
| КТ4: цепочки/мониторинг/задания | 15/15 | 83/83 |
| КТ5: итоговые gates/review/handoff | 0/1 | 0/5 |

| Остаток | Недостающее действие и достаточная проверка |
| --- | --- |
| U14.3 | Push855 D9 дошёл до EXACT_REPLAY: preview200/all3identity true; Attendance→Schedule.GetLessonById UNAVAILABLE до Mongo claim/receipt/ACK, ledger PENDING. Cleanup FAILED. PR855 тот же исходный D9 прошёл replay/duplicate/allchecks, ту же archive completion, restore и participants. CounterFAIL остаётся: точный transport/channel/server origin UNKNOWN. Existing v8 cause даст конечную безопасную причину; бизнес/RPC/retry/ACK/timeouts/assertions прежние. |
| U25 | Нужны совместимые final push/PR CI, Security и handoff; оба855 CLI/Academic SUCCESS. Coverage/OpenAPI/обе Apps/Monitoring SUCCESS. PR pipeline FAIL после PASS двух DOCX/4metric deltas: GATEWAY_OPENAPI_ACADEMIC_SERVICE до HTTPresponse, exact transport UNKNOWN. Узкий existing error_kind patch добавляет finite exact types, не запросы. Security ONE public Git CURRENT header finding: narrow typed-slot/ancestry/history-veto fix APPROVE, hosted gate ещё обязателен. |

**Текущие855 gates:** все terminal11:10:33UTC; push39237843240 и PR39237853362 FAILURE, не отменялись. PR lifecycle118011207299 terminal SUCCESS с D9/restore/participants/cleanup; push lifecycle FAIL, restore/participants SKIPPED по prerequisite, не отдельная restore regression. Coverage39237853330 и OpenAPI39237853305 terminal SUCCESS. Security39237853418 terminal FAILURE. Закрытые gates не опрашивать; oldddc PR permanently excluded после auto-review отказа, final oldCLI UNKNOWN.

**Первичные факты:** push12181141445/v7 EXACT_REPLAY/readiness200/3booleans true/PENDING; PR12181072669/v7 DUPLICATE/APPLIED/oneclaim/onereceipt/oneACK/allchecks true/cleanup restored. Оба actual receipt проходят exact strict reader без exception: reader masking не установлен, новый readerfix не нужен. Async12181231162: browser DOCX PASS, job meters PASS, последующий OpenAPI transport FAIL. Security12179629765: ONE public Git CURRENT finding; прежние4 отсутствуют. Canonical safe files и exact hashes один раз в [attempts](attempts.ndjson)/ignored resume; raw logs/SARIF values/PII не читать и не копировать. Историческая причина WORD crash UNKNOWN.

**Пакет / способ сокращения цикла:** Security CURRENT header patch cb21fb27 — 34public occurrences/166bounded blobs,27new+60retained controls, Cache APPROVE; default208rules/config/ignore/fullhistory+merge/wholevalue unknown+credential veto/18RED сохранены. Pipeline patch58a7dce1 — existing error_kind16exacttypes/55privacy controls/Cache APPROVE. Event6files fe16c00e v8 + CI1e830bc6 exact Root joined; Cache targeted APPROVE, Native50 PASS; previous v3–7/cap4096 сохранены. Actual gRPC1.82.4 typed causes/exact framework markers, без сырых description/cause/trailers. Готового short-D9/dispatch на current/defaultmain нет; block reorder требует отдельной fixture independence. Выбран один связный пакет + normal full CI, без новой обвязки/отдельного CI на каждое поле. Final обычный CI обязателен.

**Owners / next:** Root sole Git/taskdocs/matrix; Event — sole six-file v8 author; Report — sole CI reader writer; Cache — независимое targeted review; R2 frozen approved pipeline; Native50 PASS/released: Attendance compileJava/compileTestJava/test EXECUTED,2freshXML31PASS/source identities exact,106.66s/RSS1122582528/output108205241≤128MiB, allnewPIDsreaped; observer —855 metadata закрыт, ожидает nextpublishedruns. Heavy runtime0, Native50 lease RELEASED. No localDocker/DB/pulls/prune/reset при недоказанном reserve. Дальше — scoped hooks и один normal15file publish/full CI; следующие gates относятся к новому executable SHA. [Resources](resources.md).

[Handoff](HANDOFF-20261017.md), [protocol](BOUNDARY-PROTOCOL-20261018.md), [decisions](decisions.md), [questions](questions.md). Вопросов владельцу нет. Resume: `.agent/evidence/cloud-20261011/current-wip/latest-checkpoint.safe.json`.
**B013/B014/B015 FROZEN.** OWNER_MANUAL/DEFERRED_LOCAL: настоящий Telegram/push/install/Safari/owner stand, alertbot/offhost probe/deploy/backup/VPS/multiworker — не PASS. Обязательный env-blocker не исключать из знаменателя.
