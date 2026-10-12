# NP01 domain/bot implementation

State DOMAIN1_ACTIVE; bounded runtime lease granted. Assigned root packet read with TASK-CONTRACT
SHA 8cbd27d431815647886b2f39592905d3f1d4cb985643e5d9fa3f3f669b4df4a5 and T002.
Worktree /workspace/rct-worktrees/np01-domain-bot, branch
codex/rct-validation-np01-domain-bot-20261011, frozen base
2bca095380f72e8908a2511f2b10f0e795607f05. Requested Sol6.1/xhigh, actual UNKNOWN.

Root accepted exact product scope: NEW Academic botcredentials/
BotCredentialMessageRepository.java, BotOwnInitialCredentialsQuery.java,
BotCredentialsService.java, BotCredentialServiceIdentityInterceptor.java;
AcademicGrpcServiceImpl constructor/dependency and two new RPC delegations only.
Auth PasswordChangedEvent.java / PasswordChangedOutbox.java and affected/new tests.
NEW bot grpc_client/bot_service_credentials.py,
grpc_client/bot_credentials_client.py, services/bot_credential_cards.py;
existing handlers/start.py (/credentials also lives there),
notifications/password_changed.py, __main__.py and
consumers/event_dispatcher.py constructor + password.changed delegation only;
scoped tests. No credentials.py or shared/config/proto/schema/migration writes.

Common owner /root/export_resume prepares NEW np01-contract worktree: two RPCs,
closed typed render/cleanup outcomes, separate stable render_revision and CAS
record_revision, tracker DDL + generation/invalidation trigger, explicit v1/v2
schema/generated outputs. Exact publication SHA is prerequisite for dependent
source/compile. Config owner /root/security_fingerprint prepares NEW np01-config:
Settings.bot_to_academic_service_token blank str default, existing Academic
host/port/TLS/CA fields, grpc.service-identity.bot-to-academic-token property.
New protected client denies missing TLS/token without altering ordinary startup.

No Java/Gradle/Docker/browser/app runtime, no push, no children, no real
credentials/config/env contents. Local source/parser checks only until root lease.
Independent source/security/concurrency review and real PG/TLS/Auth→bot/provider
acceptance remain required. Next: implement transport wrapper and producer while
waiting for exact common contract publication; keep old generic RPC redacted.

Published source-only common shape:
/workspace/scratch/rct-validation/np01_contract/consumer-contract.shape.safe.json,
frozen proto SHA256 23ba76698b62f8cf03cbcb38d105f99463eeee82201654a05e0b03b7e9401ae8.
Explicit SNAPSHOT=1/RECHECK=2 mode, stable render_revision8 distinct record CAS7,
closed typed render/cleanup outcomes, replay ALREADY_REGISTERED6 never permits
render. New V57/table implementation and generated outputs pending.
Config published clean5d564870e631a276d88ac5a4b97463929f9b6687, exact2paths.

Accepted own authority source boundary: unarchived known account state;
STUDENT current ACTIVE own grant with nonnull group; HEADMAN only consistent
matching ACTIVE STUDENT group; TEACHER valid own base; ADMIN valid current
assigned FULL/LIMITED profile using existing AdminAccessJdbcAuthority, without
target credentials permission. Helper qualifies via own base grant. No new
group.is_active or default-role restriction. Source references: current
UserRoleGrantWriter active-student query, NotificationAudienceResolver headman
consistency, shared AdminAccessJdbcAuthority.profile + canonical permission
validation, T002 and canonical JS-SYSTEM-01/02/04 fragments.

Stable22path author checkpoint2f4d3d412753bedb1b084442efef63713a4e4348 clean,
SOURCE_READY_FOR_INDEPENDENT_REVIEW; AST11Python/diff/generic-method byte proof
passed, all runtime NOT_RUN. Root approved SOURCE_ONLY test extension:
NEW Academic botcredentials/BotCredentialPythonBridgeIT.java and exactly ONE
bot tests/np01_bridge_runner.py, separate harness commit from immutable2f4d.
Existing Academic test classpath expected sufficient; sole config owner notified.
Pinned Python interpreter must be explicitly supplied via NP01_PYTHON_EXECUTABLE.
Real User/Group JPA repositories, actual production AcademicGrpcServiceImpl and
Spring transactional domainbeans, disposable ownPG, synthetic realTLS loopback,
actual Python protected/generic clients + native Dispatcher + provider. Isolated
test-only loopback control for ownfixture state; no public test endpoints or new
Auth dependencies/fake producer claim. Auth actual JDBC change/reset/outbox still
separate acceptance until actual broker/live crosslayer. Synthetic sensitive
values only subprocessENV; no values in CLI/stdout/artifacts. Safe stdout
booleans/counts/phase, terminal exit required. No runtime until root lease.

