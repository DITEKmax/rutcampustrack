# Безопасная остановка — 2026-09-13

Прямое решение владельца: остановить выполнение, дать только уже начатым проверкам закончиться, сохранить состояние, новые этапы не начинать. Это отменяет текущие GO до нового решения продолжать. Terra запрещена.

## Подтверждённые результаты

- Core/profile остаётся принят на E `ffde92fdd1a7791ec3fb375102dc878d2fb2adc9`: live14 PASS, профиль 13/13, online/offline Сегодня/Задания, повторное выполнение и SQL. Не повторять без изменённого кода или нового дефекта.
- E Requests: exact83 backend overlay и exact6 Nginx/Compose импортированы; typed transport, controller/composable, owner navigation, forms/drafts/idempotency/download wiring написаны. Генератор/check, lint, три typecheck прошли; focused transport/controller/navigation 12/12 и Vue 10/10. Источник: E `.agent/requests-e-integration/stop-handoff.md`. Интеграционное independent review, окончательный source inventory и сборка ещё OPEN. Начинать их при остановке запрещено.
- Main Requests runtime: `requests-runtime/runner.ps1` и `probe.mjs` сохранены; Node syntax/self-test и PowerShell AST exit0. Исправлены ранние замечания root: лишний download, HTTP readiness TLS-порта, exit, owned-path cleanup, deadlines. Independent review, ValidateOnly и настоящий runtime NOT_RUN. Подробности `requests-runtime/STOP_SUMMARY.md`.
- Решение владельца сохранено: `statistics-transfer-owner-decision-2026-09-13.md`. После перевода прошлое место скрывается, собственные прошлые показатели остаются. Root принимает `past && everTransferred`; текущий семестр этим решением не меняется.

## Новые независимые части до STOP

- B task `01a07dbf-87de-7542-89ba-1b4952fdeb48`: выдан только source GO для Academic ResolveStudentProjectionScope, истории групп, rank visibility и focused tests в отдельном worktree от E ffde92fd. Gradle/Docker HOLD. STOP отправлен; финальный checkpoint/release уточняется ниже.
- D task `01a07dbf-97b8-70c1-a704-e12ba5104663`: выдан только source GO для Academic map read repository/service и трёх существующих RPC manifest/plan/asset в отдельном worktree от E ffde92fd. Schema/proto/BFF/UI/demand/publish вне scope. STOP отправлен; финальный checkpoint/release уточняется ниже.
- Эти назначения явно расширили прежние B/D границы только для указанных изолированных source scopes. Они не разрешают продолжение после этого STOP.

## Архитектурное решение B2 для продолжения

Read-only B recheck подтвердил root: для пяти B2 reads не нужны предварительная Mongo migration/backfill и переписывание всех attendance writers. V17 делает physical lesson identity/generation неизменяемыми. Schedule возвращает current physical IDs + occurrence/generation/revision; Attendance batch читает только эти IDs и авторизованные user IDs, отображает marks в pure calculator, затем повторно проверяет current pointers. Изменение → один bounded retry, повторное изменение/dependency failure → typed503. Старые поколения не включаются.

Реальный общий backend gap: существующий Schedule LessonService restore меняет строку in-place; V17 current occurrence/generation application writer ещё не завершён. Это отдельная обязательная работа по общему жизненному циклу занятий, а не причина переписывать все роли целиком. Нужны immutable restore generation, transfer/current pointer, cancellation/history и Homework bindings.

## Осталось по студенту

1. Requests final source inventory/affected review/build, затем настоящий Nginx I1/I2 и пользовательские сценарии отправки/скачивания/отмены.
2. Academic historical scope; Schedule authoritative lifecycle/current occurrences; пять B2 reads и интеграция принятых computation46/UI16. Старые принятые tests не повторять без изменения.
3. Map Academic/BFF read/asset/demand/publication, интеграция map9/shell/offline. Хранение PostgreSQL BYTEA принято; реальные корпуса/этажи/планы DATA_OPEN.
4. Homework publication/assignment/binding/transfer; учебный lifecycle выше.
5. Все39 финальных состояний и negative/offline/authz acceptance, итоговое review и контролируемая интеграция в исходный checkout. Light theme и настоящий Telegram-host QA отложены владельцем.

## Оценка и причина длительности

Около70% студента готово / около30% осталось — приблизительная оценка объёма, не процент тестов и не готовность всех ролей. Новые source-only части не повышают процент до их приёмки. Общий продукт по всем ролям в этой сессии не переоценивался; распространять70% на него нельзя. Старые readiness документы датированы06.09 и не отражают текущую delivery.

Время расходуется на интеграцию UI/API/данных, реальные auth/offline сценарии, общий backend и собственный runtime harness. Ранние дефекты harness и последовательные handoffs тоже добавили накладные расходы. E не реализовывал legacy Headman/Teacher/Admin features; общий Attendance/Schedule/Auth/BFF используется несколькими ролями.

При следующем продолжении разумно завершать общие backend операции вместе с минимальными сценариями преподавателя/старосты/админа, которые производят данные студенту, затем расширять интерфейсы ролей. Полная одновременная реализация всех ролей сейчас не начата и не следует из вопроса владельца.

## Ресурсы и сохранение

Main runtime leaf RELEASED; runtime не запускался. Read-only Docker ps при остановке показал0 running containers; Requests-owned networks0; process inventory Java0. Остальные Node процессы не уничтожались: в системе есть Codex/tooling и чужие процессы, наличие Node не является доказательством принадлежности задаче. E handoff подтверждает собственные tests/runtime processes0 и heavy RELEASE. Финальные B/D release и checkpoint будут добавлены после сохранения.

Исходный IntelliJ checkout не перезаписывался, push/deploy не выполнялись. Main checkpoint ниже должен включить всю новую `.agent` и сохранить эту точку продолжения.

## Финальное состояние STOP

Все E/B/D задачи `idle`, main runtime leaf завершён. Новых checks/review/build/runtime после STOP не запускалось; выполнены только сохранение и read-only сверка состояния.

- E checkpoint `b8220ac92125a8afa37598b270aa4fab7aa1f470`: весь Requests source и `.agent`, дерево чистое. Это preservation checkpoint, не independent-review/runtime PASS.
- D candidate `.agent/worktrees/campus-map-read-backend`, checkpoint `86263384ae81353af087e61e9158af61f15e0999`: контракт и четыре новых typed model/exception/enum файла; handlers/repository/service/identity/tests ещё не написаны. `git diff --check` прошёл до STOP. Дерево чистое.
- B candidate `.agent/worktrees/b2-academic-scope`, checkpoint `bb1bd4e72bae1b1393cae712c9fefec9c788684c`: read-only подготовка и STOP handoff; продуктовые изменения не начинались. Дерево чистое.
- Main runtime evidence `requests-runtime/STOP_SUMMARY.md` и owner decision сохранены. Не начинать с нового harness: продолжить independent review уже написанных runner/probe, затем необходимые source/runtime gates только после нового GO.
- E/B/D подтверждают отсутствие оставшихся собственных проверок/процессов и освобождение writer/heavy. Root Docker inventory: running containers0, Requests-owned networks0, Java processes0. Docker Desktop/Codex и неизвестные Node tooling процессы намеренно не завершались.

Оставшаяся продуктовая работа перечислена выше; завершены только сохранение и остановка. При следующем GO использовать checkpoints, не переигрывать принятые live14/calculator/map/source evidence.
