# CHANGES_REQUESTED — export backend security review

Frozen revision: `cf6d1a238441cc1b122e205e160aa23a741a3942`, parent `1226d13b4ef9a8b96af2ba44d68ebf54771600e6`; baseline `3908fad9624f97aec8891fc79338ceb6b0616bf3`. Requested `gpt-6.1-sol/xhigh`; actual model/effort UNKNOWN. Fresh independent reviewer, read-only; no Java/Gradle/Docker/browser, product edits, children or resource leases. Only this review scratch directory was written. Frontend WIP is outside this verdict.

Четыре blocking findings: один P1 и три P2. Все ниже — подтверждённые выводы по frozen-исходникам с конкретными контрпримерами; reviewer не запускал runtime и не называет их фактическими HTTP FAIL. Исправляет назначенный export author; после исправлений нужны ограниченная независимая recheck и соответствующие реальные проверки. Этот commit не одобрен для интеграции.

## EBSR01 / P1 — новое задание не проходит проверку срока authority в Gateway

Адрес: `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/ReportDownloadTicketService.java:261–262`; связанный вызов `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/security/ReportDownloadTicketDownloadFilter.java:173,250–251`; неизменённый verifier `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilter.java:222–224`.

Спецификация: EA01, `TRANSPORT.md:33`, `COMPONENT_REGISTRY.md:210`: очередь → генерация → ready → получение файла. TASK-CONTRACT §4 требует действительную подписанную authority и наблюдаемый результат.

`ExportJobService` задаёт срок задания `t0+600s`. `authorizeJob` помещает этот же срок в `ticketExpiresAt` и подписанный `report_ticket_exp`, хотя срок самого JWT короткий. Claim нового задания в `t0+1s` попадает в `generate` и прежний ticket verifier. Тот отвергает любой `ticketExpiresAt > now+60s`. Backend-запрос ещё не выполнялся; worker записывает `GENERATION_FAILED`, а после трёх быстрых попыток задание становится ERROR. Короткий JWT не устраняет отдельную проверку десятиминутного срока. Это затрагивает нормальные новые задания всех report kinds.

Исправление: согласовать reminted worker authority и её проверку: ограничить свежий capability срок, сохранив окно хранения задания, либо ввести отдельную строго проверяемую job authority. Не ослаблять существующую 60-секундную проверку обычных tickets, подпись, identity, report hash, expiry и live grants.

Verification: реальная RSA-проверка свежего Auth claim в Gateway worker/generate, backend dispatch и сохранённые READY bytes; meaningful red-before/green-after именно для этого срока. Положительный fresh job, истёкшая/поддельная authority и изменённый selector, плюс обычные 60-секундные tickets должны иметь отдельные наблюдаемые результаты. Нынешние `ReportDownloadTicketDownloadFilterTest` подменяют verifier, а `ExportJobWorkerTest` передаёт claim с null authority и подменяет весь generate. Auth HTTP-тест вручную вызывает complete и не проходит Gateway worker; эти greens дефект не покрывают.

## EBSR02 / P2 — expiry race при чтении файла превращается в 500

Адрес: `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/export/ExportJobService.java:69`.

Спецификация: TASK-CONTRACT §4, expiry/concurrency и корректные ожидаемые отказы без неожиданного 500.

`load` видит READY до истечения срока, затем выполняется первый authority read. Если срок проходит перед SELECT content, условие `expires_at>NOW()` даёт ноль строк; одновременно enqueue может удалить уже истёкшую строку. `JdbcTemplate.queryForObject` бросает `EmptyResultDataAccessException`, а не возвращает null. Проверка на следующей строке недостижима. Узкий advice это исключение не обрабатывает; shared catch-all возвращает 500. Это обычная гонка двух чтений одного истекающего результата.

Исправление: явно обработать отсутствующий/истёкший ready row корректным expired/not-found отказом. Сохранить вторую проверку текущих прав и no-store; не маскировать прочие ошибки БД blanket catch.

Verification: детерминированно истечь/удалить строку между первым admission и чтением bytes, проверить контрактный 4xx, отсутствие содержимого и no-store. Проверить normal READY bytes и отказ после revoke. Нужен реальный PG/controller либо контролируемый узкий boundary test, затем relevant red/green.

## EBSR03 / P2 — kind принимает позиционный wire identifier

Адрес: `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/ReportDownloadKind.java:4`.

