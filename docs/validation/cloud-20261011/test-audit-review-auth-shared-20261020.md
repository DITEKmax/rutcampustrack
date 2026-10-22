# Independent review: auth/shared accepted test audit

Decision: **APPROVE the assigned source changes**. No substantive blocking finding identified. This is source-review approval, not a claim that the Docker-backed integration tests passed. BS-R11 is approved only for the shared fixture/helper portion; its Academic/Attendance/Schedule actual-channel consumers remain a separate integration condition owned by root.

Reviewer requested model/effort: gpt-6.1-sol/high. Actual runtime model metadata: UNKNOWN. Reviewer is independent of the sole author. No children, source edits, builds, tests, application runtime, database/broker operations, CI operations, external messages, private outputs, or production resources were used. The only reviewer-written artifact is this report.

## Revision and scope

- Baseline: `2eca01a3ef4c65cc2ed1f1ccc6bb7c50eb211615`.
- Reviewed worktree: `/workspace/work/test-audit-implementation/auth_shared`.
- Governing scope: `.agent/orchestration-v2/RULES.md`, `CURRENT.md`, root's current audit GO, canonical `docs/validation/cloud-20261011/TEST-AUDIT-20261020.md`, and `auth_shared-packet.md`.
- Read each changed diff and each new file, then the relevant production paths and retained counterpart assertions. Review was of the new audit changes, not the entire historical PR.
- All 45 inventory paths match their declared SHA256 values, including the five new files and one removed file. No production, dependency, generated, workflow, exclusion, or threshold change appears in the reviewed inventory. The pre-existing disabled CompositeLoginKeyResolverIT annotation was only reformatted; its three explicitly accepted obsolete methods were removed. No new skip/xfail was introduced.
- Tracked diff SHA256 before and after review: `e2bee525d6d2917a028cfefc53a8663224ea3beeb714c08189a6a912905e2083`.
- Sorted inventory aggregate, hashing `path + NUL + sha256-or-None + newline`: `8077078ebad278cb9b9738f6431ee18a2d5fc1d5b854ecd130e27ed57c9f7908`.

## Per-ID review

“Accepted” below describes independent source review of the prescribed change and retained guarantee. It does not convert compile evidence into integration runtime evidence.

