# Repair-01 scoped diff

Assigned source paths:

- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/model/SessionSnapshot.java`: reject nonselectable `activeRole` after ownership and exact-snapshot checks.
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/port/SessionStatePort.java`: require every successful `RevokeResult` snapshot to be revoked, independent of `alreadyRevoked`.
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/ActiveRolePolicy.java`: close the forgeable `Evaluation` selection overload; public user/role/grants selection remains the ownership-validated path.
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/SessionLifecycleService.java`: Javadoc only, recording the trusted exact-secret replacement-hash obligation and pure-domain hashing boundary.
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/ActiveRolePolicyTest.java`: public foreign-grant selection negative.
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/PasswordPolicyTest.java`: symbol-only special category and low-surrogate negatives.
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/SessionLifecycleServiceTest.java`: suspended snapshot, live revoke result, logout/idempotency, expiry/revocation, and authority-failure no-mutation negatives.

No SQL, HTTP, DTO, schema, generated type, shared contract, Gateway, purpose4, UI, or old candidate manifest/XML was changed by this repair. The checkout also contains pre-existing work from other scopes; it remains preserved and is outside this repair diff.
