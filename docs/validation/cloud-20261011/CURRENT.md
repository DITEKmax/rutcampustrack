# ACTIVE — объединённая цель / separate DB + apps, 18.10.2026

Восстановление: [новый контракт](TASK-ADDENDUM-SPLIT-APPS-20261018.md),
[TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md),
[ASYNC](TASK-ADDENDUM-ASYNC-20261017.md), этот CURRENT и [ресурсы](resources.md).
Q031/D037: эти файлы — persisted Goal; отдельного Goal API нет, falsecomplete нет.
Root soleGit/taskdocs; ветка codex/server-validation-20261011,
[draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2), main не merged.
Stage19 starting HEAD **26cbe5912f6683a81780a831609dbddbc26feba4**.
Published/executable **5ff1a72014de91c9b7a38fc0f0bc5db76c820f43**, local/remote/PR verified.
Previous executable da3402ea; Rootmatrix/evidence updateWIP belongsnextcoherentcheckpoint.
Stage17base753173688d633ca07991f4dd73bdcd2698f45d96; received18basee66b1711.

## Принятие и конечный остаток

[Матрица v2-КТ1 FROZEN](selected-results-20261018-v2.csv)/[веса](selected-results-20261018-v2.meta.json):
**23/38,109/188=57.98% плана**, не production-readiness. Веса/acceptance зафиксированы.
[Историческая v1](selected-results-20261018.csv)/[meta](selected-results-20261018.meta.json):
**23/25,110/123=89.43%**, U15 восстановлен из owner/PRbody/review45b5989a/SAFE50821c16.
U15 oldsameDBrepeat не две новыеDB/VPSrestart. U14 старый parent раскрыт детьми,
U23 assessment исторический и не counted вv2, приложения/PMM имеют отдельные критерии.
TASK17 функционально9/9 DONE_CHANGED9, начальные6/9 в истории; старые manual/FROZEN
ограничения не превращены вPASS. Остальные применимые23→22 старые исходы не перепроверять
без затронутого риска; изменённый consumer требует актуального доказательства.

| КТ | Принято / всего | Вес / всего | % плана |
| --- | --- | --- | --- |
| КТ2: boundary/AAA/contracts/persistence | 8/13 | 32/63 | 50.79 |
| КТ3: bot/apps | 2/9 | 7/37 | 18.92 |
| КТ4: changedchains/monitoring/jobs | 13/15 | 70/83 | 84.34 |
| КТ5: finalgates/review/handoff | 0/1 | 0/5 | 0 |

| Остаток | Владелец / следующий достаточный результат |
| --- | --- |
| U14.1/.2/.3/.5 | Boundary+Academic: auth_db/academic_db, AAA-only, fenced durableoperation create/grant/transfer/archive/proofs; frozenproto+независимыйS3review→code→actualfailure/recovery/minrights. |
| U14.4/.6 | Jobs+Boundary: single domainjobqueue/worker/artifacts и compatibleconsumers/OpenAPI/gRPC; 2DOCX+короткийдругойпериод/уход/lost202/recovery/currentrights. |
| U15.1 | Academic+Boundary: twoDB fresh/repeat сохраняютIDs/credentials/grants/history/sessions/jobs; newSQLonly. |
| U20.1/.2/.3 | Bot+BotProducer+Boundary: exactcommon4/studentHW/headmancurrentroster/teacherownToday; privatefreshauthority/choice/timezone/empty/revoke proof. |
| U23.1/.2/.3/.4 | Apps: actualGlitchTipVueJava+Pyroprofile+protectedHomepage, combinedresourcebudget; CIruntimeнеcomposePASS. |
| U25 | Root+independentreview: allcurrentgatesPASS/source/executable/PRhead/Q009doconly/handoff. |

## Текущий связный пакет / failures

На26c Academic **476PASS/9FAIL/0skip**, все485executed; Coverage теже9.
Event6 AccessDenied228; Map1AdminAccess316; ORM2SQLGrammar/PSQL193+rollbackassert266.
Оба browser Boot SUCCESS, затем datasetPOST/api/academic/users500 доlogin/UI/DOCX.
ActualSQLSTATEUNKNOWN, не установленнаяUIregression. CI39091941238/Coverage39091941236
FAIL; Security39091941198/OpenAPI39091941265 SUCCESS; monitoringwholepipelineSUCCESS.
Flyway4(old62SQL42702→63false/repeat/principals) иSubjectHTTP8PASS; V62byteexact.

