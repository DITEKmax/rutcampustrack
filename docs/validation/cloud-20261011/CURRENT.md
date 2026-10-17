# IN_PROGRESS — A01–A04 / M01–M05, 17.10.2026

После compaction читать [TASK-CONTRACT-20261017.md](TASK-CONTRACT-20261017.md),
[TASK-ADDENDUM-ASYNC-20261017.md](TASK-ADDENDUM-ASYNC-20261017.md), этот CURRENT,
decisions/questions, resources и относящиеся к следующему шагу attempts.
Старый широкий аудит не возобновлять. [HANDOFF-20261017.md](HANDOFF-20261017.md)
один, пока явно incomplete; история/evidence один раз в attempts и Git.

Старт18:49UTC/21:49MSK, base753173688d633ca07991f4dd73bdcd2698f45d96.
Опубликованный local=remote=PR head **f8af7a4225eaf7146e047265bbdd6ce92cd99dfb**,
ветка codex/server-validation-20261011, [draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2)
→ main3908fad9624f97aec8891fc79338ceb6b0616bf3, без merge.
Предыдущийe0 actual checkout **7a8cfb494aa7a2374b2b44623dfd52a7fcbd8537**,
tree e020a8743187eab8e0b633be802b2003933f0333, artifact12117460957/SHAeb1f96f2… .
Новыйf8 actual checkout 4da17553476cf9070f8f32289e9f2875400c238e/tree31093158fcbe6019139f93e44c99545297aed22c,
artifact12117763214/SHA231a91d3… . CI39078337905/Coverage39078337926/Security39078337911/
OpenAPI39078337923: Coverage/Security/OpenAPI completedSUCCESS; CI ещё идёт.
Новыйmonitor FAIL REDIS_ADMIN_READINESS; безопасныйprimary12118332323 сохранён, pipeline/receiverPASS.
Предыдущиеe0 Coverage39075933406, Security39075933403, OpenAPI39075933528 SUCCESS.
CI39075933443 завершён:20success/4failure (monitoring/2browser/aggregate);
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
   04a4a38f APPROVE_RUNTIME_PENDING/publishedf8af7a42. Фиксированные stage/exit/
   category/UP state, исходные6denials/120s/ACL/images/cleanup сохранены.
   Новыйf8actual12118332323: REDIS_ADMIN_READINESS/CLIexit1/UNKNOWN, ещё до
   adminfixture/monitor/denials/exporter. MongoagainPASS, pipeline280.42s/receiver74.36s
   PASS/cleanupPASS. PinnedRedis7.4.6 officialACLparser требуетuserline, а шаблон содержит3comments.
   ACLfixea378173 independentlyreviewedbec2d62a/RootintegratedWIP; обеuserlines byteequal. Optional
   Debian7.0.15 nativeNOT_RUN_SETUP_FAILURE/всёreaped, sourceproof не заменяет
   следующий actualpinnedCI. Права/таймауты не менялись.
2. **Новый async DOCX.** e0safe12117836440/SHA354ee958…: download достигнут,
   Pythoncontenthelper5487 FAIL10409ms/error1; какая из2проверок и точный assert
   UNKNOWN.8targetsUP, deltasempty, cleanupPASS. Accepted202/storage/navigation
   больше не первичный отказ; файл не объявлять правильным. Reports solewriter адресно сопоставляет actuallastweekmark/row vs seed/oracle;
   Найден domainclockseam counterexample: Weeklyservice rawClock masks
   lastlesson asfuture, тогда как mark/read используют EducationalTime09MSK.
   Reports solewriter выравнивает existingclockpattern (service+existingunit only),
   RED01 fixturecompile failed(Objectcast),15770reaped/nosemanticRED.
   RED02 semantic missingEXCUSED FAIL54s/productunchanged → GREEN01 actualmodel+DOCX
   regression и existingfuture/missingguard2/2PASS61s. Все процессыreaped;
   frozen2filesbb36724a/ddf33b2c independentlyapproved/RootintegratedWIP.
   SAMEspec fixedPythonexit/stage +NAVIGATION/LOST_ACCEPTANCE diagnostic97554397
   frozen/review73a4a70aAPPROVED/publishedf8af7a42,
   без rawXML/файла/stdout/stderr/имён и без ослабления18weeks/boundaries/rights.
   TS/format/21Python+32JSprivacycontrolsPASS. Coherent3source checkpoint опубликованf8af7a42, hooks/commit/push/readbackPASS;
   следующий ordinaryCI запущен, без ручного повтора неизменённыхPASS.
