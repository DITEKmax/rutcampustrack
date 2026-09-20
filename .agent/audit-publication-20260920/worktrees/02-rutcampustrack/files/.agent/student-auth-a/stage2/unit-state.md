# Stage2 unit evidence state

## Scope

Focused auth-app unit gate only: `AuthApiContractTest`,
`AuthSessionControllerTest`, `JwtTokenPurposeTest`,
`SessionAdmissionServiceTest`, and the retried `JwtAuthenticationFilterPurposeTest`.
Evidence is limited to `.agent/student-auth-a/stage2/`; no production or test
source files were changed.

## Criteria

All five preserved JUnit suites must report zero skipped, failures, and errors;
the combined test count is `46/46` (`3 + 6 + 15 + 13 + 9`). The earlier failed
filter XML remains an immutable failure record under `failures/unit-01/`.

## Evidence

The focused retry command was
`.\gradlew.bat :services:auth-service:auth-app:test --tests ru.rutcampustrack.auth.config.JwtAuthenticationFilterPurposeTest --no-daemon --no-parallel --max-workers=1 --console=plain`
(session `76426`). It exited `0`, reported
`BUILD SUCCESSFUL` in `30s` and `24 tasks` (`2 executed`, `22 up-to-date`),
and produced `JwtAuthenticationFilterPurposeTest` at `9/9`.

Preserved JUnit XML in `junit/unit/`:

| Suite | Tests | Skipped | Failures | Errors | SHA-256 |
| --- | ---: | ---: | ---: | ---: | --- |
| `AuthApiContractTest` | 3 | 0 | 0 | 0 | `B8ADE415B5DE4EFF4D63F10D51BD8FE165FF6735B20928373E00E2947C1E639` |
| `AuthSessionControllerTest` | 6 | 0 | 0 | 0 | `4A133804999E663E0209A503FE064B4A29F2ABEADA5031F87D581039472F1777` |
| `JwtTokenPurposeTest` | 15 | 0 | 0 | 0 | `69ED9F53E556446EE05C6359D87F6904ED0A57F0654C2FAE3B9EA368B190639F` |
| `SessionAdmissionServiceTest` | 13 | 0 | 0 | 0 | `7684D72E8D1E579C9805EF7404B1FF33293A61D0943F51FD9E33E68AB552EF0C` |
| `JwtAuthenticationFilterPurposeTest` | 9 | 0 | 0 | 0 | `7B6EB3415D8EB68D57013F90A0E57662BBA86CB9404B20C88A886D536AA15DFA` |

Combined result: `46/46`, all zero-result fields. The archived failed filter
XML remains SHA-256 `630CF953682CBD6867C16C15462AC142CFA363F53F4DC53730BD2850D682F09B`.

## Checks

| Check | Command/result | Exit |
| --- | --- | ---: |
| Retry evidence copy | Source and destination filter XML SHA-256 match; byte comparison `True`; counts `9/9` | 0 |
| JUnit evidence inventory | Five XML files parse; combined count `46`; all suite result fields are zero | 0 |

## Runtime evidence

Environment: Windows PowerShell on `DITEK-PK`, revision
`8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
The Gradle retry evidence above is the supplied runtime result for this gate;
no additional Gradle or product runtime command was run during evidence
preservation.

## Diff

Only this compact state file and the byte-identical retry XML at
`junit/unit/JwtAuthenticationFilterPurposeTest.xml` were added or updated by
this handoff. The four prior passing XML files and the failure archive were
preserved.

## Limitations

This state covers the focused unit gate only. It does not claim the PostgreSQL,
Boot, concurrency, or broader auth integration checks that root must run under
the frozen stage2 contract.
