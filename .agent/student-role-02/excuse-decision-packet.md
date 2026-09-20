# Excuse selection source decision — pending fresh Sol xhigh

2026-09-07. Read-only consultant; no files/children. Owner explicitly authorizes Sol xhigh for substantive source decisions. Complete student PWA+TMA scope; do not expand roles or alter accepted design.

## Goal
Resolve final excuse reason taxonomy and lesson selection rules before freezing request API/UI.

## Context/evidence
Owner design source is final Figma VgVjQYWILLG9AC7Eh12VMk board4572:3062; later-dated accepted decisions take precedence. Root inspected captured originals. Final dropdown `design-context/4781-164.txt` contains Болезнь, Медицинское обследование, Участие в соревнованиях, Семейные обстоятельства, Другое. Older canonical stories describe different values. Existing ExcuseType contains ILLNESS, SUMMONS, UNIVERSITY_ORDER, EXEMPTION, FREE_ATTENDANCE, OTHER. Cancelled free-attendance story is excluded from the current role.

## Relevant scope
Reason taxonomy, required Other comment, multi-lesson excuse eligibility and distinction from individual manual attendance petition. Read `docs/architecture/reference-rutcampustrack-design/backend-conflicts.md`, canonical `docs/research/reference-rutcampustrack-design/knowledge/job-stories.md`, request final frames4610:848937/849007/849072 and4781:164, existing ExcuseService/LateCheckinService and DTOs.

## Required behavior
Return a dated decision with concrete originals/lines, exact proposed enum mapping, server validations and any truly unresolved owner decision. Root accepts into contract. Keep reasons distinct from mechanism type: automatic geo request, single past н-to-+ petition and multi-lesson у ticket.

## Constraints
R2: five single past-lesson submissions per semester consumed at submission including repeats; auto requests separate/unlimited; у unlimited. R11: blockage forbids geo/manual н-to-+ but permits у. No invented design. No backwards compatibility requirement for old users, but this is not permission to delete data or break other roles.

## Existing patterns
Current ExcuseService.createTicketInternal checks existing active ticket, own lesson group, and rejects a present attendance record unless ABSENT; absent record is currently allowed. validateLessonIds checks existence/group only. These are code facts, not acceptance criteria.

## Acceptance criteria
Resolve final five reason labels vs old taxonomy and explain future/current/past/cancelled/present/absent/blocked/pending selection behavior from originals. Identify whether reason-specific validations exist. Do not invent eligibility merely to fit current code.

## Verification
Read original documents/code, cite paths/lines and distinguish owner decisions from consultant inference. Consultant is not sole independent reviewer of resulting implementation. Runtime N/A for read-only decision.

## Do not
Do not write code/docs, redesign UI, change approved rules, expand into other roles, or conflate source resolution with product PASS.
