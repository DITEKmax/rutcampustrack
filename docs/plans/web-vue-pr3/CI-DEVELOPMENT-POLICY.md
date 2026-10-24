# Временная пауза автоматических проверок новой Vue ветки

Решение владельца 2026-10-23: во время активной разработки нового фронта
регулярные push/обновления PR не запускают CI, Coverage и Security.
Вернуть проверки после готовности фронта и просмотра владельца — только
по его отдельной команде. Это не разрешение merge без проверок или deploy.

## Что подготовлено

- Ветка: `codex/web-vue-redesign`, база `aa98a6aabd1463ee72ce2ef09a3195fdeef7b74f`.
- В версиях `.github/workflows/ci.yml`, `coverage.yml`, `security.yml`,
  `openapi-drift.yml` этой ветки автоматические events временно заменены на
  `workflow_dispatch`. Jobs, permissions и прочее содержимое сохранены.
- OpenAPI тоже на паузе: frontend lockfile/types иначе запускали бы ещё один
  workflow во время разработки, несмотря на паузу трёх названных владельцем.
- `deploy.yml` не меняется. Его автоматический запуск ограничен успешным
  push CI на main; разрабатываемая feature ветка не является этим событием.
- Основная ветка, другие PR и repository Actions/security settings не отключены.
  Security schedule по-прежнему берётся из default main и проверяет main.

Это подготовка branch config. Не утверждать GitHub runtime PASS только
по YAML parse; состояние запусков после публикации ветки проверяется отдельно.
GitHub-required checks могут отображаться Expected/Pending и блокировать merge.
Такой статус ожидаем при паузе, это не красный/зелёный результат тестов.

## Как работать будущему чату

1. Открыть именно подготовленную ветку, сохранить `on: workflow_dispatch`
   во всех четырёх workflow во время обычных commits/pushes.
2. Не рассчитывать на draft PR, фильтр имени head в `pull_request.branches`
   или один `[skip ci]`: draft не отключает workflows, branches для PR
   фильтрует target/base, а commit marker действует только на конкретное событие.
3. При интеграции main/чужого пакета и после Angular/CI wiring правок проверить,
   что автоматические events не вернулись. Не добавлять отдельный автоматически
   запускаемый тестовый workflow, обходящий решение владельца.
4. Первый подготовительный commit дополнительно помечен `[skip actions]`.
   Это страховка для push/PR, не основное постоянное управление и не средство
   отключить `schedule`, `workflow_run` или `pull_request_target`.
5. Не выключать Actions глобально, не снимать branch protections и не публиковать
   фиктивные successful checks. Паузу не выдавать за проверку продукта.
6. По месту выполнять нужные typecheck/build и реальные dev сценарии.
   Политика новых тестов — раздел12 IMPLEMENTATION-PLAN.md.

`workflow_dispatch` делает YAML корректным и убирает автоматические события.
Наличие удобной кнопки Run workflow до изменения default branch не обещается:
в исходном main у этих четырёх workflows нет manual event. Пользователю
не требуется сейчас менять main или настройки репозитория ради этой кнопки.
Ручные production Deploy dispatch не запускать.

## Как вернуть проверки после отдельного разрешения

Подготовить один явный commit активации. Вернуть ТОЛЬКО event blocks,
используя исходные блоки как reference; сохранить обновления Vue jobs.
Reference — WORKFLOW-ORIGINAL-TRIGGERS.json и `git show <base>:.github/workflows/<file>`.
Если main изменился, учесть его новые триггеры/политику, а не слепо восстановить
устаревший файл целиком. Удалить комментарий временной паузы.

Исходная логика:

| Workflow | Push | PR | Другие события |
|---|---|---|---|
| CI | Все ветки, с paths-ignore | Все target ветки, с paths-ignore | Нет |
| Coverage | main/dev, с paths-ignore | Все target ветки, с paths-ignore | Нет |
| Security | main, с paths-ignore | В main, с paths-ignore | Weekly schedule на default branch |
| OpenAPI | Все ветки, ограниченные paths | Все target ветки, ограниченные paths | Нет |

Проверить YAML и diff, новый web входит в итоговое сканирование и нужную сборку,
deploy остаётся вне feature ветки. Активационный commit не содержит skip markers;
в открытом PR изменение workflow files запускает применимые проверки.
OpenAPI сработает по своему paths-фильтру (свой workflow изменён), остальные
учитывают свои события. Дождаться результатов на актуальном HEAD.

Не делать `git restore` всех workflow из baseline: это уничтожит нужные
frontend job changes. Не merge временно выключенные automatic events в main.
Этот документ остаётся журналом решения, после активации отметить её commit.

## Официальные источники

- [Skipping workflow runs](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/skip-workflow-runs): commit markers относятся к push/pull_request; skipped required checks могут оставаться Pending.
- [Events that trigger workflows](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows): PR фильтры, schedule на default branch, условия workflow_dispatch и workflow_run.

Документация проверена 2026-10-23. Runtime settings/внешние GitHub Apps
могут иметь независимые triggers; этот пакет не выключает их глобально.
