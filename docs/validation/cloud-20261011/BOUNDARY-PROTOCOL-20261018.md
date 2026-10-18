# S19 boundary protocol — DRAFT / independent review pending

Target/GO: TASK-ADDENDUM-SPLIT-APPS-20261018.md. VariantБ owner-approved; this
engineering contract is source-level design, not implemented/runtime acceptance.
Base26c. Auth/sharedproto writer s19_boundary; Academic writer s19_academic;
BotPython s19_bot; directed BotRead producer s19_bot_producer; reviewer s19_review.
Root soleintegration/docs. No cross-owner SQL/shared users/FDW as final state.

## Ownership and storage

Auth logical auth_db/new versioned auth-migration path owns UID/login/BCrypt/
initial/reset/change flags/TG binding+generation/roles+versions/adminprofile/
sessions/securityproofs/credentialcardledger/securityoutbox. Auth identities have
PENDING/active state and an operation fence. Auth identity operation ledger stores
UUID/hash/kind/orderedtargets/expectedversions/grantdelta/phase/nonsecretresult/CAS;
never password/token in ledger. Auth user-facing credential retrieval remains
separate, privileged, live and available only after successful activation.

Academic logical academic_db owns assigned-UID profile(names/avatar/employeeNo),
groups/headman/helpers/membership/assignments/history/local eligibility+revision,
operation coordinator and all academic report jobs/artifacts. Auth group IDs
are opaque admission scope, not second domain membership authority. Nonsecret
identity fields for screens/exports are composed with bounded Auth batch reads.
No crossDB FK. Runtime/migrator identities restricted to their own DB; published
V62/V63 immutable, new schema/bootstrap/migrations explicit and nonresetting.

## Provisioning / mutation stages

Academic ACCEPTED → AUTH_PREPARED → LOCAL_COMMITTED → SUCCEEDED.
Auth PREPARED_FENCED → COMMITTED or FAILED_SAFE. Exact terminal/recovery enum
names and failure resolution must match final implementation and review.

1. Accept canonical UUID + command hash once; same UUID/different payload409.
   Persist command/actor UID/SID/version tuple/expectedlocalversions, not bearer.
2. Auth Prepare validates live actor and current operation-specific permission,
   last-full invariants and expected authority versions; reserves ordered target
   UID aggregate (headman old+new together). CREATE allocates UID/login/BCrypt
   only in Auth. Fence authority before ACK and reserve finalRolesVersion=expected+1 (CREATE=1). Pending/
   fenced identity denies credential read/login/OTP/QR/TMA/menu/session admission.
3. Academic rechecks group/profile/headman local revisions, atomically commits
   profile/eligibility/history/helpers/outbox + exact LOCAL_COMMITTED receipt once,
   including that reserved finalRolesVersion.
   No RPC under its transaction/DB locks. Domain failure has no partial localeffect.
4. Auth Commit uses directed service identity + exact UUID/hash/operation CAS and
   Academic committed receipt. Apply full intended grant set preserving unaffected
   grant IDs; activate/unfence at the already reserved finalRolesVersion, without
   another rolesVersion increment/projection-lag window; archive revokes sessions/cards.
5. Lost ACK/timeout queries same UUID/hash; never newUUID or timeout auto-unfence.
   Definite failure stays safe/fenced. Recovery uses durable accepted command,
   not forged fresh actor JWT. Explicit reconcile must validate current local
   state and fresh authority; no implicit restoration of obsolete privileges.

Review must establish every fence/recovery interleaving, including CREATE failure
after real Academic history flush: no localprofile/history/outbox effect; Auth
reservation remains nonactive/fenced or safely terminal, not distributed rollback.
Clarify FAILED_SAFE/reconcile paths in code so accepted work never stays silently
pending and an expired original actor cannot authorize unrelated new commands.
Reading status/result still requires current request authority/object scope.

## Transport / consumers

