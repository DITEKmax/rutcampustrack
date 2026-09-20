# P1 Gradle success evidence

Recorded at revision `d3c31acb8cce53791a4981e5858a37d44fdc9a0e` after the
bounded test-only correction.

- Start: `2026-09-08T12:02:45.2479605+03:00`.
- End: `2026-09-08T12:03:20.0126341+03:00`.
- Session: `30926`.
- Working directory: `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-notification-authority`.
- Environment: Windows PowerShell/PTY; Gradle wrapper; `--no-parallel`;
  `--max-workers=1`; `--console=plain`.
- Exact command (exit code `0`, run once after the correction):
  `./gradlew.bat :services:attendance-service:attendance-app:test --tests ru.rutcampustrack.attendance.grpc.AttendanceRequestBotGrpcServiceTest --tests ru.rutcampustrack.attendance.studentrequest.StudentRequestServiceAuthorizationTest --tests ru.rutcampustrack.attendance.grpc.StudentRequestGrpcErrorsTest --no-parallel --max-workers=1 --console=plain`
- Result: `BUILD SUCCESSFUL in 21s`; `37 actionable tasks: 14 executed, 23 up-to-date`.
- XML count: `3`; all selected test suites passed:
  - `AttendanceRequestBotGrpcServiceTest.xml`: 8 tests, 0 failures, 0 errors,
    SHA-256 `A0EAD0DB1C26171D545909AB1E375F4045B30533DF4EDE113A8F1F414869ACB1`.
  - `StudentRequestGrpcErrorsTest.xml`: 2 tests, 0 failures, 0 errors,
    SHA-256 `E1DB3417043262000B0633102F9F1B2D52F76A77CFB24D2A668CC912AA5C3449`.
  - `StudentRequestServiceAuthorizationTest.xml`: 10 tests, 0 failures,
    0 errors, SHA-256
    `46E39E3B3A3EAC88F100E0C1F965F558CCCC9F6441D1D09A966603EDCDB09B3C`.
- Post-run sourcefreeze: `current-path-manifest.txt` verification exit `0`,
  `CURRENT_MANIFEST_OK checked=14`; all scoped product/test and accepted-repair
  hashes remained at their recorded values.
