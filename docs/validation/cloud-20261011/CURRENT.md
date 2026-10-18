# ACTIVE — separate DB + apps / 18.10.2026

Persisted Goal Q031/D037: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md),
[TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md),
[ASYNC](TASK-ADDENDUM-ASYNC-20261017.md). Goal API нет; falsecomplete нет.
Root sole Git/taskdocs; codex/server-validation-20261011,
[draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2), no main/merge/deploy.
Starting **26cbe5912f6683a81780a831609dbddbc26feba4**; published/remote/PR
**7394a897a33a7b828d817eb0660bd5336c930e58**. Checkout **d7d25b99…**:
checkout-safe+GitHubcompare подтверждают0changedfiles против7394; это не dirty WIP.

## Конечный план

[Матрица v2 FROZEN](selected-results-20261018-v2.csv)/[веса](selected-results-20261018-v2.meta.json):
**23/38,109/188=57.98% плана**, не production readiness.
[v1](selected-results-20261018.csv):23/25,110/123=89.43% в прежней применимости;
U15 sameDB не двеDB/VPS. TASK17 прежние9/9 сохраняются в исходных границах.

| КТ | Закрыто / всего | Вес / всего | % плана |
| --- | --- | --- | --- |
| КТ2 boundary/AAA/contracts/persistence | 8/13 | 32/63 | 50.79 |
| КТ3 bot/apps | 2/9 | 7/37 | 18.92 |
| КТ4 changedchains/monitoring/jobs | 13/15 | 70/83 | 84.34 |
| КТ5 finalgates/review/handoff | 0/1 | 0/5 | 0 |

| Остаток | Конкретное недостающее действие / зависимость |
| --- | --- |
| U14.1/.2/.3/.5 | Actual twoDB/minrights; create/initialcredentials/grants/transfer/archive/proofs/currentrights/unknownACK/recovery через реальные SQL+AuthRPC. |
| U14.4/.6 | Actual Academic11jobs/singlequeue/2DOCX+короткий период/уход/lost202/reconnect/currentrights; official runtime snapshots→scoped TS generation. |
| U15.1 | Fresh/repeat двухDB сохраняют IDs/credentials/grants/history/sessions/jobs, включая additiveV10; no reset. |
| U20.1/.2/.3 | Actual private synthetic bot common4/studentHW/headmanroster/teacherToday, currentbinding/mixed/revoked new APIs. |
| U23.1/.2/.3/.4 | Actual GlitchTipVueJava/Pyro/protectedHomepage + measured combinedresources после networkfix; PMM assessment U23.5 отдельно закрыт. |
| U25 | Current final CI/Coverage/Security/OpenAPI/monitoring, independent review, exact source/executable/PR handoff. |

## Факты / классификация

7394: CI39134844701 иCoverage39134844724 FAIL; Security39134844726 иOpenAPI39134844710
SUCCESS. OpenAPI проверяет committed offline snapshots, не новые runtime contracts.
Auth/Academic ordinaryIT были SKIPPED: старые source pins/users-SQL/export paths
несовместимы с split. Native source/компиляция не заменяют actualtwoDB acceptance.

Root WIP после7394, source-reviewed/точно перенесён:
- Product fixes: field/status validation, Academic employee preflight +UNIQUE,
  explicit empty AdminProfile при обычномcreate, durable confirmed Telegram conflict
  subtype/replay (новаяV10). SQL/HTTP wholechain ещё NOT_RUN.
- Fixtures: Auth obsolete status helper→native13PASS; BFF unused TLS listener
  disabled→real synthetic HTTP/gRPC70PASS; Headman freshsame3s/perRPC→1PASS.
  Academic24User+16recoveryPASS; Headman1 перепроверен отдельно, прежние40 не повторялись.
- Apps private network: approved proxy-only noninternal bridge; CI-only GlitchTip/Pyro
  loopback ports. На7394 все5containersrunning/noOOM, portlookupFAIL; patchedruntimeNOT_RUN.
- CI compatibility: ordinary tests после source restoration независимо от redproof;
  current owner/source/oracles. Review поймал пропуск Attendance classifier; исправлен, affected recheck APPROVE.

Native safe evidence: current-wip/integration-compile/compile7.safe.json,
runtime8.safe.json, compile9.safe.json иtest10.safe.json. OriginalFAIL сохранён.
Unknown: Academic ordinal1, Gateway6FAIL RCA, историческийWORD incident/первыйSQLSTATE.
Presplit485PASS/createбез500/2DOCX/b9reconciliation сохранены только по применимости.
CI primary один раз: .agent/evidence/cloud-20261011/current-wip/ci-7394-primary/.

## Исполнение / следующий шаг

[Граница](BOUNDARY-PROTOCOL-20261018.md), [owners/resources](resources.md).
Следующий шаг: precommit иnormalpush связного пакета; CI1 SOURCE APPROVE.
Затем реальные ordinaryIT/currentcontracts; готовые PASS не повторять без причины.
Official Auth/Academic snapshots пока отсутствуют; один type-only report client WIP
сохранён до snapshots+existing generators, без ручных spec/generated edits.
Native heavy lease RELEASED/processes0. Local Docker/DB/imagepull NOT_RUN:
workspace ниже4GiB reserve, DockerVFS на том же filesystem; no cleanup/limit lowering.
Q032 ANSWERED; текущие разрешённые isolated checks выполнены без обходов auto-review.

[История/evidence](attempts.ndjson), [решения](decisions.md), [вопросы](questions.md),
[HANDOFF](HANDOFF-20261017.md); freshest ignored checkpoint current-wip/latest-checkpoint.safe.json.
B013/B014/B015 FROZEN без эквивалентов. DEFERRED_LOCAL/OWNER:
realTelegram/push/install/Safari/alertbot/offhostprobe/ownerstand/realdeploy/backup/VPS/multiworker.
No reset/secrets/Angular retirement/newweb.
