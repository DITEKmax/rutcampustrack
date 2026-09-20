# Evidence — SQL17/V26 exact coverage repair

## Source guard

Перед первой записью fail-closed guard сравнил четыре frozen SHA256/byte-count
пары и завершился exit `0`. После правки read-only guard подтвердил, что SQL
не изменился:

| Path | Pre SHA256 / bytes | Post SHA256 / bytes |
| --- | --- | --- |
| V17 SQL | `8D02720F48B67E21C78373B214ADB414A2F435A8016C35987839D330B6042FCC` / 18760 | `8D02720F48B67E21C78373B214ADB414A2F435A8016C35987839D330B6042FCC` / 18760 |
| V26 SQL | `21B3F522634BE7505FB73A613BF5BCB68C1584B7A1724BB45EBA5C767E4058A0` / 17109 | `21B3F522634BE7505FB73A613BF5BCB68C1584B7A1724BB45EBA5C767E4058A0` / 17109 |
| StudentOccurrenceMigrationIT.java | `29433B97247AE23E8BFF9EA83AD94768DC80A329276E565AE3E3C5063F5928D2` / 23649 | `633285777706AF26FEF3B847B172BA1805C41E6607B5170025F4DE6A02AF1A68` / 28608 |
| StudentFoundationMigrationIT.java | `8A39EC859D8CF2BA2699295A0D60B8B10FA6988B2AA0DB8BCD3E2434115D446B` / 51464 | `14C68464CFBD29EDF20C87021D9353863C81E5B0044C193D09930FBBCEE7FB8B` / 58295 |

## Static readback

- Schedule `crossTemplateRecurringOriginIsRejectedEvenWhenSnapshotsMatch`
  начинается на line 211: собственный `templateA` control row, exact INSERT на
  line 237, exact origin UPDATE на line 248; после каждой ошибки проверяются
  count `1`, `schedule_item_id = templateA` и `status = planned`.
- Schedule `crossOneOffOriginIsRejectedEvenWhenSnapshotsMatch` начинается на
  line 292: собственный `oneOffA` control row, exact INSERT на line 320, exact
  origin UPDATE на line 331; после каждой ошибки проверяются count `1`,
  `one_off_lesson_id = oneOffA` и `status = planned`.
- Schedule `assertExactSqlFailure`/`nestedSqlFailure` на lines 458/470
  извлекают вложенный `SQLException` state и message; broad legacy assertions
  остаются отдельными.
- Academic READY dimension mismatch находится на line 135 и сравнивает
  unchanged format count.
- Academic post-publication `SVG ABSENT` INSERT находится на line 229; после
  него сравниваются format graph count, asset graph count, существующие
  `asset_id`/state/bytes/hash.
- Academic demand missing-intent exact path находится на line 377, valid
  second intent with wrong day — на line 392. Для каждой попытки проверяются
  отсутствие dedupe row и полное равенство `utc_day/open_count` snapshot.
- Academic exact helper на line 787; существующий broad `assertSqlFailure` на
  line 779 сохранён для unrelated legacy paths.

## Anti-masking evidence

Recurring и one-off cross-origin INSERT используют `cancelled` и generation /
revision `2`, поэтому included unique slot и control `planned` row не скрывают
BEFORE trigger. Cross UPDATE меняет только origin ID, и mismatch check
производится раньше immutability check. Published format INSERT использует
новый `SVG` format, `ABSENT` и корректный `image/svg+xml`, поэтому unique/FK/
ready-shape constraints не предшествуют publication guard. Missing dedupe
использует `OWNER_B` без open intent; mismatch case сначала создаёт valid
`OWNER_B` intent с новым UUID на Jan 2, затем использует свободный Jan 3 key,
чтобы AFTER trigger был причиной ошибки.

## Runtime evidence

Новый exact runtime выполнен root под exclusive lease и завершился PASS. Полные
факты сохранены в `runtime-pass-2026-09-10.md`.

- Academic `StudentFoundationMigrationIT`: session `75913`, start
  `2026-09-10T19:46:54.4893287Z`, end `2026-09-10T19:48:47.3402424Z`, exit `0`,
  `BUILD SUCCESSFUL` за `1m34`, 37 tasks / 2 executed; XML SHA256
  `20715B9C9782407A9842AC0324C2CF9EE577445AD2001DCF2FEA89FCDE4C4254`,
  `94655` bytes, mtime `2026-09-10T19:48:36.4401730Z`, 15 tests,
  0 failures/errors/skipped, time `29.813s`.
- Schedule `StudentOccurrenceMigrationIT`: session `2390`, start
  `2026-09-10T19:48:53.6122527Z`, end `2026-09-10T19:50:44.8751284Z`, exit `0`,
  `BUILD SUCCESSFUL` за `1m20`, 37 tasks / 2 executed; XML SHA256
  `7E316529DAD60FDB6814B9FDE3014B30B2137F47ADB39BF92505AFE9F509367F`,
  `36150` bytes, mtime `2026-09-10T19:50:19.0916824Z`, 7 tests,
  0 failures/errors/skipped, time `14.026s`.
- Flyway не перезапускался; сохранено предыдущее PASS `3/3`, XML SHA256
  `36C4E903234ACF3158E0CFD716D08D82B176B4E18C8430AA274D5E9484340BAA`,
  `18743` bytes.

Итог нового exact scope: `22/22` теста PASS (15 Academic + 7 Schedule),
0 failures/errors/skipped. Root сообщил, что процессы/порты чисты, Docker empty,
heavy lease `RELEASED`. В Academic были только два unrelated warnings в
`UserRepositorySearchIT` и отдельная unchecked note; они не связаны с запросом
и код не менялся.

## Independent review

Fresh read-only reviewer `/root/sql17_26_coverage_recheck` (`gpt-5.6-sol`,
`high`, `fork-none`) returned `FINAL PASS — SQL17_V26_COVERAGE_RECHECK_PASS`,
findings none. Reviewer подтвердил закрытие обоих MEDIUM: exact recurring/
one-off V17 INSERT+UPDATE rollback и exact V26 dimension/published insert/
missing+mismatched intent с unchanged graph/dedupe/aggregate. Полный handoff
находится в `independent-recheck-pass-2026-09-10.md`.

Принят focused migration scope; full-role acceptance остаётся открытой. Prior
Flyway 3/3 evidence принята, текущий Flyway XML отсутствует после focused-task
cleanup и это записанная ожидаемая limitation.
