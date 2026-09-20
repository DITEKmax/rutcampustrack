# Gateway C null `group_id` correction r2

Date: 2026-09-10 (Europe/Moscow). Environment: Windows 11 amd64, PowerShell,
shared worktree `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack`.
Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. Risk: S3. The current
leaf is the sole writer for the four Gateway JWT source/test paths below;
foreign dirty work remains preserved.

## Goal

Make optional `group_id` admission strict for signed access and internal
compact JWTs while keeping an absent member valid and keeping
`AuthAdmissionResponse.groupId == null` valid.

## Context and recorded failure

The approved focused selector executed 81 tests and exited `1` after about
`1m9s`. The two relevant XML failures were preserved before this correction.
`JwtAuthenticationFilterTest` had 53 tests with one failure: a signed raw JSON
`group_id:null` was accepted because JJWT may omit a raw null from `Claims`,
then the unstubbed mock chain returned null. `InternalJwtIssuerFilterTest` had
8 tests with one `NotAMockException`: `verify()` targeted a lambda at line 69;
the captured forwarded exchange already proves invocation. The other 20 tests
in the selector were green in that run per the root packet.

The failure reproduction command was:

```text
.\gradlew.bat :services\api-gateway:test --tests ru.rutcampustrack.gateway.filter.JwtAuthenticationFilterTest --tests ru.rutcampustrack.gateway.security.InternalJwtIssuerClientTest --tests ru.rutcampustrack.gateway.security.InternalJwtIssuerFilterTest --tests ru.rutcampustrack.gateway.security.InternalIssuerClientPropertiesTest --tests ru.rutcampustrack.gateway.ratelimit.RedisRateLimiterConfigTest --no-daemon --no-parallel --max-workers=1 --console=plain --continue
```

The source XML files were copied before any later edit with these two commands;
both copy operations completed without error (exit `0`):

```text
Copy-Item -LiteralPath services/api-gateway/build/test-results/test/TEST-ru.rutcampustrack.gateway.filter.JwtAuthenticationFilterTest.xml -Destination .agent/student-gateway-c/admission/failure-81-r2/TEST-ru.rutcampustrack.gateway.filter.JwtAuthenticationFilterTest.xml
Copy-Item -LiteralPath services/api-gateway/build/test-results/test/TEST-ru.rutcampustrack.gateway.security.InternalJwtIssuerFilterTest.xml -Destination .agent/student-gateway-c/admission/failure-81-r2/TEST-ru.rutcampustrack.gateway.security.InternalJwtIssuerFilterTest.xml
```

| Preserved source / copy | SHA-256 (source = copy) | Bytes | Lines | Source FS timestamp UTC | XML timestamp | Tests / failures / errors / skipped |
| --- | --- | ---: | ---: | --- | --- | --- |
| `TEST-ru.rutcampustrack.gateway.filter.JwtAuthenticationFilterTest.xml` | `E83C5BB8468768E5B5B68750DB9A01FA56651D6584F431A6D714D982C2263B6E` | 12997 | 110 | `2026-09-10T18:43:54.5129265Z` | `2026-09-10T18:43:38` | 53 / 1 / 0 / 0 |
| `TEST-ru.rutcampustrack.gateway.security.InternalJwtIssuerFilterTest.xml` | `FB6FFCDBEE7A861C5A3DA229005372904F8659CE15B16EABDB70CF4B9C016FE7` | 2961 | 27 | `2026-09-10T18:43:54.5178663Z` | `2026-09-10T18:43:50` | 8 / 1 / 0 / 0 |

The source/copy integrity and metadata readback command exited `0`; its output
is summarized in the table above. No test report was regenerated after the
copy.

## Relevant scope

Only these product/test paths were edited for this correction:

- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java`
- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilter.java`
- `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java`
- `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilterTest.java`

Correction evidence is under `.agent/student-gateway-c/admission/**`. The
access test already contained the required raw-null regression and its bytes
remain unchanged; its four-path hash is still recorded below. No other product
path was written by this correction.

## Required behavior

After signature verification, each optional group validator checks the original
payload with `has/get`: an absent `group_id` returns null; a present member must
be textual and pass the existing canonical positive Java-Long validator. JSON
null, numbers, booleans, arrays, objects, zero, signs, leading zeroes, and
overflow fail closed. Access and internal filters retain their existing
issuer, audience, signature, time, identity, and flag checks. Response DTO
`groupId` null handling is unchanged.

