# SQL7 compile-fix evidence

- Scope: exact1 outer `Connection.close()` compile correction in
  `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/migration/StudentFoundationMigrationIT.java`.
- Baseline revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
- Required preimage/source guard SHA256: `A85C7D053186F5D75F2CC765F2A708E4FCF9C2520A78F807A90F28C6A03376D8` (matched).
- Correction/source SHA256: `27FAE6FD189C1B13F7C192D4C7641FE6EB95BBEE0F5DA6E00BCD81B9F6FAA5F1`.

## Failed check evidence

- Exact command: `.\gradlew.bat :services\academic-service\academic-app\integrationTest --tests ru.rutcampustrack.academic.migration.StudentFoundationMigrationIT --no-daemon --no-parallel --max-workers=1 --console=plain --continue`
- Exit code: `1`; `BUILD FAILED` after `1m27s`, `36 tasks`.
- Compile failure: `StudentFoundationMigrationIT.java:424` in `compileTestJava`, `unreported exception SQLException; must be caught or declared to be thrown`, caret at `try (connection)`.
- Failure was before Testcontainers and before new XML/runtime startup; no product runtime evidence was produced.
- Schedule was not run. Java/resources were released. No rerun was performed by this leaf.

## Correction and verification

- Outer close failure now throws `AssertionError("Race connection close failed", closeFailure)`, preserving the original `SQLException` cause.
- Focused source inspection found the correction at lines 454-455; nested SQL, interrupt, rollback, classifier, latches and race assertions remain untouched.
- Product compile/runtime is released to root under the packet constraints; root owns the next focused compile lease.
- NEW GO required: root should run the focused compile check after accepting this handoff.

## Limits

- No Gradle, Docker, runtime, manual javac, dependency, Schedule, commit, stage, reset, clean, delete, or unrelated product operation was performed.
