# Identity stripping r5 — fresh Sol review persistence

Reviewer: /root/gateway_identity_strip_review_r5
Verdict: PASS. No actionable findings.
Review mode: read-only; reviewer ran no runtime.

## Released five-file SHA-256

| Path | SHA-256 |
| --- | --- |
| services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java | 600CC274B507EC8000DB9B33A570B98AAD5213E4F33535C7818C6892A465B858 |
| services/api-gateway/src/main/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilter.java | 8E97507F01CBC137248AAD1A6B39396F423805EC874A9C33FF197577B377E40C |
| services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java | 7978733D2D8918199CDB1C7E3FF9A7504F6C5E3C928B5E6BED43FCDBF5D5280F |
| services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilterTest.java | BA3A62BBA723D2E7E4DDA7C2B014F3BEA7B0FD7DA5E7FF47600A71E6B0869131 |
| services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerIT.java | E716952852CDC74714C1DCBC7812018ECBEEFCE2486C8642EC2797DAEA7A2A08 |

## Verified boundaries

- Canonical v2 remains exactly 15/15 stage paths with zero SHA mismatches.
- The Jwt sanitizer runs before OPTIONS, public, bootstrap, and protected branches.
- Both filters use Locale.ROOT normalization and remove only the exact legacy identity names, including case-insensitive X-Group-Id and X-Is-Headman.
- The valid internal admission path preserves the trusted X-Internal-Token and authenticated-user attribute semantics.
- Affected unit recheck is 63/0/0/0: JwtAuthenticationFilterTest hash 0870619787400817BD059DC325D0D8B6DE7E1A17F00FA02D32B51BB57EB5F51D and InternalJwtIssuerFilterTest hash ED26831CA38A67B5CBCF2390D0C9C3FCC943438E85D2188315EA6F4C652F436D.
- InternalJwtIssuerIT is 6/0/0/0 with hash 9E0E5717C06C92C05E1F4B6B13A071559182245865E42BC1E2DCF229EC645B9E.
- Unit tests and IT inject forged mixed-case headers; unit assertions and downstream WireMock reject both names. The trusted internal token and authenticated attribute remain asserted.
- All 3 current XML source files have exact matching copies in admission/pass-r4/ (source-copy 3/3).
- Reviewer ran no runtime; this record persists the independent read-only PASS and does not broaden the released scope.