Additive proto/auth_authority.proto; existing academic/subject deletion wire numbers
unchanged. Planned PrepareIdentityOperation, CommitIdentityOperation,
GetIdentityOperation, FailIdentityOperation; typed bounded target deltas and
nonsecret replies with operation/state/revision/UID/login/versions/resultCode.
Prepare carries canonicalhash/kind/expectedAuth+Academicversions+desiredgrant/admin/
identity changes, not caller-trusted arbitrary SQL. Directed clients and Auth server
validate method/action/service/actor boundaries; deadline≤3s outside DB transactions.

Public user controllers retain successful bodies; idempotency UUID/header +
pending202/operation link/status for interrupted work, typed409/503/deny. Explicit
compatibility needed for existing clients that did not previously supply a key.
No blind retry mutation after unknown result. Auth context/admission reads its
own current authority only. Presentation composition belongs BFF; optional
Auth→Academic service-only label read has no reverse Auth admission call/cycle.

Subject/semester stepup Auth validates password/OTP/purpose/actor/SID/binding/
credential generations/expiry/replay/digest/operation; Academic verifies local
preview/eligibility/consequences/delete. Proof cannot become permission on a
foreign object or survive invalid current state. Auth never reads Academic tables.

BotAuth MenuScope/CheckOwnAuthority: private sender/chat + expectedUID/binding/
rolesVersion/action, no bot-chosen role/group/date/teacher. Directed BotRead APIs
compose fresh authority with domain own-headman/own-teacher eligibility and exact
Moscow day/current lesson. Fixed method determines action. Recheck authority after
read; no storeduserJWT/fabricated HTTP RequestContext/broad machineJWT. Sharedproto
sole Auth writer, producer/consumer exact fields freeze together before generation.

## Report lifecycle

All academic report queue/idempotency/leases/retries/artifacts/expiry/cleanup/audit/
worker/metrics move to Academic. Single queue preserves global2active, attempts3,
backoff5/10s, lease90s, retention10min, user10/global256. Gateway retains fixed public
routes/specific legacy alias before generic route and admission; no job dispatcher.
Auth retains security capability only: jobID/kind/selector hash/session generations,
never READY/artifact/domainselector responsibility.

Academic builds exact immutable admitted report plan, commits claim, then invokes
bounded renderer/data-only producer. Do not copy current public MobileBFF exporter
dispatch mechanically: Academic→BFF→Academic callback/cycle must be removed.
Reuse Attendance/headman/teacher/registry renderers with explicit internal plan,
not generic worker microservice. No report bytes in Rabbit or bearer in job.
Current Auth authority + fresh local report eligibility rechecked at claim/complete/
download; final CAS publishes artifact. Recheck after bytes where applicable.
Lost202/navigation/reconnect/crash/retry resolve the same durable job, not replacement.
1..64weekStarts validation preserved; prove2x18week and a short distinct validperiod.

## Required changed-risk proof / implementation order

First preserve corrected old Event/Map fixtures and diagnose usercreate500 without
granting secret SELECT; no final U14 closure of sharedDB. Then concrete proto/schema
S3 review, Auth authority core + Academic coordinator in parallel frozen contracts,
separate DB fresh/minrights/repeat evidence, bot/domain consumers, job/stepup move,
actual PWA/TMA/current OpenAPI/gRPC/TS checks and ordinary final gates.

Source review must check authority ownership and every write entry point, CAS /
commit/aftercommit/outbox semantics, unknown acknowledgements/restart/revoke/stale /
last-full/headman aggregate, no recursive live calls/heldlockRPC, idempotent history,
pending credentials denied and final artifact authorization. Meaningful real DB/
RPC tests required; mocks and schema/source approval do not establish end-to-endPASS.
Old U15 sameDB evidence remains historical, new twoDB preservation separateU15.1.
B013/B014/B015 FROZEN: no equivalent forbidden caller/mutation/proof rerun.
