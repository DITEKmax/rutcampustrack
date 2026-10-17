# Независимое review экспортного клиента

**Исходники: CHANGES_REQUESTED — ECR01, P2. Качество тестов: CHANGES_REQUESTED — ECR02, P2.** ECR03, P3 — отдельная небольшая ошибка сообщения PWA. Это verdict только для 27 клиентских файлов `21587695782fc5da3cee9c58589f9bed652e53e6..c0f6c43ee1cf354bf483ebb1ff8fdd8dca02b41b` и прочитанных зависимостей. Backend `cf6d1a238441cc1b122e205e160aa23a741a3942`, EBSR01–04 и отдельное review генератора не входят в этот verdict. Клиентская приёмка с настоящим backend **NOT_RUN**.

Requested `gpt-6.1-sol/xhigh`; actual model/effort **UNKNOWN**. Reviewer независим от автора, продукт не менял, npm/runtime/browser/Java/Docker и mutation не запускал, детей не создавал. Прочитаны полный TASK-CONTRACT и актуальные CURRENT/decisions/questions/resources/packet, проектные правила, текущий `rutcampustrack-design` и адресные принятые registry/wireframe/token/decision источники.

## ECR01 — P2: выгрузка пользователей берёт черновик поиска вместо показанного разреза

Место: `frontends/mobile-core/src/features/admin-users/AdminUsersScreen.vue:249–251`, `:disabled` на 946. Принятый источник: `docs/research/reference-rutcampustrack-design/knowledge/job-stories.md:253–255`, `docs/design/COMPONENT_REGISTRY.md:212`, `docs/wireframes/admin/132-admin-users.md:198–201`: текущий разрез, то, что показано, тот же отбор и порядок.

Контрпример по исходникам: после успешного списка с поиском A пользователь вводит B, не отправляя форму, и нажимает «Выгрузка» → «Скачать». Поле на 926 обновляет `search`, но список запрашивается только при `submitSearch` на 753–756; отображение на 973–994 продолжает показывать результат A. Новый `exportRequest` на 251 уже отправляет B. У поля пользователей нет `@change`, автоматически отправляющего поиск при переходе к кнопке. Кнопка допускает эту операцию. Получается другой официальный отчёт, чем выбранный видимый набор. Это source counterexample, **не выполненный browser FAIL**.

Correction автора: отделить черновик поиска от принятого серверного разреза либо явно применить поиск и дождаться текущего успешного ответа перед экспортом; запрос и scope выгрузки должны происходить из одного принятого снимка без pagination. Сохранить существующие filters/sort и live grant authority. Нужен регрессионный oracle через настоящий `AdminUsersScreen` handler: изменить только черновик, не отправляя форму, и доказать отсутствие выгрузки непоказанного разреза; затем применить новый поиск и доказать совпадение параметров списка и экспорта. Shared тест на 52–54 создаёт собственную lambda `ADMIN_GROUPS`, поэтому это место не проверяет.

## ECR02 — P2: критичная клиентская чувствительность не доказана, совместный стимул маскирует отзыв права

Места: `frontends/mobile-core/src/shared/components/export-button.test.ts:83–96`, `frontends/mobile-core/src/shared/async-export.test.ts:71–75`, browser oracle `async-export.test.ts:130–144`. Требование: TASK-CONTRACT:74–78 и packet Verification — meaningful negative oracle и фактическое sensitivity evidence для нового критичного права/enum boundary, с восстановленным final source.

Тест ExportButton на 92 одновременно меняет search и disabled. Нарушение всей disabled invalidation/guard ветки всё ещё даст `current() === false` через изменение search/revision; disabled DOM binding тоже останется true. Поэтому этот PASS не доказывает именно отзыв права во время подготовки. Проверка скачанного Blob на 141 утверждает MIME и длину, но не содержимое: замена сохранённых chunk bytes на нулевые байты той же длины сохранила бы эти assertions. Это статические контрпримеры к oracle; reviewer **не выполнял mutation**.

Новые проверки каноничных `READY`/`4`/`unknown`, чужого hint, denied fresh read и stale completion полезны и проверяют наблюдаемую границу. Однако в прочитанных frontend start/result/XML/attempts нет фактического red/green sensitivity run с соответствующим дефектом права или unknown-state parser. Backend `guard-sensitivity-*` относится к другим исходникам/правилу и этот пробел не закрывает. Старый клиентский endpoint не является достаточным known-bad доказательством, если новый тест падает лишь из-за отсутствующего symbol/API.

Correction автора: разделить смену фильтра и отзыв права на независимые stimuli, удерживая остальные props; проверить реальное содержимое переданного Blob; выполнить один адресный контролируемый known-bad/sensitivity прогон для существенной клиентской staleness/enum границы с корректной причиной FAIL и возвратом final source/PASS. Не требуется exhaustive mutation или полный suite. Reviewer не исправляет это замечание.

## ECR03 — P3: успешная выгрузка графика в PWA сообщает о Telegram

Место: `frontends/mobile-core/src/features/headman-stats/HeadmanStatsScreen.vue:697–699`. Новый PWA App подаёт browser port в headman carrier (`frontends/pwa-vue/src/App.vue:1758–1761`). `exportTrend` после browser `accepted` пишет «Telegram принял запрос…», тогда как обычная таблица уже использует `reportDownloadAcceptedMessage` на 644. Пользователь PWA получает неверное место завершения скачивания.

