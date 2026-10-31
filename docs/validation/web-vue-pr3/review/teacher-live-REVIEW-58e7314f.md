# Независимый review Teacher WEB live focus — 58e7314f

**CHANGES REQUIRED — один P2.** Исправление снимает исходный indefinite defer и сохраняет Stats draft/export в проверенном основном сценарии. Но background refresh уже догруженного Journal обрезает его до первой страницы и удаляет сфокусированную ячейку при неизменном разрешённом scope.

Reviewed commit `58e7314f64f86cd366042027438026f697cc040c`, точный parent `cab3eed183d9453a6bfcc17ca12149d1894f39ba`, worktree `/workspace/rct-worktrees/teacher-web`. Inventory: шесть product files и один новый mounted test, только `frontends/web-vue/src/features/teacher/**`. Рассмотрены целиком изменённые файлы и связанные live/transport/session-owner, TeacherExport/report port, Journal pagination, filters/table/attachment contracts. Изменений продукта reviewer не делал.

## F1 — P2: background Journal refresh теряет уже загруженные страницы и фокус

Место: `TeacherJournalPage.vue:57–78`, взаимодействие с `teacher-state.ts:121–130` и `use-journal-pagination.ts:25–41`.

Конкретный сценарий: преподаватель догрузил Journal до 60 пар и держит фокус на ссылке/ячейке пары из второй страницы. Приходит обычный live hint об удалении другой группы; текущие semester/group/subject/types и полномочия не изменились. `retain` становится true, но `requested = loadedQuery` содержит исходный query без page. `api.journal(requested)` снова получает только первую страницу (pageSize30), а `journal.load(..., undefined, true)` по завершении присваивает `data = next`, заменяя ранее merged pages. Пары31–60 удаляются из DOM; их focused node перестаёт существовать. После нового hint поведение повторяется. В прежнем коде focused region откладывал этот сброс; новый неотложный background путь делает его возможным прямо во время работы.

`retain` здесь сохраняет данные только до ответа, а не уже открытое окно Journal. Первая страница в новом mounted test скрывает этот случай. Автоматическая последующая догрузка не сохраняет исходный DOM node/focus и не отменяет произошедший сброс.

Минимальное закрытие: background refresh того же разрешённого applied query должен обновлять уже загруженное окно Journal без промежуточного обрезания до первой страницы и без применения dirty date draft. Использовать существующую пагинацию/merge и owner/revision fences; не возвращать focus/dirty блокировку admission/catalog reads. Авторитетный scope loss/401/403/404 по-прежнему должен немедленно выводить приватную проекцию. Достаточная причинная регрессия — Journal с второй догруженной страницей, фокус на её ячейке/ссылке, hint другой группы, проверка того же focused node и обновлённого текущего окна. Новый framework или широкая кампания проверок не нужны.

## Остальная квалификация

- `useTeacherScope` больше не передаёт pointer/focus/dirty в live blocked. Catalog/admission GET продолжаются при фокусе; background transient read error сохраняет предыдущий admitted catalog. Известные401/403/404 очищают catalog/semester и дочерние приватные ресурсы. Новая авторизация на клиенте не создаётся.
- Stats retain ограничен semester/scope/group/subject/types; group overview дополнительно проверяет, что все показанные группы остаются в новом catalog. При потере выбранного scope прежняя таблица/draft снимаются до ожидания нового roster. При прежнем разрешённом контексте сохраняются mounted table/filter draft и focus, busy не навязывается фоновой загрузкой.
- Stats/Journal export selector не пропадает из-за transient resource error при сохранённых admitted data/query; прежний TeacherExport/report port остаётся владельцем исходного requestId. Перепроверка неизвестной выгрузки использует прежний requestId через существующий idempotent port; фоновые hints сами export POST не отправляют. Формат/запрос/identity сохраняют существующие owner fences. Отдельных изменений Auth retry/CAS/mutation policy нет.
- Journal обновляет именно `loadedQuery`, а не dirty date fields; submit остаётся явным. Замечание F1 относится к размеру уже загруженного окна, не к повторной отправке date draft.
- Home/Day/Lesson/Excuse background loaders используют существующие resource revision/current checks. При новой identity старый scope очищается, поздний ответ не публикуется через текущий transport. Очистка catalog снимает старые проекции и attachments; slot/route context смена не переиспользует прежний retain. Общие app/live/mobile/backend исходники не изменены.

Авторское evidence `/workspace/rct-worktrees/teacher-web/.agent/evidence/teacher-live-focus/20261024`: baseline-causal содержит исходные4FAIL, финальный scoped log —4files/14PASS (включая8 новых mounted), strict/build/lint PASS сообщены автором и сохранены. Это не runtime/browser qualification; reviewer проверки не перезапускал. Наличие этих PASS не покрывает F1 с второй страницей.

Все обнаруженные существенные замечания приведены одним packet; других P1/P2 в reviewed source/influence не обнаружено. Runtime cohort/physical WS и фактическое естественное поведение WEB/PWA/TMA остаются отдельной приёмкой, QENV010 не изменён. Private/API/browser/DB/runtime операции reviewer —0. Назначение модели gpt-6.1-sol/high; actual model telemetry недоступна.
