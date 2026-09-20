# Auth token DTO redaction — evidence

Captured: 2026-09-10. Base revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
Risk: S3 (auth/token representation). Writer: bounded implementation leaf,
then evidence-only continuation. Foreign dirty and untracked work in the shared
checkout was retained.

## Goal

Close the HIGH A-subset defect where generated record `toString()` exposed a
bearer value in exactly three auth token DTOs. Keep accessors, validation,
constructors, and JSON wire values unchanged.

## Context and original evidence

The original A preflight at
`C:\Users\maksd\.codex\worktrees\1456\rutcampustrack\.agent\student-auth-a\admission\preflight.md`
(lines 30–32) requires that bearer values are not logged or echoed and that B
token DTO text representations redact the bearer. The frozen preimages were
verified against the exact25 source manifest before the product correction.

## Relevant scope

Product source:

- `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthAdmissionRequest.java`
- `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthAdmissionResponse.java`
- `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/SelectActiveRoleResponse.java`

Focused semantic guard:

- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/contract/AuthTokenDtoRedactionTest.java`

Evidence scope:

- Three affected product lines in `file-sha256.md`.
- This file, `auth-token-redaction.md`.

## Required behavior and acceptance criteria

Each DTO now overrides `toString()` and renders its bearer component exactly as
`<redacted>`. No prefix, length, hash, or bearer substring is derived from the
value. Non-token response fields remain represented. Record accessors,
constructor validation, and JSON property names and token values remain
unchanged.

`AuthTokenDtoRedactionTest` uses three distinct synthetic sentinels only. For
each DTO it checks that `<redacted>` is present and the sentinel is absent from
`toString()`. It also checks the exact `accessToken` or `internalToken` JSON
property and sentinel value, then deserializes the JSON and checks the token
accessor. The role response is built with valid `CurrentSessionResponse`, role,
and password-policy data.

## Existing patterns and constraints

The implementation follows Java 21 record DTOs in `auth-api-contract`. The test
lives in `auth-app`, whose existing test dependencies provide JUnit 5,
AssertJ, Jackson, and Java time support. No shared exception handler, app
behavior, controller, wiring, logging, generated output, OpenAPI, TypeScript,
proto, SQL, or build file was changed. No real token or secret is present in
source or evidence.

## Checks and evidence

| Check | Command/evidence | Exit | Result |
| --- | --- | ---: | --- |
| Original preimage guard | Exact SHA256 guard for the three DTO paths against frozen manifest | 0 | All three preimages matched before source write |
| Source/test readback | Root product and focused-test readback after correction | 0 | Three redacted representations, three JSON wire assertions, and round-trip checks present |
| Scoped whitespace check | `git diff --check` for the three DTOs and `AuthTokenDtoRedactionTest.java` | 0 | Root readback PASS; no whitespace errors |
| Product post-hash readback | Guarded SHA256/byte readback | 0 | Values recorded below and in `file-sha256.md` |
| Manifest update | Guarded one-occurrence replacement of exactly three old manifest lines, atomic write, line readback | 0 | Only the three affected product lines changed |
| Focused Gradle test | `./gradlew.bat :services:auth-service:auth-app:test --tests ru.rutcampustrack.auth.contract.AuthTokenDtoRedactionTest` | PENDING | Deferred to root heavy/runtime lease; no runtime PASS claimed |

### Pre/post SHA256

| Path | Frozen preimage | Accepted postimage |
| --- | --- | --- |
| `AuthAdmissionRequest.java` | `1320792F7D30E132BDDF999D5174E81ABB77A28CDC25C29DFEBB61C22F1DDC11` (511 bytes) | `A874654D1E410590F43E59B11318F80C6C35A32AC9D0A32D940A53665A089452` (625 bytes) |
| `AuthAdmissionResponse.java` | `94A87C962446947FDB0B82ADC172D6ACC7C381976C2CBAA073052A729C987289` (2225 bytes) | `FA6559334DA8072F248033528040B4210A7792F58EAE8EE159E26F7801F802A7` (2780 bytes) |
| `SelectActiveRoleResponse.java` | `F0F26D52BD6752DB4BACF9E168BD7DDD156B2BF9B2C04C6B384A390BB02C4055` (868 bytes) | `613957796DCA67F271ECB876D45791CECB7719C5DDE07A96C7D06893553777F2` (1072 bytes) |
| `AuthTokenDtoRedactionTest.java` | absent | `B9F1CDFC799116D9D24B5BBFD62DD583FCA9E51E7269A3E4E1CDFF6722714649` (4668 bytes) |

## Runtime evidence

No product runtime, Gradle task, or external write was started in this source
turn. The focused JUnit test is pending root's separate lease; therefore its
runtime result is intentionally not marked PASS. No deploy, migration, data
operation, or secret operation occurred.

## Diff

The scoped product diff adds one redacting `toString()` to each of the three
listed DTOs. The focused test adds three observable redaction/wire guards. The
manifest changes exactly the three corresponding hash/byte lines. This evidence
file is the only new evidence artifact for this correction.

## Do not and limitations

No global reflection policy or broad DTO sweep was introduced. B1 behavior,
handler/controller changes, generated specifications, and runtime wiring remain
outside this contract. Independent post-correction review and the pending
focused Gradle/runtime check remain root responsibilities. No Terra escalation
gate was opened: the correction was bounded and no new defect or complexity
boundary was recorded.