# Admin semester archive UI — 2026-09-29

## Goal

Добавить в существующий ADMIN экран семестров понятную архивацию и восстановление с подтверждением фактического завершения операции сервером.

## Context/evidence

- Frozen backend contract передан root; root подтвердил `SemesterResponse`, current `SemesterArchiveStatusResponse` и `SemesterArchiveOperationResponse`, включая `transition`, `releasePending`, `academic`, `schedule`, `attendance`.
- Архив и восстановление используют POST с пустым телом и обязательным `Idempotency-Key`; историческая операция не является источником текущего состояния.
- Изменения выполнены на baseline `0f331540eab744bfe460fc7fa4480e12383e8edc` в назначенном WT.

## Relevant scope

- `frontends/mobile-core/src/features/admin-semester/AdminSemesterScreen.vue`
- `frontends/mobile-core/src/features/admin-semester/admin-semester-client.ts`
- `frontends/mobile-core/src/features/admin-semester/admin-semester-client.test.ts`
- `frontends/mobile-core/src/features/admin-semester/admin-semester-screen.pcss`

## Required behavior

- Подтверждать архивирование и восстановление; перед POST сверять current status и entity.
- Для active-семестра объяснять, что после архивации активного периода не останется; восстановление оставляет семестр неактивным.
- Повторять неопределённый запрос только с тем же in-memory UUID; не считать HTTP 202/503 завершением.
- При PENDING разрешать продолжить проверку по operation ID; polling ограничен 10 попытками с интервалом 1,2 секунды.
- Показывать участника/ошибку операции и не разрешать обычные изменения до снятия серверного write block.
- После COMPLETED перечитывать current status, entity и список; показывать успех только при подтверждённом ожидаемом current состоянии.

## Constraints

- Изменена только назначенная feature-область; root-owned app wiring, shared types, backend и общая документация не затрагивались.
- UI сохраняет существующую карточную композицию и токены; новые design tokens и Figma-записи не добавлялись.
- Idempotency key не сохраняется в `sessionStorage`: компонент не получает стабильный actor-scope ID. В пределах смонтированного экрана неопределённый запрос повторяется с тем же ключом; после reload операция находится через current status.

## Existing patterns

- Используются существующие `AdminSemesterClient`, generation-bound session owner, карточки/кнопки экрана и локальные PCSS-токены.
- Текущие DTO и исторические snapshots разделены; версия состояния не позволяет заменить более свежие данные устаревшим ответом.

## Acceptance criteria

- Архив доступен только для стабильного незархивированного семестра; восстановление — для стабильного архивного семестра, включая корректный выход из `isWriteBlocked` архива.
- `releasePending`, переход и PENDING operation блокируют конфликтующие действия.
- Операции с ошибкой показывают причину; retryable ошибка начинает отдельную попытку после подтверждения, неопределённый ответ сохраняет исходный ключ.
- Restore не активирует семестр автоматически.
- Изменения укладываются в перечисленные feature-файлы и отдельную evidence-запись.

## Verification

- `npm run typecheck` в `frontends/mobile-core` — exit 0.
- `npx vitest run src/features/admin-semester/admin-semester-client.test.ts` — exit 0; 1 файл, 8 тестов passed.
- `npx eslint --max-warnings=0 src/features/admin-semester/AdminSemesterScreen.vue src/features/admin-semester/admin-semester-client.ts src/features/admin-semester/admin-semester-client.test.ts` — exit 0.
- `git --no-optional-locks diff --check -- frontends/mobile-core/src/features/admin-semester` — exit 0.
- Предыдущий широкий `npm run lint` завершился exit 1 с 555 repo-wide diagnostics (1 error, 554 warnings); после этого целевой ESLint трёх изменённых TS/Vue файлов прошёл. Широкий lint повторно не запускался и его сообщения не исправлялись вне доказанной связи со scope.
- PWA/TMA browser runtime не запускался: требуется общий интеграционный runtime после объединения фронтендов/backend; unit tests используют только fetch fixture и не являются backend acceptance.

## Do not

- Не утверждать успех только по HTTP-ответу или историческому operation snapshot.
- Не автоматически активировать восстановленный семестр.
- Не хранить idempotency key в session storage без actor-scope контракта.
- Не изменять чужие `.agent` fixtures/notes или файлы за пределами feature-scope.

## Bounded correction — 2026-09-30

Root передал два независимых Sol medium finding на базе `b56bc85ee9151f24d4ef45b8549ae1e7d40f7c75`, ветка `codex/semester-archive-ui-20260929`; SHA канонических RULES — `F4986A1834A9175ADBB7DCB3C49483168111A0B60C7EB13ACDA9FBF9D74927F8`. Композиция и стили не менялись; сохранён принятый экран Admin semesters (реестр `AdminSemestersPage`, wireframe 134).

- Общая блокировка мутаций теперь относится только к короткому сохранению/предварительной проверке статуса или команде `SUBMITTING`/`UNCERTAIN`. Барьеры `PENDING`, `SETTLING`, `transition`, `releasePending` остаются на конкретном семестре через `canEdit`/`canArchive`/`canRestore`/`hasPendingArchiveOperation`; отдельный `archiveSettlingOperationIds` удерживает A до подтверждения текущего состояния, даже если мониторный слот уже перешёл к B. Ожидающая операция A больше не выключает стабильный B; конфликтующая команда для A по-прежнему заблокирована.
- Проверка статуса может переключаться между durable monitor-операциями. После асинхронного чтения она сравнивает сохранённую идентичность текущей команды и не затирает конкурентно начатую команду.
- Catch-пути poll, settle завершённой операции, начального refresh pending и ручной проверки статуса передают текущему owner терминальные 401/403 через существующее событие `ownerError`. До этого проверяются scope/controller guards; `StaleSessionGenerationError` не передаётся как отказ текущего owner. При terminal auth во время settle дальнейший polling останавливается. `showError` использует тот же helper.

Diff correction: только `frontends/mobile-core/src/features/admin-semester/AdminSemesterScreen.vue` и эта существующая evidence-запись. Проверки в worktree: `git diff --check` — exit 0; `npm exec -- eslint --max-warnings=0 -- src/features/admin-semester/AdminSemesterScreen.vue` из `frontends/mobile-core` — exit 0. Новые wiring-тесты, broad lint, полный typecheck/build и browser/runtime не запускались; grouped build/runtime принадлежат root. Существующие client tests не покрывают изменения Vue-состояния и forwarding ошибок. Независимый Sol recheck этой correction ещё не выполнен.