| ID | Review | Independently checked guarantee |
| --- | --- | --- |
| AG-D01 | Accepted | Only duplicate login-cookie oracle removed; SameSiteCookieContractIT still asserts HTTP 200 and all five cookie attributes. |
| AG-D02 | Accepted | Missing refresh cookie still receives 401 in SameSiteCookieContractIT. |
| AG-D03 | Accepted | AuthIT retained refresh sends only the cookie, asserts access/refresh results and rotation, and does not add a CSRF header. |
| AG-D04 | Accepted | AuthOtpFlowIT retained real HTTP 204/null body, six-character Redis proof, and matching Rabbit payload. |
| AG-D05 | Accepted | WsTicketConcurrencyBoundaryIT retains exactly one HTTP winner, one 404, deleted Redis ticket, and another sequential 404. |
| AG-D06 | Accepted | JDBC role-selection survivor asserts grant, SV increment, same-role no-event idempotency, and stale expected-version conflict; HTTP role switch survives. |
| AG-D07 | Accepted | JDBC revoke-current survivor asserts durable later denial and exactly one logout event; bearer logout flow survives. |
| AG-D08 | Accepted | JDBC stale grant/version and foreign-hint survivors retain rejection and no extra session/login event. |
| AG-D09 | Accepted | Only the three named methods in the already-disabled obsolete composite class removed; active resolver and rate-limit counterparts remain. |
| AG-D10 | Accepted | Only redundant fractional fixture cases removed; both fractional invalid claim cases and whole-second positive cases remain. |
| AG-D11 | Accepted | OWNER-role rejection remains in the parameterized invalid-access claim cases. |
| BS-D01 | Accepted | Only noAuthRequired removed; unauthenticated legacy/modern MIME routes retain 204 and metrics assertions. |
| BS-D02 | Accepted | Retained duplicate-call test starts with the same successful first claim. |
| BS-D03 | Accepted | Retained exception-audit test asserts propagated class/message and failed audit entry. |
| BS-D04 | Accepted | Retained empty-OpenAPI test checks components and ErrorResponse schema registration. |
| AG-R01 | Accepted | Two held real BCrypt matches, three distinct existing login identities, HTTP saturation rejection, exact maximum, permit recovery and bounded worker cleanup replace the rate-limit-confounded timing oracle. |
| AG-R02 | Accepted | A trusted control succeeds; the same complete claims are re-signed with the rogue key; 401/code/no-store and zero admission/downstream calls remain. |
| AG-R03 | Accepted | Complete session claims and valid control precede individual purpose/subject/role negatives; response code, chain denial and context clearing are observed. |
| AG-R04 | Accepted | Wrong OTP is guaranteed different from issued proof; request precondition and no session/verified-event delta are asserted. |
| AG-R05 | Accepted | Real HTTP admission independently isolates RV drift with unchanged SV, and SV drift after a role round trip with unchanged identity/RV. Both yield SESSION_STATE_STALE/no-store; current controls succeed. |
| AG-R06 | Accepted | Role-only denial now uses its own fresh actor/challenge/code, separate from binding mutation; exact 409 and no receipt/consume/session/event effects are asserted. |
| AG-R07 | Accepted | Actual runtime connection PID is captured inside the bound transaction; pg_blocking_pids checks the exact waiter/blocker pair; final adapter outcomes remain. |
| AG-R08 | Accepted | New *IT uses HTTP-created real PG sessions and the actual service/Redis store; two sessions and tickets have separate issue/redemption limits, concurrent boundary winner counts, TTL and capped counters. |
| AG-R09 | Accepted | Real PEM-loaded signer, independent JJWT signature parsing, report purpose/binding/SID/SV/RV/expiry assertions, production report validator and ordinary-purpose rejection replace mock argument inspection. |
| AG-R10 | Accepted | Both verify call paths reach MessageDigest.isEqual under the focused structural guard, with its data-flow limitation stated. Real Redis/HTTP tests retain reverse ownership rejection and one session/event with both indexes consumed after the direct/by-code race. |
| AG-R11 | Accepted | Real HTTP/PG/Redis flow corrupts only the actor's cleanup set; committed revocation/hash/event, clear cookie/no-store, old bearer/refresh and WS admission denial, surviving unremoved ticket and foreign-user live control are observed. Cheap response mapping units remain. |
| AG-R12 | Accepted | Exact anonymous security denials; authenticated env/beans/heapdump 404 and structured non-disclosing body; health checks root status. Production exposure/security unchanged. |
| AG-R13 | Accepted | HTTP and document shape checks run before update mode; parsed semantic comparison preserves schema/security data and hidden internal routes while removing the stated documentation fields. |
| AG-R14 | Accepted | TestJwtKeys writes owned temporary PEM/kid files and invokes public init; independent public key retained; existing parser/purpose/raw-date cases remain. |
| AG-R15 | Accepted | Fixed synthetic Telegram specification vector anchors the generator-based state cases; real HMAC acceptance and fixed-vector bit-flip rejection retain the reserved-character boundary. |
| AG-R16 | Accepted | Only exact representation prefix and redundant raw substring oracle removed; secret exclusion, redaction marker, parsed JSON and round trips remain. |
| BS-R01 | Accepted | Actual sender type/payload values distinguish FIFO order and elapsed-budget prefix; storage result, retry, rollback/confirm assertions remain. |
| BS-R02 | Accepted | Replacement *IT starts private real Mongo, initializes production index config, reads actual indexes, rejects duplicate own pair and permits foreign owner; group/last_seen/reminder index contracts retained. Global integrationTest routing includes *IT. |
| BS-R03 | Accepted | Signed actual HTTP with real Mongo asserts exact own IDs, distinct unread state/count and decrement, no foreign payload, unchanged DB after foreign denial, and non-disclosing missing-ID 403. |
| BS-R04 | Accepted | Same-server valid Upgrade controls and observed HTTP 401 replace timeout-as-success; bounded repeated positive delivery replaces fixed sleep; socket/STOMP clients close. |
| BS-R05 | Accepted | Renderer output and task cleanup remain; independent flags are order-insensitive while executable and valued argument sequences remain checked. Native renderer counterparts remain untouched. |
| BS-R06 | Accepted | Public service.attendance response uses the client seam; nested history/day IDs, full comment, cancellation distinction and explicit JSON null fields are asserted. Current records/config do not apply NON_NULL to these fields. |
| BS-R07 | Accepted | Facade result assertions added; signed real HTTP plus actual gRPC validates teacher/headman scope, manifest body/ETag, private no-cache and bodyless matching 304; admin rejection remains. |
| BS-R08 | Accepted | Explicit standalone MVC domain-denial seam checks local 403 problem contract; 400 validation/malformed and 405 cases remain. Signed authorization is separately retained in SecurityIdorIT. |
| BS-R09 | Accepted | Production configureMessageBroker is used; actual broker heartbeat values and scheduler wiring checked, initialized scheduler work/daemon state observed and cleanup retained. |
| BS-R10 | Accepted | All three distinct history scenarios remain; replay helper wraps the original production listener, observes its successful owned delivery return and real AUTO-ACK callback, compares full persisted rows/claims and checks empty DLQ. Initial failure rollback, dated recipients, sanitization and read state remain. |
| BS-R11 | Accepted, shared portion only | Getter exemption removed; same-owner delegation followed; runtime helper requires actual CallOptions deadline; ignored-return and helper-return sensitivity cases exist. Static presence rule is explicitly not a timeout proof. Root must still accept domain consumers observing actual calls and asserting nonzero/exact RPC counts. |
| BS-R12 | Accepted | Both submitted malformed marker and canonical invalid token are excluded from actual denial status/exception; credential privacy, UNAUTHENTICATED and no-handler guarantees remain. Production interceptor has no logging path to capture. |
| BS-R13 | Accepted | Exact cutoff equality and before/after/future records distinguish strict-before retention; legacy missing-last_seen backfill survivor remains. |
| BS-R14 | Accepted | Private non-reused JVM-owned containers, fresh PG/Mongo namespace, lazy connection-property startup and started-only owned shutdown. Direct users of this shared fixture were checked; default sequential test execution does not share destructive state across JVMs. Real persistence/broker implementations remain. |

## Evidence limits and deviations

The author reports nine-module test compilation and focused unit/transport passes, including Map HTTP/gRPC, NotificationErrorHandling and Office conversion. I did not execute or read raw private runtime outputs and do not independently certify those execution results. The author's DB/broker suites are explicitly NOT_RUN locally because Docker was unavailable. Their runtime behavior, startup/cleanup, real migrations, Mongo transaction/index behavior, Redis limits and Rabbit ACK observations still need ordinary isolated integration execution; no production resource access is required or authorized by this report.

Accepted implementation choices: AG-R05 moves the inadequate lower-level stale test to the existing actual HTTP suite; AG-R08 uses real service + PG/Redis without claiming an HTTP/private-RPC authorization proof; BS-R02 renames the ineffective structural unit to a real *IT; BS-R06 preserves the current explicit-null JSON contract; BS-R07 corrects an incoherent STUDENT/headman fixture to canonical HEADMAN; BS-R08 uses the audit's permitted local exception-mapping seam. BS-R11 consumer integration remains explicitly outside this writer's completed portion. No other scope deviation identified.

Frozen B013–B015 and historical rejected Mongo/Chromium controls were not changed or executed. Root retains final confirmation of the original accepted audit items and cross-worktree integration.


## К4 — независимое review correction после опубликованных outcomes

Review receipt SHA256: `e788a9901cf1cd0dbc721411a03db4c50cc8d9a9ea00868d23d8e08ef1b8994c`.

APPROVE — независимое source review двух frozen test-only corrections. Подтверждённых defects / blocking findings нет. Это разрешение на перенос именно проверенного patch; runtime PASS и устранение четырёх исторических FAIL не установлены.

Requested model/effort: `gpt-6.1-sol` / `high`. Actual runtime model/effort: `UNKNOWN` — доступные инструменты не предоставили подтверждающих metadata. Reviewer не автор corrections, self-fix не выполнялся, дочерние агенты не создавались.

Прочитаны applicable root/services/tests AGENTS.md, `.agent/orchestration-v2/RULES.md`, CURRENT.md и workflow в assigned worktree. Текущее явное задание на review authorized audit corrections применяется выше старой proposals-only формулировки. Проверка ограничена исправлениями BS-R03 / BS-R04 и непосредственно связанными исходниками; полный PR не переаттестовывается.

Проверенная custody:

