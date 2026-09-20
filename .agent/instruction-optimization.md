# Оптимизация проектных инструкций — 05.09.2026

Риск: S0. Scope: только четыре проектных `AGENTS.md` и три активных
`.agents/skills/*/SKILL.md`. Код, `docs/agent-workflow.md`, `.codex/config.toml`,
глобальные инструкции и кэш не изменялись.

## Метод и результат

Перед записью прочитаны корневой `AGENTS.md` и `docs/agent-workflow.md`, затем
инвентарь целевых файлов: назначение из front matter, длина, ссылки и статус Git.
Полностью прочитаны только семь целевых инструкций. Объединены повторяющиеся
формулировки внутри инструкций; существенные перекрёстные проверки сохранены.
Сохранены условия остановки, S3-gates, критерии DONE/BLOCKED, роли,
ограничения Figma, PCSS/rem, backend contracts и evidence.

В корневой `AGENTS.md` добавлено правило памяти: адресный поиск релевантных
записей и минимум оригинала; не читать весь индекс; до записи через существующие
шлюзы проверять незавершённые транзакции; не захватывать чужую собственность.
Конфликт блокирует только затронутую часть, независимая работа продолжается.

Новые references не нужны: длинного самостоятельного процесса нет, а вынос
увеличил бы суммарный объём и добавил обязательную точку чтения. Reference
overhead: 0 символов, 0 файлов.

## Резервные копии

- `AGENTS.md.20260905-optimization.inactive`
- `frontends/AGENTS.md.20260905-optimization.inactive`
- `services/AGENTS.md.20260905-optimization.inactive`
- `tests/AGENTS.md.20260905-optimization.inactive`
- `.agents/skills/rct-source-resolution/SKILL.md.20260905-optimization.inactive`
- `.agents/skills/rct-verification/SKILL.md.20260905-optimization.inactive`
- `.agents/skills/rutcampustrack-design/SKILL.md.20260905-optimization.inactive`

Имена отсутствовали до копирования. Каждая копия создана до первой правки
соответствующего файла и существует. Её SHA-256 (измерен после копирования по
содержимому неизменяемой backup-копии):

| Backup | SHA-256 |
|---|---|
| `AGENTS.md.20260905-optimization.inactive` | `745E7FA533FB8DEF739AAA3F3E1CFA369DC3A48EB67028A3C46D5373DF3B48DB` |
| `frontends/AGENTS.md.20260905-optimization.inactive` | `5746CE926A47D1D927E7D98A3223AAB59905C6D083A9AD28B2A5B75483D9D6F4` |
| `services/AGENTS.md.20260905-optimization.inactive` | `4E42BFB4ADC0370A9841281797BC5743F5E8108173A63C78F25914CEDF9CDF40` |
| `tests/AGENTS.md.20260905-optimization.inactive` | `273F17112FDA6D139F9E88E4602D0B4B7E9BEB4188AF09C31AA183C218DECD57` |
| `rct-source-resolution/SKILL.md.20260905-optimization.inactive` | `6C8EF3FD8A53136668DDD9B4440E826DEA0AF076028B043634F0352D89E77CC0` |
| `rct-verification/SKILL.md.20260905-optimization.inactive` | `EB741D21EF4E858F5AC410ECDD916109140BC34F634D70308F6D53690F489C6D` |
| `rutcampustrack-design/SKILL.md.20260905-optimization.inactive` | `75578C0A02AF6C5524630EF63A991DE7D297D157AEF3698C49DE3B660539896E` |

Запись в защищённую `.agents` выполнена через запрошенное разрешение среды.

## Статистика символов

| Файл | До | После | Изменение |
|---|---:|---:|---:|
| `AGENTS.md` | 3600 | 3022 | -578 |
| `frontends/AGENTS.md` | 2343 | 1868 | -475 |
| `services/AGENTS.md` | 1383 | 1227 | -156 |
| `tests/AGENTS.md` | 1339 | 1185 | -154 |
| `.agents/skills/rct-source-resolution/SKILL.md` | 1236 | 999 | -237 |
| `.agents/skills/rct-verification/SKILL.md` | 1522 | 1216 | -306 |
| `.agents/skills/rutcampustrack-design/SKILL.md` | 2315 | 1873 | -442 |
| **Итого активные инструкции** | **13738** | **11390** | **-2348** |
| Новые reference-файлы | 0 | 0 | 0 |
| **Итого с references** | **13738** | **11390** | **-2348** |

Символы измерены как Unicode symbols после чтения UTF-8-sig и нормализации новых
строк к LF. Независимое сравнение с исходным `project/*` kit подтвердило: 7/7
backup-файлов побайтово совпадают, `mismatch = []`, `symlinks = []`.

## Проверка

- `git diff --check` завершился без замечаний.
- Все три skills сохраняют корректный YAML front matter (`name`, `description`).
- Все семь backup-файлов существуют; SHA-256 выше фиксирует содержание исходной
  версии, скопированной до редактирования; независимая сверка с kit — 7/7 byte-equal.
- Внутренние ссылки root-инструкции существуют: `frontends/AGENTS.md`,
  `services/AGENTS.md`, `tests/AGENTS.md`, `docs/agent-workflow.md`.

## Ограничение

`rutcampustrack-design` намеренно сохраняет остановку до переноса design sources.
На момент проверки отсутствуют `docs/design/COMPONENT_REGISTRY.md`,
`brandbook-v2.md`, `tokens-v2.json`, `BRAND_DIRECTION.md`,
`A11Y_REQUIREMENTS.md`, `docs/wireframes/`; также отсутствует целевой
`docs/sources/manifest.yaml` из skill source-resolution. Это состояние источников,
не предмет данной S0-редактуры; ссылки не подменялись и не удалялись.
