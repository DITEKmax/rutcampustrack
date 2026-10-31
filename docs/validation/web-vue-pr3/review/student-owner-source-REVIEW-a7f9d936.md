# Student retired-owner continuations — independent product review

**PASS. Blocking findings не обнаружены.** Продуктовая правка корректно прекращает завершение старого Student read/filter после owner loss/unmount и сохраняет transport authority fence. Это source review с принятием авторского targeted evidence; интеграция и один actual changed owner-loss case ещё остаются отдельными шагами Root.

Reviewed commit `a7f9d93618aa45f1252fae3fdf1dfc32eb9e9f54`, branch `codex/pr3-student-owner-cancellation`, worktree `/workspace/rct-worktrees/web-foundation`; base `8e83a2d9105af75d015d6df7a8888eab330f43dc`. HEAD совпал с reviewed commit, worktree clean. Affected baseline files byte-equal exact Root `7d664ce89cff5813772d1923e310ad74c2706c87`; три final hashes соответствуют `result.json`.

## Поведение и fences

- `StudentHomePage.vue:54`: после awaited resource reads сначала проверяется `scope.current()`. Retired continuation возвращается до всё ещё существующего `transport.assertCurrent()` и before subject load/local continuation. Поэтому ожидаемая owner cancellation не выходит rejected async Vue watcher; current-owner errors не перехватываются общим catch и transport assert не ослаблен.
- `StudentAttendancePage.vue:63`: `loadSubject()` теперь сразу прекращается у retired scope, в том числе при owner loss во время предыдущего `nextTick`. Старый read не запускается от stale route/filter continuation.
- `StudentAttendancePage.vue:74`: queued `remember()` возвращается до assert/router.replace после owner loss/unmount. Следующий вызов `loadSubject()` в `chooseSubject()` тоже guarded. У current owner логика query, selected types и subject read сохранена.

`scope.current()` синхронно проверяет alive + frozen identity; между новой проверкой и сохранённым assert нет await. `createWebTransport` по-прежнему бросает `WebAuthorityChangedError` при смене владельца до/после fetch. `useStudentResource` сохраняет revision/current checks и очистку при unmount/context change; поздний private result не публикуется в successor projection. Нет broad swallow, API/role/session/mutation contract changes или изменения обработки genuine current HTTP/network errors.

Адресно просмотрены близкие Student read continuations и их boundaries: scope/resource loaders, Schedule/Ranking/SubjectStats, request target и controller guarded reads. Их async transport assertions остаются внутри resource/try/controller capture с current-owner publication fences; дополнительного подтверждённого unhandled read continuation той же causal формы не найдено. Review не означает аудит всех Student actions/attachments/security flows.

## Сила regressions и evidence

`student-refresh.test.ts` убирает no-op transport mock и использует настоящий `createWebTransport` с fixture только на fetch boundary. Тест Home удерживает реальный deferred attendance read, меняет identity/generation, unmount старой страницы, монтирует successor Attendance, затем выпускает старую completion. Проверяются отсутствие Vue async errors, сохранение successor `+` и то, что отдельно сохранённый старый actual transport **всё ещё** бросает `WebAuthorityChangedError`. Тест queued filter вызывает настоящий SFC handler после owner loss и требует resolved cancellation вместо rejected action. Reset identity per test устраняет прежнюю ложную изоляцию.

Прочитаны сохранённые авторские логи:

- `baseline-isolated-owner.log`: две смысловые FAIL, четыре existing PASS. Home failure содержит WebAuthorityChangedError в Vue error collector; queued Attendance failure показывает реальный `remember` → assertCurrent stack.
- `final-behavior-minimal-diff.log`: все6 PASS, включая новые две regressions и прежние authoritative refresh/focus retention, failed-refresh + explicit retry, dirty draft deferral и paging.
- `final-web-build.log`: один `npm run build:web`, strict vue-tsc + Vite PASS; только existing chunk-size warning. `result.json` также фиксирует diff-check PASS и причины авторских повторов harness/format corrections.

Эти проверки достаточны для локального source acceptance. Reviewer их не повторял и новых tests не писал. Test fixture для Homework отдельно заглушён; это не заявляется проверкой Homework lifecycle или реального Auth/provider. Runtime e38 содержал names-only pageErrors: детерминированные source repro доказывают два реальных callsite дефекта, но не устанавливают их как единственный точный источник обоих actual names-only errors.

## Exact inventory

В commit ровно3 изменённых файла, без добавления/удаления продукта:

1. `frontends/web-vue/src/features/student/StudentHomePage.vue` — current-before-assert reorder.
2. `frontends/web-vue/src/features/student/StudentAttendancePage.vue` — два entry current guards.
3. `frontends/web-vue/src/features/student/student-refresh.test.ts` — actual transport fixture и две поведенческие regressions.

Результат для пользователя: уход/смена authority во время чтения Home или queued Attendance filter завершает старую работу без leaked owner error и без переноса старых private данных в новый owner scope. После Root integration достаточно одного actual changed owner-loss case; full theme4/native/password/ACL replay или новый full suite этим review не требуется. Actual acceptance пока не заявлена.

Reviewer выполнял только source/diff/Git/hash/log reads. Runtime/browser/Auth/network/DB/tests/build execution —0. Создан только этот report; repo/product/shared docs не менялись. CI/manual PAUSED, no merge/deploy. Requested gpt-6.1-sol/high; actual model telemetry unavailable. No children.
