# CURRENT — Auth/Academic, пользовательские операции и наблюдаемость

Persisted Goal Q031/D037 **IN_PROGRESS**, без дедлайна: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md), [TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md), [ASYNC](TASK-ADDENDUM-ASYNC-20261017.md).
Ветка `codex/server-validation-20261011`, [draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2). Starting `26cbe591`; published HEAD **28271672**, exact SHA в attempts/resume. PR2 open/draft/unmerged, source/merge tree проверены; main/deploy не выполнялись. Существующая app attachment SUCCESS; supplemental UNKNOWN-вызов не повторять.
Root WIP: восемь reviewed source/config/harness файлов (R28237, Report11c80ea9, Eventfinalaf929f20) +5 taskdocs; Root allposts exact/inversePASS, Cache whole/addressedAPPROVE. Native51 compile/unit PASS, actualD9 ещё не проверен; index empty, foreign25 byteexact. Author sparse worktrees на2827 frozen; новые исходники ещё не опубликованы, полный runtime pending.

[Матрица v3 FROZEN](selected-results-20261018-v3.csv) / [веса](selected-results-20261018-v3.meta.json): **37/39, 183/196 = 93.37% принятого плана**,35 DONE_CHANGED +2 ALREADY_SATISFIED. Это не production readiness. Веса/знаменатель неизменны; исторические v1 23/25, v2 24/38, TASK17 9/9 сохранены.

| КТ | Закрыто / всего | Вес / всего |
| --- | --- | --- |
| КТ2: границы/AAA/контракты/сохранность | 13/14 | 63/71 |
| КТ3: бот/приложения | 9/9 | 37/37 |
| КТ4: цепочки/мониторинг/задания | 15/15 | 83/83 |
| КТ5: итоговые gates/review/handoff | 0/1 | 0/5 |

**U14.3 открыт по текущему FAIL.** Push primary12185829742: PENDING_ARCHIVE, delete-preview HTTP503/transportnull/3identity booleansnull; MAIN archive/replay не запускался, cleanup/restoration/clock PASS. Конкретный producer503 UNKNOWN.
PR primary12185739923: EXACT_REPLAY, readiness200/3true; три same-event WARN PREPARE/SCHEDULE_UNAVAILABLE/SCHEDULE_LESSON_BY_ID/UNAVAILABLE/DNS; ledgerPENDING, claim/receipt/ACK=0, cleanupFAILED/clocktrue. Это не установленный дефект TX/ACK. Исходный D9v8 на09aa прошёл в обоих CI (12184146268/12184335828, all19checks/oneclaimreceiptACK/sameArchiveCompleted/duplicateUnchanged/cleanupRestoredClockPASS), прежнее принятие сохранено исторически. Текущий FAIL честно переоткрыл строку.

**U25 открыт.** PR role primary12185809852: JSON_DECODE после HTTP200 и успешного status assertion, Error/UNKNOWN; other6 cases PASS. Точная body/channel причина UNKNOWN. Source подтвердил отдельный fixture counterexample: прежний waiter принимал GET session старого документа. R2 fix8237 интегрирован Root exact: first post-commit main-frame Request identity, два local listeners с finally cleanup, <=1 retained request. Оригинальные HTTP/status200/roles/JSON/error identity/timeouts сохранены;20 causal/privacy controls PASS, Cache APPROVE. Это SOURCE fix, не browser PASS и не доказательство исключительной причины2827.

Все gates на published2827 завершены, без отмен:

| Gate | Run | Итог |
| --- | --- | --- |
| Push CI | 39249647168 | FAIL: D9; client/CLI/actualrestore/participants/cleanup SUCCESS |
| PR CI | 39249658964 | FAIL: D9 и role33; restore/participants SKIPPED после prerequisite; CLI/cleanup SUCCESS |
| Coverage | 39249658154 | SUCCESS, Academic +diff |
| Security | 39249658346 | SUCCESS, actualGitleaks/TrivyFS/config; imagesSKIPPED, не PASS |
| OpenAPI | 39249658130 | SUCCESS |

Обе Academic IT, Apps, Monitoring, asyncDOCX/pipeline и hosted bot группы SUCCESS. Старые runs закрыты, не опрашивать. Gitleaks public Git references HANDOFF исправлены reviewed HANDOFF2; default208/fullhistory/unknown+credential veto/18RED сохранены. PR DOCX/pipeline расхождение855 сохранено исторически: actual2DOCX+4deltasPASS, затем preHTTP transportOTHER; оба09aa/2827 pipeline SUCCESS, точная старая причина UNKNOWN.

**Текущий связный пакет:** Event усиливает ТОЛЬКО существующий post-Schedule-return Attendance health barrier: свежий passive schedule-service READY из того же Docker health JSON внутри исходного45s deadline, тот же процесс/канал; не restart/reset/retry. Cache whole source APPROVE; actualD9 runtime ещё нет. Report владеет пятью Java diagnostic boundaries +existingCI; Event — semester fixture/producer. Единый frozen v9 contract e533d525 даёт закрытые readiness503 source/stage/status/cause и health state/freshness, private-RAM requestId correlation; cap4096/legacyv3–8 и все исходные assertions/ACK сохраняются. Никаких body503/raw IDs/payload/новых runner/upload/framework.

**Owners/next:** Root soleGit/taskdocs/matrix; R2 frozen8237 APPROVE; Report+Event implementation в независимых worktrees; Cache адресный review; Native51 PASS/released; observer все2827 runs закрыл. Native51 PASS:4compile EXECUTED +fresh11unitPASS/162.45s/sourceeq/resources/PIDsreaped. Все8source exactintegrated/reviewAPPROVE; дальше scopedhooks и один normal13file publish/fullCI. Новый fullCI ради отдельного поля не запускать. Готового shortD9/dispatch нет; новую обвязку не строить.

**Resources:** heavy0; Native50 released/31freshPASS остаётся применим только к неизменным Java inputs. Gateway/Academic diff подтверждён Native51c8aad011: peakRSS1296678912B/output117108725B<128MiB, finalTMP4424007680B/minTMP4428165120B/minworkspace4189827072B. Это сохранённый замер; перед новым runtime нужен fresh floor check. Новые local builds не планируются. CPU1/RSS2.5GiB/TMPfloor4GiB/workspacefloor3.75GiB/output128MiB неизменны; без Docker/DB/pulls/prune/reset/cleanup. Q032 offline IPC authorization сохранена. [Resources](resources.md).

Evidence хранится единожды в ignored ci-2827-primary/ci-2827-pr-primary; exact IDs/hashes/checks в [attempts](attempts.ndjson), acceptance в матрице. [Handoff](HANDOFF-20261017.md), [protocol](BOUNDARY-PROTOCOL-20261018.md), [decisions](decisions.md), [questions](questions.md). Вопросов владельцу нет. Resume: `.agent/evidence/cloud-20261011/current-wip/latest-checkpoint.safe.json`.
**B013/B014/B015 FROZEN.** OWNER_MANUAL/DEFERRED_LOCAL: настоящий Telegram/push/install/Safari/ownerstand, alertbot/offhostprobe/deploy/backup/VPS/multiworker — не PASS. Исторические WORD crash/855transport/09aareload origins UNKNOWN. Production-ready не заявляется.
