# Guarded main fast-forward preparation evidence

Дата проверки: 2026-09-06. Среда: Windows, PowerShell 7 (pwsh -NoProfile),
Git worktree; product/runtime не запускался.

## Frozen state

- Root revision: 87784165874e2da6fc261abc1c01584e24624289.
- Root branch: codex/materials-transfer.
- Target branch: local main at the same base revision.
- master: absent.
- Active integration HEAD at final preflight: f383c10a6e0304c4f68aab794612d1bd89b509a8.
- The integration writer advanced from 1f73c1ec81186ed7dd0cf22451ff7282e09dea5d
  while this preparation was being recorded.
- Earlier active integration HEAD during preparation: 623460ef03a8ff33264acd2d4a98d908a8f86b0e.
- Requested/current survey SHA from the packet:
  f85a4d27b9093bad6d60096552f306e8f3e2222b.
- Staged diff check: exit 0; no staged paths.
- Existing tracked dirty paths: docs/INDEX.md present with SHA-256
  74dc54af438a6160d90c9cf5b94f91a501658cddb5b07e03bc9ceb30ff4074b9;
  skills-lock.json absent because it is the existing tracked deletion.
- Incoming overlap with those tracked dirty paths: 0.

## Collision survey

The bounded survey compares incoming paths from
git diff --name-only --no-renames 87784165… <FinalSHA> with root tracked paths
and checks only exact existing leaf paths. It does not recursively copy the
untracked tree and does not print file contents.

For f85a4d27b9093bad6d60096552f306e8f3e2222b, the guarded dry-run exited 0,
reported 287 changed paths and 76 collisions: 72 identical and 4 different.

For the active integration
f383c10a6e0304c4f68aab794612d1bd89b509a8, the guarded dry-run exited 0,
reported 325 changed paths and the same 76 collisions: 72 identical and 4
different. Earlier preflights at 1f73c1ec and 623460ef reported 313 and 298
changed paths respectively, with the same collision counts.

The four different owner leaves for the active integration HEAD are:

| Path | Owner SHA-256 | Incoming Git blob |
| --- | --- | --- |
| .agent/vertical-js-student-01/be-progress.json | 143dc698ab6a28c4a82c63a8fbfc121c2975361241c38d85ba564d7610886183 | 41de52cfc64bc4a38dbedfe89d61471b597264e1 |
| AGENTS.md | df68ad6ee6a15eaa3dce3e7d624c35dad837acc7e1d9dc7afc23db51f23577e0 | 556e740010778b5ab519ca3405a0f61ead572fed |
| docs/agent-workflow.md | 315fbbfcbb3f141bed520eee40dd8caaaf72c46710140e6be694dd2d85037c2a | ccd6218a92ed40e189ddf2d282a2786089b1428d |
| docs/implementation/parallel-development.md | 9752c819f99db5cc337ca4e0008d816ad2e7dc2013f8917c02e30bdcdb3d1570 | 7dd59992f780e716587e6148ea85a9d0047dbebc |

The old survey result 76 = 58 identical + 18 different is not reproduced at
the current bytes; the current result is recorded as 76 = 72 + 4.

## Instruction conflict

Root AGENTS.md and docs/agent-workflow.md are untracked owner files. Their
SHA-256 values differ from the active integration copies, and their write times
are later:

- AGENTS.md: root 2026-09-06T16:50:13.908008Z; integration
  2026-09-06T13:05:33.1610901Z.
- docs/agent-workflow.md: root 2026-09-06T17:12:35.3515202Z; integration
  2026-09-06T13:05:33.1684294Z.

The script reports both addresses and requires explicit
-AllowInstructionConflicts for a future apply. No original instruction file
was overwritten in this preparation.

## Guard behavior evidence

The script is dry-run by default. It refuses a changed root branch/HEAD,
missing or non-descendant FinalSHA, a moved target main base, in-progress Git
operation, staged changes, tracked-dirty incoming overlap, directory
obstructions, reparse points, or owner-byte drift before a move. A future
-Apply copies and hashes every one of the 76 owner leaves, writes an exclusive
manifest, then moves exact leaves one by one, runs switch main and merge
--ff-only, restores and verifies owner SHA-256 values, verifies the
tracked-dirty snapshots, and verifies final main HEAD.

No -Apply invocation, branch switch, merge, move, delete, stash, clean, reset,
product runtime, or worktree metadata operation was executed here.
