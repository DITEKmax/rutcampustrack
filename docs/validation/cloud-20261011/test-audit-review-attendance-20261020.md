# Attendance independent implementation review

Current disposition after narrow correction recheck: **APPROVE (source review)**. Both initial P2 findings below are resolved by correction manifest `dad1c661761ad2b27208c9597da6f2454a75d3564b753995fc068cd8e7343500`; compile verification and actual Mongo runtime remain pending. The initial review and its evidence are retained below as history. This reviews only the assigned Attendance inventory, not all of PR 2.

Initial disposition: **REQUEST_CHANGES**. Two substantive gaps remained in AO-R08 and AO-R10.

Reviewer requested configuration: gpt-6.1-sol / high; actual runtime model/effort metadata: UNKNOWN. Independent from the writer; no children. Read-only inspection of source, diffs, contracts, survivor tests, production boundaries, and structured existing run receipts. No source edits, tests, builds, DB/broker/browser operations, CI actions, or external messages were performed. This report is the only written artifact.

Baseline: `2eca01a3ef4c65cc2ed1f1ccc6bb7c50eb211615`. Worktree: `/workspace/work/test-audit-implementation/attendance`. Manifest SHA-256 before and after review: `050d84def84852b739ffa2c80ec92c99f8da81949b403670bf1d14304b52c7a1`; all 39 owned path hashes match the manifest, including the three absent deleted files. The Auth-owned GrpcDeadlineArchRules compilation mirror is excluded from Attendance ownership; the canonical root helper was read. All changes are tests or test fixtures; no production/API/config/dependency change is present in the owned inventory.

## Findings

1. **P2 — AO-R10 still accepts a stale geo overwrite after both commands settle.** `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/student/StudentCheckinTransactionIT.java:1266` (also the revised helper at line 1600).

   Trigger/counterexample: the manual ABSENT command commits and the geo writer subsequently leaves PRESENT/STUDENT_GEO. The final `else` explicitly accepts this state. With the revised helper, a labelled Mongo conflict loser is retried after both original transactions settle, and the only permitted domain rejection is geo's MANUAL_ABSENCE_REQUIRES_APPEAL. Therefore the manual command must succeed. Both valid serial orders end ABSENT/HEADMAN/777: geo then manual overwrites with absence; manual then geo is rejected by StudentCheckinService's manual-absence checks before and after pair locking (production lines 321 and 345). PRESENT is precisely the stale overwrite the test is intended to prevent, rather than a valid alternate outcome.

   Impact: the core race invariant remains unproved even though broad infrastructure-error masking has been fixed. A regression bypassing the manual-absence guard can remain green.

   Minimum correction: require final ABSENT/HEADMAN/777 once both commands have settled. Capture the geo result or exact expected domain refusal and verify replay of the same command/key preserves that final attendance and any existing immutable receipt. Retain the narrow labelled112 filter, bounded future waits, finite retries, and executor termination. The accepted contract's receipt/history/outbox replay invariants are also currently absent from these two callers; publishers are MockitoBean in this fixture, so do not describe the current checks as actual durable outbox/history proof.

2. **P2 — AO-R08 does not match each durable batch intent to its committed mark.** `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/MarkingIT.java:432`.

   Trigger/counterexample: batch processing persists correct rows but publishes status `present` for the ABSENT user 100 (or a wrong lesson/source). The new intent assertion checks only three rows and user IDs 99/100/101. The separate single-mark event test exercises only PRESENT, so it does not reject this counterexample. Replay checks only the intent count, allowing replacement or mutation of the three envelopes.

   Impact: the accepted guarantee of corresponding durable event intents is weaker than the domain oracle and can permit notifications/consumers to observe a different status from committed attendance.

   Minimum correction: parse and schema-check each batch envelope; compare its lesson/user/status/source and canonical scope to the expected committed row, including `42/100/absent/headman`. Snapshot the durable intents before replay and compare stable event identity and payload after replay, in addition to the existing stable attendance IDs/count. Keep the actual storage failure and rollback scenario.

These are static counterexamples grounded in the inspected code, not executed mutation results or reported production failures.

## Per-ID coverage and judgment

All paths below are under `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/` unless stated otherwise. “Accept source” does not assert unrun integration success.

