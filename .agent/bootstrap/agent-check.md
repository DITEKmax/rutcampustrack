# Agent bootstrap check

Дата: 2026-09-05. Риск: S1. Получатель результата: `/root`.

## Scope и критерии

Разрешённая область этой партии — только `.agent/bootstrap/agent-check.md` и
`.agent/bootstrap/agent-check.json`. Продуктовый код, конфигурация, Figma,
контракты API, БД, `DATABASES_OVERVIEW.md`, пользовательские настройки,
секреты и память не менялись.

Критерии: адресно прочитать 12 проектных инструкционных файлов; проверить TOML
и front matter skills; зафиксировать точные SHA-256; отделить configuration/API
evidence от runtime; записать применимые документарные checks с exit code и
ограничениями. Runtime продукта для этой документационной проверки не нужен.

## Роли и маршрутизация

| Роль | Инструкция | Назначение | Selected model / effort | Writes | Result recipient |
|---|---|---|---|---|---|
| root | `AGENTS.md`, `docs/agent-workflow.md` | классифицирует, маршрутизирует и принимает | ожидается `gpt-6-astra` / `low` по user scope и config; runtime `null`, UNVERIFIED | не в этой партии | owner |
| explorer | `.codex/agents/explorer.toml` | разведка без записи | запрошены `gpt-5.6-luna` / `medium`; runtime `null`, UNVERIFIED | нет | `/root` |
| developer | `.codex/agents/developer.toml` | единственный writer этой проверки | запрошены `gpt-5.6-terra` / `medium`; runtime `null`, UNVERIFIED | только два файла этой партии | `/root` |
| reviewer | `.codex/agents/reviewer.toml` | независимое review без правок | planned `gpt-5.6-sol` / `high`; не запущен, runtime `null`, UNVERIFIED | нет | `/root` |
| judge | workflow | только при реальном споре с evidence | не назначался | нет | `/root` |

`spawn_agent` API принимает роли `developer`, `explorer`, `reviewer`; это
registry evidence о доступности описаний ролей, а не доказательство полного
native loading role files. Доступная metadata содержит canonical task name и
status, но не фактические model/effort. Поэтому ручное чтение TOML и config
подтверждает содержимое файлов, а native role loading и фактические параметры
сессий остаются UNVERIFIED.

Участники: `/root`, `/root/bootstrap_developer`, `/root/bootstrap_explorer`.
`/root/bootstrap_reviewer` — запланированный canonical ID, не создан в этой
партии.

## Проверенные источники

Прочитаны: `AGENTS.md`, `frontends/AGENTS.md`, `services/AGENTS.md`,
`tests/AGENTS.md`, `docs/agent-workflow.md`, `.codex/config.toml`, три
`.codex/agents/*.toml` и три `.agents/skills/*/SKILL.md`. Применён
`rct-verification`: для документационной задачи выполнены синтаксические и
конфигурационные проверки; запуск продукта/сервиса неприменим.

Точные SHA-256 и provenance находятся в JSON-артефакте. Hash измеряет исходные
точные bytes файлов, без нормализации текста.

## Checks и evidence

| Check | Exit | Evidence |
|---|---:|---|
| `git rev-parse HEAD` | 0 | `87784165874e2da6fc261abc1c01584e24624289` совпадает с base revision |
| `git status --short` | 0 | чужие baseline changes обнаружены и не менялись; Git вывел известные permission warnings |
| `python -c … tomllib …` | 0 | `.codex/config.toml` и все три role TOML синтаксически валидны |
| `python -c … front matter with utf-8-sig …` | 0 | 3/3 skill front matter содержат `name` и `description` |
| `rg … .agent …` | 1 | совпадений ownership/bootstrap нет; exit 1 означает отсутствие совпадений |
| `python -c … design-source paths …` | 0 | семь обязательных design/source путей отсутствуют и зафиксированы как gap |
| `python -c … JSON parse and SHA-256 reread …` | 0 | JSON разобран; 12/12 exact-byte hashes совпали при повторном чтении |
| `python -c … scoped text format …` | 0 | обе созданные карточки оканчиваются LF и не содержат trailing spaces |
| `git status --short -- .agent/bootstrap` | 0 | только `?? .agent/bootstrap/`; scoped diff: 79 Markdown + 96 JSON строк |

Первичный front-matter checker завершился exit 1, потому что не учёл UTF-8 BOM;
он не был evidence дефекта skills. Повтор с `utf-8-sig` завершился exit 0.
Первичный scoped text checker также завершился exit 1 из-за ошибочного литерала
проверяющей команды, который трактовал завершающую `t` как whitespace; адресная
диагностика показала отсутствие нарушений, исправленный checker завершился exit 0.

## Ограничения и требуемая дельта

Для будущей UI-работы отсутствуют `docs/design/COMPONENT_REGISTRY.md`,
`docs/design/brandbook-v2.md`, `docs/design/tokens-v2.json`,
`docs/design/BRAND_DIRECTION.md`, `docs/design/A11Y_REQUIREMENTS.md`,
`docs/wireframes/` и `docs/sources/manifest.yaml`. Нужен перенос/уточнение
источников владельцем или отдельной source-resolution партией до UI реализации.
Figma auth/read execution и browser runtime не проверялись; discovery этих
возможностей — PASS по данным root, их выполнение — UNVERIFIED.

Второй независимый review пока не выполнен и не может быть отмечен PASS.
