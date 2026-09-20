# Correction-02 scope

Risk: S3. Baseline revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

Sole source ownership is exactly:

- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/InternalSessionAdmissionIT.java`
- evidence under `.agent/student-auth-a/admission-producer/correction-02-spring-wiring/`

The bounded correction makes the existing positive HTTP admission test create
an `AnnotationConfigApplicationContext`, register the already-initialized JWT,
JDBC authority and issuer-properties instances as singletons, register the
production `SessionAdmissionService` and
`InternalSessionAdmissionController` classes, refresh, and exercise the
Spring-created controller through the existing MockMvc flow. The separate
service-secret denial test, PostgreSQL setup, all PG assertions, timestamps,
old-route 404 and no-store checks remain in place.

No product code, Auth13 contract, shared-security source, other test,
build/config/lockfile, or external state is in scope. No child was created and
no file was staged, committed, reverted, reset, or reformatted. Gradle,
Docker, Testcontainers, PostgreSQL and service runtime commands are outside
this static correction gate.

The required correction delta is recorded from the root finding: the previous
positive path manually constructed both service and controller, so it did not
verify the production three-argument `@Autowired` constructor. No product,
contract, or scope redesign decision was needed.
