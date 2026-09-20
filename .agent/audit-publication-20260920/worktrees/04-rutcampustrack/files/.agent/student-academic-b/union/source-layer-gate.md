# Accepted source-layer correction gate

Date: 2026-09-08. This recorded defect blocks proto/DTO edits until the
accepted source sequence is complete. It is within the existing frozen B0
contract and does not expand scope.

## Request and reproduction

The union contract requires the exact 42-file Git delta at
`d3c31acb8cce53791a4981e5858a37d44fdc9a0e`, followed by the three snapshot
manifests. The first import copied only the 81 manifest entries. Reproduction:
`git diff --name-only 8002b9ea4356b10779c5bb9a6d99746d32d78ae2` still shows the
d3 layer absent; for example `proto/academic.proto` and the d3 TodayScreen
preimage do not match their d3 blobs.

## New evidence

Root observed `TodayScreen` Git blob/current hash mismatch
(`d3` blob `909f4b4f466c53b460a9739f2a5178cece7294c`, current hash
`000a25e43cd2a0582503445f40f872587fa5b94c`) and `proto/academic.proto` still
at the 8002 source. The 42-path delta has three overlaps with the imported
dependency snapshot; dependency remains the frozen winner for those three:
`docs/openapi/mobile-bff.json`,
`frontends/mobile-core/src/api/generated/mobile-bff.ts`, and
`services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java`.
The remaining 39 d3 paths are non-overlapping accepted imports.

## Correction and root decision

Restore the 39 non-overlap d3 paths from the exact d3 Git blobs after a fresh
per-path destination guard. Keep the three dependency-winning overlap bytes
unchanged. Recompute 42-layer and full-union SHA evidence, then continue with
proto/DTO edits. No live source, numeric evidence, UI worktree, or unaccepted
transport is imported. This correction is explicitly approved by root as a
late guarded import; no Terra escalation is requested.
