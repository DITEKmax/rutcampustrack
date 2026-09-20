# I2 integration summary

Status: `READY_FOR_ROOT_REVIEW` for the bounded 34-path union and H31 checks.

The target `codex/v2-integration-a2-l3` remains based on E
`b8220ac92125a8afa37598b270aa4fab7aa1f470` and preserves the accepted I1
18-path union. Exactly 17 accepted R5 tracked paths were transferred from the
read-only `codex/v2-requests-contract` sibling, producing an exact 34-path
product union. The canonical path/hash manifest is
`i2-union-manifest.sha256` with SHA256
`32031090D593BBC8B53869590F14D90386A97B1AD4786CDC7B6C16586C8CC266`.

The only overlap was `OpenApiSnapshotIT.java`. A no-index source comparison
showed exactly the two R5 `requestBody.required` assertion lines. The target
now equals the accepted R5 file at SHA256
`D0D715C22A77CBADFE61177454428B606A5F0476359C2B8FD879573A4A221C25`, while
the I1 UUID import and nine-argument JWT fixture remain intact. The generated
OpenAPI snapshot and TypeScript artifact match accepted R5 hashes
`D4F97E...` and `5D697D...`; the snapshot parses and reports
`requestBody.required=true`.

Source manifest hash, source/target hashes, overlap evidence, exact scope count,
JSON and diff checks, runtime boundary, limitations, and command exit codes are
in `i2-checks.json` and `i2-source-freeze.md`. The tracked `.env.ci` row was
copied opaque; its values were never opened, logged, or printed. No R5 evidence,
source instructions, build/cache output, or unrelated paths were imported.

I1's `ERRORED_CAPACITY_METADATA_CHECKPOINT` remains historical. This I2 packet
does not invent completion for the previous author. The six known I1 time
metadata changes plus the required request body are recorded as the bounded
seven OpenAPI deltas; no redesign or product decision delta was required.

Under the later explicit root H31 exclusive GO, the exact BFF OpenAPI selector
passed with XML `tests=4 failures=0 errors=0 skipped=0` and captured raw log
`h31-bff-openapi.log`. The frontend guard passed after `npm ci --offline` from
the existing cache; it reported the accepted spec hash. Git Bash syntax checks,
the 35-caller `--env-file` inventory, and synthetic PROD/E2E Compose config-only
checks all passed. No snapshot update or source regeneration ran.

## Runtime and remaining gates

Runtime evidence is `PASS_BOUNDED_H31`: BFF integration test context started
and shut down cleanly; Compose checks used `config --quiet` and started no
services or containers. No Testcontainers, deployment, TLS, or production
operation ran. Fresh independent full integration review remains a future
separately allocated integration lease.
