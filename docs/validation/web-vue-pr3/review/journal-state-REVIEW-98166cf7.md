# Journal state fix — independent source review: CHANGES REQUIRED

**Один P2: новый error filter теряет current session denial из штатного refresh/re-auth пути.** Local503/403 recovery и static loading skeleton закрывают исходные presentation causes в source, но security retirement boundary нужно сохранить до интеграции.

Exact revision `98166cf77975a34be82e465e30e66ae1f8d64e93`, `/workspace/rct-worktrees/theme-foundation`, branch `codex/pr3-journal-state-fix`, basecab3. Прочитаны весь трёхфайловый diff, `journal-state-fix/HANDOFF.json` и адресно связанные Teacher transport/PWA/TMA owner classifiers. Product не менялся.

## P2 — current refresh denial ошибочно остаётся локальным journal error

**Путь:** `frontends/mobile-core/src/features/teacher/TeacherJournalScreen.vue:294`.

Новый predicate передаёт host только `cause instanceof TeacherApiError && cause.response.status===401`. Но реальный `TeacherApi.response` (`teacher-client.ts:633–644`) при первом journal401 сначала вызывает `options.onUnauthorized()`. Generation-bound wrapper (`:715–718`) вызывает current owner's refresh/re-auth и пропускает его отказ без преобразования в TeacherApiError.

**Конкретная causal sequence:** mounted current Teacher journal GET401 → PWA `refreshFor` получает Auth refresh401 → `AuthRequestError` выходит из `TeacherApi.response` → current `TeacherJournal.load` catch видит этот error, но новый predicate не делает emit. PWA `auth.ts:250–252` только проверяет current generation и rethrows; самостоятельно owner не очищает. Раньше host получал error, а `App.vue:639–644,929–940` узнавал AuthRequestError401/typed current-role denial и запускал существующий retirement. Теперь host не получает этот сигнал: journal очищает свою table и показывает локальный error, но cached sibling Teacher projections/owner остаются до другого trigger.

Аналогичный TMA путь: `createTeacherApi` делегирует `refreshFor: authenticateFor` (`tma-session.ts:290–296`); current re-auth/candidate-session denial даёт `TmaAuthError401/403` (`:143–182`), не TeacherApiError. Existing TMA host `isAuthDenied` (`App.vue:803–813`) должен его получить, но новый predicate скрывает его. Stale owner/query fences до predicate правильны и не требуют ослабления.

Это source-derived auth retirement regression, не утверждение о новом actual denial run и не backend authorization bypass. Новые current401 tests создают TeacherApiError401 напрямую через mocked `api.journal`, минуя реальный onUnauthorized path, поэтому13/13 не проверяет эту отдельную границу.

**Минимальная correction:** оставить recoverable endpoint TeacherApiError403/503 локальными, но сохранить forwarding текущих authority-denial ошибок штатного refresh/re-auth к существующему host classifier; не предполагать, что они всегда TeacherApiError. Сохранить active/revision/API/context и StaleSessionGenerationError fences. Проверка должна представлять current journal401 → rejected owner refresh/re-auth и убедиться в передаче session denial, плюс late obsolete outcome остаётся ignored. Новая Auth/browser campaign, повтор исходного login или изменение App/shared contract для самого review не требуется; конкретный узкий predicate/adapter выбирает тот же автор.

## Остальной causal scope

- Local Teacher endpoint503/403 больше не записывается в host bootstrapError: existing local retry очищает local error и принимает тот же query; глобальный stale warning из actual MB04/05/06 этим источником устраняется. При этом generic auth-denial distinction выше ещё не завершён.
- Skeleton статический, семь rows/четыре columns; `role=status`, polite hidden caption и декоративный `aria-hidden`, существующий main aria-busy сохраняются. Styles используют существующие semantic surface/border/skeleton/spacing/touch tokens, minmax tracks и overflow containment; motion не добавлен. Отдельного существенного source finding здесь нет. Фактические after-PNG ещё нужны в согласованном узком visual scope, не новый full-width/state matrix.
- Query/pagination/late-result guards, loaded table/chart/export paths и unknown command handling не изменены. Три adjacency-formatting изменения template не меняют auth/domain behavior. Product inventory ровно TeacherJournalScreen.vue, teacher-journal-screen.pcss и существующий teacher-journal-pagination.test.ts.

Author HANDOFF reports13/13 existing scoped suite и strict PWA TypeScript/Prettier/PostCSS/diff PASS. Они переиспользованы без rerun; `postcss-check.log` фиксирует preprocess PASS. Новых tests/runtime/browser/API/private values reads reviewer не выполнял. Reviewed HEAD совпал с98166; единственная новая review запись — этот отчёт. Requested Sol6.1/high; served-model telemetry недоступна.

Предыдущий actual392 six-frame result остаётся отдельным before evidence. Его own browser/context/TTY64379 уже закрыты reviewed stdin terminal proof, raw exit1/native cookie-guard flags сохранены; actual exact-own revoke квалифицирован отдельным Root helper+independent postguard, не ложным native204. Это не runtime validation нового98166 source.
