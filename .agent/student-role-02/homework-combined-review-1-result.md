## FAIL

### Findings

1. **MEDIUM — disclosure неверно выровнен без материалов.**  
   [homework-screen.pcss](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/homework-ui/frontends/mobile-core/src/features/homework/homework-screen.pcss:179) всегда использует `space-between`. При `link === null` единственная кнопка остаётся слева. Оригинал [4922-343.txt](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/student-role-02/design-context/4922-343.txt:44) требует `justify-end`; финальный capture воспроизводит ошибку.  
   **Repro:** карточка с `link: null`, viewport 390×844.

2. **MEDIUM — `title` и `description` показаны в обратной роли и порядке.**  
   [HomeworkScreen.vue](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/homework-ui/frontends/mobile-core/src/features/homework/HomeworkScreen.vue:417) показывает опциональный `description` сразу, а обязательный `title` — только после раскрытия и после actions. Первичный API определяет `title` как обязательное название, `description` как опциональное описание; BFF переносит их без перестановки. Оригинал [4601-848562.txt](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/student-role-02/design-context/4601-848562.txt:41) показывает краткий текст, затем подробности до actions.  
   **Impact:** при пустом допустимом `description` название задания скрыто до раскрытия; разные значения отображаются в неверном порядке.  
   **Repro:** `{ title: "Практика", description: "" }`.

### Repair contract

- Выровнять disclosure вправо для `link === null`, сохранив layouts доступной и небезопасной ссылки.
- Всегда показывать `title` как краткий текст; раскрывать непустой `description` перед actions; не создавать пустое disclosure.
- Scope: `HomeworkScreen.vue`, его PCSS и meaningful component/fixture coverage. API/generated types не менять.
- Проверить distinct/empty descriptions, три состояния материалов, пять состояний 390×844 и адаптивные варианты.
- После исправления обязательна свежая независимая recheck.

Проверки: frozen 25/25 hashes совпали; scope extra/missing — 0; `git diff --check` — exit 0; Vitest — 9 файлов, 28 тестов, exit 0; corrected owner-retry probe — exit 0. Range finding снят фактическим probe. `generate:types:check` остаётся exit 1 из-за line-ending-only drift и не считается schema/product-дефектом.
