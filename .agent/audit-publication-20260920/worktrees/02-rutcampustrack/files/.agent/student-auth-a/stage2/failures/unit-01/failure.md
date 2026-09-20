# Unit gate failure — fixture-only

- Command: `./gradlew.bat :services:auth-service:auth-app:test --tests ru.rutcampustrack.auth.arch.AuthApiContractTest --tests ru.rutcampustrack.auth.config.JwtAuthenticationFilterPurposeTest --tests ru.rutcampustrack.auth.controller.AuthSessionControllerTest --tests ru.rutcampustrack.auth.service.JwtTokenPurposeTest --tests ru.rutcampustrack.auth.session.SessionAdmissionServiceTest --no-daemon --no-parallel --max-workers=1 --console=plain`
- Gradle session: `33772`
- Exit code: `1` (`BUILD FAILED in 1m`)
- Aggregate result: `46` tests, `5` failed.
- Failing fixture XML suite: `tests=9`, `skipped=0`, `failures=5`, `errors=0`.
- Common failure: the test constructs `JwtAuthenticationFilter` with a bare `ObjectMapper`; XML records `InvalidDefinitionException` for `java.time.Instant` in `ErrorResponse.timestamp`.
- Cookie authority case: XML records the assertion at `JwtAuthenticationFilterPurposeTest.java:217`; the mapper exception is caught by the cookie logout fallback path, so `chainCalled` is `true`.
- Source XML SHA-256: `630CF953682CBD6867C16C15462AC142CFA363F53F4DC53730BD2850D682F09B`
- Preserved XML: `.agent/student-auth-a/stage2/failures/unit-01/junit/TEST-ru.rutcampustrack.auth.config.JwtAuthenticationFilterPurposeTest.xml`
- Preserved XML SHA-256: `630CF953682CBD6867C16C15462AC142CFA363F53F4DC53730BD2850D682F09B`
- No Gradle log is reproduced here; the XML above is the preserved raw failure evidence.
