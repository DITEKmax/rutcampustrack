# CURRENT — Auth/Academic, пользовательские операции и наблюдаемость

Goal Q031/D037 **IN_PROGRESS**, без дедлайна: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md), [TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md), [ASYNC](TASK-ADDENDUM-ASYNC-20261017.md).
Ветка `codex/server-validation-20261011`, [draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2); starting **26cbe5912f6683a81780a831609dbddbc26feba4**, опубликованный HEAD **79905e02b948bb00d8c5e9c358577dac3503856e**. Нового commit/push нет: три попытки не исполнены из-за `Automatic approval review: Selected model is at capacity`; обходов нет.

[Матрица v3 FROZEN](selected-results-20261018-v3.csv) / [веса](selected-results-20261018-v3.meta.json): **33/39, вес170/196 = 86.73% принятого плана**, 31 DONE_CHANGED +2 ALREADY_SATISFIED. Это не production readiness; веса/критерии неизменны. Исторические v1 23/25, v2 24/38 и TASK17 9/9 сохранены в матрицах.

| КТ | Закрыто / всего | Вес / всего | % плана |
| --- | --- | --- | --- |
| КТ2: границы/AAA/контракты/сохранность | 13/14 | 66/71 | 92.96 |
| КТ3: бот/приложения | 6/9 | 26/37 | 70.27 |
| КТ4: цепочки/мониторинг/задания | 14/15 | 78/83 | 93.98 |
| КТ5: итоговые gates/review/handoff | 0/1 | 0/5 | 0 |

| Остаток | Доказано → конкретное действие / зависимость |
| --- | --- |
| U14.5 | Samearchive/DLQ/replay/dedup/restoration и semester UI PASS сохранены. Subject BEFORE marks1 PASS, затем799 preview503/SCHEDULE_PREVIEW/RPC_FAILURE/UNAUTHENTICATED (12162161283/0cea42ae). Schedule exact-directed admission исправлен в3 файлах: independent APPROVE, Native43 compile+4unit PASS. Нужен исходный hosted preview/delete/participants на новом коде с прежними proof/grants. |
| U14.6 | 5 auth/role/logout PASS. Group-report capability ошибочно шла в ordinary Auth/Attendance validators: R2 scoped data/batching21 файлов independent whole/addressed APPROVE, compile41+26unit42 PASS. Admin second-open race исправлена в2 UI файлах,56 targetVitest PASS. Нужны исходные admin actions и5 report formats; прежняя исключительная причина timeout UNKNOWN. |
| U20.1 | Private common/menu, TeacherToday/empty и отдельный actual TLS→Python bridge PASS сохранены. Hosted799 остановлен в headman read/check37 с широким RPC_UNAVAILABLE; подтверждённой причины нет. Закрытый raw gRPC/local-outcome diagnostic2 готов. Нужен исходный hosted запуск; API не расширять. |
| U20.2 | Own current lesson/roster/unmarked/statistics source согласован; runtime не завершён из-за U20.1. Пройти исходные current/none/revoke. |
| U20.3 | TeacherToday/empty PASS; поздние freshbinding/revoke/currentrights не завершены. Пройти остаток того же hosted сценария после U20.1. |
| U25 |799 Coverage39194703278/OpenAPI39194703304 SUCCESS, оба Academic IT/Apps/Monitoring SUCCESS. CI39194696460/PR39194703392 красные; Security39194703316 FAIL. Restore seed/pending PASS, fresh restore PG_QUERY/exit1: exact DB-owner guardfix1 independent APPROVE/3pure PASS, actualPG pending. Reconciliation остановлен SETUP/DLQ_READY до своей controlled-close; причина сообщения UNKNOWN, старый recovery PASS сохранён; existing fixed-carrier diagnostic1 готов. Финальные gates только на совместимой revision. |

## Готовый пакет и ближайший шаг

**30 source/fixture файлов +4 taskdocs готовы к normal commit/push**: R2 report21, Schedule3, AdminUI2, recovery1, Bot diagnostic2, reconciliation diagnostic1. Адресные source/review/compile/unit проверки достаточны для публикации draft; новый hosted путь ещё NOT_RUN. Root — sole Git/docs/matrix writer. После восстановления auto-review опубликовать тем же штатным способом и принять ordinary CI; модель/канал ради обхода не менять.

Security2 отдельно LOCAL WIP7916/d59: whole/addressed independent APPROVE,37 differential/5 resource/1948 reference-equivalence controls PASS, прежние native18 required-red сохранены. Один полный range3908..f9 завершён естественно: prep с cProfile230.513s +native38.495s =269.701s, exit2/96findings (generic37/sourcegraph59), treeRSS225860KiB, caps/cleanup PASS. Прежние120s были локальным бюджетом и прерывали конечный typed-разбор истории; не acceptance gate. Security красный; Event классифицирует12 rule/path групп без Secret/Match/snippets, никаких широких исключений или нового запуска до конкретного исправления.

Runtime: все Native41/42/43 и AdminVitest leases RELEASED; source pins сохранены. Local Docker запрещён текущим disk reserve (<4GiB); никаких cleanup/reset/prune. Foreign25 untracked сохранены. CI observer STOPPED из-за той же approval-capacity, статусы выше — последние известные, не новый poll.

Принятые результаты и exact evidence — в матрице; история один раз в [attempts](attempts.ndjson). [Resources/owners](resources.md), [protocol](BOUNDARY-PROTOCOL-20261018.md), [decisions](decisions.md), [questions](questions.md). Q033 закрыт actual e5c CLI/core evidence; production bootstrap не выполнялся. Историческая причина WORD crash UNKNOWN.
Ignored resume: `.agent/evidence/cloud-20261011/current-wip/latest-checkpoint.safe.json`; вопросы владельцу отсутствуют. App attach/list последний outcome UNKNOWN, прежний PR URL сохранён; повторов нет.
**B013/B014/B015 FROZEN**. OWNER_MANUAL/DEFERRED_LOCAL: Telegram/push/install/Safari/alertbot/offhostprobe/ownerstand/deploy/backup/VPS/multiworker — не PASS; обязательные env-blockers остаются в знаменателе.
