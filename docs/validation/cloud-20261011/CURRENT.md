# ACTIVE — separate DB + apps / 18.10.2026

Persisted Goal Q031/D037: [новый GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md),
[TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md),
[ASYNC](TASK-ADDENDUM-ASYNC-20261017.md). Goal API нет; falsecomplete нет.
Root soleGit/taskdocs, codex/server-validation-20261011,
[draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2), main не merged.
Stage19 base **26cbe5912f6683a81780a831609dbddbc26feba4**.
Последний проверенный опубликованный executable **a69dbbd3bbee3471e860799db7b936ddf9a2f5cc**;
следующий source checkpoint добавляет готовые6 файлов ниже. Его SHA — Git HEAD;
приведённые CI результаты относятся к a69, не к следующему diff.

## Принято / конечный остаток

[Матрица v2-КТ1 FROZEN](selected-results-20261018-v2.csv)/[веса](selected-results-20261018-v2.meta.json):
**23/38,109/188=57.98% плана**, не production-readiness. Историческая
[v1](selected-results-20261018.csv)/[meta](selected-results-20261018.meta.json):
23/25,110/123=89.43%; U15 sameDBrepeat не две новыеDB/VPSrestart.
TASK17 старые9/9 сохранены в прежней применимости. Нет double parent/child credit.
Достаточное неизменённое evidence не повторять; изменённый путь требует новой проверки.

| КТ | Закрыто / всего | Вес / всего | % плана |
| --- | --- | --- | --- |
| КТ2 boundary/AAA/contracts/persistence | 8/13 | 32/63 | 50.79 |
| КТ3 bot/apps | 2/9 | 7/37 | 18.92 |
| КТ4 changedchains/monitoring/jobs | 13/15 | 70/83 | 84.34 |
| КТ5 finalgates/review/handoff | 0/1 | 0/5 | 0 |

| Остаток | Владелец → минимальный достаточный результат |
| --- | --- |
| U14.1/.2/.3/.5 | Boundary+Academic → ownDB/minrights, create/grant/transfer/archive/proofs с durable recovery/fences; actual SQL/HTTP/failure/revoke. |
| U14.4/.6 | Jobs+Boundary+IdentityUI → 11jobs/singlequeue и согласованные API/consumers; 2DOCX+иной короткий период/уход/lost202/currentrights. |
| U15.1 | Academic+Boundary → fresh/repeat двухDB сохраняют IDs/credentials/grants/history/sessions/jobs; newSQLonly. |
| U20.1/.2/.3 | Bot+Producers → common4/studentHW/headmancurrentroster/teacherToday, privatefreshbinding/roles/empty/mixed/revoke. |
| U23.1/.2/.3/.4 | Apps → реальные GlitchTipVueJava/Pyro/защищённыйHomepage + measured combinedresourcebudget. U23.5 закрывает только PMMnonselection. |
| U25 | Root+review → finalCI/Coverage/Security/OpenAPI/monitoring и точный handoff/source/executable/PRhead. |

## Что действительно проверено

Presplit3 на da устранил прежние9 AcademicFAIL: 485PASS/0FAIL/0skip; браузерный
create перестал получать500. Точный первоначальный SQLSTATE и historical WORD crash UNKNOWN.
На **a69** Coverage39115359592/Security39115359631/OpenAPI39115359612 SUCCESS;
client117639949326 SUCCESS, включая2async18weekDOCX/navigation/lost202.
CI39115359580 FAIL: lifecycle47subject OTPinspection UNKNOWN;57semesterpreview
transport UNKNOWN/status0, cleanupFAILED/restorationfalse; application60 SKIPPED по
зависимости. Promotion/assignment/Today/schedule прошли. Не выдавать masking за RCA.
Apps117639949553 FAIL isolated-startup после Java baseline; cleanup PASS.

Готовый diff6: Homepage tmpfs теперь две quoted строки вместо шести неверных YAML
entries; существующие безопасные firstfailure diagnostics сохраняют конечные
class/action/errno/exitstatus. Команды/guards/assertions/timeouts/heap/limits прежние.
Root independent SOURCE APPROVE, effectiveCompose synthetic/privacy/AST/6hooks PASS;
actual Apps/grouping/profile/resource acceptance открыты. Детали один раз в attempts.

Streaming DocxRenderer a69 — legacy fixed-layout profiling utility, НЕ текущий
Headman renderer. JUnit3/3 и неизменённый Probe192MiB PASS; safe before/after:
[receipts/S19-docx-streaming-memory](receipts/S19-docx-streaming-memory/).
Не приписывать ему причину historical owner incident.

## Текущая реализация / следующий шаг

[Техническая граница DRAFT](BOUNDARY-PROTOCOL-20261018.md), [owners/resources](resources.md).
Auth core/runtime, Jobs58, Attendance renderer23 и AcademicBot7 имеют независимый
SOURCE APPROVE и адресные compile/tests. Actual twoDB/новый fullchain NOT_RUN.
Academic R6 main/test compile+34uniquePASS; source review нашло3 SQL/membership/archive
findings, исправляет тот же автор. Config17 review:3 secret/localDB/SLIselector findings.
IdentityUI8 review:3 observation403/bodydeadline/groupresume races. Нужен focused recheck;
source/compile сами по себе не закрывают строки. Полные findings не копировать сюда.

Следующий шаг: опубликовать готовый diff6; параллельно закрыть review findings,
local durable negative receipt и group-lifecycle security fanout. Затем coherent
Auth+Academic+Jobs+contracts+consumers integration, fresh/repeat/failure и настоящие
PWA/TMA/report/bot/App проверки. Не публиковать половину DB switch.
Local DB/container/imagepull не запускались; heavy только по lease.
Coherent WIP/ready patches сохранены один раз в ignored workspace, не milestone.

[История/evidence](attempts.ndjson), [решения](decisions.md), [вопросы](questions.md),
[единственный HANDOFF](HANDOFF-20261017.md). Существенных owner вопросов сейчас нет.
B013/B014/B015 FROZEN без эквивалентов черезCI/model/agent/launcher.
DEFERRED_LOCAL/OWNER: realTelegram/push/install/Safari/alertbot/offhostprobe/ownerstand/
realdeploy/backup/VPS/multiworker. No main/merge/deploy/reset/secrets/Angular/newweb.
