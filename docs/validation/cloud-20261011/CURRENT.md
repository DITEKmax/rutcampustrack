# CURRENT — Auth/Academic, пользовательские операции и наблюдаемость

Persisted Goal Q031/D037 **IN_PROGRESS**, без дедлайна: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md), [TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md), [ASYNC](TASK-ADDENDUM-ASYNC-20261017.md).
Ветка `codex/server-validation-20261011`, [draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2). Starting `26cbe591`; published HEAD **32b53b2f**, exact SHA в attempts/resume. PR2 open/draft/unmerged, source/merge tree проверены; main/deploy не выполнялись. Существующая app attachment SUCCESS; supplemental UNKNOWN-вызов не повторять.

[Матрица v3 FROZEN](selected-results-20261018-v3.csv) / [веса](selected-results-20261018-v3.meta.json): **36/39, 178/196 = 90.82% принятого плана**,34 DONE_CHANGED +2 ALREADY_SATISFIED. Это не production readiness. Веса/знаменатель неизменны; исторические v1 23/25, v2 24/38, TASK17 9/9 сохранены.

| КТ | Закрыто / всего | Вес / всего |
| --- | --- | --- |
| КТ2: границы/AAA/контракты/сохранность | 13/14 | 63/71 |
| КТ3: бот/приложения | 9/9 | 37/37 |
| КТ4: цепочки/мониторинг/задания | 14/15 | 78/83 |
| КТ5: итоговые gates/review/handoff | 0/1 | 0/5 |

**U14.3 открыт.** Текущие D9v9 на32b оба остановились STRANDED/error3667 в добавленном network-address guard ДО healthREADY/readiness/replay: health/readiness/replay projection null, close identity3true, exactDLQ/setup3true. PUSH primary12192141756: cleanupFAILED/restorationfalse/clocktrue; PR primary12191668292: cleanupPASSED/restorationtrue/clocktrue, recovery publish/applied произошли в cleanup, sameArchiveCompletedfalse/duplicatefalse. Это не MAIN acceptance и не новый доказанный503/DNS/ACK/TX дефект; конкретный failed address conjunct UNKNOWN.
Source выявил неверный guard: pre-stop IP equality не гарантируется Docker при намеренном stop/start. Event patch513266f8/post364e5fc9 убирает ТОЛЬКО это сравнение, сохраняет address shape/стабильность внутри исходного45s barrier/process/container/image/mounts и добавляет immutable network-name/NetworkID pin→before→current. 119 causal controls +8 AST checks/401 original business assertions/331 awaits PASS; Cache addressed APPROVE, Root exact integrated. Runtime нового increment NOT_RUN.
Прежний09aa D9v8 в обоихCI прошёл:12184146268/12184335828/all19checks/oneclaimreceiptACK/sameArchiveCompleted/duplicateUnchanged/cleanupRestoredClockPASS. На2827 PUSH readiness503/producerUNKNOWN, PR typedScheduleDNS до effects; исторические proof/FAIL сохранены. Текущий guardfix не объявляется исправлением этих неизвестных причин.

**U18 переоткрыт только по frontend reconnect.** Конкретный source counterexample: App.goOnline→bootstrap терял online-demand при уже выполняющемся bootstrap; older attempt мог закончить offline/readOnly без replay. Actual32b PR D002 primary12191592327: session response deadline/refresh response тоже не наблюдался; request-start/online-handler/HTTPstatus UNKNOWN,7othercasesPASS. reloadSession здесь не вызывается; exclusive cause UNKNOWN. Старые server PG admission/generation/revoke/Redis503 proof сохранены.
R2 PWA2 patchd225509b: App pending online generation coalesces реально полученное событие, consume-before-replay после older success/failure; offline/logout/newgeneration/!auto/passwordrecovery отменяют demand, нет failure-driven loop. Перед fresh bootstrap сохраняется readonly; authority-invalidation refresh:false fallback сохранён. Только App.vue и существующий bootstrap-policy.test.ts; TMA/API/timers/permissions не менялись. Actual-App-source extracted29Vitest PASS (old14 byteexact +15 regression cases), Vue/TS/format/AST/inverse PASS. Cache independent APPROVE, Root exact integrated; hosted reconnect нового source ещё NOT_RUN.