| ID | Inspected implementation/survivor and judgment |
| --- | --- |
| AO-D01 | LessonGenerationMergeTest deleted; original only echoed its mock. Accept source. |
| AO-D03 | RabbitConsumerIT removes only publish-does-not-throw; queue declarations and EventConsumerIT actual dated ABSENT delivery survive. Accept source. |
| AO-D04 | MarkingServiceTest removes only repeated upsert mock test; MarkingIT repeated HTTP mark plus immutable-field survivor retain persistence identity/status. Accept source. |
| AO-D05 | LessonEventServiceTest removes only bulk-call happy path; real EventConsumerIT close/backfill and empty/missing-semester/dependency/cancellation guards survive. Accept source. |
| AO-D06 | LessonEventServiceParallelTest deleted; independent dated close and late-membership/backfill survivors remain, rather than circular reliance on AO-D05. Accept source. |
| AR-D01 | Self-owned template replacement/escaping sample removed; actual renderer coverage and AR-R11 replace the inventory together. Accept source. |
| AR-D02 | Only MVC-only PDF echo removed; HeadmanWeeklyReportControllerIT retains HTTP/Mongo/actual DOCX with explicit converter seam and MIME/filename/bytes. Accept source. |
| AR-D03 | Only standalone alias equality removed; full publisher and real contract survivor independently assert both user_id and student_id equal 100. Accept source. |
| AR-D04 | Only two pending-list units removed; actual controller/Mongo own-group/non-headman scenarios and narrow null-group/decision/cutoff guards survive. Accept source. |
| AO-R01 | Both filter ITs and AttendanceIdentityRecordFixture use socket HTTP, signed/legacy identity, actual Mongo records, foreign sentinel, exact 200/body, and raw no-write comparison; 401 cases remain. Accept source. |
| AO-R02 | Public handler now receives supplied coordinates/unavailable commands and rejects read-only claims with OUT_OF_SCOPE before dependencies. Accept source. |
| AO-R03 | Exact envelope replay, one unchanged APPLIED receipt, real effect-ACK per delivery, foreign-row preservation, DLQ absence and terminal close receipt. Accept source. |
| AO-R06 | Signed socket mark, actual persisted PRESENT/HEADMAN and one matching schema-valid durable attendance.marked envelope. Broker delivery is not claimed. Accept source. |
| AO-R08 | Actual signed batch HTTP/transaction/Mongo/outbox, stable row IDs and real post-second-insert rollback. Matching per-item event status/scope and immutable intent replay missing; finding 2. |
| AO-R10 | Existing Mongo races retained; only causal Mongo112 plus TransientTransactionError is tolerated, futures/retries/shutdown bounded, conflict commands retried after settlement. Invalid final PRESENT branch and replay invariants remain; finding 1. |
| AO-R04 | Real Mongo dedup claims and actual cache-refresh dispatch count; later same-worker/prefetch-one barrier replaces time delay. Production listener factory confirms one consumer and prefetch one. Accept source. |
| AO-R05 | Authenticated public projection handler plus protobuf round trip preserves full OTHER comments, day/history optional presence, cancellation reason and authority-outage survivor. Accept source. |
| AO-R07 | Actual repository exact IDs/group/status/order, production index initializer, unhinted winning index/no COLLSCAN/no blocking SORT and bounded examined rows replace host-speed threshold. Accept source. |
| AO-R09 | Actual proxied ExcuseService with request-scoped identity, actual outbox insertion then failure, rollback and same-input recovery. Service boundary deviation accepted because both production HTTP create routes unconditionally throw legacy retirement 410. No endpoint resurrection. Accept source at this expressly limited boundary. |
| AO-R11 | Signed socket records read checks exact lesson/subject/date/status/source/symbol with foreign student and historical-semester sentinels; raw attendance/outbox unchanged. Accept source. |
| AO-R12 | Actual production Mongo conversions and BSON value comparisons preserve original snapshots/decision selection through subtraction and round trip; effective/history cases remain. Accept source. |
| AO-R13 | EMPTY/SEEDED socket 410 parameterization preserves raw attendance and receipt/pair/Redis/outbox no-effects; old duplicate carrier removed together. Accept source. |
| AR-R01 | Unit forbids every writer interaction; new real service/write-port/Mongo test preserves attendance on rejection, one decided intent, and raw all-store identity on stale REJECTED/APPROVED deliveries. Accept source. |
| AR-R02 | StudentRequestDomainIT imports actual MongoConfig and invokes initIndexes after owned DB drop; copied index list and test transaction-manager bean removed; existing business scenarios remain. Accept source. |
| AR-R03 | Fake Query parsing removed only with real retention test moved into StudentRequestDomainIT; independent real clock vs two teaching clocks, expired/retained Mongo rows, byte clearing and untouched retained BSON; cheap downloads survive. Accept source. |
| AR-R04 | Authenticated public handler/protobuf response checks nullable own rank and absence of peer markers; explicit cohort ranking response carries sentinel identity; algorithm units survive. Accept only mapper/serialization boundary, not full data-selection privacy acceptance. |
| AR-R05 | Only raw width literals removed; actual DOCX valid XML/marks/names/week/table/section behavior survives. Accept source. |
| AR-R06 | Actual Word/HTML/worksheet logical rows, 120 names, supplied 26.3 rather than recalculated 25, numeric types, no-data and formula-like text/no formulas. Accept source. |
| AR-R07 | PNG decode/signature/MIME/20MiB retained; actual HTML checks all 22 period labels and both metric series. Accept source; visual PNG completeness remains UNKNOWN. |
| AR-R08 | Real HTML/XLSX logical student/date mark and literal escaping replace CSS/width/color assertions. Accept source. |
| AR-R09 | Teacher export batching checks exact requested union and <=100 size, dated marks/future blank, named typed metrics, summary scopes/order/no-data and format/scope-hash survivors. Converter seam explicit. Accept source. |
| AR-R10 | Namespace-aware logical date/type/lesson headers connect all 36x30 cells across arbitrary segments, exact supplied summaries, week order, no placeholders/private identifiers and invalid-kind guards. Accept source. |
| AR-R11 | Actual legacy renderer/template proves all six dates and 30 unique type sentinels as visible semantic cells plus escaping/valid XML; no placeholder-inventory copy. Accept source. |

