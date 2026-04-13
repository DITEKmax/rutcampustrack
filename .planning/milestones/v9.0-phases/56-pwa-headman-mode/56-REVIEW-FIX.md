---
phase: 56-pwa-headman-mode
fixed_at: 2026-04-13T00:00:00Z
review_path: .planning/phases/56-pwa-headman-mode/56-REVIEW.md
iteration: 1
findings_in_scope: 8
fixed: 8
skipped: 0
status: all_fixed
---

# Phase 56: Code Review Fix Report

**Fixed at:** 2026-04-13
**Source review:** .planning/phases/56-pwa-headman-mode/56-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 8 (1 critical + 7 warnings, info findings deferred)
- Fixed: 8
- Skipped: 0

Полный прогон `npx tsc --noEmit` (0 ошибок) и `npx vitest run` (21 test file, 115 tests passed) после всех правок — регрессий нет.

## Fixed Issues

### CR-01: Нарушение Rules of Hooks в `SubjectStatsCollector`

**Files modified:** `frontends/pwa/src/features/headman/stats/StatsPage.tsx`
**Commit:** 0125546
**Applied fix:** Удалён цикл вызова хуков `for (subject of subjects) useJournal/useResolveThreshold`. Переработан на паттерн «один collector-компонент на предмет» — `SubjectStatsCollector` безголовый (возвращает `null`), вызывает ровно одну пару хуков на инстанс (стабильный порядок), и через `useEffect` + `onStats` callback репортит собранные stats в `useReducer` родителя. Родитель сортирует по severity и рендерит `SubjectStatsCard`. Добавлен action `prune` для удаления устаревших записей при удалении предмета. Мёртвый код `SubjectRow` и `void SubjectRow` удалён (попутно закрыт IN-02).

### WR-01: Непоследовательный импорт анимационной библиотеки (`framer-motion` vs `motion/react`)

**Files modified:** `Overview.tsx`, `SubjectStatsCard.tsx`, `AddAssistantModal.tsx`, `SubjectFormModal.tsx`
**Commit:** a5941c6
**Applied fix:** Заменён импорт `from 'framer-motion'` на `from 'motion/react'` во всех 4 файлах согласно правилу CLAUDE.md.

### WR-02: `isLoading` в GroupHub использует `&&` вместо `||`

**Files modified:** `frontends/pwa/src/features/headman/group-hub/GroupHub.tsx`
**Commit:** 35cf857
**Applied fix:** `membersLoading && subjectsLoading` → `membersLoading || subjectsLoading`. Теперь спиннер показывается пока не прогрузились оба запроса; исключены «полупустые» карточки вида «— предметов».

### WR-03: JWT-клейм `sub` парсится как число без валидации

**Files modified:** `frontends/pwa/src/features/auth/AuthProvider.tsx`
**Commit:** b5d0b49
**Applied fix:** В `tokenToUser` после `Number(payload.sub)` добавлен guard `if (!Number.isFinite(idNum)) throw new Error(...)`. Дополнительно: `groupId` теперь явно проверяется `typeof === 'number'`, остальное превращается в `undefined` (защита от non-numeric значений в токене).

### WR-04: `parseJwt` не валидирует структуру токена

**Files modified:** `frontends/pwa/src/features/auth/AuthProvider.tsx`
**Commit:** b5d0b49 (совместно с WR-03 — правки в одной функции)
**Applied fix:** Перед `token.split('.')[1]` добавлена проверка `parts.length !== 3 → throw new Error('Malformed JWT: expected 3 segments')`. Теперь malformed-токен даёт осмысленное сообщение вместо `TypeError: Cannot read properties of undefined`.

### WR-05: `useTabs` мутирует массив через `splice` внутри `useMemo`

**Files modified:** `frontends/pwa/src/shared/components/useTabs.ts`
**Commit:** d1a5718
**Applied fix:** `baseTabs.splice(...)` заменён на декларативный conditional spread: `{'/checkin',...}, ...(isHeadman ? [{'/group',...}] : []), {'/profile',...}`. Порядок тот же (Главная → Расписание → Отметка → [Группа] → Профиль), но без мутации локального массива. Тесты `BottomNav` продолжают проходить (2/2).

### WR-06: Неблокируемое повторное нажатие на сегмент при pending-мутации

**Files modified:** `frontends/pwa/src/features/headman/journal/JournalStudentRow.tsx`
**Commit:** 910e33f
**Applied fix:** Деструктурирован `isPending` из `useMarkAttendance()`. В `handleChange` добавлен early-return `if (isPending) return`. `SegmentedControl` получает `disabled={isPending}`. Для корректного отката `previous` теперь хранится в `useRef<AttendanceStatus>` (инициализирован `initialStatus`), а не замыкании — ранее быстрая последовательность тапов могла зафиксировать уже оптимистично обновлённое значение.

**Note:** Требует human verification — затрагивает логику отката мутаций. Запустить JournalPage в браузере и протестировать rapid-tap сценарий б→н→у для проверки, что (a) второй тап игнорируется при pending, (b) при 500-ошибке откат идёт к первоначальному статусу, а не к промежуточному.

### WR-07: Два `mutation.onSuccess` не согласованы в SubjectFormModal

**Files modified:** `frontends/pwa/src/features/headman/subjects/SubjectFormModal.tsx`
**Commit:** c4d4da4
**Applied fix:** Дубликат `onSuccess: () => handleClose()` в двух ветках `create`/`edit` вынесен в локальную константу `const onSuccess = () => handleClose()` и передаётся в оба вызова `.mutate(..., { onSuccess })`.

## Skipped Issues

Нет пропущенных findings.

## Info Findings (вне scope)

Info-замечания (IN-01…IN-06) не обрабатывались по конфигу `fix_scope=critical_warning`. IN-02 (мёртвый код `SubjectRow`) был попутно закрыт в рамках CR-01 — компонент удалён вместе с `void SubjectRow` suppression.

---

_Fixed: 2026-04-13_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