Correction автора: использовать тот же carrier-aware helper для графика. Новый unit-тест только на wording не нужен; проверить сообщение на затронутом реальном PWA пути. P3 самостоятельно не блокирует source verdict.

## Принятое в прочитанном source scope

- Enqueue → queued/preparing → authenticated status read → ready → fresh ticket. UUID создаётся до `authenticated` и сохраняется при одном 401 refresh retry. Повторные клики ограничены busy; ошибка допускает новую попытку. State parser принимает ровно четыре каноничных кода, неизвестное состояние не выдаёт capability.
- Session generation и callback текущего разреза проверяются вокруг await, polling и file stream; Vue invalidates scope/port/disabled и unmount. Old/foreign socket hint только будит запрос статуса, не передаёт authoritative state/file; callback уведомления проверяет current generation. Listener снимается в finally. Само наличие этих guards не является runtime acceptance всех revoke/logout races.
- Browser использует проверенный relative capability path, один GET без Authorization, ожидаемый MIME, непустой ограниченный stream ≤20 MiB, безопасное имя; до handoff повторно проверяет owner. Создаёт Blob URL, удаляет anchor и отзывает URL по таймеру 60 s либо при ошибке handoff. Не сохраняет bearer или PII в URL/имени/local storage. Уже разрешённый handoff и последующая 60 s уборка не доказывают факт завершения скачивания браузером.
- TMA сохраняет отдельный host adapter, HTTPS exact-origin/relative-path/no-query/no-hash/filename guard; старый host без `downloadFile` возвращает unsupported. Callback acceptance означает принятие native запроса, не завершённую загрузку. Реальный Telegram не проверялся.
- Admin requests не несут client-granted FULL/LIMITED/column selectors/credentials; текущие USERS_VIEW/GROUPS_VIEW управляют доступностью UI, сервер остаётся authority D006/D007. Existing teacher/headman selectors сохраняют server formats, разрешённые scope/filter/sort/type/week параметры без pagination. Новый subject-journal control ограничен активным semester и выбранными типами.
- Новый shared control использует русские подписи, native controls/status/alert/aria-busy, существующие focus и rem tokens; inline раскрытие не создаёт нового overlay. Geometry/contrast/mobile overflow/root-font/native keyboard и browser accessibility не измерялись. Текущий design не заменён desktop resize или Figma публикацией.

## Проверенная привязка evidence

`source-equivalence.json`: **27/27** author SHA256 = worktree bytes = frozen Git blobs. Независимо восстановлен prelaunch fingerprint `3969c99a2a4eabed586f0f18061ca296cca411a4aa2b90059369c4f15d131826`: binary diff 22 отслеживаемых файлов + пять новых файлов + исключённый read-only node_modules symlink; он точно совпал с final author start/result. Reviewer не выполнял эти команды заново.

`author-evidence-verification.json`: все восемь selected log hashes и start/end WIP fingerprints совпадают. Actual XML старого WIP `92bada3d…`: **13 suites, 167 tests, failures/errors/skips 0**; это квалифицированный receipt предшествующих исходников, не независимая проверка всех final bytes. Final-source ExportButton recheck: **3 tests, failures/errors/skips 0**, точный reconstructed `3969c99a…`; эти три уже входят в предыдущие 167, складывать в 170 нельзя. Lint 24 файлов — старый WIP; final recheck двух ExportButton файлов — exact final digest. Whole-workspace lint/build не заявляются PASS.

Final typecheck receipt привязан к exact digest и действительно содержит core source+tests, PWA Vue и TMA Vue. Strict consumer probe привязан к exact digest и проверяет два старых dynamic aliases, новые literal job/file/ticket и точный four-state union. `contract-compatibility.json`: Auth schema/header hash совпадает, четыре state values совпадают с generated и boundary; две legacy generated object bodies идентичны literal route bodies; четыре Academic registry paths присутствуют; Schedule byte-exact относительно parent. Actual normal Auth/Academic/Schedule `--check` receipts PASS на предшествующем `2e3b71ec…`, но reviewer normal generation не запускал; generator source review отдельное.

## Что требуется дальше

Автор исправляет ECR01 и ECR02; независимая narrow recheck затронутых мест и их влияния. Родитель сохраняет отдельные backend EBSR01–04 и generator verdicts; этот клиентский пакет их не закрывает.

До пользовательской приёмки нужны реальные PWA/TMA → Gateway/Auth → job worker/report/file пути: FULL/LIMITED D006/D007 в initiation/status/ticket/download и итоговых колонках/аудите; revoke/logout/role/session/account change в enqueue/poll/stream/native handoff; stale private notification после смены owner; корректные bytes/MIME/filename для пяти табличных форматов и png/html графика; empty/error/retry/catalogue, истёкшая capability и зависимость worker/renderer; настоящий PWA Blob handoff и согласованные mobile keyboard/overflow/root-font состояния. Native Telegram/external private socket acceptance требует отдельного доступного host/runtime. Mock Vue/STOMP/host tests и unit green не продвигают всю клиентскую семью в PASS/DONE.

Созданы только собственные review artifacts; продуктовые и root docs не изменены. Безопасный immutable manifest — `immutable-review-manifest.json`.