**U25 открыт.** Все32b runs terminal, observer остановлен; текущие FAIL не скрыты:

| Gate | Run | Итог |
| --- | --- | --- |
| Push CI | 39258916981 | FAILURE: D959; restore/participants SKIPPED после prerequisite |
| PR CI | 39258928962 | FAILURE: D959 +Remaining5/D002; actual restore62/participants64 SUCCESS |
| Coverage | 39258928792 | SUCCESS, Academic+diff |
| Security | 39258928672 | SUCCESS: actualGitleaks/TrivyFS/config; imagesSKIPPED |
| OpenAPI | 39258929032 | SUCCESS |

В обоихCI role33/upload34, Academic IT, Apps, Monitoring, asyncDOCX/pipeline, hostedbot, cleanup и actualCLI72/upload73 SUCCESS. Published reload ownership fix8237/06a4cae8 получил actualrole proof; историческая2827 JSON_DECODE/HTTP200 исключительная причина UNKNOWN. Gitleaks public Git references HANDOFF исправлены reviewed HANDOFF2, actualgateGREEN; default208/fullhistory/unknown+credential veto/18RED сохранены. Старый855 DOCX/pipeline: actual2DOCX+4deltasPASS, затем preHTTP transportOTHER; последующие09aa/2827/32b pipeline SUCCESS, точная старая причина UNKNOWN.

**Текущий связный WIP:**3 source/test/fixture +5 taskdocs, оба автора frozen/independentAPPROVE/exactRootintegration; indexempty/foreign25 byteexact. Root soleGit/taskdocs/matrix. R2 — App/test, Event — addressincrement, Cache — independentreview; Report published5Java+CI frozen; Native51 PASS/released; observer all32b terminal/released. Далее scopedhooks один раз, normal commit/push текущей ветки, update existingdraftPR и полный ordinaryCI. Новый fullCI нужен для изменённого поведения, не отдельного поля. Готового shortD9/dispatch нет; новый runner/framework не строится. Дополнительные диагностические v9 поля/4096cap/legacyv3–8/исходные assertions/ACK/timeouts неизменны.

**Resources:** heavy0. Native51c8aad011:4compile EXECUTED +fresh11PASS/162.45s, peakRSS1296678912B/output117108725B<128MiB; PIDsreaped. Java/build/proto/resources inputs в новом3fileincrement неизменны — повтор Native не требуется. Последний Native finalTMP4424007680B/minworkspace4189827072B — сохранённый замер, не текущая capacity. CPU1/RSS2.5GiB/TMPfloor4GiB/workspacefloor3.75GiB/output128MiB; без Docker/DB/pulls/prune/reset/cleanup. PWA targetedVitest один actualrun, CPU1/worker1/heap384MiB/deadline90s,29PASS; начальный отсутствующий time wrapper не запускал тесты. Q032 offline IPC authorization сохранена. [Resources](resources.md).

Evidence — единственные ignored ci-32b-primary и прежние ci-2827-primary; exact IDs/hashes/checks в [attempts](attempts.ndjson), acceptance в матрице. [Handoff](HANDOFF-20261017.md), [protocol](BOUNDARY-PROTOCOL-20261018.md), [decisions](decisions.md), [questions](questions.md). Вопросов владельцу нет. Resume: `.agent/evidence/cloud-20261011/current-wip/latest-checkpoint.safe.json`.
**B013/B014/B015 FROZEN.** OWNER_MANUAL/DEFERRED_LOCAL: настоящий Telegram/push/install/Safari/ownerstand, alertbot/offhostprobe/deploy/backup/VPS/multiworker — не PASS. Исторические WORD crash/855transport/reload origins UNKNOWN. Production-ready не заявляется.
