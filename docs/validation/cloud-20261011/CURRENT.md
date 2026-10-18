# IN_PROGRESS — A01–A04/M01–M05 + принятый scope18.10

После compaction читать [расширение18.10](TASK-CONTRACT-20261018.md),
[матрицуv1](selected-results-20261018.csv)/[version+weights](selected-results-20261018.meta.json), [контракт17.10](TASK-CONTRACT-20261017.md),
[async-дополнение](TASK-ADDENDUM-ASYNC-20261017.md), этотCURRENT, decisions/questions,
[resources](resources.md) и относящиеся к nextstep attempts. Один пока incomplete
[HANDOFF](HANDOFF-20261017.md); историю не перечитывать/не повторять достаточныеPASS.

Старт17.10 18:49UTC; stagebase753173688d633ca07991f4dd73bdcd2698f45d96.
Local=remote=PR head **e66b1711b3d93faee625b50a2e2f785020a0f43a**, ветка
codex/server-validation-20261011, [draftPR2](https://github.com/DITEKmax/rutcampustrack/pull/2)
→main3908fad9624f97aec8891fc79338ceb6b0616bf3, безmerge. Stage diff259paths:
50A/209M/0D,9taskdocs; точныйinventory через gitdiffbase..head.
PushCI39080567322 и PRCI39080571663: async-кейс FAIL после первого корректного DOCX;
финальный общий статус не перечитан. Coverage39080571692, Security39080571702
и OpenAPI39080571786 SUCCESS наe66; новый reconnectfixture ожидает обычногоCI.
Actualpushcheckout e66b1711, PRcheckout7eb18090d21eedbce098f1164db6c6f0f565215f;
оба treea7bb0f041ea7fa6779177a8d6804b844b8a5aed4, safe12119160097/12118936028 verified. Предыдущийf8actual4da17553476cf9070f8f32289e9f2875400c238e/
tree31093158fcbe6019139f93e44c99545297aed22c,12117763214/SHA231a91d3…;
егоCoverage/Security/OpenAPI PASS, Redis/asyncFAIL, reconciliation+ordinaryarchivePASS.

| ID | Статус | Достаточный результат / конкретный остаток |
| --- | --- | --- |
| A01 | DONE_CHANGED | Совместимый управляемый BCrypt/constructor injection; hash/lifecycle + realVue admin-create→login PASS/review. executable8ae9b530, Auth/Academic/admin12114507172. |
| A02 | DONE_CHANGED | Три enum с прежними текстовыми DB-кодами; realJPA/JDBC/JPQL/REST/gRPC unknown/null/legacy PASS/review. SQL/proto не перенумерованы.8ae9b530, Academic12114232912. |
| A03 | DONE_CHANGED | DTO redaction/JSON, RS256/claims, safeCI uploads; realAuth145unit+200IT PASS0skip/review.8ae9b530/checkoutda31a937,12114133029. |
| A04 | DONE_CHANGED | RealSQL pages/order/fields/empty/currentstudent isolation PASS: subjects2 enrichment reads/HW1 completion read без per-row роста.8ae9b530,12114232912/review. |
| M01 | DONE_CHANGED | RealGateway→BFF→TCPgRPC24spans/Rabbit delayedretry→Mongodedup10spans/independentnext event→Tempo+Loki/Grafana, context/privacy/cancel controls, independent runtime review. e283b95c/12114439186; e0pipeline12116514443 повторноPASS. |
| M02 | IN_PROGRESS | Apps8/node/cAdvisor/Botqueue/2PG blocks и Mongo auth+diagnostic+appreadDENIED+exporter PASS. Redis/Rabbitplugin actualPASS M05; остаток: realreport/job deltas и retainedfamilies из нового asyncbrowser. Infra/Reports. |
| M03 | IN_PROGRESS | Realresourcequeries/30dengine/logs↔traces PASS e0pipeline299.77s. Scoped dashboard/BFF/queryoracle corrections reviewed. Остаток: retained JVM/Hikari/Tomcat/gRPC/report/Auth panels после2realDOCX. Infra. |
| M04 | IN_PROGRESS | Старый HEADMAN HTTP/Mongo18week DOCX7/7 PASS85463; realRabbit8cases/mandatory oldRED→GREEN/ACK-return-NACK/retry/poison/dedup PASS e283, receiverPASS. Новыйasync e0 достигdownload, но содержимое НЕ принято. Reconciliation samearchive/DLQ/dedup/cleanup actualPASSf8. Остаток: обаcorrectDOCX/reconnect/lost202/currentrights, ordinaryarchive actualf8PASS12118678030 (historical500causeUNKNOWN). |
| M05 | DONE_CHANGED | e66 realRedisadmin+6NOPERM/exporter/Rabbit and2PG+Mongo rights PASS; privatequery/admin403/recreate/positions/capacity/storemap accepted independentreview.12118842862/SHAfb1a7985… . |

**Закрыто6/9: DONE_CHANGED6, ALREADY_SATISFIED0.** C01 настоящийPG oldstaleRED→
afterCommit commit/rollback2GREEN12113673595; C02 publishedmigration guard/28controlsPASS.
C03 2PG+Mongo+Redisrights/exporters actualPASS; fullcohort91.85s/cleanupPASS,
independentlyaccepted12118842862; productiongrants не выполнялись.

## Конечный остаток / next step

1. **M05/C03 закрыты наe66push:**12118842862/SHAfb1a7985… fullmanifestPASS,
   exporters91.85s/pipeline355.16s/delivery74.37s/cleanupPASS. IndependentReviewAuth
   связал exactsource и все34manifestconfigs; Redisadmin+6NOPERM/exporter, Mongo/PG
   права, Rabbitready/unacked/DLQ/timestamp реальны. Machinecapacityнеизмерена,
   productiongrants/deploy нет; отдельныеDEFERRED_LOCAL остаются.
2. **Новый async18 DOCX:** e66pushclient12118927134/SHA0e4b13ce FAILlater21353ms: firstNAVIGATION
   correct18weekDOCX иmarker-clear PASS, lost202 replayPOST/download5694/5699 timeout.
   Историческое timingUNKNOWN. Sourcecounterexample: прежний offlineowner мог принять
   click до activation новойсессии. Толькоfixture исправлен3d631113, independentR1
   CLOSED: session200 + exactold.mobile-shell detached +newhome; реальныйreplaypending. Старыйf8artifact
   12117919693/SHA47eaa821: first NAVIGATIONdownload ZIP/XML/all18dates/group/first+
   PASS, LAST_STUDENT_MARK FAIL. Причина доказана: weeklyrawClock != EducationalTime
   урока. Originalproduct semanticRED54s→alignedconstructor model+actualDOCX и
   existingfuture/missingguard2GREEN61s, independentlyapprovedbb36724a/ddf33b2c,
   опубликованоe66. Нужны actual2correctDOCX/navigation/reconnect/lost202/currentrights;
   oldsync18week7/7/3240marks85463 не заменяетновыйasyncпуть.
3. **Reconciliation и ordinaryarchive ужеPASS наf8**, не повторять вручную:
   12118602956/SHAe94fdfcc… exactDLQ→PENDING→APPLIED→samearchiveCOMPLETED→duplicate
   unchanged/cleanup/restoration/clock; configured3, measuredattemptsUNKNOWN, retainedDLQ1.
   12118678030/SHA c6d8ddbe… ordinaryarchive→restore79980ms/all3RELEASED/cleanup/
   clock/disposePASS. Прежняяe0MARK500 причинаUNKNOWN, не объявленаflaky/исправленной.
   **Диагностика:** опубликованныйMARK collector e03c0c44/f5f74cce reviewed6f6bbb40
   приватно связываетfirstcanonicalresponseIDдоfiniteprojection, но Root нашёл stale
   lineguard1580/1806 послеpreview+7lines: actual1587/1813. Infra исправилТОЛЬКО2числа
   вCI cb7ce1ad +actualsourceboundarycontrol; reviewerAuth rechecka222cd8c/RootWIPaccepted. Никакого
   измененияправ/identity/schema/FROZEN илиповторнойfullCAPcampaign.
4. **M02/M03:** после2correctDOCX существующий8app check проверяет реальныеjob/report
   deltas, retained JVM/Hikari/Tomcat/gRPC/report/Auth panels. Realhost/node/cAdvisor/
   Botqueue/scopedresourcequeries/30dengine/logs↔traces ужеPASS. Не заменятьactual
   недостающееsourcecontrols. IndependentM04applicability проверил exactcurrent
   Auth14ExportJobsIT+2HTTPIT(12114133029/SHA43d1537b…): boundedretry/lease/fencing/
   idempotency/admission/retention/livegrants/session и worker/client stalecontrols
   достаточны; новогообязательногоgap сверхэтогоконечногоостатка нет. Это неprocesskill/loadproof.
5. ПослеactualPASS — finalindependentapplicability/currentmandatorygates и одинhandoff,
   executablevsfinaldocsSHA/diffот753. D009 допускает finaldocsonlycommit с доказанным
   sourceidentity; workflowcorrection вэтоисключение не входит. Неполное неDONE.

## Решения / owners / ресурсы

D032/Q028 WORD — журналстаросты, прежнийdirectHTTP/JavaXMLZIP, неRabbit; исходныйcrashUNKNOWN.
D035 durableasync job вPG, Gatewayworker вызывает совместимыйHTTPreportAPI; большойDOCX
непересылаетсячерезRabbit. D033 engineering99.9%/30d, read+login2s/write3s/report30s p95,
asyncfinal≤60s99.5%; expected4xx/cancel исключены, missing/noDataне100%.
D034 offhostprobe приdeploy и futurealertbot — два отдельныхDEFERRED_LOCAL.
Продуктовыхвопросов нет; Q031 ANSWERED/D037: TASK-CONTRACT/CURRENT = persistedGoal. Root soleGit/docs, selectedSol6.1ULTRA; детиSol6.1/high/forknone,
actualUNKNOWN, capacity7inclRoot. Infra CI2constantcorrection reviewedRootWIP; ReviewAuth
independentM05+findingrecheck. U14 readonlytechnicalplan active; остальныеidle; всеnative/JVM/Redis leasesRELEASED/reaped.
LocalDB/Docker/browser/server нет. Полныйownership/resources — resources.md.
RootdocsWIP сохраняютновыеactuals; foreign25untracked/старыйstand/volumes/keys нетронуты.
Workspacefloor4GiB; exacttemporaryGitindex/tmp позволилобычныйcommit, normalRootindex
byteverified restored. Ownpublished824pack72cfc560/SHA4c946d96/backups/tmp; noprune/GC.
Noreset/main/merge/deploy/newbranch/newPR/Angular/newweb/secrets/otherchats/daemon.
B013/B014/B015 FROZEN; прежние nativeTelegram/push/install/Safari/manualограничения
отдельно. Этотэтап не объявляет прежнийаудит иливесьпродукт productionDONE.

## КТ0/расширение18.10

D036 принято; рольответственного за выбранные сценарии/адресныйrefactor, Root интегратор.
D037: ownerподтвердил TASK-CONTRACT/CURRENT как persistedGoal.
Объединённаяцель18 ACTIVEвфайлах; старые17criteria/6из9 сохраняютсявнутри, неfalsecomplete.
ОтдельныйplatformGoalAPI неимитируется; повторногоGO на выбранныйscope нет.
Матрицаv1-КТ1 FROZEN: 8/25, weights38/123 = 30.89% плана, неproduction-readiness.
Старые6/9 отдельно. Состав/criteria/weights зафиксированыКТ1 до новойреализации;
условныеunselected integrations иownerDEFERRED отдельновне denominator; U23assessment обязателен.
КТ1 nativeagents readonly: Auth HTTP/DTO/DI/access; Propagation owner/startup/cache;
Reports files/notifications/menu/UI/k6; Infra Alloy/conditionaltools. Root сохраняет
критический DOCX/CI остаток; reviewers независимы. Ресурсно native7inclRoot,
без новых heavy/runtimeleases. Существенные productвопросы после boundedsourcecheck.

КТ1 mappings сохранены attempts/matrixv1. U23assessment ALREADY_SATISFIED,
четыре условных integration UNSELECTED. U07 final3d631113 independentlyaccepted,
secondLOST_ACCEPTANCE/currentrights/runtime ещёpending; CI diagnosticguard меняет
только stale2lineconstants. Готовый пакет:2source+10taskdocs, безновойпродуктовойправки.
Послеpush — новые currentbaselineWT для HTTP/DI/access, atomicownerboundary,
Alloy/Redis и ролевогобота/файлов. Старые753WT/WIP сохраняются, noreset.
