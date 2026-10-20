# Independent Academic source review — 2026-10-20

Reviewer: fresh independent acceptedtestauditPR2 assignment; requested Sol6.1/high; actual runtime model/effort metadata UNKNOWN. Read-only review; no children, product/source edits, build/test/runtime/DB/browser/CI/network actions. This report is the only reviewer-written artifact.

Baseline `2eca01a3ef4c65cc2ed1f1ccc6bb7c50eb211615`; accepted source audit `47daac1bb3d48eb2a912689a66d95b938798c284`; scope AD+AA+AI 58 IDs (21 DELETE /37 REWRITE), exact65 authored paths (59 present/6 deleted). Initial manifest `c77bb6a17fa978bc32b9433b118e7e9719973ecc6cc406cfe849eb49ffaa1139`; after two authorized author self-corrections `cf3a912dacf7d92b91492b60ec76f670e3d520b8490e61b1a70cfcf48ec0ca3b` canonical sorted compact JSON SHA verified against all65 filesystem states/bytes. Shared GrpcDeadlineArchRules mirror excluded as non-authored.

## Final verdict: APPROVE — source review, runtime acceptance pending

**P2 — AA-R03 — `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:305`.** `jdbc` now uses `runtimeData` (lines68–85; fixture properties use `rct_academic_runtime`) on a schema owned by migrator. Grants55–67 provide schema USAGE, table DML, sequences and function EXECUTE, but neither schema CREATE nor table TRIGGER/ownership. The existing fault injection at305–311 creates a function and trigger through that runtime principal; cleanup325–326 also drops owner objects. PostgreSQL rejects this fixture DDL before the intended artifact/audit/metric rollback scenario. Use the existing fixture owner solely for qualified DDL in this exact UUID schema, including cleanup; preserve runtime JDBC for repository operations and do not widen runtime grants. Root independently confirmed the source finding and assigned the sole writer the correction. **Resolved on recheck:** final file SHA `30afcc02d68553512cd322724485e3bacf8d5d5df16c85610070946b5c3e1b63`; lines305–339 enforce the owned export UUID schema pattern, use fixture-owner JDBC for fully qualified function/trigger CREATE/DROP, and nested finally/IF EXISTS cleanup. Repository operations still use runtime JDBC and runtime grants remain unchanged. No PostgreSQL execution was performed by this reviewer. No unresolved new source findings remain.

The earlier authorized corrections were reviewed: RestApiIT orders effect receipts by actual `source_event_id`; writer apply compares receipt revision/Auth version against actual persisted SQL values. No objection.

## Accepted deviations and verification limits

AD-R05 retains the correct failing negative: SQL CHECK accepts UNKNOWN for LESSON+NULL in the retained V45 constraint. This is an explicitly admitted production-source failure, not grounds to weaken/skip the new test or change production within this audit. It is source-predicted, not runtime-reproduced here.

AA-R03 cannot seed two simultaneously active semesters under the actual schema. Its actual integrity rejection plus narrow controlled count=2 fallback test is defensible and disclosed; the remaining16 scenarios remain DB component tests. Guarded PREPARED→APPLIED CLOSE_UNASSIGN replaces the invalid REVOKED fixture without disabling constraints. This preserves real schema authority.

Controlled ports retained only at accepted component seams: prepared Auth protobuf/local writer, command capture/controlled Auth for real JDBC profile projection, assistant authority branch, Schedule remote seam; actual HTTP is used where the accepted replacement requires it. Auth-created identities and actual owner/runtime separation are used for endpoint fixtures. Cleanup is scoped to owned disposable resources.

Author reports cf3a… compileTestJava PASS and18 suites/104 tests/0failures/0errors/0skips in `/tmp/academic-final-cheap-20261020-f.log`, including actual Channel4RPC; earlier261PASS was a pre-final subset. Reviewer did not run or independently attest these commands. The one-file post-finding correction is refrozen as final manifest `e33a7f03213af742a80e7eb1366b7174f6058658d1bc76c433afa5bc098c8f56`; canonical SHA and all65 filesystem states/bytes independently match on final recheck. Compile of this final owner-DDL delta is pending the root lease; cf3a… compile/104PASS evidence is earlier-source evidence and is not relabeled as final e33a… PASS. New DB/Redis/HTTP IT runtime remains NOT_RUN because Docker is inaccessible; no new exclusions/skips/xfail/gate changes. Source approval does not establish runtime acceptance, whole PR acceptance, or a7446-test audit. Frozen B013–15/historical rejected Mongo/Chromium untouched.

