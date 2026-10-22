# Независимая recheck MVR01–03

**Verdict: APPROVE** для ограниченного correction package на `c73ef351d1a1b794e600bcdec2e8f8b282c2e558` относительно `e68989d29e65008dbbeba7b8df1b80bcb070a538`. Новых блокирующих findings нет. Это независимая приёмка исходников, качества oracle и сохранённого bounded runtime evidence; не PASS всей карты, браузера или сети.

Назначение: свежий read-only reviewer, requested `gpt-6.1-sol/xhigh`, actual metadata **UNKNOWN**, fallback NONE. Own writes только `/workspace/scratch/rct-validation/map_validation_recheck`. Java/Gradle/Docker/browser/mutations/image inflation/children/product writes — **NOT_RUN/NONE**. Канонические contract/CURRENT/decisions/questions/resources, D008/D004 и предыдущий 39-file immutable review восстановлены. Reviewer не исправлял собственные findings и не менял авторские исходники.

Accepted basis: `/workspace/rutcampustrack/docs/wireframes/admin/135-admin-map.md:147` запрещает SVG scripts, внешние ссылки/шрифты и вложенные объекты с ясным отказом; D004 откладывает только легенду. `/workspace/rutcampustrack/docs/product/decisions/2026-10-11-png-validation-resource-budget.md` D008 сохраняет 10 MiB upload cap и разрешает дополнительные пределы 134217728 decoded scanline bytes и 1000000 scanline checks, включая фильтры/непустые Adam7-проходы, без нового canvas limit.

## Закрытие MVR01, ранее P2

Файл `/workspace/rct-worktrees/file-boundaries/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminService.java:623`, далее 628, 643 и 656–699. Теперь сохраняется Document, а iterative traversal начинается с него и посещает PI до/после SVG root. Для точного XML target `xml-stylesheet` pseudoattributes разбираются отдельным secure XML parser; внешний nonempty href запрещён, local fragment разрешён. Обе фабрики сохраняют secure processing, запрет DOCTYPE/entities/external DTD/XInclude. Ошибка разбора превращается в field-specific BadRequest, а не 5xx.

Контрпримеры до корня, после корня, relative href и character-reference href имеют четыре actual red policy failures. Все четыре SVG-байта из финального тестового исходника совпали с оригинальными red served artifacts. На коррекции все четыре проходят oracle 400, нулевые inventory/asset/version/format writes и неизменный revision count. XML declaration + local stylesheet PI сохраняются в positive stored/served test.

Коррекция соответствует прежнему замечанию; нового решения или расширения разрешённых resource types нет. Браузерное применение stylesheet PI и сетевые запросы здесь не воспроизводились.

## Закрытие MVR02, ранее P2

Тот же service:62 и 735–887; новые IT source:224–255. Resource scanner распознаёт `image-set`/`-webkit-image-set`, а также существующие URL и смежные string-resource функции. Он сканирует raw CSS tokens; escape decoding выполняется после определения string boundary. Поэтому `/*...*/` внутри строки и escaped quote сохраняют смысл URI, а comments вне строк пропускаются как spacing. Escaped function names и URI декодируются при соответствующих сравнениях. `type('image/png')` имеет отдельный nonresource function frame, поэтому descriptor string не путается с URL. Iterative stack заменяет recursion; каждый token/index продвигается, substring/escape scans ограничены конечным входом. PNG/SVG upload cap остаётся 10 MiB.

Семь actual red случаев: absolute, vendor protocol-relative, relative, escaped function/URI, второй option после local url, comment-looking prefix внутри resource string, escaped quote внутри resource string. Все семь финальных SVG input bytes совпадают с оригинальными admitted/stored/served artifacts. Green XML подтверждает все семь 400-oracle cases. Positive local image-set с type descriptor и escaped quote принят и возвращён без изменения байтов. Существующие local url/ordinary styles/opacity animation/embedded PNG/JPEG positive controls тоже зелёные. Whitelist `safeSvgResource` и публичный контракт не менялись.

Scoped token validator не является полной CSS engine/conformance реализацией. Unresolved `var()` внутри resource functions отвергается; ordinary safe style variables вне этих функций не стали отдельным запретом. Native CSS/image presentation и network acceptance — отдельный scope.

## Закрытие MVR03, ранее P2, по D008

Тот же service:528–546. Оба remaining budgets вычисляются до `PngDataStream`, `Inflater` и любой инфляции. Для каждой непустой pass учитывается `(packed pixel bytes + 1 filter byte) × pass rows`; pass rows также суммируются независимо от canvas height. Максимальный legal positive int width при samples≤4/depth≤16 даёт безопасный long per-row byte count. Сравнение `rows > remainingBytes / scanlineBytes` выполняется до умножения, а последующее умножение ограничено уже принятым remaining budget. Remaining bytes/rows не становятся отрицательными. Новая ветка возвращает BadRequest(`file`) с обоими бюджетами; stream остаётся 8192 bytes, raster/row array allocation и arbitrary dimension cap не добавлены.

Независимый arithmetic audit использовал Python integers и cardinality координатных ranges, без создания/инфляции PNG:

