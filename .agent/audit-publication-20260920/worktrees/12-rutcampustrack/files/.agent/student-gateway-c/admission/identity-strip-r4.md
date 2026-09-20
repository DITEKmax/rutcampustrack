# Gateway identity stripping repair r4

Date: 2026-09-10 (Europe/Moscow). Revision under test:
`8002b9ea4356b10779c5bb9a6d99746d32d78ae2` plus the uncommitted working tree.
Risk: S3 authorization boundary. This is a bounded repair; no Terra escalation
was needed.

## Scope

The fresh Sol medium finding from `/root/gateway_admission_review_r3` identified
the two owned sanitizer predicates as incomplete. The only code/test scope is:

- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java`
- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilter.java`
- `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java`
- `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilterTest.java`
- `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerIT.java`

The existing canonical v2 manifest is status evidence. Its five corresponding
stage-path SHA rows were refreshed after the repair. Existing foreign and
accepted worktree changes, old evidence, and copied PASS XMLs were preserved.

## Criteria

1. Both filters normalize header names with the existing `Locale.ROOT` helper
   and remove exact case-insensitive `x-group-id` and `x-is-headman` names beside
   the existing removals.
2. The Jwt unit coverage injects mixed-case forged headers and proves absence on
   a protected request and an OPTIONS request before their branch dispatch.
3. The InternalJwtIssuer unit coverage injects both mixed-case forged headers,
   proves both are absent after valid admission, preserves the response
   `X-Internal-Token`, and keeps the authenticated user attribute `42`.
4. The existing check-in IT sends both forged headers and WireMock requires the
   downstream request to omit both while retaining the trusted internal token.
5. The canonical manifest remains v2 with exactly 15 stage paths and only the
   five corresponding rows receive refreshed SHA-256 values.

## Recorded finding and reproduction

Before this repair, `JwtAuthenticationFilter.isClientIdentityHeader` and
`InternalJwtIssuerFilter.isIdentityHeader` lowercased names but stopped after
`x-user-*`, `x-internal-token`, and `x-login`. Therefore a client header named
`x-gRoUp-Id` or `X-iS-HeAdMaN` normalized to an unrecognized exact name and could
remain in the request. The pre-repair unit evidence (`83/0`) and IT evidence
(`6/0`) did not inject these forged mixed-case names, so they do not prove this
finding closed.

## Correction and bounded diff

Each owned predicate now adds only:

```java
|| normalized.equals("x-group-id")
|| normalized.equals("x-is-headman");
```

The five scoped tests add mixed-case forged headers and observable absence
assertions. The internal test retains its trusted response token and
authenticated-user assertion. The IT retains the existing check-in route and
adds two WireMock `withoutHeader` matchers. No admission call, JWT claim,
role/status/group semantic, limiter, route, config, build, Auth13, or shared
code was changed.

## Checks

All commands below were run in the shared Windows worktree at the revision
above. No Gradle, Docker, product runtime, or network command was run.

| Criterion | Command | Exit | Evidence |
| --- | --- | ---: | --- |
| Exact source matchers | `rg -n -F 'normalized.equals("x-group-id")' services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java services/api-gateway/src/main/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilter.java`; same readback for `x-is-headman` | 0 | `x-group-id` at Jwt `:415`, Internal `:418`; `x-is-headman` at Jwt `:416`, Internal `:419` |
| Mixed-case fixtures | `rg -n -F 'x-gRoUp-Id' ...; rg -n -F 'X-iS-HeAdMaN' ...; rg -n -F 'x-GROUP-id' ...; rg -n -F 'x-is-HEADMAN' ...` over the three scoped tests | 0 | Protected, OPTIONS, internal admission, and check-in fixtures are present |
| Case-insensitive unit assertions | `rg -n -F 'noneMatch(name -> name.equalsIgnoreCase("X-Group-Id"))' ...;` same for `X-Is-Headman` | 0 | Jwt protected/OPTIONS and Internal admission assertions are present |
| Downstream absence assertions | `rg -n -F '.withoutHeader("X-Group-Id")' ...;` same for `X-Is-Headman` | 0 | Check-in WireMock verifies both headers are absent |
| Canonical v2 and all hashes | Parse `.agent/student-gateway-c/admission/canonical-changed-paths.json`, compare every stage path SHA-256 to the working tree, and require schema v2/count 15/mismatches 0 | 0 | `schema=rct.gateway.changed-paths.v2 stagePaths=15 mismatches=0` |
| Refresh boundary | Parse canonical manifest and count stage rows among the five scoped code/test paths | 0 | `stagePaths=15 refreshedRows=5` |
| JSON syntax | `Get-Content -Raw .agent/student-gateway-c/admission/canonical-changed-paths.json | ConvertFrom-Json` | 0 | Manifest parses |
| Scoped whitespace | `git diff --check --` the five code/test paths and `.agent/student-gateway-c/admission` | 0 | No whitespace errors; Git emitted only existing LF/CRLF advisory warnings |
| Scoped path readback | `git diff --name-only --` the five code/test paths | 0 | Exactly the five assigned code/test paths are listed |

Post-repair SHA-256 values for the five manifest rows:

| Path | SHA-256 |
| --- | --- |
| `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java` | `600CC274B507EC8000DB9B33A570B98AAD5213E4F33535C7818C6892A465B858` |
| `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilter.java` | `8E97507F01CBC137248AAD1A6B39396F423805EC874A9C33FF197577B377E40C` |
| `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java` | `7978733D2D8918199CDB1C7E3FF9A7504F6C5E3C928B5E6BED43FCDBF5D5280F` |
| `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilterTest.java` | `BA3A62BBA723D2E7E4DDA7C2B014F3BEA7B0FD7DA5E7FF47600A71E6B0869131` |
| `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerIT.java` | `E716952852CDC74714C1DCBC7812018ECBEEFCE2486C8642EC2797DAEA7A2A08` |

## Runtime evidence and limitations

Runtime evidence is `N/A (root-owned)`: the frozen repair contract forbids
Gradle/runtime/Docker/network execution by this writer and reserves the exact
unit selector and `InternalJwtIssuerIT` selector for root under a separate
lease. The prior `83/0` unit and `6/0` integration results predate this
correction and are not reused as a repair PASS. This leaf therefore claims no
unit, integration, runtime, scanner, or review PASS. Root must run the released
selectors and obtain the fresh independent recheck before acceptance.

## Release state

WRITER RELEASE / SOURCE_READY. The five scoped code/test files are ready for
root-owned exact checks; canonical v2 is 15/15 with the five hashes above.
