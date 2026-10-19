# CURRENT — отдельные Auth/Academic БД, отчёты и приложения

Goal Q031/D037 **IN_PROGRESS**, без дедлайна: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md),
[TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md), [ASYNC](TASK-ADDENDUM-ASYNC-20261017.md).
Ветка codex/server-validation-20261011; [draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2).
Starting26cbe5912f6683a81780a831609dbddbc26feba4; опубликованная candidate-база
**77811f7476e628973d27fc13238183796e711f7c**. CurrentCI39191321026/PR39191326789 красные; Coverage39191326877/OpenAPI39191326785 SUCCESS; Security39191326806 FAIL; main/merge/deploy нет.

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
| U14.5 | Samearchive/DLQ/replay/dedup/restoration и semesterUI PASS; subject BEFOREcontrolMarks1 PASS, затем previewGET5854 HTTP503. Серверная закрытая phase/reason/rpcStatus + существующий reporter готовы/compile40 PASS; actual причинаUNKNOWN. Проверить исходный preview/delete/participants с прежними proof/grants. |
| U14.6 | 5auth/role/logout иOpenAPI PASS. SOURCE-PROVEN productGAP: groupreport academic_report forwarded toordinaryAuth+Attendance validators. R2aa scopedreport-data chain+finitebatching21files sourceREADY/independentreview; admin sameactions inline fordistinctframes. Actualexclusive timeoutcauseUNKNOWN. |
| U20.1 | Private common/menu иTeacherToday/empty дошли доPASS; hosted headmanphase FAIL/currentchecks37; first read/READY+lesson ambiguity. Closedreadoutcome добавлен в прежний carrier. Отдельный NativeBridge malformedbinding0 fixture исправлен к exactINVALID_ARGUMENT; actual rerun нужен. |
| U20.2 | Own currentlesson/roster/unmarked/statistics source согласован; тотже hostedFAIL, конкретное условие покаUNKNOWN. Пройти исходные headman/currentrights/none/revoke, зависит U20.1; новый API/дизайн не нужен. |
| U20.3 | TeacherToday+empty PASS до headmanFAIL; поздние freshbinding/revoke/currentrights ещё не закончены. Пройти оставшуюся часть существующего hosted сценария после U20.1. |
| U25 | Финальные compatibleCI/Coverage/Security/OpenAPI/review/handoff. 778 Coverage/OpenAPI/обаApps PASS; SecurityFAIL. d3bf seed+pending PASS, fresh-restore RuntimeError FAIL; closedstagehint готов в прежнем retainer. PRMonitoring POSTGRES_ACADEMIC exit2; finalTCPreadiness готов. Итоговые gates требуют актуальной revision. |

## Принятые текущие результаты / evidence

**U14.4 DONE_CHANGED** наe5c: registry12158013689/4c3a539b all6PASS — FULL/LIMITED/currentcapability403,
HTML/PDF/PNG, явный terminal при renderer outage, restoration и freshPDF. Existing2x18week/lost202/reconnect/short3week/D035 сохранены.
**U14.7 DONE_CHANGED** наe5c: standaloneCLI12158825618/ff648942 actualFRESH+REPEAT PASS;
Academic12158670472/25089ef9 exactsource5d255d02 core4PASS: firstlogin/profile/passwordchange, durable restart/unknownresult,
concurrency/existingadmin и runtimeDBboundarydenial. Q033 закрыт, production invocation не выполнялась.
U14.3 current grants/CAS/history принят8c5; его evidence/review и остальные закрытые строки — в матрице.
e5c exact restore12157058652/9f042ebf PASS:3PG/data/schema/sequence/owners/ACL/defaultprivileges/runtimegrants+deny/Mongo/files.
d3bf application seed12159894958/4351df83 иpending/6cb8a058 PASS; fresh-restore RuntimeError FAIL. Исторический WORD crash остаётся UNKNOWN.

## Следующий связный пакет / владельцы

778 proof12160525964/2ef10638 andcache12160785135/cf9c25ef PASS: exactpreview1132/409→200 negative retained; restoredsource true; currentcommit+rollback2green.
778 Apps push12160092313/8a4ef145 +PR12159944906/89c9b0ee PASS: fixed192heap; rawRSSdelta46170112/41201664B, CPU1.023/1.022, same64/384MiB gates, cleanupPASS. OldRSS71786496BFAIL retained.
d3bf Academic12160442366/02c61395 actual521PASS/0FAIL/1normalCLI-SKIP; standaloneCLI actualPASS separately. CollectorFAIL exactstaleHistoricalMembershippin nowjoined; 4SOURCE_METHODPASS retained. CoverageSUCCESS.
Root joined SubjectPREVIEW4main+2tests: independent addressedAPPROVE; Native40 compileJava+compileTestJava PASS67.04s/RSS867168256B/output102189504B; lease RELEASED. Exact optional503tuple reporter46270dfd privacy15groupsPASS; actual runtime ещё UNKNOWN.
Ready publish: тотже preview503/Bot7closedreadoutcomes/admin3sameactionsinline; exactHistoricalMembershipa21d sourcepin; existingfresh-restore closedhints; Monitoring finalTCPready independentAPPROVE. Assertions/guards/cleanup сохранены.
Security authorr9cf_event: small2file typednonsecret exactexceptions +immutableacceptedrecord guard, fullhistory/merge/default208 retained; no blanketignore. R2aa scopedreport21files sourceREADY independentreviewr9cf_report; a05 prepares Native41 no lease yet.

Root soleGit/docs/matrix/PR; no ownerquestions. Lastresources workspace4208455680B/TMP4628328448B/cgroup~6.5of17.18GB; no localDocker (<4GiBreserve), no cleanup/reset/prune; foreign25 preserved.
[Resources/owners](resources.md), [protocol](BOUNDARY-PROTOCOL-20261018.md), [decisions](decisions.md), [questions](questions.md).
История один раз в [attempts](attempts.ndjson); exact checkpoint — ignored current-wip/latest-checkpoint.safe.json.
Appattach/list не завершились: новый outcomeUNKNOWN, прежнийPR URL сохранён; повторов нет. Вопросов владельцу нет.
**B013/B014/B015 FROZEN**. DEFERRED_OWNER/LOCAL: Telegram/push/install/Safari/alertbot/offhostprobe/ownerstand/deploy/backup/VPS/multiworker — не PASS.