## Per-ID source coverage

| ID | Independent source assessment |
|---|---|
| AD-D01 | Удалены два mock protocol/order метода; PG overlap 201/409 и реальная semester/subject lock-сериализация сохранены. |
| AD-D02 | Удалён весь forwarding-only файл (один метод); standalone/tuple closure, key/date conflict и retained history сохранены. |
| AD-D03 | Удалён один duplicate helper case; fixed-clock archive name/flags/timestamp и event остаются. |
| AD-D04 | Удалён mock rename-chain; surviving PG four-course chain, UNIQUE names/IDs, ARCHIVE ledger/history остаются. |
| AD-D05 | Удалён entity reflection case; registry/persisted canonical names остаются. |
| AD-D06 | Удалены ровно четыре согласованных mock cases; legacy denial, full assignment tuple, closure fence/tuple close остаются; create-lock risk перенесён AD-R01. |
| AD-D07 | Удалён весь lock-contract файл; date immutability, name-only update и assignment/semester race остаются. |
| AD-D08 | Удалены два non-author success mocks; migrated-PG edit/archive сохраняют creator, completion/history, replay и action outbox. |
| AD-D09 | Удалены два completion protocol mocks; unique desired-state concurrency, other-student isolation и timestamp undo/redo остаются. |
| AD-D10 | Удалён один upload wiring case; existing real PG upload/history/absent SVG и pointer-write rollback proof остаются. |
| AD-D11 | Удалён один delegate-only aggregate getter; validation/privacy units остаются. |
| AD-D12 | Удалены два status duplicates; deterministic signed-status matrix + archived-aware reads остаются. |
| AA-D01 | Удалён mock assignment projection; real gRPC producer fixture проверяет весь retained tuple и concrete null-end. |
| AA-D02 | Удалён mock teacher-subject projection; actual gRPC current/future/expired и active grant controls остаются. |
| AA-D03 | Удалён duplicate exception mapping; inconsistent-source mapping и resolver closed-history branch остаются. |
| AA-D04 | Удалён scripted two-read fake; actual foreign AUTH_PREPARED fence с неизменным digest в RC сохраняется. |
| AA-D05 | Удалены ровно три repository-overload/delegate cases; actual search/role DB predicates остаются. |
| AI-D01 | Удалён весь substring OpenAPI файл (три метода); parsed full snapshot и shared per-operation errors/media/schema units остаются. |
| AI-D02 | Удалён только null-bean/contextLoads method; остальные mapping/archived/array/active-semester/credential proofs сохранены. |
| AI-D03 | Удалён один random repeated blank-middle case; 12 golden vectors включая null/empty/spaces и другие properties сохранены. |
| AI-D04 | Удалён dormant second repository guard и exclusive helpers; entities no-relations и SQL/data batch proofs сохранены. |
| AD-R01 | Новый signed MockMvc subject-create/semester-update PG race принимает только consistent winners, проверяет полные subject/type/assignment counts и bounds. |
| AD-R02 | DTO contract проверяется serialized JSON name/absence code вместо private field reflection. |
| AD-R03 | Real HTTP roster с actual concurrent role transition после guard; первый ответ coherent old state, следующий committed state; pending-group cheap denial сохранён. |
| AD-R04 | Real HTTP creator edit/archive/replay на migrated PG; exact content/revision/creator, completion/history, one update и action counts. |
| AD-R05 | Корректный ordinary unskipped negative LESSON+NULL; accepted product failure SQL UNKNOWN сохранён и явно disclosed, не исправлен продукт. |
| AD-R06 | Specific PostgreSQL SQLSTATE/column/constraint и valid foreign-group exclusion rows; нет false generic integrity rejection. |
| AD-R07 | Fixed instructional Clock делает explicit academic year unit независимым от host date. |
| AD-R08 | Link URI path/origin/query semantics проверяются без зависимости от query parameter order; self/next сохранены. |
| AA-R01 | Frozen literal lifecycle wire codes + enum key-set completeness; oracle не вызывает getCode у expected. |
| AA-R02 | Cancellation наступает после first emitted chunk, не по числу isCancelled probes; exactly1/no completion/no error. |
| AA-R03 | Все17 методов на full Flyway UUID schema/runtime principal сохранены. P2 fault-injection DDL privilege defect исправлен sole writer и независимо перепроверен: owner-qualified UUID-schema DDL/cleanup; runtime jobs/grants неизменны. Impossible dual-active seed заменён DB rejection + narrow mocked ambiguity-count fallback; CLOSED guard operation валиден. |
| AA-R04 | Real DomainWriter transaction + actual helper/headman/history/outbox, accepted actor, event set и durable receipt revision. |
| AA-R05 | Все4 CSV transitions и3 separate methods на real migrated PG; IDs/dates/reasons/control history, helper no-op и profile persisted rows. |
| AA-R06 | Real JDBC ResultSet replaces SQL parser; controlled Auth/captured command допустимы accepted seam; directory SUSPENDED/revision2/ACTIVE grant сохраняются. |
| AA-R07 | Owned Redis process, two limiter instances, independent users, limit denial и observed expiry recovery без TTL refresh; cheap boundary/fail-open units остаются. |
| AA-R08 | Actual signed-admin HTTP exact200/IDs/no credentials и forged legacy controls; dual-mode legacy mapping narrow filter chain с assertions. |
| AA-R09 | Strict-mode actual signed HTTP exact200/exact3IDs/no credentials; legacy/missing/invalid negatives остаются. |
| AA-R10 | Actual HTTP invalid opaque ticket status404/problem/no-store/no secret echo in body/headers/template instance; pure token validity tests остаются. |
| AA-R11 | Actual HTTP enqueue/render with real captured report-purpose admission; ordinary route denial, wrong lease, FULL sensitive fields, LIMITED redaction, permission revoke. |
| AA-R12 | Migrated PG adapter exact IDs с foreign group, other semester broad-range control и half-open excluded endpoints; controlled Auth не используется данным методом. |
| AA-R13 | DB literal underscore/backslash/plain and existing percent witness exercise actual predicate; wildcard controls исключены. |
| AA-R14 | Actual HTTP user creation + real Auth persisted initial credential/BCrypt; Academic schema/entity не владеет credentials. |
| AA-R15 | Null/empty/spaces и optional role/status absence проверяют exact7IDs через PG predicate вместо null Specification representation. |
| AA-R16 | Archived registry actual HTTP paginated exact teacher IDs включая restored archived-role history; active/suspended/foreign roles excluded. |
| AA-R17 | Audience batches bounded<=200 with exact union/no duplicates; deliberate second RPC failure не возвращает partial first batch. |
| AA-R18 | Removed only reflection handler identity; real advice safe status/privacy retains response oracle. Actual Channel sees deadline on4 production calls including getHomeworkBinding + observed count4. |
| AI-R01 | Actual Academic DELETE HTTP via actual Auth servlet handler/JDBC/Redis password service and issuer filter; live digest/wrong current password403; durable semester/operations/outbox/jobs/Auth/effects unchanged. |
| AI-R02 | Owned outbox witnesses by IDs/types; old and cutoff-1microsecond delete, exact cutoff/recent/pending retained; second run zero. |
| AI-R03 | Actual JDBC execution counter: cold positive/warm zero plus result equality; current-authority user gRPC intentionally remains uncached and profile Spring proxy measured separately. |
| AI-R04 | Real HTTP activate different semester and archive selected synthetic user; later reads show new semester/archived profile and unrelated controls unchanged. |
| AI-R05 | Existing50ms gate retained; exact30-ID scope plus normalized EXPLAIN rows/filter work<=120 makes full scan observable on1800rows; no index dropped. |
| AI-R06 | Actual HTTP resolver subject/group/global/default, sourceId/value/level and foreign pair; redundant exact getters deleted. |
| AI-R07 | Data/order/publication/actor cases plus bounded query budget sizes1/4/6 permit fewer statements; actual signed HTTP homework page; lifecycle roundtrip remains. |
| AI-R08 | Actual HTTP >100 real Auth-created identities, exact100 metadata/body, total105, two pages/no duplicates/disjoint/full known ID set. |
| AI-R09 | Actual HTTP top-level health.status and exact accepted management links; direct forbidden endpoint probes remain. |
| AI-R10 | PG lock owner/PID relation + pg_blocking_pids replaces literal SQL equality; actual wait/release/revoke/history/old-session denial remain. |
| AI-R11 | Actual HTTP rename + migrated PG durable outbox/Rabbit event pair correlated by actual IDs/group, canonical name/actor/envelope; same-name negative remains. |


