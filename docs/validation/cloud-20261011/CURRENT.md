# ACTIVE — separate DB + apps / 18.10.2026

Persisted Goal Q031/D037: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md),
[TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md),
[ASYNC](TASK-ADDENDUM-ASYNC-20261017.md). Goal API нет; falsecomplete нет.
Root sole Git/taskdocs; codex/server-validation-20261011,
[draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2), no main/merge/deploy.
Starting26cbe5912f6683a81780a831609dbddbc26feba4; published/remote/PR
9928d388021f04ed6537cfabbcf7a91d09459849. CI executable
10c95978288d613fd7fbbed5141306b52b1f0859; GitHubcompare0changedfiles.

## Конечный план

[Матрица v2 FROZEN](selected-results-20261018-v2.csv)/[веса](selected-results-20261018-v2.meta.json):
**24/38,114/188=60.64% плана**, не production readiness.
[v1](selected-results-20261018.csv):23/25,110/123=89.43% в прежней применимости;
U15 sameDB не двеDB/VPS. TASK17 прежние9/9 сохраняются в исходных границах.

| КТ | Закрыто / всего | Вес / всего | % плана |
| --- | --- | --- | --- |
| КТ2 boundary/AAA/contracts/persistence | 9/13 | 37/63 | 58.73 |
| КТ3 bot/apps | 2/9 | 7/37 | 18.92 |
| КТ4 changedchains/monitoring/jobs | 13/15 | 70/83 | 84.34 |
| КТ5 finalgates/review/handoff | 0/1 | 0/5 | 0 |

| Остаток | Конкретное недостающее действие / зависимость |
| --- | --- |
| U14.2/.3/.5 | Actual twoDB/minrights; create/initialcredentials/grants/transfer/archive/proofs/currentrights/unknownACK/recovery через реальные SQL+AuthRPC. |
| U14.4/.6 | Actual Academic11jobs/singlequeue/2DOCX+короткий период/уход/lost202/reconnect/currentrights; official runtime snapshots→scoped TS generation. |
| U15.1 | Fresh/repeat двухDB сохраняют IDs/credentials/grants/history/sessions/jobs, включая additiveV10; no reset. |
| U20.1/.2/.3 | Actual private synthetic bot common4/studentHW/headmanroster/teacherToday, currentbinding/mixed/revoked new APIs. |
| U23.1/.2/.3/.4 | Actual GlitchTipVueJava/Pyro/protectedHomepage + measured combinedresources; PMM assessment U23.5 отдельно закрыт. |
| U25 | Current final CI/Coverage/Security/OpenAPI/monitoring, independent review, exact source/executable/PR handoff. |

## Текущие факты / границы evidence

9928: CI39144007011/Coverage39144006985 running with Academic FAIL;
Security39144007055/offlineOpenAPI39144006974 SUCCESS. Auth166unitPASS;
AuthIT199PASS/1OpenAPIdriftFAIL: passwordcleanup/adminprofile fixtures verified.
Academic652unitPASS; IT91PASS407FAIL. Primary единожды:
.agent/evidence/cloud-20261011/current-wip/ci-9928-primary/.
97 Monitoring117714643693 SUCCESS pinnedownDBACL+telemetry, не businesspersistence.
Текущие browser/Apps продолжаются; прежний97 browser BootSUCCESS/fixturelinkFAIL,
Apps5running/noOOM/timeout сохранены без выдачи за новый PASS.

Следующий пакет: exactclosedtablehint переведён на уже SOURCE_METHOD Homework
primarycase (native97 SchemaValidator136); OpenApi был вторичным contextthreshold145,
его новый hintABSENT_OR_UNRECOGNIZED не доказывает имя таблицы. Rootошибку target
исправил через прежнего автора, независимая affectedprivacy recheck APPROVE;
3affectedcontrolsPASS. Parser/24enum/caps/identity/status/oracles неизменны.
Actualmissingtable UNKNOWN. Никакого c62/ORDINAL исключения или frozenmutation.
U14.1 отдельно закрыт sufficient97evidence; прочие строки открыты.

Native current11 compile+exact scheduler JUnit1/ArchTest1 PASS/sourceed609daa;
lease RELEASED/processes0. Presplit485PASS/createбез500/2DOCX и ac9 ownPG
job17/preflight5/adminNOOP2 — только прежняя/ограниченная применимость.
ИсторическийWORD incident/первыйSQLSTATE UNKNOWN. Frozen probes не повторять.

## Владельцы / следующий шаг

[Граница](BOUNDARY-PROTOCOL-20261018.md), [owners/resources](resources.md).
9928d388 опубликован; новый hint2 sourceapproved и готов к normalpush.
CI9928 продолжает полезные независимые browser/Apps результаты без отмены.
Все authors/reviewer освободили scopes.
U14.1 принято независимо по actual97 ownDB/minrights и unchangedboundary;
прочие business/recovery/persistence остаются открытыми. OfficialAuth snapshot+генерация/81consumers PASS готовы;
Academic snapshot отсутствует. Atomic5 contracts/client ждут его, typealias WIP сохранён.
Следующий шаг: normalpush/CI → exactclosedmissingtable cause → исправление владельцем;
parallel actualbrowser/Apps результаты; existinggenerators после runtime snapshot.
Local Docker/DB/imagepull NOT_RUN: workspace<4GiB reserve, DockerVFS тот же FS;
no cleanup/limit lowering. Q032 ANSWERED, exact granted compilation уже выполнена.

[История/evidence](attempts.ndjson), [решения](decisions.md), [вопросы](questions.md),
[HANDOFF](HANDOFF-20261017.md); ignored current-wip/latest-checkpoint.safe.json.
B013/B014/B015 FROZEN. DEFERRED_LOCAL/OWNER: realTelegram/push/install/Safari/
alertbot/offhostprobe/ownerstand/realdeploy/backup/VPS/multiworker.
No reset/secrets/Angular retirement/newweb. Goal IN_PROGRESS, ownerquestion нет.
