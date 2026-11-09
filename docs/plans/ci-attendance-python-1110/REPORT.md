# Разбор и локальное исправление CI — 10.11.2026

## Goal
Исправить причины падения Java IT/coverage Attendance и Python Lint & Test на97e756f1b. Владелец разрешил самостоятельно решить судьбу проверок и выполнить локальные исправления; push/deploy запрещены до отдельного задания.

## Context/evidence
[CI](https://github.com/DITEKmax/rutcampustrack/actions/runs/42712358621) и [Coverage](https://github.com/DITEKmax/rutcampustrack/actions/runs/42712358689), одинаковый SHA97e756f1b4e2723a70f43f5dd8c155d768ba74d1. Первичные GitHub job logs прочитаны, безопасные memory/outcome artifacts сохранены локально в `.agent/evidence/ci-attendance-python-1110/` интеграционного worktree. Ниже — установленные причины, не предположения о memory leak.

| Job | Что проверялось | Что упало | Решение |
|---|---|---|---|
| Java IT — attendance-service | Реальный API/БД, транзакции, права, история и контракт |284/285 PASS; один OpenApiSnapshotIT.apiDocsMatchesSnapshot, line67: фактическая схема отличается отdocs/openapi/attendance.json |Оставить тест; обновить snapshot официальным runtimeгенератором и проверить точный diff |
| Java coverage — attendance-service |Те же unit/IT под сборщикомcoverage и memory diagnostics |438unit PASS,284IT PASS, один тот же OpenApiSnapshotIT FAIL; OOM0/javaHeapOomfalse/nativeMemoryFailurefalse. JaCoCoXML не создан из-за раннего ненулевого завершения тестов |Отдельной ошибки памяти/coverage не доказано; не отключатьcoverage и не менятьmemoryлимиты |
| Python Lint & Test |Ruff lint/format, затемpytest |ruffcheck PASS; ruffformat требует оформить5файлов, поэтомуpytest не запускался |ПрименитьRuff0.17.0; не удалятьпроверку, не менятьлогику |

Схема Attendance не была обновлена после добавления геодиагностики в журнал действий. Сверка JSON структурная, порядок ключей/переносы строк не являются её целью. Это полезная защита публичного API: например, новые source/fixTimestamp/sampleCount/completionReason должны быть описаны для клиентов.

Дополнительный служебный сбой: automatic OpenAPI candidate helper привязан к старому SHA исходника теста. Его строгие source/XML/binding guards сохраняются; допускается только обновление pin на текущий authoritative GitLFblob после проверки точного source/method. Удаление guard не требуется.

## Relevant scope
Base97e756f1b. Sole implementation writer `/root/ci_attendance_python_fix_1110`, requestedgpt-6.1-sol/high; runtime metadata не доступны. Worktree `.agent/worktrees/ci-attendance-python-1110`, branchcodex/ci-attendance-python-1110. Root — только contracts/docs/integration. Пять Pythonфайлов: bot_menu_client.py, handlers/prefs.py, handlers/start.py, observability.py, tests/test_start_handler.py; docs/openapi/attendance.json; при подтверждении scripts/ci/attendance-openapi-candidate.py толькоsourcepin. Чужой WIP и четыре служебных dirtyprocessfiles сохраняются.

## Required behavior
Проверки остаются строгими. API snapshot экспортируется из настоящего Attendance runtime прежним официальным методом; JSON не правится вручную. Python AST должен остаться прежним. Никаких изменений UI,400мгеозоны, permissions, authz, receipt/idempotency или бизнес-правил.

## Constraints
Не удалять/skip проверки ради PASS, не увеличивать budgets, не менять workflow. No push/deploy/production. DockerDesktop и globalconfig не запускать/не менять. Временная native-local обвязка тольковown.agent с точными resourceguards и остановкой собственных процессов.

## Existing patterns
OpenApiSnapshotIT update-property и структурная compare-mode проверка. Parent IT требуетMongo/Rabbit/Redis Testcontainers; допустим узкий локальный scaffold для неизменённого официального метода с настоящимSpring/Swagger/MockMvc и nativeMongo, честно отделённый от полного CIcontainersuite. Source test/harness в Git не менять. Готовые tools/artifacts переиспользуются readonly; один heavy lease уwriter.

## Acceptance criteria
Ровно ожидаемый API diff геодиагностики; unchanged official snapshot method comparePASS. Ruffcheck/formatPASS поCIworkspace, ASTнеизменён. Нужные guard/tests сохранены; CIhelperpin исправлен без ослабления source binding. Итоговый список файлов и фактические проверки зафиксированы, локальный commitготов, pushотсутствует.

## Verification
Ruff0.17.0 format пяти файлов →check/format wholebot; ASTcomparison. RealSpring officialsnapshot baselineFAIL→update→comparePASS с собственным nativeMongo. Дополнительные затронутые проверки — только при конкретном новом риске. УжеPASS285/438CIсценарии не повторять полным набором без причины; текущаяправка не меняет поведение сервиса.

## Do not
Не подгонять frontend/backend под устаревший snapshot. Не выдавать локальную snapshotпроверку за полный новый CIrun. Не пушить: владелец даст отдельную команду.

## Result
Локальная правка завершена и интегрирована: source commit `470589e26` (авторский `64b062869`), base `97e756f1b`. Root открыл фактический schema diff, unchanged official test и native adapter: update-флаг выключен при заключительной compare-проверке. Дизайн, Java runtime, бизнес-правила и тестовые assertions не менялись. Ничего не удалено; push/deploy/remote CI rerun не выполнялись.

Проверки: Ruff0.17.0 lint иformat-check всего notification-bot PASS,123formatted; AST всех5файлов одинаковый до/после. Точный официальный JUnit source изGitLF: ожидаемый baselineFAILline67→official updatePASS→compare(false)PASS. НастоящийSpring/Swagger/MockMvc/809beans иnativeMongo, узкая локальная обвязка вместо контейнерной базы; это не новый полный CIrun. Итоговый JSON отличается ровно7Geo properties, никаких иныхschema изменений. Helper AST отличается толькоsourcehash; source/XML/frame/binding/wrapperguards сохранены. Hooks/JSON/Gitleaks/whitespacePASS. Native Java/Mongo остановлены, heavy leases0.

Создан [REPORT.md](C:/Users/maksd/.codex/worktrees/9689/rutcampustrack/.agent/worktrees/frontend-refactor-v2/docs/plans/ci-attendance-python-1110/REPORT.md). Изменены:

- [Attendance OpenAPI](C:/Users/maksd/.codex/worktrees/9689/rutcampustrack/.agent/worktrees/frontend-refactor-v2/docs/openapi/attendance.json):25сгенерированных строк/7полей геодиагностики.
- [OpenAPI candidate helper](C:/Users/maksd/.codex/worktrees/9689/rutcampustrack/.agent/worktrees/frontend-refactor-v2/scripts/ci/attendance-openapi-candidate.py):актуальный pin неизменённого официального теста, остальныеguards прежние.
- [bot_menu_client.py](C:/Users/maksd/.codex/worktrees/9689/rutcampustrack/.agent/worktrees/frontend-refactor-v2/services/notification-bot/bot/grpc_client/bot_menu_client.py):Ruffformatonly.
- [prefs.py](C:/Users/maksd/.codex/worktrees/9689/rutcampustrack/.agent/worktrees/frontend-refactor-v2/services/notification-bot/bot/handlers/prefs.py):Ruffformatonly.
- [start.py](C:/Users/maksd/.codex/worktrees/9689/rutcampustrack/.agent/worktrees/frontend-refactor-v2/services/notification-bot/bot/handlers/start.py):Ruffformatonly.
- [observability.py](C:/Users/maksd/.codex/worktrees/9689/rutcampustrack/.agent/worktrees/frontend-refactor-v2/services/notification-bot/bot/observability.py):Ruffformatonly.
- [test_start_handler.py](C:/Users/maksd/.codex/worktrees/9689/rutcampustrack/.agent/worktrees/frontend-refactor-v2/services/notification-bot/tests/test_start_handler.py):Ruffformatonly, тесты сохранены.

Первичные команды/логи/семантическиеproof ировно7fileinventory: [SUMMARY.md](C:/Users/maksd/.codex/worktrees/9689/rutcampustrack/.agent/worktrees/ci-attendance-python-1110/.agent/evidence/ci-attendance-python-1110/SUMMARY.md), `format-and-pin-result.json`, `final-check.json`, `native-03/native-result.json`. Первые localharnessfailures сохранены:javac JVM-option внутриargfile иWSL sandboxE_ACCESSDENIED до запускаMongo; исправлены только локальнаяобвязка/способразрешённогозапуска. ПолныйcontainerIT/coverage/pytest не повторялся: изменены артефакт и форматирование; прежние функциональные CI результаты438+284PASS известны. Новый GitHub CI проверяется после отдельно разрешённого push.
