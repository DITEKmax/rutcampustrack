# Compact correction packet r2

Risk: S3. Role: fresh bounded correction writer and sole product writer in the
requests-bff-contract worktree; no children. Base revision:
d3c31acb8cce53791a4981e5858a37d44fdc9a0e.

## Goal

Restore the accepted P1 NotificationResolution model dependency while
preserving the current commentRequired producer implementation.

## Context / evidence

The frozen attendance-app:test command previously failed with exit 1 before
tests because StudentRequestService.java lines 58, 437 and 484 could not find
StudentRequestModels.NotificationResolution. Frozen target before SHA:
A6356840FAF4D34EF3B0E1022DC3CEACBD82AE2CEB92943FDD4198A578EA5AA9.
Accepted P1 source in sibling worktree
..\requests-notification-authority has SHA
142609485B22E52C9A6AA2506D332FD42E8F37CFEC178FCC22910777C9CB6D10 and contains
the canonical record after RequestDetail.

## Relevant scope

Product scope is exactly
services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestModels.java.
Evidence scope is exactly .agent/student-requests-comment-required-c/r2/**.
Foreign dirty work remains untouched.

## Required behavior

Add exactly the accepted record with fields in this order and type:
long groupId, long studentId, String studentName, RequestDetail detail,
including the accepted two-line Javadoc. Preserve every existing target
declaration, especially
ReasonOption(ExcuseType code, String label, boolean commentRequired).

## Constraints

No StudentRequestService, proto/OpenAPI/generated TypeScript, config,
lockfile, tests, Docker, Gradle or product runtime changes. No cleanup, reset or
commit. No new fields and no redesign.

## Existing patterns

Transplant the block immediately after RequestDetail, matching the accepted P1
original byte-for-byte. The target ReasonOption boolean is a deliberate
combined dependency and remains the target form.

## Acceptance criteria

Exactly one NotificationResolution exists and its fields/order/types/Javadoc
match P1. ReasonOption retains commentRequired. Final product SHA:
C5FF83BB1ABA886BA89DA94CD0A2832D7F88E566DBD37B3E2E820A2AD4C76EDD. Source SHA
remains the frozen P1 SHA. Product diff is insertion-only relative to the
pre-correction target; no other product path is changed by this leaf.

## Verification

Use static/hash/diff/scope guards only. Record commands and exit codes. Runtime
is explicitly NOT RUN (N/A); root reruns the affected frozen command set.

## Do not

Do not copy the whole P1 model, drop commentRequired, edit other product/test
sources, touch shared contracts, claim runtime, or escalate to Terra.