BS-R11 cross-scope AttendanceClientDeadlineTest also inspected: actual Channel.newCall sees five calls across Academic, Schedule, DocumentRenderer, TeacherAcademic and AdminPermission clients, including getActiveSemester and delegated conversion; every call asserts a non-null actual CallOptions deadline, with an explicit total count of five. Canonical shared helper remains a structural guard plus runtime assertion, not a standalone deadline proof. Accept source.

## Evidence limits

Existing writer receipts record successful compileTestJava, initial 19 focused unit suites with 117 tests/two new fixture assertion failures, successful correction run 2 for the two affected suites, and successful run 3 for other changed helper consumers. I inspected the structured receipt command/result/hash metadata, not private raw logs. Initial receipt belongs to the earlier manifest; successful later receipts match the reviewed manifest. No further runtime was run by this reviewer. Mongo/Rabbit/Redis ITs remain NOT_RUN locally/CI_PENDING because Docker access was unavailable; their compilation is not runtime acceptance. B013–B015/rejected Mongo/Chromium equivalents were not opened or executed. No raw private payloads, tokens, or PII are included here.

## Narrow correction recheck — AO-R08 and AO-R10

Scope: actual delta of the two correction files against integrated root `ac0f0517`, plus the directly affected production services, audit writers, transaction setup, and replay helper. The other 31 IDs were not re-reviewed. Actual runtime model metadata remains UNKNOWN. No tests, builds, containers, DB, browser, CI actions, or source edits were performed during recheck.

Correction manifest SHA-256 before and after recheck: `dad1c661761ad2b27208c9597da6f2454a75d3564b753995fc068cd8e7343500`. Exact reviewed source hashes:

- StudentCheckinTransactionIT.java: `5732f0df9a7504c8ccfacda10b198aca3592c9833e6aec72b552e1bb1171b927`.
- MarkingIT.java: `8b11e847b0e59ac6f0e3a924cec8bb5e2724b4ccf254778ca73981b56fdf411d`.

**Finding 1 resolved.** The manual/geo race now requires final ABSENT/HEADMAN/777 after both commands settle and again after receipt replay. It captures either the successful geo ACK with its exact stored receipt or the specific MANUAL_ABSENCE_REQUIRES_APPEAL refusal and no receipt. Existing successful receipt replay must return the original ACK while raw attendance/pair/receipt/request/history/outbox rows remain identical. The approval/geo case also checks its pending receipt, optional successful geo receipt, stale approval, provenance, and unchanged raw snapshot on replay. The no-receipt branches inspect absence of a receipt rather than claiming a newly rejected command produces no rejection audit.

The two corrected race cases construct the actual AttendanceEventPublisher and LateCheckinEventPublisher with MongoOutboxStorage, attach actual LessonActionService/RequestActionRecorder, use the existing actual AttendanceWritePortImpl and attachment service, and initialize the actual decision/marking services through Spring's existing advisors. Both services must be AOP proxies; actual audit appends independently require an active transaction. StudentCheckinService retains its actual TransactionTemplate boundary. Thus durable history/outbox snapshots in these cases are no longer a claim based only on Mockito publishers. Fixture cleanup adds only the owned lesson_actions collection. The causal Mongo112 plus TransientTransactionError predicate, maximum three retry attempts, 20-second future waits, and five-second executor-termination bound remain intact; unrelated exceptions fail. No frozen historical experiment was changed.

