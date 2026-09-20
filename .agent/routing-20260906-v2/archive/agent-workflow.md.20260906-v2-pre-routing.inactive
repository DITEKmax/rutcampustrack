# Агентный workflow RutCampusTrack

✏️ 06.09.2026, решение владельца. Этот документ заменяет маршрутизацию от
05.09.2026. Control plane — текущий Codex; он не запускает второй orchestrator и
не обещает качество, цену или scheduler модели.

## Выбор модели и риск

| Работа | Model ID | Effort |
|---|---|---|
| Root orchestration | gpt-6-astra | medium |
| Очевидный план S0/S1 | gpt-6-astra | low |
| Неоднозначность, архитектура, продуктовый или риск-вопрос | gpt-6-astra | medium/high |
| Узкий scout и fallback | gpt-5.6-luna | high |
| Ограниченная реализация | gpt-5.6-luna | max |
| Эскалация реализации или разбора | gpt-5.6-terra | high |
| Hard debug, security, concurrency, важное review | gpt-5.6-sol | high |
| Исключительный S4 после обоснования | gpt-5.6-sol | xhigh |

Astra medium — обычный root. Astra high используется лишь для критичной
архитектуры, продукта или риска. Terra medium допустима для сложных связей,
Terra high предпочтительна при эскалации реализации. Sol xhigh требует явного
S4-обоснования и никогда не ослабляет меры S3.

## Минимально достаточный маршрут

| Риск | Маршрут |
|---|---|
| S0 | Luna high для точного поиска либо Luna max для крошечной ограниченной правки → применимые checks → итог root; отдельный scout, planner и review не обязательны. |
| S1 | Узкий scout при необходимости → compact contract → Luna max → checks/runtime. |
| S2 | Luna high scout → Astra low/medium contract → Luna max; при сложности Terra high → checks/runtime → свежий Sol high важного review. |
| S3 | Scout → Astra medium/high contract → Terra high или Sol high → tests/runtime → свежий Sol high review; свежий Astra medium/high architecture review при критичной архитектуре, риске или неопределённости. |
| S4 | Исключение: обоснованный Sol xhigh или Astra high; S3-предохранители сохраняются. |

Root активно делегирует полезную ограниченную работу, чтобы сохранять контекст;
не создаёт фиксированный «зоопарк» ролей и не поручает широкое исследование без
конкретного вопроса. Обычный task в shared folder имеет одного writer. Root,
explorer и reviewer не пишут код; developer — единственный writer в назначенной
области. После contract freeze FE и BE одной истории могут работать в разных
worktrees с общими baseline и revision; contracts, generated types, lockfiles,
configs, docs и общий status остаются у одного writer, а интеграцию делает
назначенный developer.

## Эскалация и независимость

Эскалация нужна при дефекте контракта/evidence, коррекции scope, непонятном
failing check, архитектурной границе или риске security/data loss/concurrency.
Сначала устрани причину и получи новую информацию; не повторяй ту же попытку.
Цепочка: cheapest capable Luna max, затем Terra high или Sol high, затем
независимая повторная проверка. Выбирай ближайшую способную модель, а не
проходи ступени формально.

Важный review выполняет свежий Sol high без transcript автора. Свежий
Astra medium/high architecture review опционален при критичной архитектуре,
риске или существенной неопределённости. Reviewer получает criteria, стабильный diff и evidence;
последующая правка требует повторной проверки затронутой части.

## Spawn и роли

`.codex/config.toml` задаёт root и безопасный fallback. Файлы explorer,
developer и reviewer задают только поведение роли: model и effort в них не
закрепляются, потому что они перекрыли бы routing явного запуска. Каждый spawn
передаёт обязательную пару model/effort, свежий compact packet и `fork_turns="none"`;
полная история не используется для override. Фактические параметры подтверждай
runtime metadata. Config не меняет параметры уже запущенной сессии.

Если поверхность не поддерживает выбранные model/effort, фиксируй BLOCKED
routing и используй явно поддержанный выбор. Не создавай скрытый CLI-orchestrator.

Handoff обязателен: research даёт compact evidence; planner открывает критичные
оригиналы, а не только пересказ scout, и формирует contract; developer получает
contract и необходимые файлы; reviewer получает исходную цель, contract, diff и
checks. Explorer не пишет: он возвращает кратко files/symbols,
patterns/dependencies, tests/constraints, uncertainties и locations. Developer
читает применимые AGENTS.md и skills, сохраняет evidence, запускает проверки и
runtime. Reviewer не вносит правки и формулирует finding как severity, file:line,
evidence, impact и воспроизведение.

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
