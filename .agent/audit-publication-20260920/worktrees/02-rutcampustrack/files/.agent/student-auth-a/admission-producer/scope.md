# Admission producer scope

- Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
- Risk: S3 (authn/authz and session revocation boundary).
- Owner: admission producer leaf; sole writer for the producer slice and this evidence directory.
- Frozen responsibility: session-bound access parsing, live authority admission, internal JWT production, strict shared validator, typed admission errors, old issuer-controller removal, and focused source tests.
- Accepted Auth13 source is imported byte-for-byte; its manifest is recorded in `manifest.json`.
- Foreign profile/UI, purpose, session-domain/JDBC, gateway, and `.agent` work remains in the working tree and was not reverted or staged.

The stage is source-only. No Gradle, Docker, Testcontainers, database, deploy,
migration, commit, or push was performed by this leaf.
