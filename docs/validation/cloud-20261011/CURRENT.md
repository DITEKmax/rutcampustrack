# IN_PROGRESS — A01–A04 / M01–M05, 17.10.2026

После compaction читать [TASK-CONTRACT-20261017.md](TASK-CONTRACT-20261017.md),
[TASK-ADDENDUM-ASYNC-20261017.md](TASK-ADDENDUM-ASYNC-20261017.md), этот CURRENT,
decisions/questions, resources и относящиеся к следующему шагу attempts.
Старый широкий аудит не возобновлять. [HANDOFF-20261017.md](HANDOFF-20261017.md)
один, пока явно incomplete; история/evidence один раз в attempts и Git.

Старт18:49UTC/21:49MSK, base753173688d633ca07991f4dd73bdcd2698f45d96.
Опубликованный local=remote=PR head **e0a44e4f335c58b6993c4e4de1b4f90222e695c2**,
ветка codex/server-validation-20261011, [draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2)
→ main3908fad9624f97aec8891fc79338ceb6b0616bf3, без merge.
Actual checkout **7a8cfb494aa7a2374b2b44623dfd52a7fcbd8537**,
tree e020a8743187eab8e0b633be802b2003933f0333, artifact12117460957/SHAeb1f96f2… .
Coverage39075933406, Security39075933403, OpenAPI39075933528 completedSUCCESS.
CI39075933443 ещё идёт: monitoring и async18 FAIL, остальное продолжается;
новый диагноз/исправление не подтверждаются старым green.

| ID | Статус | Достаточный результат / конкретный остаток |
| --- | --- | --- |
| A01 | DONE_CHANGED | Совместимый управляемый BCrypt/constructor injection; hash/lifecycle + realVue admin-create→login PASS/review. executable8ae9b530, Auth/Academic/admin12114507172. |
| A02 | DONE_CHANGED | Три enum с прежними текстовыми DB-кодами; realJPA/JDBC/JPQL/REST/gRPC unknown/null/legacy PASS/review. SQL/proto не перенумерованы.8ae9b530, Academic12114232912. |
| A03 | DONE_CHANGED | DTO redaction/JSON, RS256/claims, safeCI uploads; realAuth145unit+200IT PASS0skip/review.8ae9b530/checkoutda31a937,12114133029. |
| A04 | DONE_CHANGED | RealSQL pages/order/fields/empty/currentstudent isolation PASS: subjects2 enrichment reads/HW1 completion read без per-row роста.8ae9b530,12114232912/review. |
| M01 | DONE_CHANGED | RealGateway→BFF→TCPgRPC24spans/Rabbit delayedretry→Mongodedup10spans/independentnext event→Tempo+Loki/Grafana, context/privacy/cancel controls, independent runtime review. e283b95c/12114439186; e0pipeline12116514443 повторноPASS. |
| M02 | IN_PROGRESS | Apps8/node/cAdvisor/Botqueue/2PG blocks и Mongo auth+diagnostic+appreadDENIED+exporter PASS. Остаток: Redis→Rabbitplugin; realreport/job deltas и retainedfamilies из нового asyncbrowser. Infra/Reports. |
| M03 | IN_PROGRESS | Realresourcequeries/30dengine/logs↔traces PASS e0pipeline299.77s. Scoped dashboard/BFF/queryoracle corrections reviewed. Остаток: retained JVM/Hikari/Tomcat/gRPC/report/Auth panels после2realDOCX. Infra. |
| M04 | IN_PROGRESS | Старый HEADMAN HTTP/Mongo18week DOCX7/7 PASS85463; realRabbit8cases/mandatory oldRED→GREEN/ACK-return-NACK/retry/poison/dedup PASS e283, receiverPASS. Новыйasync e0 достигdownload, но содержимое НЕ принято. Остаток: обаcorrectDOCX/reconnect/lost202/currentrights и текущий reconciliation. |
| M05 | IN_PROGRESS | Independent review принял privatequery/auth/admin403/ownedrecreate/positions/provisioning/capacity/settings/storemap. Actual Mongo rights/exporter PASS e0. Общий остаток: Redis monitor rights/exporter. |

**Закрыто5/9: DONE_CHANGED5, ALREADY_SATISFIED0.** C01 actualPG old-staleRED→
restoredcommit/rollback2GREEN12113673595 принят. C02 publishedmigration guard/
28controls ordinaryCI PASS. C03 минимальныероли/source reviewed,2PG+Mongo blocks
PASS; весьcohort ещёFAIL, productiongrants не применялись.

## Исполнимый остаток и текущее действие

