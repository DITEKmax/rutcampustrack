# Ordinary diagnostic harness — 25 октября 2026

Пакет реализует [прямое задание владельца](https://github.com/DITEKmax/rutcampustrack/pull/3#issuecomment-6396083761).
Исходный checkpoint `2b297a50656617cb224f4d58aa605cbe92ab6992` сохранён в истории
и в отдельном local ref. Это проверка изменённой диагностической цепочки; продуктовые
счётчики, галерея и owner-local gates от неё не меняются.

## Что изменилось

Один JSON catalog содержит 18 стабильных case ID и конечные допустимые события.
Публичное событие `rct-diagnostic-event.v1` имеет ровно `schema`, `caseId`, `stageId`,
`eventCode`, `httpStatus`; максимум восемь событий на строку. Reporter фиксирует
регистрацию до fixtures, связывает actual raw Error с текущим framework failure
и публикует только закрытые события. Частный свежий per-run HMAC envelope связывает
case/test/project/retry/run; proof, key, raw exception и stack не выходят в report.
Plain text, чужой Error, replay и Error с идентичными message/stack не получают
происхождение HTTP. Граница доверия — проверенный checkout и per-run integrity,
не произвольный вредоносный код внутри самого checkout.

Общая Python реализация импортируется offline controls и вызывается CI/Ubuntu.
Шесть CI consumers больше не извлекают определения из YAML. Удалены ручные
source SHA maps и fixed file/line gates; basename больше не определяет async case.
File/line могут оставаться ограниченными необязательными metadata. CI и local
сохраняют собственные строгие context/owner/nonce/Compose/CA/source admission;
local не имитирует GitHub-hosted. Динамические manifests, before/after integrity,
source restoration, build/runtime/download hashes, Mongo/gRPC/ambiguity rules,
byte/time limits, original failure и cleanup сохранены.

## Проверено в облаке

| Результат | Evidence и границы |
|---|---|
| Producer → reporter → shared consumer PASS | [Единый запуск](integrated-control.json) на интегрированной revision: 7 строк, 20 конечных отрицательных вариантов; Python 19 controls PASS за 1.117s. Внутренний Playwright намеренно завершился EXIT1; внешний launcher подтвердил этот отказ и завершился успешно |
| Python semantic/privacy/ownership controls PASS | CLI/import parity, relocation и comment shifts, fresh dynamic manifest, exact restoration, closed schema/case/stage, extra/private/bool/duplicate fields; project/service/nonce ownership до/после; timeout/SIGTERM process-group cleanup и сохранение исходного EXIT17. Runtime seams синтетические; сервисы не запускались |
| Текущий client cohort PASS | [Исправление](cohort-correction.json): десять настоящих зарегистрированных cases допускаются; повтор, чужой ASYNC, одиннадцатая строка и private поля отвергаются. Восемь событий на строку и one-row local first-failure scope сохранены |
| Actual raw Error association PASS | [Receipt](producer-correction.json): native body и worker fixture HTTP503, copied Error/replay/plain text FRAMEWORK0. Helper/reporter/privacy strict tsc PASS |
| Существующая artifact privacy проверка PASS | [Receipt](legacy-privacy.json): expected failure/success, marker absence, восемь guard controls, interruption/startup/missing guard. Только два устаревших synthetic adapters переведены на регистрацию cases; browser не стартовал |
| Независимый review PASS | [Связанный audit](LINKED-AUDIT.md), [сверка интеграции](INTEGRATION-e0dcb3496.md), исходные замечания [TS](TS-REVIEW-c1f2d943.md)/[Python](PY-REVIEW-f9b8be6c.md), закрывающие [TS recheck](TS-RECHECK-50f723e6.md)/[Python recheck](PY-RECHECK-557fc782.md), [финальная обвязка](REVIEW-SHARED-CONTROL.md) |

Начальный TS review обнаружил принятие другого Error с скопированными message/stack;
исправлено привязкой к actual raw object до сериализации. Python review обнаружил
унаследованный cap 8 строк при выборе CI 10 cases; исправлен конечным cohort 10.
Проверки повторялись по этим конкретным дефектам. После нового CLI/CI wiring выполнен
один связанный запуск `--with-consumer`; прежние неизменённые runtime evidence не повторялись.

## Сохранённые ограничения и неуспешные исходные результаты

- [Legacy baseline](legacy-baseline.json) уже RED на `2b297a`: signature допускается,
  source bindings нет. Comment-only overlay также RED; это не доказательство нового
  отказа именно от комментария. Временная копия восстановлена byte-for-byte,
  repository sources при repro не изменялись. Новая relocation/comment проверка PASS.
- Более широкий touched-spec strict tsc остаётся EXIT2 с одним прежним TS2322
  в semester lifecycle transport union. [Baseline](baseline-2b297a5-tsc.txt) и
  [после](producer-current-tsc.txt) совпадают по ошибке; строка сдвинулась из-за import.
  Полный TypeScript PASS не заявлен; проверка helper/reporter/privacy отдельно PASS.
- Raw synthetic producer report целиком отвергается как CI report (пустой project,
  intentional stderr). Consumer controls используют явно названные unit projections,
  сохраняя genuine события и legacy hints. Это не поддельный runtime CI PASS.
- Первый privacy extension упал на устаревших synthetic adapters, без утечки.
  Его summary/diagnosis сохранены private; сборщик debug evidence при retry перезаписал
  первые raw child copies. Недоступные первые raw copies не заявлены сохранёнными;
  финальные PASS artifacts находятся в отдельном private attempt directory.
  Исторические принятые receipts не менялись.
- Временный cardinality repro сначала имел слишком короткий путь и получил IndexError;
  повторён только repro с правильным disposable layout, не runtime suite.
- Docker/Auth/API/БД/browser/Gradle/full E2E/owner-local group/Nginx не запускались.
  Реальная Ubuntu/CI service integration нового diagnostic collector остаётся
  неподтверждённой этим offline пакетом. CI/Coverage/Security/OpenAPI PAUSED/manual,
  dispatch 0. Group owner-local NOT_RUN 0/1 сохранён.

## Переносимые команды

Из корня checkout с уже установленными lockfile dependencies `tests/e2e`
(Node 24, Python 3.11+, используемый Playwright 1.59.1):

```sh
node tests/e2e/fixtures/diagnostic-events.control.mjs --with-consumer
python3 -B tests/e2e/fixtures/http500-diagnostic-contract.py
node tests/e2e/check-artifact-privacy.mjs
```

Первая команда — общий связанный контроль Ubuntu и manual CI, без browser/API/globalSetup.
Последние две — адресные самостоятельные режимы, их не требуется повторять после PASS
без изменённой зависимости. Обычный local launcher `scripts/local_e2e_async.py`
сам вызывает общий diagnostic module после разрешённого сбоя своего lane, до logs/cleanup;
он требует существующий отдельный синтетический owner context, не запускается этими
offline командами. Shared CLI `http500_diagnostic.py` принимает явные `--context`,
`--stage`, `--root`, `--run-dir`; смена root проверяется, не обходит admission.
Владелец сам выполняет локальные команды; его прежний stand не обновлялся.
