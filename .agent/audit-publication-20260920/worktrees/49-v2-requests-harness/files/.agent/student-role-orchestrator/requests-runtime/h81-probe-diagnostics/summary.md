# H81 probe failure diagnostics

This is a narrow amendment to the accepted H78 request-runtime contract. The
H78 BSON descriptor correction remains frozen; this packet records only the
nonzero Node probe diagnostics boundary.

## Goal

Retain a concrete safe probe failure from the already registry-redacted JSON
emitted by the Node helper when its exit code is nonzero, while preserving the
existing generic fallback for malformed or unexpected output.

## Context/evidence

The root H81 runtime attempt exited 1 before Mongo/I1. The prior runner stored
the typed stderr and discarded the typed stdout before `Assert-That`, so the
report and failure text contained only `Requests Node HTTPS probe failed; raw
probe output was withheld`. Root cleanup evidence was empty and the product
cause was not established. The actual helper catch contract is the already
redacted object `{schema, status, error}` with `status: FAIL`.

## Relevant scope

Only `requests-runtime/runner.ps1` `Invoke-RequestsProbe` failure handling and
the existing `h68-probe-diagnosis/h68-probe-check.ps1` targeted helper check.
The one H78 contract metadata correction adds the required `B256` prefix to
the owner rules digest.

## Required behavior

On a nonzero helper exit, parse separately redacted stdout and accept it only
when it is a JSON object with exactly `schema`, `status`, and `error` fields,
the exact request-probe schema, `status: FAIL`, and a nonempty string error.
Retain that error through the existing `Protect-ReportText` and
`Limit-EdgeDiagnosticText` path, bounded to 1200 source characters, in the
current diagnostics record and history, and include it in the failure text.
Malformed JSON, a different shape, schema, status, or error type keeps the
existing generic message. Success parsing, typed streams, exit handling,
history, cleanup, and stderr retention remain unchanged.

## Constraints

No product, Node probe, BSON conversion, Docker, Gradle, network, key, port,
runtime, or unrelated source changes. No raw stdout, headers, tokens, request
bodies, or unbounded diagnostic text may enter the report. The root remains the
sole runtime lease owner; this leaf does not claim a product runtime PASS.

## Existing patterns

The implementation reuses `Protect-ReportText` and
`Limit-EdgeDiagnosticText`, keeps `Invoke-ExternalSafe` typed `Stdout`,
`Stderr`, and compatibility `Output`, and appends to the existing capped
`probeDiagnosticsHistory` list. The check uses AST-extracted production
functions and temporary Node helpers without changing the production probe.

## Acceptance criteria

The bounded check must retain a valid redacted `FAIL.error`, truncate a long
error through the existing limiter, and use the generic fallback without
leaking an opaque sentinel for malformed output. Existing valid-JSON parsing,
trailing-junk rejection, nonzero exit, stderr redaction, sequence history,
timeout, cleanup-capture, and legacy `Output` checks must continue to pass.

## Verification

Command: `pwsh -NoProfile -File .agent/student-role-orchestrator/requests-runtime/h68-probe-diagnosis/h68-probe-check.ps1`.
Exit code: `0`. Environment: Windows PowerShell 7.4 runtime, `login:false`,
offline synthetic helpers only; Docker/Gradle/network/product runtime were
`NOT_RUN`. Frozen source hashes are recorded in `checks.json`.

## Do not

Do not add a message catalog, new redactor, generalized framework, probe edit,
runtime rerun, history sweep, BSON change, or broad source hardening. The root
must run the next actual helper/runtime attempt and record the product cause
before any further decision.

## Result and limitations

The source correction is frozen after the targeted PASS. The check establishes
the consumer boundary and safe fallback using synthetic helper output; it does
not establish why the root-owned product I1 helper exited 1. H78 native BSON
proof and its accepted runner/R8 behavior are preserved and are outside this
amendment.
