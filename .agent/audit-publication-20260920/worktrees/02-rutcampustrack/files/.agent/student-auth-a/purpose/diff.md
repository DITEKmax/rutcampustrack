# Scope diff manifest

Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

Changed exactly the four assigned product files:

1. `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java`
2. `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilter.java`
3. `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/JwtTokenPurposeTest.java`
4. `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilterPurposeTest.java`

Added only scoped evidence files under `.agent/student-auth-a/purpose/`:

- `packet.md`
- `evidence.md`
- `checks.json`
- `runtime-evidence.md`
- `diff.md`
- `summary.md` (written after this manifest)
- `manifest.json`
- `manifest-verification.json`
- `evidence/executed.log`
- `evidence/junit/TEST-ru.rutcampustrack.auth.service.JwtTokenPurposeTest.xml`
- `evidence/junit/TEST-ru.rutcampustrack.auth.config.JwtAuthenticationFilterPurposeTest.xml`

No Gateway, DTO/proto, migration, build/config/dependency, generated type,
shared status, or unrelated files were changed. No commit was created.

Changed source SHA-256 after verification:

- `JwtService.java`: `81429C4219EC00FD2207E11460C8AB03314787C7CDFB45EA28B3A18A72E1BD5D`
- `JwtAuthenticationFilter.java`: `A321630875C98B40DE20D30D8D220885C80AB35B871EE9207C2F10DA2EB864FA`
- `JwtTokenPurposeTest.java`: `61E4DB92A8376452787C8467AE61FC8CEE75DE539C226C04BD5D558646867855`
- `JwtAuthenticationFilterPurposeTest.java`: `F313EAF985473AC7205D21EB850014F4E4D6C762B9994F0285B361E25DB2F574`

Canonical source hashes and final JUnit source/copy hashes are recorded in
`manifest.json`; `manifest-verification.json` records the post-edit manifest
digest and mismatch result.
