# Ответы на вопросы

✏️ 05.09.2026.

- Разработка: PWA/TMA frontend и backend идут параллельно по одному contract
  revision, в двух worktrees; затем интеграция и независимое review.
- Актуальные старые и новые job stories сохраняются в реестре с происхождением,
  отдельными decision/delivery statuses и проверяемым покрытием.
- Backend: сверяются design requests/conflicts/TRANSPORT, поздние kit decisions/
  gaps и реальный код; добавление, refactor и оптимизация имеют отдельные основания.
- Эталон: используются доказанные Vue/PCSS практики; чужой домен/federation
  и отсутствие тестов не копируются как требования проекта.
- Figma: основной путь MCP с локальным design packet; CSS можно передать как
  часть ручного пакета со screenshot/assets/states. Поэкранный CSS не обязателен.
- Старый дизайн: superseded материалы архивируются, удаление идёт по карте
  replacement/consumers/story coverage. Актуальные kit/mobile/web-later источники
  и истории не объявляются устаревшими автоматически.

Полный процесс: [playbook](../../_work/prompts/agent-project-start/parallel-development-playbook.md).
Порядок запуска: [08 подготовка](../../_work/prompts/agent-project-start/08-prepare-product-repository.md)
→ [09 Figma packet](../../_work/prompts/agent-project-start/09-extract-figma-packet.md)
→ [10 реализация](../../_work/prompts/agent-project-start/10-implement-parallel-story.md).
[11 cleanup](../../_work/prompts/agent-project-start/11-retire-legacy-ui.md) — после замены.
Проверка обвязки 01 и запрос команды 07 остаются применимы.

## Разбор, источники и ограничения

Это продолжение подготовки инструкций; новые промпты не исполнялись.
Текущее решение владельца заменяет статус «параллельность — рекомендация» и
последовательный маршрут прежнего 06 для сквозной истории. Один writer остаётся
у общей папки/контрактов/lockfiles; два developer имеют разные worktrees и scopes.

Прочитаны frontend research, релевантный playbook, TRANSPORT, начальные разделы
backend requests/conflicts и job stories, поздние gaps kit. Полного пересчёта
всех актуальных stories/операций не было; старые счётчики не названы живым backlog.
Устаревшее «транспорт ещё не выбран» в requests учитывается как история:
TRANSPORT уже принимает отдельный REST BFF над gRPC. Запрос и решение не равны
готовому endpoint, а запись to-owner не равна принятому продуктовому требованию.

Применены прочитанные rutcampustrack-design и figma-design-to-code инструкции
для описания будущего переноса. Figma content не запрашивался и не менялся.
Выполнен один whoami: аккаунт имеет Full/Professional в Ruttrack, также другие
team/seat; принадлежность конкретного файла не проверена. Лимиты проверены на
[официальной странице](https://developers.figma.com/docs/figma-mcp-server/rate-limits-access/),
whoami исключён из read-квоты. Локальный журнал вызовов не знает чужие сессии.

## Проверка

Другим проходом проверены существование локальных ссылок, UTF-8 и code fences.
Пять новых файлов процесса/промптов существуют. Старые инструкции дополнены
явным указателем на замену, история не удалена. Новые model/API настройки
и конфигурация основного repo в этой партии не менялись.

Геометрические изменения — 0. Общие мастера/потребители не затронуты.
Литералы и semantic bindings в Figma не проверялись: интерфейс не редактировался.
Числа файлов и обращений имеют названные единицы, не Figma A.

## Что не сделано и почему

- Нет копирования материалов/создания текущего story registry: это исполняет 08.
- Нет выгрузки Figma: 09 сначала определяет выбранную историю, узлы и budget.
- Нет реализации/тестирования продукта или удаления старого UI: 10/11 —
  последующие конкретные задания в основном repo.
- Не закрыты неизвестные product decisions, Figma file team/node mapping,
  test device/environment availability. Промпты требуют их адресной проверки,
  продолжая независимые пункты.

## Изменённые файлы со строками

- `_work/prompts/agent-project-start/parallel-development-playbook.md:1` — полный процесс.
- `_work/prompts/agent-project-start/08-prepare-product-repository.md:1` — подготовка.
- `_work/prompts/agent-project-start/09-extract-figma-packet.md:1` — пакет дизайна.
- `_work/prompts/agent-project-start/10-implement-parallel-story.md:1` — FE/BE параллельно.
- `_work/prompts/agent-project-start/11-retire-legacy-ui.md:1` — cleanup.
- `_work/prompts/agent-project-start/README.md:74` — новый порядок запуска.
- `_work/prompts/agent-project-start/06-start-development.md:64` — отмена прежнего маршрута для этой работы.
- `journal/DECISIONS.md:2593` — текущее решение владельца.
- `journal/reports/2026-09-05-parallel-mobile-backend-process.md:1` — отчёт.