Harness separate clean checkpointdc9acc4385c756ce7bf070f248feadc7c4dba234,
exact2newpaths, existing22path bytes unchanged at that checkpoint. Root assigned
soleconfigwriter narrow Academic-only CI/Coverage Pythonvenv environment seam;
new bridge deliberately has no skip or implicit systemPython fallback.

Author source finding NP01-COMPAT-01: actual main always injects a disabled
protected client under missing dedicated token/TLS; frozen2f4d /start then fails
even ordinary redacted changed-password profile, contrary to accepted config
compatibility. Bounded own4path correction prepared: safe configured bool,
delivery-only fallback to existing generic profile with zero protected RPC or
credential edit, focused handler/client source controls. Cleanup continues to
fail/retry pending. Runtime sensitivity against frozen2f4d remains NOT_RUN.
22base and2pathharness commits retained immutable for independent review.

2026-10-12 resumed DOMAIN1 after sole root dependency import. Actual worktree
HEAD7521982ef69e2fc29ea01c4100d28840d34079fd: all own24 paths exact0b9,
12 common/config paths exact root2a; source6gates8666 native0. Fresh full root
TASK/CURRENT/decisions/questions/resources and independent source recheck
restored (APPROVE_SOURCE_ONLY, runtime NOT_RUN). Current cloud observations
enforced unrestricted HTTP/no secret bindings; Docker/networking skill refs
read. Default sandbox Docker socket denied; exact authorized local daemon
check via escalation PASS28.4.0/vfs, no daemon/config changes.

Lease maxworker1/Gradle768MiB/test768MiB/maxonefork ownPG16-alpine +ephemeral
loopback TLS/control/aiogram provider +READ_ONLY pinnedvenv only. Other lease
export audit independently owns its PG/Redis/AuthHTTP. Floor4GiB immediate/
periodic. No fullapp/Rabbit/browser/realTelegram. Before prelaunch three owned
test image constants narrowed postgres:16→postgres:16-alpine as lease requires;
no product/common source delta. Temporary own domain-heap.gradle caps Test forks
and disables HTML artifacts only; retained native XML/logs private and safe
outcome summaries. Next sequential targeted module compile and scoped checks,
then meaningful COMPAT01 bad22/green; propose critical faults after normal green.

DOMAIN1 restore complete. compile01 terminated before Gradle/JVM/PG because scratch evidence collector used quoted Git filenames. Safe setup failure preserved; NUL-delimited path parsing fixed. Auth JdbcSessionAuthorityIT original postgres:16 is restored exactly; only two new owned Academic fixture image constants select16-alpine. Next compile02; no product runtime result inferred.

python01 actual pinned interpreter scoped tests: native0, 71 tests/0fail/0error/0skip, all7046 tracked inputs unchanged, synthetic-marker matched files0, safe summary runtime/python01/summary.safe.json. Includes event_dispatcher existing controls; source-authority/provider stubs only, not realPG/TLS acceptance. compile02 remains single running JVM; next Java units after terminalcompile.

compile02 actual targeted Academic/Auth compileJava+compileTestJava native0/78.481s, all7046 tracked inputs unchanged, no marker matches. Normal generatedJava compiled, no classpath failure or sharedsource workaround needed. Next unit01 receiver+PasswordChangedContract, then PG lifecycle/Auth np01 and TLS/Pythonbridge.

unit01 actual receiver/schema native0:13/0fail/error/skip, all7046 inputs unchanged/no marker matches. Refining one COMPAT01 meaningful test to use actual disabled BotCredentialsGrpcClient (read wrapped only for zero-call observation), rather than stub fallback; frozenbad22 should render service-unavailable and fail exact ordinary-profile expectation, no AttributeError-only proof. This owned test-source delta is saved before new runtime; full previous Python71 is historical for previous test bytes. Next realPG lifecycle/auth np01, then bridge and COMPAT counterexample.

pg01 actual PostgreSQL fixture reached,19 cases FAIL before rules: group synthetic name41 exceeds persisted varchar32. Both two new own fixtures corrected to25chars; original Auth fixture untouched. Auth np01 task not reached. Raw privateXML/safe summary retained; no product failure inferred. COMPAT before01 actual frozen2f4d two product paths + final meaningful test: native1/one AssertionError, no AttributeError;125 copied inputs preserved byteexact. Next pg02 and COMPAT after01.

