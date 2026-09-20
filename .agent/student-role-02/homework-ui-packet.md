# Homework shared UI — frozen packet

2026-09-07; S2. Dispatched to fresh homework_shared_ui Luna max. Frozen baseline and explicit ownership are in the dispatch appendix; root owns this packet.

## Goal
Implement final student homework feed and interaction in shared mobile-core for PWA and TMA.

## Context/evidence
Final Figma file VgVjQYWILLG9AC7Eh12VMk, frames 4601:142, 4601:848562, 4601:848636, 4788:146, 4922:343. Exact extracted originals and all five PNGs in parent `.agent/student-role-02/design-context/`; read homework-render-note.md for confirmed screenshot-export/title discrepancy and accepted heading/date contract in homework-date-delta-packet.md. Root inspected originals. Final feed supersedes old calendar modes; completion is private and reversible. Read active-contract.md and accepted source decisions.

## Relevant scope
Fresh Luna max developer, separate worktree from frozen integrated revision. Own homework feature Vue/PCSS/query/domain and focused tests, plus public exports as explicitly assigned at dispatch. No API/generated/config/lockfile or shell adapter ownership. Shell adapters follow after shared feature acceptance. No children; preserve other work.

## Required behavior
Use typed StudentApi homework read and desired-state completion. Render «Выполнено сегодня» from server completedAt compared to serverNow in Europe/Moscow, grouping once independently of lessonDate; other items remain chronological lesson-date groups, incomplete before complete within a date. Do not infer completion date from boolean or device time. Current feed includes today-completed union; historical-only ranges preserve date semantics. Provide previous dates, return to today, inline text disclosure, optional materials action, completion swipe and keyboard/screen-reader equivalent, reversal and pending/error/recovery. Match all five final states. Use server dates/semester bounds; no device-time assumption for feed dates. Supported absolute external links use shell callback and preserve original target. Unsafe link data cannot navigate. Keep one query owner; duplicate commands and lost responses remain recoverable. Expose offline read-only and disable mutations, leaving persistence to the PWA adapter. Do not imply completion on a failed or unknown ACK.

## Constraints
Vue strict TypeScript, separate PCSS/rem, semantic/component tokens. Read frontends/AGENTS and rutcampustrack-design references. Figma read-only. No new product tokens without resolved requirement. No copying generated React/Tailwind. Do not introduce library/config changes. Host APIs remain outside core.

## Existing patterns
Use integrated MobileShell/nav boundaries, StudentApiError and existing query conventions. Frozen GET `/api/v1/student/homework?from&to` gives semester/from/to/serverNow/items; PUT `/api/v1/student/homework/{id}/completion` accepts `{completed}` and returns `{id,completed,completedAt}` (nullable timestamp); each GET item also has completedAt. IDs strings. Transport/generated contracts belong to API writer.

## Acceptance criteria
All five Figma states represented by deterministic development data and working interactions. Completion/reversal, repeated activation, failed response/retry, disclosure and previous/today navigation tested; no foreign query reuse after identity change. Dark 390x844 compared with originals, light plus larger root font and narrow/wide overflow checked. Keyboard equivalent performs same command as swipe. Missing link has final no-materials composition. Scope remains shared feature.

## Verification
Record exact baseline/diff, command, exit, environment and evidence in worktree `.agent/student-role-02/homework-ui/`. Run scoped meaningful unit/component checks, typecheck, scoped lint and both builds. Browser fixture evidence is preliminary. Real API PWA and TMA adapter scenarios and fresh independent Sol high review remain required after integration; do not claim full-role PASS.

## Do not
Do not commit before scoped acceptance; do not integrate main, change other roles, add unrelated calendar/ranking/QR UI, cache auth tokens, build a mutation outbox, publish/deploy, or stop the root task. Report localized token/source gaps promptly while continuing independent work.


Root additionally preserved46originalSVGs(15uniquecontenthashes) from5finalHomeworkcontexts in design-context/homework-svg; bindings.json mapsframe/constbinding/file/hash. No rasterediting/Figmawrites. Use neededcanonicalassets only; avoidduplicatingalreadyexistingshellicons. ActualVueadapters are frontends/pwa-vue and frontends/tma-vue; oldpwa/mini-app Reacttrees are not thisUIwriter's scope.

All final student SVG assets are now preserved in design-context/all-svg:276 URLs,83 unique contents,119113 bytes. manifest.json maps frame/binding/sourceURL/file/SHA256; content-index.json groups duplicates. Reuse canonical content rather than copying duplicate icons. Homework-specific46 originals remain available too. These assets are reference material, not an automatic token or visual acceptance.

