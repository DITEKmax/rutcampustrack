FAIL

1. **HIGH — shell не реагирует на внешний `navigation.push/replace`.**  
   **File:** `frontends/mobile-core/src/shared/components/MobileShell.vue:43`, `frontends/mobile-core/src/shared/navigation.ts:81`  
   **Evidence:** `currentRoute` зависит от локального `stackRevision`; он увеличивается только внутри shell `navigate/goBack` на строках 56–65. Сам stack хранит обычный массив и не публикует изменения. Независимый probe дал `{"before":"today","after":"today","actualStack":"today/checkin"}` после `stack.push(nestedRoute(...))`.  
   **Impact:** feature или deep link переводит stack на `task/detail/editor`, но shell продолжает считать экран root: dock остаётся видимым, Product/Host Back не появляется. Это ломает основную цель extraction.  
   **Reproduction:** создать stack на `today`, завернуть `stack.current` в Vue `computed`, прочитать значение, выполнить `stack.push(nestedRoute('today','today/checkin','task'))`; computed остаётся `today`, хотя `stack.current` уже `today/checkin`.

2. **HIGH — stack не гарантирует возврат nested route к его собственному root.**  
   **File:** `frontends/mobile-core/src/shared/navigation.ts:71`, `frontends/mobile-core/src/shared/navigation.ts:81`, `frontends/mobile-core/src/shared/navigation.ts:85`  
   **Evidence:**  
   - `createMobileNavigationStack(nestedRoute('homework','homework/detail','detail')).back()` возвращает `null`, stack остаётся `["homework/detail"]`.  
   - Stack `["today"]` после `push(homework/detail)` и `back()` возвращается в `today`, а не в owning root `homework`.  
   **Impact:** contextual/deep-link вход в detail может остаться без пути назад либо вернуть пользователя в чужой root. Это прямо нарушает acceptance criterion «nested back returns to its root, then stops at the root».  
   **Reproduction:** выполнить два сценария выше через экспортированные `createMobileNavigationStack`, `rootRoute` и `nestedRoute`.

3. **MEDIUM — замена `host` оставляет старые subscriptions и stale Back state.**  
   **File:** `frontends/mobile-core/src/shared/components/MobileShell.vue:72`, `frontends/mobile-core/src/shared/components/MobileShell.vue:78`, `frontends/mobile-core/src/shared/components/MobileShell.vue:85`  
   **Evidence:** watch при смене `props.host` вызывает только `setBackVisible` нового host. `subscribeKeyboard/subscribeBack` выполняются один раз в `onMounted`; старый host не отписывается и не получает `setBackVisible(false)`. На unmount скрывается только текущий host, хотя unsubscribe handles принадлежат первоначальному.  
   **Impact:** после adapter replacement старый Telegram BackButton может остаться видимым и продолжить управлять shell; новый host не получает keyboard/back callbacks.  
   **Reproduction:** mount detail route с `hostA`, заменить prop на `hostB`, затем вызвать сохранённые listeners: события `hostA` всё ещё меняют shell, а subscribe-функции `hostB` не вызваны.

4. **MEDIUM — регрессировало каноническое доступное имя пункта «Учёт».**  
   **File:** `frontends/mobile-core/src/shared/components/MobileBottomNav.vue:23`, `frontends/mobile-core/src/features/today/TodayScreen.vue:42`  
   **Evidence:** критичный оригинал `.agent/student-role-02/design-context/4581-3062.txt:278` требует UI-label «Учёт» и доступное имя «Посещаемость». До extraction Today задавал `aria-label="Посещаемость"`; теперь `itemLabel()` формирует `Учёт. Раздел пока недоступен`. Тип item не поддерживает отдельное accessible name.  
   **Impact:** screen reader получает неверное имя раздела; после включения маршрута будет слышно только «Учёт».  
   **Reproduction:** отрендерить текущий Today и проверить accessible name третьей кнопки nav.

Repair contract:

- **Defect:** устранить четыре нарушения выше.
- **Evidence:** использовать приведённые stack/reactivity результаты, lifecycle-анализ и Figma `4581:3062`.
- **Correction:** сделать stack observable для shell или оставить shell полностью controlled через reactive `route`; гарантировать owning-root history для initial/cross-root nested transitions; при смене host скрывать и отписывать предыдущий adapter, подписывать новый; добавить отдельное accessible name и объединять его с disabled reason.
- **Scope:** только `frontends/mobile-core/src/shared/**`, Today nav declaration, exports и bounded component/contract tests. Не менять App/adapters/backend/package contracts и не добавлять SDK.
- **Verification:** component-level test внешнего `push/replace`, deep-link/cross-root Back tests, `hostA → hostB → unmount` lifecycle test, accessible-name assertion; затем typecheck, 6+ contract tests, scoped lint, PWA/TMA build, keyboard probe и `git diff --check`.

Нужна свежая независимая recheck затронутой части после исправления.

Независимо подтверждены: baseline `8002b9e…`, typecheck exit 0, 6/6 tests, scoped lint exit 0, keyboard probe `true → false`, `git diff --check` exit 0 и fixture HTTP 200. `package.json` имеет тот же blob hash, что HEAD. Full lint остаётся отдельным известным gate из-за pre-existing `fixture-transport.test.ts:94`; genuine TMA host, real backend и полный visual PASS student role остаются последующими gates. В `evidence.md:34` есть неблокирующая неточность: написано «typed default factory», хотя код задаёт scalar default `undefined as never`; compiled probe при этом подтверждает нужное поведение.
