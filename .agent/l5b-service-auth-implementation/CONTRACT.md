# L5B service identity — Access recommendation, 2026-09-20

## Goal
S3 bounded dependency for Academic↔Schedule assignment closing. Root-freeze proposal: caller authentication and exact RPC admission, with no usable close operation or new authentication platform.

## Context/evidence
Accepted baseline 13e5fd1985b798bbb61bcc85969e6a74b1fc6837, read-only checkout C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-runtime-build-r3; HEAD verified. RULES.md SHA256 B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A. NEXT-L5B-START.md grants Access this planning directory only. Prior .agent/access-l5b-auth-contract/ remains historical evidence; policy A is now selected by root.

Canonical docs/architecture/reference-rutcampustrack-design/TRANSPORT.md:9 and :25–27 require REST/BFF→gRPC and boundary authorization; they do not choose a service credential wire. docs/security/SECURITY-AUDIT.md:209–216 describes shared-secret or mTLS interception, marked FIXED historically; it does not prove current caller-specific identity. Thus the mechanism below is an explicit engineering recommendation, not an already accepted credential standard.

## Relevant scope
One future assigned developer owns shared service-identity primitive and the Academic/Schedule gRPC adapters/tests in one isolated worktree. Root must freeze exact new files before dispatch. Lead owns this document only; no changes to product checkout, proto, migrations, global config or secret materials.

## Required behavior
Recommend two independent high-entropy opaque bearer credentials: Academic→Schedule and Schedule→Academic. Provision each only to its caller and target verifier. Separate `x-service-token` metadata; receiver maps a verified credential to the fixed principal `academic-service` or `schedule-service`. Neither claimed caller metadata nor payload actor IDs authenticate a service. Tokens must be distinct, generated from at least 32 random bytes, never built-in defaults or user JWTs. The existing global `grpc.auth.secret` is not one of these credentials.

Server admission uses local target identity plus full service/method name and an explicit caller allowlist. One credential cannot be accepted at the reverse audience. Constant-time comparison, bounded token representation, reject missing/blank/multiple/malformed credentials, no token logging. Invalid credentials yield UNAUTHENTICATED; authenticated disallowed caller/method yields PERMISSION_DENIED before handler/domain access. Missing verifier configuration cannot turn protection off. Put immutable verified principal in a separate gRPC Context key; never reinterpret InternalJwtClaims.

Client credentials attach only to the exact target stub and selected protected methods; discard caller-supplied service-token metadata before attaching one local credential. Never send them through a global interceptor to every downstream service. Existing deadline and typed failure behavior remains. The legacy shared-secret layer may remain as an additional check, but must never substitute for service identity; tests cover interceptor composition/order.

Confidentiality is mandatory for bearer credentials: authenticated TLS between these peers, with receiver-name verification. Root's concrete engineering choice: server requires the real transport Grpc.TRANSPORT_ATTR_SSL_SESSION (never metadata); target-scoped CallCredentials releases the token only when RequestInfo.getSecurityLevel() is PRIVACY_AND_INTEGRITY. This dependency must include real local TLS-positive and plaintext/untrusted-peer negative tests. Existing accepted runtime TLS readiness is not established by this plan. No production certificate provisioning/deployment is authorized. In-process tests prove admission only; they do not satisfy this transport gate.

Future method matrix (method names are proposal, not existing proto declarations):

| Target full method | Allowed caller | Domain authority required later |
|---|---|---|
| rutcampustrack.schedule.ScheduleGrpcService/InstallAssignmentCloseCap | academic-service | Exact persisted Academic PREPARED operation, op/hash/assignment tuple/D/target, fetched before Schedule locks |
| rutcampustrack.academic.AcademicGrpcService/GetPreparedAssignmentCloseOperation | schedule-service | Narrow exact operation lookup, no arbitrary assignment enumeration |
| A future close-receipt read, if root selects it | academic-service | Exact operation/hash/assignment only; no unrestricted status API |

