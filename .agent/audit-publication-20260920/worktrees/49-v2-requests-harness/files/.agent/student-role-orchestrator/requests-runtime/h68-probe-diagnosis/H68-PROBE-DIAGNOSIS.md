# H68 probe stream correction

Дата: 2026-09-19. Риск: S2. Статус: bounded correction PASS; production runtime не заявлен.

## Scope and source pins

Исправлен только assigned scope: `requests-runtime/runner.ps1` и собственные H68
offline checks/evidence. Product sources, `probe.mjs`, root H68 evidence, private
fixtures and foreign WIP preserved. Packet
`REQUESTS-H68-PROBE-DIAGNOSIS.md` SHA256
`6abcb3c4782553a8af02aa3f84f2b0564e300120826c995c1252703ca4ab0eaa`; RULES SHA256
`b256a175274987da9710d804b3c050a5dbcb47d8448cc03644d74168b52a437a`.

Immutable pre-fix diagnosis: `h68-probe-evidence.prefixed.json`, SHA256
`f6b4ae476b5a5e91fc16904c767b59cbf106de722f937be31bae77c59f8fe480`.
Its AST reproduction showed child exit 0 with valid JSON stdout plus a warning
stderr, while the old timed helper merged both streams into `Output` and strict
JSON parsing failed. The actual H68 raw probe output remains withheld, so its
production cause is **UNPROVEN**.

## Correction

`Invoke-ExternalSafe` now returns typed `Stdout` and `Stderr` for the timed
subprocess path while retaining combined `Output` compatibility and timeout
exit 124 behavior. `Invoke-RequestsProbe` redacts and parses only stdout as a
strict whole JSON document, checks the exit code, and records bounded separately
redacted stderr before either parse or nonzero failure. The existing singleton
`report.probeDiagnostics` remains the latest probe for compatibility; each call
also appends mode, exit code and bounded redacted stderr to capped
`report.probeDiagnosticsHistory` (eight records) so health/I1/I2 diagnostics are
not lost on the next call. Sensitive JSON string replacements remain quoted,
including escaped value contents; plaintext and Bearer redaction remain active.

## Evidence and checks

Correction evidence: [h68-probe-correction-evidence.json](h68-probe-correction-evidence.json), SHA256
`b133a92849e48c62712920805bc7c41d771de5c597d506bb6765fcd748a6d542`.
Runner after correction SHA256 `127e02cec1cc01c5048e378178f6c0bb5137d1b4b941241292aa8cfc17516513`;
pre-correction source pin `9a2d703548110a142597b02196f5483f0f61d7329dfbfed313e38f1afc0b4b9d`.
The latest CURRENT snapshot used by the evidence is SHA256
`86114c177a6a968cba9bac95fb7a356fc7bd32a0f1a2715d6b37f00974b5e159`.
The correction check SHA256 is
`06574a8e950be14ad1bdaffa5662dd351f2418259338220ec6e39ceb089a01c1`.

Command and exit code:

```powershell
pwsh -NoProfile -File .agent\worktrees\v2-requests-harness\.agent\student-role-orchestrator\requests-runtime\h68-probe-diagnosis\h68-probe-check.ps1
# exit 0
```

The check AST-extracted the production helpers and the real outer catch/finally
blocks and passed: valid JSON plus
stderr warning parses and retains the warning; trailing stdout junk rejects;
nonzero valid JSON rejects while retaining stderr; three successive calls retain
three bounded history records with `health-only`, `i1`, and `i2` modes; warning
sentinel text is redacted. Timeout and legacy `Output` behavior remain
compatible; quoted and escaped sensitive values stay parseable with no synthetic
sentinel leak. Actual outer fault injection preserved the first failure while
recording `CAPTURE_FAILED`; actual cleanup fault injection appended the cleanup
failure. Parser, strict stdout, separate diagnostics, JSON-safe redaction and
prior outer-failure/cleanup guards passed. `node --check` passed for all four
synthetic children (exit 0 each). Docker, Gradle, network, ports, services,
runtime and keys were not run/read.

## Limits and handoff

The synthetic children prove the helper contract and correction only. They do
not identify H68's withheld production stream. No Docker retry, TLS change,
permissive parsing, redaction removal, product change or runtime-fixed claim is
made. Fresh review should use the frozen runner SHA and correction evidence;
the historical diagnosis evidence remains immutable.
