# Результат переноса материалов

Статус: **PASS** для переноса по утверждённой карте. Writer-проверки и независимое review Terra high завершены; verdict и known gaps — в [review.md](review.md).

## Diff и evidence

- Добавлено 1 150 файлов документации объёмом 103 627 138 B: 1 146 inventory exact copies, сохранённый прежний INDEX и три generated source artifacts. Изменён [INDEX.md](../../docs/INDEX.md) — 11 строк датированной навигации; его прежнее тело сохранено без изменений.
- В `.agent/migration/` добавлены task packet, result, checks, preflight/post-transfer evidence и воспроизводимые scripts. Чужие `skills-lock.json`, `.codex/`, AGENTS и прочие исходные untracked changes не входят в этот diff.
- Evidence: [preflight](transfer-preflight.json), [protected baseline](pre-transfer-baseline.json), [post-transfer verification](post-transfer-verification.json), [checks](transfer-checks.json), [manifest](../../docs/sources/manifest.yaml), [address map](../../docs/sources/address-map.json) и [сохранённый INDEX](../../docs/archive/transfer-20260905/INDEX.md).

## Результат по inventory

В source inventory 2 230 взаимоисключающих записей:

- точных canonical-копий — 129;
- точных archive-копий — 1 017;
- точных копий всего — 1 146; archive является подмножеством этого total и не суммируется с ним повторно;
- адаптированных документов — 1: `docs/INDEX.md`; его прежние точные байты сохранены в `docs/archive/transfer-20260905/INDEX.md`;
- retained duplicates — 1 049;
- исключений — 34;
- blocked — 0.

Сгенерированные навигационные и адресные артефакты не входят в source-inventory counts: [manifest](../../docs/sources/manifest.yaml), [address map](../../docs/sources/address-map.json) и [отчёт ссылок](../../docs/sources/unresolved-references.md).

## Provenance и решения

`docs/sources/manifest.yaml` — JSON-совместимый YAML. Он содержит каждый source, корень/путь, destination и canonical address, inventory и фактический status, owner/date решения, отдельные `transfer_owner`/`transfer_date`, supersedence, source/result hashes и verification state.

`decision_owner` и `decision_date` равны `null`, если источник не доказывает решение владельца. Единственное такое утверждение — `kit:journal/DECISIONS.md`: `project owner`, 05.09.2026. Технический перенос указан отдельно как `transfer_owner=root`, `transfer_date=2026-09-06`.

Реестр фиксирует приоритет позднего решения без неявной смены продукта: PWA/TMA → web, Vue/PCSS без Tailwind/shadcn, один BFF с REST на краю и gRPC к сервисам. Part 26 ограничен названными mobile Teacher Profile role-entry/gradient carriers. Первый mobile release, cache owner, role visibility/tokens, radius baseline и расхождение Admin 668/659 остаются blocked для решения владельца.

Старые AGENTS/SKILL попали только в archive/research с суффиксом `.inactive`. Исключённые inventory configs/secrets не открывались и не хешировались. Figma, API, код, база данных и production не менялись.

## Проверка и ограничения

Preflight сверил 2 196 безопасно хешируемых copy/duplicate sources с inventory: source drift, missing files, target collisions и unsafe instruction targets равны нулю. Post-transfer verification проверил все 2 230 records, SHA-256 exact targets, retained duplicate content, source drift и protected files: failures = 0.

Address map разрешает 25 source-relative ссылок, включая historical `.inactive` prompts. Реально отсутствуют только standalone specs `001a-recovery.md` и `001b-reset.md`; они перечислены без ремонта в [отчёте](../../docs/sources/unresolved-references.md). Имеющийся `001-login.md` остаётся evidence, а не заменой решения владельца.

Приложение и сервис не запускались: задача меняет только документацию и provenance artifacts. Это записано как `SKIPPED`, а не PASS, в [transfer-checks.json](transfer-checks.json).