Presplit3 published da: originalheadmancontext beforeSubject, oneMapguard currentauthority,
Academic generatedkeys=false, no secretSELECT/oldSQL/assertion changes. Compile43tasks
153.06s/RSS1.12GiB, sixhooks and independentS3sourceAPPROVE. daCI39109858933:
client actual2DOCX/navigation/lost202, authsmoke/remaining5/WebKitonline/workerlogic PASS;
Academic485IT completedPASS. TerminalallCI notPASS; actualoldSQLSTATEUNKNOWN.
da Coverage39109858950(all10)/Security39109858940/OpenAPI39109858920 SUCCESS.
Academic safeartifact12130403445:485PASS/0fail/skip, all9oldfailmatchedPASS; originalSQLSTATE
stillUNKNOWN. da lifecycleFAIL: first2containerinspectidentityguards(fielddiffUNKNOWN),
transfer own202+6GET200/PENDING, semesterpreviewtransport0+failedrestoration. New5ff CI
39111397263/Coverage39111397260/Security39111397283 active; OpenAPI39111397255SUCCESS.

Apps39paths+oneauthoredCIhook integrated after independentS3sourceapproval; threeP2
harness issues fixed/rechecked (cleanup-beforePASS, realidlesamples, structuredstackframes).
Java+Vueprivacy/compile checks PASS; 5ffApps CI126 beforeGradle (wrapper mode100644),
one-line authored bashlauncherfix prepared. Actual3apps/grouping/profile/resources NOT_RUN.
Bot26Python and Producer25Java sourceapproved, addressed tests PASS; realtwoDB/TLSchainpending.
U23.5 DONE_CHANGED: reviewed justified PMM nonselection only (selection.json), no PMM runtime
claim. Otherthreeapps/DB/runtime rows remain open; no closure from compile/partialCI. Apps5ff published→nativeApps halted beforeGradle (126/nonexecwrapper); one-linebashfix prepared,
while Auth/Academic concreteoperation/catalog and Jobs domainqueue migration continue.

## KT0 / writers / восстановление

Cloud current running/connected, exec работает. /tmp послеoutage пуст: старые4WT,
2последнихfixtureWIP/receipts/caches утрачены. Root4taskdocsWIP+25foreignuntracked,
regularGitpack/index сохранены. Не заявлять старыйstand/volumes/keys проверенными;
ничего не reset/delete. Actualpacka0e5040f SHAefd2f155…4705 unchanged.
WTs/tmp/rct-s19-{auth,academic,bot,apps,bot-producer} base26; Jobs WTbase da,
auxbare readonlyRootobjects; noRootGC/prune. Native7slots inclRoot; Sol6.1/high,
actualmetadataUNKNOWN accepted. Root integrates readyinventory only; currentreadyWIP
singlecopy .agent/evidence/cloud-20261011/current-wip protects against observedTMPloss.
Boundary Auth/proto/generated/basecompose/env/routes; Academicprofile/coordinator/catalog;
Jobs all11domainjobs/AcademicV2/leasedrendererhelpers/Notificationadmit; BotPython;
ProducerBFF/Attendance/Schedulebotreads; Appsinfra/SDK/build/lock; review independent.
Authruntime ownDB/session/credentialmetadata author separateWTbase5ff; Producer now owns
Attendance data-only plannedrenderer, Jobs Academicplanner/queue (no BFFcallback or genericJWT).
CIauthor isolatedWTbase5ff fixes unstableordering-only oracle and safeclosedfielddiff; actual
first2runtimecauseUNKNOWN, transfer/backendreconciliation stillopen. Academic/Authnewcore
compilesPASS; source review3Academic/P1Auth findings accepted/correctionsinprogress. Authruntime oneboundedcompileleaseCPU0 (oldsourcecore passed, currentnewownDBtests only
compile; noDBproof); no localDB/Docker/images. Limits/measurements inresources.

[Evidence/history](attempts.ndjson), [decisions](decisions.md), [questions](questions.md),
[единственный HANDOFF](HANDOFF-20261017.md) обновляются вместе связнымcheckpoint.
Существенных вопросов владельцу пока нет. No main/merge/deploy/reset/secrets/Angular/newweb.
B013/B014/B015FROZEN, никаких эквивалентов черезCI/model/agent/launcher.
DEFERRED_LOCAL/OWNER: realTelegram/push/install/Safari/alertbot/offhostprobe/ownerstand/
realdeploy/backup/VPS/multiworker. ИсторическийsyncWORD crashcauseUNKNOWN.
