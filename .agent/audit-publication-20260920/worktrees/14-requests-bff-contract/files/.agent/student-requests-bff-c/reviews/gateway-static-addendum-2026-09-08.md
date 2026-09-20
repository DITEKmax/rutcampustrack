### Addendum — 2026-09-08

**PASS — STATIC issuer↔Gateway compatibility for the frozen final union.**

Source supplement independently verified:

- Auth purpose manifest SHA-256 matches `393721C46A2302BA747B48FA367812B5A7FB7EEA59492627BFD9ABBF3FB75587`.
- All four product-file hashes match; `JwtService.java` matches `81429C4219EC00FD2207E11460C8AB03314787C7CDFB45EA28B3A18A72E1BD5D`.
- Accepted issuer emits exact `token_use=access` at `JwtService.java:141`, `refresh` at `:160`, and `internal` at `:221`.
- Strict access/refresh parsing is implemented at `:177-200`; issuer, audience, purpose, subject, expiration, role, and refresh `jti` fail closed.
- Original XML evidence contains 13 tests, 0 failures/errors/skips: 8 token-purpose tests and 5 authentication-filter tests.

The earlier HIGH finding remains accurate for the isolated C checkout, which lacks the separately owned A import. With the accepted A source included in the frozen B union, it is **not a new production issuer defect**. The previous repair contract is superseded only for that finding; do not create a duplicate issuer repair.

The remaining integration prerequisite is to import the exact four A files into B and reverify their manifest hashes. This import does not prove the full Gateway flow.

Still OPEN and required: real Auth issuer → Gateway protected-route HTTP, refresh/internal rejection through the deployed key path, Nginx/Gateway/Redis behavior, exact fixed/chunked ingress, early upstream response/error handling, multipart cancellation, no-store, scanners, and zero domain/Mongo/outbox writes.
