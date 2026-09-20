# Codex agent routing

Этот файл — канонические общие правила маршрутизации Codex для всех проектов.
Проектные `AGENTS.md` описывают только границы и процедуры проекта и ссылаются
сюда; они не переопределяют этот маршрут без нового решения владельца.

## Root и coordinator

Root — текущий Codex на `gpt-6-astra` с effort `medium`. `low` допустим для
очевидного плана S0/S1, а `high` — для критичной архитектуры, продукта или
риска. Root классифицирует риск, открывает критичные исходные источники,
фиксирует compact contract и принимает evidence.

Root может создать bounded task-scoped coordinators на `gpt-6-astra` с effort
`low` или `medium`, только когда независимые scopes и доступные slots делают их
полезными. Все coordinators находятся ровно в одном coordinator layer.
Coordinator read-only по repository/data/external state: он делает узкий
поиск/сверку, возвращает компактные files/symbols, patterns/dependencies,
constraints, uncertainties и locations, но не пишет code, docs, evidence, data
или внешние приложения. Только coordinator, которому root явно назначил эту
функцию, может spawn назначенные ему свежие Luna max leaves с явным packet.
Обычный scout/explorer детей не создаёт. Coordinator не может создавать другой
coordinator или новый layer. Leaves не создают детей. Если coordinator мешает
полезной параллельности, root направляет leaf напрямую. Persistent
`coordinator` role/config key не добавляется: для функции используется
существующая read-only роль `explorer`.

## Model/effort и риск

| Работа | Model ID | Effort |
|---|---|---|
| Обычный root | `gpt-6-astra` | `medium` |
| Очевидный план S0/S1 | `gpt-6-astra` | `low` |
| Критичная архитектура, продукт, неопределённость или риск | `gpt-6-astra` | `high` |
| Bounded read-only coordinator | `gpt-6-astra` | `low` или `medium` |
| Свежий bounded implementation leaf | `gpt-5.6-luna` | `max` |
| Подтверждённая implementation/debug escalation | `gpt-5.6-terra` | `high` |
| Важное независимое review | `gpt-5.6-sol` | `high` |
| Исключительный S4 review | `gpt-5.6-sol` | `xhigh` с явным обоснованием |

Каждый spawn получает свежий compact packet, явные model и effort и
`fork_turns="none"`. Default implementation leaf — Luna max, включая S3;
риск выбирает safeguards, checks и review, а не автоматически Terra. Terra не
является fallback: она возможна только при recorded defect или подтверждённой
границе сложности, где packet содержит request/reference, воспроизведение,
новое evidence, correction, bounded scope и решение root. Повтор без нового
evidence запрещён.

## Минимальный маршрут по риску

| Риск | Маршрут |
|---|---|
| S0 | Root → свежий Luna max leaf для крошечной ограниченной правки или проверки → применимые checks; coordinator и review не обязательны. |
| S1 | Root contract → Luna max leaf; coordinators Astra low/medium только при пользе → checks/runtime. |
| S2 | При пользе bounded coordinators Astra low/medium в одном layer → root contract → Luna max leaf → checks/runtime → свежий Sol high для важного review. |
| S3 | При пользе bounded coordinators в одном layer → root contract → Luna max leaf → checks/runtime → свежий Sol high review; Astra high architecture review при критичности, риске или неопределённости; Terra high только через evidence gate. |
| S4 | Явное обоснование root/владельца; Sol xhigh допускается только для независимого review как исключение, сохраняются все S3 checks, runtime и независимость review. |

S0–S3 не требуют Terra по одному только номеру риска. Свежий Sol review не
становится исполнителем исправлений. После FAIL фиксируются severity/defect,
evidence, correction, scope и verification; правку выполняет ближайший способный
leaf, затем проводится независимая recheck затронутой части.

## Handoff и evidence

Цепочка: узкое read-only evidence → root/planner открывает критичные оригиналы
и формирует contract → fresh leaf получает packet и необходимые файлы →
applicable checks/runtime → fresh Sol reviewer получает исходную цель, contract,
стабильный diff и evidence. Reviewer сам открывает критичные оригиналы и не
использует скрытые reasoning или полный transcript автора. Contract содержит
девять разделов: Goal, Context/evidence, Relevant scope, Required behavior,
Constraints, Existing patterns, Acceptance criteria, Verification и Do not.

При coordinator fan-out root сохраняет один frozen contract, а каждый coordinator
получает собственный bounded scope и не пересекающиеся resources. Один shared
checkout имеет одного writer; parallel writers работают только в независимых
worktrees с frozen baseline/revision и выделенными runtime resources. Shared
contracts, generated types, lockfiles, configs, docs и status имеют одного
writer. Не создавай скрытый CLI orchestrator или постоянный второй control plane.

## Проверка выбора и состояния

Фактические model/effort подтверждаются runtime metadata конкретного spawn.
Config задаёт defaults и не меняет параметры уже запущенной сессии. Поле
`agents.max_concurrent_threads_per_session` — лимит детей, а не обещание общего
числа slots; его значение и другие config keys должны соответствовать
официальной схеме. Если поверхность не поддерживает выбранную пару, фиксируй
BLOCKED и получай решение root, а не подменяй модель молча.

Эти правила не разрешают deploy, production migration, удаление данных/backup,
firewall или rotation secrets. Для таких действий нужны отдельные проверки,
готовый rollback и явное разрешение на конкретную операцию.

Проверенные источники схемы и standalone roles: [config schema](https://learn.chatgpt.com/docs/config-schema.json),
[subagents](https://learn.chatgpt.com/docs/agent-configuration/subagents).