## К4 — независимое review Academic corrections

Review receipt SHA256: `d21cd821476b5426f1ec71e4f7b7fc81fdf371206c36cb6d0d9d2ec7c8a2f563`.

# Независимое review Academic HTTP corrections — APPROVE SOURCE

Итог: **APPROVE SOURCE**, blocking findings **0**. Это заключение относится только к финальному frozen четырёхфайловому TEST-only delta. Runtime/compile/CI acceptance **NOT_RUN**; исторические failure stage/cause **UNKNOWN**.

Reviewer независим от автора; requested model/effort `gpt-6.1-sol/high`, actual runtime metadata **UNKNOWN**. Прочитаны применимые root/services AGENTS, canonical RULES/CURRENT и workflow; текущий scoped owner GO передан root. Более старые proposal/packets не расширяют задание. Дочерние агенты не создавались.

## Проверенный снимок

- Root source: `/workspace/work/pr2-review/repo`, `bc30ea151c733803c118be79155b86cf49bd0a4d`.
- Writer: `/workspace/work/test-audit-implementation/academic`, HEAD `2eca01a3ef4c65cc2ed1f1ccc6bb7c50eb211615` с ранее существовавшим WIP.
- Frozen package: `/workspace/work/test-audit-implementation/academic-http-correction`.
- `manifest.json` SHA256: `65e759d45b11ac143e44036d7e721ec517a110a2a44ef8712cada14841555c0c`.
- `correction.patch` SHA256: `0e2321ec286d6aaa574ae96b9f8dceca668063b7175a469beea84f9fd1171410`.

