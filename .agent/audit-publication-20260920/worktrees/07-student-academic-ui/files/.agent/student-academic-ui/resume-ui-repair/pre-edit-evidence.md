# Pre-edit evidence — root box sizing repair

Captured: 2026-09-08T10:41:30+03:00 (Europe/Moscow)

## Revision and environment

- Worktree: `C:/Users/maksd/.codex/worktrees/34a5/rutcampustrack/.agent/worktrees/student-academic-ui`
- Branch: `codex/student-academic-ui`
- HEAD: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`
- Node: `v24.14.0`
- npm: `11.9.0`
- Requested fresh writer: `gpt-5.6-luna / max`

## Confirmed defect

Root-provided browser reproduction at
`http://127.0.0.1:18210/?fixture=4593-142&theme=dark`, `390x844`:

| Measurement | Before repair |
|---|---:|
| document.clientWidth | 375 |
| document.scrollWidth | 407 |
| main bounding rect width | 406.8 |
| root computed box-sizing | `content-box` |
| root padding | `20px 16px 108px` |

All 13 known fixture states share the overflow. The root declaration uses
`inline-size: min(100%, 26.625rem)`; with `content-box`, the horizontal padding
is added to the calculated width. The existing descendant and pseudo-element
rules set `box-sizing: border-box` but do not apply to the root itself.

Actual keyboard Back return with solid focus outline was already confirmed by
root and is retained by this repair; this leaf does not reproduce it.

## Pre-edit root rules

`attendance-screen.pcss:4` starts `.attendance-screen` and its declaration
block has no `box-sizing`. The descendant rule begins at line 20 and sets
`box-sizing: border-box`.

`statistics-screen.pcss:4-5` starts the grouped `.statistics-screen,
.statistics-detail` rule and its declaration block has no `box-sizing`. The
descendant/pseudo-element rule begins at line 21 and sets
`box-sizing: border-box`.

## Frozen 23-application-path SHA manifest (pre-edit)

| Path | SHA256 |
|---|---|
| `frontends/mobile-core/src/features/attendance/AttendanceGraph.vue` | `217147BF1BDEEEBF365491800502DC82C5859FFEDDA4B49F4F1C8035359D722A` |
| `frontends/mobile-core/src/features/attendance/AttendanceLessonRow.vue` | `77D61D0549C1B2F6FD94D6D0035F88B11FEC749D9321A9D6A49E1510E7602F97` |
| `frontends/mobile-core/src/features/attendance/AttendanceScreen.vue` | `5B989216208768A6AC983E1C2CE6C775E86E57A15CA1DF75D3D92C68D39E8277` |
| `frontends/mobile-core/src/features/attendance/AttendanceSubjectList.vue` | `0139DAF91491D21ADDA1023BF911693647D636D95672C8D8B8F984739ED00BAC` |
| `frontends/mobile-core/src/features/attendance/attendance-screen.pcss` | `C0CE846FB39BA74AEC88A3B08196C5458E7A387C03153161D4881D98AA6C1283` |
| `frontends/mobile-core/src/features/attendance/attendance-tokens.pcss` | `06496BD820FF76F948BB3B219FDA037F7FE06DDA5D18EAC826B837A4C0EF80C7` |
| `frontends/mobile-core/src/features/attendance/attendance-view-model.test.ts` | `61E0C9298A09D12A0BB54453976574EA2BE88AEB6C8B2482A2CF52AAFE30E53C` |
| `frontends/mobile-core/src/features/attendance/attendance-view-model.ts` | `988E5FBC9BA22852E2341A6DC56B9910FD76B9BBCCB93BA6703DCDCB2C520BAE` |
| `frontends/mobile-core/src/features/statistics/StatisticsScreen.vue` | `34EF4D06F23D4CEA3E433F39B6E32ACBE3C30CD9624DEF9DBDD49B752978EF05` |
| `frontends/mobile-core/src/features/statistics/StatisticsSemesterChart.vue` | `C51A5A480244161CBE487764A1CF371807A9FD265A9D11F8F9E0EFFB3F5D39D3` |
| `frontends/mobile-core/src/features/statistics/StatisticsSubjectDetail.vue` | `7DCA5C918135CD7F216E7BF464A7416BE93A6A9800F973D279F4A9E0B40D1F85` |
| `frontends/mobile-core/src/features/statistics/StatisticsTypeCard.vue` | `51E77D9A7D450822D7D142D119C6A0CC87DA4973CA2979A4BEBB990C71E046FF` |
| `frontends/mobile-core/src/features/statistics/statistics-screen.pcss` | `45B5CE2DBD4360E9EEAA32CEBF68D321AF0224628EFBAD7301B2F84489D5A2A3` |
| `frontends/mobile-core/src/features/statistics/statistics-tokens.pcss` | `55B4DD7C29F174165E0C1408793B0DD4C0545EED974606A4322EF71DF8338489` |
| `frontends/mobile-core/src/features/statistics/statistics-view-model.test.ts` | `DC5935FF2841A3A77F7B2F169604FE25BC4940BC0652D87AE6F6666CB509674E` |
| `frontends/mobile-core/src/features/statistics/statistics-view-model.ts` | `5A00CA36345A5621D62C8D8F29135BC1AACAC99C6DA70525B6FFE72D28A69547` |
| `frontends/mobile-core/harness/attendance-statistics/Harness.vue` | `8EE4A0D403C6D8261FF0662B3428C342E8417F9F71008CA9B98835A26AB9E600` |
| `frontends/mobile-core/harness/attendance-statistics/fixtures.ts` | `BFBD824013214C20708894C6FCD73EABF40366CFB29FF81B86FA25C3EEDC76FE` |
| `frontends/mobile-core/harness/attendance-statistics/harness.pcss` | `CA6D1BD276168A1EEC7E3283D4EEA631885E73B4B878FD224AD17C90C0917949` |
| `frontends/mobile-core/harness/attendance-statistics/index.html` | `D5C75E85A6094B5C0C09D0A7DC7B29A05128D571BC9D8E859E410B5A6EECD9BD` |
| `frontends/mobile-core/harness/attendance-statistics/main.ts` | `1CB4A4736117F983FB2C5C846509B41E7A89F4896D43F0CE841914B59967E3E7` |
| `frontends/mobile-core/harness/attendance-statistics/tsconfig.json` | `692971CF8E01C21773CF92AB981E6136927B3D9F96CF1FC48B8C01BBB5F03756` |
| `frontends/mobile-core/harness/attendance-statistics/vite.config.ts` | `A1CDDE90D8D35735BFF5C8720DD590D834284618D7E40F3B8C64AB6167298636` |

All 23 paths were already untracked WIP relative to the immutable baseline;
their contents and the pre-existing evidence were preserved. No code was
changed before this packet and evidence were written.

## Historical evidence correction

The immutable shutdown checkpoint records generated font
`onest-latin-ext-wght-normal-CnNj8hVb.woff2` with a 63-hex SHA. A fresh complete
hash, computed directly with `Get-FileHash -Algorithm SHA256`, is
`391A9B24B5C46EBFF5D21F53CDB2EDAA31C3446DA3B722F038AB07D75D02A82D`.
The current value matches the checkpoint prefix; this repair records the
malformed historical evidence only and does not infer product drift.
