# I1 integration summary

Status: `BLOCKED_AFTER_H19_OUT_OF_SCOPE_FAILURE` pending root acceptance decision.

The target branch `codex/v2-integration-a2-l3` is at exact E
`b8220ac92125a8afa37598b270aa4fab7aa1f470`. The source union is frozen at 18
product paths: accepted A2's 14 paths plus L3's four non-overlapping paths;
`proto/academic.proto` is the sole deliberate composition. Seventeen exact
copies match source hashes, and the composed Academic proto has final SHA256
`2EF807ACD1E3A31057A553B01EAFE466ABA87BBC16D62B5D4C30C165C9CBCD32`.
The canonical 18-path union manifest is
`EB831B86E3E61559B4B9B3755AD36F69F582039CB6D460F88B003544E210DC85`.

## Criteria/evidence

- Source provenance, base/source/final hashes and ownership are in
  `source-freeze.md` and `docs/sources/manifest.yaml`.
- Scope exactness check passed with exit code 0: exactly 18 product paths and
  no missing/extra path.
- Proto ownership check passed with exit code 0: binding declarations/messages
  occur only in Schedule; Academic retains A2 resolver, Homework and map
  declarations and reserved tag 2.
- `git diff --check` passed with exit code 0.
- H19 canonical15 was run once with the frozen no-daemon/no-parallel/
  max-workers=1/no-problems-report flags. It exited 1 at the first actual
  failure, `:services:shared:shared-outbox:compileJava`, after Schedule
  `generateProto` and several shared dependency compile tasks had passed.
  The task emitted 13 unresolved symbol/package errors and 4 warnings in
  out-of-scope shared files. The causal classification remains unresolved because
  no classpath/output diagnostic was run; this is not evidence of an I1 source
  defect. Raw log and exact executed/unreached task lists are in `checks.json`.
- H20 then ran only `:services:shared:shared-outbox:compileJava` with
  `--no-build-cache --rerun-tasks` under the approved narrow escalated context.
  It passed exit code 0 with 3/3 dependency tasks executed; referenced class
  outputs were readable before the run. This narrows the H19 failure to
  execution/cache/task-topology context while leaving the sole causal factor
  unresolved. No source, ACL or configuration correction was made.

## Runtime evidence

No application runtime or Docker/Testcontainers resource was started. Focused
A2, Schedule security and BFF OpenAPI tests remain pending the conditional H21
canonical15 gate. No cleanup was required. Real transfer/grant behavior and
production operations remain outside this integration evidence.

## Diff and limitations

The product diff contains only the frozen 18 paths. Metadata is limited to the
target AGENTS pointer, `.agent/integration-i1/*`, and the narrow provenance
manifest. The root checkout and A2/L3 source worktrees remain preserved with
their pre-existing status. H19 did not reach the five app compile targets or
focused test gates; root must decide whether a bounded out-of-scope diagnostic
is warranted before any further check. No source widening, blind fix, Terra
escalation, push, deploy or main merge occurred.
