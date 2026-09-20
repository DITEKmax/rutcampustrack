# FE13 statistics compact stage

Recorded: 2026-09-09 (Europe/Moscow)

## Scope

- Goal: close the Statistics detail compactness finding while preserving the
  already-correct subject overview metrics/disclosure finding.
- Risk: S2 presentation inside the parent S3 FE13 story.
- Frozen revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
- Worktree/cwd: `C:/Users/maksd/.codex/worktrees/34a5/rutcampustrack/.agent/worktrees/student-academic-ui`.
- Sole product writer scope: the four exact statistics paths named by root.
  Only the chart and statistics PCSS changed in this stage; overview and detail
  screen source was preserved.

## Criteria and evidence

| Criterion | Evidence | Result |
| --- | --- | --- |
| Detail remains controlled | `StatisticsSubjectDetail.vue` retains `selectedAggregate`, selected type guard, typed `set-types`, typed range event and `days`/`weeks` affordance; its preimage SHA is unchanged. | PASS by source/hash |
| Compact source hierarchy | `StatisticsSemesterChart.vue` groups title and range controls in `statistics-chart__header`; the large visible point list is replaced by a `statistics-visually-hidden` semantic `<ol>`. Inline aggregate styling and existing type-card stack remain source-backed. | PASS by source/build |
| Assistive technology point detail | Hidden list keeps supplied point label, `dateFrom`/`dateTo`, DATA/FUTURE/NO_DATA state, present/presentOrExcused/excused/absent percentages and counts, plus supplied held/planned values. SVG remains `aria-hidden`. | PASS by source/typecheck |
| Overview finding preserved | `StatisticsScreen.vue` preimage SHA is unchanged; source retains all three supplied subject metrics, count labels, 44px disclosure and local `Ещё` current dock with `Учёт` as a separate label. | PASS by source/hash |
| No client aggregation/form/API change | Only presentation markup/CSS changed in exact4; no view-model, harness, API, generated type, config or lockfile path changed. | PASS by scope |

## Checks

Environment: Windows, Node `v24.14.0`, npm `11.9.0`, Vite `7.3.6`.

1. `npm exec vue-tsc -- --noEmit -p mobile-core/harness/attendance-statistics/tsconfig.json` from `frontends` — exit `0`; no output.
2. `npm exec eslint -- mobile-core/src/features/statistics/StatisticsScreen.vue mobile-core/src/features/statistics/StatisticsSubjectDetail.vue mobile-core/src/features/statistics/StatisticsSemesterChart.vue --max-warnings=0` from `frontends` — exit `0`; no output.
3. Requested workspace command `npm run build -- --config mobile-core/harness/attendance-statistics/vite.config.ts` from `frontends` — exit `1`. Raw relevant output: root `build` invoked PWA/TMA workspaces, then Vite reported `Cannot read directory "../../../../../../../../..": Access is denied` and could not resolve `.../frontends/pwa-vue/vite.config.ts`. This is a command-shape/infrastructure diagnostic; no source or config correction was made.
4. Scoped equivalent `node node_modules/vite/bin/vite.js build --config mobile-core/harness/attendance-statistics/vite.config.ts --configLoader runner` from `frontends` — exit `0`. Raw relevant output: `vite v7.3.6`, `transforming...`, `✓ 40 modules transformed`, `✓ built in 2.18s`; emitted `runtime/harness-dist` CSS/JS assets.

## Runtime evidence

No browser or dev server was started by this leaf. Product runtime, screenshots,
responsive geometry, keyboard/focus and root-font checks are `N/A` here and
remain root-owned. The production build above is build evidence only; it is not
a browser/runtime PASS.

## Diff and hashes

Leaf product delta against the exact4 preimages supplied in the task:

| Path | Preimage SHA256 | Final SHA256 | Leaf change |
| --- | --- | --- | --- |
| `frontends/mobile-core/src/features/statistics/StatisticsScreen.vue` | `EDA312C04FB8CBE9AF6983EC25B3FC9D70FFE661A91B83E249507BDB507F5315` | `EDA312C04FB8CBE9AF6983EC25B3FC9D70FFE661A91B83E249507BDB507F5315` | unchanged |
| `frontends/mobile-core/src/features/statistics/StatisticsSubjectDetail.vue` | `7DCA5C918135CD7F216E7BF464A7416BE93A6A9800F973D279F4A9E0B40D1F85` | `7DCA5C918135CD7F216E7BF464A7416BE93A6A9800F973D279F4A9E0B40D1F85` | unchanged |
| `frontends/mobile-core/src/features/statistics/StatisticsSemesterChart.vue` | `C51A5A480244161CBE487764A1CF371807A9FD265A9D11F8F9E0EFFB3F5D39D3` | `B046957227E8C18FF682D79F62989ABEB9CF58E6690D465E2102B9E5CB615F02` | changed |
| `frontends/mobile-core/src/features/statistics/statistics-screen.pcss` | `23C3DF1E2374B40A12B3D1E8C0D5A87CD4B4E275159738BC9416E44613129A33` | `4FDD939B75FF7F7108C384B7FDDBC36FC20A7E0A16B36F44B9ED5F41CC75B344` | changed |

Current full23 application SHA snapshot (post-stage):

