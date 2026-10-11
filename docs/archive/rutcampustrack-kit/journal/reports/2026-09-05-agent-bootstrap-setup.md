# Ответы на вопросы

✏️ 05.09.2026.

- Исходный документ исправлен датированными дополнениями; старые назначения
  моделей и допущение shadcn-vue явно отменены, история сохранена.
- В kit настроены root Astra low, fallback Terra medium и три дочерние роли.
  Политика routing: Luna medium — рутина/поиск, Terra medium/high — основная
  разработка, Sol high — сложная работа/важное review, Astra medium/high — решения.
- Новый frontend: только PCSS/PostCSS, размерные CSS-длины rem, без shadcn/Tailwind.
- Для переноса подготовлены три коротких skills; старые 18 проектных skills
  основного repo будут архивированы установщиком. Глобальные plugins не удалялись.
- Настройка следующего проекта и стартовый запрос:
  [README](../../_work/agent-bootstrap/README.md). Открыть существующую папку
  `C:/Users/maksd/IntelliJIDEA/rutcampustrack`, установить пакет, подтвердить trust,
  начать новую задачу Astra low и проверить effective model/effort.

## Разбор и важные уточнения

Переданное исследование использовано как требования к новой архитектуре;
старые промпты внутри отчёта не исполнялись. Пользователь отделил подготовку
здесь от последующего переноса материалов и реализации в основном repo.

Обычный S1 reviewer — Terra high; default для важного review — Sol high.
Это уточняет две формулировки исходного исследования без второго постоянного
reviewer. Дочерние TOML намеренно не закрепляют model/effort: по официальному
precedence это перекрыло бы динамический выбор при spawn.

PCSS — постпроцессорный CSS; rem применяется к размерным длинам, а не к времени,
безразмерным коэффициентам и координатам screenshot API. Слово «shadow» в запросе
трактовано в контексте старого документа как shadcn; принятые дизайн-тени сохранены.

При отдельном чтении поздних решений обнаружен устаревший охват design skill:
desktop-запреты на glass/gradient/glow и несколько акцентов не относятся ко всему
mobile. Краткий skill учитывает исключения 01–05.09.2026; обе старые копии получили
указатель на них. Это применение уже принятого решения, а не изменение дизайна.

Репозиторий [vibe](https://github.com/di-sukharev/vibe) проверен по полному tree
commit `820ca32865c27063add8885139d2b1687cd8030f`: SKILL.md отсутствуют.
Ничего из его другого стека не устанавливалось. Подробности:
[skill-audit](../../_work/agent-bootstrap/skill-audit.md).

## Проверки и ограничения

- TOML kit и пакета разбирается; поля project config сверены с официальной схемой.
- Три новых skills прошли официальный quick_validate; Markdown/UTF-8 проверены.
- Установщик проверен в отдельной тестовой папке: preview без записи,
  сохранение оригиналов/кода, неактивный архив, идемпотентность, отказ на неизвестном
  skill/config/неподходящей папке до записи. На основном repo выполнен только preview.
- Заключительная сверка отдельно перечитала поздние продуктовые решения, копии
  design skill, TOML и статус основного repo. Субагент-review не запускался.
- Команда strict-config для `features list` не поддержана CLI 0.135.0;
  это ограничение, а не успешный runtime-тест. Подробный протокол:
  [validation](../../_work/agent-bootstrap/validation.md).
- Основной repo не изменён; ранее существовавший untracked DATABASES_OVERVIEW.md
  сохранён. Kit не является Git checkout, поэтому Git diff для kit недоступен.

Figma не читалась и не менялась. Геометрические изменения — 0; общие мастера
не затронуты. Потребители, литералы мастера/QA и смысловые token bindings не
пересчитывались: интерфейс не редактировался. Реестр не дополнялся.
Числа файлов/skills/ролей в этом отчёте имеют названные единицы, не Figma A.

## Что не сделано и почему

- Установка и архивирование старых skills в основном repo: отложены до переноса,
  как обозначено владельцем. Готовый установщик сначала показывает план.
- Перенос design/wireframes/research: следующая партия. Целевые ссылки на эти
  документы в skills не означают, что документы уже перенесены.
- Живой model-routing smoke-test новой задачи: требуется новый проект/сеанс.
  Config не доказывает параметры уже работающей задачи.
- Жёсткие ACL по ролям: набор инструкций не заменяет runtime permissions;
  пакет не выдаёт текстовый запрет за техническую изоляцию и не ослабляет sandbox.
- Vue/BFF, БД, запуск продуктового стека, deploy: вне текущей настройки.

Открытые продуктовые решения старого плана (release scope, конкретный канон
BFF-контракта, query/cache owner, offline-политика) этой партией не закрывались.

## Изменённые файлы со строками

Пути ниже относительно корня kit; `:1` у нового файла означает весь созданный файл.

| Файл | Начало изменения |
|---|---:|
| AGENTS.md | 285 |
| .codex/config.toml | 6 |
| .codex/agents/explorer.toml | 1 |
| .codex/agents/developer.toml | 1 |
| .codex/agents/reviewer.toml | 1 |
| .agents/skills/rutcampustrack-design/SKILL.md | 275 |
| .claude/skills/rutcampustrack-design/SKILL.md | 275 |
| journal/DECISIONS.md | 2569 |
| journal/reports/2026-09-05-agent-development-vue-bff-plan.md | 5; адресные поправки, итог с 723 |
| journal/reports/2026-09-05-agent-bootstrap-setup.md | 1 |
| _work/agent-bootstrap/README.md | 1 |
| _work/agent-bootstrap/skill-audit.md | 1 |
| _work/agent-bootstrap/validation.md | 1 |
| _work/agent-bootstrap/install.ps1 | 1 |
| _work/agent-bootstrap/test-install.ps1 | 1 |
| _work/agent-bootstrap/validate.py | 1 |
| _work/agent-bootstrap/project/AGENTS.md | 1 |
| _work/agent-bootstrap/project/.codex/config.toml | 1 |
| _work/agent-bootstrap/project/.codex/agents/explorer.toml | 1 |
| _work/agent-bootstrap/project/.codex/agents/developer.toml | 1 |
| _work/agent-bootstrap/project/.codex/agents/reviewer.toml | 1 |
| _work/agent-bootstrap/project/.agents/skills/rutcampustrack-design/SKILL.md | 1 |
| _work/agent-bootstrap/project/.agents/skills/rct-source-resolution/SKILL.md | 1 |
| _work/agent-bootstrap/project/.agents/skills/rct-verification/SKILL.md | 1 |
| _work/agent-bootstrap/project/frontends/AGENTS.md | 1 |
| _work/agent-bootstrap/project/services/AGENTS.md | 1 |
| _work/agent-bootstrap/project/tests/AGENTS.md | 1 |
| _work/agent-bootstrap/project/docs/agent-workflow.md | 1 |
| _work/vibe-tree-2026-09-05.json | 1 |
| _work/codex-config-schema-2026-09-05.json | 1 |

Тестовые исходные и результирующие файлы:
`_work/agent-bootstrap/test-output/20260905-225630-971/`.
Они являются evidence установки и не входят в переносимую папку project.