**Finding 2 resolved.** Every durable batch envelope is schema-validated, carries a parseable event UUID, and is matched to its persisted user with exact lesson/status/headman source and canonical group/subject/date/lesson-number assertions. The exact user set includes the ABSENT user 100. After batch replay, full parsed envelopes must remain identical, preserving event identity and payload in addition to stable domain IDs. The existing failure after the second actual event insert and rollback/recovery scenario remains intact.

No new substantive findings in this narrow recheck. **APPROVE for the corrected source.** The correction manifest reports scoped syntax/formatter checks as PASS and compile as queued; I did not run or independently assert those checks. Actual Mongo integration remains NOT_RUN locally and requires ordinary CI verification. This approval does not convert compilation or source inspection into runtime transaction/race acceptance.


## К4 — независимое review correction после опубликованных outcomes

Review receipt SHA256: `5343ed7ff08ec3f7193d4dae350b7c6c41604be7ffcac2e385c31e4f94920532`.

# Attendance audit correction — независимое review

Вердикт: **APPROVE** для точного TEST-only incremental patch. Blocking findings не обнаружены. Это source review, а не runtime acceptance и не утверждение об устранении пяти CI failures.

Scope: одна строка `MarkingIT.java:364`, `"STUDENT"` → `"HEADMAN"` в `signedHeadman()`. Риск: корректность auth fixture; production authorization и транзакционные гарантии не изменены. Прочитаны применимые `AGENTS.md`, `services/AGENTS.md`, `.agent/orchestration-v2/RULES.md`, `CURRENT.md`, `docs/agent-workflow.md`. Текущий явный root packet/делегированный owner GO задаёт границы; исторические proposal/STOP не расширяют и не отменяют их. Запрошенные model/effort: Sol6.1/high; фактическое runtime metadata: UNKNOWN. Детей не создавал.

## Механизм и контракт

Пути ниже относительно `/workspace/work/test-audit-implementation/attendance/`.

- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/MarkingIT.java:354`: helper передаёт роль и capability в `InternalJwtTestFactory.validToken`; `:364` теперь HEADMAN, `:367` остаётся true, `:366` положительная группа, `:365` ACTIVE, `:368` readOnly=false. Factory (`services/shared/shared-security/src/testFixtures/java/ru/rutcampustrack/shared/security/InternalJwtTestFactory.java:98`) подписывает role как передан, без исправления семантики.
- `services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java:128` вызывает `requireSemanticIdentity`; `:206` требует `isHeadman == role.equals("HEADMAN")`. Поэтому исходная STUDENT/true fixture достоверно нарушала контракт; HEADMAN/true с данной положительной группой удовлетворяет этому правилу. Другие admission/signature/wire/time проверки остаются действующими.
- `services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/DualModeUserContextFilter.java:66` валидирует токен до controller; `:70`–`:73` возвращает 401 при `InternalJwtException`. Это доказанный механизм исходного дефекта, но не установленный этап падения конкретного CI testcase.
- `services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtClaims.java:28` отображает валидный HEADMAN в service-domain STUDENT, сохраняя raw role и отдельный headman capability. `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/security/AttendanceUserContextFilter.java:33` переносит domain role, группу и capability в RequestContext.
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingController.java:41` и `:97` сохраняют `@RequireRole(STUDENT)`; `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/security/RoleCheckAspect.java:30` сохраняет отказ для неподходящей роли. `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java:507` сохраняет совпадение группы, `:510`–`:513` — серверное подтверждение старосты через academic client. Исправление не обходит эти fences.
- Сохранившийся аналог: `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/ReportIT.java:241`–`:250` использует HEADMAN/ACTIVE/positive group/true/false для настоящего socket запроса. Safe uploaded case `getLessonAttendance_unknownStoredStatusRefusesWithoutChangingKnownRows` имеет SOURCE_METHOD/PASSED с SHA `986ad8a69ea2b6531baeffda16d7a1ca858692feaf9f14e27efb7f1f286491b5`, совпадающим с исходником. `InternalJwtValidatorTest.java:151`–`:160` уже содержит явное принятие HEADMAN и mapping в STUDENT, `:377` — отказ STUDENT/true; эти тесты не запускались заново.

## Независимая проверка bytes/custody

Read-only Python/hash/git checks, без Gradle и запуска тестов:

