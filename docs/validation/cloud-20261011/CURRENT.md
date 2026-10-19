# CURRENT — отдельные Auth/Academic БД, отчёты и приложения

Goal Q031/D037 **IN_PROGRESS**, без дедлайна: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md),
[TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md), [ASYNC](TASK-ADDENDUM-ASYNC-20261017.md).
Ветка codex/server-validation-20261011; [draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2).
Starting26cbe5912f6683a81780a831609dbddbc26feba4; starting опубликованный HEAD/remote/PR:
**2aa7d648d8231d8a855330d62b37673c45a84059**. Есть принадлежащий задаче WIP; без main/merge/deploy.

## Конечный план

[Матрица v3 FROZEN](selected-results-20261018-v3.csv)/[веса](selected-results-20261018-v3.meta.json):
**30/39, вес146/196=74.49% плана**, 28 DONE_CHANGED +2 ALREADY_SATISFIED; это не production readiness.
Критерии/веса/знаменатель сохранены. [v1](selected-results-20261018.csv)23/25,110/123,
[v2](selected-results-20261018-v2.csv)24/38,114/188 и прежние TASK17 9/9 сохранены в своей применимости.

| КТ | Закрыто / всего | Вес / всего | % плана |
| --- | --- | --- | --- |
| КТ2: границы/AAA/контракты/сохранность | 11/14 | 50/71 | 70.42 |
| КТ3: бот/приложения | 6/9 | 26/37 | 70.27 |
| КТ4: изменённые цепочки/мониторинг/задания | 13/15 | 70/83 | 84.34 |
| КТ5: итоговые gates/review/handoff | 0/1 | 0/5 | 0 |

| Открытый ID | Конкретный остаток / следующий шаг |
| --- | --- |
| U14.3 | Реальный конкурентный HEADMAN: проигравший 202 вместо409. Auth pending-reservation/effective RECONCILE correction принята независимым review; проверить реальные33 Auth cases и текущий Academic CAS. |
| U14.4 | Два18weekDOCX/lost202/reconnect/currentrights и short3week уже PASS. Исправлен FULL registry guard; нужны реальные FULL/LIMITED/revoke результаты после исправления. |
| U14.5 | Прежний samearchive/DLQ/replay PASS сохранён. Subject deletion падает на BEFORE-precondition; четыре неизменных условия разделены для точной причины. Semester promotion fixture переведён на два владельца БД; нужны реальные результаты. |
| U14.6 | Изменённые clients/current-grants/UI зависят U14.3/.4/.5. OpenAPI gate2aa SUCCESS; Gateway probe исправлен под Academic export owner. Остались названные реальные браузерные пути. |
| U14.7 | Q033 CLI FRESH2/AUTH_PREPARE/CONFIG. Исправлен synthetic config owner/umask: nonroot UID:GID,0700/0600,readonly. Нужны firstcreate/retry/refusal/login; core DB proofs сохранены. |
| U20.1 | Hosted body теперь выполняется, но Python child nonzero. Закрытая диагностика существующего phase добавлена; установить причину и подтвердить точное приватное меню/current binding. |
| U20.2 | Подтвердить настоящие current-lesson roster/statistics/none/revoke через hosted bot; неотмеченный не считается отсутствующим. Зависит U20.1. |
| U20.3 | Подтвердить собственные Teacher Today/empty/current rights/mixed roles через hosted bot; teacher-only/admin-only без student HW. Зависит U20.1. |
| U25 | Финальные совместимые CI/Coverage/review. Restore Academic inventory FAIL: конечный hint подготовлен, точная причина UNKNOWN. Apps PR RSS FAIL сохраняет64MiB gate; новые observations не снимают его. |

## Текущий пакет

2aa pushCI39176690016/PRCI39176694718/Coverage39176694797 **FAIL**;
Security39176694722/OpenAPI39176694732/Monitoring/Python SUCCESS; все полезные публикации и cleanup завершены.
Academic518PASS/3FAIL/1SKIP; Coverage повторяет те же3 FAIL, unit662PASS, diffreportSKIPPED.
Открытые Academic случаи: Pythonbridge, registry render421, HEADMAN race202; это не процент покрытия.
Auth205PASS,31store cases PASS. History/archive/headman fixtures исправлены и реально прошли.
2aa async browser2DOCX PASS12.431s; четыре metric deltas2,8targetsUP/426panelqueries приняты.
Apps pushPASS/PRRSSdelta82472960B>64MiB FAIL на одинаковом source tree; exclusive cause UNKNOWN.

Source WIP: FULL guard APPROVE; Auth reservation/recovery whole+P1 addressed APPROVE;
Q033 fixture и ограниченные диагностики/owner fixtures прошли Root routine review.
Precommit13 source PASS1.59s/no drift. Native37 PASS64.95s; **Native38 PASS76.42s**:
Auth main/test + Academic bootstrap test, exact3Java /tmp/rct-s19-native38-inputs.json.
Следующий шаг: один normalpush этого проверенного source checkpoint и обычный CI; source-only строки не закрывать.

[Owners/resources](resources.md), [protocol](BOUNDARY-PROTOCOL-20261018.md), [decisions](decisions.md),
[questions](questions.md); история один раз в [attempts](attempts.ndjson), точный WIP в ignored current-wip/latest-checkpoint.safe.json.
Local Docker/DB/pulls NOT_RUN: workspace ниже4GiB Dockerreserve/VFSsameFS. Без cleanup/reset/prune; foreign25 сохранены.
Вопросов владельцу нет. **B013/B014/B015 FROZEN**. DEFERRED_OWNER/LOCAL: реальные Telegram/push/install/Safari,
alertbot/offhost probe/owner stand/deploy/backup/VPS/multiworker; это не PASS.
