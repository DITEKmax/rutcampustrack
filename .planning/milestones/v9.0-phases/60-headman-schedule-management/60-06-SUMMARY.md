---
phase: 60-headman-schedule-management
plan: 06
subsystem: web-panel
tags: [frontend, angular, material, headman, subjects, multi-select, forms]
dependency_graph:
  requires:
    - "60-01 — backend /api/academic/subjects принимает {name, type, teacherIds[]}"
  provides:
    - "SubjectDialogComponent multi-select teacherIds[] + type (LECTURE/PRACTICE/LAB)"
    - "HeadmanApiService.createSubject/updateSubject signatures: {name, type, teacherIds[]}"
    - "HeadmanApiService.addTeacherToSubject / removeTeacherFromSubject"
  affects: []
tech_stack:
  added: []
  patterns:
    - "Angular Reactive Forms с nonNullable: true"
    - "MatSelect multiple для multi-select"
    - "@testing-library/vitest style specs (vi.fn + of)"
requirements: [AC-01, AC-11]
key_files:
  created:
    - frontends/web-panel/src/app/features/headman/subjects/subject-dialog.component.spec.ts
  modified:
    - frontends/web-panel/src/app/features/headman/subjects/subject-dialog.component.ts
    - frontends/web-panel/src/app/features/headman/shared/headman-api.service.ts
    - frontends/web-panel/src/app/features/headman/shared/headman-api.service.spec.ts
decisions:
  - "type и name — required validators; teacherIds — опциональный (пустой массив валиден, D-19)"
  - "FormControl<number[]>([], nonNullable:true) для массива teacherIds — избегает null ambiguity"
  - "В mat-option отображаем lastName (fallback на fullName) — консистентно с предыдущим UI"
  - "mat-hint 'Можно выбрать нескольких; все — равноправные наблюдатели' — подчёркивает D-15 модель"
metrics:
  duration_min: ~8
  completed: 2026-04-14
  tests_total: 369
  tests_added: 4
---

# Phase 60 Plan 06: Headman Subjects Dialog — Multi-select Teachers + Type Field

Обновил Angular-диалог `/headman/subjects`: заменил single-select `teacherId` на multi-select `teacherIds: number[]`, добавил обязательный выбор `type` (LECTURE/PRACTICE/LAB). `HeadmanApiService.createSubject`/`updateSubject` теперь шлют `{name, type, teacherIds[]}` в соответствии с контрактом из Plan 60-01. Добавлены новые методы `addTeacherToSubject` / `removeTeacherFromSubject` для inline-управления преподавателями.

## Что сделано

### Task 1 — subject-dialog + api service + specs (commit `95427e4`)

**subject-dialog.component.ts:**
- FormGroup: `name` (required, maxLength 120), `type` (required), `teacherIds: number[]` (nonNullable, default `[]`).
- Новый `<mat-select>` для type с тремя опциями: Лекция / Практика / Лабораторная.
- `<mat-select formControlName="teacherIds" multiple>` — multi-select с сортировкой по lastName (ru-collator).
- `mat-hint` о равноправности наблюдателей (D-15).
- Edit mode: `patchValue` с `type: subject.type ?? ''` и `teacherIds: subject.teacherIds ?? []`.
- `onSubmit()` body: `{name, type, teacherIds: teacherIds ?? []}`.

**headman-api.service.ts:**
- `createSubject({name, type, teacherIds: number[]})` — сигнатура обновлена.
- `updateSubject(id, {name, type, teacherIds: number[]})` — аналогично.
- Новые методы:
  - `addTeacherToSubject(subjectId, teacherId)` → POST `/api/academic/subjects/{id}/teachers/{teacherId}` (201 / 409).
  - `removeTeacherFromSubject(subjectId, teacherId)` → DELETE (204 / 404).

**subject-dialog.component.spec.ts (8 тестов):**
- create mode: submit c teacherIds=[1,2] + type=LECTURE → `createSubject` вызван с правильным body.
- create mode: teacherIds=[] валиден.
- create mode: пустой type → form.invalid, submit не вызывает API.
- create mode: пустой name → form.invalid.
- edit mode: `data.subject.teacherIds=[3,4]` предзаполняется.
- edit mode: submit через `updateSubject(10, {...})`.
- edit mode: отсутствие `subject.teacherIds` → дефолт `[]`.
- teacher loading: сортировка по lastName с ru-collator.

**headman-api.service.spec.ts:**
- Обновлены тесты `createSubject` / `updateSubject` на новый body.
- Добавлен тест `createSubject` с пустым `teacherIds`.
- Новые тесты `addTeacherToSubject` и `removeTeacherFromSubject`.

## Тесты

- `src/app/features/headman/subjects/subject-dialog.component.spec.ts`: 8/8 зелёные.
- `src/app/features/headman/shared/headman-api.service.spec.ts`: 13/13 зелёные (+3).
- Полный прогон `npx vitest run`: **369/369 passed** (было 365 до плана 60-06, +4 новых теста).

## Endpoints (покрытые сервисом)

| Метод | Путь | Описание |
|-------|------|----------|
| POST | `/api/academic/subjects` | `{name, type, teacherIds[]}` |
| PUT | `/api/academic/subjects/{id}` | full-update |
| POST | `/api/academic/subjects/{id}/teachers/{teacherId}` | добавить препода (201/409) |
| DELETE | `/api/academic/subjects/{id}/teachers/{teacherId}` | удалить препода (204/404) |

## Deviations from Plan

None — план исполнен как написан.

## Known Stubs

Нет.

## Threat Flags

Нет нового surface. STRIDE-map плана:
- T-60-01 (Broken Access Control): backend (Plan 60-01) проверяет `requireHeadmanGroupId()` + JWT `is_headman` — frontend не может подменить `groupId`.
- T-60-06 (Injection): `teacherIds` — массив чисел, Angular form validators, backend принимает `List<Long>`.

## Self-Check: PASSED

**Created files (existence verified):**
- FOUND: frontends/web-panel/src/app/features/headman/subjects/subject-dialog.component.spec.ts

**Modified files (verified):**
- FOUND: frontends/web-panel/src/app/features/headman/subjects/subject-dialog.component.ts
- FOUND: frontends/web-panel/src/app/features/headman/shared/headman-api.service.ts
- FOUND: frontends/web-panel/src/app/features/headman/shared/headman-api.service.spec.ts

**Commits (verified via `git log --oneline -1`):**
- FOUND: 95427e4 feat(60-06): multi-select teachers + type field in subject dialog

**Tests:** `npx vitest run` — 369/369 PASSED.
