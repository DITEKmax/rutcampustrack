# ACTIVE — объединённая цель / separate DB + apps, 18.10.2026

Восстановление: [новый контракт](TASK-ADDENDUM-SPLIT-APPS-20261018.md),
[TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md),
[ASYNC](TASK-ADDENDUM-ASYNC-20261017.md), этот CURRENT и [ресурсы](resources.md).
Q031/D037: эти файлы — persisted Goal; отдельного Goal API нет, falsecomplete нет.
Root soleGit/taskdocs; ветка codex/server-validation-20261011,
[draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2), main не merged.
Stage19 starting HEAD **26cbe5912f6683a81780a831609dbddbc26feba4**.
Published/executable **4e7454cdd56348ce67ad638a089e64f2b782d443**, local/remote/PR verified.
Previous executables da3402ea/5ff1a720; status WIP joins next coherent product checkpoint.
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

## Текущий пакет и реальные проверки

26c: Academic 476PASS/9FAIL; оба browser jobs получали usercreate500 до UI.
Presplit3 опубликован da3402ea без расширения ACL, старых SQL и ослабления assertions.
На da все 485 IT PASS, включая прежние девять FAIL; dataset create и два DOCX работают.
Первичный SQLSTATE и причина исторического WORD crash остаются UNKNOWN.
Первичные artifacts/хэши и уже пройденные проверки — один раз в attempts.ndjson.

На **4e** Coverage39112670066 / Security39112670088 / OpenAPI39112670055 **SUCCESS**.
CI39112670053 **FAIL**: client117631910145 SUCCESS (включая два 18-недельных DOCX,
навигацию/lost202); lifecycle117631910206 FAIL в subject deletion, stranded semester
reconciliation, application recovery. Семантический Docker identity guard опубликован;
новая первичная диагностика трёх FAIL — s19_ci_diagnosis, причины пока UNKNOWN.

Apps117631910291: bash-launcher исправлен; Java baseline падает до запуска приложений.
Точный неизменённый 18-week Probe при heap192MiB воспроизвёл OOM в
DocxRenderer.mergeDocumentXml147 (35.4s, RSS194MB); historical incident не отождествлять.
Двухфайловый streaming fix4ae78a52 — независимый SOURCE APPROVE; javac и JUnit3/3 PASS
(все630имён/540предметов/18900отметок), неизменённый Probe192MiB PASS65.27s/RSS121.1MiB.
Safe before/after evidence: receipts/S19-docx-streaming-memory/. Actual GlitchTip/grouping/Pyroscope/Homepage/resources
NOT_RUN; cleanup PASS, принадлежащих job ресурсов не осталось.
U23.5 закрывает только обоснованный PMM nonselection; три обязательных приложения открыты.

Подшаги split (не новые закрытые строки):

- Academic round5: coordinator/read fences/negative Prepare receipt, 23 tests PASS,
  независимый S3 APPROVE. Round6 public directory/mutation wiring ещё IN_PROGRESS.
- Auth core/security/login: SOURCE APPROVE, AAA9+login3 PASS; последний empty-Unicode
  login case source-only. Auth runtime25v4: source APPROVE, compileTestJava PASS59.22s;
  прежние pure10 PASS не повторялись. OwnDB runtime NOT_RUN.
- Jobs freeze4: 58 authored paths, старые Auth domain classes убраны с явным mapping
  прежних критериев. Следующий compile/Validation6+Authority5; actual DB15/twoDB E2E NOT_RUN.
- Attendance data-only renderer23: SOURCE APPROVE; 34 новых +76 затронутых старых +4 Schedule
  PASS, actual TLS и DOCX18/18/3. Последние два metadata изменения и новый DocxRenderer
  требуют адресной проверки. Полный twoDB путь не принят.
- Bot Python26 и directed Java25: source APPROVE, адресные проверки PASS; новый
  Auth/Academic/twoDB/TLS пользовательский путь остаётся непроверенным.

## Среда, writers и следующий шаг

Cloud current running/connected, exec работает. После прежнего outage /tmp потерян;
workspace/Git и 25 foreign untracked сохранены, без reset/delete/GC/prune.
Последнее измерение: workspace4,286,464,000B free; TMP5,214,949,376B free;
cgroup5,436,284,928/17,179,869,184B, CPU quota4. Не32GiB VPS.
Root sole Git/taskdocs/matrix. WTs /tmp/rct-s19-{auth,academic,bot,apps,bot-producer}
base26; Jobs base da; Auth runtime/CI author base5ff. Auxbare использует Root objects readonly.
Native7 slots inclRoot; selected Sol6.1/high/forknone, actual metadata UNKNOWN accepted.

Boundary — Auth/proto/generated/basecompose/env/routes; Academic — profile/coordinator/
catalog/public mutations; Jobs — 11 domain jobs/V2/Notification admit; Runtime — Auth ownDB
session/credential read paths; Bot — Python; Producer — BFF/Attendance/Schedule directed reads
и data-only renderer; Apps — infra/SDK/build/lock и двухфайловый DOCX memory fix.
Independent reviewer только читает, findings исправляет автор. CI author завершил чтение: subject OTP Docker inspect/parsing UNKNOWN;
semester preview transport UNKNOWN (restoration PASS); recovery application RuntimeError
без точной команды. Product causes не установлены, promotion/schedule прошли.

Runtime lease завершён; его точный25v4 patch/evidence сохранён в workspace WIP.
Apps lease завершён; Jobs CPU0 выполняет compile/11адресных tests. Разные outputs/caches,
максимум два heavy, TMP floor4GiB. Local DB/container/image pull не запускались.
Existing backup/testrestore consumer: s19_recovery_consumer, isolated WTbase4e,
ровно recovery.py +два existing tests +test-restore compose для auth_db;
это source compatibility, реальные backup/deploy отложены.
Coherent ready WIP сохранён один раз в ignored workspace; не milestone и не Git raw logs.
Дальше: принять адресный DOCX memory результат и опубликовать готовый независимый пакет;
закончить связное split wiring, затем реальные twoDB/failure/recovery/consumer проверки.

[Evidence/history](attempts.ndjson), [decisions](decisions.md), [questions](questions.md),
[единственный HANDOFF](HANDOFF-20261017.md) обновляются вместе связнымcheckpoint.
Существенных вопросов владельцу пока нет. No main/merge/deploy/reset/secrets/Angular/newweb.
B013/B014/B015FROZEN, никаких эквивалентов черезCI/model/agent/launcher.
DEFERRED_LOCAL/OWNER: realTelegram/push/install/Safari/alertbot/offhostprobe/ownerstand/
realdeploy/backup/VPS/multiworker. ИсторическийsyncWORD crashcauseUNKNOWN.
