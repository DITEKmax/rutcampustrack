# ACTIVE — separate DB + apps / 18.10.2026

Persisted Goal Q031/D037: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md),
[TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md),
[ASYNC](TASK-ADDENDUM-ASYNC-20261017.md). Goal API нет; falsecomplete нет.
Root sole Git/taskdocs; codex/server-validation-20261011,
[draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2), no main/merge/deploy.
Starting26cbe5912f6683a81780a831609dbddbc26feba4; published/remote/PR
97eade5c7b0b6ea2ad6704cc3f94fbc76bf1a6d5. CI executable
 a0abad0406f978bb6f89e06909b6690639ca730c; GitHubcompare0changedfiles.

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
| U23.1/.2/.3/.4 | Actual GlitchTipVueJava/Pyro/protectedHomepage + measured combinedresources; PMM assessment U23.5 отдельно закрыт. |
| U25 | Current final CI/Coverage/Security/OpenAPI/monitoring, independent review, exact source/executable/PR handoff. |

## Текущие факты / границы evidence

97: CI39140801377 FAIL, Coverage39140801365 FAIL; Security39140801469 и
OpenAPI39140801394 SUCCESS (offline snapshots). Все7Javaunit/Gateway/BFF/
Attendance/Schedule/Notification IT SUCCESS; Monitoring117714643693 SUCCESS.
AuthIT182PASS18FAIL; AcademicIT90PASS408FAIL. Safe primary единожды:
.agent/evidence/cloud-20261011/current-wip/ci-97-primary/; история в attempts.
Browser оба Bootstack SUCCESS, затем FAIL на synthetic Telegram binding до UI.
Apps5containers running/noOOM; FAIL browser-ready до GTgroups/Pyro/resources.
Actual Academic JPA missingtable UNKNOWN; не объявлять все408 fixtureдефектами.

Принятый к публикации пакет поверх97: Auth cleanup SQL/currentidentity и admin
fixture canonicalTX/fence; Botfixture exactV11helperEXECUTE; внешний browserfixture
Sentry SDK resolver; один closedtablehint для exactSOURCE_METHOD failed Academic
OpenAPIcase. Privacy independent SOURCE APPROVE, existing39harness controls PASS;
unknown/ordinal/othercase не получает hint, statuses/oracles не меняются.
Browser binding fixture переводится на Auth-owned identity/DB; productionACL не меняются.
Runtime этих исправлений NOT_RUN; обычный CI необходим. Новых закрытых строк нет.

Native current11 compile+exact scheduler JUnit1/ArchTest1 PASS/sourceed609daa;
lease RELEASED/processes0. Presplit485PASS/createбез500/2DOCX и ac9 ownPG
job17/preflight5/adminNOOP2 — только прежняя/ограниченная применимость.
ИсторическийWORD incident/первыйSQLSTATE UNKNOWN. Frozen probes не повторять.

## Владельцы / следующий шаг

[Граница](BOUNDARY-PROTOCOL-20261018.md), [owners/resources](resources.md).
Root интегрирует один связный пакет; ac9_auth_it заканчивает exactbinding2.
s19_candidate_ci читает только три existing safe artifacts; остальные authors/reviewer
завершили свои corrections. OfficialAuth snapshot+генерация/81consumers PASS готовы;
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
