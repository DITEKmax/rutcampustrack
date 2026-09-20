# B0 minimal A/E handoff - 2026-09-09

## 1. Goal

Release the accepted immutable V24 storage boundary to A's frozen exact-two `JdbcSessionAuthority` slice and state the exact dependency boundary visible to E. This is a partial B0 handoff, not whole-B0 acceptance.

## 2. Context and evidence

- Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
- Frozen B0 contract: `C9C4DC2C4BE2229AFF3CEDB27E0E43634329EBA5AC83CE42A889E34C33DAFC74`.
- B0 path manifest: `14219D88304A419E4FBDBB4FC62BBCCC190D34A3E78ED79EB6E331B6987E40EC`.
- A authority contract: `47FC36F57AFCEEC489B5C95C6C169C2CBA9F53FEED2287CF6946B1CB150D807E`.
- A exact-two packet: `527AE300E42008E727348F13FF7C776C550AEE39E9B8D6E1046191AE6ACADA01`.
- V24: `1D15418FA4869D1288B3FA25F688A237087294F360AEA6729F524DE2F5B67F90`.
- Academic fixture: `27FAE6FD189C1B13F7C192D4C7641FE6EB95BBEE0F5DA6E00BCD81B9F6FAA5F1`.
- Fresh Academic XML: `CF086E070724CC9E076EEFF33B88F0B7D410C23E8EF5557525FB9E3F03E20ECF`; 9 tests, 0 failures/errors/skipped.
- Persisted successful SQL7 evidence: `.agent/student-academic-b/sql7-runtime-pass-2026-09-09.md`, SHA256 `BA8511323AEF075BF358445FA58EAF2545B178140A2D3965265AF31176BA2241`.
- Separate fresh Sol V24 content review: PASS, no findings. It checked V24 against the A authority and exact-two packets and directly inspected the fresh XML. The earlier SQL7 review covered only the exact-three fixture correction.
- Persisted V24 content review: `.agent/student-academic-b/union/v24-content-review-pass-2026-09-09.md`, SHA256 `9EA67C5EEC9BE98FC49A21BCDA65DA95F128E9556DCF9BE27142CD6969321FF7`.

## 3. Relevant scope

A may consume immutable V24 in its separate worktree for exactly:

- new `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java`;
- new `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthorityIT.java`;
- A-owned evidence.

B has no active writer on V24. B's current writer owns only the other exact 25 B0 product paths. E receives a dependency notice only and owns no V24/proto/shared DTO/generated/Auth/Gateway paths.

## 4. Required behavior

V24 is authoritative for `users.roles_version`, `user_role_grants`, `auth_sessions`, and `account_security_events`. A must preserve lowercase SQL role/status with explicit uppercase domain mapping; unique `(user_id,role)`; composite own-grant/session FKs; trigger-owned roles-version increments; immutable grant owner; strict refresh JTI/absolute-expiry/revocation constraints; own-session events; append-only event history; exact legacy-fact conversion without Redis or inferred grants.

## 5. Constraints

V24 is frozen at the hash above. Any drift stops A and requires a recorded correction/review. A still needs sole-writer ownership, isolated PostgreSQL 16, its exact IT, stable manifest and fresh review. No production migration or deploy.

E's live login -> Today/Homework -> server integration remains gated by accepted Auth session API/DTO routes, A JDBC/runtime, C admission/Gateway, BFF/generated-client export, and server Homework/occurrence contracts. SQL7 alone does not release that integration.

## 6. Existing patterns

A implements existing `SessionStatePort` and `CredentialSessionTransactionPort` with Java 21, Spring JDBC and one transaction manager, locking user before session. E retains typed `StudentApi`, session-first bootstrap and owner/generation invalidation.

## 7. Acceptance criteria

The V24 handoff is accepted for A exact-two implementation. A's adapter remains open until its own race, rollback, ownership, revocation and credential-recheck PostgreSQL IT passes and receives independent review. E may continue independent frontend gates but cannot claim backend integration.

## 8. Verification

Do not rerun SQL7. A uses the exact command frozen in its packet against isolated `rct_student_auth`, recording command, exit, XML and final hashes. B continues exact25 proto/standalone contracts and requests one compile/protoc lease after source freeze.

## 9. Do not

Do not call whole B0 complete, edit V24, infer grants, restore Redis authority, hand-edit generated clients, let E consume absent DTO/proto as accepted, touch `proto/attendance.proto`, or mix Auth HTTP/JWT/Gateway/WS into A exact-two.
