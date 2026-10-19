# CURRENT — Auth/Academic, пользовательские операции и наблюдаемость

Persisted Goal Q031/D037 **IN_PROGRESS**, без дедлайна: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md), [TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md), [ASYNC](TASK-ADDENDUM-ASYNC-20261017.md).
Ветка `codex/server-validation-20261011`, [draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2). Starting `26cbe5912f6683a81780a831609dbddbc26feba4`; опубликованный HEAD **7dff3ecd7a4ebc41724cad1a6c47a48f1c144567**. PR body/HEAD проверены, app attachment SUCCESS; main не merged. **Готов связный checkpoint: 7 source/test/workflow файлов + 4 task docs; штатные hooks/normal push следующие.** Чужие 25 untracked сохранены.

[Матрица v3 FROZEN](selected-results-20261018-v3.csv) / [веса](selected-results-20261018-v3.meta.json): **36/39, вес 183/196 = 93.37% принятого плана**, 34 DONE_CHANGED + 2 ALREADY_SATISFIED. Это не production readiness. Исторические v1 23/25, v2 24/38, TASK17 9/9 сохранены.

| КТ | Закрыто / всего | Вес / всего | % плана |
| --- | --- | --- | --- |
| КТ2: границы/AAA/контракты/сохранность | 14/14 | 71/71 | 100 |
| КТ3: бот/приложения | 7/9 | 29/37 | 78.38 |
| КТ4: цепочки/мониторинг/задания | 15/15 | 83/83 | 100 |
| КТ5: итоговые gates/review/handoff | 0/1 | 0/5 | 0 |

| Остаток | Доказано; конкретное действие |
| --- | --- |
| U20.1 | Exact menu/private checks2–31 PASS. Нужны реальные shared fresh binding48–53, revoke54–57, expired callback58 и final controls59–60 того же hosted сценария; предыдущий запуск остановился на headman37. |
| U20.2 | Actual7d checks37/BFF_ATTENDANCE_RESPONSE/TIME: подтверждённый product defect — допустимый ISO offset timestamp без нулевых секунд отвергается Instant.parse. Исправление4файлов6154e0e3 интегрировано/review APPROVE/Native45 PASS. Нужны настоящие roster/statistics/unmarked/direct-render/no-current checks38–47 на новой revision. |
| U25 | На7d Coverage/OpenAPI и оба ordinaryAcademic gates SUCCESS; Security FAIL4 public-metadata context gaps исправлены workflow-only9d941297/review APPROVE/original18RED сохранены. D9 SETUP/deadReady2/ownAPPLIEDtrue/globalpending; consumer hint UNKNOWN. Restore seed/pending PASS, затем fresh-restore RESTORE_SCRIPT/GUARD RuntimeError1; дочерняя причина потеряна. Diagnostic2 patch06995fc4 review APPROVE добавляет только закрытые reason-коды в те же failure paths. Нужны actual причины/устранение и финальные совместимые gates. Guards/CASE_EXIT/oracles сохраняются. |

## Принято сейчас, владельцы и следующий шаг

**U20.3 принято отдельно от headman:** actual7d teacher checks32–36 подтверждают свои пары/directtext/empty; Native45 current teacher deny/revoke/dependency и сохранённые Python157 подтверждают остальные границы. Cache evidence-applicability APPROVE. Source/runtime lineage различается в матрице; shared binding не объявлен PASS и остаётся U20.1.

Native45 canonical `/tmp/rct-s19-integration-compile/current45/current45.safe.json`: 4 compile + 2 test EXECUTED, 2 fresh XML /16PASS, 96.95s/RSS1.113GB/output112.42MB. Source guards/PythonTS стабильны, новые PID reaped, lease RELEASED. Native44 и ранее принятый evidence сохранены. Это не hosted headman PASS.

Текущие normal runs7d: push CI39207941744 FAILURE; PR CI39207949788 заканчивает полезные client/CLI шаги; Coverage39207949770 SUCCESS; Security39207949746 FAILURE; OpenAPI39207949769 SUCCESS. Safe primaries: `.agent/evidence/cloud-20261011/current-wip/ci-7d-primary/` (bot12167660342, D912167502037, restore12167328316, closed hints117914756422). Raw log остаётся только private tool-store, в Git/файлы не скопирован.

Root — sole Git/docs/matrix интегратор. Все три пакета приняты независимым review; авторы/Native writer released, доступны для адресных findings. CI observer следит только за последними незавершёнными шагами PR CI. Local heavy runtime отсутствует; Docker ниже резерва4GiB, без cleanup/reset/prune. Следующее: штатные hooks и один normal push11файлов, затем новые actual bot/D9/restore/Security данные; текущий CI не отменять.

История один раз в [attempts](attempts.ndjson); [resources/owners](resources.md), [protocol](BOUNDARY-PROTOCOL-20261018.md), [decisions](decisions.md), [questions](questions.md). Вопросов владельцу нет. Q033 bootstrap принят по actuale5c evidence, актуальный CLI на7d PASS; production-команда не выполнялась. Историческая причина WORD crash UNKNOWN.
**B013/B014/B015 FROZEN.** OWNER_MANUAL/DEFERRED_LOCAL: настоящий Telegram/push/install/Safari, alertbot, offhostprobe, ownerstand, deploy/backup/VPS/multiworker — не PASS. Обязательные env-blockers не исключать. Resume: `.agent/evidence/cloud-20261011/current-wip/latest-checkpoint.safe.json`.
