# CURRENT — Auth/Academic, пользовательские операции и наблюдаемость

Persisted Goal Q031/D037 **IN_PROGRESS**, без дедлайна: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md), [TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md), [ASYNC](TASK-ADDENDUM-ASYNC-20261017.md).
Ветка `codex/server-validation-20261011`, [draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2). Starting `26cbe5912f6683a81780a831609dbddbc26feba4`; published HEAD **1fbf23f8c3560013fe4d24090a63b14229c89fe5**. Текущий ready checkpoint:11source/test/workflow+5taskdocs, source review APPROVE. Main не merged, deploy не выполнялся. App attachment SUCCESS; дополнительный UNKNOWN-вызов не повторять. Foreign25 сохранены; NEW Schedule unit — явно owned.

[Матрица v3 FROZEN](selected-results-20261018-v3.csv) / [веса](selected-results-20261018-v3.meta.json): **37/39,183/196=93.37% принятого плана**,35 DONE_CHANGED+2 ALREADY_SATISFIED. U14.3 переоткрыт по actualarchiveFAIL; веса/знаменатель прежние, старое evidence сохранено. Это не production readiness. Исторические v1 23/25, v2 24/38, TASK17 9/9 сохранены.

| КТ | Закрыто / всего | Вес / всего | % плана |
| --- | --- | --- | --- |
| КТ2: границы/AAA/контракты/сохранность | 13/14 | 63/71 | 88.73 |
| КТ3: бот/приложения | 9/9 | 37/37 | 100 |
| КТ4: цепочки/мониторинг/задания | 15/15 | 83/83 | 100 |
| КТ5: итоговые gates/review/handoff | 0/1 | 0/5 | 0 |

| Остаток | Конкретное недостающее действие |
| --- | --- |
| U14.3 | Actual1fb initialmembershipbarrier PASS после fencefix, но D9 EXACT_REPLAY conjunction3677 FAIL; failedsubpredicate/cause UNKNOWN. Reviewed v6 сохраняет последний MAIN ledger/receipt/ACK snapshot и exactevent/ACK-joined closedcause доcleanup. Нужно подтвердить originalarchive/replay/dedup/restore на следующем actualCI; source-onlydiag не закрывает критерий. |
| U25 | Current1fb обаCI FAIL, Coverage/Security/OpenAPI/Apps/Monitoring/Academic/CLI SUCCESS. PRrestore seed503 после durableadoptioncommit: fixture теперь продолжает только exactoperation, без recreate, с прежним60sbudget. PRHW login429: normalresponse-onlydiagnostic, причинаUNKNOWN. Нужны finalcompatibleCI/review/handoff. |

**Ready16 пакет:** Event7 patch7ac8ee7f (6Java+semesterfixture), Report2 patch355ba7a3 (durable503continuation+2existingCIv6readers/sourcepin), R2 patch67eec277 (PWA429existingresponse→2fixedmarkers). Все exactRootjoined; Cache independent APPROVE/no findings. Retry/ACK/TX/INITIAL/400originalassertions и таймеры сохранены; v6frame2562B≤4096 прежнегоcap. Отозванный HW sessionreuse отсутствует: исходный spec fd5b45f9 exactHEAD, P1 закрыт withdrawal. Нет newcaller/probe/IP/limit/retrychanges, B014FROZEN.

**Проверка:** Native49 canonical `/tmp/rct-s19-integration-compile/current49/current49.safe.json` SHAab57f573:4compile+2testEXECUTED/2freshXML18+1PASS/0failerrorSkip,110.98s/RSS1256062976B/output112482576B≤128MiB; Java-inputsRoot2095/native2275/30absences stable/allnewPIDsreaped. Это Java source-equivalence, не полный app/runtimePASS. Pure fixture/reader/privacy controls применимы; hosted replay ещё не проверен.

**1fb gates, все terminal09:33:** push39225568080/PR39225578648 FAILURE; Coverage39225578578/Security39225578621/OpenAPI39225578551 SUCCESS. PRmergea671923f/source1fb exacttree87ef9bc4. ОбаAcademic/Apps/Monitoring/CLI PASS; fullpushclientPASS/PRHW429FAIL. Canonical `.agent/evidence/cloud-20261011/current-wip/ci-1fb-primary/`:12176540775 D9 EXACT_REPLAY/cleanupFAILED(aggregatefalse не доказывает PGstatePENDING);12176556048 seedSubject503ADOPTION_PENDING/restoreNOT_REACHED;12176272199 HWcase783/login429/other3PASS. Старый d5d realPGrestore12171184584 принят; новой restore-regression не установлено.

**Владельцы/следующее:** Root sole Git/taskdocs/matrix, authors Event/Report/R2 frozen/released, Cache APPROVE/released, Native49 released, observer1fb terminal/released. Heavy runtime0. Docker ниже4GiB reserve, без localDocker/DB/pulls/prune/reset. Следующее: scopedhooks→один normal16filecommit/push→обычный CI, один canonical failure artifact при необходимости. Новая причина/статус hashes не требуют полного повторного review. [Ресурсы](resources.md).

История один раз в [attempts](attempts.ndjson); [handoff](HANDOFF-20261017.md), [protocol](BOUNDARY-PROTOCOL-20261018.md), [decisions](decisions.md), [questions](questions.md). Raw logs только private tool-store, не в файлах/Git. Вопросов владельцу нет. Историческая причина WORD crash и exactoldmessagecausality UNKNOWN.
**B013/B014/B015 FROZEN.** OWNER_MANUAL/DEFERRED_LOCAL: настоящий Telegram/push/install/Safari/ownerstand, alertbot/offhostprobe/deploy/backup/VPS/multiworker — не PASS. Обязательные env-blockers не исключать. Resume: `.agent/evidence/cloud-20261011/current-wip/latest-checkpoint.safe.json`.
