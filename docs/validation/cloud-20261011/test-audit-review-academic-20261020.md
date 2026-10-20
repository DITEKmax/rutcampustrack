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
