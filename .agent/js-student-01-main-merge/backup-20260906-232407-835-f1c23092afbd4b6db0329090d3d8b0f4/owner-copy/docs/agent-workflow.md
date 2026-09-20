# Агентный workflow RutCampusTrack

✏️ 06.09.2026, решение владельца. Общий model/risk routing, coordinator layer,
leaf boundary и spawn-параметры каноничны в глобальном
`C:\Users\maksd\.codex\AGENTS.md`. Этот project-specific документ не повторяет
его таблицы и описывает порядок работы RutCampusTrack. Control plane — текущий
Codex; второй orchestrator и собственный scheduler не создаются.

## Project procedure

Сначала root фиксирует scope, риск, ownership и compact contract из девяти
разделов ниже. Root/planner открывает критичные оригиналы; bounded read-only
coordinators возвращают только узкие evidence и могут запускать назначенные
fresh leaves в рамках одного coordinator layer. Coordinator не пишет repo,
docs/evidence или внешнее состояние; leaves не создают детей. Если отдельный
coordinator не добавляет полезной параллельности, root направляет leaf напрямую.

Обычная задача в shared checkout имеет одного writer. FE и BE одной истории
работают в разных worktrees только после contract freeze и сверки
baseline/revision; parallel writers используют независимые worktrees и
выделенные runtime resources. Shared contracts, generated types, lockfiles,
configs, docs и общий status имеют одного writer. Worktree не изолирует ports, DB
и volumes: интегратор выделяет runtime resources и проверяет объединённый diff.

✏️ 06.09.2026, решение владельца для будущих партий. После freeze API и shared
components независимые экраны можно выполнять параллельными fresh Luna max
заданиями; затем для каждой роли в партии создаются отдельные задания на
end-to-end сценарий от входа до результата, включая negative cases. При
необходимости интегратор поднимает объединённый Docker runtime, после чего
проводится fresh независимый Sol important review. Старых пользователей и
данных нет, поэтому compatibility layer для legacy не добавляется; проверяются
принятые новые flows, authz и data invariants. В текущей партии пять defects,
TMA checks отложены владельцем; финальную интеграцию в main root выполнит позже,
сохранив pre-dirty состояние.

## Escalation and independence

При дефекте contract/evidence, failing check, архитектурной границе или риске
сначала зафиксируй воспроизведение и новое evidence. Любая implementation/debug
эскалация проходит bounded repair contract (defect, request/reference,
reproduction, evidence, correction, scope и root decision); один только риск S3
не назначает более тяжёлый implementer. После правки обязательна независимая
recheck затронутой части.

Важный review выполняет fresh независимый reviewer по глобальному канону. Он
получает исходную цель, contract, стабильный diff, checks и ссылки на критичные
оригиналы, открывает оригиналы сам и не меняет файлы. Findings содержат
severity, file:line, evidence, impact и воспроизведение.

## Spawn и handoff

Каждый spawn получает явные model/effort, свежий compact packet и
`fork_turns="none"`; роль не закрепляет model/effort в TOML. Фактическую пару
подтверждай runtime metadata, а config считай только default для новых spawn:
он не меняет уже запущенную сессию. Если поверхность не поддерживает выбранную
пару, зафиксируй BLOCKED и сообщи root.

Handoff: узкое evidence → root/planner и критичные originals → contract → fresh
leaf и необходимые файлы → checks/runtime → independent review. Explorer
возвращает files/symbols, patterns/dependencies, tests/constraints,
uncertainties и locations; developer читает applicable AGENTS.md/skills,
сохраняет evidence и summary; reviewer не вносит правки. Полный transcript
автора не заменяет packet и evidence.

## Contract и состояние задачи

До реализации root/planner формирует compact task contract с разделами:

1. Goal.
2. Context/evidence.
3. Relevant scope.
4. Required behavior.
5. Constraints.
6. Existing patterns.
7. Acceptance criteria.
8. Verification.
9. Do not.

Для S1–S3 developer ведёт `.agent/` с packet, evidence, checks и summary;
добавляет decision только когда реально принято решение. Packet фиксирует base
revision, назначенную модель/effort, ownership и окружение. В evidence нет
секретов, персональных данных и полных transcripts.

При FAIL сначала зафиксируй дефект, evidence, нужную correction, scope и
verification; затем назначь cheapest capable implementer и независимую recheck.
WARN/ERROR сам по себе не дефект: свяжи его с запросом, воспроизведением и
критерием до изменения кода.

## Проверка и приёмка

Используй `rct-verification`: сопоставь checks с criteria, запиши revision,
command, exit code, среду и evidence. Документация/TOML требует синтаксической
и ссылочной проверки; запуск продукта для неё не применим. Runtime доказывает
изменённый сценарий, а не весь продукт. Непройденный обязательный check —
BLOCKED. DONE требует criteria, применимые checks, runtime где применим,
требуемое review, чистый scope diff и отсутствие незакрытых critical findings.

S3 human gate относится к конкретной опасной операции после diff, checks и
rollback; локальные безопасные проверки и исправления отдельного разрешения не
требуют. Deploy, production migration, удаление данных/backup, firewall и
rotation секретов требуют отдельного разрешения.

## Проверенные источники конфигурации

Схема конфигурации и runtime-поля подтверждены в
[Config schema](https://learn.chatgpt.com/docs/config-schema.json).
Standalone роли и precedence описаны в
[Subagents](https://learn.chatgpt.com/docs/agent-configuration/subagents).
Проектный config и его precedence описаны в
[Config basics](https://learn.chatgpt.com/docs/config-file/config-basic).
