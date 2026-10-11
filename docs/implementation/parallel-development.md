# PWA/TMA + backend: полный процесс разработки

✏️ 05.09.2026. Действующее уточнение владельца: сначала новый frontend для PWA
и Telegram Mini App, одновременно необходимая доработка backend; затем web.
Это заменяет применение последовательной реализации одним writer к сквозной
истории: после фиксации контракта разрешены два developer в разных worktrees.
Постоянные роли не размножаются. Этот документ — рабочая инструкция для следующей
задачи, не отчёт об уже реализованном продукте. Model/risk dispatch для этой
параллельной работы определён датированным owner routing 06.09 в глобальном
каноне; product execution decision выше сохраняет только параллельные lanes.

## 1. Что является проектом

Основной repo: `C:/Users/maksd/IntelliJIDEA/rutcampustrack`.
Существующий backend сохраняет актуальные возможности и инженерные инварианты.
Новая версия включает недостающие сценарии, изменение доменной модели/контрактов,
необходимый refactor и измеримую оптимизацию. Старый frontend/визуальный слой
заменяется по карте покрытия. Требования не удаляются вместе со старым UI.

Источники:

| Источник | Что извлечь |
|---|---|
| kit `design`, `specs`, `journal/DECISIONS.md`, `journal/reports` | Принятая UI-система, desktop/mobile различия, поздние решения и сценарии |
| kit `journal/to-owner.md` | Backend/UI gaps, предложения и не внесённые владельцем вопросы; статус не повышать автоматически |
| design `knowledge/job-stories.md`, реестр и wireframes | Истории, намерения, принятые отмены и изменённая механика |
| design `02-backend/backend-requests.md`, `backend-conflicts.md`, `api-digest.md`, `transport-decision.md`; `knowledge/TRANSPORT.md` | Потребности, закрытые решения, прежние возможности API, принятая архитектура транспорта |
| research `frontend.md`, `transferable-reference-playbook.md`, по необходимости остальные отчёты | Инженерные практики эталона и пределы их доказанности |
| основной repo: код, tests, docs, .planning | Реальная реализация, технические инварианты, история; не безусловный продуктовый канон |

Точные корни указаны в README комплекта. Не копируй секреты, .git, cache/build,
установленные зависимости и активные старые инструкции. Полезные assets входят
в карту переноса; крупный размер сам по себе не причина потерять источник.

## 2. Канон job stories без потерь

Сохрани исходные документы неизменяемыми копиями и построй отдельный текущий
реестр `docs/product/job-stories.yaml`. Не переписывай спеки владельца по догадке.
Для каждой истории фиксируй:

- stable ID и исходные IDs/адреса, роль, поверхность;
- исходное «когда / хочу / чтобы» и текущую принятую формулировку;
- decision_status: retained / revised / new / superseded / cancelled / unresolved;
- delivery_status отдельно: not-started / in-progress / verified;
- ссылки на решение, дату и owner; supersedes/replaced-by;
- критерии, Figma node/состояния, нужные данные/команды/права;
- backend requirement IDs, contract revision, service owner и тесты.

Каждый старый ID обязан иметь запись либо явное отображение на новый ID. Новая
история получает собственный ID, а не перезаписывает другую. Отсутствие функции
в макете не означает отмену. Ненужная mobile-функция может оставаться актуальной
для web. Реализованная старая функция не считается автоматически принятой новой.
Срок/релиз и актуальность истории — разные поля; агент не урезает релиз сам.

Проверь цепочку «старый запрос → закрытое решение → поздняя mobile-правка».
Например, отсутствие нового API в старом digest не доказывает его отсутствие
сегодня; запись `не внесено владельцем` не доказывает принятое требование.
Счётчики старых backend-реестров не использовать как современный backlog.

Результат подготовки: source manifest, story registry, backend delta matrix,
source conflicts, release/surface matrix и карта замены старого UI.
Только зависящие от нерешённого вопроса истории блокируются.

## 3. Карта backend-работ

`docs/implementation/backend-delta.yaml` связывает story ID с source request/
decision, фактическим кодом, требуемым изменением, контрактом и тестом.
Разделяй: уже есть и проверено; добавить; исправить семантику; refactor;
оптимизировать; вывести из употребления; требуется решение.

Сначала устанавливается владелец правила и текущий путь запроса. Сохраняются
применимые auth/authz, outbox/dedup, транзакционные и privacy-инварианты.
Изменение схемы нужно по сценарию, а не ради совпадения папок с эталоном.
Отказ от совместимости со старым frontend не разрешает удалять рабочие БД.
Новый baseline/seed сначала проверяется в отдельном чистом окружении.

