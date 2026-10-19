# CURRENT — Auth/Academic, пользовательские операции и наблюдаемость

Persisted Goal Q031/D037 **IN_PROGRESS**, без дедлайна: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md), [TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md), [ASYNC](TASK-ADDENDUM-ASYNC-20261017.md).
Ветка `codex/server-validation-20261011`, [draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2). Starting26cbe591; последний опубликованный и проверенный executable **35078fef**. Все его runs завершены, ничего не отменялось. Сейчас готовится один связный диагностический checkpoint: 6 source/test/workflow files и 4 taskdocs; публикация и новое runtime evidence ещё впереди. Root — единственный Git/taskdocs/matrix writer. Index пуст; foreign25 и прежние WTs сохранены. Guard подтверждает прежний rawNUL список foreign paths; это не новый content hash их файлов. Main/merge/deploy не выполнялись. PR2 attachment существует; supplemental UNKNOWN не повторять.

[Матрица v3 FROZEN](selected-results-20261018-v3.csv) / [веса](selected-results-20261018-v3.meta.json): **37/39, 183/196 = 93.37% принятого плана**; 35 DONE_CHANGED +2 ALREADY_SATISFIED. Открыты U14.3/U25. Веса/знаменатель неизменны. Это не production readiness; исторические v1 23/25, v2 24/38 и TASK17 9/9 сохранены.

| КТ | Закрыто / всего | Вес / всего |
| --- | --- | --- |
| КТ2: границы/AAA/контракты/сохранность | 13/14 | 63/71 |
| КТ3: бот/приложения | 9/9 | 37/37 |
| КТ4: цепочки/мониторинг/задания | 15/15 | 83/83 |
| КТ5: итоговые gates/review/handoff | 0/1 | 0/5 |

**U14.3 REOPENED — точный D9 PR FAIL на35078.** Safe primary12196083474/f6d2c314, runnerc8d61704: case2782/error4889/157604ms, PENDING_ARCHIVE; delete-preview503/transportnull/три identity null. Unique correlated source=SCHEDULE_CLIENT/stage=SCHEDULE_PREVIEW/status=UNAVAILABLE/cause=CONNECT. Это Academic→Schedule, до MAIN archive/replay. Fresh READY относится к каналу Attendance. CleanupPASSED/clocktrue; applied/restorationtrue достигнуты только cleanup. Зависимые restore62/participants64 SKIPPED — не отдельная регрессия restore.
Push primary12195906657/0377feb7 +runnerb07b6ff6 сохраняет отдельный полный PASS MAIN DUPLICATE/all19/APPLIED/ONE claim+receipt+MAIN ACK/sameArchiveCompleted/duplicateBusinessUnchanged/retainedDLQ1/restore/participants. Последующая duplicate delivery даёт дополнительный ACK без повторного business effect; общий ACK count1 не выдумывать. Configured listener attempts3, measuredUNKNOWN. Push PASS не отменяет PR counterFAIL. Historical DNS/guard/WORD causes не переименованы в новую исключительную причину.

**U25 OPEN — Mark500 и совместимые final gates.** Push primary12195651054/e5234364: mobile case5794/error5915/2170ms DATASET_ATTENDANCE_MARK_HTTP HTTP500 в подготовительном HEADMAN PUT own lesson/student, до Python/menu. Непосредственно перед упавшим PUT прошли GETreport200+EMPTY guard внутри ABSENT/PRESENT loop. Итерация, exception и commit outcome UNKNOWN; обе EMPTY проверки до сбоя не доказаны. PR bot PASS не классифицирует прежний FAIL как environment. Blind retry/изменение EMPTY/statuses не разрешены.

**Разовый Astra/high RCA завершён.** Owner19.10 разрешил один fresh fork-none read-only разбор обоих сбоев; пара задана явно, actual runtime metadata UNKNOWN (не экспонируются). Файлы/запуски/children/defaults не менялись, участие ENDED; повтор только с новым ownerGO. Проверены четыре safe-primary SHA. Установлены transport failure и readiness coverage gap, но фактические state/адрес/порт/причина ConnectException не сохранены. Preview уже создаёт RPC demand: eager getState(true) не принят как исправление наблюдавшегося CONNECT. Passive joint READY также не принят. Поддерживаемость native factory hook проверена Event+Cache, реализации нет. По Mark500 достаточно уже одобренного capture; никаких предположительных product fixes.

**Текущий ограниченный пакет — диагностика, не восстановленный продукт.**

