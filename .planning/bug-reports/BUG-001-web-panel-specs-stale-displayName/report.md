# BUG-001: web-panel `.spec.ts` используют устаревшее поле `displayName` вместо split-ФИО

**Найден:** 2026-04-13
**Severity:** minor
**Status:** open
**Component:** web-panel
**Role affected:** all (only affects unit tests, not runtime)
**Phase reference:** v9.0 (ФИО-split follow-up)

## Описание

После сплита ФИО на `lastName / firstName / middleName` (коммиты `73cad7b`, `33ee2f7`, `a3d3bd3`) runtime-код web-panel мигрирован на новые поля + helper `fullName()`, однако ряд `*.spec.ts` файлов всё ещё формирует мок-объекты с полем `displayName`. Это не блокирует прод (деплой и UI работают), но unit-тесты либо падают, либо проходят против фиктивных типов через `any`-cast и теряют ценность как защитная сетка для будущих ФИО-изменений.

## Шаги для воспроизведения

1. `cd frontends/web-panel`
2. `npm test` (или `ng test --watch=false`)
3. Наблюдать падения/warnings в тестах, перечисленных ниже, а также несоответствие типов в IDE при открытии `.spec.ts`.

## Ожидаемое поведение

Все `.spec.ts` используют `lastName / firstName / middleName` (+ `middleName: null` где отчества нет), как это сделано в `frontends/web-panel/src/app/features/admin/shared/types.ts` и в сервисе `admin-api.service.ts`. Тесты зелёные и типобезопасны.

## Фактическое поведение

Мок-данные и assertion'ы в следующих файлах используют `displayName`:

- `frontends/web-panel/src/app/features/admin/users/users-page.component.spec.ts:24` — моки пользователей.
- `frontends/web-panel/src/app/features/admin/users/users-page.component.spec.ts:116` — моки пользователей.
- `frontends/web-panel/src/app/features/admin/shared/admin-api.service.spec.ts:33,72,85,92,193,198` — моки запросов/ответов create/update users.
- `frontends/web-panel/src/app/features/admin/groups/groups-page.component.spec.ts:31-33,78` — моки headman + assertion `expect(headman!.displayName).toBe(...)`.
- `frontends/web-panel/src/app/features/headman/journal/headman-journal-page.component.spec.ts:33` — мок студента.
- `frontends/web-panel/src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.spec.ts:24,31` — моки строк журнала (здесь поле `displayName` правомерно, т.к. это DTO от attendance-service, но стоит перепроверить консистентность).
- `frontends/web-panel/src/app/features/teacher/journal/journal-grid/journal-grid.component.spec.ts:15,25` — то же.
- `frontends/web-panel/src/app/features/teacher/stats/stats-utils.spec.ts:21,30,50,62,67,82,104,112,132,154` — входные данные утилиты.

## Окружение

- **Браузер/устройство:** N/A (unit-тесты, Karma/Jest)
- **URL:** N/A
- **Пользователь/роль:** N/A
- **Дата/время:** 2026-04-13
- **Сеть:** N/A

## Скриншоты / видео

N/A — баг выявлен статическим анализом (grep `displayName` по `frontends/web-panel/**/*.spec.ts`) при расследовании падения деплоя auth-service на schema validation `display_name`.

## Логи / console / network (опционально)

```
# grep фикстур displayName в web-panel
grep -rn "displayName" frontends/web-panel/src/**/*.spec.ts
```

## Проанализированные причины

При разбивке ФИО на три поля (`lastName/firstName/middleName`) обновили:
- backend entity + Flyway V1 baseline,
- REST DTO (`UserResponse`, `CreateUserRequest`, …),
- runtime-TS web-panel (shared/types.ts + helper `fullName()`),
- большинство компонентов.

Не обновили мок-данные в spec-файлах. Для DTO из attendance-service (`JournalStudentRow.displayName`) поле правомерно — его заполняет academic через composed `getDisplayName()`. Для academic `UserResponse` — устарело.

## Предполагаемый fix

1. В `admin`-фичах заменить `displayName: '...'` на `lastName: '...', firstName: '...', middleName: null` согласно `UserResponse` из `features/admin/shared/types.ts`.
2. Там где в assertion'ах использовался `displayName`, использовать `fullName(u)` helper.
3. Для journal/stats проверить контракт Attendance `JournalStudentRow` — если поле всё ещё `displayName`, мок корректен; в противном случае мигрировать.
4. Прогнать `npm test` в `frontends/web-panel`, убедиться что all green.

## Fix (заполнить когда status = fixed)

- Commit(s):
- Phase/plan:
- Notes:
