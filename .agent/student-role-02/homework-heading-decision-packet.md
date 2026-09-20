# Homework completed heading — bounded source decision pending slot

07.09.2026. Fresh Sol xhigh read-only per owner authorization. No implementation changes until decision; independent four-defect API repair continues unchanged.

## Goal
Resolve the exact meaning/data requirement of final heading «Выполнено сегодня» before common Homework UI implementation.

## Context/evidence
Final4601:848636 (PNG, original/refreshed design context) contains that heading with two completed tasks. Root reread live original. Other finalframes4601:142/848562,4788:146,4922:343 show chronological lesson dates, previous tasks and inline disclosure. Existing source freeze accepted chronological feed; older registry order=completion-first/date-asc and personal boolean. No original source found defining completion-date grouping. Current frozen API GET semester/from/to/serverNow/items fields include lessonDate and completed boolean only; PUT returns id/completed. Academic HomeworkCompletion entity already stores completedAt. Root source capture note homework-render-note.md distinguishes unrelated screenshot/title export anomaly; preserve actual visible title.

## Relevant scope
Read exact five Figma extracts/images, docs/design/figma-spec-mobile.md final02.09 sections, registry HomeworkList and current HomeworkCompletion/proto/BFF files in homework-api worktree. Read-only; no children. Do not touch ongoing repair or decide unrelated statistics.

## Required decision
Does heading group tasks by action completion date (then requires server completedAt and a defined feed/query rule for past/future tasks completed today), or label completed tasks in today's lesson-date group as a mock state? Choose one bounded interpretation grounded in sources, clearly mark inference. Ensure no false label for tasks completed yesterday, no invented local completion history, no unrelated dashboard/time tracking. If API needs fields/query adjustment, specify smallest precise contract delta and tests before UI.

## Constraints
Do not change accepted Figma wording/composition, temporal truth, chronological feed or owner scope. Do not assume boolean can establish completion time. No client device-time authority or persisted mutation outbox.

## Existing patterns
Server Moscow date/active semester bounds, private per-student completion record with timestamp, desired-state idempotent repeated true preserves existing completedAt, false removes completion.

## Acceptance criteria
Decision handles completing old/future assignment today, completion yesterday, reload, repeated true, undo/redo and historical view with exact header/date semantics. Clear minimal API delta or explicit evidence why no delta is needed.

## Verification
Source consultant runtime N/A; return concise decision with original file/node locations and inference; root freezes before implementation/review of any delta.

## Do not
No product writes, further agent layers, user question unless truly unresolvable within owner design, no unrelated new UI/API behavior.
