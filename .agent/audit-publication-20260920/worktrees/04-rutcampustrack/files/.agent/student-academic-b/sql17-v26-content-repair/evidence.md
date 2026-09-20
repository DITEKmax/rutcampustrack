# Evidence

## Scope and criteria

The implementation is bounded to V17, V26, `StudentOccurrenceMigrationIT`,
`StudentFoundationMigrationIT`, and this evidence directory. It covers the
recorded V17 crosswire defect, V26 published graph/plan identity defect, V26
decoupled TTL defect, the required behavior tests, and preservation of valid
draft/legacy cases.

## Static evidence

- Base revision readback: `git rev-parse HEAD`, exit code `0`,
  `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
- V17 origin and Schedule selectors: targeted `rg -n ...`, exit code `0`;
  V17 lines 169-170 and Schedule lines 208/262 contain the expected markers.
- V26 source markers and parent-lock/retention guards: targeted `rg -n ...`,
  exit code `0`; plan-version/format `FOR UPDATE`, publication guards and
  dedupe `FOR UPDATE` are present.
- Persistent intent FK absence: PowerShell `Select-String` guard, exit code
  `0`, output `no persistent intent FK`.
- Academic selectors and typed failure helper: targeted `rg -n ...`, exit
  code `0`; all eight new behavior names and `assertSqlFailure` are present.
- Changed-method source readback: PowerShell `Get-Content` readback of the V17
  trigger, V26 plan/format/intent functions, and all added Java methods/helpers,
  exit code `0`.
- Java source balance: PowerShell character-count check, exit code `0`;
  Academic `parens=843/843 braces=75/75`, Schedule `parens=331/331
  braces=20/20`.
- Trailing whitespace scan for all four product paths, packet and this
  evidence directory, exit code `0`; output `no trailing whitespace`.
- Scoped `git diff --check` for all four product paths and the evidence
  directory, exit code `0`.

## Runtime status

Runtime is `PENDING_ROOT_HEAVY_LEASE`. This leaf did not run Gradle, Java
compilation, Docker, Testcontainers or PostgreSQL. PG16 execution must be run
later by root under the exclusive lease, followed by the required fresh Sol
recheck. No runtime PASS is claimed here.

## Limitations

The Java balance and targeted readback checks are static evidence and do not
replace compilation or PG16 behavior execution. The V26 prehash was the prior
frozen guard; its changed posthash is authorized by root's explicit scope
expansion recorded in `packet.md`.
