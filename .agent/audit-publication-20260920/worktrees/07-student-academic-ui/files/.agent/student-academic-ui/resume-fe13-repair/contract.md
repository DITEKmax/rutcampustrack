# FE13 independent review findings and bounded repair contract

2026-09-08. Root transcription of independent reviewer `/root/resume_fe13_review` final findings, followed by root repair contract. Review model gpt-5.6-sol / high; review verdict FAIL, S2 presentation. This is findings evidence, not a full conversation transcript.

## Review findings

Stable input: HEAD 8002b9ea4356b10779c5bb9a6d99746d32d78ae2; nested UI manifest SHA256 47ED86271F241599FB333E381F388F833C1839CDBCEE2E0192015E35CB27A97A, all 23 file hashes match. Browser QA SHA256 EA2266AA96C677D997459AEED3B1CF42E437CAF6C210478A20A4C61020D3C690. Scoped typecheck, ESLint and build green before review.

1. MEDIUM, attendance-screen.pcss:516. `--attendance-history-count` never set, so grid defaults to one column. Fixture 4596-365 at 390x844: 12 segments identical left=36.8/right=338 with successive vertical positions, first card height225.15 and history140. Original 4596-365.png/txt:68 specifies a 100px card with twelve horizontal full-width segments. Chronology is misrepresented and lower cards are pushed under dock.
2. MEDIUM, attendance-screen.pcss:62 and statistics-screen.pcss:90. Fixtures 4593-142 and 4603-142 at 320x844, theme dark, rootFont24: document scrollWidth311 versus clientWidth305. Native screenshots show truncated metrics and overlapping switch/dock labels. Enlarged text is unreadable and causes forbidden global horizontal scroll.
3. MEDIUM, harness/attendance-statistics/Harness.vue:101. setStatisticsTypes only changes selectedTypes. In 4603-848696, deselecting Lecture leaves aggregate65%,20/20 and identical chart path despite Lab canned projection63%,8/8. This is a harness stale-projection defect; feature must remain controlled and must not aggregate client-side.
4. MEDIUM, StatisticsSubjectDetail.vue:136, StatisticsSemesterChart.vue:85,164. Extra aggregate blocks, range control and eight-row visible legend push first type card top808.6 and second929.75, document height1131 at390x844. Original4603-848696.png shows both cards fully in viewport. Accepted selected aggregate/range remain required but do not authorize these large visible blocks. Restore source hierarchy while preserving typed behavior and accessible information.
5. MEDIUM, StatisticsScreen.vue:194. Subject buttons render only present percentage. Original4603-142.txt:144 has horizontal present/excused/absent metrics and disclosure target. Runtime examples Math68%, Programming77%, Physics64% omit the other supplied metrics. Render all three without local formulas.
6. LOW, StatisticsScreen.vue:222. Local showDock currently marks Учёт current; source4603-142.txt:199/209 selects Ещё. Wrong route and aria-current. Shared navigation remains excluded.
7. LOW, AttendanceSubjectList.vue:116 and attendance-view-model.ts:136. History aria-label values +/н/у are ambiguous; use full Russian names, preserve visual source symbols elsewhere.

Reviewer found no fetch, submit, peer payload, client aggregation, data-loss or executable security defect in standalone features. Requests-form and real API/PWA/TMA host integration remain OPEN. After correction a fresh independent reviewer must open originals and recheck affected FE13 presentation.

## 1. Goal

Repair only these seven confirmed FE13 defects. Risk S2. Fresh Luna max developer, fork none, sole writer of nested UI worktree after parent finite GO. Do not start before GO.

## 2. Context/evidence

UI worktree C:/Users/maksd/.codex/worktrees/34a5/rutcampustrack/.agent/worktrees/student-academic-ui. HEAD8002b9ea4356b10779c5bb9a6d99746d32d78ae2. Frozen23 manifest `.agent/student-academic-ui/resume-ui-repair/manifest-23-final.md`, same SHA above. Original packet/source-map at `.agent/student-academic-ui/packet.md` and `source-map.md`. Prior root QA at this directory/student-academic-ui-browser-qa.json. Root opened critical Vue/PCSS/view-model, original source texts, statistics-root-decision.md and statistics-decision-result.md and images4596-365/4603-848696. Design source directory C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/student-role-02/design-context/. All13 frame IDs listed in original packet/source-map.