- Mark500: Event producer0d2190cc/postdb4db9bf и Report consumer284ca7f7 интегрированы точно; независимый Cache coherent APPROVE. Только точный hosted case/HEADMAN/private run; first UUID witness приватен, correlation→owned logger/exception/frame→конечные Mongo code/labels/site. Site — место стека, не commit outcome. Legacy/default, bounded capture и красный итог сохранены.
- D9v10: Event fixture27aed9ce/postcb676059 и Report Java/CI increment интегрированы точно. Nullable scheduleTransport содержит passive FACTORY_WATCH_SNAPSHOT before/catch, connectKind REFUSED/TIMEOUT/NO_ROUTE/OTHER/UNKNOWN, endpointRelation UNKNOWN. Это не fresh ManagedChannel state или доказательство перехода. Нет create/getState(true)/warm-up/RPC/reset/factory override; старые RPC/deadlines/error mapping/assertions сохранены.
- Report final client5b396cdf/helperc133a0fb/test69b62d71/CI7a3b6374, wire232c9c5d. Addressed CIac9a4115 исправляет только malformed-schema membership; Java/TS неизменны. Fixture parser/privacy/malformed controls, CI legacy3–9/typed vectors, Java source/business/privacy projections и formatter/parse/inverse PASS. Новые 5 JUnit methods WRITTEN, **compile/JUnit NOT_RUN_ENV**. Coherent Cache APPROVE/no findings, включая адресный malformed-schema recheck; final source pins/Root bytes совпали. Source hooks PASS_NO_DRIFT до последнего CI-only increment; итоговые scoped hooks перед commit обязательны.

| Gate exact35078 | Run | Итог |
| --- | --- | --- |
| Push CI | 39267587144 | FAILURE: hostedbot prerequisite Mark500; D9/restore/participants SUCCESS |
| PR CI | 39267606006 | FAILURE: D9 readiness503 CONNECT; all client steps/CLI SUCCESS; restore/participants SKIPPED |
| Coverage | 39267605898 | SUCCESS: Academic+diff |
| Security | 39267605847 | SUCCESS: actual Gitleaks/TrivyFS/config; images SKIPPED |
| OpenAPI | 39267605823 | SUCCESS |

В обоих runs Vue install/contracts/typechecks/workerchecks/build, Academic IT, Apps, Monitoring, asyncDOCX, Remaining5/D002, role, cleanup, actualCLI72/upload73 SUCCESS. U18 принят на35078 по полному D0023957/4569ms/current authority/late old feed/new-account/offline isolation и revoked3509; Cache independent applicability confirmed. Старые достаточные PASS без изменения зависимости не повторять. Gitleaks narrow metadata fix принят; default208/full history/unknown+credential veto/18 RED сохранены. Все старые runs terminal; polling stopped.

**Владельцы и следующий шаг:** Report sole Java+CI/closed contract в /tmp/rct-s19-mark500-consumer-350; Event sole D9TS в /tmp/rct-s19-mark500-producer-350; baseline обоих35078, Mark WIP сохранён. Cache — независимый reviewer, root интегрирует только точные packets. Astra завершён; observer остановлен после terminal35078. Source review завершён; выполнить final scoped hooks и один normal commit/push в текущую ветку, обновить draft PR2 и использовать ordinary CI. Новый постоянный runner/framework не создаётся. Подтверждённый exception/transport evidence затем определит необходимый fix; source-ready не закрывает U14.3/U25.

**Ресурсы:** heavy0, local Docker/DB/pulls/prune/reset/cleanup не запускались. Native51c8aad011 прежние4compile+11freshPASS/162.45s/peakRSS1296678912B/output117108725B сохранён в прежней применимости; он не компилировал новый helper. Новая минимальная Academic compileJava+compileTestJava+pure test подготовка ENV_BLOCKED: TMP4418719744B < floor4294967296+full128MiB reserve, нехватка10465280B; workspace4178694144B проходит. Overlay/Gradle не запускались, резерв не снижен. Новая Java проверяется ordinary CI. Перед новым local runtime нужны свежая capacity и lease. [Resources](resources.md).

Evidence единожды в ignored ci-35078-primary и packets; [attempts](attempts.ndjson), [handoff](HANDOFF-20261017.md), [protocol](BOUNDARY-PROTOCOL-20261018.md), [decisions](decisions.md), [questions](questions.md). Вопросов владельцу нет. Resume: `.agent/evidence/cloud-20261011/current-wip/latest-checkpoint.safe.json`.
**B013/B014/B015 FROZEN.** OWNER_MANUAL/DEFERRED_LOCAL: Telegram/push/install/Safari/owner stand, alertbot/offhost probe/deploy/backup/VPS/multiworker — не PASS. Production-ready не заявляется; исходная внешняя приёмка отдельно.
