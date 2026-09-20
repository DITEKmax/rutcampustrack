# L5B frozen design independent review
## Goal
S3 independent architecture review of proposed durable cap/physical lifecycle protocol. Assess safety and design readiness, not product runtime acceptance.
## Context/evidence
Read RULES C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md SHA B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A; CURRENT; canonical LESSONS-L5.md v3; LESSONS-L5A-ACCEPTED-2026-09-19.md; LESSONS-L5B-DESIGN-PREP.md. L5A exact46 accepted f59b3b9951c971bde42265ae67df8754dc594a58 parentb8220ac9; Schedule accepted integration426a15b6 separate. No integration inferred.
## Relevant scope
Read-only frozen .agent/lessons-l5b-design/PROPOSAL.md SHA 04AF82B8086AD41AECB871E02F20837390ABC0350A4C250E4B0BF2AE07D7239D and sources.json SHA FAEAAA26E084F819957AD76650EBC21F8D93D4BD4BBB4784CC0B9437131D8F6B plus checks.json. Open critical exact originals independently from 42source map: V17/V25, generation/oneoff/restore/reconciler, DB constraints, Academic locks/closure, gRPC/auth, Homework binding/lifecycle, outbox and Attendance destructive consumer boundaries. No writes.
## Required behavior
Review all proposed steps for race/transaction/auth/replay/partialfailure/history loss/lockorder. Validate cap-vs-create interleavings including stale remote snapshots and firstfence creation; durable pendingoperation/rejectedreplay; everyphysical producer/restore/job; immutable occurrence/currentgeneration; historical references; crossservice consumer cutover. Distinguish source-backed owner decisions from remaining source-resolution and engineering choices. Identify actual contradictions/missing safety requirements and bounded corrections, not stylistic preferences.
## Constraints
Fresh Sol high forknone reviewer; no children/Terra/source/docs/evidence edits, no tests/runtime/Gradle/Docker or remote mutation. Not alone preserve others. Root heavy queue currently free but unallocated; no execution. Current mutable status hash is not frozen gate. No deployment/mainmerge/production migration.
## Existing patterns
V17 physical history guards and V25 immutable assignment/source authority; BEFORE_COMMIT outbox; existing authenticated service boundaries. Strict all retained physical refs date>=D block is canonical accepted consequence, not a question for reconfirmation. Restoreemptygeneration existing authority retained.
## Acceptance criteria
Return PASS or FAIL for design proposal with actionable severity/file:line/evidence/impact/reproduction interleaving/correction. Explicitly list remaining design/source decisions without presenting acknowledged OPEN implementation/runtime gates as discovered defects. PASS design is not authorization to implement unresolved choices or production readiness.
## Verification
Read-only hash/source/protocol review only, runtime N/A. Report substantive milestone after critical originals if long; final concise concrete verdict and RELEASE. No author transcript supplied.
## Do not
No implementation/review fixes, author handwave accepted as evidence, count-only race proof, guessed backfills/time/roles, hidden taskchildren, repeated metadata-only loops or premature lifecycleDONE.

## v2 recheck evidence and boundaries
Fresh reviewer, FULL proposal not patch-only. Read LESSONS-L5B-DESIGN-CORRECTION.md with exact prior findings and later source resolution. v1 snapshots preserved. Focus corrections of HIGH ownerB2 currentread pointer/generation/revision doublecheck with one retry503/missingdepsfailclosed and MED full Academiclockgraph explicit+FK+deferredtriggers/cutover. Compatible B34a5/R7/R25 source decisions resolved; laterHANDOFFB2 overrides oldMongo prerequisite. Current L5A assignTeacher/addTeacher deadlock separately staticconfirmed and correction under preparation; NOT alreadyfixed or runtimeproven. Six acceptedf59Academic originals snapshotted immutably under designfolder/sources; use these or gitshowf59, not mutable WIP as frozenbaseline. Verify source snapshot provenance/hash. No designimplementation allowed; acknowledged auth/events/time-source gates remain. Return complete fresh verdict/release. Root keeps heavy queue, reviewer no runtime.
