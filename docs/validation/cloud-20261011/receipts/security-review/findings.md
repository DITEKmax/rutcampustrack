# Independent security review

Status: REVIEW_PASS_WITH_LIMITS. No confirmed blocking findings in this package.
Review target: 8a9d111dd8f47e41a4b23ba0b5b53736ab7b46f5.
Baseline: 3908fad9624f97aec8891fc79338ceb6b0616bf3.
Worktree: /workspace/rct-worktrees/security (clean at first check).
Scope: product5/test8; locale DB/query mapping, signed TMA timestamp/reuse/live authority, MANAGE_HOMEWORK grant/scope/revoke, QR redaction regression test quality and evidence.
Reviewer owns only this receipt directory. No product edits, heavy processes, child agents or runtime suite repetitions.

Read authoritative packet, full owner contract, CURRENT/decisions/questions/resources, applicable AGENTS and orchestration RULES. D003 permits proof reuse within86400s with fresh authority. Root remains responsible for real PWA/TMA and combined Schedule/Academic client acceptance.

## Verdict and findings

The product5/test8 package is suitable for integration. Confirmed findings: **none**. This is an independent source/test/evidence review, not an independent runtime execution or full product acceptance. The reviewed source HEAD remained8a9d111dd8f47e41a4b23ba0b5b53736ab7b46f5; `git diff --check baseline..HEAD` passed and tracked status stayed clean.

### TMA timestamp and fresh authority

[TmaService.java](/workspace/rct-worktrees/security/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/TmaService.java:72) checks the signed timestamp before reading the linked user or issuing a session. The ASCII digit/length check rejects missing, negative, nonnumeric and out-of-range dates; the caught NumberFormatException is replaced with a constant safe message. Future dates reject before subtraction, removing the previous signed-long overflow path. For the deployed current positive epoch and nonnegative accepted dates, subtraction cannot overflow. The inclusive configured past86400-second window is retained. Production constructor uses UTC epoch seconds; the package-private fixed Clock seam supports exact boundary assertions without sleeping or changing public API.

Validation still verifies HMAC before extracting identity. Reuse is permitted by D003; no invented single-use policy or cached authentication result is added. A repeated call resolves the Telegram binding again, and [AuthService.java](/workspace/rct-worktrees/security/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/AuthService.java:184) reloads grants/rolesVersion and delegates to the locked session transaction. [JdbcSessionAuthority.java](/workspace/rct-worktrees/security/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java:337) checks the fresh grant snapshot/version before insert. Role-policy/bootstrap behavior remains unchanged: suspended grants can yield a neutral session; they do not become selected access. The added replay test correctly distinguishes a successful neutral bootstrap from restored selected permissions. Sequential unbind replay returns401 with no session insert.

### Enum compatibility and unknown/null behavior

Auth and Academic converters now use Locale.ROOT in both directions; Academic query mapping uses it for uppercase conversion. [V1__baseline.sql](/workspace/rct-worktrees/security/services/academic-service/academic-app/src/main/resources/db/migration/V1__baseline.sql:4) establishes the existing stable lowercase PostgreSQL names. Concrete autoApply converters and Java enum sets were independently inspected. No identifier, enum member, public value, protobuf number, column, migration or ordinal mapping changed. Existing generated TypeScript contracts already contain the uppercase API values and all five helper permissions; protobuf permission remains string field2, actor-free.

Known lowercase historical DB codes still map to the same Java values. Null converter inputs remain null; blank query input remains an optional absent filter; unknown query status yields400 on the actual HTTP route. Unknown DB values throw rather than map to a known permission; current PostgreSQL enum types disallow those unknown role/status/subject rows, so this exception assertion is an internal integrity boundary, not an observed public HTTP failure. No compatibility migration or rollback is needed for unchanged stored values. Persisted names continue to be immutable codes; a future rename requires an explicit mapping/migration.

### MANAGE_HOMEWORK grant and scope

Canonical JS-HEADMAN-14/15/16/17 in [job-stories.md](/workspace/rct-worktrees/security/docs/research/reference-rutcampustrack-design/knowledge/job-stories.md:575) accept this capability and its revocation. [AssistantPermissionAuthority.java](/workspace/rct-worktrees/security/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/AssistantPermissionAuthority.java:26) adds exactly MANAGE_HOMEWORK to the existing closed list. The protected RPC interceptor validates the internal JWT and binds actor claims; target requests have no actor override. Authority still requires active writable own-group identity and fresh durable student grant/active assistant assignment, and matches only the requested capability. Foreign group and revoked capability therefore deny. Repository failures retain the existing UNAVAILABLE path rather than becoming successful access.

The whitelist is shared with `allowsRecipient`; the current notification catalogue requests helper MARK_ATTENDANCE or MANAGE_EXCUSES only. Adding MANAGE_HOMEWORK does not change any current event recipient set. [LessonService.java](/workspace/rct-worktrees/security/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java:102) already requests MANAGE_HOMEWORK for next-homework lesson selection. Its existing consumer test checks an actual selected lesson or403 while mocking AcademicGrpcClient; it is adequate consumer evidence but not a live cross-service flow.

