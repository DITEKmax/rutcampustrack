# Независимое bounded review group/homework a6b16f3bd

Результат: **corrections required: пять подтверждённых P2**.

Scope: только frozen `a6b16f3bdbe244c41c7bea9812be27a22c58712f` (восемь файлов): GroupPage, HomeworkPage и связанные journal/context/routes/control changes. Parentf0 прежние findings проверены отдельно. Source читался через gitshow, moving worktree не использовался. Read-only, без продуктовых правок/commits/push/CI/Gradle/Docker/install/heavy/детей. Запрошенная модельgpt-6.1-sol/high; runtime telemetry недоступна.

## Findings одним пакетом

1. **P2 — stale helper draft может восстановить право, уже отозванное другим клиентом.** `frontends/web-vue/src/features/headman/GroupPage.vue:194–200` (local snapshot/context:151–166). PermissionConfirmationFlow проверяет только локальные revision/helperIdentity, затем UI передаёт старый full permission set в updatePermissions. Existing HeadmanGroupApi и UpdateAssistantPermissionsRequest содержат лишь новый permissions array; AssistantService.updatePermissions под server lock заменяет текущий set без expected version/permissions. Repro: UI читает [MANAGE_HOMEWORK] и готовится добавить VIEW_STATS; другой клиент заменяет права на [MARK_ATTENDANCE], отзывая MANAGE_HOMEWORK; старый UI подтверждает [MANAGE_HOMEWORK,VIEW_STATS] и незаметно восстанавливает отозванное право. Local confirmation не является server CAS. Для заявленной защиты concurrent grant/revoke нужен server-enforced precondition/contract; один дополнительный GET до PATCH не закрывает окно атомарно. Это явный dependency gap существующего API, который нельзя считать проверенным CAS только по UI flow.

2. **P2 — budget recovery теряет собственную command identity по чужому aggregate increase.** `frontends/web-vue/src/features/headman/GroupPage.vue:254–262`. limit>=beforeLimit+increase не доказывает применение конкретного requestKey. Repro: limit10, своё +5 не применилось/ответ неизвестен; другой command увеличил limit до15 → recovery читает15 и удаляет исходный budgetIntent/requestKey без подтверждения своей команды. Для additive command это неверный итог; позднее выполнение либо следующая попытка становятся непрослеживаемыми для пользователя. Existing StudentRequestService.increaseHeadmanBudget имеет exact actor/key receipt (budgetReplay перед increment). Использовать этот контракт для исходной frozen command либо сохранять явную неопределённость; aggregate limit не является receipt.

3. **P2 — выбор новой пары того же дня/типа вообще не попадает в edit PUT.** `frontends/web-vue/src/features/headman/HomeworkPage.vue:232–246`. changedPlacement сравнивает только bound/date/type и не замечает выбранный nextLesson/lessonNumber/occurrence. Repro: existing LESSОN dateD/LECTURE/пара1; нажать «Выбрать ближайшую пару», получить подтверждённую пару2 того жеD/LECTURE → nextLesson отображает2, но changedPlacement=false, PUT не содержит bindingMode/lessonNumber/selectedOccurrenceId. Backend закономерно сохраняет прежнюю пару1, UI не показывает ошибку. Placement intent должен учитывать факт и identity новой выбранной пары; existing update contract поддерживает lessonNumber + selectedOccurrenceId/Revision.

4. **P2 — unknown edit readback ложно подтверждает другую placement.** `frontends/web-vue/src/features/headman/HomeworkPage.vue:316–332`. Проверяются title/description/link/date/binding, но не lessonType/lessonNumber, хотя эти поля входят в HeadmanHomeworkUpdateInput и доступны в authoritative HeadmanManagedHomework. Repro: отправлялся LAB/пара2, результат неизвестен; сервер вернул новую revision с теми же текстом/date/binding, но LECTURE/пара1 (например, concurrent text edit). Код получает same=true, очищает pending, закрывает editor и не показывает ошибку. Нужен полный comparison подтверждаемого intent либо явное unresolved/conflict при невозможности доказать occurrence snapshot; новая revision сама по себе не подтверждает данную команду.

5. **P2 — локально невалидный title навсегда блокирует persisted homework intent.** `frontends/web-vue/src/features/headman/HomeworkPage.vue:236–247,264,353–365`. UI сохраняет pending в sessionStorage до local typed-client validation; catch очищает только определённые HTTP HeadmanHomeworkApiError. HTML required допускает строку из пробелов, а existing HeadmanHomeworkApi.updateHomework/createHomework синхронно отклоняет trim-empty title до fetch. Repro: открыть edit, ввести три пробела в title, сохранить → запросов0, error «title обязателен», но pending=edit и persisted intent остаётся. Поля/Cancel/новые изменения заблокированы; «Проверить результат» снова приходит к той же local validation, reload восстанавливает pending. Валидировать перед persistence либо отличать доказанный local preflight failure от unknown transmitted mutation и освобождать draft для исправления.

## Независимые causal checks

Однократный Node/V8+установленный TypeScript harness исполнил exact frozen SFC script setup с минимальными Vue/API stubs; для local validation дополнительно использован настоящий HeadmanHomeworkApi. Он не менял продуктовые файлы и не выполнял real backend/browser requests. Полученные результаты:

- Stale permissions: sent/actual=[MANAGE_HOMEWORK,VIEW_STATS], error=null после моделируемого другого current set=[MARK_ATTENDANCE]. Last-write-wins соответствует прочитанным реальным DTO/service source, а не предположению о server CAS.
- Budget: ownApplied=false, posts1, limit15, pending=null после aggregate readback чужой команды.
- Same-day new lesson: selectedLessonNumber2, sentPlacement={}, error=null.
- Unknown rebind: requested LAB/2, actual LECTURE/1, pending=null/error=null.
- Whitespace title: actual typed client fetches0, pending=edit, persisted1, editor=true, error «title обязателен».

`git diff --check a6b16f3bd^..a6b16f3bd` — PASS. Авторские typed/build/browser read/overflow evidence не дублировались и этим source harness не объявляются runtime acceptance всех mutations.

## Рассмотренные границы без дополнительных findings

- Group management ограничен HEADMAN и writable context; rights controls исключают себя/current headman и inactive student. Credentials используют существующий useHeadmanCredentials с новым enabled/member context, sync invalidation при selection/tab/authority change, volatile refs и clear; password/link не сохраняются в storage/logs. Backend остаётся live authorization owner; active ADMIN target и generated-password CAS проверяются существующим API.
- Roster export переиспользует frozen requestId/job intent и исправленную terminal recovery. Assistant homework permissions берутся из общего current grants owner; этот bus/owner — отдельный review root, не гипотетический missing-registry blocker.
- Homework DATE/LESSON placement использует активный semester/date/teacher assignment helpers, create requestKey/accepted receipt сохраняются; recovery переиспользует identity, delete не повторяется слепо, archived record/history читаются существующими scoped endpoints. Указанные конкретные false ACK/preflight defects не устраняются самим наличием typed clients.
- Journal unknown mark в этом commit дополнительно удерживает uncertainty до явного принятия server record; type normalization не ослабляет row CAS/permission guard. StudentDetail изменения относятся к typing/formatting props.

Нужны scoped corrections и независимая recheck. Source gate нового group/homework пакета открыт; accepted role/action counts и реальные mutations этим отчётом не повышаются.
