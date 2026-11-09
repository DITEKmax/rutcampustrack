# Разбор и локальное исправление CI — 10.11.2026

Актуальное задание владельца от 10.11: удалить конкретный упавший E2E, исправить Academic coverage, проверить остальные результаты CI и после проверок отправить изменения в main. Это разрешение заменяет запрет на push из первоначального задания ниже. Deploy не разрешён. Первые исправления `470589e26` сохранены; вторые интегрированы как `631600de8`. Независимое review второго этапа: PASS, блокирующих замечаний нет.

## Дополнение: Academic и E2E

Полный аудит четырёх workflows на97e756f1b: Security/OpenAPI Drift Guard PASS. УCI дополнительно обнаружен Java IT Academic с теми же двумя причинами, что уAcademiccoverage; E2Eaggregate падает вслед заclient. Остальные конкретные задания успешны; Diffcoverage skipped вслед за failed coverage, не отдельный дефект.

Academic: `AcademicGrpcIT.getCampusGeofence_returnsCampusSettings` ожидает канонические55.788328/37.606637/400, но actual `AcademicIdentityIntegrationFixture.seedCanonical()` и ресурс `academic-fixture-seed.sql` по-прежнему вставляют55.788204/37.606762/200. OrdinarygRPC fixture использует первое, поэтому одной правкиSQLресурса недостаточно. Исправляются обе тестовые fixture, production геозона не меняется. `FlywayMigrationIT.authOwnershipCutoverUsesRealPrincipalsAndPreservesOneCommitAcrossUpgradeAndRestart` послеV62 ожидает ровно одну миграцию, а теперь применяютсяV63 иV64. Вместо новой хрупкой цифры2 проверяется применение нужнойV63 с разрешёнными более поздними миграциями; проверки реальных principals, прав, идемпотентности/истории и одного commit остаются.

По прямому решению владельца удаляется только `real geolocation confirms fresh student, then D005 manual absence rejects the blocked attempt` из `tests/e2e/clock-specs/teaching-clock-geo.spec.ts`, отдельный запуск/выгрузка результатаstudent-geo изci.yml и прямой устаревший selector. Другой quick-requestcase, общиеfixtures, teachingclockconfig иprivacyguards сохраняются. Продукт/дизайн не подгоняется под E2E.

Solewriter остаётсяci_attendance_python_fix_1110 впрежнемworktree; parent64b062869+copiedrootdoc95eaf3b77 (treeэквивалентrootd3f31c282). RiskS2 для testguardalignment. Один heavy lease: nativePG16.15/15459/ownnamespace rct-ci-academic-1110, JVM≤1GiB. Проверки: exactexistingFlywaytest сrealPostgreSQL/principals влокальномownedcontainerfacade, canonicalfixture→actualread/JPA; TypeScript/YAML/Playwrightdiscovery безповтораудалённогоUIсценария. Новые businesscode/schema/migrationchanges не входят вscope; oldmigrationchecksums не меняются. Локальныеадаптеры тольковown.agent, shutdown и limitations обязательны. После стабильногоdiff — независимая scoped сверка.

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

## Result первого этапа — Attendance/Python
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

## Result второго этапа — Academic/E2E и полный аудит

Новый авторский commit `2de3e84d34bd8f68c39f3888bacff802d9539b76` интегрирован как `631600de8695acda2da03ec4debe1f6200e513d0`. Изменены тестовые данные, одна проверка миграций и запуск удалённого E2E. Продуктовый Java/SQL, фронтенд и дизайн не менялись.

Два полезных теста Academic сохранены:

- Java fixture и SQL resource теперь содержат утверждённые координаты `55.788328/37.606637` и радиус `400`. Неизменённый `AcademicGrpcIT` уже ожидал эти значения; прежние тестовые данные содержали другой центр и радиус `200`.
- В `FlywayMigrationIT` проверяется применение необходимой V63 вместо общего количества миграций после V62. Новая V64 больше не вызывает ложное падение. Проверки реальных ролей, ACL, данных, rollback, одного commit и повторного запуска сохранены.

По прямому решению владельца удалён один кейс `real geolocation confirms fresh student, then D005 manual absence rejects the blocked attempt`, два выделенных шага CI и его устаревший локальный selector. Оставшийся Today quick-request побайтно сохранён. Другие E2E, общие fixtures и privacy guards не изменены.

### Выполненные проверки

