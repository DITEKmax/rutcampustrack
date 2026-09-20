# Task packet — карта переноса материалов

- Дата: 2026-09-05; база: `87784165874e2da6fc261abc1c01584e24624289`.
- Scope: только новая карта в `.agent/migration/`; не копировать продукт, код, конфиги, исходные материалы и не менять Figma/API.
- Риск: S1 (неверная карта может исказить последующую миграцию, но данная партия ничего не переносит).
- Маршрут workflow: ordinary developer, целевой `gpt-5.6-terra` / `medium`; runtime metadata этой сессии не предоставлена.
- Источники read-only: `rutcampustrack-kit`, `rutcampustrack-design`, repository-research-contour reports. Текущие `docs`, `.planning`, `CLAUDE.md` — история, не новый канон.
- Критерии: пофайловый путь/category/size/hash/destination/status/reason; у duplicate есть retained address; исключённые деревья не обходятся и не получают ложные нулевые totals; JSON валиден; семантические споры вынесены отдельно.
- Нельзя: перезаписывать текущие AGENTS/skills, читать секреты, трогать `DATABASES_OVERVIEW.md`, source roots или чужие файлы в `.agent`.
