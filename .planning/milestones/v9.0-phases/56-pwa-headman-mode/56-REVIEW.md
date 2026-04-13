---
phase: 56-pwa-headman-mode
reviewed: 2026-04-13T00:00:00Z
depth: standard
files_reviewed: 32
files_reviewed_list:
  - frontends/pwa/src/features/auth/AuthProvider.tsx
  - frontends/pwa/src/features/auth/api.ts
  - frontends/pwa/src/features/auth/__tests__/AuthProvider.isHeadman.test.tsx
  - frontends/pwa/src/features/headman/excuses/ExcusesPage.test.tsx
  - frontends/pwa/src/features/headman/excuses/ExcusesPage.tsx
  - frontends/pwa/src/features/headman/group-hub/GroupHub.test.tsx
  - frontends/pwa/src/features/headman/group-hub/GroupHub.tsx
  - frontends/pwa/src/features/headman/journal/JournalPage.test.tsx
  - frontends/pwa/src/features/headman/journal/JournalPage.tsx
  - frontends/pwa/src/features/headman/journal/JournalStudentRow.tsx
  - frontends/pwa/src/features/headman/late-checkin/LateCheckinPage.test.tsx
  - frontends/pwa/src/features/headman/late-checkin/LateCheckinPage.tsx
  - frontends/pwa/src/features/headman/overview/Overview.test.tsx
  - frontends/pwa/src/features/headman/overview/Overview.tsx
  - frontends/pwa/src/features/headman/shared/headmanApi.ts
  - frontends/pwa/src/features/headman/shared/types.ts
  - frontends/pwa/src/features/headman/stats/StatsPage.test.tsx
  - frontends/pwa/src/features/headman/stats/StatsPage.tsx
  - frontends/pwa/src/features/headman/stats/SubjectStatsCard.tsx
  - frontends/pwa/src/features/headman/students/AddAssistantModal.tsx
  - frontends/pwa/src/features/headman/students/StudentsList.test.tsx
  - frontends/pwa/src/features/headman/students/StudentsList.tsx
  - frontends/pwa/src/features/headman/subjects/SubjectFormModal.tsx
  - frontends/pwa/src/features/headman/subjects/SubjectsList.test.tsx
  - frontends/pwa/src/features/headman/subjects/SubjectsList.tsx
  - frontends/pwa/src/main.tsx
  - frontends/pwa/src/shared/components/BottomNav.tsx
  - frontends/pwa/src/shared/components/SegmentedControl.tsx
  - frontends/pwa/src/shared/components/__tests__/BottomNav.test.tsx
  - frontends/pwa/src/shared/components/__tests__/SegmentedControl.test.tsx
  - frontends/pwa/src/shared/components/useTabs.ts
  - frontends/pwa/src/sw-runtime-cache.ts
  - frontends/pwa/src/sw.ts
findings:
  critical: 1
  warning: 7
  info: 6
  total: 14
status: issues_found
---

# Phase 56: Code Review Report

**Reviewed:** 2026-04-13
**Depth:** standard
**Files Reviewed:** 32 (19 исходников + 13 тестов)
**Status:** issues_found

## Summary

Архитектура headman-модуля чистая: роль-aware навигация через `useTabs`, централизованный `headmanApi`, разделение на доменные директории (group-hub/overview/journal/students/subjects/stats/excuses/late-checkin), SW-слой с выделенной чистой функцией `isHeadmanApiRequest`. Тестовое покрытие хорошее (оптимистичный UI в JournalPage, сортировка в StatsPage, D-10 graceful degradation).

Ключевая проблема — нарушение Rules of Hooks в `StatsPage.tsx` (`SubjectStatsCollector` вызывает React-хуки в цикле по `subjects`, что формально работает только при стабильной длине массива, но рушится при любом изменении длины; eslint-disable маскирует реальный риск). Также есть несогласованность импорта анимационной библиотеки: CLAUDE.md предписывает `motion/react`, но `Overview.tsx`, `SubjectStatsCard.tsx`, `AddAssistantModal.tsx`, `SubjectFormModal.tsx` импортируют из `framer-motion`. Прочие замечания — незначительные.

