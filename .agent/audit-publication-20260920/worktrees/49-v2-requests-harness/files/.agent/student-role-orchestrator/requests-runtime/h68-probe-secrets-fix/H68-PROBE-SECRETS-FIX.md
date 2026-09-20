# H68 probe dynamic secret registry correction

Дата: 2026-09-20. Риск: S3. Статус: bounded correction PASS; full runtime не заявлен.

## Scope and pins

Изменён только assigned `probe.mjs`; synthetic preload/check/evidence принадлежат
собственной директории H68. Runner SHA256 `127e02cec1cc01c5048e378178f6c0bb5137d1b4b941241292aa8cfc17516513`,
product sources, fixtures, keys и предыдущие H68 artifacts сохранены.

Packet `REQUESTS-H68-PROBE-SECRETS-FIX.md` SHA256
`91dde97513f89889a82c39ab34665c8b8498dc371ef596d849ef1bb53a0c2ddc`; RULES
SHA256 `b256a175274987da9710d804b3c050a5dbcb47d8448cc03644d74168b52a437a`.

## Actual pre-fix defect

Immutable pre-fix evidence: [h68-probe-secrets-prefixed-evidence.json](h68-probe-secrets-prefixed-evidence.json),
SHA256 `81f3624e7b59d792fb1742d671b612d03850b4ca30dfaff466cf4e990f9f7788`.
The real entrypoint was run with a controlled synthetic HTTPS transport. Success
exited `0`, forced top-level catch exited `1`; opaque access-token and unknown
field values appeared in both outputs. Existing string redaction also inserted
unquoted markers inside JSON strings, so both outputs failed JSON parsing. The
immutable record therefore has `parsed: null` for both cases; its redacted
`outputRedacted` fields preserve the raw pre-fix captures for diagnosis.

## Correction

`main` now owns one closure-scoped diagnostic registry used by both success and
failure emitters. `runRuntimeProbe` registers the password immediately after
input read, the access token immediately after extraction, and cookie whole
header, pairs, and values immediately after parsing and before validation or the
next request. Empty and non-string values are ignored.

`redact` now recursively sanitizes structured values and JSON-stringifies the
sanitized result. Registry values, Bearer/JWT text, and non-empty sensitive-key
values remain redacted without inserting invalid JSON quoting. The existing raw
plaintext sensitive key/value regex and JWT pattern are retained for string
callers; structured emitter values use the JSON-safe traversal. Unknown fields
and error messages carrying a registered opaque value are covered.

## Verification

Correction evidence: [h68-probe-secrets-correction-evidence.json](h68-probe-secrets-correction-evidence.json),
SHA256 `a03af240e4409c1c07212d3ea18be7c925592e999d30b2f4259b159525e27650`.
Probe after-fix SHA256 `3a23ba65c71fa396c07c5ae5efae5979a374fb46e2b1ec019a3f20dd7fc707e6`.
Correction check SHA256 `5671a7b0bef53055f87e020ce4990cc1ae86aa4fd1f75e8ae7e969b3fb716c99`;
synthetic preload SHA256 `3aa4e4aa373968cf2a73ddb08a9b1e14d06dd180b024cb38f460c6853ebd3e4f`.

Commands and results:

```powershell
node --check .agent\student-role-orchestrator\requests-runtime\probe.mjs
# exit 0
node .agent\student-role-orchestrator\requests-runtime\h68-probe-secrets-fix\secrets-fix-correction-check.mjs
# exit 0
node .agent\student-role-orchestrator\requests-runtime\probe.mjs --self-test
# exit 0, source-self-test PASS
pwsh -NoProfile -File .agent\student-role-orchestrator\requests-runtime\h68-probe-diagnosis\h68-probe-check.ps1
# exit 0, prior H68 stream/redaction/outer-failure checks PASS
```

Actual correction emitter checks passed: success output is valid `PASS/runtime-i2`
JSON with exit `0` and no opaque token/password/cookie; forced top catch is valid
`FAIL` JSON with exit `1` and no leak. Unknown key/message positions, generic
plaintext sensitive labels, Bearer/JWT text, escaped quotes, backslashes and a
control character are covered in both emitter paths; registration ordering guards
pass. Docker, Gradle, network, ports, runtime services and real credentials/keys
were not used.

## Limits

The synthetic transport proves actual entrypoint emission and registry ordering;
it does not claim a full Docker runtime. No TLS/auth behavior, failure exit
semantics, or redaction was weakened. Fresh FULL Sol review should use the frozen
probe SHA and correction evidence together with the immutable pre-fix evidence.
