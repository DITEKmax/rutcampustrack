# Homework shared UI — scoped compact contract

Revision baseline: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`  
Branch: `codex/student-role-02-homework-ui`  
Scope owner: fresh homework shared UI leaf; parent root retains integration and role status.

## Goal

Implement the final student homework feed and interaction in shared `frontends/mobile-core` for both PWA and TMA consumers.

## Context/evidence

The frozen parent packet is `.agent/student-role-02/homework-ui-packet.md`; final Figma references, extracted SVGs and accepted date/material decisions are in the parent `design-context/`. The feed API returns server dates, `completedAt`, semester bounds and desired-state completion ACKs.

## Relevant scope

Own `mobile-core` homework domain helpers, Vue Query composable, `HomeworkScreen.vue`, its PCSS, focused tests, public homework exports, canonical homework SVG copies and limited existing-token aliases. Shell adapters, API/generated contracts, Today/shell components, configs and lockfiles remain outside this worktree scope.

## Required behavior

Group `completedAt` matching `serverNow` in `Europe/Moscow` under «Выполнено сегодня», deduplicate by assignment ID, group remaining items by lesson date, and keep incomplete items before completed items within a date. Support bounded previous ranges, return to today, disclosure, safe absolute HTTP(S) materials, swipe plus keyboard completion/reversal, pending/error/retry, scope isolation and offline read-only behavior.

## Constraints

Strict TypeScript, Vue/PostCSS with rem-authored CSS, existing MobileShell/navigation boundary, one Vue Query response owner, no auth-token persistence, no offline mutation outbox, no Figma writes, no new dependency or unrelated product UI. Preserve other working-tree changes.

## Existing patterns

Use `StudentApi`, `StudentHomework*` generated types, `MobileShell`, `MobileBottomNavItems`, semantic tokens and Vue Query conventions already present in `mobile-core`. Scope identity is a cache-isolation key and never an authorization credential.

## Acceptance criteria

The five final homework states and required loading/empty/error/offline/historical transitions are represented; completion/reversal, malformed ACK rejection, failure recovery, duplicate grouping, disclosure, material safety, previous/today navigation and scope replacement are covered by focused checks/runtime evidence. PWA/TMA builds remain green.

## Verification

Record exact commands, exit codes, revision, environment, runtime scenarios, diff and limitations in `checks.md`, `runtime.md`, `diff.md` and `summary.md`. Browser fixture evidence is preliminary; real adapter/API scenarios and independent review remain downstream responsibilities.

## Do not

Do not edit API/generated types, PWA/TMA adapters, Today/shell/config/lockfiles, unrelated roles or root evidence; do not commit, integrate, deploy or claim whole-role PASS.