### Meaningful tests, sensitivity and cleanup

The timestamp unit tests assert the proper validation exception plus no repository/session interactions for malformed/future proofs. The actual HTTP test asserts401, safe error and unchanged auth_sessions count. Exact past/future one-second boundaries use a fixed Clock. The locale tests compare actual stable codes and query conversion under tr-TR, include null/unknown values, and restore the global locale in finally with ResourceLock. The corrected Academic HTTP fixture selects an existing active durable ADMIN grant through the established helper and deletes its new session in finally; it does not invent or bypass production grants.

The assistant integration test uses signed gRPC and real durable PostgreSQL grants, verifies own capability=true, foreign group=false and the same signed actor=false after partial revoke. The owned assistant/grant/user records are deleted in finally; the existing Academic fixture reset validates fresh container ownership. Auth tests use fresh non-reused PostgreSQL/Redis containers; binding fixtures clear the synthetic binding and the grants test restores statuses in finally with a monotonic version change. The reviewed tests do not skip, swallow test/infra exceptions or replace forbidden expectations with permissive ones.

QR candidate L03 was correctly disproved: [QrLoginExceptionHandler.java](/workspace/rct-worktrees/security/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/exception/QrLoginExceptionHandler.java:19) already handles invalid/malformed proofs at highest scoped precedence and returns a generic400, INVALID_REQUEST and no-store without rejected values. The final standalone fixture includes that actual advice; actual HTTP status/exchange confirms routing. The safe control deliberately omits advice only in standalone synthetic MockMvc and proves the same guard fails with AssertionError containing the reflected marker. That failure is about disclosure, not missing infrastructure or syntax. No product QR mutation was executed; the rejected proposal was not bypassed.

## Reconciled evidence

Reviewed the original23 XML files and four terminal Gradle logs, not only author summaries. All XML have zero skipped/error counts. The early green03 invocation as a whole was BUILD FAILED because the new Academic locale HTTP fixture received403 without a selected admin session; the final fixture-control04 invocation is BUILD SUCCESSFUL and its corrected Academic case passes. Unaffected green03 passes remain valid package evidence; no broad repeated suite was needed.

| Scenario/source | Roles/client/prestate/action | Expected forbidden boundary | Observed evidence and status |
|---|---|---|---|
| SD01 / D003, owner security contract | Synthetic linked account; signed current/expired/malformed/future initData; actual `/auth/tma` and deterministic unit clock | Invalid401, no insert; valid reuse with current authority | Red-runtime02 missing/nonnumeric/overflow=500 and negative/future=200; green03 TmaIT all16 PASS; fixture-control04 TmaServiceTest3 PASS. Bounded PASS |
| SD02 / L04, owner enum contract | Synthetic existing users/full ADMIN grant; tr-TR JPA and actual HTTP `status=active/unknown` | Stable persisted names; known200, unknown400; no guessed role | Red-unit01 expected admin got admın; active conversion failed. Green03 converter/query units PASS; final fixture-control04 Academic case PASS. Bounded PASS |
| SD03 / canonical JS-HEADMAN14–17 | Active own-group STUDENT helper durable MANAGE_HOMEWORK; signed Academic RPC; grant then foreign/revoke | Own true; foreign/revoked false | Red-runtime02 own capability false; green03 assistant RPC PASS. Existing Schedule consumer selects lesson or403, upstream mocked. Bounded PASS |
| L03 QR / owner sensitive-errors contract | Anonymous invalid browser proof; invalid approval/malformed enum standalone fixtures; actual route | Generic400/no-store, no proof echo | Baseline real HTTP already safe; initial red-unit lacked actual advice. Green03 real HTTP PASS; final fixture-control04 protected+disclosure-control3 PASS. Existing mechanism sufficient |

The root must preserve author command/revision provenance and meaningful evidence in the branch/PR. This report does not attribute baseline CI receipts to the author commit or treat local tests as exact-revision remote gates.

## Limits and next step

No real Telegram host/provider, PWA/TMA browser flow, combined gateway→Schedule→Academic flow or final integration/remote CI was run by this reviewer. Root retains these acceptance scopes; separate RPC and mocked consumer results do not close them.

Concurrent Telegram rebind/unbind during issuance was not exercised. `TmaService` performs the binding lookup separately from the later locked session insertion; JdbcSessionAuthority's locked user row contains id/password_hash/roles_version but no Telegram binding. Sequential current-binding/replay is proven; an atomic binding-issuance guarantee is not. This is an unverified concurrency scope, not a confirmed defect or a new product rule: admission may legitimately linearize at the initial lookup. Root should reconcile it with the accepted concurrency matrix before global DONE.

Next: root may integrate this bounded package; keep real client/cross-service and exact final remote validation outstanding. No author correction or reviewer recheck is required for a confirmed finding, because none was found. Reviewer processes: none; resources: READ_ONLY; children: none; product mutations: none.