Спецификация: TASK-CONTRACT §4 enums — позиция enum не является стабильным wire identifier. OpenAPI объявляет kind строкой; существующие строковые значения должны сохраняться.

Расширенный enum остался обычным Jackson enum без строгого boundary mapping. Реальный shared Jackson customizer не включает `FAIL_ON_NUMBERS_FOR_ENUMS`; локальный Gateway mapper также оставляет default ordinal coercion. Поэтому JSON `kind:8` соответствует новому `ADMIN_USERS`, и default scalar coercion также допускает числовую строку. Это незаявленное позиционное отображение; перестановка enum изменяет его смысл. Current grants всё ещё защищают данные — обход прав этим finding не утверждается.

Исправление: узко принимать только явные существующие строковые kind codes; отвергать числовой/ordinal ввод без глобального изменения mapper/config ownership и без изменения смысла старых строк.

Verification: реальные настроенные Auth/Gateway decoders: integer и numeric string, unknown/null/malformed → безопасный отказ; все десять canonical strings → прежний смысл. Selective known-bad decoder control и восстановленный green. Строгие registry filter decoders и State mapping не закрывают иной kind decoder.

## EBSR04 / P2 — прежний ticket issuer не проверяет новые registry grants при initiation

Адрес: `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/ReportDownloadTicketService.java:116–121`; public route `/auth/report-download-tickets`.

Спецификация: D007 требует текущие USERS_VIEW/GROUPS_VIEW и scope при initiation/read/download; выбранная роль и её grants не заменяются самим наличием selectable session.

Новые ADMIN_USERS/ADMIN_GROUPS доступны в старом public selector. `issue` вызывает `issueReadyJob`, который проверяет active selectable session и отдельно лишь trend/roster. Teacher/student либо LIMITED ADMIN только с GROUPS_VIEW может выпустить capability для ADMIN_USERS: до записи Redis ticket проверки ADMIN/USERS_VIEW нет. Поздний Academic endpoint запрещает саму генерацию; finding касается выпуска capability/инициации, не доказанной утечки содержимого. Правильный отказ на durable-job initiation не закрывает этот отдельный public entry point.

Исправление: применять текущую report-role/grant admission к новым видам также при legacy issuance, переиспользовав одну rule без циклических зависимостей. Сохранить sync compatibility, прежний TTL и fail-closed поведение.

Verification: настоящий Auth HTTP/Redis: teacher/student и LIMITED wrong/no-grant issuance запрещены без записанного capability; соответствующие USERS_VIEW/GROUPS_VIEW разрешены; revoke и authority outage запрещают выпуск; FULL-only Telegram column rule сохраняется. Relevant negative control + восстановленный green.

## Что независимо проверено в исходниках и evidence