1. **Redis exact primary.** e0safe12116514443/SHA63a8e1be…: MongoALLGREEN,
   затем REDIS_ROLE_AND_EXPORTER/AssertionError, cleanupPASS. Existingcompletedlog
   не сохранил ветку; ACL/pinnedexporter source не обосновывает расширениеправ.
   Infra ONEfile check_exporters diagnostics7800ed4b frozen/independentreview
   04a4a38f APPROVE_RUNTIME_PENDING/RootintegratedWIP. Фиксированные stage/exit/
   category/UP state, исходные6denials/120s/ACL/images/cleanup сохранены.
   Следующий actualcohort обязан пройти Redis→Rabbitplugin.
2. **Новый async DOCX.** e0safe12117836440/SHA354ee958…: download достигнут,
   Pythoncontenthelper5487 FAIL10409ms/error1; какая из2проверок и точный assert
   UNKNOWN.8targetsUP, deltasempty, cleanupPASS. Accepted202/storage/navigation
   больше не первичный отказ; файл не объявлять правильным. Reports solewriter
   сравнил тот же helper/renderer/seed: доказанного product/oracledefect нет.
   SAMEspec fixedPythonexit/stage +NAVIGATION/LOST_ACCEPTANCE diagnostic97554397
   frozen/review73a4a70aAPPROVED/RootintegratedWIP,
   без rawXML/файла/stdout/stderr/имён и без ослабления18weeks/boundaries/rights.
   TS/format/21Python+32JSprivacycontrolsPASS. Готов coherentent3source diagnosticcheckpoint; дальше scopedhooks/commit/push/readback
   и следующий обычныйCI, без повторов прежних достаточныхPASS вручную.
3. **Reconciliation.** Исходный fullhealth barrier перенесён ПОСЛЕ восстановления
   Schedule, при outage остаются running/exactretry/finalDLQ; sourcea1135f4c/review
   4d64ca92. Actuale0 дошёл через exactDLQ и исходные fullhealth barriers до PENDING_ARCHIVE,
   но admin request.get(delete-preview)4136 FAIL до response/try (12117802213).
   Точная requestexception UNKNOWN; cleanup/recoveryeffect/restorationPASS,
   samearchive/duplicate ещёНЕPASS. Finite TimeoutError-vs-OTHER witness8808b910/reviewAPPROVED интегрированRootWIP,
   rethrow SAMEerror; retries/budgets/barriers не изменены.
4. **После2correctDOCX** существующий check_pipeline в том же8appстенде проверяет
   report/job deltas и actual retained panel queries; не заменятьsourcecontrols.
5. После достаточных actualPASS — независимая применимость итоговогоevidence,
   обязательныеcurrentgates, обновить одинHANDOFF с diffот753/inventory/обаSHA.
   D009 разрешает finaldocs-onlycommit при доказанном sourceidentity.

Не повторять достаточные PASS без затронутого риска.5c assignmentprepare/pending/
resume actualPASS12116498187 закрылstudy-window fixture; formatselection e0достигает
файла, selectorfixture закрыт.5cAuthunit доtestsFAIL из-заMavenHTTP429/NO_RESULTS,
currente0AuthunitSUCCESS, код и expectations для этого не менялись.

## Решения, владельцы, ресурсы

D032/Q028 WORD — журнал старосты; прежний renderer HTTP/JavaXMLZIP, неRabbit.
D035 asyncjob хранится вPG, Gatewayworker вызывает совместимыйHTTP reportAPI;
большойDOCX вRabbit не пересылается. Исходная причина пользовательского сбояUNKNOWN.
D033 инженерныецели99.9%/30d, read/login2s/write3s/report30s p95,
asyncfinal≤60s99.5%; expected4xx/cancel исключены, no-data не100%.
D034 offhostprobe приdeploy DEFERRED_LOCAL, отдельно futurealertbotDEFERRED_LOCAL.
**Вопросов владельцу сейчас нет.**

Root soleGit/docs integrator selectedSol6.1ULTRA; детиSol6.1/high/forknone,
actualUNKNOWN. Soleauthors: InfraRedis7800ed4b и ReportsDOCX97554397/preview8808b910 diagnostics
frozen/reviewed/Rootintegrated; все authors/reviewers idle, процессов нет. Runtime7
включаяRoot; подробности/resources.md. RootsourceWIP — три revieweddiagnostics7800ed4b/97554397/8808b910,
плюс Rootdocs/одинhandoff. Foreign25untracked/старыйстенд/volumes/keys сохранены.
ЛокальныхDB/Docker/browser/server нет; CPU5/RAM16GiB;4GiBworkspacefloor сохранён,
~1.98MBmargin после exactbackup/pack765 толькоownpublishedobjects
(pack34c67a90/SHA b37dc3c1…); refs/index/status/diff не изменены,45689reaped.
ПередGitstaging снова измерить atomicindex+newobject reserve; tmp~6.3GB.

Noreset/prune/main/merge/deploy/newbranch/newPR/Angular/newweb/secrets/otherchats/
daemon. B013/B014/B015 FROZEN и прежние nativeTelegram/push/install/Safari/manual
ограничения остаются отдельно; новыйэтап не объявляет прежнийаудит/productionDONE.
