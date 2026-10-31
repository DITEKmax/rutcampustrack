# Обычная диагностика: переносимый контракт вместо source pins

Прямое задание владельца25октября2026:
[полный contract](https://github.com/DITEKmax/rutcampustrack/pull/3#issuecomment-6396083761).
Baseline сохранён: `2b297a50656617cb224f4d58aa605cbe92ab6992`, local ref
`codex/pr3-preserved-checkpoint-2b297a506`. Архивы предыдущего checkpoint не меняются.
Это пакет существующего Goal/PR3, не новый Goal и не новая тестовая инфраструктура.

## Результат для разработчика

Сдвиг теста из-за комментария/пустых строк и другой каталог checkout не требуют
ручного repin и не меняют семантику диагностики. Ubuntu и CI вызывают общий модуль
с одинаковыми значимыми параметрами; paths/resources задаются своим контекстом.
Реальный сбой остаётся FAIL. Неизвестные или поддельные case/stage/schema/поля,
приватные данные и несогласованный контекст отвергаются.

## Подтверждённые исходные findings

- `.github/workflows/ci.yml`: inline Python с четырьмя SOURCE SHA maps,
  дополнительными OWN_FRAME/housekeeping pins и fixed file/line identity.
- `tests/e2e/fixtures/http500-diagnostic-contract.py`: AST извлекается из workflow;
  проверяются текущие source SHA, lines410/1942/5942 и фрагменты реализации.
- Producer chain safe-reporter/mobile-dataset/specs: case определяется координатами
  и сообщениями; Playwright меняет runtime annotations, поэтому регистрация должна
  фиксироваться до fixtures, а не браться из worker data на onTestEnd.
- Collector/load/persist зависят от GitHub-hosted/RUNNER_TEMP/run prefix,
  фиксированных `rct-e2e`, env и Compose inputs. Local launcher использует другой
  owner nonce/generated Compose и не вызывает общий diagnostic path после failure.
- Связанный `check_pipeline.py` использует basename spec как oracle.
- Корректные защиты `ROOT=...__file__`, dynamic per-run source/config/CA manifest
  в `local_e2e_async.py` сохраняются, это не дефекты.

## Scope и владельцы

| Владелец | Единственный writer |
|---|---|
| disk_files | Общий конечный JSON catalog/schema, Python validator/CLI/contract tests, CI inline extraction и callers, local_e2e_async и связанный Python pipeline consumer |
| group_resume | TS producers/reporter/регистрация и только связанные specs/helpers |
| root | Общая документация, ownership, Git integration и PR |
| angular_review | Read-only linked portability audit; исходные findings переданы авторам |
| Независимый reviewer | Coherent privacy/security diff после реализации; без авторства |

Исполнители в отдельных сохранённых worktrees от2b297a. Shared catalog имеет одного
владельца; TS/Python читают один контракт. Locks/generated business types/DB/API
не меняются. Модель/effort существующих агентов сохраняются: gpt-6.1-sol/high;
telemetry недоступна, fallback не заявляется.

## Минимальное решение

Стабильные case ID регистрируются до fixtures; file/line остаются ограниченной
необязательной диагностикой. Версионированные события имеют закрытую схему и
конечные допустимые case/stage/event/status сочетания. Human error text не oracle.
Для worker→reporter сохраняется привязка к доверенной регистрации и actual failure;
частный per-run envelope не публикует raw exception/stack, ключ или подпись.
Это защита данных текущего запуска, не утверждение безопасности от произвольного
вредоносного кода самого доверенного checkout.

Одна импортируемая/запускаемая Python реализация заменяет carrier в YAML и его AST
извлечение тестом. GitHub/local ownership admission различаются честно; local не
выдаёт себя за hosted. Исходный exit/failure сохраняется через diagnostic/cleanup.
Ручные expected SHA текущих исходников и line bounds уходят из ordinary gates.

Не удаляются download/artifact bytes, build/runtime identity, digest ДО операции,
source restoration, dynamic per-run manifest, owner/resource fences, CAS/idempotency,
ambiguity/closed schemas, timeout/byte limits и cleanup/privacy. Immutable receipts
и их hashes остаются историей, а не переписываются под новый формат.

## Достаточная проверка

- Сохранить полезный прежний failure repro и конечный набор contract controls.
- Провести producer→reporter→validator по изменённому пути без запуска продукта.
- Добавить комментарии/пустые строки и перенести checkout в новый временный каталог:
  результат тот же, workflow repin не нужен.
- Отвергнуть forged/unknown case, stage/schema, лишние/private поля, stale/foreign
  контекст и имитацию machine error обычным текстом. Реальный FAIL не становится PASS.
- Проверить динамическую mutation/restoration/integrity и cleanup/original exit
  на ограниченных synthetic process/filesystem controls, не на БД или чужих ресурсах.
- Адресные TS/Python проверки и синтаксис workflow, затем независимое privacy review.

Контейнеры/Auth/БД/browser/full E2E/coverage campaign/Gradle для этого пакета не нужны.
Owner-local group/Nginx NOT_RUN не запускаются; старые успешные продуктовые evidence
переиспользуются. CI/Coverage/Security/OpenAPI PAUSED/manual; dispatch0. Без main,
forcepush, merge, deploy, production, нового PR/Goal/среды.

Статус: source package завершён; общий producer → reporter → consumer контроль и
независимый privacy/security review PASS. [Итог, команды, evidence и ограничения](../../validation/web-vue-pr3/checkpoints/diagnostic-harness-20261025/README.md).
Фактический service runtime collector в CI/Ubuntu этим offline пакетом не проверен.
CI PAUSED, owner-local group/Nginx NOT_RUN; продуктовые 117/116 и визуальный остаток
не меняются. Старый checkpoint2b сохранён, исторические receipts не переписывались.
