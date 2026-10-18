# ACTIVE — separate DB + apps + Q033 / 18.10.2026

Persisted Goal Q031/D037: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md),
[TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md),
[ASYNC](TASK-ADDENDUM-ASYNC-20261017.md), Q033/D038. Goal IN_PROGRESS.
Root sole Git/taskdocs; branch codex/server-validation-20261011,
[draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2); no main/merge/deploy.
Starting26cbe5912f6683a81780a831609dbddbc26feba4. Published/runtime baseline
**b836b1943a3a5c694eb9a5f95050cd250cc03981**; current source is HEAD plus owned WIP.

## Конечный план

[Матрица v3 FROZEN](selected-results-20261018-v3.csv)/[веса](selected-results-20261018-v3.meta.json):
**25/39,117/196=59.69% плана**, не production readiness. Состав/веса не менялись.
[v2](selected-results-20261018-v2.csv)24/38,114/188 и [v1](selected-results-20261018.csv)23/25,110/123
сохранены; TASK17 прежние9/9 и U15 sameDB действуют в прежней применимости.

| КТ | Закрыто / всего | Вес / всего | % плана |
| --- | --- | --- | --- |
| КТ2 boundary/AAA/contracts/persistence | 9/14 | 37/71 | 52.11 |
| КТ3 bot/apps | 3/9 | 10/37 | 27.03 |
| КТ4 changedchains/monitoring/jobs | 13/15 | 70/83 | 84.34 |
| КТ5 finalgates/review/handoff | 0/1 | 0/5 | 0 |

| Остаток | Следующее достаточное действие / зависимость |
| --- | --- |
| U14.2/.3/.5 | Create/credentials/grants/transfer/archive/proofs/currentrights/recovery. Разобрать конкретные FAIL, сохранить deny/unknown-outcome сценарии. |
| U14.4/.6 | Два async DOCX и короткий период/lost202/reconnect/currentrights; atomic5 из исправленного официального snapshot уже joined; итоговые consumers/gates pending. |
| U14.7 | Q033: 3/4 DB PASS; новый FAIL — удаление grant с сохранённым SID. Fixture сохраняет историю; actual CLI/config/privateIPC ещё не принят. |
| U15.1 | Сохранность двух DB в UserOwnershipLifecycleIT; первичный SQLSTATE53300 TOO_MANY_CONNECTIONS. Test-only idle pool fix joined; actual повтор pending. |
| U20.1/.2/.3 | Private synthetic bot с двумя DB: точное меню/headman roster/teacher Today/currentbinding/revoke. |
| U23.1/.2/.4 | Реальные Vue/Java GlitchTip, профиль Pyroscope и общий расход. Vue/Java groups/release/stack/scrub actual PASS prefix; Pyro flamegraph UNKNOWN. Homepage U23.3/PMM U23.5 приняты. |
| U25 | Финальные совместимые CI/Coverage/Security/OpenAPI/monitoring, review и exact SHA handoff. |

## Текущее доказательство и работа

b836 push39155034033 terminalFAIL, оба browser cleanupPASS; полезные пути завершены.
PR39155040105 ещё наблюдался running, его статус учитывается отдельно.
Coverage39155040110 FAILURE, Security39155040081/OpenAPI39155040095 SUCCESS.
Новый actual administrator account lifecycle29 PASS; FIRST3/map/remaining WebKit PASS.
Application-recovery: seed/pending API prefix PASS; полный шаг FAIL на backup.
Async DOCX/admin-grants/registry/semester ещё открыты. Изменённые gates
красные; подробные прежние outcomes сохранены в attempts и матрице.

Actual Academic185PASS/335FAIL: AssignmentAuthority7/Replacement6 и admin notification
cycle1 PASS. Части context FAIL соответствуют SQLSTATE53300: cached idle floor160
при PG100. Test-only minimumIdle0/idleTimeout10s joined, max20/concurrency сохранены.
Все335 этой причиной не объясняются. Legacy lifecycle2 IT переведены на настоящий
протокол с сохранением rollback/fence/replay/current401/race/history; review APPROVE.
Отдельные product fixes сохраняют unchanged grant.updated_at и400 field=groupId;
реальные SQL/API assertions оставлены, independent source review APPROVE.

Официальные b836 Academic02f8625b/Auth92b e571a08e source-equivalent → atomic5 joined.
Коллизия Operation устранена; generators/drift/production tsc PASS10.993s;
неизменённые81Auth/74report tests и mobile test-tsc сохранены. FullPWA heapOOM не PASS.
Actual Apps подтвердил Vue+Java grouping/release/stack/scrub; flamegraph wait падает
до budget/outage/retention. Существующий diagnostic получает только закрытые hints.
DOCX: после возврата statusGET200, затем WAIT_DOWNLOAD TimeoutError; ready/error
UNKNOWN. Наблюдение уже полученных state/errorCode добавлено без новых запросов.

Q033 actual CLI/config/privateIPC fifth case + guarded client CI + honest metadata
review APPROVE; grant fixture теперь сохраняет SID history. Общий bot seed исправлен:
обязательный directory_status=ACTIVE; никаких упрощений current binding/deny.
Auth/Academic compileJava+compileTestJava готового пакета прошли; следующий шаг:
адресные checks/precommit → один normal push после текущей browser safe boundary.
current18 все4 Auth/Academic compile tasks PASS87.33s/RSS1,108,602,880B; DB NOT_RUN.
Runtime готового WIP pending; source-only не увеличивает приёмку.
Параллельно: точный ADMIN export cause и synthetic recovery init secrets/ACL/input.
Owners/resources — [resources](resources.md). Foreign25 сохранены; reserved alias
перенесён в atomic5. Local Docker/DB/imagepull NOT_RUN: workspace<4GiB reserve/VFS
sameFS. Нет cleanup/reset/prune. Вопросов владельцу нет.

[Protocol](BOUNDARY-PROTOCOL-20261018.md), [history](attempts.ndjson),
[decisions](decisions.md), [questions](questions.md), [handoff](HANDOFF-20261017.md);
ignored current-wip/latest-checkpoint.safe.json сохраняет точный WIP.
B013/B014/B015 FROZEN. DEFERRED_OWNER: real Telegram/push/install/Safari/alertbot/
offhost probe/owner stand/deploy/backup/VPS/multiworker. No reset/secrets/new web/Angular retirement.
