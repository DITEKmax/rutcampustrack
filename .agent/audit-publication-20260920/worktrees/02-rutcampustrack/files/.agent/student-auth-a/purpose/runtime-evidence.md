# Runtime evidence

Status: PASS for the bounded servlet/filter unit behavior; OPEN for the full
S3 integrated gate.

The root-leased focused command ran on Windows PowerShell with Microsoft
OpenJDK 21.0.10 and `--max-workers=1`; it exited `0` with `BUILD SUCCESSFUL`.
The test reports recorded 8 `JwtTokenPurposeTest` cases and 5
`JwtAuthenticationFilterPurposeTest` cases, with zero failures and errors.

This scope deliberately has no external product runtime: tests construct a
synthetic in-memory RSA key pair, install it into `JwtService` by reflection,
and mock Redis/properties/user data. No real key material, secret, Redis, DB,
network, or Testcontainers was used.

Full Gateway HTTP ingress, downstream token exchange, service integration and
security-scanner evidence remain OPEN and must be supplied by the root/session
integration packet before the overall S3 story is marked complete.
