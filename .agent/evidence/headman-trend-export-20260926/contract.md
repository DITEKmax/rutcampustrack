# Headman trend export contract

## Goal
Let a current-group assistant with `VIEW_STATS` download the selected SEMESTER, WEEK, or SUBJECT attendance trend as true PNG bytes or self-contained HTML, in PWA and TMA.

## Context and evidence
The existing trend API and graph use one server-owned point grid and two metrics. `HeadmanStatsService.trend` obtains the current group from request context and rechecks `VIEW_STATS`. The existing ticket flow is an allowlisted capability with a fixed Gateway destination. Wireframe: `docs/wireframes/headman/113-headman-stats.md`, sections 4.7 and 4.10.

## Relevant scope
Headman stats feature in mobile-core; attendance report contract/controller/service; Auth typed ticket selector; Gateway typed dispatch; only tests that exercise these behaviors. No `App.vue`, `index.ts`, notification, protocol, Academic, Student-detail export, or MAIN changes.

## Required behavior
Export reruns the authoritative trend query for the ticket selectors. The ticket contains no group or recipient override. Preserve existing ticket TTL/size/content-type guards and HEADMAN_STATS exports. PNG is a real image with Cyrillic-capable labels, 0–100 axes, both series, legend, periods, and distinct null gaps. HTML is self-contained, escapes server values, charts the same points, and includes an accessible table with numerator/denominator. PWA uses direct download; TMA uses the existing native ticket capability.

## Constraints
Do not weaken role/permission checks, change global security/proxy behavior, accept caller-supplied SVG/HTML, add unrelated dependencies, alter existing export semantics, or overwrite foreign work.

## Existing patterns
Reuse `HeadmanStatsService.trend`, the typed `ReportDownloadTicketRequest` allowlist, fixed Gateway dispatch, `ReportDownloadPort`, the current chart colors/metric labels, and the existing attachment response pattern.

## Acceptance criteria
All selectors render the same point keys/percentages/null gaps as the trend JSON. Both formats preserve both series and period labels; HTML exposes each metric's numerator and denominator. Unauthorized/current non-`VIEW_STATS` users are denied through the existing trend service. PWA and TMA choose their existing respective download adapters.

## Verification
One focused Attendance renderer/export test and one focused ticket/Gateway or feature-level selector check, plus scoped frontend type/lint checks. No full suite. Gradle/Docker require a fresh HEAVY lease. Record exit codes and runtime evidence after checks.

## Do not
Do not change trend-query semantics, caller group/recipient data, student detail export, other app shells, existing report permissions/formats/TTL, or integration MAIN. Do not run broad suites, create a harness, or push/deploy.