## Critical Issues

### CR-01: Нарушение Rules of Hooks в `SubjectStatsCollector`

**File:** `frontends/pwa/src/features/headman/stats/StatsPage.tsx:145-194`
**Issue:** Компонент `SubjectStatsCollector` вызывает `useJournal` и `useResolveThreshold` в `for`-цикле по массиву `subjects`. Комментарий утверждает, что это безопасно «as long as order does not change within a render», но длина `subjects` меняется при каждом CRUD-событии (создание/удаление предмета через `useCreateSubject`/`useDeleteSubject`, которые инвалидируют `groupSubjects`). Как только `subjects.length` изменится между рендерами того же инстанса компонента — React выбросит «Rendered more/fewer hooks than during the previous render» и экран `/group/stats` крашнется.

Дополнительно: `computeStudentStats(cells)` вызывается прямо в теле компонента без `useMemo`, а `stats` пересобирается каждый рендер (хоть и не в хуке — это OK по корректности, но проигрывает по производительности при большом числе предметов).

**Fix:** Либо вернуться к варианту «одна `SubjectRow` на предмет» (уже присутствует как мёртвый код `SubjectRow` на строке 111, задушенный `void SubjectRow`), передавая стат-аггрегатор через callback/reducer родителю, либо использовать `useQueries` из TanStack Query:

```ts
import { useQueries } from '@tanstack/react-query'

function SubjectStatsCollector({ subjects, groupId, dateFrom, dateTo }) {
  const journalResults = useQueries({
    queries: subjects.map((s) => ({
      queryKey: ['journal', groupId, s.id, dateFrom, dateTo],
      queryFn: async () => {
        const { data } = await apiClient.get('/attendance/reports/journal', {
          params: { groupId, subjectId: s.id, dateFrom, dateTo },
        })
        return data._embedded?.journalCellList ?? []
      },
      enabled: !!groupId && !!s.id,
    })),
  })
  const thresholdResults = useQueries({
    queries: subjects.map((s) => ({
      queryKey: ['threshold', groupId, s.id],
      queryFn: async () => {
        const { data } = await apiClient.get('/academic/thresholds/resolve', {
          params: { groupId, subjectId: s.id },
        })
        return data
      },
      enabled: !!groupId && !!s.id,
    })),
  })
  // …build stats from journalResults[i]/thresholdResults[i]
}
```

`useQueries` — единственный хук, который получает динамический массив queries и легально его обрабатывает.

## Warnings

### WR-01: Непоследовательный импорт анимационной библиотеки (`framer-motion` vs `motion/react`)

**Files:**
- `frontends/pwa/src/features/headman/overview/Overview.tsx:3`
- `frontends/pwa/src/features/headman/stats/SubjectStatsCard.tsx:2`
- `frontends/pwa/src/features/headman/students/AddAssistantModal.tsx:2`
- `frontends/pwa/src/features/headman/subjects/SubjectFormModal.tsx:2`

**Issue:** В CLAUDE.md правило: «Motion (не Framer Motion)». Остальные компоненты фазы (GroupHub, JournalStudentRow, BottomNav) импортируют из `motion/react`. Четыре файла выше импортируют из `framer-motion`. Это либо два разных пакета в `node_modules` (+килобайты в bundle), либо сломанная сборка, если `framer-motion` не установлен. В любом случае нарушение proj-guide.
**Fix:** Заменить везде импорт:
```ts
import { AnimatePresence, motion } from 'motion/react'
```

### WR-02: `isLoading` в GroupHub использует `&&` вместо `||`

**File:** `frontends/pwa/src/features/headman/group-hub/GroupHub.tsx:73`
**Issue:** `const isLoading = membersLoading && subjectsLoading` — спиннер покажется только если оба запроса одновременно в полёте. Если участники уже закэшированы, а предметы ещё грузятся, `isLoading=false` и карточка «Предметы» покажет «— предметов» вместо скелета (выглядит как пустой ответ). Обычно `||` семантически корректнее для «хотя бы один ещё грузится».
**Fix:**
```ts
const isLoading = membersLoading || subjectsLoading
```
Либо убрать экран-загрузчик вовсе и показывать тире `—` в карточках до прихода данных (текущая `getMeta(m ?? '—', …)` уже это делает).

