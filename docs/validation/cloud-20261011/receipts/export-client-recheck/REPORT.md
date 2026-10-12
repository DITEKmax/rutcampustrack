# Узкая повторная проверка ECR01–03

**Исходники: APPROVE. Качество исправленных тестов: APPROVE. ECR01, ECR02 и ECR03 закрыты в source/test-quality scope.** Проверена коррекция `a7c86e1bb180c209f0a0c65680040f2ab40d79f1`, шесть файлов, и влияние на существующие обработчики экспорта. Это не приёмка PWA/TMA с настоящим backend: browser/native/full-stack **NOT_RUN**; EBSR01–04 и отдельный generator verdict не входят в результат.

Requested Sol6.1/xhigh, actual UNKNOWN. Reviewer read-only, npm/browser/Gradle/runtime/mutation не запускал, продукт/авторские receipts/root docs не менял. Восстановлены полный текущий TASK-CONTRACT, CURRENT/decisions/questions/resources, original packet и текущий `rutcampustrack-design`; принятый контракт видимого разреза из COMPONENT_REGISTRY:212 и ПК132:198–201 применён без нового дизайна. Восемь payload-файлов прежнего sealed manifest проверены по SHA256 и сохранены.

## ECR01 — CLOSED

`AdminUsersScreen.vue:251–255` теперь строит request и scope из одного `appliedListQuery`. На 452 снимок очищается перед новым запросом; на 458–466 параметры списка захватываются до await; на 467 отбрасывается устаревший ответ; на 475 сохраняется только текущий успешно показанный разрез без pagination. На 953 выгрузка недоступна во время загрузки/при отсутствии снимка/потере live USERS_VIEW. Поэтому неотправленный черновик поиска не подменяет выгрузку показанного списка, а новый refresh инвалидирует старый inline control. Фильтры роли/статуса и сортировка происходят из того же снимка. При ошибке запроса снимок остаётся null; права и колонки по D006/D007 по-прежнему определяет сервер.

Новый `AdminUsersScreen.test.ts:199–221` монтирует реальный SFC, меняет настоящий v-model, не отправляя форму, проверяет отсутствие нового list request и отсутствие draft search в фактическом export request. Затем отправляет форму и сравнивает search нового list request с export request. Повторное открытие export control после refresh соответствует его реальной invalidation, oracle не ослаблен.

Actual author `frontend-ecr-before-2`: 29 tests, ровно один релевантный assertion FAIL — draft search оказался в export request, ожидалось отсутствие; ошибки/пропуски 0. У этого старого запуска сохранены head/WIP fingerprint/start/result/log/XML, но нет per-file prelaunch manifest: точную старую рабочую копию reviewer независимо по отдельным файлам не подтверждает. Counterexample принятого старого Git source уже сохранён в первом review. Текущий успешный handler тест привязан к frozen файлам по final manifest; это достаточный narrow regression bridge без расширения исторического FAIL claim.

## ECR02 — CLOSED с точной границей sensitivity claim

`export-button.test.ts:90–120` разделяет filter-only и grant-only изменения. В первом disabled остаётся false, во втором search остаётся прежним; late completion не показывает принятие скачивания, текущая callback guard становится false. Маскировка от совместного стимула устранена.

`async-export.test.ts:142` проверяет `Blob.text()` на точное содержимое, дополнительно к MIME/размеру. Прежний counterexample с заменой bytes на нулевые bytes той же длины теперь нарушает assertion. Canonical unknown-state tests сохранены, boundary/parser/generated contracts не менялись.

Контроль на `fixtures/export-grant-gap.ts:4–16` — тестовый **неправильный caller**, всегда передающий disabled=false в **неизменённый настоящий ExportButton**. Выбор fixture делается только внутри теста (`export-button.test.ts:6–10`); обычный запуск возвращает настоящий импорт. Fixture не выдумывает current(), не возвращает готовый verdict и не переписывает внутренний product guard.

Actual `frontend-ecr-final-sensitivity-red-1`: четыре теста, один содержательный grant-only FAIL на `export-button.test.ts:117` — current() остался true после потери права; три остальных positive/filter/catalogue controls PASS, errors/skips 0. Prelaunch hashes test/fixture/настоящего ExportButton совпадают с frozen текущими bytes. Это доказывает чувствительность oracle к потере **передачи caller grant binding** на границе настоящего компонента. Это **не** выполненная mutation внутреннего guard, не доказательство серверного revoke и не отдельный mutation каждого enum/stream guard. Такое ограниченное выбранное доказательство соответствует требованию meaningful sensitivity для существенного client authority правила; exhaustive mutation не требуется.

После удаления fixture env actual `frontend-ecr-final-green-2` выполняет настоящий компонент и четыре адресных файла: 41 tests, failures/errors/skips 0. Эти проверки не складываются с прежними 167 в новый показатель готовности.

## ECR03 — CLOSED

`HeadmanStatsScreen.vue:697–698` использует `reportDownloadAcceptedMessage(reportDownload)`. Существующий helper (`report-download-client.ts:593–594`) сообщает browser handoff для PWA и native Telegram acceptance для TMA. Ни один из текстов не утверждает завершённую загрузку файла. Actual согласованный browser feedback/keyboard/file flow остаётся отдельной приёмкой, не заменён wording-only unit.

## Источники и receipts

`source-equivalence.json`: шесть correction files = author inventory = frozen Git blobs = рабочие bytes; остальные 23 файла original client inventory неизменны. HEAD экспорта может двигаться из-за отдельных backend test-config commits; source verdict привязан к шести blobs, а не к такому HEAD.

`author-evidence-verification.json`: независимо сверены SHA256 start/result/log/XML и hashes prelaunch manifests. Final-green-2 и types3 используют одинаковый manifest `2e6326a8…`, exact current шесть файлов и unchanged ExportButton/browser/client dependencies; sourceFilesUnchangedDuringRun=true, start/end WIP одинаков. Types3 actual log подтверждает core source+tests, PWA Vue и TMA Vue. Scoped lint2 покрывает пять exact final файлов и предыдущую версию только AdminUsersScreen.test; lint3 покрывает именно final версию этого теста. Все актуальные selected checks terminal exit0.

Types1 сохранён как **FAIL/exit2/source changed during run**, start/end WIP различаются; он не получает current PASS credit. Sensitivity manifest отличается от final manifest только вне его выполняемого scope — предыдущей type-annotation версией AdminUsersScreen.test; выполняемые grant test/fixture/product dependencies exact final. Reviewer не восстанавливал исторический полный WIP fingerprint по неизвестным untracked backend bytes: per-file final prelaunch bridge проверен непосредственно, broader backend identity не заявляется.

Новых blocking findings в narrow correction нет. Коррекция готова к назначенной интеграции с сохранением отдельного backend/generator verdict. До пользовательского PASS остаются настоящие PWA/TMA → server/job/worker/file пути, D006/D007 и revoke/logout/session/role boundaries, stale socket frames, фактические download bytes/MIME/filename и native host acceptance, mobile keyboard/overflow/root-font состояния и итоговые gates. Подробные исходные ограничения первого review сохраняются.
