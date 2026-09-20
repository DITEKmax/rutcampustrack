# L5B service identity — source resolution and engineering proposal

Date: 2026-09-20. Risk S3. Status: static handoff for root freeze, not implementation or security readiness.

## Goal

Resolve the existing caller identity, RPC scope and revocation patterns needed by
accepted L5B PREPARE/install/FINALIZE. No new authentication system is authorized.

## Context/evidence

`sources.json` pins 13 directly relevant source files at integration revision
`426a15b6b42e816deaa3ca5c50437e0964aaf85e`, plus accepted v3 proposal and A2
contract. Academic authority `f59b3b9951c971bde42265ae67df8754dc594a58` is a
separate accepted baseline; combining it with Schedule remains an integration
gate. No claim about its current worktree follows from this source map.

The accepted v3 is `.agent/lessons-l5b-design/PROPOSAL.md`, SHA256
`836D567E86F868102819DACD283ECFFA0AC068B308C5A5643BC44A3C7109E0A0`.
This replaces the scout's historical v2 citation for this handoff. Root's
`LESSONS-L5B-DESIGN-ACCEPTED-V3.md` is acceptance evidence, not activation.

## Relevant scope

Only this new `.agent/access-l5b-auth-contract/` documentation directory.
Scout `l5b_service_auth_scout`, fresh Luna max/fork none, completed and released.
The lead independently opened Schedule server/client interceptors, shared JWT
validation, Academic method admission, both service client wrappers, Schedule
proto, Auth session revocation/live-state code and accepted v3 sections 4.2/5.

## Required behavior: source facts

| Boundary / symbol | Observed fact | Consequence for L5B |
|---|---|---|
| Schedule and Academic `GrpcAuthInterceptor` | `x-grpc-secret` checked only when configured; no distinct authenticated caller identity established here | Shared-secret possession or an absent configuration cannot satisfy caller-specific authorization |
| `GrpcSecretClientInterceptor` in both services | Attaches the same configured secret when nonblank | Client service name/`@GrpcClient` target is routing, not proof of caller identity |
| `InternalJwtValidator.validate` | Signature, issuer, audience, token purpose/time, user `sub`, `sid`, `sv`, `rv`, role/status and optional group checked | Existing token is user/session authority, not service identity; this validator alone performs no durable session-revocation lookup |
| Academic `StudentHomeworkGrpcIdentityInterceptor` | Signed user identity required for six Homework/projection/map methods | Reuse the method-admission/context pattern; do not silently treat this user token as an Academic/Schedule service credential |
| Academic `ScheduleGrpcClient` | Current wrappers call ResolveLesson and CountSubjectReferences with deadlines | L5B cap/binding calls are not established by these wrappers |
| Schedule `AcademicGrpcClient` | Current wrappers call GetGroup, GetActiveSemester and IsHeadman | Prepared-operation verification requires an additional narrow contract |
| `proto/schedule.proto` and Schedule handler | History and three binding RPCs declared; inspected implementation exposes six existing read/reference handlers | Declaration is not implementation, authenticated scope or tested authorization |
| `JdbcSessionAuthority` / `SessionLifecycleService` | Durable session revocation, session/roles versions, live-session checks and security events | Reuse actor-authority/revocation semantics where applicable; they do not grant service identity or decide recovery policy after PREPARE |

These statements describe inspected boundaries, not a repository-wide proof of
absence. No credential values, environment files or private keys were inspected.

## Constraints and minimal caller/RPC matrix proposal

The principal labels below are proposed authenticated identities, not existing
claims. Missing/invalid service identity denies before domain reads/writes;
authenticated but wrong caller or resource scope also denies. A body `actorId`,
UUID, request key, `@GrpcClient` name or shared secret is not authorization.

