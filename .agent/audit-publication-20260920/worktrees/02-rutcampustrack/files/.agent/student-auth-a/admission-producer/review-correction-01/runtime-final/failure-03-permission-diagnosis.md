# Runtime failure 03 and permission diagnosis

Status: `ENVIRONMENT DIAGNOSED / HEAVY RELEASE`.

Cleaning and rebuilding both `shared-observability` and `shared-security`
reproduced the missing `BusinessMetrics` error, disproving the stale-output-only
hypothesis. Gradle's `compileClasspath` report includes the rebuilt
`shared-observability/build/classes/java/main` directory and all expected
external jars.

A minimal `javac` probe in this evidence directory reproduced the failure in
the same sandbox. With the upstream class directory and its Micrometer
dependency on the classpath, javac emitted the decisive cause:

`java.nio.file.AccessDeniedException` for the Micrometer jar under
`C:\Users\maksd\.gradle\caches\modules-2\...`.

The Gradle dependency cache is outside the writable/readable workspace surface
available to the sandboxed Java compiler. This explains both generations of
unresolved symbols. Source hashes remained frozen. The next exact test command
must run through the standard escalated Gradle execution path so javac can read
its existing dependency cache; no product edit or broader clean is justified.
