# I1 integration packet — accepted A2 + L3

Date: 2026-09-14. Assigned developer: fresh `gpt-5.6-luna`, `max`; one writer
in this isolated worktree. Rules SHA256:
`B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.

## Goal

Integrate only the accepted A2 resolver and L3 binding ownership correction on
the exact E base `b8220ac92125a8afa37598b270aa4fab7aa1f470`, producing a reviewable
union for root. This is source integration evidence, not main merge, deploy, or
a full product-complete claim.

## Context/evidence

The A2 source worktree is `codex/v2-access-scope` at E with canonical 15-file
manifest SHA256 `0A033E8E07EC57AD6219844512D0232C666BC71385F3A844D24F4C28C13A7923`
and accepted A8/H18 evidence (34 unit cases, 3 PostgreSQL cases). The L3 source
worktree is `codex/v2-binding-proto` at E with accepted five-file product diff,
L3 source proof and H9 five-module generate/compile evidence. Both source
worktrees were read-only inputs and were hash-checked before transfer.

## Relevant scope

Target: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-integration-a2-l3`,
branch `codex/v2-integration-a2-l3`, base E. Product scope is the exact 18-path
union: 14 A2 paths (academic resolver, one dependency line and focused tests)
plus four L3 paths beyond the overlapping `proto/academic.proto` (schedule
proto and three approved tests). Own metadata is this directory, the short
target `AGENTS.md` pointer, and the narrow `docs/sources/manifest.yaml`.

## Required behavior

A2 remains additive: signed student own history/subjects and separate rank
cohort with typed errors, current authority checks, repeatable-read snapshot,
existing Homework/map registrations and all accepted tests. L3 removes the
Schedule-owned binding import/declarations from Academic and places identical
declarations/messages in Schedule, retaining qualified `LessonInfo current_lesson`
tag 3 and all wire tags/enums. The three L3 fixture corrections retain their
security/OpenAPI assertions and complete current JWT fixture semantics.

## Constraints

Preserve the dirty root checkout and both source worktrees. Copy only approved
product paths after SHA verification; compose the overlapping Academic proto
from A2 additions plus L3 removals. Do not copy source AGENTS/evidence wholesale,
generated output, lockfiles, migrations, data, cache, secrets, or unrelated
packages. No children, Terra, full access, push, deploy or main merge.

## Existing patterns

Use repository Gradle wrapper, Java 21, Gradle 8.12 and existing five-module
protobuf generation. Heavy commands are sequential with
`--no-daemon --no-parallel --max-workers=1 --no-problems-report`; testcontainers
uses the existing PostgreSQL 16 harness and named disposable cleanup.

## Acceptance criteria

Exactly 18 intended product paths plus declared integration metadata; source
manifests and source worktrees stay unchanged. Academic and Schedule proto
graphs contain one owner for binding declarations, preserve A2 fields/reserved
tags and existing Homework/map declarations. Five consumer modules generate and
compile; the focused A2 unit/PostgreSQL tests and changed Schedule/BFF tests
pass with nonzero JUnit counts. A fresh independent Sol review follows stable
checks; unresolved critical findings block acceptance.

## Verification

After this source union is frozen, execute H19 in the single heavy lane: the
canonical 15 generate/compile tasks for schedule, academic, attendance,
mobile-bff and document-renderer; then exact A2 three-unit selector plus
`StudentProjectionQueryAdapterIT`; then changed Schedule
`ScheduleUserContextFilterIT`/`ScheduleUserContextFilterStrictModeIT` and BFF
`OpenApiSnapshotIT` tasks derived from their build definitions. Record revision,
command, exit code, environment, logs, JUnit XML counts and cleanup in
`.agent/integration-i1/checks.json`. Stop on the first actual failure and report
bounded evidence before any scope extension.

## Do not

Do not import a whole preservation commit or source metadata, manually edit
generated files, run snapshot update, count `compileTestJava` as executed tests,
rerun stale source checks as current union evidence, fix unrelated WARN/ERROR,
or claim runtime/production transfer beyond these checks.
