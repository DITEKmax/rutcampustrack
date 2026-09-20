# Correction-02 compile infrastructure failure

## Reproduction

Exact command:

`\.\\gradlew.bat :services:auth-service:auth-app:compileJava :services:auth-service:auth-app:compileTestJava --no-daemon --no-parallel --max-workers=1 --console=plain`

Environment: Windows PowerShell, shared checkout. Exit code: `1`.

## Raw evidence

The Java sources compiled successfully, including `compileTestJava`; Gradle
then failed while writing its generated problems report:

```text
> Task :services:auth-service:auth-app:compileTestJava
Note: OtpServiceTest.java uses unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.

FAILURE: Build failed with an exception.

* What went wrong:
java.nio.file.FileAlreadyExistsException:
C:\Users\maksd\.gradle\.tmp\problems-report7712426666575707519.html ->
C:\Users\maksd\.codex\worktrees\1456\rutcampustrack\build\reports\problems\problems-report.html

BUILD FAILED in 19s
11 actionable tasks: 1 executed, 10 up-to-date
```

## Correction and verification

The target is generated under `build/`, not source or evidence. Remove only
that stale generated report and rerun the same command. The prior compile
source error is already recorded in `compile-01/failure.md` and corrected.

