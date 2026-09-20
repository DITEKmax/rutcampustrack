**PASS — routing v2. Findings отсутствуют.**

Проверено:

- Active [global AGENTS.md](C:/Users/maksd/.codex/AGENTS.md:14) реализует один coordinator layer, Astra low/medium coordinators, Luna max leaves, Terra evidence gate и fresh Sol review. Локальные [AGENTS.md](C:/Users/maksd/IntelliJIDEA/rutcampustrack/AGENTS.md:25) и [workflow](C:/Users/maksd/IntelliJIDEA/rutcampustrack/docs/agent-workflow.md:9) не содержат конфликтующей model/risk таблицы.
- Global и project role-файлы совпадают со staged payloads и не закрепляют model/effort. Официальная документация подтверждает используемые `[agents]` keys и precedence явного spawn над defaults: [Configuration Reference](https://learn.chatgpt.com/docs/config-file/config-reference), [Subagents](https://learn.chatgpt.com/docs/agent-configuration/subagents).
- 12 active/staged TOML-файлов разобраны успешно; PowerShell AST check прошёл. Backup содержит девять исходных файлов с hashes из preconditions.
- [Apply script](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/routing-20260906-v2/apply-protected-routing.ps1:141) ограничен девятью targets, проверяет stage/live hashes и paths до записи, создаёт backup, выполняет same-directory replacement, readback и rollback.
- Stable diff независимо сверен через v2 archive и предыдущий [global stage](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/routing-20260906/global-stage/AGENTS.md:1).
- Runtime tool results подтверждают coordinator→leaf spawn, четыре одновременно открытых thread и отказ дополнительного spawn на capacity. Запрошенные model/effort были приняты spawn API; эффективные скрытые model metadata не заявляются.
- Решение для будущих партий записано в [workflow](C:/Users/maksd/IntelliJIDEA/rutcampustrack/docs/agent-workflow.md:24): API/shared freeze, параллельные экраны, отдельные role E2E с negative cases, Docker при необходимости и fresh Sol review.

Неблокирующий для routing gap: полный [manifest.yaml](C:/Users/maksd/IntelliJIDEA/rutcampustrack/docs/sources/manifest.yaml:31196) сейчас не разбирается из-за независимого malformed fragment `decision]cate`. Routing record на строках 18–37 цел и был успешно разобран до последующей общей правки. Repository-level DONE требует исправить этот внешний fragment и повторить whole-file parse, но это не дефект routing v2. Product runtime/tests для документационно-конфигурационного scope неприменимы.