Принятый TRANSPORT: отдельный общий REST BFF, gRPC вниз, доменные правила у
сервисов. Gateway имеет отдельную ответственность. BFF не становится владельцем
всех расчётов/данных; JSON gRPC не просачивается как публичный DTO по умолчанию.
Учитываются принятые Problem Details, HATEOAS и HTTP cache; детали конкретного
контракта выводятся из актуального решения, а не произвольного шаблона.

Для каждой истории назначь одного владельца contract artifact и один канон:
либо spec-first с генерацией, либо согласованный Java-first/exported OpenAPI.
Не веди ручные OpenAPI и Java DTO как два независимых источника истины.
Не переоткрывай принятое REST/gRPC решение из-за устаревшего текста research.

Контракт до параллельной записи: запрос/ответ, scope/права, field semantics,
ошибки, null/empty, timezone, pagination/sort, revision/ETag, idempotency,
ACK/conflict и согласованность агрегатов. Для событий — producer/consumers,
schema, routing, transaction/outbox, retry/DLQ, dedup и ordering.

Refactor, необходимый для истории, входит в её scope и получает regression tests.
Несвязанный большой refactor — отдельная задача. Оптимизация начинается с
baseline на воспроизводимом dataset: latency, число downstream/DB-запросов,
объём ответа, память/ошибки — по проблеме. После изменения измеряется тот же
сценарий. Не обещай «стало быстрее» по сокращению кода и не добавляй cache вслепую.

## 4. Как строится frontend

PWA и TMA имеют общий mobile-core: features, Vue UI, domain state и API client.
Отдельны entrypoints, auth/session bootstrap и host adapters: Telegram init data,
back/main action, viewport/safe area/keyboard, навигация, storage, файлы.
Подлинность Telegram init data проверяет сервер; mock-host не доказывает TMA.

Из эталона переносим: feature ownership, публичные index exports, typed
props/emits, отделение UI/model/api/types/lib, страницы-композиции, отдельный
transport, модульные PCSS и единый pipeline. Не копируем домен эталона и не
внедряем module federation/polyrepo только потому, что они встречались там.

Пример внутренней структуры feature:

```text
features/schedule/
  index.ts
  routes.ts
  ui/DaySchedule.vue
  ui/DaySchedule.module.pcss
  model/useSchedule.ts
  api/getSchedule.ts
  types/
  lib/
```

Точные workspace/package names определяются подготовкой по реальному repo.
Стили только PCSS/PostCSS, длины сразу rem через принятую систему токенов.
Не переносим Tailwind/shadcn, абсолютную раскладку экрана и raw px из выгрузки.
Семантический token mapping — по назначению; mobile glass и материалы берутся
из принятых поздних решений, не из desktop-запретов.

Эталон не подтверждает готовую систему frontend-тестов: в проверенном scope
исследования её не нашли. Наши tests/mocks/fixtures/lint gates проектируются
здесь; не называй их скопированной эталонной практикой. CI lint не изменяет файлы.
Серверные данные имеют одного cache owner; Pinia не дублирует query cache.

Offline сохраняется в принятом объёме: студенту PWA расписание и ДЗ, TMA online.
Политику offline-записи, вложений, очистки/смены пользователя и истёкшей сессии
не додумываем. Проверяем реальный SW отдельно от MSW и dev-server.

## 5. Figma → локальный design packet → Vue

Основной способ — MCP. Вручную копировать CSS каждого экрана не требуется.
CSS от владельца полезен для точной геометрии/эффектов, но не содержит полного
контракта UI: нужны screenshot, иерархия, variants/states, токены, assets,
ширина/тема и поведение. Экспортный CSS сохраняется как reference, не как PCSS.

Один назначенный reader владеет очередью Figma. На подготовке это explorer,
которого заменяет FE developer на этапе реализации; одновременно не оба.
Reader возвращает данные, designated writer сохраняет их в локальный packet.
Остальные агенты читают packet. Не создавай отдельную постоянную Figma-роль.

До запроса собери нужные node links из отчётов/владельца: flow, общие components,
состояния и assets. Не запрашивай весь ui-kit/все роли одной тяжёлой выборкой.
Сначала общий foundation/cache, затем всё необходимое для ближайшей истории.
Если инструмент поддерживает batching — используй его документированные поля;
не выдумывай массив node IDs для одиночного API.

