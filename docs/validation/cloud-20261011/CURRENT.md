# CURRENT — отдельные Auth/Academic БД, отчёты и приложения

Goal Q031/D037 **IN_PROGRESS**, без дедлайна: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md),
[TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md), [ASYNC](TASK-ADDENDUM-ASYNC-20261017.md).
Ветка codex/server-validation-20261011; [draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2).
Starting26cbe5912f6683a81780a831609dbddbc26feba4; последняя опубликованная/проверенная база
**e5c211195653ecb71cbffd786632a4fd3fe83ab3**. Local b0a2a9cf сохранён; связный increment готовится к одному normalpush; main/merge/deploy нет.

## Конечный план

[Матрица v3 FROZEN](selected-results-20261018-v3.csv)/[веса](selected-results-20261018-v3.meta.json):
**33/39, вес170/196=86.73% плана**, 31 DONE_CHANGED +2 ALREADY_SATISFIED; это не production readiness.
Критерии/веса/знаменатель неизменны. Исторические [v1](selected-results-20261018.csv)23/25,110/123,
[v2](selected-results-20261018-v2.csv)24/38,114/188 и TASK17 9/9 сохранены в своей применимости.

| КТ | Закрыто / всего | Вес / всего | % плана |
| --- | --- | --- | --- |
| КТ2: границы/AAA/контракты/сохранность | 13/14 | 66/71 | 92.96 |
| КТ3: бот/приложения | 6/9 | 26/37 | 70.27 |
| КТ4: изменённые цепочки/мониторинг/задания | 14/15 | 78/83 | 93.98 |
| КТ5: итоговые gates/review/handoff | 0/1 | 0/5 | 0 |

| Остаток | Доказано / конкретное недостающее действие |
| --- | --- |
| U14.5 | Samearchive/DLQ/replay/dedup/restoration и semesterUI PASS; subject BEFOREcontrolMarks1 теперь PASS, затем previewGET5854 FAIL/HTTPUNKNOWN. Exact previewGET marker готов; проверить preview/delete/participants с прежними proof/grants. |
| U14.6 | OpenAPI/current create-login/contracts и5auth-role-logout PASS; schedule56 теперь PASS. Admin helper430 и group ticket930/935 FAIL, причинаUNKNOWN; caller452/470/488 diagnostic готов. Доказать исходные2UI операции; alias Auth→Academic корректен. |
| U20.1 | Private common/menu иTeacherToday/empty дошли доPASS; hosted headmanphase FAIL f2403a80. Existingchecks0..60 теперь сохраняются безопасно. Отдельный NativeBridge malformedbinding0 fixture исправлен к exactINVALID_ARGUMENT; actual rerun нужен. |
| U20.2 | Own currentlesson/roster/unmarked/statistics source согласован; тотже hostedFAIL, конкретное условие покаUNKNOWN. Пройти исходные headman/currentrights/none/revoke, зависит U20.1; новый API/дизайн не нужен. |
| U20.3 | TeacherToday+empty PASS до headmanFAIL; поздние freshbinding/revoke/currentrights ещё не закончены. Пройти оставшуюся часть существующего hosted сценария после U20.1. |
| U25 | Финальные compatibleCI/Coverage/Security/OpenAPI/review/handoff. Currente5c CI/PR/Coverage FAIL, OpenAPI/Apps/Monitoring/Auth SUCCESS; source-only исправления ниже требуют обычного CI. AppRecovery seed SUBJECT_CREATE_HTTP503 до backup/restore, причинаUNKNOWN; exact503 finite problem-category готов, без retry создания. |

## Принятые текущие результаты / evidence

**U14.4 DONE_CHANGED** наe5c: registry12158013689/4c3a539b all6PASS — FULL/LIMITED/currentcapability403,
HTML/PDF/PNG, явный terminal при renderer outage, restoration и freshPDF. Existing2x18week/lost202/reconnect/short3week/D035 сохранены.
**U14.7 DONE_CHANGED** наe5c: standaloneCLI12158825618/ff648942 actualFRESH+REPEAT PASS;
Academic12158670472/25089ef9 exactsource5d255d02 core4PASS: firstlogin/profile/passwordchange, durable restart/unknownresult,
concurrency/existingadmin и runtimeDBboundarydenial. Q033 закрыт, production invocation не выполнялась.
U14.3 current grants/CAS/history принят8c5; его evidence/review и остальные закрытые строки — в матрице.
e5c exact restore12157058652/9f042ebf PASS:3PG/data/schema/sequence/owners/ACL/defaultprivileges/runtimegrants+deny/Mongo/files.
Application recovery12158801161/0924ae35 seedFAIL987ms POSTsubjects503; checkpoint/pending/backup/restore ещё не начинались.
Историческая причина WORD crash и8c5 fresh-restore stage остаются UNKNOWN; новые PASS их не объясняют.

## Следующий связный пакет / владельцы

Root интегрировал complete-history pinnedGitleaks8.24.3 с точным rule+path+type-line AND allowlist; все208default rules сохранены.
Старый hash-only fingerprint убран только из собственного неопубликованного increment. Synthetic scanner3expectedfindings/exit2 PASS; addressed independentAPPROVE.
Current-cache negative сохраняет DBsplit; независимый шаг выполняется после чужого FAIL только на clean source. Exactbinding0 INVALID_ARGUMENT fixture принят независимо.
FiniteSpring/BOT/preview/ADMINcaller и firstsubjectPOST503 observations используют прежние failure channels; assertions/identity/restoration/первичная ошибка сохранены.
Root — soleGit/docs/matrix/PR; writers/reviewers released, heavy lease свободен. Source checks приняты; final scoped hooks → commit → один normalpush → ordinaryCI.
Native39 compilePASS52.90s/sourceexact остаётся применим к последнему Java изменению; новых Java изменений в пакете нет.
LocalDocker/DB/pulls NOT_RUN: workspace ниже4GiB Dockerreserve/VFSsameFS; без cleanup/reset/prune, foreign25 сохранены.
[Resources/owners](resources.md), [protocol](BOUNDARY-PROTOCOL-20261018.md), [decisions](decisions.md), [questions](questions.md).
История один раз в [attempts](attempts.ndjson); exact checkpoint — ignored current-wip/latest-checkpoint.safe.json.
Appattach/list не завершились: новый outcomeUNKNOWN, прежнийPR URL сохранён; повторов нет. Вопросов владельцу нет.
**B013/B014/B015 FROZEN**. DEFERRED_OWNER/LOCAL: Telegram/push/install/Safari/alertbot/offhostprobe/ownerstand/deploy/backup/VPS/multiworker — не PASS.
