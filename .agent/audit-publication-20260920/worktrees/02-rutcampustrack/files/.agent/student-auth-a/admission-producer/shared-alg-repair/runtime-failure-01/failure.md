# Runtime failure 01

- Revision/baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2` plus the exact effective Stage 1 overlay verified immediately before execution (`0` bad; 15 present and 2 deleted; Auth13 manifest unchanged).
- Command: `.\gradlew.bat :services:shared:shared-security:test --tests ru.rutcampustrack.shared.security.InternalJwtValidatorTest --tests ru.rutcampustrack.shared.security.DualModeUserContextFilterTest --no-daemon --no-parallel --max-workers=1 --console=plain`.
- Environment: Windows PowerShell; approved escalation only for the existing Gradle dependency cache; Java 21; no Docker or product runtime.
- Exit: `1`; Gradle compiled main, test fixtures, and tests, then reported `BUILD FAILED in 48s`.
- Result: 22 tests, 16 passed, 6 failed, 0 errors, 0 skipped.
- Exact cause: all six failures share `IllegalArgumentException: Collection of Identifiable instances may not be null or empty` from `DefaultJwtParserBuilder$4.changed` while executing `InternalJwtValidator.java:64`. JJWT applies the collection change callback immediately on `.sig().clear()`, so the intermediate empty registry is rejected before `.add(RS256)` can run. Valid/expired tokens and both DualMode positives fail for this same builder-construction defect; the tuple assertion itself is not the cause.
- Correction: remove the unusable collection mutation. The already implemented strict raw JOSE parser rejects every token whose signed-input header does not contain exact `alg=RS256` before JJWT verification; JJWT then verifies that exact algorithm with the configured RSA public key. Keep all real-signature RS384/RS512/PS256 and duplicate-key negatives. Do not relax the raw header or signature checks.
- Heavy runtime: RELEASED after command completion.

Evidence copied immediately after the run:

- `junit/TEST-ru.rutcampustrack.shared.security.InternalJwtValidatorTest.xml`: SHA-256 `FC34268487605D3ADDC96D65B3D1CF21B174AB369211B67681A930F94B6D44D0`, 28791 bytes, byte-identical to the Gradle result; 14 tests, 4 failures.
- `junit/TEST-ru.rutcampustrack.shared.security.DualModeUserContextFilterTest.xml`: SHA-256 `7858D65E547A9D50D50384E3016ADC319AE470AD0AD821C91306FAF48A62A924`, 4684 bytes, byte-identical to the Gradle result; 8 tests, 2 failures.