Каждый before SHA совпадает с root source, каждый after SHA — одновременно с frozen и writer. Patch независимо восстановлен из before/frozen и совпадает побайтно. Academic source root не отличается от опубликованного `a6c64389284b9daa9249f056ab443e8cc4f25dac`.

| TEST-only файл относительно repo | Финальный SHA256 |
| --- | --- |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/RestApiIT.java` | `8ee9bb192b91e0803913bc16fb8486039678634603043e576bdd3eb6906566cb` |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/security/AcademicUserContextFilterIT.java` | `64ab08c7b82b6182adb048cf9d75ebb09ad6e6058f7b432ddc105d561cf71b6d` |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/security/AcademicUserContextFilterStrictModeIT.java` | `c5383c7896e0b157a48d4808b8d14108176da352c43170841e03bb8c8bba2fe4` |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserCredentialOwnershipIT.java` | `460738a98d3719c40b76842eb24da8a8e40af8b3cd7d2cf825bfef826410101b` |

## Source review

1. **RestApiIT:737,743,759–770 — корректная fixture-транзакция.** Проверена текущая `db/academic-migration/V3__academic_domain_baseline.sql`, а не только историческая `db/migration`. Constraint triggers на строках 663 и 669 DEFERRABLE INITIALLY DEFERRED требуют минимум один `subject_lesson_types` у сохранённого subject. Новый data-modifying CTE записывает parent и lecture child одним SQL statement/commit. Аналогичный CTE уже существует в сохранённом homework сценарии этого класса. Все threshold resolution HTTP 200, level/value/sourceId, четыре уровня и повторная чужая group/subject control остаются побайтно прежними.

