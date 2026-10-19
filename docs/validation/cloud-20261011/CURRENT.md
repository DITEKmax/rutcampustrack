# CURRENT — Auth/Academic, пользовательские операции и наблюдаемость

Persisted Goal Q031/D037 **IN_PROGRESS**, без дедлайна: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md), [TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md), [ASYNC](TASK-ADDENDUM-ASYNC-20261017.md).
Ветка `codex/server-validation-20261011`, [draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2). Starting `26cbe5912f6683a81780a831609dbddbc26feba4`; опубликованный HEAD **57054c6e8092c7d6d2b69f3e6a66c7006a5fd2d0**. Нормальный checkpoint11файлов/7source4docs опубликован, hooks PASS без drift; remote/PR HEAD проверены, draft/open, main не merged. App attachment SUCCESS; повторный idempotent attach не вернул ответ, не повторяется. Чужие25untracked сохранены.

[Матрица v3 FROZEN](selected-results-20261018-v3.csv) / [веса](selected-results-20261018-v3.meta.json): **38/39, вес191/196 =97.45% принятого плана**, 36 DONE_CHANGED +2 ALREADY_SATISFIED. Это не production readiness. Исторические v1 23/25, v2 24/38, TASK17 9/9 сохранены.

| КТ | Закрыто / всего | Вес / всего | % плана |
| --- | --- | --- | --- |
| КТ2: границы/AAA/контракты/сохранность | 14/14 | 71/71 | 100 |
| КТ3: бот/приложения | 9/9 | 37/37 | 100 |
| КТ4: цепочки/мониторинг/задания | 15/15 | 83/83 | 100 |
| КТ5: итоговые gates/review/handoff | 0/1 | 0/5 | 0 |

| Остаток | Доказано; конкретное действие |
| --- | --- |
| U25 | На570 Coverage/Security/OpenAPI SUCCESS, ordinary Academic и весь push client SUCCESS, включая2DOCX/бот/bootstrap/cleanup. Push CI39212562754 FAILURE: D9 SETUP/DLQ_READY, deadReady1/own4APPLIED/globalpending; INITIAL_MEMBERSHIP ACADEMIC_UNAVAILABLE1 ещё не связан с конкретным pending/message. Fresh-restore RESTORE_SCRIPT/GUARD/OS_ERROR exit1. Нужны исправления подтверждённых причин и конечные совместимые gates/review/handoff. |

## Текущая работа

**Restore:** Report sole writer scripts/recovery.py +CI. Reviewed patchf81d3a94 exact joined Root: stdin только2single DO; остальные -c/multi-statement/CREATE-DROP семантики сохранены. PureLinux /tmp/rct-restore-argv-rca-20261019/argv-control.safe.json: корректные258kSQL аргументы дают E2BIG, stdin сохраняет полные байты; Cache APPROVE. Реальный source bug устранён, но actual570errno/размер каталога UNKNOWN. ActualPGrestore остаётся обязательным ordinaryCI.

**INITIAL_MEMBERSHIP:** retry уже есть:3attempts+1confirmed30sredrive. Категория объединяет transport/auth/semantic guards, конкретная причина не доказана. Event2Java132158dc +CIincrementa6fdfbdd exactRootjoined, Cache APPROVE: initial-only конечные categorycodes в том же WARN/existingcategoryCounts; allowlist/sourcepin согласованы. Retry/business/ACK/oracles неизменны; startup-race не установлен.

**CI570:** Coverage39212571332, Security39212571290, OpenAPI39212571343 SUCCESS. Push39212562754 и PR39212571286 terminal FAILURE; оба ordinaryAcademic/fullclient/CLI SUCCESS. PR lifecycle117929047304 BOOT_ENV: renderer bootJar/официальный Gradle8.12 download SocketTimeout, D9/restore SKIPPED. Один штатный job-rerun дал403 run already running без эффекта; после terminal не повторять автоматически: сначала текущие исправления, старый job лишь при реально недостающем evidence. Timeout/oracles не меняются.

**Принятое не повторять:** U20.1/U20.2/U20.3 actual570fullbot PASS12.976s/0errors, artifact12169205427/safeSHAa16a8b14: actualTLS/qualified/completephase/allcontrols, headman/table/stats/unmarked/no-current/rebind/revoke/stale callback/finalattendanceunchanged. Настоящий Telegram DEFERRED_LOCAL. Текущая source verification: /tmp/rct-s19-integration-compile/current46/current46.safe.json SHA51816139. Native44/45 и прежнее evidence сохранены по неизменённым границам.

Root sole Git/taskdocs/matrix интегратор; Report restore/CI, Event INITIAL, Cache независимый reviewer, observer только meaningful570 metadata. Native46 PASS: compileJava/compileTestJava/test EXECUTED,17/0fail/errors/skips,88.16s/RSS1.138GB/output103.77MB; sourceeq/newPIDsreaped/leaseRELEASED. Heavy runtime отсутствует; Docker ниже4GiB резерва, без cleanup/reset/prune. Следующее: normal hooks и один8file normal commit/push/CI; actualPGrestore и INITIALcause ещё не приняты.

История один раз в [attempts](attempts.ndjson); [resources/owners](resources.md), [protocol](BOUNDARY-PROTOCOL-20261018.md), [decisions](decisions.md), [questions](questions.md). Safe primaries .agent/evidence/cloud-20261011/current-wip/ci-570-primary/; raw logs только private tool-store, не в файлах/Git. Вопросов владельцу нет. Q033 bootstrap actualCLI570 PASS; production-команда не выполнялась. Историческая причина WORD crash UNKNOWN.
**B013/B014/B015 FROZEN.** OWNER_MANUAL/DEFERRED_LOCAL: настоящий Telegram/push/install/Safari, alertbot/offhostprobe/ownerstand/deploy/backup/VPS/multiworker — не PASS. Обязательные env-blockers не исключать. Resume: .agent/evidence/cloud-20261011/current-wip/latest-checkpoint.safe.json.