### WR-03: JWT-клейм `sub` парсится как число без валидации

**File:** `frontends/pwa/src/features/auth/AuthProvider.tsx:42`
**Issue:** `id: Number(payload.sub)` — если backend однажды выпустит токен с non-numeric `sub`, `NaN` тихо пройдёт в `user.id` и сломается вся арифметика, завязанная на ID. Аналогично `payload.groupId` — тип приходит как `number | undefined`, но если сервер пришлёт строку, код не словит это.
**Fix:**
```ts
const idNum = Number(payload.sub)
if (!Number.isFinite(idNum)) throw new Error('Invalid JWT: sub is not numeric')
return { id: idNum, role: payload.role, groupId: payload.groupId, isHeadman: payload.is_headman ?? false }
```

### WR-04: `parseJwt` не валидирует структуру токена

**File:** `frontends/pwa/src/features/auth/AuthProvider.tsx:27-37`
**Issue:** `token.split('.')[1]` выбросит `TypeError: Cannot read properties of undefined (reading 'replace')`, если токен пустой / без точек. `atob` кидает `InvalidCharacterError` на мусорных данных. В `login` эти ошибки не ловятся — форма логина зависнет с бесконечной прогресс-полоской.
**Fix:** обернуть `tokenToUser` в try/catch или проверить формат:
```ts
function parseJwt(token: string) {
  const parts = token.split('.')
  if (parts.length !== 3) throw new Error('Malformed JWT')
  // …existing decoding
}
```

### WR-05: `useTabs` мутирует массив через `splice` внутри `useMemo`

**File:** `frontends/pwa/src/shared/components/useTabs.ts:14-29`
**Issue:** `baseTabs.splice(baseTabs.length - 1, 0, {...})` работает, но мутация локального массива внутри `useMemo` — антипаттерн; читающему неочевидно, что `splice` здесь — вставка, а не удаление. Легко наступить при рефакторинге.
**Fix:** чище через условный spread:
```ts
const tabs: Tab[] = [
  { to: '/home', icon: House, label: 'Главная' },
  { to: '/schedule', icon: Calendar, label: 'Расписание' },
  { to: '/checkin', icon: Fingerprint, label: 'Отметка' },
  ...(user?.isHeadman ? [{ to: '/group', icon: Users, label: 'Группа' }] : []),
  { to: '/profile', icon: User, label: 'Профиль' },
]
return tabs
```

### WR-06: Неблокируемое повторное нажатие на сегмент при pending-мутации

**File:** `frontends/pwa/src/features/headman/journal/JournalStudentRow.tsx:22-38`
**Issue:** `useMarkAttendance()` не берёт `isPending` из мутации, а `SegmentedControl` не получает `disabled`. Если пользователь быстро тапает б→н→у, `mutate` выстреливает 3 раза; откат при ошибке первой мутации (`previous`) неверный — `previous` зафиксирован в момент второго `handleChange`, когда первая ещё не вернулась. Возможна рассинхронизация UI и сервера.
**Fix:** деструктурировать `isPending` и передавать в SegmentedControl:
```ts
const { mutate, isPending } = useMarkAttendance()
// …
<SegmentedControl value={status} onValueChange={handleChange} disabled={isPending} ... />
```
Либо внутри `handleChange` игнорировать tap при `isPending`. Для корректного отката — хранить `previousRef` через `useRef`, а не замыкание.

### WR-07: Два `mutation.onSuccess` не согласованы в SubjectFormModal

