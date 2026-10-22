# Export HTTP audit follow-on

Bounded verification is complete. EXPORT_AUDIT1 is released. HEAD is `1bb527d5acdc704fab1b1df9bdf1ee4db9c474b6`; its parent product revision is `f09b70e383b1c29b31960b5da99647f8ae6530bf`. Only ExportJobHttpIT.java changed (10 added lines, 1 removed). Its canonical SHA256 is `7116bf1fde7346bbb8dde9ae8d3a6f5bb9e3aa74abedf07e427ba2bfee19dbff`. All 6510 tracked file bytes and modes are identical across the eight run inventories. The other 6509 files match original f09. The untracked node_modules symlink is preserved.

The selected case produced four actual outcomes: the old global query failed with expected 1, actual 2 (native c6949f); the corrected query passed, asserting current-job count 1 and retained historical-row count 1 (751fd5); a valid duplicated current-job fact failed the unchanged scoped count assertion with expected 1, actual 2 (70c0e4); the final canonical test passed again (c25f41). Every phase ran exactly one case with zero errors and zero skips. The two failures occurred at the intended assertions, after valid setup. Normal passes retain the HTTP, file bytes, event schema, ticket and live-revocation checks.

Each native result, private original XML hash, derived redacted XML, source inventory, clock and resource receipt was saved before the next phase. Original CI 38193576638/job114631310135 confirms its assertion failure; its numeric actual remains UNKNOWN. Local observed 2 is separate evidence.

All 12 owned PostgreSQL/Redis/Ryuk containers were removed by standard cleanup, and all four runner processes and 12 observed owned Java processes terminated. Foreign DOMAIN1 resources were preserved. The 4 GiB floor was never crossed; release free space was 9480380416 bytes. No further heavy allocation is planned.

This verifies the one-file oracle correction on original exports 1bb/V56. It does not verify current integrated whole Auth/V57, renderer, broker or browser acceptance. Fresh independent review, root integration and new CI/Coverage/Security receipts remain required. No whole-task DONE claim is made. Earlier sealed NP01 and exports receipts remain unchanged.

Read runtime/final-summary.safe.json, runtime/final-source-bridge.safe.json, runtime/lease-release.safe.json and each phase's classification, cleanup and results files. The safe archive is export-audit-final-safe-evidence.zip; final-manifest.safe.json inventories its members, and final-package.safe.json records its hash. Private logs, original XML, source copies, overlays and key material are excluded.