2. **AcademicUserContextFilterIT:130–133,168–171; StrictModeIT:76–79 — semantic privacy.** Текущий `UserController.listUsers` вызывает `UserAssembler.toRegistryResponse`, который передаёт `includeInitialPassword=false` и создаёт null. `UserResponse` содержит nullable getter; текущий shared `JacksonConfig` не включает NON_NULL. В canonical `docs/openapi/academic.json` поле `EntityModelUserResponse.initialPassword` optional: required list отсутствует. Поэтому отсутствие поля и явный JSON null допустимы для privacy oracle; финальная проверка принимает только MissingNode/NullNode. Любой непустой или пустой string, number, boolean, array либо object отвергается. `passwordHash` по-прежнему обязан отсутствовать. Точные 200, totalElements=3, canonical IDs, conflicting legacy tuple и все существующие 401 negatives сохранены.

3. **UserCredentialOwnershipIT:67–72 — невакуумный catalog oracle.** Fixture `configureDatabase:353` устанавливает runtime `search_path=pg_catalog,public`; production `infra/postgres/init-roles.sh:83` использует ту же политику. `current_schema()` поэтому указывает на pg_catalog, а canonical V1 создаёт `academic_profiles` в public. Явный public исправляет адрес проверяемой таблицы. Положительный witness `id,last_name,first_name` не допускает пустой результат metadata query. Отсутствие трёх credential columns, Auth initial/BCrypt/password_changed proof, отсутствие credential значений в Academic entity и passwordHash в response сохранены.

4. **Реальная граница сохранена.** Base использует RANDOM_PORT; positive HTTP вызовы идут через TestRestTemplate. Существующая fixture мигрирует split Academic/Auth PG, использует отдельные runtime/migrator roles, real SessionLifecycleService create/selectRole и Auth gRPC AdmitSessionIdentity. ACL/schema ownership guards и legacy/strict property checks не изменены. Общие helpers не менялись.

Замечание к промежуточному freeze `ce824005…` (exact JSON null вместо semantic absent-or-null) устранено автором. Независимо перечитаны все три финальных assertion и подтверждена их граница; прежний intermediate не одобрен как финальный.

## Сохранность и границы доказательства

- Baseline содержит 66 dirty paths: 65 предыдущих WIP и 1 чужой shared helper. Все 62 paths вне четырёх исправлений сохранили прежние SHA/удалённость; полный набор porcelain statuses совпал с baseline. Локальные тестовые методы не добавлялись и не удалялись. Production/workflows/dependencies/grants/common helpers не затронуты incremental delta.
- Проверены `manifest.json`, `results.json`, before snapshot, final frozen source, exact patch и адресные production/contract/fixture sources. Не выполнялись тесты, Gradle, compile, DB, browser, CI, commit или push; продуктовый/тестовый код reviewer не правил. Единственная запись reviewer — этот внешний отчёт.
- Safe projection SHA256 `298dfb6a9ae85b341e2055f100d32a30f5e5559aa41dc073f148c47ab24ac7c0` проверен. Он связан с run `39505605425` attempt 1, artifact `12305165990`, source `a6c64389284b9daa9249f056ab443e8cc4f25dac`, artifact digest `8c02348e5c3dc00c61929ba9a3532f72a766a096090924363dda0589c3414ffc`: 496 PASS, 44 FAIL, 1 SKIP; 44 failures имеют SOURCE_METHOD identity.
- В рассматриваемом HTTP/domain scope 24 failure cases: только 5 source-affected приведёнными четырьмя файлами; 19 остаются обычными неразобранными failures. Safe projection не содержит failure stage/cause. Исправления обоснованы текущими источниками; принадлежность исторического падения конкретному assertion и последующий PASS не установлены.
- Export scope, сохранённое root finding HomeworkMigrationIT, B013–15, rejected Mongo PUT500/Chromium equivalents и canonical 263-ID audit не переоткрывались и этим заключением не сертифицируются.

Следующий шаг root: личная проверка источников/заключения и интеграция только перечисленного frozen delta; runtime acceptance отдельно по разрешённой стадии.

Review receipt SHA256: `82979198e9abdd5ab3c808420075b9e675c6ebbdad6ccc1b634ccaefd0393d6e`.

APPROVE — независимое source-only review frozen пакета `academic-exports-correction`. Blocking source findings не обнаружены. Это одобрение узкой правки fixture, а не runtime acceptance или подтверждение устранения CI-падений.

