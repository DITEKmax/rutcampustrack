# Concrete proposal for root freeze

One assigned Luna developer, one isolated worktree at13e5fd; these are proposed new files, not already implemented symbols.

- services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/grpc/{ServicePrincipal,DirectedServiceCredential,ServiceTokenCallCredentials,ServiceIdentityServerInterceptor}.java
- services/shared/shared-security/build.gradle.kts: pinned grpc-api and test transport dependencies only as required, no version upgrade.
- services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/{AssignmentCloseServiceIdentityInterceptor,AssignmentCloseServiceAuthConfiguration}.java
- services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/{AssignmentCloseServiceIdentityInterceptor,AssignmentCloseServiceAuthConfiguration}.java
- Shared corresponding tests: ServiceIdentityServerInterceptorTest, ServiceTokenCallCredentialsTest, ServiceIdentityTlsTransportTest; each app: AssignmentCloseServiceIdentityInterceptorTest. Test-only TLS material generated in owned test resources/temp scope; no ambient keys or credentials.

Shared primitive owns immutable principal, credential validation, secure transport gating and exact-method admission; adapters own their fixed local target/caller and method policy. Target-scoped CallCredentials is available for future protected stubs. No existing unrelated client calls gain service authorization or receive credentials.

Exact initial policy:

- rutcampustrack.schedule.ScheduleGrpcService/InstallAssignmentCloseCap accepts academic-service only.
- rutcampustrack.academic.AcademicGrpcService/GetPreparedAssignmentCloseOperation accepts schedule-service only.

These method IDs are reserved future identifiers exercised through test-only service descriptors, not new production proto declarations or domain handlers. All other legacy calls retain their existing admission contracts. Binding/history resource admission and a receipt RPC are excluded.

Proposed commands, NOT RUN; root owns heavy queue:

```text
./gradlew :services:shared:shared-security:test --tests '*ServiceIdentity*Test' --tests '*ServiceTokenCallCredentialsTest'
./gradlew :services:academic-service:academic-app:test --tests '*AssignmentCloseServiceIdentityInterceptorTest' --tests '*StudentHomeworkGrpcIdentityInterceptorTest'
./gradlew :services:schedule-service:schedule-app:test --tests '*AssignmentCloseServiceIdentityInterceptorTest'
```

Windows execution uses the checkout's gradlew.bat. A real local TLS positive plus plaintext, untrusted-peer and receiver-name mismatch negatives is mandatory; mocked SSL metadata/in-process alone is insufficient. Check both existing close routes still return their accepted typed409 using existing applicable tests selected by developer from original sources. Fresh Sol high full review after stable implementation and checks. Product runtime/deployment not claimed by this plan.
