# A session-domain implementation packet
2026-09-08. S3. Fresh Luna max developer, fork none; sole writer of assigned new domain files and evidence. Parent accepted exact scope. No other writer active when dispatched. Preserve accepted purpose4 and all others.

## 1. Goal
Implement the finite pure domain behind future PostgreSQL session/role authority, with explicit atomic port operations, role policies, refresh outcomes and password policy. Produce verified importable new files; do not pretend detached domain is wired server behavior.

## 2. Context/evidence
Baseline8002b9ea4356b10779c5bb9a6d99746d32d78ae2 plus purpose manifest .agent/student-auth-a/purpose/manifest.json (root verified4hash0mismatch). Read ../session/root-decision.md and ../session/shared-integration-contract.md, primary R6:144 and main wireframe108:113. Sol consultation is unmodified in ../session/consultation-result.md; source risks are not reproduced exploits. Read docs/agent-workflow.md, services/AGENTS.md, tests/AGENTS.md and main .agents/skills/rct-verification/SKILL.md.

## 3. Relevant scope
New files only below services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/:
session/model/{AuthRole,RoleStatus,RoleGrant,SessionState,SessionSnapshot,SessionRevokeReason,AuthMethod,SecurityEvent}.java
session/port/{SessionStatePort,CredentialSessionTransactionPort}.java
session/{ActiveRolePolicy,PasswordPolicy,SessionLifecycleService}.java
Tests under src/test/java/ru/rutcampustrack/auth/session/{ActiveRolePolicyTest,PasswordPolicyTest,SessionLifecycleServiceTest}.java (helpers nested in owned tests/classes, no surprise files).
Own .agent/student-auth-a/session-domain/** evidence, excluding frozen packet.md. No other product/docs/config changes.

## 4. Required behavior
AuthRole STUDENT,HEADMAN,TEACHER,ADMIN; RoleStatus ACTIVE,EXPELLED,GRADUATED,SUSPENDED,ARCHIVED. Actual grants only, immutable own user/grant identity; terminal3 selectable readOnly; suspended denies. Default selectable STUDENT else selectableTEACHER else no active grant. No fake role NONE or automatic elevated role. Selected role is per-session. Foreign/duplicate/inconsistent grants rejected.
Use Java records immutable copies, explicit Clock/now input and positive versions/IDs, UUID sid/JTIs. SessionState tracks schema fields needed for transition; no bearer/password plaintext stored or rendered.
SessionStatePort and CredentialSessionTransactionPort encode atomic business operations and typed results/failures. Service must not assemble independent get/set calls and claim CAS. A test double can model atomicity under a lock solely to exercise competing commands; real SQL proof stays OPEN.
Commands cover creation/snapshot, selectRole expected-version conflict, strict refresh current/previous/unknown results without grace, current logout, all logout, and changePassword transaction command. Fixed refresh expiry, live session guard, no implicit other-role permissions. If selected grant becomes unavailable, result must not silently switch to another grant.
PasswordPolicy:12 Unicode scalar values minimum, one Nd digit, one P/S special, <=72UTF8bytes, reject unpaired surrogate, no normalization/truncation. Current password is not constrained by new policy. Domain takes verified expected credential/hash and new hash through transaction port; no BCrypt/DB implementation here. Never put secrets in toString/log/tests output.
Return typed outcomes using nested enums/records/exceptions within reserved classes as needed. Canonical codes in integration contract. Account SecurityEvent carries only truthful typed event metadata; no raw credentials.
The pure domain does not expose HTTP endpoints or mint JWT. Bootstrap is null active grant with restricted authority, no new actual role. Account security operations allowed for terminal selected roles; product mutation capability is not.

## 5. Constraints
No edits to existing hot JwtService/AuthService/Auth filter or any JPA/controller/DTO/migration/proto/Gateway/shared-security/Redis/build/config/lockfile. No children, no main edits, no network/secrets/realOTP/TMA. No commit. You are not alone; preserve others. Runtime18100-18119 reserved but no launch without lease.

## 6. Existing patterns
Java21, JUnit5/AssertJ/Mockito installed Gradle project. Existing AuthUser enums are legacy3 and deliberately untouched; new domain role/status stands apart until explicit adapter mapping. Ports are backend domain boundaries, not a fake API or alternate global configuration.

## 7. Acceptance criteria
Unit negatives: foreign grant, duplicate/invalidgrant, suspended denial, terminal readonly, student precedence including terminal, teacher fallback, admin/headman-only neutral, same-role idempotency/conflict, stale/expired/revoked session, concurrent refresh one winner+loser409, olderunknown401, fixedexpiry, revokeall includescurrent, wrong expected credential fails without partial session revoke, authority failure not success. Password boundary vectors cover BMP/supplementary,12count,72/73bytes,digitNd,P/S,space/combining exclusions,unpaired surrogate and no normalization.
No tests merely mirroring getters or wording. Exact manifest includes path/SHA256/bytes and checks. Integration gaps explicit.

## 8. Verification
Prepare exact focused Gradle command but DO NOT run until parent lease delivered. Parent B/C queue owns current heavy/light slot. Independent javac/read-only checks allowed only if no competing build resources; do not launch ad-hoc alternative hidden runner. Record commands/exits/revision/env, copied final XML. After stable source tests fresh Sol high review. No SQL/runtime/scanner/full profile PASS from this domain.
Send ready-for-tests and finite port signatures promptly so integration contract can be updated only by root.

## 9. Do not
No implementation of deferred adapters or other-role screens; no grants inferred from final examples; no persistence authority in memory/Redis; no per-operation get/write race illusion; no fabricated atomic transaction proof; no expanding test suite indefinitely after required checks pass.