3. **Reconciliation.** Исходный fullhealth barrier перенесён ПОСЛЕ восстановления
   Schedule, при outage остаются running/exactretry/finalDLQ; sourcea1135f4c/review
   4d64ca92. Actuale0 дошёл через exactDLQ и исходные fullhealth barriers до PENDING_ARCHIVE,
   но admin request.get(delete-preview)4136 FAIL до response/try (12117802213).
   Точная requestexception UNKNOWN; cleanup/recoveryeffect/restorationPASS,
   samearchive/duplicate ещёНЕPASS. Обычный archive/restore gate тожеFAIL вSETUP:
   attendance mark HTTP500/spec1806 (12117711849), ещё доarchive; causeUNKNOWN,
   cleanup/clock/disposePASS. Reports source/logRCA завершён: CAS/roster inputs согласованы, но existingprivate500
   classifier UNAVAILABLE_PRIVATE_INPUT для другого LESSON_REPORT. Нужна точная
   MARK request-correlated Attendance exceptionclass/frame; Infra подтвердил staleInternalJwtIssuerClientpin и отсутствие MARK collector route/
   responseRequestId. Reviewed/RootintegratedWIP CI+Actor.call точную корреляцию первого MARK500
   и currentpin;2filesfrozen e03c0c44/f5f74cce,28JS+inlinePythonprivacy/correlation/
   TS/format/YAML controlsPASS, independent6f6bbb40 APPROVE_RUNTIME_PENDING; без ослабленияidentity/новогоframework. CauseUNKNOWN, не новыйscope. Finite TimeoutError-vs-OTHER witness8808b910/reviewAPPROVED опубликованf8af7a42,
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
actualUNKNOWN. Runtime7 включаяRoot. Soleauthors Infra(reviewedACL1+MARK2),
Reports(reviewedclock2), все5sourcefiles RootintegratedWIP; обеreviewers независимы.
Auth read-only observer ждёт только новыеsafe reconciliation/lifecycle artifacts текущегоCI.
Остальныеidle; все локальные native/JVM/Redis процессыreaped, leasesRELEASED.
ЛокальныхDB/Docker/browser/server нет. Foreign25untracked/старыйстенд/volumes/keys сохранены.
Rootdocs5WIP; следующийшаг scopedhooks→обычныйcommit/push/readback→новыйordinaryCI
для5sourcefixes. ТочныйследующийSHA ещёнеопределён. CPU5/RAM16GiB; workspacefloor4GiB.
Точный atomicindex+blob+treebudget не помещается45KiB: использовать штатный временный
Gitindex в/tmp с проверенным сохранением/восстановлениемRootindex; не снижатьfloor.
Толькоownpublished785objects compacted packb6f928cf/SHAf57e7cb6; refs/index/diff неизменны.
28ownsafeZIPs сохраненыbyteequal/tmp+исходныеsymlinks; публичныйGradleinstaller также
сохранён/tmp, но underlyinglowerlayer не дал ожидаемогоfree—повторной уборки нет.

Noreset/prune/main/merge/deploy/newbranch/newPR/Angular/newweb/secrets/otherchats/
daemon. B013/B014/B015 FROZEN и прежние nativeTelegram/push/install/Safari/manual
ограничения остаются отдельно; новыйэтап не объявляет прежнийаудит/productionDONE.
