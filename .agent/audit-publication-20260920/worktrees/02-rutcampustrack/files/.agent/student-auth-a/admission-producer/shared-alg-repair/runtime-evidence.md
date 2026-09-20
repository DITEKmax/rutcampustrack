# Runtime evidence

Status: N/A for this source-stage leaf.

The contract prohibited Gradle, Docker, Testcontainers, and product runtime until
root grants a separate runtime lease. Root owns the current-source focused Gradle
run and any downstream/runtime checks.

Two existing XML artifacts were inspected for provenance only:

- InternalJwtValidatorTest: 9 tests, 0 skipped, 0 failures, 0 errors;
  SHA-256 275DDE88BCA2D6771A6C7F0D0861B6999CA115FA755CED256FE3C02C7A0977EC.
- DualModeUserContextFilterTest: 8 tests, 0 skipped, 0 failures, 0 errors;
  SHA-256 E228FE5D8822B8E483A2733129942B06560DF1601F31FD533600897B5C83182F.

Those artifacts predate the current expanded source and are therefore not claimed
as post-repair runtime proof.
