# CURRENT — Auth/Academic, пользовательские операции и наблюдаемость

Persisted Goal Q031/D037 **IN_PROGRESS**, без дедлайна: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md), [TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md), [ASYNC](TASK-ADDENDUM-ASYNC-20261017.md).
Ветка `codex/server-validation-20261011`, [draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2). Starting `26cbe5912f6683a81780a831609dbddbc26feba4`; опубликованный HEAD **d5d7d4c633fdd9edb438026fadbcefc3c69ca92e** + WIP2Advice/5taskdocs. Main не merged, deploy не выполнялся. App attachment SUCCESS; дополнительный UNKNOWN-вызов не повторять. Foreign25 сохранены, новый тест явно owned.

[Матрица v3 FROZEN](selected-results-20261018-v3.csv) / [веса](selected-results-20261018-v3.meta.json): **37/39,183/196=93.37% принятого плана**,35 DONE_CHANGED+2 ALREADY_SATISFIED. U14.3 переоткрыт по actualarchiveFAIL/sourcebug; веса/знаменатель прежние, старое evidence сохранено. Это не production readiness. Исторические v1 23/25, v2 24/38, TASK17 9/9 сохранены.

| КТ | Закрыто / всего | Вес / всего | % плана |
| --- | --- | --- | --- |
| КТ2: границы/AAA/контракты/сохранность | 13/14 | 63/71 | 88.73 |
| КТ3: бот/приложения | 9/9 | 37/37 | 100 |
| КТ4: цепочки/мониторинг/задания | 15/15 | 83/83 | 100 |
| КТ5: итоговые gates/review/handoff | 0/1 | 0/5 | 0 |

| Остаток | Конкретное недостающее действие |
| --- | --- |
| U14.3 | Currentarchive ждёт INITIAL ACK. Sourcebug: identity fence UNAVAILABLE→genericAdvice INTERNAL, клиент не повторяет. Reviewed2filefix Rootjoined; Native48 PASS→normalpush/realD9/archive. Связь с конкретным старым сообщением UNKNOWN. |
| U25 | Final совместимые CI/review/handoff послеU14.3. d5d Coverage/Security/OpenAPI/Apps/Monitoring/Academic/clients/CLI SUCCESS, обаCI FAILURE(D9/archive); realPGrestore PASS. |

**Активный пакет:** Advice dd59a623 +NEWtest242900cb; patch8b5f0c04 +test-onlyincrement667b7529. Только2собственных статических fenceописания→freshsafeUNAVAILABLE после прежнего UnknownAssignment guard; остальные handlers/retry/SQL/grants/CI неизменны. Report автор frozen; Cache independent APPROVE и addressed recheck APPROVE. Native47 maincompilePASS/testcompileFAIL(CHECKED_EXCEPTION)/testNOT_REACHED сохранён. Исправлена только setup throws declaration,4casebodies неизменны. Native48 PASS: mainUP_TO_DATE/compiledclass pin6a67c079 preserved, compileTestJava/testEXECUTED,1freshXML4/0failerrorSkip,89.50s/RSS1073369088B/output91149276B≤128MiB. Canonicalcurrent48 SHA6ac1c415, sourceeq/allnewPIDsreaped.

**d5d CI, все terminal:** push39217861708/PR39217871620 FAILURE; Coverage39217871797/Security39217871504/OpenAPI39217871732 SUCCESS. D9 initialRPC_OTHER1/globalpending; semester12172265821 ARCHIVING/INITIAL_ACKpending. SCHEDULE-only locator66 для этого reason неприменим, не отдельный productFAIL. Realrestore12171184584 seed/pending/admit/attachment PASS: freshinventory/history/originaltransferreplay/revocation/attachment. Canonical primaries: `.agent/evidence/cloud-20261011/current-wip/ci-d5d-primary/`.

**Владельцы/ресурсы:** Root sole Git/taskdocs/matrix; Report/Cache released, Native48 PASS/released, observer d5d terminal/released. Native48 stricter outputcap128MiB по measured100.64MB/test-only estimate105–115MB; TMP4GiBfloor/CPU/RSS/time/sourceguards прежние, без cleanup. Docker ниже4GiB reserve, без localDocker/DB/pulls/prune/reset. Следующее: normal7filecheckpoint/push→обычный CI; productFAIL закрывается только realarchive/D9. [Ресурсы](resources.md).

Принятое не повторять без изменённого риска: DOCX/bot/bootstrap/currentrights, прочие v3 результаты; Native46 source-equivalentd5d,17PASS, canonicalcurrent46 SHA51816139. Историческая причина WORD crash и actual570errno UNKNOWN.
История один раз в [attempts](attempts.ndjson); [handoff](HANDOFF-20261017.md), [protocol](BOUNDARY-PROTOCOL-20261018.md), [decisions](decisions.md), [questions](questions.md). Raw logs только private tool-store, не в файлах/Git. Вопросов владельцу нет.
**B013/B014/B015 FROZEN.** OWNER_MANUAL/DEFERRED_LOCAL: настоящий Telegram/push/install/Safari/ownerstand, alertbot/offhostprobe/deploy/backup/VPS/multiworker — не PASS. Обязательные env-blockers не исключать. Resume: `.agent/evidence/cloud-20261011/current-wip/latest-checkpoint.safe.json`.