- Все 62 committed blobs совпадают с author inventory по SHA-256 и размеру. Дополнительно сохранены 10 frozen зависимостей; все совпали с прочитанными immutable dependency bytes. `frozen-source-manifest.json` и `dependency-manifest.json` фиксируют точный inventory. Один Academic Flyway pipeline владеет V56; historical migrations этим commit не изменены. Root при интеграции должен сохранить уже принятое T001 predicate изменение в SubjectRepository и не переносить parent config повторно.
- Независимо открыты 18 оригинальных acceptance sources: SHA совпали; критичные fragments и пять датированных решений прочитаны. D006/D007 прочитаны в текущем canonical decision, полный TASK-CONTRACT с D008 восстановлен с SHA `c9336b4770425eb1f9c80f3a793c005191b673c97467dc819510ad446ce94d5c`. D008 к export не относится. Метаданные identity/status в ledger сохраняют provenance; неизвестные даты/owner не выдуманы.
- READY content, безопасный audit fact и outbox сохраняются в одной локальной транзакции. Old lease после восстановления не принимается; enqueue request ID с другим binding hash даёт conflict. FULL flag хранится отдельно от actor role; safe user projection не включает password/hash/recovery. Read/ticket/file/WS admission привязаны к точной сессии и текущим grants. Private events несут только job ID/state; outbound проверяет socket/session/user/job. Это ограниченный source review с указанными findings, не blanket security PASS.
- OpenAPI semantic comparison независимо подтвердил 10 добавленных paths, append трёх kind strings и additive schemas/properties; старых members не удалено/не изменено. Proto SubjectInfo сохраняет поля 1–3, добавляет 4–5; новый consumer при отсутствующем group ID запрещает чужой scope. Это не доказательство любой mixed-version deployment совместимости.
- Independently hashed original logs всех 14 phases из backend-cf6d-tests.json: все 14 SHA совпали. Original XML прочитаны по suite/case/outcome; сохранены лишь whitelisted metadata, методы, counts и failure types. Actual Auth HTTP/PG/Redis/RSA проверяет login, explicit ADMIN selection, queue/claim, вручную переданные committed HTML bytes, Redis redemption и logout denial. RabbitTemplate mocked. Registry MVC/JPA/PG проверяет filtered complete rows, direct HTML/DOCX/XLSX safe columns, grants и exact async lease/selector. Native converter mocked.
- `full-guard-sensitivity-bad-fixture-2` действительно выполнил Auth task с `--rerun`: negative oracle упал, потому что expected exception не возникло. Первый Auth sensitivity run был UP-TO-DATE и остаётся NOT_RUN. Headman permissive-scope fixture также действительно упал на отсутствии expected rejection. В последующем normal run Auth guard 1 и headman 4 cases green, хотя общий invocation был FAIL из-за шести Gateway cases; отдельный Gateway rerun 23 cases green. Это допустимые bounded результаты, не green всего initial invocation.
- SQL FULL→LIMITED fixture меняет rolesVersion; оно не изолирует FULL metadata guard. Отдельный typed LIMITED/current-session fixture изолирует этот guard, но использует mocked upstream admission/profile seam. Эти controls доказывают чувствительность отрицательного oracle к известной permissive authority/scope fixture; они не являются mutation каждого production guard, wire decoder, SQL CAS или transaction boundary.
- Baseline missing-function oracle независимо открыт: один MVC case ожидал 200, фактически получил 404. Это evidence отсутствующего accepted endpoint, не red-before негативного authorization правила. Early held source manifest: 40/40 original snapshots совпали; 27/40 совпадают с final frozen files, 13 изменены позже. Aggregate HEAD+WIP hashes без per-file final prelaunch snapshots не доказывают byte-equivalence каждого текущего critical source с его старым green. Поздние receipts не переименованы в проверки committed cf6d.
- Realtime-green-2 original XML содержит 8 tests (5 outbound + 3 consumer), все green/0 skip; author wording «9» из этого receipt не подтверждено. Spring template/simple broker настоящий; job admission mocked. Реального Rabbit/socket/client chain здесь нет.

## Не закрытые verification dependencies

1. Автор исправляет EBSR01–04 и передаёт coherent frozen commit, exact inventory и red/green evidence; reviewer rechecks affected rule/dependencies. Особый обязательный риск — реальный Auth-signed claim через настоящий Gateway verifier/worker, который нынешние mocks скрыли.
2. Для существенных изменённых sources нужны prelaunch per-file hashes или эквивалентный воспроизводимый immutable source bridge к конечному commit. Зафиксировать текущие sources/dependencies до исправительных запусков; не объявлять старые aggregate WIP hashes достаточным final byte-equivalence доказательством.
3. Сохранить selective sensitivity для strict optional-filter/kind decoder и новых значимых data/expiry rules; неизвестный фильтр должен отказать, а не расширить выборку. Actual competing worker ownership/lease expiry и revoke-during-generation требуют достаточного наблюдаемого результата, не только последовательного retry.
4. Native generated registry/headman DOCX→PDF/PNG completeness, MIME/size и безопасные user data; актуальный private Rabbit/WS delivery с live revoke; real PWA/TMA async/file flows остаются NOT_RUN/UNKNOWN. Эти scopes выполняются назначенными авторами под root leases, reviewer их не запускал.
5. После source approval — integration с сохранением T001/config ownership, достаточные current checks, formatter/contract/consumer checks и exact final remote CI/Coverage/Security. Ни EA families, ни клиентская приёмка, ни DONE из этих unit/mock/частичных integration receipts не следуют.

Безопасные review receipts: REPORT.md, findings.json, review-ledger.json, source-ledger.json, frozen-source-manifest.json, dependency-manifest.json, evidence-review.json, early-source-equivalence.json, openapi-compatibility.json, CURRENT.json и attempts.ndjson. Raw logs/XML/stdout/request bodies/properties/keys не копируются в publishable review package. Frozen scratch source copies служат только локальной навигации и не предлагаются как root documentation artifact.
