# Focused shared-security runtime result

- Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2` plus effective Stage 1 overlay.
- Source guard: `0` bad across 15 present Stage 1 files and 2 intended deletions; Auth13 manifest unchanged.
- Corrected validator: SHA-256 `E8633DE82330BCCB8A9805E37351F5E9F439A27FD1C95524B760592B614E3FF5`, 23230 bytes.
- Command: `.\gradlew.bat :services:shared:shared-security:test --tests ru.rutcampustrack.shared.security.InternalJwtValidatorTest --tests ru.rutcampustrack.shared.security.DualModeUserContextFilterTest --no-daemon --no-parallel --max-workers=1 --console=plain`.
- Started UTC: `2026-09-10T21:09:58.1731391Z`.
- Environment: Windows PowerShell, Java 21, approved escalation for the existing Gradle dependency cache; no Docker or product runtime.
- Exit code: `0`; `BUILD SUCCESSFUL in 43s`; 9 tasks, 4 executed and 5 up-to-date.
- Tests: 22 passed, 0 failures, 0 errors, 0 skipped.
- Heavy runtime: RELEASED after completion.

Byte-identical JUnit evidence:

- `junit/TEST-ru.rutcampustrack.shared.security.InternalJwtValidatorTest.xml`: 14/0/0/0, SHA-256 `2BED126604E68670BEDD93153E8E7C9D75D850EC66039C76EF9AA68BDF997665`, 2408 bytes.
- `junit/TEST-ru.rutcampustrack.shared.security.DualModeUserContextFilterTest.xml`: 8/0/0/0, SHA-256 `F15611D001CA90C93A1765A500EF873220CBFAD8A9FCD2B416D8FD2232775F31`, 2561 bytes.