**File:** `frontends/pwa/src/features/headman/subjects/SubjectFormModal.tsx:49-62`
**Issue:** `onSuccess` в `mutate(body, { onSuccess: … })` вызывается локально, но в `headmanApi.ts:212, 231` глобальный `onSuccess` у мутации делает `invalidateQueries(['groupSubjects'])`. Порядок исполнения: сначала глобальный, потом локальный; `handleClose()` закроет модалку до получения свежих данных — ОК. Но сам код дважды повторяет `onSuccess: () => handleClose()` для create и edit, хотя мог быть один раз:
**Fix:**
```ts
function handleSave() {
  if (!name.trim() || name.trim().length < 2) return
  const body = { name: name.trim(), teacherId: teacherId ? Number(teacherId) : null }
  const onSuccess = () => handleClose()
  if (mode === 'create') createSubject.mutate(body, { onSuccess })
  else if (subject) updateSubject.mutate({ id: subject.id, body }, { onSuccess })
}
```
Не баг, но убирает дублирование и снижает риск расхождения.

## Info

### IN-01: `as any` в нескольких местах вместо типизированного error

**Files:**
- `frontends/pwa/src/features/headman/students/AddAssistantModal.tsx:54`
- `frontends/pwa/src/features/headman/subjects/SubjectFormModal.tsx:44`
**Issue:** `(createAssistant.error as any)?.response?.status` — `any` обходит типовую проверку. В `SubjectStatsCard.tsx:79` уже использован корректный тип `{ response?: { status?: number } }`. Привести к общему стилю.

### IN-02: Мёртвый код `SubjectRow` в StatsPage

**File:** `frontends/pwa/src/features/headman/stats/StatsPage.tsx:111-129, 270`
**Issue:** Компонент `SubjectRow` определён, но не вызывается; строка `void SubjectRow` существует только чтобы подавить warning `ts(6133)`. Если он нужен на будущее — лучше удалить и достать из git history; если нужен сейчас — использовать его (см. CR-01).
**Fix:** удалить компонент и `void SubjectRow`, либо использовать его вместо `SubjectStatsCollector`.

### IN-03: Overview — нереализованный endpoint при 404 отдаёт пустой массив, а счётчик всё равно прячется

**File:** `frontends/pwa/src/features/headman/overview/Overview.tsx:135-146`
**Issue:** Когда `excuseCount === 0`, показывается «Нет ожидающих запросов». Это корректно по D-10, но UX-смысл «endpoint не реализован» неотличим от «все одобрены». Если нужна лучшая семантика — рассмотреть флаг `isUnsupported` из query (в headmanApi возвращать нечто вроде `{ data: [], meta: { unsupported: true } }` при 404). Сейчас — не баг, просто ограничение.

### IN-04: `staleTime: 24h` для `useGroupSubjects` может отставать от CRUD

**File:** `frontends/pwa/src/features/headman/shared/headmanApi.ts:39`
**Issue:** Сутки staleTime для предметов, тогда как create/update/delete делают `invalidateQueries(['groupSubjects'])`. `invalidate` пересиливает staleTime, так что баг не возникает, но консистентнее поставить 5 минут как для journal, чтобы при cross-tab sync свежие данные приходили без ожидания мутации.

### IN-05: `computeSemesterStart` использует `new Date()` напрямую

**File:** `frontends/pwa/src/features/headman/stats/StatsPage.tsx:15-19`
**Issue:** `computeSemesterStart()` захватывает `Date` в момент импорта модуля при первом вызове `useMemo(computeSemesterStart, [])` — если пользователь оставит вкладку открытой 1 сентября, семестр не обновится до hard-reload. Не критично для MVP, но стоит упомянуть в отчёте.

### IN-06: `SubjectsList` row — `role="button"` на `<div>`, но без keyboard handler

**File:** `frontends/pwa/src/features/headman/subjects/SubjectsList.tsx:131-133`
**Issue:** `<div role="button" onClick={…}>` без `onKeyDown` (Enter/Space) и без `tabIndex={0}` — недоступно с клавиатуры. A11y violation. Лучше использовать `<button>` вокруг всей карточки, либо выделить клик-зону в собственный `<button>` внутри.
**Fix:**
```tsx
<div ... role="button" tabIndex={0}
  onClick={() => openEdit(subject)}
  onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') openEdit(subject) }}>
```

---

_Reviewed: 2026-04-13_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
