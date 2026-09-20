# V24 independent content review - 2026-09-09

Result: `PASS`. Findings: none.

## Stable snapshot

- Frozen B0 contract: `C9C4DC2C4BE2229AFF3CEDB27E0E43634329EBA5AC83CE42A889E34C33DAFC74`.
- A shared authority contract: `47FC36F57AFCEEC489B5C95C6C169C2CBA9F53FEED2287CF6946B1CB150D807E`.
- A exact-two JDBC packet: `527AE300E42008E727348F13FF7C776C550AEE39E9B8D6E1046191AE6ACADA01`.
- V24: `1D15418FA4869D1288B3FA25F688A237087294F360AEA6729F524DE2F5B67F90`.
- StudentFoundationMigrationIT: `27FAE6FD189C1B13F7C192D4C7641FE6EB95BBEE0F5DA6E00BCD81B9F6FAA5F1`.
- Fresh Academic XML: `CF086E070724CC9E076EEFF33B88F0B7D410C23E8EF5557525FB9E3F03E20ECF`; 9 tests, 0 failures/errors/skipped.

## Review conclusion

The reviewer independently inspected V24 source, the A authority contract, the exact-two JDBC packet, the migration IT and fresh PostgreSQL 16 XML. V24 rejects unknown legacy role/status and inconsistent headman rows before DDL; creates grants only from explicit legacy facts; preserves users and legacy columns; leaves session/event tables empty; enforces restrictive and composite ownership FKs; increments roles_version on relevant grant changes; protects immutable grant ownership; constrains session JTI/version/expiry/revocation/auth method; and protects account events from UPDATE/DELETE.

The XML directly shows a successful migration/validate path and a malformed-headman atomic abort. The abort preserved the original row and omitted roles_version, grants and V24 Flyway history. The IT also covers foreign grant rejection, version bump and event UPDATE/DELETE rejection.

## Boundary and residual risk

This PASS accepts the immutable V24 schema as the dependency for A's future exact-two JdbcSessionAuthority slice. It does not accept the absent adapter, its transactional/race/error mapping, or Auth HTTP/JWT/Gateway/WS cutover. The fixture does not enumerate every individual CHECK, INSERT/DELETE/overflow trigger branch or every legacy-row combination; those were reviewed statically and the A adapter must run its own PostgreSQL IT before acceptance.