| Input | Decoded bytes с filter | Pass rows | D008 |
| --- | ---: | ---: | --- |
| 1×1000000 gray1 | 2000000 | 1000000 | accept |
| 134217727×1 gray8 | 134217728 | 1 | accept |
| 1×1000001 gray1 | 2000002 | 1000001 | reject |
| 134217728×1 gray8 | 134217729 | 1 | reject |
| 2×666667 Adam7 gray1 | 2000002 | 1000001 | reject |
| 2147483647² RGBA16 header | 36893488115206848519 | 2147483647 | reject safely before inflation |

Actual old red evidence содержит три конечных strongly compressed over-budget PNG; их IHDR/chunk CRC и work accounting проверены независимо, без инфляции. Green содержит эти три rejection cases и дополнительный maximal RGBA16-header control: 400/error field `file`/budget-specific detail/no writes, без huge decoded stream. Два exact-limit positives реально stored/served и зелёные. Wide100000×1, grayscale/indexed/16-bit/color/Adam7 и native-decoded small streamed gray controls сохранены. Billion-row candidate не выполнялся. Не заявляется измеренный end-to-end CPU SLA.

## Публикация, tests и исходники

Correction inventory ровно 3 modified files: service private validation, new IT, fixture helper. Cumulative package относительно frozen root e093 — 5 paths, 2A/3M. Два старых test files менялись только ранее, на e689: fake24-byte PNG заменён настоящим encoded/decoded640×480 PNG; прежние expectations не ослаблены. Их текущие байты равны e689. Public methods/API/proto/grants/transaction/history logic не менялись.

`uploadPlan` service:168–171 и `uploadVersion`:205–208 по-прежнему валидируют все новые payloads перед первым catalog lock/write. Real-PG/MVC helper IT:362–390 требует точно 400, нулевые five publication tables и прежний revision count. При unexpected201 helper сначала устанавливает PG asset count и GET200 exact payload bytes, затем падает на 400-vs201; exceptions/skips не скрыты. Существующие selected storage tests:549/619 сохраняют same-record replacement, retained formats/history и controlled failure-after-pointer rollback. Inactive-code и concurrent/publication checks также не редактировались; данный green отобрал только два названных old storage tests, не выдаёт остальные за заново выполненные.

Current five source bytes равны final Git blobs/inventory; три correction bytes также равны correction-prep и green prelaunch. Old red product bytes равны e689 Git blob; fixture bytes red/final равны. Red test был uncommitted WIP с оригинальным prelaunch hash; он не объявлен равным final test blob. Дополнительный input bridge независимо связывает все 11 final negative SVG literals с exact red served bytes. Green выполнялся как correction WIP при HEAD e689; hash equality, а не ретроактивный запуск c73ef, связывает результат с final revision.

## Проверенные оригинальные receipts

- Предыдущий immutable review: все 39 manifest entries совпали с rooted original files и 40-entry immutable archive; прежний CHANGES_REQUESTED не переписан.
- Current оригиналы: 37 byte-equal preserved files, включая 11 XML, два logs, prelaunch/terminal/summary/inventory/prep/final/harness и 14 bad-file artifacts; исходные failures не нормализованы и не перезаписаны.
- Red `73108`: product XML17 tests, 14 meaningful 400-vs201 AssertionFailedError, 3 positive pass, errors0/skips0. Все 14 diagnostics: upload201/PG asset1/GET200/exact byte equality; 14 разных hashes и actual artifact lengths совпадают. Red log:141 `BUILD FAILED in 1m 9s`; log22 aggregate tests includes architecture, product count17 берётся из оригинального XML.
- Green `38765`: unit4/mapIT37/selected old storage2, failures0/errors0/skips0. Все 11 red+green XML testcase totals совпали с declarations; architecture suites отмечены отдельно. Green log:87 `BUILD SUCCESSFUL in 1m 39s`. Test counts и red/green overlap не являются unique scenario metric.
- Оригинальные terminal argv/hashes равны prelaunch; statusTERMINAL/exit1 или0 соответствуют summaries/logs. Process exit/FILES2 release/empty author-owned resources — авторские сохранённые receipts, независимо не replayed/read back через Docker.

Source/evidence checks — PASS; обязательные root integration/final hooks/remote CI/Coverage/Security остаются задачей root. Author hook отсутствовал по его receipt; review не обходил и не менял gates.

Evidence entry points: `source-equivalence.json`, `prior-immutable-review-audit.json`, `original-xml-audit.json`, `served-artifact-audit.json`, `negative-input-source-bridge.json`, `png-budget-audit.json`, `original-log-audit.json`, `original-byte-index.json`, `input-hash-manifest.json`. Safe immutable transfer перечисляет ровно own outputs и byte hashes.

Остаточные границы: browser rendering/image presentation/client download/network/TCP/signed JWT gateway **NOT_RUN**; whole-map/all-file-family/full PNG ancillary/APNG/indexed sample semantics/full CSS conformance **не сертифицированы**. Это не новые blockers для назначенной MVR recheck, и не скрытый глобальный DONE.
