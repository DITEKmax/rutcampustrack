# ACTIVE — separate DB + apps / 18.10.2026

Persisted Goal Q031/D037: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md),
[TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md),
[ASYNC](TASK-ADDENDUM-ASYNC-20261017.md). Goal API нет; falsecomplete нет.
Root sole Git/taskdocs; codex/server-validation-20261011,
[draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2), no main/merge/deploy.
Stage19 base **26cbe5912f6683a81780a831609dbddbc26feba4**.
Published/remote/PR HEAD **da2cb606f751990d4ad8c8af4ff6ec07b3974bf4**.
Последний commit: только Apps startup2; весь согласованный split source join остаётся
незакоммиченным. Это не проверенный twoDB executable; половину switch не push.

## Конечный принятый план

[Матрица v2 FROZEN](selected-results-20261018-v2.csv)/[веса](selected-results-20261018-v2.meta.json):
**23/38,109/188=57.98% плана**, не production readiness. Историческая
[v1](selected-results-20261018.csv):23/25,110/123=89.43%; U15 sameDB не двеDB/VPS.
TASK17 старые9/9 сохранены в прежней применимости; достаточные неизменённые PASS не повторять.

| КТ | Закрыто / всего | Вес / всего | % плана |
| --- | --- | --- | --- |
| КТ2 boundary/AAA/contracts/persistence | 8/13 | 32/63 | 50.79 |
| КТ3 bot/apps | 2/9 | 7/37 | 18.92 |
| КТ4 changedchains/monitoring/jobs | 13/15 | 70/83 | 84.34 |
| КТ5 finalgates/review/handoff | 0/1 | 0/5 | 0 |

| Остаток | Ответственный → недостающая достаточная проверка |
| --- | --- |
| U14.1/.2/.3/.5 | Auth+Academic: ownDB/minrights; create/initialcredentials/grant/transfer/archive/proofs, unknown/revoke/recovery/currentrights SQL+HTTP. |
| U14.4/.6 | Jobs+FE:11jobs/singlequeue/API/consumers;2DOCX+короткий период/уход/lost202/currentrights; actual snapshots/scoped TS generation. |
| U15.1 | Auth+Academic: fresh/repeat двухDB сохраняют IDs/credentials/grants/history/sessions/jobs; newSQLonly. |
| U20.1/.2/.3 | Bot+Producers: common4/studentHW/headmancurrentroster/teacherToday; private freshbinding/empty/mixed/revoke actual new APIs. |
| U23.1/.2/.3/.4 | Apps: actual GlitchTipVueJava/Pyro/protectedHomepage и measured combinedresources. PMMassessment U23.5 отдельно закрыт. |
| U25 | Root+review: current final CI/Coverage/Security/OpenAPI/monitoring; exact source/executable/PR head handoff. |

## Подтверждённое / актуальные FAIL

Published da2: CI39129621591 FAIL; Coverage39129621580/Security39129621631/
OpenAPI39129621656 SUCCESS. Java/monitoring/client jobs PASS, прежние2asyncDOCX
сохранены. Application checkpoint restore PASS. Это presplit, не dirty twoDB.
Открыты: subject Docker inspect ETIMEDOUT; semester readiness GET transport до archive
POST (причина UNKNOWN, barrier не опровергнут); Apps proxy READ_ONLY_FS.
Pyroscope running/noOOM; Apps cleanupPASS. Исправление proxy temp paths source-approved
и joined, ещё не проверено runtime. Старый WORD incident/первый SQLSTATE UNKNOWN.
Presplit прежние9 AcademicFAIL устранены:485PASS/0FAIL/0skip,create без500.
На b9 sameArchiveCompleted/duplicateBusinessUnchanged/restoration true,cleanupPASS;
не повторять неизменённое без причины. Safe primary current-wip/ci-da2-*.safe.json.

## Исполнение / следующий шаг

[Граница](BOUNDARY-PROTOCOL-20261018.md), [owners/resources](resources.md).
В coherent Root WIP объединены source-approved Auth/core/jobs/commonfixture/consumers,
FAILED_SAFE reconcile15 с additiveV9/сохранением исходного UID/всех fences/currentrights,
старые9 сценариев, Dashboard5+clock fix и recovery UI9+transient authority fix.
Независимые существенные findings закрыты; actual twoDB/TLS/SQL/fullchain NOT_RUN.
Native current4:14/14 compileJava/compileTestJava PASS,2252paths1e64095f…,
221.27s/RSS1.49GB. Current5: finalRecovery+old9consumers Academic Java/TestJava
и16 адресных pure cases PASS,2259paths e38603a5…,92.60s/RSS1.18GB.
Safe evidence current-wip/integration-compile/compile4.safe.json и compile5.safe.json.
UI scoped client6/screen15 PASS; affected P2 causalRED2→PASS5/type/lint/format PASS.
Dashboard new source compileTestJava PASS: current6/65.39s/RSS0.85GB,
safe current-wip/integration-compile/compile6.safe.json; DB/runtime не доказан.
Q032 ANSWERED/точный запуск допущен; offline Sentry найден в существующем cache.
NativeIT NOT_RUN: workspace ниже4GiB reserve, DockerVFS на том же filesystem.
No cleanup/limit lowering; actual twoDB/runtime выполняется ordinary CI.

Следующий шаг: exact-owned stage/normalpush
coherent draft candidate; CI даёт реальные twoDB и official Auth/Academic snapshots.
Не включать type-only report-download-client.ts до этих snapshots и scoped generators;
сохранённый WIP остаётся локально, committed Auth.Status wire-compatible.
Не ручной OpenAPI/generated types и не ослабление gate. Proxy/CI JSON brace corrections
включаются в тот же пакет; новый hash не повод повторять неизменённое полное review.
Матрица остаётся23/38: source/compile не достаточны для runtime приёмки.

[История/evidence](attempts.ndjson), [решения](decisions.md), [вопросы](questions.md),
[HANDOFF](HANDOFF-20261017.md); freshest ignored checkpoint current-wip/latest-checkpoint.safe.json.
B013/B014/B015 FROZEN без эквивалентов. Local Docker/DB/imagepull не запускались.
DEFERRED_LOCAL/OWNER: realTelegram/push/install/Safari/alertbot/offhostprobe/ownerstand/
realdeploy/backup/VPS/multiworker. No reset/secrets/Angular retirement/newweb.