- Integration checkout `/workspace/work/pr2-review/repo`: HEAD `a6c64389284b9daa9249f056ab443e8cc4f25dac`.
- Correction worktree `/workspace/work/test-audit-implementation/auth_shared`: HEAD `2eca01a3ef4c65cc2ed1f1ccc6bb7c50eb211615`.
- `notification-audit-correction.incremental.patch`: SHA256 `2ab64c05e6005227ad30507af361cfc97f4106d398689374cb34065f86bedb14`.
- Initial inventory SHA256 `182be5e42206aef0b6243fe42ac97f30ee4900ef22400102a6160b49a5b4ca8d`; results SHA256 `5e462e8f13fffc8f406a76050991fb8f9ff2b36dc5a4d3f0191e4fa706a319d7` — совпадают с manifest.
- Independently reconstructed patch from old integration bytes equals current and frozen bytes exactly. Initial inventory содержит 45 путей; изменились только два ниже, остальные 43 сохранили исходный SHA256 либо исходное отсутствие удалённого файла. `git status --porcelain=v1 --untracked-files=all` не содержит новых путей относительно initial inventory.

| Путь относительно worktree | Old SHA256, integration + initial inventory | New SHA256, current + frozen |
| --- | --- | --- |
| `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/security/SecurityIdorIT.java` | `8ea3aba45789f09c17ce793d8df9a3de795979c54a18eb54a4f1db291430fca6` | `083873755593e66ab1806dec13492bf81430d1939fbf457c70fbb929a0e995d8` |
| `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/ws/StompIntegrationIT.java` | `e2ce46154c2ea0cdffb1bbc1911279a2c0513933bfb38d9945cb8b2546c45166` | `511fd9deb8925369f81ea382c767e5b860fdcfa64a0c9ccf271a64746b13fee7` |

В [SecurityIdorIT.java](/workspace/work/test-audit-implementation/auth_shared/services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/security/SecurityIdorIT.java:86) fixture сохраняет три own и пять foreign записей. Все получают явный `STUDENT` RoleView, `orderAt=sentAt`, `revision=0`; у одной own записи сохраняется исходный root `readAt` и такое же значение projection `readAt`, у остальных оба состояния unread. Это соответствует shape `NotificationRoleHistoryWriter.java:36,71` и `NotificationInboxService.java:261`, а не меняет политику доступа.

Исходный дефект fixture подтверждён source: production `NotificationHistoryService.java:51,64,75` делегирует legacy endpoints в inbox; `NotificationInboxService.java:235` требует одновременно user_id и role projection. `adoptLegacy` (:249) обрабатывает только собственные строки без role_views; для EXCUSE_REQUESTED `legacyRole` (:282) требует совпадающий payload user_id либо отдельный доказуемый HEADMAN shape. Строки только с owner_marker не получают STUDENT view. Исходная own строка с payload user_id получает projection и законно меняет collection при adoption до проверки чужого id в `legacyRead` (:184). Добавление canonical fixture устраняет это смешение migration и IDOR-проверки. Review не установил скрываемого этим изменения product defect; произвольное изменение legacy adoption в продукте не предлагается.

Всё от `asUserA` (:158) до конца SecurityIdorIT byte-identical old/new: signed Internal JWT и disabled legacy headers сохранены; list (:170) требует status200, totalElements3, точное множество трёх own IDs и отсутствие foreign marker/id; forbidden read (:185) требует 403, foreign root readAt=null и recursive equality **всей collection** до/после; nonexistent id (:199) остаётся 403; unread (:210) остаётся 2 → own PATCH204 → 1, с foreign readAt=null. Изменение любой foreign projection, собственной истории, числа документов или полей после forbidden write по-прежнему нарушит whole-collection comparison. Fixture не отключает эти gates. Отдельный existing legacy-adoption case в `NotificationStateV2IT.java:155` также сохранён; его runtime этим review не подтверждается.

В [StompIntegrationIT.java](/workspace/work/test-audit-implementation/auth_shared/services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/ws/StompIntegrationIT.java:173) expected берётся из неизменяемого полного `wsMessage`, сериализованного и прочитанного через ObjectMapper того же `MappingJackson2MessageConverter`, который установлен клиенту (:114). Полученный actual не участвует в построении expected. Нет subset/contains/string-coercion сравнения: Map equality на :192 сохраняет оба верхних поля, оба payload поля, значения, вложенную структуру и отсутствие лишних полей. В частности, group_id43, другой event type, строка вместо числа, удалённое/добавленное поле остаются различимыми. Для данного сообщения единственная необходимая нормализация — JSON integer42 вместо Java Long42; wire значение не изменяется.