| Authenticated caller → owner | RPC / operation | Required authority and scope | Replay / status |
|---|---|---|---|
| Academic → Schedule | **Proposed** InstallAssignmentCloseCap | Academic-only service admission; fetch Academic-owned PREPARED operation before local locks; verify target service, operation ID, exact assignment tuple, D and payload hash | Same operation/hash returns stored receipt; changed payload conflicts; request actor/tuple never replaces persisted authority |
| Schedule → Academic | **Proposed** GetPreparedAssignmentCloseOperation | Schedule-only narrow read; exact operation/hash/assignment and intended target; no broad actor/session export | Return immutable prepared authority plus explicit operation state; unknown/mismatched operation fails closed |
| Academic → Schedule | ReserveHomeworkBinding | Proposed Academic-only writer scope; persisted actor, allowed group/occurrence, operation key/hash and publication transition | Repeated accepted key preserves identity; foreign group/binding denied; transport failure never implies success |
| Academic → Schedule | ConfirmHomeworkBinding | Same caller/scope plus exact persisted binding/homework identity and current transition/revision checks | Never undo transfer pointer; ARCHIVED terminal; keep accepted v3 parent→binding locks/CAS protocol |
| Academic → Schedule | GetHomeworkBindings | Explicit read scope; each occurrence/group authorized for the requesting user/workflow | Revalidate current pointer/state coherently; batch never leaks foreign rows |
| Academic → Schedule | GetOccurrenceHistory | Separate retained-history permission; exact occurrence/group and actor authority | Historical identity is not current teacher permission; retention does not bypass authorization |
| Authorized user → Academic; internal Academic recovery | **Proposed** close status/replay / GetCloseOperation | User reauthorized for the operation's scope; recovery has a distinct approved internal execution context bound to stored operation | Same operation/hash only; return stored outcome plus current resource; do not create a new close or reopen cap |

The matrix is a minimal engineering proposal for these two services. Additional
read callers require an explicit allowlist entry justified by their actual
workflow; they must not inherit mutation rights. The exact service credential
mechanism, service-principal validation/propagation and per-RPC enforcement are
not established by the inspected implementation. Root must freeze them using
the project's accepted transport/security patterns before implementation.

## Existing patterns and revocation decision

Accepted v3 requires PREPARE to authorize the REST actor and exact path relation,
then persist immutable actor/tuple/date/hash before any RPC. It forbids RPC under
database locks, treats transport failure as pending/unknown, and never reopens a
pending cap. Accepted A2 requires signed user identity plus authoritative rereads
of current grants/status/version; signature validation is not sufficient alone.

| Moment | Fixed invariant | Root engineering freeze still needed |
|---|---|---|
| Before PREPARE / a new operation | Require current valid actor authority; stale/revoked/suspended authority cannot initiate writes | Exact reuse of existing authority lookup for the allowed actor roles |
| After PREPARE, before install/finalize | Keep immutable operation provenance; replays bind operation/hash; do not erase history or implicitly cancel/reopen a cap | Choose A: authenticated internal recovery completes already accepted operation, or B: hold strict cap/pending operation for operator resolution |
| Service credential unavailable/revoked | No unsigned/shared-secret fallback; no new domain action without authenticated permitted caller | Rotation/retry/recovery operational contract; restoring credentials does not rewrite operation actor |
| After COMMITTED | Revocation cannot undo the committed close or immutable receipt | Which currently authorized roles may inspect retained status/history |

Recommendation for root evaluation: A supports deterministic completion after a
lost response, provided the persisted PREPARE authorization is sufficient and
recovery has a narrowly scoped authenticated principal. B is the conservative
hold when policy requires current actor authority at each step. Neither option
is declared an existing owner decision. Both preserve strict cap/no-reopen and
history. This is an engineering freeze item, not a question sent to the user.

## Acceptance criteria

This handoff provides exact pinned originals and a minimal proposed matrix.
Before product work, root must freeze service credential/principal enforcement,
the caller/RPC/resource allowlist, post-PREPARE recovery policy and the combined
source baseline. Existing user JWTs must retain their user/session meaning.
No method becomes implemented or authorized merely through this document.

## Verification

Read-only file/revision/SHA256 inspection; runtime and tests N/A. Source map
contains full hashes rather than the scout's abbreviated hashes. The scout
reported 15 source files plus policy-document references; the frozen handoff
retains the 13 integration source files and two directly used policy originals.
The historical f59-specific scout claims are not used as new independently
verified implementation evidence here. No extra scout or runtime was launched.

Future implementation checks must cover missing identity, wrong service/method,
foreign group/occurrence/binding, payload-changing replay, revoked actor before
PREPARE, selected post-PREPARE policy, dependency failure with cap retained,
retained-history authorization and batch non-disclosure. They are requirements,
not executed tests.

## Do not

No product/auth/config changes, secrets, network scans, runtime, source promotion
or new auth architecture. No fail-open security and no readiness claim. Main
owns shared source registry/status and next policy/implementation allocation.