| Path | SHA256 |
| --- | --- |
| `frontends/mobile-core/src/features/attendance/AttendanceGraph.vue` | `217147BF1BDEEEBF365491800502DC82C5859FFEDDA4B49F4F1C8035359D722A` |
| `frontends/mobile-core/src/features/attendance/AttendanceLessonRow.vue` | `77D61D0549C1B2F6FD94D6D0035F88B11FEC749D9321A9D6A49E1510E7602F97` |
| `frontends/mobile-core/src/features/attendance/AttendanceScreen.vue` | `5B989216208768A6AC983E1C2CE6C775E86E57A15CA1DF75D3D92C68D39E8277` |
| `frontends/mobile-core/src/features/attendance/AttendanceSubjectList.vue` | `C51AF8DAEA1C86F614A6FEA7A6760DAC1A608B2F058ED603382FE6AB778F4882` |
| `frontends/mobile-core/src/features/attendance/attendance-screen.pcss` | `9B7606392B1E9DB0BFAF83B1BAEB9377F7A286220A1E5B7EFAE53A8DBB8459A1` |
| `frontends/mobile-core/src/features/attendance/attendance-tokens.pcss` | `06496BD820FF76F948BB3B219FDA037F7FE06DDA5D18EAC826B837A4C0EF80C7` |
| `frontends/mobile-core/src/features/attendance/attendance-view-model.test.ts` | `A67FD1DD9BD9115BAED8E331188269B5C0256FEA4FD097DFC83065A6695EE7D1` |
| `frontends/mobile-core/src/features/attendance/attendance-view-model.ts` | `2854112350F251046ECB59B9F065083390D9D6BDA3EEA55C07737A3302A13B4C` |
| `frontends/mobile-core/src/features/statistics/StatisticsScreen.vue` | `EDA312C04FB8CBE9AF6983EC25B3FC9D70FFE661A91B83E249507BDB507F5315` |
| `frontends/mobile-core/src/features/statistics/StatisticsSemesterChart.vue` | `B046957227E8C18FF682D79F62989ABEB9CF58E6690D465E2102B9E5CB615F02` |
| `frontends/mobile-core/src/features/statistics/StatisticsSubjectDetail.vue` | `7DCA5C918135CD7F216E7BF464A7416BE93A6A9800F973D279F4A9E0B40D1F85` |
| `frontends/mobile-core/src/features/statistics/StatisticsTypeCard.vue` | `51E77D9A7D450822D7D142D119C6A0CC87DA4973CA2979A4BEBB990C71E046FF` |
| `frontends/mobile-core/src/features/statistics/statistics-screen.pcss` | `4FDD939B75FF7F7108C384B7FDDBC36FC20A7E0A16B36F44B9ED5F41CC75B344` |
| `frontends/mobile-core/src/features/statistics/statistics-tokens.pcss` | `55B4DD7C29F174165E0C1408793B0DD4C0545EED974606A4322EF71DF8338489` |
| `frontends/mobile-core/src/features/statistics/statistics-view-model.test.ts` | `DC5935FF2841A3A77F7B2F169604FE25BC4940BC0652D87AE6F6666CB509674E` |
| `frontends/mobile-core/src/features/statistics/statistics-view-model.ts` | `5A00CA36345A5621D62C8D8F29135BC1AACAC99C6DA70525B6FFE72D28A69547` |
| `frontends/mobile-core/harness/attendance-statistics/Harness.vue` | `B1B9C841418CCF4A4ADD5864C5537147170E9BDC21ED1C23583F849AB1387F1C` |
| `frontends/mobile-core/harness/attendance-statistics/fixtures.ts` | `3C902E1B13209A442E2D9E0EB5C67D26EE42EC25672DD0C150E39A0D72F4DBEC` |
| `frontends/mobile-core/harness/attendance-statistics/harness.pcss` | `CA6D1BD276168A1EEC7E3283D4EEA631885E73B4B878FD224AD17C90C0917949` |
| `frontends/mobile-core/harness/attendance-statistics/index.html` | `D5C75E85A6094B5C0C09D0A7DC7B29A05128D571BC9D8E859E410B5A6EECD9BD` |
| `frontends/mobile-core/harness/attendance-statistics/main.ts` | `1CB4A4736117F983FB2C5C846509B41E7A89F4896D43F0CE841914B59967E3E7` |
| `frontends/mobile-core/harness/attendance-statistics/tsconfig.json` | `692971CF8E01C21773CF92AB981E6136927B3D9F96CF1FC48B8C01BBB5F03756` |
| `frontends/mobile-core/harness/attendance-statistics/vite.config.ts` | `A1CDDE90D8D35735BFF5C8720DD590D834284618D7E40F3B8C64AB6167298636` |

Changed path set from this leaf: exactly the two changed statistics paths above;
no files outside exact4 were written. Existing unrelated WIP and generated
harness output remain preserved.

## Limitations

- The root-script build failure is recorded as command-shape evidence; the
  scoped direct Vite build is the passing build check.
- No browser/runtime evidence is claimed by this leaf. Root must independently
  verify the 390x844 source comparison, both type cards in viewport, narrow and
  enlarged-font no-overflow behavior, dark/light themes, keyboard/focus and
  controlled terminal/type/range states before review.
- No independent Sol review was run by this leaf; code is ready for the root's
  stable diff handoff and required independent recheck.
