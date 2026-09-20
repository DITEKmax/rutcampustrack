# B0 compact contracts resume — baseline guard

Captured: 2026-09-09, before the first product-source mutation in this resume.
Role: fresh bounded B0 implementation leaf, sole writer for the exact remaining
25 paths. Risk: S3. Workspace: C:\Users\maksd\.codex\worktrees\34a5\rutcampustrack.

## Scope and authority

- Frozen contract: union/contract.md, SHA256
  C9C4DC2C4BE2229AFF3CEDB27E0E43634329EBA5AC83CE42A889E34C33DAFC74.
- Implementation packet: union/implementation-packet.md, SHA256
  85387CEDC6B11A03ECB7301C10E5D437798A6DD1FBFC61A83ABB6E4DE4513D6F.
- Addendum: union/implementation-addendum.md, SHA256
  FF05B4ED18F0E230B0543D756F49262956F08938A4980A7DD7067161B4CA450B.
- Base revision: 8002b9ea4356b10779c5bb9a6d99746d32d78ae2.
- Ownership: only proto/academic.proto, proto/schedule.proto, and the 23
  new standalone Java declaration paths in the frozen contract. Evidence is
  written only below this resume directory; current-status.md is updated once
  after source freeze.

## Before-mutation checks

Environment: Windows PowerShell, Java/Gradle/protoc not invoked. Commands and
results:

| Check | Command | Exit |
|---|---|---:|
| HEAD | git rev-parse HEAD | 0 |
| target inventory | guarded Test-Path/Get-FileHash loop over the 25 reserved targets | 0 |
| foreign worktree | git status --short --untracked-files=all | 0 |
| owned preimage status | git status --short -- <25 paths> | 0 |
| academic proto preimage | Get-FileHash proto/academic.proto -Algorithm SHA256 | 0 |
| schedule proto preimage | Get-FileHash proto/schedule.proto -Algorithm SHA256 | 0 |

Evidence: HEAD 8002b9ea4356b10779c5bb9a6d99746d32d78ae2; both proto files are
present; all 23 Java targets are ABSENT. Only proto/academic.proto has an
existing owned delta. proto/schedule.proto and all Java targets are clean or
absent. Foreign dirty/untracked work is present and retained.

Academic proto preimage: SHA256
04FF22416CB97BD79075F481E8DB5169197F88E18EE88D2EE232800315486525, 5305
bytes, mtime 2026-09-08T07:42:25.9960420Z.
Schedule proto preimage: SHA256
09A34562CB7DFF59B2EF56045DAEE81DD6D10A00D01A6F3480041D4C1FB46608, 4852
bytes, mtime 2026-09-07T21:21:30.0902545Z.

The existing academic proto delta is the accepted homework-completion change
(SetHomeworkCompletion, completion-day fields, and completed_at=10) and is
preserved. Existing SQL, generated files, attendance transport, numeric
evidence, and foreign dirty paths are outside this writer's scope.

## Required result

Implement only the finite additive wire ledger and standalone Java declarations
from contract.md and implementation-addendum.md. Preserve existing proto
tags/RPCs, including reserved LessonResponse.teacher_id tag/name 5 and
HomeworkInfo.completed_at tag 10. IDs, versions, and revisions remain decimal
string Java contract values; no controllers, services, generated output,
build-file edits, SQL edits, or runtime behavior are introduced.

## Guard limitations

This is a source-freeze guard. Gradle, protoc, migration integration, product
runtime, and independent review are pending root's later leases. No product
runtime is applicable to declaration-only source freeze. Any unexpected
preimage delta found immediately before a proto write stops that file only and
is reported to root.
