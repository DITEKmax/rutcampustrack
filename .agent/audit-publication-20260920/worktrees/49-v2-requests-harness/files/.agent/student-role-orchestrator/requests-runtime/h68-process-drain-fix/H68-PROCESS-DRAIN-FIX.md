# H68 timed process drain and probe redaction correction

Дата: 2026-09-20. Риск: S3. Статус: bounded correction PASS; full runtime не заявлен.

## Scope and pins

Изменены только production `runner.ps1` и `probe.mjs`; synthetic checks/evidence
находятся в этой директории. Base checkout `73fd5f27ceb429ad0692b073189f8a527c550830`.
Packet `REQUESTS-H68-PROCESS-DRAIN-FIX.md` SHA256
`e5920216b0288d87cebf41bcd1f2d605b1b31f492630cbb8781d78b2e08ad9dc`; RULES
SHA256 `b256a175274987da9710d804b3c050a5dbcb47d8448cc03644d74168b52a437a`.
The root message carried the pre-amendment packet pin `c9492e...`; the final
scope-boundary amendment was read before the checks and is recorded with the
actual current hash above.
Runner pre-fix SHA256 `127e02cec1cc01c5048e378178f6c0bb5137d1b4b941241292aa8cfc17516513`;
probe pre-fix SHA256 `3a23ba65c71fa396c07c5ae5efae5979a374fb46e2b1ec019a3f20dd7fc707e6`.

## Reproduced findings

The immutable runner prefixed record was captured before the drain correction:
[h68-process-drain-prefixed-evidence.json](h68-process-drain-prefixed-evidence.json),
SHA256 `8bc103725bec305b80baa17b587888f098d3276c29bfcacff7664204efe9c788`.
The AST-extracted production helper timed out with exit `124` and captured zero
bytes when a synthetic child wrote 1 MiB to both redirected pipes.

The historical probe prefixed path suffered an overwrite during fixture
adjustment. The original bytes and original hash are unavailable and are not
claimed as recovered; see [h68-prefixed-evidence-overwrite-incident.json](h68-prefixed-evidence-overwrite-incident.json).
The postcheck copy is [h68-probe-secrets-prefixed-evidence.postcheck.json](h68-probe-secrets-prefixed-evidence.postcheck.json),
SHA256 `fd054268fcf8d1044f4073277e765cb8398167b06fb4d9d2d423d7767296207c`.
A fresh actual pre-fix rerun, clearly marked as a new run, is
[h68-probe-secrets-prefixed-evidence.recovered.json](h68-probe-secrets-prefixed-evidence.recovered.json),
SHA256 `2ea4bc7ea9606786bcdeeb827ebc72697c44bfe3dcd806146308c99cc89650d6`.
It uses the genuine pre-fix source hash above, reproducing escaped quote/single
quote suffix leaks and insertion-order prefix overlap leaks in success and catch.

## Correction

`Invoke-ExternalSafe` starts `ReadToEndAsync()` on stdout and stderr immediately
after process start. Process completion, post-kill waiting, stream-drain waiting,
and cleanup use finite bounds; process tree termination and disposal remain in
the finally path. Typed `Stdout`/`Stderr`, combined legacy `Output`, exit `7`,
and timeout `124` are retained.

`redact` now replaces registered secrets with one collision-safe longest-first
regular expression in one pass. Sensitive quoted plaintext values consume
escaped quotes/backslashes before replacement. The original Bearer and exact
three-segment JWT patterns remain unchanged.

## Verification

Runner evidence: [h68-process-drain-correction-evidence.json](h68-process-drain-correction-evidence.json),
SHA256 `04eaf77fef5c4a7294516d63ee61efd9b4b77fff16b32fe902d1e7b3f5722727`.
It records exact 1 MiB stdout/stderr hashes, simultaneous drains, exit `7`,
timeout `124`, no owned direct child and no owned descendant after timeout,
typed fields, and bounded-source guards. The tree fixture keeps the owned
parent alive until timeout and checks only its known descendant PID; it does
not claim arbitrary daemon discovery or cleanup after a parent has exited.
Probe evidence: [h68-probe-secrets-correction-evidence.json](h68-probe-secrets-correction-evidence.json),
SHA256 `b47890f69d52bfa3384e4f2cf02d60613f6ed2b0aa0181a23da23d9dd2aeda4b`.
Actual success exit `0` and top-level catch exit `1` both return valid JSON with
no opaque or overlap suffix leaks. The fixture covers prefix, suffix and
equality overlap among password, access token, cookie pair/value, and marker
text while preserving the original generic plaintext/Bearer/JWT guards.

Commands, all exit `0`:

```powershell
pwsh -NoProfile -File .agent\student-role-orchestrator\requests-runtime\h68-process-drain-fix\process-drain-correction-check.ps1
node .agent\student-role-orchestrator\requests-runtime\h68-process-drain-fix\probe-secrets-process-correction-check.mjs
node --check .agent\student-role-orchestrator\requests-runtime\probe.mjs
node .agent\student-role-orchestrator\requests-runtime\probe.mjs --self-test
pwsh -NoProfile -File .agent\student-role-orchestrator\requests-runtime\h68-probe-diagnosis\h68-probe-check.ps1
node .agent\student-role-orchestrator\requests-runtime\h68-probe-secrets-fix\secrets-fix-correction-check.mjs
git diff --check -- .agent/student-role-orchestrator/requests-runtime/runner.ps1 .agent/student-role-orchestrator/requests-runtime/probe.mjs .agent/student-role-orchestrator/requests-runtime/h68-process-drain-fix
```

Final production hashes: `runner.ps1`
`05c76b696bf987370c0597c3af76c9db94fb588aa3ac3087d6907110ebbb9716`;
`probe.mjs` `1093b32778d8a7af7cbc3e8f691073597257eb3091ac6a51df8b12de8e8731d6`.
Docker, Gradle, network, ports, services, keys and real credentials were not
used. No commit was created.

## Limits

The subprocess and probe runs are controlled offline synthetic runtimes. They
prove the actual extracted helper and actual probe entrypoint/emitter paths, but
do not claim a full Docker runtime or restore the overwritten original evidence.
