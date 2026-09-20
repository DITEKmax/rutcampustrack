# Gateway C focused test compile correction

- Date: 2026-09-10 (Europe/Moscow)
- Baseline revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`
- Scope: `JwtAuthenticationFilterTest.java`, `InternalJwtIssuerFilterTest.java`,
  `InternalIssuerClientPropertiesTest.java`, and this admission evidence
  directory only.
- Root decision: bounded test-only correction; no Gateway product source,
  config, Auth13 import, Gradle, runtime, or review changes.

## Recorded defect and reproduction

The approved focused selector reached `:services:api-gateway:compileTestJava`
and failed with exit code `1` after `1m12s`; `:services:api-gateway:compileJava`
passed in the same run. The exact command was:

```text
.\gradlew.bat :services:api-gateway:test --tests ru.rutcampustrack.gateway.filter.JwtAuthenticationFilterTest --tests ru.rutcampustrack.gateway.security.InternalJwtIssuerClientTest --tests ru.rutcampustrack.gateway.security.InternalJwtIssuerFilterTest --tests ru.rutcampustrack.gateway.security.InternalIssuerClientPropertiesTest --tests ru.rutcampustrack.gateway.ratelimit.RedisRateLimiterConfigTest --no-daemon --no-parallel --max-workers=1 --console=plain --continue
```

The compiler reported seven errors: ambiguous AssertJ `assertThat` overloads
for generic `getAttribute` values at `JwtAuthenticationFilterTest.java:135,
137, 228, 483` and `InternalJwtIssuerFilterTest.java:75`; and missing static
`assertThat` at `InternalIssuerClientPropertiesTest.java:44,45`.

## Correction

The four JWT generic attributes now use explicit `(Instant)`/`(String)` casts,
the internal authenticated-user attribute uses an explicit `(String)` cast,
and `InternalIssuerClientPropertiesTest` restores the static
`org.assertj.core.api.Assertions.assertThat` import. No production file or
foreign dirty path was edited.

## Static verification after correction

The post-correction unit selector is intentionally root-owned and was not run
by this leaf. The following read-only checks are the evidence for this
bounded correction:

| Check | Command | Exit code | Evidence |
| --- | --- | ---: | --- |
| Three-file SHA refresh | `Get-FileHash -Algorithm SHA256 -LiteralPath <three corrected test paths>` | `0` | Values are `9C063B1B2C6052C3FA17FBDD8A20F329EC6F87CFF24D428AF06B08C786634383`, `9AACA8E50D4C1754A361A9EA41FF3470900750D07A376196EF74CE8DA5592949`, and `4540E4736C79C8D32C684FCF4FB01E272071A633B21D4E582C0BFDAE6EF71D8F`; canonical v2 was refreshed. |
| Canonical path guard | Parse canonical v2 and compare every stage path SHA-256 with the working tree | `0` | `15/15` stage paths match; only the three corrected test entries changed from the prior guard. |
| JSON syntax | `Get-Content -Raw .agent/student-gateway-c/admission/canonical-changed-paths.json | ConvertFrom-Json` | `0` | Manifest parses. |
| Scoped whitespace | `git diff --check -- services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilterTest.java services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/InternalIssuerClientPropertiesTest.java .agent/student-gateway-c/admission` | `0` | No whitespace errors. |

The exact root recheck command is released to root in the handoff; this leaf
does not claim unit, runtime, or review PASS.
