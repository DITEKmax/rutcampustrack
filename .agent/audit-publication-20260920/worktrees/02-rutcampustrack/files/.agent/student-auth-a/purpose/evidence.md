# Purpose correction evidence

## Baseline and source resolution

- Baseline revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
- Initial `git status --short --branch` was clean at the detached baseline;
  no unrelated work was overwritten.
- Canonical probe read from
  `C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\student-role-02\auth-token-purpose-probe\evidence.md`.
  SHA-256: `BC14178C5D8D2B58D09B44F7D46778A140EF328D3FD0D630862F6290F6E8BC20`.
- Canonical decision packet read from
  `C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\student-role-02\profile-auth-decision-packet.md`.
  SHA-256: `E47819AD123D69E32FC24B7AD9429FFD747CA160A714C0484E3B61CFEA694BBF`.
- Project instructions and `rct-verification` were read before implementation.

The canonical probe reproduced a real compiled Auth filter accepting a signed
refresh token and establishing `ROLE_null`; missing-header and access-token
controls passed. It did not prove a full Gateway/HTTP exploit or revocation
effect. That evidence maps directly to the strict access parser and filter
correction in this scope.

## Caller trace

- `WsTicketController.java:55` calls `jwtService.parseToken(token)` for an
  access token; `parseToken` remains a strict access alias.
- `AuthService.java:102-103,133-134` calls `extractUserId` and `extractJti`
  only on refresh-token request/logout inputs.
- `AuthService.java:85,122`, `TmaService.java:75`, and
  `OtpService.java:243` call `extractJti` only on newly generated refresh
  tokens.
- `JwtAuthenticationFilter.java:39` now calls `parseAccessToken` directly.

## Behavioral evidence

The focused synthetic-RSA tests cover generated access/refresh/internal purpose
claims, strict parser cross-use rejection, missing/wrong purpose, positive
numeric subject, required expiration, known role, required refresh `jti`,
issuer/audience/signature preservation, valid filter authentication, and
fail-closed filter negatives. Test reports show 8 parser tests and 5 filter
tests, all with zero failures/errors.

Final report artifacts were captured under `evidence/junit/` from the existing
Gradle output. Their source report hashes are recorded in `manifest.json`:
`JwtTokenPurposeTest.xml` source SHA-256
`E1A5DCEAAB7F8D07ABF065616CF8F1FFBD5405EAA2CA668B5191BC697361B55F` (1552
bytes), and `JwtAuthenticationFilterPurposeTest.xml` source SHA-256
`9903E9773A7C508BCA3886868A54DC6267620A2D2D17184BA6C3B414DF3BF217` (1971
bytes). Evidence copies are content-equivalent XML with LF line endings; their
own hashes and sizes are recorded separately in the manifest.

The test key pair is generated in memory and installed through reflection;
`JwtService.init()` is never called. Tests use mocked `JwtProperties`,
`StringRedisTemplate`, and `User`. No real keys, secrets, Redis, DB, network,
or external runtime were used.

## Environment and observations

PowerShell 7.6.5 on Windows 11/Windows 10 build `10.0.26200`, Microsoft OpenJDK
21.0.10 LTS, Gradle 8.12 wrapper. Gradle emitted the existing Mockito dynamic
agent warning and Java compiler unknown Jackson annotation warning; neither was
linked to the requested purpose behavior or changed.

The shell also emitted pre-existing PowerShell profile virtual-terminal warnings
and Git warnings that `C:\Users\maksd/.config/git/ignore` was inaccessible.
They did not change the repository diff or test result.

## Open limitation

The focused unit evidence does not close the S3 integrated gate for full
Gateway HTTP ingress, downstream token exchange, or security scanners. Those
checks remain open for root's session/integration packet; no claim is made that
this bounded auth-app change proves revocation behavior or a complete external
exploit.
