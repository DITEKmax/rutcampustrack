# I5 evidence ledger

## Frozen inputs and ownership

- Rules SHA256: `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.
- Target: `codex/v2-integration-a2-l3`, base/revision `b8220ac92125a8afa37598b270aa4fab7aa1f470`.
- Accepted I4 manifest: 44 rows, canonical `28447CAB436CE49C2AADEC9103A4293E358D5BCBE569C9B61C85F7C655FABDA9`.
- R4 source ledger: `.agent/v2-requests-ui/diff.md`, source blob `7f6071b49c11cbb1933e125bf13f0ac8f46f0c84`, source revision equal to E.
- R4 source worktree status after transfer still contains exactly its accepted 10 modified + 4 added product files and its own `.agent/v2-requests-ui` metadata; no source path was changed by I5.

## Pre-copy gate

Read-only source/target gate before copy:

```text
I4 manifest rows=44 missing=0 mismatches=0
R4 source blobs=14 mismatches=0
R4 required test environment SHA256=CF3212D7D65917AB03161C487471EFD6A088B7463A0EF76C542D93CA65782205 bytes=221
target expected R4 overlap=0
```

After copy, all 14 source/target byte hashes and Git blobs matched, the I4
44-row manifest remained at zero mismatches, and the target had 58 unique
product paths. The only non-product copy was the exact 221-byte client test
environment recorded in `i5-diff.md`.

## Manifest gate

The target `i5-union-manifest.sha256` contains 58 sorted product rows. The
post-copy recomputation reported:

```text
i5_manifest_rows=58
i5_manifest_actual_hash_mismatches=0
i5_manifest_declared_canonical=D3FF3DFDC7C093935CCE3DEF4B4C0A4151F876F1572C3A3851A4480A588FC204
i5_manifest_recomputed_canonical=D3FF3DFDC7C093935CCE3DEF4B4C0A4151F876F1572C3A3851A4480A588FC204
i4_manifest_rows=44
r4_product_rows=14
supplemental_env_sha256=CF3212D7D65917AB03161C487471EFD6A088B7463A0EF76C542D93CA65782205
supplemental_env_bytes=221
```

The supplemental environment is excluded from the product manifest so the
58 count describes product source only.

Final source/status reconciliation reported `source_r4_blob_mismatches=0`,
`target_i4_manifest_mismatches=0`, `changed_product_paths=58`,
`unexpected_product_paths=0`, `missing_product_paths=0`, and equal source and
target environment SHA256 values.

## Frontend checks

The default sandbox Vitest attempt exited 1 before collection with the exact
esbuild access-denied output retained in `i5-vitest-sandbox.log`. The identical
offline command then ran under the narrow scoped escalation and returned exit
0 with `5` test files and `52` tests passed; raw test output is in
`i5-vitest.log`. Typecheck and lint returned exit 0; their command output is in
`i5-typecheck.log` and `i5-lint.log`. The product whitespace scan found zero
trailing-whitespace lines and `git diff --check` returned exit 0;
`i5-diff-check.log` records the result.

The final clean capture was run with `login=false`/no-profile PowerShell only
to preserve complete command streams. The Vitest capture has exit 0 and empty
stderr; the typecheck and lint captures have exit 0 and empty stderr. The diff
check capture has exit 0 and only Git's existing line-ending notices on stderr.

## Warnings and interpretation

PowerShell emitted the existing profile and Git LF-to-CRLF notices during
tool invocations. They were unrelated to the request and did not alter source
or cause a failing check. The sandbox access failure was linked to the test
request by reproduction and resolved only with the same scoped command under
escalated execution; no code or harness adaptation was made.

## Open gates

This evidence proves source transfer, manifest integrity, and scoped frontend
tests/typecheck/lint. It does not prove browser behavior, fullservice behavior,
backend integration, production readiness, or E/main promotion. A fresh
independent full58 Sol review remains required after this stable diff.