## 3. Relevant scope

Exact permitted existing product paths under UI worktree:
- frontends/mobile-core/src/features/attendance/AttendanceSubjectList.vue
- frontends/mobile-core/src/features/attendance/attendance-screen.pcss
- frontends/mobile-core/src/features/attendance/attendance-view-model.ts
- frontends/mobile-core/src/features/attendance/attendance-view-model.test.ts
- frontends/mobile-core/src/features/statistics/StatisticsScreen.vue
- frontends/mobile-core/src/features/statistics/StatisticsSubjectDetail.vue
- frontends/mobile-core/src/features/statistics/StatisticsSemesterChart.vue
- frontends/mobile-core/src/features/statistics/statistics-screen.pcss
- frontends/mobile-core/harness/attendance-statistics/Harness.vue
- frontends/mobile-core/harness/attendance-statistics/fixtures.ts

New evidence only `.agent/student-academic-ui/resume-fe13-repair/**`; build-generated runtime evidence as existing build config. No extra test/helper/token/config path unless root subsequently freezes it. No unrelated reformat. Other13 product paths retain hashes. Parent34a5 SQL writer is independent; do not modify parent worktree.

## 4. Required behavior

History is one non-wrapping equal-width strip spanning card inner width with all future segments. Use content-driven layout without unset CSS variable or style literals. No global overflow or collisions at320/rootFont24; labels can wrap coherently, layout can reflow, keep full text and enlarged font. Never hide overflow or shrink font to mask defect. Detail compact header/type filters/chart/type-history composition must preserve selected aggregate and range affordance; compact aggregate placement and accessible chart point detail may replace large extra blocks. At390/rootFont16 two-type source cards fit viewport, while larger-font layouts may scroll vertically. Subject summaries display supplied present/excused/absent metrics and disclosure. Local Statistics dock selects Ещё. Full Russian history accessible names. Harness selects explicit canned projections for supported type subsets and range; no reduce/count/average/rank/bucket derivation. Retain all available type cards and reject empty type selection. Preserve H0/null/FUTURE/NO_DATA, opaque IDs, terminal/denied behavior, theme, request focus return and slot gate.

## 5. Constraints

Read applicable AGENTS, workflow and rutcampustrack-design/rct-verification skills. Russian/Onest/PCSS/rem/source tokens. Need for new semantic tokens is reported before expanding scope. You are not alone; preserve others' changes. No shared shell/form owner and no live request submission. No runtime server before root allocation. No Gradle required. Runtime browser root owns18210 after rebuild; stop edits at frozen manifest.

## 6. Existing patterns

StatisticsTypeCard has horizontal history and meaningful labels; reuse patterns where compatible. Existing canned fixtures include type-specific aggregate projections. Feature props/emits remain controlled. Original design source is authority for anatomy; current percent values are illustration, not calculation oracle. Existing vue-tsc/ESLint/Vite scripts from prior checks are authority for commands.

## 7. Acceptance criteria

All7 findings corrected; same13 fixtures remain reachable. Measured one-row history equal cells/full width; at390x844/root16 two detail cards visible; at320x844/root24 both overview pages scrollWidth<=clientWidth with readable metrics/switches/dock. Type subset changes canned selected aggregate and series, retained cards unchanged; day/week switches expose correct supplied series. Overview all three metrics and disclosure; only Ещё current; full meaningful history names. No changes outside exact10 paths plus evidence; 23-file manifest verifies preservation.

## 8. Verification

Record preimages and exact baseline. Run scoped vue-tsc, ESLint, Vite build and affected meaningful existing view-model tests; no broad unrelated test rerun. If harness behavioral unit coverage needs new file, report exact need; root actual browser interaction remains mandatory. Record command/exit/environment/revision/raw logs. Freeze23 manifest and return READY/release. Root browser all13, source comparison, 390/light/dark,320/root24, keyboard/request return, terminal, type/range projection changes and history geometry. Fresh Sol high independent recheck against originals after stable build. No role/backend PASS from these checks.

## 9. Do not

No backend/API/generated/shell/PWA/TMA/forms/shared tokens/lockfiles/config/Figma/global writes, no fake API or client aggregation, no peer data, no deploy/migration/secrets/cleanup/commit/stage/reset, no children. Do not copy old green status over new failures. Full student role remains IN_PROGRESS.