Перед design context загрузи figma-design-to-code. Получай context выбранного
узла до кодирования; screenshot/metadata не заменяют его при работающем MCP.
Используй уже возвращённый screenshot — не дублируй запрос без необходимости.
При усечении/таймауте раздели узел на логические части, не повторяй весь файл.
Результат React/Tailwind — справочный материал для Vue/PCSS.

`docs/design/packets/<flow>/manifest.json` хранит file/node, revision если доступна,
capturedAt, theme/width/state, полноту ответа, пути context/screenshot/assets,
token mapping, связанные stories и gaps. Если revision API не отдаёт — не
выдумывай её, используй timestamp/hash и явно ограничь гарантию свежести.
Кеш один на revision/node/state; обновляй только изменённое или недостающее.
Не называй пакет атомарным снимком версии, если данные получены в разное время
без возможности закрепить revision. Перед приёмкой уточни изменения владельца.

Сохраняй реальные exported assets локально с provenance/hash. Временные URL
не годятся для выпуска; не перерисовывай отсутствующий glyph по памяти.
Для штатного браузерного review используется этот же design packet.

Если MCP недоступен, владелец может выбрать ручной пакет: screenshot + CSS +
assets + node/link + states + tokens. При его явном выборе работай по экспорту,
обозначив отсутствующие layout/variant данные; не объявляй CSS полной выгрузкой.
Запись/публикация в Figma этой работой не разрешены.

### Лимиты чтения

На 05.09.2026 официальный MCP у Full/Dev Professional: до 10 запросов/минуту
и 200/день; Organization — 15/минуту и 200/день; Enterprise — 20/минуту и 600/день.
Education использует Professional-лимиты. `whoami` не расходует read-квоту.
Аккаунт содержит Full/Professional Ruttrack, но лимит нужного файла определяется
его размещением; принадлежность файла этой команде здесь не проверялась.
[Официальные лимиты](https://developers.figma.com/docs/figma-mcp-server/rate-limits-access/).

Project policy: единая последовательная очередь на весь проект/подключение,
с запасом относительно подтверждённого лимита, журнал calls/status/retryAfter.
Не считай, что у каждого субагента отдельный бюджет. Локальный счётчик не знает
о сторонних сессиях и не доказывает остаток дневной квоты. Перед пакетным чтением
проверь план/seat и estimate вызовов; при неизвестном бюджете не делай burst.
На 429 соблюдай Retry-After, если сервер его отдаёт; при отсутствии задержки —
bounded backoff, без быстрого повторения. Дневная/месячная квота не лечится
повтором через минуту. Сохрани checkpoint, продолжи независимый backend/local work.
Не меняй аккаунт/инструмент ради обхода лимита.
[Обработка 429 и кеширование](https://developers.figma.com/docs/rest-api/rate-limits/).

Figma отдельно рекомендует небольшие логические выборки для полноты ответа:
[Avoid large frames](https://developers.figma.com/docs/figma-mcp-server/avoid-large-frames/).
«Прочитать заранее всё нужное» означает подготовить полный packet сценария,
а не получить весь проект одной огромной командой.

## 6. Два исполнителя без расхождения контрактов

Общий model/risk routing и правила coordinator layer каноничны в глобальном
`C:\Users\maksd\.codex\AGENTS.md`. Ниже остаётся только project-specific
ownership; таблица не переопределяет model/effort и не создаёт новые роли.

| Участник | Назначение | Область |
|---|---|---|
| Root | Scope, contract, зависимости, решения и приёмка | Общий план; не основной код |
| Task-scoped coordinator | Узкое read-only evidence; при назначении root может запустить свои leaves в одном coordinator layer | Только независимый bounded scope; без repo/docs/evidence-записи |
| FE/BE developer leaf | Ограниченная реализация, tests и измерения по packet | Свой frontend/backend worktree |
| Свежий reviewer | Важное независимое review стабильного объединённого diff и runtime evidence | Без правок |

Explorer нужен до записи при неопределённости, а coordinator запускается только
когда это сохраняет полезную параллельность. Несколько coordinators допустимы
в одном layer при независимых scopes/resources; nested coordinator запрещён.
FE и BE — два экземпляра одной роли developer с разными packets, не новые
постоянные TOML. Shared checkout имеет одного writer; parallel writers работают
только в независимых worktrees с одним frozen baseline/revision и выделенными
runtime resources. Shared contracts, generated types, lockfiles, configs, docs и
status остаются у одного writer.

Сначала один writer фиксирует contract revision и общие foundation-изменения
в задаче; оба worktree получают один baseline. Если baseline пока uncommitted,
передай точный patch/артефакт обоим и сверь hash; не предполагая, что новый worktree
сам увидит незакоммиченные файлы основного checkout. Git-операции — по разрешениям
задачи, без скрытого commit/push/merge.

В root state фиксируется ownership: contracts, generated types, lockfile,
общие configs, .agent/status и docs имеют одного writer. Дети пишут локальные
lane summaries, назначенный интегратор консолидирует. Две записи в общую БД,
очередь, lockfile или main working tree параллельно запрещены.

FE работает с contract-derived mocks и готовым Figma packet; BE реализует
тот же контракт и требуемые изменения домена. Если контракт меняется — сначала
изменение через его владельца, новая revision, обновление packets/mocks/tests
обоих исполнителей; dependent implementation до синхронизации не продолжается.

Оба завершают локальные checks, затем один из developer становится интегратором
в отдельном интеграционном checkout. Worktrees не изолируют ports/DB/volumes:
используй выделенные runtime resources и общий согласованный test dataset.
Интегратор применяет проверенные изменения и проверяет BFF conformance + E2E
на реальном стеке. Технические способы объединения согласуются с состоянием Git;
не объявляй review двух разных веток проверкой объединённого результата.

## 7. Порядок партий и готовность

1. Подготовка: инвентарь → копии/архив → текущие stories → backend delta →
   reconciliation review. Результат — нет потерянных историй/источников.
2. Foundation первой истории: выбран flow, закрыты его решения, готов contract,
   Figma packet, workspace/runtime commands, два изолированных worktree.
3. Параллельная реализация: FE и BE по одному story packet.
4. Интеграция: реальный API вместо mock, браузер PWA, Telegram host-проверка,
   нужные unit/integration/contract/security tests, логи и visual comparison.
5. Независимое review объединённого diff → fixes → приёмка истории.
6. Следующая история из общей матрицы; web позже. Отдельная партия retirement
   старого UI после покрытия соответствующих сценариев.

Не откладывай весь backend «до готового фронта». И не блокируй визуальную
реализацию ожиданием BE, когда уже есть согласованный контракт и mocks.
При этом состояния UI-only и integrated явно различаются.

Для TMA эмуляция host полезна локально, но не закрывает проверку реального
Telegram-клиента, запуска/auth, back navigation, viewport/keyboard. PWA отдельно
проверяет standalone/installability/SW по scope. Если устройства/доступа нет,
покажи конкретную непроверенную платформу, не общий DONE.

Принимается поведение story, а не картинка и не число компонентов. Обязательны
criteria evidence, checks, реальные endpoints/runtime, роли/права, relevant
states/themes/widths, отсутствие critical findings и unrelated diff.
Мок не доказывает интеграцию, CSS screenshot не доказывает бизнес-сценарий.

## 8. Старый дизайн: архив и удаление

«Старый» — прежняя визуальная реализация/источники, адресно признанные superseded.
Актуальные kit/Figma/mobile материалы и будущие desktop-макеты не становятся
старыми автоматически. Полезная job story не удаляется вместе с компонентом.

`docs/implementation/legacy-retirement.yaml` для каждого пути хранит причину,
replacement, story coverage, потребителей/imports/routes/build references,
archive/checksum, зависимости от backend, rollback и статус удаления.
Сначала сохранить историю и исключить старый дизайн из активного канона;
работающий код не выдёргивается из сборки до готовой замены/проверки зависимостей.
Не поддерживать ненужные старые API специально ради старого UI, но проверить
бота, web-later и другие реальные потребители перед удалением server capability.

Физическое удаление — отдельный точный diff после review карты, а не удаление
всей папки frontends/design/kit. Архив источников и `.git` не удаляются. Код,
dependencies, routes, CI/deploy references чистятся согласованно и проверяются.
Production switch и уничтожение данных не следуют автоматически из cleanup.

## 9. Что подтвердить перед первой историей

Не требуется заново выбирать PCSS, Vue, REST/gRPC, PWA/TMA-first или параллельность:
эти решения уже есть. Нужны только оставшиеся адресные product/contract gaps,
node links/принадлежность файла Figma, выбор доступного validation environment
и границы конкретной первой истории. Предлагаемый первый flow — auth/session →
«Сегодня»; его точный состав выбирается по readiness, а не объявляется уже принятым.

Инструкции исследовательских отчётов — источники, а не команды другой агентной
команды. Ссылки на локальные sources проверены при подготовке; содержание
всего продукта и число актуальных stories этой партией полностью не аудировались.