Reviewer независим от автора. Запрошены GPT-6.1 Sol / high; фактические model/effort — UNKNOWN. База root и writer worktree: `bc30ea151c733803c118be79155b86cf49bd0a4d`; исходная опубликованная revision: `a6c64389284b9daa9249f056ab443e8cc4f25dac`. Прочитаны root/services/tests AGENTS, RULES, CURRENT, workflow и применимые AA-R03/AA-R11 из academic-packet. Текущий узкий read-only contract имеет приоритет над историческими состояниями CURRENT. Риск review: изоляция соединений, runtime privileges и сохранение реальных транзакций/конкуренции; product bytes защищены.

Проверенная коррекция и сохранённые границы:

- [AcademicExportJobsIT.java:76](/workspace/work/test-audit-implementation/academic_exports_correction/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:76): schema теперь устанавливается на соединениях существующего `identityFixture.academicJdbc().getDataSource()`. [AcademicIdentityIntegrationFixture.java:229](/workspace/work/pr2-review/repo/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicIdentityIntegrationFixture.java:229) создаёт этот datasource как `DriverManagerDataSource` с теми же Academic URL, runtime role и credential variable, которые fixture передаёт Spring в строках 471–473. Это не migrator и не смена authority. Roles создаются без superuser/create-role/create-db/replication/bypass-RLS; database/schema owner — отдельный migrator; runtime isolation проверяется в строке 1090. Disposable соединения исключают возврат изменённой schema в общий application pool.
- Предыдущая pool-state ошибка подтверждается source: удалённое autowired поле питало wrapper, вызывающий `setSchema` без восстановления. В доступных application/fixture properties нет Hikari schema; frozen dependency proof показывает `PoolBase.resetConnectionState` с проверкой `schema != null` перед reset. SHA-256 локального HikariCP 6.3.3 JAR независимо совпал с proof: `709f378c05756280939ce50fc1b1f1a53bb8e1899dc1b249f21f12703640b48b`. Повторный `javap` недоступен в PATH; новых зависимостей не получал. Это устанавливает fixture isolation defect по исходникам, но не причину конкретного CI-падения.
- [AcademicExportJobsIT.java:47](/workspace/work/test-audit-implementation/academic_exports_correction/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:47) сохраняет полный Flyway chain `classpath:db/academic-migration` в UUID schema под owner. Это текущая production location ([docker-compose.prod.yml:373](/workspace/work/pr2-review/repo/docker-compose.prod.yml:373)), также выбранная fixture override в строке 482; соседний legacy `db/migration` не принят за канон. Таблицы/constraints/trigger bodies не заменены ручными урезанными аналогами.
- [AcademicExportJobsIT.java:61](/workspace/work/test-audit-implementation/academic_exports_correction/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:61) и строка 71 восстанавливают закрытость operator bootstrap table/function после существующих blanket grants. Это соответствует [V11__initial_admin_bootstrap.sql:29](/workspace/work/pr2-review/repo/services/academic-service/academic-app/src/main/resources/db/academic-migration/V11__initial_admin_bootstrap.sql:29): table закрыта PUBLIC/runtime, функция закрыта PUBLIC, а runtime не должен получать отдельный EXECUTE. Сохраняется revoke UUID-schema Flyway ledger; canonical afterMigrate callback для `public.flyway_schema_history` не подменяет этот дополнительный revoke. Не появилось owner DML вместо runtime DML или предоставления runtime CREATE/TRIGGER/ownership.
- [AcademicExportJobsIT.java:94](/workspace/work/test-audit-implementation/academic_exports_correction/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:94): JdbcTemplate, LocalReportAuthority proxy и ExportJobRepository proxy сохраняют один wrapper datasource как Spring transaction resource key. Оба настоящих `DataSourceTransactionManager` и annotation advice остаются. `REQUIRES_NEW` LocalReportAuthority по-прежнему получает отдельную транзакцию/соединение с suspension внешней транзакции; замена на unpooled datasource этому не препятствует. Advisory transaction locks, FOR UPDATE/CAS, rollback artifact/audit/outbox и afterCommit metrics остаются в production repository.
- Сценарий четырёх simultaneous claims в строке 210 сохраняет executor/latch и независимые реальные JDBC transactions: нет single-connection datasource или добавленной сериализации. Остались global-two lease oracle, bounded queues, retry/backoff, stale leases, actor/scope/hash/current authority, availability guards, rollback/audit/outbox/metrics, retention/cleanup и privacy. [AcademicExportJobsIT.java:121](/workspace/work/test-audit-implementation/academic_exports_correction/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:121) удаляет только UUID schema через owner после метода; fixture listener и existing lease в `AcademicIdentityIntegrationFixture.beforeTest/afterTest` сохраняются. Unpooled datasource не удерживает pool со schema, которую уже удалили.
- [AdminRegistryExportsIT.java:324](/workspace/work/test-audit-implementation/academic_exports_correction/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/export/AdminRegistryExportsIT.java:324) не изменён: real HTTP purpose/lease/FULL→LIMITED→revocation/privacy sequence сохранён. [ReportDownloadTicketBoundaryIT.java:17](/workspace/work/test-audit-implementation/academic_exports_correction/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/ReportDownloadTicketBoundaryIT.java:17) не изменён: 43-character anonymous ticket request, owner 404/problem/no-store/no-echo соответствуют текущим filter/controller/advice boundaries. Оснований исправлять их oracle по одному FAILED outcome не установлено.