- Реальный PostgreSQL 16.15 и четыре роли без superuser: исходный полный метод Flyway воспроизводит FAIL `expected 1 / actual 2`, исправленный полный метод проходит за 5908 ms. Локальная обвязка заменяет только lifecycle/accessors Testcontainers; JDBC, Flyway, роли, транзакции и assertions настоящие. Все 83 SQL-ресурса совпадают с Git по SHA; старые миграции не правились.
- Значения actual Java fixture и SQL resource проверены через настоящие JPA repository, `AcademicReadService` и метод `AcademicGrpcServiceImpl.getCampusGeofence`: старые данные дают 200, исправленные — 400 и нужный центр. Это адресная проверка, не полный Auth/Spring/network `AcademicGrpcIT`.
- YAML сравнение подтверждает удаление ровно двух выделенных CI steps. TypeScript syntax: 0 ошибок. Playwright discovery: один оставшийся case; его тело совпадает с базой. Браузерный E2E и полный semantic typecheck не запускались.
- `git diff --check`, commit hooks и независимое review стабильного diff: PASS. Reviewer независимо сверил исходники, native evidence, миграции и сохранённый quick-request.
- Свои PostgreSQL/JVM остановлены, heavy leases 0. Локальные QA PWA/TMA оставлены как прежде.

Полные команды, ограничения и первичные доказательства: [Academic/E2E SUMMARY](C:/Users/maksd/.codex/worktrees/9689/rutcampustrack/.agent/worktrees/ci-attendance-python-1110/.agent/evidence/ci-academic-e2e-1110/SUMMARY.md), `native-03/result.json`, `native-03/source-proof.json`, `native-03/seed-result.json`, `static-result.json`. Полный удалённый CI и coverage будут выполнены GitHub после push; локальные проверки не выдаются за их успешное завершение.

### Проверены все завершившиеся workflows на 97e756f1b

| Workflow | Результат аудита |
|---|---|
| CI, 25 jobs | 20 PASS; 5 FAIL: Python, Attendance IT, Academic IT, E2E client и зависимый E2E aggregate. Все первичные причины обработаны этим пакетом. Lifecycle E2E прошёл. |
| Coverage, 10 jobs | 7 PASS; Attendance и Academic падают по тем же причинам; Diff coverage пропущен вслед за ними, отдельного дефекта не обнаружено. |
| Security | PASS |
| OpenAPI Drift Guard | PASS |

Других упавших заданий в этих запусках не найдено. Vue WEB/PWA/TMA, остальные Java сервисы, Python/Vue coverage, monitoring, recovery и ShellCheck прошли. Это состояние старого SHA, а не обещание результата следующего запуска.

### Дополнительный inventory второго этапа

Изменены:

- [AcademicIdentityIntegrationFixture.java](C:/Users/maksd/.codex/worktrees/9689/rutcampustrack/.agent/worktrees/frontend-refactor-v2/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicIdentityIntegrationFixture.java): canonical Java seed.
- [academic-fixture-seed.sql](C:/Users/maksd/.codex/worktrees/9689/rutcampustrack/.agent/worktrees/frontend-refactor-v2/services/academic-service/academic-app/src/test/resources/academic-fixture-seed.sql): такой же canonical SQL seed.
- [FlywayMigrationIT.java](C:/Users/maksd/.codex/worktrees/9689/rutcampustrack/.agent/worktrees/frontend-refactor-v2/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/migration/FlywayMigrationIT.java): проверка конкретной V63.
- [teaching-clock-geo.spec.ts](C:/Users/maksd/.codex/worktrees/9689/rutcampustrack/.agent/worktrees/frontend-refactor-v2/tests/e2e/clock-specs/teaching-clock-geo.spec.ts): удалён один разрешённый кейс и неиспользуемые импорты.
- [ci.yml](C:/Users/maksd/.codex/worktrees/9689/rutcampustrack/.agent/worktrees/frontend-refactor-v2/.github/workflows/ci.yml): удалены запуск этого кейса и выгрузка его результата.
- [playwright.local-batch.config.ts](C:/Users/maksd/.codex/worktrees/9689/rutcampustrack/.agent/worktrees/frontend-refactor-v2/tests/e2e/playwright.local-batch.config.ts): удалён selector этого кейса.

Общий пакет относительно опубликованного `97e756f1b`: 14 файлов — 13 изменены, создан этот отчёт, удалённых файлов нет; удалён один E2E-кейс. Четыре прежних служебных dirty-файла и чужой WIP не включаются в commit. Техническая готовность к разрешённому push подтверждена; фактический SHA публикации и состояние нового CI проверяются после отправки. Deploy не выполняется.
