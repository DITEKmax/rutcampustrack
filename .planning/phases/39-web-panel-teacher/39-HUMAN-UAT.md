---
status: partial
phase: 39-web-panel-teacher
source: [39-VERIFICATION.md]
started: 2026-04-07T12:00:00Z
updated: 2026-04-07T12:00:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Journal Grid Visual Rendering
expected: Log in as TEACHER, navigate to /teacher/journal, select group and subject, set date range. Grid renders with sticky student name column on left; columns are date+lesson pairs; each cell shows 2-char symbol (б, н, у, сп, —) on color-coded chip; horizontal scroll keeps student column in place; vertical virtual scroll handles 30+ students smoothly.
result: [pending]

### 2. Stats Charts Visual Rendering
expected: Log in as TEACHER, navigate to /teacher/stats, select a group. Loading bar appears, then per-subject stacked bar charts render with 4 color-coded datasets (green/amber/purple/red); OverallStatCard shows total lessons count and attendance percentage; chart legend appears at top.
result: [pending]

### 3. Filter Cascading Behaviour
expected: On journal page, select Group A — subjects dropdown shows only Group A's subjects; switch to Group B — subjects dropdown updates to Group B's subjects only. Same cascading on stats page.
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps
