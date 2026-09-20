# One-off time authority — source result, 2026-09-20

Status: bounded source stage complete; runtime N/A; no implementation authority. Fresh Luna max scout inspected12 content files and RELEASED. Its coverage was10 Schedule code files + accepted proposal + L5 source: it did **not** reach requested canonical API/kit documents. Lead therefore directly inspected the three explicitly named critical originals api-digest/backend-requests/backend-conflicts; total15 distinct relevant files, transparently separated in sources.json. No further scout/search expansion or archive-kit inspection claimed.

## Confirmed original requirement

`docs/architecture/reference-rutcampustrack-design/backend-requests.md:196`, SC-02, SHA40A13A9DDB8CC2E8A2AB8C7CEC2AEC9F0DDFE7F6DE10DFBC6E779423046330D3: «Справочник слотов времени: номера пар с временем начала и конца, единый для вуза, + число учебных дней». Source points to ч5 S-2/screens117/118; API marked missing. Это зафиксированная потребность первичного массива, не доказательство готовой реализации/данных и не найденная явная команда владельца о варианте persistence.

`api-digest.md:133–151` перечисляет Schedule/template/one-off endpoints, но в этом разделе нет time-slot directory API. `backend-conflicts.md:329–350` R25 задаёт явные дату/время/аудиторию для transfer; это не разрешение приравнять one-off create к transfer. Поздние R7/R25/HANDOFF выше старых противоречащих cancellation строк backend-requests: в этой проверке они не переоткрываются.

## Current implementation independently checked

- CreateOneOffLessonRequest:19–50 — groupId, subjectId, date, lessonNumber1..8, classroom; start/end отсутствуют.
- OneOffLessonService.createOneOffLesson:98–147 — active semester/date checks, template occupancy check, затем origin persistence/event date/number/room. Time resolver не вызывается. Occupied template slot запрещён, поэтому брать время из существующей активной пары того же слота нельзя считать готовым решением.
- OneOffLesson:29–69 — нет start/end/timezone columns.
- ScheduleItem:49–62 содержит LocalTime startTime/endTime для recurring template; ScheduleItemService копирует request times в template. Это per-template source, не доказательство общего справочника.
- ClockConfig:9–19 задаёт Europe/Moscow для Clock. Это источник timezone текущих temporal comparisons, не mapping номера пары в время.
- Остальные scoped API/domain/generator файлы из scout inventory не добавляют такого mapping. Ограниченный результат не доказывает отсутствие implementation во всех остальных файлах.

## Smallest next contract for root

SC-02 сужает следующий freeze: определить Schedule-owned canonical time-slot directory/resolver для one-off creation и consistent read API для grid117/118, затем сохранять resolved start/end в immutable physical snapshot. Нельзя объявлять произвольные explicit one-off times равноценной уже согласованной альтернативой только по наличию TransferLessonRequest.

В root engineering/source freeze остаются: реальные значения слотов и их provenance; существующий источник конфигурации/данных, если он есть вне проверенных файлов; effective-date/version semantics при изменении сетки; допустимость per-lesson time override; учебные дни и timezone validation. Versioned resolver/snapshot — инженерное предложение, не найденное owner decision. Пока точные времена не подтверждены, не подставлять типовые звонки и не backfill из текущего template.

Рекомендуемый следующий адресный источник, если root решит продолжить, — конкретный ч5 S-2/117/118 reference, на который ссылается SC-02. Это не предложение повторить B foundation/max8doc поиск. Вопрос владельцу на этом этапе не задавался.

## Verification / release

Exact paths/SHA/line navigation and scout-vs-lead scope recorded in sources.json. Lead самостоятельно открыл DTO/entity/service/ClockConfig/recurring fields и три критичных canonical документа. Проверка read-only; Gradle/Docker/browser/tests не запускались. Source baseline426a15b6b42e816deaa3ca5c50437e0964aaf85e; accepted frozen L5B proposal836D unchanged. Only own packet/result/source-map/checks written by lead; scout wrote nothing. RELEASE source lookup slot.
