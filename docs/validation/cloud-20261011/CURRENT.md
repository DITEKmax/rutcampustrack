# CURRENT — Auth/Academic, пользовательские операции и наблюдаемость

Goal Q031/D037 **IN_PROGRESS**, без дедлайна: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md), [TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md), [ASYNC](TASK-ADDENDUM-ASYNC-20261017.md).
Ветка `codex/server-validation-20261011`, [draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2); starting **26cbe5912f6683a81780a831609dbddbc26feba4**, опубликованный HEAD **ee03752941748f6c942aa66f7d02674dd0ab3357** (parent79905e02). Normal commit/push30source+4docs исполнены, remote verified; foreign25 сохранены. PR остаётся draft/open/main не merged; body последнего чтения ещё799, обновление UNCONFIRMED. Current merge0c0fb1dc штатно fetched/parents3908+ee/equal-tree/trusted778 verified, HEAD/index/foreign25 unchanged.
CI ee: push39201752437 /PR39201759319 **FAIL**, terminal05:37:42; Coverage39201759309 и OpenAPI39201759297 **SUCCESS**, Security39201759300 **FAIL/Gitleaks**. Штатные Git/metadata снова доступны. Actual Academic regular tests/collector/upload SUCCESS; changed-domain FAIL до RED_RUN на SOURCE_ADMISSION/stale source pin. Monitoring/Synthetic SUCCESS; reconciliation SETUP FAIL, restore SKIPPED. Источник — metadata05:19–05:23 и существующие safe artifacts.

[Матрица v3 FROZEN](selected-results-20261018-v3.csv) / [веса](selected-results-20261018-v3.meta.json): **35/39, вес180/196 = 91.84% принятого плана**, 33 DONE_CHANGED +2 ALREADY_SATISFIED. Это не production readiness; веса/критерии неизменны. Исторические v1 23/25, v2 24/38 и TASK17 9/9 сохранены в матрицах.

| КТ | Закрыто / всего | Вес / всего | % плана |
| --- | --- | --- | --- |
| КТ2: границы/AAA/контракты/сохранность | 14/14 | 71/71 | 100 |
| КТ3: бот/приложения | 6/9 | 26/37 | 70.27 |
| КТ4: цепочки/мониторинг/задания | 15/15 | 83/83 | 100 |
| КТ5: итоговые gates/review/handoff | 0/1 | 0/5 | 0 |

| Остаток | Доказано → конкретное действие / зависимость |
| --- | --- |
| U20.1–U20.3 | Teacher/common/private/TLS bridge PASS сохранены. Actual ee hosted12164797033: checks37/headman_read/RPC_STATUS_UNAVAILABLE; BFF/Attendance могут нормализовать разные причины в тот же status. R2 bounded producer RCA; нет доказанного transport/ACL дефекта. Нужны current roster/none/revoke и оставшиеся freshbinding/currentrights того же сценария. |
| U25 | Security2 narrow residual correction/addressed review; current finalCI/Coverage ещё не завершены. Academic changed-domain12164260971 остановлен SOURCE_ADMISSION на старом AdminReport source pin после R2, regular tests PASS; a05 готовит только2 pin corrections. Reconciliation12164034233: SETUP/DLQ_READY, deadReady2/main0/unacked0/retry0; own4 memberships APPLIED, другая pendingwork глобального стенда, её связь с DLQ UNKNOWN. Reportreviewer boundedRCA; barriers не ослаблять/queue не чистить. Restore после setupFAIL SKIPPED, прежнийPASS сохранён. |

## Готовый пакет и ближайший шаг

**U14.5/U14.6 приняты наee037529** по existing artifacts: subject12164671395, semesterUI12164068407, admin12164807301, roles/5actualformats12164298277 PASS. Subject proof/delete/participants/control preserved; Vue assign/update/reopen/revoke/currentrights и файлы проверены. Independent source/reviews/compile/unit применимы; full evidence в матрице. Root — sole Git/docs/matrix writer; normalpublication30source+4docs сохранена.

Готовый связный пакет: strict CI source-pin/reader fixes + diagnostic7 + Security2 final22 correction. Последняя CI visibility ветка independently APPROVE/Rootjoined9a2739ed: существующий collector должен сохранить закрытые INITIAL_MEMBERSHIP categories после D9 setupFAIL; тот же owned Attendance capture/byte+time caps, строгая startup-noise обработка, без новых файлов/артефактов. Source guards/statuses/RPC/oracles/CASE_EXIT сохраняются.

Native44 **PASS**: все6 compile+3test EXECUTED,4freshXML/32PASS/0fail-error-skip;182.23s/RSS1.61GB/output153.76MB within192MiB. Canonical `/tmp/rct-s19-integration-compile/current44/current44.safe.json`;5Java exactRootposts, Native Python/TS не менялись, allnewPIDs reaped/leaseRELEASED. Bot/consumer privacy independentAPPROVE; actualU20/D9causeUNKNOWN до нового hostedCI.

Security actual3908..0c0 до последней коррекции: prep272.238s/native45.167s/exit2/**22findings**, treeRSS385960KiB within384MiB, cleanup0; `/tmp/r9cf-gitleaks-current-ee-384-20261019/`. Четыре недостающих public-metadata producer contexts исправлены финальным903c82c4 increment/independentAPPROVE. Config57eb/ignore unchanged, originalnative18RED и прежние causal controls сохранены; actualPASS даст обязательный ordinaryCI новогоSHA, без повторного fullscan staleee.

Runtime: heavyleases RELEASED; localDocker ниже4GiB reserve, без cleanup/reset/prune. Foreign25 untracked сохранены. Root soleGit/docs; Event Security2 sourcecomplete, R2 botcomplete, Report CIvisibilitywriter, Cache addressedreview, a05 Native44complete; CIobserver ee terminal/released. Следующий шаг — targetedprecommit, ONE normalcommit/push14файлов, обновить draftPR2 и принять текущие gates/userprobes.

Принятые результаты и exact evidence — в матрице; история один раз в [attempts](attempts.ndjson). [Resources/owners](resources.md), [protocol](BOUNDARY-PROTOCOL-20261018.md), [decisions](decisions.md), [questions](questions.md). Q033 закрыт actual e5c CLI/core evidence; production bootstrap не выполнялся. Историческая причина WORD crash UNKNOWN.
Ignored resume: `.agent/evidence/cloud-20261011/current-wip/latest-checkpoint.safe.json`; вопросы владельцу отсутствуют. App attach/list последний outcome UNKNOWN, прежний PR URL сохранён; повторов нет.
**B013/B014/B015 FROZEN**. OWNER_MANUAL/DEFERRED_LOCAL: Telegram/push/install/Safari/alertbot/offhostprobe/ownerstand/deploy/backup/VPS/multiworker — не PASS; обязательные env-blockers остаются в знаменателе.