Независимая проверка точных bytes (Python read-only comparison, git show/diff/status; без компиляции или тестовых запусков):

| Файл | Baseline SHA-256 | Frozen/worktree SHA-256 | @Test |
| --- | --- | --- | --- |
| AdminRegistryExportsIT.java | `f13b59a4c508ddb05c32ace38830c094ef7557a1b64a243fce084d5e12115454` | тот же | 6 |
| AcademicExportJobsIT.java | `30afcc02d68553512cd322724485e3bacf8d5d5df16c85610070946b5c3e1b63` | `b454db34df04d223ad893769ecc392e445b84e22eb5eb18816eb8ae78e944a7b` | 17 |
| ReportDownloadTicketBoundaryIT.java | `2c12b9a012313ff170096689cad21e68cda43404de4f4b458c9fa662da9b28f9` | тот же | 1 |

Все baseline bytes совпали и с bc30, и с a6c64389 через `git show`; frozen bytes совпали с writer worktree. Для каждого файла независимо сравнён весь suffix от первого `@Test` до EOF: byte-identical, включая полные declarations/bodies всех 24 тестов и последующие helpers. Это независимо подтверждает заявленную сохранность методов без повторного запуска авторского Javac checker. Baseline/frozen inventories содержат ровно по три Java-файла. Полный `git diff --no-ext-diff --binary` worktree побайтно совпал с incremental.patch; SHA-256 `a06299fff798e91756d3348928773e0968266eb34613cf54befcc6299702c342`. Единственный modified tracked path — AcademicExportJobsIT.java, 10 insertions / 1 deletion. `git diff --check` PASS; прочих source/untracked изменений status не показал.

Ограничения: Gradle, compileTestJava, tests, DB, browser и CI не запускались; commits/push/source writes не выполнялись. Создан только этот отчёт вне tracked tree. Hikari bytecode proof прочитан из frozen пакета; проверены версия/hash доступного JAR, но его disassembly повторно не построен. Сведения об uploaded Push `39505605425` attempt 1, artifact `12305165990`, ZIP SHA `8c02348e5c3dc00c61929ba9a3532f72a766a096090924363dda0589c3414ffc`, 496 PASS / 44 FAIL / 1 SKIP и 19 scoped SOURCE_METHOD failures — входной safe evidence contract; raw private XML/logs не открывались, ZIP заново не скачивался. Точные failure stages и runtime causes остаются UNKNOWN. Одобрение не означает, что устранены 17 AcademicExportJobsIT failures либо какие-либо из 19 scoped failures. Полный audit 94+169 не переоткрывался; frozen B013–15 и rejected Mongo/Chromium equivalents не изменялись и не запускались. Product behavior не корректировался; ослабления проверяемых product требований в diff нет.
