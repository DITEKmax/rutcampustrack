FAIL

- **MEDIUM — invalidation реализована как одноразовый edge, а не как граница sensitive-state.**  
  `frontends/mobile-core/src/features/profile/SecurityScreen.vue:64` очищает форму только при входе в `ACCOUNT_INVALIDATED`; поля и reveal-кнопки остаются активными (`:174`, `:184`, `:210`, `:220`, `:238`, `:248`), тогда как disabled привязан лишь к submit (`:302`).  
  **Evidence:** после срабатывания watcher пользователь может снова ввести пароли и включить reveal; при снятии ошибки callback watcher на `:67` ничего не очищает.  
  **Impact:** invalidated-экран снова удерживает чувствительные значения, а снятие ошибки оставляет заполненную, раскрытую и уже разрешённую к submit форму вместо требуемой пустой формы.  
  **Воспроизведение:** установить `ACCOUNT_INVALIDATED` → ввести значения и включить reveal при сохраняющейся ошибке → очистить `props.error`; значения/reveal сохранятся, submit станет доступен.

Repair contract:

- **Defect:** invalidation не удерживает форму в очищенном состоянии.
- **Evidence:** строки выше; существующая mounted QA проверяла только данные, введённые до invalidation.
- **Correction:** в `SecurityScreen.vue` запретить редактирование и reveal при `accountInvalidated` и гарантировать повторную очистку при выходе из invalidated-state. Не ослаблять независимый submit guard и не менять поведение обычных ошибок.
- **Scope:** только `SecurityScreen.vue` и узкая observable проверка.
- **Verification:** сценарий «invalidation → попытка ввода/reveal → clear error» должен дать три пустых скрытых поля, enabled submit и callback count 0; затем повторить текущие vue-tsc, ESLint, state 20/20, Vite build и mounted ordinary-error/success проверки. После правки обязательна свежая независимая recheck.

Проверенные хэши contract, Security, source manifest, checks manifest и mounted QA совпали; reverse-transform callback/default правки Security точно восстановил pre-state `51FD…`/10825 bytes. Текущие callback/default объявления остальных шести SFC структурно сохраняют optional guards. Byte-level pre-edit snapshots этих шести файлов отсутствуют, поэтому callback-only provenance для них подтверждён слабее, чем для Security. Backend/Auth/PWA/TMA integration не проверялась и не заявляется.
