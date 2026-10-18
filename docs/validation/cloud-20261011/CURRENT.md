# ACTIVE — separate DB + apps + Q033 / 18.10.2026

Persisted Goal Q031/D037: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md),
[TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md),
[ASYNC](TASK-ADDENDUM-ASYNC-20261017.md), Q033/D038. Goal IN_PROGRESS.
Root sole Git/taskdocs; branch codex/server-validation-20261011,
[draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2); no main/merge/deploy.
Starting26cbe5912f6683a81780a831609dbddbc26feba4. Published/runtime baseline
**92be74818741044d4a79c62ae2beabdb5a4ba7ae**; current source is HEAD plus owned WIP.

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
| U14.4/.6 | Два async DOCX и короткий период/lost202/reconnect/currentrights; официальный Academic snapshot после исправления коллизии схем → существующие генераторы/atomic5. |
| U14.7 | Q033: 3/4 DB PASS; first-login fixture исправлен по bootstrap→selectRole. Actual CLI/config/privateIPC и fresh/repeat ещё не приняты. |
| U15.1 | Сохранность двух DB в UserOwnershipLifecycleIT; запуск блокируется context/Flyway/connection ошибкой, причина UNKNOWN. |
| U20.1/.2/.3 | Private synthetic bot с двумя DB: точное меню/headman roster/teacher Today/currentbinding/revoke. |
| U23.1/.2/.4 | Реальные Vue/Java GlitchTip, профиль Pyroscope и общий расход. SDK resolver исправлен; Homepage U23.3 и PMM decision U23.5 приняты. |
| U25 | Финальные совместимые CI/Coverage/Security/OpenAPI/monitoring, review и exact SHA handoff. |

## Текущее доказательство и работа

92b push39151426401 завершён FAILURE; оба browser cleanup SUCCESS. Coverage FAILURE;
Security/OpenAPI/полный monitoring SUCCESS. Новые actual PASS: map view/download
после API replacement и semester reconciliation; FIRST3/geo/HW/assignment/Today PASS.
Другие UI FAIL не объединены одной гипотезой; archive participants SKIPPED после
checkpoint recovery FAIL. Evidence и прежняя приёмка — в матрице/attempts.

Root интегрирует пакет: archived-card credential enrichment fix; уникальное имя
identity OpenAPI schema; Q033 role-selection fixture; Homework servlet context;
реальный BEFORE_COMMIT rollback fixture; SDK ESM resolution; прежние CI pins;
закрытая JDBC диагностика в существующем сборщике. Source reviews приняты.
current15 compileJava/compileTestJava PASS; unit4 первоначально FAIL из-за
неинъецированных private полей теста. Исправлен только setup; current16 все4 PASS
(80.82s), прочий код не повторялся. Runtime DB/UI для нового пакета ещё pending.

Owners и ресурсы: [resources](resources.md). Canonical assignment cohort8 принят
независимым reviewer, current17 compileTestJava PASS68.13s; прежние unit4 PASS сохранены.
Следующий Q033 пакет проверяет настоящий CLI; runtime DB для cohort8 ещё pending.
Root не ждёт всех направлений перед публикацией готового пакета.
Официальные runtime specs92b получены; штатная генерация, два mobile typecheck
и74 report tests PASS. Atomic5 удержан: схема Operation потеряла subject-deletion
поля из-за name collision; DTO fix должен дать новый официальный snapshot.
Полный PWA typecheck достиг heap limit; PASS не заявляется.

Следующий шаг: адресные проверки/affected review → один normal push; обычный CI
даёт актуальные первичные причины и исправленный runtime OpenAPI → atomic5.
Source-only результат не увеличивает счётчик приёмки. Foreign25 и reserved report
type alias сохранены. Local Docker/DB/imagepull NOT_RUN: workspace меньше4GiB
reserve/VFS sameFS; нет cleanup/reset/prune. Вопросов владельцу нет.

[Protocol](BOUNDARY-PROTOCOL-20261018.md), [history](attempts.ndjson),
[decisions](decisions.md), [questions](questions.md), [handoff](HANDOFF-20261017.md);
ignored current-wip/latest-checkpoint.safe.json сохраняет точный WIP.
B013/B014/B015 FROZEN. DEFERRED_OWNER: real Telegram/push/install/Safari/alertbot/
offhost probe/owner stand/deploy/backup/VPS/multiworker. No reset/secrets/new web/Angular retirement.