## Constraints and existing patterns

The access filter now reads one `JsonNode originalPayload` per access/bootstrap
validation and passes it to the time and optional-group checks. The internal
filter reuses its already-read payload. `Claims.containsKey` is not used to
decide optional presence. The internal tests use the existing raw RS256 signer
for explicit JSON null and absent JSON cases. The valid forwarding test uses
the existing `AtomicReference<ServerWebExchange>` capture and asserts it is
non-null instead of verifying a lambda with Mockito.

No routes, policy, Auth/shared contract, DTO, build, config, runtime, Docker,
network, secret, or unrelated test file was changed.

## Acceptance criteria and diff

The access raw-null parameterized case remains present. Internal coverage now
has signed raw JSON absent-positive and explicit-null-negative cases. The
internal valid path's observable forwarded headers and authenticated user
attribute assertions remain intact. The implementation diff is limited to the
payload-aware optional validators and the single-payload access time refactor;
the test diff adds the two internal group cases and removes the invalid lambda
verification.

The canonical manifest remains schema `rct.gateway.changed-paths.v2` with
exactly 15 stage paths. Four Gateway JWT rows are the affected correction
scope; the access test row retains its existing hash because its raw-null
regression was already present.

| Current scoped path | Current SHA-256 |
| --- | --- |
| `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java` | `2066A1116DE92F99EA096718385657615C2750F68E1641587DB9CB937B71D9B7` |
| `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilter.java` | `06CDC1A279E5CA1C0855291003F9EC6DDA68E95F59114C025E3943AC205FBB8E` |
| `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java` | `9C063B1B2C6052C3FA17FBDD8A20F329EC6F87CFF24D428AF06B08C786634383` |
| `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilterTest.java` | `82705864F3FC3B48B1D525338781E05381E93B53172003ED3DE777DEB1DA1802` |

All 15 current hashes match the canonical manifest; the complete 15-path
manifest is `canonical-changed-paths.json`, and the before/after table is
`changed-path-hashes.md`.

## Verification and checks

All commands below were run in the stated PowerShell environment after the
correction, except the explicitly preserved failure selector above. No Gradle
command was rerun.

| Check | Exit | Evidence |
| --- | ---: | --- |
| `Get-Content -Raw .agent/student-gateway-c/admission/canonical-changed-paths.json \| ConvertFrom-Json` plus exact 15-entry/current SHA-256 comparison | 0 | `schema=rct.gateway.changed-paths.v2; stagePaths=15; hashes=15/15` |
| `git diff --check -- <four product/test paths> .agent/student-gateway-c/admission` | 0 | No whitespace errors; Git emitted only existing LF/CRLF conversion notices. |
| Static readback for `originalPayload.has(name)`, payload-aware optional calls, and `JsonNode` time validator | 0 | Both filters contain the required calls/signatures. |
| Forbidden presence guard for `claims.containsKey` in both filters | 0 | No Claims presence lookup remains. |
| Static test readback for access raw-null, internal absent-positive, internal explicit-null-negative, and forwarded capture | 0 | All required cases/assertions present. |
| Lambda verification guard over `InternalJwtIssuerFilterTest` | 0 | No `verify(chain)` remains; capture assertion is used. |
| Preserved XML source/copy hash, byte, line, and JUnit metadata comparison | 0 | Source and copies match; metadata is recorded above. |

## Runtime evidence and limitations

Runtime is `N/A` for this leaf: the requested correction is a signed compact
JWT unit/filter contract, and the frozen packet prohibits Gradle/runtime,
Docker, and network execution. The preserved XML is failure evidence only and
does not establish a post-correction test PASS. Root owns the released focused
recheck, runtime evidence, and any independent review; this leaf makes no unit,
runtime, or review PASS claim.

The current shared worktree contains broad foreign and previously accepted
Gateway changes. They were not reverted or rewritten. The canonical 15-path
hash check covers the frozen Gateway stage, while the four current hashes above
identify this correction handoff. Any product or contract decision outside
this strict optional-wire validation remains a root decision.