The interceptor authenticates the caller, not the assignment. Root policy A: recovery may finish only the exact durably authorized PREPARED operation after actor-session revocation. New PREPARE still requires current user authorization. A service credential cannot create a new user action, expand D/scope, reopen a closed assignment or bypass exact ledger/receipt matching.

## Constraints
First slice implements admission/client primitives and endpoint adapters with meaningful in-process calls; it does not invent usable domain RPCs just for a demonstration. Future protected method names may be covered with test service descriptors. No current unrelated RPC is newly restricted without a caller inventory and explicit root selection. No optional bypass flag for protected methods. Both public close handlers retain typed409 and zero ledger/cap side effects. Binding/history admission remains a later resource-scoped contract, not blanket authorization granted by this credential.

Exact initial reserved allowlist is the two fully qualified methods in the table. Do not add an unspecified receipt method now; it remains a future protocol decision. The four declared Schedule binding/history methods are absent from ScheduleGrpcServiceImpl at13e5fd. Recommend excluding them from this first two-method rollout: their resource/user authorization is independent, and service identity alone must not authorize their bodies. No generated proto/schema change is needed for reserved admission or test-only service descriptors.

Proposed bounded product paths for root freeze: new Java classes under services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/grpc/ for immutable principal, directed credential validation, TLS-gated CallCredentials and reusable server admission; shared-security build.gradle.kts only if grpc-api dependency is required (same existing BOM/version); new service-specific identity interceptor/policy/configuration classes under each Academic and Schedule app grpc package; corresponding focused tests. Existing ScheduleGrpcClient/AcademicGrpcClient need changes only where a protected stub is actually constructed, never attach tokens to unrelated current methods. No source-wide/global client interceptor rewrite. File names become exact in root's implementation packet.

## Existing patterns
At baseline, Academic StudentHomeworkGrpcIdentityInterceptor.java:27–77 provides full-method selection and Contexts.interceptCall; its adjacent test exercises identity binding and denied handler access. Shared InternalJwtValidator.java:54–98 verifies user/session claims and is not a service validator. Academic ScheduleGrpcClient.java:32–72 is the target-specific stub/deadline seam. Both GrpcAuthInterceptor implementations are optional shared-secret checks, not service principals. Keep their existing unrelated behavior outside selected rollout scope.

## Acceptance criteria
Real client→server in-process calls prove both directed credentials bind the correct immutable principal. Missing/blank/malformed/duplicate/wrong/reverse credentials, userJWT-only, forged claimed principal, wrong target and forbidden full-method calls do not reach the handler. Test concurrent calls for Context isolation, replacement of supplied metadata, no credential forwarding to unrelated stubs/methods, missing configuration failclosed, and legacy/student interceptor composition. Exact token values never appear in errors/logs. Check public close remains disabled. Transport activation additionally requires a verified TLS-positive and wrong/untrusted-peer negative test; in-process PASS alone cannot claim that.

## Verification
Planning only: targeted original reads and exact baseline HEAD; runtime/build N/A, no accepted checks repeated. One allocated Luna max read-only gap scout may supply a bounded source correction; no product writer exists yet. Root freezes developer contract and transport activation boundary, then targeted tests and fresh Sol high full review on the stable implementation. No secret values read or produced.

Pinned gradle/libs.versions.toml:9 is grpc1.82.4; both app build.gradle.kts import its grpc BOM and use grpc-netty with starter3.1.0.RELEASE. No dependency upgrade is proposed. Root supplied official API references for CallCredentials.RequestInfo and Grpc; developer must compile/test against this pin. Real TLS tests use isolated generated test material only, never ambient credentials.

## Do not
No service JWT issuance endpoint, reuse of user signing keys/session claims, blanket retrofit of all RPCs, fake deployment readiness, half-enabled close, broad source audit, bells/calendar decisions, code/build/runtime, push/deploy or changes to shared orchestration files.
