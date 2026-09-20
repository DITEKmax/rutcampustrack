# Correction-01 scope

Risk: S3. Baseline revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

This bounded repair owns exactly two existing producer test fixtures:

- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/JwtTokenPurposeTest.java`
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/SessionAdmissionServiceTest.java`

The first fixture receives live whole-second issue times in the two positive
signed-token cases. The second fixture aligns the default ACTIVE claims with
the snapshot group and explicitly keeps the terminal read-only fixture
group-less. No product code, Auth13 contract, shared-security source, other
test, build/config/lockfile, or external state is in scope. Evidence is owned
only under this `correction-01/` directory.

The correction is based on the recorded runtime failure manifest
`../runtime-failure-01/manifest.json` (SHA256
`21E86F90299A5348BC0199EEDAA6FC8EA5CED568AF8C58E6E88649FFB0369916`) and the
17-entry source guard `../source-hashes.json` (SHA256
`DF8896D704B015E75C7266BCC0E6AAB1F56BDCC98D03276E75878C1342DCDA98`). No
product, contract, or scope decision was needed; the required delta is fixture
data/time only.

No child was created. No file was staged, committed, reverted, reset, or
reformatted. Gradle, Docker, Testcontainers, PostgreSQL, and service runtime
commands are outside this leaf's correction gate.