- Root фактически HEAD `5c827c7a20be915654aed56c93c4c8b8790feef3`; `git diff --name-only a6c64389284b9daa9249f056ab443e8cc4f25dac HEAD -- services/attendance-service services/auth-service services/shared/shared-security` пуст. Attendance/Auth в этих границах совпадают с опубликованной базой.
- Writer HEAD `2eca01a3ef4c65cc2ed1f1ccc6bb7c50eb211615`. Все 40 pathname/status из before manifest совпали с текущим porcelain: 39 Attendance + 1 foreign WIP. Frozen copies совпали со всеми before hashes, включая отсутствующие/deleted paths. Единственный изменившийся hash после снимка — MarkingIT.
- Все 39 Attendance before entries совпали с bytes опубликованного `a6c6438`. Полный published attendance tree против writer содержит ровно один изменённый путь MarkingIT и не содержит дополнительных существующих путей. Замена единственного точного literal фрагмента frozen MarkingIT воспроизводит текущий файл байт-в-байт.
- MarkingIT before SHA256 `8b11e847b0e59ac6f0e3a924cec8bb5e2724b4ccf254778ca73981b56fdf411d`; after SHA256 `6ada994f9d47f1fce36bb366a0c3b36868fd738f09c0d2bf215416d7de6764e4`.
- Все шесть production mechanism bindings из `attendance-audit-correction-source-bindings.json` независимо совпали между writer, manifest и published root. Foreign `GrpcDeadlineArchRules.java` SHA256 `63abe080a5ee9dedb2f3b3e44a3a5c2347a82c7dc504a1bdb70b0b4a7ded8b45` сохранён.
- Вне correction существует baseline difference writer против published root в `services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/grpc/ServiceIdentityServerInterceptorTest.java`; он не входит в correction inventory, не изменялся мной и не включается в одобрение/перенос данной правки. О нём root уведомлён.
- Все status/authz/CAS и exact-envelope assertions, включая требуемые 100 envelopes в принятом Attendance audit inventory, сохранены по byte comparison всего Attendance tree. В MarkingIT отдельно прочитаны HTTP200 (`:316`, `:425`, `:473`), exact three pairs (`:430`), durable/schema-valid envelope identity (`:432`–`:464`), stable document/event replay (`:474`–`:478`), вызов реального outbox save до второго insert fault (`:487`–`:491`), 5xx и ровно два insert (`:504`–`:505`), нулевые side effects в четырёх коллекциях (`:506`–`:514`), повтор той же команды и восстановление (`:518`–`:528`). Их bytes не менялись. Legacy MockMvc STUDENT headers также сохранены.
- Оба safe input JSON совпали с before hashes. `git diff --check` writer завершился exit0.

## Пределы доказательства

Safe uploaded projection: run `39505618901`, attempt `1`, PR head `a6c64389284b9daa9249f056ab443e8cc4f25dac`, checkout `06abc14fd0d67e3de0a7ee8b932e8deec5b05324`; 252 PASSED, 5 FAILED, 1 SKIPPED, acceptance NOT_ASSESSED. Я сверил только supplied safe JSON и исходники; provenance uploaded artifact/digest принят по root custody packet, новый remote download не выполнялся.

Три failures имеют SOURCE_METHOD identity на MarkingIT и совпадающий before SHA: `mark_successfulMark_publishesAttendanceMarkedEvent`, `batchHttpPersistsExactPairsStableIdsAndDurableIntents`, `batchHttpFailureAfterRealOutboxInsertRollsBackAndSameCommandRecovers`. Фактический failed stage каждого — **UNKNOWN**. Два остальных failures остаются classname/method/source=null, identity=ORDINAL_ONLY; их нельзя идентифицировать, переименовать или приписать данному дефекту по косвенным признакам. Коррекция source-defect не доказывает, что разрешены все пять failures, и не доказывает последующий успех трёх MarkingIT.

Compile/tests/runtime: **NOT_RUN** в рамках явного задания. Для runtime acceptance нужны разрешённые root hosted outcomes на точной corrected revision/run/attempt; при необходимости доказать первоначальную причину нужны доверенные закрытые stage/assertion facts, без raw logs/bodies/messages.

B013–B015, отклонённые Mongo PUT500/shared-semester experiments и Chromium equivalents остаются FROZEN. Не запускались DB/Mongo/browser/CI/Gradle/тесты, не читались raw project XML/private logs/PII/credentials, не менялся product/test source, не выполнялись commits/push. Cloud runtime skill потребовал environment status; это только metadata access, без запуска окружения, ручных диагностических probes или использования возвращённых сетевых diagnostics для выводов review.

Создан только этот отчёт вне tracked tree: `/workspace/work/test-audit-implementation/review-attendance-correction.md`. Self-fix и перенос не выполнялись. Root лично проверяет и интегрирует только точный incremental patch.