07.09.2026 root reopened COMPONENT_REGISTRY.md lines1343–1374: 02 September decision removes separate S-07/screen15 route; expanded details belong to MobileHomeworkNow. materials=none hides the action and leaves neither empty container nor 'материалов нет' copy. Apply these explicit states rather than old desktop HomeworkItem/header-action notes.

Root also verified canonical tokens-v2.json dimension font/size/title-screen=1.5rem (line726), font/size/title-block=1.125rem, font/leading/normal=1.5. Existing frontend tokens.pcss exposes title-block under a generic title name but does not yet expose title-screen. At UI dispatch, root may assign this writer tokens.pcss additions LIMITED to aliases of already existing canonical semantic/dimension/component paths, with documented source mapping; no new product tokens/values and no unrelated token corrections. Prefer correct title-screen mapping over reusing same-number space/6 or Today-specific tokens. Shared stylesheet ownership must be explicit and isolated; dependency/config writer does not own it.

Root reread all five context names: 4788:146 is 'далеко от сегодня' and shows historical August28/29 cards with a 'Сегодня' return action. Treat the month/dates as representative content, not authority to bypass server semester bounds. Preserve scroll/return-to-today behavior and inline anatomy; do not infer a new calendar route or unbounded cross-semester query from sample dates. Fixture semester/date values must be internally consistent with the frozen API.

Root query-boundary clarification after reading current use-today.ts: that older helper uses a literal ['student','today'] key. Do not copy that unscoped key into Homework. The new Homework query must accept an explicit authenticated scope from its adapter (user, active role/group/semester and reset generation as needed), include it in the query key, and capture the same scope for completion callbacks. A delayed request/ACK from a prior identity must not update the currently displayed owner. Test identity replacement while GET/PUT is pending. This does not authorize changing Today or inventing server authority; scope is a cache-isolation key, never an authorization credential. Missing scope disables authenticated read/write. Persistence remains a later adapter concern.

At dispatch, explicit public export ownership is limited to appending Homework feature/composable/types exports in mobile-core/src/index.ts without altering accepted shell/API exports. API types are already exported via api/types and contain StudentHomework, StudentHomeworkItem and StudentHomeworkCompletion; do not duplicate those generated contracts.

External materials boundary: for the shared web/Telegram implementation, support absolute http and https URLs. Validate scheme/absolute form without rewriting the original target passed to the shell callback. Reject script/data/file/relative and protocol-relative targets; do not infer support for custom app schemes. If stored data is unsafe or unsupported, prevent navigation and expose an accessible recoverable explanation instead of opening it. Null link is the accepted materials=none composition. No backend link rewrite or multiple-link model is authorized by this UI boundary.

Root shell-boundary clarification: MobileShell already accepts navItems, activeId/route, navigation and host. Homework must reuse this boundary and accept the adapter-owned navigation data/events needed to switch sections. Do not copy TodayScreen's hardcoded disabled route list into the new feature or claim other sections work. Keep host APIs outside core and avoid nesting two MobileShell docks. Any required TodayScreen adapter prop change belongs to the later adapter contract, not this UI writer.

### Dispatch freeze — 07.09.2026
This appendix freezes prior staged fields without changing the nine-section behavior contract. Baseline d3c31acb8cce53791a4981e5858a37d44fdc9a0e, parent8002b9ea; root independently verified exact42 committed paths, source/destination84SHA0mismatch, clean integration tracked status and13 fresh integration IT tests. Sole writer target: C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/homework-ui, branch codex/student-role-02-homework-ui, created clean at this revision. Main and all other worktrees are readonly inputs.

Explicit additional ownership: append only Homework public exports in frontends/mobile-core/src/index.ts; add only needed aliases of existing canonical token paths in frontends/mobile-core/src/styles/tokens.pcss (mapping in evidence); new canonical SVG copies for this feature if not already available. No package.json/lockfile/API/generated/Today/shell component edits. Task-owned dev fixture and local Vite entry/setup may live in the worktree .agent/student-role-02/homework-ui evidence directory, so visual inspection can mount actual shared component without changing PWA/TMA Apps. Use port5181 exclusively for this fixture; do not start full backend. Runtime fixture is preliminary, real adapters/API downstream. Install unchanged npm lock dependencies locally if needed; no new dependency. Stop owned fixture runtime after evidence/handoff unless root explicitly takes ownership.


