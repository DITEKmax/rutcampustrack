# Admission migration changed-path evidence

- Date: 2026-09-10 (Europe/Moscow)
- Baseline revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`
- Before values are Git blob IDs from `git rev-parse HEAD:<path>` (SHA-1).
- After values are working-tree SHA-256 values from
  `Get-FileHash -Algorithm SHA256 -LiteralPath ...`.
- The migration owns the Gateway admission implementation and its six
  admission-focused test classes; the Redis resolver test is included because
  it is the user-attribute boundary for the same contract.

| Path | Before Git blob | After SHA-256 |
| --- | --- | --- |
| `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java` | `7e7dc0a0be9596feaecf0b653f4bff6ada47afcf` | `2066A1116DE92F99EA096718385657615C2750F68E1641587DB9CB937B71D9B7` |
| `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilter.java` | `6f6530e35b49618681fe766cc6bba3bdd32e0873` | `06CDC1A279E5CA1C0855291003F9EC6DDA68E95F59114C025E3943AC205FBB8E` |
| `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java` | `99f083e6df12937a69c9634c677c727a868aa265` | `9C063B1B2C6052C3FA17FBDD8A20F329EC6F87CFF24D428AF06B08C786634383` |
| `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilterTest.java` | `8befaf776bbaba99f9da5aef2c4b0d7362557e48` | `82705864F3FC3B48B1D525338781E05381E93B53172003ED3DE777DEB1DA1802` |
| `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerIT.java` | `fb62c93a1b177d6736d0ade9cbb9fd23b33bc043` | `DE886757CFE579A19B4A7BD2FBEA6A4E6855CC464314017BC116830FBE5E911E` |
| `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerClientTest.java` | `61dda7465fe70cde967f6df64128e06cf11062d5` | `8E3221FF07A5A3D1C763FE7F7BED6377C1282501EED8076EDB0C5FA13A642899` |
| `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/InternalIssuerClientPropertiesTest.java` | `473528f1d1be98d29fcb6d316b5a80ace83d87c1` | `4540E4736C79C8D32C684FCF4FB01E272071A633B21D4E582C0BFDAE6EF71D8F` |
| `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/RedisRateLimiterConfigTest.java` | `f6e82fd32d0511b6ca3e6fb4c07ff01fd3e56948` | `EBCA9709B9EDDA7672C4E944953ABDD65B4BA997F7CEED9C2F11D9EE7479F640` |
