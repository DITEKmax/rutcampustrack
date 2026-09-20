# Frozen 23-application-path manifest — post-repair

Captured: 2026-09-08 after the scoped build, at HEAD
`8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

| Path | SHA256 |
|---|---|
| `frontends/mobile-core/src/features/attendance/AttendanceGraph.vue` | `217147BF1BDEEEBF365491800502DC82C5859FFEDDA4B49F4F1C8035359D722A` |
| `frontends/mobile-core/src/features/attendance/AttendanceLessonRow.vue` | `77D61D0549C1B2F6FD94D6D0035F88B11FEC749D9321A9D6A49E1510E7602F97` |
| `frontends/mobile-core/src/features/attendance/AttendanceScreen.vue` | `5B989216208768A6AC983E1C2CE6C775E86E57A15CA1DF75D3D92C68D39E8277` |
| `frontends/mobile-core/src/features/attendance/AttendanceSubjectList.vue` | `0139DAF91491D21ADDA1023BF911693647D636D95672C8D8B8F984739ED00BAC` |
| `frontends/mobile-core/src/features/attendance/attendance-screen.pcss` | `6F7ACB866A5D2B3F2FC2B9DE733EB3641BDF50804E00E44843C3912DC63DE4A2` |
| `frontends/mobile-core/src/features/attendance/attendance-tokens.pcss` | `06496BD820FF76F948BB3B219FDA037F7FE06DDA5D18EAC826B837A4C0EF80C7` |
| `frontends/mobile-core/src/features/attendance/attendance-view-model.test.ts` | `61E0C9298A09D12A0BB54453976574EA2BE88AEB6C8B2482A2CF52AAFE30E53C` |
| `frontends/mobile-core/src/features/attendance/attendance-view-model.ts` | `988E5FBC9BA22852E2341A6DC56B9910FD76B9BBCCB93BA6703DCDCB2C520BAE` |
| `frontends/mobile-core/src/features/statistics/StatisticsScreen.vue` | `34EF4D06F23D4CEA3E433F39B6E32ACBE3C30CD9624DEF9DBDD49B752978EF05` |
| `frontends/mobile-core/src/features/statistics/StatisticsSemesterChart.vue` | `C51A5A480244161CBE487764A1CF371807A9FD265A9D11F8F9E0EFFB3F5D39D3` |
| `frontends/mobile-core/src/features/statistics/StatisticsSubjectDetail.vue` | `7DCA5C918135CD7F216E7BF464A7416BE93A6A9800F973D279F4A9E0B40D1F85` |
| `frontends/mobile-core/src/features/statistics/StatisticsTypeCard.vue` | `51E77D9A7D450822D7D142D119C6A0CC87DA4973CA2979A4BEBB990C71E046FF` |
| `frontends/mobile-core/src/features/statistics/statistics-screen.pcss` | `474C49CD16A4883E90639C94DD295FB1A99674569788F7492E9FE726DB767115` |
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

The pre-edit manifest is in `pre-edit-evidence.md`. A direct hash comparison
reports `product_paths=23 changed_from_pre_edit=2`; only the two scoped PCSS
files changed. The generated harness output is evidence WIP under the existing
`.agent/student-academic-ui/runtime/` path and is outside this 23-path product
manifest.