pg02 native1/19fixtureFAIL: existing user_role_grants requires nonnull created_at+updated_at, no SQL default. Added NOW()/NOW() to own two new fixture INSERTs following existing AuthIT fixture; no domain/assertion/schema modification. Auth np01 not reached. COMPAT after01 actual native0/1PASS, allinputs unchanged; before qualification corrected for pytestXML omitted failuretype using exact AssertionError text protected-read count1 vs0. Real disabled client used both phases. Next pg03.

COMPAT visible outcome supplement actual real disabled client: frozenbad22 ordinaryprofile=false/safeunavailable=true/protectedreads1/genericreads1/credentialedits0; final ordinaryprofile=true/unavailable=false/protectedreads0/genericreads1/edits0. Same synthetic test fixture, own observer sha428c23; safe compat-visible-outcomes.safe.json. Before selected test expectedAssertionFAIL, afterPASS. pg03 remains single actual JVM; no Rabbit attribution.

pg03 actual green: native0/{'tests': 22, 'failures': 0, 'errors': 0, 'skipped': 0}, all trackedinputs unchanged/no marker matches. Own newLifecycle PG16-alpine plus original AuthJdbc PG16, safe database-provenance receipt; actual Authchange/reset/outboxrollback qualified, no broker/eventconsumer ACK claim. Next bridge01 actual production RPC/TLS/PG/Python.

pg03 TERMINAL native0 counts{'tests': 22, 'failures': 0, 'errors': 0, 'skipped': 0} trackedinputs stable=True; safe receipt runtime/pg03/summary.safe.json.

bridge01 TERMINAL native0 counts{'tests': 1, 'failures': 0, 'errors': 0, 'skipped': 0} trackedinputs stable=True; safe receipt runtime/bridge01/summary.safe.json.

python02 TERMINAL native0 counts{'tests': 71, 'failures': 0, 'errors': 0, 'skipped': 0} trackedinputs stable=True; safe receipt runtime/python02/summary.safe.json.

Corrected test-only checkpoint frozen aa713963941f10b32b00f1ca9cb7b7c369dd8947, cleanauthor WT. Own24 hash inventory domain1-source-inventory.safe.json; product bytes exact0b9, only3 owned testpaths follow-on. Normal green source produced before commit has samebytes; mutation plan stillPROPOSED_NOT_RUN, no active JVM/PG/bridge/process. Own PG cleanup proof runtime-cleanup.safe.json. Actual PostgreSQL16.15/nativeimage16 and16-alpine verified retainedprivateXML. Next root selectivefault grant or independentreceipts source review.

Safe runtime report DOMAIN1-REPORT.md frozen normal pipeline. Preparing detached own fault-worktree under own scratch from aa713963; only source preparation, no faulty code/Gradle/service execution yet. Exact3 plan sent root before execution; next bounded sensitivity phase under existing DOMAIN1 maxworker1 resources if no steering conflicts. All normalown containers absent.

FAULT_PERMISSION_RUNNING session49073, isolated detached ownfault WT from aa713963; exactsource snapshot fault-sources/permission + fault-permission-inputs.safe.json. Existing DOMAIN1 selectiveproof authorization used after normalgreen and root plan publication; root steering takes precedence. No actual grpc/bridge/app exposure. Next native/xml intendedAssertion qualification and exactrestore.

Faultpermission01 actual native1/one intendedAssertion missing foreignUID denial (qualification.safe.json), source7046 unchanged/no marker matches; query restored exactnormalhash before next enum variant. ENUMapplied onlyisolatedrepository unknownoutcome default→NO_EDIT, ownsource snapshot frozen, directtestonly next.

Faultenum01 actual native1/one intendedAssertion missing unknown991 denial (qualification.safe.json), source7046 unchanged/no marker matches; repository restored exactnormalhash before CAS requestedrevision comparison variant, all otherstate/fence/SQLCASguards preserved. Next selectedCAS directtest then restore3green.

FaultCAS01 actual native1/one intendedAssertion missing stale recordrevision denial (qualification.safe.json), source7046 unchanged/no marker matches; repository restored exactnormalhash. IsolatedWT git diff clean aa713963; next same3 selected normalgreen, no author source delta.

DOMAIN1 TERMINAL_SCOPED_GREEN: all3 planned direct-only faults actualintendedAssertion, exactrestore between, finalsame3PASS/native0 at clean aa713963 detachedcopy, all7046 copy+author bytes identical. All9 ownPG nowABSENT; no liveauthor JVM/provider/TLS/control/consumer. RawXML/log private only. Exactrunner versions normal84fe88 and isolated95cfae retained validatedhash, no checksum rewritten to later version. Safe sealed transfer next; actualbrokerflow remainsPENDING scopeproposal. FullrootTASK contract8cbd read earlier samecontext and current/resources latest03:12:35 rechecked rootcontinued originallease.