Existing bounded readiness loop и deadline5s (:181) сохранены. Negative handshake, valid same-server upgrade control, timeout-as-failure и observed HTTP401 (:199–275) byte-identical old/new; disconnect/finally и closeClient сохранены. Inspected ticket admission, subscription authorization, group equality, broker configuration и signed REST context product sources byte-identical между integration и correction worktrees. Продукт, shared helpers, dependencies и CI gates не входят в incremental delta.

Прочитаны author pure-control source и закрытый PASS-log в `/workspace/work/test-audit-implementation/notification-pure-controls/`: actual Spring converter control показывает исходное numeric Map inequality, canonical full-message equality и negatives wrong group/type/missing field. Reviewer эти Java controls не запускал. Рассуждение о сохранении дополнительных wire negatives основано на полном Map equality и конкретном scalar-only fixture, не объявляется отдельным выполненным sensitivity test.

Историческая evidence принята только из закрытого `/workspace/work/test-audit-implementation/ci-primary-uploaded/notification-projection.safe.json`: run `39505618901`, attempt1, PR head `a6c64389284b9daa9249f056ab443e8cc4f25dac`, checkout `06abc14fd0d67e3de0a7ee8b932e8deec5b05324`, 60PASS/4FAIL/0ERROR/0SKIPPED. Три SecurityIdor failures имеют SOURCE_METHOD identity для `markAsRead_otherUsersNotification_returns403`, `list_returnsOnlyOwnNotifications`, `unreadCount_returnsOnlyOwnUnreadCount`, closed code ASSERTION; точная упавшая assertion stage/status/value UNKNOWN. Четвёртый failure имеет только StompIntegrationIT class + ORDINAL_ONLY, method=null, ASSERTION; failed method и stage UNKNOWN, happy-path failure из этого не выводится. Сохранённые uploaded source hashes совпадают с независимо проверенными old bytes. Сам uploaded checkout git object локально недоступен; независимый `git show` его bytes поэтому не подтверждён. Это ограничение не подменено source-гипотезой.

Review выполнил только чтение source/safe evidence, Git read-only status/HEAD и Python byte/hash/patch comparisons; tracked files не редактировались. Gradle, tests, DB, browser, native Mongo/Chromium, CI/rerun, commit/push и внешние сообщения не запускались. Frozen B013–B015 и rejected equivalents не изменялись/не воспроизводились. Не запрашивались raw private logs/XML/HTML, PII, секреты либо arbitrary diagnostic payloads; incremental patch не добавляет их публикацию или ослабление закрытых evidence collectors. Единственный созданный файл — этот review вне tracked tree.

Ограничение результата: corrections source-ready; compilation и фактический HTTP/Mongo/WebSocket результат остаются NOT_RUN/UNKNOWN. APPROVE не утверждает, что исторические failures устранены, и не заменяет последующую authorized runtime acceptance root.

Review receipt SHA256: `f88b750fe407504c30597ed356d23e8547f04bcf2e28ffd832044271d73db072`.

# Независимое review Auth corrections — 2026-10-20

Вердикт: **APPROVE (SOURCE ONLY)**. Blocking findings не найдены. Это не runtime PASS и не подтверждение исправления двух исторических CI failures.

Scope: только incremental correction двух тестов в `/workspace/work/test-audit-implementation/auth_shared`; текущая база интеграции `/workspace/work/pr2-review/repo` — `5c827c7a20be915654aed56c93c4c8b8790feef3` (Auth unchanged от PR head `a6c64389284b9daa9249f056ab443e8cc4f25dac`). Read-only production review; единственный созданный мной файл — этот отчёт вне tracked tree. Назначение Sol6.1/high/forknone — из root packet; actual runtime model/effort **UNKNOWN**, независимо не подтверждены. Дочерних агентов нет.

Прочитаны root/services AGENTS, RULES, CURRENT, workflow, принятые AG-R05/AG-R10 и актуальная implementation matrix. Текущий owner GO из прямого packet главного применяется к принятым DELETE94/REWRITE169; старые proposals не расширяют scope.

## Проверка поведения

- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/OtpIT.java:204`: ошибочный oracle `auth_outbox +1` заменён проверкой отсутствия outbox-записи и ровно одного реального `OtpVerifiedEvent` (`:210–218`, `:257`). Production `OtpService.java:259–275` создаёт session и вызывает настоящий `ApplicationEventPublisher.publishEvent`; `event/DomainEventListener.java:39` синхронно обрабатывает событие и отправляет через настоящий RabbitTemplate. Это существующий Spring/Rabbit путь, не durable outbox. Общий DomainEvent действительно наследует Spring ApplicationEvent. В AbstractIntegrationTest нет подмены publisher; конфигурация асинхронного multicaster в проверенных auth/shared/build источниках не найдена.
- Listener установлен до отправки обоих HTTP запросов, ограничен классом события и synthetic Telegram UID, AtomicInteger виден серверным потокам. Оба HTTP Future завершаются до проверки счётчика; синхронная публикация завершается до успешного HTTP ответа. `finally` (`:260–263`) удаляет именно этот listener и завершает executor. Посторонние типы/UID не увеличивают счётчик. Сохраняются два реальных endpoint, ровно один 200 и один 401 (`:243–249`), прирост PG session ровно на один (`:250`), потребление обоих Redis индексов (`:258–259`). Метод, race и строгий event oracle не удалены; publisher mock не добавлен.
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/SessionAuthFlowIT.java:206` и `:238`: исправлены только две stale admission status expectations 401 → 409; точный `SESSION_STATE_STALE` сохранён. Production `exception/GlobalExceptionHandler.java:144–170` явно сопоставляет SessionAdmissionException stale с 409 и no-store; `session/SessionAdmissionService.java:133–135` проверяет RV/SV. Существующий `InternalSessionAdmissionIT.java:547` ожидает тот же 409. Это correction тестового ожидания, не сокрытие production bug. RV-only/SV-only fixtures, актуальные 200 controls, неизменная identity при SV round-trip, no internalToken и no-store в assertAdmission (`:241–261`) сохранены. Остальные 401 контракты incremental patch не изменяет. Матрица относит этот сценарий к **AG-R05**, не AG-R03.

## Provenance и неизменность

SHA-256 incremental patch `auth-audit-correction.incremental.patch`: `74669ead9eadae770eb259d908c19445ad228e71ba40e51267131bc7062b0f47`. Patch побайтно реконструирован из before snapshot и writer файлов. Два целевых файла совпадают с frozen final snapshot и manifest:

| Файл | До / текущая база | После |
|---|---|---|
| OtpIT.java | `1f613fca92ec96ea42f5aa39d1146f69efba20a8cc5963b8ae639c16d4beacfd` | `8a80d2ddaf1dccb9890ecea5bf1f80d9655ed39d910271313525f2468b96605c` |
| SessionAuthFlowIT.java | `a96227fb55d78100383b502c3dc6de7bcca35b32569f5d617e680e966b7b3dfd` | `e6fc3228a46ac48365258ea66fc53577b7bc18cc8b8d8a955fcf014842a523b9` |

Проверен весь before inventory из45 путей: изменены ровно два разрешённых Auth файла; остальные43 состояния/хеша неизменны, включая frozen Notification corrections. Проверенные production файлы OTP/admission/event и surviving AuthOtpFlowIT побайтно совпадают writer/current. B013–15, rejected Mongo/Chromium equivalence и status/identity collector вне patch; расширения источников/DI/CI нет.

## Ограничения

Safe projection `/workspace/work/test-audit-implementation/ci-primary-uploaded/auth-projection.safe.json`: run39505618901 attempt1, artifact12304252989, zip SHA `719f6a8609d12963c4db033b95d0856625885450fbccd5a17d5cef4d4ac4c456`, 208 PASSED/2 FAILED; оба failures идентифицированы как SOURCE_METHOD с above before hashes. Точная упавшая assertion/stage **UNKNOWN**. Исправления основаны на текущем production contract; safe projection сам по себе не доказывает причину исторического падения.

Runtime/Gradle/tests/DB/browser/CI мной **NOT RUN**; raw private logs/XML не читались. Writer compile1 safe receipt показывает OFFLINE_CACHE_MISSING/DEPENDENCY_RESOLUTION до target compileTestJava; это не результат компиляции исходников. Следующая writer verification ещё не оценена в этом review. Runtime двух исправленных методов остаётся NOT RUN, historical failure resolution UNKNOWN.

Listener доказывает публикацию ровно одного Spring OtpVerifiedEvent при гонке; он не доказывает доставку otp.verified в Rabbit/consumer. Сохранившийся `AuthOtpFlowIT.java:182–210` подтверждает Rabbit delivery **otp.requested**; выдавать его за delivery otp.verified нельзя. Production listener подавляет ошибки Rabbit по существующему контракту; тест эту политику не меняет. Source approval допустим с этим явно указанным пределом; runtime acceptance должна оставаться отдельной.
