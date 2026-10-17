# Независимое review — APPROVE

Блокирующих findings нет. Проверен целостный test-only пакет обновления синтетического расписания V17 → V18 → V30; это конечное подтверждение выбранного набора данных, не закрытие всего P18/B50.

Reviewer: `/root/schedule_upgrade_independent_review`, read-only относительно репозитория, без native-прогонов, Docker-команд, детей и исправлений автора. Requested model/effort: Sol6.1/high; actual UNKNOWN. Единственный созданный файл reviewer — этот отчёт. Общие документы и интеграция остаются у Root.

Base: `a85219366ac9995fc0bc170ecf128bd4ab8a6cfc`. Проверенная авторская revision: `ca2aeaa1d930d35f1f1b57be824a46b31f3c4b63`, branch `codex/schedule-historical-upgrade-20261013`, clean worktree `/workspace/rct-worktrees/shared-validator-execution-20261013`. Единственное изменение относительно base — `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/migration/StudentOccurrenceMigrationIT.java`; SHA256 `53a976a533cf04949d8385df06651dd50e38e225d15cfb7ec34b21ddf591d504`. Все 30 SQL-миграций самостоятельно сравнены с base: побайтно неизменны; product/config/schema edits отсутствуют. `git diff --check` прошёл.

Самостоятельно открыты TASK-CONTRACT, CURRENT, decisions/questions/resources, RULES, root/services/tests AGENTS и workflow; исходный критичный метод на base, текущий diff/метод/helpers, FlywayMigrationIT и применимые DDL/индексы/столбцы миграций V17–V30. Остальные неизменные продуктовые семейства не переаудировались.

## Качество проверок

- Fixture создаётся в disposable PostgreSQL16, отдельная схема класса с SAME_THREAD; V17 содержит шаблон, occurrence, физическую cancelled lesson с current pointer и явную CANCELLED lifecycle entry. Insert RETURNING связывает реальные ID; дата события фиксирована. NOW-поля сравниваются с собственным снимком в той же БД, поэтому wall clock не определяет ожидаемый результат.
- Исходные проверки V18 сохранены: cancelled/current lesson и отсутствие выдуманных day/week snapshots или fences. Новая часть снимает полные упорядоченные JSONB-снимки на границе V18 и проверяет сохранность после остальных 12 миграций, current V30/no pending и Flyway validate. Проверяются исходные version/script/checksum для первых 18 записей; repair/clean не используется.
- В снимках templates/history исключены только реально добавленные V27 и V20/V21/V25/V27 столбцы. Их defaults/null отдельно проверены, поэтому исключение не скрывает изменение старых полей. IDs, FK-ссылки, cancellation state, generation/revision, причины/actor/event timestamp остаются внутри сравнения.
- Пять исторических индексов сначала обязаны существовать и быть ready/valid; после upgrade сравниваются также их pg_get_indexdef. Три выбранных новых индекса обязаны быть ready/valid. Это проверка выбранных объектов, не полного каталога DDL.
- Четыре последовательности получаются через schema-qualified pg_get_serial_sequence для actual id columns: две исходные BIGSERIAL и две V17 identity. Проверяются last_value и is_called после upgrade и повторного zero-work migrate; nextval должен дать сохранённый ID + 1. Сам nextval выполняется только в disposable схеме после проверки сохранности.
- Неизменённые fresh-install/validate/no-op и pre-V17 source-refusal проверки переиспользуются. FlywayMigrationIT самостоятельно прочитан и побайтно совпадает с base; отказ V17 сохраняет старую строку, отсутствие новых columns и отсутствие успешной записи V17. Этот review не выдаёт переиспользованное evidence за новый native-прогон.

## Actual evidence и чувствительность

Источник: `/workspace/scratch/rct-validation/schedule_historical_upgrade/outcome.safe.json`, соответствующие безопасные container receipts и локальный конечный XML, прочитанный только как counts/case metadata без raw XML/stdout.

- Scoped Spotless apply/check: exit0, 20.62s. Initial selected PostgreSQL test: native0, 1 PASS / 0 failures/errors/skips, 55.61s.
- Контроль временно добавил только setval(lessons sequence, last ID, false). Реконструированный в памяти mutant SHA совпал с `e4f220460ff871b6f160a002dc85e746244371fb57efce4c0e043058489d8a8a`. Mutant line445 — именно assert сохранности последовательностей (в restored source line442). Actual RED: native1, 1 assertion failure / 0 errors/skips, 47.86s; sanitized proof фиксирует true/false is_called. Private Gradle log подтверждает AssertionFailedError на этом frame, не infra/compile failure.
- Restored GREEN завершён: native0, 1 PASS / 0 failures/errors/skips, 50.01s. Reviewer самостоятельно проверил конечный XML: ровно нужный testcase, counts 1/0/0/0, timestamp `2026-10-13T23:08:37`. Текущий исходник совпадает с принятым SHA; compiled class SHA `8053ecef567dd716251c892674467cc7357a7a55ab08cb8c4857cf9fe5c8b070` совпадает с первоначальным GREEN.
- Все три observer receipts: failure null, settled true, active_owned_ids empty; foreign inventory сохраняет 0 containers / 11 volumes / 3 networks. Native process receipts settled/reaped. В трёх `/tmp/rct-schedule-upgrade-*` каталогах остались только containers.safe.json как evidence; это не активные DB/authority ресурсы. Фикстуры синтетические, новые безопасные receipts не содержат credentials, raw XML или DB contents.

Ограничения: сохранность полных снимков доказывается от V18 boundary до V30; переход V17→V18 использует прежние целевые assertions и неизменный SQL. Набор включает одну recurring cancellation/history, выбранные индексы и четыре populated sequence states, а не все lifecycle/one-off/homework/legacy combinations или production upgrade. Чувствительность показана одной релевантной поломкой sequence bit; это не exhaustive mutation. Parent P18/B50 остаются UNKNOWN за пределами данного набора. Integration/push/current CI — ответственность Root и не заявлены выполненными этим review.
