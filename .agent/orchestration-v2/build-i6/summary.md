# I6 summary

Status: `SOURCE_AND_PURE_PASS; AUTHOR_RELEASED_FOR_FRESH_SOL_REVIEW; H41_RESERVED_NOT_ACTIVE`.

The accepted I5 union was verified and committed as
`d7ec16572db325d944f1a1fbd3b4960b827d09c3` in the assigned integration
worktree. A new clean `codex/v2-runtime-build` worktree points at that exact
commit. Git blob verification is `0/58` mismatches; raw checkout bytes differ
on 53 paths only because of line-ending normalization.

The earlier producer/pure hashes are historical. The current FIX4 source hashes
are `producer.ps1`
`DB8A4B84E4D042F72F5E0912C2F270CA063AB4735228CB9299A45748DFAC7A83` and
`producer-pure-check.ps1`
`7E00D3E4B04E759E17A5851EB243FF7FB24ABBB95756251F87CB2984D7D9387D`.
The pure checker and both AST checks pass at exit `0` and match the accepted R3
canonical serializer. The wrong-revision preflight still exits `1` before
Gradle/npm and emits no manifest. No product build, Docker, ValidateOnly or
Requests runtime was run; H42 offline dependency setup is recorded separately
as exit `0` evidence.

## Resume repair — 2026-09-19

The previous source and pure evidence above is retained as historical evidence;
its producer hashes predate the stopped MEDIUM publication finding. The current
bounded repair is limited to the external producer and pure checker. It moves
all manifest write/read/hash/report operations before the explicit commit point,
the atomic no-overwrite `File.Move(..., $false)`, and it leaves only a
warning-only post-commit diagnostic path. Actual helper fault injections cover
temporary write/read, hash, report write and rename; each preserved its original
error, kept the published destination absent before commit, and cleaned the
run-owned temporary file. A pre-existing foreign destination remained byte
identical, and the accepted consumer serializer/timestamp parity remained
passing.

FIX4 adds pre-build ancestor/junction confinement for all planned outputs and
caller paths, a lazy stale-JAR diagnostic, and a process-level publication
failure harness. Five real child processes exited `1` with original errors,
FAIL reports, no manifest and cleaned owned temporary files; a separate
post-commit diagnostic kept the committed PASS authoritative. Actual junction
fixtures proved no external sentinel write. Exact raw logs, command exits and
hashes are recorded in `evidence.md` and `checks.json`. Gradle, npm build,
Docker, ValidateOnly and product runtime remain `NOT_RUN`; no H41 lease was
used.

## FIX2 release — pure fixture ownership and evidence — 2026-09-19

The current pure checker is `EDA74A40683D55F8CA561826F870217C5573504CBE596A63DC3C4538346BEE34`.
Its final run exited `0`; raw command stdout/stderr are preserved as
`pure-fix2-ownership-20260919-01.stdout.log`
(`E13DC278F5A26E743CB9607A9A635AFD1E73801464A8F1A87BFA3A32239AC189`)
and `pure-fix2-ownership-20260919-01.stderr.log` (empty,
`E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855`).

The correction is limited to `producer-pure-check.ps1`: unique GUID-owned
fixture roots are confined to `build-i6`, marker ownership and resolved bounds
are checked before recursive cleanup, and process raw logs use preserved
run-scoped no-overwrite paths. The occupied
`process-case-manifestHash/union/foreign-sentinel` and occupied log regression
both stayed byte-identical. All five publication fault children exited `1`
with their original errors, no trusted manifest, and cleaned owned temporary
output. Final raw child logs are under
`pure-process-logs-2a4a6a07c0b94714a303b8758375b445`; prior logs remain intact.

Producer hash `DB8A4B84E4D042F72F5E0912C2F270CA063AB4735228CB9299A45748DFAC7A83`
is unchanged. No producer, consumer, product, H44 build output, or runtime
source was touched. This pure gate is released for the fresh independent FULL
Sol review; artifact build/runtime success remains outside this gate.
